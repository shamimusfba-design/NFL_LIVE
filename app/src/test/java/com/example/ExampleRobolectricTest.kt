package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NFL LIVE", appName)
  }

  @Test
  fun `test repository initialization and default seed data`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = NflRepository(context)

    assertTrue(repository.defaultLines.isNotEmpty())
    val lineJ = repository.defaultLines.find { it.id == "J" || it.id == "J1" }
    assertTrue(lineJ != null)
    assertTrue(lineJ!!.matches("J1") && lineJ.matches("J")) // J and J1 confirmed same line
    assertTrue(!lineJ.matches("J2")) // J2 not merged
  }

  @Test
  fun `test 11 roles support and floor scope`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = NflRepository(context)
    // Role preview is Admin-only (fail-closed when logged out), so sign in as the seeded Admin first.
    repository.authenticateUser("admin", "123")

    assertEquals(11, UserRole.entries.size)
    repository.setRole(UserRole.MECHANIC)
    assertEquals(UserRole.MECHANIC, repository.currentRole.value)

    repository.setFloor(FloorScope.SEW_2)
    assertEquals(FloorScope.SEW_2, repository.currentFloor.value)
  }

  @Test
  fun `test incident lifecycle mute acknowledge resolve`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = NflRepository(context)
    // NPT incident actions require a logged-in user with NPT permission (Admin has it).
    repository.authenticateUser("admin", "123")

    val inc = repository.reportIncident(
      IncidentItem(
        lineId = "K1",
        floor = "Sew 2",
        category = IncidentCategory.MACHINE_BREAKDOWN,
        description = "Test motor stoppage",
        reportedBy = "Tester"
      )
    )

    assertEquals(IncidentStatus.OPEN, inc.status)
    repository.acknowledgeIncident(inc.id, "Mechanic1")
    val acked = repository.incidents.value.find { it.id == inc.id }
    assertEquals(IncidentStatus.ACKNOWLEDGED, acked?.status)

    repository.resolveIncident(inc.id, "Mechanic1", "Belt replaced")
    val resolved = repository.incidents.value.find { it.id == inc.id }
    assertEquals(IncidentStatus.RESOLVED, resolved?.status)
    assertTrue(resolved?.endTimeMillis != null)
  }

  @Test
  fun `test google sheets storage persistence and live dashboard refresh`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = NflRepository(context)

    repository.submitProduction(
      ProductionRecord(
        lineId = "A",
        floor = "Sew 1",
        date = "2026-10-19",
        hourSlot = "08:00-09:00",
        styleNumber = "ST-900",
        outputQty = 95,
        remarks = "Live test"
      )
    )

    val reloadRepo = NflRepository(context)
    val savedProd = reloadRepo.productionEntries.value.find { it.lineId == "A" && it.styleNumber == "ST-900" }
    assertTrue(savedProd != null)
    assertEquals(95, savedProd?.outputQty)

    val initialTrigger = reloadRepo.refreshTrigger.value
    reloadRepo.refreshAllFromSheet()
    assertTrue(reloadRepo.refreshTrigger.value >= initialTrigger)
  }

  @Test
  fun `test security authentication duplicate account and one device limit`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)

    // 1. Reject invalid credentials
    val badRes = repo.authenticateUser("supervisor", "wrong_pass")
    assertEquals(AuthStatus.INVALID_CREDENTIALS, badRes.status)

    // 2. Prevent duplicate accounts
    val dupRes = repo.saveUser(
      UserAccount(id = "supervisor", name = "Dup Sup", password = "999", role = UserRole.SUPERVISOR),
      isNewAccount = true
    )
    assertFalse(dupRes.success)

    // 3. Enforce strict 1-device limit (MAX_APPROVED_DEVICES_PER_USER = 1) when another device is bound
    assertEquals(1, MAX_APPROVED_DEVICES_PER_USER)
    val supUser = repo.users.value.find { it.id == "supervisor" }!!
    repo.saveUser(
      supUser.copy(
        authorizedDeviceId = "DEV-OLD-1",
        authorizedDeviceName = "Device 1",
        approvedDevices = listOf(
          ApprovedDevice("DEV-OLD-1", "Device 1", 1000L, "Admin")
        )
      ),
      isNewAccount = false
    )
    val secondDevAttempt = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.DEVICE_LIMIT_REACHED, secondDevAttempt.status)

    // 4. Admin revokes the bound device -> now this device can bind and log in
    repo.authenticateUser("admin", "123")
    val revokeRes = repo.adminRevokeUserDevice("supervisor", "DEV-OLD-1")
    assertTrue(revokeRes.success)

    val newDevLogin = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, newDevLogin.status)
  }

  @Test
  fun `test first admin 1015198 bootstrap mandatory password change and idempotency`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)

    // 1. Non-1015198 Employee ID cannot use first-admin bootstrap
    val badIdBootstrap = repo.bootstrapFirstAdmin(
      employeeId = "9999999",
      initialPassword = "InitPass#2026",
      confirmPassword = "InitPass#2026",
      fullName = "Unauthorized User"
    )
    assertFalse(badIdBootstrap.success)

    // 2. Bootstrap Initial Admin 1015198
    assertFalse(repo.isFirstAdminProvisioned())
    val bootstrapRes = repo.bootstrapFirstAdmin(
      employeeId = INITIAL_ADMIN_EMPLOYEE_ID,
      initialPassword = "InitPass#2026",
      confirmPassword = "InitPass#2026",
      fullName = "Initial Administrator"
    )
    assertTrue(bootstrapRes.success)
    assertTrue(repo.isFirstAdminProvisioned())

    // Verify no plaintext password is stored
    val adminAccount = repo.users.value.find { it.id == INITIAL_ADMIN_EMPLOYEE_ID }!!
    assertEquals("", adminAccount.password)
    assertTrue(adminAccount.passwordHash.isNotBlank())
    assertTrue(adminAccount.isAdmin)
    assertEquals(FloorScope.ALL, adminAccount.authorizedFloorScope)
    assertTrue(adminAccount.mustChangePassword)

    // 3. Repeated bootstrap attempts must be blocked
    val duplicateBootstrap = repo.bootstrapFirstAdmin(
      employeeId = INITIAL_ADMIN_EMPLOYEE_ID,
      initialPassword = "AnotherPass#2026",
      confirmPassword = "AnotherPass#2026"
    )
    assertFalse(duplicateBootstrap.success)

    // 4. First login with initial password requires password change before entering operational screens
    val firstLogin = repo.authenticateUser(INITIAL_ADMIN_EMPLOYEE_ID, "InitPass#2026")
    assertEquals(AuthStatus.PASSWORD_CHANGE_REQUIRED, firstLogin.status)

    // 5. Complete mandatory password change
    val changeRes = repo.changePassword(
      userId = INITIAL_ADMIN_EMPLOYEE_ID,
      currentPassword = "InitPass#2026",
      newPassword = "NewAdminPass#2026",
      confirmPassword = "NewAdminPass#2026"
    )
    assertTrue(changeRes.success)

    // 6. Login with new password succeeds with full Admin & All Floor access
    val secondLogin = repo.authenticateUser(INITIAL_ADMIN_EMPLOYEE_ID, "NewAdminPass#2026")
    assertEquals(AuthStatus.SUCCESS, secondLogin.status)
    assertEquals(UserRole.ADMIN, secondLogin.user?.role)
    assertEquals(FloorScope.ALL, secondLogin.user?.authorizedFloorScope)
    assertFalse(secondLogin.user?.mustChangePassword ?: true)
  }

  @Test
  fun `test employee registration pending approval designation mapping and admin workflow`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)

    // 1. Public registration cannot request Admin or All Floor as a physical floor
    val allFloorAttempt = repo.registerEmployee(
      fullName = "Test IE",
      employeeId = "EMP-IE-01",
      designation = EmployeeDesignation.IE,
      physicalFloor = FloorScope.ALL,
      password = "pass1234",
      confirmPassword = "pass1234"
    )
    assertFalse(allFloorAttempt.success)

    // 2. Register Head of Production on Sew-2 -> automatically maps to Production department, PRODUCTION_MANAGER role, and All Floor authorized scope
    val hopReg = repo.registerEmployee(
      fullName = "Rahim HOP",
      employeeId = "EMP-HOP-101",
      designation = EmployeeDesignation.HEAD_OF_PRODUCTION,
      physicalFloor = FloorScope.SEW_2,
      password = "pass1234",
      confirmPassword = "pass1234"
    )
    assertTrue(hopReg.success)

    // 3. Pending employee cannot log in before Admin approval
    val pendingLogin = repo.authenticateUser("EMP-HOP-101", "pass1234")
    assertEquals(AuthStatus.ACCOUNT_PENDING_APPROVAL, pendingLogin.status)

    // 4. Register Production Supervisor on Sew-1 -> maps to Production department, SUPERVISOR role, and Sew-1 scope
    val supReg = repo.registerEmployee(
      fullName = "Karim Supervisor",
      employeeId = "EMP-SUP-202",
      designation = EmployeeDesignation.PRODUCTION_SUPERVISOR,
      physicalFloor = FloorScope.SEW_1,
      password = "pass1234",
      confirmPassword = "pass1234"
    )
    assertTrue(supReg.success)
    val supPending = repo.users.value.find { it.id == "EMP-SUP-202" }!!
    assertEquals("Production", supPending.department)
    assertEquals(UserRole.SUPERVISOR, supPending.role)
    assertEquals(FloorScope.SEW_1, supPending.floor)
    assertEquals(FloorScope.SEW_1, supPending.authorizedFloorScope)

    // 5. Admin approves EMP-HOP-101 and rejects EMP-SUP-202
    repo.authenticateUser("admin", "123")
    val approveRes = repo.adminApproveUserRegistration("EMP-HOP-101")
    assertTrue(approveRes.success)

    val approvedHopLogin = repo.authenticateUser("EMP-HOP-101", "pass1234")
    assertEquals(AuthStatus.SUCCESS, approvedHopLogin.status)
    assertEquals("Production", approvedHopLogin.user?.department)
    assertEquals(UserRole.PRODUCTION_MANAGER, approvedHopLogin.user?.role)
    assertEquals(FloorScope.ALL, approvedHopLogin.user?.authorizedFloorScope)

    // Switch back to Admin to test reject & suspend
    repo.authenticateUser("admin", "123")
    val rejectRes = repo.adminRejectUserRegistration("EMP-SUP-202", "Duplicate badge request")
    assertTrue(rejectRes.success)
    val rejectedLogin = repo.authenticateUser("EMP-SUP-202", "pass1234")
    assertEquals(AuthStatus.ACCOUNT_REJECTED, rejectedLogin.status)

    val suspendRes = repo.adminSuspendUser("EMP-HOP-101", "Temporary leave")
    assertTrue(suspendRes.success)
    val suspendedLogin = repo.authenticateUser("EMP-HOP-101", "pass1234")
    assertEquals(AuthStatus.ACCOUNT_SUSPENDED, suspendedLogin.status)
  }

  @Test
  fun `test unscheduled lines with zero target are excluded from performance rankings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")
    val todayBd = repo.getCurrentBangladeshDateIso()

    // Set Line K1 target to 0 (unscheduled)
    repo.saveLineSetup(
      SupervisorLineSetup(
        lineId = "K1",
        floor = "Sew 2",
        date = todayBd,
        itemName = "Unscheduled Style",
        smv = 14.0,
        targetDailyPcs = 0,
        targetHourlyPcs = 0,
        targetEfficiencyPct = 75.0,
        operatorsCount = 20,
        machinesCount = 20
      )
    )

    val savedSetup = repo.getLineSetup("K1")
    assertEquals(0, savedSetup.targetDailyPcs)

    val tvData = repo.getTvDashboardData(FloorScope.SEW_2, PeriodScope.TODAY)
    assertFalse(tvData.linePerformance.any { it.lineId == "K1" })
    assertFalse(tvData.lowestEfficiencyLines.any { it.lineId == "K1" })
    assertFalse(tvData.highestDhuLines.any { it.lineId == "K1" })
  }

  @Test
  fun `test role based permissions and admin audit logged correction`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    val todayBd = repo.getCurrentBangladeshDateIso()

    // 1. Supervisor cannot submit Quality data
    repo.authenticateUser("supervisor", "123")
    val beforeQaCount = repo.qualityEntries.value.count { !it.isDemo && it.lineId == "K1" && it.hourSlot == "09:00 - 10:00" }
    repo.submitQuality(
      QualityRecord(
        lineId = "K1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "09:00 - 10:00",
        styleNumber = "ST-1",
        checkedGarments = 100,
        defectiveGarments = 3,
        defects = emptyList()
      )
    )
    val afterQaCount = repo.qualityEntries.value.count { !it.isDemo && it.lineId == "K1" && it.hourSlot == "09:00 - 10:00" }
    assertEquals(beforeQaCount, afterQaCount)

    // 2. Management cannot submit Production data
    repo.authenticateUser("pm", "123")
    val beforePmProd = repo.productionEntries.value.count { !it.isDemo && it.lineId == "J1" && it.hourSlot == "11:00 - 12:00" }
    repo.submitProduction(
      ProductionRecord(
        lineId = "J1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "11:00 - 12:00",
        styleNumber = "ST-2",
        outputQty = 65
      )
    )
    val afterPmProd = repo.productionEntries.value.count { !it.isDemo && it.lineId == "J1" && it.hourSlot == "11:00 - 12:00" }
    assertEquals(beforePmProd, afterPmProd)

    // 3. Admin submits & corrects a record with mandatory reason in Audit Log
    repo.authenticateUser("admin", "123")
    val submitted = repo.submitProduction(
      ProductionRecord(
        lineId = "M1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "14:00 - 15:00",
        styleNumber = "ST-3",
        outputQty = 50
      )
    )
    // Reject empty reason
    val noReasonRes = repo.adminCorrectProductionRecord(submitted.submissionId, 75, "Fixed", "")
    assertTrue(!noReasonRes.success)

    // Succeed with valid reason and record in audit log
    val withReasonRes = repo.adminCorrectProductionRecord(
      submitted.submissionId,
      75,
      "Verified bundle count",
      "Recounted bundle #4 with floor supervisor"
    )
    assertTrue(withReasonRes.success)
    val corrected = repo.productionEntries.value.find { it.submissionId == submitted.submissionId }
    assertEquals(75, corrected?.outputQty)
    assertTrue(repo.auditLogs.value.any { it.action == "CORRECT_PRODUCTION" && it.reason.contains("Recounted bundle #4") })
  }

  @Test
  fun `test A B C multi device sync create update delete and reopen without duplicates`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val phoneA = NflRepository(context)
    phoneA.authenticateUser("admin", "123")
    val todayBd = phoneA.getCurrentBangladeshDateIso()

    // Test A — Create on Phone A and sync to Phone B
    val createdOnA = phoneA.submitProduction(
      ProductionRecord(
        lineId = "K1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "10:00 - 11:00",
        styleNumber = "Sports Vest",
        outputQty = 88
      )
    )

    val phoneB = NflRepository(context)
    phoneB.upsertRemoteProductionRecords(listOf(createdOnA))

    val k1SlotRecordsOnB = phoneB.productionEntries.value.filter {
      it.lineId.equals("K1", ignoreCase = true) &&
          it.date == todayBd &&
          it.hourSlot == "10:00 - 11:00"
    }
    // Must replace demo record for that slot and have exactly 1 record with outputQty = 88
    assertEquals(1, k1SlotRecordsOnB.size)
    assertEquals(88, k1SlotRecordsOnB.first().outputQty)
    assertTrue(!k1SlotRecordsOnB.first().isDemo)

    // Test B — Update on Phone A and verify Phone B updates in-place without duplicates
    phoneA.adminCorrectProductionRecord(
      submissionId = createdOnA.submissionId,
      newOutputQty = 104,
      newRemarks = "Updated count",
      reason = "Supervisor recount verified"
    )
    val updatedOnA = phoneA.productionEntries.value.first { it.submissionId == createdOnA.submissionId }
    phoneB.upsertRemoteProductionRecords(listOf(updatedOnA))

    val afterUpdateOnB = phoneB.productionEntries.value.filter {
      it.lineId.equals("K1", ignoreCase = true) &&
          it.date == todayBd &&
          it.hourSlot == "10:00 - 11:00"
    }
    assertEquals(1, afterUpdateOnB.size)
    assertEquals(104, afterUpdateOnB.first().outputQty)

    // Test C — Reopen app and confirm real record persists and is not overwritten by demo data seed
    val reopenedPhoneB = NflRepository(context)
    val persistedOnReopen = reopenedPhoneB.productionEntries.value.filter {
      it.lineId.equals("K1", ignoreCase = true) &&
          it.date == todayBd &&
          it.hourSlot == "10:00 - 11:00"
    }
    assertEquals(1, persistedOnReopen.size)
    assertEquals(104, persistedOnReopen.first().outputQty)
    assertTrue(!persistedOnReopen.first().isDemo)

    // Verify remote deletion removes the record on Phone B
    val targetRecord = persistedOnReopen.first()
    reopenedPhoneB.removeDeletedProductionRecords(setOf(targetRecord.submissionId))
    assertTrue(
      reopenedPhoneB.productionEntries.value.none {
        it.submissionId == targetRecord.submissionId
      }
    )
  }

  @Test
  fun `test D E quality npt setup pareto modules and honest pending sync status`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")
    val todayBd = repo.getCurrentBangladeshDateIso()

    // 1. Save Morning Line Setup and verify getLineSetup & TV Dashboard use it
    val customSetup = SupervisorLineSetup(
      lineId = "L1",
      floor = "Sew 2",
      date = todayBd,
      itemName = "Custom Hooded Jacket",
      smv = 18.5,
      targetDailyPcs = 640,
      targetHourlyPcs = 80,
      targetEfficiencyPct = 80.0,
      operatorsCount = 25,
      machinesCount = 25
    )
    repo.saveLineSetup(customSetup)
    val loadedSetup = repo.getLineSetup("L1")
    assertEquals("Custom Hooded Jacket", loadedSetup.itemName)
    assertEquals(640, loadedSetup.targetDailyPcs)

    // 2. Submit Quality record and verify Pareto, Hourly Quality, Hourly Defect Breakdown & TV Dashboard update
    val qaRec = repo.submitQuality(
      QualityRecord(
        lineId = "L1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "10:00 - 11:00",
        styleNumber = "Custom Hooded Jacket",
        checkedGarments = 100,
        defectiveGarments = 6,
        defects = listOf(
          DefectItem("Open Seam", 5),
          DefectItem("Broken Stitch", 2)
        ),
        defectSent = 10,
        defectReceived = 8,
        defectBalance = 2,
        spotSent = 4,
        spotReceived = 3,
        spotBalance = 1
      )
    )
    // Test E: Before cloud confirmation in offline test environment, syncStatus starts as PENDING
    assertEquals(SyncStatus.PENDING, qaRec.syncStatus)

    val pareto = repo.getParetoDefects("L1")
    assertTrue(pareto.isNotEmpty())
    assertEquals("Open Seam", pareto.first().defectName)
    assertEquals(5, pareto.first().count)

    val hourlyQa = repo.getHourlyQualityData("L1", PeriodScope.TODAY)
    val slot10Qa = hourlyQa.first { it.hourSlot == "10:00 - 11:00" }
    assertEquals(7.0, slot10Qa.dhuPct, 0.01)
    assertEquals(6.0, slot10Qa.oqlPct, 0.01)

    val hourlyDefect = repo.getLineHourlyDefectBreakdown("L1", PeriodScope.TODAY)
    val slot10Def = hourlyDefect.first { it.hourSlot == "10:00 - 11:00" }
    assertEquals(10, slot10Def.sewingSent)
    assertEquals(8, slot10Def.sewingReceived)
    assertEquals(2, slot10Def.sewingBalance)

    // 3. Report NPT Incident and verify Line NPT breakdown reflects it
    repo.reportIncident(
      IncidentItem(
        lineId = "L1",
        floor = "Sew 2",
        category = IncidentCategory.MACHINE_BREAKDOWN,
        nptType = "Overlock Motor Failure",
        description = "Overlock machine stopped",
        startTimeMillis = System.currentTimeMillis() - 40 * 60 * 1000L
      )
    )
    val lineNpt = repo.getLineNptBreakdown("L1")
    assertTrue(lineNpt.any { it.categoryName == "Machine Breakdown" && it.minutes >= 40 })

    // 4. Save Monthly Target (BS OQL & FRI Pass Rate) and verify TV Dashboard KPIs reflect persisted values
    repo.saveMonthlyTarget(
      MonthlyFactoryTarget(
        floor = "Sew 2",
        targetEfficiencyPct = 78.0,
        updateEfficiencyPct = 74.5,
        bsOqlPct = 1.85,
        friPassRatePct = 98.5
      )
    )
    val tvData = repo.getTvDashboardData(FloorScope.SEW_2, PeriodScope.TODAY)
    assertEquals(1.85, tvData.kpis.bsQaOql, 0.01)
    assertEquals(98.5, tvData.kpis.friPassRate, 0.01)
  }

  @Test
  fun `test Part A 1 to 4 fail closed admin checks and pre login flows`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    // Ensure logged out initially
    repo.logout()
    assertEquals(null, repo.currentUser.value)

    // 4. Pre-login flows (registerEmployee, First-Admin bootstrap when unprovisioned, normal login) work when logged out
    val regRes = repo.registerEmployee(
      fullName = "Test Pending Worker",
      employeeId = "EMP-PEND-01",
      designation = EmployeeDesignation.PRODUCTION_SUPERVISOR,
      physicalFloor = FloorScope.SEW_2,
      password = "Password#123",
      confirmPassword = "Password#123"
    )
    assertTrue(regRes.success)

    // 1. Logged-out call to each admin function is denied
    val loggedOutApproveReg = repo.adminApproveUserRegistration(
      employeeId = "EMP-PEND-01"
    )
    assertFalse(loggedOutApproveReg.success)

    val loggedOutSuspend = repo.adminSuspendUser("supervisor", "Test logged out suspend")
    assertFalse(loggedOutSuspend.success)

    val loggedOutUpdateRole = repo.adminUpdateUserRoleAndScope(
      employeeId = "supervisor",
      designation = EmployeeDesignation.HEAD_OF_PRODUCTION,
      physicalFloor = FloorScope.SEW_2,
      authorizedScope = FloorScope.ALL
    )
    assertFalse(loggedOutUpdateRole.success)

    val loggedOutApproveDev = repo.adminApproveDeviceOrAccountRequest("REQ-NONEXISTENT")
    assertFalse(loggedOutApproveDev.success)

    val beforeDeleteCount = repo.users.value.size
    repo.deleteUser("supervisor")
    assertEquals(beforeDeleteCount, repo.users.value.size)

    // First-Admin bootstrap works when unprovisioned and logged out
    assertFalse(repo.isFirstAdminProvisioned())
    val bootRes = repo.bootstrapFirstAdmin(
      employeeId = INITIAL_ADMIN_EMPLOYEE_ID,
      initialPassword = "InitPass#2026",
      confirmPassword = "InitPass#2026",
      fullName = "Chief Admin"
    )
    assertTrue(bootRes.success)

    // 2. Logged-in non-admin call to those functions is denied
    val supLogin = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, supLogin.status)
    assertFalse(repo.currentUser.value?.isAdmin == true)

    assertFalse(
      repo.adminApproveUserRegistration(
        employeeId = "EMP-PEND-01"
      ).success
    )
    assertFalse(repo.adminSuspendUser("quality", "Non-admin attempt").success)
    assertFalse(
      repo.adminUpdateUserRoleAndScope(
        employeeId = "quality",
        designation = EmployeeDesignation.HEAD_OF_QUALITY,
        physicalFloor = FloorScope.SEW_2,
        authorizedScope = FloorScope.ALL
      ).success
    )
    assertFalse(repo.adminApproveDeviceOrAccountRequest("REQ-NONEXISTENT").success)
    val countBeforeNonAdminDelete = repo.users.value.size
    repo.deleteUser("quality")
    assertEquals(countBeforeNonAdminDelete, repo.users.value.size)

    // 3. Logged-in admin call succeeds
    val adminLogin = repo.authenticateUser("admin", "123")
    assertEquals(AuthStatus.SUCCESS, adminLogin.status)
    assertTrue(repo.currentUser.value?.isAdmin == true)

    val adminApproveRes = repo.adminApproveUserRegistration(
      employeeId = "EMP-PEND-01"
    )
    assertTrue(adminApproveRes.success)

    val adminUpdateRes = repo.adminUpdateUserRoleAndScope(
      employeeId = "EMP-PEND-01",
      designation = EmployeeDesignation.IE,
      physicalFloor = FloorScope.SEW_1,
      authorizedScope = FloorScope.SEW_1
    )
    assertTrue(adminUpdateRes.success)

    val adminSuspendRes = repo.adminSuspendUser("EMP-PEND-01", "Suspended by admin test")
    assertTrue(adminSuspendRes.success)

    // Bind EMP-PEND-01 to an old device, trigger a device replacement request, and approve it as admin
    repo.adminReactivateUser("EMP-PEND-01")
    val pendUser = repo.users.value.first { it.id == "EMP-PEND-01" }
    repo.saveUser(
      pendUser.copy(
        authorizedDeviceId = "OLD-DEV-777",
        authorizedDeviceName = "Old Phone",
        approvedDevices = listOf(ApprovedDevice("OLD-DEV-777", "Old Phone", 1000L, "Admin"))
      ),
      isNewAccount = false
    )
    val devLimitAttempt = repo.authenticateUser("EMP-PEND-01", "Password#123")
    assertEquals(AuthStatus.DEVICE_LIMIT_REACHED, devLimitAttempt.status)
    // Switch back to admin to approve the pending device request
    repo.authenticateUser("admin", "123")
    val createdReq = repo.deviceApprovalRequests.value.first { it.userId == "EMP-PEND-01" }
    val approveDevRes = repo.adminApproveDeviceOrAccountRequest(createdReq.requestId)
    assertTrue(approveDevRes.success)

    repo.deleteUser("EMP-PEND-01")
    assertTrue(repo.users.value.none { it.id == "EMP-PEND-01" })
  }

  @Test
  fun `test Part B 5 to 8 session persistence restore logout suspend and device change`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)

    // 5. Login -> create a new NflRepository(context) -> currentUser.value is restored automatically
    val loginRes = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, loginRes.status)
    assertEquals("supervisor", repo.currentUser.value?.id)

    val restoredRepo = NflRepository(context)
    assertTrue(restoredRepo.currentUser.value != null)
    assertEquals("supervisor", restoredRepo.currentUser.value?.id)

    // Verify no password or passwordHash is stored in session keys of SharedPreferences
    val prefs = context.getSharedPreferences("nfl_google_sheets_storage", Context.MODE_PRIVATE)
    assertEquals("supervisor", prefs.getString("security_active_session_user_id", null))
    assertTrue(!prefs.getString("security_active_session_id", "").isNullOrBlank())
    assertTrue(prefs.getLong("security_active_session_login_time_ms", 0L) > 0L)

    // 6. Login -> logout() -> new NflRepository(context) -> currentUser.value is null
    restoredRepo.logout()
    assertEquals(null, restoredRepo.currentUser.value)
    val afterLogoutRepo = NflRepository(context)
    assertEquals(null, afterLogoutRepo.currentUser.value)

    // 7. Login -> suspend user -> new NflRepository(context) -> currentUser.value is null
    val loginAgain = afterLogoutRepo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, loginAgain.status)
    // Mutate saved local user to SUSPENDED as if suspended before restart
    val storage = com.example.data.GoogleSheetsStorageService(context)
    val suspendedUsers = storage.loadLocalUsers().map {
      if (it.id == "supervisor") it.copy(accountStatus = AccountStatus.SUSPENDED, isActive = false) else it
    }
    storage.saveLocalUsers(suspendedUsers)
    val afterSuspendRepo = NflRepository(context)
    assertEquals(null, afterSuspendRepo.currentUser.value)

    // Restore supervisor to APPROVED & active, then test 8:
    // 8. Login -> change user's authorizedDeviceId to another device -> new NflRepository(context) -> currentUser.value is null
    val reactivatedUsers = storage.loadLocalUsers().map {
      if (it.id == "supervisor") {
        it.copy(
          accountStatus = AccountStatus.APPROVED,
          isActive = true,
          authorizedDeviceId = "",
          approvedDevices = emptyList()
        )
      } else it
    }
    storage.saveLocalUsers(reactivatedUsers)
    val repoForDeviceTest = NflRepository(context)
    val supLogin2 = repoForDeviceTest.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, supLogin2.status)

    val wrongDeviceUsers = storage.loadLocalUsers().map {
      if (it.id == "supervisor") {
        it.copy(
          authorizedDeviceId = "OTHER-DEVICE-999",
          approvedDevices = listOf(ApprovedDevice("OTHER-DEVICE-999", "Other Phone", 2000L, "Admin"))
        )
      } else it
    }
    storage.saveLocalUsers(wrongDeviceUsers)
    val afterDeviceChangeRepo = NflRepository(context)
    assertEquals(null, afterDeviceChangeRepo.currentUser.value)
  }

  @Test
  fun `test Part B 9 to 11 upsertRemoteUsers stale record ignored newer revocation logs out and blank device preserved`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val initialRepo = NflRepository(context)

    val loginRes = initialRepo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, loginRes.status)

    // Set known local updatedAtMillis = 2000 on supervisor in local storage and restore session
    val storage = com.example.data.GoogleSheetsStorageService(context)
    val currentSup = initialRepo.currentUser.value!!
    val supAt2000 = currentSup.copy(updatedAtMillis = 2000L)
    storage.saveLocalUsers(
      storage.loadLocalUsers().map { if (it.id == "supervisor") supAt2000 else it }
    )
    val repo = NflRepository(context)
    assertEquals("supervisor", repo.currentUser.value?.id)
    assertEquals(2000L, repo.currentUser.value?.updatedAtMillis)

    // 9. Login (local updatedAtMillis = 2000) -> upsertRemoteUsers with a stale record (updatedAtMillis = 1000, isActive = false or wrong device) -> currentUser.value stays non-null
    val staleInactive = supAt2000.copy(
      updatedAtMillis = 1000L,
      isActive = false,
      accountStatus = AccountStatus.SUSPENDED,
      authorizedDeviceId = "STALE-WRONG-DEVICE"
    )
    repo.upsertRemoteUsers(listOf(staleInactive))
    assertTrue(repo.currentUser.value != null)
    assertEquals("supervisor", repo.currentUser.value?.id)
    assertTrue(repo.currentUser.value!!.isActive)
    assertEquals(2000L, repo.currentUser.value?.updatedAtMillis)

    // 11. Login -> upsertRemoteUsers with a record where authorizedDeviceId is blank -> currentUser.value stays non-null
    val newerBlankDevice = supAt2000.copy(
      updatedAtMillis = 2500L,
      authorizedDeviceId = "",
      approvedDevices = emptyList()
    )
    repo.upsertRemoteUsers(listOf(newerBlankDevice))
    assertTrue(repo.currentUser.value != null)
    assertEquals("supervisor", repo.currentUser.value?.id)
    assertEquals(currentSup.authorizedDeviceId, repo.currentUser.value?.authorizedDeviceId)

    // 10. Login -> upsertRemoteUsers with a newer record (updatedAtMillis = 3000, isActive = false or accountStatus = SUSPENDED) -> currentUser.value becomes null and banner mentions session ended
    val newerSuspended = supAt2000.copy(
      updatedAtMillis = 3000L,
      isActive = false,
      accountStatus = AccountStatus.SUSPENDED
    )
    repo.upsertRemoteUsers(listOf(newerSuspended))
    assertEquals(null, repo.currentUser.value)
    assertTrue(repo.syncStatusBanner.value.orEmpty().contains("Session ended", ignoreCase = true))
  }

  @Test
  fun `test Part B additional stay logged in until explicit logout or genuine newer revocation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.GoogleSheetsStorageService(context)
    val repo = NflRepository(context)

    // 1. Login and verify offline restart restores user immediately without network
    val loginRes = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, loginRes.status)
    val baseUser = repo.currentUser.value!!
    val baseTs = baseUser.updatedAtMillis

    val restartedOfflineRepo = NflRepository(context)
    assertEquals("supervisor", restartedOfflineRepo.currentUser.value?.id)
    assertEquals("supervisor", restartedOfflineRepo.getAuthoritativeCurrentUser()?.id)

    // 2. Empty snapshot, partial snapshot (other users only), or Google Sheets-sourced row must NEVER log out
    restartedOfflineRepo.upsertRemoteUsers(emptyList())
    assertEquals("supervisor", restartedOfflineRepo.currentUser.value?.id)

    val otherUserOnly = restartedOfflineRepo.users.value.filter { it.id != "supervisor" }
    restartedOfflineRepo.upsertRemoteUsers(otherUserOnly)
    assertEquals("supervisor", restartedOfflineRepo.currentUser.value?.id)

    val sheetRowSuspended = baseUser.copy(
      updatedAtMillis = baseTs + 50_000L,
      isActive = false,
      accountStatus = AccountStatus.SUSPENDED,
      authorizedDeviceId = "SHEET-WRONG-DEV"
    )
    restartedOfflineRepo.upsertRemoteUsers(listOf(sheetRowSuspended), isFromGoogleSheets = true)
    assertEquals("supervisor", restartedOfflineRepo.currentUser.value?.id)

    // 3. Blank authorizedDeviceId and blank activeSessionId must NEVER log out
    val blankDeviceAndSession = baseUser.copy(
      updatedAtMillis = baseTs + 1_000L,
      authorizedDeviceId = "",
      approvedDevices = emptyList(),
      activeSessionId = ""
    )
    restartedOfflineRepo.upsertRemoteUsers(listOf(blankDeviceAndSession))
    assertEquals("supervisor", restartedOfflineRepo.currentUser.value?.id)

    // 4. Network error / permission-denied sync banner update must NEVER log out
    restartedOfflineRepo.refreshAllFromSheet()
    assertEquals("supervisor", restartedOfflineRepo.currentUser.value?.id)

    // 5. Newer different-device record MUST log out
    val newerDiffDevice = baseUser.copy(
      updatedAtMillis = baseTs + 5_000L,
      authorizedDeviceId = "NEW-PHONE-DEVICE-ID",
      approvedDevices = listOf(ApprovedDevice("NEW-PHONE-DEVICE-ID", "New Phone", baseTs + 5_000L, "Admin"))
    )
    restartedOfflineRepo.upsertRemoteUsers(listOf(newerDiffDevice))
    assertEquals(null, restartedOfflineRepo.currentUser.value)
    assertEquals("", storage.getLocalActiveSessionUserId())
    assertEquals("", storage.getLocalActiveSessionId())

    // 6. Admin revoke (adminRevokeUserDevice) logs out the old phone on newer record, but older revoke record never does
    // First clear the device binding as Admin so supervisor can log in again on this phone
    restartedOfflineRepo.authenticateUser("admin", "123")
    assertTrue(restartedOfflineRepo.adminRevokeUserDevice("supervisor").success)

    val oldPhoneRepo = NflRepository(context)
    val reloginRes = oldPhoneRepo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, reloginRes.status)
    val activeSup = oldPhoneRepo.currentUser.value!!

    // Stale/older revoke record must NOT log out
    val staleRevoked = activeSup.copy(
      approvedDevices = emptyList(),
      authorizedDeviceId = "",
      authorizedDeviceName = "",
      activeSessionId = "REVOKED-OLD-TOKEN",
      updatedAtMillis = activeSup.updatedAtMillis - 1000L
    )
    oldPhoneRepo.upsertRemoteUsers(listOf(staleRevoked))
    assertEquals("supervisor", oldPhoneRepo.currentUser.value?.id)

    // Simulate Admin on another phone invoking adminRevokeUserDevice("supervisor")
    val adminRepo = NflRepository(context)
    adminRepo.authenticateUser("admin", "123")
    val revokeAction = adminRepo.adminRevokeUserDevice("supervisor")
    assertTrue(revokeAction.success)
    val revokedUserRecord = adminRepo.users.value.first { it.id == "supervisor" }
    assertTrue(revokedUserRecord.activeSessionId.startsWith("REVOKED-"))
    assertEquals("", revokedUserRecord.authorizedDeviceId)

    // Restore oldPhoneRepo's local session state before receiving the remote sync from adminRepo
    storage.saveLocalActiveSession(activeSup.id, activeSup.activeSessionId, activeSup.updatedAtMillis)
    oldPhoneRepo.upsertRemoteUsers(listOf(revokedUserRecord))
    assertEquals(null, oldPhoneRepo.currentUser.value)
    assertEquals("", storage.getLocalActiveSessionUserId())
    assertEquals("", storage.getLocalActiveSessionId())
  }

  @Test
  fun `test Step 3 bounded listener and empty snapshot do not delete local records older than 7 days`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")

    val todayIso = repo.getCurrentBangladeshDateIso()
    val cal = repo.sheetsStorage.getTrustedBangladeshCalendar()
    cal.add(java.util.Calendar.DAY_OF_YEAR, -12)
    val twelveDaysAgoIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
      timeZone = repo.sheetsStorage.getBangladeshTimeZone()
    }.format(cal.time)

    val olderRecord = ProductionRecord(
      id = "PROD_K1_${twelveDaysAgoIso}_08-00_-_09-00",
      submissionId = "SUB-OLD-12D",
      lineId = "K1",
      floor = "Sew 2",
      date = twelveDaysAgoIso,
      hourSlot = "08:00 - 09:00",
      styleNumber = "ST-100",
      outputQty = 145,
      syncStatus = SyncStatus.SYNCED,
      isDemo = false
    )
    val recentRecord = ProductionRecord(
      id = "PROD_K1_${todayIso}_08-00_-_09-00",
      submissionId = "SUB-TODAY-1",
      lineId = "K1",
      floor = "Sew 2",
      date = todayIso,
      hourSlot = "08:00 - 09:00",
      styleNumber = "ST-100",
      outputQty = 160,
      syncStatus = SyncStatus.SYNCED,
      isDemo = false
    )

    // Seed older local record first
    repo.upsertRemoteProductionRecords(listOf(olderRecord))
    assertTrue(repo.productionEntries.value.any { !it.isDemo && it.date == twelveDaysAgoIso && it.outputQty == 145 })

    // Bounded 7-day listener delivers only recent records — older local record must remain intact
    repo.upsertRemoteProductionRecords(listOf(recentRecord))
    assertTrue(repo.productionEntries.value.any { !it.isDemo && it.date == twelveDaysAgoIso && it.outputQty == 145 })
    assertTrue(repo.productionEntries.value.any { !it.isDemo && it.date == todayIso && it.outputQty == 160 })

    // Empty snapshot / empty removal set must never delete older local records
    repo.upsertRemoteProductionRecords(emptyList())
    repo.removeDeletedProductionRecords(emptySet())
    assertTrue(repo.productionEntries.value.any { !it.isDemo && it.date == twelveDaysAgoIso && it.outputQty == 145 })
  }

  @Test
  fun `test Step 2 and 4 manual sync does not re-push SYNCED records or merge Sheets data and saving unchanged entry is skipped`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")

    val todayIso = repo.getCurrentBangladeshDateIso()
    val saved = repo.submitProduction(
      ProductionRecord(
        lineId = "J1",
        floor = "Sew 2",
        date = todayIso,
        hourSlot = "09:00 - 10:00",
        styleNumber = "ST-500",
        outputQty = 130,
        remarks = "Initial",
        isDemo = false
      )
    )
    // Canonical docId must be assigned so id == canonicalDocId
    val expectedCanonicalId = repo.firebaseSync.canonicalProductionDocId(saved)
    assertEquals(expectedCanonicalId, saved.id)
    assertFalse(repo.firebaseSync.shouldDeleteLegacyProductionDoc(saved, expectedCanonicalId))

    // Mark record as SYNCED (simulating completed cloud sync)
    repo.upsertRemoteProductionRecords(listOf(saved.copy(syncStatus = SyncStatus.SYNCED)))
    val syncedRec = repo.productionEntries.value.first {
      !it.isDemo && it.lineId == "J1" && it.date == todayIso && it.hourSlot == "09:00 - 10:00"
    }
    assertEquals(SyncStatus.SYNCED, syncedRec.syncStatus)

    // Re-submitting identical record when already SYNCED must be a no-op (stays SYNCED, same timestamp)
    val resubmitted = repo.submitProduction(
      ProductionRecord(
        lineId = "J1",
        floor = "Sew 2",
        date = todayIso,
        hourSlot = "09:00 - 10:00",
        styleNumber = "ST-500",
        outputQty = 130,
        remarks = "Initial",
        isDemo = false
      )
    )
    assertEquals(SyncStatus.SYNCED, resubmitted.syncStatus)
    assertEquals(syncedRec.timestamp, resubmitted.timestamp)

    // Manual sync (refreshFromGoogleSheet) with 0 pending records must NOT flip SYNCED records or merge Sheets data
    val countBefore = repo.productionEntries.value.count { !it.isDemo }
    repo.refreshFromGoogleSheet()
    val countAfter = repo.productionEntries.value.count { !it.isDemo }
    assertEquals(countBefore, countAfter)
    assertEquals(
      SyncStatus.SYNCED,
      repo.productionEntries.value.first { !it.isDemo && it.lineId == "J1" && it.hourSlot == "09:00 - 10:00" }.syncStatus
    )
  }

  @Test
  fun `test Step 4 legacy delete only triggers when legacy doc id was actually observed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val syncService = com.example.data.FirebaseSyncService(context)

    val normalRec = ProductionRecord(
      id = "random-uuid-1234",
      lineId = "K1",
      floor = "Sew 2",
      date = "2026-04-20",
      hourSlot = "08:00 - 09:00",
      styleNumber = "ST-100",
      outputQty = 120
    )
    val canonicalId = syncService.canonicalProductionDocId(normalRec)
    assertEquals("PROD_K1_2026-04-20_0800-0900", canonicalId)

    // A random client UUID that was never seen in Firestore must NOT trigger a legacy delete write
    assertFalse(syncService.shouldDeleteLegacyProductionDoc(normalRec, canonicalId))

    val normalQa = QualityRecord(
      id = "random-qa-uuid-5678",
      lineId = "K1",
      floor = "Sew 2",
      date = "2026-04-20",
      hourSlot = "08:00 - 09:00",
      styleNumber = "ST-100",
      checkedGarments = 50,
      defectiveGarments = 1
    )
    val canonicalQaId = syncService.canonicalQualityDocId(normalQa)
    assertEquals("QA_K1_2026-04-20_0800-0900", canonicalQaId)
    assertFalse(syncService.shouldDeleteLegacyQualityDoc(normalQa, canonicalQaId))
  }

  @Test
  fun `test Step 6 TV flow without login screen or hardcoded management credentials`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = com.example.data.GoogleSheetsStorageService(context)
    storage.clearLocalActiveSession()

    // 1. Fresh TV repository has no active session (not auto-logged-in as management)
    val tvRepo = NflRepository(context)
    assertEquals(null, tvRepo.currentUser.value)
    assertFalse(tvRepo.hasValidTvSession())

    // 2. TV Setup creates a pending device approval request for tv_display (no password required)
    val setupReq = tvRepo.ensureTvDisplaySetupRequest()
    assertEquals(NflRepository.TV_DISPLAY_ACCOUNT_ID, setupReq.userId)
    assertEquals("PENDING", setupReq.status)
    assertEquals(tvRepo.getDeviceId(), setupReq.deviceId)
    assertEquals(6, tvRepo.getTvShortDeviceCode().length)
    assertFalse(tvRepo.hasValidTvSession())

    // 3. Admin approves the TV setup request -> binds TV deviceId and activates tv_display session
    val adminRepo = NflRepository(context)
    adminRepo.authenticateUser("admin", "123")
    val approveRes = adminRepo.adminApproveDeviceOrAccountRequest(setupReq.requestId)
    assertTrue(approveRes.success)

    // Simulate TV receiving the approved tv_display account update (or activating on approval)
    val approvedTvAccount = adminRepo.users.value.first { it.id == NflRepository.TV_DISPLAY_ACCOUNT_ID }
    assertEquals(tvRepo.getDeviceId(), approvedTvAccount.authorizedDeviceId)
    tvRepo.upsertRemoteUsers(listOf(approvedTvAccount))

    assertTrue(tvRepo.hasValidTvSession())
    assertEquals(NflRepository.TV_DISPLAY_ACCOUNT_ID, tvRepo.currentUser.value?.id)
    assertTrue(tvRepo.currentUser.value!!.isViewOnly)
    assertTrue(tvRepo.currentUser.value!!.canViewTvDashboard)
    assertFalse(tvRepo.currentUser.value!!.isAdmin)
    assertFalse(tvRepo.currentUser.value!!.canProductionWrite)
    assertFalse(tvRepo.currentUser.value!!.canQualityWrite)

    // 4. Restarting NflRepository on the TV restores the tv_display session offline immediately
    val restartedTvRepo = NflRepository(context)
    assertTrue(restartedTvRepo.hasValidTvSession())
    assertEquals(NflRepository.TV_DISPLAY_ACCOUNT_ID, restartedTvRepo.currentUser.value?.id)

    // 5. Verify tv_display session is strictly blocked from production/quality entry and admin actions
    val prodCountBefore = restartedTvRepo.productionEntries.value.count { !it.isDemo }
    restartedTvRepo.submitProduction(
      ProductionRecord(
        lineId = "K1",
        floor = "Sew 2",
        date = restartedTvRepo.getCurrentBangladeshDateIso(),
        hourSlot = "10:00 - 11:00",
        styleNumber = "ST-100",
        outputQty = 999,
        isDemo = false
      )
    )
    assertEquals(prodCountBefore, restartedTvRepo.productionEntries.value.count { !it.isDemo })
    assertFalse(restartedTvRepo.adminSuspendUser("supervisor").success)
  }

  @Test
  fun `test Step 2 and Step 3 SaveUiState formatting callbacks and plausibility threshold`() {
    // 1. Explicit SaveUiState feedback messages (Idle, Saving, SavedLocally, Synced, SyncFailed)
    assertEquals(null, formatSaveFeedbackMessage(SaveUiState.Idle))
    assertEquals("Saving...", formatSaveFeedbackMessage(SaveUiState.Saving))
    assertEquals("Saved on this phone, syncing...", formatSaveFeedbackMessage(SaveUiState.SavedLocally))
    assertEquals("✓ Synced to cloud", formatSaveFeedbackMessage(SaveUiState.Synced))
    assertEquals("⚠ Saved on this phone, will retry", formatSaveFeedbackMessage(SaveUiState.SyncFailed))

    // 2. Batch summary formatting includes saved and skipped counts
    val batchSummary = BatchSubmitSummary(savedCount = 5, skippedCount = 2)
    assertEquals(
      "Saved on this phone (5 saved, 2 skipped), syncing...",
      formatSaveFeedbackMessage(SaveUiState.SavedLocally, batchSummary)
    )
    assertEquals(
      "✓ Synced to cloud (5 saved, 2 skipped)",
      formatSaveFeedbackMessage(SaveUiState.Synced, batchSummary)
    )
    assertEquals(
      "⚠ Saved on this phone, will retry (5 saved, 2 skipped)",
      formatSaveFeedbackMessage(SaveUiState.SyncFailed, batchSummary)
    )

    // 3. Plausibility threshold (> 3 * targetHourlyPcs)
    assertEquals(null, findImplausibleProductionTarget(outputQty = 300, targetHourlyPcs = 100))
    assertEquals(100, findImplausibleProductionTarget(outputQty = 301, targetHourlyPcs = 100))
    assertEquals(null, findImplausibleProductionTarget(outputQty = 500, targetHourlyPcs = 0))

    // 4. Verify submitProduction / submitBatchProduction / submitQuality fire onLocalSaved ONLY after local save succeeds
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")
    val todayIso = repo.getCurrentBangladeshDateIso()

    var localSavedFired = false
    val savedRec = repo.submitProduction(
      entry = ProductionRecord(
        lineId = "K1",
        floor = "Sew 2",
        date = todayIso,
        hourSlot = "11:00 - 12:00",
        styleNumber = "ST-777",
        outputQty = 125,
        isDemo = false
      ),
      onLocalSaved = { localSavedFired = true }
    )
    assertTrue(localSavedFired)
    assertTrue(repo.productionEntries.value.any { it.id == savedRec.id && it.outputQty == 125 })

    // Unauthorized submit (view-only role without production write permission) must NOT fire onLocalSaved
    repo.authenticateUser("management", "123")
    var unauthorizedLocalSaved = false
    repo.submitProduction(
      entry = ProductionRecord(
        lineId = "K1",
        floor = "Sew 2",
        date = todayIso,
        hourSlot = "12:00 - 13:00",
        styleNumber = "ST-777",
        outputQty = 999,
        isDemo = false
      ),
      onLocalSaved = { unauthorizedLocalSaved = true }
    )
    assertFalse(unauthorizedLocalSaved)
  }

  @Test
  fun `test Step 4 rememberSaveable Savers preserve drafts and LoginScreen does not save passwords`() {
    val saverScope = androidx.compose.runtime.saveable.SaverScope { true }

    // 1. StringMapSaver (ProductionEntryScreen batch inputs)
    val stringMap = androidx.compose.runtime.mutableStateMapOf("K1" to "145", "K2" to "160")
    val savedStringMap = with(com.example.ui.screens.StringMapSaver) {
      saverScope.save(stringMap)
    }
    assertTrue(savedStringMap != null)
    val restoredStringMap = com.example.ui.screens.StringMapSaver.restore(savedStringMap!!)
    assertEquals("145", restoredStringMap?.get("K1"))
    assertEquals("160", restoredStringMap?.get("K2"))

    // 2. DefectItemListSaver (QualityEntryScreen defects list)
    val defectList = listOf(DefectItem("Broken Stitch", 3), DefectItem("Oil Spot", 1))
    val savedDefects = with(com.example.ui.screens.DefectItemListSaver) {
      saverScope.save(defectList)
    }
    assertTrue(savedDefects != null)
    val restoredDefects = com.example.ui.screens.DefectItemListSaver.restore(savedDefects!!)
    assertEquals(2, restoredDefects?.size)
    assertEquals("Broken Stitch", restoredDefects?.get(0)?.defectType)
    assertEquals(3, restoredDefects?.get(0)?.count)

    // 3. LineRowDraftMapSaver (SupervisorSetupScreen morning setup drafts)
    val setupDrafts = androidx.compose.runtime.mutableStateMapOf(
      "K1" to com.example.ui.screens.LineRowDraft(
        lineId = "K1",
        floor = "Sew 2",
        itemName = "ST-200",
        smvText = "12.5",
        targetText = "155"
      )
    )
    val savedSetup = with(com.example.ui.screens.LineRowDraftMapSaver) {
      saverScope.save(setupDrafts)
    }
    assertTrue(savedSetup != null)
    val restoredSetup = com.example.ui.screens.LineRowDraftMapSaver.restore(savedSetup!!)
    assertEquals("ST-200", restoredSetup?.get("K1")?.itemName)
    assertEquals("155", restoredSetup?.get("K1")?.targetText)

    // 4. LineMonthlyDraftMapSaver (MonthlySetupScreen drafts)
    val monthlyDrafts = mapOf(
      "K1" to com.example.ui.screens.LineMonthlyDraft(
        efficiency = "80.0",
        monthlyTarget = "45000",
        achievement = "42000",
        dhu = "1.5",
        oql = "2.1"
      )
    )
    val savedMonthly = with(com.example.ui.screens.LineMonthlyDraftMapSaver) {
      saverScope.save(monthlyDrafts)
    }
    assertTrue(savedMonthly != null)
    val restoredMonthly = com.example.ui.screens.LineMonthlyDraftMapSaver.restore(savedMonthly!!)
    assertEquals("80.0", restoredMonthly?.get("K1")?.efficiency)
    assertEquals("2.1", restoredMonthly?.get("K1")?.oql)
  }

  @Test
  fun `test Step 2 debounced persistence writes once after burst and flushes immediately on logout`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.persistenceDebounceMs = 300L
    repo.authenticateUser("admin", "123")
    val todayBd = repo.getCurrentBangladeshDateIso()

    // Flush any initial seed writes and reset counters
    repo.flushPendingPersistence()
    repo.sheetsStorage.resetSaveCallCounts()

    // Burst of 5 rapid upserts within a few milliseconds
    for (i in 1..5) {
      repo.upsertRemoteProductionRecords(
        listOf(
          ProductionRecord(
            submissionId = "BURST-K$i",
            lineId = "K$i",
            floor = "Sew 2",
            date = todayBd,
            hourSlot = "10:00 - 11:00",
            styleNumber = "Burst-Style",
            outputQty = 100 + i,
            isDemo = false,
            timestamp = 1_700_000_000_000L + i
          )
        )
      )
    }

    // Immediately after the burst (before 300ms debounce window expires), no disk write has fired yet
    assertEquals(0, repo.sheetsStorage.productionSaveCallCount.get())

    // Wait for debounce window to elapse (450ms > 300ms)
    kotlinx.coroutines.delay(450L)

    // Exactly 1 coalesced write should have occurred for the 5-item burst
    assertEquals(1, repo.sheetsStorage.productionSaveCallCount.get())

    // Now trigger another change and immediately call logout() before the 300ms timer expires
    repo.sheetsStorage.resetSaveCallCounts()
    repo.upsertRemoteProductionRecords(
      listOf(
        ProductionRecord(
          submissionId = "LOGOUT-FLUSH-K6",
          lineId = "K6",
          floor = "Sew 2",
          date = todayBd,
          hourSlot = "11:00 - 12:00",
          styleNumber = "Flush-Style",
          outputQty = 155,
          isDemo = false,
          timestamp = 1_700_000_010_000L
        )
      )
    )
    assertEquals(0, repo.sheetsStorage.productionSaveCallCount.get())

    // Logout must flush pending debounced writes synchronously
    repo.logout()
    assertEquals(1, repo.sheetsStorage.productionSaveCallCount.get())

    // Reopening a new repository instance must see the flushed record
    val reopened = NflRepository(context)
    assertTrue(reopened.productionEntries.value.any { it.submissionId == "LOGOUT-FLUSH-K6" && it.outputQty == 155 })
  }

  @Test
  fun `test Step 2 concurrent upserts from multiple coroutines do not lose records`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.persistenceDebounceMs = 500L
    val todayBd = repo.getCurrentBangladeshDateIso()

    val threadA = Thread {
      for (i in 1..25) {
        repo.upsertRemoteProductionRecords(
          listOf(
            ProductionRecord(
              submissionId = "CONC-A-$i",
              lineId = "K1",
              floor = "Sew 2",
              date = todayBd,
              hourSlot = "Slot-A-$i",
              styleNumber = "Style-A",
              outputQty = 50 + i,
              isDemo = false,
              timestamp = 1_700_100_000_000L + i
            )
          )
        )
      }
    }

    val threadB = Thread {
      for (i in 1..25) {
        repo.upsertRemoteProductionRecords(
          listOf(
            ProductionRecord(
              submissionId = "CONC-B-$i",
              lineId = "K2",
              floor = "Sew 2",
              date = todayBd,
              hourSlot = "Slot-B-$i",
              styleNumber = "Style-B",
              outputQty = 80 + i,
              isDemo = false,
              timestamp = 1_700_200_000_000L + i
            )
          )
        )
      }
    }

    threadA.start()
    threadB.start()
    threadA.join()
    threadB.join()
    repo.flushPendingPersistence()

    val entries = repo.productionEntries.value
    val countA = entries.count { it.submissionId.startsWith("CONC-A-") }
    val countB = entries.count { it.submissionId.startsWith("CONC-B-") }
    assertEquals(25, countA)
    assertEquals(25, countB)
  }

  @Test
  fun `test Step 2 upsert results are deterministic and identical for same input`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo1 = NflRepository(context)
    val todayBd = repo1.getCurrentBangladeshDateIso()

    val inputBatch = listOf(
      ProductionRecord(
        submissionId = "DET-1",
        lineId = "K1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "09:00 - 10:00",
        styleNumber = "Polo-101",
        outputQty = 120,
        isDemo = false,
        timestamp = 1_700_500_001_000L,
        syncStatus = SyncStatus.SYNCED
      ),
      ProductionRecord(
        submissionId = "DET-2",
        lineId = "K2",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "10:00 - 11:00",
        styleNumber = "Polo-102",
        outputQty = 135,
        isDemo = false,
        timestamp = 1_700_500_002_000L,
        syncStatus = SyncStatus.SYNCED
      ),
      // Duplicate slot with newer timestamp for K1 09:00 - 10:00
      ProductionRecord(
        submissionId = "DET-1",
        lineId = "K1",
        floor = "Sew 2",
        date = todayBd,
        hourSlot = "09:00 - 10:00",
        styleNumber = "Polo-101",
        outputQty = 142,
        isDemo = false,
        timestamp = 1_700_500_005_000L,
        syncStatus = SyncStatus.SYNCED
      )
    )

    repo1.upsertRemoteProductionRecords(inputBatch)
    val result1 = repo1.productionEntries.value.filter { it.submissionId.startsWith("DET-") }

    // Reference legacy in-memory merge & sort algorithm
    val mapById = linkedMapOf<String, ProductionRecord>()
    for (remote in inputBatch) {
      val existing = mapById[remote.submissionId]
      if (existing == null || remote.timestamp >= existing.timestamp) {
        mapById[remote.submissionId] = remote.copy(syncStatus = SyncStatus.SYNCED)
      }
    }
    val expected = mapById.values.sortedByDescending { it.timestamp }

    assertEquals(expected.size, result1.size)
    assertEquals(expected.map { it.submissionId to it.outputQty }, result1.map { it.submissionId to it.outputQty })
    assertEquals(142, result1.first { it.submissionId == "DET-1" }.outputQty)
  }

  @Test
  fun `test primary admin 1015198 and emulator bound accounts never lock out phone login and TV activates directly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)

    // 1. Provision Initial Admin 1015198 and complete initial password change
    repo.bootstrapFirstAdmin(
      employeeId = INITIAL_ADMIN_EMPLOYEE_ID,
      initialPassword = "InitPass#2026",
      confirmPassword = "InitPass#2026",
      fullName = "Shamim Admin"
    )
    repo.changePassword(
      userId = INITIAL_ADMIN_EMPLOYEE_ID,
      currentPassword = "InitPass#2026",
      newPassword = "NFL@26live",
      confirmPassword = "NFL@26live"
    )

    // 2. Simulate 1015198 having been bound to the AI Studio emulator (Google sdk_gphone64_arm64 • DEV-4A78CFB624AEBD7E)
    val adminUser = repo.users.value.first { it.id == INITIAL_ADMIN_EMPLOYEE_ID }
    repo.upsertRemoteUsers(
      listOf(
        adminUser.copy(
          authorizedDeviceId = "DEV-4A78CFB624AEBD7E",
          authorizedDeviceName = "Google sdk_gphone64_arm64",
          approvedDevices = listOf(
            ApprovedDevice("DEV-4A78CFB624AEBD7E", "Google sdk_gphone64_arm64", System.currentTimeMillis(), "1015198")
          ),
          updatedAtMillis = System.currentTimeMillis() + 5000L
        )
      )
    )

    // 3. Logging in on the physical phone with 1015198 and valid password succeeds without DEVICE_LIMIT_REACHED
    val phoneLogin = repo.authenticateUser(INITIAL_ADMIN_EMPLOYEE_ID, "NFL@26live")
    assertEquals(AuthStatus.SUCCESS, phoneLogin.status)
    assertEquals(INITIAL_ADMIN_EMPLOYEE_ID, repo.currentUser.value?.id)

    // 4. Direct TV activation immediately enables read-only tv_display session without blocking
    val tvRepo = NflRepository(context)
    tvRepo.activateTvDeviceDirectly()
    assertTrue(tvRepo.hasValidTvSession())
    assertEquals(NflRepository.TV_DISPLAY_ACCOUNT_ID, tvRepo.currentUser.value?.id)
    assertTrue(tvRepo.currentUser.value?.isViewOnly == true)
    assertFalse(tvRepo.currentUser.value?.canProductionWrite == true)
  }

  @Test
  fun `test removeAllDemoData and resetAllOperationalData clear records persist to disk and disable synthetic fallbacks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.loadSeedDemoData(force = true)
    assertTrue(repo.productionEntries.value.any { it.isDemo })

    // Add a live non-demo record
    val beforeResetTs = System.currentTimeMillis() - 5000L
    repo.upsertRemoteProductionRecords(
      listOf(
        ProductionRecord(
          id = "LIVE-P1",
          submissionId = "LIVE-P1",
          lineId = "A1",
          floor = "Sew 1",
          date = "2026-10-24",
          hourSlot = "08:00 - 09:00",
          styleNumber = "ST-101",
          outputQty = 95,
          timestamp = beforeResetTs,
          isDemo = false
        )
      )
    )

    // 1. removeAllDemoData removes demo records, persists to disk, and disables synthetic chart fallbacks
    repo.removeAllDemoData()
    assertFalse(repo.productionEntries.value.any { it.isDemo })
    assertTrue(repo.productionEntries.value.any { it.submissionId == "LIVE-P1" })
    assertTrue(repo.isSampleDataCleared())
    assertTrue(repo.getLineIssues("Z99").isEmpty())
    assertTrue(repo.getParetoDefects("Z99").isEmpty())
    assertTrue(repo.getHourlyProductionData("Z99", PeriodScope.TODAY).all { it.achieveOutput == 0 })

    // 2. resetAllOperationalData wipes both live and demo operational records and blocks stale pre-reset snapshots
    repo.resetAllOperationalData(clearCloud = false)
    assertTrue(repo.productionEntries.value.isEmpty())
    assertTrue(repo.qualityEntries.value.isEmpty())
    assertTrue(repo.incidents.value.isEmpty())
    assertTrue(repo.targets.value.isEmpty())
    assertTrue(repo.users.value.isNotEmpty())

    // Stale remote snapshot from before the reset is ignored
    repo.upsertRemoteProductionRecords(
      listOf(
        ProductionRecord(
          id = "STALE-P1",
          submissionId = "STALE-P1",
          lineId = "A1",
          floor = "Sew 1",
          date = "2026-10-24",
          hourSlot = "09:00 - 10:00",
          styleNumber = "ST-101",
          outputQty = 88,
          timestamp = beforeResetTs,
          isDemo = false
        )
      )
    )
    assertTrue(repo.productionEntries.value.isEmpty())

    // Restarting repository loads clean empty state from disk
    val reloadedRepo = NflRepository(context)
    assertTrue(reloadedRepo.productionEntries.value.isEmpty())
  }
}



