# NFL LIVE — Factory Production & Quality Dashboard

NFL LIVE is an Android application for factory production, quality, line targets, monthly performance, incidents/alerts, and a TV dashboard. The project is written in Kotlin using Jetpack Compose.

> **Important:** This README describes features found in the repository source. Actual cloud synchronization depends on Firebase configuration, Firestore security rules, Google Sheets configuration, internet access, and the installed APK version. A feature in code is not by itself proof that a live deployment is configured correctly.

## Contents
- [Main features](#main-features)
- [How synchronization works](#how-synchronization-works)
- [Monthly Data: Auto and Manual](#monthly-data-auto-and-manual)
- [Refresh and real-time updates](#refresh-and-real-time-updates)
- [Screens and source files](#screens-and-source-files)
- [Setup and configuration](#setup-and-configuration)
- [How to test sync across devices](#how-to-test-sync-across-devices)
- [Troubleshooting](#troubleshooting)
- [Current limitations to verify](#current-limitations-to-verify)

## Main features

### Production and factory performance
- Production entry screen for recording line production.
- Line/floor-based performance views and dashboards.
- Line-level detail and trend/chart screens.
- TV dashboard screen intended for a larger display.
- Demo/seed data support in the repository; distinguish demo records from real records when checking synchronization.

### Quality
- Quality entry screen and quality records.
- Quality metrics are used in dashboards and monthly calculations.
- The repository includes quality data synchronization code.

### Line setup and monthly targets
- Supervisor/line setup screen.
- Monthly setup screen for overall/floor-level monthly figures and line-wise monthly figures.
- Monthly line table includes Efficiency, Target, Achievement, DHU and OQL fields.
- **All Floors** view can show lines from multiple floors together.
- The line-wise monthly table uses one **Save All Line-wise Monthly Data** action instead of an individual Save button on every row.
- When saving the All Floors view, each line is saved with its own floor name.

### Incidents, notifications and administration
- Incident/alert handling and a notification center are included in the source.
- Admin/user management screen is present.
- System ledger and audit history screen includes sync status and a manual refresh action.
- Access to individual actions depends on the role and permission logic in the app.

## How synchronization works

The repository contains two shared-data integrations.

### 1. Firebase Cloud Firestore
**FirebaseSyncService.kt** contains Firestore integration and real-time snapshot listeners for these collections:
- **production_entries**
- **quality_entries**
- Factory incidents/alerts
- Line/supervisor setups
- Monthly targets
- User accounts

**NflRepository.kt** connects listener callbacks to the app's state and starts the listeners when the repository initializes. When Firestore accepts a remote change and a listener receives it, the app is designed to update its local in-memory data and screens without the user pressing Refresh.

**This requires** the correct Firebase project configuration, a working network connection, valid Firestore security rules, and compatible data in the database. If Firestore returns a permission error, the app code can show a warning that rules are restricting access.

### 2. Google Sheets
**GoogleSheetsStorageService.kt** contains code to:
- Read data from a configured Google Apps Script Web App endpoint (JSON).
- Fall back to Google Sheets' published/exported CSV endpoints for supported sheet tabs.
- Push pending production/quality records when a valid Apps Script Web App URL is configured.
- Save local records and track the last refresh time.

Google Sheets synchronization is a fetch/push operation when the app's sync routine runs. **Google Sheets by itself does not provide the same instant listener mechanism as Firestore in this app.** Changes made directly in a Sheet may therefore not appear instantly unless another refresh/polling mechanism is implemented and enabled.

The repository's refresh routine combines data from Firestore and Google Sheets. Do not assume both services are successfully connected just because both integrations exist in source.

## Monthly Data: Auto and Manual

The Monthly Setup screen includes an Auto/Manual mode and an All Floors selector.

- **Manual:** IE/Admin users can enter monthly figures in the available overall/floor-wise and line-wise fields, subject to the app's permission checks.
- **Auto:** the screen calculates monthly figures from available daily production and quality records. The exact result depends on the source records, their dates, and the formulas in the current code.
- **October 2026:** the current implementation defaults this month to Manual.
- **Later months:** the current implementation defaults to Auto unless the setting has already been changed on that device.
- **Save All:** in Manual mode, the line-wise Save All button saves all valid line drafts and reports if some rows were skipped due to missing/invalid values.

**Mode-setting note:** the Auto/Manual preference is stored in local SharedPreferences per device in the current implementation. It is not a shared global setting automatically synchronized to every user's device.

## Refresh and real-time updates

These are different mechanisms:

| Mechanism | What it does |
|---|---|
| Firestore real-time listener | Receives supported remote Firestore changes automatically while the listener is connected and authorized. |
| Refresh Data | Calls the repository's sync routine to push pending records and fetch the latest available Firestore/Sheets data. |
| Google Sheets fetch | Reads the configured sheet when the sync routine runs; this is not automatically an instant push notification from Sheets. |
| Local storage | Keeps records on the device; local data alone does not prove that another device has received the record. |

The **System Ledger & Audit History** screen (**HistorySyncScreen.kt**) contains a Refresh icon and a **Refresh Data** button. The refresh action calls **repository.refreshFromGoogleSheet()**.

A refresh button is useful as a fallback, but it cannot fix incorrect Firebase settings, Firestore permissions, an invalid Apps Script URL, or records that were never successfully uploaded.

## Screens and source files

| File | Purpose |
|---|---|
| **app/src/main/java/com/example/MainActivity.kt** | Main navigation and app entry point |
| **app/src/main/java/com/example/data/NflRepository.kt** | Central app data/state, sync orchestration and repository logic |
| **app/src/main/java/com/example/data/FirebaseSyncService.kt** | Firestore access, real-time listeners and cloud record operations |
| **app/src/main/java/com/example/data/GoogleSheetsStorageService.kt** | Google Sheets read/write integration and local persistence |
| **app/src/main/java/com/example/model/DomainModels.kt** | Main data models |
| **app/src/main/java/com/example/ui/screens/ProductionEntryScreen.kt** | Production entry UI |
| **app/src/main/java/com/example/ui/screens/QualityEntryScreen.kt** | Quality entry UI |
| **app/src/main/java/com/example/ui/screens/MonthlySetupScreen.kt** | Monthly Auto/Manual and monthly entry UI |
| **app/src/main/java/com/example/ui/screens/MonthlyBreakdownScreen.kt** | Monthly breakdown view |
| **app/src/main/java/com/example/ui/screens/LineDeepDiveScreen.kt** | Line details |
| **app/src/main/java/com/example/ui/screens/TvDashboardScreen.kt** | TV dashboard |
| **app/src/main/java/com/example/ui/screens/HistorySyncScreen.kt** | Sync status, ledger/audit history and manual refresh |
| **app/src/main/java/com/example/ui/screens/AdminUsersScreen.kt** | User administration UI |
| **app/src/main/java/com/example/ui/screens/NotificationCenterScreen.kt** | Notification center |
| **.github/workflows/build-debug-apk.yml** | GitHub Actions debug APK build workflow |

## Setup and configuration

### Android/Firebase
1. Open the project in Android Studio or use the repository's documented build workflow.
2. Confirm the Firebase configuration file is present and points to the intended Firebase project.
3. Confirm Firestore is enabled in that project.
4. Review and deploy appropriate Firestore security rules. Do not use open public read/write rules in production.
5. Confirm the installed app build contains the same Firebase configuration and code as the intended GitHub commit.

### Google Sheets
1. Open the app's settings/configuration screen.
2. Enter the correct Google Sheet ID or URL.
3. If using Apps Script writes, deploy the Web App and configure its URL as required by the app.
4. Confirm the sheet has the expected tabs/columns for the configured integration.
5. Run a manual refresh and read the sync status message. A success-looking UI message should still be validated against the actual data in the destination sheet/database.

Do not put private API keys, service-account credentials, passwords, or other secrets in this README or commit them to the repository.

## How to test sync across devices

Use two phones and, if available, the TV dashboard. Keep both phones online and use a clearly identifiable test record.

1. On Phone A, create or edit one test production record and save it.
2. Verify that the record exists in the intended shared backend (Firestore or Google Sheets), not only in Phone A's local UI.
3. Keep Phone B on the relevant screen and observe whether it updates without any tap.
4. If it does not update, press **Refresh Data** on Phone B and check the sync status message.
5. Repeat with a quality record and a monthly target, because these use different data paths.
6. Check the TV dashboard separately; confirm whether it updates without reopening the screen.
7. Delete test data only if it is clearly identified as test data and you have permission to do so.

Interpret the result:
- **Updates without interaction:** real-time sync is working for that record type and screen in that test.
- **Updates only after Refresh:** fetch/refresh works, but real-time update for that path/screen may be missing or failing.
- **Does not update after Refresh:** inspect backend configuration, write permissions, read permissions, network, sheet endpoint, and sync status/errors.

## Troubleshooting

### Other phone does not update automatically
- Check that the record was actually saved to Firestore or the shared Sheet.
- Check the sync status/banner and Android logs for Firestore permission errors.
- Verify both devices use the same Firebase project and the intended database.
- Check whether the relevant Firestore listener covers the collection being changed.
- Confirm the screen observes repository state and refreshes its UI when the state changes.

### Refresh works, but live update does not
- Verify that the corresponding Firestore listener is successfully registered.
- Check for listener errors or permission-denied messages.
- If the data source is Google Sheets only, remember that the current Sheets integration is pull-based when sync runs; a refresh/polling strategy may be needed for more frequent updates.

### Data appears on one phone only
- The record may still be local or pending upload.
- Check the record's sync status, configured Sheet ID/Web App URL, Firestore rules, and backend contents.
- Do not mark a record as synced unless the relevant remote write succeeded.

### Monthly Auto values look wrong or empty
- Verify daily production and quality records exist for the selected month and line.
- Check that stored record dates match the date format expected by the monthly calculation.
- Verify that the screen is in Auto mode; Manual mode uses user-entered figures.
- Remember the Auto/Manual choice is device-local in the current implementation.

## Current limitations to verify

- A source-code review cannot prove that Firebase and Google Sheets are configured correctly in the live environment.
- This README does not claim that the latest APK has been built and tested on physical devices.
- Google Sheets changes are not guaranteed to appear instantly; Firestore listeners are the intended real-time path where configured and authorized.
- Auto/Manual mode is stored per device, not globally synchronized.
- The TV dashboard's actual update behavior should be tested on the target TV/device.
- Before production rollout, test roles/permissions, Firestore rules, sync failure handling, duplicate/conflicting edits, and recovery after network loss.

## Repository

- GitHub: https://github.com/shamimusfba-design/NFL_LIVE
- Current default branch: **main**
