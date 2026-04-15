package SecureDataSharing.storage;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

/**
 * Service for managing user sessions (persistent login).
 * Saves the logged-in username so users stay logged in after closing the app.
 */
public class SessionManager {
    private final MongoCollection<Document> sessionsCollection;

    public SessionManager() {
        this.sessionsCollection = MongoManager.getInstance().getCollection("sessions", "sessions");
    }

    /**
     * Saves the current session (logged-in username).
     * @param username The username of the logged-in user
     */
    public void saveSession(String username) {
        Document sessionDoc = new Document("_id", "active_session")
                .append("username", username)
                .append("timestamp", System.currentTimeMillis());

        sessionsCollection.deleteMany(new Document());
        sessionsCollection.insertOne(sessionDoc);
    }

    /**
     * Loads the saved session and returns the username.
     * @return The username if a session exists, null otherwise
     */
    public String loadSession() {
        Document sessionDoc = sessionsCollection.find().first();
        if (sessionDoc == null) {
            return null;
        }
        return sessionDoc.getString("username");
    }

    /**
     * Clears the current session (logs out the user).
     */
    public void clearSession() {
        sessionsCollection.deleteMany(new Document());
    }

    /**
     * Checks if a session exists.
     * @return true if a session exists, false otherwise
     */
    public boolean hasSession() {
        return sessionsCollection.countDocuments() > 0;
    }
}
