package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.model.OqlBand
import com.example.model.OqlThresholds
import com.example.model.WeeklyQualityEntry
import com.example.model.WeeklyQualityWeek
import com.example.ui.screens.WbTheme
import com.example.ui.screens.wbOqlColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Weekly BS OQL / NFL AQC entry and the OQL limits (commitment 4.5, Level 1 below 7, red above 10).
 * NOTE: written without being able to run Gradle; run ./gradlew :app:testDebugUnitTest to confirm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyQualityTest {

  private fun dhaka(y: Int, m: Int, d: Int, h: Int = 15): Long {
    val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
    c.clear()
    c.set(y, m - 1, d, h, 30, 0)
    return c.timeInMillis
  }

  private fun newRepo(): NflRepository =
    NflRepository(ApplicationProvider.getApplicationContext<Context>())

  @Test
  fun `week keys follow ISO weeks in Bangladesh time`() {
    assertEquals("2026-W43", WeeklyQualityWeek.keyFor(dhaka(2026, 10, 24)))
    assertEquals("2026-10-19", WeeklyQualityWeek.startIsoFor(dhaka(2026, 10, 24)))
    assertEquals("2026-W01", WeeklyQualityWeek.keyFor(dhaka(2026, 1, 1)))
    assertEquals("2025-12-29", WeeklyQualityWeek.startIsoFor(dhaka(2026, 1, 1)))
    assertEquals("2026-W53", WeeklyQualityWeek.keyFor(dhaka(2027, 1, 1)))
    assertEquals("2026-12-28", WeeklyQualityWeek.startIsoFor(dhaka(2027, 1, 1)))
    assertEquals("W43", WeeklyQualityWeek.labelFor("2026-W43"))
  }

  @Test
  fun `recent weeks are consecutive and start with the current week`() {
    val weeks = WeeklyQualityWeek.recentWeeks(12, dhaka(2026, 10, 24))
    assertEquals(12, weeks.size)
    assertEquals("2026-W43", weeks[0].key)
    assertEquals("2026-W42", weeks[1].key)
    assertEquals("2026-W32", weeks[11].key)
    assertEquals(12, weeks.map { it.key }.toSet().size)
  }

  @Test
  fun `OQL limits are commitment 4_5, Level 1 below 7 and red above 10`() {
    assertEquals(OqlBand.WITHIN_COMMITMENT, OqlThresholds.bandOf(4.5))
    assertEquals(OqlBand.LEVEL1, OqlThresholds.bandOf(4.51))
    assertEquals(OqlBand.LEVEL1, OqlThresholds.bandOf(6.99))
    assertEquals(OqlBand.LEVEL1_LOST, OqlThresholds.bandOf(7.0))
    assertEquals(OqlBand.LEVEL1_LOST, OqlThresholds.bandOf(10.0))
    assertEquals(OqlBand.CRITICAL, OqlThresholds.bandOf(10.01))
  }

  @Test
  fun `TV colours follow the same OQL limits`() {
    assertEquals(WbTheme.GREEN, wbOqlColor(4.0, OqlThresholds.COMMITMENT))
    assertEquals(WbTheme.AMBER, wbOqlColor(5.58, OqlThresholds.COMMITMENT))
    assertEquals(WbTheme.ORANGE, wbOqlColor(8.0, OqlThresholds.COMMITMENT))
    assertEquals(WbTheme.RED, wbOqlColor(10.5, OqlThresholds.COMMITMENT))
  }

  @Test
  fun `logged out user cannot save weekly values`() {
    val repo = newRepo()
    repo.logout()
    val result = repo.saveWeeklyQuality(System.currentTimeMillis(), 5.0, 6.0)
    assertFalse(result.success)
    assertTrue(repo.weeklyQuality.value.isEmpty())
  }

  @Test
  fun `admin can save and a blank value keeps the saved one`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    val now = System.currentTimeMillis()
    assertTrue(repo.saveWeeklyQuality(now, 5.58, 6.39).success)
    assertTrue(repo.saveWeeklyQuality(now, 5.1, null).success)
    val entry = repo.weeklyQuality.value.single()
    assertEquals(5.1, entry.bsOqlPct!!, 0.0001)
    assertEquals(6.39, entry.nflAqcPct!!, 0.0001)
  }

  @Test
  fun `bad values and future weeks are rejected`() {
    val repo = newRepo()
    repo.authenticateUser("admin", "123")
    val now = System.currentTimeMillis()
    assertFalse(repo.saveWeeklyQuality(now, 101.0, null).success)
    assertFalse(repo.saveWeeklyQuality(now, -1.0, null).success)
    assertFalse(repo.saveWeeklyQuality(now, null, null).success)
    assertFalse(repo.saveWeeklyQuality(now + 14L * 24L * 60L * 60L * 1000L, 5.0, null).success)
    assertTrue(repo.weeklyQuality.value.isEmpty())
  }

  @Test
  fun `remote weekly values keep the newer one and survive a restart`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    fun entry(bs: Double, updated: Long) =
      WeeklyQualityEntry("2026-W42", "2026-10-12", bs, 6.0, "IE", updated)

    repo.upsertRemoteWeeklyQuality(listOf(entry(5.0, 1000L)))
    repo.upsertRemoteWeeklyQuality(listOf(entry(9.0, 500L)))
    assertEquals(5.0, repo.weeklyQuality.value.single().bsOqlPct!!, 0.0001)

    repo.upsertRemoteWeeklyQuality(listOf(entry(4.0, 2000L)))
    assertEquals(4.0, repo.weeklyQuality.value.single().bsOqlPct!!, 0.0001)

    val restarted = NflRepository(context)
    assertEquals(4.0, restarted.weeklyQuality.value.single().bsOqlPct!!, 0.0001)
  }
}
