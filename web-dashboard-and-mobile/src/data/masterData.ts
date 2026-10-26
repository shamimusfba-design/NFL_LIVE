import {
  LineMasterItem,
  UserAccount,
  LineTargetConfig,
  IncidentRecord,
  ProductionEntryRecord,
  QualityEntryRecord,
  WeeklyQualityRecord,
  MonthlyTargetConfig,
  UserRole
} from '../types';

export const getBangladeshTodayIso = (): string => {
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Dhaka',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).formatToParts(new Date());
    const y = parts.find(p => p.type === 'year')?.value;
    const m = parts.find(p => p.type === 'month')?.value;
    const d = parts.find(p => p.type === 'day')?.value;
    if (y && m && d) return `${y}-${m}-${d}`;
  } catch {
    // Fallback
  }
  return new Date().toISOString().slice(0, 10);
};

export const ALL_FACTORY_LINES: LineMasterItem[] = [
  // Sew 1 (22 lines + Iron 1 / Packing 1)
  { id: 'A1', floor: 'Sew 1', isActive: true }, { id: 'A2', floor: 'Sew 1', isActive: true },
  { id: 'B', floor: 'Sew 1', isActive: true }, { id: 'C', floor: 'Sew 1', isActive: true },
  { id: 'D1A', floor: 'Sew 1', isActive: true }, { id: 'D2A', floor: 'Sew 1', isActive: true },
  { id: 'D1B', floor: 'Sew 1', isActive: true }, { id: 'D2B', floor: 'Sew 1', isActive: true },
  { id: 'E1A', floor: 'Sew 1', isActive: true }, { id: 'E2A', floor: 'Sew 1', isActive: true },
  { id: 'E1B', floor: 'Sew 1', isActive: true }, { id: 'E2B', floor: 'Sew 1', isActive: true },
  { id: 'FA', floor: 'Sew 1', isActive: true }, { id: 'FB', floor: 'Sew 1', isActive: true },
  { id: 'G1A', floor: 'Sew 1', isActive: true }, { id: 'G2A', floor: 'Sew 1', isActive: true },
  { id: 'G1B', floor: 'Sew 1', isActive: true }, { id: 'G2B', floor: 'Sew 1', isActive: true },
  { id: 'H1A', floor: 'Sew 1', isActive: true }, { id: 'H2A', floor: 'Sew 1', isActive: true },
  { id: 'H1B', floor: 'Sew 1', isActive: true }, { id: 'H2B', floor: 'Sew 1', isActive: true },

  // Sew 2 (17 lines)
  { id: 'J1', floor: 'Sew 2', aliases: ['J'], isActive: true }, { id: 'J2', floor: 'Sew 2', aliases: ['J'], isActive: true },
  { id: 'K1', floor: 'Sew 2', isActive: true }, { id: 'K2', floor: 'Sew 2', isActive: true },
  { id: 'L1', floor: 'Sew 2', isActive: true }, { id: 'L2', floor: 'Sew 2', isActive: true },
  { id: 'M1', floor: 'Sew 2', aliases: ['M'], isActive: true }, { id: 'M2', floor: 'Sew 2', aliases: ['M'], isActive: true },
  { id: 'N1', floor: 'Sew 2', isActive: true }, { id: 'N2', floor: 'Sew 2', isActive: true },
  { id: 'O1', floor: 'Sew 2', isActive: true }, { id: 'O2', floor: 'Sew 2', isActive: true },
  { id: 'P1', floor: 'Sew 2', aliases: ['P'], isActive: true }, { id: 'P2', floor: 'Sew 2', aliases: ['P'], isActive: true },
  { id: 'Q1', floor: 'Sew 2', isActive: true }, { id: 'Q2', floor: 'Sew 2', isActive: true },
  { id: 'R1', floor: 'Sew 2', aliases: ['R'], isActive: true },

  // Sew 3 (23 lines)
  { id: 'S1', floor: 'Sew 3', isActive: true }, { id: 'S2', floor: 'Sew 3', isActive: true },
  { id: 'T1', floor: 'Sew 3', isActive: true }, { id: 'T2', floor: 'Sew 3', isActive: true },
  { id: 'U1', floor: 'Sew 3', isActive: true }, { id: 'U2', floor: 'Sew 3', isActive: true },
  { id: 'UA', floor: 'Sew 3', isActive: true }, { id: 'UB', floor: 'Sew 3', isActive: true },
  { id: 'V1A', floor: 'Sew 3', isActive: true }, { id: 'V1B', floor: 'Sew 3', isActive: true },
  { id: 'W1A', floor: 'Sew 3', isActive: true }, { id: 'W1B', floor: 'Sew 3', isActive: true },
  { id: 'W2A', floor: 'Sew 3', isActive: true }, { id: 'W2B', floor: 'Sew 3', isActive: true },
  { id: 'X1', floor: 'Sew 3', isActive: true }, { id: 'Y1', floor: 'Sew 3', isActive: true },
  { id: 'Y2', floor: 'Sew 3', isActive: true }, { id: 'Z1', floor: 'Sew 3', isActive: true },
  { id: 'Z2', floor: 'Sew 3', isActive: true }, { id: 'ZA1A', floor: 'Sew 3', isActive: true },
  { id: 'ZA1B', floor: 'Sew 3', isActive: true }, { id: 'ZA2A', floor: 'Sew 3', isActive: true },
  { id: 'ZA2B', floor: 'Sew 3', isActive: true }
];

export const GARMENT_STYLES = [
  'SS T shirt', 'LS T shirt', 'Hoodie', 'Jacket', 'Hoody Jacket',
  'Leggings', 'Pant', 'Body Suit', 'Jumpsuit', 'Custom'
];

export const STANDARD_STYLES = [
  { styleNumber: 'SS T shirt', name: 'Short Sleeve Tee', smv: 6.5 },
  { styleNumber: 'LS T shirt', name: 'Long Sleeve Tee', smv: 8.2 },
  { styleNumber: 'Hoodie', name: 'Fleece Hoodie', smv: 16.5 },
  { styleNumber: 'Jacket', name: 'Track Jacket', smv: 18.0 },
  { styleNumber: 'Hoody Jacket', name: 'Zip Hoody Jacket', smv: 19.5 },
  { styleNumber: 'Leggings', name: 'Stretch Leggings', smv: 7.4 },
  { styleNumber: 'Pant', name: 'Jogger Pant', smv: 13.8 },
  { styleNumber: 'Body Suit', name: 'Infant Body Suit', smv: 9.0 },
  { styleNumber: 'Jumpsuit', name: 'Knit Jumpsuit', smv: 15.2 }
];

export const STANDARD_HOUR_SLOTS = [
  '08:00 - 09:00',
  '09:00 - 10:00',
  '10:00 - 11:00',
  '11:00 - 12:00',
  '12:00 - 13:00',
  '14:00 - 15:00',
  '15:00 - 16:00',
  '16:00 - 17:00',
  '17:00 - 18:00',
  '18:00 - 19:00'
];

export const BI_HOURLY_AUDIT_SLOTS = [
  '08:00 - 10:00',
  '10:00 - 12:00',
  '13:00 - 15:00',
  '15:00 - 17:00',
  '17:00 - 19:00',
  '19:00 - 21:00'
];

/**
 * 12-Hour AM/PM Time Formatting Helpers (matching Android NflRepository.kt)
 */
function formatHour12h(hour24: number): string {
  const h = ((hour24 % 24) + 24) % 24;
  const period = h >= 12 ? 'PM' : 'AM';
  const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  return `${h12}:00 ${period}`;
}

export function formatHourShort12h(hour24: number): string {
  const h = ((hour24 % 24) + 24) % 24;
  const period = h >= 12 ? 'PM' : 'AM';
  const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  return `${h12} ${period}`;
}

export function parseSlotHours24(rawSlot: string): [number, number] | null {
  if (!rawSlot) return null;
  const parts = String(rawSlot).split(/[-–]/);
  if (parts.length < 2) return null;
  const parsePart = (token: string): number | null => {
    const t = token.trim().toUpperCase();
    const m = t.match(/\d+/);
    if (!m) return null;
    let h = parseInt(m[0], 10);
    if (t.includes('PM') && h >= 1 && h <= 11) h += 12;
    if (t.includes('AM') && h === 12) h = 0;
    return h;
  };
  const s = parsePart(parts[0]);
  const e = parsePart(parts[1]);
  if (s === null || e === null) return null;
  return [s, e];
}

export function formatHourSlot12h(rawSlot: string): string {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return rawSlot || '';
  return `${formatHour12h(parsed[0])} - ${formatHour12h(parsed[1])}`;
}

export function formatSlotShortLabel12h(rawSlot: string): string {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return rawSlot || '';
  return formatHourShort12h(parsed[0]);
}

export function normalizeHourSlot24h(rawSlot: string): string {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return (rawSlot || '').trim();
  const s = String(parsed[0]).padStart(2, '0');
  const e = String(parsed[1]).padStart(2, '0');
  return `${s}:00 - ${e}:00`;
}

export function isBiHourlyAuditSlot(rawSlot: string): boolean {
  const parsed = parseSlotHours24(rawSlot);
  if (!parsed) return false;
  return parsed[1] - parsed[0] === 2;
}

/**
 * Admin, Auditor, and IE can write Weekly BS OQL / NFL AQC and Daily Bi-Hourly Audit data.
 */
export function canWriteWeeklyQualityOrBiHourlyAudit(role?: UserRole | null): boolean {
  return role === 'ADMIN' || role === 'AUDITOR' || role === 'IE';
}

export const DEFECT_TYPES = [
  'Poor iron', 'Uncut thread', 'Dirty spot', 'Oil spot', 'Skip stitch',
  'Open seam', 'Broken stitch', 'Puckering', 'Pleat', 'Shading', 'Custom'
];

export const STANDARD_DEFECT_TYPES = DEFECT_TYPES.filter(d => d !== 'Custom');

export const NPT_TYPES = [
  'Machine Breakdown', 'Power Failure', 'Material Shortage',
  'Manpower Shortage', 'Quality Issue', 'Setup Time', 'Custom'
];

export const STANDARD_USERS: UserAccount[] = [
  { id: '1015198', name: 'Primary Administrator (1015198)', username: '1015198', role: 'ADMIN', floor: 'All' },
  { id: 'admin', name: 'System Admin', username: 'admin', role: 'ADMIN', floor: 'All' },
  { id: 'auditor', name: 'NFL Auditor (Bi-Hourly & Weekly OQL)', username: 'auditor', role: 'AUDITOR', floor: 'All' },
  { id: 'qual_rep', name: 'Quality Inspector (Hourly DHU)', username: 'qual_rep', role: 'QUALITY_REPORTER', floor: 'Sew 2' },
  { id: 'prod_rep', name: 'Production Reporter', username: 'prod_rep', role: 'PRODUCTION_REPORTER', floor: 'Sew 2' },
  { id: 'ie', name: 'IE Officer', username: 'ie', role: 'IE', floor: 'All' },
  { id: 'supervisor', name: 'Floor Supervisor', username: 'supervisor', role: 'SUPERVISOR', floor: 'Sew 2' },
  { id: 'production', name: 'Production Manager', username: 'production', role: 'PRODUCTION_MANAGER', floor: 'All' },
  { id: 'quality', name: 'Quality Manager', username: 'quality', role: 'QUALITY_MANAGER', floor: 'All' },
  { id: 'mechanic', name: 'Senior Mechanic', username: 'mechanic', role: 'MECHANIC', floor: 'Sew 2' },
  { id: 'input_coord', name: 'Input Coordinator', username: 'input_coord', role: 'INPUT_MATERIAL', floor: 'Sew 2' },
  { id: 'management', name: 'Factory Management', username: 'management', role: 'MANAGEMENT_VIEW', floor: 'All' }
];

export function generateRecentIsoWeeks(count = 12): Array<{ weekKey: string; weekStartIso: string }> {
  const result: Array<{ weekKey: string; weekStartIso: string }> = [];
  const now = new Date();
  const day = now.getDay();
  const diffToMon = (day === 0 ? -6 : 1) - day;
  const currentMon = new Date(now);
  currentMon.setDate(now.getDate() + diffToMon);

  for (let i = count - 1; i >= 0; i--) {
    const d = new Date(currentMon);
    d.setDate(currentMon.getDate() - i * 7);
    const isoDate = d.toISOString().slice(0, 10);
    // Calculate ISO week number
    const temp = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const dayNum = temp.getUTCDay() || 7;
    temp.setUTCDate(temp.getUTCDate() + 4 - dayNum);
    const yearStart = new Date(Date.UTC(temp.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil((((temp.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    const weekKey = `${temp.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
    result.push({ weekKey, weekStartIso: isoDate });
  }
  return result;
}

export function generateSyntheticDemoDataset(dateStr: string = getBangladeshTodayIso()): {
  targets: LineTargetConfig[];
  productions: ProductionEntryRecord[];
  qualities: QualityEntryRecord[];
  incidents: IncidentRecord[];
  weeklyQuality: WeeklyQualityRecord[];
  monthlyTargets: MonthlyTargetConfig[];
} {
  const targets: LineTargetConfig[] = [];
  const productions: ProductionEntryRecord[] = [];
  const qualities: QualityEntryRecord[] = [];

  const demoSlots = [
    '08:00 - 09:00',
    '09:00 - 10:00',
    '10:00 - 11:00',
    '11:00 - 12:00',
    '12:00 - 13:00',
    '14:00 - 15:00',
    '15:00 - 16:00'
  ];

  ALL_FACTORY_LINES.forEach((line, idx) => {
    const styleObj = STANDARD_STYLES[idx % STANDARD_STYLES.length];
    const dailyTarget = 480 + (idx % 6) * 40;
    const hourlyTarget = Math.round(dailyTarget / 8);

    targets.push({
      lineId: line.id,
      floor: line.floor,
      date: dateStr,
      dailyTargetPcs: dailyTarget,
      hourlyTargetPcs: hourlyTarget,
      targetEfficiencyPct: 75,
      buyer: 'NFL Global',
      styleNumber: styleObj.styleNumber,
      smv: styleObj.smv,
      operatorsCount: 20,
      machinesCount: 20,
      helpersCount: 2,
      includeHelpersInEfficiency: false,
      workingHoursSoFar: 7,
      totalShiftHours: 8,
      isDemo: true
    });

    demoSlots.forEach((slot, sIdx) => {
      const slotFactor = 0.78 + ((idx + sIdx) % 5) * 0.05;
      const slotQty = Math.round(hourlyTarget * slotFactor);
      productions.push({
        id: `demo-prod-${line.id}-${dateStr}-${sIdx}`,
        submissionId: `demo-prod-${line.id}-${dateStr}-${sIdx}`,
        lineId: line.id,
        floor: line.floor,
        date: dateStr,
        hourSlot: slot,
        styleNumber: styleObj.styleNumber,
        outputQty: slotQty,
        isConfirmedZero: false,
        remarks: 'Demo hourly output',
        reportedBy: 'Demo Seeder',
        timestamp: Date.now() - (demoSlots.length - sIdx) * 1800_000,
        revision: 1,
        isDemo: true
      });
    });

    const defectsCount = 2 + (idx % 4);
    const sewSent = defectsCount + 2;
    const sewRecv = Math.max(0, sewSent - (idx % 3));
    const spotSent = 1 + (idx % 2);
    const spotRecv = idx % 2 === 0 ? spotSent : Math.max(0, spotSent - 1);

    qualities.push({
      id: `demo-qa-${line.id}-${dateStr}`,
      submissionId: `demo-qa-${line.id}-${dateStr}`,
      lineId: line.id,
      floor: line.floor,
      date: dateStr,
      hourSlot: demoSlots[idx % demoSlots.length],
      styleNumber: styleObj.styleNumber,
      checkedGarments: 120,
      defectiveGarments: Math.max(1, defectsCount - 1),
      defects: [
        { defectType: STANDARD_DEFECT_TYPES[idx % STANDARD_DEFECT_TYPES.length], count: defectsCount }
      ],
      totalDefects: defectsCount,
      defectSent: sewSent,
      defectReceived: sewRecv,
      spotSent: spotSent,
      spotReceived: spotRecv,
      isConfirmedZero: false,
      remarks: 'Demo 1-Hr QC inspection',
      reportedBy: 'Demo QA',
      timestamp: Date.now() - 3600_000,
      revision: 1,
      isDemo: true
    });

    // Also seed a Bi-Hourly Audit (2-Hr OQL) record
    const biDefective = 1 + (idx % 3);
    qualities.push({
      id: `demo-biqa-${line.id}-${dateStr}`,
      submissionId: `demo-biqa-${line.id}-${dateStr}`,
      lineId: line.id,
      floor: line.floor,
      date: dateStr,
      hourSlot: BI_HOURLY_AUDIT_SLOTS[idx % 3],
      styleNumber: styleObj.styleNumber,
      checkedGarments: 80,
      defectiveGarments: biDefective,
      defects: [
        { defectType: STANDARD_DEFECT_TYPES[(idx + 2) % STANDARD_DEFECT_TYPES.length], count: biDefective }
      ],
      totalDefects: biDefective,
      defectSent: 0,
      defectReceived: 0,
      spotSent: 0,
      spotReceived: 0,
      isConfirmedZero: false,
      remarks: 'Demo 2-Hr Bi-Hourly Audit',
      reportedBy: 'NFL Auditor',
      timestamp: Date.now() - 2400_000,
      revision: 1,
      isDemo: true
    });
  });

  const incidents: IncidentRecord[] = [
    {
      id: 'demo-inc-1',
      floor: 'Sew 2',
      lineId: 'K1',
      category: 'MACHINE_BREAKDOWN',
      machineRef: 'SNLS-04',
      description: 'Needle bar vibration & thread breakage on SNLS-04',
      reportedCause: 'Drive belt tension slip',
      impactLevel: 'HIGH',
      affectedManpower: 4,
      startTimeMillis: Date.now() - 25 * 60 * 1000,
      status: 'OPEN',
      reportedBy: 'Floor Supervisor',
      isDemo: true
    },
    {
      id: 'demo-inc-2',
      floor: 'Sew 1',
      lineId: 'A2',
      category: 'INPUT_MATERIAL',
      description: 'Waiting for collar rib bundles from cutting store',
      reportedCause: 'Bundle numbering delay',
      impactLevel: 'MEDIUM',
      affectedManpower: 8,
      startTimeMillis: Date.now() - 18 * 60 * 1000,
      status: 'ACKNOWLEDGED',
      acknowledgedBy: 'Biplob Das',
      acknowledgedTimeMillis: Date.now() - 10 * 60 * 1000,
      reportedBy: 'Line Supervisor',
      isDemo: true
    }
  ];

  const recentWeeks = generateRecentIsoWeeks(8);
  const sampleBs = [4.1, 4.4, 3.8, 4.7, 4.2, 3.9, 3.6, 3.5];
  const sampleAqc = [5.2, 4.9, 4.6, 5.1, 4.5, 4.3, 4.1, 3.9];
  const weeklyQuality: WeeklyQualityRecord[] = recentWeeks.map((w, i) => ({
    weekKey: w.weekKey,
    weekStartIso: w.weekStartIso,
    bsOqlPct: sampleBs[i % sampleBs.length],
    nflAqcPct: sampleAqc[i % sampleAqc.length],
    lastUpdatedBy: 'NFL Auditor',
    lastUpdatedMillis: Date.now() - (8 - i) * 86400_000,
    isDemo: true
  }));

  const monthYear = dateStr.slice(0, 7);
  const monthlyTargets: MonthlyTargetConfig[] = [
    {
      id: `Sew 1__${monthYear}`,
      floor: 'Sew 1',
      lineId: '',
      monthYear,
      targetEfficiencyPct: 75,
      targetDhuPct: 2.0,
      targetOqlPct: 4.5,
      bsQaOqlPct: 2.4,
      friPassRatePct: 98.5
    },
    {
      id: `Sew 2__${monthYear}`,
      floor: 'Sew 2',
      lineId: '',
      monthYear,
      targetEfficiencyPct: 75,
      targetDhuPct: 2.0,
      targetOqlPct: 4.5,
      bsQaOqlPct: 2.3,
      friPassRatePct: 99.0
    },
    {
      id: `Sew 3__${monthYear}`,
      floor: 'Sew 3',
      lineId: '',
      monthYear,
      targetEfficiencyPct: 75,
      targetDhuPct: 2.0,
      targetOqlPct: 4.5,
      bsQaOqlPct: 2.5,
      friPassRatePct: 98.0
    }
  ];

  return { targets, productions, qualities, incidents, weeklyQuality, monthlyTargets };
}