# 🛡️ BacoorConnect - Professional Community Safety Platform

**BacoorConnect** is a comprehensive Android-based public safety and incident reporting ecosystem developed specifically for the City of Bacoor. It serves as a digital bridge between citizens and local government, leveraging cutting-edge AI and secure cloud infrastructure to improve emergency response times and community resilience.

---

## 📖 Table of Contents
1. [System Overview](#1-system-overview)
2. [Deep Feature Analysis](#2-deep-feature-analysis)
3. [Comprehensive Tech Stack](#3-comprehensive-tech-stack)
4. [Security Architecture](#4-security-architecture)
5. [API & Remote Config Guide](#5-api--remote-config-guide)
6. [Firebase Infrastructure](#6-firebase-infrastructure)
7. [Installation & Deployment](#7-installation--setup)
8. [Financial & Scaling Model](#8-scaling--costs)
9. [Operational Maintenance](#9-maintenance--operations)
10. [Troubleshooting & Support](#10-troubleshooting--support)

---

## 1. System Overview
BacoorConnect is more than just a reporting tool; it is a verified information network. In an era of misinformation, BacoorConnect ensures that every report is tied to a verified identity, analyzed for authenticity by multiple AI layers, and logged in an immutable audit trail for LGU accountability.

**Key Objectives:**
- Improve emergency response times through real-time incident alerts.
- Reduce manual workload on city hall personnel through automation.
- Increase citizen engagement in community safety.
- Provide verified, trustworthy information to both citizens and LGU officials.

---

## 2. Deep Feature Analysis

### 👥 For Citizens
*   **Intelligent Incident Reporting**: Categorized reporting (Fire, Accident, Natural Disaster, Traffic) with high-precision GPS tagging via `osmdroid`.
*   **Multi-Stage Verification**: 
    1.  **Azure Form Recognizer**: Extracts text from Government IDs to verify identity.
    2.  **JavaMail OTP**: Verifies email ownership.
    3.  **Encrypted Document Storage**: Personal IDs are encrypted with AES-GCM before upload.
*   **Incident Feed & Interaction**: A live feed where users can upvote/downvote reports. High-trust users' reports are prioritized.
*   **Emergency Resource Finder**: Dynamic discovery of nearby hospitals, fire stations, and police stations with one-tap navigation.
*   **Real-Time Weather & Alerts**: Integration with **Open-Meteo** for localized forecasts and **Firebase Cloud Messaging** for government advisories.

### 🛡️ For LGUs (Administrators)
*   **Centralized Moderation Dashboard**: View and moderate all submitted reports in real-time.
*   **User Management**: Manage account status, strikes, and trust scores.
*   **Trust Score Engine**: Automatically calculates user reliability based on AI "Verdict" logs in `ScanLogs`.
*   **Audit Trail**: Immutable log of every administrative action for full accountability.
*   **Automated Content Policing**:
    *   **Azure Content Safety**: Detects hate speech, violence, and sexual content.
    *   **Sightengine**: Flags AI-generated/fake images or inappropriate visual content.
    *   **SERPAPI**: Performs reverse image searches to detect "recycled" or non-original incident photos.

---

## 3. Comprehensive Tech Stack

### Frontend (Android)
*   **Platform**: Android (Java), API Level 31+ (Android 12)
*   **Architecture**: MVC / Repository Pattern
*   **UI Framework**: Material Design Components
*   **Map Engine**: `osmdroid` (OpenStreetMap) - Chosen for flexibility and offline potential.
*   **Networking**: `OkHttp3` & `Retrofit` for secure API communications.
*   **Image Processing**: `Glide` for efficient caching and `Base64` encoding for AI analysis.
*   **Security**: `androidx.security:security-crypto` for local key management.

### Backend (Firebase)
*   **Authentication**: Secure OAuth2-based email/password management.
*   **Realtime Database (`baconek`)**: High-speed, JSON-based state management.
*   **Cloud Storage**: Encrypted bucket for IDs and public bucket for report images.
*   **Remote Config**: Centralized management for all API keys and threshold settings.

---

## 4. Security Architecture

### 🛡️ The "Zero-Storage" Password Policy
One of BacoorConnect's core security pillars is that **user passwords are never stored in the database.**
1.  **Registration Phase**: Password is input and stored in `EncryptedSharedPreferences` (accessible only by the app).
2.  **Verification Phase**: User must pass ID Verification and OTP.
3.  **Creation Phase**: The password is sent directly to `FirebaseAuth.createUserWithEmailAndPassword`.
4.  **Purge**: The local encrypted copy is wiped immediately.

### 🗝️ API Key Management
To prevent source-code leaks, no API keys are hardcoded.
- Keys are fetched from **Firebase Remote Config** on app startup (`MyApplication.java`).
- They are stored in **EncryptedSharedPreferences** using the **Android Keystore System** (AES-256 GCM).

### 🔒 Document Encryption
Before any Government ID is uploaded to Firebase Storage, it is encrypted on-device using a **SecretKeySpec** derived from a user-specific salt. Even with root access to Firebase Storage, the files are unreadable.

---

## 5. API & Remote Config Guide
The following keys must be configured in the Firebase Remote Config console for the app to function:

| Parameter Key | Description | Service |
| :--- | :--- | :--- |
| `azure_key` | Primary key for Form Recognizer | Azure |
| `vision_key` | Key for Computer Vision (Captions) | Azure |
| `content_safety_key` | Key for Text/Image moderation | Azure |
| `sightengine_api_user` | User ID for image detection | Sightengine |
| `sightengine_api_secret` | Secret key for image detection | Sightengine |
| `sightengine_threshold` | Confidence threshold (0.5-0.8) | Sightengine |
| `serpapi_key` | Key for Reverse Image Search | SerpApi |
| `google_places_api_key` | Key for nearby hospitals/stations | Google Places |
| `email_address` | SMTP sender email for OTPs | JavaMail |
| `email_password` | App Password for SMTP email | JavaMail |

### Azure Form Recognizer
**Purpose:** Extracts text from Government IDs during registration.

**Setup:**
1. Create Azure Cognitive Services resource
2. Enable prebuilt-idDocument model
3. Copy endpoint URL and key

**Endpoint:** `https://[region].api.cognitive.microsoft.com/`

### Azure Content Safety
**Purpose:** Moderates text descriptions and images for inappropriate content (violence, hate speech, sexual content).

**Setup:**
1. Create Azure Content Safety resource
2. Copy endpoint URL and key

### Azure Computer Vision
**Purpose:** Analyzes images to verify they match the selected category (e.g., accident photo matches "accident" category).

**Setup:**
1. Create Azure Computer Vision resource
2. Enable Dense Captioning
3. Copy endpoint URL and key

### Sightengine
**Purpose:** Detects AI-generated/fake images to prevent misinformation.

**Setup:**
1. Sign up at [sightengine.com](https://sightengine.com)
2. Subscribe to Face Analysis plan
3. Copy `api_user` and `api_secret`

**Threshold:** `sightengine_threshold` (recommended 0.7)
- 0.5 = More sensitive (flags more images)
- 0.8 = Less sensitive (flags obvious AI only)

### SerpApi
**Purpose:** Reverse image search to detect if a photo already exists online (prevents recycled/fake images).

**Setup:**
1. Sign up at [serpapi.com](https://serpapi.com)
2. Subscribe to plan
3. Copy API key

### JavaMail (Gmail SMTP)
**Purpose:** Sends OTP (One-Time Password) emails during registration.

**Setup:**
1. Create Gmail account (e.g., `bacoorconnect@gmail.com`)
2. Enable 2-Step Verification
3. Generate App Password:
   - Google Account → Security → App Passwords
   - Select "Mail" and "Android Device"
   - Copy 16-character password

**Important:** Use App Password, NOT your actual Gmail password.

### Open-Meteo API
**Purpose:** Provides real-time weather data and forecasts.

**Setup:** No API key required. Uses public endpoint.

**Endpoint:** `https://api.open-meteo.com/v1/`

### Google Places API
**Purpose:** Finds nearby hospitals, fire stations, and police stations for emergency services.

**Setup:**
1. Enable Places API in Google Cloud Console
2. Create API key
3. Restrict key to Android apps

### Firebase Services
**Services Used:** Authentication, Realtime Database, Cloud Storage, Remote Config, Cloud Messaging

**Setup:**
1. Create Firebase project
2. Enable Email/Password authentication
3. Create Realtime Database (start in test mode, then apply rules)
4. Enable Cloud Storage
5. Set up Remote Config
6. Download `google-services.json`

---

### Where to Find All Keys
| Service | Where to Find |
| :--- | :--- |
| Azure Keys | Azure Portal → Your Resource → Keys and Endpoint |
| Sightengine | Sightengine Dashboard → API Keys |
| SerpApi | SerpApi Dashboard → API Key |
| Gmail App Password | Google Account → Security → App Passwords |
| Google Places | Google Cloud Console → Credentials |

### Adding Keys to Remote Config
1. Go to Firebase Console → Remote Config
2. Click Add Parameter
3. Enter the key name (exact match from table above)
4. Enter the value
5. Set data type (String for all except `sightengine_threshold` which is Double)
6. Click Save
7. Click Publish Changes


### 🔹 Azure Form Recognizer
**Purpose**: Extracts text from Government IDs during registration.

**Setup**:
1. Create Azure Cognitive Services resource.
2. Enable `prebuilt-idDocument` model.
3. Copy endpoint URL and key.

**Endpoint**: `https://[region].api.cognitive.microsoft.com/`

### 🔹 Azure Content Safety
**Purpose**: Moderates text descriptions and images for inappropriate content (violence, hate speech, sexual content).

**Setup**:
1. Create Azure Content Safety resource.
2. Copy endpoint URL and key.

### 🔹 Azure Computer Vision
**Purpose**: Analyzes images to verify they match the selected category (e.g., accident photo matches "accident" category).

**Setup**:
1. Create Azure Computer Vision resource.
2. Enable **Dense Captioning**.
3. Copy endpoint URL and key.

### 🔹 Sightengine
**Purpose**: Detects AI-generated/fake images to prevent misinformation.

**Setup**:
1. Sign up at [sightengine.com](https://sightengine.com).
2. Subscribe to **Face Analysis** plan.
3. Copy `api_user` and `api_secret`.

**Threshold**: `sightengine_threshold` (recommended **0.7**)
- **0.5**: More sensitive (flags more images).
- **0.8**: Less sensitive (flags obvious AI only).

### 🔹 SerpApi
**Purpose**: Reverse image search to detect if a photo already exists online (prevents recycled/fake images).

**Setup**:
1. Sign up at [serpapi.com](https://serpapi.com).
2. Subscribe to a plan.
3. Copy API key.

### 🔹 JavaMail (Gmail SMTP)
**Purpose**: Sends OTP (One-Time Password) emails during registration.

**Setup**:
1. Create a Gmail account (e.g., `bacoorconnect@gmail.com`).
2. Enable **2-Step Verification**.
3. Generate **App Password**:
   - Google Account → Security → App Passwords.
   - Select "Mail" and "Android Device".
   - Copy the 16-character password.

**Important**: Use the **App Password**, NOT your actual Gmail password.

### 🔹 Open-Meteo API
**Purpose**: Provides real-time weather data and forecasts.

**Setup**: No API key required. Uses public endpoint.

**Endpoint**: `https://api.open-meteo.com/v1/`

### 🔹 Google Places API
**Purpose**: Finds nearby hospitals, fire stations, and police stations for emergency services.

**Setup**:
1. Enable **Places API** in Google Cloud Console.
2. Create an API key.
3. Restrict the key to Android apps.

### 🔹 Firebase Services
**Services Used**: Authentication, Realtime Database, Cloud Storage, Remote Config, Cloud Messaging.

**Setup**:
1. Create a Firebase project.
2. Enable **Email/Password authentication**.
3. Create **Realtime Database** (start in test mode, then apply rules).
4. Enable **Cloud Storage**.
5. Set up **Remote Config**.
6. Download `google-services.json`.

---

### 🔍 Where to Find All Keys
| Service | Where to Find |
| :--- | :--- |
| **Azure Keys** | Azure Portal → Your Resource → Keys and Endpoint |
| **Sightengine** | Sightengine Dashboard → API Keys |
| **SerpApi** | SerpApi Dashboard → API Key |
| **Gmail App Password** | Google Account → Security → App Passwords |
| **Google Places** | Google Cloud Console → Credentials |

### 🚀 Adding Keys to Remote Config
1. Go to Firebase Console → **Remote Config**.
2. Click **Add Parameter**.
3. Enter the key name (exact match from table above).
4. Enter the value.
5. Set data type (**String** for all except `sightengine_threshold` which is **Double**).
6. Click **Save**.
7. Click **Publish Changes**.

---

## 6. Firebase Infrastructure

### Database Schema Breakdown
- **`Users/`**: Profile data, trust scores, and account status.
- **`Report/`**: Main incident data (latitude, longitude, category, imageUrl).
- **`ScanLogs/`**: History of AI analysis results for every post.
- **`audit_trail/`**: Immutable log of administrative actions.
- **`otp_requests/`**: Temporary storage for generated verification codes.
- **`temp_registrations/`**: Intermediate storage for users awaiting verification.
- **`Earthquakes/`, `HourlyForecast/`, `WeatherForecast/`**: Cached weather and geological data.

### 🛡️ Security Rules (Production Ready)

#### Realtime Database Rules
```json
{
  "rules": {
    "otp_requests": {
      "$email": {
        ".write": true,
        ".read": true
      }
    },

    "ScanLogs": {
      ".write": "auth != null",
      ".read": "auth != null",
      ".indexOn": ["userId"]
    },

    "ReportFlags": {
      ".read": "auth != null",
      ".write": "auth != null",
      "$reportId": {
        ".read": "auth != null",
        "$flagId": {
          ".write": "auth != null",
          ".validate": "newData.hasChildren(['userId', 'reason', 'timestamp'])"
        }
      }
    },

    "Report": {
      ".read": true,
      "$reportId": {
        ".read": true,
        ".write": "auth != null",
        "flagCount": {
          ".write": "auth != null"
        },
        "flags": {
          ".write": "auth != null",
          "$flagId": {
            ".validate": "newData.hasChildren(['userId', 'reason', 'timestamp'])"
          }
        },
        "editCount": {
          ".write": "auth != null"
        },
        "lastEdited": {
          ".write": "auth != null"
        },
        "editHistory": {
          ".write": "auth != null",
          "$editId": {
            ".validate": "newData.hasChildren(['timestamp', 'changedFields'])"
          }
        }
      }
    },

    "temp_registrations": {
      ".indexOn": ["email", "firstName", "lastName"],
      ".read": true,
      "$tempId": {
        ".write": "newData.child('email').exists()",
        ".read": true,
        ".validate": "newData.hasChildren(['firstName', 'lastName', 'email']) && !newData.hasChild('password')"
      }
    },

    "Users": {
      ".indexOn": ["email"],
      ".read": true,
      "$userId": {
        ".read": "$userId === auth.uid || root.child('Users').child(auth.uid).child('admin').val() == 1",
        ".write": "$userId === auth.uid || root.child('Users').child(auth.uid).child('admin').val() == 1",
        "firstName": {
          ".read": true
        },
        "lastName": {
          ".read": true
        },
        "profileImage": {
          ".read": true
        },
        "trustScore": {
          ".read": true
        },
        "totalReports": {
          ".read": true
        },
        "approvedReports": {
          ".read": true
        },
        "joinDate": {
          ".read": true
        },
        "admin": {
          ".read": "$userId === auth.uid"
        },
        "email": {
          ".read": "$userId === auth.uid || root.child('Users').child(auth.uid).child('admin').val() == 1"
        },
        "password": {
          ".read": false
        },
        "phone": {
          ".read": "$userId === auth.uid"
        },
        "address": {
          ".read": "$userId === auth.uid"
        },
        "status": {
          ".write": "$userId === auth.uid"
        }
      }
    },

    "audit_trail": {
      ".read": true,
      ".write": true
    },

    "Earthquakes": {
      ".read": true,
      ".write": true
    },
    "HourlyForecast": {
      ".read": true,
      ".write": true
    },
    "WeatherForecast": {
      ".read": true,
      ".write": true
    }
  }
}
```

#### Cloud Storage Rules
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /temp_ids/{allPaths=**} {
      allow write: if true;
      allow read: if true;
    }
    
    match /report_images/{allPaths=**} {
      allow write: if request.auth != null;
      allow read: if true;
    }
    
    match /user_ids/{allPaths=**} {
      allow write: if request.auth != null;
      allow read: if request.auth != null;
    }
    
    match /profile_images/{userId}/{allPaths=**} {
      allow write: if request.auth != null && request.auth.uid == userId;
      allow read: if true;
    }
    
    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

---

## 7. Installation & Setup

### For Developers
1.  **Clone & Sync**: Open in Android Studio Flamingo+.
2.  **Firebase**: Add `google-services.json` to the `app/` folder.
3.  **Remote Config**: Populate keys in Firebase Console as per [Section 5](#5-api--remote-config-guide).
4.  **Email**: Configure a Gmail account with "App Passwords" enabled for the OTP service.

### For LGU Deployment
1.  **Billing**: Connect a debit card to Azure and Firebase. Ensure you stay within the **Free Tier** for initial testing.
2.  **Admin Creation**: Manually set `admin: 1` in the database for the head moderator's account.

---

## 8. Financial & Scaling Model

BacoorConnect is designed to be highly cost-efficient by using localized caching for weather and Maps.

### Detailed Service Costs (Monthly Estimates)
| API / Service | Recommended Plan | Estimated Cost | Included Usage |
| :--- | :--- | :--- | :--- |
| **SERPAPI** | Developer | $75 (₱4,200) | 5,000 searches |
| **SightEngine** | Pro | $99 (₱5,500) | 40,000 operations |
| **Azure Services** | Pay-as-you-go | ~$16 (₱900) | 10K moderation, 5K vision |
| **Firebase** | Blaze | ~$10 (₱560) | Storage & DL overages |
| **Total** | - | **~$200 (≈ ₱11,147)** | - |

### Efficiency Projections
| Active Users | Cost Per User |
| :--- | :--- |
| 1,000 | ₱4.00 - ₱6.00 |
| 5,000 | ₱1.60 - ₱2.00 |
| 10,000 | ₱1.11 |

**Note**: Costs are primarily driven by Azure OCR and Sightengine operations. High-volume discounts are available at higher tiers.

---

## 9. Maintenance & Operations

### Daily Tasks
- **Moderation**: Review reports in the "Flagged" queue.
- **Audit Review**: Check the `audit_trail` to ensure moderators are acting fairly.
- **Monitor Usage**: Check API quotas in Azure and Sightengine dashboards.
- **Database Backup**: Periodically export the JSON state from the Firebase Console.

### LGU Recommendations
- **Dedicated Team**: Assign at least one staff member to active monitoring.
- **Key Rotation**: Periodically rotate API keys in Remote Config for security.
- **Community Engagement**: Promote the app through official barangay channels.

---

## 10. Troubleshooting & Support

- **OTP Issues**: Check if the "App Password" in Remote Config has expired. Verify SMTP settings.
- **ID OCR Failing**: Ensure the user has provided a clear, glare-free photo of a supported ID (UMID, Driver's License, PhilID).
- **API Errors**: Verify that your Azure/Sightengine subscriptions are active and have available credits.

---

## 📞 Support & Contact
**Developer**: [FourSight]  
**Email**: [marcdaniel.manuel@gmail.com]

📄 **License**: This project and its underlying architecture are developed exclusively for the City of Bacoor. All rights reserved.
