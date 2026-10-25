package com.example.ui.screens

import com.example.data.NflRepository
import com.example.model.*
import java.util.Calendar
import java.util.Locale

/**
 * TV wallboard metrics computed ONLY from real stored records.
 * Nothing here writes data, and nothing invents numbers: when there is no data the value is null / empty.
 * It replaces the TV's use of todayOql*0.4 as DHU, the padded NPT minutes and the padded hourly rows.
 */
data class WbHour(val label: String, val output: Int, val dhu: Double?, val oql: Double?)
data class WbCause(val name: String, val minutes: Int)
data class WbLineDefect(val lineId: String, val defect: String, val dhu: Double)

data class WbMetrics(
  val dhu: Double? = null,
  val oql: Double? = null,
  val checkedPcs: Int = 0,
  val downtimeMin: Int = 0,
  val lostPcsEst: Int = 0,
  val causes: List<WbCause> = emptyList(),
  val hours: List<WbHour> = emptyList(),
  val hourlyTarget: Int = 0,
  val hourlySupported: Boolean = false,
  val worstLineDefects: List<WbLineDefect> = emptyList()
)

private fun dayStart(daysAgo: Int, now: Long): Long {
  val c = Calendar.getInstance()
  c.timeInMillis = now
  c.set(Calendar.HOUR_OF_DAY, 0)
  c.set(Calendar.MINUTE, 0)
  c.set(Calendar.SECOND, 0)
  c.set(Calendar.MILLISECOND, 0)
  c.add(Calendar.DAY_OF_YEAR, -daysAgo)
  return c.timeInMillis
}

private fun monthStart(now: Long): Long {
  val c = Calendar.getInstance()
  c.timeInMillis = dayStart(0, now)
  c.set(Calendar.DAY_OF_MONTH, 1)
  return c.timeInMillis
}

private fun periodWindow(period: PeriodScope, now: Long): Pair<Long, Long> = when (period) {
  PeriodScope.TODAY -> dayStart(0, now) to now
  PeriodScope.YESTERDAY -> dayStart(1, now) to dayStart(0, now)
  PeriodScope.WEEK -> dayStart(6, now) to now
  PeriodScope.MONTH -> monthStart(now) to now
}

private fun slotOrder(slot: String): Int =
  NflRepository.parseSlotHours24(slot)?.first ?: (Regex("\\d+").find(slot)?.value?.toIntOrNull() ?: 99)

private fun slotLabel(slot: String): String =
  NflRepository.formatHourShort12h(slot)

fun buildWbMetrics(
  repo: NflRepository,
  floor: FloorScope,
  period: PeriodScope,
  prods: List<ProductionRecord>,
  quals: List<QualityRecord>,
  incidents: List<IncidentItem>,
  lines: List<LinePerformancePoint>,
  now: Long
): WbMetrics {
  fun key(raw: String): String =
    (repo.allLinesMaster.firstOrNull { it.matches(raw.trim()) }?.id ?: raw.trim()).uppercase(Locale.US)

  val lineKeys = lines.map { key(it.lineId) }.toSet()

  // De-duplicate by (line, date, hour) exactly like the repository does: real beats demo, newest wins.
  val prodMap = LinkedHashMap<String, ProductionRecord>()
  prods.forEach { r ->
    val k = key(r.lineId) + "|" + r.date + "|" + r.hourSlot.trim().lowercase(Locale.US)
    val e = prodMap[k]
    if (e == null || (e.isDemo && !r.isDemo) || (!e.isDemo && !r.isDemo && r.timestamp >= e.timestamp)) prodMap[k] = r
  }
  val qualMap = LinkedHashMap<String, QualityRecord>()
  quals.forEach { r ->
    val k = key(r.lineId) + "|" + r.date + "|" + r.hourSlot.trim().lowercase(Locale.US)
    val e = qualMap[k]
    if (e == null || (e.isDemo && !r.isDemo) || (!e.isDemo && !r.isDemo && r.timestamp >= e.timestamp)) qualMap[k] = r
  }
  val prodIn = prodMap.values.filter { key(it.lineId) in lineKeys && repo.isDateInPeriod(it.date, period) }
  val qualIn = qualMap.values.filter { key(it.lineId) in lineKeys && repo.isDateInPeriod(it.date, period) }

  // Quality totals: DHU = defects / checked * 100 ; OQL = defective / checked * 100 (same formulas as the repository)
  val checked = qualIn.sumOf { it.checkedGarments }
  val defective = qualIn.sumOf { it.defectiveGarments }
  val defects = qualIn.sumOf { it.totalDefects }
  val dhu = if (checked > 0) Math.round(defects.toDouble() / checked * 10000.0) / 100.0 else null
  val oql = if (checked > 0) Math.round(defective.toDouble() / checked * 10000.0) / 100.0 else null

  // Real downtime: only real incidents of this floor, counted only inside the selected period window. No fake minutes, no clamp.
  val (w0, w1) = periodWindow(period, now)
  val hourlyByLine = lines.associate { key(it.lineId) to it.hourlyTargetPcs }
  var totalMin = 0
  var lost = 0.0
  val byCause = LinkedHashMap<String, Int>()
  incidents.forEach { inc ->
    val master = repo.allLinesMaster.firstOrNull { it.matches(inc.lineId.trim()) }
    val incFloor = master?.floor ?: inc.floor
    if (!repo.isInSelectedFloor(incFloor, floor)) return@forEach
    val s = maxOf(inc.startTimeMillis, w0)
    val e = minOf(inc.endTimeMillis ?: now, w1)
    if (e <= s) return@forEach
    val minutes = ((e - s) / 60000L).toInt()
    if (minutes <= 0) return@forEach
    totalMin += minutes
    val name = if (inc.category == IncidentCategory.OTHER && inc.customNptTypeName.isNotBlank()) inc.customNptTypeName else inc.category.displayName
    byCause[name] = (byCause[name] ?: 0) + minutes
    lost += minutes * (hourlyByLine[key(inc.lineId)] ?: 0) / 60.0
  }

  // Hourly view only makes sense for a single day.
  val hourlySupported = period == PeriodScope.TODAY || period == PeriodScope.YESTERDAY
  val hours = if (hourlySupported) {
    val outBySlot = prodIn.groupBy { it.hourSlot.trim() }
    val qaBySlot = qualIn.groupBy { it.hourSlot.trim() }
    (outBySlot.keys + qaBySlot.keys).distinct().sortedBy { slotOrder(it) }.map { slot ->
      val q = qaBySlot[slot].orEmpty()
      val c = q.sumOf { it.checkedGarments }
      WbHour(
        label = slotLabel(slot),
        output = outBySlot[slot].orEmpty().sumOf { it.outputQty },
        dhu = if (c > 0) q.sumOf { it.totalDefects }.toDouble() / c * 100.0 else null,
        oql = if (c > 0) q.sumOf { it.defectiveGarments }.toDouble() / c * 100.0 else null
      )
    }
  } else emptyList()

  // Main defect of the worst DHU lines (from real defect entries)
  val worst = lines.filter { it.dhu != null }.sortedByDescending { it.dhu }.take(5).mapNotNull { l ->
    val counts = HashMap<String, Int>()
    qualIn.filter { key(it.lineId) == key(l.lineId) }.forEach { r ->
      r.defects.forEach { d -> if (d.count > 0) counts[d.defectType] = (counts[d.defectType] ?: 0) + d.count }
    }
    val top = counts.maxByOrNull { it.value }
    if (top == null) null else WbLineDefect(l.lineId, top.key, l.dhu ?: 0.0)
  }

  return WbMetrics(
    dhu = dhu,
    oql = oql,
    checkedPcs = checked,
    downtimeMin = totalMin,
    lostPcsEst = Math.round(lost).toInt(),
    causes = byCause.map { WbCause(it.key, it.value) }.sortedByDescending { it.minutes },
    hours = hours,
    hourlyTarget = lines.sumOf { it.hourlyTargetPcs },
    hourlySupported = hourlySupported,
    worstLineDefects = worst
  )
}
