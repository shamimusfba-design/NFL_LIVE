# Fix Login Password Keyboard Dismissal & Finalize Quality/Monthly Metrics

## 1. Problem Analysis & Root Cause

### The Password Keyboard Dismissal Issue
- **User Symptom**: On the Login page, when typing a password, the virtual/soft keyboard collapses and disappears after every single character typed. Re-selecting the input brings the keyboard back, but typing another character immediately dismisses it again.
- **Root Cause**:
  1. **`autoComplete="new-password"` on Login Field**: In `web-dashboard-and-mobile/src/components/LoginView.tsx`, the password field was configured with `autoComplete="new-password"`. In mobile browsers (Chrome, Samsung Internet) and Android WebView, `new-password` instructs the browser that the user is generating a brand-new password. On every `input`/`change` event, the browser's credential generator/autofill engine intercepts the field to evaluate password strength or display a suggestion bar. This steals DOM focus from the input element, causing the OS Input Method Editor (virtual keyboard) to immediately collapse.
  2. **Missing `id` and `name` attributes**: The bare input inside `Field` lacked `name="password"` and a stable `id`, confusing browser autofill heuristics.
  3. **Show/Hide Password Button Focus Stealing**: The visibility toggle button lacked `tabIndex={-1}`, allowing focus to shift away from the active input.
  4. **WebView Inset Resizing in Android**: In `WebAppHostScreen.kt`, the container used `windowInsetsPadding(WindowInsets.safeDrawing)`. Because `safeDrawing` includes the software keyboard (`ime`), the WebView bounds were resized on keyboard changes, triggering resize loops in the WebView that blurred the active DOM input.
  5. **Native Compose Fallback**: In `LoginScreen.kt`, `password` was stored using `remember` instead of `rememberSaveable`.

---

## 2. Proposed Changes

### A. Fix Web Login View (`web-dashboard-and-mobile/src/components/LoginView.tsx`)
1. **Change Autocomplete to `current-password`**:
   - Update `Field` call for password to use `autoComplete="current-password"` instead of `"new-password"`.
   - Provide standard `name="password"` and `id="login_password"`.
2. **Prevent Focus Loss in `Field`**:
   - Maintain an `inputRef` reference to the `<input>` element.
   - Add `tabIndex={-1}` to the show/hide password toggle button so it never interferes with keyboard or focus order.
   - Ensure `onFocus` and `onChange` do not trigger re-mounts or blur events while typing.
   - Add `name` and `id` props to `Field` for proper DOM identification.

### B. Stabilize Android WebView Insets (`app/src/main/java/com/example/ui/screens/WebAppHostScreen.kt`)
1. In `WebAppHostScreen.kt`, change:
   - `WindowInsets.safeDrawing` to `WindowInsets.statusBars` on the root container Box.
   - This prevents the WebView from being abruptly resized and squashed whenever the virtual keyboard opens or adjusts suggestion strips, keeping the web DOM focus steady.

### C. Update Native Compose Fallback (`app/src/main/java/com/example/ui/screens/LoginScreen.kt`)
1. Update `var password by remember { mutableStateOf("") }` to `var password by rememberSaveable { mutableStateOf("") }` so state is preserved across recompositions and insets shifts.

### D. Finalize Bi-Hourly OQL Audit & Monthly Performance Metrics
1. **Bi-Hourly OQL Audit (Pass/Fail Buttons)**:
   - In `EntryForms.tsx` (Bi-Hourly OQL tab): Add explicit interactive `[ ✅ AUDIT PASS ]` and `[ ❌ AUDIT FAIL ]` toggle buttons.
   - Allow inspector to manually override or confirm Pass/Fail based on AQL limit (4.5%).
   - Store `auditStatus: 'PASS' | 'FAIL'` in Firestore (`quality_entries`) and local storage.
2. **Monthly Data Entry & Dashboard KPI Cards**:
   - In `EntryForms.tsx` (Monthly Data tab): Add selector for Floor Monthly Targets vs Line-wise Monthly Targets.
   - Allow entering targets for Efficiency %, Achieve %, OQL %, DHU %, and Output pcs for individual lines.
   - In `TvDesktopDashboard.tsx`: When `Month` period is selected, render dedicated cards for:
     * `MONTHLY EFFICIENCY` (Actual vs Target)
     * `MONTHLY ACHIEVE %` (Actual output vs Target pcs)
     * `MONTHLY OQL %` (OQL % and Pass/Fail counts)
     * `MONTHLY DHU %` (DHU % vs Limit)
   - In Line Performance table, display Audit Pass/Fail badges (`[PASS]` / `[FAIL]`) and line-wise Monthly KPIs.

---

## 3. Verification Plan

1. **Build & Compilation**:
   - Run `npm --prefix web-dashboard-and-mobile run build` to verify type safety and bundle generation.
   - Sync built distribution to `docs/` and `app/src/main/assets/webapp/`.
   - Run `compile_applet` to confirm the Android APK builds without errors.
2. **Keyboard Behavior Validation**:
   - Verify that typing into the password field allows continuous entry of all characters without the virtual keyboard dismissing.
   - Verify that the eye toggle button shows and hides the password without dropping cursor focus.
3. **Audit & Monthly Verification**:
   - Verify that the Bi-Hourly OQL form displays the interactive PASS and FAIL buttons.
   - Verify that the Monthly dashboard shows the 4 monthly metric cards (Efficiency, Achieve %, OQL, DHU) and line table badges.
