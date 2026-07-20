package rondes.service

import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import rondes.db.GuardRole
import rondes.db.Guards
import rondes.db.dbQuery
import rondes.model.GuardDto

object GuardService {

    suspend fun list(): List<GuardDto> = dbQuery {
        Guards.selectAll().map {
            GuardDto(
                id = it[Guards.id].value,
                badge = it[Guards.badge],
                fullName = it[Guards.fullName],
                role = it[Guards.role].name,
                active = it[Guards.active],
            )
        }
    }

    suspend fun create(badge: String, fullName: String, pin: String, role: GuardRole): Int = dbQuery {
        val existing = Guards.selectAll().where { Guards.badge eq badge }.singleOrNull()
        if (existing != null) throw ConflictException("Ce badge existe deja")
        Guards.insertAndGetId {
            it[Guards.badge] = badge
            it[Guards.fullName] = fullName
            it[pinHash] = AuthService.hashPin(pin)
            it[Guards.role] = role
        }.value
    }

    suspend fun setActive(guardId: Int, active: Boolean) = dbQuery {
        Guards.selectAll().where { Guards.id eq guardId }.singleOrNull()
            ?: throw NotFoundException("Compte introuvable")
        Guards.update({ Guards.id eq guardId }) { it[Guards.active] = active }
        Unit
    }
}
