package SecureDataSharing.storage;

import SecureDataSharing.access.AccessRequest;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * Service for persisting and loading access requests from MongoDB.
 */
public class AccessRequestStorageService {
    private final MongoCollection<Document> requestsCollection;

    public AccessRequestStorageService() {
        this.requestsCollection = MongoManager.getInstance().getCollection("access_requests", "access_requests");
    }

    /**
     * Saves all access requests to MongoDB.
     */
    public void saveAccessRequests(Map<String, AccessRequest> accessRequests) throws IOException {
        requestsCollection.deleteMany(new Document());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (AccessRequest request : accessRequests.values()) {
            Document requestDoc = new Document("requestId", request.getRequestId())
                    .append("requesterUsername", request.getRequesterUsername())
                    .append("fileId", request.getFileId())
                    .append("ownerUsername", request.getOwnerUsername())
                    .append("status", request.getStatus().name())
                    .append("reason", request.getReason());

            if (request.getRequestDate() != null) {
                requestDoc.append("requestDate", sdf.format(request.getRequestDate()));
            }

            requestsCollection.insertOne(requestDoc);
        }
    }

    /**
     * Loads all access requests from MongoDB.
     */
    public void loadAccessRequests(Map<String, AccessRequest> accessRequests) throws IOException {
        accessRequests.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Document requestDoc : requestsCollection.find()) {
            String requesterUsername = requestDoc.getString("requesterUsername");
            String fileId = requestDoc.getString("fileId");
            String ownerUsername = requestDoc.getString("ownerUsername");

            AccessRequest request = new AccessRequest(requesterUsername, fileId, ownerUsername);

            try {
                setField(request, "requestId", requestDoc.getString("requestId"));

                String requestDateStr = requestDoc.getString("requestDate");
                if (requestDateStr != null && !requestDateStr.isEmpty()) {
                    setField(request, "requestDate", sdf.parse(requestDateStr));
                }
            } catch (Exception e) {
                throw new IOException("Failed restoring access request fields", e);
            }

            String statusStr = requestDoc.getString("status");
            if (statusStr != null && !statusStr.isEmpty()) {
                try {
                    request.setStatus(AccessRequest.RequestStatus.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid access request status for " + request.getRequestId() + ": " + statusStr);
                }
            }

            String reason = requestDoc.getString("reason");
            if (reason != null) {
                request.setReason(reason);
            }

            String key = request.getRequestId() != null ? request.getRequestId() : fileId + ":" + requesterUsername;
            accessRequests.put(key, request);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
