package rondes.service

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.mindrot.jbcrypt.BCrypt
import rondes.db.Guards
import rondes.db.Sessions
import rondes.db.dbQuery
import rondes.db.GuardRole
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class AuthedGuard(val id: Int, val badge: String, val fullName: String, val role: GuardRole)

private const val MAX_FAILED_ATTEMPTS = 5
private val LOCK_DURATION: Duration = Duration.ofMinutes(15)
private val SESSION_DURATION: Duration = Duration.ofHours(12)

object AuthService {

    private val random = SecureRandom()

    fun hashPin(pin: String): String = BCrypt.hashpw(pin, BCrypt.gensalt())

    /**
     * Verrouille le compte apres [MAX_FAILED_ATTEMPTS] echecs pour freiner
     * le bruteforce d'un code PIN a 4 chiffres (espace de recherche faible).
     */
    suspend fun login(badge: String, pin: String): Triple<String, AuthedGuard, Instant> = dbQuery {
        val row = Guards.selectAll().where { Guards.badge eq badge }.singleOrNull()
            ?: throw UnauthorizedException("Identifiants invalides")

        if (!row[Guards.active]) throw UnauthorizedException("Compte desactive")

        val lockedUntil = row[Guards.lockedUntil]
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw AccountLockedException("Compte verrouille suite a trop d'echecs, reessayez plus tard")
        }

        val guardId = row[Guards.id].value
        if (!BCrypt.checkpw(pin, row[Guards.pinHash])) {
            val attempts = row[Guards.failedAttempts] + 1
            Guards.update({ Guards.id eq guardId }) {
                it[failedAttempts] = attempts
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    it[Guards.lockedUntil] = Instant.now().plus(LOCK_DURATION)
                }
            }
            throw UnauthorizedException("Identifiants invalides")
        }

        Guards.update({ Guards.id eq guardId }) {
            it[failedAttempts] = 0
            it[Guards.lockedUntil] = null
        }

        val token = generateToken()
        val now = Instant.now()
        val expiresAt = now.plus(SESSION_DURATION)
        Sessions.insertAndGetId {
            it[Sessions.token] = token
            it[Sessions.guardId] = guardId
            it[createdAt] = now
            it[Sessions.expiresAt] = expiresAt
        }

        Triple(token, AuthedGuard(guardId, row[Guards.badge], row[Guards.fullName], row[Guards.role]), expiresAt)
    }

    suspend fun authenticate(token: String): AuthedGuard = dbQuery {
        val session = Sessions.selectAll().where { Sessions.token eq token }.singleOrNull()
            ?: throw UnauthorizedException("Session invalide")

        if (session[Sessions.expiresAt].isBefore(Instant.now())) {
            // Utilisation de la table directement pour eviter les erreurs de resolution sur 'it'
            Sessions.deleteWhere { Sessions.token eq token }
            throw UnauthorizedException("Session expiree")
        }

        val guard = Guards.selectAll().where { Guards.id eq session[Sessions.guardId] }.single()
        if (!guard[Guards.active]) throw UnauthorizedException("Compte desactive")

        AuthedGuard(guard[Guards.id].value, guard[Guards.badge], guard[Guards.fullName], guard[Guards.role])
    }

    suspend fun logout(sessionToken: String) = dbQuery {
        // Utilisation de la table directement pour eviter les erreurs de resolution sur 'it'
        Sessions.deleteWhere { Sessions.token eq sessionToken }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
