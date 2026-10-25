package com.example.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Factory-level weekly quality figures that are entered by hand once a week.
 * Both values are percentages (for example 5.58 means 5.58%).
 *
 * weekKey is the ISO week in Asia/Dhaka (Monday to Sunday), for example "2026-W43".
 * A value that is null has not been entered for that week.
 */
data class WeeklyQualityEntry(
  val weekKey: String,
  val weekStartIso: String,
  val bsOqlPct: Double? = null,
  val nflAqcPct: Double? = null,
  val lastUpdatedBy: String = "",
  val lastUpdatedMillis: Long = 0L
)

enum class OqlBand {
  /** At or below the factory commitment. */
  WITHIN_COMMITMENT,

  /** Above the commitment but still below the Level 1 limit. */
  LEVEL1,

  /** At or above the Level 1 limit, up to and including the red limit. */
  LEVEL1_LOST,

  /** Above the red limit. */
  CRITICAL
}

/** The single place for the OQL limits given by management. Used by the phone screens and the TV. */
object OqlThresholds {
  /** Factory commitment: at or below this is good. */
  const val COMMITMENT = 4.5

  /** Below this OQL the factory stays Level 1. */
  const val LEVEL1_BELOW = 7.0

  /** More than this is critical (red). */
  const val RED_ABOVE = 10.0

  fun bandOf(value: Double, commitment: Double = COMMITMENT): OqlBand = when {
    value <= commitment -> OqlBand.WITHIN_COMMITMENT
    value < LEVEL1_BELOW -> OqlBand.LEVEL1
    value <= RED_ABOVE -> OqlBand.LEVEL1_LOST
    else -> OqlBand.CRITICAL
  }

  fun describe(value: Double, commitment: Double = COMMITMENT): String = when (bandOf(value, commitment)) {
    OqlBand.WITHIN_COMMITMENT -> "Within commitment (${trim(commitment)}% or less)"
    OqlBand.LEVEL1 -> "Above commitment, still Level 1 (below ${trim(LEVEL1_BELOW)}%)"
    OqlBand.LEVEL1_LOST -> "Level 1 lost (${trim(LEVEL1_BELOW)}% or more)"
    OqlBand.CRITICAL -> "Critical (above ${trim(RED_ABOVE)}%)"
  }

  private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

data class WeekRef(
  val key: String,
  val startIso: String,
  val startMillis: Long,
  val label: String,
  val rangeLabel: String
)

/** ISO week helpers in Bangladesh time. Verified against java.time for 2018-2031. */
object WeeklyQualityWeek {
  private val DHAKA: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

  private fun calendarFor(millis: Long): Calendar =
    Calendar.getInstance(DHAKA, Locale.US).apply {
      firstDayOfWeek = Calendar.MONDAY
      minimalDaysInFirstWeek = 4
      timeInMillis = millis
    }

  fun keyFor(millis: Long): String {
    val c = calendarFor(millis)
    return String.format(Locale.US, "%04d-W%02d", c.weekYear, c.get(Calendar.WEEK_OF_YEAR))
  }

  /** Midnight (Asia/Dhaka) of the Monday that starts the week containing [millis]. */
  fun startMillisFor(millis: Long): Long {
    val c = calendarFor(millis)
    val daysBack = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    c.add(Calendar.DAY_OF_YEAR, -daysBack)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
  }

  fun startIsoFor(millis: Long): String = formatIso(startMillisFor(millis))

  fun labelFor(weekKey: String): String = "W" + weekKey.substringAfter("-W", weekKey)

  /** The current week first, then earlier weeks. */
  fun recentWeeks(count: Int, nowMillis: Long): List<WeekRef> {
    val currentStart = startMillisFor(nowMillis)
    val rangeFmt = SimpleDateFormat("d MMM", Locale.US).apply { timeZone = DHAKA }
    return (0 until count).map { i ->
      val c = calendarFor(currentStart)
      c.add(Calendar.DAY_OF_YEAR, -7 * i)
      c.set(Calendar.HOUR_OF_DAY, 12)
      val midWeek = c.timeInMillis
      val start = startMillisFor(midWeek)
      val end = start + 6L * 24L * 60L * 60L * 1000L + 12L * 60L * 60L * 1000L
      val key = keyFor(midWeek)
      WeekRef(
        key = key,
        startIso = formatIso(start),
        startMillis = start,
        label = labelFor(key),
        rangeLabel = "${rangeFmt.format(Date(start + 60_000L))} - ${rangeFmt.format(Date(end))}"
      )
    }
  }

  private fun formatIso(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = DHAKA }.format(Date(millis))
}
