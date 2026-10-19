package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.*
import org.junit.Assert.assertEquals
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
  fun `test 10 roles support and floor scope`() {
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
  fun `test security authentication duplicate account and two device limit`() {
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
    assertTrue(!dupRes.success)

    // 3. Enforce 2-device limit when 2 other devices are already approved
    val supUser = repo.users.value.find { it.id == "supervisor" }!!
    repo.saveUser(
      supUser.copy(
        approvedDevices = listOf(
          ApprovedDevice("DEV-OLD-1", "Device 1", 1000L, "Admin"),
          ApprovedDevice("DEV-OLD-2", "Device 2", 2000L, "Admin")
        )
      ),
      isNewAccount = false
    )
    val thirdDevAttempt = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.DEVICE_LIMIT_REACHED, thirdDevAttempt.status)

    // 4. Admin revokes one device -> now this device can bind and log in
    repo.authenticateUser("admin", "123")
    val revokeRes = repo.adminRevokeUserDevice("supervisor", "DEV-OLD-1")
    assertTrue(revokeRes.success)

    val secondDevLogin = repo.authenticateUser("supervisor", "123")
    assertEquals(AuthStatus.SUCCESS, secondDevLogin.status)
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
}
