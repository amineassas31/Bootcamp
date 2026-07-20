package rondes.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import rondes.db.GuardRole
import rondes.service.AuthService
import rondes.service.AuthedGuard
import rondes.service.ForbiddenException
import rondes.service.UnauthorizedException

suspend fun ApplicationCall.authedGuard(): AuthedGuard {
    val header = request.header("Authorization")
        ?: throw UnauthorizedException("En-tete Authorization manquant")
    val token = header.removePrefix("Bearer ").trim()
    if (token.isEmpty()) throw UnauthorizedException("Jeton manquant")
    return AuthService.authenticate(token)
}

fun AuthedGuard.requireRole(vararg roles: GuardRole) {
    if (role !in roles) throw ForbiddenException("Role insuffisant pour cette action")
}
