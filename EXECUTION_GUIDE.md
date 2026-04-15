# Execution Guide

This guide explains how to run and use the Secure Data Sharing System after building it.

## 1. Start the Java Web Application

Open a terminal in the main project folder, where `pom.xml` is located.

Run:

```bash
mvn spring-boot:run
```

Wait until the Spring Boot application finishes starting. The website will run on:

```bash
http://localhost:8080
```

Keep this terminal open while using the application.

## 2. Start the Python ML Service

Open a second terminal in the main project folder.

Run:

```bash
cd ml-service
python app.py
```

The ML service will run on:

```bash
http://localhost:5000
```

Keep this terminal open too.

The Java application can still open if the ML service is not running, but suspicious behavior monitoring and ML-based alerts will not work properly.

## 3. Open the Website

Open any modern browser and go to:

```bash
http://localhost:8080
```

You will see the login and registration screen.

## 4. Register a User

Click register and enter:

- Username.
- Password.
- Email address.
- Role: `admin`, `manager`, or `user`.
- Department.

MFA/OTP is enabled automatically for every user.

## 5. Log In

Enter the username and password.

The system sends an OTP to the registered email address. Enter the OTP to complete login.

If OTP is not received:

- Check the spam folder.
- Check the email settings in `config.json`.
- Make sure `email.enabled` is `true`.
- Use an app password if using Gmail.

## 6. Upload a File

Only `admin` and `manager` users can upload files.

Steps:

1. Log in as an admin or manager.
2. Open the `My Files` tab.
3. Click `Upload File`.
4. Choose the file.
5. Set the key/file expiry value and unit.
6. Choose the required role.
7. Optionally enter the required department.
8. Submit the upload.

The system encrypts the file and stores it inside:

```bash
encrypted_files/
```

The file metadata and policy are stored inside:

```bash
data/
```

## 7. Request Access to a File

Steps:

1. Log in as another user.
2. Open the `Available Files` tab.
3. Select a file.
4. Click `Request Access`.

The request is sent to the file owner. The user cannot download the file until the owner approves the request.

## 8. Approve or Deny a Request

Only the file owner can approve or deny requests for their uploaded files.

Steps:

1. Log in as the file owner.
2. Open the `Pending Requests` tab.
3. Select a request.
4. Click approve or deny.
5. If approving, set the access expiry.

After approval, the requester can download the file.

## 9. Download an Approved File

Steps:

1. Log in as the approved user.
2. Open the `Available Files` tab.
3. Select the approved file.
4. Click download/decrypt.

The browser downloads the decrypted file.

## 10. Stop the Project

To stop the Java application, go to the terminal running:

```bash
mvn spring-boot:run
```

Press:

```bash
Ctrl+C
```

To stop the Python ML service, go to the terminal running:

```bash
python app.py
```

Press:

```bash
Ctrl+C
```

## Important Runtime Files

- `audit.log`: Java application actions and security events.
- `ml-service/ml_audit.log`: Suspicious ML events.
- `data/users.json`: Registered users and user keys.
- `data/file_metadata.json`: Uploaded file metadata.
- `data/policies.json`: File access rules.
- `data/access_requests.json`: Access request records.
- `data/keys.json`: File keys and re-encryption keys.
- `encrypted_files/`: Encrypted file contents.
- `config.json`: Email, OTP, admin alert, AI monitoring, and session settings.

## Common Runtime Problems

If the website does not open, make sure the Java terminal is still running and open:

```bash
http://localhost:8080
```

If port `8080` is already in use, stop the other application using that port.

If the ML service fails to start, make sure you are inside the `ml-service` folder and that `safelinkmodel.pkl` exists there.

If Python reports missing packages, run:

```bash
pip install flask joblib scikit-learn
```

If OTP email does not arrive, check `config.json`, the email app password, and the spam folder.

If a user cannot upload files, make sure the user role is `admin` or `manager`.

If a user cannot download a file, make sure the access request was approved and has not expired or been revoked.
