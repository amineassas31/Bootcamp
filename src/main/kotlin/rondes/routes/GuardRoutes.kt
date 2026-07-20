package rondes.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import rondes.db.GuardRole
import rondes.model.GuardCreateRequest
import rondes.service.GuardService
import rondes.service.BadRequestException

fun Route.guardRoutes() {
    route("/api/guards") {
        get {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            call.respond(GuardService.list())
        }
        post {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            val req = call.receive<GuardCreateRequest>()
            val role = try {
                GuardRole.valueOf(req.role)
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("Role invalide")
            }
            // seule la direction peut creer des comptes chef de poste / direction
            if (role != GuardRole.GARDIEN) {
                guard.requireRole(GuardRole.DIRECTION)
            }
            val id = GuardService.create(req.badge, req.fullName, req.pin, role)
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }
    }
}
