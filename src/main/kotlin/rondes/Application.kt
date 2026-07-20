package rondes

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import rondes.db.DatabaseFactory
import rondes.model.ErrorResponse
import rondes.routes.authRoutes
import rondes.routes.guardRoutes
import rondes.routes.historyRoutes
import rondes.routes.patchRoutes
import rondes.routes.roomRoutes
import rondes.routes.scanRoutes
import rondes.routes.supervisionRoutes
import rondes.service.AccountLockedException
import rondes.service.BadRequestException
import rondes.service.ConflictException
import rondes.service.ForbiddenException
import rondes.service.NotFoundException
import rondes.service.UnauthorizedException
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    Seed.run()

    install(ContentNegotiation) { json() }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }

    install(CallLogging)

    install(CORS) {
        anyHost() // POC : dashboard/scan servis depuis le meme serveur ou un tunnel de demo ; a restreindre en prod
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowHeader("Authorization")
        allowHeader("Content-Type")
    }

    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(cause.message ?: "Non authentifie"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Acces refuse"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Introuvable"))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse(cause.message ?: "Conflit"))
        }
        exception<AccountLockedException> { call, cause ->
            call.respond(HttpStatusCode.Locked, ErrorResponse(cause.message ?: "Compte verrouille"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Requete invalide"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Erreur non geree", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Erreur interne"))
        }
    }

    routing {
        staticResources("/", "static")
        authRoutes()
        scanRoutes()
        roomRoutes()
        patchRoutes()
        historyRoutes()
        guardRoutes()
        supervisionRoutes()
    }
}
