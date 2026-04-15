package SecureDataSharing.config;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Configuration Manager for loading and accessing application settings from JSON.
 */
public class ConfigManager {
    private static ConfigManager instance;
    private JSONObject config;
    
    private ConfigManager() {
        loadConfig();
    }
    
    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    private void loadConfig() {
        try {
            // Try to load from config directory first
            InputStream inputStream = new FileInputStream("config.json");
            config = new JSONObject(new JSONTokener(inputStream));
            inputStream.close();
        } catch (IOException e) {
            // If file not found, try loading from classpath
            try {
                InputStream inputStream = getClass().getClassLoader()
                    .getResourceAsStream("config.json");
                if (inputStream != null) {
                    config = new JSONObject(new JSONTokener(inputStream));
                    inputStream.close();
                } else {
                    System.err.println("Configuration file not found. Using defaults.");
                    loadDefaults();
                }
            } catch (IOException ex) {
                System.err.println("Error loading configuration: " + ex.getMessage());
                loadDefaults();
            }
        } catch (Exception e) {
            System.err.println("Error parsing JSON configuration: " + e.getMessage());
            loadDefaults();
        }
    }
    
    private void loadDefaults() {
        config = new JSONObject();
        JSONObject app = new JSONObject();
        JSONObject window = new JSONObject();
        window.put("width", 1400);
        window.put("height", 900);
        app.put("window", window);
        config.put("app", app);
        
        JSONObject theme = new JSONObject();
        theme.put("primary", new JSONObject().put("color", "#2C3E50"));
        theme.put("secondary", new JSONObject().put("color", "#3498DB"));
        theme.put("accent", new JSONObject().put("color", "#E74C3C"));
        theme.put("success", new JSONObject().put("color", "#27AE60"));
        theme.put("background", new JSONObject().put("color", "#ECF0F1"));
        config.put("theme", theme);
        
        JSONObject email = new JSONObject();
        email.put("enabled", false);
        config.put("email", email);
        
        JSONObject otp = new JSONObject();
        otp.put("length", 6);
        otp.put("validity", new JSONObject().put("minutes", 5));
        config.put("otp", otp);
    }
    
    /**
     * Gets a property value using dot notation (e.g., "app.window.width")
     */
    public String getProperty(String key) {
        return getProperty(key, null);
    }
    
    /**
     * Gets a property value using dot notation with default value
     */
    public String getProperty(String key, String defaultValue) {
        try {
            Object value = getNestedValue(key);
            return value != null ? value.toString() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Gets an integer property value
     */
    public int getIntProperty(String key, int defaultValue) {
        try {
            Object value = getNestedValue(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value != null) {
                return Integer.parseInt(value.toString());
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean property value
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        try {
            Object value = getNestedValue(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value != null) {
                return Boolean.parseBoolean(value.toString());
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Navigates through nested JSON objects using dot notation
     */
    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        JSONObject current = config;
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (current.has(parts[i])) {
                Object obj = current.get(parts[i]);
                if (obj instanceof JSONObject) {
                    current = (JSONObject) obj;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        String lastKey = parts[parts.length - 1];
        if (current.has(lastKey)) {
            return current.get(lastKey);
        }
        
        return null;
    }
    
    public void reload() {
        loadConfig();
    }
    
    /**
     * Gets the raw JSON object for advanced access
     */
    public JSONObject getConfig() {
        return config;
    }
}
