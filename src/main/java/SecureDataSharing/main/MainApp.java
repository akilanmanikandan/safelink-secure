package SecureDataSharing.main;

import SecureDataSharing.auth.AuthService;
import SecureDataSharing.auth.User;
import SecureDataSharing.access.Attribute;
import SecureDataSharing.access.Policy;
import SecureDataSharing.access.ABACService;
import SecureDataSharing.crypto.AESUtil;
import SecureDataSharing.crypto.KeyManager;
import SecureDataSharing.crypto.PREService;
import SecureDataSharing.storage.FileStorageService;
import SecureDataSharing.audit.AuditLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;
import javax.crypto.SecretKey;

/**
 * Main Application for Secure Data Storage and Controlled File Sharing.
 * 
 * Demonstrates the complete functional flow:
 * 1. User registration with attributes and key pairs
 * 2. User login with MFA
 * 3. File upload and AES encryption
 * 4. Access policy definition
 * 5. Access request and ABAC evaluation
 * 6. Proxy Re-Encryption
 * 7. File decryption by authorized user
 * 8. Automatic access expiry
 * 9. Audit logging of all events
 */
public class MainApp {
    private AuthService authService;
    private ABACService abacService;
    private KeyManager keyManager;
    private FileStorageService fileStorage;
    private AuditLogger auditLogger;
    private Scanner scanner;
    
    // Store policies: fileId -> Policy
    private java.util.Map<String, Policy> policies;
    
    public MainApp() throws Exception {
        // Initialize services
        auditLogger = new AuditLogger("audit.log");
        authService = new AuthService(auditLogger);
        abacService = new ABACService();
        keyManager = new KeyManager();
        fileStorage = new FileStorageService("encrypted_files");
        policies = new java.util.concurrent.ConcurrentHashMap<>();
        scanner = new Scanner(System.in);
        
        System.out.println("=== Secure Data Storage and Controlled File Sharing System ===");
        System.out.println("Initialized successfully.\n");
    }
    
    public void run() throws Exception {
        boolean running = true;
        
        while (running) {
            System.out.println("\n=== Main Menu ===");
            System.out.println("1. Register User");
            System.out.println("2. Login");
            System.out.println("3. Upload and Encrypt File");
            System.out.println("4. Request File Access");
            System.out.println("5. Decrypt and Download File");
            System.out.println("6. Revoke Access");
            System.out.println("7. View Audit Log");
            System.out.println("8. Exit");
            System.out.print("Choose an option: ");
            
            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            
            switch (choice) {
                case 1:
                    registerUser();
                    break;
                case 2:
                    login();
                    break;
                case 3:
                    uploadFile();
                    break;
                case 4:
                    requestAccess();
                    break;
                case 5:
                    decryptFile();
                    break;
                case 6:
                    revokeAccess();
                    break;
                case 7:
                    viewAuditLog();
                    break;
                case 8:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
        
        cleanup();
    }
    
    private void registerUser() throws Exception {
        System.out.println("\n=== User Registration ===");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        
        User user = authService.registerUser(username, password);
        
        // Assign default attributes
        System.out.print("Role (admin/user/manager): ");
        String role = scanner.nextLine();
        user.addAttribute(new Attribute("role", role));
        
        System.out.print("Department: ");
        String department = scanner.nextLine();
        user.addAttribute(new Attribute("department", department));
        
        // Enable MFA
        System.out.print("Enable MFA? (y/n): ");
        String enableMFA = scanner.nextLine();
        if (enableMFA.equalsIgnoreCase("y")) {
            authService.enableMFA(username);
            System.out.println("MFA enabled for user: " + username);
        }
        
        System.out.println("User registered successfully!");
        System.out.println("Public Key: " + user.getPublicKey().getAlgorithm());
    }
    
    private void login() {
        System.out.println("\n=== User Login ===");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        
        User user = authService.getUser(username);
        if (user == null) {
            System.out.println("User not found!");
            return;
        }
        
        // Check if MFA is enabled
        if (user.isMfaEnabled()) {
            System.out.println("MFA is enabled. Generating OTP...");
            String otp = authService.generateOTP(username);
            System.out.println("Generated OTP: " + otp + " (valid for 5 minutes)");
            System.out.print("Enter OTP: ");
            String enteredOTP = scanner.nextLine();
            
            if (!authService.validateOTP(username, enteredOTP)) {
                System.out.println("Invalid or expired OTP. Login failed.");
                return;
            }
        }
        
        if (authService.authenticate(username, password)) {
            System.out.println("Login successful!");
        } else {
            System.out.println("Login failed!");
        }
    }
    
    private void uploadFile() throws Exception {
        System.out.println("\n=== Upload and Encrypt File ===");
        System.out.print("Username (owner): ");
        String username = scanner.nextLine();
        
        User owner = authService.getUser(username);
        if (owner == null) {
            System.out.println("User not found!");
            return;
        }
        
        System.out.print("File path to encrypt: ");
        String filePath = scanner.nextLine();
        
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found!");
            return;
        }
        
        // Read file
        byte[] fileData = Files.readAllBytes(file.toPath());
        System.out.println("File size: " + fileData.length + " bytes");
        
        // Encrypt file with AES
        System.out.println("Encrypting file with AES-256-GCM...");
        AESUtil.EncryptionResult result = AESUtil.encryptFile(fileData);
        byte[] encryptedData = result.getEncryptedData();
        SecretKey aesKey = result.getKey();
        
        auditLogger.logEncryption(username, file.getName(), "AES-256-GCM");
        
        // Encrypt AES key with owner's public key
        byte[] encryptedAESKey = PREService.encryptAESKey(aesKey, owner.getPublicKey());
        
        // Generate file ID
        String fileId = generateFileId();
        
        // Store encrypted file
        fileStorage.storeFile(fileId, encryptedData, username, file.getName());
        
        // Store encrypted AES key (with optional expiry)
        System.out.print("Set key expiry? (y/n): ");
        String setExpiry = scanner.nextLine();
        Date expiryDate = null;
        if (setExpiry.equalsIgnoreCase("y")) {
            System.out.print("Expiry in hours: ");
            int hours = scanner.nextInt();
            scanner.nextLine();
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, hours);
            expiryDate = cal.getTime();
        }
        
        keyManager.storeFileKey(fileId, encryptedAESKey, owner.getPublicKey(), expiryDate);
        
        // Create access policy
        System.out.println("\n=== Define Access Policy ===");
        Policy policy = new Policy(fileId, expiryDate);
        
        System.out.print("Required role: ");
        String requiredRole = scanner.nextLine();
        policy.addRequiredAttribute(new Attribute("role", requiredRole));
        
        System.out.print("Required department: ");
        String requiredDept = scanner.nextLine();
        policy.addRequiredAttribute(new Attribute("department", requiredDept));
        
        policies.put(fileId, policy);
        
        System.out.println("File encrypted and stored successfully!");
        System.out.println("File ID: " + fileId);
        System.out.println("Original file: " + file.getName());
        System.out.println("Encrypted file size: " + encryptedData.length + " bytes");
    }
    
    private void requestAccess() throws Exception {
        System.out.println("\n=== Request File Access ===");
        System.out.print("Requester username: ");
        String requesterUsername = scanner.nextLine();
        
        User requester = authService.getUser(requesterUsername);
        if (requester == null) {
            System.out.println("User not found!");
            return;
        }
        
        System.out.print("File ID: ");
        String fileId = scanner.nextLine();
        
        if (!fileStorage.fileExists(fileId)) {
            System.out.println("File not found!");
            return;
        }
        
        Policy policy = policies.get(fileId);
        if (policy == null) {
            System.out.println("No access policy found for this file!");
            return;
        }
        
        FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(fileId);
        String ownerUsername = metadata.getOwnerUsername();
        
        auditLogger.logAccessRequest(requesterUsername, fileId, ownerUsername);
        
        // Step 1: ABAC Evaluation (MUST happen before PRE)
        System.out.println("Evaluating ABAC policy...");
        boolean accessGranted = abacService.evaluateAccess(requester, policy);
        
        if (!accessGranted) {
            auditLogger.logABACEvaluation(requesterUsername, fileId, false, 
                                         "User attributes do not satisfy policy requirements");
            System.out.println("Access DENIED: User attributes do not satisfy policy requirements.");
            System.out.println("Required attributes: " + policy.getRequiredAttributes());
            System.out.println("User attributes: " + requester.getAttributes());
            return;
        }
        
        auditLogger.logABACEvaluation(requesterUsername, fileId, true, 
                                     "All policy requirements satisfied");
        System.out.println("Access GRANTED: Policy requirements satisfied.");
        
        // Step 2: Generate re-encryption key
        User owner = authService.getUser(ownerUsername);
        if (owner == null) {
            System.out.println("Owner not found!");
            return;
        }
        
        System.out.println("Generating re-encryption key...");
        byte[] reEncryptionKey = PREService.generateReEncryptionKey(
            owner.getPrivateKey(), requester.getPublicKey());
        
        // Step 3: Get encrypted AES key
        KeyManager.FileKeyEntry fileKeyEntry = keyManager.getFileKey(fileId);
        if (fileKeyEntry == null) {
            System.out.println("File key not found or expired!");
            return;
        }
        
        byte[] encryptedAESKey = fileKeyEntry.getEncryptedAESKey();
        
        // Step 4: Re-encrypt AES key for requester
        System.out.println("Re-encrypting AES key (Proxy Re-Encryption)...");
        byte[] reEncryptedAESKey = PREService.reEncryptAESKeyWithOwnerKey(
            encryptedAESKey, owner.getPrivateKey(), requester.getPublicKey());
        
        auditLogger.logReEncryption(requesterUsername, fileId, ownerUsername);
        
        // Step 5: Store re-encryption key with expiry
        System.out.print("Set access expiry in hours (0 for no expiry): ");
        int hours = scanner.nextInt();
        scanner.nextLine();
        
        Date expiryDate = null;
        if (hours > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, hours);
            expiryDate = cal.getTime();
        }
        
        // Store the re-encrypted key (in practice, this would be stored securely)
        keyManager.storeReEncryptionKey(fileId, requesterUsername, 
                                       reEncryptedAESKey, expiryDate);
        
        System.out.println("Access granted and re-encryption completed!");
        System.out.println("Re-encrypted AES key stored for user: " + requesterUsername);
        if (expiryDate != null) {
            System.out.println("Access expires at: " + expiryDate);
        }
    }
    
    private void decryptFile() throws Exception {
        System.out.println("\n=== Decrypt and Download File ===");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        
        User user = authService.getUser(username);
        if (user == null) {
            System.out.println("User not found!");
            return;
        }
        
        // MFA check before decryption
        if (user.isMfaEnabled()) {
            System.out.println("MFA required for file access. Generating OTP...");
            String otp = authService.generateOTP(username);
            System.out.println("Generated OTP: " + otp);
            System.out.print("Enter OTP: ");
            String enteredOTP = scanner.nextLine();
            
            if (!authService.validateOTP(username, enteredOTP)) {
                System.out.println("Invalid OTP. Access denied.");
                return;
            }
        }
        
        System.out.print("File ID: ");
        String fileId = scanner.nextLine();
        
        if (!fileStorage.fileExists(fileId)) {
            System.out.println("File not found!");
            return;
        }
        
        // Check if user has re-encryption key
        KeyManager.ReEncryptionKeyEntry reKeyEntry = keyManager.getReEncryptionKey(fileId, username);
        if (reKeyEntry == null) {
            System.out.println("No valid re-encryption key found. Access may have expired or been revoked.");
            return;
        }
        
        // Decrypt AES key using requester's private key
        System.out.println("Decrypting AES key...");
        byte[] reEncryptedAESKey = reKeyEntry.getReEncryptionKey();
        SecretKey aesKey = PREService.decryptAESKey(reEncryptedAESKey, user.getPrivateKey());
        
        // Retrieve encrypted file
        System.out.println("Retrieving encrypted file...");
        byte[] encryptedData = fileStorage.retrieveFile(fileId);
        
        // Decrypt file
        System.out.println("Decrypting file...");
        byte[] decryptedData = AESUtil.decrypt(encryptedData, aesKey);
        
        auditLogger.logDecryption(username, fileId);
        
        // Save decrypted file
        FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(fileId);
        String outputPath = "decrypted_" + metadata.getOriginalFileName();
        Files.write(new File(outputPath).toPath(), decryptedData);
        
        System.out.println("File decrypted successfully!");
        System.out.println("Decrypted file saved as: " + outputPath);
        System.out.println("File size: " + decryptedData.length + " bytes");
    }
    
    private void revokeAccess() {
        System.out.println("\n=== Revoke Access ===");
        System.out.print("File owner username: ");
        String ownerUsername = scanner.nextLine();
        
        System.out.print("File ID: ");
        String fileId = scanner.nextLine();
        
        FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(fileId);
        if (metadata == null) {
            System.out.println("File not found!");
            return;
        }
        
        if (!metadata.getOwnerUsername().equals(ownerUsername)) {
            System.out.println("Only the file owner can revoke access!");
            return;
        }
        
        System.out.print("Revoke for specific user (enter username) or all users (enter 'all'): ");
        String target = scanner.nextLine();
        
        if (target.equalsIgnoreCase("all")) {
            keyManager.revokeAllReEncryptionKeys(fileId);
            auditLogger.logKeyLifecycle("REVOKE_ALL", ownerUsername, fileId, 
                                       "All re-encryption keys revoked");
            System.out.println("All access revoked for file: " + fileId);
        } else {
            keyManager.revokeReEncryptionKey(fileId, target);
            auditLogger.logKeyLifecycle("REVOKE", ownerUsername, fileId, 
                                       "Re-encryption key revoked for user: " + target);
            System.out.println("Access revoked for user: " + target);
        }
    }
    
    private void viewAuditLog() {
        System.out.println("\n=== Audit Log ===");
        try {
            java.nio.file.Path logPath = java.nio.file.Paths.get("audit.log");
            if (java.nio.file.Files.exists(logPath)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(logPath);
                System.out.println("Last 20 log entries:");
                int start = Math.max(0, lines.size() - 20);
                for (int i = start; i < lines.size(); i++) {
                    System.out.println(lines.get(i));
                }
            } else {
                System.out.println("No audit log found.");
            }
        } catch (IOException e) {
            System.out.println("Error reading audit log: " + e.getMessage());
        }
    }
    
    private String generateFileId() {
        return "FILE_" + System.currentTimeMillis() + "_" + 
               new java.util.Random().nextInt(10000);
    }
    
    private void cleanup() {
        auditLogger.close();
        scanner.close();
        System.out.println("\nApplication closed. Goodbye!");
    }
    
    public static void main(String[] args) {
        try {
            MainApp app = new MainApp();
            app.run();
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
