package rondes.service

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
        val roomsList = Rooms.selectAll().toList()

        // On récupère le dernier scan par salle en mémoire pour éviter les conflits d'ID lors des jointures
        val lastScanByRoom = Scans
            .selectAll()
            .orderBy(Scans.scannedAt, SortOrder.DESC)
            .toList()
            .groupBy { it[Scans.roomId].value }
            .mapValues { it.value.first() }

        val guardNames = Guards.selectAll().associate { it[Guards.id].value to it[Guards.fullName] }

        val patchesByRoom = Patches.selectAll()
            .where { Patches.active eq true }
            .associateBy { it[Patches.roomId]?.value }

        val now = Instant.now()

        roomsList.map { room ->
            val roomId = room[Rooms.id].value
            val lastScan = lastScanByRoom[roomId]
            val elapsedMinutes = lastScan?.let { Duration.between(it[Scans.scannedAt], now).toMinutes() }
            val orangeThreshold = room[Rooms.orangeThresholdMinutes]
            val redThreshold = room[Rooms.redThresholdMinutes]
            val patch = patchesByRoom[roomId]
            
            val level = when {
                patch?.get(Patches.damaged) == true -> "ROUGE"
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
        Rooms.selectAll().where { Rooms.id eq roomId }.singleOrNull()
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

    suspend fun deleteRoom(roomId: Int) = dbQuery {
        Rooms.selectAll().where { Rooms.id eq roomId }.singleOrNull()
            ?: throw NotFoundException("Salle introuvable")
        
        // 1. On détache les patches de cette salle
        Patches.update({ Patches.roomId eq roomId }) {
            it[Patches.roomId] = null
        }
        
        // 2. On supprime l'historique des scans (pour éviter les erreurs de clé étrangère)
        Scans.deleteWhere { Scans.roomId eq roomId }
        
        // 3. On supprime la salle
        Rooms.deleteWhere { Rooms.id eq roomId }
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
