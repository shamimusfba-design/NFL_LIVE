package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.DataCleanScope
import com.example.model.FloorManpower
import com.example.model.SupervisorLineSetup
import com.example.model.autoHourlyTarget
import com.example.model.computeManpowerSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Line manpower, automatic hourly target, floor totals and the Administrator-only Data Clean.
 * NOTE: written without being able to run Gradle; run ./gradlew :app:testDebugUnitTest to confirm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManpowerAndCleanTest {

  private fun newRepo() = NflRepository(ApplicationProvider.getApplicationContext<Context>())

  private fun clean(repo: NflRepository, scope: DataCleanScope, password: String): Pair<Boolean, String> {
    val latch = CountDownLatch(1)
    var result = Pair(false, "")
    repo.cleanData(scope, password) { ok, msg -> result = ok to msg; latch.countDown() }
    assertTrue("cleanData did not finish", latch.await(10, TimeUnit.SECONDS))
    return result
  }

  @Test
  fun `efficiency counts operators, helpers and finishing staff together`() {
    val s = SupervisorLineSetup(lineId = "K1", date = "2026-10-27", operatorsCount = 20, helpersCount = 2, finishingStaffCount = 4)
    assertEquals(26, s.includedManpower)
    assertEquals(26, s.copy(includeHelpersInEfficiency = false).includedManpower)
    assertEquals(26, s.totalManpower)
  }

  @Test
  fun `hourly target is manpower x 60 x target percent divided by SMV`() {
    assertEquals(113, autoHourlyTarget(30, 12.0, 75.0))          // 112.5 rounds up
    assertEquals(77, autoHourlyTarget(26, 15.2, 75.0))
    assertEquals(0, autoHourlyTarget(0, 12.0, 75.0))
    assertEquals(0, autoHourlyTarget(30, 0.0, 75.0))
    assertEquals(0, autoHourlyTarget(30, 12.0, 0.0))
  }

  @Test
  fun `the four manpower numbers follow the agreed formulas`() {
    val d = "2026-10-27"
    val lines = listOf(
      SupervisorLineSetup(lineId = "K1", floor = "Sew 2", date = d, operatorsCount = 50, snapshotTimestamp = 1),
      SupervisorLineSetup(lineId = "K2", floor = "Sew 2", date = d, operatorsCount = 50, snapshotTimestamp = 1)
    )
    val entry = FloorManpower("Sew 2", d, totalMachines = 100, machinesRunning = 86, operatorsPresent = 90, helpersPresent = 10, operatorsOnHandwork = 8)
    val s = computeManpowerSummary(listOf(entry), lines, d, "Sew 2")
    assertEquals(1, s.floorsEntered)
    assertEquals(100, s.operatorsPlanned)
    assertEquals(90.0, s.operatorsPresentPct, 0.0001)               // present ÷ planned
    assertEquals(86.0, s.machinesRunningPct, 0.0001)                // running ÷ total machines
    assertEquals(86 * 100.0 / 90, s.machinesPerOperatorPct, 0.0001) // running ÷ present operators
    assertEquals(10.0, s.helpersPctOfMachines, 0.0001)              // helpers ÷ total machines
    assertEquals(8, s.operatorsOnHandwork)
    assertEquals(8 * 100.0 / 90, s.handworkPctOfOperators, 0.0001)  // handwork operators ÷ operators present
  }

  @Test
  fun `only the chosen floor and day are counted, never demo, finishing or other days`() {
    val d = "2026-10-27"
    val entries = listOf(
      FloorManpower("Sew 2", d, 100, 80, 90, 10, 0),
      FloorManpower("Sew 1", d, 60, 50, 55, 6, 0),
      FloorManpower("Sew 2", "2026-10-26", 100, 99, 99, 9, 0)
    )
    val setups = listOf(
      SupervisorLineSetup(lineId = "K1", floor = "Sew 2", date = d, operatorsCount = 100, snapshotTimestamp = 1),
      SupervisorLineSetup(lineId = "A1", floor = "Sew 1", date = d, operatorsCount = 60, snapshotTimestamp = 1),
      SupervisorLineSetup(lineId = "Iron 2", floor = "Sew 2", date = d, operatorsCount = 9, snapshotTimestamp = 1),
      SupervisorLineSetup(lineId = "K2", floor = "Sew 2", date = d, operatorsCount = 7, isDemo = true, snapshotTimestamp = 1)
    )
    val sew2 = computeManpowerSummary(entries, setups, d, "Sew 2")
    assertEquals(90, sew2.operatorsPresent)
    assertEquals(100, sew2.operatorsPlanned)
    val factory = computeManpowerSummary(entries, setups, d, null)
    assertEquals(2, factory.floorsEntered)
    assertEquals(145, factory.operatorsPresent)
    assertEquals(160, factory.operatorsPlanned)
    assertFalse(computeManpowerSummary(emptyList(), setups, d).hasData)
  }

  @Test
  fun `logged out user cannot save floor totals`() {
    val repo = newRepo()
    repo.logout()
    assertFalse(repo.saveFloorManpower("Sew 2", 100, 80, 90, 10, 5).success)
    assertFalse(repo.manpowerSummary("Sew 2").hasData)
  }

  @Test
  fun `admin saves floor totals and wrong numbers are rejected`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    assertFalse(repo.saveFloorManpower("Sew 2", 100, 120, 90, 10, 5).success)   // running > total
    assertFalse(repo.saveFloorManpower("All Floor", 100, 80, 90, 10, 5).success) // one floor at a time
    assertFalse(repo.saveFloorManpower("Sew 2", -1, 0, 0, 0, 0).success)
    assertFalse(repo.saveFloorManpower("Sew 2", 100, 80, 20, 10, 25).success)    // more operators on handwork than operators present
    val r = repo.saveFloorManpower("Sew 2", 100, 80, 90, 10, 5)
    assertTrue(r.message, r.success)
    val s = repo.manpowerSummary("Sew 2")
    assertTrue(s.hasData)
    assertEquals(80, s.machinesRunning)
    assertEquals(90, s.operatorsPresent)
    assertEquals(5, s.operatorsOnHandwork)
    // a second save the same day replaces the first
    assertTrue(repo.saveFloorManpower("Sew 2", 100, 85, 92, 10, 5).success)
    assertEquals(85, repo.manpowerSummary("Sew 2").machinesRunning)
  }

  @Test
  fun `newer remote floor totals win and survive a restart`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    val d = repo.getCurrentBangladeshDateIso()
    repo.upsertRemoteFloorManpower(listOf(FloorManpower("Sew 3", d, 90, 70, 60, 6, 0, "x", 1000L)))
    repo.upsertRemoteFloorManpower(listOf(FloorManpower("Sew 3", d, 90, 10, 60, 6, 0, "x", 500L)))   // older: ignored
    assertEquals(70, repo.manpowerSummary("Sew 3").machinesRunning)
    repo.upsertRemoteFloorManpower(listOf(FloorManpower("Sew 3", d, 90, 75, 60, 6, 0, "x", 2000L)))
    assertEquals(75, repo.manpowerSummary("Sew 3").machinesRunning)
    assertEquals(75, NflRepository(context).manpowerSummary("Sew 3").machinesRunning)
  }

  @Test
  fun `data clean needs an administrator and the right password`() {
    val repo = newRepo()
    repo.logout()
    assertFalse(clean(repo, DataCleanScope.PRODUCTION, "123").first)
    repo.authenticateUser("supervisor", "123")
    assertFalse(clean(repo, DataCleanScope.PRODUCTION, "123").first)
    repo.logout()
    repo.authenticateUser("admin", "123")
    assertFalse(clean(repo, DataCleanScope.PRODUCTION, "wrong-password").first)
  }

  @Test
  fun `admin cleans production, weekly and floor totals`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    repo.loadSeedDemoData(force = true)
    assertTrue(repo.productionEntries.value.isNotEmpty())
    assertTrue(repo.saveWeeklyQuality(System.currentTimeMillis(), 5.0, 6.0).success)
    assertTrue(repo.saveFloorManpower("Sew 2", 100, 80, 90, 10, 5).success)

    assertTrue(clean(repo, DataCleanScope.PRODUCTION, "123").first)
    assertTrue(repo.productionEntries.value.isEmpty())

    assertTrue(clean(repo, DataCleanScope.WEEKLY, "123").first)
    assertTrue(repo.weeklyQuality.value.isEmpty())

    assertTrue(clean(repo, DataCleanScope.SETUP, "123").first)
    assertTrue(repo.floorManpower.value.isEmpty())
  }
}
