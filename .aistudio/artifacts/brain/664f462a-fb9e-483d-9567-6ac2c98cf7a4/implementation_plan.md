# Implementation Plan: 12-Hour AM/PM Time Format, Daily Bi-Hourly Audit, Weekly BS OQL Role Access & Desktop PWA

## 1. Overview & Constraints
- **Preserve TV Dashboard Untouched**: Keep `TvDashboardScreen.kt` and `WbComponents.kt` (authored with Claude) completely untouched.
- **12-Hour AM/PM Time Format Everywhere**: Replace 24-hour (`13:00`, `14:00`) displays across all screens, dropdowns, reports, charts, and history logs with familiar **12-hour AM/PM format** (e.g., `8:00 AM - 9:00 AM`, `1:00 PM - 2:00 PM`, `2:00 PM - 3:00 PM`, and compact chart axis labels `8 AM`, `1 PM`, `2 PM`), while keeping backward compatibility with existing stored records.
- **Daily Bi-Hourly Audit Tab in Quality Entry**: Add a dedicated **Bi-Hourly Audit (2-Hour OQL)** tab inside `QualityEntryScreen.kt` alongside the **Hourly Quality (DHU)** tab.
- **Strict Role Permissions (Admin & Auditor Only)**: Restrict **Weekly BS OQL / NFL AQC Entry** and **Daily Bi-Hourly Audit Entry** strictly to **Admin** (`UserRole.ADMIN`) and **Auditor** (`UserRole.AUDITOR`), excluding **IE** and **Quality Manager**.
- **Desktop Web & Installable PWA**: Provide the complete free Desktop Web/PWA folder (`desktop-pwa/`) synced live with Firebase Firestore using the same 12-hour AM/PM format and role rules.

---

## 2. Key Functional Changes

### A. 12-Hour AM/PM Time Formatting Across Reports & Data Entry (`NflRepository.kt`, `ProductionEntryScreen.kt`, `QualityEntryScreen.kt`, `LineDeepDiveScreen.kt`, `HistorySyncScreen.kt`, `HomeScreen.kt`)
- **Centralized 12-Hour Slot Formatter (`NflRepository.formatHourSlot12h` & `formatHourShort12h`)**:
  - Convert any 1-hour or 2-hour slot (e.g., `08:00 - 09:00`, `13:00 - 14:00`, `14:00 - 15:00`, `13:00 - 15:00`) into clean 12-hour AM/PM strings:
    - **1-Hour Slots**: `8:00 AM - 9:00 AM`, `9:00 AM - 10:00 AM`, ..., `12:00 PM - 1:00 PM`, `1:00 PM - 2:00 PM`, `2:00 PM - 3:00 PM`, `3:00 PM - 4:00 PM`, `4:00 PM - 5:00 PM`, `5:00 PM - 6:00 PM`, `6:00 PM - 7:00 PM`, `7:00 PM - 8:00 PM`, `8:00 PM - 9:00 PM`, `9:00 PM - 10:00 PM`.
    - **2-Hour Bi-Hourly Audit Slots**: `8:00 AM - 10:00 AM`, `10:00 AM - 12:00 PM`, `1:00 PM - 3:00 PM`, `3:00 PM - 5:00 PM`, `5:00 PM - 7:00 PM`, `7:00 PM - 9:00 PM`.
    - **Short Chart Labels**: `8 AM`, `9 AM`, `10 AM`, `11 AM`, `12 PM`, `1 PM`, `2 PM`, `3 PM`, `4 PM`, `5 PM`, etc.
- **Normalize Slot Matching**:
  - Update slot comparison helpers in `NflRepository.kt` so records saved as either `14:00 - 15:00` or `2:00 PM - 3:00 PM` map to the same canonical hour slot, preventing any data loss or duplicate slots.
- **Apply Across All Screens & Reports**:
  - **Production Entry Screen**: Hour slot dropdown, matrix badges, and status messages show `8:00 AM - 9:00 AM`, `2:00 PM - 3:00 PM`, etc.
  - **Quality Entry Screen**: Both Hourly DHU and Bi-Hourly Audit slot dropdowns and recent entry cards show 12-hour AM/PM slots.
  - **Line Deep Dive Screen**: Hourly Output, Efficiency, DHU charts (`8 AM`, `1 PM`, `2 PM`) and the Hourly Breakdown Report Table (`8:00 AM - 9:00 AM`, `1:00 PM - 2:00 PM`, `2:00 PM - 3:00 PM`).
  - **History & Sync Screen**: Production and Quality history cards, edit dialogs, and delete confirmations show 12-hour AM/PM slots.

### B. Daily Bi-Hourly Audit Tab (`QualityEntryScreen.kt` & `NflRepository.kt`)
- **Mode Switcher Tab at Top of Quality Entry**:
  - Tab 1: **Hourly DHU & Rectification (1-Hr)** — uses 1-hour AM/PM slots (`8:00 AM - 9:00 AM`, `2:00 PM - 3:00 PM`, etc.).
  - Tab 2: **Bi-Hourly OQL Audit (2-Hr)** — uses 2-hour AM/PM audit slots (`8:00 AM - 10:00 AM`, `10:00 AM - 12:00 PM`, `1:00 PM - 3:00 PM`, `3:00 PM - 5:00 PM`, `5:00 PM - 7:00 PM`, `7:00 PM - 9:00 PM`), accessible strictly to **Admin** and **Auditor**.
- **Streamlined Bi-Hourly Audit Fields**:
  - Floor, Line, Date, and 2-Hour AM/PM Audit Slot selector.
  - **Checked Garments (pcs)**, **Defective Garments (pcs)**, and **Defect Breakdown by Type** (with live **Bi-Hourly OQL %** banner, hiding Sewing/Spot Rectification inputs in Bi-Hourly mode).

### C. Weekly BS OQL / NFL AQC Role & Navigation Update (`WeeklyQualityEntryScreen.kt`, `MainActivity.kt`)
- **Admin & Auditor Only**:
  - Update `WeeklyQualityEntryScreen.kt` and `MainActivity.kt` drawer visibility so only **Admin** (`UserRole.ADMIN`) and **Auditor** (`UserRole.AUDITOR`) can view and enter Weekly BS OQL / NFL AQC data and Daily Bi-Hourly Audit data (excluding **IE** and **Quality Manager**).
  - Add a quick button in `QualityEntryScreen.kt` for Admin and Auditor users to open **Weekly BS OQL / AQC Entry** directly.

### D. Free Desktop Web & Installable PWA (`desktop-pwa/`)
- Include the complete Desktop Web & Installable PWA (`desktop-pwa/`) synced with Firebase Firestore, displaying all hourly and bi-hourly slots in **12-hour AM/PM format** (`2:00 PM - 3:00 PM`) across Data Entry, Live Dashboard, Reports, and Excel Export.
