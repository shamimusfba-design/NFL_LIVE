export type FloorId = 'Sew 1' | 'Sew 2' | 'Sew 3' | 'All';

export type UserRole =
  | 'ADMIN'
  | 'PRODUCTION_REPORTER'
  | 'QUALITY_REPORTER'
  | 'SUPERVISOR'
  | 'PRODUCTION_MANAGER'
  | 'QUALITY_MANAGER'
  | 'MECHANIC'
  | 'IE'
  | 'INPUT_MATERIAL'
  | 'MANAGEMENT_VIEW';

export interface UserAccount {
  id: string;
  name: string;
  username: string;
  role: UserRole;
  floor?: FloorId;
  assignedLine?: string;
}

export interface LineMasterItem {
  id: string;
  floor: 'Sew 1' | 'Sew 2' | 'Sew 3';
  aliases?: string[];
  isActive: boolean;
  notes?: string;
}

export interface LineTargetConfig {
  lineId: string;
  floor: 'Sew 1' | 'Sew 2' | 'Sew 3';
  date: string;
  dailyTargetPcs: number;
  hourlyTargetPcs: number;
  buyer: string;
  styleNumber: string;
  smv: number; // Standard Minute Value (Strict SMV terminology)
  operatorsCount: number;
  machinesCount: number;
  helpersCount: number;
  includeHelpersInEfficiency: boolean;
  workingHoursSoFar: number; // For elapsed target due calculation
  totalShiftHours: number;
  isDemo?: boolean;
}

export interface ProductionEntryRecord {
  id: string;
  submissionId: string;
  lineId: string;
  floor: 'Sew 1' | 'Sew 2' | 'Sew 3';
  date: string;
  hourSlot: string;
  styleNumber: string;
  outputQty: number;
  isConfirmedZero: boolean;
  remarks?: string;
  reportedBy: string;
  timestamp: number;
  revision: number;
  isDemo?: boolean;
}

export interface QualityDefectItem {
  defectType: string;
  count: number;
}

export interface QualityEntryRecord {
  id: string;
  submissionId: string;
  lineId: string;
  floor: 'Sew 1' | 'Sew 2' | 'Sew 3';
  date: string;
  hourSlot: string;
  styleNumber: string;
  checkedGarments: number;
  defectiveGarments: number;
  defects: QualityDefectItem[];
  totalDefects: number;
  isConfirmedZero: boolean;
  remarks?: string;
  reportedBy: string;
  timestamp: number;
  revision: number;
  isDemo?: boolean;
}

export type IncidentCategory =
  | 'MACHINE_BREAKDOWN'
  | 'QUALITY_PROBLEM'
  | 'LINE_BALANCING'
  | 'INPUT_MATERIAL'
  | 'LOW_PRODUCTION'
  | 'OTHER';

export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface IncidentRecord {
  id: string;
  floor: 'Sew 1' | 'Sew 2' | 'Sew 3';
  lineId: string;
  category: IncidentCategory;
  machineRef?: string;
  description: string;
  reportedCause?: string;
  impactLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'FULL_LINE';
  affectedManpower: number;
  startTimeMillis: number;
  acknowledgedTimeMillis?: number | null;
  acknowledgedBy?: string | null;
  endTimeMillis?: number | null;
  resolvedBy?: string | null;
  resolutionNote?: string;
  status: IncidentStatus;
  isMutedOnCurrentDevice?: boolean;
  reportedBy: string;
  isDemo?: boolean;
}

export interface LinePerformanceSummary {
  lineId: string;
  floor: 'Sew 1' | 'Sew 2' | 'Sew 3';
  styleNumber: string;
  target: number;
  targetDue: number;
  output: number | null; // null means not reported!
  isConfirmedZero: boolean;
  gap: number;
  achievementPct: number | null;
  efficiencyPct: number | null;
  todayOqlPct: number | null;
  monthlyOqlPct: number;
  monthlyEfficiencyPct: number;
  dhu: number | null;
  operators: number;
  machines: number;
  reportingStatus: 'Complete' | 'Not reported' | 'In Progress';
  openIncidentsCount: number;
  lastUpdate: string;
}

export interface FloorKpis {
  todayEfficiencyPct: number;
  monthlyEfficiencyPct: number;
  monthlyEfficiencyDelta: number;
  todayOutputPcs: number;
  todayTargetPcs: number;
  todayTargetDuePcs: number;
  achievementPct: number;
  operatorsCount: number;
  machinesCount: number;
  todayOqlPct: number;
  bsQaOqlPct: number;
  friPassRatePct: number;
  reportingCoveragePct: number;
  reportedLinesCount: number;
  targetedLinesCount: number;
  openIncidentsCount: number;
}
