package SecureDataSharing.storage;

import SecureDataSharing.config.ConfigManager;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoManager {
    private static MongoManager instance;

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final ConfigManager configManager;

    private MongoManager() {
        this.configManager = ConfigManager.getInstance();

        String uri = configManager.getProperty("mongodb.uri");
        if (uri == null || uri.trim().isEmpty()) {
            throw new RuntimeException("MongoDB URI is missing in config.json");
        }

        String databaseName = configManager.getProperty("mongodb.database", "secure_data_sharing");

        ServerApi serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .serverApi(serverApi)
                .build();

        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(databaseName);
    }

    public static synchronized MongoManager getInstance() {
        if (instance == null) {
            instance = new MongoManager();
        }
        return instance;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getCollection(String configKey, String defaultName) {
        String name = configManager.getProperty("mongodb.collections." + configKey, defaultName);
        return database.getCollection(name);
    }

    public void close() {
        mongoClient.close();
    }
}
