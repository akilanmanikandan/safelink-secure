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
