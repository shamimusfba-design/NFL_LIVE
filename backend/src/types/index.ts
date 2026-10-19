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

export interface User {
  id: string;
  name: string;
  username: string;
  role: UserRole;
  floor?: 'Sew 1' | 'Sew 2' | 'Sew 3' | 'All';
  assignedLine?: string;
}

export interface ProductionEntry {
  id: string;
  submissionId: string; // Stable UUID for idempotency
  lineId: string;
  date: string;
  hourSlot: string;
  styleNumber: string;
  outputQty: number;
  remarks?: string;
  timestamp: number;
  revision: number;
  modifiedBy: string;
}

export interface DefectItem {
  defectType: string;
  count: number;
}

export interface QualityEntry {
  id: string;
  submissionId: string;
  lineId: string;
  date: string;
  hourSlot: string;
  styleNumber: string;
  checkedGarments: number;
  defectiveGarments: number;
  defects: DefectItem[];
  totalDefects: number;
  remarks?: string;
  timestamp: number;
  revision: number;
  modifiedBy: string;
}

export interface NptEvent {
  id: string;
  submissionId: string;
  lineId: string;
  styleNumber: string;
  reason: string;
  isFullLine: boolean;
  affectedManpower: number;
  machineOrOpRef?: string;
  remarks?: string;
  startTimeMillis: number;
  endTimeMillis?: number | null;
  isClosed: boolean;
  lostManMinutes: number;
  overlapFlagged: boolean;
  createdBy: string;
}

export interface SupervisorSetup {
  lineId: string;
  date: string;
  styleNumber: string;
  orderNumber: string;
  smv: number;
  targetHourlyPcs: number;
  targetEfficiencyPct: number;
  operatorsCount: number;
  helpersCount: number;
  includeHelpersInEfficiency: boolean;
  workingMinutesPerHour: number;
  snapshotTimestamp: number;
}

export interface TvDashboardResponse {
  todayEfficiency: number;
  todayTargetEfficiency: number;
  monthlyEfficiency: number;
  monthlyTargetEfficiency: number;
  operatorsCount: number;
  machinesCount: number;
  todayFloorEfficiency: number;
  monthlyFloorEfficiency: number;
  bsQaOql: number;
  bsQaSampleSize: number;
  bsQaAcceptance: number;
  todayOql: number;
  todayTargetOql: number;
  friPassRate: number;
  friTargetPassRate: number;
  lastDataUpdate: string;
  lastRefreshTime: string;
  isDemoMode: boolean;
  floorName: string;
  linePerformance: Array<{
    lineId: string;
    targetPcs: number;
    achievementPcs: number;
    todayOqlPct: number;
    monthlyOqlPct: number;
    todayEfficiencyPct: number;
    monthlyEfficiencyPct: number;
    dhu: number;
  }>;
  topDefects: Array<{ defectType: string; count: number }>;
  highestDhuLines: Array<{ lineId: string; dhu: number }>;
  weeklyQaTrend: Array<{ dateLabel: string; bsOqlPct: number; nflAqcPct: number }>;
  activeNptEvents: NptEvent[];
}
