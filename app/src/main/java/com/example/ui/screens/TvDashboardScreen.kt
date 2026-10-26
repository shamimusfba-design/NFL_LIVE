package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NflRepository
import com.example.model.*
import com.example.ui.components.LiveLinePerformanceTable
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Kept for source compatibility with any code that still references it. */
enum class TvViewMode(val label: String, val shortLabel: String) {
  ALL_GRAPHS("All Main Charts (Fit Screen)", "Main Charts"),
  PRODUCTION("Production Focus", "Production"),
  QUALITY("Quality & OQL Focus", "Quality"),
  TABLE("Detailed Matrix Table", "Table")
}

enum class WbPage(val label: String) {
  PRODUCTION("Production"),
  QUALITY("Quality"),
  ALL("All"),
  TABLE("Table")
}

/** Slide interval choices in seconds; 0 = paused. The first one is the default (30 s). */
private val WB_INTERVALS = listOf(30, 60, 10, 15, 0)
private const val FRI_GOOD = 95.0

private fun floorLabel(f: FloorScope): String = when (f) {
  FloorScope.SEW_1 -> "Sew 1"
  FloorScope.SEW_2 -> "Sew 2"
  FloorScope.SEW_3 -> "Sew 3"
  FloorScope.ALL -> "All Floors"
  else -> f.displayName
}

private class WbCtx(
  val floor: FloorScope,
  val period: PeriodScope,
  val isToday: Boolean,
  val d: TvDashboardData,
  val m: WbMetrics,
  val dhuLimit: Double,
  val oqlTarget: Double,
  val weekly: List<WeeklyQualityEntry>,
  val nowMs: Long,
  val floorKpis: List<Pair<FloorScope, FloorKpiSummary>>
)

// ---------- line value builders ----------

private fun achItems(lines: List<LinePerformancePoint>, isToday: Boolean): List<WbLineVal> = lines.map { l ->
  val pct = l.achievementPct
  val out = l.achievementPcs
  WbLineVal(
    name = l.lineId,
    back = if (isToday) l.targetPcs.toDouble() else null,
    front = out?.toDouble(),
    frontColor = if (isToday) wbAchColor(pct) else WbTheme.BLUE,
    belowText = if (isToday && pct != null) "${pct.toInt()}%" else "—",
    belowColor = if (isToday) wbAchColor(pct) else WbTheme.MUTED,
    marker = null,
    rankValue = if (isToday) (pct ?: 0.0) else (out ?: 0).toDouble(),
    rankText = if (isToday) (pct?.let { "${it.toInt()}%" } ?: "--") else (out ?: 0).toString(),
    rankColor = if (isToday) wbAchColor(pct) else WbTheme.BLUE,
    sortKey = if (isToday) (pct ?: -1.0) else (out ?: 0).toDouble()
  )
}

private fun effItems(lines: List<LinePerformancePoint>, isToday: Boolean): List<WbLineVal> = lines.map { l ->
  val t = if (isToday) l.todayEfficiencyPct else null
  val shown = t ?: l.monthlyEfficiencyPct
  val col = if (t != null) wbEffColor(t, l.targetEfficiencyPct) else WbTheme.BLUE
  WbLineVal(
    name = l.lineId,
    back = l.monthlyEfficiencyPct,
    front = t,
    frontColor = col,
    belowText = if (t != null) "${t.toInt()}%" else "—",
    belowColor = if (t != null) col else WbTheme.MUTED,
    marker = null,
    rankValue = shown,
    rankText = "${shown.toInt()}%",
    rankColor = col,
    sortKey = shown
  )
}

private fun dhuItems(lines: List<LinePerformancePoint>, limit: Double): List<WbLineVal> = lines.map { l ->
  val v = l.dhu
  val col = if (v != null) wbDhuColor(v, limit) else WbTheme.GREY
  WbLineVal(
    name = l.lineId, back = null, front = v, frontColor = col,
    belowText = "", belowColor = col, marker = null,
    rankValue = v ?: 0.0, rankText = if (v != null) wbF1(v) else "--", rankColor = col,
    sortKey = if (v != null) -v else 1.0
  )
}

private fun oqlItems(lines: List<LinePerformancePoint>, target: Double): List<WbLineVal> = lines.map { l ->
  val v = l.todayOqlPct
  val col = if (v != null) wbOqlColor(v, target) else WbTheme.GREY
  WbLineVal(
    name = l.lineId, back = null, front = v, frontColor = col,
    belowText = "", belowColor = col,
    marker = if (l.monthlyOqlPct > 0.0) l.monthlyOqlPct else null,
    rankValue = v ?: 0.0, rankText = if (v != null) wbF2(v) else "--", rankColor = col,
    sortKey = if (v != null) -v else 1.0
  )
}

private fun roundedMax(values: List<Double>, floor: Double, headroom: Double): Double =
  maxOf(values.maxOrNull() ?: 0.0, floor, 0.0001) * headroom

@Composable
private fun ColumnScope.WbEmpty(text: String) {
  Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
    WbText(text, 15, WbTheme.MUTED, FontWeight.Medium)
  }
}

@Composable
private fun ColumnScope.WbBig(value: String, color: Color, sub: String) {
  Column(
    Modifier.weight(1f).fillMaxWidth(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    WbText(value, 44, color, FontWeight.Black)
    WbText(sub, 13, WbTheme.MUTED, FontWeight.Medium)
  }
}

@Composable
private fun WbDivider() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(WbTheme.LINE))
}

// ---------- shared panels ----------

@Composable
private fun RowScope.WbTargetPanel(c: WbCtx, weight: Float) {
  val lines = c.d.linePerformance
  val items = achItems(lines, c.isToday)
  val maxPcs = lines.maxOfOrNull { maxOf(it.targetPcs, it.achievementPcs ?: 0) }?.toDouble() ?: 1.0
  val cap = if (c.isToday) "bar = target pcs · % = achieved" else "output pcs · target is daily (Today only)"
  WbPanel("Live Target vs Achievement", cap, Modifier.weight(weight).fillMaxHeight()) {
    WbLineAdaptive(
      items = items, vmax = maxPcs * 1.12, tick = null,
      rankMax = if (c.isToday) 100.0 else maxPcs, rankLimit = null,
      labelOnBack = true, fmt = { wbInt(it.toInt()) },
      modifier = Modifier.weight(1f).fillMaxWidth()
    )
  }
}

@Composable
private fun RowScope.WbEffPanel(c: WbCtx, weight: Float) {
  val lines = c.d.linePerformance
  val tgt = if (lines.isEmpty()) 75.0 else lines.map { it.targetEfficiencyPct }.average()
  WbPanel("Efficiency by Line", "bar = month · narrow = today", Modifier.weight(weight).fillMaxHeight()) {
    WbLineAdaptive(
      items = effItems(lines, c.isToday), vmax = 100.0, tick = if (c.isToday) tgt else null,
      rankMax = 100.0, rankLimit = if (c.isToday) tgt else null,
      labelOnBack = true, fmt = { wbF1(it) },
      modifier = Modifier.weight(1f).fillMaxWidth()
    )
  }
}

@Composable
private fun RowScope.WbOqlPanel(c: WbCtx, weight: Float) {
  val lines = c.d.linePerformance
  val vals = lines.mapNotNull { it.todayOqlPct } + lines.map { it.monthlyOqlPct }
  val vmax = roundedMax(vals, WbThresholds.OQL_COMMITMENT, 1.15)
  WbPanel("OQL by Line", "tick = monthly · dashed = ${wbF1(WbThresholds.OQL_COMMITMENT)}%", Modifier.weight(weight).fillMaxHeight()) {
    WbLineAdaptive(
      items = oqlItems(lines, c.oqlTarget), vmax = vmax, tick = WbThresholds.OQL_COMMITMENT,
      rankMax = vmax, rankLimit = WbThresholds.OQL_COMMITMENT,
      labelOnBack = false, fmt = { wbF2(it) },
      modifier = Modifier.weight(1f).fillMaxWidth()
    )
  }
}

// ---------- pages ----------

@Composable
private fun WbProductionPage(c: WbCtx, modifier: Modifier) {
  val k = c.d.kpis
  val lines = c.d.linePerformance
  val reported = lines.count { it.achievementPcs != null }
  val overallPct = if (k.todayTargetPcs > 0) k.todayOutputPcs * 100.0 / k.todayTargetPcs else null
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val tm = Modifier.weight(1f).fillMaxHeight()
      WbKpiTile(
        "EFFICIENCY · target ${wbF1(k.todayTargetEfficiency)}%",
        if (c.isToday) "${wbF1(k.todayEfficiency)}%" else "—",
        if (c.isToday) wbEffColor(k.todayEfficiency, k.todayTargetEfficiency) else WbTheme.MUTED,
        if (c.isToday) "Month ${wbF1(k.monthlyEfficiency)}%" else "Targets are daily: Today only", tm
      )
      val gap = k.todayTargetPcs - k.todayOutputPcs
      WbKpiTile(
        "OUTPUT vs TARGET",
        if (c.isToday) "${wbInt(k.todayOutputPcs)} / ${wbInt(k.todayTargetPcs)}" else wbInt(k.todayOutputPcs),
        if (c.isToday) wbAchColor(overallPct) else WbTheme.TXT,
        if (c.isToday) "${wbInt(abs(gap))} pcs ${if (gap > 0) "behind" else "ahead"}" else "target n/a for this period",
        tm, valueSize = 30
      )
      WbKpiTile(
        "LINES REPORTED", "$reported of ${lines.size}",
        if (reported < lines.size) WbTheme.AMBER else WbTheme.GREEN,
        if (reported < lines.size) "${lines.size - reported} line(s) without output entry" else "all scheduled lines reported", tm
      )
      WbKpiTile(
        "DOWNTIME (elapsed)", "${wbInt(c.m.downtimeMin)} min",
        if (c.m.downtimeMin > 0) WbTheme.RED else WbTheme.GREEN,
        "≈ ${wbInt(c.m.lostPcsEst)} pcs lost (est.) · ${c.d.activeIncidents.size} open", tm
      )
      WbKpiTile(
        "OPERATORS / MACHINES", "${k.operatorsCount} / ${k.machinesCount}", WbTheme.TXT,
        "planned counts, not attendance", tm
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WbTargetPanel(c, 5f)
        WbEffPanel(c, 4f)
        WbPanel("Lowest Efficiency Lines", "balancing alert", Modifier.weight(3f).fillMaxHeight()) {
          val low = c.d.lowestEfficiencyLines.filter { it.todayEfficiencyPct != null }.take(5)
          if (!c.isToday) WbEmpty("Ranking is available for Today")
          else if (low.isEmpty()) WbEmpty("No efficiency reported yet")
          else WbRankBars(
            low.map {
              val v = it.todayEfficiencyPct ?: 0.0
              WbRow(it.lineId, v, "${v.toInt()}%", wbEffColor(v, it.targetEfficiencyPct))
            },
            100.0, low.first().targetEfficiencyPct, 40.dp, 15, Modifier.weight(1f).fillMaxWidth()
          )
        }
      }
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ht = c.m.hourlyTarget.toDouble()
        WbPanel(
          "Hourly Output vs Target",
          if (c.isToday && ht > 0) "dashed = ${wbInt(c.m.hourlyTarget)} pcs/hr (floor)" else "floor total",
          Modifier.weight(5f).fillMaxHeight()
        ) {
          val hs = c.m.hours
          if (!c.m.hourlySupported) WbEmpty("Hourly view is for Today or Yesterday")
          else if (hs.isEmpty()) WbEmpty("No hourly output recorded yet")
          else {
            val vmax = maxOf(hs.maxOf { it.output }.toDouble(), if (c.isToday) ht else 0.0, 1.0) * 1.15
            WbColumnChart(
              labels = hs.map { if (it.label.contains("AM") || it.label.contains("PM")) it.label else it.label + "h" },
              back = hs.map<WbHour, Double?> { null },
              front = hs.map<WbHour, Double?> { it.output.toDouble() },
              vmax = vmax,
              frontColor = { i ->
                if (c.isToday && ht > 0) wbAchColor(hs[i].output * 100.0 / ht) else WbTheme.BLUE
              },
              below = hs.map {
                if (c.isToday && ht > 0) {
                  val p = it.output * 100.0 / ht
                  "${p.toInt()}%" to wbAchColor(p)
                } else ("" to WbTheme.MUTED)
              },
              labelOnBack = false, marker = null,
              tick = if (c.isToday && ht > 0) ht else null,
              fmt = { wbInt(it.toInt()) },
              modifier = Modifier.weight(1f).fillMaxWidth()
            )
          }
        }
        WbPanel("Downtime by Cause (min)", "real incidents only", Modifier.weight(4f).fillMaxHeight()) {
          val cs = c.m.causes.take(6)
          if (cs.isEmpty()) WbEmpty("No downtime recorded for this period")
          else WbRankBars(
            cs.mapIndexed { i, x -> WbRow(x.name, x.minutes.toDouble(), "${x.minutes}m", if (i == 0) WbTheme.RED else WbTheme.AMBER) },
            cs.first().minutes.toDouble(), null, 124.dp, 13, Modifier.weight(1f).fillMaxWidth()
          )
        }
        WbPanel("Open Stoppages", "line · cause · open", Modifier.weight(3f).fillMaxHeight()) {
          val act = c.d.activeIncidents.take(5)
          if (act.isEmpty()) WbEmpty("No open stoppage")
          else Column(Modifier.weight(1f).fillMaxWidth()) {
            act.forEach { inc ->
              val mins = ((c.nowMs - inc.startTimeMillis) / 60000L).coerceAtLeast(0L)
              WbDivider()
              Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                WbText(inc.lineId, 20, WbTheme.TXT, FontWeight.Black, Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                  WbText(inc.category.displayName, 14, WbTheme.TXT, FontWeight.Bold)
                  WbText(inc.status.displayName, 12, WbTheme.MUTED, FontWeight.Medium)
                }
                WbText("${mins}m", 18, if (mins > 30) WbTheme.RED else WbTheme.AMBER, FontWeight.Black)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WbQualityPage(c: WbCtx, modifier: Modifier) {
  val k = c.d.kpis
  val lines = c.d.linePerformance
  val worst = lines.filter { it.dhu != null }.maxByOrNull { it.dhu ?: 0.0 }
  val dhuV = c.m.dhu
  val oqlV = c.m.oql
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val tm = Modifier.weight(1f).fillMaxHeight()
      WbKpiTile(
        "DHU · limit ${wbF1(c.dhuLimit)}", dhuV?.let { wbF2(it) } ?: "—",
        if (dhuV != null) wbDhuColor(dhuV, c.dhuLimit) else WbTheme.MUTED,
        worst?.let { "worst ${it.lineId} ${wbF1(it.dhu ?: 0.0)}" } ?: "no inspection yet", tm
      )
      WbKpiTile(
        "OQL · commitment ${wbF1(WbThresholds.OQL_COMMITMENT)}%", oqlV?.let { "${wbF2(it)}%" } ?: "—",
        if (oqlV != null) wbOqlColor(oqlV, c.oqlTarget) else WbTheme.MUTED,
        "target ${wbF1(c.oqlTarget)}%", tm
      )
      WbKpiTile(
        "FIRST-TIME RIGHT (derived)", oqlV?.let { "${wbF1(100.0 - it)}%" } ?: "—",
        if (oqlV != null) WbTheme.GREEN else WbTheme.MUTED, "100 minus OQL", tm
      )
      val pending = k.totalDefectBalance + k.totalSpotBalance
      WbKpiTile(
        "DEFECTS PENDING", "${wbInt(pending)} pcs",
        if (pending > 0) WbTheme.RED else WbTheme.GREEN,
        "sewing ${k.totalDefectBalance} · spot ${k.totalSpotBalance} not returned", tm
      )
      WbKpiTile("CHECKED", "${wbInt(c.m.checkedPcs)} pcs", WbTheme.TXT, "${lines.count { it.dhu != null }} of ${lines.size} lines inspected", tm)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val dMax = roundedMax(lines.mapNotNull { it.dhu }, c.dhuLimit, 1.15)
        WbPanel("DHU by Line", "dashed = ${wbF1(c.dhuLimit)} limit", Modifier.weight(5f).fillMaxHeight()) {
          WbLineAdaptive(
            items = dhuItems(lines, c.dhuLimit), vmax = dMax, tick = c.dhuLimit,
            rankMax = dMax, rankLimit = c.dhuLimit, labelOnBack = false, fmt = { wbF1(it) },
            modifier = Modifier.weight(1f).fillMaxWidth()
          )
        }
        WbOqlPanel(c, 4f)
        WbPanel("Top Defects (Pareto)", "count · cumulative %", Modifier.weight(3f).fillMaxHeight()) {
          val par = c.d.paretoDefects.take(5)
          if (par.isEmpty()) WbEmpty("No defects recorded")
          else WbRankBars(
            par.map { WbRow(it.defectName, it.count.toDouble(), "${it.count} · ${it.cumulativePercentage.toInt()}%", WbTheme.BLUE) },
            par.maxOf { it.count }.toDouble().coerceAtLeast(1.0), null, 92.dp, 13, Modifier.weight(1f).fillMaxWidth()
          )
        }
      }
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WbPanel("Hourly DHU and OQL", "red = DHU · orange = OQL · dashed = DHU limit", Modifier.weight(5f).fillMaxHeight()) {
          val hs = c.m.hours.filter { it.dhu != null || it.oql != null }
          if (!c.m.hourlySupported) WbEmpty("Hourly view is for Today or Yesterday")
          else if (hs.isEmpty()) WbEmpty("No hourly inspection recorded yet")
          else {
            val mx = hs.flatMap { listOfNotNull(it.dhu, it.oql) }.maxOrNull() ?: 1.0
            WbLineChart(
              labels = hs.map { if (it.label.contains("AM") || it.label.contains("PM")) it.label else it.label + "h" },
              series = listOf(WbSeries(hs.map { it.dhu }, WbTheme.RED), WbSeries(hs.map { it.oql }, WbTheme.ORANGE)),
              lo = 0.0, hi = maxOf(mx, c.dhuLimit) * 1.2, limit = c.dhuLimit,
              modifier = Modifier.weight(1f).fillMaxWidth()
            )
          }
        }
        WbPanel("Defect Rectification Pending", "pcs · sewing / spot", Modifier.weight(4f).fillMaxHeight()) {
          val rows = lines
            .map { Triple(it.lineId, it.defectBalance.coerceAtLeast(0), it.spotBalance.coerceAtLeast(0)) }
            .filter { it.second + it.third > 0 }
            .sortedByDescending { it.second + it.third }
            .take(6)
          if (rows.isEmpty()) WbEmpty("No pending defects")
          else WbStackBars(rows, WbTheme.AMBER, WbTheme.BLUE, Modifier.weight(1f).fillMaxWidth())
        }
        WbPanel("Main Defect, Worst Lines", "line · defect · DHU", Modifier.weight(3f).fillMaxHeight()) {
          val w = c.m.worstLineDefects
          if (w.isEmpty()) WbEmpty("No defect entries yet")
          else Column(Modifier.weight(1f).fillMaxWidth()) {
            w.forEach { x ->
              WbDivider()
              Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                WbText(x.lineId, 20, WbTheme.TXT, FontWeight.Black, Modifier.width(44.dp))
                WbText(x.defect, 14, WbTheme.TXT, FontWeight.Bold, Modifier.weight(1f))
                WbText(wbF1(x.dhu), 18, wbDhuColor(x.dhu, c.dhuLimit), FontWeight.Black)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WbAllPage(c: WbCtx, modifier: Modifier) {
  val k = c.d.kpis
  val lines = c.d.linePerformance
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      WbPanel("Today Live Efficiency", "target ${wbF1(k.todayTargetEfficiency)}%", Modifier.weight(1.1f).fillMaxHeight()) {
        WbGauge(
          if (c.isToday) k.todayEfficiency else 0.0, 100.0, k.todayTargetEfficiency,
          if (c.isToday) wbEffColor(k.todayEfficiency, k.todayTargetEfficiency) else WbTheme.MUTED,
          if (c.isToday) "${wbF1(k.todayEfficiency)}%" else "—",
          Modifier.weight(1f).fillMaxWidth()
        )
      }
      WbPanel("Monthly Efficiency", "", Modifier.weight(0.9f).fillMaxHeight()) {
        WbBig("${wbF1(k.monthlyEfficiency)}%", wbEffColor(k.monthlyEfficiency, k.todayTargetEfficiency), "target ${wbF1(k.todayTargetEfficiency)}%")
      }
      WbPanel("Operator vs Machine", "planned", Modifier.weight(1f).fillMaxHeight()) {
        WbDonut(k.operatorsCount, k.machinesCount, WbTheme.ORANGE, WbTheme.BLUE, Modifier.weight(1f).fillMaxWidth())
        WbText("■ ${k.operatorsCount} Operators", 13, WbTheme.ORANGE, FontWeight.Bold)
        WbText("■ ${k.machinesCount} Machines", 13, WbTheme.BLUE, FontWeight.Bold)
      }
      WbPanel("Floor & Factory Efficiency", "bar = month · narrow = today", Modifier.weight(1.4f).fillMaxHeight()) {
        val fk = c.floorKpis
        if (fk.size < 4) WbEmpty("Loading floors...")
        else {
          val names = listOf("Sew 1", "Sew 2", "Sew 3", "Factory")
          WbColumnChart(
            labels = names,
            back = fk.map<Pair<FloorScope, FloorKpiSummary>, Double?> { it.second.monthlyEfficiency },
            front = fk.map<Pair<FloorScope, FloorKpiSummary>, Double?> { if (c.isToday) it.second.todayEfficiency else null },
            vmax = 100.0,
            frontColor = { i -> wbEffColor(fk[i].second.todayEfficiency, fk[i].second.todayTargetEfficiency) },
            below = fk.map {
              if (c.isToday) "${wbF1(it.second.todayEfficiency)}%" to wbEffColor(it.second.todayEfficiency, it.second.todayTargetEfficiency)
              else ("" to WbTheme.MUTED)
            },
            labelOnBack = true, marker = null, tick = k.todayTargetEfficiency, fmt = { wbF1(it) },
            modifier = Modifier.weight(1f).fillMaxWidth()
          )
        }
      }
      WbPanel("BS QA OQL", "IE-entered monthly", Modifier.weight(0.9f).fillMaxHeight()) {
        WbBig("${wbF2(k.bsQaOql)}%", wbOqlColor(k.bsQaOql, c.oqlTarget), "commitment ${wbF1(WbThresholds.OQL_COMMITMENT)}%")
      }
      WbPanel("Today Live OQL", "limit ${wbF1(WbThresholds.OQL_COMMITMENT)}%", Modifier.weight(1f).fillMaxHeight()) {
        val o = c.m.oql
        WbGauge(
          o ?: 0.0, 10.0, WbThresholds.OQL_COMMITMENT,
          if (o != null) wbOqlColor(o, c.oqlTarget) else WbTheme.MUTED,
          o?.let { "${wbF2(it)}%" } ?: "—", Modifier.weight(1f).fillMaxWidth()
        )
      }
      WbPanel("BS QA FRI Pass", "manual monthly", Modifier.weight(0.9f).fillMaxHeight()) {
        WbBig("${wbF1(k.friPassRate)}%", if (k.friPassRate >= FRI_GOOD) WbTheme.GREEN else WbTheme.AMBER, "target ${wbF1(FRI_GOOD)}%")
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WbTargetPanel(c, 5f)
        WbOqlPanel(c, 5f)
        WbPanel("Top 5 Defects", "DHU", Modifier.weight(3f).fillMaxHeight()) {
          val td = c.d.topDefects.take(5)
          if (td.isEmpty()) WbEmpty("No defects recorded")
          else WbRankBars(
            td.map { WbRow(it.defectType, it.dhu, wbF2(it.dhu), WbTheme.BLUE) },
            td.maxOf { it.dhu }.coerceAtLeast(0.01), null, 92.dp, 13, Modifier.weight(1f).fillMaxWidth()
          )
        }
      }
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WbEffPanel(c, 5f)
        val wk = c.weekly.sortedBy { it.weekStartIso }.takeLast(12)
        WbPanel(
          "Weekly BS OQL and NFL AQC",
          if (wk.isEmpty()) "no weekly values entered yet"
          else "last ${wk.size} weeks · dashed = commitment ${wbF1(WbThresholds.OQL_COMMITMENT)}%",
          Modifier.weight(5f).fillMaxHeight()
        ) {
          if (wk.isEmpty()) {
            WbEmpty("Weekly BS OQL and NFL AQC are entered by IE once a week")
          } else {
            val latestBs = wk.lastOrNull { it.bsOqlPct != null }?.bsOqlPct
            val latestAqc = wk.lastOrNull { it.nflAqcPct != null }?.nflAqcPct
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              WbText("■ BS OQL" + (latestBs?.let { " ${wbF2(it)}%" } ?: ""), 12, WbTheme.BLUE, FontWeight.Bold)
              WbText("■ NFL AQC" + (latestAqc?.let { " ${wbF2(it)}%" } ?: ""), 12, WbTheme.ORANGE, FontWeight.Bold)
            }
            val all = wk.flatMap { listOfNotNull(it.bsOqlPct, it.nflAqcPct) } + WbThresholds.OQL_COMMITMENT
            val lo = Math.max(0.0, Math.floor((all.minOrNull() ?: 0.0) - 1.0))
            val hi = Math.ceil((all.maxOrNull() ?: 10.0) + 1.0)
            WbLineChart(
              labels = wk.map { WeeklyQualityWeek.labelFor(it.weekKey) },
              series = listOf(
                WbSeries(wk.map { it.nflAqcPct }, WbTheme.ORANGE),
                WbSeries(wk.map { it.bsOqlPct }, WbTheme.BLUE)
              ),
              lo = lo, hi = hi, limit = WbThresholds.OQL_COMMITMENT,
              modifier = Modifier.weight(1f).fillMaxWidth(),
              showValues = true
            )
          }
        }
        WbPanel("Poor 5 DHU Lines", "dashed = ${wbF1(c.dhuLimit)} limit", Modifier.weight(3f).fillMaxHeight()) {
          val hl = c.d.highestDhuLines.take(5)
          if (hl.isEmpty()) WbEmpty("No DHU data yet")
          else {
            val mx = roundedMax(hl.map { it.dhu }, c.dhuLimit, 1.15)
            WbRankBars(
              hl.map { WbRow(it.lineId, it.dhu, wbF2(it.dhu), wbDhuColor(it.dhu, c.dhuLimit)) },
              mx, c.dhuLimit, 40.dp, 14, Modifier.weight(1f).fillMaxWidth()
            )
          }
        }
      }
    }
  }
}

// ---------- screen ----------

@Composable
fun TvDashboardScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val currentFloor by repository.currentFloor.collectAsStateWithLifecycle()
  val currentPeriod by repository.currentPeriod.collectAsStateWithLifecycle()
  val isDemoMode by repository.isDemoMode.collectAsStateWithLifecycle()
  val incidents by repository.incidents.collectAsStateWithLifecycle()
  val productionEntries by repository.productionEntries.collectAsStateWithLifecycle()
  val qualityEntries by repository.qualityEntries.collectAsStateWithLifecycle()
  val targets by repository.targets.collectAsStateWithLifecycle()
  val monthlyTargets by repository.monthlyTargets.collectAsStateWithLifecycle()
  val weeklyQuality by repository.weeklyQuality.collectAsStateWithLifecycle()
  val lastRefreshTime by repository.lastRefreshTime.collectAsStateWithLifecycle()
  val refreshTrigger by repository.refreshTrigger.collectAsStateWithLifecycle()

  var isScreenActive by remember { mutableStateOf(true) }
  var page by remember { mutableStateOf(WbPage.PRODUCTION) }
  var intervalIdx by remember { mutableIntStateOf(0) }
  val interval = WB_INTERVALS[intervalIdx]
  var countdown by remember { mutableIntStateOf(WB_INTERVALS[0]) }
  var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var syncSeenAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isScreenActive = true }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { isScreenActive = false }

  LaunchedEffect(lastRefreshTime) { syncSeenAt = System.currentTimeMillis() }

  val tvData = remember(
    currentFloor, currentPeriod, isDemoMode, incidents, productionEntries,
    qualityEntries, targets, monthlyTargets, refreshTrigger
  ) {
    repository.getTvDashboardData(currentFloor, currentPeriod)
  }

  val metrics = remember(
    currentFloor, currentPeriod, isDemoMode, incidents, productionEntries,
    qualityEntries, targets, monthlyTargets, refreshTrigger
  ) {
    buildWbMetrics(
      repository, currentFloor, currentPeriod, productionEntries, qualityEntries,
      incidents, tvData.linePerformance, System.currentTimeMillis()
    )
  }

  val onAllPage = page == WbPage.ALL
  val floorKpis = remember(
    currentPeriod, isDemoMode, productionEntries, qualityEntries, targets,
    monthlyTargets, refreshTrigger, onAllPage
  ) {
    if (onAllPage) {
      listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.ALL)
        .map { it to repository.getTvDashboardData(it, currentPeriod).kpis }
    } else emptyList()
  }

  // Auto-slide Production -> Quality -> All. Pauses on the Table page and when the screen is paused.
  LaunchedEffect(isScreenActive, interval, page) {
    while (isScreenActive) {
      delay(1000)
      nowMs = System.currentTimeMillis()
      if (interval > 0 && page != WbPage.TABLE) {
        if (countdown > 1) {
          countdown -= 1
        } else {
          countdown = interval
          page = when (page) {
            WbPage.PRODUCTION -> WbPage.QUALITY
            WbPage.QUALITY -> WbPage.ALL
            WbPage.ALL -> WbPage.PRODUCTION
            WbPage.TABLE -> WbPage.TABLE
          }
        }
      }
    }
  }

  val floorTarget = monthlyTargets.firstOrNull {
    it.lineId.isBlank() && repository.isInSelectedFloor(it.floor, currentFloor)
  }
  val dhuLimit = floorTarget?.targetDhuPct?.takeIf { it > 0.0 } ?: 2.0
  val oqlTarget = floorTarget?.targetOqlPct?.takeIf { it > 0.0 } ?: WbThresholds.OQL_COMMITMENT
  val isToday = currentPeriod == PeriodScope.TODAY
  val lines = tvData.linePerformance

  val floorLinesCount = repository.allLinesMaster.count { line ->
    val fin = line.id.startsWith("Iron", ignoreCase = true) || line.id.startsWith("Packing", ignoreCase = true)
    if (currentFloor == FloorScope.FINISHING) fin || repository.isInSelectedFloor(line.floor, currentFloor)
    else !fin && repository.isInSelectedFloor(line.floor, currentFloor)
  }

  val ctx = WbCtx(
    floor = currentFloor, period = currentPeriod, isToday = isToday, d = tvData, m = metrics,
    dhuLimit = dhuLimit, oqlTarget = oqlTarget, weekly = weeklyQuality, nowMs = nowMs, floorKpis = floorKpis
  )

  val baseDensity = LocalDensity.current
  val tnum = LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = "tnum"))

  BoxWithConstraints(Modifier.fillMaxSize().background(WbTheme.BG)) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    // Design canvas is 1280x720 dp; density scales it to the real TV (720p, 1080p, 4K) so nothing overflows.
    val density = if (widthPx > 0f && heightPx > 0f) {
      minOf(widthPx / 1280f, heightPx / 720f).coerceIn(0.45f, 4.5f)
    } else baseDensity.density

    CompositionLocalProvider(
      LocalDensity provides Density(density = density, fontScale = 1.0f),
      LocalTextStyle provides tnum
    ) {
      Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // ---- header ----
        Row(
          Modifier.fillMaxWidth().height(44.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var backFocused by remember { mutableStateOf(false) }
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier
                .size(30.dp)
                .testTag("tv_back_button")
                .onFocusChanged { backFocused = it.isFocused }
                .border(
                  width = if (backFocused) 2.dp else 0.dp,
                  color = if (backFocused) WbTheme.AMBER else Color.Transparent,
                  shape = CircleShape
                )
                .focusable()
            ) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Navigation",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }
            Surface(color = Color.White, shape = RoundedCornerShape(4.dp)) {
              Row(
                Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_nfl_logo),
                  contentDescription = "NFL Logo",
                  modifier = Modifier.size(18.dp),
                  tint = Color.Unspecified
                )
                WbText("NFL LIVE", 18, Color(0xFF002D62), FontWeight.Black)
              }
            }
          }
          WbSegment(
            items = WbPage.values().toList(), selected = page, label = { it.label },
            fontSp = 14, tagPrefix = "tv_page_"
          ) {
            page = it
            countdown = if (interval > 0) interval else WB_INTERVALS[0]
          }
          WbSegment(
            items = listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.ALL),
            selected = currentFloor, label = { floorLabel(it) }, fontSp = 14, tagPrefix = "tv_floor_"
          ) { repository.setFloor(it) }
          WbSegment(
            items = listOf(PeriodScope.TODAY, PeriodScope.YESTERDAY, PeriodScope.WEEK, PeriodScope.MONTH),
            selected = currentPeriod, label = { it.displayName }, fontSp = 13, tagPrefix = "tv_period_"
          ) { repository.setPeriod(it) }
        }

        // ---- status strip ----
        Row(
          Modifier.fillMaxWidth().height(26.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WbText("${floorLabel(currentFloor)} · ${page.label} · ${currentPeriod.displayName}", 16, WbTheme.TXT, FontWeight.Bold)
            if (isToday) {
              val onT = lines.count { (it.achievementPct ?: -1.0) >= WbThresholds.ACH_GREEN }
              val slight = lines.count { val p = it.achievementPct; p != null && p >= WbThresholds.ACH_AMBER && p < WbThresholds.ACH_GREEN }
              val far = lines.count { val p = it.achievementPct; p != null && p < WbThresholds.ACH_AMBER }
              val none = lines.count { it.achievementPcs == null }
              val notRunning = (floorLinesCount - lines.size).coerceAtLeast(0)
              WbChip("$onT on target", WbTheme.GREEN, Color(0xFF052E22))
              WbChip("$slight slightly behind", WbTheme.AMBER, Color(0xFF3B2A00))
              WbChip("$far far behind", WbTheme.RED, Color(0xFF3B0A0A))
              WbChip("$none no output", WbTheme.GREY, Color(0xFF0F172A))
              WbChip("$notRunning not running", WbTheme.DIM, WbTheme.TXT)
            } else {
              WbChip("Target % shown for Today only", WbTheme.DIM, WbTheme.TXT)
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            var slideFocused by remember { mutableStateOf(false) }
            Surface(
              onClick = {
                intervalIdx = (intervalIdx + 1) % WB_INTERVALS.size
                countdown = if (WB_INTERVALS[intervalIdx] > 0) WB_INTERVALS[intervalIdx] else WB_INTERVALS[0]
              },
              color = WbTheme.PANEL,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier
                .testTag("tv_auto_slide_toggle")
                .onFocusChanged { slideFocused = it.isFocused }
                .border(
                  width = if (slideFocused) 2.dp else 1.dp,
                  color = if (slideFocused) WbTheme.AMBER else WbTheme.LINE,
                  shape = RoundedCornerShape(6.dp)
                )
                .focusable()
            ) {
              Row(
                Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                WbText(
                  if (interval > 0) "Auto-slide ${interval}s · next ${countdown}s" else "Auto-slide paused",
                  13, WbTheme.MUTED, FontWeight.Bold
                )
                listOf(WbPage.PRODUCTION, WbPage.QUALITY, WbPage.ALL).forEach { p ->
                  Box(
                    Modifier
                      .width(22.dp)
                      .height(6.dp)
                      .background(if (p == page) WbTheme.BLUE else WbTheme.DIM, RoundedCornerShape(3.dp))
                  )
                }
              }
            }
            if (isDemoMode) WbChip("DEMO DATA", WbTheme.RED, Color(0xFF3B0A0A))
            val age = ((nowMs - syncSeenAt) / 1000L).coerceAtLeast(0L)
            val stale = age > WbThresholds.STALE_SECONDS
            val ageText = if (age < 60) "${age}s ago" else "${age / 60}m ago"
            WbChip(
              "Synced ${lastRefreshTime.takeLast(8)} · $ageText",
              if (stale) WbTheme.RED else WbTheme.PANEL,
              if (stale) WbTheme.BG else WbTheme.GREEN
            )
          }
        }

        // ---- page body ----
        when (page) {
          WbPage.PRODUCTION -> WbProductionPage(ctx, Modifier.weight(1f))
          WbPage.QUALITY -> WbQualityPage(ctx, Modifier.weight(1f))
          WbPage.ALL -> WbAllPage(ctx, Modifier.weight(1f))
          WbPage.TABLE -> Box(
            Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
              .verticalScroll(rememberScrollState())
          ) {
            LiveLinePerformanceTable(lines = lines)
          }
        }
      }
    }
  }
}
