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
