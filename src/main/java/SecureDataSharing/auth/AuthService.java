package SecureDataSharing.auth;

import SecureDataSharing.audit.AuditLogger;
import SecureDataSharing.config.ConfigManager;
import SecureDataSharing.crypto.KeyManager;
import SecureDataSharing.email.EmailService;
import SecureDataSharing.storage.UserStorageService;
import java.io.IOException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

/**
 * Authentication Service with Multi-Factor Authentication (MFA) support.
 * 
 * Implements:
 * - User registration
 * - Password-based authentication
 * - OTP-based MFA
 * - Session management
 */
public class AuthService {
    private Map<String, User> users;
    private Map<String, String> activeOTPs; // username -> OTP
    private Map<String, Long> otpExpiry; // username -> expiry timestamp
    private ConfigManager config;
    private EmailService emailService;
    private AuditLogger auditLogger;
    private UserStorageService userStorage;
    private int otpLength;
    private long otpValidityMs;
    
    public AuthService(AuditLogger auditLogger) {
        this.users = new ConcurrentHashMap<>();
        this.activeOTPs = new ConcurrentHashMap<>();
        this.otpExpiry = new ConcurrentHashMap<>();
        this.auditLogger = auditLogger;
        this.config = ConfigManager.getInstance();
        this.emailService = EmailService.getInstance();
        this.userStorage = new UserStorageService();
        this.otpLength = config.getIntProperty("otp.length", 6);
        int validityMinutes = config.getIntProperty("otp.validity.minutes", 5);
        this.otpValidityMs = validityMinutes * 60 * 1000L;
        
        // Load users from disk
        try {
            userStorage.loadUsers(users);
            auditLogger.log("SYSTEM", "SYSTEM", "Loaded " + users.size() + " users from disk");
        } catch (IOException e) {
            System.err.println("Error loading users: " + e.getMessage());
            auditLogger.log("SYSTEM", "SYSTEM", "Error loading users: " + e.getMessage());
        }
    }
    
    /**
     * Registers a new user with password hashing and key pair generation.
     */
    public User registerUser(String username, String password) throws Exception {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("User already exists");
        }
        
        // Hash password
        String passwordHash = hashPassword(password);
        
        // Generate RSA key pair for PRE
        KeyPair keyPair = KeyManager.generateKeyPair();
        
        // Create user
        User user = new User(username, passwordHash, keyPair);
        users.put(username, user);
        
        // Save users to disk
        try {
            userStorage.saveUsers(users);
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
            auditLogger.log("SYSTEM", username, "Error saving users to disk: " + e.getMessage());
        }
        
        auditLogger.log("REGISTRATION", username, "User registered successfully");
        
        return user;
    }
    
    /**
     * Authenticates a user with username and password.
     * Returns true if authentication succeeds.
     */
    public boolean authenticate(String username, String password) {
        User user = users.get(username);
        if (user == null) {
            auditLogger.log("LOGIN_FAILED", username, "User not found");
            return false;
        }
        
        String passwordHash = hashPassword(password);
        if (!user.getPasswordHash().equals(passwordHash)) {
            auditLogger.log("LOGIN_FAILED", username, "Invalid password");
            return false;
        }
        
        auditLogger.log("LOGIN", username, "User authenticated successfully");
        return true;
    }
    
    /**
     * Generates and stores an OTP for MFA.
     * Sends OTP via email if email service is enabled and user has email.
     */
    public String generateOTP(String username) {
        // Generate OTP based on configured length
        Random random = new SecureRandom();
        int min = (int) Math.pow(10, otpLength - 1);
        int max = (int) Math.pow(10, otpLength) - 1;
        int otp = min + random.nextInt(max - min + 1);
        String otpString = String.valueOf(otp);
        
        // Store OTP with expiry
        activeOTPs.put(username, otpString);
        otpExpiry.put(username, System.currentTimeMillis() + otpValidityMs);
        
        // Send OTP via email if enabled
        User user = users.get(username);
        if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
            boolean emailSent = emailService.sendOTP(user.getEmail(), otpString);
            if (emailSent) {
                auditLogger.log("MFA_OTP_GENERATED", username, "OTP generated and sent via email");
            } else {
                auditLogger.log("MFA_OTP_GENERATED", username, "OTP generated (email sending failed)");
            }
        } else {
            auditLogger.log("MFA_OTP_GENERATED", username, "OTP generated for MFA");
        }
        
        return otpString;
    }
    
    /**
     * Validates an OTP for MFA.
     */
    public boolean validateOTP(String username, String otp) {
        String storedOTP = activeOTPs.get(username);
        Long expiry = otpExpiry.get(username);
        
        if (storedOTP == null || expiry == null) {
            auditLogger.log("MFA_FAILED", username, "No active OTP found");
            return false;
        }
        
        if (System.currentTimeMillis() > expiry) {
            activeOTPs.remove(username);
            otpExpiry.remove(username);
            auditLogger.log("MFA_FAILED", username, "OTP expired");
            return false;
        }
        
        if (!storedOTP.equals(otp)) {
            auditLogger.log("MFA_FAILED", username, "Invalid OTP");
            return false;
        }
        
        // OTP validated successfully
        activeOTPs.remove(username);
        otpExpiry.remove(username);
        auditLogger.log("MFA_SUCCESS", username, "OTP validated successfully");
        return true;
    }
    
    /**
     * Enables MFA for a user and generates a secret.
     */
    public void enableMFA(String username) {
        User user = users.get(username);
        if (user != null) {
            // Generate a secret for OTP (in production, use TOTP standard)
            SecureRandom random = new SecureRandom();
            byte[] secretBytes = new byte[20];
            random.nextBytes(secretBytes);
            String secret = bytesToHex(secretBytes);
            
            user.setMfaSecret(secret);
            user.setMfaEnabled(true);
            
            // Save users to disk
            try {
                userStorage.saveUsers(users);
            } catch (IOException e) {
                System.err.println("Error saving users: " + e.getMessage());
            }
            
            auditLogger.log("MFA_ENABLED", username, "MFA enabled for user");
        }
    }
    
    /**
     * Performs full authentication with MFA if enabled.
     */
    public boolean authenticateWithMFA(String username, String password, String otp) {
        // Step 1: Password authentication
        if (!authenticate(username, password)) {
            return false;
        }
        
        User user = users.get(username);
        if (user == null) {
            return false;
        }
        
        // Step 2: MFA if enabled
        if (user.isMfaEnabled()) {
            if (otp == null || !validateOTP(username, otp)) {
                auditLogger.log("MFA_REQUIRED", username, "MFA validation failed");
                return false;
            }
        }
        
        auditLogger.log("LOGIN_SUCCESS", username, "Full authentication successful");
        return true;
    }
    
    /**
     * Gets a user by username.
     */
    public User getUser(String username) {
        return users.get(username);
    }
    
    /**
     * Gets all users in the system (for admin view).
     */
    public java.util.Collection<User> getAllUsers() {
        return users.values();
    }
    
    /**
     * Saves users to disk (called after user modifications).
     */
    public void saveUsers() {
        try {
            userStorage.saveUsers(users);
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
            if (auditLogger != null) {
                auditLogger.log("SYSTEM", "SYSTEM", "Error saving users: " + e.getMessage());
            }
        }
    }
    
    /**
     * Hashes a password using SHA-256.
     * In production, use bcrypt or Argon2.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }
    
    /**
     * Converts byte array to hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
