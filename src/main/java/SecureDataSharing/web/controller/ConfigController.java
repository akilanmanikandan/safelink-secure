package SecureDataSharing.web.controller;

import SecureDataSharing.config.ConfigManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @GetMapping("/ui")
    public ResponseEntity<?> getUiConfig() {
        Map<String, Object> response = new HashMap<>();
        try {
            org.json.JSONObject fullConfig = ConfigManager.getInstance().getConfig();
            if (fullConfig.has("ai_monitoring")) {
                org.json.JSONObject aiMon = fullConfig.getJSONObject("ai_monitoring");
                Map<String, String> uiConfig = new HashMap<>();
                uiConfig.put("icon", aiMon.optString("icon", "🤖"));
                uiConfig.put("chat_color", aiMon.optString("chat_color", "#1e3a8a"));
                uiConfig.put("language", aiMon.optString("language", "en"));
                response.put("ai_monitoring", uiConfig);
            } else {
                Map<String, String> defaultAiMon = new HashMap<>();
                defaultAiMon.put("icon", "🤖");
                defaultAiMon.put("chat_color", "#1e3a8a");
                defaultAiMon.put("language", "en");
                response.put("ai_monitoring", defaultAiMon);
            }
        } catch (Exception e) {
            Map<String, String> defaultAiMon = new HashMap<>();
            defaultAiMon.put("icon", "🤖");
            defaultAiMon.put("chat_color", "#1e3a8a");
            defaultAiMon.put("language", "en");
            response.put("ai_monitoring", defaultAiMon);
        }
        return ResponseEntity.ok(response);
    }
}
