package SecureDataSharing.storage;

import SecureDataSharing.access.Attribute;
import SecureDataSharing.auth.User;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for persisting and loading users from MongoDB.
 */
public class UserStorageService {
    private final MongoCollection<Document> usersCollection;

    public UserStorageService() {
        this.usersCollection = MongoManager.getInstance().getCollection("users", "users");
    }

    /**
     * Saves all users to MongoDB.
     */
    public void saveUsers(Map<String, User> users) throws IOException {
        usersCollection.deleteMany(new Document());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (User user : users.values()) {
            Document userDoc = new Document("username", user.getUsername())
                    .append("passwordHash", user.getPasswordHash())
                    .append("email", user.getEmail() != null ? user.getEmail() : "")
                    .append("mfaEnabled", user.isMfaEnabled())
                    .append("mfaSecret", user.getMfaSecret() != null ? user.getMfaSecret() : "");

            if (user.getRegistrationDate() != null) {
                userDoc.append("registrationDate", sdf.format(user.getRegistrationDate()));
            }

            List<Document> attributes = new ArrayList<>();
            for (Attribute attr : user.getAttributes()) {
                attributes.add(new Document("name", attr.getName())
                        .append("value", attr.getValue()));
            }
            userDoc.append("attributes", attributes);

            try {
                PublicKey publicKey = user.getPublicKey();
                PrivateKey privateKey = user.getPrivateKey();

                userDoc.append("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
                userDoc.append("privateKey", Base64.getEncoder().encodeToString(privateKey.getEncoded()));
            } catch (Exception e) {
                System.err.println("Error encoding keys for user " + user.getUsername() + ": " + e.getMessage());
            }

            usersCollection.insertOne(userDoc);
        }
    }

    /**
     * Loads all users from MongoDB.
     */
    public void loadUsers(Map<String, User> users) throws IOException {
        users.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Document userDoc : usersCollection.find()) {
            String username = userDoc.getString("username");
            String passwordHash = userDoc.getString("passwordHash");

            try {
                String publicKeyStr = userDoc.getString("publicKey");
                String privateKeyStr = userDoc.getString("privateKey");

                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
                byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
                PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

                User user = new User(username, passwordHash, new KeyPair(publicKey, privateKey));

                String email = userDoc.getString("email");
                if (email != null && !email.isEmpty()) {
                    user.setEmail(email);
                }

                Boolean mfaEnabled = userDoc.getBoolean("mfaEnabled");
                if (mfaEnabled != null) {
                    user.setMfaEnabled(mfaEnabled);
                }

                String mfaSecret = userDoc.getString("mfaSecret");
                if (mfaSecret != null && !mfaSecret.isEmpty()) {
                    user.setMfaSecret(mfaSecret);
                }

                List<Document> attributes = (List<Document>) userDoc.get("attributes");
                if (attributes != null) {
                    for (Document attrDoc : attributes) {
                        Attribute attr = new Attribute(attrDoc.getString("name"), attrDoc.getString("value"));
                        user.addAttribute(attr);
                    }
                }

                String registrationDateStr = userDoc.getString("registrationDate");
                if (registrationDateStr != null && !registrationDateStr.isEmpty()) {
                    try {
                        setField(user, "registrationDate", sdf.parse(registrationDateStr));
                    } catch (ParseException e) {
                        System.err.println("Error parsing registration date for user " + username + ": " + e.getMessage());
                    }
                }

                users.put(username, user);
            } catch (Exception e) {
                System.err.println("Error loading user " + username + ": " + e.getMessage());
            }
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
