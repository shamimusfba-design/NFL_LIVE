# NFL LIVE — Release Readiness Audit Validation & Hardening Plan

## 1. Validation Summary of ChatGPT's Audit Findings

| Audit Item | Validation Result | Action to Take |
| :--- | :--- | :--- |
| **Web vs. Android Firestore Document IDs** | **Confirmed Bug**: Both apps connect to the same named Firestore database (`ai-studio-android-nfllivea-664f462a-fb9e-483d-9567-6ac2c98cf7a4`), but `web-dashboard-and-mobile` formats document IDs differently (`K1_2026-10-26_08_00_-_09_00`, `SEW_2_K1_2026-10-26`, `SEW_2__2026-10`) than Android (`PROD_K1_2026-10-26_0800-0900`, `QA_K1_2026-10-26_0800-0900`, `K1_2026-10-26`, `Sew 2`). | Align all canonical document IDs in `web-dashboard-and-mobile/src/services/firebaseLiveSync.ts` to match Android's exact `FirebaseSyncService.kt` format so edits on Web and Android update the exact same document without duplicates. |
| **Web Role Switcher Privilege Escalation** | **Confirmed Security Hole**: In `web-dashboard-and-mobile/src/App.tsx`, clicking the top-bar user badge opens a Role Switcher modal that allows any non-Admin user to switch their active session to `System Admin (ADMIN)` without entering a password. | Lock the Role Switcher in `web-dashboard-and-mobile/src/App.tsx` so only a user who authenticated as `ADMIN` can preview other roles; non-Admin users see their locked session badge without role escalation. |
| **Firestore Security Rules (`firestore.rules`) & Employee ID Login** | **Confirmed Schema Hardening Needed**: Keeping Employee ID + SHA-256 password login ensures zero breakage on factory phones and TVs. However, `firestore.rules` had a typo on the initial Admin document path (`users/U-1015198` instead of `users/1015198`), and `firebaseLiveSync.ts` Web Admin approval payloads needed `id`, `employeeId`, and `name` fields so `setDoc(..., { merge: true })` never fails Firestore `create` validation if a document (`tv_display` or a seeded user) does not yet exist in the cloud. | Harden `firestore.rules` with strict schema/field checks (`lineId`, `date`, `hourSlot`, `floor`, non-negative numbers, protected `1015198` Admin account, no plaintext passwords), update `firebaseLiveSync.ts` approval payloads, and deploy rules via `deploy_firebase`. |
| **Admin Phone System Notifications for Registrations & TV Setup** | **Confirmed Gap**: Pending employee registrations (`PENDING_APPROVAL`), employee device replacement requests (`DEV_PENDING:`), and first-time Smart TV requests (`TV_PENDING:`) appear in the Admin UI, but `FactoryAlertManager` did not post an Android system notification or audible alert when a new request arrived. | Add `triggerAdminApprovalNotification` in `FactoryAlertManager.kt` and invoke it in `NflRepository.upsertRemoteUsers()` whenever a logged-in Admin phone receives a new registration, device replacement, or Smart TV setup request. Tapping the notification opens `MainActivity` directly into the Admin Approval screen. |
| **Standalone `desktop-pwa/` Warning Banner** | **Confirmed**: Keep `desktop-pwa/` separate with its prominent `LOCAL ONLY — NOT CONNECTED TO FIREBASE` warning banner, and remove the misleading Admin privilege escalation from its header notes. | Keep `desktop-pwa/` separate with its clear warning banner as requested. |
| **GitHub Actions CI Pipeline (`.github/workflows/build-debug-apk.yml`)** | **Confirmed Gap**: `.github/workflows/build-debug-apk.yml` only ran `:app:assembleDebug` and did not execute Android unit tests (`:app:testDebugUnitTest`) or verify the `web-dashboard-and-mobile` production build (`npm run build`). | Update `.github/workflows/build-debug-apk.yml` (and `.github/workflows/android-build.yml`) to run `:app:testDebugUnitTest`, build `web-dashboard-and-mobile` (`npm ci && npm run build`), and upload both the `NOS-Live-app-debug.apk` and the built `web-dashboard-dist` artifact ready for Firebase Hosting. |

---

## 2. Planned Code Changes

### A. Align Web & Android Canonical Firestore Document IDs (`web-dashboard-and-mobile/src/services/firebaseLiveSync.ts`)
- Update `canonicalProdDocId(entry)` to generate `PROD_${cleanLine}_${cleanDate}_${cleanHour}` (matching `FirebaseSyncService.canonicalProductionDocId`).
- Update `canonicalQaDocId(entry)` to generate `QA_${cleanLine}_${cleanDate}_${cleanHour}` (matching `FirebaseSyncService.canonicalQualityDocId`).
- Update `canonicalSetupDocId(setup)` to generate `${cleanLine}_${cleanDate}` (matching `FirebaseSyncService.pushLineSetupAwait`).
- Update `pushMonthlyTargetToCloud(target)` to use `${target.floor}` (or `${target.floor}_${target.lineId}`) and include `bsOqlPct` alongside `bsQaOqlPct` (matching `FirebaseSyncService.pushMonthlyTargetAwait`).
- Ensure `approveCloudUserRegistration`, `rejectCloudUserRegistration`, and `approveCloudDeviceOrTvRequest` always write `id`, `employeeId`, `name`, and `password: ''` so Firestore security rules pass whether updating or creating the document.

### B. Lock Web Role Switcher Against Non-Admin Escalation (`web-dashboard-and-mobile/src/App.tsx`)
- Track `authenticatedAdmin` state (true only when the user actually logged in through `LoginView` with an `ADMIN` account).
- Only allow opening the Role Preview Switcher (`showRoleSelector`) when `authenticatedAdmin` is `true`; for all other roles, clicking the user badge displays their locked role/floor assignment without allowing role switching.

### C. Add Android System Notifications for Admin Approvals (`FactoryAlertManager.kt`, `NflRepository.kt`, `MainActivity.kt`)
- Add `triggerAdminApprovalNotification(requestKey: String, title: String, body: String)` to `FactoryAlertManager.kt`, posting a high-priority notification on `CHANNEL_CRITICAL` with an intent extra `open_admin_approvals = true` and a chime when sound is enabled.
- In `NflRepository.upsertRemoteUsers()`, when the active user on the phone is an Admin (`activeBefore?.isAdmin == true`), trigger `alertManager.triggerAdminApprovalNotification(...)` whenever a new `PENDING_APPROVAL` employee registration, `TV_PENDING:` Smart TV setup request, or `DEV_PENDING:` device replacement request is detected.
- In `MainActivity.kt`, inspect `intent?.getBooleanExtra("open_admin_approvals", false)` so tapping the notification navigates straight to the Admin Users & Device Requests screen.

### D. Harden `firestore.rules` & Deploy to Firebase
- Fix the initial Admin document path check to `/databases/$(database)/documents/users/1015198`.
- Enforce required schema fields (`lineId`, `date`, `hourSlot`, `floor`, `hasNoPlaintextPassword`) across `users`, `production_entries`, `quality_entries`, `factory_alerts`, `daily_setups`, `monthly_targets`, and `weekly_quality`.
- Deploy the updated rules immediately via `deploy_firebase`.

### E. Upgrade GitHub Actions CI (`.github/workflows/build-debug-apk.yml`)
- Add steps to run `gradle --no-daemon :app:testDebugUnitTest`, build `web-dashboard-and-mobile` (`npm ci && npm run build`), and upload both `NOS-Live-app-debug` and `NFL-Live-Web-Dashboard-dist` as downloadable artifacts on every push to `main`.
- Verify everything locally with `npm run build` (in `web-dashboard-and-mobile`), `gradle :app:testDebugUnitTest`, and `compile_applet`.
