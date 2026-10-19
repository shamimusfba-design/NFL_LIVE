import React, { useState, useEffect } from 'react';
import {
  FloorId,
  FloorKpis,
  LinePerformanceSummary,
  IncidentRecord
} from '../types';

interface TvDesktopDashboardProps {
  selectedFloor: FloorId;
  onSelectFloor: (floor: FloorId) => void;
  selectedPeriod: 'Today' | 'Week' | 'Month';
  onSelectPeriod: (p: 'Today' | 'Week' | 'Month') => void;
  kpis: FloorKpis;
  lineSummaries: LinePerformanceSummary[];
  topDefects: Array<{ defect: string; count: number; dhu: number }>;
  highestDhuLines: Array<{ line: string; dhu: number }>;
  isDemoMode: boolean;
  onToggleDemo: () => void;
  openIncidents: IncidentRecord[];
  onOpenIncidentPanel: () => void;
}

export const TvDesktopDashboard: React.FC<TvDesktopDashboardProps> = ({
  selectedFloor,
  onSelectFloor,
  selectedPeriod,
  onSelectPeriod,
  kpis,
  lineSummaries,
  topDefects,
  highestDhuLines,
  isDemoMode,
  onToggleDemo,
  openIncidents,
  onOpenIncidentPanel
}) => {
  const [autoRotate, setAutoRotate] = useState(false);
  const [refreshSeconds, setRefreshSeconds] = useState(30);

  // Auto-refresh countdown every second
  useEffect(() => {
    const timer = setInterval(() => {
      setRefreshSeconds(s => (s > 1 ? s - 1 : 30));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // Timed auto-rotation between floors if enabled on Smart TV
  useEffect(() => {
    if (!autoRotate) return;
    const floors: FloorId[] = ['Sew 1', 'Sew 2', 'Sew 3', 'All'];
    const timer = setInterval(() => {
      const nextIndex = (floors.indexOf(selectedFloor) + 1) % floors.length;
      onSelectFloor(floors[nextIndex]);
    }, 20000); // 20s rotation
    return () => clearInterval(timer);
  }, [autoRotate, selectedFloor, onSelectFloor]);

  return (
    <div className="min-h-screen bg-[#F0F4F8] text-slate-800 flex flex-col p-3 space-y-3 select-none">
      {/* Top Header Bar matching Image 2 */}
      <header className="bg-[#002D62] text-white px-5 py-3 rounded-xl shadow-md flex items-center justify-between">
        <div className="flex items-center gap-3">
          {/* NOS Live company badge */}
          <div className="bg-white text-[#002D62] font-black text-sm px-2.5 py-1 rounded shadow tracking-wider flex items-center gap-1.5">
            <span>NOS Live</span>
          </div>
          <div>
            <h1 className="text-lg font-black tracking-wide">
              NOS Live — Northern Operating System Live
            </h1>
            <div className="text-xs text-blue-200">
              Garment Manufacturing Floor Monitor • Smart TV 16:9 Command Center
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <button
            onClick={onToggleDemo}
            className={`text-xs font-black px-3 py-1 rounded shadow transition ${
              isDemoMode ? 'bg-[#003B7A] text-blue-200 border border-blue-400' : 'bg-green-600 text-white'
            }`}
          >
            {isDemoMode ? 'DESIGN PREVIEW • DEMO DATA' : 'LIVE PRODUCTION DATA'}
          </button>

          <div className="text-right text-xs">
            <div className="text-blue-200 flex items-center gap-1 justify-end">
              <span>Last refresh:</span>
              <span className="font-bold text-white">18:49</span>
              <span className="cursor-pointer hover:rotate-180 transition">🔄</span>
            </div>
            <div className="text-[11px] text-blue-300">Next update in {refreshSeconds}s</div>
          </div>

          <div className="flex items-center gap-2 bg-[#00234D] p-1.5 rounded-lg border border-blue-900">
            <span className="text-xs text-blue-200 font-semibold px-1">📅 09 Oct 2026</span>
            {(['Today', 'Week', 'Month'] as const).map(p => (
              <button
                key={p}
                onClick={() => onSelectPeriod(p)}
                className={`text-xs font-bold px-3 py-1 rounded transition ${
                  selectedPeriod === p ? 'bg-[#1976D2] text-white shadow' : 'text-blue-300 hover:text-white'
                }`}
              >
                {p}
              </button>
            ))}
          </div>

          {/* TV Auto-rotate toggle */}
          <button
            onClick={() => setAutoRotate(!autoRotate)}
            className={`text-xs font-bold px-2.5 py-1 rounded border transition ${
              autoRotate
                ? 'bg-amber-500 border-amber-400 text-slate-900'
                : 'border-blue-700 text-blue-300 hover:text-white'
            }`}
            title="Auto-rotate floors every 20s on TV display"
          >
            {autoRotate ? '⏸ Auto-Rotate: ON' : '▶ TV Rotate'}
          </button>
        </div>
      </header>

      {/* Second Row: Floor Filter Bar */}
      <div className="bg-white px-5 py-2 rounded-xl shadow-sm border border-slate-200 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-xs font-black text-slate-700 uppercase tracking-wider">Floor:</span>
          {(['Sew 1', 'Sew 2', 'Sew 3', 'All'] as FloorId[]).map(floor => (
            <button
              key={floor}
              onClick={() => onSelectFloor(floor)}
              className={`px-4 py-1.5 rounded-lg text-xs font-black transition shadow-sm ${
                selectedFloor === floor
                  ? 'bg-[#1976D2] text-white ring-2 ring-blue-300'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              {floor}
            </button>
          ))}
        </div>

        <div className="text-xs text-slate-500 font-medium">
          Floor selection updates every KPI, chart and line table. All combines three floors.
        </div>

        {openIncidents.length > 0 && (
          <div
            onClick={onOpenIncidentPanel}
            className="flex items-center gap-2 bg-red-50 border border-red-300 px-3 py-1 rounded-lg cursor-pointer hover:bg-red-100 transition animate-pulse"
          >
            <span className="text-red-600 font-black text-xs">
              ⚠️ {openIncidents.length} Open Incidents
            </span>
            <span className="text-[10px] text-red-700 underline font-bold">Action Required</span>
          </div>
        )}
      </div>

      {/* 7 Top Executive KPI Cards */}
      <div className="grid grid-cols-7 gap-2.5">
        {/* Card 1: Today Efficiency */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>Today Eff</span>
            <span className={kpis.todayEfficiencyPct >= 75 ? 'text-emerald-600' : 'text-red-600'}>
              {kpis.todayEfficiencyPct >= 75 ? 'ON TARGET' : 'UNDER'}
            </span>
          </div>
          <div className="my-2">
            <div className="text-3xl font-black text-slate-900">{kpis.todayEfficiencyPct}%</div>
            <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden mt-2">
              <div
                className="bg-blue-600 h-full rounded-full"
                style={{ width: `${kpis.todayEfficiencyPct}%` }}
              ></div>
            </div>
          </div>
          <div className="flex justify-between text-[10px] text-slate-500 font-medium">
            <span>Target: 75%</span>
            <span>Δ {(kpis.todayEfficiencyPct - 75).toFixed(1)} pp</span>
          </div>
        </div>

        {/* Card 2: Monthly Efficiency */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>Monthly Eff</span>
            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700">
              ▲ +{kpis.monthlyEfficiencyDelta} pp
            </span>
          </div>
          <div className="my-2">
            <div className="text-3xl font-black text-[#002D62]">{kpis.monthlyEfficiencyPct}%</div>
            <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden mt-2">
              <div
                className="bg-sky-500 h-full rounded-full"
                style={{ width: `${kpis.monthlyEfficiencyPct}%` }}
              ></div>
            </div>
          </div>
          <div className="text-[10px] text-slate-400 font-medium">vs last month (68.1%)</div>
        </div>

        {/* Card 3: Operator / Machine */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>Operators & Machines</span>
            <span className="text-slate-400">👥</span>
          </div>
          <div className="my-2">
            <div className="text-2xl font-black text-slate-900">
              {kpis.operatorsCount} <span className="text-base font-semibold text-slate-400">/</span> {kpis.machinesCount}
            </div>
            <div className="text-[11px] text-slate-500 font-medium mt-1">
              Ratio: {(kpis.operatorsCount / Math.max(1, kpis.machinesCount)).toFixed(2)} Ops/Mach
            </div>
          </div>
          <div className="flex justify-between text-[10px] text-slate-600 font-bold">
            <span className="text-amber-700">● Ops {kpis.operatorsCount}</span>
            <span className="text-sky-700">● Mach {kpis.machinesCount}</span>
          </div>
        </div>

        {/* Card 4: Floor & Factory Efficiency */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>Floor Benchmark</span>
            <div className="flex gap-1.5 text-[8px]">
              <span className="text-blue-600">■ Today</span>
              <span className="text-emerald-700">■ Mo</span>
            </div>
          </div>
          <div className="flex items-end justify-between h-16 pt-2 px-1 border-b border-slate-100">
            {[
              { floor: 'Sew 1', today: 76, month: 72 },
              { floor: 'Sew 2', today: 68, month: 63 },
              { floor: 'Sew 3', today: 70, month: 66 },
              { floor: 'Fac', today: 71, month: 67 }
            ].map(f => (
              <div key={f.floor} className="flex flex-col items-center gap-0.5">
                <div className="flex items-end gap-0.5 h-12">
                  <div className="w-2 bg-blue-500 rounded-t" style={{ height: `${f.today}%` }}></div>
                  <div className="w-2 bg-emerald-600 rounded-t" style={{ height: `${f.month}%` }}></div>
                </div>
                <span className="text-[8px] font-bold text-slate-600">{f.floor}</span>
              </div>
            ))}
          </div>
          <div className="text-[9px] text-slate-400 text-center font-medium mt-0.5">Cross-floor comparison</div>
        </div>

        {/* Card 5: BS QA OQL */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>BS QA OQL</span>
            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-blue-50 text-blue-700">SPEC</span>
          </div>
          <div className="my-2 text-center">
            <div className="text-3xl font-black text-[#FF7A00]">{kpis.bsQaOqlPct.toFixed(2)}%</div>
            <div className="text-[11px] font-bold text-slate-600 mt-1">Baseline Standard</div>
          </div>
          <div className="text-[10px] text-slate-400 text-center font-medium">Buyer tolerance threshold</div>
        </div>

        {/* Card 6: Today OQL */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>Today OQL</span>
            <span className={kpis.todayOqlPct <= 5 ? 'text-emerald-600' : 'text-red-600'}>
              {kpis.todayOqlPct <= 5 ? 'PASS' : 'ALERT'}
            </span>
          </div>
          <div className="my-2">
            <div className="text-3xl font-black text-slate-900">{kpis.todayOqlPct}%</div>
            <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden mt-2">
              <div
                className="h-full rounded-full"
                style={{
                  width: `${kpis.todayOqlPct}%`,
                  backgroundColor: kpis.todayOqlPct <= 2.0 ? '#10B981' : kpis.todayOqlPct <= 5.0 ? '#F59E0B' : '#EF4444'
                }}
              ></div>
            </div>
          </div>
          <div className="flex justify-around text-[9px] text-slate-500 font-bold">
            <span className="text-green-600">● ≤2% Good</span>
            <span className="text-amber-600">● 2–5% Watch</span>
          </div>
        </div>

        {/* Card 7: BS QA FRI Pass Rate */}
        <div className="bg-white rounded-xl p-3 border border-slate-200/90 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center text-xs font-extrabold text-slate-800">
            <span>FRI Pass Rate</span>
            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700">100%</span>
          </div>
          <div className="my-2 text-center">
            <div className="text-3xl font-black text-[#10B981]">{kpis.friPassRatePct}%</div>
            <div className="text-[11px] font-bold text-slate-700 mt-1">First Pass Rate</div>
          </div>
          <div className="text-[9px] text-slate-400 text-center font-medium">Final Inspection Passed</div>
        </div>
      </div>

      {/* Chart Grid (6 charts matching Image 2) */}
      <div className="grid grid-cols-3 gap-3">
        {/* Chart 1: Live Target vs Achievement */}
        <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-sm">
          <div className="flex justify-between items-center mb-2">
            <h3 className="text-xs font-extrabold text-slate-900">
              Live Target vs Achievement — {selectedFloor}
            </h3>
            <div className="flex gap-3 text-[10px] font-bold">
              <span className="flex items-center gap-1 text-slate-600">
                <span className="w-2.5 h-2.5 bg-[#2196F3] rounded-sm"></span> Target
              </span>
              <span className="flex items-center gap-1 text-slate-600">
                <span className="w-2.5 h-2.5 bg-[#10B981] rounded-sm"></span> Output
              </span>
            </div>
          </div>
          <div className="h-44 flex items-end justify-between gap-1 pt-6 pb-2 border-b border-slate-200">
            {lineSummaries.slice(0, 9).map(line => {
              const maxVal = 700;
              const targetH = Math.round((line.target / maxVal) * 120);
              const outputH = line.output !== null ? Math.round((line.output / maxVal) * 120) : 0;
              return (
                <div key={line.lineId} className="flex flex-col items-center flex-1">
                  <div className="flex items-end gap-1 h-32 justify-center w-full">
                    <div className="w-3 bg-[#2196F3] rounded-t relative" style={{ height: `${targetH}px` }}>
                      <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[8px] font-bold text-slate-600">
                        {line.target}
                      </span>
                    </div>
                    <div className="w-3 bg-[#10B981] rounded-t relative" style={{ height: `${outputH}px` }}>
                      {line.output !== null && (
                        <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[8px] font-bold text-green-700">
                          {line.output}
                        </span>
                      )}
                    </div>
                  </div>
                  <span className="text-[10px] font-black text-slate-800 mt-1">{line.lineId}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Chart 2: Line-wise OQL — Today vs Month */}
        <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-sm">
          <div className="flex justify-between items-center mb-2">
            <h3 className="text-xs font-extrabold text-slate-900">Line-wise OQL — Today vs Month</h3>
            <div className="flex gap-3 text-[10px] font-bold">
              <span className="flex items-center gap-1 text-slate-600">
                <span className="w-2.5 h-2.5 bg-[#2196F3] rounded-sm"></span> Today
              </span>
              <span className="flex items-center gap-1 text-slate-600">
                <span className="w-2.5 h-2.5 bg-[#FF9800] rounded-sm"></span> Month
              </span>
            </div>
          </div>
          <div className="h-44 flex items-end justify-between gap-1 pt-6 pb-2 border-b border-slate-200">
            {lineSummaries.slice(0, 9).map(line => {
              const maxVal = 7;
              const todayOql = line.todayOqlPct ?? 0;
              const monthOql = line.monthlyOqlPct;
              const todayH = Math.round((todayOql / maxVal) * 120);
              const monthH = Math.round((monthOql / maxVal) * 120);
              return (
                <div key={line.lineId} className="flex flex-col items-center flex-1">
                  <div className="flex items-end gap-1 h-32 justify-center w-full">
                    <div className="w-3 bg-[#2196F3] rounded-t relative" style={{ height: `${todayH}px` }}>
                      <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[8px] font-bold text-slate-600">
                        {todayOql}%
                      </span>
                    </div>
                    <div className="w-3 bg-[#FF9800] rounded-t relative" style={{ height: `${monthH}px` }}>
                      <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[8px] font-bold text-slate-600">
                        {monthOql}%
                      </span>
                    </div>
                  </div>
                  <span className="text-[10px] font-black text-slate-800 mt-1">{line.lineId}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Chart 3: Top 5 Defects — Sew 2 (Today) */}
        <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center mb-1">
            <h3 className="text-xs font-extrabold text-slate-900">
              Top 5 Defects — {selectedFloor} (Today)
            </h3>
            <span className="text-[10px] text-slate-400 font-bold">DHU</span>
          </div>
          <div className="space-y-2 my-auto">
            {topDefects.map(d => (
              <div key={d.defect} className="space-y-0.5">
                <div className="flex justify-between text-xs font-bold text-slate-700">
                  <span>{d.defect}</span>
                  <span className="text-slate-900">{d.dhu.toFixed(2)}</span>
                </div>
                <div className="w-full bg-slate-100 h-3 rounded-full overflow-hidden">
                  <div
                    className="bg-[#FF7A00] h-full rounded-full"
                    style={{ width: `${Math.min(100, Math.round((d.dhu / 0.5) * 100))}%` }}
                  ></div>
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-between text-[9px] text-slate-400 font-bold px-1 pt-2 border-t border-slate-100">
            <span>0.00</span>
            <span>0.10</span>
            <span>0.20</span>
            <span>0.30</span>
            <span>0.40</span>
            <span>0.50</span>
          </div>
        </div>

        {/* Chart 4: Line Efficiency — Today vs Month */}
        <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-sm">
          <div className="flex justify-between items-center mb-2">
            <h3 className="text-xs font-extrabold text-slate-900">Line Efficiency — Today vs Month</h3>
            <div className="flex gap-3 text-[10px] font-bold">
              <span className="flex items-center gap-1 text-slate-600">
                <span className="w-2.5 h-2.5 bg-[#2196F3] rounded-sm"></span> Today
              </span>
              <span className="flex items-center gap-1 text-slate-600">
                <span className="w-2.5 h-2.5 bg-[#FF9800] rounded-sm"></span> Month
              </span>
            </div>
          </div>
          <div className="h-44 flex items-end justify-between gap-1 pt-6 pb-2 border-b border-slate-200">
            {lineSummaries.slice(0, 9).map(line => {
              const todayEff = line.efficiencyPct ?? 0;
              const monthEff = line.monthlyEfficiencyPct;
              const todayH = Math.round((todayEff / 100) * 120);
              const monthH = Math.round((monthEff / 100) * 120);
              return (
                <div key={line.lineId} className="flex flex-col items-center flex-1">
                  <div className="flex items-end gap-1 h-32 justify-center w-full">
                    <div className="w-3 bg-[#2196F3] rounded-t relative" style={{ height: `${todayH}px` }}>
                      <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[8px] font-bold text-slate-600">
                        {todayEff}%
                      </span>
                    </div>
                    <div className="w-3 bg-[#FF9800] rounded-t relative" style={{ height: `${monthH}px` }}>
                      <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[8px] font-bold text-slate-600">
                        {monthEff}%
                      </span>
                    </div>
                  </div>
                  <span className="text-[10px] font-black text-slate-800 mt-1">{line.lineId}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Chart 5: Weekly Quality Trend — Sew 2 (Line Chart) */}
        <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center mb-1">
            <h3 className="text-xs font-extrabold text-slate-900">
              Weekly Quality Trend — {selectedFloor}
            </h3>
            <div className="flex gap-3 text-[10px] font-bold">
              <span className="flex items-center gap-1 text-blue-600">● BS OQL</span>
              <span className="flex items-center gap-1 text-amber-600">● NFL AQC</span>
            </div>
          </div>
          <div className="relative h-40 w-full pt-4">
            <svg className="w-full h-32" viewBox="0 0 280 100">
              {/* Grid lines */}
              <line x1="10" y1="20" x2="270" y2="20" stroke="#F1F5F9" strokeWidth="1" />
              <line x1="10" y1="50" x2="270" y2="50" stroke="#F1F5F9" strokeWidth="1" />
              <line x1="10" y1="80" x2="270" y2="80" stroke="#F1F5F9" strokeWidth="1" />

              {/* NFL AQC Trend (Amber line descending from 6.1% to 4.8%) */}
              <polyline
                fill="none"
                stroke="#FF7A00"
                strokeWidth="2.5"
                points="20,30 55,34 90,38 125,42 160,45 195,47 230,50 265,52"
              />
              {/* BS OQL Trend (Blue line descending from 3.9% to 3.1%) */}
              <polyline
                fill="none"
                stroke="#0284C7"
                strokeWidth="2.5"
                points="20,60 55,62 90,64 125,66 160,68 195,70 230,71 265,73"
              />

              {/* Points */}
              <circle cx="20" cy="30" r="3" fill="#FF7A00" />
              <circle cx="265" cy="52" r="3" fill="#FF7A00" />
              <circle cx="20" cy="60" r="3" fill="#0284C7" />
              <circle cx="265" cy="73" r="3" fill="#0284C7" />
            </svg>
            <div className="flex justify-between text-[9px] text-slate-500 font-bold px-2">
              <span>02 Oct</span>
              <span>03 Oct</span>
              <span>04 Oct</span>
              <span>05 Oct</span>
              <span>06 Oct</span>
              <span>07 Oct</span>
              <span>08 Oct</span>
              <span>09 Oct</span>
            </div>
          </div>
        </div>

        {/* Chart 6: 5 Lines with Highest DHU */}
        <div className="bg-white rounded-xl p-3.5 border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex justify-between items-center mb-1">
            <h3 className="text-xs font-extrabold text-slate-900">
              5 Lines with Highest DHU — {selectedFloor} (Today)
            </h3>
            <span className="text-[10px] text-slate-400 font-bold">DHU</span>
          </div>
          <div className="space-y-2 my-auto">
            {highestDhuLines.map((l, idx) => {
              const colors = ['#EF4444', '#F97316', '#F59E0B', '#EAB308', '#FACC15'];
              return (
                <div key={l.line} className="space-y-0.5">
                  <div className="flex justify-between text-xs font-bold text-slate-700">
                    <span>{l.line}</span>
                    <span className="text-slate-900">{l.dhu.toFixed(2)}</span>
                  </div>
                  <div className="w-full bg-slate-100 h-3 rounded-full overflow-hidden">
                    <div
                      className="h-full rounded-full"
                      style={{
                        backgroundColor: colors[idx % colors.length],
                        width: `${Math.min(100, Math.round((l.dhu / 2.0) * 100))}%`
                      }}
                    ></div>
                  </div>
                </div>
              );
            })}
          </div>
          <div className="flex justify-between text-[9px] text-slate-400 font-bold px-1 pt-2 border-t border-slate-100">
            <span>0.00</span>
            <span>0.50</span>
            <span>1.00</span>
            <span>1.50</span>
            <span>2.00</span>
          </div>
        </div>
      </div>

      {/* Bottom Table: Targeted Line Performance matching Image 2 */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="bg-[#002D62] text-white px-4 py-2.5 flex items-center justify-between">
          <h2 className="text-sm font-black tracking-wide">
            {selectedFloor} — Targeted Line Performance
          </h2>
          <span className="text-xs text-blue-200">
            {lineSummaries.length} positively targeted lines
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-xs text-left">
            <thead className="bg-[#0A3D78] text-white font-extrabold">
              <tr>
                {selectedFloor === 'All' && <th className="py-2.5 px-3">Floor</th>}
                <th className="py-2.5 px-3">Line</th>
                <th className="py-2.5 px-3">Item Name</th>
                <th className="py-2.5 px-3 text-right">SMV</th>
                <th className="py-2.5 px-3 text-right">Target</th>
                <th className="py-2.5 px-3 text-right">Output</th>
                <th className="py-2.5 px-3 text-right">Achievement</th>
                <th className="py-2.5 px-3 text-right">Efficiency</th>
                <th className="py-2.5 px-3 text-right">OQL</th>
                <th className="py-2.5 px-3 text-right">DHU</th>
                <th className="py-2.5 px-3 text-center">Reporting</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium">
              {lineSummaries.map(line => {
                const isReported = line.output !== null;

                return (
                  <tr key={line.lineId} className="hover:bg-slate-50 transition">
                    {selectedFloor === 'All' && (
                      <td className="py-2.5 px-3 font-semibold text-slate-500">{line.floor}</td>
                    )}
                    <td className="py-2.5 px-3 font-black text-slate-900">{line.lineId}</td>
                    <td className="py-2.5 px-3 text-slate-700">{line.styleNumber || 'Polo Shirt'}</td>
                    <td className="py-2.5 px-3 text-right text-sky-600 font-bold">14.50</td>
                    <td className="py-2.5 px-3 text-right text-slate-800 font-bold">{line.target}</td>
                    <td className="py-2.5 px-3 text-right">
                      {isReported ? (
                        <span className="font-extrabold text-slate-900">{line.output}</span>
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
                    <td className="py-2.5 px-3 text-right">
                      {isReported && line.efficiencyPct !== null ? (
                        <span className="font-extrabold text-blue-900">{line.efficiencyPct}%</span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {isReported && line.todayOqlPct !== null ? (
                        <span className="font-bold text-amber-700">{line.todayOqlPct}%</span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {isReported && line.dhu !== null ? (
                        <span className="font-bold text-red-600">{line.dhu}</span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      {isReported ? (
                        <span className="inline-flex items-center gap-1 text-[11px] font-black text-green-700 bg-green-100 px-2.5 py-0.5 rounded-full">
                          ✓ Complete
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[11px] font-black text-red-700 bg-red-100 px-2.5 py-0.5 rounded-full">
                          ! Not reported
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="px-4 py-2 bg-slate-50 border-t border-slate-200 text-xs text-slate-500 font-medium">
          Only lines with a positive target for the selected date are shown • Scroll for all targeted lines
        </div>
      </div>
    </div>
  );
};