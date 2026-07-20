package rondes.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import rondes.model.ScanRequest
import rondes.service.ScanService
import rondes.service.SupervisionHub

fun Route.scanRoutes() {
    post("/api/scan") {
        val guard = call.authedGuard()
        val req = call.receive<ScanRequest>()
        val response = ScanService.recordScan(req.tagUid, guard, req.scannedAt)
        SupervisionHub.broadcastRoomsUpdated()
        call.respond(HttpStatusCode.Created, response)
    }
}
