import React, { useState, useMemo, useEffect } from 'react';
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
import { FinishingEntry } from './FinishingEntry';
import type { PhysicalFloor } from '../types';
import { EntryModuleId, ENTRY_MODULES } from '../data/entryModules';
import {
  STANDARD_HOUR_SLOTS,
  STANDARD_DEFECT_TYPES,
  getBangladeshTodayIso,
  formatHourSlot12h,
  hourOptionsForLine,
  lineDisplayName,
  isDepartmentFloor,
  isFinishingUnit,
  AUDIT_PASS_OQL_PCT,
  floorMatches,
  ordinalHr,
  canWriteWeeklyQualityOrBiHourlyAudit,
  isoWeeksOfYear,
  isoWeekKeyOf
} from '../data/masterData';
import { isClientDemoSession } from '../data/clientDemo';

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
  monthlyTargets?: MonthlyTargetConfig[];
  /** One module per kind of person. When set, only that module is shown. */
  module?: EntryModuleId;
  targets?: LineTargetConfig[];
  /** Lines this person may enter for (null = all) */
  allowedLines?: string[] | null;
  /** A floor-bound person can only use their own floor */
  lockedFloor?: PhysicalFloor | null;
  floorTotals?: FloorManpowerRecord[];
  onSaveFloorManpower?: (record: FloorManpowerRecord) => void;
  readOnlyDemo?: boolean;
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
  monthlyTargets = [],
  module,
  targets = [],
  allowedLines = null,
  lockedFloor = null,
  floorTotals = [],
  onSaveFloorManpower,
  readOnlyDemo = false,
  onClose
}) => {
  const [demoNotice, setDemoNotice] = useState<string | null>(null);
  const triggerDemoReadOnlyNotice = (featureLabel: string) => {
    setDemoNotice(`🔒 Client Demo Mode (Read-Only): "${featureLabel}" form previewed! Saving or modifying live Firebase database records is disabled in Client Demo View.`);
    setTimeout(() => setDemoNotice(null), 4500);
  };
  const [activeTab, setActiveTab] = useState<
    'PRODUCTION' | 'QUALITY' | 'WEEKLY_OQL' | 'SETUP' | 'MONTHLY' | 'FINISHING'
  >(
    module === 'TARGET' ? 'SETUP'
      : module === 'FINISHING' ? 'FINISHING'
      : module === 'DHU' || module === 'AUDIT' ? 'QUALITY'
      : module === 'MONTHLY' ? 'MONTHLY'
      : module === 'WEEKLY' ? 'WEEKLY_OQL'
      : 'PRODUCTION'
  );

  // Shared state
  const [formDate, setFormDate] = useState(getBangladeshTodayIso());
  const [formFloor, setFormFloor] = useState<PhysicalFloor>(lockedFloor || 'Sew 2');
  const [formLine, setFormLine] = useState('K1');
  // The hour is chosen by number (1st hr, 2nd hr...). The stored slot of each line is worked out from it.
  const [formHourNo, setFormHourNo] = useState(1);
  // The item / style of a line comes from today's Target Setting. Nothing is chosen from a made-up list.
  const styleOf = (lineId: string): string =>
    targets.find(t => t.lineId === lineId && t.date === formDate && (readOnlyDemo || !t.isDemo))?.styleNumber || '';

  // Production State (Single + Batch All-Lines Matrix)
  const [prodEntryMode, setProdEntryMode] = useState<'SINGLE' | 'BATCH'>('SINGLE');
  const [prodQty, setProdQty] = useState('');
  const [prodIsZero, setProdIsZero] = useState(false);
  const [prodRemarks, setProdRemarks] = useState('');
  const [batchQtys, setBatchQtys] = useState<Record<string, string>>({});
  const [prodSuccess, setProdSuccess] = useState<string | null>(null);

  // Quality State (Hourly DHU vs Hourly OQL Audit)
  const [qaMode, setQaMode] = useState<'HOURLY_DHU' | 'BI_HOURLY_AUDIT'>(module === 'AUDIT' ? 'BI_HOURLY_AUDIT' : 'HOURLY_DHU');
  const [biPairNo, setBiPairNo] = useState(1);
  const [checkedGarments, setCheckedGarments] = useState('');
  const [defectiveGarments, setDefectiveGarments] = useState('');
  const [auditStatus, setAuditStatus] = useState<'PASS' | 'FAIL'>('PASS');
  const [auditManualOverride, setAuditManualOverride] = useState(false);
  const [qcPass, setQcPass] = useState('');
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
  const [moScope, setMoScope] = useState<'FLOOR' | 'LINE'>('FLOOR');
  const [moEffTarget, setMoEffTarget] = useState('75');
  const [moAchieveTarget, setMoAchieveTarget] = useState('95');
  const [moDhuLimit, setMoDhuLimit] = useState('2.0');
  const [moOqlCommitment, setMoOqlCommitment] = useState('4.5');
  const [moBsQaOql, setMoBsQaOql] = useState('2.4');
  const [moFriPass, setMoFriPass] = useState('98.5');
  const [moActualEff, setMoActualEff] = useState('');
  const [moActualAchieve, setMoActualAchieve] = useState('');
  const [moActualOql, setMoActualOql] = useState('');
  const [moActualDhu, setMoActualDhu] = useState('');
  const [moSuccess, setMoSuccess] = useState(false);


  // Auto-populate monthly target inputs when scope, floor, line, or month changes
  useEffect(() => {
    if (activeTab !== 'MONTHLY') return;
    const isLine = moScope === 'LINE';
    const cleanLine = formLine.replace(/^Line\s*/i, '').trim();
    const existing = monthlyTargets.find(m =>
      floorMatches(m.floor, formFloor) &&
      (isLine
        ? (m.lineId === formLine || m.lineId === cleanLine || m.lineId === 'Line ' + cleanLine)
        : (!m.lineId || m.lineId === ''))
    );
    if (existing) {
      setMoEffTarget(String(existing.targetEfficiencyPct || 75));
      setMoAchieveTarget(String(existing.targetAchievePct || 95));
      setMoDhuLimit(String(existing.targetDhuPct || 2.0));
      setMoOqlCommitment(String(existing.targetOqlPct || 4.5));
      setMoBsQaOql(String(existing.bsQaOqlPct || 2.4));
      setMoFriPass(String(existing.friPassRatePct || 98.5));
      setMoActualEff(existing.actualEfficiencyPct !== undefined ? String(existing.actualEfficiencyPct) : '');
      setMoActualAchieve(existing.actualAchievePct !== undefined ? String(existing.actualAchievePct) : '');
      setMoActualOql(existing.actualOqlPct !== undefined ? String(existing.actualOqlPct) : '');
      setMoActualDhu(existing.actualDhuPct !== undefined ? String(existing.actualDhuPct) : '');
    }
  }, [activeTab, moScope, formFloor, formLine, monthYear, monthlyTargets]);

  const canWriteAuditOrWeekly = canWriteWeeklyQualityOrBiHourlyAudit(currentUserRole);
  const floorLines = lines.filter(l => l.floor === formFloor && l.isActive && !isFinishingUnit(l.id));
  const withTarget = floorLines.filter(l => targets.some(t => t.lineId === l.id && t.date === formDate && !t.isDemo && (t.hourlyTargetPcs > 0 || t.dailyTargetPcs > 0)));
  // Lines without a target that day are not running. If no target was set at all yet, every line is shown.
  const allowedSet = Array.isArray(allowedLines) && allowedLines.length > 0 ? new Set(allowedLines) : null;
  const availableLines = (withTarget.length > 0 ? withTarget : floorLines).filter(l => !allowedSet || allowedSet.has(l.id));
  // Departments: Cutting has no quality check; Print and Embroidery record checked and QC pass only.
  const deptFloor = isDepartmentFloor(formFloor);
  const noQcFloor = formFloor === 'Cutting';

  // Hours of the selected line, and the largest hour list on this floor (for the batch form).
  const lineHourOpts = hourOptionsForLine(formLine);
  const hourNoSafe = Math.min(formHourNo, lineHourOpts.length);
  const formHourSlot = lineHourOpts[hourNoSafe - 1]?.key ?? lineHourOpts[0].key;
  const hourLabel = `${ordinalHr(hourNoSafe)} hr`;
  const batchHourCount = Math.max(1, ...availableLines.map(l => hourOptionsForLine(l.id).length));
  // The OQL audit is done every hour, so it uses the same hour list as the line (1st hr, 2nd hr...).
  const biOpts = lineHourOpts;
  const biNoSafe = Math.min(biPairNo, biOpts.length);
  const biHourlySlot = biOpts[biNoSafe - 1]?.key ?? biOpts[0].key;
  const biLabel = biOpts[biNoSafe - 1]?.label ?? biOpts[0].label;

  // Calculated Quality preview
  const numChecked = Number(checkedGarments) || 0;
  const numDefective = Number(defectiveGarments) || 0;
  const totalDefects = Object.values(defectCounts).reduce((s, c) => s + c, 0);
  const liveOql = numChecked > 0 ? ((numDefective / numChecked) * 100).toFixed(2) : '0.00';
  const liveDhu = numChecked > 0 ? ((totalDefects / numChecked) * 100).toFixed(2) : '0.00';
  // Audit result: the auditor's PASS/FAIL choice when made, otherwise the standard rule (PASS when OQL <= limit).
  const effectiveAuditStatus: 'PASS' | 'FAIL' = auditManualOverride
    ? auditStatus
    : numChecked > 0 && (numDefective / numChecked) * 100 > AUDIT_PASS_OQL_PCT ? 'FAIL' : 'PASS';

  const handleDefectChange = (defect: string, delta: number) => {
    const current = defectCounts[defect] || 0;
    const next = Math.max(0, current + delta);
    setDefectCounts(prev => ({ ...prev, [defect]: next }));
  };

  const handleProductionSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (readOnlyDemo) {
      triggerDemoReadOnlyNotice('Hourly Production Entry');
      return;
    }
    if (prodEntryMode === 'BATCH') {
      let savedCount = 0;
      let skipped = 0;
      availableLines.forEach(line => {
        const raw = batchQtys[line.id];
        if (raw !== undefined && raw.trim() !== '') {
          const opt = hourOptionsForLine(line.id)[formHourNo - 1];
          if (!opt) { skipped++; return; } // this line does not work that many hours
          const q = Math.max(0, Number(raw) || 0);
          onSaveProduction({
            lineId: line.id,
            floor: formFloor,
            date: formDate,
            hourSlot: opt.key,
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
        `✓ Batch saved ${savedCount} line(s) for the ${ordinalHr(formHourNo)} hr!${skipped ? ` (${skipped} line(s) skipped: they do not work that many hours)` : ''}`
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
      `✓ Saved Line ${formLine} output (${qty} pcs) for the ${hourLabel}!`
    );
    setTimeout(() => setProdSuccess(null), 2500);
  };

  const handleQualitySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setQaError(null);
    if (readOnlyDemo) {
      triggerDemoReadOnlyNotice(qaMode === 'BI_HOURLY_AUDIT' ? 'Hourly OQL Audit' : 'Hourly Quality DHU');
      return;
    }

    if (qaMode === 'BI_HOURLY_AUDIT' && !canWriteAuditOrWeekly) {
      setQaError(
        'Access Denied: Only Admin, Auditor, and IE roles can submit Hourly OQL Audit data.'
      );
      return;
    }

    if (noQcFloor) {
      setQaError('Cutting has no quality check. Choose Print or Embroidery, or a sewing floor.');
      return;
    }
    if (numChecked <= 0) {
      setQaError('Checked garments must be greater than 0.');
      return;
    }
    if (deptFloor) {
      const pass = Number(qcPass);
      if (qcPass.trim() === '' || !(pass >= 0) || pass > numChecked) {
        setQaError('QC Pass must be between 0 and the checked pieces.');
        return;
      }
      const notPassed = numChecked - pass;
      onSaveQuality({
        lineId: formLine,
        floor: formFloor,
        date: formDate,
        hourSlot: formHourSlot,
        styleNumber: styleOf(formLine),
        checkedGarments: numChecked,
        defectiveGarments: notPassed,
        defects: [],
        totalDefects: notPassed,
        defectSent: 0,
        defectReceived: 0,
        spotSent: 0,
        spotReceived: 0,
        isConfirmedZero: notPassed === 0,
        remarks: qaRemarks,
        reportedBy: currentUserName,
        isDemo: false
      });
      setQaSuccess(`✓ Saved ${lineDisplayName(formLine)}: ${pass} passed of ${numChecked} checked (${hourLabel}).`);
      setTimeout(() => setQaSuccess(null), 2500);
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
      entryType: qaMode === 'BI_HOURLY_AUDIT' ? 'AUDIT' : 'DHU',
      ...(qaMode === 'BI_HOURLY_AUDIT' && numChecked > 0 ? { auditResult: effectiveAuditStatus } : {}),
      isDemo: false
    });
    if (qaMode === 'BI_HOURLY_AUDIT') {
      setAuditManualOverride(false);
      setAuditStatus('PASS');
    }

    setQaSuccess(
      `✓ Saved ${
        qaMode === 'BI_HOURLY_AUDIT' ? 'Hourly OQL Audit' : '1-Hr Quality DHU'
      } for Line ${formLine} (${qaMode === 'BI_HOURLY_AUDIT' ? biLabel : hourLabel})!`
    );
    setTimeout(() => setQaSuccess(null), 2500);
  };

  const aqcBrand = readOnlyDemo || isClientDemoSession() ? 'DEMO' : 'NFL';

  const handleWeeklySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (readOnlyDemo) {
      triggerDemoReadOnlyNotice(`Weekly BS OQL / ${aqcBrand} AQC`);
      return;
    }
    if (!canWriteAuditOrWeekly) {
      setWeeklyMsg(
        `⚠️ Access Denied: Only Admin, Auditor, and IE roles can save Weekly BS OQL / ${aqcBrand} AQC.`
      );
      return;
    }
    const bs = bsOqlInput.trim() !== '' ? Number(bsOqlInput) : null;
    const aqc = nflAqcInput.trim() !== '' ? Number(nflAqcInput) : null;
    if (!selectedWeekKey || (bs === null && aqc === null)) {
      setWeeklyMsg(`⚠️ Please enter BS OQL (%) or ${aqcBrand} AQC (%) for the selected week.`);
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
    setWeeklyMsg(`✓ Saved Weekly BS OQL / ${aqcBrand} AQC for ${selectedWeekKey}!`);
    setTimeout(() => setWeeklyMsg(null), 3000);
  };

  const handleSetupSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (readOnlyDemo) {
      triggerDemoReadOnlyNotice('Target Setting');
      return;
    }
    const daily = Number(setupDailyTarget);
    const hrs = Math.max(1, Number(setupShiftHours) || 8);
    onSaveSetup({
      lineId: formLine,
      floor: formFloor,
      date: formDate,
      dailyTargetPcs: daily,
      hourlyTargetPcs: Math.round(daily / hrs),
      targetEfficiencyPct: Number(setupTargetEff) || 75,
      buyer: `Nike / ${aqcBrand}`,
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
    if (readOnlyDemo) {
      triggerDemoReadOnlyNotice(moScope === 'LINE' ? `Monthly Target (Line ${formLine})` : 'Monthly Floor Targets');
      return;
    }
    const targetLineId = moScope === 'LINE' ? formLine : '';
    const cleanLine = targetLineId.replace(/^Line\s*/i, '').trim();
    const docId = targetLineId ? `${formFloor}__${cleanLine}__${monthYear}` : `${formFloor}__${monthYear}`;
    onSaveMonthlyTarget?.({
      id: docId,
      floor: formFloor,
      lineId: targetLineId,
      monthYear,
      targetEfficiencyPct: Number(moEffTarget) || 75,
      targetAchievePct: Number(moAchieveTarget) || 95,
      targetDhuPct: Number(moDhuLimit) || 2.0,
      targetOqlPct: Number(moOqlCommitment) || 4.5,
      bsQaOqlPct: Number(moBsQaOql) || 2.4,
      friPassRatePct: Number(moFriPass) || 98.5,
      actualEfficiencyPct: moActualEff.trim() !== '' ? Number(moActualEff) : undefined,
      actualAchievePct: moActualAchieve.trim() !== '' ? Number(moActualAchieve) : undefined,
      actualOqlPct: moActualOql.trim() !== '' ? Number(moActualOql) : undefined,
      actualDhuPct: moActualDhu.trim() !== '' ? Number(moActualDhu) : undefined
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
              {module ? ENTRY_MODULES.find(m => m.id === module)?.label.replace(/NFL/g, aqcBrand) : 'Factory Floor Data Entry (12-Hour AM/PM Format)'}
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

        {readOnlyDemo && (
          <div className="bg-amber-50 border-b border-amber-300 px-5 py-2.5 flex flex-wrap items-center justify-between gap-2 text-xs text-amber-950">
            <div className="font-bold">
              👁️ <span className="font-black">CLIENT DEMO VIEW (READ-ONLY ENTRY PREVIEW):</span> You can explore all 7 entry forms, switch floors/lines, and test live calculators — saving to the database is disabled.
            </div>
            <span className="rounded-full bg-amber-300/80 px-2.5 py-0.5 text-[10px] font-black uppercase text-amber-950">
              Read-Only Mode
            </span>
          </div>
        )}

        {demoNotice && (
          <div className="bg-blue-900 text-amber-200 px-5 py-2.5 text-xs font-black border-b border-blue-700 flex items-center justify-between">
            <span>{demoNotice}</span>
            <button type="button" onClick={() => setDemoNotice(null)} className="text-white ml-2">✕</button>
          </div>
        )}

        {/* Module Tab Switcher (shown when no single module was forced, or in Client Demo so buyers can switch freely) */}
        {(!module || readOnlyDemo) && (
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
            onClick={() => setActiveTab('SETUP')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'SETUP'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            ⚙️ Target Setting
          </button>
          <button
            onClick={() => setActiveTab('FINISHING')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'FINISHING'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            📦 Iron &amp; Packing
          </button>
          <button
            onClick={() => setActiveTab('QUALITY')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'QUALITY'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            🔍 Quality &amp; Hourly OQL Audit
          </button>
          <button
            onClick={() => setActiveTab('WEEKLY_OQL')}
            className={`pb-2.5 px-3 border-b-2 transition ${
              activeTab === 'WEEKLY_OQL'
                ? 'border-[#002D62] text-[#002D62] font-black'
                : 'border-transparent text-slate-500'
            }`}
          >
            📈 Weekly BS OQL / {aqcBrand} AQC
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
          {activeTab !== 'WEEKLY_OQL' && activeTab !== 'SETUP' && activeTab !== 'FINISHING' && (
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
                  disabled={!!lockedFloor}
                  onChange={e => {
                    const newFloor = e.target.value as PhysicalFloor;
                    setFormFloor(newFloor);
                    const firstLine = lines.find(l => l.floor === newFloor)?.id || 'K1';
                    setFormLine(firstLine);
                  }}
                  className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                >
                  <option value="Sew 1">Sew 1</option>
                  <option value="Sew 2">Sew 2</option>
                  <option value="Sew 3">Sew 3</option>
                  <option value="Cutting">Cutting</option>
                  <option value="Print">Print</option>
                  <option value="Embroidery">Embroidery</option>
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
                      {deptFloor ? lineDisplayName(l.id) : `Line ${l.id}`}
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
                    Hour:
                  </label>
                  <select
                    value={prodEntryMode === 'BATCH' ? Math.min(formHourNo, batchHourCount) : hourNoSafe}
                    onChange={e => setFormHourNo(Number(e.target.value))}
                    className="text-xs font-black border border-blue-400 rounded-lg p-2 bg-blue-50 text-[#002D62]"
                  >
                    {Array.from({ length: prodEntryMode === 'BATCH' ? batchHourCount : lineHourOpts.length }, (_, i) => i + 1).map(n => (
                      <option key={n} value={n}>
                        {ordinalHr(n)} hr
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {prodEntryMode === 'SINGLE' ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">
                      Output Quantity (Pcs) for {deptFloor ? lineDisplayName(formLine) : `Line ${formLine}`}
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
                    <b>{hourLabel}</b>:
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-2.5 max-h-60 overflow-y-auto p-1">
                    {availableLines.map(l => (
                      <div
                        key={l.id}
                        className="p-2 rounded-lg border border-slate-200 bg-slate-50 flex flex-col gap-1"
                      >
                        <span className="text-xs font-black text-[#002D62]">{deptFloor ? lineDisplayName(l.id) : `Line ${l.id}`}</span>
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
              {/* Mode Sub-Tabs (hidden when Hourly DHU or Hourly OQL was chosen from the menu) */}
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
                  📋 Hourly OQL Audit (Admin &amp; Auditor)
                </button>
              </div>

              {qaMode === 'BI_HOURLY_AUDIT' && !canWriteAuditOrWeekly && (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-300 text-amber-900 text-xs font-bold">
                  🔒 Hourly OQL Audit entry is restricted strictly to <b>Admin</b> and <b>Auditor</b> roles (IE and Quality Manager have view-only access).
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    {qaMode === 'BI_HOURLY_AUDIT'
                      ? 'Audit Hour'
                      : 'Inspection Hour'}
                  </label>
                  {qaMode === 'BI_HOURLY_AUDIT' ? (
                    <select
                      value={biNoSafe}
                      onChange={e => setBiPairNo(Number(e.target.value))}
                      className="w-full text-xs font-black border border-emerald-400 rounded-lg p-2 bg-emerald-50 text-emerald-950"
                    >
                      {biOpts.map(o => (
                        <option key={o.no} value={o.no}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <select
                      value={hourNoSafe}
                      onChange={e => setFormHourNo(Number(e.target.value))}
                      className="w-full text-xs font-black border border-blue-400 rounded-lg p-2 bg-blue-50 text-[#002D62]"
                    >
                      {lineHourOpts.map(o => (
                        <option key={o.no} value={o.no}>
                          {o.label}
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
                    {deptFloor ? 'QC Pass Pcs' : 'Defective Pcs'}
                  </label>
                  {deptFloor ? (
                    <input
                      type="number"
                      min={0}
                      value={qcPass}
                      onChange={e => setQcPass(e.target.value)}
                      className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                    />
                  ) : (
                    <input
                      type="number"
                      min={0}
                      value={defectiveGarments}
                      onChange={e => setDefectiveGarments(e.target.value)}
                      className="w-full text-sm font-black border border-slate-300 rounded-lg p-2"
                    />
                  )}
                </div>
              </div>

                            {/* Hourly OQL Audit Result Selector & Pass/Fail Option */}
              {qaMode === 'BI_HOURLY_AUDIT' && (
                <div className="bg-slate-50 border-2 border-slate-200 rounded-xl p-3.5 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-black text-slate-800 uppercase tracking-wide">
                      Audit Result Option (Pass / Fail)
                    </span>
                    <span
                      className={`text-xs font-black px-2.5 py-0.5 rounded-full ${
                        effectiveAuditStatus === 'PASS'
                          ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                          : 'bg-red-100 text-red-800 border border-red-300'
                      }`}
                    >
                      Status: {effectiveAuditStatus}
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setAuditStatus('PASS');
                        setAuditManualOverride(true);
                        if (numChecked > 0 && (numDefective / numChecked) * 100 > AUDIT_PASS_OQL_PCT) {
                          setDefectiveGarments('0');
                        }
                      }}
                      className={`flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-black border-2 transition-all shadow-sm ${
                        effectiveAuditStatus === 'PASS'
                          ? 'bg-emerald-600 text-white border-emerald-700 shadow-emerald-200 ring-2 ring-emerald-400'
                          : 'bg-white text-emerald-700 border-emerald-200 hover:bg-emerald-50'
                      }`}
                    >
                      <span className="text-lg leading-none font-black">✓</span>
                      <span>AUDIT PASS</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setAuditStatus('FAIL');
                        setAuditManualOverride(true);
                        if (numDefective === 0) {
                          setDefectiveGarments('1');
                        }
                      }}
                      className={`flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-sm font-black border-2 transition-all shadow-sm ${
                        effectiveAuditStatus === 'FAIL'
                          ? 'bg-red-600 text-white border-red-700 shadow-red-200 ring-2 ring-red-400'
                          : 'bg-white text-red-700 border-red-200 hover:bg-red-50'
                      }`}
                    >
                      <span className="text-lg leading-none font-black">✕</span>
                      <span>AUDIT FAIL</span>
                    </button>
                  </div>

                  <div className="text-[11px] font-bold text-center text-slate-600">
                    Standard Rule: PASS when OQL is ≤ {AUDIT_PASS_OQL_PCT}% · Select PASS or FAIL above to record decision.
                  </div>
                </div>
              )}

              {/* Live Metric Banner (sewing) */}
              {!deptFloor && (
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

              )}

              {/* Print / Embroidery: what passed */}
              {deptFloor && !noQcFloor && (
                <div className="grid grid-cols-2 gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-center">
                  <div>
                    <div className="text-[10px] font-bold text-emerald-700">QC Pass rate</div>
                    <div className="text-xl font-black text-emerald-800">{numChecked > 0 && qcPass !== '' ? `${((Math.min(Number(qcPass) || 0, numChecked) / numChecked) * 100).toFixed(1)}%` : '—'}</div>
                  </div>
                  <div>
                    <div className="text-[10px] font-bold text-red-700">Not passed</div>
                    <div className="text-xl font-black text-red-700">{numChecked > 0 && qcPass !== '' ? Math.max(0, numChecked - (Number(qcPass) || 0)) : '—'}</div>
                  </div>
                </div>
              )}

              {!deptFloor && (
                <>
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
                    Rectification for the hour chosen above ({hourLabel}). Choose another hour to enter that hour.
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

                </>
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
                  disabled={(qaMode === 'BI_HOURLY_AUDIT' && !canWriteAuditOrWeekly) || noQcFloor}
                  className="bg-[#002D62] disabled:bg-slate-400 hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  {deptFloor
                    ? 'Save Checked & QC Pass'
                    : qaMode === 'BI_HOURLY_AUDIT'
                    ? 'Save Hourly OQL Audit'
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
                  Weekly BS OQL &amp; {aqcBrand} AQC Entry (Admin &amp; Auditor Only)
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Commitment ≤ 4.5% • Level 1 below 7.0% • Critical Red above 10.0%. Only Admin and Auditor can enter or update weekly figures.
                </p>
              </div>

              {!canWriteAuditOrWeekly && (
                <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-red-800 text-xs font-bold">
                  🔒 Access Denied: Only Admin and Auditor roles can save Weekly BS OQL / {aqcBrand} AQC data (Quality Manager and IE have view-only access).
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
                    {aqcBrand} AQC (%)
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
                  Save Weekly BS OQL / {aqcBrand} AQC
                </button>
              </div>

              {/* Saved Weekly History Table */}
              <div className="border-t pt-3">
                <div className="text-xs font-black text-slate-700 mb-2">
                  Saved Weekly BS OQL &amp; {aqcBrand} AQC History
                </div>
                <div className="overflow-x-auto max-h-44">
                  <table className="w-full text-xs border-collapse">
                    <thead>
                      <tr className="bg-slate-100 text-slate-700 font-bold text-left">
                        <th className="p-2">Week</th>
                        <th className="p-2">Week Start</th>
                        <th className="p-2 text-right">BS OQL %</th>
                        <th className="p-2 text-right">{aqcBrand} AQC %</th>
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
          {activeTab === 'FINISHING' && (
            <FinishingEntry
              onlyFloor={lockedFloor}
              date={formDate}
              userName={currentUserName}
              targets={targets}
              onSaveProduction={entry => {
                if (readOnlyDemo) {
                  triggerDemoReadOnlyNotice('Iron & Packing Output');
                  return;
                }
                onSaveProduction(entry);
              }}
            />
          )}

          {activeTab === 'SETUP' && (
            <BulkTargetSetup
              onlyFloor={lockedFloor}
              lines={lines}
              targets={targets}
              floorTotals={floorTotals}
              date={formDate}
              userName={currentUserName}
              onSaveSetup={setup => {
                if (readOnlyDemo) {
                  triggerDemoReadOnlyNotice('Target Setting');
                  return;
                }
                onSaveSetup(setup);
              }}
              onSaveFloorManpower={r => {
                if (readOnlyDemo) {
                  triggerDemoReadOnlyNotice('Floor Manpower Setup');
                  return;
                }
                onSaveFloorManpower?.(r);
              }}
            />
          )}

          {activeTab === 'MONTHLY' && (
            <div className="space-y-4">
              <form
                onSubmit={handleMonthlySubmit}
                className="space-y-4 bg-white p-5 rounded-xl border border-slate-200 shadow-sm"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
                  <div>
                    <h3 className="text-sm font-black text-[#002D62] flex items-center gap-2">
                      <span>Monthly Data &amp; Target Setup</span>
                      <span className="text-xs px-2 py-0.5 rounded bg-blue-100 text-blue-800 font-bold">
                        {moScope === 'FLOOR' ? `Floor: ${formFloor}` : `Line: ${lineDisplayName(formLine)}`}
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500">
                      Configure Efficiency, Achieve %, OQL, and DHU targets for the floor or specific line
                    </p>
                  </div>

                  {/* Scope Selector: Entire Floor vs Line-Wise */}
                  <div className="inline-flex rounded-lg border border-slate-300 p-0.5 bg-slate-100 shrink-0">
                    <button
                      type="button"
                      onClick={() => setMoScope('FLOOR')}
                      className={`px-3 py-1.5 rounded-md text-xs font-black transition ${
                        moScope === 'FLOOR' ? 'bg-[#002D62] text-white shadow' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      🏢 Entire Floor ({formFloor})
                    </button>
                    <button
                      type="button"
                      onClick={() => setMoScope('LINE')}
                      className={`px-3 py-1.5 rounded-md text-xs font-black transition ${
                        moScope === 'LINE' ? 'bg-[#002D62] text-white shadow' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      🧵 Line-Wise ({lineDisplayName(formLine)})
                    </button>
                  </div>
                </div>

                {moScope === 'LINE' && (
                  <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg flex items-center justify-between">
                    <div className="text-xs font-bold text-blue-900">
                      Currently editing Monthly Target for: <span className="font-black text-[#002D62] underline">{lineDisplayName(formLine)}</span> ({formFloor})
                    </div>
                    <div className="text-[11px] text-blue-700 font-semibold">
                      Use Line dropdown at the top of modal to switch lines
                    </div>
                  </div>
                )}

                {/* Main 4 Monthly Target & Benchmark Inputs */}
                <div>
                  <div className="text-xs font-black uppercase tracking-wider text-slate-700 mb-2">
                    1. Primary Targets &amp; Benchmarks (Efficiency, Achieve %, OQL, DHU)
                  </div>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1">
                        Month (YYYY-MM)
                      </label>
                      <input
                        type="month"
                        value={monthYear}
                        onChange={e => setMonthYear(e.target.value)}
                        className="w-full text-xs font-black border border-slate-300 rounded-lg p-2"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1 text-emerald-800">
                        Target Efficiency (%)
                      </label>
                      <input
                        type="number"
                        step="0.5"
                        value={moEffTarget}
                        onChange={e => setMoEffTarget(e.target.value)}
                        className="w-full text-sm font-black border border-emerald-300 bg-emerald-50/40 rounded-lg p-2 text-emerald-900"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1 text-blue-800">
                        Target Achieve % (%)
                      </label>
                      <input
                        type="number"
                        step="0.5"
                        value={moAchieveTarget}
                        onChange={e => setMoAchieveTarget(e.target.value)}
                        className="w-full text-sm font-black border border-blue-300 bg-blue-50/40 rounded-lg p-2 text-blue-900"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1 text-amber-800">
                        DHU Limit (%)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={moDhuLimit}
                        onChange={e => setMoDhuLimit(e.target.value)}
                        className="w-full text-sm font-black border border-amber-300 bg-amber-50/40 rounded-lg p-2 text-amber-900"
                      />
                    </div>
                  </div>
                </div>

                {/* Quality Commitments & Actuals */}
                <div className="pt-2">
                  <div className="text-xs font-black uppercase tracking-wider text-slate-700 mb-2">
                    2. Quality Commitments &amp; Audit Benchmarks
                  </div>
                  <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
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
                </div>

                {/* Optional MTD Actual Overrides */}
                <div className="pt-2">
                  <div className="text-xs font-black uppercase tracking-wider text-slate-700 mb-2">
                    3. Optional Month-To-Date (MTD) Actual Overrides
                  </div>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200">
                    <div>
                      <label className="block text-[11px] font-bold text-slate-600 mb-1">
                        MTD Actual Eff (%)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        placeholder="Auto / Calculated"
                        value={moActualEff}
                        onChange={e => setMoActualEff(e.target.value)}
                        className="w-full text-xs font-bold border border-slate-300 rounded-lg p-1.5"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-bold text-slate-600 mb-1">
                        MTD Actual Achieve %
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        placeholder="Auto / Calculated"
                        value={moActualAchieve}
                        onChange={e => setMoActualAchieve(e.target.value)}
                        className="w-full text-xs font-bold border border-slate-300 rounded-lg p-1.5"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-bold text-slate-600 mb-1">
                        MTD Actual OQL (%)
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        placeholder="Auto / Calculated"
                        value={moActualOql}
                        onChange={e => setMoActualOql(e.target.value)}
                        className="w-full text-xs font-bold border border-slate-300 rounded-lg p-1.5"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] font-bold text-slate-600 mb-1">
                        MTD Actual DHU (%)
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        placeholder="Auto / Calculated"
                        value={moActualDhu}
                        onChange={e => setMoActualDhu(e.target.value)}
                        className="w-full text-xs font-bold border border-slate-300 rounded-lg p-1.5"
                      />
                    </div>
                  </div>
                </div>

                {moSuccess && (
                  <div className="p-3 bg-emerald-50 text-emerald-800 text-xs font-bold rounded-xl border border-emerald-200 flex items-center gap-2">
                    <span className="text-base font-black">✓</span>
                    <span>
                      Saved Monthly Data for {moScope === 'LINE' ? `Line ${formLine}` : `Entire Floor ${formFloor}`}!
                    </span>
                  </div>
                )}

                <div className="flex items-center justify-between pt-2">
                  <div className="text-xs text-slate-500 font-semibold">
                    {moScope === 'LINE'
                      ? `Saving specifically for Line ${formLine}`
                      : `Saving floor-level benchmark for ${formFloor}`}
                  </div>
                  <button
                    type="submit"
                    className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                  >
                    Save {moScope === 'LINE' ? `Line ${formLine} Monthly Target` : 'Floor Monthly Target'}
                  </button>
                </div>
              </form>

              {/* Line-Wise Monthly Setup & Quick Edit Table */}
              <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-xs font-black text-slate-800 uppercase tracking-wide">
                      Line-Wise Monthly Target Summary ({formFloor})
                    </h4>
                    <p className="text-[11px] text-slate-500">
                      Quick overview of all lines on {formFloor}. Click "Edit Line" to load into the form above.
                    </p>
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-black text-slate-600 uppercase">
                        <th className="p-2">Line</th>
                        <th className="p-2 text-right">Target Eff %</th>
                        <th className="p-2 text-right">Target Achieve %</th>
                        <th className="p-2 text-right">DHU Limit %</th>
                        <th className="p-2 text-right">OQL Commit %</th>
                        <th className="p-2 text-center">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {floorLines.map(line => {
                        const clean = line.id.replace(/^Line\s*/i, '').trim();
                        const lineTarget = monthlyTargets.find(m =>
                          floorMatches(m.floor, formFloor) &&
                          (m.lineId === line.id || m.lineId === clean || m.lineId === 'Line ' + clean)
                        );
                        const isCurrent = moScope === 'LINE' && (formLine === line.id || formLine === clean);

                        return (
                          <tr
                            key={line.id}
                            className={`hover:bg-blue-50/50 transition ${isCurrent ? 'bg-blue-50 font-black' : ''}`}
                          >
                            <td className="p-2 font-black text-[#002D62]">
                              {lineDisplayName(line.id)}
                            </td>
                            <td className="p-2 text-right font-bold text-emerald-800">
                              {lineTarget?.targetEfficiencyPct ?? moEffTarget}%
                            </td>
                            <td className="p-2 text-right font-bold text-blue-800">
                              {lineTarget?.targetAchievePct ?? moAchieveTarget}%
                            </td>
                            <td className="p-2 text-right font-bold text-amber-800">
                              {lineTarget?.targetDhuPct ?? moDhuLimit}%
                            </td>
                            <td className="p-2 text-right font-bold text-slate-700">
                              {lineTarget?.targetOqlPct ?? moOqlCommitment}%
                            </td>
                            <td className="p-2 text-center">
                              <button
                                type="button"
                                onClick={() => {
                                  setFormLine(clean);
                                  setMoScope('LINE');
                                }}
                                className="px-2.5 py-1 text-[11px] font-black bg-blue-600 hover:bg-blue-700 text-white rounded-md transition shadow-xs"
                              >
                                {isCurrent ? 'Editing' : 'Edit Line'}
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
