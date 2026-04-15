package SecureDataSharing.email;

import SecureDataSharing.config.ConfigManager;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Email Service for sending OTP codes and notifications.
 */
public class EmailService {
    private static EmailService instance;
    private ConfigManager config;
    private boolean enabled;
    private String smtpHost;
    private int smtpPort;
    private String fromAddress;
    private String fromPassword;
    private String fromName;
    
    private EmailService() {
        config = ConfigManager.getInstance();
        enabled = config.getBooleanProperty("email.enabled", false);
        
        if (enabled) {
            smtpHost = config.getProperty("email.smtp.host", "smtp.gmail.com");
            smtpPort = config.getIntProperty("email.smtp.port", 587);
            fromAddress = config.getProperty("email.from.address", "");
            fromPassword = config.getProperty("email.from.password", "");
            fromName = config.getProperty("email.from.name", "Secure Data Sharing System");
        }
    }
    
    public static EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sends an OTP code to the specified email address.
     */
    public boolean sendOTP(String toEmail, String otp) {
        if (!enabled) {
            return false;
        }
        
        if (fromAddress.isEmpty() || fromPassword.isEmpty()) {
            System.err.println("Email configuration incomplete. Please check config/application.properties");
            return false;
        }
        
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(fromAddress, fromPassword);
                }
            });
            
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            
            int validityMinutes = config.getIntProperty("otp.validity.minutes", 5);
            String subject = config.getProperty("otp.email.subject", "Your OTP Code");
            String body = config.getProperty("otp.email.body.template", 
                "Your OTP code is: {OTP}. This code is valid for {MINUTES} minutes.");
            
            body = body.replace("{OTP}", otp).replace("{MINUTES}", String.valueOf(validityMinutes));
            
            message.setSubject(subject);
            message.setText(body);
            
            new Thread(() -> {
                try {
                    Transport.send(message);
                } catch (Exception e) {
                    System.err.println("Error sending email async: " + e.getMessage());
                }
            }).start();
            return true;
        } catch (Exception e) {
            System.err.println("Error creating email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Sends a notification email.
     */
    public boolean sendNotification(String toEmail, String subject, String body) {
        if (!enabled) {
            return false;
        }
        
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(fromAddress, fromPassword);
                }
            });
            
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(body);
            
            new Thread(() -> {
                try {
                    Transport.send(message);
                } catch (Exception e) {
                    System.err.println("Error sending notification email async: " + e.getMessage());
                }
            }).start();
            return true;
        } catch (Exception e) {
            System.err.println("Error creating notification email: " + e.getMessage());
            return false;
        }
    }
}
