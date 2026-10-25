# NFL LIVE — Complete Data Reset Controls & Free Installable Desktop PWA

This plan addresses the root causes preventing Admin data clearing from sticking across sessions and delivers a 100% free, real-time **Desktop Web & Installable PWA** edition connected to the same Firebase Firestore database used by the Android Phone and Smart TV apps.

## User Review & Critical Decisions

> [!IMPORTANT]
> Please review the confirmed architectural choices below and click **Proceed** to build both the Android Admin Reset fix and the Free Desktop Web/PWA package.

- **Confirmed Decision 1 — Dual Admin Reset Controls**: Provide both **Clear Demo Data** (removes only sample/synthetic records and immediately persists the clean state to disk) and **Factory Reset All Records** (wipes all Production, Quality, NPT Incidents, and Daily Line Setups from both Local Storage and Cloud Firestore so the factory starts at clean `0` values, while preserving User Accounts, Roles, and Line Master definitions).
- **Confirmed Decision 2 — Zero Synthetic Fallback After Reset**: Once Demo Data or Factory Reset is triggered, hourly charts and line issue tables will display true `0` values instead of falling back to simulated numbers when no real entries exist yet.
- **Confirmed Decision 3 — 100% Free Desktop Version via Firebase-Synced PWA**: Build a full-featured Web & Installable Desktop Progressive Web App (PWA) configured with a Web App Manifest and Service Worker so any Windows/Mac PC can install it directly from Chrome/Edge as a standalone desktop window or deploy it for free on Firebase Hosting.
- **Confirmed Decision 4 — Full Desktop Feature Parity**: Include Live Factory Dashboard, Batch & Single Data Entry (Monthly/Morning Setup, Production, Quality, NPT Incidents), Admin Control Center (including User Management and Data Reset), Excel/CSV Report Export, and the 4-Page Auto-Rotating TV Wallboard View.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Permanent Admin Data Clearing & Factory Reset (Android + Cloud)**: Fixes `removeAllDemoData()` so local storage is immediately overwritten and synthetic chart fallbacks are turned off, and adds a protected **Factory Reset All Records** action in Admin & Settings that deletes test records from both device storage and Cloud Firestore collections (`nfl_production`, `nfl_quality`, `nfl_incidents`, `nfl_line_setups`).
  2. **Free Desktop Web & Installable PWA**: Upgrades the web dashboard into a live, Firebase-connected Desktop PWA that synchronizes in real time with the Android phones and Smart TVs on the factory floor, plus generates a ready-to-open standalone desktop bundle.
- **Target Audience / Persona**: Factory Administrators, IE Officers, Production/Quality Supervisors working on office PCs, and Management monitoring live floor KPIs on desktop monitors.
- **Key Value**: Eliminates ghost/resurrected test data when going live on the factory floor and provides a zero-cost desktop application for PC operators and managers without paying for extra hosting or licensing.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Admin Data Reset Flow (Android & Desktop)**:
     - Admin opens **Settings** or **Admin Access Control** → **Data Management & Reset** section.
     - Chooses either **Clear Demo Data** (removes sample-tagged data and disables synthetic chart fillers) or **Factory Reset All Operational Data** (confirmation dialog requiring explicit confirmation before wiping all Production, Quality, Incident, and Setup records from Local + Firestore).
     - Dashboard immediately reflects clean `0` metrics across all floors and stays clean after app restart or cloud sync.
  2. **Desktop PC Installation & Operation Flow**:
     - User opens the Desktop Web App in Chrome or Microsoft Edge on Windows/Mac (either via free Firebase Hosting URL or local desktop launcher) and clicks **"Install NFL LIVE Desktop"** in the title bar.
     - Logs in with their existing Employee ID and Password (e.g., Admin `1015198`).
     - Navigates seamlessly across **Live Floor Dashboard**, **PC Batch Data Entry**, **Incident Command**, **Excel/CSV Reports**, **Admin Control**, and **Live TV Wallboard Mode**.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Industrial High-Contrast Factory Telemetry UI matching the Android and TV Wallboard suite.
  - *Color Palette & Mood*: Deep Slate Navy (`#0B1120` / `#0F172A`) surfaces, crisp Emerald (`#10B981`) for on-target KPIs, Amber (`#F59E0B`) for warnings, Crimson (`#EF4444`) for critical DHU/NPT alerts and destructive reset actions, and Electric Blue (`#3B82F6`) for primary controls.
  - *Typography & Hierarchy*: Bold tabular numerals (`font-variant-numeric: tabular-nums`) for output, efficiency, OEE, and DHU counters; clean sans-serif labels for rapid scanning on 1080p/4K desktop monitors.
  - *Component Styling & Layout*: Multi-column widescreen desktop grid with a persistent top command bar, quick floor switcher (`All`, `Sew 1`, `Sew 2`, `Sew 3`, `Sew 4`, `Finishing`), modal/drawer forms for rapid keyboard tab-navigation during batch entry, and 1-click CSV/Excel download.
- **Interactive Feedback & Motion**:
  - Instant toast banners confirming cloud deletion counts and sync status (`LIVE CLOUD SYNCED`, `DEMO CLEARED`, `FACTORY RESET COMPLETE`).
  - Destructive confirmation modals with record counts before purging cloud data.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Separate "Clear Demo Data" vs. "Factory Reset All Records"**
  - *Chosen Approach*: Provide both buttons in Admin & Settings, with clear record counters (`Demo Records` vs. `Live/Cloud Operational Records`).
  - *Why*: When testing the app, admins often enter test production/quality rows that are saved as "real" (`isDemo = false`) records in Firestore. A "Clear Demo Data" button alone only filters `isDemo == true`, leaving test submissions in Firestore. Adding **Factory Reset All Records** lets the admin wipe all test submissions from Firestore and local storage in one click when ready for real factory production, without losing employee accounts.
  - *Alternatives Considered*: Making "Clear Demo Data" wipe all real data silently — rejected because it could accidentally delete real production records later.
- **Decision 2: Installable Desktop PWA + Firebase Hosting Configuration**
  - *Chosen Approach*: Connect the React + TypeScript desktop web app directly to the project's live Firestore database (`ai-studio-android-nfllivea-664f462a-fb9e-483d-9567-6ac2c98cf7a4`), add a PWA `manifest.webmanifest` and offline-ready Service Worker, and configure `firebase.json` hosting + a zero-cost PC launch script.
  - *Why*: Firebase Hosting's Spark plan is 100% free (SSL included) and Chrome/Edge on Windows 10/11 natively installs PWAs as standalone desktop apps with taskbar/desktop shortcuts and no browser address bar.
  - *Alternatives Considered*: Paid desktop cloud VMs or heavy Electron installers requiring code-signing certificates — rejected due to cost and deployment friction.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   SHARED FIREBASE CLOUD BACKEND (FREE)                     │
│   Named Database: ai-studio-android-nfllivea-664f462a-fb9e-483d-9567-...   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────────────┐  │
│  │nfl_production│ │ nfl_quality  │ │nfl_incidents │ │nfl_line_setups/usr│  │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └─────────┬─────────┘  │
└─────────┼────────────────┼────────────────┼───────────────────┼────────────┘
          │ Real-Time Sync │ Batch Delete   │ Real-Time Sync    │
   ┌──────┴────────────────┴───────┐ ┌──────┴───────────────────┴──────────┐
   │   ANDROID APP (PHONE & TV)    │ │   FREE DESKTOP WEB & PWA CLIENT     │
   │ ┌───────────────────────────┐ │ │ ┌─────────────────────────────────┐ │
   │ │ NflRepository             │ │ │ │ Firestore Realtime Sync Engine  │ │
   │ │ • removeAllDemoData()     │ │ │ │ • Live onSnapshot Listeners     │ │
   │ │   + Immediate Disk Flush  │ │ │ │ • Batch Cloud Write & Reset     │ │
   │ │   + Disable Seed Fallback │ │ │ └────────────────┬────────────────┘ │
   │ │ • resetAllOperationalData │ │ │                  ▼                  │
   │ │   + Firestore Batch Purge │ │ │ ┌─────────────────────────────────┐ │
   │ └─────────────┬─────────────┘ │ │ │ Widescreen Desktop UI + PWA     │ │
   │               ▼               │ │ │ • Floor KPIs & Line Grid        │ │
   │ ┌───────────────────────────┐ │ │ │ • Batch Setup/Prod/QA Entry     │ │
   │ │ Settings & Admin Screens  │ │ │ │ • Excel/CSV Export & TV View    │ │
   │ │ • Clear Demo Button       │ │ │ │ • Admin Users & Data Reset      │ │
   │ │ • Factory Reset Dialog    │ │ │ └─────────────────────────────────┘ │
   │ └───────────────────────────┘ │ └─────────────────────────────────────┘
   └───────────────────────────────┘
```

- **Data Model & State**:
  - **Immediate Persistence on Clear**: `removeAllDemoData()` in `NflRepository` will immediately write the filtered lists (`_targets`, `_productionEntries`, `_qualityEntries`, `_incidents`) to `sheetsStorage` and flush pending persistence so restarting the app never reloads old cached JSON.
  - **Eliminating Synthetic Fallbacks**: `getHourlyProductionData()` and `getLineIssues()` will check `!_isDemoMode.value && prefs.getBoolean("sample_data_cleared", false)` so empty slots return `0` output and `emptyList()` issues instead of hardcoded random seed numbers.
  - **Cloud + Local Operational Reset (`resetAllOperationalData`)**: Clears local lists, persists empty arrays to `SharedPreferences`, sets `sample_data_cleared = true`, and invokes `FirebaseSyncService.deleteAllOperationalRecords()` (using chunked Firestore `WriteBatch` deletes of up to 450 docs per batch across `nfl_production`, `nfl_quality`, `nfl_incidents`, and `nfl_line_setups`).
- **Interactive Component & State Mapping**:
  - **Android Admin & Settings Reset Panel**: Shows live counts of Demo vs. Cloud/Local Operational records, with distinct confirmation dialogs for **Remove Demo Data** and **Factory Reset All Records (Local + Cloud)**.
  - **Desktop PWA Workspace**: Provides top-bar navigation between **Desktop Dashboard**, **Batch Entry (Setup / Production / Quality)**, **NPT Incidents**, **Excel/CSV Export**, **Admin Panel (Users + Data Reset)**, and **4-Page TV Wallboard Mode**, plus an **Install Desktop App** prompt button.
