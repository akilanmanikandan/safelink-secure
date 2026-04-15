# Configuration Guide

This guide explains how to configure the Secure Data Sharing System for optimal performance and security.

## Configuration File Structure

The application uses a JSON configuration file located at `config/config.json`. The configuration is divided into several sections:

```json
{
  "email": {...},
  "otp": {...},
  "security": {...}
}
```

## Email Configuration

Email configuration is required for OTP (One-Time Password) delivery during Multi-Factor Authentication. **MFA is mandatory for all users**, so email configuration is essential for the application to function properly.

### Gmail Setup
1. Enable 2-Factor Authentication on your Gmail account
2. Generate an App Password:
   - Go to Google Account settings
   - Security → 2-Step Verification → App passwords
   - Generate password for "Mail"
   - Use this 16-character password (not your regular password)

### Configuration Example
```json
{
  "email": {
    "enabled": true,
    "smtp": {
      "host": "smtp.gmail.com",
      "port": 587,
      "auth": true,
      "starttls": {
        "enable": true
      }
    },
    "from": {
      "address": "your-email@gmail.com",
      "password": "abcd-efgh-ijkl-mnop",
      "name": "Secure Data Sharing System"
    }
  }
}
```

### Other Email Providers

#### Outlook/Hotmail
```json
{
  "email": {
    "enabled": true,
    "smtp": {
      "host": "smtp-mail.outlook.com",
      "port": 587,
      "auth": true,
      "starttls": {
        "enable": true
      }
    },
    "from": {
      "address": "your-email@outlook.com",
      "password": "your-app-password",
      "name": "Secure Data Sharing System"
    }
  }
}
```

#### Yahoo Mail
```json
{
  "email": {
    "enabled": true,
    "smtp": {
      "host": "smtp.mail.yahoo.com",
      "port": 587,
      "auth": true,
      "starttls": {
        "enable": true
      }
    },
    "from": {
      "address": "your-email@yahoo.com",
      "password": "your-app-password",
      "name": "Secure Data Sharing System"
    }
  }
}
```

#### Custom SMTP Server
```json
{
  "email": {
    "enabled": true,
    "smtp": {
      "host": "your-smtp-server.com",
      "port": 587,
      "auth": true,
      "starttls": {
        "enable": true
      }
    },
    "from": {
      "address": "noreply@yourdomain.com",
      "password": "your-smtp-password",
      "name": "Secure Data Sharing System"
    }
  }
}
```

### Email Testing
To test email configuration:
1. Register a new user with a valid email address (email is required and validated)
2. MFA is automatically enabled during registration
3. Attempt to login - OTP should be sent to the email automatically

### Email Validation
The application validates email addresses during registration:
- **Format Validation**: Checks email format using regex pattern
- **Domain Validation**: Verifies domain exists by checking MX (Mail Exchange) records
- **Error Messages**: Clear feedback if email is invalid or domain doesn't exist

## OTP Configuration

Configure One-Time Password settings for Multi-Factor Authentication.

### Default Configuration
```json
{
  "otp": {
    "length": 6,
    "validity": {
      "minutes": 5
    },
    "email": {
      "subject": "Your OTP Code",
      "body": {
        "template": "Your OTP code is: {OTP}. This code is valid for {MINUTES} minutes."
      }
    }
  }
}
```

### Configuration Options

| Setting | Description | Default | Range |
|---------|-------------|---------|-------|
| `length` | Number of digits in OTP | 6 | 4-10 |
| `validity.minutes` | How long OTP is valid | 5 | 1-60 |
| `subject` | Email subject line | "Your OTP Code" | Any string |
| `template` | Email body template | See above | Must include {OTP} and {MINUTES} |

### Custom Email Template
```json
{
  "otp": {
    "email": {
      "subject": "Secure Access Code - {MINUTES} minutes",
      "body": {
        "template": "Hello,\n\nYour secure access code is: {OTP}\n\nThis code expires in {MINUTES} minutes.\n\nIf you didn't request this, please ignore this email.\n\nBest regards,\nSecure Data Sharing System"
      }
    }
  }
}
```

## Security Configuration

Configure session timeout and other security settings.

### Default Configuration
```json
{
  "security": {
    "session": {
      "timeout": {
        "minutes": 30
      }
    }
  }
}
```

### Session Management
- `timeout.minutes`: How long before automatic logout (in minutes)
- Currently not implemented in UI, but affects server-side sessions if deployed

## Directory Structure

The application creates the following directories:

```
SecureDataSharing/
├── config/
│   └── config.json          # Main configuration file
├── data/
│   └── users.json           # User data (created automatically)
├── encrypted_files/         # Encrypted file storage (created automatically)
└── audit.log               # Security audit log (created automatically)
```

## Environment Variables (Optional)

For enhanced security, you can use environment variables instead of storing sensitive data in config.json:

```bash
# Set environment variables
export SMTP_PASSWORD="your-app-password"
export EMAIL_ADDRESS="your-email@gmail.com"

# Then use in config.json
{
  "email": {
    "from": {
      "address": "${EMAIL_ADDRESS}",
      "password": "${SMTP_PASSWORD}"
    }
  }
}
```

## Troubleshooting Configuration

### Email Issues

**Problem**: "Authentication failed" or "Invalid credentials"
- **Solution**: Use App Password instead of regular password for Gmail
- **Solution**: Verify SMTP host and port settings

**Problem**: Emails not being delivered
- **Solution**: Check spam/junk folder
- **Solution**: Verify sender email address is authorized
- **Solution**: Test with different email providers

**Problem**: Connection timeout
- **Solution**: Check firewall settings
- **Solution**: Verify internet connection
- **Solution**: Try different SMTP ports (587, 465, 25)

### Configuration Validation

To validate your configuration:
1. Run the application: `mvn exec:java@run-gui`
2. Check console output for configuration errors
3. Check `audit.log` for system initialization messages
4. Test email functionality by registering with MFA

### Backup Configuration

Always backup your configuration:
```bash
# Backup config
cp config/config.json config/config.json.backup

# Backup user data
cp data/users.json data/users.json.backup
```

## Production Considerations

### Security Hardening
- Use strong, unique app passwords
- Regularly rotate SMTP credentials
- Store configuration files securely
- Use environment variables for sensitive data
- Implement proper logging and monitoring

### Performance Tuning
- Adjust OTP validity based on security vs usability needs
- Monitor email delivery rates
- Consider using dedicated SMTP services (SendGrid, Mailgun) for high volume

### Compliance
- Ensure email configuration complies with GDPR/privacy regulations
- Implement proper audit logging for compliance requirements
- Regular security audits of configuration and credentials</content>
</xai:function_call name="write">
<parameter name="file_path">BUILD_GUIDE.md