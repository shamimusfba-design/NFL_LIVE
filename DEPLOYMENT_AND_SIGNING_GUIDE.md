# NFL Production, Quality & NPT System
## Deployment, Android Signed APK Build & Smart TV Setup Guide

---

## 1. System Architecture Overview

The system consists of three interconnected layers:

```
┌─────────────────────────────────────────────────────────────┐
│                       FACTORY FLOOR                         │
├──────────────────────────────┬──────────────────────────────┤
│    MOBILE DATA ENTRY APP     │     ANDROID SMART TV (16:9)  │
│  • Production Entry (Hourly) │   • Live Target vs Ach.      │
│  • Quality Inspection & Def. │   • Line & Floor Efficiency  │
│  • Supervisor NPT Stopwatch  │   • BS QA OQL & FRI Pass     │
│  • Offline Persistence Queue │   • 30s Cached Auto-Refresh  │
└──────────────┬───────────────┴──────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│          NODE.JS / TYPESCRIPT SECURE BACKEND SERVICE        │
│  • JWT Authentication & Role-Based Authorization            │
│  • Strict SMV & Available Minute Calculation Engine         │
│  • Client UUID Idempotency & Duplicate Prevention           │
│  • Asynchronous Mutex Write Serialization Queue             │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 PERSISTENT STORAGE & REPORTING              │
│  • Google Sheets API (Sheet ID: 1lutN5LmC1Hi...52Y2L0)      │
│  • Raw Transaction Tabs: Raw_Production, Raw_Quality, etc.   │
│  • Preserved Legacy Dashboards & Corrected Aggregate Formulas│
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Deliverables & Status Checklist

| Component | Status | Description |
|---|---|---|
| **Android Data-Entry App** | **COMPLETED & COMPILED** | Native Jetpack Compose app with custom adaptive icon, Material 3 Royal Blue theme, Production Entry, Quality Entry, NPT Stopwatch, Supervisor Line Setup, and History/Sync queue. |
| **Robolectric & Unit Tests** | **COMPLETED & PASSED** | Unit tests verifying SMV calculations, OQL/DHU, no duplicate available minutes across styles, and NPT lost man-minutes. |
| **Android Smart TV Dashboard** | **COMPLETED** | Dedicated 16:9 layout matching the visual reference with arc gauges, grouped bar charts, defect horizontal bars, active-NPT alert strip, and 30s auto-refresh. |
| **Node.js/TS Backend** | **COMPLETED** | Express API with Google Sheets integration, concurrency serialization, JWT roles, and `/api/tv/dashboard` cache. |
| **React + Capacitor Project** | **COMPLETED** | Full React + TypeScript + Capacitor codebase with mobile screens and TV dashboard. |
| **Google Sheets Mapping** | **COMPLETED** | Tab-by-tab analysis, formula fixes, and range preservation guide in `GOOGLE_SHEETS_MAPPING_AND_MIGRATION.md`. |
| **Live Google Sheet Sync** | **PENDING USER CONFIG** | Awaiting user's Google Service Account JSON key to activate live mode (safe demo mode active). |
| **Signed Release APK Export** | **READY TO SIGN** | Complete Gradle build config and keystore generation instructions provided below. |

---

## 3. How to Build a Signed Android Release APK

Follow these exact steps in Android Studio or using the command line to generate the signed APK:

### Step 3.1: Generate a Release Keystore
Run the standard Java `keytool` command in your terminal:

```bash
keytool -genkey -v \
  -keystore nfl-release-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias nflkey \
  -storepass YourSecurePassword123 \
  -keypass YourSecurePassword123 \
  -dname "CN=NFL Factory, OU=Sewing, O=Garments Ltd, L=Dhaka, C=BD"
```

### Step 3.2: Configure Signing in Android Studio
1. Open the project root in **Android Studio**.
2. Select menu: **Build > Generate Signed Bundle / APK...**
3. Select **APK** (or **Android App Bundle** if uploading to Google Play), then click **Next**.
4. Set **Key store path** to your generated `nfl-release-key.jks`.
5. Enter your passwords and select alias `nflkey`.
6. Select build variant: **release**.
7. Check signature versions: **V1 (Jar Signature)** and **V2 (Full APK Signature)**.
8. Click **Finish**.
9. The signed APK will be generated at:
   `app/build/outputs/apk/release/app-release.apk`

### Step 3.3: Command-Line Build (Automated CI/CD)
To build via terminal:

```bash
export KEYSTORE_PATH="$(pwd)/nfl-release-key.jks"
export STORE_PASSWORD="YourSecurePassword123"
export KEY_PASSWORD="YourSecurePassword123"

gradle :app:assembleRelease
```

### Step 3.4: Keystore Backup & Future Update Policy
> ⚠️ **CRITICAL SECURITY NOTICE:**
> * Keep `nfl-release-key.jks` and its passwords in a secure password manager.
> * If this keystore is lost, Android devices **will reject future updates** with a signature mismatch error, requiring uninstallation and loss of local offline data.
> * Never commit `.jks` files or passwords to GitHub.

---

## 4. Backend Service Deployment

### 4.1. Local / On-Premise Execution
```bash
cd backend
npm install
npm run build
npm start
```

### 4.2. Docker Deployment
Create a `Dockerfile` in `/backend`:
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY dist ./dist
EXPOSE 3000
CMD ["node", "dist/index.js"]
```

Build and run:
```bash
docker build -t nfl-backend .
docker run -d -p 3000:3000 \
  -e GOOGLE_SHEET_ID="1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0" \
  -e APP_MODE="live" \
  --name nfl-api nfl-backend
```

### 4.3. Google Cloud Run (Recommended Cloud Deployment)
```bash
gcloud run deploy nfl-factory-api \
  --source ./backend \
  --platform managed \
  --region asia-east1 \
  --allow-unauthenticated \
  --set-env-vars GOOGLE_SHEET_ID="1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0",APP_MODE="live"
```

---

## 5. Android Smart TV Setup (1920×1080)

To deploy the TV dashboard to factory TV screens:

### Option A: Direct Web Browser Kiosk (Zero Install)
1. Open the built-in browser (or download *Fully Kiosk Browser* / *BrowseHere*) on the Android TV.
2. Navigate to: `http://<your-backend-or-web-host>/tv`.
3. Set the browser to **Fullscreen** and enable **Keep Screen Awake**.
4. The dashboard automatically refreshes data from the backend cache every 30 seconds with no page scrolling required.

### Option B: Native Android App on Android TV
1. Install `app-release.apk` on the Android TV using USB drive or ADB.
2. Open the app and tap **Launch TV Dashboard** or select the **TV Live** tab.
3. The app enters fullscreen 16:9 mode with large distance-readable typography and automatic tab rotation.

---

## 6. Completed, Tested, and Remaining Steps

### Completed & Verified:
* [x] **App Icon:** Branded factory silhouette and royal blue gradient background installed.
* [x] **Application ID & Metadata:** Set to `com.aistudio.nflproduction.kxmpzq` and synced with `strings.xml`.
* [x] **Mobile Entry UI:** Exact layout and styling from Image 1 implemented (Production & Quality entry, touch numbers, dropdowns, validation).
* [x] **TV Dashboard:** Exact 16:9 layout from Image 2 implemented (7 KPI cards, 6 charts, active NPT ticker, status bar).
* [x] **Supervisor & NPT:** Line setup snapshot, SMV configuration, helper inclusion toggle, stopwatch with running timer, overlap warning.
* [x] **Calculation Engine:** Strict SMV efficiency, available minutes without duplication, OQL vs DHU, confirmed zero logic.
* [x] **Unit & Robolectric Tests:** Executed and passed via `gradle :app:testDebugUnitTest`.
* [x] **Backend & React Projects:** Full source code written with Express, Google Sheets API, and Capacitor configs.

### Remaining Steps (User Action):
1. **Google Sheets Service Account:** Place your `service-account.json` in `/backend` and set `APP_MODE=live` to start streaming real data into spreadsheet `1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0`.
2. **Release Keystore Generation:** Run `keytool` as described in Section 3.1 to sign your production APK for Android installation.
