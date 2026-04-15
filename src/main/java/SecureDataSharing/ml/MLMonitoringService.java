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
