package SecureDataSharing.access;

import java.util.Objects;

/**
 * Represents an attribute for Attribute-Based Access Control (ABAC).
 * Attributes define user properties like role, department, clearance level, etc.
 */
public class Attribute {
    private String name;
    private String value;
    
    public Attribute(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    public String getName() {
        return name;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attribute attribute = (Attribute) o;
        return Objects.equals(name, attribute.name) && 
               Objects.equals(value, attribute.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
    
    @Override
    public String toString() {
        return name + "=" + value;
    }
}
