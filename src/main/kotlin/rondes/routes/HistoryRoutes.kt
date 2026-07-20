package rondes.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import rondes.db.GuardRole
import rondes.service.HistoryService
import java.time.Instant

fun Route.historyRoutes() {
    get("/api/history") {
        val guard = call.authedGuard()
        val requestedGuardId = call.request.queryParameters["guardId"]?.toIntOrNull()
        val roomId = call.request.queryParameters["roomId"]?.toIntOrNull()
        val from = call.request.queryParameters["from"]?.let { Instant.parse(it) }
        val to = call.request.queryParameters["to"]?.let { Instant.parse(it) }

        // Un gardien ne consulte que son propre historique ; seuls chef de poste / direction voient tout.
        val effectiveGuardId = if (guard.role == GuardRole.GARDIEN) guard.id else requestedGuardId

        call.respond(HistoryService.search(roomId, effectiveGuardId, from, to))
    }
}
