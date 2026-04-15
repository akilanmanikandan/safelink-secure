# SecureDataSharing Core Modules Source Guide

Generated: 2026-04-10 19:29:22

- Project: Secure Data Storage and Controlled File Sharing System
- Primary stack: Java / Spring Boot web application with a Python Flask ML sidecar
- Output intent: This document focuses on the most important source modules rather than every support file.
- How to read it: Each major module starts with a short explanation, then each important source file is introduced and reproduced in full so the code and the explanation stay together.

## Module Index
- **Application Bootstrap**: These entry points wire the system together. WebApplication.java is the production Spring Boot bootstrap. MainApp.java is a console-driven demonstration flow that still shows the end-to-end security design clearly.
- **Authentication and Session**: This module owns user registration, password authentication, OTP-based MFA, session persistence, and durable user storage.
- **Access Control and Approval Workflow**: This area governs who may request access, how policies are evaluated, and how managers approve or deny access to encrypted files.
- **Cryptography and Secure File Handling**: This module is the core security path: encrypt the file, protect the AES key, persist encrypted blobs, and only decrypt for approved users.
- **Monitoring and Dynamic Configuration**: These modules add anomaly detection, administrator alerting, and runtime configuration loading for UI and email behavior.

## Application Bootstrap

These entry points wire the system together. WebApplication.java is the production Spring Boot bootstrap. MainApp.java is a console-driven demonstration flow that still shows the end-to-end security design clearly.

Support submodules worth knowing about:
- SecureDataSharing.web.controller.ViewController.java - static view routing
- src/main/resources/application.properties - Spring runtime properties

### WebApplication.java

- **Path**: `src\main\java\SecureDataSharing\web\WebApplication.java`
- **What it is**: Spring Boot application bootstrap and bean registry.
- **Why it matters**: This file defines the singleton services and in-memory maps that the web controllers rely on for auth, policies, access requests, key management, storage, sessions, and ML monitoring.

```
package SecureDataSharing.web;

import SecureDataSharing.audit.AuditLogger;
import SecureDataSharing.auth.AuthService;
import SecureDataSharing.crypto.KeyManager;
import SecureDataSharing.storage.*;
import SecureDataSharing.access.ABACService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import SecureDataSharing.access.Policy;
import SecureDataSharing.access.AccessRequest;

/**
 * Spring Boot Web Application for Secure Data Sharing System.
 */
@SpringBootApplication
@Configuration
public class WebApplication {
    
    @Bean
    public AuditLogger auditLogger() throws Exception {
        return new AuditLogger("audit.log");
    }
    
    @Bean
    public AuthService authService(AuditLogger auditLogger) {
        return new AuthService(auditLogger);
    }
    
    @Bean
    public ABACService abacService() {
        return new ABACService();
    }
    
    @Bean
    public KeyManager keyManager() {
        return new KeyManager();
    }
    
    @Bean
    public FileStorageService fileStorageService() {
        return new FileStorageService("encrypted_files");
    }
    
    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }
    
    @Bean
    public PolicyStorageService policyStorageService() {
        return new PolicyStorageService();
    }
    
    @Bean
    public AccessRequestStorageService accessRequestStorageService() {
        return new AccessRequestStorageService();
    }
    
    @Bean
    public Map<String, Policy> policies(PolicyStorageService policyStorage) {
        Map<String, Policy> policiesMap = new ConcurrentHashMap<>();
        try {
            if (policyStorage != null) {
                policyStorage.loadPolicies(policiesMap);
                System.out.println("Loaded " + policiesMap.size() + " policies from disk");
            }
        } catch (Exception e) {
            System.err.println("Error loading policies: " + e.getMessage());
        }
        return policiesMap;
    }
    
    @Bean
    public Map<String, AccessRequest> accessRequests(AccessRequestStorageService accessRequestStorage) {
        Map<String, AccessRequest> requestsMap = new ConcurrentHashMap<>();
        try {
            accessRequestStorage.loadAccessRequests(requestsMap);
            System.out.println("Loaded " + requestsMap.size() + " access requests from disk");
        } catch (Exception e) {
            System.err.println("Error loading access requests: " + e.getMessage());
        }
        return requestsMap;
    }

    @Bean
    public SecureDataSharing.ml.MLMonitoringService mlMonitoringService() {
        return new SecureDataSharing.ml.MLMonitoringService();
    }
    
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}

```

### MainApp.java

- **Path**: `src\main\java\SecureDataSharing\main\MainApp.java`
- **What it is**: Console-mode orchestration of the full secure sharing workflow.
- **Why it matters**: Even if the web app is the main runtime, this class is useful because it demonstrates registration, MFA, file encryption, policy creation, access requests, PRE-based sharing, decryption, revocation, and audit logging in one place.

```
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

```

## Authentication and Session

This module owns user registration, password authentication, OTP-based MFA, session persistence, and durable user storage.

Support submodules worth knowing about:
- SecureDataSharing.auth.User.java - user domain model with keys, attributes, and MFA flags
- SecureDataSharing.storage.SessionManager.java - remembers the active session
- SecureDataSharing.email.EmailValidator.java - validates registration email addresses
- SecureDataSharing.email.EmailService.java - sends OTPs and notifications

### AuthController.java

- **Path**: `src\main\java\SecureDataSharing\web\controller\AuthController.java`
- **What it is**: REST API for registration, login, OTP validation, logout, and session lookup.
- **Why it matters**: This is the entry point the UI uses for the full authentication lifecycle. It also triggers ML monitoring on successful logins and enforces mandatory MFA at the controller level.

```
package SecureDataSharing.web.controller;

import SecureDataSharing.auth.AuthService;
import SecureDataSharing.auth.User;
import SecureDataSharing.email.EmailValidator;
import SecureDataSharing.storage.SessionManager;
import SecureDataSharing.access.Attribute;
import SecureDataSharing.audit.AuditLogger;
import SecureDataSharing.ml.MLMonitoringService;
import SecureDataSharing.ml.MLPredictionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private MLMonitoringService mlMonitoringService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            String email = request.get("email");
            String role = request.get("role");
            String department = request.get("department");

            if (username == null || password == null || email == null ||
                    role == null || department == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "All fields are required");
                return ResponseEntity.badRequest().body(error);
            }

            // Validate email
            EmailValidator.ValidationResult emailValidation = EmailValidator.validate(email);
            if (!emailValidation.isValid()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", emailValidation.getErrorMessage());
                return ResponseEntity.badRequest().body(error);
            }

            // Register user
            User user = authService.registerUser(username, password);
            user.setEmail(email);
            user.addAttribute(new Attribute("role", role));
            user.addAttribute(new Attribute("department", department));

            // MFA is mandatory
            authService.enableMFA(username);
            authService.saveUsers();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully! MFA has been enabled.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            if (username == null || password == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Username and password are required");
                return ResponseEntity.badRequest().body(error);
            }

            User user = authService.getUser(username);
            if (user == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "User not found");
                return ResponseEntity.badRequest().body(error);
            }

            // Check if MFA is enabled (it should be for all users)
            if (user.isMfaEnabled()) {
                // Generate OTP
                String otp = authService.generateOTP(username);

                Map<String, Object> response = new HashMap<>();
                response.put("mfaRequired", true);
                response.put("message", "OTP has been sent to your email");
                response.put("email", user.getEmail());

                // Store username in session temporarily
                session.setAttribute("pendingUsername", username);

                return ResponseEntity.ok(response);
            }

            // If no MFA (shouldn't happen), authenticate directly
            if (authService.authenticate(username, password)) {
                session.setAttribute("username", username);
                sessionManager.saveSession(username);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("user", getUserInfo(user));

                // ML Check
                MLPredictionResponse mlResponse = mlMonitoringService.checkAnomaly(username, "login");
                if (mlResponse != null && "SUSPICIOUS BEHAVIOR DETECTED".equals(mlResponse.getStatus())) {
                    response.put("warning", "Suspicious login behavior detected. An alert has been sent to the administrator.");
                    response.put("risk_score", mlResponse.getRisk_score());
                    response.put("reasons", mlResponse.getReasons());
                }

                return ResponseEntity.ok(response);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid username or password");
                return ResponseEntity.badRequest().body(error);
            }
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Login failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/validate-otp")
    public ResponseEntity<?> validateOTP(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            String otp = request.get("otp");
            String username = (String) session.getAttribute("pendingUsername");

            if (username == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No pending login session");
                return ResponseEntity.badRequest().body(error);
            }

            if (otp == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "OTP is required");
                return ResponseEntity.badRequest().body(error);
            }

            // Validate OTP
            if (!authService.validateOTP(username, otp)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid or expired OTP");
                return ResponseEntity.badRequest().body(error);
            }

            // Get password from session or request
            String password = request.get("password");
            if (password == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Password is required");
                return ResponseEntity.badRequest().body(error);
            }

            // Authenticate
            if (!authService.authenticate(username, password)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid password");
                return ResponseEntity.badRequest().body(error);
            }

            // Login successful
            User user = authService.getUser(username);
            session.setAttribute("username", username);
            session.removeAttribute("pendingUsername");
            sessionManager.saveSession(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", getUserInfo(user));

            // ML Check
            MLPredictionResponse mlResponse = mlMonitoringService.checkAnomaly(username, "login");
            if (mlResponse != null && "SUSPICIOUS BEHAVIOR DETECTED".equals(mlResponse.getStatus())) {
                response.put("warning", "Suspicious login behavior detected. An alert has been sent to the administrator.");
                response.put("risk_score", mlResponse.getRisk_score());
                response.put("reasons", mlResponse.getReasons());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "OTP validation failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username != null) {
            sessionManager.clearSession();
            auditLogger.log("LOGOUT", username, "User logged out");
        }
        session.invalidate();
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        Map<String, Object> response = new HashMap<>();
        if (username != null) {
            User user = authService.getUser(username);
            if (user != null) {
                response.put("user", getUserInfo(user));
                return ResponseEntity.ok(response);
            }
        }
        response.put("user", null);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> getUserInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("mfaEnabled", user.isMfaEnabled());

        Map<String, String> attributes = new HashMap<>();
        for (Attribute attr : user.getAttributes()) {
            attributes.put(attr.getName(), attr.getValue());
        }
        userInfo.put("attributes", attributes);

        return userInfo;
    }
}

```

### AuthService.java

- **Path**: `src\main\java\SecureDataSharing\auth\AuthService.java`
- **What it is**: Core authentication service for password hashing, user lookup, OTP generation, MFA validation, and user persistence.
- **Why it matters**: It is one of the central business-logic classes in the project because nearly every secure workflow depends on the user, key, and MFA state it manages.

```
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

```

### UserStorageService.java

- **Path**: `src\main\java\SecureDataSharing\storage\UserStorageService.java`
- **What it is**: JSON-backed persistence for users, attributes, and RSA keys.
- **Why it matters**: This file explains how the application survives restarts: it serializes users, attributes, MFA settings, and both public/private keys to data/users.json.

```
package SecureDataSharing.storage;

import SecureDataSharing.auth.User;
import SecureDataSharing.access.Attribute;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Service for persisting and loading users from disk.
 */
public class UserStorageService {
    private static final String USERS_FILE = "data/users.json";
    private static final String DATA_DIR = "data";
    
    public UserStorageService() {
        // Create data directory if it doesn't exist
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }
    
    /**
     * Saves all users to disk.
     */
    public void saveUsers(Map<String, User> users) throws IOException {
        JSONArray usersArray = new JSONArray();
        
        for (User user : users.values()) {
            JSONObject userJson = new JSONObject();
            userJson.put("username", user.getUsername());
            userJson.put("passwordHash", user.getPasswordHash());
            userJson.put("email", user.getEmail() != null ? user.getEmail() : "");
            userJson.put("mfaEnabled", user.isMfaEnabled());
            userJson.put("mfaSecret", user.getMfaSecret() != null ? user.getMfaSecret() : "");
            
            // Save registration date
            if (user.getRegistrationDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                userJson.put("registrationDate", sdf.format(user.getRegistrationDate()));
            }
            
            // Save attributes
            JSONArray attributesArray = new JSONArray();
            for (Attribute attr : user.getAttributes()) {
                JSONObject attrJson = new JSONObject();
                attrJson.put("name", attr.getName());
                attrJson.put("value", attr.getValue());
                attributesArray.put(attrJson);
            }
            userJson.put("attributes", attributesArray);
            
            // Save public key (for PRE)
            try {
                PublicKey publicKey = user.getPublicKey();
                byte[] publicKeyBytes = publicKey.getEncoded();
                userJson.put("publicKey", Base64.getEncoder().encodeToString(publicKeyBytes));
                
                // Save private key (encrypted in production, but for now base64)
                PrivateKey privateKey = user.getPrivateKey();
                byte[] privateKeyBytes = privateKey.getEncoded();
                userJson.put("privateKey", Base64.getEncoder().encodeToString(privateKeyBytes));
            } catch (Exception e) {
                System.err.println("Error encoding keys for user " + user.getUsername() + ": " + e.getMessage());
            }
            
            usersArray.put(userJson);
        }
        
        // Write to file
        try (FileWriter fileWriter = new FileWriter(USERS_FILE)) {
            fileWriter.write(usersArray.toString(2)); // Pretty print with 2-space indent
        }
    }
    
    /**
     * Loads all users from disk.
     */
    public void loadUsers(Map<String, User> users) throws IOException {
        File usersFile = new File(USERS_FILE);
        if (!usersFile.exists()) {
            return; // No users file yet, start fresh
        }
        
        String content = new String(Files.readAllBytes(Paths.get(USERS_FILE)));
        JSONArray usersArray = new JSONArray(content);
        
        for (int i = 0; i < usersArray.length(); i++) {
            JSONObject userJson = usersArray.getJSONObject(i);
            
            String username = userJson.getString("username");
            String passwordHash = userJson.getString("passwordHash");
            
            // Load keys
            KeyPair keyPair = null;
            try {
                String publicKeyStr = userJson.getString("publicKey");
                String privateKeyStr = userJson.getString("privateKey");
                
                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
                byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);
                
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
                PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                
                keyPair = new KeyPair(publicKey, privateKey);
            } catch (Exception e) {
                System.err.println("Error loading keys for user " + username + ": " + e.getMessage());
                continue; // Skip this user if keys can't be loaded
            }
            
            // Create user
            User user = new User(username, passwordHash, keyPair);
            
            // Load email
            if (userJson.has("email") && !userJson.getString("email").isEmpty()) {
                user.setEmail(userJson.getString("email"));
            }
            
            // Load MFA settings
            if (userJson.has("mfaEnabled")) {
                user.setMfaEnabled(userJson.getBoolean("mfaEnabled"));
            }
            if (userJson.has("mfaSecret") && !userJson.getString("mfaSecret").isEmpty()) {
                user.setMfaSecret(userJson.getString("mfaSecret"));
            }
            
            // Load attributes
            if (userJson.has("attributes")) {
                JSONArray attributesArray = userJson.getJSONArray("attributes");
                for (int j = 0; j < attributesArray.length(); j++) {
                    JSONObject attrJson = attributesArray.getJSONObject(j);
                    Attribute attr = new Attribute(attrJson.getString("name"), attrJson.getString("value"));
                    user.addAttribute(attr);
                }
            }
            
            users.put(username, user);
        }
    }
}

```

## Access Control and Approval Workflow

This area governs who may request access, how policies are evaluated, and how managers approve or deny access to encrypted files.

Support submodules worth knowing about:
- SecureDataSharing.access.Policy.java - resource policy model with required attributes and validity windows
- SecureDataSharing.access.Attribute.java - simple name/value ABAC attribute
- SecureDataSharing.access.AccessRequest.java - approval request state model
- SecureDataSharing.storage.PolicyStorageService.java - persists file policies
- SecureDataSharing.storage.AccessRequestStorageService.java - persists request state

### ABACService.java

- **Path**: `src\main\java\SecureDataSharing\access\ABACService.java`
- **What it is**: Attribute-Based Access Control evaluator.
- **Why it matters**: This is the rule engine that decides whether a user matches a file policy before deeper sharing logic runs.

```
package SecureDataSharing.access;

import SecureDataSharing.auth.User;
import java.util.Set;
import java.util.Date;

/**
 * Attribute-Based Access Control (ABAC) Service.
 * 
 * Evaluates access policies based on user attributes such as:
 * - Role (e.g., "admin", "user", "manager")
 * - Department (e.g., "engineering", "sales", "hr")
 * - Clearance level (e.g., "confidential", "secret", "top-secret")
 * - Time validity (access granted only within certain time windows)
 * 
 * ABAC is evaluated BEFORE Proxy Re-Encryption is triggered.
 */
public class ABACService {
    
    /**
     * Evaluates whether a user's attributes satisfy a policy's requirements.
     * 
     * @param user The user requesting access
     * @param policy The access policy to evaluate
     * @return true if the user's attributes satisfy all policy requirements, false otherwise
     */
    public boolean evaluateAccess(User user, Policy policy) {
        // Check if policy is valid (not revoked, within time bounds)
        if (!policy.isValid()) {
            return false;
        }
        
        // Get user attributes
        Set<Attribute> userAttributes = user.getAttributes();
        Set<Attribute> requiredAttributes = policy.getRequiredAttributes();
        
        // Check if user has all required attributes
        // In ABAC, all required attributes must be present
        for (Attribute required : requiredAttributes) {
            boolean found = false;
            for (Attribute userAttr : userAttributes) {
                if (userAttr.getName().equals(required.getName()) && 
                    userAttr.getValue().equals(required.getValue())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false; // Missing required attribute
            }
        }
        
        // All required attributes are present
        return true;
    }
    
    /**
     * Evaluates access with additional time-based constraints.
     * 
     * @param user The user requesting access
     * @param policy The access policy
     * @param currentTime The current time (for testing or time-based access)
     * @return true if access is granted
     */
    public boolean evaluateAccessWithTime(User user, Policy policy, Date currentTime) {
        // Check policy validity
        if (policy.isRevoked()) {
            return false;
        }
        
        // Check time bounds
        if (policy.getValidUntil() != null && currentTime.after(policy.getValidUntil())) {
            return false;
        }
        
        if (currentTime.before(policy.getValidFrom())) {
            return false;
        }
        
        // Evaluate attributes
        return evaluateAccess(user, policy);
    }
    
    /**
     * Creates a simple policy requiring specific role and department.
     */
    public static Policy createRoleBasedPolicy(String resourceId, String role, String department) {
        Policy policy = new Policy(resourceId);
        policy.addRequiredAttribute(new Attribute("role", role));
        policy.addRequiredAttribute(new Attribute("department", department));
        return policy;
    }
    
    /**
     * Creates a time-bound policy.
     */
    public static Policy createTimeBoundPolicy(String resourceId, Date validUntil, 
                                               String role, String department) {
        Policy policy = new Policy(resourceId, validUntil);
        policy.addRequiredAttribute(new Attribute("role", role));
        policy.addRequiredAttribute(new Attribute("department", department));
        return policy;
    }
}

```

### AccessRequestController.java

- **Path**: `src\main\java\SecureDataSharing\web\controller\AccessRequestController.java`
- **What it is**: REST API for creating, listing, approving, denying, and revoking access requests.
- **Why it matters**: This controller connects policy checks, ownership checks, PRE key generation, audit logging, persistence, and ML monitoring. It is one of the most behavior-heavy modules in the project.

```
package SecureDataSharing.web.controller;

import SecureDataSharing.auth.AuthService;
import SecureDataSharing.auth.User;
import SecureDataSharing.access.AccessRequest;
import SecureDataSharing.access.Policy;
import SecureDataSharing.access.ABACService;
import SecureDataSharing.crypto.KeyManager;
import SecureDataSharing.crypto.PREService;
import SecureDataSharing.storage.FileStorageService;
import SecureDataSharing.audit.AuditLogger;
import SecureDataSharing.ml.MLMonitoringService;
import SecureDataSharing.ml.MLPredictionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Calendar;
import java.util.Date;

@RestController
@RequestMapping("/api/access-requests")
public class AccessRequestController {
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private FileStorageService fileStorage;
    
    @Autowired
    private KeyManager keyManager;
    
    @Autowired
    private ABACService abacService;
    
    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private MLMonitoringService mlMonitoringService;
    
    @Autowired
    private Map<String, Policy> policies;
    
    @Autowired
    private Map<String, AccessRequest> accessRequests;
    
    @Autowired(required = false)
    private SecureDataSharing.storage.AccessRequestStorageService accessRequestStorage;
    
    @Autowired(required = false)
    private SecureDataSharing.storage.PolicyStorageService policyStorage;
    
    @PostMapping("/request")
    public ResponseEntity<?> requestAccess(@RequestBody Map<String, String> request, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                .body(createErrorMap("Not authenticated"));
        }
        
        try {
            String fileId = request.get("fileId");
            if (fileId == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File ID is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            if (!fileStorage.fileExists(fileId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            Policy policy = policies.get(fileId);
            if (policy == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No access policy found");
                return ResponseEntity.badRequest().body(error);
            }
            
            User user = authService.getUser(username);
            
            // Get file owner
            FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(fileId);
            String ownerUsername = metadata.getOwnerUsername();
            
            // Check if request already exists (check by fileId and username)
            AccessRequest existingRequest = null;
            for (AccessRequest req : accessRequests.values()) {
                if (req.getFileId().equals(fileId) && req.getRequesterUsername().equals(username)) {
                    existingRequest = req;
                    break;
                }
            }
            
            if (existingRequest != null && existingRequest.getStatus() == AccessRequest.RequestStatus.PENDING) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Access request already pending");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Perform ABAC evaluation
            boolean accessGranted = abacService.evaluateAccess(user, policy);
            
            // Create access request
            AccessRequest accessRequest = new AccessRequest(username, fileId, ownerUsername);
            String requestId = accessRequest.getRequestId();
            accessRequests.put(requestId, accessRequest);
            
            // Save access requests
            if (accessRequestStorage != null) {
                try {
                    accessRequestStorage.saveAccessRequests(accessRequests);
                } catch (Exception e) {
                    System.err.println("Error saving access requests: " + e.getMessage());
                }
            }
            
            auditLogger.log("ACCESS_REQUESTED", username, 
                "Requested access to file: " + fileId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requestId", requestId);
            response.put("message", accessGranted ? 
                "Access request created. Attributes match policy." : 
                "Access request created. Manager approval required.");
            
            // ML Check
            MLPredictionResponse mlResponse = mlMonitoringService.checkAnomaly(username, "request_access");
            if (mlResponse != null && "SUSPICIOUS BEHAVIOR DETECTED".equals(mlResponse.getStatus())) {
                response.put("warning", "Suspicious behavior detected. An alert has been sent to the administrator.");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error creating access request: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(error);
        }
        
        User user = authService.getUser(username);
        String userRole = getUserRole(user);
        
        // Only managers and admins can see pending requests
        if (!"manager".equalsIgnoreCase(userRole) && !"admin".equalsIgnoreCase(userRole)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Access denied");
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(error);
        }
        
        try {
            List<Map<String, Object>> pendingRequests = new ArrayList<>();
            for (AccessRequest req : accessRequests.values()) {
                if (req.getStatus() == AccessRequest.RequestStatus.PENDING) {
                    FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(req.getFileId());
                    if (metadata != null && metadata.getOwnerUsername().equals(username)) {
                        pendingRequests.add(requestToMap(req));
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("requests", pendingRequests);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error loading requests: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<?> approveRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                .body(createErrorMap("Not authenticated"));
        }
        
        try {
            AccessRequest accessRequest = accessRequests.get(requestId);
            if (accessRequest == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Request not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Verify ownership
            FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(accessRequest.getFileId());
            if (metadata == null || !metadata.getOwnerUsername().equals(username)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You can only approve requests for your own files");
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(error);
            }
            
            // Get expiry settings
            int expiryValue = 24;
            String expiryUnit = "hours";
            if (request != null) {
                if (request.containsKey("expiryValue")) {
                    expiryValue = ((Number) request.get("expiryValue")).intValue();
                }
                if (request.containsKey("expiryUnit")) {
                    expiryUnit = (String) request.get("expiryUnit");
                }
            }
            Date expiryDate = calculateExpiryDate(expiryValue, expiryUnit);
            
            // Get requester
            User requester = authService.getUser(accessRequest.getRequesterUsername());
            if (requester == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Requester not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Get file owner
            User owner = authService.getUser(username);
            
            // Get file key
            KeyManager.FileKeyEntry fileKey = keyManager.getFileKey(accessRequest.getFileId());
            if (fileKey == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Cannot approve: The uploaded file has reached its expiration date and its cryptographic key has been destroyed.");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Re-encrypt AES key for requester
            byte[] encryptedAESKey = fileKey.getEncryptedAESKey();
            byte[] reEncryptedAESKey = PREService.reEncryptAESKeyWithOwnerKey(
                encryptedAESKey, owner.getPrivateKey(), requester.getPublicKey());
            
            keyManager.storeReEncryptionKey(accessRequest.getFileId(), 
                accessRequest.getRequesterUsername(), reEncryptedAESKey, expiryDate);
            
            // Update request status
            accessRequest.setStatus(AccessRequest.RequestStatus.APPROVED);
            accessRequest.setReason("Approved by " + username);
            
            // Save access requests
            if (accessRequestStorage != null) {
                try {
                    accessRequestStorage.saveAccessRequests(accessRequests);
                } catch (Exception e) {
                    System.err.println("Error saving access requests: " + e.getMessage());
                }
            }
            
            auditLogger.log("ACCESS_APPROVED", username, 
                "Approved access request: " + requestId + " for user: " + accessRequest.getRequesterUsername());
            
            // Save access requests
            if (accessRequestStorage != null) {
                try {
                    accessRequestStorage.saveAccessRequests(accessRequests);
                } catch (Exception e) {
                    System.err.println("Error saving access requests: " + e.getMessage());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Access approved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error approving request: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @PostMapping("/{requestId}/deny")
    public ResponseEntity<?> denyRequest(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, String> request,
            HttpSession session) {
        
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(error);
        }
        
        try {
            AccessRequest accessRequest = accessRequests.get(requestId);
            if (accessRequest == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Request not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Verify ownership
            FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(accessRequest.getFileId());
            if (metadata == null || !metadata.getOwnerUsername().equals(username)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You can only deny requests for your own files");
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(error);
            }
            
            String reason = request != null ? request.get("reason") : null;
            
            accessRequest.setStatus(AccessRequest.RequestStatus.DENIED);
            if (reason != null && !reason.isEmpty()) {
                accessRequest.setReason(reason);
            } else {
                accessRequest.setReason("Denied by " + username);
            }
            
            // Save access requests
            if (accessRequestStorage != null) {
                try {
                    accessRequestStorage.saveAccessRequests(accessRequests);
                } catch (Exception e) {
                    System.err.println("Error saving access requests: " + e.getMessage());
                }
            }
            
            auditLogger.log("ACCESS_DENIED", username, 
                "Denied access request: " + requestId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Access denied");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error denying request: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @PostMapping("/revoke")
    public ResponseEntity<?> revokeAccess(@RequestBody Map<String, String> request, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(error);
        }
        
        try {
            String fileId = request.get("fileId");
            String targetUsername = request.get("username");
            
            if (fileId == null || targetUsername == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File ID and username are required");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Verify ownership
            FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(fileId);
            if (metadata == null || !metadata.getOwnerUsername().equals(username)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You can only revoke access to your own files");
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(error);
            }
            
            if ("all".equalsIgnoreCase(targetUsername)) {
                keyManager.revokeAllReEncryptionKeys(fileId);
            } else {
                keyManager.revokeReEncryptionKey(fileId, targetUsername);
            }
            
            auditLogger.log("ACCESS_REVOKED", username, 
                "Revoked access for file: " + fileId + ", user: " + targetUsername);
            
            Map<String, Boolean> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error revoking access: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    private Map<String, Object> requestToMap(AccessRequest req) {
        Map<String, Object> map = new HashMap<>();
        map.put("requestId", req.getRequestId());
        map.put("requester", req.getRequesterUsername());
        map.put("fileId", req.getFileId());
        
        FileStorageService.FileMetadata metadata = fileStorage.getFileMetadata(req.getFileId());
        if (metadata != null) {
            map.put("fileName", metadata.getOriginalFileName());
        }
        
        map.put("requestDate", req.getRequestDate());
        map.put("status", req.getStatus().toString());
        return map;
    }
    
    private String getUserRole(User user) {
        for (SecureDataSharing.access.Attribute attr : user.getAttributes()) {
            if ("role".equalsIgnoreCase(attr.getName())) {
                return attr.getValue().toLowerCase();
            }
        }
        return "user";
    }
    
    private Map<String, String> createErrorMap(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
    
    private Date calculateExpiryDate(int value, String unit) {
        Calendar cal = Calendar.getInstance();
        switch (unit.toLowerCase()) {
            case "seconds":
                cal.add(Calendar.SECOND, value);
                break;
            case "minutes":
                cal.add(Calendar.MINUTE, value);
                break;
            case "hours":
                cal.add(Calendar.HOUR, value);
                break;
            case "days":
                cal.add(Calendar.DAY_OF_MONTH, value);
                break;
            default:
                cal.add(Calendar.HOUR, 24);
        }
        return cal.getTime();
    }
}

```

## Cryptography and Secure File Handling

This module is the core security path: encrypt the file, protect the AES key, persist encrypted blobs, and only decrypt for approved users.

Support submodules worth knowing about:
- SecureDataSharing.storage.KeyStorageService.java - persists encrypted file keys and re-encryption keys

### AESUtil.java

- **Path**: `src\main\java\SecureDataSharing\crypto\AESUtil.java`
- **What it is**: AES-256-GCM encryption and decryption utility.
- **Why it matters**: This module performs the actual content encryption. It is the lowest-level file confidentiality primitive in the system.

```
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

```

### KeyManager.java

- **Path**: `src\main\java\SecureDataSharing\crypto\KeyManager.java`
- **What it is**: Lifecycle manager for encrypted file keys and requester re-encryption keys.
- **Why it matters**: It is central because access expiry, key lookup, revocation, and durable PRE sharing all route through this class.

```
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

```

### PREService.java

- **Path**: `src\main\java\SecureDataSharing\crypto\PREService.java`
- **What it is**: Proxy Re-Encryption helper and educational PRE implementation.
- **Why it matters**: This class captures the project secure sharing idea: keep the file encrypted, and transform only the AES key for approved users.

```
package SecureDataSharing.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Proxy Re-Encryption Service.
 * 
 * Implements Proxy Re-Encryption (PRE) to allow secure sharing of encrypted data
 * without exposing the original encryption key or decrypting the data.
 * 
 * The process:
 * 1. Owner encrypts file with AES key K
 * 2. Owner encrypts K with their public key: E_owner(K)
 * 3. When sharing, proxy generates re-encryption key: rk_owner->requester
 * 4. Proxy re-encrypts: E_requester(K) = ReEncrypt(E_owner(K), rk_owner->requester)
 * 5. Requester decrypts with their private key to get K
 * 6. Requester uses K to decrypt the file
 * 
 * The file itself is never decrypted during re-encryption - only the AES key is re-encrypted.
 */
public class PREService {
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING";
    
    /**
     * Encrypts an AES key with the owner's public key.
     * This is the initial encryption of the file's AES key.
     */
    public static byte[] encryptAESKey(SecretKey aesKey, PublicKey ownerPublicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, ownerPublicKey);
        return cipher.doFinal(AESUtil.keyToBytes(aesKey));
    }
    
    /**
     * Generates a re-encryption key that allows converting
     * an AES key encrypted for the owner to be encrypted for the requester.
     * 
     * In a full PRE scheme, this would involve more complex cryptographic operations.
     * For this implementation, we use a simplified approach where the re-encryption key
     * is derived from both key pairs, allowing the proxy to transform the ciphertext.
     */
    public static byte[] generateReEncryptionKey(PrivateKey ownerPrivateKey, 
                                                 PublicKey requesterPublicKey) throws Exception {
        // In a production PRE scheme, this would involve more sophisticated operations.
        // For this implementation, we create a transformation key that allows
        // the proxy to re-encrypt without seeing the plaintext.
        
        // Simplified approach: The re-encryption key is a combination that allows
        // the proxy to transform the ciphertext. In practice, this would use
        // bilinear maps or other advanced cryptographic primitives.
        
        // For demonstration, we'll use a hybrid approach:
        // 1. Decrypt with owner's key (proxy doesn't have this, so we simulate)
        // 2. Re-encrypt with requester's key
        
        // In a real PRE system, the re-encryption key would be generated by the owner
        // and would allow the proxy to transform ciphertexts without decryption.
        // Here we simulate this by creating a key that represents the transformation.
        
        // For this implementation, we'll store the transformation parameters
        // that allow re-encryption. The actual re-encryption happens in reEncryptAESKey.
        
        // Generate a random component for the re-encryption key
        // In a full PRE scheme, this would be computed from the key pairs
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] reKey = new byte[256];
        random.nextBytes(reKey);
        
        // In practice, PRE schemes like BBS98 or AFGH use bilinear pairings
        // For this educational implementation, we use a simplified model
        return reKey;
    }
    
    /**
     * Re-encrypts an AES key from owner's encryption to requester's encryption.
     * 
     * This is the core PRE operation: the proxy can transform the ciphertext
     * without decrypting it, so the original AES key is never exposed.
     * 
     * @param encryptedAESKey The AES key encrypted with owner's public key
     * @param reEncryptionKey The re-encryption key generated by generateReEncryptionKey
     * @param ownerPublicKey Owner's public key (for verification)
     * @param requesterPublicKey Requester's public key (for re-encryption)
     * @return The AES key encrypted with requester's public key
     */
    public static byte[] reEncryptAESKey(byte[] encryptedAESKey, 
                                         byte[] reEncryptionKey,
                                         PublicKey ownerPublicKey,
                                         PublicKey requesterPublicKey) throws Exception {
        // In a full PRE implementation, this would use the re-encryption key
        // to transform the ciphertext directly without decryption.
        
        // For this simplified implementation, we demonstrate the concept:
        // 1. The proxy receives the encrypted AES key (encrypted for owner)
        // 2. Using the re-encryption key, it transforms it to be encrypted for requester
        // 3. The original AES key is never decrypted during this process
        
        // Simplified approach for demonstration:
        // In practice, PRE schemes allow this transformation without decryption.
        // Here we simulate by decrypting and re-encrypting, but in a real system,
        // the re-encryption key would allow direct ciphertext transformation.
        
        // For educational purposes, we'll decrypt with owner's key and re-encrypt
        // with requester's key. In a real PRE system, this would be done without
        // full decryption using bilinear pairings or other advanced techniques.
        
        // Note: In a production system, the proxy would not have the owner's private key.
        // The re-encryption key would be designed to allow transformation without it.
        // This is a simplified demonstration of the PRE concept.
        
        // Decrypt with owner's public key (in real PRE, this step wouldn't decrypt)
        // For this demo, we need the owner's private key to show the concept
        // In real PRE, the re-encryption key enables transformation without this
        
        // Re-encrypt with requester's public key
        // In a real PRE system, the re-encryption key would transform the ciphertext
        // directly to be decryptable by the requester's private key
        
        // For this implementation, we'll return a placeholder that represents
        // the re-encrypted key. In practice, this would be computed using the
        // re-encryption key to transform the ciphertext.
        
        // Since we can't implement full bilinear pairing PRE here, we'll use
        // a hybrid approach: the re-encryption process will decrypt and re-encrypt
        // but the key point is that the FILE is never decrypted, only the AES key.
        
        // For demonstration: We'll need to decrypt the AES key first
        // In a real PRE system, this would be transformed without decryption
        // This requires the owner's private key, which the proxy shouldn't have
        // in a true PRE system. For this demo, we'll simulate the transformation.
        
        // Return a transformed ciphertext (in real PRE, computed from encryptedAESKey
        // and reEncryptionKey without full decryption)
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, requesterPublicKey);
        
        // In a real PRE system, we would transform encryptedAESKey using reEncryptionKey
        // without decrypting. For this demo, we simulate by creating a new encryption.
        // The important point: the FILE remains encrypted, only the KEY is re-encrypted.
        
        // For this simplified implementation, we'll decrypt the original and re-encrypt
        // In production PRE, the re-encryption key allows ciphertext transformation
        // without this intermediate decryption step.
        
        // Since we need to demonstrate the concept, we'll use a workaround:
        // The re-encryption key in this simplified model allows us to extract
        // enough information to re-encrypt. In practice, PRE schemes are more complex.
        
        // For this educational implementation, we'll decrypt and re-encrypt
        // The key security property: the FILE is never decrypted, only the AES KEY
        // is re-encrypted from one user to another.
        
        // Extract the AES key bytes (in real PRE, this would be transformed, not extracted)
        // This is a limitation of this simplified implementation
        // In production, use a library that implements full PRE (e.g., using bilinear pairings)
        
        // For now, return a placeholder - in a real system this would be the transformed ciphertext
        // The important concept: PRE allows re-encryption without exposing the plaintext key
        return encryptedAESKey; // Placeholder - in real PRE this would be transformed
    }
    
    /**
     * Alternative implementation that properly demonstrates PRE concept.
     * Since full PRE requires advanced cryptography, we use a hybrid approach:
     * The owner provides a re-encryption token that allows the proxy to transform
     * the ciphertext without the owner's private key.
     */
    public static byte[] reEncryptAESKeyWithOwnerKey(byte[] encryptedAESKey,
                                                     PrivateKey ownerPrivateKey,
                                                     PublicKey requesterPublicKey) throws Exception {
        // This method demonstrates the PRE concept more clearly:
        // 1. Decrypt the AES key using owner's private key
        // 2. Re-encrypt it using requester's public key
        // 
        // In a full PRE system, steps 1 and 2 would be combined into a single
        // transformation using the re-encryption key, without exposing the plaintext.
        
        // Step 1: Decrypt AES key (in real PRE, this is transformed, not decrypted)
        Cipher decryptCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        decryptCipher.init(Cipher.DECRYPT_MODE, ownerPrivateKey);
        byte[] aesKeyBytes = decryptCipher.doFinal(encryptedAESKey);
        
        // Step 2: Re-encrypt for requester
        Cipher encryptCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        encryptCipher.init(Cipher.ENCRYPT_MODE, requesterPublicKey);
        return encryptCipher.doFinal(aesKeyBytes);
        
        // Key Security Property: The FILE itself is never decrypted during this process.
        // Only the small AES key (32 bytes) is re-encrypted. The file remains encrypted.
    }
    
    /**
     * Decrypts an AES key using the requester's private key.
     */
    public static SecretKey decryptAESKey(byte[] encryptedAESKey, PrivateKey requesterPrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, requesterPrivateKey);
        byte[] aesKeyBytes = cipher.doFinal(encryptedAESKey);
        return AESUtil.bytesToKey(aesKeyBytes);
    }
}

```

### FileStorageService.java

- **Path**: `src\main\java\SecureDataSharing\storage\FileStorageService.java`
- **What it is**: Encrypted file persistence and metadata store.
- **Why it matters**: This class shows how encrypted bytes and file metadata are written to disk and later recovered for secure download.

```
package SecureDataSharing.storage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File Storage Service for managing encrypted file storage.
 * 
 * Stores encrypted files on local disk (simulating cloud storage).
 * Files are stored with their metadata separately from encryption keys.
 */
public class FileStorageService {
    private String storageDirectory;
    private static final String METADATA_FILE = "data/file_metadata.json";
    private static final String DATA_DIR = "data";
    private Map<String, FileMetadata> fileMetadata; // fileId -> metadata
    
    public FileStorageService(String storageDirectory) {
        this.storageDirectory = storageDirectory;
        this.fileMetadata = new ConcurrentHashMap<>();
        
        // Create storage directory if it doesn't exist
        File dir = new File(storageDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Create data directory if it doesn't exist
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        // Load metadata from disk
        loadMetadata();
    }
    
    /**
     * Stores an encrypted file.
     * 
     * @param fileId Unique identifier for the file
     * @param encryptedData The encrypted file data
     * @param ownerUsername The username of the file owner
     * @param originalFileName The original filename
     * @return true if storage succeeded
     */
    public boolean storeFile(String fileId, byte[] encryptedData, 
                            String ownerUsername, String originalFileName) throws IOException {
        // Create file path
        Path filePath = Paths.get(storageDirectory, fileId + ".encrypted");
        
        // Write encrypted data to file
        Files.write(filePath, encryptedData);
        
        // Store metadata
        FileMetadata metadata = new FileMetadata(fileId, ownerUsername, originalFileName, 
                                                 filePath.toString(), encryptedData.length);
        fileMetadata.put(fileId, metadata);
        
        // Save metadata to disk
        saveMetadata();
        
        return true;
    }
    
    /**
     * Retrieves an encrypted file.
     */
    public byte[] retrieveFile(String fileId) throws IOException {
        FileMetadata metadata = fileMetadata.get(fileId);
        if (metadata == null) {
            throw new IOException("File not found: " + fileId);
        }
        
        Path filePath = Paths.get(metadata.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("Encrypted file not found on disk: " + fileId);
        }
        
        return Files.readAllBytes(filePath);
    }
    
    /**
     * Gets file metadata.
     */
    public FileMetadata getFileMetadata(String fileId) {
        return fileMetadata.get(fileId);
    }
    
    /**
     * Deletes a file and its metadata.
     */
    public boolean deleteFile(String fileId) throws IOException {
        FileMetadata metadata = fileMetadata.get(fileId);
        if (metadata != null) {
            Path filePath = Paths.get(metadata.getFilePath());
            Files.deleteIfExists(filePath);
            fileMetadata.remove(fileId);
            saveMetadata();
            return true;
        }
        return false;
    }
    
    /**
     * Checks if a file exists.
     */
    public boolean fileExists(String fileId) {
        return fileMetadata.containsKey(fileId);
    }
    
    /**
     * Gets all file metadata (for admin/manager views).
     */
    public java.util.Collection<FileMetadata> getAllFiles() {
        return fileMetadata.values();
    }
    
    /**
     * Gets all files owned by a specific user.
     */
    public java.util.List<FileMetadata> getFilesByOwner(String ownerUsername) {
        java.util.List<FileMetadata> result = new java.util.ArrayList<>();
        for (FileMetadata metadata : fileMetadata.values()) {
            if (metadata.getOwnerUsername().equals(ownerUsername)) {
                result.add(metadata);
            }
        }
        return result;
    }
    
    /**
     * Saves file metadata to disk.
     */
    private void saveMetadata() {
        try {
            JSONArray metadataArray = new JSONArray();
            for (FileMetadata metadata : fileMetadata.values()) {
                JSONObject metaJson = new JSONObject();
                metaJson.put("fileId", metadata.getFileId());
                metaJson.put("ownerUsername", metadata.getOwnerUsername());
                metaJson.put("originalFileName", metadata.getOriginalFileName());
                metaJson.put("filePath", metadata.getFilePath());
                metaJson.put("fileSize", metadata.getFileSize());
                metadataArray.put(metaJson);
            }
            
            try (FileWriter fileWriter = new FileWriter(METADATA_FILE)) {
                fileWriter.write(metadataArray.toString(2));
            }
        } catch (IOException e) {
            System.err.println("Error saving file metadata: " + e.getMessage());
        }
    }
    
    /**
     * Loads file metadata from disk.
     */
    private void loadMetadata() {
        File metadataFile = new File(METADATA_FILE);
        if (!metadataFile.exists()) {
            return; // No metadata file yet
        }
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(METADATA_FILE)));
            JSONArray metadataArray = new JSONArray(content);
            
            for (int i = 0; i < metadataArray.length(); i++) {
                JSONObject metaJson = metadataArray.getJSONObject(i);
                String fileId = metaJson.getString("fileId");
                String ownerUsername = metaJson.getString("ownerUsername");
                String originalFileName = metaJson.getString("originalFileName");
                String filePath = metaJson.getString("filePath");
                long fileSize = metaJson.getLong("fileSize");
                
                // Verify the encrypted file still exists
                if (Files.exists(Paths.get(filePath))) {
                    FileMetadata metadata = new FileMetadata(fileId, ownerUsername, 
                        originalFileName, filePath, fileSize);
                    fileMetadata.put(fileId, metadata);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading file metadata: " + e.getMessage());
        }
    }
    
    /**
     * Represents file metadata.
     */
    public static class FileMetadata {
        private String fileId;
        private String ownerUsername;
        private String originalFileName;
        private String filePath;
        private long fileSize;
        
        public FileMetadata(String fileId, String ownerUsername, String originalFileName,
                          String filePath, long fileSize) {
            this.fileId = fileId;
            this.ownerUsername = ownerUsername;
            this.originalFileName = originalFileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
        }
        
        public String getFileId() {
            return fileId;
        }
        
        public String getOwnerUsername() {
            return ownerUsername;
        }
        
        public String getOriginalFileName() {
            return originalFileName;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public long getFileSize() {
            return fileSize;
        }
    }
}

```

### FileController.java

- **Path**: `src\main\java\SecureDataSharing\web\controller\FileController.java`
- **What it is**: REST API for listing, uploading, deleting, downloading, and reading file policy data.
- **Why it matters**: It is the main business workflow controller for secure file handling. Upload and download both pass through here, making it one of the project highest-value modules.

```
package SecureDataSharing.web.controller;

import SecureDataSharing.auth.AuthService;
import SecureDataSharing.auth.User;
import SecureDataSharing.crypto.AESUtil;
import SecureDataSharing.crypto.KeyManager;
import SecureDataSharing.crypto.PREService;
import SecureDataSharing.storage.FileStorageService;
import SecureDataSharing.storage.FileStorageService.FileMetadata;
import SecureDataSharing.access.Policy;
import SecureDataSharing.audit.AuditLogger;
import SecureDataSharing.ml.MLMonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileStorageService fileStorage;

    @Autowired
    private KeyManager keyManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private MLMonitoringService mlMonitoringService;

    @Autowired
    private Map<String, Policy> policies;

    @Autowired(required = false)
    private SecureDataSharing.storage.PolicyStorageService policyStorage;

    @GetMapping("/my-files")
    public ResponseEntity<?> getMyFiles(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        try {
            java.util.Collection<FileMetadata> allFiles = fileStorage.getAllFiles();
            List<Map<String, Object>> myFiles = new ArrayList<>();
            for (FileMetadata file : allFiles) {
                if (file.getOwnerUsername().equals(username)) {
                    myFiles.add(fileToMap(file));
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("files", myFiles);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error loading files: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @Autowired
    private Map<String, SecureDataSharing.access.AccessRequest> accessRequests;

    @GetMapping("/available")
    public ResponseEntity<?> getAvailableFiles(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        try {
            User user = authService.getUser(username);
            java.util.Collection<FileMetadata> allFiles = fileStorage.getAllFiles();
            List<Map<String, Object>> availableFiles = new ArrayList<>();

            for (FileMetadata file : allFiles) {
                if (!file.getOwnerUsername().equals(username)) {
                    Map<String, Object> fileMap = fileToMap(file);

                    // Check access status
                    KeyManager.ReEncryptionKeyEntry reKey = keyManager.getReEncryptionKey(
                            file.getFileId(), username);

                    if (reKey != null && !reKey.isRevoked() && !reKey.isExpired()) {
                        fileMap.put("status", "Access Granted");
                    } else {
                        // Check if request exists
                        boolean isPending = false;
                        if (accessRequests != null) {
                            for (SecureDataSharing.access.AccessRequest req : accessRequests.values()) {
                                if (req.getFileId().equals(file.getFileId()) &&
                                        req.getRequesterUsername().equals(username) &&
                                        req.getStatus() == SecureDataSharing.access.AccessRequest.RequestStatus.PENDING) {
                                    isPending = true;
                                    break;
                                }
                            }
                        }

                        if (isPending) {
                            fileMap.put("status", "Pending Approval");
                        } else {
                            fileMap.put("status", "No Access");
                        }
                    }

                    availableFiles.add(fileMap);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("files", availableFiles);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error loading files: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("expiryValue") int expiryValue,
            @RequestParam("expiryUnit") String expiryUnit,
            @RequestParam("requiredRole") String requiredRole,
            @RequestParam(value = "requiredDepartment", required = false) String requiredDepartment,
            HttpSession session) {

        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        User user = authService.getUser(username);
        String userRole = getUserRole(user);

        // Only managers and admins can upload
        if (!"manager".equalsIgnoreCase(userRole) && !"admin".equalsIgnoreCase(userRole)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Only managers and admins can upload files");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        try {
            // Generate file ID
            String fileId = "FILE_" + System.currentTimeMillis() + "_" +
                    new Random().nextInt(10000);

            // Encrypt file
            byte[] fileData = file.getBytes();
            AESUtil.EncryptionResult encryptionResult = AESUtil.encryptFile(fileData);

            // Store encrypted file
            fileStorage.storeFile(fileId, encryptionResult.getEncryptedData(),
                    username, file.getOriginalFilename());

            // Encrypt AES key with owner's public key
            byte[] encryptedAESKey = PREService.encryptAESKey(encryptionResult.getKey(), user.getPublicKey());

            // Store file key
            Date expiryDate = calculateExpiryDate(expiryValue, expiryUnit);
            keyManager.storeFileKey(fileId, encryptedAESKey, user.getPublicKey(), expiryDate);

            // Create policy
            Policy policy = new Policy(fileId);
            policy.addRequiredAttribute(new SecureDataSharing.access.Attribute("role", requiredRole));
            if (requiredDepartment != null && !requiredDepartment.isEmpty()) {
                policy.addRequiredAttribute(new SecureDataSharing.access.Attribute("department", requiredDepartment));
            }
            policies.put(fileId, policy);

            // Save policies
            try {
                policyStorage.savePolicies(policies);
            } catch (Exception e) {
                System.err.println("Error saving policies: " + e.getMessage());
            }

            auditLogger.log("FILE_UPLOADED", username,
                    "Uploaded file: " + file.getOriginalFilename() + " (ID: " + fileId + ")");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileId", fileId);
            response.put("message", "File uploaded successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error uploading file: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileId, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        try {
            FileMetadata metadata = fileStorage.getFileMetadata(fileId);
            if (metadata == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File not found");
                return ResponseEntity.badRequest().body(error);
            }

            if (!metadata.getOwnerUsername().equals(username)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "You can only delete files that you uploaded");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            // Delete file
            fileStorage.deleteFile(fileId);
            keyManager.deleteFileKey(fileId);
            policies.remove(fileId);

            // Save policies
            if (policyStorage != null) {
                try {
                    policyStorage.savePolicies(policies);
                } catch (Exception e) {
                    System.err.println("Error saving policies: " + e.getMessage());
                }
            }

            auditLogger.log("FILE_DELETED", username,
                    "Deleted file: " + fileId);

            Map<String, Boolean> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error deleting file: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<?> downloadFile(@PathVariable String fileId, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        try {
            User user = authService.getUser(username);

            // Check access
            KeyManager.ReEncryptionKeyEntry reKeyEntry = keyManager.getReEncryptionKey(
                    fileId, username);
            if (reKeyEntry == null || reKeyEntry.isRevoked() || reKeyEntry.isExpired()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No valid access. Please request access and wait for approval.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            // Decrypt AES key
            byte[] reEncryptedAESKey = reKeyEntry.getReEncryptionKey();
            SecretKey aesKey = PREService.decryptAESKey(reEncryptedAESKey, user.getPrivateKey());

            // Retrieve and decrypt file
            byte[] encryptedData = fileStorage.retrieveFile(fileId);
            byte[] decryptedData = AESUtil.decrypt(encryptedData, aesKey);

            FileMetadata metadata = fileStorage.getFileMetadata(fileId);

            auditLogger.log("FILE_DOWNLOADED", username,
                    "Downloaded file: " + fileId);

            // ML Check for file download
            mlMonitoringService.checkAnomaly(username, "download_file");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", metadata.getOriginalFileName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(decryptedData);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error downloading file: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{fileId}/policy")
    public ResponseEntity<?> getPolicy(@PathVariable String fileId, HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        Policy policy = policies.get(fileId);
        if (policy == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Policy not found");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> policyMap = new HashMap<>();
        policyMap.put("resourceId", policy.getResourceId());
        List<Map<String, String>> attributes = new ArrayList<>();
        for (SecureDataSharing.access.Attribute attr : policy.getRequiredAttributes()) {
            Map<String, String> attrMap = new HashMap<>();
            attrMap.put("name", attr.getName());
            attrMap.put("value", attr.getValue());
            attributes.add(attrMap);
        }
        policyMap.put("requiredAttributes", attributes);
        policyMap.put("validFrom", policy.getValidFrom());
        policyMap.put("validUntil", policy.getValidUntil());

        return ResponseEntity.ok(policyMap);
    }

    private Map<String, Object> fileToMap(FileMetadata file) {
        Map<String, Object> map = new HashMap<>();
        map.put("fileId", file.getFileId());
        map.put("fileName", file.getOriginalFileName());
        map.put("fileSize", file.getFileSize());
        map.put("owner", file.getOwnerUsername());
        return map;
    }

    private String getUserRole(User user) {
        for (SecureDataSharing.access.Attribute attr : user.getAttributes()) {
            if ("role".equalsIgnoreCase(attr.getName())) {
                return attr.getValue().toLowerCase();
            }
        }
        return "user";
    }

    private Date calculateExpiryDate(int value, String unit) {
        Calendar cal = Calendar.getInstance();
        switch (unit.toLowerCase()) {
            case "seconds":
                cal.add(Calendar.SECOND, value);
                break;
            case "minutes":
                cal.add(Calendar.MINUTE, value);
                break;
            case "hours":
                cal.add(Calendar.HOUR, value);
                break;
            case "days":
                cal.add(Calendar.DAY_OF_MONTH, value);
                break;
            default:
                cal.add(Calendar.HOUR, 24); // Default 24 hours
        }
        return cal.getTime();
    }
}

```

## Monitoring and Dynamic Configuration

These modules add anomaly detection, administrator alerting, and runtime configuration loading for UI and email behavior.

Support submodules worth knowing about:
- SecureDataSharing.web.controller.ConfigController.java - serves configuration to the UI
- src/main/resources/static/js/app.js - front-end logic for the dashboard and role-based flows
- src/main/resources/static/index.html - main single-page UI shell

### MLMonitoringService.java

- **Path**: `src\main\java\SecureDataSharing\ml\MLMonitoringService.java`
- **What it is**: Java-side ML integration that extracts features and calls the Python detector.
- **Why it matters**: This service bridges the core app and the ML microservice, and it triggers security alerts without blocking normal application flow when the ML side is down.

```
package SecureDataSharing.ml;

import SecureDataSharing.auth.AuthService;
import SecureDataSharing.auth.User;
import SecureDataSharing.email.EmailService;
import SecureDataSharing.config.ConfigManager;
import SecureDataSharing.storage.FileStorageService;
import SecureDataSharing.access.AccessRequest;
import SecureDataSharing.audit.AuditLogger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class MLMonitoringService {

    @Autowired
    private AuthService authService;

    @Autowired
    private FileStorageService fileStorage;

    @Autowired
    private Map<String, AccessRequest> accessRequests;

    @Autowired
    private AuditLogger auditLogger;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String ML_API_URL = "http://localhost:5000/detect";

    /**
     * Checks user behavior anomalies by extracting features and calling the ML API.
     * Returns true if suspicious behavior was detected, else false.
     */
    public MLPredictionResponse checkAnomaly(String username, String actionType) {
        try {
            User user = authService.getUser(username);
            if (user == null) {
                return null;
            }

            // 1. login_hour
            double loginHour = LocalDateTime.now().getHour();

            // 2. request_count (total requests made by this user)
            long requestCount = getRequestCountByRequester(username);

            // 3. file_access_count (number of files owned by this user)
            long fileAccessCount = fileStorage.getFilesByOwner(username).size();

            // 4. approval_rate (approved / total requests made by this user)
            long approvedCount = getApprovedRequestCount(username);
            double approvalRate = requestCount == 0 ? 0.0 : (double) approvedCount / requestCount;

            // 5. file_size_avg (average size of files owned)
            double avgFileSize = getAverageFileSize(username);

            // 6. role
            double role = getUserRole(user).equalsIgnoreCase("manager") ? 1.0 : 0.0;

            List<Double> features = Arrays.asList(
                    loginHour,
                    (double) requestCount,
                    (double) fileAccessCount,
                    approvalRate,
                    avgFileSize,
                    role
            );

            MLPredictionRequest request = new MLPredictionRequest(features);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<MLPredictionRequest> entity = new HttpEntity<>(request, headers);
            
            // Call Python ML Service
            ResponseEntity<MLPredictionResponse> responseEntity = restTemplate.postForEntity(
                    ML_API_URL, entity, MLPredictionResponse.class);

            MLPredictionResponse response = responseEntity.getBody();

            if (response != null && "SUSPICIOUS BEHAVIOR DETECTED".equals(response.getStatus())) {
                handleSuspiciousActivity(username, actionType, response);
            }

            return response;
        } catch (Exception e) {
            System.err.println("Error calling ML API: " + e.getMessage());
            // If ML Service fails, we fail open (do not block normal operations)
            MLPredictionResponse errorResponse = new MLPredictionResponse();
            errorResponse.setError("ML API Offline: " + e.getMessage());
            return errorResponse;
        }
    }

    private void handleSuspiciousActivity(String username, String actionType, MLPredictionResponse response) {
        String adminEmail = ConfigManager.getInstance().getProperty("email.admin.address", "");
        String reasonsStr = String.join(", ", response.getReasons());
        
        String logMessage = String.format("Suspicious activity detected for user %s during %s. Risk Score: %d. Reasons: %s", 
                                          username, actionType, response.getRisk_score(), reasonsStr);
        auditLogger.log("SECURITY_ALERT", username, logMessage);

        if (!adminEmail.isEmpty()) {
            String subject = "Security Alert: Suspicious Activity Detected (" + username + ")";
            String body = "SafeLink Security System detected unusual behavior.\n\n" +
                          "User: " + username + "\n" +
                          "Action: " + actionType + "\n" +
                          "Risk Score: " + response.getRisk_score() + "\n" +
                          "Reasons: " + reasonsStr + "\n\n" +
                          "Please review the system logs.";
            
            EmailService.getInstance().sendNotification(adminEmail, subject, body);
        }
    }

    private long getRequestCountByRequester(String username) {
        if (accessRequests == null) return 0;
        return accessRequests.values().stream()
                .filter(req -> username.equals(req.getRequesterUsername()))
                .count();
    }

    private long getApprovedRequestCount(String username) {
        if (accessRequests == null) return 0;
        return accessRequests.values().stream()
                .filter(req -> username.equals(req.getRequesterUsername()) &&
                        AccessRequest.RequestStatus.APPROVED == req.getStatus())
                .count();
    }

    private double getAverageFileSize(String username) {
        List<FileStorageService.FileMetadata> files = fileStorage.getFilesByOwner(username);
        if (files.isEmpty()) return 0.0;
        
        long totalSize = files.stream()
                .mapToLong(FileStorageService.FileMetadata::getFileSize)
                .sum();
                
        return (double) totalSize / files.size();
    }

    private String getUserRole(User user) {
        for (SecureDataSharing.access.Attribute attr : user.getAttributes()) {
            if ("role".equalsIgnoreCase(attr.getName())) {
                return attr.getValue().toLowerCase();
            }
        }
        return "user";
    }
}

```

### ml-service/app.py

- **Path**: `ml-service\app.py`
- **What it is**: Flask anomaly-detection microservice using a trained scikit-learn model.
- **Why it matters**: This is the other half of the monitoring design. It scores user behavior, labels suspicious activity, logs security events, and sends administrator emails.

```
from flask import Flask, request, jsonify
import joblib
import datetime
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import json
import os

app = Flask(__name__)

model = joblib.load("safelinkmodel.pkl")


CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'config.json')

def load_config():
    try:
        with open(CONFIG_PATH, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading config: {e}")
        return None

def send_admin_email(result):
    config = load_config() or {}
    if not config.get('email', {}).get('enabled', False):
        return
    
    email_cfg = config.get('email', {})
    smtp_cfg = email_cfg.get('smtp', {})
    from_cfg = email_cfg.get('from', {})
    admin_cfg = email_cfg.get('admin', {})
    alert_template = admin_cfg.get('alert_template', {})

    msg = MIMEMultipart()
    msg['From'] = f"{from_cfg.get('name', 'SafeLink')} <{from_cfg.get('address', '')}>"
    msg['To'] = admin_cfg.get('address', '')
    
    # Use dynamic subject from config
    msg['Subject'] = alert_template.get('subject', "ðŸš¨ ALERT: Suspicious Behavior Detected!")

    reasons_str = '\n'.join(f"- {r}" for r in result.get('reasons', []))
    
    # Process string replacements for the template loaded from config
    template_str = alert_template.get('body', {}).get('template', "")
    if template_str:
        body = (template_str.replace("{USERNAME}", str(result.get('username')))
                            .replace("{USER_ID}", str(result.get('user_id')))
                            .replace("{ROLE}", str(result.get('role')))
                            .replace("{ML_SCORE}", str(result.get('ml_score')))
                            .replace("{RISK_SCORE}", str(result.get('risk_score')))
                            .replace("{REASONS}", reasons_str))
    else:
        # Fallback if template is missing from config
        body = f"Suspicious behavior detected.\n\nUsername: {result.get('username')}\nReasons:\n{reasons_str}"

    msg.attach(MIMEText(body, 'plain'))

    try:
        server = smtplib.SMTP(smtp_cfg.get('host', 'smtp.gmail.com'), smtp_cfg.get('port', 587))
        if smtp_cfg.get('starttls', {}).get('enable', False):
            server.starttls()
        if smtp_cfg.get('auth', False):
            server.login(from_cfg.get('address', ''), from_cfg.get('password', ''))
        server.send_message(msg)
        server.quit()
        print("Admin email sent successfully.")
    except Exception as e:
        print(f"Failed to send admin email: {e}")

# ðŸ“ Simple log function
def log_event(data):
    with open("ml_audit.log", "a") as f:
        f.write(f"{datetime.datetime.now()} | {data}\n")


# ðŸ” Risk + Reason Engine
def analyze(features):
    reasons = []
    risk = 0

    login_hour, request_count, file_access, approval_rate, file_size, role = features

    if login_hour < 9 or login_hour > 19:
        reasons.append("Unusual login time")
        risk += 25

    if request_count > 10:
        reasons.append("High number of access requests")
        risk += 20

    if file_access > 5:
        reasons.append("Excessive file access")
        risk += 20

    if approval_rate < 0.5:
        reasons.append("Low approval rate")
        risk += 15

    if file_size > 600_000_000:
        reasons.append("Unusually large file access")
        risk += 15

    return risk, reasons


@app.route("/detect", methods=["POST"])
def detect():
    try:
        data = request.json

        # ðŸ‘¤ Identity Info
        username = data.get("username", "unknown")
        user_id = data.get("user_id", "unknown")
        user_role = data.get("role", "unknown")

        features = data["features"]

        # ðŸ¤– ML Prediction
        prediction = model.predict([features])[0]
        score = model.decision_function([features])[0]

        # ðŸ“Š Risk Analysis
        risk_score, reasons = analyze(features)

        # ðŸ§  Final Decision
        if prediction == -1 or risk_score > 60:
            status = "SUSPICIOUS BEHAVIOR DETECTED"

            if prediction == -1 and len(reasons) == 0:
                reasons.append("Anomalous behavior detected by ML model")

        else:
            status = "NORMAL BEHAVIOR"

        result = {
            "username": username,
            "user_id": user_id,
            "role": user_role,
            "status": status,
            "ml_score": float(score),
            "risk_score": risk_score,
            "reasons": reasons
        }

        # ðŸ“ Log ONLY suspicious events (recommended)
        if status == "SUSPICIOUS BEHAVIOR DETECTED":
            log_event(result)
            
            # ðŸ“§ Send email alert to admin asynchronously
            import threading
            threading.Thread(target=send_admin_email, args=(result,)).start()

        return jsonify(result)

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(port=5000, debug=True)
```

### ConfigManager.java

- **Path**: `src\main\java\SecureDataSharing\config\ConfigManager.java`
- **What it is**: JSON configuration loader with dot-path lookups and defaults.
- **Why it matters**: This module makes email, OTP, and UI-related behavior configurable through config.json rather than hard-coding values everywhere.

```
package SecureDataSharing.config;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Configuration Manager for loading and accessing application settings from JSON.
 */
public class ConfigManager {
    private static ConfigManager instance;
    private JSONObject config;
    
    private ConfigManager() {
        loadConfig();
    }
    
    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    private void loadConfig() {
        try {
            // Try to load from config directory first
            InputStream inputStream = new FileInputStream("config.json");
            config = new JSONObject(new JSONTokener(inputStream));
            inputStream.close();
        } catch (IOException e) {
            // If file not found, try loading from classpath
            try {
                InputStream inputStream = getClass().getClassLoader()
                    .getResourceAsStream("config.json");
                if (inputStream != null) {
                    config = new JSONObject(new JSONTokener(inputStream));
                    inputStream.close();
                } else {
                    System.err.println("Configuration file not found. Using defaults.");
                    loadDefaults();
                }
            } catch (IOException ex) {
                System.err.println("Error loading configuration: " + ex.getMessage());
                loadDefaults();
            }
        } catch (Exception e) {
            System.err.println("Error parsing JSON configuration: " + e.getMessage());
            loadDefaults();
        }
    }
    
    private void loadDefaults() {
        config = new JSONObject();
        JSONObject app = new JSONObject();
        JSONObject window = new JSONObject();
        window.put("width", 1400);
        window.put("height", 900);
        app.put("window", window);
        config.put("app", app);
        
        JSONObject theme = new JSONObject();
        theme.put("primary", new JSONObject().put("color", "#2C3E50"));
        theme.put("secondary", new JSONObject().put("color", "#3498DB"));
        theme.put("accent", new JSONObject().put("color", "#E74C3C"));
        theme.put("success", new JSONObject().put("color", "#27AE60"));
        theme.put("background", new JSONObject().put("color", "#ECF0F1"));
        config.put("theme", theme);
        
        JSONObject email = new JSONObject();
        email.put("enabled", false);
        config.put("email", email);
        
        JSONObject otp = new JSONObject();
        otp.put("length", 6);
        otp.put("validity", new JSONObject().put("minutes", 5));
        config.put("otp", otp);
    }
    
    /**
     * Gets a property value using dot notation (e.g., "app.window.width")
     */
    public String getProperty(String key) {
        return getProperty(key, null);
    }
    
    /**
     * Gets a property value using dot notation with default value
     */
    public String getProperty(String key, String defaultValue) {
        try {
            Object value = getNestedValue(key);
            return value != null ? value.toString() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Gets an integer property value
     */
    public int getIntProperty(String key, int defaultValue) {
        try {
            Object value = getNestedValue(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value != null) {
                return Integer.parseInt(value.toString());
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean property value
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        try {
            Object value = getNestedValue(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Navigates through nested JSON objects using dot notation
     */
    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        JSONObject current = config;
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (current.has(parts[i])) {
                Object obj = current.get(parts[i]);
                if (obj instanceof JSONObject) {
                    current = (JSONObject) obj;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        String lastKey = parts[parts.length - 1];
        if (current.has(lastKey)) {
            return current.get(lastKey);
        }
        
        return null;
    }
    
    public void reload() {
        loadConfig();
    }
    
    /**
     * Gets the raw JSON object for advanced access
     */
    public JSONObject getConfig() {
        return config;
    }
}

```


