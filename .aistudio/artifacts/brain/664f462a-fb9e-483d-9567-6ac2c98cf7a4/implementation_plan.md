# NFL LIVE — Tomorrow's Factory Go-Live Readiness Plan (Phone, Smart TV & Live Web)

This plan prepares the complete **NFL LIVE** ecosystem—Android Mobile App, Smart TV 4-Page Wallboard, and Live Desktop Web Dashboard—for tomorrow's live factory launch, including end-to-end verification and hardening of **Employee Registration Requests** and **First-Time Smart TV Login Approval**, zero synthetic demo clutter, **IE** role access for Weekly BS OQL / NFL AQC, and **Firebase Hosting** setup.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Audit of Registration Requests & First-Time TV Login (New Findings & Fixes Included):**
> Upon auditing the live Firestore sync path between Employee Phones, Smart TVs, and the Admin Panel, we identified and included critical hardening fixes so neither Registration Requests nor First-Time TV Logins can ever be missed:

- **Confirmed Fix A — First-Time Smart TV Login (`TV_PENDING` Clock-Skew Immunity)**:
  - *Issue Found*: Smart TVs often have system clocks that drift a few seconds ahead of or behind an Admin's phone, and the TV's 30-second republish loop updates `updatedAtMillis` on every retry. Strict timestamp filtering (`remote.updatedAtMillis <= localTs`) could cause the Admin phone to ignore a TV's `TV_PENDING` request if the TV clock is behind, OR cause the TV to ignore the Admin's `APPROVED` response (and overwrite it 30 seconds later) if the TV clock is ahead.
  - *Fix Included*: Bypass timestamp-drop guards for `TV_PENDING:` markers on Admin devices and for matching `APPROVED` (`authorizedDeviceId == myDeviceId && rejectionReason == ""`) updates on the TV, and stop `republishTvSetupRequest()` immediately once the TV is approved.
- **Confirmed Fix B — Employee Registration Requests (`PENDING_APPROVAL`) & Device Replacement Requests (`DEV_PENDING`)**:
  - *Issue Found*:
    1. If a newly registering employee's phone has a slightly earlier clock or brief network hiccup, or if an Admin checks the **Device Requests** tab instead of the **Pending Users** tab, a request could be overlooked.
    2. When an existing employee logs in from a new phone, the device approval request was stored locally on that phone without publishing a `DEV_PENDING:` marker to Firestore.
    3. The Desktop Web Admin Console (`AdminView`) did not yet display live `PENDING_APPROVAL` registrations or `TV_PENDING:` requests with Approve/Reject actions.
  - *Fix Included*:
    1. Always merge remote `PENDING_APPROVAL` user documents and re-push any unsynced local `PENDING_APPROVAL` registration during Manual Sync.
    2. Publish `DEV_PENDING:` markers to Firestore for employee device changes so they appear in the Admin's **Device Requests** tab just like TV requests.
    3. Show Pending Employee Registrations in **both** the **Pending Users** list and the **Device Requests** tab on Android, AND add live **Approve / Reject** controls for both Employee Registrations and Smart TV Setup Requests in the Desktop Web Admin Console.
- **Confirmed Decision 1 — Firebase Hosting Setup for Live Web Dashboard**: Configure Firebase Hosting (`nfl-live-72213.web.app`) with SPA routing and a pure-JS SHA-256 fallback so login works across both HTTPS and local office networks.
- **Confirmed Decision 2 — Admin, Auditor & IE Access for Weekly BS OQL / NFL AQC**: Allow **Admin**, **Auditor**, and **IE** roles to enter Weekly BS OQL and NFL AQC values across Android and Web.
- **Confirmed Decision 3 — Clean Zero-Data Default on Fresh Launch**: Default `sample_data_cleared` to `true` on fresh installs across Android and Web so all devices start tomorrow at `0` with only real Cloud Firestore records.

---

## 1. Overview & Core Concept

- **What It Does**: Synchronizes real-time garment factory production, 1-hour DHU quality inspections, 2-hour Bi-Hourly OQL audits, Weekly BS OQL / NFL AQC benchmarks, Morning Line Setups, NPT Stoppage Alerts, Employee Registrations, and Smart TV Display Approvals across Android phones, Smart TV wallboards, and office PC browsers.
- **Target Audience / Persona**:
  - **Floor Reporters & Supervisors**: Self-register on the Android app (`PENDING_APPROVAL`), get approved by Admin in 1 tap, and enter hourly output, quality checks, and stoppage alerts.
  - **Smart TV Wallboards**: Boot directly into the passwordless **TV Setup Screen**, display a 6-character Device Code + live `"Cloud: Request sent..."` status, and automatically transition to the 4-page 16:9 live wallboard the moment an Admin approves the TV.
  - **Auditors, IE Head & Admins**: Enter Bi-Hourly OQL audits, Weekly BS OQL / NFL AQC values, and approve employee/TV requests from either the Android Admin Panel or the Desktop Web Dashboard.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **First-Time Smart TV Login Flow**:
     - TV opens in TV Mode $\rightarrow$ displays 6-character code (e.g., `A9F42C`) and `"Cloud: Request sent. It now appears on the Admin phone under Device Requests."`
     - Admin opens **User & Access Control** on Android Phone (or **Admin & Reset $\rightarrow$ Users & Requests** on Web) $\rightarrow$ sees `Smart TV Display (Code: A9F42C)` $\rightarrow$ taps **Approve**.
     - TV receives the Firestore snapshot in real time, binds its `deviceId`, saves its local session, and immediately switches to the 4-page live factory wallboard permanently (approval is required only once).
  2. **New Employee Registration Flow**:
     - Employee taps **Register New Account** on the login screen $\rightarrow$ enters Name, Employee ID, Designation, Floor (`Sew-1`, `Sew-2`, `Sew-3`, or `Finishing`), and Password $\rightarrow$ submits as `PENDING_APPROVAL`.
     - Admin immediately sees the badge count increment under **Pending** and **Device Req** on Android (and in the Web Admin modal) $\rightarrow$ taps **Approve** (with optional floor/designation override).
     - Employee can immediately sign in on their phone and the Web Dashboard.
  3. **Clean Day-1 Startup (Phone, TV & Web)**:
     - Every device starts with clean zeroed KPIs (`—` / `0 pcs`) and listens only to live Cloud Firestore records.
  4. **Weekly BS OQL & Bi-Hourly Audit Entry**:
     - **Admin**, **Auditor**, and **IE** users enter Weekly BS OQL & NFL AQC percentages, immediately updating the 12-week trend chart on the TV Wallboard and Web Dashboard.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Clock-Skew-Immune Handshake for `TV_PENDING` and `PENDING_APPROVAL`**
  - *Chosen Approach*: Treat state-transition markers (`TV_PENDING:`, `DEV_PENDING:`, `PENDING_APPROVAL`, and TV `APPROVED` device binding) as high-priority state transitions in `upsertRemoteUsers` that are never discarded due to clock drift between TVs and phones.
  - *Why*: Android Smart TVs frequently have system clocks skewed by several seconds or minutes compared to mobile phones on cellular time; immunity to clock drift guarantees 100% reliable first-time TV pairing and registration visibility.
- **Decision 2: Dual-Surface Approval (Android Admin Panel + Web Admin Console)**
  - *Chosen Approach*: Surface both `PENDING_APPROVAL` employee registrations and `TV_PENDING` / `DEV_PENDING` device requests in the Android Admin Panel (across both the Pending Users and Device Requests views) AND in the Live Web Dashboard's Admin modal.
  - *Why*: Whether you are on the factory floor with your Android phone or at your desk on a PC browser, you can approve new employees and Smart TVs in one click.
- **Decision 3: Default Clean Startup (`sample_data_cleared = true`) & IE Weekly OQL Access**
  - *Chosen Approach*: Start fresh installs with zero demo data by default and include `UserRole.IE` alongside `UserRole.ADMIN` and `UserRole.AUDITOR` for Weekly BS OQL / NFL AQC entry.
  - *Why*: Ensures tomorrow's launch has no fake numbers and allows the IE Head to enter weekly quality benchmarks directly.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Approval Handshake Diagram

```
┌──────────────────────────────────┐         ┌──────────────────────────────────┐
│     New Employee Phone           │         │       Smart TV (First Boot)      │
│  registerEmployee()              │         │  ensureTvDisplaySetupRequest()   │
│  • status = PENDING_APPROVAL     │         │  republishTvSetupRequest() (30s) │
│  • salted SHA-256 passwordHash   │         │  • marker = TV_PENDING:req:id... │
└────────────────┬─────────────────┘         └────────────────┬─────────────────┘
                 │                                            │
                 │  Writes users/{employeeId}                 │  Writes users/tv_display
                 ▼                                            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                Cloud Firestore (nfl-live-72213 / users collection)            │
└────────────────┬────────────────────────────────────────────▲─────────────────┘
                 │                                            │
                 │  Real-time Snapshot (Clock-Skew Immune)    │  Writes APPROVED +
                 ▼                                            │  authorizedDeviceId
┌─────────────────────────────────────────────────────────────┴─────────────────┐
│           Admin Phone (AdminUsersScreen) & Web Dashboard (AdminView)          │
│  • Shows Pending Employee Registrations (Approve / Reject)                    │
│  • Shows Pending TV Display & Device Requests with 6-digit Code (Approve)     │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

1. **First-Time TV Login & Approval State Machine**:
   - TV writes `rejectionReason = "TV_PENDING:<reqId>:<deviceId>:<deviceName>:<shortCode>"` on `users/tv_display`.
   - Admin phone / Web receives `users/tv_display`, extracts the `TV_PENDING` marker (even if `remote.updatedAtMillis <= localTs`), and displays it in **Device Requests**.
   - Admin taps **Approve** $\rightarrow$ writes `accountStatus = APPROVED`, `isActive = true`, `authorizedDeviceId = <tvDeviceId>`, `rejectionReason = ""` to `users/tv_display`.
   - TV receives the approved `users/tv_display` document, immediately stops the 30-second republish loop, calls `activateTvDisplaySession()`, and opens `TvDashboardScreen`.
2. **Employee Registration & Device Request State Machine**:
   - Employee submits registration $\rightarrow$ writes `users/{employeeId}` with `accountStatus = PENDING_APPROVAL`.
   - Admin sees the request in both **Pending Users** and **Device Requests** on Android (and in **Users & Requests** on Web) $\rightarrow$ taps **Approve** $\rightarrow$ sets `accountStatus = APPROVED`, `isActive = true`.
3. **Automated Robolectric Verification**:
   - Add dedicated tests verifying that a remote `PENDING_APPROVAL` registration and a remote `TV_PENDING` request (even with skewed timestamps) appear in the Admin repository state and transition to active approved sessions upon Admin approval.
