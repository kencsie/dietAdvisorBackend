package example.com

import example.com.plugins.*
import io.ktor.server.application.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun provideHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json() // Configures Kotlinx JSON serialization for the client
        }
        install(HttpTimeout){
            socketTimeoutMillis = 30000
        }
    }
}

fun Application.module() {
    //Need to configure CORS
    //https://milosgarunovic.com/posts/ktor-cors/
    install(CORS) {
        allowHost("diet.kencs.net")

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowNonSimpleContentTypes = true
    }
    
    val client = provideHttpClient()
    configureSerialization()
    configureHTTP()
    configureSecurity(client)
    configureDatabases(client)
    configureRouting(client)
    configureSessions()
}
