package SecureDataSharing.access;

import java.util.Date;

/**
 * Represents a pending access request from a user to a file owner.
 */
public class AccessRequest {
    private String requestId;
    private String requesterUsername;
    private String fileId;
    private String ownerUsername;
    private Date requestDate;
    private RequestStatus status;
    private String reason; // Reason for approval/denial
    
    public enum RequestStatus {
        PENDING,
        APPROVED,
        DENIED
    }
    
    public AccessRequest(String requesterUsername, String fileId, String ownerUsername) {
        this.requestId = "REQ_" + System.currentTimeMillis() + "_" + 
            new java.util.Random().nextInt(10000);
        this.requesterUsername = requesterUsername;
        this.fileId = fileId;
        this.ownerUsername = ownerUsername;
        this.requestDate = new Date();
        this.status = RequestStatus.PENDING;
        this.reason = "";
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getRequesterUsername() {
        return requesterUsername;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public String getOwnerUsername() {
        return ownerUsername;
    }
    
    public Date getRequestDate() {
        return requestDate;
    }
    
    public RequestStatus getStatus() {
        return status;
    }
    
    public void setStatus(RequestStatus status) {
        this.status = status;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }
    
    public boolean isApproved() {
        return status == RequestStatus.APPROVED;
    }
    
    public boolean isDenied() {
        return status == RequestStatus.DENIED;
    }
}
