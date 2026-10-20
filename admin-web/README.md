# NFL LIVE Admin Web Panel

Desktop-first web control panel scaffold for the existing NFL LIVE project. This branch does not deploy the app or change the existing Android login flow yet.

## Important status
- The panel requires a Firebase Web App config.
- Sign-in uses Firebase Authentication (email/password).
- A signed-in UID must have a protected `admin_users/{uid}` document with `isAdmin: true` and optional `active: true`.
- **The current repository's Firestore rules are open (`allow read, write: if true`). Do not deploy this panel to real users or rely on UI checks until secure rules and the Android authentication migration are completed.**
- Current Android code uses its own Employee ID/password records, including a legacy plaintext `password` field. The Admin Panel intentionally does not display or edit password fields. A safe migration must move credentials to Firebase Authentication and remove plaintext passwords without locking out existing users.
- Registration request review in the UI expects `registration_requests` documents. Android registration/device approval code must be connected to this schema before the buttons can process real registrations reliably.
- Edits currently write directly to Firestore. Before production use, enforce admin-only authorization in Firestore rules or a trusted backend, and implement atomic approval, single-device session invalidation, and immutable audit records.

## Configure Firebase Hosting
1. In Firebase Console, open the correct project and create/register a **Web app**.
2. Copy the Web app's public config into `firebaseConfig` in `index.html` (apiKey, authDomain, projectId, appId). These values are not service-account secrets.
3. Enable Firebase Authentication > Email/Password.
4. Create the owner's Admin Authentication account. From a trusted Firebase Admin SDK environment / Cloud Shell, create `admin_users/{UID}` with `isAdmin: true`, `active: true`. Do not add a UI that lets a user promote themselves.
5. Secure Firestore rules before exposing real data. Existing Android app currently relies on unauthenticated Firestore operations, so rules must be coordinated with an Android Firebase Auth migration. Do not simply tighten rules without updating/testing the Android client.
6. From a terminal with Firebase CLI, select the correct project explicitly and deploy Hosting only:
   `firebase use <YOUR_PROJECT_ID>`
   `firebase deploy --only hosting`
7. Open the Firebase-provided `https://<project-id>.web.app` URL and test with a non-admin account; it must be denied.

## Collections currently referenced
- `users`
- `production_entries`
- `quality_entries`
- `factory_alerts`
- `daily_setups`
- `monthly_targets`
- `registration_requests` (requires Android/backend integration)
- `audit_logs` (requires trusted audit implementation)

## Data safety
- Export data before making changes.
- The UI loads at most 250 documents per collection view.
- Do not store passwords in Firestore. Never add service account JSON/private keys to this repo or browser code.
- Direct client-side audit fields are not immutable or tamper-proof; critical operations should be audited by a trusted backend.
