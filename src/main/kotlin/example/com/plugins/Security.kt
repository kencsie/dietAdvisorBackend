package example.com.plugins

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.*
import io.ktor.client.request.*
import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.client.call.*


fun Application.configureSecurity(hostClient:HttpClient) {
    install(Authentication) {
        oauth("auth-oauth-google") {
            urlProvider = { "http://diet.kencs.net/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID"),
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile")
                )
            }
            client = hostClient
        }
    }
    routing {
        authenticate("auth-oauth-google") {
            get("login") {
                call.respondRedirect("/callback")
            }

            get("/callback")  {
                val currentPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                if (currentPrincipal != null) {
                    val accessToken = currentPrincipal.accessToken
                    call.sessions.set(UserSession(accessToken))

                    //println("${getPersonalInfo(hostClient, accessToken)}\n\n\n")
                    call.respondRedirect("/login/complete")
                } else {
                    call.respondRedirect("/home")
                }
            }
        }
    }          
}


suspend fun getPersonalInfo(
    httpClient: HttpClient,
    userToken: String?
): ApiResponse {
    val response:HttpResponse = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $userToken")
        }
    }
    var body: GoogleUserInfo = GoogleUserInfo("","","", "", "")
    if(response.status == HttpStatusCode.OK)
    {
        body = response.body()
    }

    return ApiResponse(
        statusCode = response.status,
        body = body
    )
}

data class UserSession(val token: String)

data class ApiResponse(
    val statusCode: HttpStatusCode,
    val body: GoogleUserInfo
)

@Serializable
data class GoogleUserInfo(
    val id: String,
    val name: String,
    val given_name: String,
    val family_name: String? = null,
    val picture: String
)

