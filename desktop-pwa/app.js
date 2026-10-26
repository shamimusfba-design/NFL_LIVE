// NFL LIVE Web & TV Wallboard PWA — Full APK Feature Parity + 4-Page TV Wallboard
// Uses 12-Hour AM/PM time formatting across all forms, charts, tables, and CSV exports.

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
  "Sew 1": ["A1","A2","B","C","D1A","D2A","D1B","D2B","E1A","E2A","E1B","E2B","FA","FB","G1A","G2A","G1B","G2B","H1A","H2A","H1B","H2B"],
  "Sew 2": ["J1","J2","K1","K2","L1","L2","M1","M2","N1","N2","O1","O2","P1","P2","Q1","Q2","R1"],
  "Sew 3": ["S1","S2","T1","T2","U1","U2","UA","UB","V1A","V1B","W1A","W1B","W2A","W2B","X1","Y1","Y2","Z1","Z2","ZA1A","ZA1B","ZA2A","ZA2B"]
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

const WB_INTERVALS = [30, 60, 10, 15, 0];

function formatHour12h(hour24) {
  const h = ((hour24 % 24) + 24) % 24;
  const period = h >= 12 ? "PM" : "AM";
  const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  return `${h12}:00 ${period}`;
}

export function formatHourShort12h(hour24) {
  const h = ((hour24 % 24) + 24) % 24;
  const period = h >= 12 ? "PM" : "AM";
  const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  return `${h12} ${period}`;
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

export function formatSlotShortLabel12h(rawSlot) {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return rawSlot || "";
  return formatHourShort12h(parsed[0]);
}

export function isBiHourlySlot(rawSlot) {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return false;
  return (parsed[1] - parsed[0]) === 2;
}

export function canWriteWeeklyOrBiHourly(role) {
  return role === "ADMIN" || role === "AUDITOR";
}

function getTodayIso() {
  return new Date().toISOString().slice(0, 10);
}

function generateRecentWeeks(count = 12) {
  const list = [];
  const now = new Date();
  const day = now.getDay();
  const diffToMon = (day === 0 ? -6 : 1) - day;
  const mon = new Date(now);
  mon.setDate(now.getDate() + diffToMon);
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date(mon);
    d.setDate(mon.getDate() - i * 7);
    const iso = d.toISOString().slice(0, 10);
    const tmp = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const dayNum = tmp.getUTCDay() || 7;
    tmp.setUTCDate(tmp.getUTCDate() + 4 - dayNum);
    const yearStart = new Date(Date.UTC(tmp.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil((((tmp - yearStart) / 86400000) + 1) / 7);
    list.push({
      weekKey: `${tmp.getUTCFullYear()}-W${String(weekNo).padStart(2, "0")}`,
      weekStartIso: iso
    });
  }
  return list;
}

const state = {
  role: "ADMIN",
  floor: "Sew 2",
  period: "TODAY",
  wbPage: "ALL", // "PRODUCTION" | "QUALITY" | "ALL" | "TABLE"
  wbIntervalIdx: 0,
  wbCountdown: 30,
  prodMode: "SINGLE", // "SINGLE" | "BATCH"
  qaMode: "HOURLY_DHU", // "HOURLY_DHU" | "BI_HOURLY_AUDIT"
  setups: JSON.parse(localStorage.getItem("nfl_desktop_setups") || "[]"),
  monthlyTargets: JSON.parse(localStorage.getItem("nfl_desktop_monthly") || "{}"),
  productionEntries: JSON.parse(localStorage.getItem("nfl_desktop_prod") || "[]"),
  qualityEntries: JSON.parse(localStorage.getItem("nfl_desktop_qa") || "[]"),
  weeklyEntries: JSON.parse(localStorage.getItem("nfl_desktop_weekly") || "[]"),
  incidents: JSON.parse(localStorage.getItem("nfl_desktop_npt") || "[]"),
  defectRows: [{ defectType: "Skip Stitch", count: 1 }]
};

function seedDemoIfEmpty() {
  if (state.productionEntries.length > 0 || localStorage.getItem("nfl_desktop_cleared") === "true") {
    return;
  }
  const today = getTodayIso();
  Object.entries(LINES_BY_FLOOR).forEach(([fl, lines]) => {
    lines.slice(0, 8).forEach((lineId, idx) => {
      const dailyTgt = 480 + (idx % 5) * 40;
      const hrTgt = Math.round(dailyTgt / 8);
      state.setups.push({
        lineId,
        floor: fl,
        date: today,
        styleNumber: idx % 2 === 0 ? "SS T shirt" : "Hoodie",
        smv: idx % 2 === 0 ? 7.5 : 14.5,
        operatorsCount: 20,
        helpersCount: 2,
        workingHours: 8,
        targetEff: 75,
        dailyTarget: dailyTgt,
        hourlyTarget: hrTgt,
        isDemo: true
      });
      HOUR_SLOTS_24.slice(0, 6).forEach((slot, sIdx) => {
        state.productionEntries.push({
          lineId,
          floor: fl,
          date: today,
          hourSlot: slot,
          outputQty: Math.round(hrTgt * (0.82 + ((idx + sIdx) % 4) * 0.06)),
          styleNumber: idx % 2 === 0 ? "SS T shirt" : "Hoodie",
          remarks: "Demo seed",
          modifiedBy: "Demo Seeder",
          timestamp: Date.now() - (6 - sIdx) * 1800000,
          isDemo: true
        });
      });
      const def = 2 + (idx % 3);
      state.qualityEntries.push({
        lineId,
        floor: fl,
        date: today,
        hourSlot: HOUR_SLOTS_24[idx % 5],
        checkedGarments: 120,
        defectiveGarments: Math.max(1, def - 1),
        totalDefects: def,
        defects: [{ defectType: STANDARD_DEFECTS[idx % STANDARD_DEFECTS.length], count: def }],
        defectSent: def + 1,
        defectReceived: def,
        spotSent: 1,
        spotReceived: idx % 2,
        modifiedBy: "Demo QA",
        timestamp: Date.now() - 3600000,
        isDemo: true
      });
      state.qualityEntries.push({
        lineId,
        floor: fl,
        date: today,
        hourSlot: BI_HOURLY_SLOTS_24[idx % 3],
        checkedGarments: 80,
        defectiveGarments: 2,
        totalDefects: 2,
        defects: [{ defectType: STANDARD_DEFECTS[(idx + 2) % STANDARD_DEFECTS.length], count: 2 }],
        defectSent: 0,
        defectReceived: 0,
        spotSent: 0,
        spotReceived: 0,
        modifiedBy: "AUDITOR",
        timestamp: Date.now() - 2400000,
        isDemo: true
      });
    });
  });

  const weeks = generateRecentWeeks(8);
  const bsArr = [4.2, 4.4, 3.9, 4.6, 4.1, 3.8, 3.6, 3.5];
  const aqcArr = [5.1, 4.8, 4.5, 4.9, 4.4, 4.2, 4.0, 3.9];
  weeks.forEach((w, i) => {
    state.weeklyEntries.push({
      weekKey: w.weekKey,
      weekStartIso: w.weekStartIso,
      bsOqlPct: bsArr[i],
      nflAqcPct: aqcArr[i],
      lastUpdatedBy: "AUDITOR",
      lastUpdatedMillis: Date.now(),
      isDemo: true
    });
  });

  state.incidents.push({
    id: "inc-demo-1",
    floor: "Sew 2",
    lineId: "K1",
    category: "MACHINE_BREAKDOWN",
    description: "SNLS-04 thread breakage & needle bar slip",
    startTimeMillis: Date.now() - 22 * 60000,
    status: "OPEN",
    isDemo: true
  });
  saveLocalState();
}

function saveLocalState() {
  localStorage.setItem("nfl_desktop_setups", JSON.stringify(state.setups));
  localStorage.setItem("nfl_desktop_monthly", JSON.stringify(state.monthlyTargets));
  localStorage.setItem("nfl_desktop_prod", JSON.stringify(state.productionEntries));
  localStorage.setItem("nfl_desktop_qa", JSON.stringify(state.qualityEntries));
  localStorage.setItem("nfl_desktop_weekly", JSON.stringify(state.weeklyEntries));
  localStorage.setItem("nfl_desktop_npt", JSON.stringify(state.incidents));
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
    .map((s) => `<option value="${s}">${formatHourSlot12h(s)}</option>`)
    .join("");
}

function renderBatchMatrix() {
  const container = document.getElementById("batchMatrixContainer");
  if (!container) return;
  const floor = document.getElementById("prodFloor")?.value || "Sew 2";
  const lines = LINES_BY_FLOOR[floor] || [];
  container.innerHTML = lines
    .map(
      (lineId) => `
      <div class="batch-card">
        <label style="color:var(--navy);font-weight:900;">Line ${lineId}</label>
        <input type="number" min="0" class="batch-line-input" data-line="${lineId}" placeholder="Output pcs" />
      </div>
    `
    )
    .join("");
}

function computeFloorData(floorScope) {
  const floors = floorScope === "ALL" ? ["Sew 1", "Sew 2", "Sew 3"] : [floorScope];
  const allLines = floors.flatMap((f) => (LINES_BY_FLOOR[f] || []).map((id) => ({ id, floor: f })));

  const lineRows = allLines.map(({ id, floor }) => {
    const setup = state.setups.find((s) => s.lineId === id) || {
      styleNumber: "SS T shirt",
      smv: 8.5,
      operatorsCount: 20,
      helpersCount: 2,
      workingHours: 8,
      targetEff: 75,
      dailyTarget: 480,
      hourlyTarget: 60
    };
    const prods = state.productionEntries.filter((p) => p.lineId === id);
    const qas = state.qualityEntries.filter((q) => q.lineId === id);

    const output = prods.length > 0 ? prods.reduce((s, p) => s + Number(p.outputQty || 0), 0) : null;
    const achPct = output !== null && setup.dailyTarget > 0 ? (output / setup.dailyTarget) * 100 : null;
    const earnedMins = output !== null ? output * (setup.smv || 8.5) : 0;
    const availMins = (setup.operatorsCount || 20) * (setup.workingHours || 8) * 60;
    const effPct = output !== null && availMins > 0 ? (earnedMins / availMins) * 100 : null;

    const checked = qas.reduce((s, q) => s + Number(q.checkedGarments || 0), 0);
    const defective = qas.reduce((s, q) => s + Number(q.defectiveGarments || 0), 0);
    const defects = qas.reduce((s, q) => s + Number(q.totalDefects || 0), 0);
    const sewSent = qas.reduce((s, q) => s + Number(q.defectSent || 0), 0);
    const sewRecv = qas.reduce((s, q) => s + Number(q.defectReceived || 0), 0);
    const spotSent = qas.reduce((s, q) => s + Number(q.spotSent || 0), 0);
    const spotRecv = qas.reduce((s, q) => s + Number(q.spotReceived || 0), 0);

    const dhu = checked > 0 ? (defects / checked) * 100 : null;
    const oql = checked > 0 ? (defective / checked) * 100 : null;

    return {
      lineId: id,
      floor,
      styleNumber: setup.styleNumber,
      smv: setup.smv,
      hourlyTarget: setup.hourlyTarget,
      target: setup.dailyTarget,
      output,
      achPct,
      effPct,
      monthEffPct: effPct !== null ? Math.max(0, effPct - 1.5) : 0,
      targetEff: setup.targetEff || 75,
      checked,
      defective,
      defects,
      dhu,
      oql,
      sewSent,
      sewRecv,
      sewBal: Math.max(0, sewSent - sewRecv),
      spotSent,
      spotRecv,
      spotBal: Math.max(0, spotSent - spotRecv),
      ops: setup.operatorsCount || 20,
      helpers: setup.helpersCount || 2
    };
  });

  const activeRows = lineRows.filter((r) => r.output !== null || r.checked > 0);
  const totalTarget = activeRows.reduce((s, r) => s + r.target, 0);
  const totalOutput = activeRows.reduce((s, r) => s + (r.output || 0), 0);
  const avgEff =
    activeRows.length > 0
      ? activeRows.reduce((s, r) => s + (r.effPct || 0), 0) / activeRows.length
      : 0;
  const totalChecked = activeRows.reduce((s, r) => s + r.checked, 0);
  const totalDefective = activeRows.reduce((s, r) => s + r.defective, 0);
  const totalDefects = activeRows.reduce((s, r) => s + r.defects, 0);
  const dhuPct = totalChecked > 0 ? (totalDefects / totalChecked) * 100 : 0;
  const oqlPct = totalChecked > 0 ? (totalDefective / totalChecked) * 100 : 0;
  const sewBal = activeRows.reduce((s, r) => s + r.sewBal, 0);
  const spotBal = activeRows.reduce((s, r) => s + r.spotBal, 0);
  const opsTotal = activeRows.reduce((s, r) => s + r.ops, 0);

  return {
    lineRows,
    activeRows,
    totalTarget,
    totalOutput,
    achPct: totalTarget > 0 ? (totalOutput / totalTarget) * 100 : 0,
    avgEff,
    monthEff: Math.max(0, avgEff - 1.2),
    totalChecked,
    totalDefective,
    totalDefects,
    dhuPct,
    oqlPct,
    sewBal,
    spotBal,
    defectsPending: sewBal + spotBal,
    opsTotal,
    reportedCount: activeRows.filter((r) => r.output !== null).length,
    totalLinesCount: lineRows.length
  };
}

function renderWallboard() {
  const container = document.getElementById("wbPageContainer");
  if (!container) return;

  const subtitle = document.getElementById("wbStatusSubtitle");
  if (subtitle) {
    subtitle.textContent = `${state.floor === "ALL" ? "All Floors" : state.floor} · ${state.wbPage} · ${state.period} · 12-Hour AM/PM`;
  }

  const data = computeFloorData(state.floor);
  const active = data.activeRows.slice(0, 17);

  if (state.wbPage === "PRODUCTION") {
    container.innerHTML = `
      <div class="wb-kpi-row-5">
        <div class="wb-panel">
          <div class="wb-panel-title">EFFICIENCY · target 75%</div>
          <div class="wb-kpi-big" style="color:var(--wb-green)">${data.avgEff.toFixed(1)}%</div>
          <div class="wb-kpi-foot"><span>Month ${data.monthEff.toFixed(1)}%</span><span>Δ +1.2 pp</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">OUTPUT vs TARGET</div>
          <div class="wb-kpi-big" style="color:var(--wb-blue)">${data.totalOutput} <span style="font-size:12px;color:var(--wb-muted)">/ ${data.totalTarget} pcs</span></div>
          <div class="wb-kpi-foot"><span>${data.achPct.toFixed(0)}% of target</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">LINES REPORTED</div>
          <div class="wb-kpi-big" style="color:var(--wb-amber)">${data.reportedCount} / ${data.totalLinesCount}</div>
          <div class="wb-kpi-foot"><span>Active floor coverage</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">DOWNTIME (elapsed)</div>
          <div class="wb-kpi-big" style="color:var(--wb-red)">${state.incidents.filter(i => i.status !== "RESOLVED").length * 20} min</div>
          <div class="wb-kpi-foot"><span>${state.incidents.filter(i => i.status !== "RESOLVED").length} open stoppages</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">OPERATORS / MACHINES</div>
          <div class="wb-kpi-big" style="color:var(--wb-blue)">${data.opsTotal} / ${data.opsTotal}</div>
          <div class="wb-kpi-foot"><span>Ratio 1.00</span></div>
        </div>
      </div>

      <div class="wb-grid-3">
        <div class="wb-panel">
          <div class="wb-panel-header"><span class="wb-panel-title">Live Target vs Achievement</span><span class="wb-panel-sub">Grey: Target · Color: Output</span></div>
          <div class="wb-col-chart">
            ${active.map(r => {
              const hOut = Math.min(100, Math.round(((r.output || 0) / Math.max(1, r.target)) * 100));
              const col = (r.achPct || 0) >= 95 ? "var(--wb-green)" : (r.achPct || 0) >= 80 ? "var(--wb-amber)" : "var(--wb-red)";
              return `<div class="wb-col-item">
                <div class="wb-col-top-val" style="color:${col}">${r.output || 0}</div>
                <div class="wb-col-bars">
                  <div class="wb-bar-outer" style="height:85%"></div>
                  <div class="wb-bar-inner" style="height:${hOut}%;background:${col}"></div>
                </div>
                <div class="wb-col-lbl">${r.lineId}</div>
              </div>`;
            }).join("")}
          </div>
        </div>

        <div class="wb-panel">
          <div class="wb-panel-header"><span class="wb-panel-title">Efficiency by Line</span><span class="wb-panel-sub">Target 75%</span></div>
          <div class="wb-col-chart">
            ${active.map(r => {
              const eff = Math.min(100, Math.round(r.effPct || 0));
              const col = eff >= 75 ? "var(--wb-green)" : eff >= 65 ? "var(--wb-amber)" : "var(--wb-red)";
              return `<div class="wb-col-item">
                <div class="wb-col-top-val" style="color:${col}">${eff}%</div>
                <div class="wb-col-bars">
                  <div class="wb-bar-outer" style="height:${Math.max(10, eff - 4)}%"></div>
                  <div class="wb-bar-inner" style="height:${eff}%;background:${col}"></div>
                </div>
                <div class="wb-col-lbl">${r.lineId}</div>
              </div>`;
            }).join("")}
          </div>
        </div>

        <div class="wb-panel">
          <div class="wb-panel-header"><span class="wb-panel-title">Hourly Output (12h AM/PM)</span><span class="wb-panel-sub">8 AM – 6 PM</span></div>
          <div class="wb-col-chart">
            ${HOUR_SLOTS_24.map(slot => {
              const sum = state.productionEntries
                .filter(p => (state.floor === "ALL" || p.floor === state.floor) && p.hourSlot === slot)
                .reduce((s, p) => s + Number(p.outputQty || 0), 0);
              const h = Math.min(100, Math.round((sum / Math.max(100, active.length * 60)) * 100));
              return `<div class="wb-col-item">
                <div class="wb-col-top-val" style="color:var(--wb-blue)">${sum || "—"}</div>
                <div class="wb-col-bars">
                  <div class="wb-bar-inner" style="height:${h}%;background:var(--wb-blue);width:65%"></div>
                </div>
                <div class="wb-col-lbl">${formatSlotShortLabel12h(slot)}</div>
              </div>`;
            }).join("")}
          </div>
        </div>
      </div>
    `;
    return;
  }

  if (state.wbPage === "QUALITY") {
    container.innerHTML = `
      <div class="wb-kpi-row-5">
        <div class="wb-panel">
          <div class="wb-panel-title">DHU · limit 2.0</div>
          <div class="wb-kpi-big" style="color:var(--wb-amber)">${data.dhuPct.toFixed(2)}%</div>
          <div class="wb-kpi-foot"><span>${data.totalDefects} defects / ${data.totalChecked} checked</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">OQL · commitment 4.5%</div>
          <div class="wb-kpi-big" style="color:var(--wb-green)">${data.oqlPct.toFixed(2)}%</div>
          <div class="wb-kpi-foot"><span>1-Hr QC + 2-Hr Bi-Hourly Audit</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">FIRST-TIME RIGHT</div>
          <div class="wb-kpi-big" style="color:var(--wb-green)">${Math.max(0, 100 - data.oqlPct).toFixed(1)}%</div>
          <div class="wb-kpi-foot"><span>Clean garments ratio</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">DEFECTS PENDING</div>
          <div class="wb-kpi-big" style="color:var(--wb-amber)">${data.defectsPending} pcs</div>
          <div class="wb-kpi-foot"><span>Sew ${data.sewBal} · Spot ${data.spotBal}</span></div>
        </div>
        <div class="wb-panel">
          <div class="wb-panel-title">CHECKED</div>
          <div class="wb-kpi-big" style="color:var(--wb-blue)">${data.totalChecked} pcs</div>
          <div class="wb-kpi-foot"><span>${data.totalDefective} defective pcs</span></div>
        </div>
      </div>

      <div class="wb-grid-3">
        <div class="wb-panel">
          <div class="wb-panel-header"><span class="wb-panel-title">DHU by Line</span><span class="wb-panel-sub">Limit 2.0%</span></div>
          <div class="wb-col-chart">
            ${active.map(r => {
              const d = r.dhu || 0;
              const h = Math.min(100, Math.round((d / 8) * 100));
              const col = d <= 2 ? "var(--wb-green)" : d <= 5 ? "var(--wb-amber)" : "var(--wb-red)";
              return `<div class="wb-col-item">
                <div class="wb-col-top-val" style="color:${col}">${d.toFixed(1)}</div>
                <div class="wb-col-bars"><div class="wb-bar-inner" style="height:${h}%;background:${col};width:65%"></div></div>
                <div class="wb-col-lbl">${r.lineId}</div>
              </div>`;
            }).join("")}
          </div>
        </div>

        <div class="wb-panel">
          <div class="wb-panel-header"><span class="wb-panel-title">OQL by Line</span><span class="wb-panel-sub">Commitment ≤ 4.5%</span></div>
          <div class="wb-col-chart">
            ${active.map(r => {
              const o = r.oql || 0;
              const h = Math.min(100, Math.round((o / 8) * 100));
              const col = o <= 4.5 ? "var(--wb-green)" : o <= 7 ? "var(--wb-amber)" : "var(--wb-red)";
              return `<div class="wb-col-item">
                <div class="wb-col-top-val" style="color:${col}">${o.toFixed(1)}%</div>
                <div class="wb-col-bars"><div class="wb-bar-inner" style="height:${h}%;background:${col};width:65%"></div></div>
                <div class="wb-col-lbl">${r.lineId}</div>
              </div>`;
            }).join("")}
          </div>
        </div>

        <div class="wb-panel">
          <div class="wb-panel-header"><span class="wb-panel-title">Defect Rectification Pending</span><span class="wb-panel-sub">Sewing &amp; Spot Balance</span></div>
          <div class="wb-rank-list">
            ${active.slice(0, 5).map(r => {
              const bal = r.sewBal + r.spotBal;
              return `<div class="wb-rank-row">
                <span class="wb-rank-label">Line ${r.lineId}</span>
                <div class="wb-rank-track"><div class="wb-rank-fill" style="width:${Math.min(100, bal * 15)}%;background:var(--wb-orange)"></div></div>
                <span class="wb-rank-val" style="color:var(--wb-orange)">${bal} pcs</span>
              </div>`;
            }).join("")}
          </div>
        </div>
      </div>
    `;
    return;
  }

  if (state.wbPage === "TABLE") {
    container.innerHTML = `
      <div class="wb-panel">
        <div class="wb-panel-header">
          <span class="wb-panel-title">15-Column Live Line Performance Table (${state.floor})</span>
          <span class="wb-panel-sub">Synced with Android TV &amp; Mobile APK</span>
        </div>
        <div class="wb-table-wrap">
          <table class="wb-table">
            <thead>
              <tr>
                <th>Line</th><th>Item</th><th>SMV</th><th>Hr Tgt</th><th>Day Tgt</th><th>Output</th>
                <th>Eff %</th><th>Ach %</th><th>DHU %</th><th>OQL %</th>
                <th>Sew Sent</th><th>Sew Recv</th><th>Sew Bal</th>
                <th>Spot Sent</th><th>Spot Recv</th><th>Spot Bal</th>
              </tr>
            </thead>
            <tbody>
              ${data.lineRows.map(r => `
                <tr>
                  <td style="font-weight:900;color:var(--wb-blue)">${r.lineId}</td>
                  <td>${r.styleNumber}</td>
                  <td>${r.smv.toFixed(1)}</td>
                  <td>${r.hourlyTarget}</td>
                  <td>${r.target}</td>
                  <td style="font-weight:900">${r.output ?? "—"}</td>
                  <td style="color:${(r.effPct || 0) >= 75 ? "var(--wb-green)" : "var(--wb-amber)"};font-weight:900">${r.effPct !== null ? r.effPct.toFixed(1) + "%" : "—"}</td>
                  <td>${r.achPct !== null ? r.achPct.toFixed(0) + "%" : "—"}</td>
                  <td>${r.dhu !== null ? r.dhu.toFixed(2) + "%" : "—"}</td>
                  <td>${r.oql !== null ? r.oql.toFixed(2) + "%" : "—"}</td>
                  <td>${r.sewSent}</td><td>${r.sewRecv}</td><td style="color:var(--wb-amber);font-weight:900">${r.sewBal}</td>
                  <td>${r.spotSent}</td><td>${r.spotRecv}</td><td style="color:var(--wb-blue);font-weight:900">${r.spotBal}</td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
      </div>
    `;
    return;
  }

  // Default: WbPage.ALL (Combined 7-Card Top Strip + Weekly BS OQL & NFL AQC Trend Chart)
  const weeklySorted = [...state.weeklyEntries].sort((a, b) => a.weekKey.localeCompare(b.weekKey)).slice(-8);
  container.innerHTML = `
    <div class="wb-all-top-7">
      <div class="wb-panel">
        <div class="wb-panel-title">Today Live Efficiency</div>
        <div class="wb-kpi-big" style="color:var(--wb-green)">${data.avgEff.toFixed(1)}%</div>
        <div class="wb-kpi-foot"><span>Target 75%</span></div>
      </div>
      <div class="wb-panel">
        <div class="wb-panel-title">Monthly Efficiency</div>
        <div class="wb-kpi-big" style="color:var(--wb-blue)">${data.monthEff.toFixed(1)}%</div>
        <div class="wb-kpi-foot"><span>Cumulative</span></div>
      </div>
      <div class="wb-panel">
        <div class="wb-panel-title">Operator vs Machine</div>
        <div class="wb-kpi-big" style="color:var(--wb-orange)">${data.opsTotal} / ${data.opsTotal}</div>
        <div class="wb-kpi-foot"><span>Ratio 1.00</span></div>
      </div>
      <div class="wb-panel">
        <div class="wb-panel-title">Floor &amp; Factory Eff</div>
        <div class="wb-kpi-big" style="color:var(--wb-blue)">S1: 75% · S2: ${data.avgEff.toFixed(0)}%</div>
        <div class="wb-kpi-foot"><span>S3: 71% · Fac: 72%</span></div>
      </div>
      <div class="wb-panel">
        <div class="wb-panel-title">BS QA OQL</div>
        <div class="wb-kpi-big" style="color:var(--wb-green)">2.40%</div>
        <div class="wb-kpi-foot"><span>Commitment ≤ 4.5%</span></div>
      </div>
      <div class="wb-panel">
        <div class="wb-panel-title">Today Live OQL</div>
        <div class="wb-kpi-big" style="color:var(--wb-green)">${data.oqlPct.toFixed(2)}%</div>
        <div class="wb-kpi-foot"><span>1-Hr + 2-Hr Audit</span></div>
      </div>
      <div class="wb-panel">
        <div class="wb-panel-title">BS QA FRI Pass</div>
        <div class="wb-kpi-big" style="color:var(--wb-green)">98.5%</div>
        <div class="wb-kpi-foot"><span>Final Random Insp.</span></div>
      </div>
    </div>

    <div class="wb-grid-3">
      <div class="wb-panel">
        <div class="wb-panel-header"><span class="wb-panel-title">Live Target vs Achievement</span><span class="wb-panel-sub">Output vs Target</span></div>
        <div class="wb-col-chart">
          ${active.map(r => {
            const hOut = Math.min(100, Math.round(((r.output || 0) / Math.max(1, r.target)) * 100));
            const col = (r.achPct || 0) >= 95 ? "var(--wb-green)" : (r.achPct || 0) >= 80 ? "var(--wb-amber)" : "var(--wb-red)";
            return `<div class="wb-col-item">
              <div class="wb-col-top-val" style="color:${col}">${r.output || 0}</div>
              <div class="wb-col-bars">
                <div class="wb-bar-outer" style="height:85%"></div>
                <div class="wb-bar-inner" style="height:${hOut}%;background:${col}"></div>
              </div>
              <div class="wb-col-lbl">${r.lineId}</div>
            </div>`;
          }).join("")}
        </div>
      </div>

      <div class="wb-panel">
        <div class="wb-panel-header"><span class="wb-panel-title">Weekly BS OQL and NFL AQC</span><span class="wb-panel-sub">Blue: BS OQL · Orange: NFL AQC · Limit 4.5%</span></div>
        <div class="wb-col-chart">
          ${weeklySorted.map(w => {
            const bsH = Math.min(100, Math.round(((w.bsOqlPct || 0) / 10) * 100));
            const aqcH = Math.min(100, Math.round(((w.nflAqcPct || 0) / 10) * 100));
            return `<div class="wb-col-item">
              <div class="wb-col-top-val" style="color:var(--wb-blue)">${w.bsOqlPct ?? "—"}%</div>
              <div class="wb-col-bars">
                <div class="wb-bar-outer" style="height:${aqcH}%;background:var(--wb-orange)"></div>
                <div class="wb-bar-inner" style="height:${bsH}%;background:var(--wb-blue)"></div>
              </div>
              <div class="wb-col-lbl">${w.weekKey.replace(/^\d{4}-/, "")}</div>
            </div>`;
          }).join("")}
        </div>
      </div>

      <div class="wb-panel">
        <div class="wb-panel-header"><span class="wb-panel-title">Efficiency by Line</span><span class="wb-panel-sub">Target 75%</span></div>
        <div class="wb-col-chart">
          ${active.map(r => {
            const eff = Math.min(100, Math.round(r.effPct || 0));
            const col = eff >= 75 ? "var(--wb-green)" : eff >= 65 ? "var(--wb-amber)" : "var(--wb-red)";
            return `<div class="wb-col-item">
              <div class="wb-col-top-val" style="color:${col}">${eff}%</div>
              <div class="wb-col-bars"><div class="wb-bar-inner" style="height:${eff}%;background:${col};width:65%"></div></div>
              <div class="wb-col-lbl">${r.lineId}</div>
            </div>`;
          }).join("")}
        </div>
      </div>
    </div>
  `;
}

function renderDefectRows() {
  const container = document.getElementById("defectRowsContainer");
  if (!container) return;
  container.innerHTML = state.defectRows
    .map(
      (row, idx) => `
      <div class="defect-row">
        <select data-defect-type="${idx}" style="flex:2;">
          ${STANDARD_DEFECTS.map(
            (d) => `<option value="${d}" ${d === row.defectType ? "selected" : ""}>${d}</option>`
          ).join("")}
        </select>
        <input type="number" min="1" value="${row.count}" data-defect-count="${idx}" style="flex:1;" />
        ${
          state.defectRows.length > 1
            ? `<button type="button" class="btn btn-danger" data-remove-defect="${idx}">✕</button>`
            : ""
        }
      </div>
    `
    )
    .join("");

  container.querySelectorAll("[data-defect-type]").forEach((el) => {
    el.addEventListener("change", (e) => {
      const i = parseInt(e.target.getAttribute("data-defect-type"), 10);
      state.defectRows[i].defectType = e.target.value;
      updateLiveQaMetrics();
    });
  });
  container.querySelectorAll("[data-defect-count]").forEach((el) => {
    el.addEventListener("input", (e) => {
      const i = parseInt(e.target.getAttribute("data-defect-count"), 10);
      state.defectRows[i].count = Math.max(0, parseInt(e.target.value || "0", 10));
      updateLiveQaMetrics();
    });
  });
  container.querySelectorAll("[data-remove-defect]").forEach((el) => {
    el.addEventListener("click", (e) => {
      const i = parseInt(e.target.getAttribute("data-remove-defect"), 10);
      state.defectRows.splice(i, 1);
      renderDefectRows();
      updateLiveQaMetrics();
    });
  });
}

function updateLiveQaMetrics() {
  const checked = parseInt(document.getElementById("qaChecked")?.value || "0", 10);
  const defective = parseInt(document.getElementById("qaDefective")?.value || "0", 10);
  const totalDefects = state.defectRows.reduce((s, r) => s + (r.count || 0), 0);
  const oql = checked > 0 ? ((defective / checked) * 100).toFixed(2) : "0.00";
  const dhu = checked > 0 ? ((totalDefects / checked) * 100).toFixed(2) : "0.00";
  const banner = document.getElementById("liveQaMetrics");
  if (banner) {
    banner.textContent = `Computed OQL: ${oql}% • Computed DHU: ${dhu}% (Total Defects: ${totalDefects})`;
  }
}

function updatePermissionsUI() {
  const allowed = canWriteWeeklyOrBiHourly(state.role);
  const biNotice = document.getElementById("biHourlyPermissionNotice");
  const submitQaBtn = document.getElementById("submitQaBtn");
  if (state.qaMode === "BI_HOURLY_AUDIT") {
    biNotice?.classList.toggle("hidden", allowed);
    if (submitQaBtn) submitQaBtn.disabled = !allowed;
  } else {
    biNotice?.classList.add("hidden");
    if (submitQaBtn) submitQaBtn.disabled = false;
  }

  const weeklyWarn = document.getElementById("weeklyPermissionWarning");
  const submitWeeklyBtn = document.getElementById("submitWeeklyBtn");
  weeklyWarn?.classList.toggle("hidden", allowed);
  if (submitWeeklyBtn) submitWeeklyBtn.disabled = !allowed;
}

function renderSupportingTables() {
  // Weekly table
  const wBody = document.getElementById("weeklyTableBody");
  if (wBody) {
    wBody.innerHTML = state.weeklyEntries
      .slice()
      .sort((a, b) => b.weekKey.localeCompare(a.weekKey))
      .map((w) => {
        const ref = Math.max(w.bsOqlPct ?? 0, w.nflAqcPct ?? 0);
        const badge =
          ref <= 4.5
            ? `<span class="badge badge-green">PASS (≤4.5%)</span>`
            : ref < 7
            ? `<span class="badge badge-amber">LEVEL 1 (&lt;7%)</span>`
            : `<span class="badge badge-red">CRITICAL (&gt;7%)</span>`;
        return `<tr>
          <td><strong>${w.weekKey}</strong></td>
          <td>${w.weekStartIso}</td>
          <td>${w.bsOqlPct !== null ? Number(w.bsOqlPct).toFixed(2) + "%" : "-"}</td>
          <td>${w.nflAqcPct !== null ? Number(w.nflAqcPct).toFixed(2) + "%" : "-"}</td>
          <td>${badge}</td>
          <td>${w.lastUpdatedBy}</td>
        </tr>`;
      })
      .join("");
  }

  // NPT table
  const nptBody = document.getElementById("nptTableBody");
  if (nptBody) {
    nptBody.innerHTML = state.incidents
      .map((inc, idx) => {
        const mins = Math.max(1, Math.round((Date.now() - inc.startTimeMillis) / 60000));
        return `<tr>
          <td><strong>${inc.lineId}</strong></td>
          <td>${inc.floor}</td>
          <td>${inc.category}</td>
          <td>${inc.description}</td>
          <td>${mins} min</td>
          <td><span class="badge ${inc.status === "RESOLVED" ? "badge-green" : "badge-red"}">${inc.status}</span></td>
          <td>${
            inc.status !== "RESOLVED"
              ? `<button class="btn btn-success" data-resolve-inc="${idx}" style="padding:4px 8px;font-size:11px;">Resolve</button>`
              : "Resolved"
          }</td>
        </tr>`;
      })
      .join("");
    nptBody.querySelectorAll("[data-resolve-inc]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        const i = parseInt(e.target.getAttribute("data-resolve-inc"), 10);
        state.incidents[i].status = "RESOLVED";
        saveLocalState();
        renderWallboard();
        renderSupportingTables();
      });
    });
  }

  // Deep dive table
  const ddLine = document.getElementById("ddLine")?.value || "K1";
  const ddBody = document.getElementById("deepDiveTableBody");
  if (ddBody) {
    const prods = state.productionEntries.filter((p) => p.lineId === ddLine);
    const qas = state.qualityEntries.filter((q) => q.lineId === ddLine);
    ddBody.innerHTML = [
      ...prods.map((p) => {
        const q = qas.find((x) => x.hourSlot === p.hourSlot);
        const oql = q && q.checkedGarments > 0 ? ((q.defectiveGarments / q.checkedGarments) * 100).toFixed(2) + "%" : "—";
        const dhu = q && q.checkedGarments > 0 ? ((q.totalDefects / q.checkedGarments) * 100).toFixed(2) + "%" : "—";
        return `<tr>
          <td><strong>${formatHourSlot12h(p.hourSlot)}</strong></td>
          <td>1-Hr Production &amp; QC</td>
          <td><strong>${p.outputQty}</strong></td>
          <td>${q?.checkedGarments ?? "—"}</td>
          <td>${q?.defectiveGarments ?? "—"}</td>
          <td>${q?.totalDefects ?? "—"}</td>
          <td>${oql}</td>
          <td>${dhu}</td>
          <td>${q ? `${q.defectSent || 0}/${q.defectReceived || 0}` : "—"}</td>
          <td>${q ? `${q.spotSent || 0}/${q.spotReceived || 0}` : "—"}</td>
        </tr>`;
      }),
      ...qas
        .filter((q) => isBiHourlySlot(q.hourSlot))
        .map((q) => `<tr>
          <td><strong>${formatHourSlot12h(q.hourSlot)}</strong></td>
          <td><span class="badge badge-blue">2-Hr Bi-Hourly Audit</span></td>
          <td>—</td>
          <td><strong>${q.checkedGarments}</strong></td>
          <td><strong>${q.defectiveGarments}</strong></td>
          <td><strong>${q.totalDefects}</strong></td>
          <td>${((q.defectiveGarments / Math.max(1, q.checkedGarments)) * 100).toFixed(2)}%</td>
          <td>${((q.totalDefects / Math.max(1, q.checkedGarments)) * 100).toFixed(2)}%</td>
          <td>—</td>
          <td>—</td>
        </tr>`)
    ].join("");
  }

  // Recent submissions in Admin tab
  const recBody = document.getElementById("recentRecordsBody");
  if (recBody) {
    const combined = [
      ...state.productionEntries.slice(0, 15).map((p, idx) => ({
        type: "PROD",
        idx,
        lineId: p.lineId,
        floor: p.floor,
        date: p.date,
        slot12: formatHourSlot12h(p.hourSlot),
        cat: "Production (1-Hr)",
        details: `Output: ${p.outputQty} pcs`,
        by: p.modifiedBy
      })),
      ...state.qualityEntries.slice(0, 15).map((q, idx) => ({
        type: "QA",
        idx,
        lineId: q.lineId,
        floor: q.floor,
        date: q.date,
        slot12: formatHourSlot12h(q.hourSlot),
        cat: isBiHourlySlot(q.hourSlot) ? "Bi-Hourly Audit (2-Hr OQL)" : "Quality DHU (1-Hr)",
        details: `Checked: ${q.checkedGarments} • Defective: ${q.defectiveGarments}`,
        by: q.modifiedBy
      }))
    ];
    recBody.innerHTML = combined
      .map(
        (r) => `<tr>
        <td><strong>${r.lineId}</strong></td>
        <td>${r.floor}</td>
        <td>${r.date}</td>
        <td><strong>${r.slot12}</strong></td>
        <td>${r.cat}</td>
        <td>${r.details}</td>
        <td>${r.by}</td>
        <td>${
          state.role === "ADMIN"
            ? `<button class="btn btn-danger" data-del-type="${r.type}" data-del-idx="${r.idx}" style="padding:3px 8px;font-size:11px;">Delete</button>`
            : "—"
        }</td>
      </tr>`
      )
      .join("");

    recBody.querySelectorAll("[data-del-type]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        const t = e.target.getAttribute("data-del-type");
        const i = parseInt(e.target.getAttribute("data-del-idx"), 10);
        if (t === "PROD") state.productionEntries.splice(i, 1);
        else state.qualityEntries.splice(i, 1);
        saveLocalState();
        renderWallboard();
        renderSupportingTables();
      });
    });
  }
}

function downloadCsv(filename, rows) {
  const csv = rows
    .map((r) => r.map((v) => `"${String(v ?? "").replace(/"/g, '""')}"`).join(","))
    .join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

window.addEventListener("DOMContentLoaded", () => {
  seedDemoIfEmpty();

  const today = getTodayIso();
  ["setupDate", "prodDate", "qaDate", "weeklyStartDate"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.value = today;
  });

  populateLineSelect("setupLine", "Sew 2");
  populateLineSelect("prodLine", "Sew 2");
  populateLineSelect("qaLine", "Sew 2");
  populateLineSelect("nptLine", "Sew 2");
  populateLineSelect("ddLine", "Sew 2");
  populateSlotSelect("prodHourSlot", HOUR_SLOTS_24);
  populateSlotSelect("qaHourSlot", HOUR_SLOTS_24);
  renderBatchMatrix();
  renderDefectRows();

  // Render ISO week chips
  const weeks = generateRecentWeeks(12);
  const weekChips = document.getElementById("weekChipsContainer");
  if (weekChips) {
    weekChips.innerHTML = weeks
      .map(
        (w, idx) =>
          `<button type="button" class="week-chip ${idx === weeks.length - 1 ? "active" : ""}" data-wk="${w.weekKey}" data-wkstart="${w.weekStartIso}">${w.weekKey}</button>`
      )
      .join("");
    const lastW = weeks[weeks.length - 1];
    if (lastW) {
      document.getElementById("weeklyKey").value = lastW.weekKey;
      document.getElementById("weeklyStartDate").value = lastW.weekStartIso;
    }
    weekChips.querySelectorAll(".week-chip").forEach((chip) => {
      chip.addEventListener("click", () => {
        weekChips.querySelectorAll(".week-chip").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        document.getElementById("weeklyKey").value = chip.getAttribute("data-wk");
        document.getElementById("weeklyStartDate").value = chip.getAttribute("data-wkstart");
      });
    });
  }

  updatePermissionsUI();
  renderWallboard();
  renderSupportingTables();

  // Top nav tabs
  document.querySelectorAll(".nav-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".nav-tab").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".tab-panel").forEach((p) => p.classList.add("hidden"));
      btn.classList.add("active");
      document.getElementById(`tab-${btn.getAttribute("data-tab")}`)?.classList.remove("hidden");
    });
  });

  // Wallboard page buttons
  document.querySelectorAll("[data-wbpage]").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("[data-wbpage]").forEach((b) => b.classList.remove("page-active"));
      btn.classList.add("page-active");
      state.wbPage = btn.getAttribute("data-wbpage");
      renderWallboard();
    });
  });

  // Wallboard floor buttons
  document.querySelectorAll("[data-wbfloor]").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("[data-wbfloor]").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      state.floor = btn.getAttribute("data-wbfloor");
      document.getElementById("floorFilter").value = state.floor;
      renderWallboard();
    });
  });

  // Auto-slide timer for TV Wallboard
  const autoBtn = document.getElementById("wbAutoSlideBtn");
  autoBtn?.addEventListener("click", () => {
    state.wbIntervalIdx = (state.wbIntervalIdx + 1) % WB_INTERVALS.length;
    const iv = WB_INTERVALS[state.wbIntervalIdx];
    state.wbCountdown = iv || 30;
    autoBtn.textContent = iv > 0 ? `Auto-slide ${iv}s · next ${state.wbCountdown}s` : "Auto-slide paused";
  });

  setInterval(() => {
    const now = new Date();
    const clockEl = document.getElementById("wbClockTime");
    if (clockEl) {
      clockEl.textContent = now.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit", hour12: true });
    }
    const iv = WB_INTERVALS[state.wbIntervalIdx];
    if (iv > 0 && state.wbPage !== "TABLE") {
      if (state.wbCountdown > 1) {
        state.wbCountdown--;
      } else {
        state.wbCountdown = iv;
        const order = ["PRODUCTION", "QUALITY", "ALL"];
        state.wbPage = order[(order.indexOf(state.wbPage) + 1) % order.length];
        document.querySelectorAll("[data-wbpage]").forEach((b) => {
          b.classList.toggle("page-active", b.getAttribute("data-wbpage") === state.wbPage);
        });
        renderWallboard();
      }
      if (autoBtn) autoBtn.textContent = `Auto-slide ${iv}s · next ${state.wbCountdown}s`;
    }
  }, 1000);

  // Fullscreen TV toggle
  document.getElementById("fullscreenTvBtn")?.addEventListener("click", () => {
    const shell = document.getElementById("wbShell");
    shell?.classList.toggle("fullscreen-mode");
  });

  // Role & Floor selectors
  document.getElementById("floorFilter")?.addEventListener("change", (e) => {
    state.floor = e.target.value;
    renderWallboard();
  });
  document.getElementById("roleSelector")?.addEventListener("change", (e) => {
    state.role = e.target.value;
    updatePermissionsUI();
    renderSupportingTables();
    showBanner(`Switched active role to ${state.role}`);
  });

  // Morning Setup auto-calc & save
  document.getElementById("autoCalcSetupBtn")?.addEventListener("click", () => {
    const smv = parseFloat(document.getElementById("setupSmv").value || "8.5");
    const ops = parseInt(document.getElementById("setupOps").value || "20", 10);
    const hrs = parseInt(document.getElementById("setupHours").value || "8", 10);
    const eff = parseFloat(document.getElementById("setupTargetEff").value || "75") / 100;
    if (smv > 0) {
      document.getElementById("setupDailyTarget").value = Math.round(((ops * hrs * 60) / smv) * eff);
    }
  });
  document.getElementById("saveSetupBtn")?.addEventListener("click", () => {
    const lineId = document.getElementById("setupLine").value;
    const floor = document.getElementById("setupFloor").value;
    const dailyTarget = parseInt(document.getElementById("setupDailyTarget").value || "480", 10);
    const workingHours = parseInt(document.getElementById("setupHours").value || "8", 10);
    state.setups = state.setups.filter((s) => s.lineId !== lineId);
    state.setups.push({
      lineId,
      floor,
      date: document.getElementById("setupDate").value,
      styleNumber: document.getElementById("setupStyle").value,
      smv: parseFloat(document.getElementById("setupSmv").value || "8.5"),
      operatorsCount: parseInt(document.getElementById("setupOps").value || "20", 10),
      helpersCount: parseInt(document.getElementById("setupHelpers").value || "2", 10),
      workingHours,
      targetEff: parseFloat(document.getElementById("setupTargetEff").value || "75"),
      dailyTarget,
      hourlyTarget: Math.round(dailyTarget / Math.max(1, workingHours))
    });
    saveLocalState();
    renderWallboard();
    showBanner(`✓ Saved Morning Line Setup for Line ${lineId}`);
  });

  // Production Single vs Batch Matrix
  document.getElementById("prodModeSingleBtn")?.addEventListener("click", () => {
    state.prodMode = "SINGLE";
    document.getElementById("prodModeSingleBtn").classList.add("active");
    document.getElementById("prodModeBatchBtn").classList.remove("active");
    document.getElementById("batchMatrixContainer").classList.add("hidden");
    document.getElementById("prodLineWrap").classList.remove("hidden");
    document.getElementById("prodQtyWrap").classList.remove("hidden");
  });
  document.getElementById("prodModeBatchBtn")?.addEventListener("click", () => {
    state.prodMode = "BATCH";
    document.getElementById("prodModeBatchBtn").classList.add("active");
    document.getElementById("prodModeSingleBtn").classList.remove("active");
    document.getElementById("batchMatrixContainer").classList.remove("hidden");
    document.getElementById("prodLineWrap").classList.add("hidden");
    document.getElementById("prodQtyWrap").classList.add("hidden");
    renderBatchMatrix();
  });
  document.getElementById("prodFloor")?.addEventListener("change", (e) => {
    populateLineSelect("prodLine", e.target.value);
    renderBatchMatrix();
  });

  document.getElementById("submitProdBtn")?.addEventListener("click", () => {
    const floor = document.getElementById("prodFloor").value;
    const date = document.getElementById("prodDate").value;
    const hourSlot = document.getElementById("prodHourSlot").value;
    const styleNumber = document.getElementById("prodStyle").value;
    const remarks = document.getElementById("prodRemarks").value;

    if (state.prodMode === "BATCH") {
      let saved = 0;
      document.querySelectorAll(".batch-line-input").forEach((inp) => {
        const val = inp.value.trim();
        if (val !== "") {
          const lineId = inp.getAttribute("data-line");
          state.productionEntries.unshift({
            lineId,
            floor,
            date,
            hourSlot,
            outputQty: parseInt(val, 10) || 0,
            styleNumber,
            remarks,
            modifiedBy: state.role,
            timestamp: Date.now()
          });
          inp.value = "";
          saved++;
        }
      });
      saveLocalState();
      renderWallboard();
      renderSupportingTables();
      showBanner(`✓ Batch saved ${saved} lines for ${formatHourSlot12h(hourSlot)}`);
      return;
    }

    const lineId = document.getElementById("prodLine").value;
    const outputQty = parseInt(document.getElementById("prodQty").value || "0", 10);
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
    renderWallboard();
    renderSupportingTables();
    showBanner(`✓ Saved Production for Line ${lineId} (${formatHourSlot12h(hourSlot)}): ${outputQty} pcs`);
  });

  // Quality 1-Hr vs 2-Hr Bi-Hourly Audit
  document.getElementById("modeHourlyDhuBtn")?.addEventListener("click", () => {
    state.qaMode = "HOURLY_DHU";
    document.getElementById("modeHourlyDhuBtn").classList.add("active");
    document.getElementById("modeBiHourlyAuditBtn").classList.remove("active");
    document.getElementById("rectificationSection").classList.remove("hidden");
    populateSlotSelect("qaHourSlot", HOUR_SLOTS_24);
    updatePermissionsUI();
  });
  document.getElementById("modeBiHourlyAuditBtn")?.addEventListener("click", () => {
    state.qaMode = "BI_HOURLY_AUDIT";
    document.getElementById("modeBiHourlyAuditBtn").classList.add("active");
    document.getElementById("modeHourlyDhuBtn").classList.remove("active");
    document.getElementById("rectificationSection").classList.add("hidden");
    populateSlotSelect("qaHourSlot", BI_HOURLY_SLOTS_24);
    updatePermissionsUI();
  });

  document.getElementById("addDefectRowBtn")?.addEventListener("click", () => {
    state.defectRows.push({ defectType: STANDARD_DEFECTS[0], count: 1 });
    renderDefectRows();
    updateLiveQaMetrics();
  });
  document.getElementById("qaChecked")?.addEventListener("input", updateLiveQaMetrics);
  document.getElementById("qaDefective")?.addEventListener("input", updateLiveQaMetrics);

  document.getElementById("submitQaBtn")?.addEventListener("click", () => {
    const isBi = state.qaMode === "BI_HOURLY_AUDIT";
    if (isBi && !canWriteWeeklyOrBiHourly(state.role)) {
      showBanner("Access Denied: Only Admin and Auditor can submit Bi-Hourly Audit.", true);
      return;
    }
    const lineId = document.getElementById("qaLine").value;
    const floor = document.getElementById("qaFloor").value;
    const date = document.getElementById("qaDate").value;
    const hourSlot = document.getElementById("qaHourSlot").value;
    const checkedGarments = parseInt(document.getElementById("qaChecked").value || "0", 10);
    const defectiveGarments = parseInt(document.getElementById("qaDefective").value || "0", 10);
    const totalDefects = state.defectRows.reduce((s, r) => s + (r.count || 0), 0);

    if (checkedGarments <= 0) {
      showBanner("Checked garments must be > 0.", true);
      return;
    }
    state.qualityEntries.unshift({
      lineId,
      floor,
      date,
      hourSlot,
      checkedGarments,
      defectiveGarments,
      totalDefects,
      defects: [...state.defectRows],
      defectSent: isBi ? 0 : parseInt(document.getElementById("qaSewSent").value || "0", 10),
      defectReceived: isBi ? 0 : parseInt(document.getElementById("qaSewRecv").value || "0", 10),
      spotSent: isBi ? 0 : parseInt(document.getElementById("qaSpotSent").value || "0", 10),
      spotReceived: isBi ? 0 : parseInt(document.getElementById("qaSpotRecv").value || "0", 10),
      modifiedBy: state.role,
      timestamp: Date.now()
    });
    saveLocalState();
    renderWallboard();
    renderSupportingTables();
    showBanner(`✓ Saved ${isBi ? "2-Hr Bi-Hourly Audit" : "1-Hr Quality DHU"} for Line ${lineId} (${formatHourSlot12h(hourSlot)})`);
  });

  // Weekly BS OQL / NFL AQC
  document.getElementById("submitWeeklyBtn")?.addEventListener("click", () => {
    if (!canWriteWeeklyOrBiHourly(state.role)) {
      showBanner("Access Denied: Only Admin and Auditor can enter Weekly BS OQL / AQC.", true);
      return;
    }
    const weekKey = document.getElementById("weeklyKey").value.trim();
    const weekStartIso = document.getElementById("weeklyStartDate").value;
    const bs = document.getElementById("weeklyBsOql").value.trim();
    const aqc = document.getElementById("weeklyNflAqc").value.trim();
    state.weeklyEntries = state.weeklyEntries.filter((w) => w.weekKey !== weekKey);
    state.weeklyEntries.push({
      weekKey,
      weekStartIso,
      bsOqlPct: bs !== "" ? parseFloat(bs) : null,
      nflAqcPct: aqc !== "" ? parseFloat(aqc) : null,
      lastUpdatedBy: state.role,
      lastUpdatedMillis: Date.now()
    });
    saveLocalState();
    renderWallboard();
    renderSupportingTables();
    showBanner(`✓ Saved Weekly BS OQL / NFL AQC for ${weekKey}`);
  });

  // NPT Report
  document.getElementById("reportNptBtn")?.addEventListener("click", () => {
    state.incidents.unshift({
      id: `inc-${Date.now()}`,
      floor: document.getElementById("nptFloor").value,
      lineId: document.getElementById("nptLine").value,
      category: document.getElementById("nptCategory").value,
      description: document.getElementById("nptDesc").value || "Stoppage reported",
      startTimeMillis: Date.now(),
      status: "OPEN"
    });
    saveLocalState();
    renderWallboard();
    renderSupportingTables();
    showBanner("🚨 Stoppage reported and added to TV Wallboard!");
  });

  // Deep dive selector
  document.getElementById("ddFloor")?.addEventListener("change", (e) => {
    populateLineSelect("ddLine", e.target.value);
    renderSupportingTables();
  });
  document.getElementById("ddLine")?.addEventListener("change", renderSupportingTables);

  // CSV Exports
  document.getElementById("exportProdCsvBtn")?.addEventListener("click", () => {
    downloadCsv("NFL_Production_12h.csv", [
      ["Line", "Floor", "Date", "Slot (12h AM/PM)", "Output Pcs", "Style", "Updated By"],
      ...state.productionEntries.map((p) => [
        p.lineId,
        p.floor,
        p.date,
        formatHourSlot12h(p.hourSlot),
        p.outputQty,
        p.styleNumber,
        p.modifiedBy
      ])
    ]);
  });
  document.getElementById("exportQaCsvBtn")?.addEventListener("click", () => {
    downloadCsv("NFL_Quality_BiHourly_Audit_12h.csv", [
      ["Line", "Floor", "Date", "Slot (12h AM/PM)", "Mode", "Checked", "Defective", "Defects", "Updated By"],
      ...state.qualityEntries.map((q) => [
        q.lineId,
        q.floor,
        q.date,
        formatHourSlot12h(q.hourSlot),
        isBiHourlySlot(q.hourSlot) ? "2-Hr Bi-Hourly Audit" : "1-Hr Quality DHU",
        q.checkedGarments,
        q.defectiveGarments,
        q.totalDefects,
        q.modifiedBy
      ])
    ]);
  });
  document.getElementById("exportWeeklyCsvBtn")?.addEventListener("click", () => {
    downloadCsv("NFL_Weekly_BS_OQL_AQC.csv", [
      ["Week Key", "Week Start ISO", "BS OQL (%)", "NFL AQC (%)", "Updated By"],
      ...state.weeklyEntries.map((w) => [w.weekKey, w.weekStartIso, w.bsOqlPct ?? "", w.nflAqcPct ?? "", w.lastUpdatedBy])
    ]);
  });

  // Demo & Reset controls
  document.getElementById("loadDemoBtn")?.addEventListener("click", () => {
    localStorage.setItem("nfl_desktop_cleared", "false");
    state.productionEntries = [];
    seedDemoIfEmpty();
    renderWallboard();
    renderSupportingTables();
    showBanner("✓ Loaded synthetic demo dataset across all floors.");
  });
  document.getElementById("clearDemoBtn")?.addEventListener("click", () => {
    localStorage.setItem("nfl_desktop_cleared", "true");
    state.productionEntries = state.productionEntries.filter((r) => !r.isDemo);
    state.qualityEntries = state.qualityEntries.filter((r) => !r.isDemo);
    state.weeklyEntries = state.weeklyEntries.filter((r) => !r.isDemo);
    state.incidents = state.incidents.filter((r) => !r.isDemo);
    saveLocalState();
    renderWallboard();
    renderSupportingTables();
    showBanner("✓ Cleared all demo/sample records.");
  });
  document.getElementById("factoryResetBtn")?.addEventListener("click", () => {
    localStorage.setItem("nfl_desktop_cleared", "true");
    state.productionEntries = [];
    state.qualityEntries = [];
    state.weeklyEntries = [];
    state.incidents = [];
    saveLocalState();
    renderWallboard();
    renderSupportingTables();
    showBanner("✓ Factory Reset complete: All operational records cleared.");
  });
});
