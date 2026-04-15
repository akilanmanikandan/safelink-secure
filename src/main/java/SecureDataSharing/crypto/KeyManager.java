package SecureDataSharing.crypto;

import SecureDataSharing.storage.KeyStorageService;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;

/**
 * Manages cryptographic keys for users and files.
 * Handles key lifecycle including expiry and revocation.
 */
public class KeyManager {
    private static final String RSA_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE = 2048;
    
    // Store file encryption keys encrypted with owner's public key
    // Format: fileId -> (ownerPublicKey, encryptedAESKey, expiryDate)
    private Map<String, FileKeyEntry> fileKeys;
    
    // Store re-encryption keys for PRE
    // Format: (fileId, requesterUsername) -> ReEncryptionKeyEntry
    private Map<String, ReEncryptionKeyEntry> reEncryptionKeys;
    private KeyStorageService keyStorage;
    
    public KeyManager() {
        this.fileKeys = new ConcurrentHashMap<>();
        this.reEncryptionKeys = new ConcurrentHashMap<>();
        this.keyStorage = new KeyStorageService();
        
        // Load keys from disk
        try {
            keyStorage.loadKeys(fileKeys, reEncryptionKeys);
        } catch (IOException e) {
            System.err.println("Error loading keys: " + e.getMessage());
        }
    }
    
    /**
     * Generates a new RSA key pair for a user.
     */
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        keyPairGenerator.initialize(RSA_KEY_SIZE, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }
    
    /**
     * Stores an AES key encrypted with the owner's public key.
     */
    public void storeFileKey(String fileId, byte[] encryptedAESKey, java.security.PublicKey ownerPublicKey, Date expiryDate) {
        fileKeys.put(fileId, new FileKeyEntry(ownerPublicKey, encryptedAESKey, expiryDate));
        saveKeys();
    }
    
    /**
     * Retrieves the encrypted AES key for a file.
     */
    public FileKeyEntry getFileKey(String fileId) {
        FileKeyEntry entry = fileKeys.get(fileId);
        if (entry != null && entry.isExpired()) {
            fileKeys.remove(fileId);
            return null;
        }
        return entry;
    }
    
    /**
     * Stores a re-encryption key with expiry and revocation support.
     */
    public void storeReEncryptionKey(String fileId, String requesterUsername, 
                                     byte[] reEncryptionKey, Date expiryDate) {
        String key = fileId + ":" + requesterUsername;
        reEncryptionKeys.put(key, new ReEncryptionKeyEntry(reEncryptionKey, expiryDate, false));
        saveKeys();
    }
    
    /**
     * Retrieves a re-encryption key if valid and not revoked.
     */
    public ReEncryptionKeyEntry getReEncryptionKey(String fileId, String requesterUsername) {
        String key = fileId + ":" + requesterUsername;
        ReEncryptionKeyEntry entry = reEncryptionKeys.get(key);
        if (entry != null && (entry.isExpired() || entry.isRevoked())) {
            reEncryptionKeys.remove(key);
            return null;
        }
        return entry;
    }
    
    /**
     * Revokes a re-encryption key immediately.
     */
    public void revokeReEncryptionKey(String fileId, String requesterUsername) {
        String key = fileId + ":" + requesterUsername;
        ReEncryptionKeyEntry entry = reEncryptionKeys.get(key);
        if (entry != null) {
            entry.revoke();
            saveKeys();
        }
    }
    
    /**
     * Revokes all re-encryption keys for a file.
     */
    public void revokeAllReEncryptionKeys(String fileId) {
        reEncryptionKeys.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(fileId + ":"));
        saveKeys();
    }
    
    /**
     * Deletes a file key and all associated re-encryption keys.
     */
    public void deleteFileKey(String fileId) {
        fileKeys.remove(fileId);
        revokeAllReEncryptionKeys(fileId);
        saveKeys();
    }
    
    /**
     * Saves keys to disk.
     */
    private void saveKeys() {
        try {
            keyStorage.saveKeys(fileKeys, reEncryptionKeys);
        } catch (IOException e) {
            System.err.println("Error saving keys: " + e.getMessage());
        }
    }
    
    /**
     * Represents a stored file key entry.
     */
    public static class FileKeyEntry {
        private java.security.PublicKey ownerPublicKey;
        private byte[] encryptedAESKey;
        private Date expiryDate;
        
        public FileKeyEntry(java.security.PublicKey ownerPublicKey, 
                           byte[] encryptedAESKey, Date expiryDate) {
            this.ownerPublicKey = ownerPublicKey;
            this.encryptedAESKey = encryptedAESKey;
            this.expiryDate = expiryDate;
        }
        
        public java.security.PublicKey getOwnerPublicKey() {
            return ownerPublicKey;
        }
        
        public byte[] getEncryptedAESKey() {
            return encryptedAESKey;
        }
        
        public boolean isExpired() {
            if (expiryDate == null) return false;
            return new Date().after(expiryDate);
        }
        
        public Date getExpiryDate() {
            return expiryDate;
        }
    }
    
    /**
     * Represents a re-encryption key entry with lifecycle management.
     */
    public static class ReEncryptionKeyEntry {
        private byte[] reEncryptionKey;
        private Date expiryDate;
        private boolean revoked;
        
        public ReEncryptionKeyEntry(byte[] reEncryptionKey, Date expiryDate, boolean revoked) {
            this.reEncryptionKey = reEncryptionKey;
            this.expiryDate = expiryDate;
            this.revoked = revoked;
        }
        
        public byte[] getReEncryptionKey() {
            return reEncryptionKey;
        }
        
        public boolean isExpired() {
            if (expiryDate == null) return false;
            return new Date().after(expiryDate);
        }
        
        public boolean isRevoked() {
            return revoked;
        }
        
        public void revoke() {
            this.revoked = true;
        }
        
        public Date getExpiryDate() {
            return expiryDate;
        }
    }
}
