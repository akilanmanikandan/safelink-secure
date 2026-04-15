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
