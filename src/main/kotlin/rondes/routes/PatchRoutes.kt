package rondes.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import rondes.db.GuardRole
import rondes.model.PatchEnrollRequest
import rondes.service.RoomService
import rondes.service.SupervisionHub

fun Route.patchRoutes() {
    route("/api/patches") {
        get {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            call.respond(RoomService.listPatches())
        }
        post("/enroll") {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            val req = call.receive<PatchEnrollRequest>()
            val patch = RoomService.enrollPatch(req.tagUid, req.roomId)
            SupervisionHub.broadcastRoomsUpdated()
            call.respond(HttpStatusCode.Created, patch)
        }
        post("/{id}/report-damaged") {
            call.authedGuard() // n'importe quel compte authentifie peut signaler un patch endommage
            val id = call.parameters["id"]!!.toInt()
            RoomService.setPatchDamaged(id, damaged = true)
            SupervisionHub.broadcastRoomsUpdated()
            call.respond(HttpStatusCode.NoContent)
        }
        post("/{id}/clear-damaged") {
            val guard = call.authedGuard()
            guard.requireRole(GuardRole.CHEF_DE_POSTE, GuardRole.DIRECTION)
            val id = call.parameters["id"]!!.toInt()
            RoomService.setPatchDamaged(id, damaged = false)
            SupervisionHub.broadcastRoomsUpdated()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
