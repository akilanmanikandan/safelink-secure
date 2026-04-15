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
