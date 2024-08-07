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
import example.com.model.User


class UserService(private val database: MongoDatabase) {
    var collection: MongoCollection<Document>

    init {
        database.createCollection("users")
        collection = database.getCollection("users")
    }

    // Create new user
    suspend fun create(user: User): String = withContext(Dispatchers.IO) {
        val doc = user.toDocument()
        collection.insertOne(doc)
        doc["_id"].toString()
    }

    // Read a user
    suspend fun read(userName: String): User? = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("userName", userName)).first()?.let(User::fromDocument)
    }

    // Update a user
    suspend fun update(userName: String, user: User): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndReplace(Filters.eq("userName", userName), user.toDocument())
    }

    // Delete a user
    suspend fun delete(userName: String): Document? = withContext(Dispatchers.IO) {
        collection.findOneAndDelete(Filters.eq("userName", userName))
    }
}

