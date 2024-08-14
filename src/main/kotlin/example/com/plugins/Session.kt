package example.com.plugins

import io.ktor.server.sessions.*
import io.ktor.server.application.*

fun Application.configureSessions() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }
}
