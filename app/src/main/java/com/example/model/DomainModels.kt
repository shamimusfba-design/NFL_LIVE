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
  ALL("All Floor", "All")
}

enum class PeriodScope(val displayName: String) {
  YESTERDAY("Yesterday"),
  TODAY("Today"),
  WEEK("Weekly"),
  MONTH("Monthly")
}

enum class MonthlyMetricType(val displayName: String, val banglaName: String) {
  EFFICIENCY("Monthly Efficiency", "Monthly Efficiency"),
  DHU("Monthly DHU", "Monthly DHU"),
  OQL("Monthly OQL", "Monthly OQL")
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

enum class EmployeeDesignation(
  val displayName: String,
  val department: String,
  val defaultRole: UserRole,
  val defaultAuthorizedScope: FloorScope?, // null means use assigned physical floor
  val requiresPhysicalFloor: Boolean = true,
  val isPublicSelectable: Boolean = true
) {
  MANAGEMENT("Management", "Management", UserRole.MANAGEMENT_VIEW, FloorScope.ALL, true, true),
  FACTORY_MANAGEMENT("Factory Management", "Management", UserRole.MANAGEMENT_VIEW, FloorScope.ALL, true, true),
  HEAD_OF_PRODUCTION("Head of Production", "Production", UserRole.PRODUCTION_MANAGER, FloorScope.ALL, true, true),
  HEAD_OF_QUALITY("Head of Quality", "Quality", UserRole.QUALITY_MANAGER, FloorScope.ALL, true, true),
  IE("IE", "IE & Planning", UserRole.IE, null, true, true),
  REPORTER("Reporter", "IE & Planning", UserRole.PRODUCTION_REPORTER, null, true, true),
  PRODUCTION_SUPERVISOR("Production Supervisor", "Production", UserRole.SUPERVISOR, null, true, true),
  PRODUCTION_OFFICER("Production Officer", "Production", UserRole.SUPERVISOR, null, true, true),
  QUALITY_SUPERVISOR("Quality Supervisor", "Quality", UserRole.QUALITY_REPORTER, null, true, true),
  QUALITY_OFFICER("Quality Officer", "Quality", UserRole.AUDITOR, null, true, true),
  FINISHING_SUPERVISOR("Finishing Supervisor", "Finishing", UserRole.SUPERVISOR, FloorScope.FINISHING, false, true),
  FINISHING_IN_CHARGE("Finishing In-charge", "Finishing", UserRole.SUPERVISOR, FloorScope.FINISHING, false, true),
  FINISHING_MANAGER("Finishing Manager", "Finishing", UserRole.PRODUCTION_MANAGER, FloorScope.FINISHING, false, true),
  MAINTENANCE("Maintenance", "Maintenance", UserRole.MECHANIC, null, true, true),
  MECHANIC("Mechanic", "Maintenance", UserRole.MECHANIC, null, true, true),
  OTHER("Other", "Other", UserRole.MANAGEMENT_VIEW, null, true, true),
  ADMIN("Admin", "Administration", UserRole.ADMIN, FloorScope.ALL, false, false);

  companion object {
    fun publicOptions(): List<EmployeeDesignation> = entries.filter { it.isPublicSelectable }

    fun adminOptions(): List<EmployeeDesignation> = entries.toList()

    fun fromDisplayName(name: String): EmployeeDesignation {
      val trimmed = name.trim()
      return entries.firstOrNull {
        it.displayName.equals(trimmed, ignoreCase = true) ||
            it.name.equals(trimmed, ignoreCase = true)
      } ?: OTHER
    }
  }
}

object DesignationPolicy {
  val validPhysicalSewingFloors: List<FloorScope> = listOf(
    FloorScope.SEW_1,
    FloorScope.SEW_2,
    FloorScope.SEW_3
  )

  val validPhysicalFloorsWithFinishing: List<FloorScope> = listOf(
    FloorScope.SEW_1,
    FloorScope.SEW_2,
    FloorScope.SEW_3,
    FloorScope.FINISHING
  )

  fun isFinishingDesignation(designation: EmployeeDesignation): Boolean {
    return designation == EmployeeDesignation.FINISHING_SUPERVISOR ||
        designation == EmployeeDesignation.FINISHING_IN_CHARGE ||
        designation == EmployeeDesignation.FINISHING_MANAGER
  }

  fun sanitizePhysicalFloor(
    requestedFloor: FloorScope,
    designation: EmployeeDesignation
  ): FloorScope {
    if (isFinishingDesignation(designation)) {
      return FloorScope.FINISHING
    }
    return if (requestedFloor in validPhysicalFloorsWithFinishing) {
      requestedFloor
    } else {
      FloorScope.SEW_1
    }
  }

  fun resolveAuthorizedScope(
    designation: EmployeeDesignation,
    physicalFloor: FloorScope,
    overrideScope: FloorScope? = null
  ): FloorScope {
    if (overrideScope != null) return overrideScope
    return designation.defaultAuthorizedScope ?: sanitizePhysicalFloor(physicalFloor, designation)
  }

  fun applyDesignationTemplate(
    base: UserAccount,
    designation: EmployeeDesignation,
    physicalFloor: FloorScope,
    overrideScope: FloorScope? = null
  ): UserAccount {
    val cleanPhysical = sanitizePhysicalFloor(physicalFloor, designation)
    val authScope = resolveAuthorizedScope(designation, cleanPhysical, overrideScope)
    return when (designation) {
      EmployeeDesignation.ADMIN -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.ADMIN,
        floor = cleanPhysical,
        authorizedFloorScope = FloorScope.ALL,
        isAdmin = true,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = true,
        canExportReports = true,
        canProductionWrite = true,
        canFinishingWrite = true,
        canQualityWrite = true,
        canOqlWrite = true,
        canNptWrite = true,
        canMorningTargetWrite = true,
        canMonthlyTargetWrite = true,
        canTargetRevisionWrite = true
      )
      EmployeeDesignation.MANAGEMENT,
      EmployeeDesignation.FACTORY_MANAGEMENT -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.MANAGEMENT_VIEW,
        floor = cleanPhysical,
        authorizedFloorScope = FloorScope.ALL,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = true,
        canExportReports = true,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = false,
        canMorningTargetWrite = false,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.HEAD_OF_PRODUCTION -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.PRODUCTION_MANAGER,
        floor = cleanPhysical,
        authorizedFloorScope = FloorScope.ALL,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = true,
        canExportReports = true,
        canProductionWrite = true,
        canFinishingWrite = true,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = true,
        canMonthlyTargetWrite = true,
        canTargetRevisionWrite = true
      )
      EmployeeDesignation.HEAD_OF_QUALITY -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.QUALITY_MANAGER,
        floor = cleanPhysical,
        authorizedFloorScope = FloorScope.ALL,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = true,
        canExportReports = true,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = true,
        canOqlWrite = true,
        canNptWrite = true,
        canMorningTargetWrite = false,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.IE -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.IE,
        floor = cleanPhysical,
        authorizedFloorScope = authScope,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = true,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = true,
        canMonthlyTargetWrite = true,
        canTargetRevisionWrite = true
      )
      EmployeeDesignation.REPORTER -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.PRODUCTION_REPORTER,
        floor = cleanPhysical,
        authorizedFloorScope = authScope,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = false,
        canProductionWrite = true,
        canFinishingWrite = cleanPhysical == FloorScope.FINISHING,
        canQualityWrite = true,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = true,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.PRODUCTION_SUPERVISOR,
      EmployeeDesignation.PRODUCTION_OFFICER -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.SUPERVISOR,
        floor = cleanPhysical,
        authorizedFloorScope = authScope,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = false,
        canProductionWrite = true,
        canFinishingWrite = false,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = true,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.QUALITY_SUPERVISOR -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.QUALITY_REPORTER,
        floor = cleanPhysical,
        authorizedFloorScope = authScope,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = false,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = true,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = false,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.QUALITY_OFFICER -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.AUDITOR,
        floor = cleanPhysical,
        authorizedFloorScope = authScope,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = true,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = true,
        canOqlWrite = true,
        canNptWrite = true,
        canMorningTargetWrite = false,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.FINISHING_SUPERVISOR,
      EmployeeDesignation.FINISHING_IN_CHARGE,
      EmployeeDesignation.FINISHING_MANAGER -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = designation.defaultRole,
        floor = FloorScope.FINISHING,
        authorizedFloorScope = FloorScope.FINISHING,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = true,
        canViewMonthlyDhu = true,
        canViewMonthlyOql = true,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = designation == EmployeeDesignation.FINISHING_MANAGER,
        canProductionWrite = true,
        canFinishingWrite = true,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = designation == EmployeeDesignation.FINISHING_MANAGER,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.MAINTENANCE,
      EmployeeDesignation.MECHANIC -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.MECHANIC,
        floor = cleanPhysical,
        authorizedFloorScope = authScope,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = false,
        canViewMonthlyDhu = false,
        canViewMonthlyOql = false,
        canViewLineDeepDive = true,
        canViewAuditLogs = false,
        canExportReports = false,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = true,
        canMorningTargetWrite = false,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
      EmployeeDesignation.OTHER -> base.copy(
        designation = designation.displayName,
        department = designation.department,
        role = UserRole.MANAGEMENT_VIEW,
        floor = cleanPhysical,
        authorizedFloorScope = cleanPhysical,
        isAdmin = false,
        canViewDashboard = true,
        canViewTvDashboard = true,
        canViewMonthlyEfficiency = false,
        canViewMonthlyDhu = false,
        canViewMonthlyOql = false,
        canViewLineDeepDive = false,
        canViewAuditLogs = false,
        canExportReports = false,
        canProductionWrite = false,
        canFinishingWrite = false,
        canQualityWrite = false,
        canOqlWrite = false,
        canNptWrite = false,
        canMorningTargetWrite = false,
        canMonthlyTargetWrite = false,
        canTargetRevisionWrite = false
      )
    }
  }
}

enum class AppFunctionCategory(val displayName: String, val banglaName: String) {
  DATA_ENTRY("Data Entry Functions", "Data Entry Functions"),
  VISUAL_REPORT("Visual & Reporting Functions", "Visual & Reporting Functions")
}

data class AppFeaturePermission(
  val key: String,
  val englishName: String,
  val banglaName: String,
  val category: AppFunctionCategory,
  val description: String
)

const val INITIAL_ADMIN_EMPLOYEE_ID = "1015198"
const val PRIMARY_ADMIN_GMAIL = "shamim.sustipe15@gmail.com"
const val MAX_APPROVED_DEVICES_PER_USER = 1

enum class AccountStatus(val displayName: String) {
  APPROVED("Approved"),
  PENDING_APPROVAL("Pending Approval"),
  REJECTED("Rejected"),
  SUSPENDED("Suspended")
}

data class ApprovedDevice(
  val deviceId: String,
  val deviceName: String,
  val approvedAtMillis: Long = System.currentTimeMillis(),
  val approvedBy: String = INITIAL_ADMIN_EMPLOYEE_ID,
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
  val designation: String = "",
  val department: String = "",
  val requestedFloor: String = "Sew-1",
  val deviceId: String = "",
  val deviceName: String = "",
  val previousDeviceId: String = "",
  val previousDeviceName: String = "",
  val requestType: String = "DEVICE_REPLACEMENT",
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
) {
  val id: String
    get() = requestId
}

enum class AuthStatus {
  SUCCESS,
  INVALID_CREDENTIALS,
  ACCOUNT_SUSPENDED,
  ACCOUNT_REJECTED,
  PENDING_ADMIN_APPROVAL,
  ACCOUNT_PENDING_APPROVAL,
  DEVICE_APPROVAL_REQUIRED,
  DEVICE_LIMIT_REACHED,
  ACTIVE_ON_ANOTHER_DEVICE,
  PASSWORD_CHANGE_REQUIRED
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
  val password: String = "",
  val passwordHash: String = "",
  val passwordSalt: String = "",
  val designation: String = "Production Supervisor",
  val department: String = "Production",
  val requestedDesignation: String = designation,
  val requestedFloor: FloorScope = FloorScope.SEW_2,
  val role: UserRole = UserRole.SUPERVISOR,
  val floor: FloorScope = FloorScope.SEW_2,
  val authorizedFloorScope: FloorScope = floor,
  val assignedLine: String = "K1",
  val isAdmin: Boolean = false,
  val mustChangePassword: Boolean = false,
  val activeSessionId: String = "",
  val authorizedDeviceId: String = "",
  val authorizedDeviceName: String = "",
  val rejectionReason: String = "",
  val createdAtMillis: Long = System.currentTimeMillis(),
  val updatedAtMillis: Long = System.currentTimeMillis(),
  val approvedBy: String = "",
  val approvedAtMillis: Long = 0L,
  val firebaseUid: String = "",
  val googleEmail: String = "",
  val fcmToken: String = "",
  val fcmDeviceId: String = "",
  val fcmUpdatedAtMillis: Long = 0L,

  // 1. VISUAL / VIEW PERMISSIONS
  val canViewDashboard: Boolean = true,
  val canViewTvDashboard: Boolean = true,
  val canViewMonthlyEfficiency: Boolean = true,
  val canViewMonthlyDhu: Boolean = true,
  val canViewMonthlyOql: Boolean = true,
  val canViewLineDeepDive: Boolean = true,
  val canViewAuditLogs: Boolean = true,
  val canExportReports: Boolean = true,

  // 2. DATA ENTRY PERMISSIONS
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

  // 3. SECURITY, ACCOUNT STATUS & 1-DEVICE LIMIT
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

  val canAccessAllFloors: Boolean
    get() = isAdmin || authorizedFloorScope == FloorScope.ALL || floor == FloorScope.ALL

  fun canAccessFloor(targetFloor: FloorScope): Boolean {
    if (canAccessAllFloors) return true
    if (targetFloor == FloorScope.ALL) return false
    return authorizedFloorScope == targetFloor || floor == targetFloor
  }

  fun canAccessFloorName(floorName: String): Boolean {
    if (canAccessAllFloors) return true
    val normalized = floorName.trim().replace("-", " ")
    val authNormalized = authorizedFloorScope.apiValue.trim().replace("-", " ")
    val physNormalized = floor.apiValue.trim().replace("-", " ")
    return normalized.equals(authNormalized, ignoreCase = true) ||
        normalized.equals(physNormalized, ignoreCase = true)
  }
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
  /** Total people assigned to the line, independent of the efficiency calculation toggle. */
  val totalManpower: Int
    get() = operatorsCount + helpersCount + finishingStaffCount

  /** People counted for available production minutes when calculating efficiency. */
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

enum class AlertSeverity(val displayName: String, val levelNumber: Int) {
  CRITICAL("Critical", 1),
  HIGH("High", 2),
  MEDIUM("Medium", 3),
  LOW_INFO("Low / Info", 4)
}

enum class IncidentStatus(val displayName: String) {
  OPEN("Reported / Open"),
  REPORTED("Reported / Open"),
  ACKNOWLEDGED("Acknowledged"),
  IN_PROGRESS("In Progress"),
  RESOLVED("Resolved"),
  VERIFIED("Verified")
}

data class IncidentComment(
  val id: String = UUID.randomUUID().toString(),
  val incidentId: String = "",
  val userName: String = "User",
  val userRole: String = "",
  val commentText: String = "",
  val timestamp: Long = System.currentTimeMillis()
)

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
  val machineId: String = "",
  val description: String,
  val reportedCause: String = "",
  val impactLevel: String = "HIGH",
  val severity: AlertSeverity = AlertSeverity.HIGH,
  val affectedManpower: Int = 6,
  val startTimeMillis: Long = System.currentTimeMillis(),
  val acknowledgedTimeMillis: Long? = null,
  val acknowledgedBy: String? = null,
  val mechanicStartTimeMillis: Long? = null,
  val mechanicName: String? = null,
  val isMaintenanceStarted: Boolean = false,
  val inProgressTimeMillis: Long? = null,
  val endTimeMillis: Long? = null,
  val resolvedBy: String? = null,
  val resolutionNote: String = "",
  val verifiedBy: String? = null,
  val verifiedTimeMillis: Long? = null,
  val correctiveAction: String = "",
  val status: IncidentStatus = IncidentStatus.OPEN,
  val isMutedOnCurrentDevice: Boolean = false,
  val isSilencedOnDevice: Boolean = false,
  val reportedBy: String = "Supervisor",
  val isEscalated: Boolean = false,
  val escalationReason: String? = null,
  val escalatedAtMillis: Long? = null,
  val measuredQualityValue: Double? = null,
  val qualityThresholdValue: Double? = null,
  val defectDetails: String = "",
  val isReadOnCurrentDevice: Boolean = false,
  val comments: List<IncidentComment> = emptyList(),
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
  val lastUpdatedMillis: Long = System.currentTimeMillis(),
  // Blank lineId identifies the separately entered floor-level record.
  val lineId: String = "",
  val monthlyTargetPcs: Double = 0.0,
  val monthlyAchievementPcs: Double = 0.0
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
  val percentage: Double,
  val cumulativePercentage: Double = 0.0
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
  val helpersCount: Int = 32,
  val finishingStaffCount: Int = 54,
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
) {
  val totalManpower: Int
    get() = operatorsCount + helpersCount + finishingStaffCount
}

data class TvDashboardData(
  val floorScope: FloorScope = FloorScope.SEW_2,
  val periodScope: PeriodScope = PeriodScope.TODAY,
  val kpis: FloorKpiSummary = FloorKpiSummary(),
  val linePerformance: List<LinePerformancePoint> = emptyList(),
  val topDefects: List<DefectSummary> = emptyList(),
  val highestDhuLines: List<LineDhuSummary> = emptyList(),
  val lowestEfficiencyLines: List<LinePerformancePoint> = emptyList(),
  val paretoDefects: List<ParetoDefectPoint> = emptyList(),
  val weeklyQaTrend: List<DailyQaTrend> = emptyList(),
  val nptBreakdown: List<NptCategorySlice> = emptyList(),
  val activeIncidents: List<IncidentItem> = emptyList(),
  val lastDataUpdate: String = "10:24 AM",
  val lastRefreshTime: String = "18:49",
  val isDemoMode: Boolean = true
)

enum class SaveUiState {
  Idle,
  Saving,
  SavedLocally,
  Synced,
  SyncFailed
}

data class BatchSubmitSummary(
  val savedCount: Int,
  val skippedCount: Int
)

fun formatSaveFeedbackMessage(
  state: SaveUiState,
  batchSummary: BatchSubmitSummary? = null
): String? {
  val suffix = if (batchSummary != null) {
    " (${batchSummary.savedCount} saved, ${batchSummary.skippedCount} skipped)"
  } else {
    ""
  }
  return when (state) {
    SaveUiState.Idle -> null
    SaveUiState.Saving -> "Saving..."
    SaveUiState.SavedLocally -> "Saved on this phone$suffix, syncing..."
    SaveUiState.Synced -> "✓ Synced to cloud$suffix"
    SaveUiState.SyncFailed -> "⚠ Saved on this phone, will retry$suffix"
  }
}

fun findImplausibleProductionTarget(outputQty: Int, targetHourlyPcs: Int): Int? {
  if (targetHourlyPcs <= 0) return null
  return if (outputQty > targetHourlyPcs * 3) targetHourlyPcs else null
}

data class AdminApprovalDeepLink(
  val requestId: String,
  val requestType: String, // "EMPLOYEE_REGISTRATION" or "TV_DEVICE_APPROVAL"
  val targetUserId: String,
  val timestampMillis: Long = System.currentTimeMillis()
)

data class AdminNotificationPayload(
  val notificationId: String = UUID.randomUUID().toString(),
  val requestId: String,
  val requestType: String, // "EMPLOYEE_REGISTRATION" or "TV_DEVICE_APPROVAL"
  val targetUserId: String,
  val title: String,
  val summary: String,
  val requestedRoleOrDevice: String = "",
  val requestedFloor: String = "",
  val status: String = "PENDING",
  val createdByUid: String = "",
  val createdAtMillis: Long = System.currentTimeMillis()
)


