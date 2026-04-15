package SecureDataSharing.audit;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Secure Audit Logger for logging all critical security events.
 * 
 * Logs events such as:
 * - User registration and login
 * - File upload and encryption
 * - Access requests and ABAC evaluations
 * - Proxy re-encryption operations
 * - Key lifecycle events (expiry, revocation)
 * - File decryption
 */
public class AuditLogger {
    private String logFilePath;
    private PrintWriter logWriter;
    private SimpleDateFormat dateFormat;
    private ReentrantLock lock;
    
    public AuditLogger(String logFilePath) throws IOException {
        this.logFilePath = logFilePath;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        this.lock = new ReentrantLock();
        
        // Open log file in append mode
        FileWriter fileWriter = new FileWriter(logFilePath, true);
        this.logWriter = new PrintWriter(fileWriter, true);
        
        // Log initialization
        log("SYSTEM", "SYSTEM", "Audit logger initialized");
    }
    
    /**
     * Logs a security event.
     * 
     * @param eventType The type of event (e.g., "LOGIN", "ENCRYPTION", "ACCESS_REQUEST")
     * @param username The username associated with the event
     * @param details Additional details about the event
     */
    public void log(String eventType, String username, String details) {
        lock.lock();
        try {
            String timestamp = dateFormat.format(new Date());
            String logEntry = String.format("[%s] [%s] [%s] %s", 
                                          timestamp, eventType, username, details);
            
            // Write to file
            logWriter.println(logEntry);
            logWriter.flush();
            
            // Also print to console for visibility
            System.out.println(logEntry);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Logs a file-related event with file ID.
     */
    public void logFileEvent(String eventType, String username, String fileId, String details) {
        log(eventType, username, String.format("FileID=%s, %s", fileId, details));
    }
    
    /**
     * Logs an encryption event.
     */
    public void logEncryption(String username, String fileId, String algorithm) {
        logFileEvent("ENCRYPTION", username, fileId, 
                    String.format("Algorithm=%s, File encrypted successfully", algorithm));
    }
    
    /**
     * Logs a decryption event.
     */
    public void logDecryption(String username, String fileId) {
        logFileEvent("DECRYPTION", username, fileId, "File decrypted successfully");
    }
    
    /**
     * Logs an access request.
     */
    public void logAccessRequest(String requester, String fileId, String owner) {
        logFileEvent("ACCESS_REQUEST", requester, fileId, 
                    String.format("Requested access to file owned by %s", owner));
    }
    
    /**
     * Logs an ABAC evaluation result.
     */
    public void logABACEvaluation(String username, String fileId, boolean granted, String reason) {
        logFileEvent("ABAC_EVALUATION", username, fileId, 
                    String.format("Access %s - %s", granted ? "GRANTED" : "DENIED", reason));
    }
    
    /**
     * Logs a proxy re-encryption event.
     */
    public void logReEncryption(String requester, String fileId, String owner) {
        logFileEvent("RE_ENCRYPTION", requester, fileId, 
                    String.format("AES key re-encrypted from %s to %s", owner, requester));
    }
    
    /**
     * Logs a key lifecycle event.
     */
    public void logKeyLifecycle(String eventType, String username, String fileId, String details) {
        logFileEvent("KEY_LIFECYCLE_" + eventType, username, fileId, details);
    }
    
    /**
     * Closes the audit logger.
     */
    public void close() {
        lock.lock();
        try {
            if (logWriter != null) {
                logWriter.close();
            }
        } finally {
            lock.unlock();
        }
    }
}
