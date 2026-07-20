package rondes.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import rondes.model.LoginRequest
import rondes.model.LoginResponse
import rondes.service.AuthService

fun Route.authRoutes() {
    post("/api/auth/login") {
        val req = call.receive<LoginRequest>()
        val (token, guard, expiresAt) = AuthService.login(req.badge, req.pin)
        call.respond(
            HttpStatusCode.OK,
            LoginResponse(token = token, guardName = guard.fullName, role = guard.role.name, expiresAt = expiresAt.toString()),
        )
    }

    post("/api/auth/logout") {
        val guard = call.authedGuard()
        // On recupere le token depuis l'en-tete via authedGuard, 
        // mais pour supprimer la session il nous faut le token brut.
        val authHeader = call.request.headers["Authorization"]
        val token = authHeader?.removePrefix("Bearer ")?.trim()
        if (token != null) {
            AuthService.logout(token)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
