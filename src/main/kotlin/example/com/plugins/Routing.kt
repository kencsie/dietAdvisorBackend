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
                    url = "http://100.108.170.70:5000/yolo",
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
                        url = "http://100.108.170.70:5000/calorie",
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
    }
}
