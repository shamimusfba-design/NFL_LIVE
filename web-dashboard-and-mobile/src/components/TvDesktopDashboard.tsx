import React, { useState, useEffect, useMemo } from 'react';

/** Button and title text for every view */
const FLOOR_LABEL: Record<FloorId, string> = {
  'Sew 1': 'Sew 1', 'Sew 2': 'Sew 2', 'Sew 3': 'Sew 3',
  'All Sewing': 'Sewing', 'Cutting': 'Cutting', 'Print': 'Print', 'Embroidery': 'Embroidery', 'Print & Embroidery': 'Print & Embroidery', 'Factory': 'Factory'
};
/** Buttons in order: the three sewing floors, all sewing, the departments, then the whole factory */
const FLOOR_BUTTONS: FloorId[] = ['Sew 1', 'Sew 2', 'Sew 3', 'All Sewing', 'Cutting', 'Print & Embroidery', 'Factory'];
import {
  FloorId,
  PeriodId,
  FloorKpis,
  LinePerformanceSummary,
  IncidentRecord,
  WeeklyQualityRecord,
  ProductionEntryRecord,
  QualityEntryRecord
} from '../types';
import { HourlyWallboardBucket, FinishingFloorSummary } from '../services/calculationEngine';
import { formatHourSlot12h,
  hourLabelOfSlot, isBiHourlyAuditSlot, floorMatches, lineDisplayName, isDepartmentFloor, unitWord,
  AUDIT_PASS_OQL_PCT, auditResultOf, getBangladeshTodayIso } from '../data/masterData';
import { CalculationEngine as CalcForPeriod } from '../services/calculationEngine';

export type WbPageId = 'PRODUCTION' | 'QUALITY' | 'FINISHING' | 'ALL' | 'TABLE';

const WB_INTERVALS = [30, 60, 10, 15, 0];

// Colours come from CSS variables (see index.html) so one definition serves the Night and the Day theme.
const WB_THEME = {
  BG: 'var(--wb-bg)',
  PANEL: 'var(--wb-panel)',
  LINE: 'var(--wb-line)',
  TXT: 'var(--wb-txt)',
  MUTED: 'var(--wb-muted)',
  RED: 'var(--wb-red)',
  AMBER: 'var(--wb-amber)',
  GREEN: 'var(--wb-green)',
  BLUE: 'var(--wb-blue)',
  ORANGE: 'var(--wb-orange)',
  GREY: 'var(--wb-grey)',
  DIM: 'var(--wb-dim)'
};

interface TvDesktopDashboardProps {
  selectedFloor: FloorId;
  onSelectFloor: (floor: FloorId) => void;
  selectedPeriod: PeriodId;
  onSelectPeriod: (p: PeriodId) => void;
  kpis: FloorKpis;
  floorBenchmarks?: Array<{ floor: string; todayEff: number; monthEff: number }>;
  lineSummaries: LinePerformanceSummary[];
  hourlySeries: HourlyWallboardBucket[];
  weeklyQuality: WeeklyQualityRecord[];
  productions: ProductionEntryRecord[];
  qualities: QualityEntryRecord[];
  topDefects: Array<{ defect: string; count: number; dhu: number }>;
  highestDhuLines: Array<{ line: string; dhu: number }>;
  isDemoMode: boolean;
  onToggleDemo: () => void;
  openIncidents: IncidentRecord[];
  allIncidents?: IncidentRecord[];
  onOpenIncidentPanel: () => void;
  /** Iron, Packing and Quality of each sewing floor in the view */
  finishing?: FinishingFloorSummary[];
  /** Cutting / Print / Embroidery totals, shown as a strip on the Factory view */
  departmentSummaries?: Array<{ floor: string; units: number; target: number; output: number; achievementPct: number | null; checked: number; passed: number }>;
  /** The side menu can ask for a page. n changes on every request so the same page can be asked twice. */
  pageRequest?: { page: WbPageId; n: number };
}

function effColor(val: number | null, target = 75): string {
  if (val === null) return WB_THEME.GREY;
  const ratio = target > 0 ? val / target : 0;
  if (ratio >= 1.0) return WB_THEME.GREEN;
  if (ratio >= 0.85) return WB_THEME.AMBER;
  return WB_THEME.RED;
}

function achColor(pct: number | null): string {
  if (pct === null) return WB_THEME.GREY;
  if (pct >= 95) return WB_THEME.GREEN;
  if (pct >= 80) return WB_THEME.AMBER;
  return WB_THEME.RED;
}

function dhuColor(dhu: number | null, limit = 2.0): string {
  if (dhu === null) return WB_THEME.GREY;
  if (dhu <= limit) return WB_THEME.GREEN;
  if (dhu <= limit * 2.5) return WB_THEME.AMBER;
  return WB_THEME.RED;
}

function oqlColor(oql: number | null, target = 4.5): string {
  if (oql === null) return WB_THEME.GREY;
  if (oql <= target) return WB_THEME.GREEN;
  if (oql <= 7.0) return WB_THEME.AMBER;
  return WB_THEME.RED;
}

export const TvDesktopDashboard: React.FC<TvDesktopDashboardProps> = ({
  pageRequest,
  selectedFloor,
  onSelectFloor,
  selectedPeriod,
  onSelectPeriod,
  kpis,
  floorBenchmarks = [],
  lineSummaries,
  hourlySeries,
  weeklyQuality,
  productions,
  qualities,
  topDefects,
  highestDhuLines,
  isDemoMode,
  onToggleDemo,
  openIncidents,
  allIncidents = [],
  onOpenIncidentPanel,
  departmentSummaries = [],
  finishing = []
}) => {
  const [page, setPage] = useState<WbPageId>('ALL');
  // A page chosen in the side menu is shown at once.
  useEffect(() => {
    if (pageRequest) setPage(pageRequest.page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageRequest?.n]);
  const [intervalIdx, setIntervalIdx] = useState(0);
  const interval = WB_INTERVALS[intervalIdx];
  const [countdown, setCountdown] = useState(WB_INTERVALS[0]);
  const [deepDiveLine, setDeepDiveLine] = useState<LinePerformanceSummary | null>(null);
  // Cutting, Print and Embroidery get their own page: no sewing cards (DHU, OQL, operator vs machine, efficiency).
  // TV fit: scale the whole wallboard to the width of the screen (a 43-inch TV fits the 1600px design at about 1.6x).
  const [tvFit, setTvFit] = useState<boolean>(() => { try { return localStorage.getItem('nfl_tv_fit') === '1'; } catch { return false; } });
  const [vw, setVw] = useState<number>(() => (typeof window !== 'undefined' ? window.innerWidth : 1200));
  useEffect(() => {
    const onResize = () => setVw(window.innerWidth);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  useEffect(() => { try { localStorage.setItem('nfl_tv_fit', tvFit ? '1' : '0'); } catch {} }, [tvFit]);
  const canFit = vw >= 1100;
  const fitScale = tvFit && canFit ? Math.min(1.6, Math.max(0.6, vw / 1600)) : 1;
  const isDept = isDepartmentFloor(selectedFloor) || selectedFloor === 'Print & Embroidery';
  const hasQc = selectedFloor === 'Print' || selectedFloor === 'Embroidery' || selectedFloor === 'Print & Embroidery';
  const unit = unitWord(selectedFloor);
  const todayW = getBangladeshTodayIso();
  const auditByLine = useMemo(() => {
    const m = new Map<string, { lineId: string; floor: string; pass: number; fail: number }>();
    qualities
      .filter(q => isBiHourlyAuditSlot(q.hourSlot) && floorMatches(q.floor, selectedFloor) && CalcForPeriod.isDateInPeriod(q.date, todayW, selectedPeriod))
      .forEach(q => {
        const r = auditResultOf(q.checkedGarments, q.defectiveGarments);
        if (!r) return;
        const v = m.get(q.lineId) || { lineId: q.lineId, floor: q.floor, pass: 0, fail: 0 };
        if (r === 'PASS') v.pass++; else v.fail++;
        m.set(q.lineId, v);
      });
    return Array.from(m.values()).sort((a, b) => a.lineId.localeCompare(b.lineId, undefined, { numeric: true }));
  }, [qualities, selectedFloor, selectedPeriod, todayW]);

  // Auto-slide Production -> Quality -> All (pauses on Table or when interval === 0)
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (interval > 0 && (isDept || page !== 'TABLE')) {
        setCountdown(prev => {
          if (prev > 1) return prev - 1;
          if (isDept) {
            if (selectedFloor === 'Print') onSelectFloor('Embroidery');
            else if (selectedFloor === 'Embroidery') onSelectFloor('Print');
            return interval;
          }
          setPage(curr => {
            if (curr === 'PRODUCTION') return 'QUALITY';
            if (curr === 'QUALITY') return 'FINISHING';
            if (curr === 'FINISHING') return 'ALL';
            if (curr === 'ALL') return 'PRODUCTION';
            return 'TABLE';
          });
          return interval;
        });
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [interval, page, isDept, selectedFloor]);

  const activeLines = useMemo(
    () => lineSummaries.filter(l => l.target > 0 || l.output !== null || l.checkedGarments > 0),
    [lineSummaries]
  );

  // Status counts for Today status strip
  const onTargetCount = activeLines.filter(l => (l.achievementPct ?? -1) >= 95).length;
  const slightBehindCount = activeLines.filter(
    l => l.achievementPct !== null && l.achievementPct >= 80 && l.achievementPct < 95
  ).length;
  const farBehindCount = activeLines.filter(
    l => l.achievementPct !== null && l.achievementPct < 80
  ).length;
  const noOutputCount = activeLines.filter(l => l.output === null).length;
  const notRunningCount = Math.max(0, lineSummaries.length - activeLines.length);

  // Downtime by Cause
  const downtimeByCause = useMemo(() => {
    const map: Record<string, number> = {};
    const list = allIncidents.length > 0 ? allIncidents : openIncidents;
    list
      .filter(i => floorMatches(i.floor, selectedFloor))
      .forEach(i => {
        const mins = Math.max(1, Math.round(((i.endTimeMillis || Date.now()) - i.startTimeMillis) / 60000));
        const label = i.category.replace(/_/g, ' ');
        map[label] = (map[label] || 0) + mins;
      });
    return Object.entries(map)
      .map(([cause, mins]) => ({ cause, mins }))
      .sort((a, b) => b.mins - a.mins)
      .slice(0, 5);
  }, [allIncidents, openIncidents, selectedFloor]);

  // Worst lines for main defect
  const mainDefectName = topDefects[0]?.defect || 'Uncut thread';
  const mainDefectWorstLines = useMemo(() => {
    const map: Record<string, number> = {};
    qualities
      .filter(q => floorMatches(q.floor, selectedFloor))
      .forEach(q => {
        q.defects.forEach(d => {
          if (d.defectType.toLowerCase() === mainDefectName.toLowerCase()) {
            map[q.lineId] = (map[q.lineId] || 0) + d.count;
          }
        });
      });
    return Object.entries(map)
      .map(([line, count]) => ({ line, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);
  }, [qualities, selectedFloor, mainDefectName]);

  const sortedWeekly = useMemo(() => {
    return [...weeklyQuality]
      .filter(w => w.bsOqlPct !== null || w.nflAqcPct !== null)
      .sort((a, b) => a.weekKey.localeCompare(b.weekKey))
      .slice(-8);
  }, [weeklyQuality]);

  return (
    <div
      style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.TXT, ...(fitScale !== 1 ? { zoom: fitScale } : {}) }}
      className="min-h-[calc(100vh-42px)] flex flex-col p-2.5 gap-2 select-none font-sans tabular-nums"
    >
      {/* ==================== HEADER BAR (Matches TvDashboardScreen.kt) ==================== */}
      <div className="flex flex-wrap items-center justify-between gap-2 min-h-[40px]">
        <div className="hidden md:flex items-center gap-2">
          <div className="bg-white text-[#002D62] px-2.5 py-1 rounded flex items-center gap-1.5 font-black text-sm shadow">
            <span>🏭</span>
            <span>NFL LIVE</span>
          </div>
          <span className="hidden sm:inline text-xs font-bold" style={{ color: WB_THEME.MUTED }}>
            TV Wallboard &amp; Command Center
          </span>
        </div>

        {/* 4-Page Segment: Production | Quality | All | Table (sewing only) */}
        <div
          style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
          className={`${isDept ? 'hidden' : 'grid'} grid-cols-5 sm:flex w-full sm:w-auto items-center p-0.5 rounded-md border gap-0.5`}
        >
          {(
            [
              { id: 'PRODUCTION', label: 'Production' },
              { id: 'QUALITY', label: 'Quality' },
              { id: 'FINISHING', label: 'Finishing' },
              { id: 'ALL', label: 'All' },
              { id: 'TABLE', label: 'Table' }
            ] as Array<{ id: WbPageId; label: string }>
          ).map(item => (
            <button
              key={item.id}
              onClick={() => {
                setPage(item.id);
                setCountdown(interval > 0 ? interval : WB_INTERVALS[0]);
              }}
              style={{
                backgroundColor: page === item.id ? WB_THEME.BLUE : 'transparent',
                color: page === item.id ? 'var(--wb-on-selected)' : WB_THEME.MUTED
              }}
              className="shrink-0 px-3 py-1.5 sm:py-1 rounded text-xs font-black transition text-center whitespace-nowrap"
            >
              {item.label}
            </button>
          ))}
        </div>

        {/* Floor Segment: Sew 1 | Sew 2 | Sew 3 | All Floors */}
        <div
          style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
          className="nfl-scroll flex w-full sm:w-auto items-center p-0.5 rounded-md border gap-0.5 overflow-x-auto"
        >
          {FLOOR_BUTTONS.map(fl => (
            <button
              key={fl}
              onClick={() => onSelectFloor(fl)}
              style={{
                backgroundColor: selectedFloor === fl ? WB_THEME.BLUE : 'transparent',
                color: selectedFloor === fl ? 'var(--wb-on-selected)' : WB_THEME.MUTED
              }}
              className="shrink-0 whitespace-nowrap px-2.5 py-1.5 sm:py-1 rounded text-[11px] sm:text-xs font-black transition text-center"
            >
              {FLOOR_LABEL[fl]}
            </button>
          ))}
        </div>

        {/* Period Segment: Today | Yesterday | Week | Month */}
        <div
          style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
          className="grid grid-cols-4 sm:flex w-full sm:w-auto items-center p-0.5 rounded-md border gap-0.5"
        >
          {(['Today', 'Yesterday', 'Week', 'Month'] as PeriodId[]).map(p => (
            <button
              key={p}
              onClick={() => onSelectPeriod(p)}
              style={{
                backgroundColor: selectedPeriod === p ? WB_THEME.BLUE : 'transparent',
                color: selectedPeriod === p ? 'var(--wb-on-selected)' : WB_THEME.MUTED
              }}
              className="px-2.5 py-1.5 sm:py-1 rounded text-xs font-black transition text-center"
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* ==================== STATUS STRIP ==================== */}
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex w-full sm:w-auto items-center gap-1.5 overflow-x-auto whitespace-nowrap sm:flex-wrap sm:overflow-visible pb-1 sm:pb-0">
          <span className="font-extrabold text-sm mr-1 shrink-0">
            {FLOOR_LABEL[selectedFloor]} ·{' '}
            {page === 'PRODUCTION'
              ? 'Production'
              : page === 'FINISHING'
              ? 'Finishing'
              : page === 'QUALITY'
              ? 'Quality'
              : page === 'ALL'
              ? 'All'
              : 'Table'}{' '}
            · {selectedPeriod}
          </span>
          {selectedPeriod === 'Today' ? (
            <>
              <span
                style={{ backgroundColor: WB_THEME.GREEN, color: 'var(--wb-on-green)' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {onTargetCount} on target
              </span>
              <span
                style={{ backgroundColor: WB_THEME.AMBER, color: 'var(--wb-on-amber)' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {slightBehindCount} slightly behind
              </span>
              <span
                style={{ backgroundColor: WB_THEME.RED, color: 'var(--wb-on-red)' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {farBehindCount} far behind
              </span>
              <span
                style={{ backgroundColor: WB_THEME.GREY, color: 'var(--wb-on-grey)' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {noOutputCount} no output
              </span>
              <span
                style={{ backgroundColor: WB_THEME.DIM, color: WB_THEME.TXT }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {notRunningCount} not running
              </span>
            </>
          ) : (
            <span
              style={{ backgroundColor: WB_THEME.DIM, color: WB_THEME.TXT }}
              className="px-2 py-0.5 rounded font-black text-[11px]"
            >
              Target % shown for Today only
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5">
          <button
            onClick={() => {
              const nextIdx = (intervalIdx + 1) % WB_INTERVALS.length;
              setIntervalIdx(nextIdx);
              setCountdown(WB_INTERVALS[nextIdx] > 0 ? WB_INTERVALS[nextIdx] : WB_INTERVALS[0]);
            }}
            style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
            className="px-2.5 py-1 rounded border flex items-center gap-2 hover:border-amber-400 transition"
          >
            <span style={{ color: WB_THEME.MUTED }} className="text-[11px] font-bold">
              {interval > 0 ? `Auto-slide ${interval}s · next ${countdown}s` : 'Auto-slide paused'}
            </span>
            <div className="flex items-center gap-1">
              {(['PRODUCTION', 'QUALITY', 'FINISHING', 'ALL'] as WbPageId[]).map(p => (
                <span
                  key={p}
                  style={{ backgroundColor: page === p ? WB_THEME.BLUE : WB_THEME.DIM }}
                  className="w-5 h-1.5 rounded-sm inline-block"
                />
              ))}
            </div>
          </button>

          {canFit && (
            <button
              onClick={() => setTvFit(v => !v)}
              title="Scale the whole screen to fill this TV or monitor"
              className="px-2 py-0.5 rounded border text-[11px] font-black"
              style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE, color: tvFit ? WB_THEME.BLUE : WB_THEME.MUTED }}
            >
              {tvFit ? 'TV fit: On' : 'TV fit: Off'}
            </button>
          )}
          {isDemoMode && (
            <button
              onClick={onToggleDemo}
              style={{ backgroundColor: WB_THEME.RED, color: 'var(--wb-on-red)' }}
              className="px-2 py-0.5 rounded font-black text-[11px]"
              title="Click to clear Demo Data"
            >
              DEMO DATA
            </button>
          )}

          <span
            style={{ backgroundColor: WB_THEME.PANEL, color: WB_THEME.GREEN, borderColor: WB_THEME.LINE }}
            className="px-2 py-0.5 rounded border font-black text-[11px]"
          >
            Synced Live
          </span>
        </div>
      </div>

      {/* ==================== FACTORY COMMON STRIP: same on every section ==================== */}
      {(() => {
        const inP = (d: string) => CalcForPeriod.isDateInPeriod(d, todayW, selectedPeriod);
        const all = qualities.filter(q => inP(q.date));
        const checked = all.reduce((a, q) => a + (q.checkedGarments || 0), 0);
        const defective = all.reduce((a, q) => a + (q.defectiveGarments || 0), 0);
        const defects = all.reduce((a, q) => a + (q.totalDefects || 0), 0);
        const oql = checked > 0 ? (defective / checked) * 100 : null;
        const dhu = checked > 0 ? (defects / checked) * 100 : null;
        const pass = oql !== null ? 100 - oql : null;
        const latestW = [...weeklyQuality].filter(w => w.bsOqlPct !== null || w.nflAqcPct !== null).sort((a, b) => (a.weekKey < b.weekKey ? 1 : -1))[0];
        const pc = (v: number | null, good: number, warn: number, lowerBetter: boolean) =>
          v === null ? WB_THEME.GREY : lowerBetter ? (v <= good ? WB_THEME.GREEN : v <= warn ? WB_THEME.AMBER : WB_THEME.RED) : (v >= good ? WB_THEME.GREEN : v >= warn ? WB_THEME.AMBER : WB_THEME.RED);
        return (
          <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
            <WbTile label="Factory QC pass" value={pass !== null ? `${pass.toFixed(1)}%` : '—'} color={pc(pass, 97, 93, false)} sub={`${(checked - defective).toLocaleString()} passed of ${checked.toLocaleString()} checked`} />
            <WbTile label="Factory DHU" value={dhu !== null ? `${dhu.toFixed(2)}%` : '—'} color={pc(dhu, 4, 5.5, true)} sub={`${defects.toLocaleString()} defects`} />
            <WbTile label="Factory OQL" value={oql !== null ? `${oql.toFixed(2)}%` : '—'} color={pc(oql, AUDIT_PASS_OQL_PCT, 7, true)} sub={`limit ${AUDIT_PASS_OQL_PCT}%`} />
            <WbTile label="BS OQL (weekly)" value={latestW && latestW.bsOqlPct !== null ? `${latestW.bsOqlPct.toFixed(2)}%` : '—'} color={pc(latestW?.bsOqlPct ?? null, 4.5, 7, true)} sub={latestW ? `Week ${latestW.weekKey.replace(/^\d{4}-/, '')}` : 'No weekly record yet'} />
            <WbTile label="NFL AQC (weekly)" value={latestW && latestW.nflAqcPct !== null ? `${latestW.nflAqcPct.toFixed(2)}%` : '—'} color={pc(latestW?.nflAqcPct ?? null, 4.5, 7, true)} sub={latestW ? `Week ${latestW.weekKey.replace(/^\d{4}-/, '')}` : 'No weekly record yet'} />
          </div>
        );
      })()}

      {/* ==================== DEPARTMENT PAGE: CUTTING / PRINT / EMBROIDERY ==================== */}
      {isDept && (() => {
        const target = activeLines.reduce((a, l) => a + (l.target || 0), 0);
        const output = activeLines.reduce((a, l) => a + (l.output || 0), 0);
        const ach = target > 0 ? (output / target) * 100 : null;
        const checked = activeLines.reduce((a, l) => a + (l.checkedGarments || 0), 0);
        const defective = activeLines.reduce((a, l) => a + (l.defectiveGarments || 0), 0);
        const passed = Math.max(0, checked - defective);
        const passPct = checked > 0 ? (passed / checked) * 100 : null;
        const reporting = activeLines.filter(l => (l.output ?? 0) > 0).length;
        if (activeLines.length === 0) {
          return (
            <div style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE, color: WB_THEME.MUTED }} className="rounded-lg border p-8 text-center text-sm font-bold">
              No {unit.toLowerCase()} target is set for {selectedFloor} in this period. Set targets in Data entry, Target Setting, {selectedFloor}.
            </div>
          );
        }
        return (
          <div className="space-y-2">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              <WbTile label="Target" value={target.toLocaleString()} unit="pcs" color={WB_THEME.BLUE} sub={`${activeLines.length} ${unit.toLowerCase()}${activeLines.length === 1 ? '' : 's'} with a target`} />
              <WbTile label="Output" value={output.toLocaleString()} unit="pcs" color={achColor(ach)} sub={`${reporting} of ${activeLines.length} reporting`} />
              <WbTile label="Achievement" value={ach !== null ? `${ach.toFixed(1)}%` : '—'} color={achColor(ach)} sub={`Gap ${(output - target).toLocaleString()} pcs`} />
              {hasQc ? (
                <WbTile label="QC Pass rate" value={passPct !== null ? `${passPct.toFixed(1)}%` : '—'} color={passPct === null ? WB_THEME.MUTED : passPct >= 97 ? WB_THEME.GREEN : passPct >= 93 ? WB_THEME.AMBER : WB_THEME.RED} sub={`${passed.toLocaleString()} passed of ${checked.toLocaleString()} checked`} />
              ) : (
                <WbTile label="Open NPT" value={String(openIncidents.length)} color={openIncidents.length > 0 ? WB_THEME.RED : WB_THEME.GREEN} sub="stoppages now" />
              )}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2">
              <WbPanel title="Hourly Target vs Output" sub="By hour of work (1st hr, 2nd hr…) · Target (back) vs Output (front) · pcs" className="lg:col-span-7">
                <HourlyOutputWallboardChart buckets={hourlySeries} />
              </WbPanel>
              <WbPanel title={`${unit} Target vs Achievement`} sub={`Each ${unit.toLowerCase()} · Grey: target · Coloured: output · % below`} className="lg:col-span-5">
                <LiveTargetVsAchievementChart lines={activeLines} onSelectLine={setDeepDiveLine} />
              </WbPanel>
              {hasQc && (
                <WbPanel title="Hourly Checked vs QC Pass" sub="By hour of work · Blue: checked · Green: QC pass · pcs" className="lg:col-span-7">
                  <HourlyQcPassChart buckets={hourlySeries} />
                </WbPanel>
              )}
              <WbPanel
                title="Open NPT"
                sub={`${selectedFloor} stoppages now`}
                className={hasQc ? 'lg:col-span-5' : 'lg:col-span-12'}
                right={<WbBadge text={`${openIncidents.length} open`} bg={openIncidents.length > 0 ? WB_THEME.RED : WB_THEME.GREEN} fg={openIncidents.length > 0 ? 'var(--wb-on-red)' : 'var(--wb-on-green)'} />}
              >
                <OpenStoppagesList incidents={openIncidents} onClick={onOpenIncidentPanel} />
              </WbPanel>
            </div>
          </div>
        );
      })()}

      {isDept && (
        <WbPanel title="Weekly BS OQL vs NFL AQC (from week 32)" sub="Blue: BS OQL · Orange: NFL AQC · Red dashed: 4.5% commitment">
          <WeeklyBsOqlChart records={weeklyQuality} commitment={4.5} />
        </WbPanel>
      )}

      {/* ==================== FINISHING: IRON · PACKING · QUALITY ==================== */}
      {!isDept && page === 'FINISHING' && (() => {
        const sum = (f: (x: FinishingFloorSummary) => number) => finishing.reduce((a, x) => a + f(x), 0);
        const addBuckets = (pick: (x: FinishingFloorSummary) => HourlyWallboardBucket[]) => {
          const len = Math.max(0, ...finishing.map(x => pick(x).length));
          return Array.from({ length: len }, (_, i) => {
            const parts = finishing.map(x => pick(x)[i]).filter(Boolean) as HourlyWallboardBucket[];
            const b = parts[0];
            return {
              ...b,
              target: parts.reduce((a, p) => a + p.target, 0),
              output: parts.reduce((a, p) => a + p.output, 0),
              checked: parts.reduce((a, p) => a + p.checked, 0),
              defective: parts.reduce((a, p) => a + p.defective, 0)
            };
          });
        };
        const ironT = sum(x => x.iron.target), ironO = sum(x => x.iron.output);
        const packT = sum(x => x.packing.target), packO = sum(x => x.packing.output);
        const qcC = sum(x => x.quality.checked), qcP = sum(x => x.quality.passed);
        const pct = (o: number, t: number) => (t > 0 ? (o / t) * 100 : null);
        const many = finishing.length > 1;
        const floorBars = (pick: (x: FinishingFloorSummary) => { target: number; output: number }) =>
          finishing.map(x => {
            const v = pick(x);
            return { lineId: x.floor, target: v.target, output: v.output, achievementPct: pct(v.output, v.target) } as unknown as LinePerformanceSummary;
          });
        return (
          <div className="space-y-2">
            <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
              <WbTile label="Iron" value={ironO.toLocaleString()} unit="pcs" color={achColor(pct(ironO, ironT))} sub={ironT > 0 ? `Target ${ironT.toLocaleString()} · ${pct(ironO, ironT)!.toFixed(0)}%` : 'No iron target set'} />
              <WbTile label="Packing" value={packO.toLocaleString()} unit="pcs" color={achColor(pct(packO, packT))} sub={packT > 0 ? `Target ${packT.toLocaleString()} · ${pct(packO, packT)!.toFixed(0)}%` : 'No packing target set'} />
              <WbTile label="Quality: QC Pass" value={qcP.toLocaleString()} unit="pcs" color={qcC === 0 ? WB_THEME.MUTED : qcP / qcC >= 0.97 ? WB_THEME.GREEN : qcP / qcC >= 0.93 ? WB_THEME.AMBER : WB_THEME.RED} sub={qcC > 0 ? `of ${qcC.toLocaleString()} checked · ${((qcP / qcC) * 100).toFixed(1)}% pass` : 'No quality check yet'} />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
              <WbPanel title="Iron: Hourly Target vs Output" sub={`${many ? 'All sewing floors' : finishing[0]?.floor ?? ''} · by hour of work · pcs`}>
                <HourlyOutputWallboardChart buckets={addBuckets(x => x.iron.buckets)} />
              </WbPanel>
              <WbPanel title="Packing: Hourly Target vs Output" sub={`${many ? 'All sewing floors' : finishing[0]?.floor ?? ''} · by hour of work · pcs`}>
                <HourlyOutputWallboardChart buckets={addBuckets(x => x.packing.buckets)} />
              </WbPanel>
              <WbPanel title="Quality: Hourly Checked vs QC Pass" sub="From the line-wise hourly quality entries · Blue: checked · Green: pass">
                <HourlyQcPassChart buckets={addBuckets(x => x.quality.buckets)} />
              </WbPanel>
              {many && (
                <>
                  <WbPanel title="Iron by Floor" sub="Grey: target · Coloured: output · % below">
                    <LiveTargetVsAchievementChart lines={floorBars(x => x.iron)} onSelectLine={() => {}} />
                  </WbPanel>
                  <WbPanel title="Packing by Floor" sub="Grey: target · Coloured: output · % below">
                    <LiveTargetVsAchievementChart lines={floorBars(x => x.packing)} onSelectLine={() => {}} />
                  </WbPanel>
                  <WbPanel title="QC Pass by Floor" sub="Pass rate of the checked pieces">
                    <HorizontalRankBars
                      rows={finishing.map(x => ({
                        label: x.floor,
                        value: x.quality.checked > 0 ? (x.quality.passed / x.quality.checked) * 100 : 0,
                        text: x.quality.checked > 0 ? `${((x.quality.passed / x.quality.checked) * 100).toFixed(1)}% (${x.quality.passed}/${x.quality.checked})` : '—',
                        color: x.quality.checked === 0 ? WB_THEME.GREY : x.quality.passed / x.quality.checked >= 0.97 ? WB_THEME.GREEN : x.quality.passed / x.quality.checked >= 0.93 ? WB_THEME.AMBER : WB_THEME.RED
                      }))}
                      maxVal={100}
                    />
                  </WbPanel>
                </>
              )}
            </div>
          </div>
        );
      })()}

      {/* ==================== FACTORY: department strip above the sewing pages ==================== */}
      {selectedFloor === 'Factory' && departmentSummaries.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 mb-2">
          {departmentSummaries.map(d => (
            <button
              key={d.floor}
              onClick={() => onSelectFloor(d.floor as FloorId)}
              style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
              className="rounded-lg border p-2.5 text-left"
              title={`Open ${d.floor}`}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-black">{d.floor}</span>
                <span style={{ color: achColor(d.achievementPct) }} className="text-sm font-black">
                  {d.achievementPct !== null ? `${d.achievementPct.toFixed(0)}%` : '—'}
                </span>
              </div>
              <div style={{ color: WB_THEME.MUTED }} className="text-[11px] font-semibold">
                {d.units > 0 ? `Output ${d.output.toLocaleString()} / Target ${d.target.toLocaleString()} pcs` : 'No target set'}
                {d.floor !== 'Cutting' && d.checked > 0 ? ` · QC pass ${d.passed.toLocaleString()} of ${d.checked.toLocaleString()}` : ''}
              </div>
            </button>
          ))}
        </div>
      )}

      {/* ==================== PAGE 1: PRODUCTION ==================== */}
      {!isDept && page === 'PRODUCTION' && (
        <div className="flex-1 flex flex-col gap-2">
          {/* 5 KPI Tiles */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
            <WbTile
              label={`EFFICIENCY · target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              value={kpis.reportedLinesCount > 0 ? `${kpis.todayEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.reportedLinesCount > 0 ? kpis.todayEfficiencyPct : null, kpis.targetEfficiencyPct)}
              sub={`Month ${kpis.monthlyEfficiencyPct.toFixed(1)}% · Δ ${kpis.monthlyEfficiencyDelta >= 0 ? '+' : ''}${kpis.monthlyEfficiencyDelta.toFixed(1)} pp`}
            />
            <WbTile
              label="OUTPUT vs TARGET"
              value={kpis.todayOutputPcs.toLocaleString()}
              unit={`/ ${kpis.todayTargetPcs.toLocaleString()} pcs`}
              color={achColor(kpis.achievementPct)}
              sub={`${kpis.achievementPct.toFixed(0)}% of target · gap ${(kpis.todayOutputPcs - kpis.todayTargetPcs >= 0 ? '+' : '')}${(kpis.todayOutputPcs - kpis.todayTargetPcs).toLocaleString()} pcs`}
            />
            <WbTile
              label="LINES REPORTED"
              value={`${kpis.reportedLinesCount}`}
              unit={`/ ${kpis.totalFloorLinesCount}`}
              color={kpis.reportedLinesCount === kpis.totalFloorLinesCount && kpis.totalFloorLinesCount > 0 ? WB_THEME.GREEN : WB_THEME.AMBER}
              sub={`${noOutputCount} lines with no output · ${notRunningCount} not running`}
            />
            <WbTile
              label="DOWNTIME (elapsed)"
              value={`${kpis.downtimeMinutes}`}
              unit="min"
              color={kpis.openIncidentsCount > 0 ? WB_THEME.RED : kpis.downtimeMinutes > 0 ? WB_THEME.AMBER : WB_THEME.GREEN}
              sub={`${kpis.openIncidentsCount} open · ${allIncidents.length} total today`}
            />
            <WbTile
              label="OPERATORS / MACHINES"
              value={`${kpis.operatorsCount} / ${kpis.machinesCount}`}
              color={WB_THEME.BLUE}
              sub={`Helpers ${kpis.helpersCount} · Ratio ${kpis.machinesCount > 0 ? (kpis.operatorsCount / kpis.machinesCount).toFixed(2) : '0.00'}`}
            />
          </div>

          {/* Row 1 Panels */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1 min-h-[220px]">
            <WbPanel
              title="Live Target vs Achievement"
              sub="Column: target (grey) vs output (status) · % below"
              right={
                <div className="flex gap-1">
                  <WbBadge text="≥95%" bg={WB_THEME.GREEN} fg="var(--wb-on-green)" />
                  <WbBadge text="80-95%" bg={WB_THEME.AMBER} fg="var(--wb-on-amber)" />
                  <WbBadge text="<80%" bg={WB_THEME.RED} fg="var(--wb-on-red)" />
                </div>
              }
              className="lg:col-span-5"
            >
              <LiveTargetVsAchievementChart lines={activeLines} onSelectLine={setDeepDiveLine} />
            </WbPanel>

            <WbPanel
              title="Efficiency by Line"
              sub={`Front: ${selectedPeriod} · Back: Month · Dashed: target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              className="lg:col-span-4"
            >
              <EfficiencyByLineChart lines={activeLines} targetEff={kpis.targetEfficiencyPct} onSelectLine={setDeepDiveLine} />
            </WbPanel>

            <WbPanel
              title="Lowest Efficiency Lines"
              sub="Action focus · vs line target"
              className="lg:col-span-3"
            >
              <HorizontalRankBars
                rows={[...activeLines]
                  .filter(l => l.efficiencyPct !== null)
                  .sort((a, b) => (a.efficiencyPct ?? 0) - (b.efficiencyPct ?? 0))
                  .slice(0, 5)
                  .map(l => ({
                    label: l.lineId,
                    value: l.efficiencyPct ?? 0,
                    text: `${(l.efficiencyPct ?? 0).toFixed(0)}%`,
                    color: effColor(l.efficiencyPct, l.targetEfficiencyPct)
                  }))}
                maxVal={100}
              />
            </WbPanel>
          </div>

          {/* Row 2 Panels */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
            <WbPanel
              title="Hourly Output vs Target"
              sub="By hour of work (1st hr, 2nd hr…) · Target (back) vs Output (front)"
              className="lg:col-span-6"
            >
              <HourlyOutputWallboardChart buckets={hourlySeries} />
            </WbPanel>

            <WbPanel
              title="Downtime by Cause (min)"
              sub={`Total ${kpis.downtimeMinutes} min`}
              className="lg:col-span-3"
            >
              <HorizontalRankBars
                rows={downtimeByCause.map(d => ({
                  label: d.cause,
                  value: d.mins,
                  text: `${d.mins}m`,
                  color: WB_THEME.AMBER
                }))}
              />
            </WbPanel>

            <WbPanel
              title="Open Stoppages"
              sub="Lines currently stopped or slowed"
              right={
                <WbBadge
                  text={`${openIncidents.length} open`}
                  bg={openIncidents.length > 0 ? WB_THEME.RED : WB_THEME.GREEN}
                  fg={openIncidents.length > 0 ? 'var(--wb-on-red)' : 'var(--wb-on-green)'}
                />
              }
              className="lg:col-span-3"
            >
              <OpenStoppagesList incidents={openIncidents} onClick={onOpenIncidentPanel} />
            </WbPanel>
          </div>
        </div>
      )}

      {/* ==================== PAGE 2: QUALITY ==================== */}
      {!isDept && (page === 'QUALITY' || page === 'ALL') && (() => {
        const done = auditByLine.reduce((a, r) => a + r.pass + r.fail, 0);
        const passed = auditByLine.reduce((a, r) => a + r.pass, 0);
        const failed = auditByLine.reduce((a, r) => a + r.fail, 0);
        const rate = done > 0 ? (passed / done) * 100 : null;
        const linesAudited = auditByLine.length;
        return (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-2">
            <WbTile label="Audits done" value={String(done)} color={WB_THEME.BLUE} sub={`${linesAudited} line${linesAudited === 1 ? '' : 's'} audited · ${selectedFloor === 'All Sewing' ? 'all sewing' : FLOOR_LABEL[selectedFloor]}`} />
            <WbTile label="Passed" value={String(passed)} color={WB_THEME.GREEN} sub={`OQL at or below ${AUDIT_PASS_OQL_PCT}%`} />
            <WbTile label="Failed" value={String(failed)} color={failed > 0 ? WB_THEME.RED : WB_THEME.GREEN} sub="OQL above the limit" />
            <WbTile label="Pass rate" value={rate !== null ? `${rate.toFixed(1)}%` : '—'} color={rate === null ? WB_THEME.GREY : rate >= 90 ? WB_THEME.GREEN : rate >= 70 ? WB_THEME.AMBER : WB_THEME.RED} sub="of audits done" />
          </div>
        );
      })()}

      {!isDept && page === 'QUALITY' && (
        <div className="flex-1 flex flex-col gap-2">
          {/* 5 KPI Tiles */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
            <WbTile
              label={`DHU · limit ${kpis.dhuLimitPct.toFixed(1)}`}
              value={kpis.checkedGarments > 0 ? kpis.todayDhuPct.toFixed(2) : '—'}
              color={dhuColor(kpis.checkedGarments > 0 ? kpis.todayDhuPct : null, kpis.dhuLimitPct)}
              sub={`${kpis.totalDefects} defects / ${kpis.checkedGarments} checked`}
            />
            <WbTile
              label={`OQL · commitment ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              value={kpis.checkedGarments > 0 ? `${kpis.todayOqlPct.toFixed(2)}%` : '—'}
              color={oqlColor(kpis.checkedGarments > 0 ? kpis.todayOqlPct : null, kpis.oqlCommitmentPct)}
              sub={`BS QA OQL ${kpis.bsQaOqlPct.toFixed(2)}% · FRI Pass ${kpis.friPassRatePct.toFixed(1)}%`}
            />
            <WbTile
              label="FIRST-TIME RIGHT (derived)"
              value={
                kpis.checkedGarments > 0
                  ? `${Math.max(0, 100 - (kpis.defectiveGarments / kpis.checkedGarments) * 100).toFixed(1)}%`
                  : '—'
              }
              color={WB_THEME.GREEN}
              sub={`${Math.max(0, kpis.checkedGarments - kpis.defectiveGarments)} clean / ${kpis.checkedGarments} checked`}
            />
            <WbTile
              label="DEFECTS PENDING"
              value={`${kpis.defectsPending}`}
              unit="pcs"
              color={kpis.defectsPending > 15 ? WB_THEME.RED : kpis.defectsPending > 0 ? WB_THEME.AMBER : WB_THEME.GREEN}
              sub={`Sewing ${kpis.sewPending} (sent ${kpis.sewSent}) · Spot ${kpis.spotPending} (sent ${kpis.spotSent})`}
            />
            <WbTile
              label="CHECKED"
              value={kpis.checkedGarments.toLocaleString()}
              unit="pcs"
              color={WB_THEME.BLUE}
              sub={`${kpis.defectiveGarments} defective pcs · ${kpis.totalDefects} total defects`}
            />
          </div>

          {/* Row 1 Panels */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1 min-h-[220px]">
            <WbPanel
              title="DHU by Line"
              sub={`Dashed: limit ${kpis.dhuLimitPct.toFixed(1)}`}
              className="lg:col-span-4"
            >
              <MetricByLineBarChart
                lines={activeLines}
                getValue={l => l.dhu}
                getColor={v => dhuColor(v, kpis.dhuLimitPct)}
                formatVal={v => v.toFixed(1)}
                limitVal={kpis.dhuLimitPct}
                onSelectLine={setDeepDiveLine}
              />
            </WbPanel>

            <WbPanel
              title="OQL by Line"
              sub={`Dashed: commitment ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              className="lg:col-span-4"
            >
              <MetricByLineBarChart
                lines={activeLines}
                getValue={l => l.todayOqlPct}
                getColor={v => oqlColor(v, kpis.oqlCommitmentPct)}
                formatVal={v => `${v.toFixed(1)}%`}
                limitVal={kpis.oqlCommitmentPct}
                onSelectLine={setDeepDiveLine}
              />
            </WbPanel>

            <WbPanel
              title="Top Defects (Pareto)"
              sub="Count and DHU contribution"
              className="lg:col-span-4"
            >
              <HorizontalRankBars
                rows={topDefects.map(d => ({
                  label: d.defect,
                  value: d.count,
                  text: `${d.count} (${d.dhu.toFixed(2)})`,
                  color: WB_THEME.RED
                }))}
              />
            </WbPanel>
          </div>

          {/* Row 2 Panels */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
            <WbPanel
              title="Hourly DHU and OQL"
              sub="By hour of work (1st hr, 2nd hr…) · Orange: DHU · Blue: OQL%"
              className="lg:col-span-5"
            >
              <HourlyQualityLineChart buckets={hourlySeries} limit={kpis.oqlCommitmentPct} />
            </WbPanel>

            <WbPanel
              title="Defect Rectification Pending"
              sub="Unreturned pcs · Orange: Sewing · Blue: Spot"
              right={
                <WbBadge
                  text={`${kpis.defectsPending} pcs pending`}
                  bg={kpis.defectsPending > 0 ? WB_THEME.AMBER : WB_THEME.GREEN}
                  fg="var(--wb-on-green)"
                />
              }
              className="lg:col-span-4"
            >
              <RectificationPendingChart lines={activeLines} onSelectLine={setDeepDiveLine} />
            </WbPanel>

            <WbPanel
              title="Main Defect, Worst Lines"
              sub={`Top contributor: ${mainDefectName}`}
              className="lg:col-span-3"
            >
              <HorizontalRankBars
                rows={
                  mainDefectWorstLines.length > 0
                    ? mainDefectWorstLines.map(r => ({
                        label: r.line,
                        value: r.count,
                        text: `${r.count}`,
                        color: WB_THEME.ORANGE
                      }))
                    : highestDhuLines.map(r => ({
                        label: r.line,
                        value: r.dhu,
                        text: r.dhu.toFixed(1),
                        color: WB_THEME.ORANGE
                      }))
                }
              />
            </WbPanel>
          </div>
        </div>
      )}

      {/* ==================== PAGE 3: ALL (EXECUTIVE COMBINED WALLBOARD) ==================== */}
      {!isDept && page === 'QUALITY' && (
        <WbPanel title="Weekly BS OQL vs NFL AQC (from week 32)" sub="Blue: BS OQL · Orange: NFL AQC · Red dashed: 4.5% commitment">
          <WeeklyBsOqlChart records={weeklyQuality} commitment={4.5} />
        </WbPanel>
      )}

      {!isDept && page === 'ALL' && (
        <div className="flex-1 flex flex-col gap-2">
          {/* Top 7 Summary Panels */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-7 gap-2">
            {/* 1. Today Live Efficiency Gauge */}
            <WbSummaryCard
              title="Today Live Efficiency"
              badge={kpis.todayEfficiencyPct >= kpis.targetEfficiencyPct ? 'GOOD' : 'LOW'}
              badgeColor={effColor(kpis.todayEfficiencyPct, kpis.targetEfficiencyPct)}
            >
              <SemicircleGauge
                value={kpis.todayEfficiencyPct}
                max={100}
                label={`${kpis.todayEfficiencyPct.toFixed(1)}%`}
                color={effColor(kpis.todayEfficiencyPct, kpis.targetEfficiencyPct)}
                sub={`Target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              />
            </WbSummaryCard>

            {/* 2. Monthly Efficiency */}
            <WbSummaryCard
              title="Monthly Efficiency"
              badge={`${kpis.monthlyEfficiencyDelta >= 0 ? '+' : ''}${kpis.monthlyEfficiencyDelta.toFixed(1)} pp`}
              badgeColor={kpis.monthlyEfficiencyDelta >= 0 ? WB_THEME.GREEN : WB_THEME.RED}
            >
              <div className="flex flex-col justify-between h-full pt-1">
                <div style={{ color: WB_THEME.BLUE }} className="text-3xl font-black">
                  {kpis.monthlyEfficiencyPct.toFixed(1)}%
                </div>
                <div className="w-full h-1.5 rounded bg-[var(--wb-bg)] overflow-hidden my-1">
                  <div
                    style={{
                      width: `${Math.min(100, kpis.monthlyEfficiencyPct)}%`,
                      backgroundColor: WB_THEME.BLUE
                    }}
                    className="h-full"
                  />
                </div>
                <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-bold">
                  Target: {kpis.targetEfficiencyPct.toFixed(0)}%
                </div>
              </div>
            </WbSummaryCard>

            {/* 3. Operator vs Machine */}
            <WbSummaryCard title="Operator vs Machine">
              <div className="flex items-center justify-between h-full gap-2">
                <div>
                  <div className="text-xl font-black">
                    {kpis.operatorsCount}/{kpis.machinesCount}
                  </div>
                  <div style={{ color: WB_THEME.ORANGE }} className="text-[10px] font-bold">
                    ● Ops {kpis.operatorsCount}
                  </div>
                  <div style={{ color: WB_THEME.BLUE }} className="text-[10px] font-bold">
                    ● Mach {kpis.machinesCount}
                  </div>
                </div>
                <div className="w-12 h-12 rounded-full border-4 border-[#5AA9FF] flex items-center justify-center text-[10px] font-black">
                  {kpis.machinesCount > 0 ? (kpis.operatorsCount / kpis.machinesCount).toFixed(1) : '1.0'}x
                </div>
              </div>
            </WbSummaryCard>

            {/* 4. Floor & Factory Efficiency */}
            <WbSummaryCard title="Floor & Factory Efficiency (Today / Month)">
              {floorBenchmarks.length === 0 || floorBenchmarks.every(b => !b.todayEff && !b.monthEff) ? (
                <div style={{ color: WB_THEME.MUTED }} className="text-[11px] font-semibold text-center py-3">
                  No data yet
                </div>
              ) : (
                <div className="flex items-end justify-around gap-1 pt-1">
                  {floorBenchmarks.map(b => (
                    <div key={b.floor} className="flex flex-col items-center gap-0.5">
                      <div className="flex items-end gap-1 h-14">
                        {[
                          { v: b.todayEff, c: WB_THEME.BLUE, tip: 'Today' },
                          { v: b.monthEff, c: WB_THEME.GREEN, tip: 'Month' }
                        ].map(bar => (
                          <div key={bar.tip} className="flex flex-col items-center justify-end h-full">
                            <span style={{ color: bar.c }} className="text-[11px] font-black leading-none mb-0.5">
                              {Math.round(bar.v)}
                            </span>
                            <div
                              style={{ height: `${Math.min(100, Math.max(6, bar.v)) * 0.62}%`, backgroundColor: bar.c }}
                              className="w-3 rounded-t-sm"
                              title={`${bar.tip}: ${bar.v}%`}
                            />
                          </div>
                        ))}
                      </div>
                      <span style={{ color: WB_THEME.MUTED }} className="text-[9px] font-bold">
                        {b.floor}
                      </span>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex justify-center gap-3 pt-1 text-[9px] font-bold" style={{ color: WB_THEME.MUTED }}>
                <span><span style={{ backgroundColor: WB_THEME.BLUE }} className="inline-block w-2 h-2 rounded-sm mr-1" />Today</span>
                <span><span style={{ backgroundColor: WB_THEME.GREEN }} className="inline-block w-2 h-2 rounded-sm mr-1" />Month</span>
              </div>
            </WbSummaryCard>

            {/* 5. BS QA OQL */}
            <WbSummaryCard
              title="BS QA OQL"
              badge={kpis.bsQaOqlPct <= kpis.oqlCommitmentPct ? 'PASS' : 'HIGH'}
              badgeColor={oqlColor(kpis.bsQaOqlPct, kpis.oqlCommitmentPct)}
            >
              <div className="flex flex-col justify-between h-full pt-1">
                <div
                  style={{ color: oqlColor(kpis.bsQaOqlPct, kpis.oqlCommitmentPct) }}
                  className="text-3xl font-black"
                >
                  {kpis.bsQaOqlPct.toFixed(2)}%
                </div>
                <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-bold">
                  Target ≤ {kpis.oqlCommitmentPct.toFixed(1)}%
                </div>
              </div>
            </WbSummaryCard>

            {/* 6. Today Live OQL Gauge */}
            <WbSummaryCard
              title="Today Live OQL"
              badge={kpis.todayOqlPct <= kpis.oqlCommitmentPct ? 'STANDARD' : 'HIGH'}
              badgeColor={oqlColor(kpis.todayOqlPct, kpis.oqlCommitmentPct)}
            >
              <SemicircleGauge
                value={kpis.todayOqlPct}
                max={12}
                label={`${kpis.todayOqlPct.toFixed(2)}%`}
                color={oqlColor(kpis.todayOqlPct, kpis.oqlCommitmentPct)}
                sub={`Limit ≤ ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              />
            </WbSummaryCard>

            {/* 7. BS QA FRI Pass */}
            <WbSummaryCard
              title="BS QA FRI Pass"
              badge={kpis.friPassRatePct >= 95 ? 'EXCELLENT' : 'WATCH'}
              badgeColor={kpis.friPassRatePct >= 95 ? WB_THEME.GREEN : WB_THEME.AMBER}
            >
              <div className="flex flex-col justify-between h-full pt-1">
                <div style={{ color: WB_THEME.GREEN }} className="text-3xl font-black">
                  {kpis.friPassRatePct.toFixed(1)}%
                </div>
                <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-bold">
                  Final Random Inspection
                </div>
              </div>
            </WbSummaryCard>
          </div>

          {/* Row 1 Panels */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
            <WbPanel
              title="Live Target vs Achievement"
              sub="Grey: Target · Colored: Output · % below"
              className="lg:col-span-5"
            >
              <LiveTargetVsAchievementChart lines={activeLines} onSelectLine={setDeepDiveLine} />
            </WbPanel>

            <WbPanel
              title="OQL by Line"
              sub={`Dashed: commitment ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              className="lg:col-span-4"
            >
              <MetricByLineBarChart
                lines={activeLines}
                getValue={l => l.todayOqlPct}
                getColor={v => oqlColor(v, kpis.oqlCommitmentPct)}
                formatVal={v => `${v.toFixed(1)}%`}
                limitVal={kpis.oqlCommitmentPct}
                onSelectLine={setDeepDiveLine}
              />
            </WbPanel>

            <WbPanel
              title="Top 5 Defects (DHU)"
              sub={`Checked ${kpis.checkedGarments} pcs · Total ${kpis.totalDefects} defects`}
              className="lg:col-span-3"
            >
              <HorizontalRankBars
                rows={topDefects.map(d => ({
                  label: d.defect,
                  value: d.dhu,
                  text: `${d.dhu.toFixed(2)} (${d.count})`,
                  color: WB_THEME.RED
                }))}
              />
            </WbPanel>
          </div>

          {/* Row 2 Panels */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
            <WbPanel
              title="Efficiency by Line"
              sub={`Front: ${selectedPeriod} · Back: Month · Dashed: target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              className="lg:col-span-5"
            >
              <EfficiencyByLineChart lines={activeLines} targetEff={kpis.targetEfficiencyPct} onSelectLine={setDeepDiveLine} />
            </WbPanel>

            <WbPanel
              title="Weekly BS OQL and NFL AQC"
              sub="Weekly trend (%) · Dashed: 4.5% commitment · Level 1 <7% · Red >10%"
              right={
                <div className="flex gap-1">
                  <WbBadge text="BS OQL" bg={WB_THEME.BLUE} fg="var(--wb-on-selected)" />
                  <WbBadge text="NFL AQC" bg={WB_THEME.ORANGE} fg="var(--wb-on-orange)" />
                </div>
              }
              className="lg:col-span-4"
            >
              <WeeklyBsOqlChart records={sortedWeekly} commitment={kpis.oqlCommitmentPct} />
            </WbPanel>

            <WbPanel
              title="Poor 5 DHU Lines"
              sub={`Highest defect rate · limit ${kpis.dhuLimitPct.toFixed(1)}`}
              className="lg:col-span-3"
            >
              <HorizontalRankBars
                rows={highestDhuLines.map(r => ({
                  label: r.line,
                  value: r.dhu,
                  text: r.dhu.toFixed(2),
                  color: dhuColor(r.dhu, kpis.dhuLimitPct)
                }))}
              />
            </WbPanel>
          </div>
        </div>
      )}

      {/* ==================== PAGE 4: TABLE (15-COLUMN LIVE LINE PERFORMANCE TABLE) ==================== */}
      {!isDept && page === 'TABLE' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-1.5 mb-1.5">
          <div style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }} className="rounded-lg border p-2 lg:col-span-7">
            <div className="text-[11px] font-black">Audit: Pass / Fail by Line</div>
            <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-semibold mb-1">Bi-hourly OQL audits · PASS when OQL is at or below {AUDIT_PASS_OQL_PCT}%</div>
            <div className="h-20"><AuditPassFailChart rows={auditByLine} /></div>
          </div>
          <div style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }} className="rounded-lg border p-2 lg:col-span-5">
            <div className="text-[11px] font-black">Audit results by Line</div>
            <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-semibold mb-1">Passed and failed audits in this period</div>
            <div className="max-h-[92px] overflow-auto">
              <table className="w-full text-xs border-collapse">
                <thead className="sticky top-0" style={{ backgroundColor: WB_THEME.BG }}>
                  <tr className="text-left" style={{ color: WB_THEME.MUTED }}>
                    <th className="p-2">Line</th><th className="p-2 text-right">Pass</th><th className="p-2 text-right">Fail</th><th className="p-2 text-right">Pass rate</th>
                  </tr>
                </thead>
                <tbody>
                  {auditByLine.length === 0 && (<tr><td colSpan={4} className="p-3 text-center" style={{ color: WB_THEME.MUTED }}>No audits yet</td></tr>)}
                  {auditByLine.map(r => {
                    const tot = r.pass + r.fail;
                    const rate = tot > 0 ? (r.pass / tot) * 100 : 0;
                    return (
                      <tr key={r.lineId} className="border-t" style={{ borderColor: WB_THEME.LINE }}>
                        <td className="p-2 font-black">{lineDisplayName(r.lineId)}</td>
                        <td className="p-2 text-right font-black" style={{ color: WB_THEME.GREEN }}>{r.pass}</td>
                        <td className="p-2 text-right font-black" style={{ color: WB_THEME.RED }}>{r.fail}</td>
                        <td className="p-2 text-right font-black" style={{ color: rate >= 90 ? WB_THEME.GREEN : rate >= 70 ? WB_THEME.AMBER : WB_THEME.RED }}>{rate.toFixed(0)}%</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
      {!isDept && page === 'TABLE' && (
        <div className="flex-1 min-h-[72vh] bg-slate-100 text-slate-900 rounded-lg overflow-auto p-2">
          <div className="flex items-center justify-between mb-1">
            <div>
              <h3 className="text-sm font-black text-[#002D62]">
                Live Line Performance Table ({FLOOR_LABEL[selectedFloor]} · {selectedPeriod})
              </h3>
              <p className="text-[11px] text-slate-500">
                Click any line row to open its Line Deep Dive &amp; Rectification Ledger
              </p>
            </div>
          </div>
          <table className="w-full text-xs border-collapse bg-white rounded-lg overflow-hidden shadow">
            <thead className="sticky top-0 z-10">
              <tr className="bg-[#002D62] text-white text-[11px] font-black">
                <th className="py-2 px-2 text-left">Line</th>
                <th className="py-2 px-2 text-left">Item</th>
                <th className="py-2 px-2 text-right">SMV</th>
                <th className="py-2 px-2 text-right">Hourly Tgt</th>
                <th className="py-2 px-2 text-right">Day Tgt</th>
                <th className="py-2 px-2 text-right">Day Output</th>
                <th className="py-2 px-2 text-right">Efficiency</th>
                <th className="py-2 px-2 text-right">Achieve %</th>
                <th className="py-2 px-2 text-right">Day DHU</th>
                <th className="py-2 px-2 text-right">Day OQL</th>
                <th className="py-2 px-2 text-right bg-amber-900/60">Sew Sent</th>
                <th className="py-2 px-2 text-right bg-amber-900/60">Sew Recv</th>
                <th className="py-2 px-2 text-right bg-amber-900/60">Sew Bal</th>
                <th className="py-2 px-2 text-right bg-sky-900/60">Spot Sent</th>
                <th className="py-2 px-2 text-right bg-sky-900/60">Spot Recv</th>
                <th className="py-2 px-2 text-right bg-sky-900/60">Spot Bal</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 font-semibold">
              {lineSummaries.map(l => (
                <tr
                  key={l.lineId}
                  onClick={() => setDeepDiveLine(l)}
                  className="hover:bg-blue-50 cursor-pointer transition"
                >
                  <td className="py-1.5 px-2 font-black text-[#002D62]">{lineDisplayName(l.lineId)}</td>
                  <td className="py-1.5 px-2 text-slate-700">{l.styleNumber}</td>
                  <td className="py-1.5 px-2 text-right">{l.smv > 0 ? l.smv.toFixed(1) : '—'}</td>
                  <td className="py-1.5 px-2 text-right">{l.hourlyTarget || '—'}</td>
                  <td className="py-1.5 px-2 text-right">{l.target || '—'}</td>
                  <td className="py-1.5 px-2 text-right font-black">
                    {l.output !== null ? l.output : '—'}
                  </td>
                  <td
                    className={`py-1.5 px-2 text-right font-black ${
                      l.efficiencyPct === null
                        ? 'text-slate-400'
                        : l.efficiencyPct >= l.targetEfficiencyPct
                        ? 'text-emerald-600'
                        : 'text-red-600'
                    }`}
                  >
                    {l.efficiencyPct !== null ? `${l.efficiencyPct.toFixed(1)}%` : '—'}
                  </td>
                  <td
                    className={`py-1.5 px-2 text-right font-black ${
                      l.achievementPct === null
                        ? 'text-slate-400'
                        : l.achievementPct >= 95
                        ? 'text-emerald-600'
                        : l.achievementPct >= 80
                        ? 'text-amber-600'
                        : 'text-red-600'
                    }`}
                  >
                    {l.achievementPct !== null ? `${l.achievementPct.toFixed(0)}%` : '—'}
                  </td>
                  <td className="py-1.5 px-2 text-right font-bold">
                    {l.dhu !== null ? `${l.dhu.toFixed(2)}%` : '—'}
                  </td>
                  <td className="py-1.5 px-2 text-right font-bold">
                    {l.todayOqlPct !== null ? `${l.todayOqlPct.toFixed(2)}%` : '—'}
                  </td>
                  <td className="py-1.5 px-2 text-right">{l.sewSent}</td>
                  <td className="py-1.5 px-2 text-right">{l.sewRecv}</td>
                  <td className={`py-1.5 px-2 text-right font-black ${l.sewBal > 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                    {l.sewBal}
                  </td>
                  <td className="py-1.5 px-2 text-right">{l.spotSent}</td>
                  <td className="py-1.5 px-2 text-right">{l.spotRecv}</td>
                  <td className={`py-1.5 px-2 text-right font-black ${l.spotBal > 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                    {l.spotBal}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ==================== LINE DEEP DIVE MODAL ==================== */}
      {deepDiveLine && (
        <div className="fixed inset-0 bg-black/75 z-50 flex items-center justify-center p-4">
          <div
            style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE, color: WB_THEME.TXT }}
            className="w-full max-w-4xl max-h-[90vh] rounded-xl border p-5 flex flex-col gap-4 overflow-y-auto shadow-2xl"
          >
            <div className="flex items-start gap-3 border-b pb-3" style={{ borderColor: WB_THEME.LINE }}>
              <div className="min-w-0 flex-1">
                <h3 className="text-lg font-black leading-tight">
                  {lineDisplayName(deepDiveLine.lineId)} <span style={{ color: WB_THEME.MUTED }} className="text-sm font-bold">({deepDiveLine.floor})</span>
                </h3>
                <p style={{ color: WB_THEME.MUTED }} className="text-xs mt-0.5">
                  {deepDiveLine.styleNumber ? `${deepDiveLine.styleNumber} · ` : ''}SMV {deepDiveLine.smv.toFixed(1)} · Operators {deepDiveLine.operators} · Helpers {deepDiveLine.helpers} · Hourly target {deepDiveLine.hourlyTarget} pcs
                </p>
              </div>
              <button
                onClick={() => setDeepDiveLine(null)}
                className="shrink-0 flex items-center gap-1.5 px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white font-black text-sm"
                aria-label="Close"
              >
                ✕ Close
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
              <WbTile
                label="EFFICIENCY"
                value={deepDiveLine.efficiencyPct !== null ? `${deepDiveLine.efficiencyPct.toFixed(1)}%` : '—'}
                color={effColor(deepDiveLine.efficiencyPct, deepDiveLine.targetEfficiencyPct)}
                sub={`Target ${deepDiveLine.targetEfficiencyPct}%`}
              />
              <WbTile
                label="OUTPUT vs TARGET"
                value={`${deepDiveLine.output ?? 0}`}
                unit={`/ ${deepDiveLine.target}`}
                color={achColor(deepDiveLine.achievementPct)}
                sub={`Achieve ${deepDiveLine.achievementPct ?? 0}%`}
              />
              <WbTile
                label="DAY DHU %"
                value={deepDiveLine.dhu !== null ? `${deepDiveLine.dhu.toFixed(2)}%` : '—'}
                color={dhuColor(deepDiveLine.dhu)}
                sub={`${deepDiveLine.totalDefects} defects / ${deepDiveLine.checkedGarments} chk`}
              />
              <WbTile
                label="DAY OQL %"
                value={deepDiveLine.todayOqlPct !== null ? `${deepDiveLine.todayOqlPct.toFixed(2)}%` : '—'}
                color={oqlColor(deepDiveLine.todayOqlPct)}
                sub={`${deepDiveLine.defectiveGarments} defective pcs`}
              />
              <WbTile
                label="ALTER & SPOT PENDING"
                value={`${deepDiveLine.sewBal + deepDiveLine.spotBal}`}
                unit="pcs"
                color={deepDiveLine.sewBal + deepDiveLine.spotBal > 0 ? WB_THEME.AMBER : WB_THEME.GREEN}
                sub={`Sew Bal ${deepDiveLine.sewBal} · Spot Bal ${deepDiveLine.spotBal}`}
              />
            </div>

            <div className="text-xs font-black uppercase tracking-wider" style={{ color: WB_THEME.MUTED }}>
              Hourly &amp; Bi-Hourly Records (scroll up and down)
            </div>
            <div className="max-h-[48vh] overflow-auto rounded-lg border" style={{ borderColor: WB_THEME.LINE }}>
              <table className="w-full min-w-[860px] text-sm border-collapse">
                <thead className="sticky top-0 z-10">
                  <tr style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.MUTED }} className="text-xs uppercase tracking-wide">
                    <th className="p-2.5 text-left">Hour</th>
                    <th className="p-2.5 text-left">Type</th>
                    <th className="p-2.5 text-right">Output</th>
                    <th className="p-2.5 text-right">Checked</th>
                    <th className="p-2.5 text-right">Defective</th>
                    <th className="p-2.5 text-center">Result</th>
                    <th className="p-2.5 text-right">Defects</th>
                    <th className="p-2.5 text-right">Sew S / R</th>
                    <th className="p-2.5 text-right">Spot S / R</th>
                  </tr>
                </thead>
                <tbody>
                  {productions
                    .filter(p => p.lineId === deepDiveLine.lineId)
                    .slice(0, 40)
                    .map(p => {
                      const matchQa = qualities.find(q => q.lineId === p.lineId && q.date === p.date && q.hourSlot === p.hourSlot);
                      return (
                        <tr key={p.id} className="border-t" style={{ borderColor: WB_THEME.LINE }}>
                          <td className="p-2.5 font-black whitespace-nowrap">{hourLabelOfSlot(p.lineId, p.hourSlot)}</td>
                          <td className="p-2.5 whitespace-nowrap">Production &amp; QC</td>
                          <td className="p-2.5 text-right font-black" style={{ color: WB_THEME.GREEN }}>{p.outputQty}</td>
                          <td className="p-2.5 text-right">{matchQa?.checkedGarments ?? '—'}</td>
                          <td className="p-2.5 text-right">{matchQa?.defectiveGarments ?? '—'}</td>
                          <td className="p-2.5 text-center">—</td>
                          <td className="p-2.5 text-right">{matchQa?.totalDefects ?? '—'}</td>
                          <td className="p-2.5 text-right">{matchQa ? `${matchQa.defectSent || 0} / ${matchQa.defectReceived || 0}` : '—'}</td>
                          <td className="p-2.5 text-right">{matchQa ? `${matchQa.spotSent || 0} / ${matchQa.spotReceived || 0}` : '—'}</td>
                        </tr>
                      );
                    })}
                  {qualities
                    .filter(q => q.lineId === deepDiveLine.lineId && isBiHourlyAuditSlot(q.hourSlot))
                    .map(q => {
                      const r = auditResultOf(q.checkedGarments, q.defectiveGarments);
                      return (
                        <tr key={q.id} className="border-t" style={{ borderColor: WB_THEME.LINE }}>
                          <td className="p-2.5 font-black whitespace-nowrap" style={{ color: WB_THEME.BLUE }}>{hourLabelOfSlot(q.lineId, q.hourSlot)}</td>
                          <td className="p-2.5 font-bold whitespace-nowrap" style={{ color: WB_THEME.BLUE }}>Bi-Hourly Audit (OQL)</td>
                          <td className="p-2.5 text-right">—</td>
                          <td className="p-2.5 text-right font-bold">{q.checkedGarments}</td>
                          <td className="p-2.5 text-right font-bold">{q.defectiveGarments}</td>
                          <td className="p-2.5 text-center">
                            <span className="rounded px-2 py-0.5 text-[11px] font-black" style={{ backgroundColor: r === 'PASS' ? WB_THEME.GREEN : WB_THEME.RED, color: r === 'PASS' ? 'var(--wb-on-green)' : 'var(--wb-on-red)' }}>{r ?? '—'}</span>
                          </td>
                          <td className="p-2.5 text-right font-bold">{q.totalDefects}</td>
                          <td className="p-2.5 text-right">—</td>
                          <td className="p-2.5 text-right">—</td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>

            <div className="text-xs font-black uppercase tracking-wider" style={{ color: WB_THEME.MUTED }}>
              NPT today on this line
            </div>
            <div className="space-y-1.5">
              {allIncidents.filter(i => i.lineId === deepDiveLine.lineId).length === 0 && (
                <div className="text-xs font-bold" style={{ color: WB_THEME.GREEN }}>No NPT on this line.</div>
              )}
              {allIncidents
                .filter(i => i.lineId === deepDiveLine.lineId)
                .sort((a, b) => b.startTimeMillis - a.startTimeMillis)
                .slice(0, 12)
                .map(inc => (
                  <div key={inc.id} className="flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2 text-xs" style={{ borderColor: WB_THEME.LINE, backgroundColor: WB_THEME.BG }}>
                    <span className="font-black">{inc.category.replace(/_/g, ' ').toLowerCase()}</span>
                    <span style={{ color: inc.status === 'RESOLVED' ? WB_THEME.GREEN : WB_THEME.RED }} className="font-black">{inc.status === 'RESOLVED' ? 'Resolved' : 'Open'}</span>
                    <span style={{ color: WB_THEME.MUTED }}>{new Date(inc.startTimeMillis + 6 * 3600 * 1000).toISOString().slice(11, 16)} BD</span>
                    <span className="flex-1 min-w-0 truncate" style={{ color: WB_THEME.MUTED }}>{inc.description}</span>
                  </div>
                ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

/* ==================== SUB-COMPONENTS MATCHING WbComponents.kt ==================== */

const WbTile: React.FC<{
  label: string;
  value: string;
  unit?: string;
  color: string;
  sub: string;
}> = ({ label, value, unit, color, sub }) => (
  <div
    style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
    className="rounded-lg border p-2.5 flex flex-col justify-between relative overflow-hidden"
  >
    <div style={{ backgroundColor: color }} className="absolute left-0 top-0 bottom-0 w-1" />
    <div style={{ color: WB_THEME.MUTED }} className="text-[11px] md:text-xs font-extrabold uppercase tracking-wider pl-1.5">
      {label}
    </div>
    <div className="flex items-baseline gap-1.5 my-1 pl-1.5">
      <span style={{ color }} className="text-2xl md:text-3xl font-black leading-none">
        {value}
      </span>
      {unit && (
        <span style={{ color: WB_THEME.MUTED }} className="text-xs font-bold">
          {unit}
        </span>
      )}
    </div>
    <div style={{ color: WB_THEME.MUTED }} className="text-xs md:text-sm font-semibold pl-1.5 leading-snug">
      {sub}
    </div>
  </div>
);

const WbSummaryCard: React.FC<{
  title: string;
  badge?: string;
  badgeColor?: string;
  children: React.ReactNode;
}> = ({ title, badge, badgeColor, children }) => (
  <div
    style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
    className="rounded-lg border p-2.5 flex flex-col justify-between min-h-[115px]"
  >
    <div className="flex items-start justify-between gap-1">
      <span className="text-[11px] font-extrabold leading-tight">{title}</span>
      {badge && (
        <span
          style={{ backgroundColor: badgeColor || WB_THEME.BLUE, color: 'var(--wb-on-selected)' }}
          className="shrink-0 px-1.5 py-0.5 rounded text-[11px] font-black"
        >
          {badge}
        </span>
      )}
    </div>
    <div className="flex-1 flex flex-col justify-center">{children}</div>
  </div>
);

const WbPanel: React.FC<{
  title: string;
  sub?: string;
  right?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}> = ({ title, sub, right, className = '', children }) => (
  <div
    style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
    className={`rounded-lg border p-2.5 flex flex-col justify-between ${className}`}
  >
    <div className="flex items-start justify-between gap-2 mb-1.5">
      <div>
        <div className="text-xs font-black tracking-wide">{title}</div>
        {sub && (
          <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-semibold">
            {sub}
          </div>
        )}
      </div>
      {right}
    </div>
    <div className="flex-1 flex flex-col justify-end min-h-[200px]">{children}</div>
  </div>
);

const WbBadge: React.FC<{ text: string; bg: string; fg: string }> = ({ text, bg, fg }) => (
  <span style={{ backgroundColor: bg, color: fg }} className="px-1.5 py-0.5 rounded text-[11px] font-black">
    {text}
  </span>
);

const SemicircleGauge: React.FC<{
  value: number;
  max: number;
  label: string;
  color: string;
  sub: string;
}> = ({ value, max, label, color, sub }) => {
  const pct = Math.max(0, Math.min(1, max > 0 ? value / max : 0));
  const dashArray = 125.6;
  const dashOffset = dashArray * (1 - pct);
  return (
    <div className="flex flex-col items-center justify-center">
      <svg viewBox="0 0 100 56" className="w-28 h-14 overflow-visible">
        <path
          d="M 10 50 A 40 40 0 0 1 90 50"
          fill="none"
          stroke={WB_THEME.DIM}
          strokeWidth="10"
          strokeLinecap="round"
        />
        <path
          d="M 10 50 A 40 40 0 0 1 90 50"
          fill="none"
          stroke={color}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={dashArray}
          strokeDashoffset={dashOffset}
        />
        <text x="50" y="48" textAnchor="middle" fill={color} fontSize="15" fontWeight="900">
          {label}
        </text>
      </svg>
      <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-bold -mt-1">
        {sub}
      </div>
    </div>
  );
};

/* ---------- shared column-chart pieces: the scale follows the data, labels sit on top of their bar ---------- */
/** Highest value of the chart plus 18% headroom for the label, so the tallest bar nearly fills the area. */
const niceMax = (vals: number[]): number => Math.max(1e-9, ...vals.filter(v => isFinite(v))) * 1.18 || 1;
const PLOT_H = 'h-44 md:h-52 xl:h-60';

/** One column; its value label sits just above the top of the column. */
const Col: React.FC<{ v: number; max: number; color: string; label?: React.ReactNode; w?: string; title?: string; labelColor?: string }> = ({ v, max, color, label, w = 'w-3 md:w-4', title, labelColor }) => {
  const h = Math.max(0, Math.min(100, (v / max) * 100));
  return (
    <div className={`relative ${w} h-full flex items-end`}>
      {label !== undefined && label !== null && label !== '' && (
        <div className="absolute left-1/2 -translate-x-1/2 whitespace-nowrap text-[11px] md:text-xs font-black leading-none" style={{ bottom: `calc(${h}% + 3px)`, color: labelColor || color }}>
          {label}
        </div>
      )}
      <div style={{ height: `${h}%`, backgroundColor: color }} className="w-full rounded-t-sm" title={title} />
    </div>
  );
};

/** The scrolling row of column groups, with an optional dashed reference line. */
const ChartRow: React.FC<{ children: React.ReactNode; refPct?: number | null; refColor?: string }> = ({ children, refPct, refColor }) => (
  <div className="nfl-scroll overflow-x-auto pb-2">
    <div className="relative flex items-end justify-between gap-1.5 min-w-full pt-5">{children}
      {refPct !== undefined && refPct !== null && (
        <div className={`absolute left-0 right-0 pointer-events-none ${PLOT_H}`} style={{ top: 20 }}>
          <div className="absolute left-0 right-0 border-t-2 border-dashed" style={{ bottom: `${Math.min(100, refPct)}%`, borderColor: refColor || WB_THEME.AMBER }} />
        </div>
      )}
    </div>
  </div>
);

const Group: React.FC<{ children: React.ReactNode; name: React.ReactNode; sub?: React.ReactNode; subColor?: string; onClick?: () => void; minW?: string }> = ({ children, name, sub, subColor, onClick, minW = 'min-w-[44px] md:min-w-[54px]' }) => (
  <div onClick={onClick} className={`flex flex-col items-center flex-1 ${minW} ${onClick ? 'cursor-pointer' : ''}`}>
    <div className={`w-full ${PLOT_H} flex items-end justify-center gap-2 md:gap-3 border-b`} style={{ borderColor: WB_THEME.LINE }}>{children}</div>
    <div className="text-[11px] md:text-xs font-black mt-1 whitespace-nowrap">{name}</div>
    {sub !== undefined && <div style={{ color: subColor }} className="text-[10px] md:text-[11px] font-bold leading-tight">{sub}</div>}
  </div>
);

const LiveTargetVsAchievementChart: React.FC<{
  lines: LinePerformanceSummary[];
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, onSelectLine }) => {
  const max = niceMax(lines.flatMap(l => [l.target, l.output ?? 0]));
  return (
    <ChartRow>
      {lines.map(l => {
        const col = achColor(l.achievementPct);
        return (
          <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)} sub={l.achievementPct !== null ? `${l.achievementPct.toFixed(0)}%` : '—'} subColor={col}>
            <Col v={l.target} max={max} color={WB_THEME.DIM} labelColor={WB_THEME.MUTED} label={l.target} w="w-2.5 md:w-3.5" title={`Target: ${l.target}`} />
            <Col v={l.output ?? 0} max={max} color={col} label={l.output ?? '—'} w="w-3 md:w-4" title={`Output: ${l.output ?? 0}`} />
          </Group>
        );
      })}
    </ChartRow>
  );
};

const EfficiencyByLineChart: React.FC<{
  lines: LinePerformanceSummary[];
  targetEff: number;
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, targetEff, onSelectLine }) => {
  const max = niceMax([targetEff, ...lines.flatMap(l => [l.efficiencyPct ?? 0, l.monthlyEfficiencyPct ?? 0])]);
  return (
    <ChartRow refPct={(targetEff / max) * 100}>
      {lines.map(l => {
        const col = effColor(l.efficiencyPct, l.targetEfficiencyPct);
        return (
          <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)}>
            <Col v={l.monthlyEfficiencyPct ?? 0} max={max} color={WB_THEME.DIM} w="w-2.5 md:w-3.5" title={`Month: ${l.monthlyEfficiencyPct ?? 0}%`} />
            <Col v={l.efficiencyPct ?? 0} max={max} color={col} label={l.efficiencyPct !== null ? `${(l.efficiencyPct ?? 0).toFixed(0)}%` : '—'} title={`Today: ${l.efficiencyPct ?? 0}%`} />
          </Group>
        );
      })}
    </ChartRow>
  );
};

const MetricByLineBarChart: React.FC<{
  lines: LinePerformanceSummary[];
  getValue: (l: LinePerformanceSummary) => number | null;
  getColor: (v: number | null) => string;
  formatVal: (v: number) => string;
  limitVal: number;
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, getValue, getColor, formatVal, limitVal, onSelectLine }) => {
  const max = niceMax([limitVal, ...lines.map(l => getValue(l) ?? 0)]);
  return (
    <ChartRow refPct={(limitVal / max) * 100} refColor={WB_THEME.RED}>
      {lines.map(l => {
        const val = getValue(l);
        const col = getColor(val);
        return (
          <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)}>
            <Col v={val ?? 0} max={max} color={col} label={val !== null ? formatVal(val) : '—'} w="w-4 md:w-6" />
          </Group>
        );
      })}
    </ChartRow>
  );
};

const HourlyOutputWallboardChart: React.FC<{ buckets: HourlyWallboardBucket[] }> = ({ buckets }) => {
  const max = niceMax(buckets.flatMap(b => [b.target, b.output]));
  return (
    <ChartRow>
      {buckets.map(b => {
        const pct = b.target > 0 ? Math.round((b.output / b.target) * 100) : 0;
        const col = b.output === 0 ? WB_THEME.DIM : achColor(pct);
        return (
          <Group key={b.slot24} name={b.label12h} sub={b.output > 0 && b.target > 0 ? `${pct}%` : ''} subColor={col} minW="min-w-[52px] md:min-w-[64px]">
            <Col v={b.target} max={max} color={WB_THEME.DIM} labelColor={WB_THEME.MUTED} label={b.target > 0 ? b.target : ""} w="w-3 md:w-5" title={`Target: ${b.target}`} />
            <Col v={b.output} max={max} color={col} label={b.output > 0 ? b.output : '—'} w="w-4 md:w-6" title={`Output: ${b.output}`} />
          </Group>
        );
      })}
    </ChartRow>
  );
};

/** Pass and fail counts of the bi-hourly audits, one pair of columns per line. */
const AuditPassFailChart: React.FC<{ rows: Array<{ lineId: string; pass: number; fail: number }> }> = ({ rows }) => {
  if (rows.length === 0) {
    return <div style={{ color: WB_THEME.MUTED }} className="flex items-center justify-center h-full text-xs font-bold">No audits recorded in this period</div>;
  }
  const max = niceMax(rows.flatMap(r => [r.pass, r.fail]));
  return (
    <ChartRow>
      {rows.map(r => (
        <Group key={r.lineId} name={lineDisplayName(r.lineId)}>
          <Col v={r.pass} max={max} color={WB_THEME.GREEN} label={r.pass} title={`Pass: ${r.pass}`} />
          <Col v={r.fail} max={max} color={WB_THEME.RED} label={r.fail} title={`Fail: ${r.fail}`} />
        </Group>
      ))}
    </ChartRow>
  );
};

const HourlyQcPassChart: React.FC<{ buckets: HourlyWallboardBucket[] }> = ({ buckets }) => {
  const max = niceMax(buckets.map(b => b.checked));
  return (
    <ChartRow>
      {buckets.map(b => {
        const passed = Math.max(0, b.checked - b.defective);
        const pct = b.checked > 0 ? Math.round((passed / b.checked) * 100) : null;
        return (
          <Group key={b.slot24} name={b.label12h} sub={pct !== null ? `${pct}%` : ''} subColor={WB_THEME.GREEN} minW="min-w-[52px] md:min-w-[64px]">
            <Col v={b.checked} max={max} color={WB_THEME.BLUE} label={b.checked > 0 ? b.checked : '—'} title={`Checked: ${b.checked}`} />
            <Col v={passed} max={max} color={WB_THEME.GREEN} label={b.checked > 0 ? passed : ''} title={`QC pass: ${passed}`} />
          </Group>
        );
      })}
    </ChartRow>
  );
};

const HourlyQualityLineChart: React.FC<{ buckets: HourlyWallboardBucket[]; limit: number }> = ({ buckets, limit }) => {
  const max = niceMax([limit, ...buckets.flatMap(b => [b.dhuPct ?? 0, b.oqlPct ?? 0])]);
  return (
    <ChartRow refPct={(limit / max) * 100} refColor={WB_THEME.RED}>
      {buckets.map(b => (
        <Group key={b.slot24} name={b.label12h} minW="min-w-[56px] md:min-w-[70px]">
          <Col v={b.dhuPct ?? 0} max={max} color={WB_THEME.ORANGE} label={b.dhuPct !== null ? b.dhuPct.toFixed(1) : '—'} title={`DHU: ${b.dhuPct ?? 0}%`} />
          <Col v={b.oqlPct ?? 0} max={max} color={WB_THEME.BLUE} label={b.oqlPct !== null ? `${b.oqlPct.toFixed(1)}%` : ''} title={`OQL: ${b.oqlPct ?? 0}%`} />
        </Group>
      ))}
    </ChartRow>
  );
};

const RectificationPendingChart: React.FC<{
  lines: LinePerformanceSummary[];
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, onSelectLine }) => {
  const pendingLines = lines.filter(l => l.sewBal + l.spotBal > 0).slice(0, 14);
  if (pendingLines.length === 0) {
    return (
      <div style={{ color: WB_THEME.GREEN }} className="flex items-center justify-center h-full text-xs font-black">
        ✓ Zero unreturned sewing or spot garments pending
      </div>
    );
  }
  const max = niceMax(pendingLines.flatMap(l => [l.sewBal, l.spotBal]));
  return (
    <ChartRow>
      {pendingLines.map(l => (
        <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)} sub={`${l.sewBal + l.spotBal} pcs`} subColor={WB_THEME.AMBER}>
          <Col v={l.sewBal} max={max} color={WB_THEME.ORANGE} label={l.sewBal} title={`Sewing: ${l.sewBal}`} />
          <Col v={l.spotBal} max={max} color={WB_THEME.BLUE} label={l.spotBal} title={`Spot: ${l.spotBal}`} />
        </Group>
      ))}
    </ChartRow>
  );
};

/** Weekly BS OQL vs NFL AQC trend, from ISO week 32 of the current year (blue: BS OQL, orange: NFL AQC). */
const WeeklyBsOqlChart: React.FC<{ records: WeeklyQualityRecord[]; commitment: number }> = ({ records, commitment }) => {
  const year = new Date().getFullYear();
  const rows = [...records]
    .filter(r => r.weekKey >= `${year}-W32` && (r.bsOqlPct !== null || r.nflAqcPct !== null))
    .sort((a, b) => (a.weekKey < b.weekKey ? -1 : 1));
  if (rows.length === 0) {
    return (
      <div style={{ color: WB_THEME.MUTED }} className="flex items-center justify-center h-full text-xs font-bold">
        No weekly BS OQL / NFL AQC records from week 32 yet
      </div>
    );
  }
  const step = 70, padL = 30, padR = 30, top = 26, plotH = 170, W = Math.max(320, padL + padR + step * (rows.length - 1));
  const max = niceMax([commitment, ...rows.flatMap(r => [r.bsOqlPct ?? 0, r.nflAqcPct ?? 0])]);
  const x = (i: number) => (rows.length === 1 ? W / 2 : padL + i * step);
  const y = (v: number) => top + plotH - (v / max) * plotH;
  const series = [
    { key: 'bs', name: 'BS OQL', color: WB_THEME.BLUE, get: (r: WeeklyQualityRecord) => r.bsOqlPct, dy: -10 },
    { key: 'aqc', name: 'NFL AQC', color: WB_THEME.ORANGE, get: (r: WeeklyQualityRecord) => r.nflAqcPct, dy: 18 }
  ];
  return (
    <div className="nfl-scroll overflow-x-auto pb-1">
      <svg viewBox={`0 0 ${W} ${top + plotH + 30}`} style={{ width: '100%', minWidth: W, height: top + plotH + 30 }}>
        <line x1={0} x2={W} y1={y(commitment)} y2={y(commitment)} stroke={WB_THEME.RED} strokeWidth={2} strokeDasharray="6 5" />
        <text x={W - 4} y={y(commitment) - 5} textAnchor="end" fontSize={11} fontWeight={800} fill={WB_THEME.RED}>{commitment}% commitment</text>
        <line x1={0} x2={W} y1={top + plotH} y2={top + plotH} stroke={WB_THEME.LINE} strokeWidth={1} />
        {series.map(sr => {
          const pts = rows.map((r, i) => ({ i, v: sr.get(r) })).filter(p => p.v !== null) as Array<{ i: number; v: number }>;
          return (
            <g key={sr.key}>
              <polyline fill="none" stroke={sr.color} strokeWidth={3} strokeLinejoin="round" points={pts.map(p => `${x(p.i)},${y(p.v)}`).join(' ')} />
              {pts.map(p => (
                <g key={p.i}>
                  <circle cx={x(p.i)} cy={y(p.v)} r={5} fill={sr.color} />
                  <text x={x(p.i)} y={y(p.v) + sr.dy} textAnchor="middle" fontSize={12} fontWeight={900} fill={sr.color}>{p.v.toFixed(1)}</text>
                </g>
              ))}
            </g>
          );
        })}
        {rows.map((r, i) => (
          <text key={r.weekKey} x={x(i)} y={top + plotH + 20} textAnchor="middle" fontSize={12} fontWeight={800} fill={WB_THEME.TXT}>
            W{r.weekKey.replace(/^\d{4}-W/, '')}
          </text>
        ))}
      </svg>
      <div className="flex justify-center gap-4 text-xs font-bold" style={{ color: WB_THEME.MUTED }}>
        <span><span className="inline-block w-3 h-1 align-middle mr-1" style={{ backgroundColor: WB_THEME.BLUE }} />BS OQL</span>
        <span><span className="inline-block w-3 h-1 align-middle mr-1" style={{ backgroundColor: WB_THEME.ORANGE }} />NFL AQC</span>
      </div>
    </div>
  );
};

const HorizontalRankBars: React.FC<{
  rows: Array<{ label: string; value: number; text: string; color: string }>;
  maxVal?: number;
}> = ({ rows, maxVal }) => {
  if (rows.length === 0) {
    return (
      <div style={{ color: WB_THEME.MUTED }} className="flex items-center justify-center h-full text-xs font-bold">
        No records
      </div>
    );
  }
  const computedMax = maxVal || Math.max(1, ...rows.map(r => r.value));
  return (
    <div className="flex flex-col justify-around h-full gap-1.5">
      {rows.map(r => {
        const pct = Math.min(100, Math.max(6, Math.round((r.value / computedMax) * 100)));
        return (
          <div key={r.label} className="flex items-center gap-2 text-xs">
            <span className="w-28 shrink-0 font-black text-[11px] leading-tight break-words">{r.label}</span>
            <div className="flex-1 h-3 rounded bg-[var(--wb-bg)] overflow-hidden">
              <div style={{ width: `${pct}%`, backgroundColor: r.color }} className="h-full rounded" />
            </div>
            <span style={{ color: r.color }} className="w-20 shrink-0 text-right font-black text-[11px] whitespace-nowrap">
              {r.text}
            </span>
          </div>
        );
      })}
    </div>
  );
};

const OpenStoppagesList: React.FC<{
  incidents: IncidentRecord[];
  onClick: () => void;
}> = ({ incidents, onClick }) => {
  if (incidents.length === 0) {
    return (
      <div style={{ color: WB_THEME.GREEN }} className="flex items-center justify-center h-full text-xs font-black">
        ✓ All lines running smoothly (0 active stoppages)
      </div>
    );
  }
  return (
    <div onClick={onClick} className="flex flex-col gap-1.5 overflow-y-auto max-h-36 cursor-pointer">
      {incidents.map(inc => {
        const mins = Math.max(1, Math.round((Date.now() - inc.startTimeMillis) / 60000));
        return (
          <div
            key={inc.id}
            style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.RED }}
            className="p-2 rounded border-l-4 flex items-center justify-between text-xs"
          >
            <div className="pr-2 min-w-0">
              <div className="font-black">
                {lineDisplayName(inc.lineId)} · {inc.category.replace(/_/g, ' ')}
              </div>
              <div style={{ color: WB_THEME.MUTED }} className="text-[10px] break-words">
                {inc.description}
              </div>
            </div>
            <span style={{ color: WB_THEME.RED }} className="font-black whitespace-nowrap">
              {mins}m
            </span>
          </div>
        );
      })}
    </div>
  );
};