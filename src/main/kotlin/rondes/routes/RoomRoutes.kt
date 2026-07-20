package rondes.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import rondes.db.GuardRole
import rondes.model.RoomCreateRequest
import rondes.model.RoomUpdateRequest
import rondes.service.RoomService

fun Route.roomRoutes() {
    route("/api/rooms") {
        get {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            call.respond(RoomService.listStatuses())
        }
        post {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            val req = call.receive<RoomCreateRequest>()
            val id = RoomService.createRoom(req)
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }
        put("/{id}") {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            val id = call.parameters["id"]!!.toInt()
            val req = call.receive<RoomUpdateRequest>()
            RoomService.updateRoom(id, req)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
