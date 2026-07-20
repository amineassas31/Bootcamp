package rondes.routes

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rondes.db.GuardRole
import rondes.service.AuthService
import rondes.service.RoomService
import rondes.service.SupervisionHub

/**
 * WebSocket pousse l'etat des salles au PC de supervision a chaque scan.
 * Le jeton est passe en query param car l'API WebSocket du navigateur ne
 * permet pas d'ajouter un en-tete Authorization a la poignee de main.
 */
fun Route.supervisionRoutes() {
    webSocket("/ws/supervision") {
        val token = call.request.queryParameters["token"]
        val guard = try {
            if (token == null) throw IllegalStateException()
            AuthService.authenticate(token)
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentification requise"))
            return@webSocket
        }
        if (guard.role != GuardRole.CHEF_DE_POSTE && guard.role != GuardRole.DIRECTION) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Role insuffisant"))
            return@webSocket
        }

        SupervisionHub.register(this)
        try {
            val json = Json { ignoreUnknownKeys = true }
            send(Frame.Text(json.encodeToString(RoomService.listStatuses())))
            for (frame in incoming) {
                // pas d'interaction client->serveur attendue sur ce canal, on ignore les frames entrantes
            }
        } finally {
            SupervisionHub.unregister(this)
        }
    }
}
