package example.com.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.webjars.*
import io.ktor.server.sessions.*
import io.ktor.http.content.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import example.com.model.ChatRequest
import example.com.model.Choice
import example.com.model.ChatResponse
import example.com.model.Message
import example.com.model.getPrompt
import example.com.model.OAuthUser

fun Application.configureRouting(client:HttpClient) {
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

        post("/yolo") {
            val multipart = call.receiveMultipart()
            var imageBytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        imageBytes = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            imageBytes?.let { bytes ->
                val response:HttpResponse = client.submitFormWithBinaryData(
                    url = "http://${System.getenv("FLASK_HOST")}:${System.getenv("FLASK_PORT")}/yolo",
                    formData = formData {
                        append("description", "Ktor logo")
                        append("image", bytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/png")
                            append(HttpHeaders.ContentDisposition, "filename=\"pic.png\"")
                        })
                    })
                    
                var body: String
                if(response.status == HttpStatusCode.OK)
                {
                    body = response.body()
                    call.respond(HttpStatusCode.OK, body)
                }
            } ?: call.respond(HttpStatusCode.BadRequest, "No image uploaded")
        }

        post("/calorie") {
            val multipart = call.receiveMultipart()
            var imageBytes: ByteArray? = null
            var jsonContent: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        if (part.contentType?.match("image/*") == true) {
                            imageBytes = part.streamProvider().readBytes()
                        }
                    }
                    is PartData.FormItem -> {
                        jsonContent = part.value
                    }

                    else -> {}
                }
                part.dispose()
            }

            // Processing the image and json data
            imageBytes?.let { bytes ->
                jsonContent?.let { json ->
                    // Example of using both image and JSON data
                    val response: HttpResponse = client.submitFormWithBinaryData(
                        url = "http://${System.getenv("FLASK_HOST")}:${System.getenv("FLASK_PORT")}/calorie",
                        formData = formData {
                            append("image", bytes, Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"pic.png\"")
                            })
                            append("data", json, Headers.build {
                                append(HttpHeaders.ContentType, "application/json")
                                append(HttpHeaders.ContentDisposition, "filename=\"data.json\"")
                            })
                        })

                    if (response.status == HttpStatusCode.OK) {
                        var body: String = response.body()
                        call.respond(HttpStatusCode.OK, body)
                    }
                } ?: call.respond(HttpStatusCode.BadRequest, "No JSON data uploaded")
            } ?: call.respond(HttpStatusCode.BadRequest, "No image uploaded")
        }

        post("/recommendation"){
            val user : OAuthUser = call.receive<OAuthUser>()
            var prompt = getPrompt(user)
            val token = System.getenv("OPENROUTER_BEARER_TOKEN")
        
            //val model: String = "openai/gpt-4o-2024-08-06"
            val model: String = "openai/gpt-4o-mini"
            val response: HttpResponse = client.post("https://openrouter.ai/api/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", token)
                setBody(ChatRequest(
                    model=model,
                    messages = listOf(
                        Message(
                            role = "user",
                            content = prompt
                        )
                    ),
                    top_p = 1.0,
                    temperature = 0.7,
                    frequency_penalty = 0.0,
                    presence_penalty = 0.0,
                    repetition_penalty = 1.0,
                    top_k = 0.0,
                ))
            }

            if (response.status == HttpStatusCode.OK) {
                val responseBody: ChatResponse = response.body()
                val responseContent: Choice = responseBody.choices[0]
                call.respond(HttpStatusCode.OK, responseContent)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Failed to retrieve data from external service")
            }
        }
    }
}
