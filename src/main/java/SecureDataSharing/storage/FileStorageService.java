package SecureDataSharing.storage;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File Storage Service for managing encrypted file storage.
 * 
 * Stores encrypted files on local disk (simulating cloud storage).
 * Files are stored with their metadata separately from encryption keys.
 */
public class FileStorageService {
    private String storageDirectory;
    private static final String DATA_DIR = "data";
    private Map<String, FileMetadata> fileMetadata; // fileId -> metadata
    private final MongoCollection<Document> fileMetadataCollection;
    
    public FileStorageService(String storageDirectory) {
        this.storageDirectory = storageDirectory;
        this.fileMetadata = new ConcurrentHashMap<>();
        this.fileMetadataCollection = MongoManager.getInstance().getCollection("file_metadata", "file_metadata");
        
        // Create storage directory if it doesn't exist
        File dir = new File(storageDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Create data directory if it doesn't exist
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        // Load metadata from disk
        loadMetadata();
    }
    
    /**
     * Stores an encrypted file.
     * 
     * @param fileId Unique identifier for the file
     * @param encryptedData The encrypted file data
     * @param ownerUsername The username of the file owner
     * @param originalFileName The original filename
     * @return true if storage succeeded
     */
    public boolean storeFile(String fileId, byte[] encryptedData, 
                            String ownerUsername, String originalFileName) throws IOException {
        // Create file path
        Path filePath = Paths.get(storageDirectory, fileId + ".encrypted");
        
        // Write encrypted data to file
        Files.write(filePath, encryptedData);
        
        // Store metadata
        FileMetadata metadata = new FileMetadata(fileId, ownerUsername, originalFileName, 
                                                 filePath.toString(), encryptedData.length);
        fileMetadata.put(fileId, metadata);
        
        // Save metadata to disk
        saveMetadata();
        
        return true;
    }
    
    /**
     * Retrieves an encrypted file.
     */
    public byte[] retrieveFile(String fileId) throws IOException {
        FileMetadata metadata = fileMetadata.get(fileId);
        if (metadata == null) {
            throw new IOException("File not found: " + fileId);
        }
        
        Path filePath = Paths.get(metadata.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IOException("Encrypted file not found on disk: " + fileId);
        }
        
        return Files.readAllBytes(filePath);
    }
    
    /**
     * Gets file metadata.
     */
    public FileMetadata getFileMetadata(String fileId) {
        return fileMetadata.get(fileId);
    }
    
    /**
     * Deletes a file and its metadata.
     */
    public boolean deleteFile(String fileId) throws IOException {
        FileMetadata metadata = fileMetadata.get(fileId);
        if (metadata != null) {
            Path filePath = Paths.get(metadata.getFilePath());
            Files.deleteIfExists(filePath);
            fileMetadata.remove(fileId);
            saveMetadata();
            return true;
        }
        return false;
    }
    
    /**
     * Checks if a file exists.
     */
    public boolean fileExists(String fileId) {
        return fileMetadata.containsKey(fileId);
    }
    
    /**
     * Gets all file metadata (for admin/manager views).
     */
    public java.util.Collection<FileMetadata> getAllFiles() {
        return fileMetadata.values();
    }
    
    /**
     * Gets all files owned by a specific user.
     */
    public java.util.List<FileMetadata> getFilesByOwner(String ownerUsername) {
        java.util.List<FileMetadata> result = new java.util.ArrayList<>();
        for (FileMetadata metadata : fileMetadata.values()) {
            if (metadata.getOwnerUsername().equals(ownerUsername)) {
                result.add(metadata);
            }
        }
        return result;
    }
    
    /**
     * Saves file metadata to disk.
     */
    private void saveMetadata() {
        fileMetadataCollection.deleteMany(new Document());
        for (FileMetadata metadata : fileMetadata.values()) {
            Document metadataDoc = new Document("fileId", metadata.getFileId())
                    .append("ownerUsername", metadata.getOwnerUsername())
                    .append("originalFileName", metadata.getOriginalFileName())
                    .append("filePath", metadata.getFilePath())
                    .append("fileSize", metadata.getFileSize());
            fileMetadataCollection.insertOne(metadataDoc);
        }
    }
    
    /**
     * Loads file metadata from disk.
     */
    private void loadMetadata() {
        fileMetadata.clear();
        for (Document metadataDoc : fileMetadataCollection.find()) {
            String fileId = metadataDoc.getString("fileId");
            String ownerUsername = metadataDoc.getString("ownerUsername");
            String originalFileName = metadataDoc.getString("originalFileName");
            String filePath = metadataDoc.getString("filePath");

            Number fileSizeValue = metadataDoc.get("fileSize", Number.class);
            long fileSize = fileSizeValue != null ? fileSizeValue.longValue() : 0L;

            if (filePath != null && Files.exists(Paths.get(filePath))) {
                FileMetadata metadata = new FileMetadata(fileId, ownerUsername,
                        originalFileName, filePath, fileSize);
                fileMetadata.put(fileId, metadata);
            }
        }
    }
    
    /**
     * Represents file metadata.
     */
    public static class FileMetadata {
        private String fileId;
        private String ownerUsername;
        private String originalFileName;
        private String filePath;
        private long fileSize;
        
        public FileMetadata(String fileId, String ownerUsername, String originalFileName,
                          String filePath, long fileSize) {
            this.fileId = fileId;
            this.ownerUsername = ownerUsername;
            this.originalFileName = originalFileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
        }
        
        public String getFileId() {
            return fileId;
        }
        
        public String getOwnerUsername() {
            return ownerUsername;
        }
        
        public String getOriginalFileName() {
            return originalFileName;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public long getFileSize() {
            return fileSize;
        }
    }
}
