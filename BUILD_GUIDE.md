# Build Guide

This guide explains only the steps needed to build the Secure Data Sharing System.

## 1. Install Required Software

Install these first:

- Java JDK 8 or later.
- Apache Maven.
- Python 3.

For the Python ML service, install the required packages:

```bash
pip install flask joblib scikit-learn
```

## 2. Open the Project Folder

Open a terminal or command prompt in this folder:

```bash
SecureDataSharing - Prod
```

This is the folder that contains `pom.xml`, `config.json`, `src/`, and `ml-service/`.

## 3. Check Java and Maven

Run these commands to make sure Java and Maven are available:

```bash
java -version
mvn -version
```

If both commands show version information, you can build the project.

## 4. Build the Java Application

Run:

```bash
mvn clean compile package
```

What this does:

- `clean` removes the old build output.
- `compile` compiles the Java code.
- `package` creates the application package under `target/`.

When the build is successful, Maven shows:

```bash
BUILD SUCCESS
```

## 5. Check the Build Output

After the build, the main output folder is:

```bash
target/
```

You should see a JAR file similar to:

```bash
target/secure-data-storage-1.0.0.jar
```

## 6. Configuration Before Running

Open `config.json` and check the email settings.

This project uses email for OTP login and admin alerts. If the email settings are wrong, users may not receive OTP codes. For Gmail, use an app password instead of the normal email password.

The most important fields are:

```json
{
  "email": {
    "enabled": true,
    "from": {
      "address": "your-email@gmail.com",
      "password": "your-app-password"
    },
    "admin": {
      "address": "admin-email@gmail.com"
    }
  }
}
```

## 7. Next Step

After the build is successful, follow `EXECUTION_GUIDE.md` to run:

```bash
mvn spring-boot:run
```

and:

```bash
cd ml-service
python app.py
```

## Common Build Problems

If `mvn` is not recognized, Maven is not installed or not added to PATH.

If `java` is not recognized, Java is not installed or not added to PATH.

If Python says a module is missing, install the ML packages again:

```bash
pip install flask joblib scikit-learn
```

If the build fails because dependencies cannot download, check your internet connection and run the build command again.
