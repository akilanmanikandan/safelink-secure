package SecureDataSharing.auth;

import SecureDataSharing.access.Attribute;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;

/**
 * Represents a user in the system with authentication credentials,
 * cryptographic keys, and attributes for ABAC.
 */
public class User {
    private String username;
    private String passwordHash;
    private KeyPair keyPair; // RSA key pair for PRE
    private Set<Attribute> attributes;
    private Date registrationDate;
    private boolean mfaEnabled;
    private String mfaSecret; // For OTP generation
    private String email; // User email for OTP delivery
    
    public User(String username, String passwordHash, KeyPair keyPair) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.keyPair = keyPair;
        this.attributes = new HashSet<>();
        this.registrationDate = new Date();
        this.mfaEnabled = false;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }
    
    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }
    
    public KeyPair getKeyPair() {
        return keyPair;
    }
    
    public Set<Attribute> getAttributes() {
        return attributes;
    }
    
    public void addAttribute(Attribute attribute) {
        this.attributes.add(attribute);
    }
    
    public void removeAttribute(Attribute attribute) {
        this.attributes.remove(attribute);
    }
    
    public boolean isMfaEnabled() {
        return mfaEnabled;
    }
    
    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }
    
    public String getMfaSecret() {
        return mfaSecret;
    }
    
    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
    }
    
    public Date getRegistrationDate() {
        return registrationDate;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
}
