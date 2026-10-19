package com.example.model

import java.util.UUID

enum class SyncStatus {
  SYNCED,
  PENDING,
  FAILED
}

enum class FloorScope(val displayName: String, val apiValue: String) {
  SEW_1("Sew-1", "Sew 1"),
  SEW_2("Sew-2", "Sew 2"),
  SEW_3("Sew-3", "Sew 3"),
  FINISHING("Finishing", "Finishing"),
  ALL("All", "All")
}

enum class PeriodScope(val displayName: String) {
  YESTERDAY("Yesterday"),
  TODAY("Today"),
  WEEK("Weekly"),
  MONTH("Monthly")
}

enum class MonthlyMetricType(val displayName: String, val banglaName: String) {
  EFFICIENCY("Monthly Efficiency", "মান্থলি এফিসিয়েন্সি"),
  DHU("Monthly DHU", "মান্থলি ডিএইচইউ"),
  OQL("Monthly OQL", "মান্থলি ওকেএল")
}

enum class UserRole(val displayName: String, val group: String) {
  ADMIN("Admin", "Administration"),
  SUPERVISOR("Supervisor", "Floor Supervision"),
  PRODUCTION_REPORTER("Production Reporter", "Data Entry"),
  QUALITY_REPORTER("Quality Reporter (Endline)", "Quality Inspection"),
  AUDITOR("Quality Auditor (OQL)", "Audit & Compliance"),
  PRODUCTION_MANAGER("Production Manager / Officer", "Management"),
  QUALITY_MANAGER("Quality Manager / Officer", "Management"),
  MECHANIC("Mechanic / Maintenance", "Engineering"),
  IE("IE (Industrial Engineering)", "Engineering"),
  INPUT_MATERIAL("Input / Material Personnel", "Supply Chain"),
  MANAGEMENT_VIEW("Management / View-Only", "Executive")
}

enum class AppFunctionCategory(val displayName: String, val banglaName: String) {
  DATA_ENTRY("Data Entry Functions", "ডাটা এন্ট্রি ফাংশনসমূহ"),
  VISUAL_REPORT("Visual & Reporting Functions", "ভিজ্যুয়াল ও রিপোর্ট ফাংশনসমূহ")
}

data class AppFeaturePermission(
  val key: String,
  val englishName: String,
  val banglaName: String,
  val category: AppFunctionCategory,
  val description: String
)

const val PRIMARY_ADMIN_GMAIL = "shamim.sustipe15@gmail.com"
const val MAX_APPROVED_DEVICES_PER_USER = 2

enum class AccountStatus {
  APPROVED,
  PENDING_APPROVAL,
  SUSPENDED
}

data class ApprovedDevice(
  val deviceId: String,
  val deviceName: String,
  val approvedAtMillis: Long = System.currentTimeMillis(),
  val approvedBy: String = PRIMARY_ADMIN_GMAIL,
  val lastActiveMillis: Long = System.currentTimeMillis()
)

enum class ApprovalRequestStatus {
  PENDING,
  APPROVED,
  REJECTED,
  EXPIRED
}

data class DeviceApprovalRequest(
  val requestId: String = UUID.randomUUID().toString(),
  val userId: String = "",
  val userName: String = "",
  val employeeId: String = userId,
  val employeeName: String = userName,
  val role: UserRole = UserRole.SUPERVISOR,
  val deviceId: String = "",
  val deviceName: String = "",
  val requestType: String = "NEW_DEVICE",
  val requestedAtMillis: Long = System.currentTimeMillis(),
  val status: String = "PENDING",
  val otpHash: String = "",
  val verificationCodeHash: String = otpHash,
  val otpExpiresAtMillis: Long = System.currentTimeMillis() + 10 * 60 * 1000L,
  val codeExpiresAtMillis: Long = otpExpiresAtMillis,
  val otpAttempts: Int = 0,
  val attemptCount: Int = otpAttempts,
  val maxAttempts: Int = 5,
  val resendCount: Int = 0,
  val maxResends: Int = 3,
  val lastSentAtMillis: Long = 0L,
  val otpUsed: Boolean = false,
  val isCodeUsed: Boolean = otpUsed,
  val emailDeliveryStatus: String = "PENDING",
  val reason: String = "Device / Account Authorization"
)

enum class AuthStatus {
  SUCCESS,
  INVALID_CREDENTIALS,
  ACCOUNT_SUSPENDED,
  PENDING_ADMIN_APPROVAL,
  ACCOUNT_PENDING_APPROVAL,
  DEVICE_APPROVAL_REQUIRED,
  DEVICE_LIMIT_REACHED
}

data class AuthResult(
  val status: AuthStatus,
  val user: UserAccount? = null,
  val pendingRequest: DeviceApprovalRequest? = null,
  val message: String = "",
  val activeDevicesCount: Int = 0
)

data class SecurityActionResult(
  val success: Boolean,
  val message: String
)

data class UserAccount(
  val id: String, // Employee ID card number
  val name: String,
  val employeeId: String = id,
  val password: String = "1234",
  val passwordHash: String = "",
  val role: UserRole = UserRole.SUPERVISOR,
  val floor: FloorScope = FloorScope.SEW_2,
  val assignedLine: String = "K1",
  val isAdmin: Boolean = false,

  // 1. VISUAL / VIEW PERMISSIONS (কে দেখতে পারবে)
  val canViewDashboard: Boolean = true,
  val canViewTvDashboard: Boolean = true,
  val canViewMonthlyEfficiency: Boolean = true,
  val canViewMonthlyDhu: Boolean = true,
  val canViewMonthlyOql: Boolean = true,
  val canViewLineDeepDive: Boolean = true,
  val canViewAuditLogs: Boolean = true,
  val canExportReports: Boolean = true,

  // 2. DATA ENTRY PERMISSIONS (কে এন্ট্রি দিতে পারবে)
  val canProductionWrite: Boolean = false,
  val canFinishingWrite: Boolean = false,
  val canQualityWrite: Boolean = false,
  val canOqlWrite: Boolean = false,
  val canNptWrite: Boolean = false,
  val canMorningTargetWrite: Boolean = false,
  val canMonthlyTargetWrite: Boolean = false,
  val canTargetRevisionWrite: Boolean = false,

  // Backward compatibility flags
  val canProductionRead: Boolean = true,
  val canQualityRead: Boolean = true,
  val canNptRead: Boolean = true,
  val canMorningTargetRead: Boolean = true,
  val canMonthlyTargetRead: Boolean = true,

  // 3. SECURITY, ACCOUNT STATUS & 2-DEVICE LIMIT
  val isActive: Boolean = true,
  val accountStatus: AccountStatus = AccountStatus.APPROVED,
  val approvedDevices: List<ApprovedDevice> = emptyList(),
  val maxDevices: Int = MAX_APPROVED_DEVICES_PER_USER
) {
  val hasAnyWritePermission: Boolean
    get() = canProductionWrite || canFinishingWrite || canQualityWrite ||
        canOqlWrite || canNptWrite || canMorningTargetWrite ||
        canMonthlyTargetWrite || canTargetRevisionWrite || isAdmin

  val isViewOnly: Boolean
    get() = !hasAnyWritePermission
}

data class LineInfo(
  val id: String,
  val floor: String,
  val aliases: List<String> = emptyList(),
  val isActive: Boolean = true
) {
  fun matches(lineInput: String): Boolean {
    val trimmed = lineInput.trim()
    return id.equals(trimmed, ignoreCase = true) ||
        aliases.any { it.equals(trimmed, ignoreCase = true) }
  }
}

data class StyleInfo(
  val styleNumber: String,
  val buyer: String,
  val smv: Double, // Standard Minute Value (Strict SMV terminology)
  val defaultTargetPcsPerHour: Int
)

data class ProductionRecord(
  val id: String = UUID.randomUUID().toString(),
  val submissionId: String = UUID.randomUUID().toString(),
  val lineId: String,
  val floor: String = "Sew 2",
  val date: String,
  val hourSlot: String,
  val styleNumber: String,
  val outputQty: Int,
  val remarks: String = "",
  val timestamp: Long = System.currentTimeMillis(),
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  val revision: Int = 1,
  val modifiedBy: String = "Production Reporter",
  val isConfirmedZero: Boolean = false,
  val isDemo: Boolean = false
)

data class DefectItem(
  val defectType: String,
  val count: Int
)

data class QualityRecord(
  val id: String = UUID.randomUUID().toString(),
  val submissionId: String = UUID.randomUUID().toString(),
  val lineId: String,
  val floor: String = "Sew 2",
  val date: String,
  val hourSlot: String,
  val styleNumber: String,
  val checkedGarments: Int,
  val defectiveGarments: Int,
  val defects: List<DefectItem> = emptyList(),
  val totalDefects: Int = defects.sumOf { it.count },
  val remarks: String = "",
  val defectSent: Int = 0,
  val defectReceived: Int = 0,
  val defectBalance: Int = 0,
  val spotSent: Int = 0,
  val spotReceived: Int = 0,
  val spotBalance: Int = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  val revision: Int = 1,
  val modifiedBy: String = "Quality Inspector",
  val isConfirmedZero: Boolean = false,
  val isDemo: Boolean = false
)

data class SupervisorLineSetup(
  val id: String = UUID.randomUUID().toString(),
  val lineId: String,
  val floor: String = "Sew 2",
  val date: String,
  val styleNumber: String = "",
  val itemName: String = "Polo Shirt",
  val orderNumber: String = "PO-88210",
  val smv: Double = 14.50,
  val targetHourlyPcs: Int = 60,
  val targetDailyPcs: Int = 480,
  val targetEfficiencyPct: Double = 75.0,
  val operatorsCount: Int = 20,
  val machinesCount: Int = 20,
  val helpersCount: Int = 2,
  val finishingStaffCount: Int = 4,
  val includeHelpersInEfficiency: Boolean = false,
  val workingMinutesPerHour: Int = 60,
  val workingHoursSoFar: Int = 7,
  val totalShiftHours: Int = 8,
  val snapshotTimestamp: Long = System.currentTimeMillis(),
  val isDemo: Boolean = false
) {
  val includedManpower: Int
    get() = operatorsCount + (if (includeHelpersInEfficiency) helpersCount else 0)
}

enum class IncidentCategory(val displayName: String, val targetRole: UserRole) {
  MACHINE_BREAKDOWN("Machine Breakdown", UserRole.MECHANIC),
  QUALITY_PROBLEM("Quality Issue", UserRole.QUALITY_MANAGER),
  INPUT_MATERIAL("Input N/A", UserRole.INPUT_MATERIAL),
  OPERATOR_NA("Operator N/A", UserRole.SUPERVISOR),
  LINE_BALANCING("Line Balancing", UserRole.IE),
  POWER_OUTAGE("Power / Utility Outage", UserRole.MECHANIC),
  LOW_PRODUCTION("Low Production", UserRole.PRODUCTION_MANAGER),
  OTHER("Custom / Other", UserRole.SUPERVISOR)
}

enum class IncidentStatus {
  OPEN,
  ACKNOWLEDGED,
  RESOLVED
}

data class IncidentItem(
  val id: String = UUID.randomUUID().toString(),
  val submissionId: String = UUID.randomUUID().toString(),
  val lineId: String,
  val floor: String = "Sew 2",
  val category: IncidentCategory = IncidentCategory.MACHINE_BREAKDOWN,
  val styleNumber: String = "",
  val nptType: String = "Machine Breakdown",
  val customNptTypeName: String = "",
  val processName: String = "",
  val machineType: String = "",
  val machineRef: String = "",
  val description: String,
  val reportedCause: String = "",
  val impactLevel: String = "HIGH",
  val affectedManpower: Int = 6,
  val startTimeMillis: Long = System.currentTimeMillis(),
  val acknowledgedTimeMillis: Long? = null,
  val acknowledgedBy: String? = null,
  val mechanicStartTimeMillis: Long? = null,
  val mechanicName: String? = null,
  val isMaintenanceStarted: Boolean = false,
  val endTimeMillis: Long? = null,
  val resolvedBy: String? = null,
  val resolutionNote: String = "",
  val status: IncidentStatus = IncidentStatus.OPEN,
  val isMutedOnCurrentDevice: Boolean = false,
  val reportedBy: String = "Supervisor",
  val isDemo: Boolean = false
)

data class MonthlyFactoryTarget(
  val floor: String = "Sew 2",
  val monthYear: String = "October 2026",
  val targetEfficiencyPct: Double = 72.0,
  val updateEfficiencyPct: Double = 68.5,
  val targetOqlPct: Double = 3.50,
  val targetDhuPct: Double = 2.00,
  val friPassRatePct: Double = 100.0,
  val bsOqlPct: Double = 2.40,
  val lastUpdatedBy: String = "Management / IE",
  val lastUpdatedMillis: Long = System.currentTimeMillis()
)

data class HourlyProductionRow(
  val hourSlot: String,
  val hourlyTarget: Int,
  val achieveOutput: Int,
  val totalTarget: Int,
  val totalAchieve: Int,
  val efficiencyPct: Double,
  val achievePct: Double,
  val oeePct: Double = 0.0
)

data class HourlyQualityRow(
  val hourSlot: String,
  val dhuPct: Double,
  val oqlPct: Double,
  val majorDefectsName: String
)

data class LineHourlyDefectRecord(
  val hourSlot: String,
  val sewingSent: Int,
  val sewingReceived: Int,
  val sewingBalance: Int,
  val spotSent: Int,
  val spotReceived: Int,
  val spotBalance: Int
)

data class ParetoDefectPoint(
  val defectName: String,
  val count: Int,
  val percentage: Double
)

data class AuditLogEntry(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: Long = System.currentTimeMillis(),
  val entityType: String,
  val entityId: String,
  val action: String,
  val changedBy: String,
  val details: String,
  val reason: String = "",
  val deviceId: String = "",
  val bangladeshTime: String = ""
)

data class LinePerformancePoint(
  val lineId: String,
  val floor: String = "Sew 2",
  val styleNumber: String = "",
  val itemName: String = "Polo Shirt",
  val smv: Double = 14.50,
  val targetPcs: Int,
  val targetDuePcs: Int = targetPcs,
  val hourlyTargetPcs: Int = (targetPcs / 8).coerceAtLeast(1),
  val targetEfficiencyPct: Double = 75.0,
  val achievementPcs: Int? = null, // null means "Not reported"!
  val isConfirmedZero: Boolean = false,
  val achievementPct: Double? = null,
  val todayEfficiencyPct: Double? = null,
  val monthlyEfficiencyPct: Double = 65.0,
  val todayOqlPct: Double? = null,
  val monthlyOqlPct: Double = 3.8,
  val bsOqlPct: Double = 2.40,
  val dhu: Double? = null,
  val monthlyDhuPct: Double = 1.35,
  val reportingStatus: String = "Complete", // "Complete", "Not reported", "In Progress"
  val openIncidentsCount: Int = 0,
  val defectSent: Int = 0,
  val defectReceived: Int = 0,
  val defectBalance: Int = 0,
  val spotSent: Int = 0,
  val spotReceived: Int = 0,
  val spotBalance: Int = 0
)

data class DefectSummary(
  val defectType: String,
  val count: Int,
  val dhu: Double = 0.35,
  val checkedGarments: Int = 1200,
  val percentage: Double = 0.0
)

data class LineDhuSummary(
  val lineId: String,
  val dhu: Double
)

data class DailyQaTrend(
  val dateLabel: String,
  val dayName: String = "",
  val bsOqlPct: Double = 3.5,
  val nflAqcPct: Double = 5.0,
  val efficiencyPct: Double = 68.0,
  val targetEfficiencyPct: Double = 75.0,
  val dhu: Double = 1.25,
  val targetDhu: Double = 1.50,
  val outputPcs: Int = 3450,
  val targetPcs: Int = 4200
)

data class NptCategorySlice(
  val categoryName: String,
  val minutes: Int,
  val count: Int,
  val percentage: Double,
  val impactLevel: String = "HIGH",
  val colorHex: Long = 0xFFEF4444
)

data class FloorKpiSummary(
  val todayEfficiency: Double = 68.0,
  val todayTargetEfficiency: Double = 75.0,
  val monthlyEfficiency: Double = 72.2,
  val monthlyEfficiencyDelta: Double = 4.1,
  val operatorsCount: Int = 266,
  val machinesCount: Int = 267,
  val todayOutputPcs: Int = 3450,
  val todayTargetPcs: Int = 4200,
  val todayTargetDuePcs: Int = 3680,
  val todayOql: Double = 3.81,
  val bsQaOql: Double = 5.00,
  val friPassRate: Double = 100.0,
  val reportingCoveragePct: Int = 95,
  val openIncidentsCount: Int = 0,
  val totalDefectSent: Int = 0,
  val totalDefectReceived: Int = 0,
  val totalDefectBalance: Int = 0,
  val totalSpotSent: Int = 0,
  val totalSpotReceived: Int = 0,
  val totalSpotBalance: Int = 0
)

data class TvDashboardData(
  val floorScope: FloorScope = FloorScope.SEW_2,
  val periodScope: PeriodScope = PeriodScope.TODAY,
  val kpis: FloorKpiSummary = FloorKpiSummary(),
  val linePerformance: List<LinePerformancePoint> = emptyList(),
  val topDefects: List<DefectSummary> = emptyList(),
  val highestDhuLines: List<LineDhuSummary> = emptyList(),
  val weeklyQaTrend: List<DailyQaTrend> = emptyList(),
  val nptBreakdown: List<NptCategorySlice> = emptyList(),
  val activeIncidents: List<IncidentItem> = emptyList(),
  val lastDataUpdate: String = "10:24 AM",
  val lastRefreshTime: String = "18:49",
  val isDemoMode: Boolean = true
)
