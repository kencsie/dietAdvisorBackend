package example.com.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.webjars.*
import io.ktor.server.sessions.*

fun Application.configureRouting() {
    routing {
        get("login/complete") {
            val userSession = call.sessions.get<UserSession>()
            call.respondText(
                contentType = ContentType.parse("text/html"),
                    text = """
                <h3>Login Complete!</h3>
                <p>Your access token is</p>
                <p>${userSession?.token}</p>
                """.trimIndent()
            )
        }
    }
}
