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
  hourLabelOfSlot, isAuditEntry, floorMatches, lineDisplayName, isDepartmentFloor, unitWord,
  AUDIT_PASS_OQL_PCT, auditResultOf, getBangladeshTodayIso } from '../data/masterData';
import { CalculationEngine as CalcForPeriod } from '../services/calculationEngine';
import { NflLogo } from './NflLogo';
import { isClientDemoSession } from '../data/clientDemo';

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
  theme?: 'light' | 'dark';
  onToggleTheme?: () => void;
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
  finishing = [],
  theme = 'light',
  onToggleTheme
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
  // TV fit mode: when active, dynamically fits all cards and charts into one single screen height (zero vertical scroll).
  const [tvFit, setTvFit] = useState<boolean>(() => { try { return localStorage.getItem('nfl_tv_fit') === '1'; } catch { return false; } });
  const [vw, setVw] = useState<number>(() => (typeof window !== 'undefined' ? window.innerWidth : 1920));
  const [vh, setVh] = useState<number>(() => (typeof window !== 'undefined' ? window.innerHeight : 1080));
  useEffect(() => {
    const onResize = () => {
      setVw(window.innerWidth);
      setVh(window.innerHeight);
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  useEffect(() => { try { localStorage.setItem('nfl_tv_fit', tvFit ? '1' : '0'); } catch {} }, [tvFit]);

  // Constrain 2D scale by available height so height NEVER overflows the single screen on TV/laptop
  const availW = Math.max(768, vw - 16);
  const availH = Math.max(480, vh - 44);
  const scaleX = availW / 1520;
  const scaleY = availH / 840;
  // Grow with the screen: a 4K TV scales about 2.5x, a 1080p TV about 1.25x. Never below 0.7.
  const fitScale = tvFit ? Math.max(0.7, Math.min(scaleX, scaleY, 3)) : 1;
  // Charts fill their panels on the TV instead of a short strip at the bottom.
  const chartPlotH = tvFit ? 'h-[210px]' : PLOT_H;
  const isDept = isDepartmentFloor(selectedFloor) || selectedFloor === 'Print & Embroidery';
  const hasQc = selectedFloor === 'Print' || selectedFloor === 'Embroidery' || selectedFloor === 'Print & Embroidery';
  const unit = unitWord(selectedFloor);
  const todayW = getBangladeshTodayIso();
  const auditByLine = useMemo(() => {
    const m = new Map<string, { lineId: string; floor: string; pass: number; fail: number }>();
    qualities
      .filter(q => isAuditEntry(q) && floorMatches(q.floor, selectedFloor) && CalcForPeriod.isDateInPeriod(q.date, todayW, selectedPeriod))
      .forEach(q => {
        const r = auditResultOf(q.checkedGarments, q.defectiveGarments, q.auditResult);
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
      .sort((a, b) => a.weekKey.localeCompare(b.weekKey));
  }, [weeklyQuality]);

  const latestWeeklyRecord = useMemo(() => {
    return [...weeklyQuality]
      .filter(w => w.bsOqlPct !== null || w.nflAqcPct !== null)
      .sort((a, b) => b.weekKey.localeCompare(a.weekKey))[0];
  }, [weeklyQuality]);

  const auditPassStats = useMemo(() => {
    const done = auditByLine.reduce((a, r) => a + r.pass + r.fail, 0);
    const passed = auditByLine.reduce((a, r) => a + r.pass, 0);
    const failed = auditByLine.reduce((a, r) => a + r.fail, 0);
    const rate = done > 0 ? (passed / done) * 100 : null;
    return { done, passed, failed, rate };
  }, [auditByLine]);

  const brand = isClientDemoSession() ? 'DEMO' : 'NFL';

  return (
    <div
      style={{
        backgroundColor: WB_THEME.BG,
        color: WB_THEME.TXT,
        ...(fitScale !== 1 ? { zoom: fitScale } : {}),
        ...(tvFit ? { height: `calc((100vh - 42px) / ${fitScale})`, maxHeight: `calc((100vh - 42px) / ${fitScale})` } : {})
      }}
      className={`tv-dashboard-root flex flex-col font-sans tabular-nums select-none ${
        tvFit
          ? 'overflow-hidden p-1.5 md:p-2 gap-1.5'
          : 'min-h-[calc(100vh-42px)] p-2.5 gap-2 overflow-y-auto'
      }`}
    >
      {/* ==================== TIER 1: MAIN NAVIGATION BAR ==================== */}
      <div
        className="flex flex-wrap items-center justify-between gap-2 p-1.5 rounded-xl border shadow-xs"
        style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
      >
        {/* Left: Brand + Floor Selector */}
        <div className="flex items-center gap-2 flex-wrap">
          <div className="bg-[#002D62] text-white px-2.5 py-1 rounded-lg flex items-center gap-1.5 font-black text-xs sm:text-sm shadow-xs shrink-0">
            <span className="inline-flex h-4 w-4 items-center justify-center overflow-hidden rounded-sm">
              <NflLogo size={16} />
            </span>
            <span>{brand} LIVE</span>
          </div>

          {/* Floor Segmented Control */}
          <div
            className="nfl-scroll flex items-center p-0.5 rounded-lg border gap-0.5 overflow-x-auto"
            style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}
          >
            {FLOOR_BUTTONS.map(fl => (
              <button
                key={fl}
                onClick={() => onSelectFloor(fl)}
                style={{
                  backgroundColor: selectedFloor === fl ? WB_THEME.BLUE : 'transparent',
                  color: selectedFloor === fl ? 'var(--wb-on-selected)' : WB_THEME.MUTED
                }}
                className="shrink-0 whitespace-nowrap px-2.5 py-1 rounded-md text-[11px] sm:text-xs font-black transition text-center"
              >
                {FLOOR_LABEL[fl]}
              </button>
            ))}
          </div>
        </div>

        {/* Right: Page Switcher + Global Controls */}
        <div className="flex items-center gap-2 flex-wrap ml-auto">
          {/* Page Switcher */}
          {!isDept && (
            <div
              className="flex items-center p-0.5 rounded-lg border gap-0.5"
              style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}
            >
              {[
                { id: 'ALL', label: '⚡ All Overview' },
                { id: 'PRODUCTION', label: '🏭 Production' },
                { id: 'QUALITY', label: '🛡️ Quality' },
                { id: 'FINISHING', label: '👔 Finishing' },
                { id: 'TABLE', label: '📋 Table' }
              ].map(item => (
                <button
                  key={item.id}
                  onClick={() => {
                    setPage(item.id as WbPageId);
                    setCountdown(interval > 0 ? interval : WB_INTERVALS[0]);
                  }}
                  style={{
                    backgroundColor: page === item.id ? WB_THEME.BLUE : 'transparent',
                    color: page === item.id ? 'var(--wb-on-selected)' : WB_THEME.MUTED
                  }}
                  className="shrink-0 px-2.5 py-1 rounded-md text-[11px] sm:text-xs font-black transition text-center whitespace-nowrap"
                >
                  {item.label}
                </button>
              ))}
            </div>
          )}

          {/* Utility Controls Group */}
          <div className="flex items-center gap-1.5 shrink-0">
            {/* TV Fit Toggle */}
            <button
              onClick={() => setTvFit(v => !v)}
              title="টিভি ফিট মোড: চাপ দিলে এক সিঙ্গেল স্ক্রিনে সব চার্ট ও কার্ড আঁটবে (কোন স্ক্রল করতে হবে না)"
              className="px-2.5 py-1 rounded-lg border text-[11px] font-black transition flex items-center gap-1.5 shadow-xs"
              style={{
                backgroundColor: tvFit ? (theme === 'light' ? '#002D62' : '#1D4ED8') : WB_THEME.BG,
                borderColor: tvFit ? '#002D62' : WB_THEME.LINE,
                color: tvFit ? '#FFFFFF' : WB_THEME.TXT
              }}
            >
              <span>📺</span>
              <span>{tvFit ? 'TV Fit: ON' : 'TV Fit: OFF'}</span>
            </button>

            {/* Day / Night Toggle */}
            {onToggleTheme && (
              <button
                onClick={onToggleTheme}
                title={`Switch to ${theme === 'light' ? 'Night' : 'Day'} mode`}
                className="px-2 py-1 rounded-lg border flex items-center gap-1 text-[11px] font-black transition shadow-xs"
                style={{
                  backgroundColor: WB_THEME.BG,
                  borderColor: WB_THEME.LINE,
                  color: WB_THEME.TXT
                }}
              >
                {theme === 'light' ? (
                  <>
                    <span className="text-amber-500 font-bold">☼</span>
                    <span>Day</span>
                  </>
                ) : (
                  <>
                    <span className="text-blue-400 font-bold">☾</span>
                    <span>Night</span>
                  </>
                )}
              </button>
            )}

            {/* Auto Slide Cycle Pill */}
            <button
              onClick={() => {
                const nextIdx = (intervalIdx + 1) % WB_INTERVALS.length;
                setIntervalIdx(nextIdx);
                setCountdown(WB_INTERVALS[nextIdx] > 0 ? WB_INTERVALS[nextIdx] : WB_INTERVALS[0]);
              }}
              title="Click to change auto-rotation slide timer"
              className="px-2 py-1 rounded-lg border flex items-center gap-1.5 text-[11px] font-bold shadow-xs hover:border-amber-400 transition"
              style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}
            >
              <span style={{ color: WB_THEME.MUTED }}>
                {interval > 0 ? `Slide ${countdown}s` : 'Paused'}
              </span>
              <div className="flex items-center gap-0.5">
                {(['ALL', 'PRODUCTION', 'QUALITY', 'FINISHING'] as WbPageId[]).map(p => (
                  <span
                    key={p}
                    style={{ backgroundColor: page === p ? WB_THEME.BLUE : WB_THEME.DIM }}
                    className="w-2 h-1 rounded-xs inline-block"
                  />
                ))}
              </div>
            </button>

            {isDemoMode && (
              <button
                onClick={onToggleDemo}
                style={{ backgroundColor: WB_THEME.RED, color: 'var(--wb-on-red)' }}
                className="px-2 py-1 rounded-lg font-black text-[11px] shadow-xs"
                title="Click to clear Demo Data"
              >
                DEMO
              </button>
            )}

            {/* Live Sync Badge */}
            <span
              style={{ backgroundColor: '#10B98118', color: WB_THEME.GREEN, borderColor: '#10B98140' }}
              className="px-2 py-1 rounded-lg border font-black text-[11px] flex items-center gap-1 shadow-xs"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              <span>Live</span>
            </span>
          </div>
        </div>
      </div>

      {/* ==================== TIER 2: PERIOD & STATUS STRIP ==================== */}
      <div
        className="flex flex-wrap items-center justify-between gap-2 px-2.5 py-1.5 rounded-xl border text-xs shadow-xs"
        style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
      >
        {/* Left: Current Scope & Period Tabs */}
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-1.5 font-black text-xs" style={{ color: WB_THEME.TXT }}>
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: WB_THEME.BLUE }}></span>
            <span>{FLOOR_LABEL[selectedFloor]}</span>
            <span style={{ color: WB_THEME.MUTED }}>·</span>
            <span style={{ color: WB_THEME.BLUE }}>
              {page === 'PRODUCTION'
                ? 'Production'
                : page === 'QUALITY'
                ? 'Quality'
                : page === 'FINISHING'
                ? 'Finishing'
                : page === 'ALL'
                ? 'Executive Overview'
                : 'Line Table'}
            </span>
          </div>

          {/* Period Tabs: Today | Yesterday | Week | Month */}
          <div
            className="flex items-center p-0.5 rounded-lg border gap-0.5 ml-1"
            style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}
          >
            {(['Today', 'Yesterday', 'Week', 'Month'] as PeriodId[]).map(p => (
              <button
                key={p}
                onClick={() => onSelectPeriod(p)}
                style={{
                  backgroundColor: selectedPeriod === p ? WB_THEME.BLUE : 'transparent',
                  color: selectedPeriod === p ? 'var(--wb-on-selected)' : WB_THEME.MUTED
                }}
                className="px-2.5 py-0.5 rounded-md text-[11px] font-black transition text-center"
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        {/* Right: Line Status Badges */}
        <div className="flex items-center gap-1.5 flex-wrap">
          {selectedPeriod === 'Today' ? (
            <>
              <span
                style={{ backgroundColor: WB_THEME.GREEN, color: 'var(--wb-on-green)' }}
                className="px-2 py-0.5 rounded-md text-[11px] font-black flex items-center gap-1 shadow-xs"
              >
                <span>●</span>
                <span>{onTargetCount} On Target (≥95%)</span>
              </span>
              <span
                style={{ backgroundColor: WB_THEME.AMBER, color: 'var(--wb-on-amber)' }}
                className="px-2 py-0.5 rounded-md text-[11px] font-black flex items-center gap-1 shadow-xs"
              >
                <span>●</span>
                <span>{slightBehindCount} Behind (80-95%)</span>
              </span>
              <span
                style={{ backgroundColor: WB_THEME.RED, color: 'var(--wb-on-red)' }}
                className="px-2 py-0.5 rounded-md text-[11px] font-black flex items-center gap-1 shadow-xs"
              >
                <span>●</span>
                <span>{farBehindCount} Critical (&lt;80%)</span>
              </span>
              <span
                style={{ backgroundColor: WB_THEME.GREY, color: 'var(--wb-on-grey)' }}
                className="px-2 py-0.5 rounded-md text-[11px] font-black flex items-center gap-1 shadow-xs"
              >
                <span>●</span>
                <span>{noOutputCount} No Output</span>
              </span>
              {notRunningCount > 0 && (
                <span
                  style={{ backgroundColor: WB_THEME.DIM, color: WB_THEME.TXT }}
                  className="px-2 py-0.5 rounded-md text-[11px] font-black flex items-center gap-1 shadow-xs"
                >
                  <span>●</span>
                  <span>{notRunningCount} Not Running</span>
                </span>
              )}
            </>
          ) : (
            <span
              style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.MUTED, borderColor: WB_THEME.LINE }}
              className="px-2 py-0.5 rounded-md border text-[11px] font-bold"
            >
              Target benchmarks are evaluated for Today view
            </span>
          )}
        </div>
      </div>

      {/* ==================== FACTORY COMMON STRIP: only on Factory view ==================== */}
      {selectedFloor === 'Factory' && (() => {
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
            <WbTile label="Factory QC pass" value={pass !== null ? `${pass.toFixed(1)}%` : '—'} color={pc(pass, 97, 93, false)} sub={`${(checked - defective).toLocaleString()} passed of ${checked.toLocaleString()} checked`} compact={tvFit} />
            <WbTile label="Factory DHU" value={dhu !== null ? `${dhu.toFixed(2)}%` : '—'} color={pc(dhu, 4, 5.5, true)} sub={`${defects.toLocaleString()} defects`} compact={tvFit} />
            <WbTile label="Factory OQL" value={oql !== null ? `${oql.toFixed(2)}%` : '—'} color={pc(oql, AUDIT_PASS_OQL_PCT, 7, true)} sub={`limit ${AUDIT_PASS_OQL_PCT}%`} compact={tvFit} />
            <WbTile label="BS OQL (weekly)" value={latestW && latestW.bsOqlPct !== null ? `${latestW.bsOqlPct.toFixed(2)}%` : '—'} color={pc(latestW?.bsOqlPct ?? null, 4.5, 7, true)} sub={latestW ? `Week ${latestW.weekKey.replace(/^\d{4}-/, '')}` : 'No weekly record yet'} compact={tvFit} />
            <WbTile label={`${brand} AQC (weekly)`} value={latestW && latestW.nflAqcPct !== null ? `${latestW.nflAqcPct.toFixed(2)}%` : '—'} color={pc(latestW?.nflAqcPct ?? null, 4.5, 7, true)} sub={latestW ? `Week ${latestW.weekKey.replace(/^\d{4}-/, '')}` : 'No weekly record yet'} compact={tvFit} />
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
        <WbPanel title={`Weekly BS OQL vs ${brand} AQC (from week 32)`} sub={`Blue: BS OQL · Orange: ${brand} AQC · Red dashed: 4.5% commitment`}>
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
        <div className={`flex-1 flex flex-col ${tvFit ? 'gap-1.5' : 'gap-2'}`}>
          {/* 6 Top KPI Tiles */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
            <WbTile
              label="Target Efficiency"
              value={`${kpis.targetEfficiencyPct.toFixed(0)}%`}
              color={WB_THEME.BLUE}
              sub={`Floor: ${FLOOR_LABEL[selectedFloor]}`}
              compact={tvFit}
            />
            <WbTile
              label="Today Efficiency"
              value={kpis.reportedLinesCount > 0 ? `${kpis.todayEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.reportedLinesCount > 0 ? kpis.todayEfficiencyPct : null, kpis.targetEfficiencyPct)}
              sub={`Target: ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              compact={tvFit}
            />
            <WbTile
              label="Monthly Efficiency"
              value={kpis.monthlyEfficiencyPct > 0 ? `${kpis.monthlyEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.monthlyEfficiencyPct > 0 ? kpis.monthlyEfficiencyPct : null, kpis.targetEfficiencyPct)}
              sub={`Δ ${kpis.monthlyEfficiencyDelta >= 0 ? '+' : ''}${kpis.monthlyEfficiencyDelta.toFixed(1)} pp vs target`}
              compact={tvFit}
            />
            <WbTile
              label="Output vs Target"
              value={kpis.todayOutputPcs.toLocaleString()}
              unit={`/ ${kpis.todayTargetPcs.toLocaleString()}`}
              color={achColor(kpis.achievementPct)}
              sub={`${kpis.achievementPct.toFixed(0)}% · Gap ${kpis.todayOutputPcs - kpis.todayTargetPcs >= 0 ? '+' : ''}${(kpis.todayOutputPcs - kpis.todayTargetPcs).toLocaleString()} pcs`}
              compact={tvFit}
            />
            <WbTile
              label="Lines Reported"
              value={`${kpis.reportedLinesCount}`}
              unit={`/ ${kpis.totalFloorLinesCount}`}
              color={kpis.reportedLinesCount === kpis.totalFloorLinesCount && kpis.totalFloorLinesCount > 0 ? WB_THEME.GREEN : WB_THEME.AMBER}
              sub={`${noOutputCount} no output · ${notRunningCount} not running`}
              compact={tvFit}
            />
            <WbTile
              label="Downtime (NPT)"
              value={`${kpis.downtimeMinutes}`}
              unit="min"
              color={kpis.openIncidentsCount > 0 ? WB_THEME.RED : kpis.downtimeMinutes > 0 ? WB_THEME.AMBER : WB_THEME.GREEN}
              sub={`${kpis.openIncidentsCount} open · ${allIncidents.length} total today`}
              compact={tvFit}
            />
          </div>

          {/* Row 1 Panels: Target vs Achievement & Efficiency by Line & Lowest Efficiency */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1">
            <WbPanel
              title="Live Target vs Achievement"
              sub="Target (blue) vs Output (status colored) · % below"
              right={
                <div className="flex gap-1">
                  <WbBadge text="≥95%" bg={WB_THEME.GREEN} fg="var(--wb-on-green)" />
                  <WbBadge text="80-95%" bg={WB_THEME.AMBER} fg="var(--wb-on-amber)" />
                  <WbBadge text="<80%" bg={WB_THEME.RED} fg="var(--wb-on-red)" />
                </div>
              }
              className="lg:col-span-5"
              compact={tvFit}
            >
              <LiveTargetVsAchievementChart lines={activeLines} onSelectLine={setDeepDiveLine} plotH={chartPlotH} />
            </WbPanel>

            <WbPanel
              title="Efficiency by Line"
              sub={`Blue: Month Avg · Colored: Today · Dashed: target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              className="lg:col-span-4"
              compact={tvFit}
            >
              <EfficiencyByLineChart lines={activeLines} targetEff={kpis.targetEfficiencyPct} onSelectLine={setDeepDiveLine} plotH={chartPlotH} />
            </WbPanel>

            <WbPanel
              title="Lowest Efficiency Lines"
              sub="Action focus · vs line target"
              className="lg:col-span-3"
              compact={tvFit}
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

          {/* Row 2 Panels: Hourly Output vs Target & Downtime */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1">
            <WbPanel
              title="Hourly Output vs Target"
              sub="By hour of work (1st hr, 2nd hr…) · Target (back) vs Output (front)"
              className="lg:col-span-7"
              compact={tvFit}
            >
              <HourlyOutputWallboardChart buckets={hourlySeries} plotH={chartPlotH} />
            </WbPanel>

            <WbPanel
              title="Downtime by Cause (min)"
              sub={`Total ${kpis.downtimeMinutes} min`}
              className="lg:col-span-5"
              compact={tvFit}
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
          </div>

          {/* Detailed Line Performance Report Table (below charts) */}
          <div className="mt-2">
            <LiveLinePerformanceTable
              lines={lineSummaries}
              onSelectLine={setDeepDiveLine}
              selectedFloor={selectedFloor}
              selectedPeriod={selectedPeriod}
            />
          </div>
        </div>
      )}

      {/* ==================== PAGE 2: QUALITY ==================== */}
      {!isDept && page === 'QUALITY' && (
        <div className={`flex-1 flex flex-col ${tvFit ? 'gap-1.5' : 'gap-2'}`}>
          {/* Top 7 Quality KPI Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-7 gap-2">
            {/* 1. Today OQL */}
            <WbTile
              label="Today OQL"
              value={kpis.checkedGarments > 0 ? `${kpis.todayOqlPct.toFixed(2)}%` : '—'}
              color={oqlColor(kpis.checkedGarments > 0 ? kpis.todayOqlPct : null, kpis.oqlCommitmentPct)}
              sub={`Limit ≤ ${kpis.oqlCommitmentPct.toFixed(1)}% · ${kpis.defectiveGarments} def`}
              compact={tvFit}
            />

            {/* 2. Monthly OQL */}
            <WbTile
              label="Monthly OQL"
              value={kpis.monthlyOqlPct !== undefined && kpis.monthlyOqlPct !== null ? `${kpis.monthlyOqlPct.toFixed(2)}%` : (kpis.todayOqlPct > 0 ? `${kpis.todayOqlPct.toFixed(2)}%` : '—')}
              color={oqlColor(kpis.monthlyOqlPct ?? kpis.todayOqlPct, kpis.oqlCommitmentPct)}
              sub={`Commitment ≤ ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              compact={tvFit}
            />

            {/* 3. Today DHU */}
            <WbTile
              label="Today DHU"
              value={kpis.checkedGarments > 0 ? `${kpis.todayDhuPct.toFixed(2)}%` : '—'}
              color={dhuColor(kpis.checkedGarments > 0 ? kpis.todayDhuPct : null, kpis.dhuLimitPct)}
              sub={`Limit ≤ ${kpis.dhuLimitPct.toFixed(1)}% · ${kpis.totalDefects} defects`}
              compact={tvFit}
            />

            {/* 4. Monthly DHU */}
            <WbTile
              label="Monthly DHU"
              value={kpis.monthlyDhuPct !== undefined && kpis.monthlyDhuPct !== null ? `${kpis.monthlyDhuPct.toFixed(2)}%` : (kpis.todayDhuPct > 0 ? `${kpis.todayDhuPct.toFixed(2)}%` : '—')}
              color={dhuColor(kpis.monthlyDhuPct ?? kpis.todayDhuPct, kpis.dhuLimitPct)}
              sub={`Floor Limit ≤ ${kpis.dhuLimitPct.toFixed(1)}%`}
              compact={tvFit}
            />

            {/* 5. Inspection Pass % */}
            <WbTile
              label="Inspection Pass %"
              value={auditPassStats.rate !== null ? `${auditPassStats.rate.toFixed(1)}%` : (kpis.friPassRatePct > 0 ? `${kpis.friPassRatePct.toFixed(1)}%` : '—')}
              color={auditPassStats.rate === null ? WB_THEME.GREY : auditPassStats.rate >= 90 ? WB_THEME.GREEN : auditPassStats.rate >= 75 ? WB_THEME.AMBER : WB_THEME.RED}
              sub={`${auditPassStats.passed} pass / ${auditPassStats.done} audits (${auditPassStats.failed} fail)`}
              compact={tvFit}
            />

            {/* 6. BS QA OQL */}
            <WbTile
              label="BS QA OQL"
              value={`${kpis.bsQaOqlPct.toFixed(2)}%`}
              color={oqlColor(kpis.bsQaOqlPct, kpis.oqlCommitmentPct)}
              sub={`Target ≤ ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              compact={tvFit}
            />

            {/* 7. Northern AQC */}
            <WbTile
              label={`${brand} AQC`}
              value={latestWeeklyRecord?.nflAqcPct !== null && latestWeeklyRecord?.nflAqcPct !== undefined ? `${latestWeeklyRecord.nflAqcPct.toFixed(2)}%` : '—'}
              color={oqlColor(latestWeeklyRecord?.nflAqcPct ?? null, kpis.oqlCommitmentPct)}
              sub={latestWeeklyRecord ? `Week ${latestWeeklyRecord.weekKey.replace(/^\d{4}-/, '')}` : 'Weekly AQC benchmark'}
              compact={tvFit}
            />
          </div>

          {/* Quality Visuals Row 1: DHU by Line, OQL by Line, Top Defects */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1">
            <WbPanel
              title="DHU by Line"
              sub={`Dashed: limit ${kpis.dhuLimitPct.toFixed(1)}%`}
              className="lg:col-span-4"
              compact={tvFit}
            >
              <MetricByLineBarChart
                lines={activeLines}
                getValue={l => l.dhu}
                getColor={v => dhuColor(v, kpis.dhuLimitPct)}
                formatVal={v => v.toFixed(1)}
                limitVal={kpis.dhuLimitPct}
                onSelectLine={setDeepDiveLine}
                plotH={chartPlotH}
              />
            </WbPanel>

            <WbPanel
              title="OQL by Line"
              sub={`Dashed: commitment ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              className="lg:col-span-4"
              compact={tvFit}
            >
              <MetricByLineBarChart
                lines={activeLines}
                getValue={l => l.todayOqlPct}
                getColor={v => oqlColor(v, kpis.oqlCommitmentPct)}
                formatVal={v => `${v.toFixed(1)}%`}
                limitVal={kpis.oqlCommitmentPct}
                onSelectLine={setDeepDiveLine}
                plotH={chartPlotH}
              />
            </WbPanel>

            <WbPanel
              title="Top Defects (Pareto)"
              sub="Count and DHU contribution"
              className="lg:col-span-4"
              compact={tvFit}
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

          {/* Quality Visuals Row 2: Bi-hourly Audit Pass/Fail, Hourly DHU & OQL, Main Defect Worst Lines */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1">
            <WbPanel
              title="Audit: Pass / Fail by Line"
              sub={`Hourly OQL audits · PASS ≤ ${AUDIT_PASS_OQL_PCT}%`}
              className="lg:col-span-5"
              compact={tvFit}
            >
              <AuditPassFailChart rows={auditByLine} />
            </WbPanel>

            <WbPanel
              title="Hourly DHU and OQL"
              sub="Orange: DHU · Blue: OQL% · Red dashed: 4.5% limit"
              className="lg:col-span-4"
              compact={tvFit}
            >
              <HourlyQualityLineChart buckets={hourlySeries} limit={kpis.oqlCommitmentPct} />
            </WbPanel>

            <WbPanel
              title="Main Defect, Worst Lines"
              sub={`Top contributor: ${mainDefectName}`}
              className="lg:col-span-3"
              compact={tvFit}
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

          {/* Quality Weekly Trend */}
          <WbPanel
            title={`Weekly BS OQL vs ${brand} AQC (from Week 32)`}
            sub={`Blue: BS OQL · Orange: ${brand} AQC · Red dashed: 4.5% commitment`}
            compact={tvFit}
          >
            <WeeklyBsOqlChart records={sortedWeekly} commitment={kpis.oqlCommitmentPct} compact={tvFit} />
          </WbPanel>
        </div>
      )}

      {/* ==================== PAGE 3: ALL (EXECUTIVE COMBINED SEWING WALLBOARD) ==================== */}
      {!isDept && page === 'ALL' && (
        <div className={`flex-1 flex flex-col ${tvFit ? 'gap-1.5' : 'gap-2'}`}>
          {/* Top 6 KPI Cards: Today Eff, Up-to-Date Eff, Today DHU, Up-to-Date DHU, Today OQL, Up-to-Date OQL */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
            {/* 1. Today Efficiency */}
            <WbTile
              label="Today Efficiency"
              value={kpis.reportedLinesCount > 0 ? `${kpis.todayEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.reportedLinesCount > 0 ? kpis.todayEfficiencyPct : null, kpis.targetEfficiencyPct)}
              sub={`Target: ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              compact={tvFit}
            />

            {/* 2. Up to Date (MTD) Efficiency */}
            <WbTile
              label="Up-to-Date (MTD) Efficiency"
              value={kpis.monthlyEfficiencyPct > 0 ? `${kpis.monthlyEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.monthlyEfficiencyPct > 0 ? kpis.monthlyEfficiencyPct : null, kpis.targetEfficiencyPct)}
              sub={`Δ ${kpis.monthlyEfficiencyDelta >= 0 ? '+' : ''}${kpis.monthlyEfficiencyDelta.toFixed(1)} pp vs target`}
              compact={tvFit}
            />

            {/* 3. Today DHU */}
            <WbTile
              label="Today DHU"
              value={kpis.checkedGarments > 0 ? `${kpis.todayDhuPct.toFixed(2)}%` : '—'}
              color={dhuColor(kpis.checkedGarments > 0 ? kpis.todayDhuPct : null, kpis.dhuLimitPct)}
              sub={`Limit ≤ ${kpis.dhuLimitPct.toFixed(1)}% · ${kpis.totalDefects} defects`}
              compact={tvFit}
            />

            {/* 4. Up to Date (MTD) DHU */}
            <WbTile
              label="Up-to-Date (MTD) DHU"
              value={kpis.monthlyDhuPct !== undefined && kpis.monthlyDhuPct !== null ? `${kpis.monthlyDhuPct.toFixed(2)}%` : (kpis.todayDhuPct > 0 ? `${kpis.todayDhuPct.toFixed(2)}%` : '—')}
              color={dhuColor(kpis.monthlyDhuPct ?? kpis.todayDhuPct, kpis.dhuLimitPct)}
              sub={`Floor Limit ≤ ${kpis.dhuLimitPct.toFixed(1)}%`}
              compact={tvFit}
            />

            {/* 5. Today OQL */}
            <WbTile
              label="Today OQL"
              value={kpis.checkedGarments > 0 ? `${kpis.todayOqlPct.toFixed(2)}%` : '—'}
              color={oqlColor(kpis.checkedGarments > 0 ? kpis.todayOqlPct : null, kpis.oqlCommitmentPct)}
              sub={`Commitment ≤ ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              compact={tvFit}
            />

            {/* 6. Up to Date (MTD) OQL */}
            <WbTile
              label="Up-to-Date (MTD) OQL"
              value={kpis.monthlyOqlPct !== undefined && kpis.monthlyOqlPct !== null ? `${kpis.monthlyOqlPct.toFixed(2)}%` : (kpis.todayOqlPct > 0 ? `${kpis.todayOqlPct.toFixed(2)}%` : '—')}
              color={oqlColor(kpis.monthlyOqlPct ?? kpis.todayOqlPct, kpis.oqlCommitmentPct)}
              sub={`Commitment ≤ ${kpis.oqlCommitmentPct.toFixed(1)}%`}
              compact={tvFit}
            />
          </div>

          {/* Body Row 1: Line-wise Target vs Achievement + Operator Present vs Machine Running */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1">
            <WbPanel
              title="Line-wise Target vs Achievement"
              sub={`Target (blue) vs Output (achievement status) · Output ${kpis.todayOutputPcs.toLocaleString()} / ${kpis.todayTargetPcs.toLocaleString()} pcs (${kpis.achievementPct.toFixed(0)}%)`}
              right={
                <div className="flex gap-1">
                  <WbBadge text="≥95%" bg={WB_THEME.GREEN} fg="var(--wb-on-green)" />
                  <WbBadge text="80-95%" bg={WB_THEME.AMBER} fg="var(--wb-on-amber)" />
                  <WbBadge text="<80%" bg={WB_THEME.RED} fg="var(--wb-on-red)" />
                </div>
              }
              className="lg:col-span-7"
              compact={tvFit}
            >
              <LiveTargetVsAchievementChart lines={activeLines} onSelectLine={setDeepDiveLine} plotH={chartPlotH} />
            </WbPanel>

            <WbPanel
              top
              title="Operator Present vs Machine Running"
              sub={`Active lines · Ratio ${kpis.machinesCount > 0 ? (kpis.operatorsCount / kpis.machinesCount).toFixed(2) : '0.00'}x`}
              right={
                <div className="flex items-center gap-1">
                  <WbBadge text={`Ops: ${kpis.operatorsCount}`} bg={WB_THEME.ORANGE} fg="var(--wb-on-orange)" />
                  <WbBadge text={`Mach: ${kpis.machinesCount}`} bg={WB_THEME.BLUE} fg="var(--wb-on-selected)" />
                  {kpis.helpersCount > 0 && <WbBadge text={`Help: ${kpis.helpersCount}`} bg={WB_THEME.GREEN} fg="var(--wb-on-green)" />}
                </div>
              }
              className="lg:col-span-5"
              compact={tvFit}
            >
              <div className="flex-1 min-h-0 flex flex-col justify-start gap-1.5">
                <div className="grid grid-cols-4 gap-1.5 text-center">
                  <div className="p-1 rounded border" style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}>
                    <div className="text-[9px] font-bold" style={{ color: WB_THEME.MUTED }}>Operators</div>
                    <div className="text-lg md:text-xl font-black" style={{ color: WB_THEME.ORANGE }}>{kpis.operatorsCount}</div>
                  </div>
                  <div className="p-1 rounded border" style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}>
                    <div className="text-[9px] font-bold" style={{ color: WB_THEME.MUTED }}>Machines</div>
                    <div className="text-lg md:text-xl font-black" style={{ color: WB_THEME.BLUE }}>{kpis.machinesCount}</div>
                  </div>
                  <div className="p-1 rounded border" style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}>
                    <div className="text-[9px] font-bold" style={{ color: WB_THEME.MUTED }}>Helpers</div>
                    <div className="text-lg md:text-xl font-black" style={{ color: WB_THEME.GREEN }}>{kpis.helpersCount}</div>
                  </div>
                  <div className="p-1 rounded border" style={{ backgroundColor: WB_THEME.BG, borderColor: WB_THEME.LINE }}>
                    <div className="text-[9px] font-bold" style={{ color: WB_THEME.MUTED }}>Op/Mc Ratio</div>
                    <div className="text-lg md:text-xl font-black" style={{ color: WB_THEME.TXT }}>
                      {kpis.machinesCount > 0 ? (kpis.operatorsCount / kpis.machinesCount).toFixed(2) : '1.00'}x
                    </div>
                  </div>
                </div>
                <div className={`flex-1 min-h-0 overflow-y-auto ${tvFit ? '' : 'max-h-[260px]'} border rounded`} style={{ borderColor: WB_THEME.LINE }}>
                  <table className={`w-full ${tvFit ? 'text-sm' : 'text-xs'} border-collapse`}>
                    <thead className="sticky top-0 z-10" style={{ backgroundColor: WB_THEME.BG }}>
                      <tr className="text-[10px] font-black uppercase text-left" style={{ color: WB_THEME.MUTED }}>
                        <th className="p-1 pl-2">Line</th>
                        <th className="p-1 text-right">Ops</th>
                        <th className="p-1 text-right">Mach</th>
                        <th className="p-1 text-right">Help</th>
                        <th className="p-1 text-right pr-2">Ratio</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y font-semibold" style={{ borderColor: WB_THEME.LINE }}>
                      {activeLines.map(l => (
                        <tr key={l.lineId} className="hover:bg-blue-50/10 cursor-pointer" onClick={() => setDeepDiveLine(l)}>
                          <td className="p-1 pl-2 font-black">{lineDisplayName(l.lineId)}</td>
                          <td className="p-1 text-right font-bold" style={{ color: WB_THEME.ORANGE }}>{l.operators}</td>
                          <td className="p-1 text-right font-bold" style={{ color: WB_THEME.BLUE }}>{l.machines}</td>
                          <td className="p-1 text-right font-bold" style={{ color: WB_THEME.GREEN }}>{l.helpers}</td>
                          <td className="p-1 text-right pr-2 font-black">
                            {l.machines > 0 ? (l.operators / l.machines).toFixed(2) : '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </WbPanel>
          </div>

          {/* Body Row 2: Trend Chart (Week 32 to Current) + Corner: NPT */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-2 flex-1">
            <WbPanel
              title={`Weekly BS OQL vs ${brand} AQC (from Week 32)`}
              sub={`Blue: BS OQL · Orange: ${brand} AQC · Red dashed: 4.5% commitment`}
              right={
                <div className="flex gap-1">
                  <WbBadge text="BS OQL" bg={WB_THEME.BLUE} fg="var(--wb-on-selected)" />
                  <WbBadge text={`${brand} AQC`} bg={WB_THEME.ORANGE} fg="var(--wb-on-orange)" />
                </div>
              }
              className="lg:col-span-7"
              compact={tvFit}
            >
              <WeeklyBsOqlChart records={sortedWeekly} commitment={kpis.oqlCommitmentPct} compact={tvFit} />
            </WbPanel>

            <WbPanel
              top
              title="NPT (Non-Productive Time) & Stoppages"
              sub={`Total ${kpis.downtimeMinutes} min elapsed today`}
              right={
                <WbBadge
                  text={`${openIncidents.length} open`}
                  bg={openIncidents.length > 0 ? WB_THEME.RED : WB_THEME.GREEN}
                  fg={openIncidents.length > 0 ? 'var(--wb-on-red)' : 'var(--wb-on-green)'}
                />
              }
              className="lg:col-span-5"
              compact={tvFit}
            >
              <div className="flex flex-col justify-between h-full gap-2">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <div className="text-[10px] font-extrabold uppercase mb-1" style={{ color: WB_THEME.MUTED }}>
                      Downtime by Cause
                    </div>
                    <HorizontalRankBars
                      rows={downtimeByCause.length > 0 ? downtimeByCause.map(d => ({
                        label: d.cause,
                        value: d.mins,
                        text: `${d.mins}m`,
                        color: WB_THEME.AMBER
                      })) : [{ label: 'Zero downtime', value: 0, text: '0m', color: WB_THEME.GREEN }]}
                    />
                  </div>
                  <div>
                    <div className="text-[10px] font-extrabold uppercase mb-1 flex items-center justify-between" style={{ color: WB_THEME.MUTED }}>
                      <span>Open Stoppages</span>
                      <span className="text-[9px] font-bold" style={{ color: WB_THEME.MUTED }}>${openIncidents.length} active</span>
                    </div>
                    <OpenStoppagesList incidents={openIncidents} onClick={onOpenIncidentPanel} />
                  </div>
                </div>
              </div>
            </WbPanel>
          </div>
        </div>
      )}

      {/* ==================== PAGE 4: TABLE (15-COLUMN LIVE LINE PERFORMANCE TABLE) ==================== */}
      {!isDept && page === 'TABLE' && (
        <div className="flex-1 min-h-[72vh] overflow-auto">
          <LiveLinePerformanceTable
            lines={lineSummaries}
            onSelectLine={setDeepDiveLine}
            selectedFloor={selectedFloor}
            selectedPeriod={selectedPeriod}
          />
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
                label="Output vs Target"
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
              Hourly DHU &amp; OQL Audit Records (scroll up and down)
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
                    .filter(q => q.lineId === deepDiveLine.lineId && isAuditEntry(q))
                    .map(q => {
                      const r = auditResultOf(q.checkedGarments, q.defectiveGarments, q.auditResult);
                      return (
                        <tr key={q.id} className="border-t" style={{ borderColor: WB_THEME.LINE }}>
                          <td className="p-2.5 font-black whitespace-nowrap" style={{ color: WB_THEME.BLUE }}>{hourLabelOfSlot(q.lineId, q.hourSlot)}</td>
                          <td className="p-2.5 font-bold whitespace-nowrap" style={{ color: WB_THEME.BLUE }}>Hourly OQL Audit</td>
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

/* ==================== REUSABLE LIVE LINE PERFORMANCE TABLE ==================== */

const LiveLinePerformanceTable: React.FC<{
  lines: LinePerformanceSummary[];
  onSelectLine: (l: LinePerformanceSummary) => void;
  selectedFloor: FloorId;
  selectedPeriod: PeriodId;
}> = ({ lines, onSelectLine, selectedFloor, selectedPeriod }) => (
  <div className="bg-slate-100 text-slate-900 rounded-lg overflow-hidden p-2 border border-slate-200 shadow-xs">
    <div className="flex items-center justify-between mb-1.5 px-1">
      <div>
        <h3 className="text-sm font-black text-[#002D62]">
          Live Line Performance Table ({FLOOR_LABEL[selectedFloor]} · {selectedPeriod})
        </h3>
        <p className="text-[11px] text-slate-500">
          Click any line row to open its Line Deep Dive &amp; Rectification Ledger
        </p>
      </div>
      <span className="text-[11px] font-black px-2 py-0.5 rounded bg-white text-slate-700 border border-slate-300">
        {lines.length} Lines
      </span>
    </div>
    <div className="overflow-x-auto rounded-lg shadow-xs">
      <table className="w-full text-xs border-collapse bg-white">
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
            <th className="py-2 px-2 text-right bg-amber-900/80">Sew Sent</th>
            <th className="py-2 px-2 text-right bg-amber-900/80">Sew Recv</th>
            <th className="py-2 px-2 text-right bg-amber-900/80">Sew Bal</th>
            <th className="py-2 px-2 text-right bg-sky-900/80">Spot Sent</th>
            <th className="py-2 px-2 text-right bg-sky-900/80">Spot Recv</th>
            <th className="py-2 px-2 text-right bg-sky-900/80">Spot Bal</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200 font-semibold">
          {lines.map(l => (
            <tr
              key={l.lineId}
              onClick={() => onSelectLine(l)}
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
  </div>
);

/* ==================== SUB-COMPONENTS MATCHING WbComponents.kt ==================== */

const WbTile: React.FC<{
  label: string;
  value: string;
  unit?: string;
  color: string;
  sub: string;
  compact?: boolean;
}> = ({ label, value, unit, color, sub, compact }) => (
  <div
    style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
    className={`rounded-xl border ${compact ? 'p-2 md:p-2.5 min-h-[96px]' : 'p-3 min-h-[108px]'} flex flex-col justify-between relative overflow-hidden shadow-xs`}
  >
    <div style={{ backgroundColor: color }} className="absolute left-0 top-0 bottom-0 w-1.5 rounded-l" />
    <div
      style={{ color: 'var(--wb-txt)' }}
      className={`${compact ? 'text-[11px] sm:text-xs' : 'text-xs sm:text-[13px]'} font-black uppercase tracking-wide pl-2 leading-tight break-words`}
    >
      {label}
    </div>
    <div className="flex items-baseline gap-1.5 my-1 pl-2">
      <span style={{ color }} className={`${compact ? 'text-2xl sm:text-3xl' : 'text-3xl sm:text-4xl'} font-black leading-none`}>
        {value}
      </span>
      {unit && (
        <span style={{ color: WB_THEME.MUTED }} className={`${compact ? 'text-[11px]' : 'text-xs sm:text-sm'} font-bold`}>
          {unit}
        </span>
      )}
    </div>
    <div
      style={{ color: WB_THEME.MUTED }}
      className={`${compact ? 'text-[10px] sm:text-[11px]' : 'text-xs'} font-bold pl-2 leading-snug break-words`}
    >
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
  compact?: boolean;
  /** content starts at the top (lists and tables) */
  top?: boolean;
}> = ({ title, sub, right, className = '', children, compact, top }) => (
  <div
    style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
    className={`rounded-lg border ${compact ? 'p-1.5 md:p-2' : 'p-2.5'} flex flex-col justify-between overflow-hidden ${className}`}
  >
    <div className={`flex items-start justify-between gap-2 ${compact ? 'mb-1' : 'mb-1.5'}`}>
      <div>
        <div className={`${compact ? 'text-[11px] md:text-xs' : 'text-xs'} font-black tracking-wide`}>{title}</div>
        {sub && (
          <div style={{ color: WB_THEME.MUTED }} className={`${compact ? 'text-[9px]' : 'text-[10px]'} font-semibold`}>
            {sub}
          </div>
        )}
      </div>
      {right}
    </div>
    <div className={`flex-1 min-h-0 flex flex-col ${top ? 'justify-start' : 'justify-end'} ${compact ? 'min-h-[110px]' : 'min-h-[180px]'}`}>{children}</div>
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
      <div style={{ height: `${h}%`, backgroundColor: color, boxShadow: '0 2px 4px rgba(0,0,0,0.12)' }} className="w-full rounded-t-md transition-all duration-300" title={title} />
    </div>
  );
};

/** The scrolling row of column groups, with an optional dashed reference line. */
const ChartRow: React.FC<{ children: React.ReactNode; refPct?: number | null; refColor?: string; plotH?: string }> = ({ children, refPct, refColor, plotH = PLOT_H }) => (
  <div className="nfl-scroll overflow-x-auto pb-1">
    <div className="relative flex items-end justify-between gap-1.5 min-w-full pt-3">{children}
      {refPct !== undefined && refPct !== null && (
        <div className={`absolute left-0 right-0 pointer-events-none ${plotH}`} style={{ top: 12 }}>
          <div className="absolute left-0 right-0 border-t-2 border-dashed" style={{ bottom: `${Math.min(100, refPct)}%`, borderColor: refColor || WB_THEME.AMBER }} />
        </div>
      )}
    </div>
  </div>
);

const Group: React.FC<{ children: React.ReactNode; name: React.ReactNode; sub?: React.ReactNode; subColor?: string; onClick?: () => void; minW?: string; plotH?: string }> = ({ children, name, sub, subColor, onClick, minW = 'min-w-[44px] md:min-w-[54px]', plotH = PLOT_H }) => (
  <div onClick={onClick} className={`flex flex-col items-center flex-1 ${minW} ${onClick ? 'cursor-pointer' : ''}`}>
    <div className={`w-full ${plotH} flex items-end justify-center gap-1.5 md:gap-2.5 border-b`} style={{ borderColor: WB_THEME.LINE }}>{children}</div>
    <div className="text-[10px] md:text-xs font-black mt-0.5 whitespace-nowrap">{name}</div>
    {sub !== undefined && <div style={{ color: subColor }} className="text-[9px] md:text-[10px] font-bold leading-tight">{sub}</div>}
  </div>
);

const LiveTargetVsAchievementChart: React.FC<{
  lines: LinePerformanceSummary[];
  onSelectLine: (l: LinePerformanceSummary) => void;
  plotH?: string;
}> = ({ lines, onSelectLine, plotH = PLOT_H }) => {
  const max = niceMax(lines.flatMap(l => [l.target, l.output ?? 0]));
  return (
    <ChartRow plotH={plotH}>
      {lines.map(l => {
        const col = achColor(l.achievementPct);
        return (
          <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)} sub={l.achievementPct !== null ? `${l.achievementPct.toFixed(0)}%` : '—'} subColor={col} plotH={plotH}>
            <Col v={l.target} max={max} color={WB_THEME.BLUE} labelColor={WB_THEME.BLUE} label={l.target} w="w-2.5 md:w-3.5" title={`Target: ${l.target}`} />
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
  plotH?: string;
}> = ({ lines, targetEff, onSelectLine, plotH = PLOT_H }) => {
  const max = niceMax([targetEff, ...lines.flatMap(l => [l.efficiencyPct ?? 0, l.monthlyEfficiencyPct ?? 0])]);
  return (
    <ChartRow refPct={(targetEff / max) * 100} plotH={plotH}>
      {lines.map(l => {
        const col = effColor(l.efficiencyPct, l.targetEfficiencyPct);
        return (
          <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)} plotH={plotH}>
            <Col v={l.monthlyEfficiencyPct ?? 0} max={max} color={WB_THEME.BLUE} labelColor={WB_THEME.MUTED} w="w-2.5 md:w-3.5" title={`Month Target/Avg: ${l.monthlyEfficiencyPct ?? 0}%`} />
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
  plotH?: string;
}> = ({ lines, getValue, getColor, formatVal, limitVal, onSelectLine, plotH = PLOT_H }) => {
  const max = niceMax([limitVal, ...lines.map(l => getValue(l) ?? 0)]);
  return (
    <ChartRow refPct={(limitVal / max) * 100} refColor={WB_THEME.RED} plotH={plotH}>
      {lines.map(l => {
        const val = getValue(l);
        const col = getColor(val);
        return (
          <Group key={l.lineId} onClick={() => onSelectLine(l)} name={lineDisplayName(l.lineId)} plotH={plotH}>
            <Col v={val ?? 0} max={max} color={col} label={val !== null ? formatVal(val) : '—'} w="w-4 md:w-6" />
          </Group>
        );
      })}
    </ChartRow>
  );
};

const HourlyOutputWallboardChart: React.FC<{ buckets: HourlyWallboardBucket[]; plotH?: string }> = ({ buckets, plotH = PLOT_H }) => {
  const max = niceMax(buckets.flatMap(b => [b.target, b.output]));
  return (
    <ChartRow plotH={plotH}>
      {buckets.map(b => {
        const pct = b.target > 0 ? Math.round((b.output / b.target) * 100) : 0;
        const col = b.output === 0 ? WB_THEME.DIM : achColor(pct);
        return (
          <Group key={b.slot24} name={b.label12h} sub={b.output > 0 && b.target > 0 ? `${pct}%` : ''} subColor={col} minW="min-w-[52px] md:min-w-[64px]" plotH={plotH}>
            <Col v={b.target} max={max} color={WB_THEME.BLUE} labelColor={WB_THEME.BLUE} label={b.target > 0 ? b.target : ""} w="w-3 md:w-5" title={`Target: ${b.target}`} />
            <Col v={b.output} max={max} color={col} label={b.output > 0 ? b.output : '—'} w="w-4 md:w-6" title={`Output: ${b.output}`} />
          </Group>
        );
      })}
    </ChartRow>
  );
};

/** Pass and fail counts of the hourly OQL audits, one pair of columns per line. */
const AuditPassFailChart: React.FC<{ rows: Array<{ lineId: string; pass: number; fail: number }> }> = ({ rows }) => {
  if (rows.length === 0) {
    return <div style={{ color: WB_THEME.MUTED }} className="flex items-center justify-center h-20 text-xs font-bold">No audits recorded in this period</div>;
  }
  const max = niceMax(rows.flatMap(r => [r.pass, r.fail]));
  return (
    <ChartRow>
      {rows.map(r => (
        <Group key={r.lineId} name={lineDisplayName(r.lineId)} plotH="h-16" minW="min-w-[38px] md:min-w-[46px]">
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
const WeeklyBsOqlChart: React.FC<{ records: WeeklyQualityRecord[]; commitment: number; compact?: boolean }> = ({ records, commitment, compact = false }) => {
  const now = new Date();
  const tempUtc = new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()));
  const dayNum = tempUtc.getUTCDay() || 7;
  tempUtc.setUTCDate(tempUtc.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(tempUtc.getUTCFullYear(), 0, 1));
  const currentIsoWeek = Math.ceil((((tempUtc.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);

  const validRecords = [...records].filter(r => r.bsOqlPct !== null || r.nflAqcPct !== null);
  const recordYears = validRecords
    .map(r => {
      const m = r.weekKey.match(/^(\d{4})-W(\d{2})$/);
      return m ? Number(m[1]) : null;
    })
    .filter((y): y is number => y !== null);
  const year = recordYears.length > 0 ? Math.max(...recordYears) : tempUtc.getUTCFullYear();

  const byWeekNo = new Map<number, WeeklyQualityRecord>();
  validRecords.forEach(r => {
    const m = r.weekKey.match(/^(\d{4})-W(\d{2})$/);
    if (m && Number(m[1]) === year) {
      const wNo = Number(m[2]);
      if (wNo >= 32) {
        byWeekNo.set(wNo, r);
      }
    }
  });

  const maxRecordWeek = byWeekNo.size > 0 ? Math.max(...Array.from(byWeekNo.keys())) : 32;
  const startWeek = 32;
  const endWeek = Math.min(52, Math.max(44, currentIsoWeek >= 32 ? currentIsoWeek : 44, maxRecordWeek));

  const slots = Array.from({ length: endWeek - startWeek + 1 }, (_, idx) => {
    const weekNo = startWeek + idx;
    const weekKey = `${year}-W${String(weekNo).padStart(2, '0')}`;
    const rec = byWeekNo.get(weekNo);
    return {
      weekNo,
      weekKey,
      bsOqlPct: rec?.bsOqlPct ?? null,
      nflAqcPct: rec?.nflAqcPct ?? null
    };
  });

  const allValues = [
    commitment,
    ...slots.flatMap(s => [s.bsOqlPct, s.nflAqcPct]).filter((v): v is number => v !== null)
  ];
  const minVal = Math.max(0, Math.floor(Math.min(...allValues) - 1.2));
  const maxVal = Math.max(minVal + 2, Math.ceil(Math.max(...allValues) + 1.2));
  const span = Math.max(1, maxVal - minVal);

  const step = compact ? 56 : 74;
  const padL = compact ? 34 : 44;
  const padR = compact ? 110 : 134;
  const top = compact ? 22 : 38;
  const plotH = compact ? 100 : 180;
  const bottom = compact ? 24 : 38;
  const lastSlotX = padL + step * Math.max(0, slots.length - 1);
  const W = Math.max(480, lastSlotX + padR);
  const H = top + plotH + bottom;

  const x = (i: number) => (slots.length === 1 ? (W - padR) / 2 : padL + i * step);
  const y = (v: number) => top + plotH - ((v - minVal) / span) * plotH;

  // Compute non-overlapping label positions in the empty space above the top point and below the bottom point
  const labelYFor = (i: number, key: 'bs' | 'aqc'): number => {
    const s = slots[i];
    const bs = s.bsOqlPct;
    const aqc = s.nflAqcPct;
    const v = key === 'bs' ? bs : aqc;
    if (v === null) return 0;
    if (bs !== null && aqc !== null) {
      const isBsUpper = bs > aqc || (bs === aqc && key === 'bs');
      const isThisUpper = (key === 'bs' && isBsUpper) || (key === 'aqc' && !isBsUpper);
      return isThisUpper ? y(v) - 14 : y(v) + 22;
    }
    return y(v) - 14;
  };

  const bsPts = slots
    .map((s, i) => (s.bsOqlPct !== null ? { i, v: s.bsOqlPct } : null))
    .filter((p): p is { i: number; v: number } => p !== null);
  const aqcPts = slots
    .map((s, i) => (s.nflAqcPct !== null ? { i, v: s.nflAqcPct } : null))
    .filter((p): p is { i: number; v: number } => p !== null);

  return (
    <div className="nfl-scroll overflow-x-auto pb-1">
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', minWidth: W, height: H }}>
        {/* Subtle vertical week slot guides from W32 so empty weeks clearly show their reserved space */}
        {slots.map((s, i) => {
          const hasData = s.bsOqlPct !== null || s.nflAqcPct !== null;
          return (
            <g key={s.weekKey}>
              <line
                x1={x(i)}
                x2={x(i)}
                y1={top}
                y2={top + plotH}
                stroke={WB_THEME.LINE}
                strokeWidth={1}
                strokeDasharray={hasData ? '2 4' : '3 5'}
                opacity={hasData ? 0.45 : 0.3}
              />
              {!hasData && (
                <text
                  x={x(i)}
                  y={top + plotH - 8}
                  textAnchor="middle"
                  fontSize={10}
                  fontWeight={700}
                  fill={WB_THEME.MUTED}
                  opacity={0.65}
                >
                  —
                </text>
              )}
            </g>
          );
        })}

        {/* Red dashed commitment line + label placed in the dedicated empty right margin */}
        <line
          x1={padL - 16}
          x2={lastSlotX + 14}
          y1={y(commitment)}
          y2={y(commitment)}
          stroke={WB_THEME.RED}
          strokeWidth={2}
          strokeDasharray="6 5"
        />
        <text
          x={lastSlotX + 20}
          y={y(commitment) + 4}
          textAnchor="start"
          fontSize={11}
          fontWeight={800}
          fill={WB_THEME.RED}
          style={{ paintOrder: 'stroke', stroke: 'var(--wb-panel)', strokeWidth: 4, strokeLinejoin: 'round' }}
        >
          {commitment}% commitment
        </text>

        {/* Bottom axis line */}
        <line x1={padL - 16} x2={lastSlotX + 14} y1={top + plotH} y2={top + plotH} stroke={WB_THEME.LINE} strokeWidth={1.5} />

        {/* Polylines drawn first so value labels always render cleanly on top */}
        {bsPts.length > 1 && (
          <polyline
            fill="none"
            stroke={WB_THEME.BLUE}
            strokeWidth={3}
            strokeLinejoin="round"
            strokeLinecap="round"
            points={bsPts.map(p => `${x(p.i)},${y(p.v)}`).join(' ')}
          />
        )}
        {aqcPts.length > 1 && (
          <polyline
            fill="none"
            stroke={WB_THEME.ORANGE}
            strokeWidth={3}
            strokeLinejoin="round"
            strokeLinecap="round"
            points={aqcPts.map(p => `${x(p.i)},${y(p.v)}`).join(' ')}
          />
        )}

        {/* Dots and non-overlapping labels in empty space above/below */}
        {bsPts.map(p => (
          <g key={`bs-${p.i}`}>
            <circle cx={x(p.i)} cy={y(p.v)} r={5} fill={WB_THEME.BLUE} stroke="var(--wb-panel)" strokeWidth={1.5} />
            <text
              x={x(p.i)}
              y={labelYFor(p.i, 'bs')}
              textAnchor="middle"
              fontSize={12}
              fontWeight={900}
              fill={WB_THEME.BLUE}
              style={{ paintOrder: 'stroke', stroke: 'var(--wb-panel)', strokeWidth: 4, strokeLinejoin: 'round' }}
            >
              {p.v.toFixed(1)}
            </text>
          </g>
        ))}

        {aqcPts.map(p => (
          <g key={`aqc-${p.i}`}>
            <circle cx={x(p.i)} cy={y(p.v)} r={5} fill={WB_THEME.ORANGE} stroke="var(--wb-panel)" strokeWidth={1.5} />
            <text
              x={x(p.i)}
              y={labelYFor(p.i, 'aqc')}
              textAnchor="middle"
              fontSize={12}
              fontWeight={900}
              fill={WB_THEME.ORANGE}
              style={{ paintOrder: 'stroke', stroke: 'var(--wb-panel)', strokeWidth: 4, strokeLinejoin: 'round' }}
            >
              {p.v.toFixed(1)}
            </text>
          </g>
        ))}

        {/* Week labels from W32 onwards */}
        {slots.map((s, i) => {
          const hasData = s.bsOqlPct !== null || s.nflAqcPct !== null;
          return (
            <text
              key={`lbl-${s.weekKey}`}
              x={x(i)}
              y={top + plotH + 22}
              textAnchor="middle"
              fontSize={12}
              fontWeight={hasData ? 800 : 700}
              fill={hasData ? WB_THEME.TXT : WB_THEME.MUTED}
            >
              W{String(s.weekNo).padStart(2, '0')}
            </text>
          );
        })}
      </svg>
      <div className="flex justify-center gap-4 text-xs font-bold" style={{ color: WB_THEME.MUTED }}>
        <span><span className="inline-block w-3 h-1 align-middle mr-1" style={{ backgroundColor: WB_THEME.BLUE }} />BS OQL (empty space below/above line)</span>
        <span><span className="inline-block w-3 h-1 align-middle mr-1" style={{ backgroundColor: WB_THEME.ORANGE }} />{isClientDemoSession() ? 'DEMO' : 'NFL'} AQC (from Week 32)</span>
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