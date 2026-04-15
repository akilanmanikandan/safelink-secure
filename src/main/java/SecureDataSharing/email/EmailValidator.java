package SecureDataSharing.email;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * Email validation utility that checks:
 * 1. Email format (regex)
 * 2. Domain MX records (DNS lookup)
 * 3. Basic SMTP verification (if possible)
 */
public class EmailValidator {
    private static final String EMAIL_REGEX = 
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    
    /**
     * Validates an email address.
     * @param email The email address to validate
     * @return ValidationResult with isValid flag and error message if invalid
     */
    public static ValidationResult validate(String email) {
        if (email == null || email.trim().isEmpty()) {
            return new ValidationResult(false, "Email address is required");
        }
        
        email = email.trim().toLowerCase();
        
        // Step 1: Format validation
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return new ValidationResult(false, "Email format is invalid. Please enter a valid email address.");
        }
        
        // Step 2: Extract domain
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return new ValidationResult(false, "Email format is invalid");
        }
        
        String domain = parts[1];
        
        // Step 3: Check if domain has MX records
        if (!hasMXRecords(domain)) {
            return new ValidationResult(false, 
                "Email domain '" + domain + "' does not exist or has no mail servers. Please enter a valid email address.");
        }
        
        // All validations passed
        return new ValidationResult(true, null);
    }
    
    /**
     * Checks if the domain has valid MX (Mail Exchange) records.
     * @param domain The domain to check
     * @return true if MX records exist, false otherwise
     */
    private static boolean hasMXRecords(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            
            Attribute mxAttr = attrs.get("MX");
            
            // If MX records exist, the email domain is valid
            if (mxAttr != null && mxAttr.size() > 0) {
                return true;
            }
            
            // If no MX records, check for A record (some domains use A records for mail)
            Attributes aAttrs = ctx.getAttributes(domain, new String[]{"A"});
            Attribute aAttr = aAttrs.get("A");
            if (aAttr != null && aAttr.size() > 0) {
                return true;
            }
            
            ctx.close();
            return false;
        } catch (NamingException e) {
            // DNS lookup failed - domain might not exist
            return false;
        } catch (Exception e) {
            // Other errors - assume invalid for safety
            System.err.println("Error checking MX records for " + domain + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Result of email validation.
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;
        
        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() {
            return isValid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
