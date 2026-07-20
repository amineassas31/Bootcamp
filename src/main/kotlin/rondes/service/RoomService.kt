package rondes.service

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import rondes.db.Guards
import rondes.db.Patches
import rondes.db.Rooms
import rondes.db.Scans
import rondes.db.dbQuery
import rondes.model.PatchDto
import rondes.model.RoomCreateRequest
import rondes.model.RoomStatusDto
import rondes.model.RoomUpdateRequest
import java.time.Duration
import java.time.Instant

object RoomService {

    suspend fun listStatuses(): List<RoomStatusDto> = dbQuery {
        val rooms = Rooms.selectAll().toList()

        // dernier scan par salle (taille de musee raisonnable pour une jointure en memoire ; a revoir avec une
        // sous-requete SQL agregee si le nombre de rondes explose en production multi-site).
        val lastScanByRoom = Scans
            .selectAll()
            .orderBy(Scans.scannedAt, SortOrder.DESC)
            .groupBy { it[Scans.roomId].value }
            .mapValues { (_, rows) -> rows.first() }

        val guardNames = Guards.selectAll().associate { it[Guards.id].value to it[Guards.fullName] }

        val patchByRoom = Patches.selectAll()
            .where { Patches.active eq true }
            .associateBy { it[Patches.roomId]?.value }

        val now = Instant.now()

        rooms.map { room ->
            val roomId = room[Rooms.id].value
            val lastScan = lastScanByRoom[roomId]
            val elapsedMinutes = lastScan?.let { Duration.between(it[Scans.scannedAt], now).toMinutes() }
            val orangeThreshold = room[Rooms.orangeThresholdMinutes]
            val redThreshold = room[Rooms.redThresholdMinutes]
            val patch = patchByRoom[roomId]
            val level = when {
                patch?.get(Patches.damaged) == true -> "ROUGE" // patch signale endommage : jamais masque par un scan passe
                elapsedMinutes == null -> "ROUGE"
                elapsedMinutes < orangeThreshold -> "VERT"
                elapsedMinutes < redThreshold -> "ORANGE"
                else -> "ROUGE"
            }

            RoomStatusDto(
                id = roomId,
                name = room[Rooms.name],
                building = room[Rooms.building],
                floor = room[Rooms.floor],
                lastScanAt = lastScan?.get(Scans.scannedAt)?.toString(),
                lastGuardName = lastScan?.let { guardNames[it[Scans.guardId].value] },
                elapsedMinutes = elapsedMinutes,
                orangeThresholdMinutes = orangeThreshold,
                redThresholdMinutes = redThreshold,
                alertLevel = level,
                patchUid = patch?.get(Patches.tagUid),
                patchDamaged = patch?.get(Patches.damaged) ?: false,
            )
        }.sortedByDescending { it.elapsedMinutes ?: Long.MAX_VALUE }
    }

    suspend fun createRoom(req: RoomCreateRequest): Int = dbQuery {
        Rooms.insertAndGetId {
            it[name] = req.name
            it[building] = req.building
            it[floor] = req.floor
            it[orangeThresholdMinutes] = req.orangeThresholdMinutes
            it[redThresholdMinutes] = req.redThresholdMinutes
        }.value
    }

    suspend fun updateRoom(roomId: Int, req: RoomUpdateRequest) = dbQuery {
        val exists = Rooms.selectAll().where { Rooms.id eq roomId }.singleOrNull()
            ?: throw NotFoundException("Salle introuvable")
        Rooms.update({ Rooms.id eq roomId }) {
            req.name?.let { v -> it[name] = v }
            req.building?.let { v -> it[building] = v }
            req.floor?.let { v -> it[floor] = v }
            req.orangeThresholdMinutes?.let { v -> it[orangeThresholdMinutes] = v }
            req.redThresholdMinutes?.let { v -> it[redThresholdMinutes] = v }
        }
        Unit
    }

    suspend fun listPatches(): List<PatchDto> = dbQuery {
        val roomNames = Rooms.selectAll().associate { it[Rooms.id].value to it[Rooms.name] }
        Patches.selectAll().map {
            val roomId = it[Patches.roomId]?.value
            PatchDto(
                id = it[Patches.id].value,
                tagUid = it[Patches.tagUid],
                roomId = roomId,
                roomName = roomId?.let { id -> roomNames[id] },
                active = it[Patches.active],
                damaged = it[Patches.damaged],
            )
        }
    }

    /** Enrole (ou re-associe) un patch physique a une salle. Un tagUid deja connu est simplement reassocie. */
    suspend fun enrollPatch(tagUid: String, roomId: Int): PatchDto = dbQuery {
        Rooms.selectAll().where { Rooms.id eq roomId }.singleOrNull()
            ?: throw NotFoundException("Salle introuvable")

        val existing = Patches.selectAll().where { Patches.tagUid eq tagUid }.singleOrNull()
        val patchId = if (existing != null) {
            val id = existing[Patches.id].value
            Patches.update({ Patches.id eq id }) {
                it[Patches.roomId] = roomId
                it[active] = true
                it[damaged] = false
            }
            id
        } else {
            Patches.insertAndGetId {
                it[Patches.tagUid] = tagUid
                it[Patches.roomId] = roomId
                it[createdAt] = Instant.now()
            }.value
        }

        PatchDto(patchId, tagUid, roomId, Rooms.selectAll().where { Rooms.id eq roomId }.single()[Rooms.name], true, false)
    }

    suspend fun setPatchDamaged(patchId: Int, damaged: Boolean) = dbQuery {
        Patches.selectAll().where { Patches.id eq patchId }.singleOrNull()
            ?: throw NotFoundException("Patch introuvable")
        Patches.update({ Patches.id eq patchId }) { it[Patches.damaged] = damaged }
        Unit
    }
}
