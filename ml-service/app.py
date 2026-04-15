from flask import Flask, request, jsonify
import joblib
import datetime
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import json
import os

app = Flask(__name__)

model = joblib.load("safelinkmodel.pkl")


CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'config.json')

def load_config():
    try:
        with open(CONFIG_PATH, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading config: {e}")
        return None

def send_admin_email(result):
    config = load_config() or {}
    if not config.get('email', {}).get('enabled', False):
        return
    
    email_cfg = config.get('email', {})
    smtp_cfg = email_cfg.get('smtp', {})
    from_cfg = email_cfg.get('from', {})
    admin_cfg = email_cfg.get('admin', {})
    alert_template = admin_cfg.get('alert_template', {})

    msg = MIMEMultipart()
    msg['From'] = f"{from_cfg.get('name', 'SafeLink')} <{from_cfg.get('address', '')}>"
    msg['To'] = admin_cfg.get('address', '')
    
    # Use dynamic subject from config
    msg['Subject'] = alert_template.get('subject', "🚨 ALERT: Suspicious Behavior Detected!")

    reasons_str = '\n'.join(f"- {r}" for r in result.get('reasons', []))
    
    # Process string replacements for the template loaded from config
    template_str = alert_template.get('body', {}).get('template', "")
    if template_str:
        body = (template_str.replace("{USERNAME}", str(result.get('username')))
                            .replace("{USER_ID}", str(result.get('user_id')))
                            .replace("{ROLE}", str(result.get('role')))
                            .replace("{ML_SCORE}", str(result.get('ml_score')))
                            .replace("{RISK_SCORE}", str(result.get('risk_score')))
                            .replace("{REASONS}", reasons_str))
    else:
        # Fallback if template is missing from config
        body = f"Suspicious behavior detected.\n\nUsername: {result.get('username')}\nReasons:\n{reasons_str}"

    msg.attach(MIMEText(body, 'plain'))

    try:
        server = smtplib.SMTP(smtp_cfg.get('host', 'smtp.gmail.com'), smtp_cfg.get('port', 587))
        if smtp_cfg.get('starttls', {}).get('enable', False):
            server.starttls()
        if smtp_cfg.get('auth', False):
            server.login(from_cfg.get('address', ''), from_cfg.get('password', ''))
        server.send_message(msg)
        server.quit()
        print("Admin email sent successfully.")
    except Exception as e:
        print(f"Failed to send admin email: {e}")

# 📝 Simple log function
def log_event(data):
    with open("ml_audit.log", "a") as f:
        f.write(f"{datetime.datetime.now()} | {data}\n")


# 🔍 Risk + Reason Engine
def analyze(features):
    reasons = []
    risk = 0

    login_hour, request_count, file_access, approval_rate, file_size, role = features

    if login_hour < 7 or login_hour > 19:
        reasons.append("Unusual login time")
        risk += 25

    if request_count > 10:
        reasons.append("High number of access requests")
        risk += 20

    if file_access > 5:
        reasons.append("Excessive file access")
        risk += 20

    if approval_rate < 0.5:
        reasons.append("Low approval rate")
        risk += 15

    if file_size > 600_000_000:
        reasons.append("Unusually large file access")
        risk += 15

    return risk, reasons


@app.route("/detect", methods=["POST"])
def detect():
    try:
        data = request.json

        # 👤 Identity Info
        username = data.get("username", "unknown")
        user_id = data.get("user_id", "unknown")
        user_role = data.get("role", "unknown")

        features = data["features"]

        # 🤖 ML Prediction
        prediction = model.predict([features])[0]
        score = model.decision_function([features])[0]

        # 📊 Risk Analysis
        risk_score, reasons = analyze(features)

        # 🧠 Final Decision
        if prediction == -1 or risk_score > 60:
            status = "SUSPICIOUS BEHAVIOR DETECTED"

            if prediction == -1 and len(reasons) == 0:
                reasons.append("Anomalous behavior detected by ML model")

        else:
            status = "NORMAL BEHAVIOR"

        result = {
            "username": username,
            "user_id": user_id,
            "role": user_role,
            "status": status,
            "ml_score": float(score),
            "risk_score": risk_score,
            "reasons": reasons
        }

        # 📝 Log ONLY suspicious events (recommended)
        if status == "SUSPICIOUS BEHAVIOR DETECTED":
            log_event(result)
            
            # 📧 Send email alert to admin asynchronously
            import threading
            threading.Thread(target=send_admin_email, args=(result,)).start()

        return jsonify(result)

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(port=5000, debug=True)