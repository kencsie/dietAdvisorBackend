package example.com.plugins

import com.mongodb.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.*
import kotlinx.coroutines.*
import example.com.model.User
import example.com.plugins.UserService

fun Application.configureDatabases() {
    val mongoDatabase = connectToMongoDB()
    val userService = UserService(mongoDatabase)
    routing {
        // Create user
        post("/users") {
            val user = call.receive<User>()
            userService.create(user)?.let { id ->
                call.respond(HttpStatusCode.Created, id)
            } ?: call.respond(HttpStatusCode.Conflict)
        }

        // Read user
        get("/user/{userName}") {
            val userName = call.parameters["userName"] ?: throw IllegalArgumentException("No userName found")
            userService.read(userName)?.let { user ->
                call.respond(user)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
        // Update user
        put("/user/{userName}") {
            val userName = call.parameters["userName"] ?: throw IllegalArgumentException("No userName found")
            val user = call.receive<User>()
            userService.update(userName, user)?.let {
                call.respond(HttpStatusCode.OK)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
        // Delete user
        delete("/user/{userName}") {
            val userName = call.parameters["userName"] ?: throw IllegalArgumentException("No userName found")
            userService.delete(userName)?.let {
                call.respond(HttpStatusCode.OK)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }
}

/**
 * Establishes connection with a MongoDB database.
 *
 * The following configuration properties (in application.yaml/application.conf) can be specified:
 * * `db.mongo.user` username for your database
 * * `db.mongo.password` password for the user
 * * `db.mongo.host` host that will be used for the database connection
 * * `db.mongo.port` port that will be used for the database connection
 * * `db.mongo.maxPoolSize` maximum number of connections to a MongoDB server
 * * `db.mongo.database.name` name of the database
 *
 * IMPORTANT NOTE: in order to make MongoDB connection working, you have to start a MongoDB server first.
 * See the instructions here: https://www.mongodb.com/docs/manual/administration/install-community/
 * all the paramaters above
 *
 * @returns [MongoDatabase] instance
 * */
fun Application.connectToMongoDB(): MongoDatabase {
    val user = environment.config.tryGetString("db.mongo.user")
    val password = environment.config.tryGetString("db.mongo.password")
    val host = environment.config.tryGetString("db.mongo.host") ?: "192.168.0.163"
    val port = environment.config.tryGetString("db.mongo.port") ?: "27017"
    val maxPoolSize = environment.config.tryGetString("db.mongo.maxPoolSize")?.toInt() ?: 20
    val databaseName = environment.config.tryGetString("db.mongo.database.name") ?: "dietAdvisor"

    val credentials = user?.let { userVal -> password?.let { passwordVal -> "$userVal:$passwordVal@" } }.orEmpty()
    val uri = "mongodb://$credentials$host:$port/?maxPoolSize=$maxPoolSize&w=majority"

    val mongoClient = MongoClients.create(uri)
    val database = mongoClient.getDatabase(databaseName)

    environment.monitor.subscribe(ApplicationStopped) {
        mongoClient.close()
    }

    return database
}
