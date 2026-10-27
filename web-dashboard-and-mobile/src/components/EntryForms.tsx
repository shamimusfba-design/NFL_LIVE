import React, { useState, useMemo } from 'react';
import {
  LineMasterItem,
  ProductionEntryRecord,
  QualityEntryRecord,
  LineTargetConfig,
  QualityDefectItem,
  WeeklyQualityRecord,
  MonthlyTargetConfig,
  FloorManpowerRecord,
  UserRole
} from '../types';
import { BulkTargetSetup } from './BulkTargetSetup';
import { EntryModuleId, ENTRY_MODULES } from '../data/entryModules';
import {
  STANDARD_HOUR_SLOTS,
  BI_HOURLY_AUDIT_SLOTS,
  STANDARD_DEFECT_TYPES,
  getBangladeshTodayIso,
  formatHourSlot12h,
  canWriteWeeklyQualityOrBiHourlyAudit,
  isoWeeksOfYear,
  isoWeekKeyOf
} from '../data/masterData';

interface EntryFormsProps {
  lines: LineMasterItem[];
  currentUserName: string;
  currentUserRole?: UserRole;
  weeklyRecords?: WeeklyQualityRecord[];
  onSaveProduction: (
    entry: Omit<ProductionEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>
  ) => void;
  onSaveQuality: (
    entry: Omit<QualityEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>
  ) => void;
  onSaveSetup: (setup: LineTargetConfig) => void;
  onSaveWeeklyQuality?: (record: WeeklyQualityRecord) => void;
  onSaveMonthlyTarget?: (target: MonthlyTargetConfig) => void;
  /** One module per kind of person. When set, only that module is shown. */
  module?: EntryModuleId;
  targets?: LineTargetConfig[];
  floorTotals?: FloorManpowerRecord[];
  onSaveFloorManpower?: (record: FloorManpowerRecord) => void;
  onClose: () => void;
}

export const EntryForms: React.FC<EntryFormsProps> = ({
  lines,
  currentUserName,
  currentUserRole = 'ADMIN',
  weeklyRecords = [],
  onSaveProduction,
  onSaveQuality,
  onSaveSetup,
  onSaveWeeklyQuality,
  onSaveMonthlyTarget,
  module,
  targets = [],
  floorTotals = [],
  onSaveFloorManpower,
  onClose
}) => {
  const [activeTab, setActiveTab] = useState<
    'PRODUCTION' | 'QUALITY' | 'WEEKLY_OQL' | 'SETUP' | 'MONTHLY'
  >(
    module === 'TARGET' ? 'SETUP'
      : module === 'DHU' || module === 'AUDIT' ? 'QUALITY'
      : module === 'MONTHLY' ? 'MONTHLY'
      : module === 'WEEKLY' ? 'WEEKLY_OQL'
      : 'PRODUCTION'
  );

  // Shared state
  const [formDate, setFormDate] = useState(getBangladeshTodayIso());
  const [formFloor, setFormFloor] = useState<'Sew 1' | 'Sew 2' | 'Sew 3'>('Sew 2');
  const [formLine, setFormLine] = useState('K1');
  const [formHourSlot, setFormHourSlot] = useState(STANDARD_HOUR_SLOTS[0]);
  // The item / style of a line comes from today's Target Setting. Nothing is chosen from a made-up list.
  const styleOf = (lineId: string): string =>
    targets.find(t => t.lineId === lineId && t.date === formDate && !t.isDemo)?.styleNumber || '';

  // Production State (Single + Batch All-Lines Matrix)
  const [prodEntryMode, setProdEntryMode] = useState<'SINGLE' | 'BATCH'>('SINGLE');
  const [prodQty, setProdQty] = useState('');
  const [prodIsZero, setProdIsZero] = useState(false);
  const [prodRemarks, setProdRemarks] = useState('');
  const [batchQtys, setBatchQtys] = useState<Record<string, string>>({});
  const [prodSuccess, setProdSuccess] = useState<string | null>(null);

  // Quality State (Hourly DHU 1-Hr vs Bi-Hourly Audit 2-Hr OQL)
  const [qaMode, setQaMode] = useState<'HOURLY_DHU' | 'BI_HOURLY_AUDIT'>(module === 'AUDIT' ? 'BI_HOURLY_AUDIT' : 'HOURLY_DHU');
  const [biHourlySlot, setBiHourlySlot] = useState(BI_HOURLY_AUDIT_SLOTS[0]);
  const [checkedGarments, setCheckedGarments] = useState('');
  const [defectiveGarments, setDefectiveGarments] = useState('');
  const [defectCounts, setDefectCounts] = useState<Record<string, number>>({});
  const [sewSent, setSewSent] = useState('');
  const [sewRecv, setSewRecv] = useState('');
  const [spotSent, setSpotSent] = useState('');
  const [spotRecv, setSpotRecv] = useState('');
  const [qaRemarks, setQaRemarks] = useState('');
  const [customDefects, setCustomDefects] = useState<string[]>([]);
  const [customDefectInput, setCustomDefectInput] = useState('');
  const [qaSuccess, setQaSuccess] = useState<string | null>(null);
  const [qaError, setQaError] = useState<string | null>(null);

  // Weekly BS OQL & NFL AQC State (Admin & Auditor Only)
  const todayWeekKey = isoWeekKeyOf(getBangladeshTodayIso());
  const [weekYear, setWeekYear] = useState<number>(Number(todayWeekKey.slice(0, 4)));
  const yearWeeks = useMemo(() => isoWeeksOfYear(weekYear), [weekYear]);
  const [selectedWeekKey, setSelectedWeekKey] = useState(todayWeekKey);
  const selectedWeek = yearWeeks.find(w => w.weekKey === selectedWeekKey) || yearWeeks[0];
  const selectedWeekStartIso = selectedWeek?.weekStartIso || getBangladeshTodayIso();
  const [bsOqlInput, setBsOqlInput] = useState('');
  const [nflAqcInput, setNflAqcInput] = useState('');
  const [weeklyMsg, setWeeklyMsg] = useState<string | null>(null);

  // Supervisor Setup State
  const [setupDailyTarget, setSetupDailyTarget] = useState('500');
  const [setupSmv, setSetupSmv] = useState('14.5');
  const [setupOps, setSetupOps] = useState('20');
  const [setupHelpers, setSetupHelpers] = useState('2');
  const [setupTargetEff, setSetupTargetEff] = useState('75');
  const [setupShiftHours, setSetupShiftHours] = useState('8');
  const [setupIncludeHelpers, setSetupIncludeHelpers] = useState(false);
  const [setupSuccess, setSetupSuccess] = useState(false);

  // Monthly Target State
  const [monthYear, setMonthYear] = useState(getBangladeshTodayIso().slice(0, 7));
  const [moEffTarget, setMoEffTarget] = useState('75');
  const [moDhuLimit, setMoDhuLimit] = useState('2.0');
  const [moOqlCommitment, setMoOqlCommitment] = useState('4.5');
  const [moBsQaOql, setMoBsQaOql] = useState('2.4');
  const [moFriPass, setMoFriPass] = useState('98.5');
  const [moSuccess, setMoSuccess] = useState(false);

  const canWriteAuditOrWeekly = canWriteWeeklyQualityOrBiHourlyAudit(currentUserRole);
  const floorLines = lines.filter(l => l.floor === formFloor && l.isActive && !/^(iron|packing)/i.test(l.id));
  const withTarget = floorLines.filter(l => targets.some(t => t.lineId === l.id && t.date === formDate && !t.isDemo && (t.hourlyTargetPcs > 0 || t.dailyTargetPcs > 0)));
  // Lines without a target that day are not running. If no target was set at all yet, every line is shown.
  const availableLines = withTarget.length > 0 ? withTarget : floorLines;

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
    if (prodEntryMode === 'BATCH') {
      let savedCount = 0;
      availableLines.forEach(line => {
        const raw = batchQtys[line.id];
        if (raw !== undefined && raw.trim() !== '') {
          const q = Math.max(0, Number(raw) || 0);
          onSaveProduction({
            lineId: line.id,
            floor: formFloor,
            date: formDate,
            hourSlot: formHourSlot,
            styleNumber: styleOf(line.id),
            outputQty: q,
            isConfirmedZero: q === 0,
            remarks: prodRemarks,
            reportedBy: currentUserName,
            isDemo: false
          });
          savedCount++;
        }
      });
      setProdSuccess(
        `✓ Batch saved ${savedCount} line(s) for ${formatHourSlot12h(formHourSlot)}!`
      );
      setBatchQtys({});
      setTimeout(() => setProdSuccess(null), 3000);
      return;
    }

    const qty = prodIsZero ? 0 : Number(prodQty);
    onSaveProduction({
      lineId: formLine,
      floor: formFloor,
      date: formDate,
      hourSlot: formHourSlot,
      styleNumber: styleOf(formLine),
      outputQty: qty,
      isConfirmedZero: prodIsZero,
      remarks: prodRemarks,
      reportedBy: currentUserName,
      isDemo: false
    });

    setProdSuccess(
      `✓ Saved Line ${formLine} output (${qty} pcs) for ${formatHourSlot12h(formHourSlot)}!`
    );
    setTimeout(() => setProdSuccess(null), 2500);
  };

  const handleQualitySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setQaError(null);

    if (qaMode === 'BI_HOURLY_AUDIT' && !canWriteAuditOrWeekly) {
      setQaError(
        'Access Denied: Only Admin, Auditor, and IE roles can submit Daily Bi-Hourly Audit (2-Hr OQL) data.'
      );
      return;
    }

    if (numChecked <= 0) {
      setQaError('Checked garments must be greater than 0.');
      return;
    }
    if (numDefective < 0 || numDefective > numChecked) {
      setQaError('Defective garments cannot exceed checked garments.');
      return;
    }

    const defectsList: QualityDefectItem[] = Object.entries(defectCounts)
      .filter(([, count]) => count > 0)
      .map(([defectType, count]) => ({ defectType, count }));

    const activeSlot = qaMode === 'BI_HOURLY_AUDIT' ? biHourlySlot : formHourSlot;

    onSaveQuality({
      lineId: formLine,
      floor: formFloor,
      date: formDate,
      hourSlot: activeSlot,
      styleNumber: styleOf(formLine),
      checkedGarments: numChecked,
      defectiveGarments: numDefective,
      defects: defectsList,
      totalDefects,
      defectSent: qaMode === 'BI_HOURLY_AUDIT' ? 0 : Number(sewSent) || 0,
      defectReceived: qaMode === 'BI_HOURLY_AUDIT' ? 0 : Number(sewRecv) || 0,
      spotSent: qaMode === 'BI_HOURLY_AUDIT' ? 0 : Number(spotSent) || 0,
      spotReceived: qaMode === 'BI_HOURLY_AUDIT' ? 0 : Number(spotRecv) || 0,
      isConfirmedZero: numDefective === 0,
      remarks: qaRemarks,
      reportedBy: currentUserName,
      isDemo: false
    });

    setQaSuccess(
      `✓ Saved ${
        qaMode === 'BI_HOURLY_AUDIT' ? '2-Hr Bi-Hourly Audit (OQL)' : '1-Hr Quality DHU'
      } for Line ${formLine} (${formatHourSlot12h(activeSlot)})!`
    );
    setTimeout(() => setQaSuccess(null), 2500);
  };

  const handleWeeklySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canWriteAuditOrWeekly) {
      setWeeklyMsg(
        '⚠️ Access Denied: Only Admin, Auditor, and IE roles can save Weekly BS OQL / NFL AQC.'
      );
      return;
    }
    const bs = bsOqlInput.trim() !== '' ? Number(bsOqlInput) : null;
    const aqc = nflAqcInput.trim() !== '' ? Number(nflAqcInput) : null;
    if (!selectedWeekKey || (bs === null && aqc === null)) {
      setWeeklyMsg('⚠️ Please enter BS OQL (%) or NFL AQC (%) for the selected week.');
      return;
    }
    onSaveWeeklyQuality?.({
      weekKey: selectedWeekKey,
      weekStartIso: selectedWeekStartIso,
      bsOqlPct: bs,
      nflAqcPct: aqc,
      lastUpdatedBy: currentUserName,
      lastUpdatedMillis: Date.now(),
      isDemo: false
    });
    setWeeklyMsg(`✓ Saved Weekly BS OQL / NFL AQC for ${selectedWeekKey}!`);
    setTimeout(() => setWeeklyMsg(null), 3000);
  };

  const handleSetupSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const daily = Number(setupDailyTarget);
    const hrs = Math.max(1, Number(setupShiftHours) || 8);
    onSaveSetup({
      lineId: formLine,
      floor: formFloor,
      date: formDate,
      dailyTargetPcs: daily,
      hourlyTargetPcs: Math.round(daily / hrs),
      targetEfficiencyPct: Number(setupTargetEff) || 75,
      buyer: 'Nike / NFL',
      styleNumber: styleOf(formLine),
      smv: Number(setupSmv),
      operatorsCount: Number(setupOps),
      machinesCount: Number(setupOps),
      helpersCount: Number(setupHelpers),
      includeHelpersInEfficiency: setupIncludeHelpers,
      workingHoursSoFar: hrs,
      totalShiftHours: hrs,
      isDemo: false
    });

    setSetupSuccess(true);
    setTimeout(() => setSetupSuccess(false), 2000);
  };

  const handleMonthlySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSaveMonthlyTarget?.({
      id: `${formFloor}__${monthYear}`,
      floor: formFloor,
      lineId: '',
      monthYear,
      targetEfficiencyPct: Number(moEffTarget) || 75,
      targetDhuPct: Number(moDhuLimit) || 2.0,
      targetOqlPct: Number(moOqlCommitment) || 4.5,
      bsQaOqlPct: Number(moBsQaOql) || 2.4,
      friPassRatePct: Number(moFriPass) || 98.5
    });
    setMoSuccess(true);
    setTimeout(() => setMoSuccess(false), 2500);
  };

  const autoCalcTarget = () => {
    const smv = Number(setupSmv) || 0;
    const ops = Number(setupOps) || 0;
    const hlp = setupIncludeHelpers ? Number(setupHelpers) || 0 : 0;
    const eff = (Number(setupTargetEff) || 75) / 100;
    const hrs = Number(setupShiftHours) || 8;
    if (smv > 0 && ops + hlp > 0) {
      const daily = Math.round((((ops + hlp) * hrs * 60) / smv) * eff);
      setSetupDailyTarget(String(daily));
    }
  };

  return (
    <div className="fixed inset-0 bg-slate-900/75 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-black tracking-wide">
              {module ? ENTRY_MODULES.find(m => m.id === module)?.label : 'Factory Floor Data Entry (12-Hour AM/PM Format)'}
            </h2>
            <div className="text-xs text-blue-200">
              Logged in as: <span className="font-bold text-white">{currentUserName}</span> ({currentUserRole})
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white bg-blue-900/60 hover:bg-blue-800 p-1.5 rounded-lg text-lg font-bold"
          >
            ✕
          </button>
        </div>

        {/* Module Tab Switcher (hidden when one module was chosen from the menu) */}
        {!module && (
        <div className="flex flex-wrap border-b border-slate-200 bg-slate-50 px-4 pt-2 gap-1.5 text-xs font-bold">
          <button
            onClick={() => setActiveTab('PRODUCTION')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'PRODUCTION'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            🧵 Production (12h AM/PM)
          </button>
          <button
            onClick={() => setActiveTab('QUALITY')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'QUALITY'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            🔍 Quality &amp; Bi-Hourly Audit
          </button>
          <button
            onClick={() => setActiveTab('WEEKLY_OQL')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'WEEKLY_OQL'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            📈 Weekly BS OQL / NFL AQC
          </button>
          <button
            onClick={() => setActiveTab('SETUP')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'SETUP'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            ⚙️ Morning Line Setup
          </button>
          <button
            onClick={() => setActiveTab('MONTHLY')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'MONTHLY'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            📅 Monthly Targets &amp; FRI
          </button>
        </div>

        )}

        <div className="flex-1 overflow-y-auto p-5 bg-[#F8FAFC] space-y-4">
          {/* Shared Date / Floor / Line / Style bar (hidden on Weekly tab) */}
          {activeTab !== 'WEEKLY_OQL' && activeTab !== 'SETUP' && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3 bg-white p-3 rounded-xl border border-slate-200">
              <div>
                <label className="block text-[11px] font-bold text-slate-600 mb-1">Date</label>
                <input
                  type="date"
                  value={formDate}
                  onChange={e => setFormDate(e.target.value)}
                  className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-600 mb-1">Floor</label>
                <select
                  value={formFloor}
                  onChange={e => {
                    const newFloor = e.target.value as 'Sew 1' | 'Sew 2' | 'Sew 3';
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
                  disabled={activeTab === 'PRODUCTION' && prodEntryMode === 'BATCH'}
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
                <label className="block text-[11px] font-bold text-slate-600 mb-1">Item / Style (from Target Setting)</label>
                <div className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2 text-xs font-bold text-slate-700">
                  {styleOf(formLine) || 'not set today'}
                </div>
              </div>
            </div>
          )}

          {/* ==================== TAB 1: PRODUCTION FORM (SINGLE + BATCH MATRIX) ==================== */}
          {activeTab === 'PRODUCTION' && (
            <form
              onSubmit={handleProductionSubmit}
              className="space-y-4 bg-white p-5 rounded-xl border border-slate-200"
            >
              <div className="flex items-center justify-between flex-wrap gap-2 border-b pb-3">
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setProdEntryMode('SINGLE')}
                    className={`px-3 py-1.5 rounded-lg text-xs font-black transition ${
                      prodEntryMode === 'SINGLE'
                        ? 'bg-[#002D62] text-white'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    Single Line Mode
                  </button>
                  <button
                    type="button"
                    onClick={() => setProdEntryMode('BATCH')}
                    className={`px-3 py-1.5 rounded-lg text-xs font-black transition ${
                      prodEntryMode === 'BATCH'
                        ? 'bg-[#002D62] text-white'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    ⚡ All Lines Batch Matrix ({availableLines.length} Lines)
                  </button>
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-600 mr-2">
                    Hour Slot (12-Hour AM/PM):
                  </label>
                  <select
                    value={formHourSlot}
                    onChange={e => setFormHourSlot(e.target.value)}
                    className="text-xs font-black border border-blue-400 rounded-lg p-2 bg-blue-50 text-[#002D62]"
                  >
                    {STANDARD_HOUR_SLOTS.map(slot => (
                      <option key={slot} value={slot}>
                        {formatHourSlot12h(slot)}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {prodEntryMode === 'SINGLE' ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">
                      Output Quantity (Pcs) for Line {formLine}
                    </label>
                    <input
                      type="number"
                      min={0}
                      disabled={prodIsZero}
                      value={prodIsZero ? '0' : prodQty}
                      onChange={e => setProdQty(e.target.value)}
                      className="w-full text-sm font-black border border-slate-300 rounded-lg p-2 text-slate-900"
                    />
                    <div className="mt-1.5 flex items-center gap-1.5 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        id="zeroCheck"
                        checked={prodIsZero}
                        onChange={e => setProdIsZero(e.target.checked)}
                      />
                      <label htmlFor="zeroCheck" className="cursor-pointer font-semibold">
                        Confirmed zero (no output produced in this hour)
                      </label>
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">Remarks</label>
                    <input
                      type="text"
                      value={prodRemarks}
                      onChange={e => setProdRemarks(e.target.value)}
                      placeholder="Optional production remarks"
                      className="w-full text-xs border border-slate-300 rounded-lg p-2"
                    />
                  </div>
                </div>
              ) : (
                <div>
                  <div className="text-xs font-bold text-slate-600 mb-2">
                    Enter hourly output (pcs) for any lines on <b>{formFloor}</b> during{' '}
                    <b>{formatHourSlot12h(formHourSlot)}</b>:
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-2.5 max-h-60 overflow-y-auto p-1">
                    {availableLines.map(l => (
                      <div
                        key={l.id}
                        className="p-2 rounded-lg border border-slate-200 bg-slate-50 flex flex-col gap-1"
                      >
                        <span className="text-xs font-black text-[#002D62]">Line {l.id}</span>
                        <input
                          type="number"
                          min={0}
                          placeholder="Pcs"
                          value={batchQtys[l.id] || ''}
                          onChange={e =>
                            setBatchQtys(prev => ({ ...prev, [l.id]: e.target.value }))
                          }
                          className="w-full text-xs font-black border border-slate-300 rounded p-1.5 bg-white"
                        />
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {prodSuccess && (
                <div className="p-2.5 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  {prodSuccess}
                </div>
              )}

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  {prodEntryMode === 'BATCH'
                    ? 'Save Batch Production Matrix'
                    : 'Save Production Record'}
                </button>
              </div>
            </form>
          )}

          {/* ==================== TAB 2: QUALITY & BI-HOURLY AUDIT ==================== */}
          {activeTab === 'QUALITY' && (
            <form
              onSubmit={handleQualitySubmit}
              className="space-y-4 bg-white p-5 rounded-xl border border-slate-200"
            >
              {/* Mode Sub-Tabs (hidden when Hourly DHU or Bi-Hourly OQL was chosen from the menu) */}
              <div className={`flex flex-wrap gap-2 border-b pb-3 ${module === 'DHU' || module === 'AUDIT' ? 'hidden' : ''}`}>
                <button
                  type="button"
                  onClick={() => setQaMode('HOURLY_DHU')}
                  className={`px-3.5 py-2 rounded-xl text-xs font-black transition ${
                    qaMode === 'HOURLY_DHU'
                      ? 'bg-[#002D62] text-white shadow'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  🔍 Hourly DHU &amp; Rectification (1-Hr Slots)
                </button>
                <button
                  type="button"
                  onClick={() => setQaMode('BI_HOURLY_AUDIT')}
                  className={`px-3.5 py-2 rounded-xl text-xs font-black transition ${
                    qaMode === 'BI_HOURLY_AUDIT'
                      ? 'bg-emerald-700 text-white shadow'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  📋 Daily Bi-Hourly Audit (2-Hr OQL • Admin &amp; Auditor)
                </button>
              </div>

              {qaMode === 'BI_HOURLY_AUDIT' && !canWriteAuditOrWeekly && (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-300 text-amber-900 text-xs font-bold">
                  🔒 Daily Bi-Hourly Audit (2-Hr OQL) entry is restricted strictly to <b>Admin</b> and <b>Auditor</b> roles (IE and Quality Manager have view-only access).
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    {qaMode === 'BI_HOURLY_AUDIT'
                      ? '2-Hr Audit Slot (12-Hour AM/PM)'
                      : '1-Hr Inspection Slot (12-Hour AM/PM)'}
                  </label>
                  {qaMode === 'BI_HOURLY_AUDIT' ? (
                    <select
                      value={biHourlySlot}
                      onChange={e => setBiHourlySlot(e.target.value)}
                      className="w-full text-xs font-black border border-emerald-400 rounded-lg p-2 bg-emerald-50 text-emerald-950"
                    >
                      {BI_HOURLY_AUDIT_SLOTS.map(slot => (
                        <option key={slot} value={slot}>
                          {formatHourSlot12h(slot)}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <select
                      value={formHourSlot}
                      onChange={e => setFormHourSlot(e.target.value)}
                      className="w-full text-xs font-black border border-blue-400 rounded-lg p-2 bg-blue-50 text-[#002D62]"
                    >
                      {STANDARD_HOUR_SLOTS.map(slot => (
                        <option key={slot} value={slot}>
                          {formatHourSlot12h(slot)}
                        </option>
                      ))}
                    </select>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Checked Pcs
                  </label>
                  <input
                    type="number"
                    min={1}
                    value={checkedGarments}
                    onChange={e => setCheckedGarments(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Defective Pcs
                  </label>
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
                  <div className="text-[10px] text-blue-700 font-bold">Computed OQL %</div>
                  <div className="text-xl font-black text-[#002D62]">{liveOql}%</div>
                </div>
                <div>
                  <div className="text-[10px] text-blue-700 font-bold">Computed DHU %</div>
                  <div className="text-xl font-black text-amber-700">{liveDhu}%</div>
                </div>
              </div>

              {/* Defect Breakdown Counter List */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-2">
                  Defect Breakdown (Total Defects: {totalDefects})
                </label>
                <div className="space-y-1.5">
                  {[...STANDARD_DEFECT_TYPES, ...customDefects].map(defect => {
                    const count = defectCounts[defect] || 0;
                    return (
                      <div key={defect} className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                        <span className="pr-2 text-sm font-semibold text-slate-800">{defect}</span>
                        <div className="flex items-center gap-2">
                          <button type="button" onClick={() => handleDefectChange(defect, -1)} className="h-8 w-8 rounded-lg bg-slate-200 text-base font-black text-slate-700 hover:bg-slate-300">-</button>
                          <span className="w-8 text-center text-base font-black text-slate-900">{count}</span>
                          <button type="button" onClick={() => handleDefectChange(defect, 1)} className="h-8 w-8 rounded-lg bg-blue-600 text-base font-black text-white hover:bg-blue-700">+</button>
                        </div>
                      </div>
                    );
                  })}
                  <div className="flex items-center gap-2 rounded-lg border border-dashed border-slate-300 bg-white px-3 py-2">
                    <input
                      type="text"
                      value={customDefectInput}
                      onChange={e => setCustomDefectInput(e.target.value)}
                      placeholder="Other defect (custom name)"
                      className="min-w-0 flex-1 rounded-lg border border-slate-300 p-2 text-sm text-slate-900"
                    />
                    <button
                      type="button"
                      onClick={() => {
                        const name = customDefectInput.trim();
                        if (!name) return;
                        const exists = [...STANDARD_DEFECT_TYPES, ...customDefects].some(d => d.toLowerCase() === name.toLowerCase());
                        if (!exists) setCustomDefects(prev => [...prev, name]);
                        setCustomDefectInput('');
                      }}
                      className="rounded-lg bg-slate-800 px-3 py-2 text-xs font-black text-white hover:bg-slate-900"
                    >
                      Add new
                    </button>
                  </div>
                </div>
              </div>

              {/* Rectification of the chosen hour: sent, received back, balance */}
              {qaMode === 'HOURLY_DHU' && (
                <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-3">
                  <div className="text-[11px] font-bold text-slate-500">
                    Rectification for the hour chosen above ({formatHourSlot12h(formHourSlot)}). Choose another hour to enter that hour.
                  </div>
                  {[
                    { title: 'Sewing defects', sent: sewSent, setSent: setSewSent, recv: sewRecv, setRecv: setSewRecv },
                    { title: 'Spot defects', sent: spotSent, setSent: setSpotSent, recv: spotRecv, setRecv: setSpotRecv }
                  ].map(g => {
                    const balance = (Number(g.sent) || 0) - (Number(g.recv) || 0);
                    return (
                      <div key={g.title}>
                        <div className="mb-1 text-xs font-black text-slate-800">{g.title}</div>
                        <div className="grid grid-cols-3 gap-2">
                          <label>
                            <span className="mb-0.5 block text-[11px] font-bold text-slate-600">Sent (pcs)</span>
                            <input type="number" min={0} value={g.sent} onChange={e => g.setSent(e.target.value)} className="w-full rounded border border-slate-300 bg-white p-2 text-sm font-black" />
                          </label>
                          <label>
                            <span className="mb-0.5 block text-[11px] font-bold text-slate-600">Received (pcs)</span>
                            <input type="number" min={0} value={g.recv} onChange={e => g.setRecv(e.target.value)} className="w-full rounded border border-slate-300 bg-white p-2 text-sm font-black" />
                          </label>
                          <div>
                            <span className="mb-0.5 block text-[11px] font-bold text-slate-600">Balance</span>
                            <div className={`w-full rounded border p-2 text-sm font-black ${balance < 0 ? 'border-red-300 bg-red-50 text-red-700' : 'border-slate-200 bg-white text-slate-900'}`}>{balance}</div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {qaError && (
                <div className="p-2.5 bg-red-50 text-red-800 text-xs font-bold rounded-lg border border-red-200">
                  ⚠️ {qaError}
                </div>
              )}

              {qaSuccess && (
                <div className="p-2.5 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  {qaSuccess}
                </div>
              )}

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  disabled={qaMode === 'BI_HOURLY_AUDIT' && !canWriteAuditOrWeekly}
                  className="bg-[#002D62] disabled:bg-slate-400 hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  {qaMode === 'BI_HOURLY_AUDIT'
                    ? 'Save 2-Hr Bi-Hourly Audit (OQL)'
                    : 'Save 1-Hr Quality DHU'}
                </button>
              </div>
            </form>
          )}

          {/* ==================== TAB 3: WEEKLY BS OQL / NFL AQC ==================== */}
          {activeTab === 'WEEKLY_OQL' && (
            <form
              onSubmit={handleWeeklySubmit}
              className="space-y-4 bg-white p-5 rounded-xl border border-slate-200"
            >
              <div>
                <h3 className="text-sm font-black text-[#002D62]">
                  Weekly BS OQL &amp; NFL AQC Entry (Admin &amp; Auditor Only)
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Commitment ≤ 4.5% • Level 1 below 7.0% • Critical Red above 10.0%. Only Admin and Auditor can enter or update weekly figures.
                </p>
              </div>

              {!canWriteAuditOrWeekly && (
                <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-red-800 text-xs font-bold">
                  🔒 Access Denied: Only Admin and Auditor roles can save Weekly BS OQL / NFL AQC data (Quality Manager and IE have view-only access).
                </div>
              )}

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-2">Week</label>
                <div className="grid grid-cols-3 gap-2">
                  <select
                    value={weekYear}
                    onChange={e => {
                      const y = Number(e.target.value);
                      setWeekYear(y);
                      const first = isoWeeksOfYear(y)[0];
                      if (first) setSelectedWeekKey(first.weekKey);
                      setBsOqlInput(''); setNflAqcInput('');
                    }}
                    className="rounded-lg border border-slate-300 bg-white p-2.5 text-sm font-black text-slate-900"
                  >
                    {[2025, 2026, 2027, 2028].map(y => <option key={y} value={y}>{y}</option>)}
                  </select>
                  <select
                    value={selectedWeekKey}
                    onChange={e => {
                      const key = e.target.value;
                      setSelectedWeekKey(key);
                      const existing = weeklyRecords.find(r => r.weekKey === key);
                      setBsOqlInput(existing && existing.bsOqlPct !== null ? String(existing.bsOqlPct) : '');
                      setNflAqcInput(existing && existing.nflAqcPct !== null ? String(existing.nflAqcPct) : '');
                    }}
                    className="col-span-2 rounded-lg border border-slate-300 bg-white p-2.5 text-sm font-black text-slate-900"
                  >
                    {yearWeeks.map(w => (
                      <option key={w.weekKey} value={w.weekKey}>
                        Week {w.weekNo} ({w.weekStartIso.slice(5)} to {w.weekEndIso.slice(5)}){weeklyRecords.some(r => r.weekKey === w.weekKey) ? ' ✓' : ''}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="mt-1 text-[11px] text-slate-500">{yearWeeks.length} weeks in {weekYear}. A tick means a value is already saved.</div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Week Start Date (Monday ISO)
                  </label>
                  <div className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2 text-xs font-bold text-slate-700">
                    {selectedWeekStartIso}
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    BS OQL (%)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min={0}
                    max={100}
                    value={bsOqlInput}
                    onChange={e => setBsOqlInput(e.target.value)}
                    placeholder="e.g. 3.85"
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    NFL AQC (%)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min={0}
                    max={100}
                    value={nflAqcInput}
                    onChange={e => setNflAqcInput(e.target.value)}
                    placeholder="e.g. 4.20"
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
              </div>

              {weeklyMsg && (
                <div className="p-2.5 bg-blue-50 text-blue-900 text-xs font-bold rounded-lg border border-blue-200">
                  {weeklyMsg}
                </div>
              )}

              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={!canWriteAuditOrWeekly}
                  className="bg-[#002D62] disabled:bg-slate-400 hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  Save Weekly BS OQL / NFL AQC
                </button>
              </div>

              {/* Saved Weekly History Table */}
              <div className="border-t pt-3">
                <div className="text-xs font-black text-slate-700 mb-2">
                  Saved Weekly BS OQL &amp; NFL AQC History
                </div>
                <div className="overflow-x-auto max-h-44">
                  <table className="w-full text-xs border-collapse">
                    <thead>
                      <tr className="bg-slate-100 text-slate-700 font-bold text-left">
                        <th className="p-2">Week</th>
                        <th className="p-2">Week Start</th>
                        <th className="p-2 text-right">BS OQL %</th>
                        <th className="p-2 text-right">NFL AQC %</th>
                        <th className="p-2">Updated By</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                      {weeklyRecords.map(r => (
                        <tr key={r.weekKey}>
                          <td className="p-2 font-black text-[#002D62]">{r.weekKey}</td>
                          <td className="p-2">{r.weekStartIso}</td>
                          <td className="p-2 text-right font-bold">
                            {r.bsOqlPct !== null ? `${r.bsOqlPct.toFixed(2)}%` : '—'}
                          </td>
                          <td className="p-2 text-right font-bold">
                            {r.nflAqcPct !== null ? `${r.nflAqcPct.toFixed(2)}%` : '—'}
                          </td>
                          <td className="p-2 text-slate-500">{r.lastUpdatedBy}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </form>
          )}

          {/* ==================== TAB 4: MORNING LINE SETUP ==================== */}
          {activeTab === 'SETUP' && (
            <BulkTargetSetup lines={lines} targets={targets} floorTotals={floorTotals} date={formDate} userName={currentUserName} onSaveSetup={onSaveSetup} onSaveFloorManpower={r => onSaveFloorManpower?.(r)} />
          )}

          {activeTab === 'MONTHLY' && (
            <form
              onSubmit={handleMonthlySubmit}
              className="space-y-4 bg-white p-5 rounded-xl border border-slate-200"
            >
              <div>
                <h3 className="text-sm font-black text-[#002D62]">
                  Monthly Floor Target, BS QA OQL &amp; FRI Pass Setup ({formFloor})
                </h3>
                <p className="text-xs text-slate-500">
                  Configures the TV Wallboard benchmarks for {formFloor}
                </p>
              </div>

              <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Month (YYYY-MM)</label>
                  <input
                    type="month"
                    value={monthYear}
                    onChange={e => setMonthYear(e.target.value)}
                    className="w-full text-xs font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Target Efficiency (%)
                  </label>
                  <input
                    type="number"
                    step="0.5"
                    value={moEffTarget}
                    onChange={e => setMoEffTarget(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    DHU Limit (%)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    value={moDhuLimit}
                    onChange={e => setMoDhuLimit(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    OQL Commitment (%)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    value={moOqlCommitment}
                    onChange={e => setMoOqlCommitment(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    BS QA OQL (%)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    value={moBsQaOql}
                    onChange={e => setMoBsQaOql(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    BS QA FRI Pass Rate (%)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    value={moFriPass}
                    onChange={e => setMoFriPass(e.target.value)}
                    className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                  />
                </div>
              </div>

              {moSuccess && (
                <div className="p-2.5 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  ✓ Monthly Floor Targets &amp; BS QA FRI Pass Rate saved!
                </div>
              )}

              <div className="flex justify-end">
                <button
                  type="submit"
                  className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  Save Monthly Targets
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
