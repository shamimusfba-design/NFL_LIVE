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
  targetEff: string;
  actualEff: string;
  targetAchieve: string;
  actualAchieve: string;
  dhuLimit: string;
  oqlCommit: string;
  friPass: string;
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

  // Floor-level Benchmark Defaults
  const [floorTargetEff, setFloorTargetEff] = useState('75');
  const [floorTargetAchieve, setFloorTargetAchieve] = useState('95');
  const [floorDhuLimit, setFloorDhuLimit] = useState('2.0');
  const [floorOqlCommit, setFloorOqlCommit] = useState('4.5');
  const [floorFriPass, setFloorFriPass] = useState('98.5');

  // Status message
  const [saveSuccessMsg, setSaveSuccessMsg] = useState<string | null>(null);

  // Filter lines belonging to selected floor
  const floorLines = useMemo(() => {
    return lines.filter(l => floorMatches(l.floor, selectedFloor));
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

    const fEff = existingFloorTarget ? String(existingFloorTarget.targetEfficiencyPct) : '75';
    const fAch = existingFloorTarget?.targetAchievePct !== undefined ? String(existingFloorTarget.targetAchievePct) : '95';
    const fDhu = existingFloorTarget ? String(existingFloorTarget.targetDhuPct) : '2.0';
    const fOql = existingFloorTarget ? String(existingFloorTarget.targetOqlPct) : '4.5';
    const fFri = existingFloorTarget ? String(existingFloorTarget.friPassRatePct) : '98.5';

    setFloorTargetEff(fEff);
    setFloorTargetAchieve(fAch);
    setFloorDhuLimit(fDhu);
    setFloorOqlCommit(fOql);
    setFloorFriPass(fFri);

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

      initialRows[line.id] = {
        targetEff: existingLineTarget ? String(existingLineTarget.targetEfficiencyPct) : fEff,
        actualEff: existingLineTarget?.actualEfficiencyPct !== undefined && existingLineTarget.actualEfficiencyPct !== null
          ? String(existingLineTarget.actualEfficiencyPct)
          : '',
        targetAchieve: existingLineTarget?.targetAchievePct !== undefined ? String(existingLineTarget.targetAchievePct) : fAch,
        actualAchieve: existingLineTarget?.actualAchievePct !== undefined && existingLineTarget.actualAchievePct !== null
          ? String(existingLineTarget.actualAchievePct)
          : '',
        dhuLimit: existingLineTarget ? String(existingLineTarget.targetDhuPct) : fDhu,
        oqlCommit: existingLineTarget ? String(existingLineTarget.targetOqlPct) : fOql,
        friPass: existingLineTarget ? String(existingLineTarget.friPassRatePct) : fFri,
        modified: false
      };
    });

    setRowStates(initialRows);
  }, [selectedFloor, selectedMonth, floorLines, monthlyTargets]);

  // Handler for row field change
  const handleFieldChange = (lineId: string, field: keyof LineRowState, value: string) => {
    setRowStates(prev => {
      const current = prev[lineId] || {
        targetEff: floorTargetEff,
        actualEff: '',
        targetAchieve: floorTargetAchieve,
        actualAchieve: '',
        dhuLimit: floorDhuLimit,
        oqlCommit: floorOqlCommit,
        friPass: floorFriPass,
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
        const cur = prev[line.id] || { actualEff: '', actualAchieve: '' };
        next[line.id] = {
          targetEff: floorTargetEff,
          actualEff: cur.actualEff, // keep actual efficiency intact!
          targetAchieve: floorTargetAchieve,
          actualAchieve: cur.actualAchieve,
          dhuLimit: floorDhuLimit,
          oqlCommit: floorOqlCommit,
          friPass: floorFriPass,
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
      alert('Demo mode: Saving monthly targets is disabled.');
      return;
    }

    const targetsToSave: MonthlyTargetConfig[] = [];

    const cleanFloor = selectedFloor.trim().replace(/\//g, '_');

    // Calculate floor average of line-wise actuals entered
    const validActualEffs = floorLines
      .map(l => rowStates[l.id]?.actualEff)
      .filter((v): v is string => !!v && v.trim() !== '')
      .map(Number)
      .filter(n => !isNaN(n));
    const floorAvgActualEff = validActualEffs.length > 0
      ? Number((validActualEffs.reduce((a, b) => a + b, 0) / validActualEffs.length).toFixed(1))
      : undefined;

    const validActualAchieves = floorLines
      .map(l => rowStates[l.id]?.actualAchieve)
      .filter((v): v is string => !!v && v.trim() !== '')
      .map(Number)
      .filter(n => !isNaN(n));
    const floorAvgActualAchieve = validActualAchieves.length > 0
      ? Number((validActualAchieves.reduce((a, b) => a + b, 0) / validActualAchieves.length).toFixed(1))
      : undefined;

    // 1. Save floor benchmark target
    const floorDocId = cleanFloor;
    targetsToSave.push({
      id: floorDocId,
      floor: selectedFloor,
      lineId: '',
      monthYear: selectedMonth,
      targetEfficiencyPct: Number(floorTargetEff) || 75,
      actualEfficiencyPct: floorAvgActualEff,
      targetAchievePct: Number(floorTargetAchieve) || 95,
      actualAchievePct: floorAvgActualAchieve,
      targetDhuPct: Number(floorDhuLimit) || 2.0,
      targetOqlPct: Number(floorOqlCommit) || 4.5,
      bsQaOqlPct: Number(floorOqlCommit) || 4.5,
      friPassRatePct: Number(floorFriPass) || 98.5
    });

    // 2. Save every line on the floor
    floorLines.forEach(line => {
      const row = rowStates[line.id];
      const cleanLine = line.id.replace(/^Line\s*/i, '').trim();
      const lineDocId = `${cleanFloor}_${cleanLine}`;

      const targetEff = row && row.targetEff.trim() !== '' ? Number(row.targetEff) : Number(floorTargetEff) || 75;
      const actualEff = row && row.actualEff.trim() !== '' ? Number(row.actualEff) : undefined;
      const targetAchieve = row && row.targetAchieve.trim() !== '' ? Number(row.targetAchieve) : Number(floorTargetAchieve) || 95;
      const actualAchieve = row && row.actualAchieve.trim() !== '' ? Number(row.actualAchieve) : undefined;
      const dhuLimit = row && row.dhuLimit.trim() !== '' ? Number(row.dhuLimit) : Number(floorDhuLimit) || 2.0;
      const oqlCommit = row && row.oqlCommit.trim() !== '' ? Number(row.oqlCommit) : Number(floorOqlCommit) || 4.5;
      const friPass = row && row.friPass.trim() !== '' ? Number(row.friPass) : Number(floorFriPass) || 98.5;

      targetsToSave.push({
        id: lineDocId,
        floor: selectedFloor,
        lineId: cleanLine,
        monthYear: selectedMonth,
        targetEfficiencyPct: targetEff,
        actualEfficiencyPct: actualEff,
        targetAchievePct: targetAchieve,
        actualAchievePct: actualAchieve,
        targetDhuPct: dhuLimit,
        targetOqlPct: oqlCommit,
        bsQaOqlPct: oqlCommit,
        friPassRatePct: friPass
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

    setSaveSuccessMsg(`✓ সফলভাবে ${selectedFloor}-এর সকল (${floorLines.length}টি) লাইনের মান্থলি ডেটা ও আপ-টু-ডেট এফিসিয়েন্সি এক ক্লিকে সেভ হয়েছে!`);
    setTimeout(() => setSaveSuccessMsg(null), 4000);
  };

  return (
    <div className="space-y-4">
      {/* Header Bar: Floor Tabs & Month Selector */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-black text-[#002D62] flex items-center gap-2">
            <span>📋 One-Shot Monthly Data &amp; Line Efficiency Grid</span>
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold uppercase">
              All Lines Table
            </span>
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            একবারে পুরো ফ্লোরের সব লাইনের আপ-টু-ডেট মান্থলি এফিসিয়েন্সি ডে বাই ডে টেবিল আকারে লিখে One-Shot সেভ করুন। বারবার ড্রপডাউন খোলার প্রয়োজন নেই।
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div>
            <label className="block text-[10px] font-bold text-slate-500 mb-0.5">মাস (Month)</label>
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
            <span>💾 Save All Lines (One-Shot)</span>
          </button>
        </div>
      </div>

      {/* Floor Selection Pills */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
        <span className="text-xs font-bold text-slate-500 mr-1 shrink-0">ফ্লোর নির্বাচন:</span>
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
            <span>{selectedFloor} ফ্লোর বেঞ্চমার্ক ডিফল্ট (Floor Defaults)</span>
          </div>
          <button
            type="button"
            onClick={handleApplyDefaultsToAll}
            className="text-[11px] font-bold text-blue-700 hover:text-blue-900 bg-blue-100 hover:bg-blue-200 px-2.5 py-1 rounded-lg transition"
          >
            ⚡ সব লাইনে ডিফল্ট কপি করুন (Copy Defaults to All)
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
          <div>
            <label className="block text-[10px] font-bold text-slate-600 mb-0.5">ফ্লোর টার্গেট এফিসিয়েন্সি (%)</label>
            <input
              type="number"
              step="0.5"
              value={floorTargetEff}
              onChange={e => setFloorTargetEff(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white text-emerald-800"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold text-slate-600 mb-0.5">টার্গেট অ্যাচিভ % (%)</label>
            <input
              type="number"
              step="0.5"
              value={floorTargetAchieve}
              onChange={e => setFloorTargetAchieve(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white text-blue-800"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold text-slate-600 mb-0.5">DHU লিমিট (%)</label>
            <input
              type="number"
              step="0.1"
              value={floorDhuLimit}
              onChange={e => setFloorDhuLimit(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white text-amber-800"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold text-slate-600 mb-0.5">OQL কমিটমেন্ট (%)</label>
            <input
              type="number"
              step="0.1"
              value={floorOqlCommit}
              onChange={e => setFloorOqlCommit(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white"
            />
          </div>
          <div>
            <label className="block text-[10px] font-bold text-slate-600 mb-0.5">FRI পাস রেট (%)</label>
            <input
              type="number"
              step="0.1"
              value={floorFriPass}
              onChange={e => setFloorFriPass(e.target.value)}
              className="w-full text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white text-emerald-700"
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

      {/* ONE-SHOT SPREADSHEET TABLE OF ALL LINES */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="text-xs font-black text-slate-800">
            {selectedFloor} - মোট {floorLines.length}টি লাইনের ওয়ান শট মান্থলি ডেটা টেবিল ({selectedMonth})
          </div>
          <div className="text-[11px] text-slate-500 font-bold">
            💡 কীবোর্ডের <kbd className="px-1.5 py-0.5 bg-white border rounded text-[10px] font-mono">Tab</kbd> চাপলে এক লাইন থেকে পরবর্তী লাইনে দ্রুত যেতে পারবেন
          </div>
        </div>

        <div className="overflow-x-auto max-h-[520px]">
          <table className="w-full text-xs border-collapse">
            <thead className="sticky top-0 bg-[#002D62] text-white z-10 text-[11px] font-black uppercase">
              <tr>
                <th className="p-2.5 text-left w-24">লাইন (Line)</th>
                <th className="p-2.5 text-center bg-blue-900 w-32">
                  টার্গেট এফিসিয়েন্সি (%)
                  <div className="text-[9px] font-normal normal-case text-blue-200">Target Eff %</div>
                </th>
                <th className="p-2.5 text-center bg-emerald-900 w-36 border-x border-emerald-700">
                  ⭐ আপ-টু-ডেট মান্থলি Eff (%)
                  <div className="text-[9px] font-normal normal-case text-emerald-200">Day-by-Day Actual Eff</div>
                </th>
                <th className="p-2.5 text-center w-28">
                  টার্গেট Achieve %
                  <div className="text-[9px] font-normal normal-case text-slate-300">Target Ach %</div>
                </th>
                <th className="p-2.5 text-center w-32">
                  আপ-টু-ডেট Achieve %
                  <div className="text-[9px] font-normal normal-case text-slate-300">Actual Ach %</div>
                </th>
                <th className="p-2.5 text-center w-24">
                  DHU Limit %
                  <div className="text-[9px] font-normal normal-case text-slate-300">Max DHU</div>
                </th>
                <th className="p-2.5 text-center w-24">
                  OQL Limit %
                  <div className="text-[9px] font-normal normal-case text-slate-300">Max OQL</div>
                </th>
                <th className="p-2.5 text-center w-24">
                  FRI Pass %
                  <div className="text-[9px] font-normal normal-case text-slate-300">Pass Rate</div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white">
              {floorLines.map((line, idx) => {
                const row = rowStates[line.id] || {
                  targetEff: floorTargetEff,
                  actualEff: '',
                  targetAchieve: floorTargetAchieve,
                  actualAchieve: '',
                  dhuLimit: floorDhuLimit,
                  oqlCommit: floorOqlCommit,
                  friPass: floorFriPass,
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
                        <span>{lineDisplayName(line.id)}</span>
                      </div>
                    </td>

                    {/* Target Eff % */}
                    <td className="p-1.5">
                      <input
                        type="number"
                        step="0.5"
                        min="0"
                        max="100"
                        value={row.targetEff}
                        onChange={e => handleFieldChange(line.id, 'targetEff', e.target.value)}
                        placeholder={floorTargetEff}
                        className="w-full text-center text-xs font-black border border-slate-300 rounded-lg p-1.5 bg-white text-emerald-800 focus:ring-2 focus:ring-blue-500"
                      />
                    </td>

                    {/* ⭐ Up-to-Date Actual Efficiency % (Day-by-Day) */}
                    <td className="p-1.5 bg-emerald-50/50 border-x border-emerald-200">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="100"
                        value={row.actualEff}
                        onChange={e => handleFieldChange(line.id, 'actualEff', e.target.value)}
                        placeholder="e.g. 78.5"
                        className="w-full text-center text-xs font-black border-2 border-emerald-500 rounded-lg p-1.5 bg-white text-emerald-900 shadow-sm focus:ring-2 focus:ring-emerald-600"
                      />
                    </td>

                    {/* Target Achieve % */}
                    <td className="p-1.5">
                      <input
                        type="number"
                        step="0.5"
                        min="0"
                        max="100"
                        value={row.targetAchieve}
                        onChange={e => handleFieldChange(line.id, 'targetAchieve', e.target.value)}
                        placeholder={floorTargetAchieve}
                        className="w-full text-center text-xs font-bold border border-slate-300 rounded-lg p-1.5 bg-white text-blue-800"
                      />
                    </td>

                    {/* Up-to-Date Actual Achieve % */}
                    <td className="p-1.5">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="150"
                        value={row.actualAchieve}
                        onChange={e => handleFieldChange(line.id, 'actualAchieve', e.target.value)}
                        placeholder="Auto/Calculated"
                        className="w-full text-center text-xs font-bold border border-slate-300 rounded-lg p-1.5 bg-white text-slate-800"
                      />
                    </td>

                    {/* DHU Limit % */}
                    <td className="p-1.5">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="20"
                        value={row.dhuLimit}
                        onChange={e => handleFieldChange(line.id, 'dhuLimit', e.target.value)}
                        placeholder={floorDhuLimit}
                        className="w-full text-center text-xs font-bold border border-slate-300 rounded-lg p-1.5 bg-white text-amber-800"
                      />
                    </td>

                    {/* OQL Limit % */}
                    <td className="p-1.5">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="20"
                        value={row.oqlCommit}
                        onChange={e => handleFieldChange(line.id, 'oqlCommit', e.target.value)}
                        placeholder={floorOqlCommit}
                        className="w-full text-center text-xs font-bold border border-slate-300 rounded-lg p-1.5 bg-white text-slate-700"
                      />
                    </td>

                    {/* FRI Pass % */}
                    <td className="p-1.5">
                      <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="100"
                        value={row.friPass}
                        onChange={e => handleFieldChange(line.id, 'friPass', e.target.value)}
                        placeholder={floorFriPass}
                        className="w-full text-center text-xs font-bold border border-slate-300 rounded-lg p-1.5 bg-white text-emerald-700"
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
            {selectedFloor} ফ্লোরের মোট {floorLines.length}টি লাইনের ডেটা রেডি আছে। সেভ বাটনে চাপলে সব লাইন একবারে ক্লাউড ও সিস্টেমে আপডেট হবে।
          </div>

          <button
            type="button"
            onClick={handleSaveAll}
            className="w-full sm:w-auto bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition flex items-center justify-center gap-2"
          >
            <span>💾 Save All Lines in One Shot (সব লাইনের ডেটা একসাথে সেভ করুন)</span>
          </button>
        </div>
      </div>
    </div>
  );
};
