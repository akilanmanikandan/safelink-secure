package SecureDataSharing.web.controller;

import SecureDataSharing.auth.AuthService;
import SecureDataSharing.auth.User;
import SecureDataSharing.email.EmailValidator;
import SecureDataSharing.storage.SessionManager;
import SecureDataSharing.access.Attribute;
import SecureDataSharing.audit.AuditLogger;
import SecureDataSharing.ml.MLMonitoringService;
import SecureDataSharing.ml.MLPredictionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private MLMonitoringService mlMonitoringService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            String email = request.get("email");
            String role = request.get("role");
            String department = request.get("department");

            if (username == null || password == null || email == null ||
                    role == null || department == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "All fields are required");
                return ResponseEntity.badRequest().body(error);
            }

            // Validate email
            EmailValidator.ValidationResult emailValidation = EmailValidator.validate(email);
            if (!emailValidation.isValid()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", emailValidation.getErrorMessage());
                return ResponseEntity.badRequest().body(error);
            }

            // Register user
            User user = authService.registerUser(username, password);
            user.setEmail(email);
            user.addAttribute(new Attribute("role", role));
            user.addAttribute(new Attribute("department", department));

            // MFA is mandatory
            authService.enableMFA(username);
            authService.saveUsers();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully! MFA has been enabled.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            if (username == null || password == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Username and password are required");
                return ResponseEntity.badRequest().body(error);
            }

            User user = authService.getUser(username);
            if (user == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "User not found");
                return ResponseEntity.badRequest().body(error);
            }

            // Check if MFA is enabled (it should be for all users)
            if (user.isMfaEnabled()) {
                // Generate OTP
                String otp = authService.generateOTP(username);

                Map<String, Object> response = new HashMap<>();
                response.put("mfaRequired", true);
                response.put("message", "OTP has been sent to your email");
                response.put("email", user.getEmail());

                // Store username in session temporarily
                session.setAttribute("pendingUsername", username);

                return ResponseEntity.ok(response);
            }

            // If no MFA (shouldn't happen), authenticate directly
            if (authService.authenticate(username, password)) {
                session.setAttribute("username", username);
                sessionManager.saveSession(username);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("user", getUserInfo(user));

                // ML Check
                MLPredictionResponse mlResponse = mlMonitoringService.checkAnomaly(username, "login");
                if (mlResponse != null && "SUSPICIOUS BEHAVIOR DETECTED".equals(mlResponse.getStatus())) {
                    response.put("warning", "Suspicious login behavior detected. An alert has been sent to the administrator.");
                    response.put("risk_score", mlResponse.getRisk_score());
                    response.put("reasons", mlResponse.getReasons());
                }

                return ResponseEntity.ok(response);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid username or password");
                return ResponseEntity.badRequest().body(error);
            }
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Login failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/validate-otp")
    public ResponseEntity<?> validateOTP(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            String otp = request.get("otp");
            String username = (String) session.getAttribute("pendingUsername");

            if (username == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No pending login session");
                return ResponseEntity.badRequest().body(error);
            }

            if (otp == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "OTP is required");
                return ResponseEntity.badRequest().body(error);
            }

            // Validate OTP
            if (!authService.validateOTP(username, otp)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid or expired OTP");
                return ResponseEntity.badRequest().body(error);
            }

            // Get password from session or request
            String password = request.get("password");
            if (password == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Password is required");
                return ResponseEntity.badRequest().body(error);
            }

            // Authenticate
            if (!authService.authenticate(username, password)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid password");
                return ResponseEntity.badRequest().body(error);
            }

            // Login successful
            User user = authService.getUser(username);
            session.setAttribute("username", username);
            session.removeAttribute("pendingUsername");
            sessionManager.saveSession(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", getUserInfo(user));

            // ML Check
            MLPredictionResponse mlResponse = mlMonitoringService.checkAnomaly(username, "login");
            if (mlResponse != null && "SUSPICIOUS BEHAVIOR DETECTED".equals(mlResponse.getStatus())) {
                response.put("warning", "Suspicious login behavior detected. An alert has been sent to the administrator.");
                response.put("risk_score", mlResponse.getRisk_score());
                response.put("reasons", mlResponse.getReasons());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "OTP validation failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username != null) {
            sessionManager.clearSession();
            auditLogger.log("LOGOUT", username, "User logged out");
        }
        session.invalidate();
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        Map<String, Object> response = new HashMap<>();
        if (username != null) {
            User user = authService.getUser(username);
            if (user != null) {
                response.put("user", getUserInfo(user));
                return ResponseEntity.ok(response);
            }
        }
        response.put("user", null);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> getUserInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("mfaEnabled", user.isMfaEnabled());

        Map<String, String> attributes = new HashMap<>();
        for (Attribute attr : user.getAttributes()) {
            attributes.put(attr.getName(), attr.getValue());
        }
        userInfo.put("attributes", attributes);

        return userInfo;
    }
}
