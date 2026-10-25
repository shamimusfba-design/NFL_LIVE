import {
  LineMasterItem,
  UserAccount,
  LineTargetConfig,
  IncidentRecord,
  ProductionEntryRecord,
  QualityEntryRecord
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
  // Sew 1 (22 lines)
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
  '13:00 - 14:00',
  '14:00 - 15:00',
  '15:00 - 16:00',
  '16:00 - 17:00',
  '17:00 - 18:00',
  '18:00 - 19:00'
];

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
  { id: 'ie', name: 'IE Officer', username: 'ie', role: 'IE', floor: 'All' },
  { id: 'supervisor', name: 'Floor Supervisor', username: 'supervisor', role: 'SUPERVISOR', floor: 'Sew 2' },
  { id: 'production', name: 'Production Manager', username: 'production', role: 'PRODUCTION_MANAGER', floor: 'All' },
  { id: 'quality', name: 'Quality Manager', username: 'quality', role: 'QUALITY_MANAGER', floor: 'All' },
  { id: 'mechanic', name: 'Senior Mechanic', username: 'mechanic', role: 'MECHANIC', floor: 'Sew 2' },
  { id: 'input_coord', name: 'Input Coordinator', username: 'input_coord', role: 'INPUT_MATERIAL', floor: 'Sew 2' },
  { id: 'management', name: 'Factory Management', username: 'management', role: 'MANAGEMENT_VIEW', floor: 'All' }
];

export function generateSyntheticDemoDataset(dateStr: string = getBangladeshTodayIso()): {
  targets: LineTargetConfig[];
  productions: ProductionEntryRecord[];
  qualities: QualityEntryRecord[];
  incidents: IncidentRecord[];
} {
  const targets: LineTargetConfig[] = [];
  const productions: ProductionEntryRecord[] = [];
  const qualities: QualityEntryRecord[] = [];

  ALL_FACTORY_LINES.forEach((line, idx) => {
    const styleObj = STANDARD_STYLES[idx % STANDARD_STYLES.length];
    const dailyTarget = 480 + (idx % 6) * 40;
    const hourlyTarget = Math.round(dailyTarget / 8);
    const achievedTotal = Math.round(dailyTarget * (0.76 + (idx % 5) * 0.04));

    targets.push({
      lineId: line.id,
      floor: line.floor,
      date: dateStr,
      dailyTargetPcs: dailyTarget,
      hourlyTargetPcs: hourlyTarget,
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

    productions.push({
      id: `demo-prod-${line.id}-${dateStr}`,
      submissionId: `demo-prod-${line.id}-${dateStr}`,
      lineId: line.id,
      floor: line.floor,
      date: dateStr,
      hourSlot: '15:00 - 16:00',
      styleNumber: styleObj.styleNumber,
      outputQty: achievedTotal,
      isConfirmedZero: false,
      remarks: 'Demo seed output',
      reportedBy: 'Demo Seeder',
      timestamp: Date.now() - 3600_000,
      revision: 1,
      isDemo: true
    });

    const defectsCount = 2 + (idx % 4);
    qualities.push({
      id: `demo-qa-${line.id}-${dateStr}`,
      submissionId: `demo-qa-${line.id}-${dateStr}`,
      lineId: line.id,
      floor: line.floor,
      date: dateStr,
      hourSlot: '15:00 - 16:00',
      styleNumber: styleObj.styleNumber,
      checkedGarments: 200,
      defectiveGarments: Math.max(1, defectsCount - 1),
      defects: [
        { defectType: STANDARD_DEFECT_TYPES[idx % STANDARD_DEFECT_TYPES.length], count: defectsCount }
      ],
      totalDefects: defectsCount,
      isConfirmedZero: false,
      remarks: 'Demo seed inspection',
      reportedBy: 'Demo QA',
      timestamp: Date.now() - 3600_000,
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

  return { targets, productions, qualities, incidents };
}