package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Data model for Weekly BS OQL point.
 */
data class BsOqlWeeklyPoint(
  val week: String,
  val oqlPct: Double
)

/**
 * Standard Insaf Life Weekly Dataset for BS OQL (Week 32 to Week 43)
 */
val DEFAULT_BS_OQL_WEEKLY_DATA: List<BsOqlWeeklyPoint> = listOf(
  BsOqlWeeklyPoint("32 W", 4.67),
  BsOqlWeeklyPoint("33 W", 5.00),
  BsOqlWeeklyPoint("34 W", 5.00),
  BsOqlWeeklyPoint("35 W", 5.11),
  BsOqlWeeklyPoint("36 W", 4.80),
  BsOqlWeeklyPoint("37 W", 4.80),
  BsOqlWeeklyPoint("38 W", 5.00),
  BsOqlWeeklyPoint("39 W", 5.00),
  BsOqlWeeklyPoint("40 W", 5.35),
  BsOqlWeeklyPoint("41 W", 5.10),
  BsOqlWeeklyPoint("42 W", 5.64),
  BsOqlWeeklyPoint("43 W", 5.58)
)

/**
 * Weekly Trend Chart for BS OQL
 *
 * Requirements satisfied:
 * 1. Clean responsive Card container matching dashboard styling (borders, padding, elevation)
 * 2. X-Axis: Categorical Weeks (32 W to 43 W)
 * 3. Y-Axis: Scaled appropriately between ~4.0% and 6.5% with '%' formatting
 * 4. Interactive Tooltip: Shows week label, exact percentage value (e.g. "BS OQL: 5.58%"), and target comparison
 * 5. Smooth curved line with tension/monotone cubic Bezier path
 * 6. Highlighted dots at all data points with active tap/scrub highlight
 * 7. Card header: "BS OQL Trend Analysis (Week 32 - Week 43)"
 */
@Composable
fun BsOqlWeeklyTrendChartCard(
  modifier: Modifier = Modifier,
  data: List<BsOqlWeeklyPoint> = DEFAULT_BS_OQL_WEEKLY_DATA,
  benchmarkTargetPct: Double = 5.00,
  compactMode: Boolean = false
) {
  // Track selected point index for interactive tooltip (defaults to latest: 43 W)
  var selectedIndex by remember(data) {
    mutableIntStateOf(if (data.isNotEmpty()) data.size - 1 else -1)
  }

  val activePoint = remember(selectedIndex, data) {
    if (selectedIndex in data.indices) data[selectedIndex] else data.lastOrNull()
  }

  // Calculate summary metrics
  val avgOql = remember(data) {
    if (data.isNotEmpty()) data.map { it.oqlPct }.average() else 0.0
  }
  val minOql = remember(data) { data.minOfOrNull { it.oqlPct } ?: 4.0 }
  val maxOql = remember(data) { data.maxOfOrNull { it.oqlPct } ?: 6.5 }
  val latestPoint = data.lastOrNull()

  // Y-axis range: Scaled strictly between 4.0% and 6.5% to clearly visualize the trend
  val yMin = 4.0
  val yMax = 6.5
  val ySteps = listOf(6.5, 6.0, 5.5, 5.0, 4.5, 4.0)

  Card(
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .then(if (compactMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        .padding(if (compactMode) 5.dp else 10.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 6.dp)
    ) {
      // 1. CARD HEADER
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 8.dp),
          modifier = Modifier.weight(1f, fill = false)
        ) {
          if (!compactMode) {
            Surface(
              color = Color(0xFFEFF6FF),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.size(34.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.ShowChart,
                  contentDescription = null,
                  tint = Color(0xFF0284C7),
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }

          Column {
            Text(
              text = "BS OQL Trend Analysis (Week 32 - Week 43)",
              fontSize = if (compactMode) 9.5.sp else 13.5.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF0F172A),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = if (compactMode) "Avg: ${String.format(Locale.US, "%.2f", avgOql)}% • Latest (${latestPoint?.week ?: "43 W"}): ${String.format(Locale.US, "%.2f", latestPoint?.oqlPct ?: 5.58)}%"
              else "Insaf Life Quality Module • Buyer Standard Outgoing Quality Level",
              fontSize = if (compactMode) 7.sp else 10.sp,
              color = Color(0xFF64748B),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          color = Color(0xFFF1F5F9),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "Std ≤ ${String.format(Locale.US, "%.2f", benchmarkTargetPct)}%",
            fontSize = if (compactMode) 7.sp else 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0369A1),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 7.dp, vertical = if (compactMode) 1.5.dp else 3.dp)
          )
        }
      }

      // 2. QUICK STAT SUMMARY TILES (Full tiles in standard mode)
      if (!compactMode) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          StatTile(
            label = "Latest (${latestPoint?.week ?: "43 W"})",
            value = "${String.format(Locale.US, "%.2f", latestPoint?.oqlPct ?: 5.58)}%",
            subtext = if ((latestPoint?.oqlPct ?: 0.0) <= benchmarkTargetPct) "Within Standard" else "Auditor Attention",
            accentColor = if ((latestPoint?.oqlPct ?: 0.0) <= benchmarkTargetPct) Color(0xFF10B981) else Color(0xFFD97706),
            modifier = Modifier.weight(1f)
          )
          StatTile(
            label = "12-Wk Average",
            value = "${String.format(Locale.US, "%.2f", avgOql)}%",
            subtext = "Mean Performance",
            accentColor = Color(0xFF0284C7),
            modifier = Modifier.weight(1f)
          )
          StatTile(
            label = "Min / Max",
            value = "${String.format(Locale.US, "%.2f", minOql)}% / ${String.format(Locale.US, "%.2f", maxOql)}%",
            subtext = "Spread: ${(maxOql - minOql).let { String.format(Locale.US, "%.2f", it) }}%",
            accentColor = Color(0xFF7C3AED),
            modifier = Modifier.weight(1f)
          )
        }
      }

      // 3. INTERACTIVE TOOLTIP CALLOUT BAR
      if (activePoint != null) {
        Surface(
          color = Color(0xFF0A2558),
          shape = RoundedCornerShape(if (compactMode) 4.dp else 8.dp),
          modifier = Modifier.fillMaxWidth().testTag("bs_oql_tooltip_bar")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = if (compactMode) 6.dp else 10.dp, vertical = if (compactMode) 2.5.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(if (compactMode) 6.dp else 8.dp)
                  .background(Color(0xFF38BDF8), CircleShape)
              )
              Text(
                text = "Week ${activePoint.week}",
                color = Color.White,
                fontSize = if (compactMode) 8.5.sp else 11.sp,
                fontWeight = FontWeight.Black
              )
              Text(
                text = "•",
                color = Color(0xFF94A3B8),
                fontSize = if (compactMode) 8.sp else 10.sp
              )
              Text(
                text = "BS OQL: ${String.format(Locale.US, "%.2f", activePoint.oqlPct)}%",
                color = Color(0xFF7DD3FC),
                fontSize = if (compactMode) 8.5.sp else 12.sp,
                fontWeight = FontWeight.Black
              )
            }

            val diff = activePoint.oqlPct - benchmarkTargetPct
            val statusColor = if (diff <= 0.0) Color(0xFF10B981) else Color(0xFFF59E0B)
            Surface(
              color = statusColor.copy(alpha = 0.2f),
              shape = RoundedCornerShape(3.dp)
            ) {
              Text(
                text = if (diff <= 0.0) "✓ Standard Met" else "Gap: +${String.format(Locale.US, "%.2f", diff)}%",
                color = if (diff <= 0.0) Color(0xFF34D399) else Color(0xFFFBBF24),
                fontSize = if (compactMode) 7.5.sp else 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = if (compactMode) 1.dp else 2.dp)
              )
            }
          }
        }
      }

      // 4. CHART CANVAS WITH SMOOTH BEZIER CURVE, Y-AXIS (4.0% - 6.5%), AND TOOLTIP SCRUBBING
      BoxWithConstraints(
        modifier = if (compactMode) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(135.dp)
      ) {
        val yLabelWidth = if (compactMode) 28.dp else 38.dp

        Row(
          modifier = Modifier.fillMaxSize()
        ) {
          // Left Y-Axis labels (6.5% to 4.0%)
          Column(
            modifier = Modifier
              .width(yLabelWidth)
              .fillMaxHeight()
              .padding(vertical = if (compactMode) 2.dp else 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
          ) {
            ySteps.forEach { stepVal ->
              Text(
                text = "${String.format(Locale.US, "%.1f", stepVal)}%",
                fontSize = if (compactMode) 6.sp else 8.5.sp,
                fontWeight = if (stepVal == benchmarkTargetPct) FontWeight.Bold else FontWeight.Medium,
                color = if (stepVal == benchmarkTargetPct) Color(0xFFD97706) else Color(0xFF64748B),
                modifier = Modifier.padding(end = if (compactMode) 3.dp else 6.dp)
              )
            }
          }

          // Main Canvas with gestures
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          ) {
            Canvas(
              modifier = Modifier
                .fillMaxSize()
                .pointerInput(data) {
                  detectTapGestures { offset ->
                    if (data.isNotEmpty()) {
                      val leftPad = 12f
                      val rightPad = size.width - 12f
                      val usableW = (rightPad - leftPad).coerceAtLeast(1f)
                      val fraction = ((offset.x - leftPad) / usableW).coerceIn(0f, 1f)
                      val idx = (fraction * (data.size - 1)).toInt().coerceIn(0, data.size - 1)
                      selectedIndex = idx
                    }
                  }
                }
                .pointerInput(data) {
                  detectDragGestures { change, _ ->
                    change.consume()
                    if (data.isNotEmpty()) {
                      val leftPad = 12f
                      val rightPad = size.width - 12f
                      val usableW = (rightPad - leftPad).coerceAtLeast(1f)
                      val fraction = ((change.position.x - leftPad) / usableW).coerceIn(0f, 1f)
                      val idx = (fraction * (data.size - 1)).toInt().coerceIn(0, data.size - 1)
                      selectedIndex = idx
                    }
                  }
                }
            ) {
              val w = size.width
              val h = size.height

              val left = 12f
              val right = w - 12f
              val top = 10f
              val bottom = h - 10f
              val plotWidth = right - left
              val plotHeight = bottom - top

              fun getX(index: Int): Float {
                return if (data.size <= 1) (left + right) / 2f
                else left + (plotWidth * (index.toFloat() / (data.size - 1).toFloat()))
              }

              fun getY(value: Double): Float {
                val clamped = value.coerceIn(yMin, yMax)
                val fraction = ((clamped - yMin) / (yMax - yMin)).toFloat()
                return bottom - (fraction * plotHeight)
              }

              // 1. Draw horizontal grid lines for 4.0% to 6.5%
              ySteps.forEach { stepVal ->
                val lineY = getY(stepVal)
                drawLine(
                  color = if (stepVal == benchmarkTargetPct) Color(0xFFFDE68A) else Color(0xFFF1F5F9),
                  start = Offset(left, lineY),
                  end = Offset(right, lineY),
                  strokeWidth = if (stepVal == benchmarkTargetPct) 1.5f else 1.0f
                )
              }

              // 2. Benchmark Target Reference Line (Dashed amber/rose line at 5.00%)
              val benchmarkY = getY(benchmarkTargetPct)
              drawLine(
                color = Color(0xFFD97706).copy(alpha = 0.8f),
                start = Offset(left, benchmarkY),
                end = Offset(right, benchmarkY),
                strokeWidth = 2.0f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
              )

              if (data.size >= 2) {
                // Calculate point coordinates
                val pointCoords = data.indices.map { i ->
                  Offset(getX(i), getY(data[i].oqlPct))
                }

                // 3. Build Smooth Catmull-Rom / Monotone Cubic Bezier Path
                val curvePath = Path()
                curvePath.moveTo(pointCoords[0].x, pointCoords[0].y)

                for (i in 0 until pointCoords.size - 1) {
                  val p0 = pointCoords.getOrElse(i - 1) { pointCoords[i] }
                  val p1 = pointCoords[i]
                  val p2 = pointCoords[i + 1]
                  val p3 = pointCoords.getOrElse(i + 2) { p2 }

                  // Tension parameter (0.35 gives natural smooth garment QA curve)
                  val tension = 0.35f
                  val cp1X = p1.x + (p2.x - p0.x) * tension
                  val cp1Y = p1.y + (p2.y - p0.y) * tension
                  val cp2X = p2.x - (p3.x - p1.x) * tension
                  val cp2Y = p2.y - (p3.y - p1.y) * tension

                  curvePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                }

                // 4. Draw subtle vertical gradient area under curve
                val fillPath = Path()
                fillPath.addPath(curvePath)
                fillPath.lineTo(pointCoords.last().x, bottom)
                fillPath.lineTo(pointCoords.first().x, bottom)
                fillPath.close()

                drawPath(
                  path = fillPath,
                  brush = Brush.verticalGradient(
                    colors = listOf(
                      Color(0xFF0284C7).copy(alpha = 0.22f),
                      Color(0xFF0284C7).copy(alpha = 0.02f),
                      Color.Transparent
                    ),
                    startY = top,
                    endY = bottom
                  )
                )

                // 5. Draw smooth curved stroke
                drawPath(
                  path = curvePath,
                  color = Color(0xFF0284C7),
                  style = Stroke(
                    width = (if (compactMode) 2.5.dp else 3.5.dp).toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                  )
                )

                // 6. Active scrubber indicator line if point is selected
                if (selectedIndex in pointCoords.indices) {
                  val activeX = pointCoords[selectedIndex].x
                  drawLine(
                    color = Color(0xFF94A3B8).copy(alpha = 0.6f),
                    start = Offset(activeX, top),
                    end = Offset(activeX, bottom),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                  )
                }

                // 7. Data point dots
                pointCoords.forEachIndexed { idx, pt ->
                  val isSelected = idx == selectedIndex
                  val pointVal = data[idx].oqlPct
                  val isAlert = pointVal > benchmarkTargetPct

                  // Outer glowing halo
                  drawCircle(
                    color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.35f)
                            else if (isAlert) Color(0xFFD97706).copy(alpha = 0.25f)
                            else Color(0xFF0284C7).copy(alpha = 0.18f),
                    radius = (if (isSelected) 8.dp else 5.dp).toPx(),
                    center = pt
                  )

                  // White border
                  drawCircle(
                    color = Color.White,
                    radius = (if (isSelected) 5.dp else 3.5.dp).toPx(),
                    center = pt
                  )

                  // Inner core dot
                  drawCircle(
                    color = if (isAlert) Color(0xFFD97706) else Color(0xFF0284C7),
                    radius = (if (isSelected) 3.5.dp else 2.5.dp).toPx(),
                    center = pt
                  )
                }
              }
            }
          }
        }
      }

      // 5. X-AXIS CATEGORICAL LABELS (32 W to 43 W)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 38.dp, end = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        data.forEachIndexed { idx, point ->
          val isSelected = idx == selectedIndex
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 1.dp)
          ) {
            Text(
              text = point.week,
              fontSize = if (compactMode) 7.5.sp else 8.5.sp,
              fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
              color = if (isSelected) Color(0xFF0284C7) else Color(0xFF64748B)
            )
          }
        }
      }

      // 6. LEGEND & FOOTNOTE
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .background(Color(0xFF0284C7), CircleShape)
            )
            Text(
              text = "BS OQL % (Monotone Curve)",
              fontSize = 8.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF475569)
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              modifier = Modifier
                .width(12.dp)
                .height(2.dp)
                .background(Color(0xFFD97706))
            )
            Text(
              text = "5.00% Standard Limit",
              fontSize = 8.5.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF64748B)
            )
          }
        }

        Text(
          text = "Tap or scrub chart to inspect week",
          fontSize = 8.sp,
          color = Color(0xFF94A3B8)
        )
      }
    }
  }
}

@Composable
private fun StatTile(
  label: String,
  value: String,
  subtext: String,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = accentColor.copy(alpha = 0.07f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
      Text(
        text = label,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF64748B),
        maxLines = 1
      )
      Text(
        text = value,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        color = accentColor,
        maxLines = 1
      )
      Text(
        text = subtext,
        fontSize = 8.sp,
        color = Color(0xFF94A3B8),
        maxLines = 1
      )
    }
  }
}
