package example.com.plugins

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.bson.Document
import org.bson.types.ObjectId
import example.com.model.OAuthUser


class UserService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("users")
        collection = database.getCollection("users")
    }

    // Create new user
    suspend fun create(user: OAuthUser): String? = withContext(Dispatchers.IO) {
        val exists = collection.countDocuments(Document("personalInfo.userName", user.personalInfo.userName)) > 0
        if (exists) {
            // If the user already exists, return null or handle as needed
            return@withContext null
        } else {
            // Convert the user to a BSON document
            val doc = user.toDocument()
            // Insert the new user document
            collection.insertOne(doc)
            // Return the newly created user's ID
            return@withContext doc["_id"].toString()
        }
    }

    // Read a user
    suspend fun read(userID: String): OAuthUser? = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("personalInfo.userID", userID)).first()?.let(OAuthUser::fromDocument)
    }

    // Update a user
    suspend fun update(userID: String, user: OAuthUser): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndReplace(Filters.eq("personalInfo.userID", userID), user.toDocument())
    }

    // Delete a user
    suspend fun delete(userID: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(Filters.eq("personalInfo.userID", userID))
    }

    //Check user exist or not
    suspend fun checkUserExists(userID: String): Boolean{
        val userCount = collection.countDocuments(Filters.eq("personalInfo.userID", userID))
        return userCount > 0
    }
}

