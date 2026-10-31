package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DailyQaTrend
import com.example.model.NptCategorySlice
import com.example.model.PeriodScope
import java.util.Locale
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface

/**
 * NPT Downtime / Lost Hours Distribution Pie Chart Card
 */
@Composable
fun NptPieChartCard(
  title: String,
  subtitle: String = "Non-productive lost time breakdown by root cause",
  breakdown: List<NptCategorySlice>,
  totalLostPcs: Int? = null,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val totalMinutes = breakdown.sumOf { it.minutes }
  val displaySlices = if (compactMode) breakdown.take(4) else breakdown

  Card(
    shape = RoundedCornerShape(if (compactMode) 8.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(if (compactMode) 6.dp else 14.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 10.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp)) {
          Text(
            text = title,
            fontSize = if (compactMode) 10.5.sp else 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "NPT loss breakdown" else subtitle,
            fontSize = if (compactMode) 7.5.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
        }

        Surface(
          color = Color(0xFFFFF7ED),
          shape = RoundedCornerShape(3.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFED7AA))
        ) {
          Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 8.dp, vertical = if (compactMode) 2.dp else 4.dp)
          ) {
            Text(
              text = "$totalMinutes Mins Lost",
              fontSize = if (compactMode) 9.sp else 11.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFFC2410C)
            )
            if (totalLostPcs != null && totalLostPcs > 0 && !compactMode) {
              Text(
                text = "-$totalLostPcs pcs impact",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEA580C)
              )
            }
          }
        }
      }

      if (breakdown.isEmpty() || totalMinutes == 0) {
        Box(
          modifier = Modifier.fillMaxWidth().height(if (compactMode) 60.dp else 100.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("No NPT incidents recorded for this scope", fontSize = if (compactMode) 9.sp else 11.sp, color = Color(0xFF64748B))
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 12.dp)
        ) {
          // Solid Pie Chart Canvas
          Box(
            modifier = Modifier.size(if (compactMode) 80.dp else 118.dp),
            contentAlignment = Alignment.Center
          ) {
            Canvas(modifier = Modifier.size(if (compactMode) 72.dp else 110.dp)) {
              var startAngle = -90f
              breakdown.forEach { slice ->
                val sweep = (slice.percentage.toFloat() / 100f) * 360f
                val color = Color(slice.colorHex)
                drawArc(
                  color = color,
                  startAngle = startAngle,
                  sweepAngle = sweep,
                  useCenter = true,
                  size = size
                )
                startAngle += sweep
              }
            }
          }

          // Legend and Metrics
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 5.dp)
          ) {
            displaySlices.forEach { slice ->
              val color = Color(slice.colorHex)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp),
                  modifier = Modifier.weight(1f, fill = false)
                ) {
                  Box(
                    modifier = Modifier
                      .size(if (compactMode) 6.dp else 8.dp)
                      .background(color, CircleShape)
                  )
                  Text(
                    text = slice.categoryName,
                    fontSize = if (compactMode) 8.sp else 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                  )
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "${slice.minutes}m",
                    fontSize = if (compactMode) 8.sp else 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                  )
                  Text(
                    text = "(${String.format(Locale.US, "%.0f", slice.percentage)}%)",
                    fontSize = if (compactMode) 7.5.sp else 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Day-by-Day Trend Analytics Card for Weekly & Monthly scopes (Efficiency, OQL, and DHU)
 */
@Composable
fun DayByDayTrendAnalyticsCard(
  title: String,
  subtitle: String,
  periodScope: PeriodScope,
  points: List<DailyQaTrend>,
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableStateOf("EFF") } // "EFF", "OQL", "DHU", "ALL"

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
          )
          Text(
            text = subtitle,
            fontSize = 9.5.sp,
            color = Color(0xFF64748B)
          )
        }

        Surface(
          color = Color(0xFFE0F2FE),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "📅 ${periodScope.displayName} Day-by-Day",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0284C7),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // Tab selector: Efficiency, OQL, DHU, All Combined
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        listOf(
          "EFF" to "⚡ Efficiency",
          "OQL" to "🎯 OQL Trend",
          "DHU" to "🔍 DHU Trend",
          "ALL" to "📊 Table Matrix"
        ).forEach { (tabId, label) ->
          val isSelected = selectedTab == tabId
          Surface(
            onClick = { selectedTab = tabId },
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) Color(0xFF0F172A) else Color.Transparent,
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = label,
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) Color.White else Color(0xFF475569),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 6.dp)
            )
          }
        }
      }

      if (points.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().height(140.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("No trend records available for this period", fontSize = 11.sp, color = Color(0xFF64748B))
        }
      } else {
        when (selectedTab) {
          "EFF" -> EfficiencyDayByDayView(points)
          "OQL" -> OqlDayByDayView(points)
          "DHU" -> DhuDayByDayView(points)
          "ALL" -> DailyTrendTableView(points)
          else -> EfficiencyDayByDayView(points)
        }

        // Daily Breakdown Data Table at the bottom of the card for full transparency
        if (selectedTab != "ALL") {
          HorizontalDivider(color = Color(0xFFF1F5F9))
          DailyTrendTableView(points)
        }
      }
    }
  }
}

/**
 * 1. Efficiency Day-by-Day Line Chart with Data Labels and Target Line
 */
@Composable
private fun EfficiencyDayByDayView(points: List<DailyQaTrend>) {
  val avgEff = points.map { it.efficiencyPct }.average().let { Math.round(it * 10.0) / 10.0 }
  val maxEff = points.maxOfOrNull { it.efficiencyPct } ?: 75.0
  val minEff = points.minOfOrNull { it.efficiencyPct } ?: 65.0

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Quick Stat Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(6.dp)) {
          Text("Avg Efficiency", fontSize = 8.5.sp, color = Color(0xFF047857))
          Text("${avgEff}%", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF065F46))
        }
      }
      Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(6.dp)) {
          Text("Peak Day", fontSize = 8.5.sp, color = Color(0xFF1D4ED8))
          Text("${maxEff}%", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E40AF))
        }
      }
      Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(6.dp)) {
          Text("Lowest Day", fontSize = 8.5.sp, color = Color(0xFFB91C1C))
          Text("${minEff}%", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF991B1B))
        }
      }
    }

    // Legend
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
        Text("Daily Efficiency %", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2563EB)))
        Text("Target (75%)", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
      }
    }

    // Trend Canvas with Day-by-Day nodes and labels
    TrendLineCanvas(
      points = points,
      valueSelector = { it.efficiencyPct },
      targetValue = 75.0,
      lineColor = Color(0xFF10B981),
      targetLineColor = Color(0xFF2563EB),
      isPercentage = true
    )
  }
}

/**
 * 2. OQL Day-by-Day Line Chart
 */
@Composable
private fun OqlDayByDayView(points: List<DailyQaTrend>) {
  val avgBsOql = points.map { it.bsOqlPct }.average().let { Math.round(it * 100.0) / 100.0 }
  val avgNflOql = points.map { it.nflAqcPct }.average().let { Math.round(it * 100.0) / 100.0 }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(6.dp)) {
          Text("Avg BS OQL", fontSize = 8.sp, color = Color(0xFF1D4ED8))
          Text("$avgBsOql%", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
        }
      }
      Surface(color = Color(0xFFFFF7ED), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(6.dp)) {
          Text("Avg NFL OQL", fontSize = 8.sp, color = Color(0xFFC2410C))
          Text("$avgNflOql%", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFC2410C))
        }
      }
      Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(6.dp)) {
          Text("Benchmark", fontSize = 8.sp, color = Color(0xFF475569))
          Text("≤ 5.00%", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF1976D2)))
        Text("BS OQL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
        Text("NFL OQL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFDC2626)))
        Text("5% Limit", fontSize = 9.sp, color = Color(0xFF64748B))
      }
    }

    OqlComparisonTrendCanvas(points)
  }
}

@Composable
private fun OqlComparisonTrendCanvas(points: List<DailyQaTrend>) {
  val maxValue = maxOf(5.0, (points.maxOfOrNull { maxOf(it.bsOqlPct, it.nflAqcPct) } ?: 5.0) * 1.2)
  Canvas(Modifier.fillMaxWidth().height(178.dp).padding(horizontal = 4.dp)) {
    val left = 18f
    val right = size.width - 18f
    val top = 26f
    val bottom = size.height - 26f
    val plotHeight = bottom - top
    fun xAt(index: Int): Float = if (points.size <= 1) (left + right) / 2f
      else left + (right - left) * index / (points.size - 1).toFloat()
    fun yAt(value: Double): Float = bottom - (value / maxValue).toFloat().coerceIn(0f, 1f) * plotHeight

    for (i in 0..4) {
      val y = top + plotHeight * i / 4f
      drawLine(Color(0xFFE2E8F0), Offset(left, y), Offset(right, y), strokeWidth = 1f)
    }

    val targetY = yAt(5.0)
    drawLine(
      Color(0xFFDC2626).copy(alpha = 0.75f),
      Offset(left, targetY),
      Offset(right, targetY),
      strokeWidth = 1.5f,
      pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    )

    fun drawSeries(selector: (DailyQaTrend) -> Double, color: Color) {
      val path = Path()
      points.forEachIndexed { index, point ->
        val p = Offset(xAt(index), yAt(selector(point)))
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        drawCircle(color, radius = 3.2f, center = p)
      }
      if (points.size > 1) drawPath(path, color, style = Stroke(width = 2.8f))
    }
    drawSeries({ it.bsOqlPct }, Color(0xFF1976D2))
    drawSeries({ it.nflAqcPct }, Color(0xFFF59E0B))

    val bsLabelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
      color = Color(0xFF1D4ED8).toArgb()
      textSize = 9.sp.toPx()
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = AndroidPaint.Align.CENTER
    }
    val nflLabelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
      color = Color(0xFFC2410C).toArgb()
      textSize = 9.sp.toPx()
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = AndroidPaint.Align.CENTER
    }
    val labelStep = if (points.size > 10) 2 else 1
    points.forEachIndexed { index, point ->
      if (index % labelStep == 0 || index == points.lastIndex) {
        val x = xAt(index)
        val bsY = (yAt(point.bsOqlPct) - 8.dp.toPx()).coerceAtLeast(top + 9.dp.toPx())
        val nflY = (yAt(point.nflAqcPct) + 15.dp.toPx()).coerceAtMost(bottom - 2.dp.toPx())
        drawContext.canvas.nativeCanvas.drawText(
          String.format(Locale.US, "%.1f%%", point.bsOqlPct), x, bsY, bsLabelPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
          String.format(Locale.US, "%.1f%%", point.nflAqcPct), x, nflY, nflLabelPaint
        )
      }
    }
  }
}

/**
 * 3. DHU Day-by-Day Line Chart (Direct Numeric Values without %)
 */
@Composable
private fun DhuDayByDayView(points: List<DailyQaTrend>) {
  val avgDhu = points.map { it.dhu }.average().let { Math.round(it * 100.0) / 100.0 }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Quick Stat Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Surface(color = Color(0xFFF3E8FF), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(6.dp)) {
          Text("Period Avg DHU", fontSize = 8.5.sp, color = Color(0xFF7E22CE))
          Text(String.format(Locale.US, "%.2f", avgDhu), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B21A8))
        }
      }
      Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(6.dp)) {
          Text("Target Standard", fontSize = 8.5.sp, color = Color(0xFF475569))
          Text("≤ 1.50", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
        }
      }
      Surface(color = if (avgDhu <= 1.50) Color(0xFFECFDF5) else Color(0xFFFEF2F2), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(6.dp)) {
          Text("Quality Rating", fontSize = 8.5.sp, color = if (avgDhu <= 1.50) Color(0xFF047857) else Color(0xFFB91C1C))
          Text(if (avgDhu <= 1.50) "EXCELLENT" else "ATTENTION", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (avgDhu <= 1.50) Color(0xFF065F46) else Color(0xFF991B1B))
        }
      }
    }

    // Legend
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8B5CF6)))
        Text("Daily Endline DHU (Numeric)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0284C7)))
        Text("Target Standard (1.50)", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
      }
    }

    TrendLineCanvas(
      points = points,
      valueSelector = { it.dhu },
      targetValue = 1.50,
      lineColor = Color(0xFF8B5CF6),
      targetLineColor = Color(0xFF0284C7),
      isPercentage = false // Direct numeric value as requested!
    )
  }
}

/**
 * Reusable Canvas Line Plot for Day-by-Day Trends
 */
@Composable
private fun TrendLineCanvas(
  points: List<DailyQaTrend>,
  valueSelector: (DailyQaTrend) -> Double,
  targetValue: Double,
  lineColor: Color,
  targetLineColor: Color,
  isPercentage: Boolean
) {
  val maxValue = maxOf(targetValue * 1.25, (points.maxOfOrNull { valueSelector(it) } ?: targetValue) * 1.20)
  val minValue = 0.0

  Column(modifier = Modifier.fillMaxWidth()) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(130.dp)
        .padding(horizontal = 6.dp)
    ) {
      val left = 16f
      val right = size.width - 16f
      val top = 22f
      val bottom = size.height - 18f
      val plotHeight = bottom - top

      // Horizontal grid lines
      for (i in 0..3) {
        val y = top + (plotHeight * i / 3f)
        drawLine(
          color = Color(0xFFF1F5F9),
          start = Offset(left, y),
          end = Offset(right, y),
          strokeWidth = 1.5f
        )
      }

      fun getX(index: Int): Float {
        return if (points.size <= 1) (left + right) / 2f
        else left + (right - left) * (index.toFloat() / (points.size - 1).toFloat())
      }

      fun getY(value: Double): Float {
        val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
        return bottom - (fraction * plotHeight)
      }

      // Target Reference Line
      val targetY = getY(targetValue)
      drawLine(
        color = targetLineColor.copy(alpha = 0.6f),
        start = Offset(left, targetY),
        end = Offset(right, targetY),
        strokeWidth = 2f
      )

      // Actual Data Line
      val path = Path()
      points.forEachIndexed { index, item ->
        val x = getX(index)
        val y = getY(valueSelector(item))
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = 3.5f)
      )

      // Points & Data Dots
      points.forEachIndexed { index, item ->
        val x = getX(index)
        val y = getY(valueSelector(item))

        // Outer halo
        drawCircle(
          color = lineColor.copy(alpha = 0.2f),
          radius = 6.5f,
          center = Offset(x, y)
        )
        // Solid core
        drawCircle(
          color = lineColor,
          radius = 3.5f,
          center = Offset(x, y)
        )
      }
    }

    // Day & Date Labels Row below the Canvas
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      points.forEach { item ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(horizontal = 1.dp)
        ) {
          val valueStr = if (isPercentage) {
            "${String.format(Locale.US, "%.1f", valueSelector(item))}%"
          } else {
            String.format(Locale.US, "%.2f", valueSelector(item))
          }

          Text(
            text = valueStr,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            color = lineColor
          )
          Text(
            text = item.dayName.ifEmpty { item.dateLabel.take(3) },
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
          )
          Text(
            text = item.dateLabel.substringBefore(" "),
            fontSize = 7.5.sp,
            color = Color(0xFF94A3B8)
          )
        }
      }
    }
  }
}

/**
 * 4. Daily Performance Matrix Table (Day | Eff % | OQL % | DHU | Output)
 */
@Composable
private fun DailyTrendTableView(points: List<DailyQaTrend>) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Text(
      text = "📋 Day-by-Day Performance Matrix:",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF1E293B)
    )

    Surface(
      shape = RoundedCornerShape(8.dp),
      color = Color(0xFFF8FAFC),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // Table Header
        Row(
          modifier = Modifier.padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Date / Day", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(65.dp))
          Text("Efficiency", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857), textAlign = TextAlign.End, modifier = Modifier.width(55.dp))
          Text("BS OQL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
          Text("NFL OQL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C), textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
          Text("DHU", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7E22CE), textAlign = TextAlign.End, modifier = Modifier.width(45.dp))
          Text("Output Pcs", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
          Text("Target Pcs", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
        }

        HorizontalDivider(color = Color(0xFFE2E8F0))

        // Table Rows
        points.forEach { p ->
          Row(
            modifier = Modifier.padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.width(65.dp)) {
              Text("${p.dateLabel}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
              Text("${p.dayName}", fontSize = 8.sp, color = Color(0xFF94A3B8))
            }

            Text(
              text = "${p.efficiencyPct}%",
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (p.efficiencyPct >= 75.0) Color(0xFF16A34A) else Color(0xFFD97706),
              textAlign = TextAlign.End,
              modifier = Modifier.width(55.dp)
            )

            Text(
              text = "${p.bsOqlPct}%",
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (p.bsOqlPct <= 5.0) Color(0xFF16A34A) else Color(0xFFDC2626),
              textAlign = TextAlign.End,
              modifier = Modifier.width(48.dp)
            )
            
            Text(
              text = "${p.nflAqcPct}%",
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (p.nflAqcPct <= 5.0) Color(0xFF16A34A) else Color(0xFFDC2626),
              textAlign = TextAlign.End,
              modifier = Modifier.width(52.dp)
            )

            Text(
              text = String.format(Locale.US, "%.2f", p.dhu),
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = if (p.dhu <= 1.50) Color(0xFF7E22CE) else Color(0xFFDC2626),
              textAlign = TextAlign.End,
              modifier = Modifier.width(45.dp)
            )

            Text(
              text = "${p.outputPcs}",
              fontSize = 9.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF0F172A),
              textAlign = TextAlign.End,
              modifier = Modifier.width(60.dp)
            )

            Text(
              text = "${p.targetPcs}",
              fontSize = 9.sp,
              color = Color(0xFF64748B),
              textAlign = TextAlign.End,
              modifier = Modifier.width(60.dp)
            )
          }
        }
      }
    }
  }
}
