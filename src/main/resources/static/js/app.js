// Global state
let currentUser = null;
let pendingUsername = null;
let selectedRequestId = null;
let aiUiConfig = {
    icon: '🤖',
    chat_color: '#1e3a8a',
    language: 'en'
};

const botDatabase = {
    en: {
        welcome: "Hello! I am the AI Monitoring Bot. How can I help you understand our security system?",
        questions: [
            { q: "How does the model work?", a: "The anomaly detection model operates by analyzing various user activity features such as login time, request counts, file access frequency, and file sizes. It uses an Isolation Forest algorithm trained to recognize typical usage patterns, instantly flagging anything anomalous." },
            { q: "What is it powered on?", a: "The monitoring system is powered by an advanced Machine Learning component (scikit-learn Isolation Forest) running via a local Flask microservice. It evaluates real-time telemetry from the Spring Boot application." },
            { q: "What are its features?", a: "Key features include: Real-time anomaly detection, risk scoring based on behavioral heuristics, automated admin alerts upon violation, and zero-trust verification of user attributes." }
        ]
    },
    ta: {
        welcome: "வணக்கம்! நான் AI கண்காணிப்பு பாட். எங்கள் பாதுகாப்பு அமைப்பைப் புரிந்துகொள்ள நான் உங்களுக்கு எப்படி உதவ முடியும்?",
        questions: [
            { q: "மாதிரி எவ்வாறு செயல்படுகிறது?", a: "பயனர் செயல்பாடுகளான உள்நுழைவு நேரம், கோரிக்கை எண்ணிக்கை, கோப்பு அணுகல் மற்றும் கோப்பு அளவுகள் ஆகியவற்றை பகுப்பாய்வு செய்து இந்த மாதிரி செயல்படுகிறது. இது அசாதாரண பயன்பாடுகளை அடையாளம் காண Isolation Forest அல்காரிதத்தைப் பயன்படுத்துகிறது." },
            { q: "இது எதில் இயங்குகிறது?", a: "இந்த கண்காணிப்பு அமைப்பு, உள்ளூர் Flask மைக்ரோசர்வீஸ் மூலம் இயங்கும் ஒரு மேம்பட்ட மெஷின் லேர்னிங் (scikit-learn Isolation Forest) அடிப்படையில் செயல்படுகிறது." },
            { q: "இதன் முக்கிய அம்சங்கள் என்ன?", a: "நிகழ்நேர அசாதாரணங்களை கண்டறிதல், நடத்தை அடிப்படையில் ஆபத்து மதிப்பெண், தானியங்கி நிர்வாகி எச்சரிக்கைகள் மற்றும் பயனர் பண்புகளின் ஜீரோ-டிரஸ்ட் சரிபார்ப்பு ஆகியவை இதன் முக்கிய அம்சங்களாகும்." }
        ]
    }
};

// API Base URL
const API_BASE = '/api';

// Initialize on page load
document.addEventListener('DOMContentLoaded', function () {
    checkSession();

    // Set up login button handler
    const loginButton = document.getElementById('loginButton');
    if (loginButton) {
        loginButton.addEventListener('click', function () {
            const otpSection = document.getElementById('otpSection');
            if (otpSection && (otpSection.style.display === 'block' || otpSection.style.display !== 'none')) {
                handleOTPValidation();
            } else {
                handleLogin();
            }
        });
    }
});

// Session Management
async function checkSession() {
    try {
        const response = await fetch(`${API_BASE}/auth/session`);
        const data = await response.json();
        if (data.user) {
            currentUser = data.user;
            showMainApp();
        } else {
            showAuthModal();
        }
    } catch (error) {
        console.error('Error checking session:', error);
        showAuthModal();
    }
}

// Authentication
async function handleLogin() {
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;

    if (!username || !password) {
        showNotification('Please enter username and password', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();

        if (data.mfaRequired) {
            pendingUsername = username;
            document.getElementById('otpSection').style.display = 'block';
            // Change login button text
            const loginButton = document.getElementById('loginButton');
            if (loginButton) {
                loginButton.textContent = 'Verify OTP';
            }
            showNotification('OTP has been sent to your email: ' + data.email + '\nPlease check your inbox.', 'info');
        } else if (data.success) {
            currentUser = data.user;
            showMainApp();
            if (data.warning) {
                showNotification(data.warning, 'warning');
            }
        } else {
            showNotification(data.error || 'Login failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

async function handleOTPValidation() {
    const otp = document.getElementById('otpCode').value;
    const password = document.getElementById('loginPassword').value;

    if (!otp) {
        showNotification('Please enter OTP code', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/auth/validate-otp`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ otp, password })
        });

        const data = await response.json();

        if (data.success) {
            currentUser = data.user;
            showMainApp();
            if (data.warning) {
                showNotification(data.warning, 'warning');
            }
        } else {
            showNotification(data.error || 'OTP validation failed', 'error');
            // Reset button if validation fails
            const loginButton = document.getElementById('loginButton');
            if (loginButton) {
                loginButton.textContent = 'Login';
            }
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

// Update login button to handle OTP
document.getElementById('loginUsername').addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        const otpSection = document.getElementById('otpSection');
        if (otpSection.style.display === 'none' || otpSection.style.display === '') {
            handleLogin();
        } else {
            handleOTPValidation();
        }
    }
});

document.getElementById('loginPassword').addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        const otpSection = document.getElementById('otpSection');
        if (otpSection.style.display === 'none' || otpSection.style.display === '') {
            handleLogin();
        } else {
            handleOTPValidation();
        }
    }
});

document.getElementById('otpCode').addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        handleOTPValidation();
    }
});

async function handleRegister() {
    const username = document.getElementById('regUsername').value;
    const password = document.getElementById('regPassword').value;
    const email = document.getElementById('regEmail').value;
    const role = document.getElementById('regRole').value;
    const department = document.getElementById('regDepartment').value;

    if (!username || !password || !email || !department) {
        showNotification('Please fill in all required fields', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password, email, role, department })
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            showLoginForm();
        } else {
            showNotification(data.error || 'Registration failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

async function handleLogout() {
    try {
        await fetch(`${API_BASE}/auth/logout`, { method: 'POST' });
        currentUser = null;
        showAuthModal();
    } catch (error) {
        console.error('Logout error:', error);
        showAuthModal();
    }
}

function showLoginForm() {
    document.getElementById('loginForm').style.display = 'block';
    document.getElementById('registerForm').style.display = 'none';
    document.getElementById('otpSection').style.display = 'none';
    // Reset login button
    const loginButton = document.getElementById('loginButton');
    if (loginButton) {
        loginButton.textContent = 'Login';
    }
    // Clear form fields
    document.getElementById('loginUsername').value = '';
    document.getElementById('loginPassword').value = '';
    document.getElementById('otpCode').value = '';
    pendingUsername = null;
}

function showRegisterForm() {
    document.getElementById('loginForm').style.display = 'none';
    document.getElementById('registerForm').style.display = 'block';
}

function showAuthModal() {
    document.getElementById('authModal').style.display = 'flex';
    document.getElementById('mainApp').style.display = 'none';
    showLoginForm();
}

function showMainApp() {
    document.getElementById('authModal').style.display = 'none';
    document.getElementById('mainApp').style.display = 'block';

    // Update dashboard title
    const userRole = currentUser.attributes.role ? currentUser.attributes.role.toUpperCase() : 'USER';
    document.getElementById('dashboardTitle').textContent =
        `Dashboard - ${currentUser.username} (${userRole})`;

    // Show/hide tabs based on role
    if (currentUser.attributes.role === 'manager' || currentUser.attributes.role === 'admin') {
        document.getElementById('pendingRequestsTab').style.display = 'inline-block';
    } else {
        document.getElementById('pendingRequestsTab').style.display = 'none';
    }

    // Show/hide Upload File button based on role (only managers and admins can upload)
    const uploadButton = document.querySelector('button[onclick="showUploadDialog()"]');
    if (uploadButton) {
        if (currentUser.attributes.role === 'manager' || currentUser.attributes.role === 'admin') {
            uploadButton.style.display = 'inline-block';
        } else {
            uploadButton.style.display = 'none';
        }
    }

    // Show permanent AI warning
    document.getElementById('aiWarningText').style.display = 'inline-block';

    // Load configuration and display AI Chat icon if admin/manager
    loadUiConfig().then(() => {
        if (currentUser.attributes.role === 'manager' || currentUser.attributes.role === 'admin') {
            document.getElementById('aiChatIcon').style.display = 'block';
            initAiChat();
        } else {
            document.getElementById('aiChatIcon').style.display = 'none';
            document.getElementById('aiChatWindow').style.display = 'none';
        }
    });

    // Load initial data
    loadMyFiles();
    loadAvailableFiles();
    loadPendingRequests();
}

// Tab Management
function showTab(tabName) {
    // Hide all tab panes
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.classList.remove('active');
    });

    // Remove active class from all tab buttons
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    // Show selected tab
    document.getElementById(tabName + 'TabContent').classList.add('active');
    document.getElementById(tabName + 'Tab').classList.add('active');
}

// File Management
async function loadMyFiles() {
    try {
        const response = await fetch(`${API_BASE}/files/my-files`);
        const data = await response.json();

        const tbody = document.querySelector('#myFilesTable tbody');
        tbody.innerHTML = '';

        data.files.forEach(file => {
            const row = tbody.insertRow();
            row.onclick = () => selectTableRow(row);
            row.insertCell(0).textContent = file.fileId;
            row.insertCell(1).textContent = file.fileName;
            row.insertCell(2).textContent = formatFileSize(file.fileSize);
            row.insertCell(3).textContent = file.owner;
        });
    } catch (error) {
        console.error('Error loading my files:', error);
    }
}

async function loadAvailableFiles() {
    try {
        const response = await fetch(`${API_BASE}/files/available`);
        const data = await response.json();

        const tbody = document.querySelector('#availableFilesTable tbody');
        tbody.innerHTML = '';

        data.files.forEach(file => {
            const row = tbody.insertRow();
            row.onclick = () => selectTableRow(row);
            row.insertCell(0).textContent = file.fileId;
            row.insertCell(1).textContent = file.fileName;
            row.insertCell(2).textContent = formatFileSize(file.fileSize);
            row.insertCell(3).textContent = file.owner;
            row.insertCell(4).textContent = file.status || 'No Access';
        });
    } catch (error) {
        console.error('Error loading available files:', error);
    }
}

async function loadPendingRequests() {
    if (currentUser.attributes.role !== 'manager' && currentUser.attributes.role !== 'admin') {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/access-requests/pending`);
        const data = await response.json();

        const tbody = document.querySelector('#pendingRequestsTable tbody');
        tbody.innerHTML = '';

        data.requests.forEach(req => {
            const row = tbody.insertRow();
            row.onclick = () => {
                selectTableRow(row);
                selectedRequestId = req.requestId;
            };
            row.insertCell(0).textContent = req.requestId;
            row.insertCell(1).textContent = req.requester;
            row.insertCell(2).textContent = req.fileId;
            row.insertCell(3).textContent = req.fileName || '';
            row.insertCell(4).textContent = new Date(req.requestDate).toLocaleString();
            row.insertCell(5).textContent = req.status;
        });
    } catch (error) {
        console.error('Error loading pending requests:', error);
    }
}

function selectTableRow(row) {
    document.querySelectorAll('tr.selected').forEach(r => r.classList.remove('selected'));
    row.classList.add('selected');
}

// File Upload
function showUploadDialog() {
    document.getElementById('uploadModal').style.display = 'flex';
}

function closeUploadDialog() {
    document.getElementById('uploadModal').style.display = 'none';
}

async function handleUpload() {
    const fileInput = document.getElementById('fileInput');
    const file = fileInput.files[0];

    if (!file) {
        showNotification('Please select a file', 'error');
        return;
    }

    const expiryValue = parseInt(document.getElementById('expiryValue').value);
    const expiryUnit = document.getElementById('expiryUnit').value;
    const requiredRole = document.getElementById('requiredRole').value;
    const requiredDepartment = document.getElementById('requiredDepartment').value;

    const formData = new FormData();
    formData.append('file', file);
    formData.append('expiryValue', expiryValue);
    formData.append('expiryUnit', expiryUnit);
    formData.append('requiredRole', requiredRole);
    if (requiredDepartment) {
        formData.append('requiredDepartment', requiredDepartment);
    }

    try {
        const response = await fetch(`${API_BASE}/files/upload`, {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (data.success) {
            showNotification('File uploaded successfully!', 'success');
            closeUploadDialog();
            loadMyFiles();
        } else {
            showNotification(data.error || 'Upload failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

// File Operations
async function deleteSelectedFile() {
    const row = document.querySelector('#myFilesTable tbody tr.selected');
    if (!row) {
        showNotification('Please select a file to delete', 'error');
        return;
    }

    const fileId = row.cells[0].textContent;
    const fileName = row.cells[1].textContent;

    if (!confirm(`Are you sure you want to delete this file?\n\nFile ID: ${fileId}\nFile Name: ${fileName}\n\nThis action cannot be undone.`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/files/${fileId}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.success) {
            showNotification('File deleted successfully', 'success');
            loadMyFiles();
        } else {
            showNotification(data.error || 'Delete failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

async function viewSelectedPolicy() {
    const row = document.querySelector('#myFilesTable tbody tr.selected');
    if (!row) {
        alert('Please select a file');
        return;
    }

    const fileId = row.cells[0].textContent;

    try {
        const response = await fetch(`${API_BASE}/files/${fileId}/policy`);
        const data = await response.json();

        if (data.error) {
            showNotification(data.error, 'error');
            return;
        }

        let policyText = 'Required Attributes:\n';
        data.requiredAttributes.forEach(attr => {
            policyText += `- ${attr.name}: ${attr.value}\n`;
        });

        showNotification(policyText.replace(/\n/g, '<br>'), 'info');
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

async function requestAccessToSelected() {
    const row = document.querySelector('#availableFilesTable tbody tr.selected');
    if (!row) {
        alert('Please select a file');
        return;
    }

    const fileId = row.cells[0].textContent;

    try {
        const response = await fetch(`${API_BASE}/access-requests/request`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fileId })
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            if (data.warning) {
                showNotification(data.warning, 'warning');
            }
            loadAvailableFiles();
        } else {
            showNotification(data.error || 'Request failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

async function downloadSelectedFile() {
    const row = document.querySelector('#availableFilesTable tbody tr.selected');
    if (!row) {
        showNotification('Please select a file', 'error');
        return;
    }

    const fileId = row.cells[0].textContent;

    try {
        const response = await fetch(`${API_BASE}/files/${fileId}/download`);

        if (!response.ok) {
            const error = await response.json();
            showNotification(error.error || 'Download failed', 'error');
            return;
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = row.cells[1].textContent;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);

        showNotification('File downloaded successfully', 'success');
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

// Access Request Management
function showExpiryDialog() {
    document.getElementById('expiryModal').style.display = 'flex';
}

function closeExpiryDialog() {
    document.getElementById('expiryModal').style.display = 'none';
}

async function confirmApproval() {
    if (!selectedRequestId) {
        showNotification('Please select a request', 'error');
        return;
    }

    const expiryValue = parseInt(document.getElementById('approvalExpiryValue').value);
    const expiryUnit = document.getElementById('approvalExpiryUnit').value;

    try {
        const response = await fetch(`${API_BASE}/access-requests/${selectedRequestId}/approve`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ expiryValue, expiryUnit })
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            closeExpiryDialog();
            loadPendingRequests();
            loadAvailableFiles();
        } else {
            showNotification(data.error || 'Approval failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

async function approveSelectedRequest() {
    const row = document.querySelector('#pendingRequestsTable tbody tr.selected');
    if (!row) {
        showNotification('Please select a request', 'error');
        return;
    }

    selectedRequestId = row.cells[0].textContent;
    showExpiryDialog();
}

async function denySelectedRequest() {
    const row = document.querySelector('#pendingRequestsTable tbody tr.selected');
    if (!row) {
        showNotification('Please select a request', 'error');
        return;
    }

    const requestId = row.cells[0].textContent;
    const reason = prompt('Enter denial reason (optional):');

    try {
        const response = await fetch(`${API_BASE}/access-requests/${requestId}/deny`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason: reason || null })
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            loadPendingRequests();
        } else {
            showNotification(data.error || 'Denial failed', 'error');
        }
    } catch (error) {
        showNotification('Error: ' + error.message, 'error');
    }
}

function refreshPendingRequests() {
    loadPendingRequests();
}

// Utility Functions
function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

function showNotification(message, type = 'info') {
    const container = document.getElementById('notification-container');
    if (!container) return;

    const notification = document.createElement('div');
    notification.className = `notification ${type}`;

    // Icon based on type
    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';
    if (type === 'warning') icon = '⚠️';

    notification.innerHTML = `
        <div style="display: flex; align-items: center; gap: 10px;">
            <span>${icon}</span>
            <span>${message}</span>
        </div>
        <button onclick="this.parentElement.remove()" style="background: none; border: none; cursor: pointer; color: inherit; margin-left: 10px;">✕</button>
    `;

    container.appendChild(notification);

    // Auto remove after 5 seconds
    setTimeout(() => {
        notification.style.animation = 'slideOut 0.3s ease-in forwards';
        setTimeout(() => {
            if (notification.parentElement) {
                notification.remove();
            }
        }, 300);
    }, 5000);
}

// Override alert with notification
window.alert = function (message) {
    showNotification(message, 'info');
};

// --- AI Chatbot Implementation ---
async function loadUiConfig() {
    try {
        const response = await fetch(`${API_BASE}/config/ui`);
        if (response.ok) {
            const data = await response.json();
            if (data.ai_monitoring) {
                Object.assign(aiUiConfig, data.ai_monitoring);
                document.getElementById('aiChatIcon').innerText = aiUiConfig.icon;
                document.getElementById('aiChatHeaderIcon').innerText = aiUiConfig.icon;
                document.getElementById('aiChatIcon').style.background = aiUiConfig.chat_color;
                document.getElementById('aiChatHeader').style.background = aiUiConfig.chat_color;
            }
        }
    } catch (error) {
        console.error("Could not load UI config", error);
    }
}

function initAiChat() {
    const lang = botDatabase[aiUiConfig.language] ? aiUiConfig.language : 'en';
    const db = botDatabase[lang];
    
    // Reset chat body
    const body = document.getElementById('aiChatBody');
    body.innerHTML = '';
    
    // Add welcome message
    const welcomeObj = document.createElement('div');
    welcomeObj.style.cssText = `background: white; border: 1px solid #e2e8f0; color: #334155; padding: 12px 16px; border-radius: 12px; border-bottom-left-radius: 4px; margin-bottom: 10px; width: fit-content; max-width: 85%;`;
    welcomeObj.innerText = db.welcome;
    body.appendChild(welcomeObj);

    // Setup buttons
    const btnContainer = document.getElementById('chatButtons');
    btnContainer.innerHTML = '';
    
    db.questions.forEach(qItem => {
        const btn = document.createElement('button');
        btn.innerText = qItem.q;
        btn.style.cssText = `background: #e0eaf5; color: ${aiUiConfig.chat_color}; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; text-align: left; font-family: 'Outfit', sans-serif; font-size: 13px; transition: background 0.2s;`;
        btn.onmouseover = () => btn.style.background = '#d0dbe8';
        btn.onmouseout = () => btn.style.background = '#e0eaf5';
        btn.onclick = () => askBot(qItem.q, qItem.a);
        btnContainer.appendChild(btn);
    });
}

function toggleAiChat() {
    const win = document.getElementById('aiChatWindow');
    win.style.display = win.style.display === 'none' ? 'block' : 'none';
    if (win.style.display === 'block') {
        const body = document.getElementById('aiChatBody');
        body.scrollTop = body.scrollHeight;
    }
}

function askBot(question, answer) {
    const body = document.getElementById('aiChatBody');
    
    // Add user message
    const userMsg = document.createElement('div');
    userMsg.style.cssText = `background: ${aiUiConfig.chat_color}; color: white; padding: 12px 16px; border-radius: 12px; border-bottom-right-radius: 4px; margin-bottom: 15px; align-self: flex-end; width: fit-content; margin-left: auto; max-width: 85%;`;
    userMsg.innerText = question;
    body.appendChild(userMsg);
    body.scrollTop = body.scrollHeight;
    
    setTimeout(() => {
        const botMsg = document.createElement('div');
        botMsg.style.cssText = `background: white; border: 1px solid #e2e8f0; color: #334155; padding: 12px 16px; border-radius: 12px; border-bottom-left-radius: 4px; margin-bottom: 15px; width: fit-content; max-width: 85%; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);`;
        botMsg.innerText = answer;
        body.appendChild(botMsg);
        body.scrollTop = body.scrollHeight;
    }, 450); // slight delay for realism
}
