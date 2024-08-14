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
import example.com.model.OAuthUser
import example.com.plugins.UserService
import io.ktor.server.auth.*
import io.ktor.client.*

fun Application.configureDatabases(client: HttpClient) {
    val mongoDatabase = connectToMongoDB()
    val userService = UserService(mongoDatabase)
    
    routing {
        post("/user") {
            val accessToken = call.request.headers["Authorization"]?.split(" ")?.lastOrNull()
            if (accessToken == null) {
                call.respond(HttpStatusCode.BadRequest, "No access token provided")
                return@post
            }
            
            val personalInfo: ApiResponse = getPersonalInfo(client, accessToken)

            if (personalInfo.statusCode != HttpStatusCode.OK) {
                call.respond(HttpStatusCode.Unauthorized, "Failed to retrieve user info")
                return@post
            }
            
            var userID = personalInfo.body.id
            // Check if the user ID is already in the database to avoid duplicates
            val userExists: Boolean = userService.checkUserExists(userID)
            if (userExists) {
                call.respond(HttpStatusCode.Conflict, "User already exists")
            } else {
                // Create user in the database
                var user = call.receive<OAuthUser>().copy(
                    userID = userID,  // New userID
                    userName = personalInfo.body.name  // New userName
                )

                userService.create(user)
                call.respond(HttpStatusCode.Created, "User created successfully")
            }
        }

        // Read user
        get("/user/{userName}") {
            val accessToken = call.request.headers["Authorization"]?.split(" ")?.lastOrNull()
            if (accessToken == null) {
                call.respond(HttpStatusCode.BadRequest, "No access token provided")
                return@get
            }

            val personalInfo: ApiResponse = getPersonalInfo(client, accessToken)
            if (personalInfo.statusCode != HttpStatusCode.OK) {
                call.respond(HttpStatusCode.Unauthorized, "Failed to retrieve user info")
                return@get
            }

            val userName = call.parameters["userName"] ?: throw IllegalArgumentException("No userName found")
            if (userName != personalInfo.body.name) {
                call.respond(HttpStatusCode.Unauthorized, "Username is not the same")
                return@get
            }
            
            userService.read(userName)?.let { user ->
                call.respond(user)
            } ?: call.respond(HttpStatusCode.NotFound, "User not found")
        }
        // Update user
        put("/user/{userName}") {
            val accessToken = call.request.headers["Authorization"]?.split(" ")?.lastOrNull()
            if (accessToken == null) {
                call.respond(HttpStatusCode.Unauthorized, "No access token provided")
                return@put
            }
            
            val personalInfo: ApiResponse = getPersonalInfo(client, accessToken)
            if (personalInfo.statusCode != HttpStatusCode.OK) {
                call.respond(HttpStatusCode.Unauthorized, "Failed to retrieve user info")
                return@put
            }

            val userName = call.parameters["userName"] ?: throw IllegalArgumentException("No userName found")
            if (userName != personalInfo.body.name) {
                call.respond(HttpStatusCode.Unauthorized, "Username is not the same")
                return@put
            }
            
            val user = call.receive<OAuthUser>()
            userService.update(userName, user)?.let {
                call.respond(HttpStatusCode.OK, "User modified successfully")
            } ?: call.respond(HttpStatusCode.NotFound, "User not found")
        }
        // Delete user
        delete("/user/{userName}") {
            val accessToken = call.request.headers["Authorization"]?.split(" ")?.lastOrNull()
            if (accessToken == null) {
                call.respond(HttpStatusCode.Unauthorized, "No access token provided")
                return@delete
            }

            val personalInfo: ApiResponse = getPersonalInfo(client, accessToken)
            if (personalInfo.statusCode != HttpStatusCode.OK) {
                call.respond(HttpStatusCode.Unauthorized, "Failed to retrieve user info")
                return@delete
            }

            val userName = call.parameters["userName"] ?: throw IllegalArgumentException("No userName found")
            if (userName != personalInfo.body.name) {
                call.respond(HttpStatusCode.Unauthorized, "Username is not the same")
                return@delete
            }
            

            userService.delete(userName)?.let {
                call.respond(HttpStatusCode.OK, "User deleted successfully")
            } ?: call.respond(HttpStatusCode.NotFound, "User not found")
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
