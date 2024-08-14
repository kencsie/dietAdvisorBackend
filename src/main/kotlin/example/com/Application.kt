package example.com

import example.com.plugins.*
import io.ktor.server.application.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun provideHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json() // Configures Kotlinx JSON serialization for the client
        }
    }
}

fun Application.module() {
    val client = provideHttpClient()
    configureSerialization()
    configureHTTP()
    configureSecurity(client)
    configureDatabases(client)
    configureRouting()
    configureSessions()
}
