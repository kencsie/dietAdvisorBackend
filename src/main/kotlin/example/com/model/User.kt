package example.com.model
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.bson.Document
import kotlinx.serialization.json.Json

enum class Gender {
    Female, Male, `Non-binary`
}

enum class DietaryGoal {
    `Lose weight`, `Stay the same`, `Gain weight`
}

enum class Language {
    Chinese, English
}

@Serializable
data class User(
    val userName: String,
    val password: String,
    val birthDate: String, //Need to check format
    val gender: Gender,
    val weight: Int,
    val height: Int,
    val dietaryGoal: DietaryGoal,
    val language: Language,
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): User = json.decodeFromString(document.toJson())
    }
}