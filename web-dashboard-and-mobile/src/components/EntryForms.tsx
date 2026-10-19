import React, { useState } from 'react';
import {
  FloorId,
  LineMasterItem,
  ProductionEntryRecord,
  QualityEntryRecord,
  LineTargetConfig,
  QualityDefectItem
} from '../types';
import { STANDARD_STYLES, STANDARD_HOUR_SLOTS, STANDARD_DEFECT_TYPES } from '../data/masterData';

interface EntryFormsProps {
  lines: LineMasterItem[];
  currentUserName: string;
  onSaveProduction: (entry: Omit<ProductionEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>) => void;
  onSaveQuality: (entry: Omit<QualityEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>) => void;
  onSaveSetup: (setup: LineTargetConfig) => void;
  onClose: () => void;
}

export const EntryForms: React.FC<EntryFormsProps> = ({
  lines,
  currentUserName,
  onSaveProduction,
  onSaveQuality,
  onSaveSetup,
  onClose
}) => {
  const [activeTab, setActiveTab] = useState<'PRODUCTION' | 'QUALITY' | 'SETUP'>('PRODUCTION');

  // Shared state
  const [formFloor, setFormFloor] = useState<'Sew 1' | 'Sew 2' | 'Sew 3'>('Sew 2');
  const [formLine, setFormLine] = useState('K1');
  const [formHourSlot, setFormHourSlot] = useState(STANDARD_HOUR_SLOTS[2]);
  const [formStyle, setFormStyle] = useState(STANDARD_STYLES[0].styleNumber);

  // Production State
  const [prodQty, setProdQty] = useState('85');
  const [prodIsZero, setProdIsZero] = useState(false);
  const [prodRemarks, setProdRemarks] = useState('');
  const [prodSuccess, setProdSuccess] = useState(false);

  // Quality State
  const [checkedGarments, setCheckedGarments] = useState('100');
  const [defectiveGarments, setDefectiveGarments] = useState('4');
  const [defectCounts, setDefectCounts] = useState<Record<string, number>>({
    'Poor iron': 2,
    'Uncut thread': 2
  });
  const [qaRemarks, setQaRemarks] = useState('');
  const [qaSuccess, setQaSuccess] = useState(false);

  // Supervisor Setup State
  const [setupDailyTarget, setSetupDailyTarget] = useState('500');
  const [setupSmv, setSetupSmv] = useState('14.5');
  const [setupOps, setSetupOps] = useState('20');
  const [setupHelpers, setSetupHelpers] = useState('2');
  const [setupIncludeHelpers, setSetupIncludeHelpers] = useState(false);
  const [setupSuccess, setSetupSuccess] = useState(false);

  // Calculated Quality preview
  const numChecked = Number(checkedGarments) || 0;
  const numDefective = Number(defectiveGarments) || 0;
  const totalDefects = Object.values(defectCounts).reduce((s, c) => s + c, 0);
  const liveOql = numChecked > 0 ? ((numDefective / numChecked) * 100).toFixed(2) : '0.00';
  const liveDhu = numChecked > 0 ? ((totalDefects / numChecked) * 100).toFixed(2) : '0.00';

  const handleDefectChange = (defect: string, delta: number) => {
    const current = defectCounts[defect] || 0;
    const next = Math.max(0, current + delta);
    setDefectCounts(prev => ({ ...prev, [defect]: next }));
  };

  const handleProductionSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const qty = prodIsZero ? 0 : Number(prodQty);

    onSaveProduction({
      lineId: formLine,
      floor: formFloor,
      date: '2026-10-09',
      hourSlot: formHourSlot,
      styleNumber: formStyle,
      outputQty: qty,
      isConfirmedZero: prodIsZero,
      remarks: prodRemarks,
      reportedBy: currentUserName,
      isDemo: false
    });

    setProdSuccess(true);
    setTimeout(() => setProdSuccess(false), 2000);
  };

  const handleQualitySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const defectsList: QualityDefectItem[] = Object.entries(defectCounts)
      .filter(([, count]) => count > 0)
      .map(([defectType, count]) => ({ defectType, count }));

    onSaveQuality({
      lineId: formLine,
      floor: formFloor,
      date: '2026-10-09',
      hourSlot: formHourSlot,
      styleNumber: formStyle,
      checkedGarments: numChecked,
      defectiveGarments: numDefective,
      defects: defectsList,
      totalDefects,
      isConfirmedZero: numDefective === 0,
      remarks: qaRemarks,
      reportedBy: currentUserName,
      isDemo: false
    });

    setQaSuccess(true);
    setTimeout(() => setQaSuccess(false), 2000);
  };

  const handleSetupSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const daily = Number(setupDailyTarget);
    onSaveSetup({
      lineId: formLine,
      floor: formFloor,
      date: '2026-10-09',
      dailyTargetPcs: daily,
      hourlyTargetPcs: Math.round(daily / 8),
      buyer: 'Nike / NFL',
      styleNumber: formStyle,
      smv: Number(setupSmv),
      operatorsCount: Number(setupOps),
      machinesCount: Number(setupOps),
      helpersCount: Number(setupHelpers),
      includeHelpersInEfficiency: setupIncludeHelpers,
      workingHoursSoFar: 7,
      totalShiftHours: 8,
      isDemo: false
    });

    setSetupSuccess(true);
    setTimeout(() => setSetupSuccess(false), 2000);
  };

  const availableLines = lines.filter(l => l.floor === formFloor);

  return (
    <div className="fixed inset-0 bg-slate-900/70 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-black tracking-wide">Factory Floor Data Entry</h2>
            <div className="text-xs text-blue-200">
              Logged in as: <span className="font-bold text-white">{currentUserName}</span>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white bg-blue-900/60 hover:bg-blue-800 p-1.5 rounded-lg text-lg font-bold"
          >
            ✕
          </button>
        </div>

        {/* Tab switcher */}
        <div className="flex border-b border-slate-200 bg-slate-50 px-4 pt-2 gap-2 text-xs font-bold">
          <button
            onClick={() => setActiveTab('PRODUCTION')}
            className={`pb-2.5 px-4 border-b-2 transition ${
              activeTab === 'PRODUCTION'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            Hourly Production
          </button>
          <button
            onClick={() => setActiveTab('QUALITY')}
            className={`pb-2.5 px-4 border-b-2 transition ${
              activeTab === 'QUALITY'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            Quality Inspection
          </button>
          <button
            onClick={() => setActiveTab('SETUP')}
            className={`pb-2.5 px-4 border-b-2 transition ${
              activeTab === 'SETUP'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            Supervisor Line Setup
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 bg-[#F8FAFC]">
          {/* Shared line selectors */}
          <div className="grid grid-cols-3 gap-3 mb-4 bg-white p-3 rounded-xl border border-slate-200">
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Floor</label>
              <select
                value={formFloor}
                onChange={e => {
                  const newFloor = e.target.value as any;
                  setFormFloor(newFloor);
                  const firstLine = lines.find(l => l.floor === newFloor)?.id || 'K1';
                  setFormLine(firstLine);
                }}
                className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
              >
                <option value="Sew 1">Sew 1</option>
                <option value="Sew 2">Sew 2</option>
                <option value="Sew 3">Sew 3</option>
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Line ID</label>
              <select
                value={formLine}
                onChange={e => setFormLine(e.target.value)}
                className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
              >
                {availableLines.map(l => (
                  <option key={l.id} value={l.id}>
                    Line {l.id}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Garment Style</label>
              <select
                value={formStyle}
                onChange={e => setFormStyle(e.target.value)}
                className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
              >
                {STANDARD_STYLES.map(s => (
                  <option key={s.styleNumber} value={s.styleNumber}>
                    {s.styleNumber} ({s.name})
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* PRODUCTION FORM */}
          {activeTab === 'PRODUCTION' && (
            <form onSubmit={handleProductionSubmit} className="space-y-4 bg-white p-5 rounded-xl border border-slate-200">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Hour Slot</label>
                  <select
                    value={formHourSlot}
                    onChange={e => setFormHourSlot(e.target.value)}
                    className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                  >
                    {STANDARD_HOUR_SLOTS.map(slot => (
                      <option key={slot} value={slot}>
                        {slot}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Output Quantity (Pcs)
                  </label>
                  <input
                    type="number"
                    min={0}
                    disabled={prodIsZero}
                    value={prodIsZero ? '0' : prodQty}
                    onChange={e => setProdQty(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2 text-slate-900"
                  />
                  <div className="mt-1 flex items-center gap-1.5 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      id="zeroCheck"
                      checked={prodIsZero}
                      onChange={e => setProdIsZero(e.target.checked)}
                    />
                    <label htmlFor="zeroCheck" className="cursor-pointer">
                      Confirmed zero (no output produced in this hour)
                    </label>
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Remarks</label>
                <input
                  type="text"
                  value={prodRemarks}
                  onChange={e => setProdRemarks(e.target.value)}
                  placeholder="e.g. Line setting 15 mins, bottle neck at cuff attachment"
                  className="w-full text-xs border border-slate-300 rounded-lg p-2"
                />
              </div>

              {prodSuccess && (
                <div className="p-2.5 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  ✓ Production output recorded successfully and reflected on live dashboard!
                </div>
              )}

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  Save Production Record
                </button>
              </div>
            </form>
          )}

          {/* QUALITY FORM */}
          {activeTab === 'QUALITY' && (
            <form onSubmit={handleQualitySubmit} className="space-y-4 bg-white p-5 rounded-xl border border-slate-200">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Checked Garments</label>
                  <input
                    type="number"
                    min={1}
                    value={checkedGarments}
                    onChange={e => setCheckedGarments(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Defective Garments</label>
                  <input
                    type="number"
                    min={0}
                    value={defectiveGarments}
                    onChange={e => setDefectiveGarments(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
              </div>

              {/* Live Metric Banner */}
              <div className="grid grid-cols-2 gap-3 bg-blue-50 p-3 rounded-lg border border-blue-200 text-center">
                <div>
                  <div className="text-[10px] text-blue-700 font-bold">Computed OQL</div>
                  <div className="text-xl font-black text-[#002D62]">{liveOql}%</div>
                </div>
                <div>
                  <div className="text-[10px] text-blue-700 font-bold">Computed DHU</div>
                  <div className="text-xl font-black text-amber-700">{liveDhu}</div>
                </div>
              </div>

              {/* Defect Counter List */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-2">
                  Defect Types Breakdown (Total Defects: {totalDefects})
                </label>
                <div className="grid grid-cols-2 gap-2 max-h-48 overflow-y-auto p-1">
                  {STANDARD_DEFECT_TYPES.map(defect => {
                    const count = defectCounts[defect] || 0;
                    return (
                      <div
                        key={defect}
                        className="flex items-center justify-between p-2 rounded-lg border border-slate-200 bg-slate-50 text-xs"
                      >
                        <span className="font-medium text-slate-800">{defect}</span>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => handleDefectChange(defect, -1)}
                            className="w-6 h-6 rounded bg-slate-200 font-bold text-slate-700 hover:bg-slate-300"
                          >
                            -
                          </button>
                          <span className="font-black text-slate-900 w-5 text-center">{count}</span>
                          <button
                            type="button"
                            onClick={() => handleDefectChange(defect, 1)}
                            className="w-6 h-6 rounded bg-blue-600 font-bold text-white hover:bg-blue-700"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Inspector Remarks</label>
                <input
                  type="text"
                  value={qaRemarks}
                  onChange={e => setQaRemarks(e.target.value)}
                  placeholder="Notes on needle changes, shading, or washing spots"
                  className="w-full text-xs border border-slate-300 rounded-lg p-2"
                />
              </div>

              {qaSuccess && (
                <div className="p-2.5 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  ✓ Quality inspection saved and added to line OQL calculation!
                </div>
              )}

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  Save Quality Inspection
                </button>
              </div>
            </form>
          )}

          {/* SUPERVISOR SETUP FORM */}
          {activeTab === 'SETUP' && (
            <form onSubmit={handleSetupSubmit} className="space-y-4 bg-white p-5 rounded-xl border border-slate-200">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Daily Target (Pcs)
                  </label>
                  <input
                    type="number"
                    min={50}
                    value={setupDailyTarget}
                    onChange={e => setSetupDailyTarget(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Style SMV (Standard Minute Value)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min={1}
                    value={setupSmv}
                    onChange={e => setSetupSmv(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Operators Census
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={setupOps}
                    onChange={e => setSetupOps(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Helpers Census
                  </label>
                  <input
                    type="number"
                    min={0}
                    value={setupHelpers}
                    onChange={e => setSetupHelpers(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 text-xs text-slate-700 font-semibold bg-slate-50 p-3 rounded-lg border border-slate-200">
                <input
                  type="checkbox"
                  id="includeHelpers"
                  checked={setupIncludeHelpers}
                  onChange={e => setSetupIncludeHelpers(e.target.checked)}
                />
                <label htmlFor="includeHelpers" className="cursor-pointer">
                  Include helpers in available minutes denominator for efficiency calculations
                </label>
              </div>

              {setupSuccess && (
                <div className="p-2.5 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  ✓ Line target and SMV setup updated successfully!
                </div>
              )}

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  Save Line Configuration
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
