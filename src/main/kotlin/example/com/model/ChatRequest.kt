package example.com.model
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.time.Period

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

fun calculateAge(birthDate: LocalDate): Int {
    val currentDate = LocalDate.now()
    return Period.between(birthDate, currentDate).years
}

fun getPrompt(filePath: String = "src/main/kotlin/example/com/static/prompt_template.txt", user: OAuthUser): String {
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