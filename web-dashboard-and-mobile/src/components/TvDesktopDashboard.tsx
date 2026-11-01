import React, { useState, useEffect, useMemo } from 'react';
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
import { HourlyWallboardBucket } from '../services/calculationEngine';
import { formatHourSlot12h, isBiHourlyAuditSlot } from '../data/masterData';

export type WbPageId = 'PRODUCTION' | 'QUALITY' | 'ALL' | 'TABLE';

const WB_INTERVALS = [30, 60, 10, 15, 0];

const WB_THEME = {
  BG: '#0A1522',
  PANEL: '#101E30',
  LINE: '#1B2E45',
  TXT: '#EAF0F7',
  MUTED: '#8FA3B8',
  RED: '#FF6B6B',
  AMBER: '#F5B73B',
  GREEN: '#3DD68C',
  BLUE: '#5AA9FF',
  ORANGE: '#F59E6B',
  GREY: '#7C8B9E',
  DIM: '#2A4560'
};

interface TvDesktopDashboardProps {
  todayIso: string;
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
  todayIso,
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
  onOpenIncidentPanel
}) => {
  const [page, setPage] = useState<WbPageId>('ALL');
  const [intervalIdx, setIntervalIdx] = useState(0);
  const interval = WB_INTERVALS[intervalIdx];
  const [countdown, setCountdown] = useState(WB_INTERVALS[0]);
  const [deepDiveLine, setDeepDiveLine] = useState<LinePerformanceSummary | null>(null);

  // Auto-slide Production -> Quality -> All (pauses on Table or when interval === 0)
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (interval > 0 && page !== 'TABLE') {
        setCountdown(prev => {
          if (prev > 1) return prev - 1;
          setPage(curr => {
            if (curr === 'PRODUCTION') return 'QUALITY';
            if (curr === 'QUALITY') return 'ALL';
            if (curr === 'ALL') return 'PRODUCTION';
            return 'TABLE';
          });
          return interval;
        });
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [interval, page]);

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
      .filter(i => selectedFloor === 'All' || i.floor === selectedFloor)
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
      .filter(q => selectedFloor === 'All' || q.floor === selectedFloor)
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

  const isDateInSelectedPeriod = (date: string) => {
    if (!date) return false;
    if (selectedPeriod === 'Today') return date === todayIso;
    const d = new Date(`${date}T00:00:00Z`);
    const t = new Date(`${todayIso}T00:00:00Z`);
    const days = Math.round((t.getTime() - d.getTime()) / 86400000);
    if (selectedPeriod === 'Yesterday') return days === 1;
    if (selectedPeriod === 'Week') return days >= 0 && days <= 6;
    return date.slice(0, 7) === todayIso.slice(0, 7);
  };

  const auditRecords = useMemo(
    () => qualities.filter(q =>
      isBiHourlyAuditSlot(q.hourSlot) &&
      (selectedFloor === 'All' || q.floor === selectedFloor) &&
      isDateInSelectedPeriod(q.date)
    ),
    [qualities, selectedFloor, selectedPeriod, todayIso]
  );

  const auditLineStats = useMemo(() => lineSummaries.map(line => {
    const records = auditRecords.filter(q => q.lineId === line.lineId);
    const pass = records.filter(q =>
      q.checkedGarments > 0 &&
      (q.defectiveGarments / q.checkedGarments) * 100 <= kpis.oqlCommitmentPct
    ).length;
    return {
      lineId: line.lineId,
      pass,
      fail: records.length - pass,
      total: records.length,
      checked: records.reduce((sum, q) => sum + q.checkedGarments, 0)
    };
  }).filter(row => row.total > 0), [lineSummaries, auditRecords, kpis.oqlCommitmentPct]);

  const auditTopDefects = useMemo(() => {
    const counts: Record<string, number> = {};
    auditRecords.forEach(q => q.defects.forEach(d => {
      if (d.count > 0) counts[d.defectType] = (counts[d.defectType] || 0) + d.count;
    }));
    return Object.entries(counts)
      .map(([label, value]) => ({ label, value, text: String(value), color: WB_THEME.RED }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 5);
  }, [auditRecords]);

  const selectedLineAuditRecords = useMemo(() =>
    deepDiveLine ? auditRecords
      .filter(q => q.lineId === deepDiveLine.lineId)
      .sort((a, b) => a.date.localeCompare(b.date) || a.hourSlot.localeCompare(b.hourSlot)) : [],
    [auditRecords, deepDiveLine]
  );

  const selectedLineAuditByHour = useMemo(() => {
    const grouped: Record<string, { slot: string; count: number; pass: number; fail: number; checked: number; defective: number; defects: number }> = {};
    selectedLineAuditRecords.forEach(q => {
      const row = grouped[q.hourSlot] || { slot: q.hourSlot, count: 0, pass: 0, fail: 0, checked: 0, defective: 0, defects: 0 };
      const passed = q.checkedGarments > 0 &&
        (q.defectiveGarments / q.checkedGarments) * 100 <= kpis.oqlCommitmentPct;
      row.count += 1;
      row.pass += passed ? 1 : 0;
      row.fail += passed ? 0 : 1;
      row.checked += q.checkedGarments;
      row.defective += q.defectiveGarments;
      row.defects += q.totalDefects;
      grouped[q.hourSlot] = row;
    });
    return Object.values(grouped).sort((a, b) => a.slot.localeCompare(b.slot));
  }, [selectedLineAuditRecords, kpis.oqlCommitmentPct]);

  const selectedLineAuditTopDefects = useMemo(() => {
    const counts: Record<string, number> = {};
    selectedLineAuditRecords.forEach(q => q.defects.forEach(d => {
      if (d.count > 0) counts[d.defectType] = (counts[d.defectType] || 0) + d.count;
    }));
    return Object.entries(counts).map(([label, value]) => ({ label, value, text: String(value), color: WB_THEME.RED }))
      .sort((a, b) => b.value - a.value).slice(0, 5);
  }, [selectedLineAuditRecords]);

  const selectedLineIncidents = useMemo(() => {
    if (!deepDiveLine) return [];
    return allIncidents
      .filter(i => i.lineId === deepDiveLine.lineId && isDateInSelectedPeriod(
        new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(new Date(i.startTimeMillis))
      ))
      .sort((a, b) => b.startTimeMillis - a.startTimeMillis);
  }, [allIncidents, deepDiveLine, selectedPeriod, todayIso]);

  const selectedLineProduction = useMemo(() => deepDiveLine
    ? productions.filter(p => p.lineId === deepDiveLine.lineId && isDateInSelectedPeriod(p.date))
      .sort((a, b) => b.timestamp - a.timestamp)
    : [], [productions, deepDiveLine, selectedPeriod, todayIso]);

  const selectedLineHourlyQuality = useMemo(() => deepDiveLine
    ? qualities.filter(q => q.lineId === deepDiveLine.lineId && !isBiHourlyAuditSlot(q.hourSlot) && isDateInSelectedPeriod(q.date))
      .sort((a, b) => b.timestamp - a.timestamp)
    : [], [qualities, deepDiveLine, selectedPeriod, todayIso]);

  return (
    <div
      style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.TXT }}
      className="min-h-[calc(100vh-42px)] flex flex-col p-2.5 gap-2 select-none font-sans tabular-nums"
    >
      {/* ==================== HEADER BAR (Matches TvDashboardScreen.kt) ==================== */}
      <div className="flex flex-wrap items-center justify-between gap-2 min-h-[40px]">
        <div className="flex items-center gap-2">
          <div className="bg-white text-[#002D62] px-2.5 py-1 rounded flex items-center gap-1.5 font-black text-sm shadow">
            <span>🏭</span>
            <span>NFL LIVE</span>
          </div>
          <span className="text-xs font-bold" style={{ color: WB_THEME.MUTED }}>
            TV Wallboard &amp; Command Center
          </span>
        </div>

        {/* 4-Page Segment: Production | Quality | All | Table */}
        <div
          style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
          className="flex items-center p-0.5 rounded-md border gap-0.5"
        >
          {(
            [
              { id: 'PRODUCTION', label: 'Production' },
              { id: 'QUALITY', label: 'Quality' },
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
                color: page === item.id ? '#031225' : WB_THEME.MUTED
              }}
              className="px-3 py-1 rounded text-xs font-black transition"
            >
              {item.label}
            </button>
          ))}
        </div>

        {/* Floor Segment: Sew 1 | Sew 2 | Sew 3 | All Floors */}
        <div
          style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
          className="flex items-center p-0.5 rounded-md border gap-0.5"
        >
          {(['Sew 1', 'Sew 2', 'Sew 3', 'All'] as FloorId[]).map(fl => (
            <button
              key={fl}
              onClick={() => onSelectFloor(fl)}
              style={{
                backgroundColor: selectedFloor === fl ? WB_THEME.BLUE : 'transparent',
                color: selectedFloor === fl ? '#031225' : WB_THEME.MUTED
              }}
              className="px-3 py-1 rounded text-xs font-black transition"
            >
              {fl === 'All' ? 'All Floors' : fl}
            </button>
          ))}
        </div>

        {/* Period Segment: Today | Yesterday | Week | Month */}
        <div
          style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE }}
          className="flex items-center p-0.5 rounded-md border gap-0.5"
        >
          {(['Today', 'Yesterday', 'Week', 'Month'] as PeriodId[]).map(p => (
            <button
              key={p}
              onClick={() => onSelectPeriod(p)}
              style={{
                backgroundColor: selectedPeriod === p ? WB_THEME.BLUE : 'transparent',
                color: selectedPeriod === p ? '#031225' : WB_THEME.MUTED
              }}
              className="px-2.5 py-1 rounded text-xs font-black transition"
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* ==================== STATUS STRIP ==================== */}
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="font-extrabold text-sm mr-1">
            {selectedFloor === 'All' ? 'All Floors' : selectedFloor} ·{' '}
            {page === 'PRODUCTION'
              ? 'Production'
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
                style={{ backgroundColor: WB_THEME.GREEN, color: '#052E22' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {onTargetCount} on target
              </span>
              <span
                style={{ backgroundColor: WB_THEME.AMBER, color: '#3B2A00' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {slightBehindCount} slightly behind
              </span>
              <span
                style={{ backgroundColor: WB_THEME.RED, color: '#3B0A0A' }}
                className="px-2 py-0.5 rounded font-black text-[11px]"
              >
                {farBehindCount} far behind
              </span>
              <span
                style={{ backgroundColor: WB_THEME.GREY, color: '#0F172A' }}
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
              {(['PRODUCTION', 'QUALITY', 'ALL'] as WbPageId[]).map(p => (
                <span
                  key={p}
                  style={{ backgroundColor: page === p ? WB_THEME.BLUE : WB_THEME.DIM }}
                  className="w-5 h-1.5 rounded-sm inline-block"
                />
              ))}
            </div>
          </button>

          {isDemoMode && (
            <button
              onClick={onToggleDemo}
              style={{ backgroundColor: WB_THEME.RED, color: '#3B0A0A' }}
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

      {/* ==================== PAGE 1: PRODUCTION ==================== */}
      {page === 'PRODUCTION' && (
        <div className="flex-1 flex flex-col gap-2">
          {/* 5 KPI Tiles */}
          <div className="grid grid-cols-2 md:grid-cols-6 gap-2">
            <WbTile
              label={`TODAY EFFICIENCY · target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
              value={kpis.reportedLinesCount > 0 ? `${kpis.todayEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.reportedLinesCount > 0 ? kpis.todayEfficiencyPct : null, kpis.targetEfficiencyPct)}
              sub="Current-day production efficiency"
            />
            <WbTile
              label="MONTHLY UPDATED EFFICIENCY"
              value={kpis.monthlyEfficiencyPct > 0 ? `${kpis.monthlyEfficiencyPct.toFixed(1)}%` : '—'}
              color={effColor(kpis.monthlyEfficiencyPct, kpis.targetEfficiencyPct)}
              sub={`Month-to-date · Target ${kpis.targetEfficiencyPct.toFixed(0)}%`}
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 flex-1 min-h-[220px]">
            <WbPanel
              title="Live Target vs Achievement"
              sub="Column: target (grey) vs output (status) · % below"
              right={
                <div className="flex gap-1">
                  <WbBadge text="≥95%" bg={WB_THEME.GREEN} fg="#052E22" />
                  <WbBadge text="80-95%" bg={WB_THEME.AMBER} fg="#3B2A00" />
                  <WbBadge text="<80%" bg={WB_THEME.RED} fg="#3B0A0A" />
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
            <WbPanel
              title="Hourly Output vs Target"
              sub="12-Hour AM/PM slots (8 AM – 6 PM) · Target (back) vs Output (front)"
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
                  fg={openIncidents.length > 0 ? '#3B0A0A' : '#052E22'}
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
      {page === 'QUALITY' && (
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 flex-1 min-h-[220px]">
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
            <WbPanel
              title="Hourly DHU and OQL"
              sub="12-Hour AM/PM trend · Orange: DHU · Blue: OQL%"
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
                  fg="#052E22"
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

          {/* Row 3: Bi-hourly audit analytics */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 min-h-[220px]">
            <WbPanel
              title="Bi-hourly Audit by Line · Pass / Fail"
              sub={`Audits ${auditRecords.length} · Pass/Fail derived from audit OQL ≤ ${kpis.oqlCommitmentPct.toFixed(2)}% · Select a line to open its full audit details`}
              className="lg:col-span-7"
            >
              <BiHourlyAuditByLineChart rows={auditLineStats} onSelectLine={lineId => {
                const line = lineSummaries.find(item => item.lineId === lineId);
                if (line) setDeepDiveLine(line);
              }} />
            </WbPanel>
            <WbPanel
              title="Top 5 Bi-hourly Audit Defects"
              sub="Defect quantity from saved audit records only"
              className="lg:col-span-5"
            >
              <HorizontalRankBars rows={auditTopDefects} />
            </WbPanel>
          </div>
        </div>
      )}

      {/* ==================== PAGE 3: ALL (EXECUTIVE COMBINED WALLBOARD) ==================== */}
      {page === 'ALL' && (
        <div className="flex-1 flex flex-col gap-2">
          {/* Top 7 Summary Panels */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2">
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
                <div className="w-full h-1.5 rounded bg-[#0A1522] overflow-hidden my-1">
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
            <WbSummaryCard title="Floor & Factory Eff">
              <div className="flex items-end justify-between h-16 pt-2 px-1">
                {(floorBenchmarks.length > 0
                  ? floorBenchmarks
                  : [
                      { floor: 'Sew 1', todayEff: 76, monthEff: 72 },
                      { floor: 'Sew 2', todayEff: kpis.todayEfficiencyPct || 68, monthEff: kpis.monthlyEfficiencyPct || 65 },
                      { floor: 'Sew 3', todayEff: 71, monthEff: 68 },
                      { floor: 'Factory', todayEff: 72, monthEff: 69 }
                    ]
                ).map(b => (
                  <div key={b.floor} className="flex flex-col items-center gap-0.5">
                    <div className="flex items-end gap-0.5 h-10">
                      <div
                        style={{
                          height: `${Math.min(100, Math.max(10, b.todayEff))}%`,
                          backgroundColor: WB_THEME.BLUE
                        }}
                        className="w-2.5 rounded-t-sm"
                        title={`Today: ${b.todayEff}%`}
                      />
                      <div
                        style={{
                          height: `${Math.min(100, Math.max(10, b.monthEff))}%`,
                          backgroundColor: WB_THEME.GREEN
                        }}
                        className="w-2.5 rounded-t-sm"
                        title={`Month: ${b.monthEff}%`}
                      />
                    </div>
                    <span style={{ color: WB_THEME.MUTED }} className="text-[9px] font-bold">
                      {b.floor}
                    </span>
                  </div>
                ))}
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
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
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-2 flex-1 min-h-[210px]">
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
                  <WbBadge text="BS OQL" bg={WB_THEME.BLUE} fg="#031225" />
                  <WbBadge text="NFL AQC" bg={WB_THEME.ORANGE} fg="#3B1A00" />
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
      {page === 'TABLE' && (
        <div className="flex-1 bg-slate-100 text-slate-900 rounded-lg overflow-auto p-3">
          <div className="flex items-center justify-between mb-2">
            <div>
              <h3 className="text-sm font-black text-[#002D62]">
                Live Line Performance Table ({selectedFloor === 'All' ? 'All Floors' : selectedFloor} · {selectedPeriod})
              </h3>
              <p className="text-[11px] text-slate-500">
                Click any line row to open its 12-Hour AM/PM Line Deep Dive &amp; Rectification Ledger
              </p>
            </div>
          </div>
          <table className="w-full text-xs border-collapse bg-white rounded-lg overflow-hidden shadow">
            <thead>
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
                  <td className="py-1.5 px-2 font-black text-[#002D62]">{l.lineId}</td>
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
        <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-2 sm:p-4">
          <div
            style={{ backgroundColor: WB_THEME.PANEL, borderColor: WB_THEME.LINE, color: WB_THEME.TXT }}
            className="w-full max-w-6xl max-h-[96vh] rounded-xl border p-3 sm:p-5 flex flex-col gap-3 overflow-y-auto shadow-2xl"
          >
            <div className="flex items-center justify-between border-b pb-3" style={{ borderColor: WB_THEME.LINE }}>
              <div>
                <h3 className="text-lg font-black">
                  Line {deepDiveLine.lineId} Deep Dive ({deepDiveLine.floor}) — {deepDiveLine.styleNumber}
                </h3>
                <p style={{ color: WB_THEME.MUTED }} className="text-xs">
                  SMV {deepDiveLine.smv.toFixed(1)} · Operators {deepDiveLine.operators} · Helpers {deepDiveLine.helpers} · Daily Target {deepDiveLine.target} pcs ({deepDiveLine.hourlyTarget}/hr)
                  · Period {selectedPeriod}
                </p>
              </div>
              <button
                onClick={() => setDeepDiveLine(null)}
                className="px-3 py-1 rounded bg-slate-800 hover:bg-slate-700 text-white font-bold text-sm"
              >
                ✕ Close
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
              <WbTile
                label={`${selectedPeriod.toUpperCase()} EFFICIENCY`}
                value={deepDiveLine.efficiencyPct !== null ? `${deepDiveLine.efficiencyPct.toFixed(1)}%` : '—'}
                color={effColor(deepDiveLine.efficiencyPct, deepDiveLine.targetEfficiencyPct)}
                sub={`Target ${deepDiveLine.targetEfficiencyPct}%`}
              />
              <WbTile
                label="MONTHLY EFFICIENCY"
                value={deepDiveLine.monthlyEfficiencyPct > 0 ? `${deepDiveLine.monthlyEfficiencyPct.toFixed(1)}%` : '—'}
                color={effColor(deepDiveLine.monthlyEfficiencyPct, deepDiveLine.targetEfficiencyPct)}
                sub="Actual month production calculation"
              />
              <WbTile
                label="OUTPUT vs TARGET"
                value={`${deepDiveLine.output ?? 0}`}
                unit={`/ ${deepDiveLine.target}`}
                color={achColor(deepDiveLine.achievementPct)}
                sub={`Achievement ${deepDiveLine.achievementPct ?? 0}%`}
              />
              <WbTile
                label="DAY DHU"
                value={deepDiveLine.dhu !== null ? `${deepDiveLine.dhu.toFixed(2)}%` : '—'}
                color={dhuColor(deepDiveLine.dhu)}
                sub={`${deepDiveLine.totalDefects} defects / ${deepDiveLine.checkedGarments} checked`}
              />
              <WbTile
                label="DAY OQL"
                value={deepDiveLine.todayOqlPct !== null ? `${deepDiveLine.todayOqlPct.toFixed(2)}%` : '—'}
                color={oqlColor(deepDiveLine.todayOqlPct)}
                sub={`${deepDiveLine.defectiveGarments} defective pcs`}
              />
              <WbTile
                label="DEFECTS PENDING"
                value={`${deepDiveLine.sewBal + deepDiveLine.spotBal}`}
                unit="pcs"
                color={deepDiveLine.sewBal + deepDiveLine.spotBal > 0 ? WB_THEME.AMBER : WB_THEME.GREEN}
                sub={`Sew ${deepDiveLine.sewBal} · Spot ${deepDiveLine.spotBal}`}
              />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
              <WbPanel title="Line Quality Performance & Rectification" sub="Actual selected-period records · no sample/fallback values">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                  <div className="rounded p-2" style={{ backgroundColor: WB_THEME.BG }}>
                    <div style={{ color: WB_THEME.MUTED }}>Sew Defect Sent</div>
                    <div className="text-xl font-black">{deepDiveLine.sewSent}</div>
                  </div>
                  <div className="rounded p-2" style={{ backgroundColor: WB_THEME.BG }}>
                    <div style={{ color: WB_THEME.MUTED }}>Sew Defect Received</div>
                    <div className="text-xl font-black" style={{ color: WB_THEME.GREEN }}>{deepDiveLine.sewRecv}</div>
                  </div>
                  <div className="rounded p-2" style={{ backgroundColor: WB_THEME.BG }}>
                    <div style={{ color: WB_THEME.MUTED }}>Spot Sent</div>
                    <div className="text-xl font-black">{deepDiveLine.spotSent}</div>
                  </div>
                  <div className="rounded p-2" style={{ backgroundColor: WB_THEME.BG }}>
                    <div style={{ color: WB_THEME.MUTED }}>Spot Received</div>
                    <div className="text-xl font-black" style={{ color: WB_THEME.GREEN }}>{deepDiveLine.spotRecv}</div>
                  </div>
                </div>
                <div className="mt-2">
                  <div className="text-xs font-black mb-1">Top 5 Hourly QC Defects</div>
                  <HorizontalRankBars rows={(() => {
                    const counts: Record<string, number> = {};
                    selectedLineHourlyQuality.forEach(q => q.defects.forEach(d => {
                      if (d.count > 0) counts[d.defectType] = (counts[d.defectType] || 0) + d.count;
                    }));
                    return Object.entries(counts).map(([label, value]) => ({
                      label, value, text: String(value), color: WB_THEME.ORANGE
                    })).sort((a, b) => b.value - a.value).slice(0, 5);
                  })()} />
                </div>
              </WbPanel>

              <WbPanel
                title="Line NPT Notifications & Stoppages"
                sub={`${selectedLineIncidents.length} incident(s) recorded for this line and period`}
              >
                {selectedLineIncidents.length === 0 ? (
                  <div className="flex items-center justify-center h-full text-xs font-bold" style={{ color: WB_THEME.MUTED }}>
                    No NPT incidents recorded for this line and period
                  </div>
                ) : (
                  <div className="flex flex-col gap-2 overflow-y-auto max-h-48">
                    {selectedLineIncidents.map(inc => {
                      const elapsed = Math.max(0, Math.round(((inc.endTimeMillis || Date.now()) - inc.startTimeMillis) / 60000));
                      return (
                        <div key={inc.id} className="rounded border-l-4 p-2 text-xs" style={{ backgroundColor: WB_THEME.BG, borderColor: inc.status === 'RESOLVED' ? WB_THEME.GREEN : WB_THEME.RED }}>
                          <div className="flex justify-between gap-2 font-black">
                            <span>{inc.category.replace(/_/g, ' ')} · {inc.status}</span>
                            <span>{elapsed} min</span>
                          </div>
                          <div style={{ color: WB_THEME.TXT }}>{inc.description}</div>
                          <div style={{ color: WB_THEME.MUTED }} className="mt-1">
                            {new Date(inc.startTimeMillis).toLocaleString('en-GB', { timeZone: 'Asia/Dhaka' })} · {inc.reportedBy}
                          </div>
                          {inc.resolutionNote && <div style={{ color: WB_THEME.GREEN }}>Resolution: {inc.resolutionNote}</div>}
                        </div>
                      );
                    })}
                  </div>
                )}
              </WbPanel>
            </div>

            <WbPanel
              title="Bi-hourly Audit — Hour-wise Pass / Fail"
              sub={`Total ${selectedLineAuditRecords.length} audits · ${selectedLineAuditByHour.reduce((s, r) => s + r.pass, 0)} pass · ${selectedLineAuditByHour.reduce((s, r) => s + r.fail, 0)} fail · Pass standard: OQL ≤ ${kpis.oqlCommitmentPct.toFixed(2)}%`}
            >
              <div className="overflow-x-auto">
                <table className="w-full text-xs border-collapse min-w-[560px]">
                  <thead>
                    <tr style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.MUTED }}>
                      <th className="p-2 text-left">Audit Slot</th>
                      <th className="p-2 text-right">Audit Count</th>
                      <th className="p-2 text-right">Checked Pcs</th>
                      <th className="p-2 text-right">Defective Pcs</th>
                      <th className="p-2 text-right">Total Defects</th>
                      <th className="p-2 text-right">Pass</th>
                      <th className="p-2 text-right">Fail</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selectedLineAuditByHour.map(row => (
                      <tr key={row.slot} className="border-b" style={{ borderColor: WB_THEME.LINE }}>
                        <td className="p-2 font-black">{formatHourSlot12h(row.slot)}</td>
                        <td className="p-2 text-right">{row.count}</td>
                        <td className="p-2 text-right">{row.checked}</td>
                        <td className="p-2 text-right">{row.defective}</td>
                        <td className="p-2 text-right">{row.defects}</td>
                        <td className="p-2 text-right font-black" style={{ color: WB_THEME.GREEN }}>{row.pass}</td>
                        <td className="p-2 text-right font-black" style={{ color: WB_THEME.RED }}>{row.fail}</td>
                      </tr>
                    ))}
                    {selectedLineAuditByHour.length === 0 && (
                      <tr><td colSpan={7} className="p-4 text-center" style={{ color: WB_THEME.MUTED }}>No bi-hourly audit records for this line and period</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </WbPanel>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
              <WbPanel title="Top 5 Bi-hourly Audit Defects" sub="Counts from this line's saved bi-hourly audit records">
                <HorizontalRankBars rows={selectedLineAuditTopDefects} />
              </WbPanel>
              <WbPanel title="Audit Record Detail" sub="Each audit slot, checked quantity, OQL result and pass/fail status">
                <div className="overflow-x-auto">
                  <table className="w-full text-xs border-collapse min-w-[500px]">
                    <thead><tr style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.MUTED }}>
                      <th className="p-2 text-left">Date / Slot</th>
                      <th className="p-2 text-right">Checked</th>
                      <th className="p-2 text-right">Defective</th>
                      <th className="p-2 text-right">OQL</th>
                      <th className="p-2 text-right">Result</th>
                    </tr></thead>
                    <tbody>
                      {selectedLineAuditRecords.map(q => {
                        const auditOql = q.checkedGarments > 0 ? (q.defectiveGarments / q.checkedGarments) * 100 : null;
                        const passed = auditOql !== null && auditOql <= kpis.oqlCommitmentPct;
                        return (
                          <tr key={q.id} className="border-b" style={{ borderColor: WB_THEME.LINE }}>
                            <td className="p-2 font-bold">{q.date} · {formatHourSlot12h(q.hourSlot)}</td>
                            <td className="p-2 text-right">{q.checkedGarments}</td>
                            <td className="p-2 text-right">{q.defectiveGarments}</td>
                            <td className="p-2 text-right">{auditOql !== null ? `${auditOql.toFixed(2)}%` : '—'}</td>
                            <td className="p-2 text-right font-black" style={{ color: passed ? WB_THEME.GREEN : WB_THEME.RED }}>{passed ? 'PASS' : 'FAIL'}</td>
                          </tr>
                        );
                      })}
                      {selectedLineAuditRecords.length === 0 && (
                        <tr><td colSpan={5} className="p-4 text-center" style={{ color: WB_THEME.MUTED }}>No audit records</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </WbPanel>
            </div>

            <WbPanel title="Hourly Production & Quality Records" sub="Latest records for the selected line and period">
              <div className="overflow-x-auto">
                <table className="w-full text-xs border-collapse min-w-[650px]">
                  <thead><tr style={{ backgroundColor: WB_THEME.BG, color: WB_THEME.MUTED }}>
                    <th className="p-2 text-left">Date / Time</th>
                    <th className="p-2 text-left">Type</th>
                    <th className="p-2 text-right">Output</th>
                    <th className="p-2 text-right">Checked</th>
                    <th className="p-2 text-right">Defective</th>
                    <th className="p-2 text-right">Defects</th>
                    <th className="p-2 text-right">Sew Sent / Recv</th>
                    <th className="p-2 text-right">Spot Sent / Recv</th>
                  </tr></thead>
                  <tbody>
                    {selectedLineProduction.map(p => {
                      const qa = selectedLineHourlyQuality.find(q => q.date === p.date && q.hourSlot === p.hourSlot);
                      return (
                        <tr key={p.id} className="border-b" style={{ borderColor: WB_THEME.LINE }}>
                          <td className="p-2 font-black">{p.date} · {formatHourSlot12h(p.hourSlot)}</td>
                          <td className="p-2">Production / hourly QC</td>
                          <td className="p-2 text-right font-black" style={{ color: WB_THEME.GREEN }}>{p.outputQty}</td>
                          <td className="p-2 text-right">{qa?.checkedGarments ?? '—'}</td>
                          <td className="p-2 text-right">{qa?.defectiveGarments ?? '—'}</td>
                          <td className="p-2 text-right">{qa?.totalDefects ?? '—'}</td>
                          <td className="p-2 text-right">{qa ? `${qa.defectSent || 0} / ${qa.defectReceived || 0}` : '—'}</td>
                          <td className="p-2 text-right">{qa ? `${qa.spotSent || 0} / ${qa.spotReceived || 0}` : '—'}</td>
                        </tr>
                      );
                    })}
                    {selectedLineProduction.length === 0 && selectedLineHourlyQuality.map(q => (
                      <tr key={q.id} className="border-b" style={{ borderColor: WB_THEME.LINE }}>
                        <td className="p-2 font-black">{q.date} · {formatHourSlot12h(q.hourSlot)}</td>
                        <td className="p-2">Hourly QC</td>
                        <td className="p-2 text-right">—</td>
                        <td className="p-2 text-right">{q.checkedGarments}</td>
                        <td className="p-2 text-right">{q.defectiveGarments}</td>
                        <td className="p-2 text-right">{q.totalDefects}</td>
                        <td className="p-2 text-right">{q.defectSent || 0} / {q.defectReceived || 0}</td>
                        <td className="p-2 text-right">{q.spotSent || 0} / {q.spotReceived || 0}</td>
                      </tr>
                    ))}
                    {selectedLineProduction.length === 0 && selectedLineHourlyQuality.length === 0 && (
                      <tr><td colSpan={8} className="p-4 text-center" style={{ color: WB_THEME.MUTED }}>No hourly production or quality records for this line and period</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </WbPanel>
          </div>
        </div>
      )}
    </div>
  );
};

/* ==================== SUB-COMPONENTS MATCHING WbComponents.kt ==================== */

const BiHourlyAuditByLineChart: React.FC<{
  rows: Array<{ lineId: string; pass: number; fail: number; total: number; checked: number }>;
  onSelectLine: (lineId: string) => void;
}> = ({ rows, onSelectLine }) => {
  if (rows.length === 0) {
    return (
      <div style={{ color: WB_THEME.MUTED }} className="flex items-center justify-center h-full text-xs font-bold">
        No bi-hourly audit records for this floor and period
      </div>
    );
  }
  const maxTotal = Math.max(1, ...rows.map(row => row.total));
  return (
    <div className="flex items-end gap-3 h-full min-h-[155px] overflow-x-auto px-1 pt-2">
      {rows.map(row => {
        const totalHeight = Math.max(8, (row.total / maxTotal) * 100);
        const passHeight = row.total > 0 ? (row.pass / row.total) * totalHeight : 0;
        const failHeight = row.total > 0 ? (row.fail / row.total) * totalHeight : 0;
        return (
          <button
            key={row.lineId}
            type="button"
            onClick={() => onSelectLine(row.lineId)}
            className="flex flex-col items-center justify-end min-w-[48px] flex-1 h-full hover:opacity-80"
            title={`Line ${row.lineId}: ${row.total} audits, ${row.pass} pass, ${row.fail} fail, ${row.checked} pcs checked`}
          >
            <span className="text-[10px] font-black mb-1">{row.total}</span>
            <div className="h-[105px] w-7 flex flex-col justify-end rounded-t overflow-hidden border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div style={{ height: `${failHeight}%`, backgroundColor: WB_THEME.RED }} />
              <div style={{ height: `${passHeight}%`, backgroundColor: WB_THEME.GREEN }} />
            </div>
            <span className="text-[10px] font-black mt-1">{row.lineId}</span>
            <span className="text-[8px] font-bold whitespace-nowrap" style={{ color: WB_THEME.MUTED }}>
              <span style={{ color: WB_THEME.GREEN }}>{row.pass}P</span>
              {' / '}
              <span style={{ color: WB_THEME.RED }}>{row.fail}F</span>
            </span>
          </button>
        );
      })}
    </div>
  );
};

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
    <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-extrabold uppercase tracking-wider pl-1.5">
      {label}
    </div>
    <div className="flex items-baseline gap-1.5 my-1 pl-1.5">
      <span style={{ color }} className="text-2xl font-black leading-none">
        {value}
      </span>
      {unit && (
        <span style={{ color: WB_THEME.MUTED }} className="text-xs font-bold">
          {unit}
        </span>
      )}
    </div>
    <div style={{ color: WB_THEME.MUTED }} className="text-[10px] font-semibold pl-1.5 truncate">
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
    <div className="flex items-center justify-between gap-1">
      <span className="text-[11px] font-extrabold truncate">{title}</span>
      {badge && (
        <span
          style={{ backgroundColor: badgeColor || WB_THEME.BLUE, color: '#031225' }}
          className="px-1.5 py-0.5 rounded text-[9px] font-black"
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
    <div className="flex-1 flex flex-col justify-end min-h-[140px]">{children}</div>
  </div>
);

const WbBadge: React.FC<{ text: string; bg: string; fg: string }> = ({ text, bg, fg }) => (
  <span style={{ backgroundColor: bg, color: fg }} className="px-1.5 py-0.5 rounded text-[9px] font-black">
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

const LiveTargetVsAchievementChart: React.FC<{
  lines: LinePerformanceSummary[];
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, onSelectLine }) => {
  const maxVal = Math.max(100, ...lines.map(l => Math.max(l.target, l.output ?? 0)));
  return (
    <div className="flex items-end justify-between gap-1 h-full pt-4 overflow-x-auto">
      {lines.map(l => {
        const tgtH = Math.round((l.target / maxVal) * 100);
        const outH = Math.round(((l.output ?? 0) / maxVal) * 100);
        const col = achColor(l.achievementPct);
        return (
          <div
            key={l.lineId}
            onClick={() => onSelectLine(l)}
            className="flex flex-col items-center flex-1 min-w-[26px] h-full justify-end cursor-pointer group"
          >
            <div className="text-[9px] font-black mb-0.5" style={{ color: col }}>
              {l.output ?? '—'}
            </div>
            <div className="w-full h-24 flex items-end justify-center gap-0.5 border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div
                style={{ height: `${tgtH}%`, backgroundColor: WB_THEME.DIM }}
                className="w-2 rounded-t-sm"
                title={`Target: ${l.target}`}
              />
              <div
                style={{ height: `${outH}%`, backgroundColor: col }}
                className="w-2.5 rounded-t-sm"
                title={`Output: ${l.output ?? 0}`}
              />
            </div>
            <div className="text-[9px] font-black mt-0.5">{l.lineId}</div>
            <div style={{ color: col }} className="text-[8px] font-bold">
              {l.achievementPct !== null ? `${l.achievementPct.toFixed(0)}%` : '—'}
            </div>
          </div>
        );
      })}
    </div>
  );
};

const EfficiencyByLineChart: React.FC<{
  lines: LinePerformanceSummary[];
  targetEff: number;
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, targetEff, onSelectLine }) => {
  return (
    <div className="relative flex items-end justify-between gap-1 h-full pt-4 overflow-x-auto">
      {/* Dashed Target Line */}
      <div
        style={{ bottom: `${Math.min(95, Math.max(15, targetEff))}%`, borderColor: WB_THEME.AMBER }}
        className="absolute left-0 right-0 border-t border-dashed pointer-events-none z-10"
      />
      {lines.map(l => {
        const eff = l.efficiencyPct ?? 0;
        const mo = l.monthlyEfficiencyPct ?? 0;
        const col = effColor(l.efficiencyPct, l.targetEfficiencyPct);
        return (
          <div
            key={l.lineId}
            onClick={() => onSelectLine(l)}
            className="flex flex-col items-center flex-1 min-w-[24px] h-full justify-end cursor-pointer"
          >
            <div style={{ color: col }} className="text-[9px] font-black mb-0.5">
              {l.efficiencyPct !== null ? `${eff.toFixed(0)}%` : '—'}
            </div>
            <div className="w-full h-24 flex items-end justify-center gap-0.5 border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div
                style={{ height: `${Math.min(100, mo)}%`, backgroundColor: WB_THEME.DIM }}
                className="w-2 rounded-t-sm"
              />
              <div
                style={{ height: `${Math.min(100, eff)}%`, backgroundColor: col }}
                className="w-2.5 rounded-t-sm"
              />
            </div>
            <div className="text-[9px] font-black mt-0.5">{l.lineId}</div>
          </div>
        );
      })}
    </div>
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
  const maxVal = Math.max(limitVal * 2, 8, ...lines.map(l => getValue(l) ?? 0));
  return (
    <div className="flex items-end justify-between gap-1 h-full pt-4 overflow-x-auto">
      {lines.map(l => {
        const val = getValue(l);
        const hPct = val !== null ? Math.min(100, Math.round((val / maxVal) * 100)) : 0;
        const col = getColor(val);
        return (
          <div
            key={l.lineId}
            onClick={() => onSelectLine(l)}
            className="flex flex-col items-center flex-1 min-w-[24px] h-full justify-end cursor-pointer"
          >
            <div style={{ color: col }} className="text-[9px] font-black mb-0.5">
              {val !== null ? formatVal(val) : '—'}
            </div>
            <div className="w-full h-24 flex items-end justify-center border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div style={{ height: `${hPct}%`, backgroundColor: col }} className="w-3 rounded-t-sm" />
            </div>
            <div className="text-[9px] font-black mt-0.5">{l.lineId}</div>
          </div>
        );
      })}
    </div>
  );
};

const HourlyOutputWallboardChart: React.FC<{ buckets: HourlyWallboardBucket[] }> = ({ buckets }) => {
  const maxVal = Math.max(100, ...buckets.map(b => Math.max(b.target, b.output)));
  return (
    <div className="flex items-end justify-between gap-1.5 h-full pt-3">
      {buckets.map(b => {
        const tgtH = Math.round((b.target / maxVal) * 100);
        const outH = Math.round((b.output / maxVal) * 100);
        const pct = b.target > 0 ? Math.round((b.output / b.target) * 100) : 0;
        const col = b.output === 0 ? WB_THEME.DIM : achColor(pct);
        return (
          <div key={b.slot24} className="flex flex-col items-center flex-1 h-full justify-end">
            <div style={{ color: col }} className="text-[9px] font-black mb-0.5">
              {b.output > 0 ? b.output : '—'}
            </div>
            <div className="w-full h-24 flex items-end justify-center gap-0.5 border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div style={{ height: `${tgtH}%`, backgroundColor: WB_THEME.DIM }} className="w-2.5 rounded-t-sm" />
              <div style={{ height: `${outH}%`, backgroundColor: col }} className="w-3 rounded-t-sm" />
            </div>
            <div className="text-[9px] font-black mt-0.5">{b.label12h}</div>
            <div style={{ color: col }} className="text-[8px] font-bold">
              {b.output > 0 && b.target > 0 ? `${pct}%` : ''}
            </div>
          </div>
        );
      })}
    </div>
  );
};

const HourlyQualityLineChart: React.FC<{ buckets: HourlyWallboardBucket[]; limit: number }> = ({
  buckets
}) => {
  return (
    <div className="flex items-end justify-between gap-1.5 h-full pt-3">
      {buckets.map(b => {
        const maxQ = 12;
        const dhuH = b.dhuPct !== null ? Math.min(100, Math.round((b.dhuPct / maxQ) * 100)) : 0;
        const oqlH = b.oqlPct !== null ? Math.min(100, Math.round((b.oqlPct / maxQ) * 100)) : 0;
        return (
          <div key={b.slot24} className="flex flex-col items-center flex-1 h-full justify-end">
            <div className="text-[8px] font-black mb-0.5" style={{ color: WB_THEME.BLUE }}>
              {b.oqlPct !== null ? `${b.oqlPct.toFixed(1)}%` : '—'}
            </div>
            <div className="w-full h-24 flex items-end justify-center gap-0.5 border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div
                style={{ height: `${dhuH}%`, backgroundColor: WB_THEME.ORANGE }}
                className="w-2.5 rounded-t-sm"
                title={`DHU: ${b.dhuPct ?? 0}%`}
              />
              <div
                style={{ height: `${oqlH}%`, backgroundColor: WB_THEME.BLUE }}
                className="w-2.5 rounded-t-sm"
                title={`OQL: ${b.oqlPct ?? 0}%`}
              />
            </div>
            <div className="text-[9px] font-black mt-0.5">{b.label12h}</div>
          </div>
        );
      })}
    </div>
  );
};

const RectificationPendingChart: React.FC<{
  lines: LinePerformanceSummary[];
  onSelectLine: (l: LinePerformanceSummary) => void;
}> = ({ lines, onSelectLine }) => {
  const pendingLines = lines.filter(l => l.sewBal + l.spotBal > 0).slice(0, 10);
  if (pendingLines.length === 0) {
    return (
      <div style={{ color: WB_THEME.GREEN }} className="flex items-center justify-center h-full text-xs font-black">
        ✓ Zero unreturned sewing or spot garments pending
      </div>
    );
  }
  const maxP = Math.max(5, ...pendingLines.map(l => l.sewBal + l.spotBal));
  return (
    <div className="flex items-end justify-around gap-1.5 h-full pt-3">
      {pendingLines.map(l => {
        const total = l.sewBal + l.spotBal;
        const sewH = Math.round((l.sewBal / maxP) * 100);
        const spotH = Math.round((l.spotBal / maxP) * 100);
        return (
          <div
            key={l.lineId}
            onClick={() => onSelectLine(l)}
            className="flex flex-col items-center flex-1 h-full justify-end cursor-pointer"
          >
            <div style={{ color: WB_THEME.AMBER }} className="text-[9px] font-black mb-0.5">
              {total}
            </div>
            <div className="w-full h-24 flex flex-col justify-end items-center border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div style={{ height: `${spotH}%`, backgroundColor: WB_THEME.BLUE }} className="w-3.5 rounded-t-sm" />
              <div style={{ height: `${sewH}%`, backgroundColor: WB_THEME.ORANGE }} className="w-3.5" />
            </div>
            <div className="text-[9px] font-black mt-0.5">{l.lineId}</div>
          </div>
        );
      })}
    </div>
  );
};

const WeeklyBsOqlChart: React.FC<{ records: WeeklyQualityRecord[]; commitment: number }> = ({
  records
}) => {
  if (records.length === 0) {
    return (
      <div style={{ color: WB_THEME.MUTED }} className="flex items-center justify-center h-full text-xs font-bold">
        No Weekly BS OQL / NFL AQC records entered yet
      </div>
    );
  }
  const maxVal = Math.max(10, ...records.map(r => Math.max(r.bsOqlPct ?? 0, r.nflAqcPct ?? 0)));
  return (
    <div className="flex items-end justify-between gap-2 h-full min-w-[520px] pt-2 overflow-x-auto">
      {records.map(r => {
        const bsH = r.bsOqlPct !== null ? Math.round((r.bsOqlPct / maxVal) * 100) : 0;
        const aqcH = r.nflAqcPct !== null ? Math.round((r.nflAqcPct / maxVal) * 100) : 0;
        return (
          <div key={r.weekKey} className="flex flex-col items-center flex-1 min-w-[48px] h-full justify-end">
            <div className="h-4 flex items-end justify-center text-[8px] font-black whitespace-nowrap" style={{ color: WB_THEME.BLUE }}>
              {r.bsOqlPct !== null ? `${r.bsOqlPct.toFixed(1)}%` : '—'}
            </div>
            <div className="w-full h-24 flex items-end justify-center gap-0.5 border-b" style={{ borderColor: WB_THEME.LINE }}>
              <div
                style={{ height: `${bsH}%`, backgroundColor: WB_THEME.BLUE }}
                className="w-2.5 rounded-t-sm"
                title={`BS OQL: ${r.bsOqlPct ?? '—'}%`}
              />
              <div
                style={{ height: `${aqcH}%`, backgroundColor: WB_THEME.ORANGE }}
                className="w-2.5 rounded-t-sm"
                title={`NFL AQC: ${r.nflAqcPct ?? '—'}%`}
              />
            </div>
            <div className="text-[9px] font-black mt-0.5">{r.weekKey.replace(/^\d{4}-/, '')}</div>
            <div className="h-4 flex items-start justify-center text-[8px] font-bold whitespace-nowrap" style={{ color: WB_THEME.ORANGE }}>
              {r.nflAqcPct !== null ? `${r.nflAqcPct.toFixed(1)}%` : '—'}
            </div>
          </div>
        );
      })}
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
            <span className="w-20 font-black truncate text-[11px]">{r.label}</span>
            <div className="flex-1 h-3 rounded bg-[#0A1522] overflow-hidden">
              <div style={{ width: `${pct}%`, backgroundColor: r.color }} className="h-full rounded" />
            </div>
            <span style={{ color: r.color }} className="w-16 text-right font-black text-[11px]">
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
            <div className="truncate pr-2">
              <div className="font-black">
                Line {inc.lineId} · {inc.category.replace(/_/g, ' ')}
              </div>
              <div style={{ color: WB_THEME.MUTED }} className="text-[10px] truncate">
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