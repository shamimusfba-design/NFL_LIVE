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
  { id: 'ZA2B', floor: 'Sew 3', isActive: true },
  // Cutting: 7 tables (no shift, no overtime). Ids have no spaces so the cloud keys stay safe.
  ...Array.from({ length: 7 }, (_, i) => ({ id: `CUT-Table-${i + 1}`, floor: 'Cutting' as const, isActive: true })),
  // Print: machines 1-3, one table, one manual machine
  { id: 'PRT-Machine-1', floor: 'Print' as const, isActive: true },
  { id: 'PRT-Machine-2', floor: 'Print' as const, isActive: true },
  { id: 'PRT-Machine-3', floor: 'Print' as const, isActive: true },
  { id: 'PRT-Table', floor: 'Print' as const, isActive: true },
  { id: 'PRT-Manual', floor: 'Print' as const, isActive: true },
  // Embroidery: machines 1-5
  ...Array.from({ length: 5 }, (_, i) => ({ id: `EMB-Machine-${i + 1}`, floor: 'Embroidery' as const, isActive: true })),
  // Iron and Packing of each sewing floor: one unit each, targets and hourly output are on the floor total
  { id: 'IRON-1', floor: 'Sew 1' as const, isActive: true }, { id: 'PACK-1', floor: 'Sew 1' as const, isActive: true },
  { id: 'IRON-2', floor: 'Sew 2' as const, isActive: true }, { id: 'PACK-2', floor: 'Sew 2' as const, isActive: true },
  { id: 'IRON-3', floor: 'Sew 3' as const, isActive: true }, { id: 'PACK-3', floor: 'Sew 3' as const, isActive: true },
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

/** The old shared accounts (admin, auditor, ie ...) are closed. Real accounts come from the cloud after sign-in. */
export const STANDARD_USERS: UserAccount[] = [];

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

/** Every ISO week of a year (52 or 53 weeks), Monday to Sunday, in UTC so the dates never shift. */
export function isoWeeksOfYear(year: number): Array<{ weekNo: number; weekKey: string; weekStartIso: string; weekEndIso: string }> {
  const jan4 = new Date(Date.UTC(year, 0, 4));
  const dow = jan4.getUTCDay() || 7;
  const firstMonday = new Date(jan4);
  firstMonday.setUTCDate(jan4.getUTCDate() - (dow - 1));
  const out: Array<{ weekNo: number; weekKey: string; weekStartIso: string; weekEndIso: string }> = [];
  for (let n = 1; n <= 53; n++) {
    const start = new Date(firstMonday);
    start.setUTCDate(firstMonday.getUTCDate() + (n - 1) * 7);
    const thursday = new Date(start);
    thursday.setUTCDate(start.getUTCDate() + 3);
    if (thursday.getUTCFullYear() !== year) break; // a week belongs to the year that holds its Thursday
    const end = new Date(start);
    end.setUTCDate(start.getUTCDate() + 6);
    out.push({
      weekNo: n,
      weekKey: `${year}-W${String(n).padStart(2, '0')}`,
      weekStartIso: start.toISOString().slice(0, 10),
      weekEndIso: end.toISOString().slice(0, 10)
    });
  }
  return out;
}

/** The ISO week that holds the given yyyy-MM-dd date. */
export function isoWeekKeyOf(dateIso: string): string {
  const d = new Date(`${dateIso}T00:00:00Z`);
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
}

// ---------------------------------------------------------------------------
// Hour numbers ("1st hr", "2nd hr"...). Data entry and charts count the hours a line really works,
// so hour 1 of a General line (starts 8:00), of a Shift A line (starts 6:00) and of a Shift B line
// (starts 15:00) line up. The breaks are left out. The stored slot stays the old "HH:00 - HH:00" key.
// ---------------------------------------------------------------------------
export type LineShiftType = 'GENERAL' | 'SHIFT_A' | 'SHIFT_B';

// Shift lines (FA is the first shift, FB the second shift of the same pair). Every other line is General.
// If a line moves between General and Shift, change only these two lists.
const SHIFT_A_LINES = new Set(['D1A', 'D2A', 'E1A', 'E2A', 'FA', 'G1A', 'G2A', 'H1A', 'H2A', 'V1A', 'W1A', 'W2A', 'ZA1A', 'ZA2A']);
const SHIFT_B_LINES = new Set(['D1B', 'D2B', 'E1B', 'E2B', 'FB', 'G1B', 'G2B', 'H1B', 'H2B', 'V1B', 'W1B', 'W2B', 'ZA1B', 'ZA2B']);

export function lineShiftType(lineId: string): LineShiftType {
  const id = String(lineId || '').trim().toUpperCase();
  if (SHIFT_A_LINES.has(id)) return 'SHIFT_A';
  if (SHIFT_B_LINES.has(id)) return 'SHIFT_B';
  return 'GENERAL';
}

// Clock start hour of each working hour, in order. The lunch/meal break hour is not in the list.
const CLOCK_HOURS: Record<LineShiftType, number[]> = {
  GENERAL: [8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23],
  SHIFT_A: [6, 7, 8, 9, 10, 11, 13, 14],
  SHIFT_B: [15, 16, 17, 18, 19, 20, 21, 22]
};

export function ordinalHr(n: number): string {
  const v = n % 100;
  const suffix = v >= 11 && v <= 13 ? 'th' : n % 10 === 1 ? 'st' : n % 10 === 2 ? 'nd' : n % 10 === 3 ? 'rd' : 'th';
  return `${n}${suffix}`;
}

const pad2 = (n: number) => String(n).padStart(2, '0');

export interface HourOption { no: number; key: string; label: string }

/** The 1-hour slots of a line: hour number, stored key, and label like "1st hr". */
export function hourOptionsForLine(lineId: string): HourOption[] {
  return CLOCK_HOURS[lineShiftType(lineId)].map((h, i) => ({
    no: i + 1,
    key: `${pad2(h)}:00 - ${pad2(h + 1)}:00`,
    label: `${ordinalHr(i + 1)} hr`
  }));
}

/** The 2-hour audit slots of a line: "1st-2nd hr", "3rd-4th hr"... The stored key spans 2 clock hours from the first hour's start. */
export function biHourlyOptionsForLine(lineId: string): HourOption[] {
  const hours = CLOCK_HOURS[lineShiftType(lineId)];
  const out: HourOption[] = [];
  for (let i = 0; i + 1 < hours.length; i += 2) {
    out.push({
      no: i / 2 + 1,
      key: `${pad2(hours[i])}:00 - ${pad2(hours[i] + 2)}:00`,
      label: `${ordinalHr(i + 1)}-${ordinalHr(i + 2)} hr`
    });
  }
  return out;
}

/** Hour number (1, 2, 3...) of a stored 1-hour slot for this line, or null when it is not a 1-hour slot of this line. */
export function hourNoOfSlot(lineId: string, slotKey: string): number | null {
  const parsed = parseSlotHours24(slotKey);
  if (!parsed || parsed[1] - parsed[0] !== 1) return null;
  const idx = CLOCK_HOURS[lineShiftType(lineId)].indexOf(parsed[0]);
  return idx >= 0 ? idx + 1 : null;
}

/** Text for a stored slot: "3rd hr", "1st-2nd hr", or the old clock text when it does not fit the line's hours. */
export function hourLabelOfSlot(lineId: string, slotKey: string): string {
  const parsed = parseSlotHours24(slotKey);
  if (!parsed) return slotKey || '';
  const hours = CLOCK_HOURS[lineShiftType(lineId)];
  if (parsed[1] - parsed[0] === 1) {
    const idx = hours.indexOf(parsed[0]);
    if (idx >= 0) return `${ordinalHr(idx + 1)} hr`;
  } else if (parsed[1] - parsed[0] === 2) {
    const idx = hours.indexOf(parsed[0]);
    if (idx >= 0 && idx % 2 === 0) return `${ordinalHr(idx + 1)}-${ordinalHr(idx + 2)} hr`;
  }
  return formatHourSlot12h(slotKey);
}

export const SEWING_FLOORS = ['Sew 1', 'Sew 2', 'Sew 3'] as const;
export const DEPARTMENT_FLOORS = ['Cutting', 'Print', 'Embroidery'] as const;

/**
 * Does a record of `recordFloor` belong to the chosen view?
 *  - a single floor: exact match (Sew 1, Cutting, Print...)
 *  - "All Sewing": Sew 1 + Sew 2 + Sew 3 only (Iron, Packing, Quality of those floors are inside)
 *  - "Factory": every floor
 */
export function floorMatches(recordFloor: string, view: string): boolean {
  if (view === 'Factory') return true;
  if (view === 'All Sewing') return (SEWING_FLOORS as readonly string[]).includes(recordFloor);
  if (view === 'Print & Embroidery') return recordFloor === 'Print' || recordFloor === 'Embroidery';
  return recordFloor === view;
}

export function isDepartmentFloor(floor: string): boolean {
  return (DEPARTMENT_FLOORS as readonly string[]).includes(floor);
}

/** What one working unit is called on screen in each department */
export function unitWord(floor: string): string {
  if (floor === 'Cutting') return 'Table';
  if (floor === 'Print' || floor === 'Embroidery' || floor === 'Print & Embroidery') return 'Machine';
  return 'Line';
}

/**
 * The name people read. Sewing lines keep their names (J1, FA...).
 * Department units drop the technical prefix: CUT-Table-3 -> "Table 3", PRT-Manual -> "Manual", EMB-Machine-2 -> "Machine 2".
 */
export function lineDisplayName(lineId: string): string {
  const f = /^(IRON|PACK)-(\d)$/.exec(String(lineId || ''));
  if (f) return `${f[1] === 'IRON' ? 'Iron' : 'Packing'} ${f[2]}`;
  const m = /^(CUT|PRT|EMB)-(.+)$/.exec(String(lineId || ''));
  if (!m) return lineId;
  return m[2].replace(/-/g, ' ');
}

/** Iron and Packing units (IRON-1, PACK-2...). They are never counted as sewing lines. */
export function isFinishingUnit(lineId: string): boolean {
  return /^(IRON|PACK)-\d$/.test(String(lineId || ''));
}

export function finishingUnitId(kind: 'IRON' | 'PACK', floor: string): string {
  return `${kind}-${String(floor).replace(/\D/g, '')}`;
}

/** NPT reasons offered for each kind of floor. "Other" lets people describe anything else. */
export function nptCategoriesFor(floor: string): Array<{ id: string; label: string }> {
  if (floor === 'Cutting') return [
    { id: 'FABRIC_ISSUE', label: 'Fabric not received / late' },
    { id: 'MARKER_PATTERN', label: 'Marker / pattern not ready' },
    { id: 'MACHINE_BREAKDOWN', label: 'Machine breakdown' },
    { id: 'FABRIC_FAULT', label: 'Fabric fault / shade problem' },
    { id: 'APPROVAL_WAIT', label: 'Waiting for approval / instruction' },
    { id: 'POWER_FAILURE', label: 'Power failure' },
    { id: 'MANPOWER_SHORTAGE', label: 'Manpower shortage' },
    { id: 'OTHER', label: 'Other (describe below)' }
  ];
  if (floor === 'Print') return [
    { id: 'CHANGEOVER', label: 'Changeover (style / colour)' },
    { id: 'SCREEN_PREP', label: 'Screen / plate preparation' },
    { id: 'MACHINE_BREAKDOWN', label: 'Machine breakdown' },
    { id: 'QUALITY_PROBLEM', label: 'Print quality issue / rework' },
    { id: 'INK_COLOR', label: 'Ink / colour mixing wait' },
    { id: 'WAITING_CUT_PANELS', label: 'Waiting for cut panels' },
    { id: 'DRYER_CURING', label: 'Dryer / curing problem' },
    { id: 'POWER_FAILURE', label: 'Power failure' },
    { id: 'OTHER', label: 'Other (describe below)' }
  ];
  if (floor === 'Embroidery') return [
    { id: 'MACHINE_SETTING', label: 'Machine setting for new style' },
    { id: 'DESIGN_FILE', label: 'Design file not ready' },
    { id: 'THREAD_NEEDLE', label: 'Thread / needle problem' },
    { id: 'FRAME_HOOP', label: 'Frame / hoop change' },
    { id: 'MACHINE_BREAKDOWN', label: 'Machine breakdown' },
    { id: 'WAITING_CUT_PANELS', label: 'Waiting for cut panels' },
    { id: 'QUALITY_PROBLEM', label: 'Quality issue' },
    { id: 'POWER_FAILURE', label: 'Power failure' },
    { id: 'OTHER', label: 'Other (describe below)' }
  ];
  return [
    { id: 'MACHINE_BREAKDOWN', label: 'Machine breakdown (to Mechanic)' },
    { id: 'QUALITY_PROBLEM', label: 'Quality problem (to Quality Mgr)' },
    { id: 'LINE_BALANCING', label: 'Line balancing (to IE)' },
    { id: 'INPUT_MATERIAL', label: 'Input / material (to Input team)' },
    { id: 'LOW_PRODUCTION', label: 'Low production (to Prod Mgr)' },
    { id: 'OTHER', label: 'Other (describe below)' }
  ];
}

/** Audit rule: a bi-hourly OQL audit PASSES when its OQL is at or below this limit (the weekly commitment, 4.5%). */
export const AUDIT_PASS_OQL_PCT = 4.5;

export function auditResultOf(checked: number, defective: number): 'PASS' | 'FAIL' | null {
  if (!(checked > 0)) return null;
  return (defective / checked) * 100 <= AUDIT_PASS_OQL_PCT ? 'PASS' : 'FAIL';
}

/** Basic garment items for sewing floors. "Custom item" lets anyone type another one. */
export const GARMENT_ITEMS = [
  'T-shirt', 'Sweatshirt', 'Polo shirt', 'Hoodie', 'Jacket', 'Hoody jacket',
  'Bodysuit', 'Dress', 'Pant', 'Leggings', 'Shorts', 'Jumpsuit', 'Skirt'
];
