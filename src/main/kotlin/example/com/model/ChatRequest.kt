package example.com.model
import kotlinx.serialization.Serializable

enum class Model {
    `gemma2:27b`, `llava:34b-v1.6`, `mistral-nemo:latest`, `llama3.1:latest`
}

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean
)