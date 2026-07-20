package rondes.service

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import rondes.db.Guards
import rondes.db.Rooms
import rondes.db.Scans
import rondes.db.dbQuery
import rondes.model.HistoryEntryDto
import java.time.Instant

object HistoryService {

    suspend fun search(roomId: Int?, guardId: Int?, from: Instant?, to: Instant?): List<HistoryEntryDto> = dbQuery {
        val roomNames = Rooms.selectAll().associate { it[Rooms.id].value to it[Rooms.name] }
        val guardNames = Guards.selectAll().associate { it[Guards.id].value to it[Guards.fullName] }

        var query = Scans.selectAll()
        roomId?.let { id -> query = query.andWhere { Scans.roomId eq id } }
        guardId?.let { id -> query = query.andWhere { Scans.guardId eq id } }
        from?.let { f -> query = query.andWhere { Scans.scannedAt greaterEq f } }
        to?.let { t -> query = query.andWhere { Scans.scannedAt lessEq t } }

        query.orderBy(Scans.scannedAt, SortOrder.DESC).limit(500).map {
            HistoryEntryDto(
                id = it[Scans.id].value,
                roomId = it[Scans.roomId].value,
                roomName = roomNames[it[Scans.roomId].value] ?: "?",
                guardName = guardNames[it[Scans.guardId].value] ?: "?",
                scannedAt = it[Scans.scannedAt].toString(),
                offlineSync = it[Scans.offlineSync],
            )
        }
    }
}
