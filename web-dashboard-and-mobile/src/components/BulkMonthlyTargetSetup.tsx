import React, { useState, useEffect, useMemo } from 'react';
import { LineMasterItem, MonthlyTargetConfig, PhysicalFloor } from '../types';
import { floorMatches, lineDisplayName } from '../data/masterData';

interface BulkMonthlyTargetSetupProps {
  lines: LineMasterItem[];
  formFloor: PhysicalFloor;
  monthYear: string;
  monthlyTargets: MonthlyTargetConfig[];
  onSaveMonthlyTargets?: (targets: MonthlyTargetConfig[]) => void;
  onSaveMonthlyTarget?: (target: MonthlyTargetConfig) => void;
  readOnlyDemo?: boolean;
}

interface LineRowState {
  monthlyEff: string;
  monthlyDhu: string;
  monthlyOql: string;
  modified: boolean;
}

const AVAILABLE_FLOORS: PhysicalFloor[] = ['Sew 1', 'Sew 2', 'Sew 3', 'Cutting', 'Print', 'Embroidery'];

export const BulkMonthlyTargetSetup: React.FC<BulkMonthlyTargetSetupProps> = ({
  lines,
  formFloor,
  monthYear,
  monthlyTargets,
  onSaveMonthlyTargets,
  onSaveMonthlyTarget,
  readOnlyDemo = false
}) => {
  const [selectedFloor, setSelectedFloor] = useState<PhysicalFloor>(formFloor);
  const [selectedMonth, setSelectedMonth] = useState<string>(monthYear);

  // Floor-level Default Benchmarks
  const [floorMonthlyEff, setFloorMonthlyEff] = useState('75.0');
  const [floorMonthlyDhu, setFloorMonthlyDhu] = useState('2.0');
  const [floorMonthlyOql, setFloorMonthlyOql] = useState('4.5');

  // Status message
  const [saveSuccessMsg, setSaveSuccessMsg] = useState<string | null>(null);

  // Filter lines belonging to selected floor (active lines only)
  const floorLines = useMemo(() => {
    return lines.filter(l => l.isActive !== false && floorMatches(l.floor, selectedFloor));
  }, [lines, selectedFloor]);

  // Line rows state map
  const [rowStates, setRowStates] = useState<Record<string, LineRowState>>({});

  // Initialize and populate line rows when floor, month, or monthlyTargets change
  useEffect(() => {
    const cleanFloor = selectedFloor.trim().replace(/\//g, '_');

    // 1. Look for existing floor-level target
    const existingFloorTarget = monthlyTargets.find(
      m =>
        floorMatches(m.floor, selectedFloor) &&
        (!m.lineId || m.lineId === '' || m.lineId.toUpperCase() === 'ALL' || m.id === cleanFloor)
    );

    const fEff = existingFloorTarget?.actualEfficiencyPct !== undefined && existingFloorTarget.actualEfficiencyPct !== null
      ? String(existingFloorTarget.actualEfficiencyPct)
      : (existingFloorTarget ? String(existingFloorTarget.targetEfficiencyPct) : '75.0');

    const fDhu = existingFloorTarget?.actualDhuPct !== undefined && existingFloorTarget.actualDhuPct !== null
      ? String(existingFloorTarget.actualDhuPct)
      : (existingFloorTarget ? String(existingFloorTarget.targetDhuPct) : '2.0');

    const fOql = existingFloorTarget?.actualOqlPct !== undefined && existingFloorTarget.actualOqlPct !== null
      ? String(existingFloorTarget.actualOqlPct)
      : (existingFloorTarget ? String(existingFloorTarget.targetOqlPct) : '4.5');

    setFloorMonthlyEff(fEff);
    setFloorMonthlyDhu(fDhu);
    setFloorMonthlyOql(fOql);

    // 2. Populate each line on the floor
    const initialRows: Record<string, LineRowState> = {};
    floorLines.forEach(line => {
      const cleanLine = line.id.replace(/^Line\s*/i, '').trim();
      const lineDocId = `${cleanFloor}_${cleanLine}`;
      const existingLineTarget = monthlyTargets.find(
        m =>
          floorMatches(m.floor, selectedFloor) &&
          (m.lineId === line.id || m.lineId === cleanLine || m.lineId === 'Line ' + cleanLine || m.id === lineDocId)
      );

      const lEff = existingLineTarget?.actualEfficiencyPct !== undefined && existingLineTarget.actualEfficiencyPct !== null
        ? String(existingLineTarget.actualEfficiencyPct)
        : (existingLineTarget ? String(existingLineTarget.targetEfficiencyPct) : fEff);

      const lDhu = existingLineTarget?.actualDhuPct !== undefined && existingLineTarget.actualDhuPct !== null
        ? String(existingLineTarget.actualDhuPct)
        : (existingLineTarget ? String(existingLineTarget.targetDhuPct) : fDhu);

      const lOql = existingLineTarget?.actualOqlPct !== undefined && existingLineTarget.actualOqlPct !== null
        ? String(existingLineTarget.actualOqlPct)
        : (existingLineTarget ? String(existingLineTarget.targetOqlPct) : fOql);

      initialRows[line.id] = {
        monthlyEff: lEff,
        monthlyDhu: lDhu,
        monthlyOql: lOql,
        modified: false
      };
    });

    setRowStates(initialRows);
  }, [selectedFloor, selectedMonth, floorLines, monthlyTargets]);

  // Handler for row field change
  const handleFieldChange = (lineId: string, field: 'monthlyEff' | 'monthlyDhu' | 'monthlyOql', value: string) => {
    setRowStates(prev => {
      const current = prev[lineId] || {
        monthlyEff: floorMonthlyEff,
        monthlyDhu: floorMonthlyDhu,
        monthlyOql: floorMonthlyOql,
        modified: true
      };
      return {
        ...prev,
        [lineId]: {
          ...current,
          [field]: value,
          modified: true
        }
      };
    });
  };

  // Quick action: Apply floor defaults to all lines
  const handleApplyDefaultsToAll = () => {
    setRowStates(prev => {
      const next: Record<string, LineRowState> = {};
      floorLines.forEach(line => {
        next[line.id] = {
          monthlyEff: floorMonthlyEff,
          monthlyDhu: floorMonthlyDhu,
          monthlyOql: floorMonthlyOql,
          modified: true
        };
      });
      return next;
    });
    setSaveSuccessMsg(`✓ Applied floor defaults to all ${floorLines.length} lines on ${selectedFloor}`);
    setTimeout(() => setSaveSuccessMsg(null), 3000);
  };

  // ONE-SHOT SAVE ALL LINES
  const handleSaveAll = () => {
    if (readOnlyDemo) {
      alert('Demo mode: Saving monthly data is disabled.');
      return;
    }

    const targetsToSave: MonthlyTargetConfig[] = [];
    const cleanFloor = selectedFloor.trim().replace(/\//g, '_');

    // Calculate floor average of line-wise actuals entered
    const validEffs = floorLines
      .map(l => rowStates[l.id]?.monthlyEff)
      .filter((v): v is string => !!v && v.trim() !== '')
      .map(Number)
      .filter(n => !isNaN(n));
    const floorAvgEff = validEffs.length > 0
      ? Number((validEffs.reduce((a, b) => a + b, 0) / validEffs.length).toFixed(1))
      : Number(floorMonthlyEff) || 75.0;

    const validDhus = floorLines
      .map(l => rowStates[l.id]?.monthlyDhu)
      .filter((v): v is string => !!v && v.trim() !== '')
      .map(Number)
      .filter(n => !isNaN(n));
    const floorAvgDhu = validDhus.length > 0
      ? Number((validDhus.reduce((a, b) => a + b, 0) / validDhus.length).toFixed(2))
      : Number(floorMonthlyDhu) || 2.0;

    const validOqls = floorLines
      .map(l => rowStates[l.id]?.monthlyOql)
      .filter((v): v is string => !!v && v.trim() !== '')
      .map(Number)
      .filter(n => !isNaN(n));
    const floorAvgOql = validOqls.length > 0
      ? Number((validOqls.reduce((a, b) => a + b, 0) / validOqls.length).toFixed(2))
      : Number(floorMonthlyOql) || 4.5;

    // 1. Save floor benchmark target
    const floorDocId = cleanFloor;
    targetsToSave.push({
      id: floorDocId,
      floor: selectedFloor,
      lineId: '',
      monthYear: selectedMonth,
      targetEfficiencyPct: floorAvgEff,
      actualEfficiencyPct: floorAvgEff,
      targetAchievePct: 95.0,
      actualAchievePct: 95.0,
      targetDhuPct: floorAvgDhu,
      actualDhuPct: floorAvgDhu,
      targetOqlPct: floorAvgOql,
      actualOqlPct: floorAvgOql,
      bsQaOqlPct: floorAvgOql,
      friPassRatePct: 100.0
    });

    // 2. Save every line on the floor
    floorLines.forEach(line => {
      const row = rowStates[line.id];
      const cleanLine = line.id.replace(/^Line\s*/i, '').trim();
      const lineDocId = `${cleanFloor}_${cleanLine}`;

      const eff = row && row.monthlyEff.trim() !== '' ? Number(row.monthlyEff) : Number(floorMonthlyEff) || 75.0;
      const dhu = row && row.monthlyDhu.trim() !== '' ? Number(row.monthlyDhu) : Number(floorMonthlyDhu) || 2.0;
      const oql = row && row.monthlyOql.trim() !== '' ? Number(row.monthlyOql) : Number(floorMonthlyOql) || 4.5;

      targetsToSave.push({
        id: lineDocId,
        floor: selectedFloor,
        lineId: cleanLine,
        monthYear: selectedMonth,
        targetEfficiencyPct: eff,
        actualEfficiencyPct: eff,
        targetAchievePct: 95.0,
        actualAchievePct: 95.0,
        targetDhuPct: dhu,
        actualDhuPct: dhu,
        targetOqlPct: oql,
        actualOqlPct: oql,
        bsQaOqlPct: oql,
        friPassRatePct: 100.0
      });
    });

    if (onSaveMonthlyTargets) {
      onSaveMonthlyTargets(targetsToSave);
    } else if (onSaveMonthlyTarget) {
      targetsToSave.forEach(t => onSaveMonthlyTarget(t));
    }

    // Mark all as unmodified
    setRowStates(prev => {
      const next: Record<string, LineRowState> = {};
      Object.keys(prev).forEach(k => {
        next[k] = { ...prev[k], modified: false };
      });
      return next;
    });

    setSaveSuccessMsg(`✓ Successfully saved Monthly Data (Efficiency, DHU, OQL) for all ${floorLines.length} lines on ${selectedFloor}!`);
    setTimeout(() => setSaveSuccessMsg(null), 4000);
  };

  return (
    <div className="space-y-4">
      {/* Header Bar: Floor Tabs & Month Selector */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-black text-[#002D62] flex items-center gap-2">
            <span>📋 Line-wise Monthly Data Entry</span>
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold uppercase">
              3 Key Metrics Only
            </span>
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Enter Line-wise Monthly Efficiency, Monthly DHU, and Monthly OQL in one fast table.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div>
            <label className="block text-[10px] font-bold text-slate-500 mb-0.5">Month (YYYY-MM)</label>
            <input
              type="month"
              value={selectedMonth}
              onChange={e => setSelectedMonth(e.target.value)}
              className="text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white"
            />
          </div>
          <button
            type="button"
            onClick={handleSaveAll}
            className="self-end bg-emerald-600 hover:bg-emerald-700 text-white font-black text-xs px-4 py-2 rounded-xl shadow transition flex items-center gap-1.5"
          >
            <span>💾 Save All Lines</span>
          </button>
        </div>
      </div>

      {/* Floor Selection Pills */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
        <span className="text-xs font-bold text-slate-500 mr-1 shrink-0">Select Floor:</span>
        {AVAILABLE_FLOORS.map(fl => (
          <button
            key={fl}
            type="button"
            onClick={() => setSelectedFloor(fl)}
            className={`px-3 py-1.5 rounded-lg text-xs font-black transition shrink-0 ${
              selectedFloor === fl
                ? 'bg-[#002D62] text-white shadow'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            {fl}
          </button>
        ))}
      </div>

      {/* Floor Default Benchmarks (Quick Setup & Copy) */}
      <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200">
        <div className="flex items-center justify-between mb-2">
          <div className="text-xs font-black text-slate-800 flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-blue-600 inline-block" />
            <span>{selectedFloor} Floor Defaults (Quick Fill)</span>
          </div>
          <button
            type="button"
            onClick={handleApplyDefaultsToAll}
            className="text-[11px] font-bold text-blue-700 hover:text-blue-900 bg-blue-100 hover:bg-blue-200 px-3 py-1 rounded-lg transition"
          >
            ⚡ Copy Defaults to All Lines
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <label className="block text-[11px] font-black text-slate-700 mb-1">
              Floor Monthly Efficiency (%)
            </label>
            <input
              type="number"
              step="0.1"
              value={floorMonthlyEff}
              onChange={e => setFloorMonthlyEff(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-2 bg-white text-emerald-800"
            />
          </div>
          <div>
            <label className="block text-[11px] font-black text-slate-700 mb-1">
              Floor Monthly DHU (%)
            </label>
            <input
              type="number"
              step="0.01"
              value={floorMonthlyDhu}
              onChange={e => setFloorMonthlyDhu(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-2 bg-white text-amber-800"
            />
          </div>
          <div>
            <label className="block text-[11px] font-black text-slate-700 mb-1">
              Floor Monthly OQL (%)
            </label>
            <input
              type="number"
              step="0.01"
              value={floorMonthlyOql}
              onChange={e => setFloorMonthlyOql(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-2 bg-white text-blue-800"
            />
          </div>
        </div>
      </div>

      {saveSuccessMsg && (
        <div className="p-3 bg-emerald-50 text-emerald-900 text-xs font-black rounded-xl border border-emerald-300 flex items-center gap-2 shadow-sm animate-pulse">
          <span className="text-base">✅</span>
          <span>{saveSuccessMsg}</span>
        </div>
      )}

      {/* ONE-SHOT SPREADSHEET TABLE OF ALL LINES: ONLY 3 METRICS */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="text-xs font-black text-slate-800">
            {selectedFloor} · Monthly Data ({floorLines.length} Lines · {selectedMonth})
          </div>
          <div className="text-[11px] text-slate-500 font-bold">
            💡 Press <kbd className="px-1.5 py-0.5 bg-white border rounded text-[10px] font-mono">Tab</kbd> to rapidly navigate across fields
          </div>
        </div>

        <div className="overflow-x-auto max-h-[520px]">
          <table className="w-full text-xs border-collapse">
            <thead className="sticky top-0 bg-[#002D62] text-white z-10 text-[11px] font-black uppercase">
              <tr>
                <th className="p-2.5 text-left w-32">Line ID</th>
                <th className="p-2.5 text-center bg-emerald-900">
                  Monthly Efficiency %
                  <div className="text-[9px] font-normal normal-case text-emerald-200">Line Efficiency</div>
                </th>
                <th className="p-2.5 text-center bg-amber-900">
                  Monthly DHU %
                  <div className="text-[9px] font-normal normal-case text-amber-200">Defects / Hundred</div>
                </th>
                <th className="p-2.5 text-center bg-blue-900">
                  Monthly OQL %
                  <div className="text-[9px] font-normal normal-case text-blue-200">Quality Audit %</div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white">
              {floorLines.map((line, idx) => {
                const row = rowStates[line.id] || {
                  monthlyEff: floorMonthlyEff,
                  monthlyDhu: floorMonthlyDhu,
                  monthlyOql: floorMonthlyOql,
                  modified: false
                };

                return (
                  <tr
                    key={line.id}
                    className={`hover:bg-blue-50/40 transition-colors ${
                      idx % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'
                    }`}
                  >
                    {/* Line Column */}
                    <td className="p-2.5 font-black text-[#002D62]">
                      <div className="flex items-center gap-1.5">
                        <span className="w-2 h-2 rounded-full bg-blue-600 inline-block" />
                        <span className="text-xs">{lineDisplayName(line.id)}</span>
                      </div>
                    </td>

                    {/* 1. Monthly Efficiency % */}
                    <td className="p-2 bg-emerald-50/30">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="100"
                        value={row.monthlyEff}
                        onChange={e => handleFieldChange(line.id, 'monthlyEff', e.target.value)}
                        placeholder="e.g. 78.5"
                        className="w-full text-center text-xs font-black border-2 border-emerald-500 rounded-lg p-2 bg-white text-emerald-900 shadow-sm focus:ring-2 focus:ring-emerald-600"
                      />
                    </td>

                    {/* 2. Monthly DHU % */}
                    <td className="p-2 bg-amber-50/30">
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        max="20"
                        value={row.monthlyDhu}
                        onChange={e => handleFieldChange(line.id, 'monthlyDhu', e.target.value)}
                        placeholder="e.g. 2.10"
                        className="w-full text-center text-xs font-black border border-amber-400 rounded-lg p-2 bg-white text-amber-900 shadow-sm focus:ring-2 focus:ring-amber-500"
                      />
                    </td>

                    {/* 3. Monthly OQL % */}
                    <td className="p-2 bg-blue-50/30">
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        max="20"
                        value={row.monthlyOql}
                        onChange={e => handleFieldChange(line.id, 'monthlyOql', e.target.value)}
                        placeholder="e.g. 4.25"
                        className="w-full text-center text-xs font-black border border-blue-400 rounded-lg p-2 bg-white text-blue-900 shadow-sm focus:ring-2 focus:ring-blue-500"
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Table Footer with Bottom Save Action */}
        <div className="p-3.5 bg-slate-50 border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="text-xs text-slate-600 font-semibold">
            Configuring all {floorLines.length} lines on {selectedFloor}. Clicking Save updates both local state and Cloud Firestore simultaneously.
          </div>

          <button
            type="button"
            onClick={handleSaveAll}
            className="w-full sm:w-auto bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition flex items-center justify-center gap-2"
          >
            <span>💾 Save All Lines in One Shot</span>
          </button>
        </div>
      </div>
    </div>
  );
};
