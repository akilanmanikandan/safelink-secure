# Secure Data Sharing System

Secure Data Sharing System is a web-based project for storing files securely and sharing them only with approved users. It uses a Java Spring Boot application for the main website and a small Python Flask service for machine-learning based monitoring.

## Project Overview

This project helps an organization upload sensitive files, encrypt them, and control who can access them. A manager or admin can upload a file, set access rules, review access requests, and approve or deny users. A normal user can register, log in with OTP verification, request access to available files, and download files only after approval.

The system runs in a browser at:

```bash
http://localhost:8080
```

The ML monitoring service runs separately at:

```bash
http://localhost:5000
```

## What Problem It Solves

In many organizations, confidential files are shared through email, messaging apps, or open drives. That can lead to accidental data leaks, weak tracking, and unclear access control.

This project solves that by:

- Encrypting uploaded files before storing them.
- Requiring login and OTP-based verification.
- Allowing only managers and admins to upload files.
- Letting users request access instead of directly downloading files.
- Giving file owners control over approvals, denials, and access expiry.
- Keeping audit logs of important actions.
- Using an ML service to detect suspicious behavior and alert the administrator.

## How It Benefits a Client

A client using this system gets a safer file sharing workflow. Sensitive files are not stored in plain form, access is not automatically given to everyone, and every important action can be reviewed later.

This is useful for teams that handle confidential documents, internal reports, employee records, project files, research files, or any data that should only be shared with selected people.

## What It Does

- Registers users with username, password, email, role, and department.
- Sends OTP codes to email during login.
- Lets admins and managers upload files.
- Encrypts uploaded files using AES-GCM.
- Stores encrypted files inside `encrypted_files/`.
- Stores users, sessions, policies, access requests, keys, and file metadata in MongoDB Atlas.
- Lets uploaders define access rules using role and department.
- Lets users request access to files.
- Lets file owners approve, deny, or revoke access.
- Lets approved users download decrypted files.
- Logs application activity in `audit.log`.
- Uses a Python ML service to check for suspicious activity and write ML events to `ml-service/ml_audit.log`.
- Shows an AI monitoring chatbot for managers and admins.

## Main Modules

- `src/main/java/SecureDataSharing/web`: Spring Boot web application and REST controllers.
- `src/main/java/SecureDataSharing/auth`: User registration, login, password hashing, and OTP/MFA handling.
- `src/main/java/SecureDataSharing/crypto`: AES encryption and key re-encryption support.
- `src/main/java/SecureDataSharing/access`: Attribute-based access control policies and access request models.
- `src/main/java/SecureDataSharing/storage`: MongoDB-backed storage for users, sessions, policies, access requests, keys, and file metadata, plus local encrypted file storage.
- `src/main/java/SecureDataSharing/email`: Email validation and OTP/admin email sending.
- `src/main/java/SecureDataSharing/ml`: Java client that sends user activity details to the Python ML service.
- `src/main/resources/static`: Browser UI files: HTML, CSS, and JavaScript.
- `ml-service/app.py`: Python Flask ML service for suspicious behavior detection.
- `config.json`: Email, OTP, AI monitoring, and session settings.

## Requirements

Install these before running the project:

- Java JDK 8 or later.
- Apache Maven.
- Python 3.
- Python packages for the ML service:

```bash
pip install flask joblib scikit-learn
```

## Build

Open a terminal in the project folder and run:

```bash
mvn clean compile package
```

After a successful build, Maven creates the packaged application under `target/`.

## Run

Open one terminal in the project folder and start the Java Spring Boot application:

```bash
mvn spring-boot:run
```

Open a second terminal for the ML service:

```bash
cd ml-service
python app.py
```

Then open the browser:

```bash
http://localhost:8080
```

## Basic Use

1. Register a user with username, password, email, role, and department.
2. Log in with username and password.
3. Enter the OTP sent to the registered email.
4. Log in as a manager or admin to upload files.
5. During upload, set file expiry and required role or department.
6. Log in as another user and request access to an available file.
7. Log back in as the file owner and approve or deny the request.
8. Once approved, the requester can download the decrypted file.

## Data Storage

The project now uses a split storage model:

- **MongoDB Atlas** stores the application data:
  - users
  - sessions
  - policies
  - access requests
  - key records
  - file metadata
- **Local storage** keeps the actual encrypted uploaded files in `encrypted_files/`.

This means the app writes new business data directly to the MongoDB Atlas cluster while continuing to keep encrypted file contents on disk.

## Important Files and Folders

- `config.json`: Main configuration file. Update the email settings here before using OTP email alerts.
- `data/`: Old local JSON storage used by earlier versions of the project. It is no longer the active source of truth after MongoDB integration.
- `encrypted_files/`: Stores encrypted uploaded files.
- `audit.log`: Stores Java application audit events.
- `ml-service/ml_audit.log`: Stores suspicious ML events.
- `ml-service/safelinkmodel.pkl`: ML model used by the Python service.

## Future Enhancements

- Move JSON storage to a proper database such as MySQL or PostgreSQL.
- Store secrets and email passwords in environment variables instead of `config.json`.
- Add stronger password hashing such as BCrypt or Argon2.
- Add a production-ready proxy re-encryption library.
- Add admin screens for user management and audit log review.
- Add file search, file categories, and bulk access approvals.
- Add HTTPS deployment support.
- Add automated tests for the web, storage, crypto, and ML modules.
- Add Docker setup so both Java and Python services can start together.

