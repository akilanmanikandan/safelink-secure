package SecureDataSharing.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for AES encryption and decryption.
 * Uses AES-256-GCM for authenticated encryption.
 */
public class AESUtil {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // 256-bit keys
    private static final int GCM_IV_LENGTH = 12; // 96-bit IV for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    
    /**
     * Generates a new AES secret key.
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        return keyGenerator.generateKey();
    }
    
    /**
     * Converts a SecretKey to a byte array for storage/transmission.
     */
    public static byte[] keyToBytes(SecretKey key) {
        return key.getEncoded();
    }
    
    /**
     * Reconstructs a SecretKey from a byte array.
     */
    public static SecretKey bytesToKey(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    /**
     * Encrypts data using AES-GCM.
     * 
     * @param data The plaintext data to encrypt
     * @param key The AES secret key
     * @return Encrypted data with IV prepended (IV + ciphertext)
     */
    public static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        // Initialize cipher with IV
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        
        // Encrypt
        byte[] ciphertext = cipher.doFinal(data);
        
        // Prepend IV to ciphertext for storage
        byte[] encryptedData = new byte[GCM_IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, encryptedData, 0, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, 0, encryptedData, GCM_IV_LENGTH, ciphertext.length);
        
        return encryptedData;
    }
    
    /**
     * Decrypts data using AES-GCM.
     * 
     * @param encryptedData The encrypted data with IV prepended
     * @param key The AES secret key
     * @return Decrypted plaintext data
     */
    public static byte[] decrypt(byte[] encryptedData, SecretKey key) throws Exception {
        // Extract IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
        
        // Extract ciphertext
        byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
        
        // Decrypt
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        
        return cipher.doFinal(ciphertext);
    }
    
    /**
     * Encrypts a file and returns both the encrypted data and the encryption key.
     */
    public static EncryptionResult encryptFile(byte[] fileData) throws Exception {
        SecretKey key = generateKey();
        byte[] encryptedData = encrypt(fileData, key);
        return new EncryptionResult(encryptedData, key);
    }
    
    /**
     * Result class containing encrypted data and the encryption key.
     */
    public static class EncryptionResult {
        private final byte[] encryptedData;
        private final SecretKey key;
        
        public EncryptionResult(byte[] encryptedData, SecretKey key) {
            this.encryptedData = encryptedData;
            this.key = key;
        }
        
        public byte[] getEncryptedData() {
            return encryptedData;
        }
        
        public SecretKey getKey() {
            return key;
        }
    }
}
