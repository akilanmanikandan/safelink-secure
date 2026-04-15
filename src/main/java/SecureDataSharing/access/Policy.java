package SecureDataSharing.access;

import java.util.Set;
import java.util.HashSet;
import java.util.Date;

/**
 * Represents an access policy for ABAC.
 * Defines which attributes are required to access a resource.
 */
public class Policy {
    private String resourceId;
    private Set<Attribute> requiredAttributes;
    private Date validFrom;
    private Date validUntil;
    private boolean revoked;
    
    public Policy(String resourceId) {
        this.resourceId = resourceId;
        this.requiredAttributes = new HashSet<>();
        this.validFrom = new Date();
        this.validUntil = null; // No expiry by default
        this.revoked = false;
    }
    
    public Policy(String resourceId, Date validUntil) {
        this(resourceId);
        this.validUntil = validUntil;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public Set<Attribute> getRequiredAttributes() {
        return requiredAttributes;
    }
    
    public void addRequiredAttribute(Attribute attribute) {
        this.requiredAttributes.add(attribute);
    }
    
    public Date getValidFrom() {
        return validFrom;
    }
    
    public Date getValidUntil() {
        return validUntil;
    }
    
    public void setValidUntil(Date validUntil) {
        this.validUntil = validUntil;
    }
    
    public boolean isRevoked() {
        return revoked;
    }
    
    public void revoke() {
        this.revoked = true;
    }
    
    /**
     * Checks if the policy is currently valid (not revoked and within time bounds).
     */
    public boolean isValid() {
        if (revoked) {
            return false;
        }
        Date now = new Date();
        if (validUntil != null && now.after(validUntil)) {
            return false;
        }
        return now.after(validFrom) || now.equals(validFrom);
    }
}
