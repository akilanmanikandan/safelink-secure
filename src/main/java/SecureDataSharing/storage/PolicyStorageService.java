package SecureDataSharing.storage;

import SecureDataSharing.access.Attribute;
import SecureDataSharing.access.Policy;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for persisting and loading policies from MongoDB.
 */
public class PolicyStorageService {
    private final MongoCollection<Document> policiesCollection;

    public PolicyStorageService() {
        this.policiesCollection = MongoManager.getInstance().getCollection("policies", "policies");
    }

    /**
     * Saves all policies to MongoDB.
     */
    public void savePolicies(Map<String, Policy> policies) throws IOException {
        policiesCollection.deleteMany(new Document());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Policy policy : policies.values()) {
            Document policyDoc = new Document("resourceId", policy.getResourceId())
                    .append("revoked", policy.isRevoked());

            if (policy.getValidFrom() != null) {
                policyDoc.append("validFrom", sdf.format(policy.getValidFrom()));
            }
            if (policy.getValidUntil() != null) {
                policyDoc.append("validUntil", sdf.format(policy.getValidUntil()));
            }

            List<Document> attributes = new ArrayList<>();
            for (Attribute attr : policy.getRequiredAttributes()) {
                attributes.add(new Document("name", attr.getName())
                        .append("value", attr.getValue()));
            }
            policyDoc.append("requiredAttributes", attributes);

            policiesCollection.insertOne(policyDoc);
        }
    }

    /**
     * Loads all policies from MongoDB.
     */
    public void loadPolicies(Map<String, Policy> policies) throws IOException {
        policies.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Document policyDoc : policiesCollection.find()) {
            String resourceId = policyDoc.getString("resourceId");
            String validUntilStr = policyDoc.getString("validUntil");

            Policy policy;
            if (validUntilStr != null && !validUntilStr.isEmpty()) {
                try {
                    policy = new Policy(resourceId, sdf.parse(validUntilStr));
                } catch (ParseException e) {
                    System.err.println("Error parsing validUntil for policy " + resourceId + ": " + e.getMessage());
                    policy = new Policy(resourceId);
                }
            } else {
                policy = new Policy(resourceId);
            }

            String validFromStr = policyDoc.getString("validFrom");
            if (validFromStr != null && !validFromStr.isEmpty()) {
                try {
                    setField(policy, "validFrom", sdf.parse(validFromStr));
                } catch (Exception e) {
                    System.err.println("Error restoring validFrom for policy " + resourceId + ": " + e.getMessage());
                }
            }

            Boolean revoked = policyDoc.getBoolean("revoked");
            if (revoked != null && revoked) {
                policy.revoke();
            }

            List<Document> attributes = (List<Document>) policyDoc.get("requiredAttributes");
            if (attributes != null) {
                for (Document attrDoc : attributes) {
                    policy.addRequiredAttribute(new Attribute(attrDoc.getString("name"), attrDoc.getString("value")));
                }
            }

            policies.put(resourceId, policy);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
