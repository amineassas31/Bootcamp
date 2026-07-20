package rondes.service

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Registre des sessions WebSocket ouvertes par le PC de supervision.
 * Diffuse le nouvel etat des salles a chaque scan pour un affichage
 * sans action manuelle (pas de bouton "rafraichir").
 */
object SupervisionHub {

    private val sessions = mutableSetOf<WebSocketSession>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun register(session: WebSocketSession) {
        mutex.withLock { sessions.add(session) }
    }

    suspend fun unregister(session: WebSocketSession) {
        mutex.withLock { sessions.remove(session) }
    }

    suspend fun broadcastRoomsUpdated() {
        val statuses = RoomService.listStatuses()
        val payload = json.encodeToString(statuses)
        val dead = mutableListOf<WebSocketSession>()
        val snapshot = mutex.withLock { sessions.toList() }
        for (session in snapshot) {
            try {
                session.send(Frame.Text(payload))
            } catch (e: Exception) {
                dead.add(session)
            }
        }
        if (dead.isNotEmpty()) {
            mutex.withLock { sessions.removeAll(dead.toSet()) }
        }
    }
}
