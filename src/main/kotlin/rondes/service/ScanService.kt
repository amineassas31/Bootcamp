package rondes.service

import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import rondes.db.Patches
import rondes.db.Rooms
import rondes.db.Scans
import rondes.db.dbQuery
import rondes.model.ScanResponse
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

private val MAX_OFFLINE_WINDOW: Duration = Duration.ofHours(6)
private val MAX_CLOCK_SKEW: Duration = Duration.ofMinutes(2)
private val OFFLINE_SYNC_THRESHOLD: Duration = Duration.ofSeconds(60)

object ScanService {

    /**
     * Enregistre un passage. scannedAt (optionnel) est l'horodatage capture par le telephone au moment
     * du tap NFC : indispensable pour les zones sans reseau (sous-sol) ou le scan est mis en file locale
     * et synchronise plus tard. On borne la fenetre acceptee pour eviter qu'un horodatage soit falsifie
     * a volonte depuis le client.
     */
    suspend fun recordScan(tagUid: String, guard: AuthedGuard, claimedScannedAt: String?): ScanResponse = dbQuery {
        val patchRow = Patches.selectAll().where { Patches.tagUid eq tagUid }.singleOrNull()
            ?: throw NotFoundException("Patch NFC inconnu du systeme")

        if (!patchRow[Patches.active]) {
            throw ConflictException("Ce patch a ete desactive")
        }
        if (patchRow[Patches.damaged]) {
            throw ConflictException("Ce patch est signale endommage : contactez le chef de poste pour le remplacement")
        }
        val roomId = patchRow[Patches.roomId]?.value
            ?: throw ConflictException("Ce patch n'est associe a aucune salle (enrollment incomplet)")

        val receivedAt = Instant.now()
        val scannedAt = resolveScannedAt(claimedScannedAt, receivedAt)
        val offlineSync = Duration.between(scannedAt, receivedAt).abs() > OFFLINE_SYNC_THRESHOLD

        Scans.insertAndGetId {
            it[patchId] = patchRow[Patches.id].value
            it[Scans.roomId] = roomId
            it[guardId] = guard.id
            it[Scans.scannedAt] = scannedAt
            it[Scans.receivedAt] = receivedAt
            it[Scans.offlineSync] = offlineSync
        }

        val roomName = Rooms.selectAll().where { Rooms.id eq roomId }.single()[Rooms.name]

        ScanResponse(
            roomId = roomId,
            roomName = roomName,
            guardName = guard.fullName,
            scannedAt = scannedAt.toString(),
            offlineSync = offlineSync,
        )
    }

    private fun resolveScannedAt(claimed: String?, receivedAt: Instant): Instant {
        if (claimed == null) return receivedAt
        val parsed = try {
            Instant.parse(claimed)
        } catch (e: DateTimeParseException) {
            throw BadRequestException("Horodatage de scan invalide")
        }
        if (parsed.isAfter(receivedAt.plus(MAX_CLOCK_SKEW))) {
            throw BadRequestException("Horodatage de scan dans le futur")
        }
        if (parsed.isBefore(receivedAt.minus(MAX_OFFLINE_WINDOW))) {
            throw BadRequestException("Horodatage de scan trop ancien (fenetre hors-ligne depassee)")
        }
        return parsed
    }
}
