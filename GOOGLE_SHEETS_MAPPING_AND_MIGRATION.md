# Google Sheets Mapping, Migration & Architecture Guide
**NFL Production, Quality & NPT System**
**Spreadsheet ID:** `1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0`

---

## 1. Executive Summary & Design Principles

This document establishes the exact integration and migration architecture between the **NFL Mobile & TV System** and the existing Google Sheet workbook. 

### Key Safeguards:
1. **Range Preservation:** The backend never overwrites existing formula cells, summary headers, or historical chart bindings.
2. **Dedicated Raw Ledger Tabs:** All high-frequency transactions from mobile entry are written to dedicated, append-only raw tabs. 
3. **Serialized Queue:** Concurrency in Google Sheets is strictly serialized using an asynchronous mutex in the Node.js backend.
4. **Idempotency:** Every transaction carries a client-generated UUID `submissionId`. Re-tries or duplicate taps never generate duplicate rows.
5. **Security:** No Service Account private keys or Google API keys are ever bundled into the Android APK, web bundle, or Git repository. All Sheets communication is proxied through the secure backend API.

---

## 2. Workbook Tab Analysis & Explicit Range Mapping

The existing workbook contains five core tabs:

### 2.1. Tab 1: `Dashboard Summary`
* **Purpose:** Executive factory view displaying factory-wide today efficiency, monthly efficiency, today OQL, BS QA OQL, FRI pass rate, and operator/machine counts.
* **Workbook Formula Audit & Corrections:**
  * *Original Formula Flaw:* Many legacy spreadsheets calculate monthly efficiency by averaging daily percentage values (`=AVERAGE(B5:B30)`). This is mathematically incorrect because days with different work hours or output volume carry unequal weights.
  * *Corrected Aggregation:* Monthly efficiency must aggregate earned minutes and available minutes first:
    $$\text{Monthly Efficiency} = \frac{\sum \text{Earned Minutes}}{\sum \text{Available Minutes}} \times 100$$
* **Mapped Input Cells:**
  * `B3:D3`: Report Date & Floor Filter
  * `C7:C8`: Active Operator / Machine census count snapshot

### 2.2. Tab 2: `Production Dashboard`
* **Purpose:** Line-by-line target vs achievement, hourly output curves, and individual line efficiencies.
* **Stable Line IDs:**
  * J, K1, L1, L2, M, N1, N2, O1, O2, P, Q1, Q2, R (13 sewing lines).
  * *Alias Rule:* `J` and `J1` are confirmed to be the exact same physical line. The system automatically normalizes `J1` entries into `J`. `J2` is treated as a separate distinct line and is never merged automatically.
* **Calculations:**
  * **SMV Terminology:** Standard Minute Value (SMV) is used exclusively (never SAM).
  * $\text{Earned Minutes} = \sum (\text{Output Quantity} \times \text{Applicable Style SMV})$.
  * $\text{Available Minutes} = \text{Included Manpower} \times \text{Working Minutes Per Hour}$.
  * $\text{Actual Efficiency} = \frac{\text{Earned Minutes}}{\text{Available Minutes}} \times 100$.
  * *Crucial Constraint:* Available minutes are **never** counted multiple times when multiple styles run on the same line within the same reporting hour.

### 2.3. Tab 3: `Quality Dashboard`
* **Purpose:** Line-by-line OQL, DHU, top 5 defect Pareto ranking, and weekly trend graphs.
* **Metrics:**
  * $\text{OQL (\%)} = \frac{\text{Defective Garments}}{\text{Checked Garments}} \times 100$.
  * $\text{DHU} = \frac{\text{Total Defects}}{\text{Checked Garments}} \times 100$.
  * Confirmed Zero: If Checked > 0 and Defective = 0, OQL is explicitly 0.00% (distinct from uninspected/missing data).
  * Independence: One garment may have multiple defects. Defective garments count and total defects are stored as distinct values.

### 2.4. Tab 4: `Quality Data` & Tab 5: `Production Data`
* **Purpose:** Historical inspection counts and output quantity inputs.
* **Safety Rule:** These tabs contain existing lookup formulas and formatting. To prevent corruption from high-concurrency mobile submissions, these tabs read aggregated slices generated from the new Raw Ledger tabs.

---

## 3. Dedicated Raw Transaction Ledger Tabs

To maintain high throughput and revision safety, the backend provisions and appends into the following dedicated tabs:

### 3.1. `Raw_Production`
| Column | Header Name | Type | Description |
|---|---|---|---|
| A | `submission_id` | UUID (String) | Stable unique client ID for idempotency |
| B | `date` | Date (`YYYY-MM-DD`) | Production date |
| C | `hour_slot` | String | e.g., `10:00 - 11:00` |
| D | `line_id` | String | Normalized line identifier (`J`, `K1`, etc.) |
| E | `style_number` | String | Garment style (e.g. `NFL-204`) |
| F | `output_qty` | Integer | Hourly output quantity in pieces |
| G | `remarks` | String | Optional downtime or feeding remarks |
| H | `created_at` | ISO Timestamp | Server receipt timestamp |
| I | `operator_id` | String | User ID who entered the data |
| J | `revision` | Integer | Revision counter (starts at 1) |

### 3.2. `Raw_Quality`
| Column | Header Name | Type | Description |
|---|---|---|---|
| A | `submission_id` | UUID (String) | Idempotent transaction key |
| B | `date` | Date | Inspection date |
| C | `hour_slot` | String | Inspection time slot |
| D | `line_id` | String | Sewing line |
| E | `style_number` | String | Style number inspected |
| F | `checked_garments`| Integer | Total pieces inspected |
| G | `defective_garments`| Integer | Pieces with 1 or more defects |
| H | `total_defects` | Integer | Sum of all defects across items |
| I | `defect_breakdown`| String | Serialized defect list (`Skip Stitch:3;Uncut Thread:4`) |
| J | `remarks` | String | Inspector notes |
| K | `created_at` | ISO Timestamp | Server receipt timestamp |
| L | `inspector_id` | String | User ID |

### 3.3. `Raw_NPT`
| Column | Header Name | Type | Description |
|---|---|---|---|
| A | `event_id` | UUID | NPT stoppage record ID |
| B | `line_id` | String | Line affected |
| C | `style_number` | String | Current style on line |
| D | `reason` | String | Machine Breakdown, Material Shortage, etc. |
| E | `scope` | String | `FULL` or `PARTIAL` |
| F | `affected_manpower` | Integer | Number of operators affected |
| G | `start_time` | ISO Timestamp | Stopwatch start timestamp |
| H | `end_time` | ISO Timestamp | Stopwatch end timestamp (empty if running) |
| I | `status` | String | `RUNNING` or `CLOSED` |
| J | `lost_man_minutes`| Decimal | $\text{Duration (mins)} \times \text{Affected Manpower}$ |
| K | `machine_ref` | String | Machine number or needle type |
| L | `remarks` | String | Mechanic / supervisor notes |
| M | `created_by` | String | Supervisor username |

### 3.4. `Raw_SupervisorSetup`
| Column | Header Name | Type | Description |
|---|---|---|---|
| A | `line_id` | String | Target line |
| B | `effective_date` | Date | Effective date of snapshot |
| C | `style_number` | String | Style number |
| D | `order_number` | String | PO / Order reference |
| E | `smv` | Decimal | Style Standard Minute Value |
| F | `target_hourly_pcs`| Integer | Hourly target |
| G | `target_eff_pct` | Decimal | Target efficiency percentage |
| H | `operators_count` | Integer | Sewing operators |
| I | `helpers_count` | Integer | Line helpers |
| J | `include_helpers` | String | `YES` or `NO` |
| K | `working_mins_hr` | Integer | Planned working minutes (e.g. 60) |
| L | `snapshot_time` | ISO Timestamp | When snapshot was recorded |

### 3.5. `Audit_Log`
| Column | Header Name | Type | Description |
|---|---|---|---|
| A | `log_id` | UUID | Audit entry ID |
| B | `timestamp` | ISO Timestamp | Change timestamp |
| C | `entity_type` | String | `PRODUCTION`, `QUALITY`, `NPT`, `SETUP` |
| D | `entity_id` | String | ID of record modified |
| E | `action` | String | `CREATE`, `UPDATE`, `CORRECTION`, `REOPEN` |
| F | `changed_by` | String | User performing change |
| G | `audit_reason` | String | Reason for correction |
| H | `details` | String | JSON before/after or summary |

---

## 4. Google Sheets Concurrency & API Limitations

Google Sheets API has strict operational limits:
1. **Quota:** 300 write requests per minute per Google Cloud Project, and 60 requests per minute per user.
2. **Lock Contention:** Concurrent HTTP writes to the same worksheet range can cause race conditions and lost data.

### Implemented Solutions:
* **Asynchronous Serialized Queue:** The Node.js backend processes all writes through a FIFO mutex queue (`runSerialized`), guaranteeing that Google Sheets requests never overlap.
* **Backend Cache with 30s TTL:** Android TV screens and mobile dashboards read from the backend memory cache (`/api/tv/dashboard`). This reduces Sheet API read calls to 2 requests per minute regardless of how many TV displays are active.
* **Database Upgrade Path:** For plants scaling beyond 20 lines with sub-minute reporting, the backend is built to instantly mirror all records to PostgreSQL or MongoDB, keeping Google Sheets purely as an exported summary.

---

## 5. Google Cloud Service Account Setup (Least Privilege)

To enable live Google Sheets synchronization:

1. **Create Service Account in Google Cloud Console:**
   * Go to `https://console.cloud.google.com/` > **IAM & Admin** > **Service Accounts**.
   * Click **Create Service Account**, name it `nfl-factory-sync`.
   * Grant **no global project roles** (Principle of Least Privilege).
2. **Generate Key:**
   * Open the service account > **Keys** tab > **Add Key** > **Create new key (JSON)**.
   * Save the file as `service-account.json`.
3. **Share Google Sheet:**
   * Open the Google Sheet: `https://docs.google.com/spreadsheets/d/1lutN5LmC1Hi6vtuwpkis4n-dYGElZ3Qtqn-Hk52Y2L0/edit`.
   * Click **Share** (top right).
   * Enter the Service Account email address: e.g. `nfl-factory-sync@<project-id>.iam.gserviceaccount.com`.
   * Set role to **Editor**.
4. **Configure Backend Environment:**
   * In `backend/.env`, set:
     ```env
     GOOGLE_APPLICATION_CREDENTIALS=./service-account.json
     APP_MODE=live
     ```
   * Do **NOT** commit `service-account.json` to source control.
