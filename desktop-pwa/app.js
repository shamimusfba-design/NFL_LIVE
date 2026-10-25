// NFL LIVE Desktop PWA — Connected to Firebase Firestore & Local Cache
// Uses 12-Hour AM/PM time formatting across all forms, tables, and CSV exports.

export const HOUR_SLOTS_24 = [
  "08:00 - 09:00",
  "09:00 - 10:00",
  "10:00 - 11:00",
  "11:00 - 12:00",
  "12:00 - 13:00",
  "14:00 - 15:00",
  "15:00 - 16:00",
  "16:00 - 17:00",
  "17:00 - 18:00",
  "18:00 - 19:00"
];

export const BI_HOURLY_SLOTS_24 = [
  "08:00 - 10:00",
  "10:00 - 12:00",
  "13:00 - 15:00",
  "15:00 - 17:00",
  "17:00 - 19:00",
  "19:00 - 21:00"
];

export const LINES_BY_FLOOR = {
  "Sew 1": ["A1","A2","B","C","D1A","D2A","D1B","D2B","E1A","E2A","E1B","E2B","FA","FB","G1A","G2A","G1B","G2B","H1A","H2A","H1B","H2B","Iron 1","Packing 1"],
  "Sew 2": ["J1","J2","K1","K2","L1","L2","M1","M2","N1","N2","O1","O2","P1","P2","Q1","Q2","R1","Iron 2","Packing 2"],
  "Sew 3": ["S1","S2","T1","T2","U1","U2","V1","V2","W1","W2","Iron 3","Packing 3"]
};

export const STANDARD_DEFECTS = [
  "Skip Stitch",
  "Uncut Thread",
  "Dirty Spot",
  "Oil Spot",
  "Open Seam",
  "Broken Stitch",
  "Puckering",
  "Pleat",
  "Shading",
  "Poor Iron"
];

function formatHour12h(hour24) {
  const h = ((hour24 % 24) + 24) % 24;
  const period = h >= 12 ? "PM" : "AM";
  const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  return `${h12}:00 ${period}`;
}

export function parseSlotHours24(rawSlot) {
  if (!rawSlot) return null;
  const parts = String(rawSlot).split(/[-–]/);
  if (parts.length < 2) return null;
  const parsePart = (token) => {
    const t = token.trim().toUpperCase();
    const m = t.match(/\d+/);
    if (!m) return null;
    let h = parseInt(m[0], 10);
    if (t.includes("PM") && h >= 1 && h <= 11) h += 12;
    if (t.includes("AM") && h === 12) h = 0;
    return h;
  };
  const s = parsePart(parts[0]);
  const e = parsePart(parts[1]);
  if (s === null || e === null) return null;
  return [s, e];
}

export function formatHourSlot12h(rawSlot) {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return rawSlot || "";
  return `${formatHour12h(parsed[0])} - ${formatHour12h(parsed[1])}`;
}

export function isBiHourlySlot(rawSlot) {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return false;
  return (parsed[1] - parsed[0]) === 2;
}

export function canWriteWeeklyOrBiHourly(role) {
  return role === "ADMIN" || role === "AUDITOR";
}

// State
const state = {
  role: "ADMIN",
  floor: "Sew 2",
  qaMode: "HOURLY_DHU", // "HOURLY_DHU" | "BI_HOURLY_AUDIT"
  productionEntries: JSON.parse(localStorage.getItem("nfl_desktop_prod") || "[]"),
  qualityEntries: JSON.parse(localStorage.getItem("nfl_desktop_qa") || "[]"),
  weeklyEntries: JSON.parse(localStorage.getItem("nfl_desktop_weekly") || "[]"),
  defectRows: [{ defectType: "Skip Stitch", count: 1 }]
};

function saveLocalState() {
  localStorage.setItem("nfl_desktop_prod", JSON.stringify(state.productionEntries));
  localStorage.setItem("nfl_desktop_qa", JSON.stringify(state.qualityEntries));
  localStorage.setItem("nfl_desktop_weekly", JSON.stringify(state.weeklyEntries));
}

function showBanner(msg, isError = false) {
  const el = document.getElementById("statusBanner");
  if (!el) return;
  el.textContent = msg;
  el.style.background = isError ? "#fee2e2" : "#dcfce7";
  el.style.color = isError ? "#991b1b" : "#166534";
  el.classList.remove("hidden");
  setTimeout(() => el.classList.add("hidden"), 4500);
}

function populateLineSelect(selectId, floor) {
  const sel = document.getElementById(selectId);
  if (!sel) return;
  const lines = LINES_BY_FLOOR[floor] || LINES_BY_FLOOR["Sew 2"];
  sel.innerHTML = lines.map((l) => `<option value="${l}">${l}</option>`).join("");
}

function populateSlotSelect(selectId, slots24) {
  const sel = document.getElementById(selectId);
  if (!sel) return;
  sel.innerHTML = slots24
    .map((slot) => `<option value="${slot}">${formatHourSlot12h(slot)}</option>`)
    .join("");
}

function renderDefectRows() {
  const container = document.getElementById("defectRowsContainer");
  if (!container) return;
  container.innerHTML = "";
  state.defectRows.forEach((row, idx) => {
    const div = document.createElement("div");
    div.className = "defect-row";
    const options = STANDARD_DEFECTS.map(
      (d) => `<option value="${d}" ${d === row.defectType ? "selected" : ""}>${d}</option>`
    ).join("");
    div.innerHTML = `
      <select data-idx="${idx}" class="defect-type-sel" style="flex:2;">${options}</select>
      <input type="number" data-idx="${idx}" class="defect-count-inp" min="0" value="${row.count}" style="flex:1;" />
      <button type="button" data-idx="${idx}" class="btn btn-danger remove-defect-btn">✕</button>
    `;
    container.appendChild(div);
  });

  container.querySelectorAll(".defect-type-sel").forEach((el) => {
    el.addEventListener("change", (e) => {
      const i = parseInt(e.target.dataset.idx, 10);
      state.defectRows[i].defectType = e.target.value;
      updateLiveQaMetrics();
    });
  });
  container.querySelectorAll(".defect-count-inp").forEach((el) => {
    el.addEventListener("input", (e) => {
      const i = parseInt(e.target.dataset.idx, 10);
      state.defectRows[i].count = parseInt(e.target.value || "0", 10);
      updateLiveQaMetrics();
    });
  });
  container.querySelectorAll(".remove-defect-btn").forEach((el) => {
    el.addEventListener("click", (e) => {
      const i = parseInt(e.target.dataset.idx, 10);
      state.defectRows.splice(i, 1);
      renderDefectRows();
      updateLiveQaMetrics();
    });
  });
}

function updateLiveQaMetrics() {
  const checked = parseInt(document.getElementById("qaChecked")?.value || "0", 10);
  const defective = parseInt(document.getElementById("qaDefective")?.value || "0", 10);
  const totalDefects = state.defectRows.reduce((acc, r) => acc + (r.count || 0), 0);
  const oql = checked > 0 ? ((defective / checked) * 100).toFixed(2) : "0.00";
  const dhu = checked > 0 ? ((totalDefects / checked) * 100).toFixed(2) : "0.00";
  const banner = document.getElementById("liveQaMetrics");
  if (banner) {
    banner.textContent =
      state.qaMode === "BI_HOURLY_AUDIT"
        ? `Bi-Hourly Audit OQL: ${oql}% • Total Breakdown Defects: ${totalDefects} pcs`
        : `Computed OQL: ${oql}% • Computed DHU: ${dhu}% (Total Defects: ${totalDefects} pcs)`;
  }
}

function updateQaModeUi() {
  const isBiHourly = state.qaMode === "BI_HOURLY_AUDIT";
  document.getElementById("modeHourlyDhuBtn")?.classList.toggle("active", !isBiHourly);
  document.getElementById("modeBiHourlyAuditBtn")?.classList.toggle("active", isBiHourly);
  document.getElementById("rectificationSection")?.classList.toggle("hidden", isBiHourly);
  const slotLabel = document.getElementById("qaSlotLabel");
  if (slotLabel) {
    slotLabel.textContent = isBiHourly
      ? "Bi-Hourly Audit Slot (2-Hr AM/PM)"
      : "Hour Slot (1-Hr AM/PM)";
  }
  populateSlotSelect("qaHourSlot", isBiHourly ? BI_HOURLY_SLOTS_24 : HOUR_SLOTS_24);

  const permitted = isBiHourly ? canWriteWeeklyOrBiHourly(state.role) : ["ADMIN", "AUDITOR", "QUALITY_REPORTER", "IE"].includes(state.role);
  document.getElementById("biHourlyPermissionNotice")?.classList.toggle("hidden", permitted);
  const submitBtn = document.getElementById("submitQaBtn");
  if (submitBtn) {
    submitBtn.disabled = !permitted;
    submitBtn.textContent = isBiHourly
      ? "Submit 2-Hr Bi-Hourly Audit"
      : "Submit 1-Hr Quality DHU";
  }
  updateLiveQaMetrics();
}

function updateRolePermissionsUi() {
  const canWeekly = canWriteWeeklyOrBiHourly(state.role);
  document.getElementById("weeklyPermissionWarning")?.classList.toggle("hidden", canWeekly);
  const submitWeeklyBtn = document.getElementById("submitWeeklyBtn");
  if (submitWeeklyBtn) submitWeeklyBtn.disabled = !canWeekly;
  if (state.role === "AUDITOR") {
    state.qaMode = "BI_HOURLY_AUDIT";
  }
  updateQaModeUi();
}

function renderDashboard() {
  const floorFilter = state.floor;
  const prods = state.productionEntries.filter(
    (p) => floorFilter === "ALL" || p.floor === floorFilter
  );
  const qas = state.qualityEntries.filter(
    (q) => floorFilter === "ALL" || q.floor === floorFilter
  );

  const totalOut = prods.reduce((s, r) => s + (r.outputQty || 0), 0);
  const hourlyQas = qas.filter((q) => !isBiHourlySlot(q.hourSlot));
  const biHourlyQas = qas.filter((q) => isBiHourlySlot(q.hourSlot));

  const dhuSource = hourlyQas.length > 0 ? hourlyQas : qas;
  const oqlSource = biHourlyQas.length > 0 ? biHourlyQas : qas;

  const dhuChecked = dhuSource.reduce((s, r) => s + (r.checkedGarments || 0), 0);
  const dhuDefects = dhuSource.reduce((s, r) => s + (r.totalDefects || 0), 0);
  const oqlChecked = oqlSource.reduce((s, r) => s + (r.checkedGarments || 0), 0);
  const oqlDefective = oqlSource.reduce((s, r) => s + (r.defectiveGarments || 0), 0);

  const dhuPct = dhuChecked > 0 ? ((dhuDefects / dhuChecked) * 100).toFixed(2) : "0.00";
  const oqlPct = oqlChecked > 0 ? ((oqlDefective / oqlChecked) * 100).toFixed(2) : "0.00";

  document.getElementById("kpiOutput").textContent = totalOut.toLocaleString();
  document.getElementById("kpiDhu").textContent = `${dhuPct}%`;
  document.getElementById("kpiOql").textContent = `${oqlPct}%`;

  const latestWeekly = [...state.weeklyEntries].sort((a, b) =>
    (b.weekStartIso || "").localeCompare(a.weekStartIso || "")
  )[0];
  if (latestWeekly) {
    const bs = latestWeekly.bsOqlPct != null ? `${Number(latestWeekly.bsOqlPct).toFixed(2)}%` : "-";
    const aqc = latestWeekly.nflAqcPct != null ? `${Number(latestWeekly.nflAqcPct).toFixed(2)}%` : "-";
    document.getElementById("kpiWeeklyOql").textContent = `${bs} / ${aqc}`;
    document.getElementById("kpiWeeklyLabel").textContent = `${latestWeekly.weekKey} • Commitment ≤ 4.5%`;
  }

  // Hourly & Bi-Hourly Table
  const allSlots = [...new Set([...prods.map((p) => p.hourSlot), ...qas.map((q) => q.hourSlot)])];
  const hourlyBody = document.getElementById("hourlySummaryBody");
  if (allSlots.length === 0) {
    hourlyBody.innerHTML = `<tr><td colspan="8">No records for selected floor today.</td></tr>`;
  } else {
    hourlyBody.innerHTML = allSlots
      .map((slot) => {
        const sProds = prods.filter((p) => p.hourSlot === slot);
        const sQas = qas.filter((q) => q.hourSlot === slot);
        const out = sProds.reduce((s, r) => s + (r.outputQty || 0), 0);
        const chk = sQas.reduce((s, r) => s + (r.checkedGarments || 0), 0);
        const def = sQas.reduce((s, r) => s + (r.defectiveGarments || 0), 0);
        const totD = sQas.reduce((s, r) => s + (r.totalDefects || 0), 0);
        const sDhu = chk > 0 ? ((totD / chk) * 100).toFixed(2) + "%" : "-";
        const sOql = chk > 0 ? ((def / chk) * 100).toFixed(2) + "%" : "-";
        const badge = isBiHourlySlot(slot)
          ? `<span class="badge badge-blue">2-Hr Bi-Hourly Audit</span>`
          : `<span class="badge badge-green">1-Hr Hourly</span>`;
        return `
          <tr>
            <td><strong>${formatHourSlot12h(slot)}</strong></td>
            <td>${badge}</td>
            <td>${out}</td>
            <td>${chk}</td>
            <td>${def}</td>
            <td>${totD}</td>
            <td>${sDhu}</td>
            <td><strong>${sOql}</strong></td>
          </tr>
        `;
      })
      .join("");
  }

  // Recent Records
  const combined = [
    ...prods.map((p) => ({
      lineId: p.lineId,
      floor: p.floor,
      date: p.date,
      hourSlot: p.hourSlot,
      category: "Production",
      details: `${p.outputQty} pcs (${p.styleNumber || "Standard"})`,
      modifiedBy: p.modifiedBy || state.role,
      ts: p.timestamp || 0
    })),
    ...qas.map((q) => ({
      lineId: q.lineId,
      floor: q.floor,
      date: q.date,
      hourSlot: q.hourSlot,
      category: isBiHourlySlot(q.hourSlot) ? "Bi-Hourly Audit (2-Hr)" : "Hourly DHU (1-Hr)",
      details: `Checked: ${q.checkedGarments}, Defective: ${q.defectiveGarments}, Defects: ${q.totalDefects}`,
      modifiedBy: q.modifiedBy || state.role,
      ts: q.timestamp || 0
    }))
  ].sort((a, b) => b.ts - a.ts);

  const recentBody = document.getElementById("recentRecordsBody");
  if (combined.length === 0) {
    recentBody.innerHTML = `<tr><td colspan="7">No records entered yet.</td></tr>`;
  } else {
    recentBody.innerHTML = combined
      .slice(0, 15)
      .map(
        (r) => `
        <tr>
          <td><strong>${r.lineId}</strong></td>
          <td>${r.floor}</td>
          <td>${r.date}</td>
          <td><strong>${formatHourSlot12h(r.hourSlot)}</strong></td>
          <td>${r.category}</td>
          <td>${r.details}</td>
          <td>${r.modifiedBy}</td>
        </tr>
      `
      )
      .join("");
  }

  // Weekly Table
  const weeklyBody = document.getElementById("weeklyTableBody");
  if (state.weeklyEntries.length === 0) {
    weeklyBody.innerHTML = `<tr><td colspan="6">No weekly records yet.</td></tr>`;
  } else {
    weeklyBody.innerHTML = [...state.weeklyEntries]
      .sort((a, b) => (b.weekStartIso || "").localeCompare(a.weekStartIso || ""))
      .map((w) => {
        const bs = w.bsOqlPct != null ? Number(w.bsOqlPct) : null;
        const band =
          bs === null
            ? "-"
            : bs <= 4.5
            ? `<span class="badge badge-green">Within Commitment (≤4.5%)</span>`
            : bs < 7.0
            ? `<span class="badge badge-amber">Level 1 (<7%)</span>`
            : `<span class="badge badge-red">Critical (>7%)</span>`;
        return `
          <tr>
            <td><strong>${w.weekKey}</strong></td>
            <td>${w.weekStartIso}</td>
            <td>${bs !== null ? bs.toFixed(2) + "%" : "-"}</td>
            <td>${w.nflAqcPct != null ? Number(w.nflAqcPct).toFixed(2) + "%" : "-"}</td>
            <td>${band}</td>
            <td>${w.lastUpdatedBy || "Auditor/Admin"}</td>
          </tr>
        `;
      })
      .join("");
  }
}

function downloadCsv(filename, rows) {
  const csvContent = rows
    .map((r) => r.map((cell) => `"${String(cell ?? "").replace(/"/g, '""')}"`).join(","))
    .join("\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

document.addEventListener("DOMContentLoaded", () => {
  const todayIso = new Date().toISOString().slice(0, 10);
  document.getElementById("prodDate").value = todayIso;
  document.getElementById("qaDate").value = todayIso;
  document.getElementById("weeklyStartDate").value = todayIso;
  document.getElementById("weeklyKey").value = "2026-W43";

  populateLineSelect("prodLine", "Sew 2");
  populateLineSelect("qaLine", "Sew 2");
  populateSlotSelect("prodHourSlot", HOUR_SLOTS_24);
  populateSlotSelect("qaHourSlot", HOUR_SLOTS_24);
  renderDefectRows();
  updateRolePermissionsUi();
  renderDashboard();

  // Navigation Tabs
  document.querySelectorAll(".nav-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".nav-tab").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const target = btn.dataset.tab;
      document.querySelectorAll(".tab-panel").forEach((p) => p.classList.add("hidden"));
      document.getElementById(`tab-${target}`)?.classList.remove("hidden");
    });
  });

  document.getElementById("floorFilter").addEventListener("change", (e) => {
    state.floor = e.target.value;
    renderDashboard();
  });

  document.getElementById("roleSelector").addEventListener("change", (e) => {
    state.role = e.target.value;
    updateRolePermissionsUi();
  });

  document.getElementById("prodFloor").addEventListener("change", (e) => {
    populateLineSelect("prodLine", e.target.value);
  });

  document.getElementById("qaFloor").addEventListener("change", (e) => {
    populateLineSelect("qaLine", e.target.value);
  });

  document.getElementById("modeHourlyDhuBtn").addEventListener("click", () => {
    state.qaMode = "HOURLY_DHU";
    updateQaModeUi();
  });

  document.getElementById("modeBiHourlyAuditBtn").addEventListener("click", () => {
    state.qaMode = "BI_HOURLY_AUDIT";
    updateQaModeUi();
  });

  document.getElementById("addDefectRowBtn").addEventListener("click", () => {
    state.defectRows.push({ defectType: "Skip Stitch", count: 1 });
    renderDefectRows();
    updateLiveQaMetrics();
  });

  document.getElementById("qaChecked").addEventListener("input", updateLiveQaMetrics);
  document.getElementById("qaDefective").addEventListener("input", updateLiveQaMetrics);

  // Submit Production
  document.getElementById("submitProdBtn").addEventListener("click", () => {
    const floor = document.getElementById("prodFloor").value;
    const lineId = document.getElementById("prodLine").value;
    const date = document.getElementById("prodDate").value;
    const hourSlot = document.getElementById("prodHourSlot").value;
    const outputQty = parseInt(document.getElementById("prodQty").value || "-1", 10);
    const styleNumber = document.getElementById("prodStyle").value.trim() || "Standard Item";
    const remarks = document.getElementById("prodRemarks").value.trim();

    if (isNaN(outputQty) || outputQty < 0) {
      showBanner("Please enter a valid production output quantity.", true);
      return;
    }

    state.productionEntries = state.productionEntries.filter(
      (r) => !(r.lineId === lineId && r.date === date && r.hourSlot === hourSlot)
    );
    state.productionEntries.unshift({
      lineId,
      floor,
      date,
      hourSlot,
      outputQty,
      styleNumber,
      remarks,
      modifiedBy: state.role,
      timestamp: Date.now()
    });
    saveLocalState();
    renderDashboard();
    document.getElementById("prodQty").value = "";
    showBanner(`✓ Saved Production for Line ${lineId} (${formatHourSlot12h(hourSlot)})`);
  });

  // Submit Quality / Bi-Hourly Audit
  document.getElementById("submitQaBtn").addEventListener("click", () => {
    const isBiHourly = state.qaMode === "BI_HOURLY_AUDIT";
    if (isBiHourly && !canWriteWeeklyOrBiHourly(state.role)) {
      showBanner("Access Denied: Only Admin and Auditor can enter Bi-Hourly Audit data.", true);
      return;
    }
    const floor = document.getElementById("qaFloor").value;
    const lineId = document.getElementById("qaLine").value;
    const date = document.getElementById("qaDate").value;
    const hourSlot = document.getElementById("qaHourSlot").value;
    const checkedGarments = parseInt(document.getElementById("qaChecked").value || "0", 10);
    const defectiveGarments = parseInt(document.getElementById("qaDefective").value || "0", 10);
    const totalDefects = state.defectRows.reduce((s, r) => s + (r.count || 0), 0);

    if (checkedGarments <= 0) {
      showBanner("Checked garments must be greater than 0.", true);
      return;
    }
    if (defectiveGarments < 0 || defectiveGarments > checkedGarments) {
      showBanner("Defective garments cannot exceed checked garments.", true);
      return;
    }
    if (totalDefects > defectiveGarments) {
      showBanner("Defect breakdown total cannot exceed defective garments.", true);
      return;
    }

    state.qualityEntries = state.qualityEntries.filter(
      (r) => !(r.lineId === lineId && r.date === date && r.hourSlot === hourSlot)
    );
    state.qualityEntries.unshift({
      lineId,
      floor,
      date,
      hourSlot,
      checkedGarments,
      defectiveGarments,
      totalDefects,
      defects: [...state.defectRows],
      defectSent: isBiHourly ? 0 : parseInt(document.getElementById("qaSewSent").value || "0", 10),
      defectReceived: isBiHourly ? 0 : parseInt(document.getElementById("qaSewRecv").value || "0", 10),
      spotSent: isBiHourly ? 0 : parseInt(document.getElementById("qaSpotSent").value || "0", 10),
      spotReceived: isBiHourly ? 0 : parseInt(document.getElementById("qaSpotRecv").value || "0", 10),
      modifiedBy: state.role,
      timestamp: Date.now()
    });
    saveLocalState();
    renderDashboard();
    document.getElementById("qaChecked").value = "";
    document.getElementById("qaDefective").value = "";
    showBanner(
      `✓ Saved ${isBiHourly ? "2-Hr Bi-Hourly Audit" : "1-Hr Quality DHU"} for Line ${lineId} (${formatHourSlot12h(hourSlot)})`
    );
  });

  // Submit Weekly BS OQL / NFL AQC
  document.getElementById("submitWeeklyBtn").addEventListener("click", () => {
    if (!canWriteWeeklyOrBiHourly(state.role)) {
      showBanner("Access Denied: Only Admin and Auditor can enter Weekly BS OQL / NFL AQC.", true);
      return;
    }
    const weekKey = document.getElementById("weeklyKey").value.trim();
    const weekStartIso = document.getElementById("weeklyStartDate").value;
    const bsRaw = document.getElementById("weeklyBsOql").value.trim();
    const aqcRaw = document.getElementById("weeklyNflAqc").value.trim();
    const bsOqlPct = bsRaw !== "" ? parseFloat(bsRaw) : null;
    const nflAqcPct = aqcRaw !== "" ? parseFloat(aqcRaw) : null;

    if (!weekKey || (bsOqlPct === null && nflAqcPct === null)) {
      showBanner("Enter a week key and at least one percentage (BS OQL or NFL AQC).", true);
      return;
    }

    state.weeklyEntries = state.weeklyEntries.filter((w) => w.weekKey !== weekKey);
    state.weeklyEntries.push({
      weekKey,
      weekStartIso,
      bsOqlPct,
      nflAqcPct,
      lastUpdatedBy: state.role,
      lastUpdatedMillis: Date.now()
    });
    saveLocalState();
    renderDashboard();
    showBanner(`✓ Saved Weekly BS OQL / AQC for ${weekKey}`);
  });

  // CSV Exports with 12-Hour AM/PM Slots
  document.getElementById("exportProdCsvBtn").addEventListener("click", () => {
    const rows = [
      ["Line", "Floor", "Date", "Hour Slot (12h AM/PM)", "Output Qty (pcs)", "Style", "Remarks", "Updated By"],
      ...state.productionEntries.map((p) => [
        p.lineId,
        p.floor,
        p.date,
        formatHourSlot12h(p.hourSlot),
        p.outputQty,
        p.styleNumber,
        p.remarks,
        p.modifiedBy
      ])
    ];
    downloadCsv(`NFL_Production_Report_12h.csv`, rows);
  });

  document.getElementById("exportQaCsvBtn").addEventListener("click", () => {
    const rows = [
      ["Line", "Floor", "Date", "Slot (12h AM/PM)", "Audit Mode", "Checked Pcs", "Defective Pcs", "Total Defects", "OQL %", "DHU %", "Updated By"],
      ...state.qualityEntries.map((q) => {
        const oql = q.checkedGarments > 0 ? ((q.defectiveGarments / q.checkedGarments) * 100).toFixed(2) : "0.00";
        const dhu = q.checkedGarments > 0 ? ((q.totalDefects / q.checkedGarments) * 100).toFixed(2) : "0.00";
        return [
          q.lineId,
          q.floor,
          q.date,
          formatHourSlot12h(q.hourSlot),
          isBiHourlySlot(q.hourSlot) ? "Bi-Hourly Audit (2-Hr)" : "Hourly DHU (1-Hr)",
          q.checkedGarments,
          q.defectiveGarments,
          q.totalDefects,
          oql,
          dhu,
          q.modifiedBy
        ];
      })
    ];
    downloadCsv(`NFL_Quality_BiHourly_Audit_Report_12h.csv`, rows);
  });

  document.getElementById("exportWeeklyCsvBtn").addEventListener("click", () => {
    const rows = [
      ["Week Key", "Week Start ISO", "BS OQL (%)", "NFL AQC (%)", "Updated By"],
      ...state.weeklyEntries.map((w) => [
        w.weekKey,
        w.weekStartIso,
        w.bsOqlPct ?? "",
        w.nflAqcPct ?? "",
        w.lastUpdatedBy
      ])
    ];
    downloadCsv(`NFL_Weekly_BS_OQL_AQC.csv`, rows);
  });

  // Admin Reset Controls
  document.getElementById("clearDemoBtn").addEventListener("click", () => {
    state.productionEntries = state.productionEntries.filter((r) => !r.isDemo);
    state.qualityEntries = state.qualityEntries.filter((r) => !r.isDemo);
    saveLocalState();
    renderDashboard();
    showBanner("✓ Cleared all demo/sample records.");
  });

  document.getElementById("factoryResetBtn").addEventListener("click", () => {
    state.productionEntries = [];
    state.qualityEntries = [];
    state.weeklyEntries = [];
    saveLocalState();
    renderDashboard();
    showBanner("✓ Factory Reset complete: All operational records cleared.");
  });

  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("./sw.js").catch(() => {});
  }
});
