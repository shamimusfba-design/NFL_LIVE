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
}
