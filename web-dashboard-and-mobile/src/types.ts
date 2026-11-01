/** Physical floors (where data is entered) */
export type PhysicalFloor = 'Sew 1' | 'Sew 2' | 'Sew 3' | 'Cutting' | 'Print' | 'Embroidery';
/** What the dashboard can show: one floor, all sewing, or the whole factory */
export type FloorId = PhysicalFloor | 'All Sewing' | 'Print & Embroidery' | 'Factory';
export type PeriodId = 'Today' | 'Yesterday' | 'Week' | 'Month';

export type UserRole =
  | 'ADMIN'
  | 'AUDITOR'
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
  designation?: string;
  department?: string;
  accountStatus?: string;
  isActive?: boolean;
  /** Admin-set entry modules this person may write. null = the role's defaults. */
  permissions?: string[] | null;
  /** Admin-set lines this person may enter for. null or empty = all lines of the floor. */
  allowedLines?: string[] | null;
  firebaseUid?: string;
  authorizedDeviceId?: string;
  authorizedDeviceName?: string;
  rejectionReason?: string;
  updatedAtMillis?: number;
}

export interface LineMasterItem {
  id: string;
  floor: PhysicalFloor;
  aliases?: string[];
  isActive: boolean;
  notes?: string;
}

export interface LineTargetConfig {
  lineId: string;
  floor: PhysicalFloor;
  date: string;
  dailyTargetPcs: number;
  hourlyTargetPcs: number;
  targetEfficiencyPct?: number;
  buyer: string;
  styleNumber: string;
  smv: number;
  operatorsCount: number;
  machinesCount: number;
  helpersCount: number;
  includeHelpersInEfficiency: boolean;
  /** Finishing staff of the line. Efficiency counts operators + helpers + finishing staff. */
  finishingStaffCount?: number;
  workingHoursSoFar: number;
  totalShiftHours: number;
  isDemo?: boolean;
}

export interface MonthlyTargetConfig {
  id: string;
  floor: FloorId;
  lineId: string; // blank for floor-level target
  monthYear: string; // e.g. "2026-10"
  targetEfficiencyPct: number;
  targetDhuPct: number;
  targetOqlPct: number;
  bsQaOqlPct: number;
  friPassRatePct: number;
}

export interface WeeklyQualityRecord {
  weekKey: string; // e.g. "2026-W43"
  weekStartIso: string; // Monday ISO date
  bsOqlPct: number | null;
  nflAqcPct: number | null;
  lastUpdatedBy: string;
  lastUpdatedMillis: number;
  isDemo?: boolean;
}

export interface ProductionEntryRecord {
  id: string;
  submissionId: string;
  lineId: string;
  floor: PhysicalFloor;
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
  floor: PhysicalFloor;
  date: string;
  hourSlot: string;
  styleNumber: string;
  checkedGarments: number;
  defectiveGarments: number;
  defects: QualityDefectItem[];
  totalDefects: number;
  defectSent?: number;
  defectReceived?: number;
  spotSent?: number;
  spotReceived?: number;
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
  // Cutting
  | 'FABRIC_ISSUE' | 'MARKER_PATTERN' | 'FABRIC_FAULT' | 'APPROVAL_WAIT'
  // Print
  | 'CHANGEOVER' | 'SCREEN_PREP' | 'INK_COLOR' | 'DRYER_CURING'
  // Embroidery
  | 'MACHINE_SETTING' | 'DESIGN_FILE' | 'THREAD_NEEDLE' | 'FRAME_HOOP'
  // Cutting / Print / Embroidery
  | 'WAITING_CUT_PANELS' | 'POWER_FAILURE' | 'MANPOWER_SHORTAGE'
  | 'OTHER';

export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface IncidentRecord {
  id: string;
  floor: PhysicalFloor;
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
  floor: PhysicalFloor;
  styleNumber: string;
  smv: number;
  hourlyTarget: number;
  target: number;
  targetDue: number;
  output: number | null;
  isConfirmedZero: boolean;
  gap: number;
  achievementPct: number | null;
  efficiencyPct: number | null;
  targetEfficiencyPct: number;
  todayOqlPct: number | null;
  monthlyOqlPct: number;
  monthlyEfficiencyPct: number;
  dhu: number | null;
  checkedGarments: number;
  defectiveGarments: number;
  totalDefects: number;
  sewSent: number;
  sewRecv: number;
  sewBal: number;
  spotSent: number;
  spotRecv: number;
  spotBal: number;
  operators: number;
  machines: number;
  helpers: number;
  reportingStatus: 'Complete' | 'Not reported' | 'In Progress';
  openIncidentsCount: number;
  lastUpdate: string;
}

export interface FloorKpis {
  todayEfficiencyPct: number;
  targetEfficiencyPct: number;
  monthlyEfficiencyPct: number;
  monthlyEfficiencyDelta: number;
  todayOutputPcs: number;
  todayTargetPcs: number;
  todayTargetDuePcs: number;
  achievementPct: number;
  operatorsCount: number;
  machinesCount: number;
  helpersCount: number;
  todayDhuPct: number;
  dhuLimitPct: number;
  todayOqlPct: number;
  oqlCommitmentPct: number;
  bsQaOqlPct: number;
  friPassRatePct: number;
  checkedGarments: number;
  defectiveGarments: number;
  totalDefects: number;
  defectsPending: number;
  sewSent: number;
  sewRecv: number;
  sewPending: number;
  spotSent: number;
  spotRecv: number;
  spotPending: number;
  reportingCoveragePct: number;
  reportedLinesCount: number;
  targetedLinesCount: number;
  totalFloorLinesCount: number;
  openIncidentsCount: number;
  downtimeMinutes: number;
}

/** Floor totals typed once a day under the line-wise target set-up (same record as the Android app). */
export interface FloorManpowerRecord {
  floor: PhysicalFloor;
  date: string;
  totalMachines: number;
  machinesRunning: number;
  operatorsPresent: number;
  helpersPresent: number;
  /** Operators doing handwork (usually because helpers are short). They are part of operatorsPresent. */
  operatorsOnHandwork: number;
  updatedBy: string;
  updatedMillis: number;
}

/** A comment, or a reply (parentId set), under an NPT incident or a high-OQL alert. */
export interface AlertComment {
  id: string;
  /** The incident id, or "oql|<line>|<date>" for a high-OQL alert. */
  targetId: string;
  parentId: string | null;
  authorId: string;
  authorName: string;
  authorRole: string;
  authorDesignation?: string;
  text: string;
  createdMillis: number;
}
