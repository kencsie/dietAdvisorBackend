package example.com.model
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.bson.Document
import kotlinx.serialization.json.Json

enum class Gender {
    Male, Female, `Non-binary`
}

enum class DietaryGoal {
    `Gain weight`, `Stay the same`, `Lose weight`
}

enum class Language {
    English, Chinese
}

@Serializable
data class NutritionalInfo(
    val carb: Double,
    val protein: Double,
    val fat: Double,
    val calorie: Double
)

@Serializable
data class IntakeEntry(
    val date: String,
    val nutritionalInfo: NutritionalInfo
)

@Serializable
data class PersonalInfo(
    val userID: String,
    val userName: String, // From Security.getPersonalInfo()
    val birthDate: String, // Need to check format
    val gender: Gender,
    val language: Language
)

@Serializable
data class BodyMeasurements(
    val weight: Double,
    val height: Double,
    val physicalActivity: Double
)

@Serializable
data class DietaryInfo(
    val dietaryGoal: DietaryGoal,
    val dietaryGoalAmount: Double,
    val TMR: Double,
    val TDEE: Double
)

@Serializable
data class OAuthUser(
    val personalInfo: PersonalInfo,
    val bodyMeasurements: BodyMeasurements,
    val dietaryInfo: DietaryInfo,
    val intakeHistory: List<IntakeEntry> = listOf(),
    val lastMeal: NutritionalInfo? = null
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): OAuthUser = json.decodeFromString(document.toJson())
    }
}