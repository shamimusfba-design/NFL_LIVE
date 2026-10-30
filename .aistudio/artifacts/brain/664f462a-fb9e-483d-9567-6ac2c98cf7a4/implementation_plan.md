# Fix Android APK Cloud Sign-In & Unified Email / Employee ID Login

## 1. Root Cause Analysis
1. **Missing Firebase Initialization in GitHub Actions APK Builds (`"Cloud sign-in is not available on this device."`)**:
   - `app/build.gradle.kts` sets `googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }`. When an APK is built in an environment or branch where `app/google-services.json` is absent or skipped, no Firebase XML resources are generated into the APK.
   - Unlike the Web App (`web-dashboard-and-mobile/src/services/firebaseLiveSync.ts`), which initializes Firebase explicitly in code with the `nfl-live-72213` config, the Android app relied solely on auto-initialization from `google-services.json`. Without it, `FirebaseApp`, `FirebaseAuth`, and `FirebaseFirestore` are `null` at runtime on the phone.
2. **Email (`shamim.usfba@gmail.com`) vs. Employee ID (`1015198`) Authentication on Fresh APK Installs**:
   - `NflRepository.authenticateUser(id, pass)` only checked in-memory `_users.value` by `id` or `employeeId`. It did not match by email (`shamim.usfba@gmail.com`), did not call Firebase Authentication (`signInWithEmailAndPassword`), and did not fetch the user profile from Firestore on a fresh APK install before local cache was populated.

---

## 2. Proposed Changes

### A. Guaranteed Firebase Initialization on Android (`FirebaseAuthManager.kt` & `FirebaseSyncService.kt`)
- Add `FirebaseAuthManager.ensureFirebaseInitialized(context: Context): Boolean` that checks `FirebaseApp.getApps(context)`.
- If no `FirebaseApp` is initialized (e.g., if an APK was compiled without `google-services.json` resources), initialize the default `FirebaseApp` programmatically using `FirebaseOptions.Builder()` with the `nfl-live-72213` project configuration (`projectId = "nfl-live-72213"`, `applicationId = "1:716260096216:android:f787f27339c03e756abfad"`, `apiKey`, `gcmSenderId = "716260096216"`, `storageBucket = "nfl-live-72213.firebasestorage.app"`).
- Call `ensureFirebaseInitialized(context)` inside `FirebaseSyncService` (before `FirebaseFirestore.getInstance(...)`) and `FirebaseAuthManager` (before `Firebase.auth`), ensuring cloud sign-in and Firestore sync are always available on every phone.

### B. Support Both Email (`shamim.usfba@gmail.com`) and Employee ID (`1015198`) Login (`DomainModels.kt`, `FirebaseAuthManager.kt`, `FirebaseSyncService.kt`, `NflRepository.kt`)
- **Admin Email Mapping (`DomainModels.kt` & `NflRepository.kt`)**:
  - Recognize `shamim.usfba@gmail.com` (as well as `shamim.sustipe15@gmail.com`) as an authorized primary admin email linked to Employee ID `1015198` (`INITIAL_ADMIN_EMPLOYEE_ID`).
  - Update `authenticateUser(id, pass)` so matching checks `user.id`, `user.employeeId`, `user.googleEmail`, and maps primary admin email logins directly to the admin account (`1015198` / `admin`).
- **Cloud Email/Password & Firestore Lookup (`FirebaseAuthManager.kt`, `FirebaseSyncService.kt`, `NflRepository.kt`)**:
  - Add `signInWithEmailAndPassword(context, email, password)` and `sendPasswordResetEmail(context, email)` in `FirebaseAuthManager.kt`.
  - Add `fetchCloudLoginUserAwait(identifier: String, firebaseUid: String, email: String): UserAccount?` in `FirebaseSyncService.kt` to fetch the user profile from Firestore (`users/{identifier}`, `users/{firebaseUid}`, or `users/1015198`) during login when the local cache does not yet have the latest cloud user record.
  - Add `suspend fun authenticateUserSuspend(id: String, pass: String): AuthResult` in `NflRepository.kt` that:
    1. Ensures Firebase is initialized.
    2. If `id` is an email address (contains `@`), signs in with Firebase Auth `signInWithEmailAndPassword(context, id, pass)` and/or fetches the user profile from Firestore, syncing the verified account and password hash locally so offline and subsequent logins also work seamlessly.
    3. If `id` is an Employee ID not yet in local memory or whose password was updated in the cloud, fetches the latest `users/{id}` document from Firestore before running the standard `authenticateUser(id, pass)` verification, RBAC, and device-binding checks.

### C. Login Screen Updates (`LoginScreen.kt`)
- Update the identifier field label/placeholder to indicate both **Employee ID or Email** (e.g., `1015198` or `shamim.usfba@gmail.com`) are accepted.
- Update the **Sign In** button in `LoginScreen.kt` to run `repository.authenticateUserSuspend(employeeId, password)` inside `coroutineScope.launch` with loading state feedback.
- Add a **"Forgot password?"** option below the password field so users with an email address can request a Firebase password reset email or view admin reset instructions.

---

## 3. Verification Plan
1. Compile the Android app via `compile_applet` to ensure zero build or type errors.
2. Add unit/Robolectric tests verifying:
   - Logging in with `shamim.usfba@gmail.com` and `Nfllive@26` succeeds and resolves the Admin profile.
   - Logging in with Employee ID (`1015198`, `admin`, `supervisor`, etc.) continues to pass all existing RBAC and device-binding checks.
   - `FirebaseAuthManager.ensureFirebaseInitialized(context)` initializes `FirebaseApp` cleanly.
3. Run `gradle :app:testDebugUnitTest` to confirm all unit and Robolectric tests pass.
