package example.com.model
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.time.Period

@Serializable
data class Message(
    val role: String,
    val content: String,
    val refusal: String? = null // Optional field
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val top_p: Double,
    val temperature: Double,
    val frequency_penalty: Double,
    val presence_penalty: Double,
    val repetition_penalty: Double,
    val top_k: Double,
)

@Serializable
data class ChatResponse(
    val id: String,
    val model: String,
    val `object`: String,
    val created: Long,
    val choices: List<Choice>,
    val system_fingerprint: String,
    val usage: Usage
)

@Serializable
data class Choice(
    val logprobs: LogProbs? = null,
    val finish_reason: String,
    val index: Int,
    val message: Message
)

@Serializable
data class LogProbs(
    val tokens: List<String>? = null,  // List of tokens, can be null
    val token_probabilities: List<Float>? = null,  // Probabilities for each token
    val top_logprobs: Map<String, Float>? = null  // Top probabilities for tokens
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
fun calculateAge(birthDate: LocalDate): Int {
    val currentDate = LocalDate.now()
    return Period.between(birthDate, currentDate).years
}

fun getPrompt(user: OAuthUser): String {
    var filePath : String = ""
    if(user.personalInfo.language == Language.Chinese){
        filePath = "src/main/kotlin/example/com/static/prompt_template_zh.txt"
    }
    else if(user.personalInfo.language == Language.English){
        filePath = "src/main/kotlin/example/com/static/prompt_template_en.txt"
    }
    var prompt: String = File(filePath).readText()
    val birthDate = LocalDate.parse(user.personalInfo.birthDate)
    val age = calculateAge(birthDate)
    
    return prompt
        .replace("{age}", age.toString())
        .replace("{weight}", user.bodyMeasurements.weight.toString())
        .replace("{height}", user.bodyMeasurements.height.toString())
        .replace("{physical_activity_multiplier}", user.bodyMeasurements.physicalActivity.toString())
        .replace("{user_intake_calorie}", user.lastMeal?.calorie.toString())
        .replace("{user_intake_protein}", user.lastMeal?.protein.toString())
        .replace("{user_intake_fat}", user.lastMeal?.fat.toString())
        .replace("{user_intake_carb}", user.lastMeal?.carb.toString())
        .replace("{weight_option}", user.dietaryInfo.dietaryGoal.toString())
        .replace("{weight_option_amount}", user.dietaryInfo.dietaryGoalAmount.toString())
        .replace("{TDEE}", user.dietaryInfo.TDEE.toString())
}