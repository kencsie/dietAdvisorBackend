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
    suspend fun extractToken(call: ApplicationCall): String =
        call.request.headers["Authorization"]?.split(" ")?.lastOrNull()
            ?: throw IllegalStateException("No access token provided")
    
    suspend fun validateAndGetPersonalInfo(client: HttpClient, accessToken: String): ApiResponse {
        val personalInfo: ApiResponse = getPersonalInfo(client, accessToken)
        if (personalInfo.statusCode != HttpStatusCode.OK) {
            throw IllegalStateException("Failed to retrieve user info")
        }
        return personalInfo
    }

    val mongoDatabase = connectToMongoDB()
    val userService = UserService(mongoDatabase)
    
    routing {
        post("/user") {
            try {
                val accessToken = extractToken(call)
                val personalInfo = validateAndGetPersonalInfo(client, accessToken)
                
                val userID = personalInfo.body.id
                val userExists: Boolean = userService.checkUserExists(userID)
                if (userExists) {
                    call.respond(HttpStatusCode.Conflict, "User already exists")
                } else {
                    var user = call.receive<OAuthUser>().copy(
                        userID = userID,
                        userName = personalInfo.body.name
                    )
                    userService.create(user)
                    call.respond(HttpStatusCode.Created, "User created successfully")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Error processing request")
            }
        }

        // Read user
        get("/user") {
            try {
                val accessToken = extractToken(call)
                val personalInfo = validateAndGetPersonalInfo(client, accessToken)
                
                if(userService.checkUserExists(personalInfo.body.id)){
                    var user = userService.read(personalInfo.body.id) ?: call.respond(HttpStatusCode.NotFound, "User not found")
                    call.respond(user) 
                }else{
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Error processing request")
            }
        }
        // Update user
        put("/user") {
            try {
                val accessToken = extractToken(call)
                val personalInfo = validateAndGetPersonalInfo(client, accessToken)
                val user = call.receive<OAuthUser>()

                userService.update(personalInfo.body.id, user)?.let {
                    call.respond(HttpStatusCode.OK, "User modified successfully")
                } ?: call.respond(HttpStatusCode.NotFound, "User not found")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Error processing request")
            }
        }
        // Delete user
        delete("/user") {
            try {
                val accessToken = extractToken(call)
                val personalInfo = validateAndGetPersonalInfo(client, accessToken)

                userService.delete(personalInfo.body.id)?.let {
                    call.respond(HttpStatusCode.OK, "User deleted successfully")
                } ?: call.respond(HttpStatusCode.NotFound, "User not found")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Error processing request")
            }
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
