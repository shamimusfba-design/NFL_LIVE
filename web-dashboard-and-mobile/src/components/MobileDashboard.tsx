import React, { useState } from 'react';
import {
  FloorId,
  FloorKpis,
  LinePerformanceSummary,
  IncidentRecord
} from '../types';

interface MobileDashboardProps {
  selectedFloor: FloorId;
  onSelectFloor: (floor: FloorId) => void;
  selectedPeriod: 'Today' | 'Week' | 'Month';
  onSelectPeriod: (p: 'Today' | 'Week' | 'Month') => void;
  kpis: FloorKpis;
  lineSummaries: LinePerformanceSummary[];
  topDefects: Array<{ defect: string; count: number; dhu: number }>;
  isDemoMode: boolean;
  onToggleDemo: () => void;
  openIncidents: IncidentRecord[];
  onOpenIncidentPanel: () => void;
  onNavigateToReports: () => void;
}

export const MobileDashboard: React.FC<MobileDashboardProps> = ({
  selectedFloor,
  onSelectFloor,
  selectedPeriod,
  onSelectPeriod,
  kpis,
  lineSummaries,
  topDefects,
  isDemoMode,
  onToggleDemo,
  openIncidents,
  onOpenIncidentPanel,
  onNavigateToReports
}) => {
  const [mobileSubTab, setMobileSubTab] = useState<'SUMMARY' | 'CHARTS' | 'TABLE'>('SUMMARY');
  const [expandedLine, setExpandedLine] = useState<string | null>('J1');
  const [tableFilter, setTableFilter] = useState<'ALL' | 'COMPLETE' | 'NOT_REPORTED'>('ALL');

  const filteredLines = lineSummaries.filter(l => {
    if (tableFilter === 'COMPLETE') return l.reportingStatus === 'Complete';
    if (tableFilter === 'NOT_REPORTED') return l.reportingStatus === 'Not reported';
    return true;
  });

  return (
    <div className="flex flex-col bg-[#F4F6F9] text-slate-800 pb-20">
      {/* Top Mobile Bar */}
      <div className="bg-[#0A2558] text-white px-4 pt-3 pb-3 shadow-md sticky top-0 z-30">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="bg-white text-[#0A2558] font-black text-xs px-2 py-0.5 rounded tracking-wider shadow-sm flex items-center gap-1">
              <span>NOS Live</span>
            </div>
            <div>
              <div className="text-xs font-bold leading-tight">Northern Operating System Live</div>
              <div className="text-[10px] text-blue-200">Industrial Factory MES • Live Monitoring</div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={onToggleDemo}
              className={`text-[10px] font-bold px-2 py-0.5 rounded transition ${
                isDemoMode ? 'bg-blue-500 text-white' : 'bg-green-600 text-white'
              }`}
            >
              {isDemoMode ? 'DEMO' : 'LIVE'}
            </button>
            <span className="flex items-center gap-1 text-[10px] text-green-300">
              <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></span>
              Live
            </span>
          </div>
        </div>

        <div className="grid grid-cols-4 gap-1.5 mt-3 bg-blue-950/60 p-1 rounded-lg">
          {(['Sew 1', 'Sew 2', 'Sew 3', 'All'] as FloorId[]).map(floor => (
            <button
              key={floor}
              onClick={() => onSelectFloor(floor)}
              className={`py-1 rounded text-xs font-bold transition text-center ${
                selectedFloor === floor
                  ? 'bg-[#1976D2] text-white shadow'
                  : 'text-blue-200 hover:text-white'
              }`}
            >
              {floor}
            </button>
          ))}
        </div>

        <div className="flex items-center justify-between mt-2.5 pt-2 border-t border-blue-900/60">
          <div className="flex items-center gap-1.5 text-xs text-blue-100 font-medium">
            <span>📅</span>
            <span>09 Oct 2026</span>
          </div>

          <div className="flex bg-blue-950 rounded p-0.5 text-[11px] font-bold">
            {(['Today', 'Week', 'Month'] as const).map(p => (
              <button
                key={p}
                onClick={() => onSelectPeriod(p)}
                className={`px-3 py-0.5 rounded transition ${
                  selectedPeriod === p ? 'bg-[#1976D2] text-white' : 'text-blue-300'
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Incident Alert Banner */}
      {openIncidents.length > 0 && (
        <div
          onClick={onOpenIncidentPanel}
          className="mx-3 mt-2 bg-amber-50 border border-amber-300 rounded-lg p-2.5 flex items-center justify-between cursor-pointer shadow-sm"
        >
          <div className="flex items-center gap-2">
            <span className="text-base">⚠️</span>
            <div>
              <div className="text-xs font-bold text-amber-900">
                {openIncidents.length} Active Incident(s) in Progress
              </div>
              <div className="text-[10px] text-amber-700">
                Tap to view elapsed timer, mute alarm, or resolve
              </div>
            </div>
          </div>
          <span className="text-xs font-bold text-blue-600">View →</span>
        </div>
      )}

      {/* Sub-view switcher */}
      <div className="flex justify-around bg-white border-b border-slate-200 mt-2 px-2 py-1.5 sticky top-[138px] z-20 shadow-sm">
        {[
          { id: 'SUMMARY', label: '1. SUMMARY' },
          { id: 'CHARTS', label: '2. CHARTS' },
          { id: 'TABLE', label: '3. LINE TABLE' }
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setMobileSubTab(tab.id as any)}
            className={`text-xs font-extrabold pb-1 border-b-2 transition ${
              mobileSubTab === tab.id
                ? 'border-[#1976D2] text-[#1976D2]'
                : 'border-transparent text-slate-500'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Main Content Area */}
      <div className="p-3 space-y-4">
        {/* ======================= TAB 1: SUMMARY ======================= */}
        {mobileSubTab === 'SUMMARY' && (
          <>
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-extrabold text-slate-900 tracking-tight">
                {selectedFloor} — Key Metrics (Today)
              </h2>
              <span className="text-[10px] font-semibold text-slate-500">
                {kpis.reportedLinesCount}/{kpis.targetedLinesCount} lines active
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2.5">
              {/* Today Efficiency */}
              <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-blue-600"></span>
                    <span className="text-xs font-bold text-slate-700">Today Eff</span>
                  </div>
                  <span className={`text-[9px] font-bold ${kpis.todayEfficiencyPct >= 75 ? 'text-emerald-600' : 'text-red-600'}`}>
                    {kpis.todayEfficiencyPct >= 75 ? 'ON TARGET' : 'UNDER'}
                  </span>
                </div>
                <div className="mt-2 mb-1">
                  <div className="text-2xl font-black text-slate-900 tracking-tight">
                    {kpis.todayEfficiencyPct}%
                  </div>
                </div>
                <div>
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="bg-blue-600 h-full rounded-full transition-all"
                      style={{ width: `${kpis.todayEfficiencyPct}%` }}
                    ></div>
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 font-medium mt-1">
                    <span>Target: 75%</span>
                    <span>Δ {(kpis.todayEfficiencyPct - 75).toFixed(1)} pp</span>
                  </div>
                </div>
              </div>

              {/* Monthly Efficiency */}
              <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-sky-500"></span>
                    <span className="text-xs font-bold text-slate-700">Month Eff</span>
                  </div>
                  <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700">
                    ▲ +{kpis.monthlyEfficiencyDelta} pp
                  </span>
                </div>
                <div className="mt-2 mb-1">
                  <div className="text-2xl font-black text-slate-900 tracking-tight">
                    {kpis.monthlyEfficiencyPct}%
                  </div>
                </div>
                <div>
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="bg-sky-500 h-full rounded-full transition-all"
                      style={{ width: `${kpis.monthlyEfficiencyPct}%` }}
                    ></div>
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 font-medium mt-1">
                    <span>Last Mo: 68.1%</span>
                    <span>Cumulative</span>
                  </div>
                </div>
              </div>

              {/* Today OQL */}
              <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-amber-500"></span>
                    <span className="text-xs font-bold text-slate-700">Today OQL</span>
                  </div>
                  <span className={`text-[9px] font-bold ${kpis.todayOqlPct <= 5 ? 'text-emerald-600' : 'text-red-600'}`}>
                    {kpis.todayOqlPct <= 5 ? 'STANDARD' : 'ALERT'}
                  </span>
                </div>
                <div className="mt-2 mb-1">
                  <div className="text-2xl font-black text-slate-900 tracking-tight">
                    {kpis.todayOqlPct}%
                  </div>
                </div>
                <div>
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="bg-amber-500 h-full rounded-full transition-all"
                      style={{ width: `${kpis.todayOqlPct}%` }}
                    ></div>
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 font-medium mt-1">
                    <span>Standard: ≤5.0%</span>
                    <span>Defects rate</span>
                  </div>
                </div>
              </div>

              {/* BS QA OQL */}
              <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-orange-500"></span>
                    <span className="text-xs font-bold text-slate-700">BS QA OQL</span>
                  </div>
                  <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-blue-50 text-blue-700">
                    BASELINE
                  </span>
                </div>
                <div className="mt-2 mb-1">
                  <div className="text-2xl font-black text-slate-900 tracking-tight">
                    {kpis.bsQaOqlPct.toFixed(2)}%
                  </div>
                </div>
                <div>
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="bg-orange-500 h-full rounded-full transition-all"
                      style={{ width: `${kpis.bsQaOqlPct}%` }}
                    ></div>
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 font-medium mt-1">
                    <span>Buyer QA spec</span>
                    <span>Audited</span>
                  </div>
                </div>
              </div>

              {/* FRI Pass Rate */}
              <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                    <span className="text-xs font-bold text-slate-700">FRI Pass</span>
                  </div>
                  <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700">
                    100% PASS
                  </span>
                </div>
                <div className="mt-2 mb-1">
                  <div className="text-2xl font-black text-slate-900 tracking-tight">
                    {kpis.friPassRatePct}%
                  </div>
                </div>
                <div>
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="bg-emerald-500 h-full rounded-full transition-all"
                      style={{ width: `${kpis.friPassRatePct}%` }}
                    ></div>
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 font-medium mt-1">
                    <span>Final Inspection</span>
                    <span>Zero reject</span>
                  </div>
                </div>
              </div>

              {/* Reporting */}
              <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-indigo-600"></span>
                    <span className="text-xs font-bold text-slate-700">Reporting</span>
                  </div>
                  <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-indigo-50 text-indigo-700">
                    {kpis.reportedLinesCount}/{kpis.targetedLinesCount} ACTIVE
                  </span>
                </div>
                <div className="mt-2 mb-1">
                  <div className="text-2xl font-black text-slate-900 tracking-tight">
                    {kpis.reportingCoveragePct}%
                  </div>
                </div>
                <div>
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div
                      className="bg-indigo-600 h-full rounded-full transition-all"
                      style={{ width: `${kpis.reportingCoveragePct}%` }}
                    ></div>
                  </div>
                  <div className="flex justify-between text-[10px] text-slate-500 font-medium mt-1">
                    <span>Hourly logs</span>
                    <span>Live coverage</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Operators & Machines */}
            <div className="flex gap-2.5">
              <div className="flex-1 bg-white rounded-xl p-3 border border-slate-200/80 shadow-sm flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 text-lg">
                  👥
                </div>
                <div>
                  <div className="text-[10px] text-slate-500 font-bold">Operators</div>
                  <div className="text-lg font-black text-slate-900">{kpis.operatorsCount}</div>
                </div>
              </div>

              <div className="flex-1 bg-white rounded-xl p-3 border border-slate-200/80 shadow-sm flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-700 text-lg">
                  ⚙️
                </div>
                <div>
                  <div className="text-[10px] text-slate-500 font-bold">Machines</div>
                  <div className="text-lg font-black text-slate-900">{kpis.machinesCount}</div>
                </div>
              </div>
            </div>

            {/* Target vs Output Bar Chart */}
            <div className="bg-white rounded-xl p-3 border border-slate-200/80 shadow-sm overflow-x-auto">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-xs font-bold text-slate-800">
                  Target vs Output — {selectedFloor}
                </h3>
                <div className="flex items-center gap-3 text-[10px]">
                  <span className="flex items-center gap-1 font-semibold text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-sm bg-[#1E88E5]"></span> Target
                  </span>
                  <span className="flex items-center gap-1 font-semibold text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-sm bg-[#10B981]"></span> Output
                  </span>
                </div>
              </div>

              <div className="h-44 flex items-end justify-around gap-2 pt-6 pb-2 border-b border-slate-200" style={{ minWidth: `${Math.max(520, lineSummaries.length * 70)}px` }}>
                {lineSummaries.map(line => {
                  const maxTarget = 650;
                  const targetH = Math.round((line.target / maxTarget) * 120);
                  const outputH = line.output !== null ? Math.round((line.output / maxTarget) * 120) : 0;

                  return (
                    <div key={line.lineId} className="flex flex-col items-center gap-1 flex-1">
                      <div className="flex items-end gap-1 h-32 justify-center w-full">
                        <div
                          className="w-4 bg-[#1E88E5] rounded-t relative group"
                          style={{ height: `${targetH}px` }}
                        >
                          <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[9px] font-bold text-slate-600">
                            {line.target}
                          </span>
                        </div>
                        <div
                          className="w-4 bg-[#10B981] rounded-t relative group"
                          style={{ height: `${outputH}px` }}
                        >
                          {line.output !== null && (
                            <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[9px] font-bold text-green-700">
                              {line.output}
                            </span>
                          )}
                        </div>
                      </div>
                      <span className="text-[11px] font-extrabold text-slate-800">{line.lineId}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Matrix Table */}
            <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-3 py-3 border-b border-slate-200 flex items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-extrabold text-slate-900">Line-wise Performance Matrix</h3>
                  <p className="text-[10px] text-slate-500 mt-0.5">All lines for {selectedFloor} • {lineSummaries.length} lines</p>
                </div>
                <button
                  onClick={() => setMobileSubTab('TABLE')}
                  className="shrink-0 text-[10px] font-bold text-blue-700 bg-blue-50 border border-blue-100 rounded-lg px-2.5 py-1.5"
                >
                  Full details →
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] text-[11px] text-left">
                  <thead className="bg-slate-50 text-slate-600 border-b border-slate-200">
                    <tr>
                      <th className="px-3 py-2.5 font-extrabold">Line</th>
                      <th className="px-3 py-2.5 text-right font-extrabold">Target</th>
                      <th className="px-3 py-2.5 text-right font-extrabold">Output</th>
                      <th className="px-3 py-2.5 text-right font-extrabold">Ach %</th>
                      <th className="px-3 py-2.5 text-right font-extrabold">Efficiency</th>
                      <th className="px-3 py-2.5 text-right font-extrabold">OQL</th>
                      <th className="px-3 py-2.5 text-right font-extrabold">DHU</th>
                      <th className="px-3 py-2.5 text-center font-extrabold">Reporting</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {lineSummaries.map(line => (
                      <tr key={`home-matrix-${line.lineId}`} className="hover:bg-slate-50">
                        <td className="px-3 py-2.5 font-extrabold text-slate-900">{line.lineId}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{line.target.toLocaleString()}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums font-bold">{line.output === null ? '—' : line.output.toLocaleString()}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{line.achievementPct === null ? '—' : `${line.achievementPct}%`}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{line.efficiencyPct === null ? '—' : `${line.efficiencyPct}%`}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{line.todayOqlPct === null ? '—' : `${line.todayOqlPct}%`}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{line.dhu === null ? '—' : line.dhu}</td>
                        <td className="px-3 py-2.5 text-center">
                          <span className={`inline-flex rounded-full px-2 py-1 text-[9px] font-extrabold whitespace-nowrap ${
                            line.reportingStatus === 'Complete'
                              ? 'bg-emerald-50 text-emerald-700'
                              : line.reportingStatus === 'In Progress'
                              ? 'bg-amber-50 text-amber-700'
                              : 'bg-red-50 text-red-700'
                          }`}>
                            {line.reportingStatus}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        )}

        {/* ======================= TAB 2: CHARTS ======================= */}
        {mobileSubTab === 'CHARTS' && (
          <div className="space-y-4">
            <h2 className="text-sm font-extrabold text-slate-900 tracking-tight">
              Production & Quality Charts
            </h2>

            <div className="bg-white rounded-xl p-3 border border-slate-200/80 shadow-sm overflow-x-auto">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-bold text-slate-800">
                  Line Efficiency — Today vs Month (%)
                </span>
                <div className="flex items-center gap-3 text-[10px]">
                  <span className="flex items-center gap-1 font-semibold text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#2196F3]"></span> Today
                  </span>
                  <span className="flex items-center gap-1 font-semibold text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#FF9800]"></span> Month
                  </span>
                </div>
              </div>

              <div className="h-44 flex items-end justify-around gap-2 pt-6 pb-2 border-b border-slate-200" style={{ minWidth: `${Math.max(520, lineSummaries.length * 70)}px` }}>
                {lineSummaries.map(line => {
                  const todayEff = line.efficiencyPct ?? 0;
                  const monthEff = line.monthlyEfficiencyPct;
                  const todayH = Math.round((todayEff / 100) * 120);
                  const monthH = Math.round((monthEff / 100) * 120);

                  return (
                    <div key={line.lineId} className="flex flex-col items-center gap-1 flex-1">
                      <div className="flex items-end gap-1 h-32 justify-center w-full">
                        <div
                          className="w-4 bg-[#2196F3] rounded-t relative"
                          style={{ height: `${todayH}px` }}
                        >
                          <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[9px] font-bold text-slate-700">
                            {todayEff}%
                          </span>
                        </div>
                        <div
                          className="w-4 bg-[#FF9800] rounded-t relative"
                          style={{ height: `${monthH}px` }}
                        >
                          <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[9px] font-bold text-slate-700">
                            {monthEff}%
                          </span>
                        </div>
                      </div>
                      <span className="text-[11px] font-extrabold text-slate-800">{line.lineId}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="bg-white rounded-xl p-3 border border-slate-200/80 shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-bold text-slate-800">
                  Line OQL — Today vs Month (%)
                </span>
                <div className="flex items-center gap-3 text-[10px]">
                  <span className="flex items-center gap-1 font-semibold text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#2196F3]"></span> Today
                  </span>
                  <span className="flex items-center gap-1 font-semibold text-slate-600">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#FF9800]"></span> Month
                  </span>
                </div>
              </div>

              <div className="h-44 flex items-end justify-around gap-2 pt-6 pb-2 border-b border-slate-200" style={{ minWidth: `${Math.max(520, lineSummaries.length * 70)}px` }}>
                {lineSummaries.map(line => {
                  const todayOql = line.todayOqlPct ?? 0;
                  const monthOql = line.monthlyOqlPct;
                  const maxOql = 8;
                  const todayH = Math.round((todayOql / maxOql) * 120);
                  const monthH = Math.round((monthOql / maxOql) * 120);

                  return (
                    <div key={line.lineId} className="flex flex-col items-center gap-1 flex-1">
                      <div className="flex items-end gap-1 h-32 justify-center w-full">
                        <div
                          className="w-4 bg-[#2196F3] rounded-t relative"
                          style={{ height: `${todayH}px` }}
                        >
                          <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[9px] font-bold text-slate-700">
                            {todayOql}%
                          </span>
                        </div>
                        <div
                          className="w-4 bg-[#FF9800] rounded-t relative"
                          style={{ height: `${monthH}px` }}
                        >
                          <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[9px] font-bold text-slate-700">
                            {monthOql}%
                          </span>
                        </div>
                      </div>
                      <span className="text-[11px] font-extrabold text-slate-800">{line.lineId}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="bg-white rounded-xl p-3 border border-slate-200/80 shadow-sm">
              <h3 className="text-xs font-bold text-slate-800 mb-3">
                Top 5 Defects — {selectedFloor} (Today)
              </h3>
              <div className="space-y-2.5">
                {topDefects.map((defect, i) => {
                  const colors = ['#FF9800', '#00BCD4', '#4DD0E1', '#81D4FA', '#B0BEC5'];
                  const barColor = colors[i % colors.length];
                  const maxCount = topDefects[0]?.count || 50;
                  const widthPct = Math.round((defect.count / maxCount) * 100);

                  return (
                    <div key={defect.defect} className="space-y-1">
                      <div className="flex justify-between text-xs font-medium text-slate-700">
                        <span>{defect.defect}</span>
                        <span className="font-extrabold text-slate-900">{defect.count}</span>
                      </div>
                      <div className="w-full bg-slate-100 h-3 rounded-full overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-500"
                          style={{ width: `${widthPct}%`, backgroundColor: barColor }}
                        ></div>
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="text-[11px] text-blue-600 font-semibold mt-3 text-center cursor-pointer">
                ⓘ Weekly quality trend continues below ▾
              </div>
            </div>
          </div>
        )}

        {/* ======================= TAB 3: LINE TABLE ======================= */}
        {mobileSubTab === 'TABLE' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-extrabold text-slate-900">
                {selectedFloor} — Line-wise Performance
              </h2>
              <select
                value={tableFilter}
                onChange={e => setTableFilter(e.target.value as any)}
                className="text-xs font-bold border border-slate-300 rounded-lg px-2 py-1 bg-white text-slate-700 shadow-sm"
              >
                <option value="ALL">Show: All targeted lines</option>
                <option value="COMPLETE">Show: Complete only</option>
                <option value="NOT_REPORTED">Show: Not reported only</option>
              </select>
            </div>

            <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-100 text-slate-600 font-extrabold border-b border-slate-200">
                  <tr>
                    <th className="py-2.5 px-3">Line</th>
                    <th className="py-2.5 px-3 text-right">Target</th>
                    <th className="py-2.5 px-3 text-right">Output</th>
                    <th className="py-2.5 px-3 text-right">Ach%</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium">
                  {filteredLines.map(line => {
                    const isReported = line.output !== null;

                    return (
                      <tr
                        key={line.lineId}
                        onClick={() => setExpandedLine(expandedLine === line.lineId ? null : line.lineId)}
                        className="hover:bg-slate-50 cursor-pointer transition"
                      >
                        <td className="py-2.5 px-3 font-extrabold text-slate-900">{line.lineId}</td>
                        <td className="py-2.5 px-3 text-right text-slate-700">{line.target}</td>
                        <td className="py-2.5 px-3 text-right">
                          {isReported ? (
                            <span className="text-slate-900 font-bold">{line.output}</span>
                          ) : (
                            <span className="text-slate-400">-</span>
                          )}
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          {isReported && line.achievementPct !== null ? (
                            <span
                              className={`font-black ${
                                line.achievementPct >= 85
                                  ? 'text-green-600'
                                  : line.achievementPct >= 75
                                  ? 'text-blue-600'
                                  : 'text-amber-600'
                              }`}
                            >
                              {line.achievementPct}%
                            </span>
                          ) : (
                            <span className="text-slate-400">-</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="space-y-2 mt-3">
              {filteredLines.map(line => {
                const isReported = line.output !== null;
                const isExpanded = expandedLine === line.lineId;

                return (
                  <div
                    key={`card-${line.lineId}`}
                    className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm"
                  >
                    <div
                      onClick={() => setExpandedLine(isExpanded ? null : line.lineId)}
                      className="p-3 flex items-center justify-between cursor-pointer hover:bg-slate-50"
                    >
                      <div className="flex items-center gap-2">
                        <span className="font-extrabold text-sm text-slate-900">
                          Line {line.lineId}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        {isReported ? (
                          <span className="bg-green-100 text-green-800 text-[10px] font-extrabold px-2 py-0.5 rounded-full flex items-center gap-1">
                            ✓ Complete
                          </span>
                        ) : (
                          <span className="bg-red-100 text-red-700 text-[10px] font-extrabold px-2 py-0.5 rounded-full flex items-center gap-1">
                            ! Not reported
                          </span>
                        )}
                        <span className="text-slate-400 text-xs">{isExpanded ? '▲' : '▼'}</span>
                      </div>
                    </div>

                    {isExpanded && (
                      <div className="px-3 pb-3 pt-1 border-t border-slate-100 bg-slate-50/50">
                        {isReported ? (
                          <div className="grid grid-cols-3 gap-2 text-center pt-1">
                            <div className="bg-white p-2 rounded-lg border border-slate-200">
                              <div className="text-[10px] text-slate-500 font-semibold">Efficiency</div>
                              <div className="text-base font-black text-blue-600">
                                {line.efficiencyPct ?? '-'}%
                              </div>
                            </div>
                            <div className="bg-white p-2 rounded-lg border border-slate-200">
                              <div className="text-[10px] text-slate-500 font-semibold">OQL</div>
                              <div className="text-base font-black text-amber-600">
                                {line.todayOqlPct ?? '-'}%
                              </div>
                            </div>
                            <div className="bg-white p-2 rounded-lg border border-slate-200">
                              <div className="text-[10px] text-slate-500 font-semibold">DHU</div>
                              <div className="text-base font-black text-red-600">
                                {line.dhu ?? '-'}%
                              </div>
                            </div>
                          </div>
                        ) : (
                          <div className="text-xs text-slate-500 py-1 flex items-center justify-between">
                            <span>No hourly production submissions logged yet today.</span>
                            <span className="font-bold text-red-600">Pending</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <button
              onClick={onNavigateToReports}
              className="w-full bg-[#1976D2] hover:bg-blue-700 text-white font-extrabold text-sm py-2.5 rounded-xl shadow flex items-center justify-center gap-2 transition"
            >
              <span>📄</span>
              <span>View line report</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};