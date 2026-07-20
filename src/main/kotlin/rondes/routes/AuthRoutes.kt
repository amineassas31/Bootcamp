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
}
