package rondes.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Une salle du musee. Les seuils d'alerte sont configurables par salle
 * (une reserve peu visitee et une salle d'entree n'ont pas la meme criticite).
 */
object Rooms : IntIdTable("rooms") {
    val name = varchar("name", 128)
    val building = varchar("building", 128).nullable()
    val floor = varchar("floor", 64).nullable()
    val orangeThresholdMinutes = integer("orange_threshold_minutes").default(120)
    val redThresholdMinutes = integer("red_threshold_minutes").default(360)
}

enum class GuardRole { GARDIEN, CHEF_DE_POSTE, DIRECTION }

/** Un compte utilisateur. Authentification par badge + code PIN (demo) -> bcrypt hash stocke. */
object Guards : IntIdTable("guards") {
    val badge = varchar("badge", 32).uniqueIndex()
    val fullName = varchar("full_name", 128)
    val pinHash = varchar("pin_hash", 60)
    val role = enumerationByName("role", 32, GuardRole::class)
    val active = bool("active").default(true)
    val failedAttempts = integer("failed_attempts").default(0)
    val lockedUntil = timestamp("locked_until").nullable()
}

/**
 * Un patch NFC physique. tagUid = identifiant materiel lu par le lecteur NFC.
 * roomId est nullable tant que le patch n'a pas ete associe (enrollment).
 */
object Patches : IntIdTable("patches") {
    val tagUid = varchar("tag_uid", 64).uniqueIndex()
    val roomId = reference("room_id", Rooms).nullable()
    val active = bool("active").default(true)
    val damaged = bool("damaged").default(false)
    val createdAt = timestamp("created_at")
}

/**
 * Une ronde enregistree. scannedAt = horodatage revendique (peut venir d'une synchro hors-ligne),
 * receivedAt = horodatage serveur de reception, pour detecter les ecarts suspects.
 */
object Scans : IntIdTable("scans") {
    val patchId = reference("patch_id", Patches)
    val roomId = reference("room_id", Rooms)
    val guardId = reference("guard_id", Guards)
    val scannedAt = timestamp("scanned_at")
    val receivedAt = timestamp("received_at")
    val offlineSync = bool("offline_sync").default(false)
}

/** Jeton de session emis a la connexion, duree de vie courte (fin de vacation). */
object Sessions : IntIdTable("sessions") {
    val token = varchar("token", 64).uniqueIndex()
    val guardId = reference("guard_id", Guards)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
}
