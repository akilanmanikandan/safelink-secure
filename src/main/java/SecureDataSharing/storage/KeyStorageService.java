package SecureDataSharing.storage;

import SecureDataSharing.crypto.KeyManager;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Service for persisting and loading keys from MongoDB.
 */
public class KeyStorageService {
    private final MongoCollection<Document> keysCollection;

    public KeyStorageService() {
        this.keysCollection = MongoManager.getInstance().getCollection("keys", "keys");
    }

    /**
     * Saves all keys to MongoDB as a single document.
     */
    public void saveKeys(Map<String, KeyManager.FileKeyEntry> fileKeys,
                        Map<String, KeyManager.ReEncryptionKeyEntry> reEncryptionKeys) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<Document> fileKeysArray = new ArrayList<>();
        for (Map.Entry<String, KeyManager.FileKeyEntry> entry : fileKeys.entrySet()) {
            KeyManager.FileKeyEntry keyEntry = entry.getValue();
            if (keyEntry.isExpired()) {
                continue;
            }

            Document keyDoc = new Document("fileId", entry.getKey())
                    .append("ownerPublicKey", Base64.getEncoder().encodeToString(keyEntry.getOwnerPublicKey().getEncoded()))
                    .append("encryptedAESKey", Base64.getEncoder().encodeToString(keyEntry.getEncryptedAESKey()));

            Date expiryDate = keyEntry.getExpiryDate();
            if (expiryDate != null) {
                keyDoc.append("expiryDate", sdf.format(expiryDate));
            }
            fileKeysArray.add(keyDoc);
        }

        List<Document> reKeysArray = new ArrayList<>();
        for (Map.Entry<String, KeyManager.ReEncryptionKeyEntry> entry : reEncryptionKeys.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) {
                continue;
            }

            KeyManager.ReEncryptionKeyEntry reKeyEntry = entry.getValue();
            if (reKeyEntry.isExpired() || reKeyEntry.isRevoked()) {
                continue;
            }

            Document reKeyDoc = new Document("fileId", parts[0])
                    .append("requesterUsername", parts[1])
                    .append("reEncryptionKey", Base64.getEncoder().encodeToString(reKeyEntry.getReEncryptionKey()))
                    .append("revoked", reKeyEntry.isRevoked());

            Date expiryDate = reKeyEntry.getExpiryDate();
            if (expiryDate != null) {
                reKeyDoc.append("expiryDate", sdf.format(expiryDate));
            }
            reKeysArray.add(reKeyDoc);
        }

        Document keysDoc = new Document("_id", "keys_store")
                .append("fileKeys", fileKeysArray)
                .append("reEncryptionKeys", reKeysArray);

        keysCollection.deleteMany(new Document());
        keysCollection.insertOne(keysDoc);
    }

    /**
     * Loads all keys from MongoDB.
     */
    public void loadKeys(Map<String, KeyManager.FileKeyEntry> fileKeys,
                        Map<String, KeyManager.ReEncryptionKeyEntry> reEncryptionKeys) throws IOException {
        fileKeys.clear();
        reEncryptionKeys.clear();

        Document keysDoc = keysCollection.find().first();
        if (keysDoc == null) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<Document> fileKeyDocs = (List<Document>) keysDoc.get("fileKeys");
        if (fileKeyDocs != null) {
            for (Document keyDoc : fileKeyDocs) {
                String fileId = keyDoc.getString("fileId");

                try {
                    byte[] publicKeyBytes = Base64.getDecoder().decode(keyDoc.getString("ownerPublicKey"));
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

                    byte[] encryptedAESKey = Base64.getDecoder().decode(keyDoc.getString("encryptedAESKey"));

                    Date expiryDate = null;
                    String expiryDateStr = keyDoc.getString("expiryDate");
                    if (expiryDateStr != null && !expiryDateStr.isEmpty()) {
                        expiryDate = sdf.parse(expiryDateStr);
                    }

                    KeyManager.FileKeyEntry keyEntry = new KeyManager.FileKeyEntry(publicKey, encryptedAESKey, expiryDate);
                    if (!keyEntry.isExpired()) {
                        fileKeys.put(fileId, keyEntry);
                    }
                } catch (Exception e) {
                    System.err.println("Error loading key for file " + fileId + ": " + e.getMessage());
                }
            }
        }

        List<Document> reKeyDocs = (List<Document>) keysDoc.get("reEncryptionKeys");
        if (reKeyDocs != null) {
            for (Document reKeyDoc : reKeyDocs) {
                String fileId = reKeyDoc.getString("fileId");
                String requesterUsername = reKeyDoc.getString("requesterUsername");
                String key = fileId + ":" + requesterUsername;

                try {
                    byte[] reEncryptionKey = Base64.getDecoder().decode(reKeyDoc.getString("reEncryptionKey"));
                    Boolean revokedValue = reKeyDoc.getBoolean("revoked");
                    boolean revoked = revokedValue != null && revokedValue;

                    Date expiryDate = null;
                    String expiryDateStr = reKeyDoc.getString("expiryDate");
                    if (expiryDateStr != null && !expiryDateStr.isEmpty()) {
                        expiryDate = sdf.parse(expiryDateStr);
                    }

                    KeyManager.ReEncryptionKeyEntry reKeyEntry =
                            new KeyManager.ReEncryptionKeyEntry(reEncryptionKey, expiryDate, revoked);

                    if (!reKeyEntry.isExpired() && !reKeyEntry.isRevoked()) {
                        reEncryptionKeys.put(key, reKeyEntry);
                    }
                } catch (Exception e) {
                    System.err.println("Error loading re-encryption key for " + key + ": " + e.getMessage());
                }
            }
        }
    }
}
