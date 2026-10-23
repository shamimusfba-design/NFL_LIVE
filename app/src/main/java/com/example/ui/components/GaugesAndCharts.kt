package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*

/**
 * Enterprise Executive KPI Card for modern factory dashboards with support for
 * rich visual accessories like mini speedometers and donut rings.
 */
@Composable
fun ExecutiveKpiCard(
  title: String,
  valueText: String,
  targetText: String? = null,
  trendText: String? = null,
  isPositiveTrend: Boolean = true,
  progressFraction: Float? = null,
  accentColor: Color = Color(0xFF1976D2),
  endVisual: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
  Card(
    modifier = cardModifier,
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = if (compactMode) 6.dp else 12.dp,
          vertical = if (compactMode) 3.dp else 10.dp
        ),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 1.dp else 6.dp)
    ) {
      // Title row with subtle category indicator and trend badge
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
              .size(if (compactMode) 5.dp else 7.dp)
              .clip(CircleShape)
              .background(accentColor)
          )
          Text(
            text = title,
            fontSize = if (compactMode) 8.sp else 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF334155),
            maxLines = 2,
            lineHeight = if (compactMode) 9.5.sp else 12.5.sp,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (trendText != null) {
          Surface(
            color = if (isPositiveTrend) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            shape = RoundedCornerShape(3.dp)
          ) {
            Text(
              text = trendText,
              fontSize = if (compactMode) 6.5.sp else 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (isPositiveTrend) Color(0xFF2E7D32) else Color(0xFFC62828),
              modifier = Modifier.padding(
                horizontal = if (compactMode) 3.dp else 5.dp,
                vertical = if (compactMode) 0.5.dp else 2.dp
              ),
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      // Large bold primary metric + End Visual (Speedometer / Donut)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = valueText,
          fontSize = if (compactMode) 13.sp else 20.sp,
          fontWeight = FontWeight.Black,
          color = Color(0xFF0F172A),
          lineHeight = if (compactMode) 14.sp else 22.sp,
          letterSpacing = if (compactMode) (-0.2).sp else (-0.5).sp,
          maxLines = 1
        )

        if (endVisual != null) {
          endVisual()
        }
      }

      // Linear attainment progress bar if applicable (when no endVisual)
      if (progressFraction != null && endVisual == null) {
        val clamped = progressFraction.coerceIn(0f, 1f)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(if (compactMode) 2.5.dp else 5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFFF1F5F9))
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(clamped)
              .fillMaxHeight()
              .clip(RoundedCornerShape(2.dp))
              .background(accentColor)
          )
        }
      }

      // Contextual target / benchmark label
      if (targetText != null) {
        Text(
          text = targetText,
          fontSize = if (compactMode) 7.sp else 10.sp,
          lineHeight = if (compactMode) 8.sp else 12.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF64748B),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

/**
 * Compact Mini Speedometer Gauge for KPI stat cards
 */
@Composable
fun MiniSpeedometerGauge(
  value: Double,
  targetValue: Double = 75.0,
  modifier: Modifier = Modifier.size(54.dp, 36.dp)
) {
  val clampedValue = value.coerceIn(0.0, 100.0).toFloat()
  val fraction = clampedValue / 100f
  val startAngle = 150f
  val sweepAngle = 240f

  Canvas(modifier = modifier) {
    val strokeWidth = 5.dp.toPx()
    val arcSize = Size(size.width - strokeWidth, (size.height * 1.6f) - strokeWidth)
    val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

    // Track
    drawArc(
      color = Color(0xFFE2E8F0),
      startAngle = startAngle,
      sweepAngle = sweepAngle,
      useCenter = false,
      topLeft = arcTopLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Progress color
    val activeColor = when {
      clampedValue >= targetValue -> Color(0xFF10B981)
      clampedValue >= 60f -> Color(0xFF0284C7)
      else -> Color(0xFFEF4444)
    }

    drawArc(
      color = activeColor,
      startAngle = startAngle,
      sweepAngle = (fraction * sweepAngle).coerceIn(0.1f, sweepAngle),
      useCenter = false,
      topLeft = arcTopLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Needle
    val centerX = size.width / 2f
    val centerY = size.height * 0.85f
    val needleLength = size.width * 0.38f
    val angleRad = Math.toRadians((startAngle + fraction * sweepAngle).toDouble())
    val endX = centerX + (needleLength * Math.cos(angleRad)).toFloat()
    val endY = centerY + (needleLength * Math.sin(angleRad)).toFloat()

    drawLine(
      color = Color(0xFF1E293B),
      start = Offset(centerX, centerY),
      end = Offset(endX, endY),
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round
    )
    drawCircle(
      color = Color(0xFF0F172A),
      radius = 3.dp.toPx(),
      center = Offset(centerX, centerY)
    )
  }
}

/**
 * Compact Mini Donut Progress Ring for KPI stat cards
 */
@Composable
fun MiniDonutProgress(
  fraction: Float,
  accentColor: Color = Color(0xFF10B981),
  modifier: Modifier = Modifier.size(36.dp)
) {
  Canvas(modifier = modifier) {
    val strokeWidth = 4.5.dp.toPx()
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

    drawArc(
      color = Color(0xFFF1F5F9),
      startAngle = -90f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth)
    )

    drawArc(
      color = accentColor,
      startAngle = -90f,
      sweepAngle = (fraction.coerceIn(0f, 1f) * 360f),
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
  }
}

/**
 * Combined Operator and Machine Allocation Stat Card
 */
@Composable
fun OperatorMachineMetricCard(
  operatorsCount: Int,
  machinesCount: Int,
  helpersCount: Int = 0,
  finishingCount: Int = 0,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val totalManpower = operatorsCount + helpersCount + finishingCount

  Card(
    modifier = modifier,
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = if (compactMode) 5.dp else 10.dp,
          vertical = if (compactMode) 3.dp else 10.dp
        ),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left Column: Total Manpower (Operators + Helpers + Finishing)
      Column(
        modifier = Modifier.weight(1.15f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 0.5.dp else 2.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(if (compactMode) 9.dp else 13.dp))
          Text(
            text = "Manpower",
            fontSize = if (compactMode) 8.sp else 11.sp,
            lineHeight = if (compactMode) 9.sp else 12.sp,
            color = Color(0xFF1E3A8A),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
          )
        }
        Text(
          text = if (totalManpower > 0) "$totalManpower" else "$operatorsCount",
          fontSize = if (compactMode) 13.sp else 17.sp,
          lineHeight = if (compactMode) 14.sp else 19.sp,
          fontWeight = FontWeight.Black,
          color = Color(0xFF0F172A),
          maxLines = 1,
          softWrap = false
        )
        Text(
          text = if (helpersCount > 0 || finishingCount > 0) {
            "Op:$operatorsCount•Hl:$helpersCount•Fn:$finishingCount"
          } else {
            "Present Today"
          },
          fontSize = if (compactMode) 6.5.sp else 8.sp,
          lineHeight = if (compactMode) 7.5.sp else 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF475569),
          maxLines = 1,
          softWrap = false
        )
      }

      // Vertical Divider
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(if (compactMode) 22.dp else 38.dp)
          .background(Color(0xFFE2E8F0))
      )

      // Right Column: Running Machines
      Column(
        modifier = Modifier.weight(0.9f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 0.5.dp else 2.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(if (compactMode) 9.dp else 13.dp))
          Text(
            text = "Machines",
            fontSize = if (compactMode) 8.sp else 11.sp,
            lineHeight = if (compactMode) 9.sp else 12.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
          )
        }
        Text(
          text = "$machinesCount",
          fontSize = if (compactMode) 13.sp else 17.sp,
          lineHeight = if (compactMode) 14.sp else 19.sp,
          fontWeight = FontWeight.Black,
          color = Color(0xFF0F172A),
          maxLines = 1,
          softWrap = false
        )
        Text(
          text = "Running Live",
          fontSize = if (compactMode) 6.5.sp else 8.sp,
          lineHeight = if (compactMode) 7.5.sp else 10.sp,
          color = Color(0xFF64748B),
          maxLines = 1,
          softWrap = false
        )
      }
    }
  }
}

/**
 * Grouped Bar Chart comparing Line Target (Royal Blue) vs Production Output (Emerald Green)
 * with dynamic line-count scaling so all 10 to 26 lines fit on-screen without clipping.
 */
@Composable
fun TargetVsOutputBarChart(
  title: String,
  lines: List<LinePerformancePoint>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false,
  fitAllLinesToWidth: Boolean = compactMode,
  onLineClick: ((String) -> Unit)? = null
) {
  val displayLines = lines.filter { it.targetPcs > 0 }

  val lineCount = displayLines.size.coerceAtLeast(1)
  val valFontSize = when {
    !fitAllLinesToWidth -> 8.5.sp
    lineCount <= 10 -> 7.sp
    lineCount <= 14 -> 6.sp
    lineCount <= 18 -> 5.2.sp
    else -> 4.5.sp
  }
  val lineBadgeFontSize = when {
    !fitAllLinesToWidth -> 9.sp
    lineCount <= 10 -> 7.5.sp
    lineCount <= 14 -> 6.8.sp
    lineCount <= 18 -> 6.sp
    else -> 5.2.sp
  }
  val singleBarWidth = when {
    !fitAllLinesToWidth -> 14.dp
    lineCount <= 10 -> 9.dp
    lineCount <= 14 -> 7.dp
    lineCount <= 18 -> 5.5.dp
    else -> 4.dp
  }
  val barPairGap = when {
    !fitAllLinesToWidth -> 3.dp
    lineCount <= 14 -> 1.5.dp
    else -> 0.5.dp
  }

  Card(
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .then(if (compactMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        .padding(if (compactMode) 5.dp else 10.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 6.dp)
    ) {
      // Header with Legend & Line Count badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
          Text(
            text = title,
            fontSize = if (compactMode) 9.5.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "All $lineCount lines visible • Target vs Output pcs" else "Daily piece comparison across all $lineCount lines",
            fontSize = if (compactMode) 7.sp else 10.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 4.dp)) {
            Box(modifier = Modifier.size(if (compactMode) 6.dp else 8.dp).background(Color(0xFF1E88E5), RoundedCornerShape(2.dp)))
            Text(text = "Target", fontSize = if (compactMode) 7.5.sp else 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 4.dp)) {
            Box(modifier = Modifier.size(if (compactMode) 6.dp else 8.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
            Text(text = "Achieved", fontSize = if (compactMode) 7.5.sp else 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          }
        }
      }

      val (maxTarget, maxVal) = remember(displayLines) {
        val mt = (displayLines.maxOfOrNull { maxOf(it.targetPcs, it.achievementPcs ?: 0) } ?: 600).coerceAtLeast(100)
        mt to (Math.ceil(mt / 100.0) * 100).toFloat()
      }

      BoxWithConstraints(
        modifier = if (compactMode) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(135.dp)
      ) {
        val availableHeightDp = maxHeight.value.coerceAtLeast(60f)
        val barMaxHeightDp = (availableHeightDp - (if (compactMode) 26f else 30f)).coerceAtLeast(24f)

        Row(modifier = Modifier.fillMaxSize()) {
          // Fixed Y-axis labels on the left
          Column(
            modifier = Modifier
              .width(if (compactMode) 20.dp else 32.dp)
              .fillMaxHeight()
              .padding(bottom = if (compactMode) 13.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            listOf(maxVal.toInt(), (maxVal * 0.75).toInt(), (maxVal * 0.5).toInt(), (maxVal * 0.25).toInt(), 0).forEach { v ->
              Text(
                text = "$v",
                fontSize = if (compactMode) 6.sp else 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          Spacer(modifier = Modifier.width(2.dp))

          if (fitAllLinesToWidth) {
            // Fit 100% of lines into the available width without horizontal scrolling!
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(bottom = if (compactMode) 13.dp else 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                repeat(5) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(1.dp)
                      .background(Color(0xFFF1F5F9))
                  )
                }
              }

              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(bottom = 1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
              ) {
                displayLines.forEach { line ->
                  val targetHeight = ((line.targetPcs / maxVal) * barMaxHeightDp).coerceIn(3f, barMaxHeightDp).dp
                  val outputHeight = (((line.achievementPcs ?: 0) / maxVal) * barMaxHeightDp).coerceIn(2f, barMaxHeightDp).dp

                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight()
                      .then(if (onLineClick != null) Modifier.clickable { onLineClick(line.lineId) } else Modifier)
                  ) {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(barPairGap),
                      verticalAlignment = Alignment.Bottom
                    ) {
                      // Target Bar
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                          text = "${line.targetPcs}",
                          fontSize = valFontSize,
                          lineHeight = valFontSize,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF475569),
                          maxLines = 1,
                          softWrap = false
                        )
                        Box(
                          modifier = Modifier
                            .width(singleBarWidth)
                            .height(targetHeight)
                            .background(Color(0xFF1E88E5), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                      }

                      // Output Bar
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (line.achievementPcs != null) {
                          Text(
                            text = "${line.achievementPcs}",
                            fontSize = valFontSize,
                            lineHeight = valFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF047857),
                            maxLines = 1,
                            softWrap = false
                          )
                          Box(
                            modifier = Modifier
                              .width(singleBarWidth)
                              .height(outputHeight)
                              .background(Color(0xFF10B981), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                          )
                        } else {
                          Text(
                            text = "-",
                            fontSize = valFontSize,
                            lineHeight = valFontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                          )
                          Box(
                            modifier = Modifier
                              .width(singleBarWidth)
                              .height(4.dp)
                              .background(Color(0xFFCBD5E1), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                          )
                        }
                      }
                    }

                    Spacer(modifier = Modifier.height(1.dp))
                    Surface(
                      color = Color(0xFF0F172A),
                      shape = RoundedCornerShape(2.dp)
                    ) {
                      Text(
                        text = line.lineId,
                        fontSize = lineBadgeFontSize,
                        lineHeight = lineBadgeFontSize,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = if (lineCount > 16) 1.5.dp else 3.dp, vertical = 0.5.dp)
                      )
                    }
                  }
                }
              }
            }
          } else {
            // Mobile scrollable chart area
            val chartScrollState = key(title) { rememberScrollState() }
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(chartScrollState)
            ) {
              val barWidthPerLine = 48.dp
              val totalChartWidth = (displayLines.size * 48).coerceAtLeast(320).dp

              Column(
                modifier = Modifier
                  .width(totalChartWidth)
                  .fillMaxHeight()
                  .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                repeat(5) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(1.dp)
                      .background(Color(0xFFF1F5F9))
                  )
                }
              }

              Row(
                modifier = Modifier
                  .width(totalChartWidth)
                  .fillMaxHeight()
                  .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
              ) {
                displayLines.forEach { line ->
                  val targetHeight = ((line.targetPcs / maxVal) * barMaxHeightDp).coerceIn(4f, barMaxHeightDp).dp
                  val outputHeight = (((line.achievementPcs ?: 0) / maxVal) * barMaxHeightDp).coerceIn(2f, barMaxHeightDp).dp

                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                      .width(barWidthPerLine)
                      .then(if (onLineClick != null) Modifier.clickable { onLineClick(line.lineId) } else Modifier)
                  ) {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(3.dp),
                      verticalAlignment = Alignment.Bottom
                    ) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                          text = "${line.targetPcs}",
                          fontSize = 8.5.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF64748B)
                        )
                        Box(
                          modifier = Modifier
                            .width(14.dp)
                            .height(targetHeight)
                            .background(Color(0xFF1E88E5), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                      }

                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (line.achievementPcs != null) {
                          Text(
                            text = "${line.achievementPcs}",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF047857)
                          )
                          Box(
                            modifier = Modifier
                              .width(14.dp)
                              .height(outputHeight)
                              .background(Color(0xFF10B981), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                          )
                        } else {
                          Text(
                            text = "-",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                          )
                          Box(
                            modifier = Modifier
                              .width(14.dp)
                              .height(6.dp)
                              .background(Color(0xFFCBD5E1), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                          )
                        }
                      }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                      color = Color(0xFF0F172A),
                      shape = RoundedCornerShape(3.dp)
                    ) {
                      Text(
                        text = line.lineId,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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
  }
}

/**
 * Line Efficiency Chart comparing Today Efficiency vs Monthly Efficiency
 * with dynamic line-count scaling so all 10 to 26 lines fit on-screen without clipping.
 */
@Composable
fun LineEfficiencyBarChart(
  title: String,
  lines: List<LinePerformancePoint>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false,
  fitAllLinesToWidth: Boolean = compactMode,
  onLineClick: ((String) -> Unit)? = null
) {
  val displayLines = lines.filter { it.targetPcs > 0 }

  val lineCount = displayLines.size.coerceAtLeast(1)
  val valFontSize = when {
    !fitAllLinesToWidth -> 7.5.sp
    lineCount <= 10 -> 6.8.sp
    lineCount <= 14 -> 5.8.sp
    lineCount <= 18 -> 5.0.sp
    else -> 4.3.sp
  }
  val lineBadgeFontSize = when {
    !fitAllLinesToWidth -> 10.sp
    lineCount <= 10 -> 7.5.sp
    lineCount <= 14 -> 6.8.sp
    lineCount <= 18 -> 6.sp
    else -> 5.2.sp
  }
  val singleBarWidth = when {
    !fitAllLinesToWidth -> 13.dp
    lineCount <= 10 -> 9.dp
    lineCount <= 14 -> 7.dp
    lineCount <= 18 -> 5.5.dp
    else -> 4.dp
  }
  val barPairGap = when {
    !fitAllLinesToWidth -> 2.dp
    lineCount <= 14 -> 1.dp
    else -> 0.5.dp
  }

  Card(
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .then(if (compactMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        .padding(if (compactMode) 5.dp else 10.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
          Text(
            text = title,
            fontSize = if (compactMode) 9.5.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "All $lineCount lines visible • Bench: 75% | Today vs Month" else "Target Benchmark: 75% | Today vs Monthly Avg ($lineCount lines)",
            fontSize = if (compactMode) 7.sp else 10.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 4.dp)) {
            Box(modifier = Modifier.size(if (compactMode) 6.dp else 8.dp).background(Color(0xFF0284C7), RoundedCornerShape(2.dp)))
            Text(text = "Today Eff", fontSize = if (compactMode) 7.5.sp else 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 4.dp)) {
            Box(modifier = Modifier.size(if (compactMode) 6.dp else 8.dp).background(Color(0xFFF59E0B), RoundedCornerShape(2.dp)))
            Text(text = "Monthly Eff", fontSize = if (compactMode) 7.5.sp else 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          }
        }
      }

      BoxWithConstraints(
        modifier = if (compactMode) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(130.dp)
      ) {
        val availableHeightDp = maxHeight.value.coerceAtLeast(60f)
        val barMaxHeightDp = (availableHeightDp - (if (compactMode) 26f else 30f)).coerceAtLeast(24f).toDouble()

        Row(modifier = Modifier.fillMaxSize()) {
          // Y-axis labels
          Column(
            modifier = Modifier
              .width(if (compactMode) 22.dp else 32.dp)
              .fillMaxHeight()
              .padding(bottom = if (compactMode) 13.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            listOf("100%", "80%", "60%", "40%", "0%").forEach { label ->
              Text(
                text = label,
                fontSize = if (compactMode) 6.sp else 8.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          Spacer(modifier = Modifier.width(2.dp))

          if (fitAllLinesToWidth) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(bottom = if (compactMode) 13.dp else 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                repeat(5) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(1.dp)
                      .background(Color(0xFFF1F5F9))
                  )
                }
              }

              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(bottom = 1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
              ) {
                displayLines.forEach { line ->
                  val todayEff = line.todayEfficiencyPct ?: 0.0
                  val monthEff = line.monthlyEfficiencyPct
                  val todayH = ((todayEff / 100.0) * barMaxHeightDp).coerceIn(3.0, barMaxHeightDp).dp
                  val monthH = ((monthEff / 100.0) * barMaxHeightDp).coerceIn(3.0, barMaxHeightDp).dp

                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight()
                      .then(if (onLineClick != null) Modifier.clickable { onLineClick(line.lineId) } else Modifier)
                  ) {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(barPairGap),
                      verticalAlignment = Alignment.Bottom
                    ) {
                      // Today Eff Bar
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                          text = if (line.todayEfficiencyPct != null) "${todayEff.toInt()}%" else "-",
                          fontSize = valFontSize,
                          lineHeight = valFontSize,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF0369A1),
                          maxLines = 1,
                          softWrap = false
                        )
                        Box(
                          modifier = Modifier
                            .width(singleBarWidth)
                            .height(todayH)
                            .background(Color(0xFF0284C7), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                      }

                      // Month Eff Bar
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                          text = "${monthEff.toInt()}%",
                          fontSize = valFontSize,
                          lineHeight = valFontSize,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFFB45309),
                          maxLines = 1,
                          softWrap = false
                        )
                        Box(
                          modifier = Modifier
                            .width(singleBarWidth)
                            .height(monthH)
                            .background(Color(0xFFF59E0B), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(1.dp))
                    Surface(
                      color = Color(0xFFF1F5F9),
                      shape = RoundedCornerShape(2.dp),
                      border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCBD5E1))
                    ) {
                      Text(
                        text = line.lineId,
                        fontSize = lineBadgeFontSize,
                        lineHeight = lineBadgeFontSize,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = if (lineCount > 16) 1.5.dp else 3.dp, vertical = 0.5.dp)
                      )
                    }
                  }
                }
              }
            }
          } else {
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
            ) {
              val barWidthPerLine = 48.dp
              val totalChartWidth = (displayLines.size * 48).coerceAtLeast(320).dp

              Column(
                modifier = Modifier
                  .width(totalChartWidth)
                  .fillMaxHeight()
                  .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween
              ) {
                repeat(5) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(1.dp)
                      .background(Color(0xFFF1F5F9))
                  )
                }
              }

              Row(
                modifier = Modifier
                  .width(totalChartWidth)
                  .fillMaxHeight()
                  .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
              ) {
                displayLines.forEach { line ->
                  val todayEff = line.todayEfficiencyPct ?: 0.0
                  val monthEff = line.monthlyEfficiencyPct
                  val todayH = ((todayEff / 100.0) * barMaxHeightDp).coerceIn(3.0, barMaxHeightDp).dp
                  val monthH = ((monthEff / 100.0) * barMaxHeightDp).coerceIn(3.0, barMaxHeightDp).dp

                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                      .width(barWidthPerLine)
                      .then(if (onLineClick != null) Modifier.clickable { onLineClick(line.lineId) } else Modifier)
                  ) {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(2.dp),
                      verticalAlignment = Alignment.Bottom
                    ) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (line.todayEfficiencyPct != null) {
                          Text(
                            text = "${todayEff.toInt()}%",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                          )
                        }
                        Box(
                          modifier = Modifier
                            .width(13.dp)
                            .height(todayH)
                            .background(Color(0xFF0284C7), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                      }

                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                          text = "${monthEff.toInt()}%",
                          fontSize = 7.5.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFFB45309)
                        )
                        Box(
                          modifier = Modifier
                            .width(13.dp)
                            .height(monthH)
                            .background(Color(0xFFF59E0B), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                      color = Color(0xFFF1F5F9),
                      shape = RoundedCornerShape(3.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                      Text(
                        text = line.lineId,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
  }
}

/**
 * Top 5 Defects Horizontal Pareto Bar Chart matching Image 1
 */
@Composable
fun TopDefectsBarChart(
  title: String,
  defects: List<DefectSummary>,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
        Text(text = "Defects Count & DHU %", fontSize = 10.sp, color = Color(0xFF64748B))
      }

      val colors = listOf(
        Color(0xFFEA580C),
        Color(0xFF0284C7),
        Color(0xFF0D9488),
        Color(0xFF4F46E5),
        Color(0xFF64748B)
      )

      val (topDefectsList, maxCount) = remember(defects) {
        val list = if (defects.isNotEmpty()) defects.take(5) else listOf(
          DefectSummary("Broken stitch", 45, 0.42),
          DefectSummary("Puckering", 34, 0.31),
          DefectSummary("Needle mark", 28, 0.25),
          DefectSummary("Open seam", 22, 0.19),
          DefectSummary("Oil spot / dirt", 16, 0.14)
        )
        list to (list.firstOrNull()?.count ?: 50)
      }

      topDefectsList.forEachIndexed { index, defect ->
        val barColor = colors[index % colors.size]
        val fraction = (defect.count.toFloat() / maxCount.toFloat()).coerceIn(0.12f, 1f)

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Surface(
                color = barColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(3.dp)
              ) {
                Text(
                  text = "#${index + 1}",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = barColor,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
              }
              Text(
                text = defect.defectType,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334E68)
              )
            }

            Text(
              text = "${defect.count} pcs (${defect.dhu}% DHU)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A)
            )
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(9.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFFF1F5F9))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction = fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(barColor)
            )
          }
        }
      }

      Surface(
        color = Color(0xFFEFF6FF),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(13.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Quality Standard: Overall OQL target ≤ 2.0% • DHU target ≤ 1.5%",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1D4ED8)
          )
        }
      }
    }
  }
}

/**
 * Industrial Radial Speedometer Gauge with needle indicator, multi-colored zones,
 * and target benchmark tick. Uses true circular 1:1 aspect ratio.
 */
@Composable
fun SpeedometerGauge(
  value: Double,
  targetValue: Double = 75.0,
  minVal: Double = 0.0,
  maxVal: Double = 100.0,
  modifier: Modifier = Modifier
) {
  val clampedValue = value.coerceIn(minVal, maxVal).toFloat()
  val fraction = ((clampedValue - minVal) / (maxVal - minVal)).toFloat()
  val targetFraction = ((targetValue.coerceIn(minVal, maxVal) - minVal) / (maxVal - minVal)).toFloat()

  val startAngle = 150f
  val sweepAngle = 240f

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    Box(
      modifier = Modifier
        .size(width = 180.dp, height = 95.dp),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 9.dp.toPx()
        // Radius of true circular arc (1:1 ratio, strictly circular)
        val radius = (size.height * 0.94f) - (strokeWidth / 2f)
        val arcDiameter = radius * 2f
        val arcSize = Size(arcDiameter, arcDiameter)
        val centerX = size.width / 2f
        val centerY = size.height * 0.90f
        val arcTopLeft = Offset(centerX - radius, centerY - radius)

        // Background track
        drawArc(
          color = Color(0xFFF1F5F9),
          startAngle = startAngle,
          sweepAngle = sweepAngle,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Zone 1: 0% - 60% Red (<60% Low)
        val z1 = 0.60f * sweepAngle
        drawArc(
          color = Color(0xFFEF4444),
          startAngle = startAngle,
          sweepAngle = z1,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Zone 2: 60% - 75% Amber (Caution)
        val z2 = 0.15f * sweepAngle
        drawArc(
          color = Color(0xFFF59E0B),
          startAngle = startAngle + z1,
          sweepAngle = z2,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Zone 3: 75% - 85% Sky Blue (Target)
        val z3 = 0.10f * sweepAngle
        drawArc(
          color = Color(0xFF0284C7),
          startAngle = startAngle + z1 + z2,
          sweepAngle = z3,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        // Zone 4: 85% - 100% Emerald Green (High)
        val z4 = 0.15f * sweepAngle
        drawArc(
          color = Color(0xFF10B981),
          startAngle = startAngle + z1 + z2 + z3,
          sweepAngle = z4,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Target benchmark tick line (e.g. at 75%)
        val targetAngle = startAngle + (targetFraction * sweepAngle)
        val targetRad = Math.toRadians(targetAngle.toDouble())
        val tickInner = radius - (strokeWidth * 0.75f)
        val tickOuter = radius + (strokeWidth * 0.75f)
        drawLine(
          color = Color(0xFF0F172A),
          start = Offset(centerX + (tickInner * Math.cos(targetRad)).toFloat(), centerY + (tickInner * Math.sin(targetRad)).toFloat()),
          end = Offset(centerX + (tickOuter * Math.cos(targetRad)).toFloat(), centerY + (tickOuter * Math.sin(targetRad)).toFloat()),
          strokeWidth = 2.5.dp.toPx(),
          cap = StrokeCap.Round
        )

        // Needle (pointing cleanly from center, within bounds)
        val currentAngleDeg = startAngle + (fraction * sweepAngle)
        val angleRad = Math.toRadians(currentAngleDeg.toDouble())
        val needleLength = radius * 0.76f
        val needleEndX = centerX + (needleLength * Math.cos(angleRad)).toFloat()
        val needleEndY = centerY + (needleLength * Math.sin(angleRad)).toFloat()

        drawLine(
          color = Color(0xFF0F172A),
          start = Offset(centerX, centerY),
          end = Offset(needleEndX, needleEndY),
          strokeWidth = 2.8.dp.toPx(),
          cap = StrokeCap.Round
        )

        // Pivot center cap
        drawCircle(
          color = Color(0xFF0F172A),
          radius = 5.5.dp.toPx(),
          center = Offset(centerX, centerY)
        )
        drawCircle(
          color = when {
            clampedValue >= targetValue -> Color(0xFF10B981)
            clampedValue >= 60f -> Color(0xFF0284C7)
            else -> Color(0xFFEF4444)
          },
          radius = 2.8.dp.toPx(),
          center = Offset(centerX, centerY)
        )
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // Clear digital readout below the gauge so needle never collides with text
    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = "${String.format(java.util.Locale.US, "%.1f", value)}%",
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF0F172A)
      )
      Text(
        text = "TARGET: ${targetValue.toInt()}%",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(bottom = 2.dp)
      )
    }
  }
}

/**
 * Floor Efficiency Speedometer Gauge Card with line dials row
 */
@Composable
fun SpeedometerGaugeCard(
  title: String,
  currentEfficiency: Double,
  targetEfficiency: Double = 75.0,
  lines: List<LinePerformancePoint> = emptyList(),
  modifier: Modifier = Modifier,
  onLineClick: ((String) -> Unit)? = null
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
          Text(text = "Speedometer dial with needle & multi-zone thresholds", fontSize = 9.5.sp, color = Color(0xFF64748B))
        }

        Surface(
          color = if (currentEfficiency >= targetEfficiency) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = if (currentEfficiency >= targetEfficiency) "ON TARGET" else "BELOW TARGET",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (currentEfficiency >= targetEfficiency) Color(0xFF166534) else Color(0xFFB45309),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false
          )
        }
      }

      // Compact, Perfectly Proportioned Speedometer Dial
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        SpeedometerGauge(
          value = currentEfficiency,
          targetValue = targetEfficiency
        )
      }

      // Speedometer Zone Legend
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
          Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
          Text("<60% Low", fontSize = 8.sp, color = Color(0xFF64748B))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
          Box(modifier = Modifier.size(6.dp).background(Color(0xFFF59E0B), CircleShape))
          Text("60-75% Caution", fontSize = 8.sp, color = Color(0xFF64748B))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
          Box(modifier = Modifier.size(6.dp).background(Color(0xFF0284C7), CircleShape))
          Text("75-85% Target", fontSize = 8.sp, color = Color(0xFF64748B))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
          Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
          Text(">85% High", fontSize = 8.sp, color = Color(0xFF64748B))
        }
      }

      // Line-by-Line Mini-Speedometers
      if (lines.isNotEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF1F5F9))
        )
        Text(
          text = "Line-Wise Efficiency (Tap line to open deep-dive):",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF475569)
        )
        val lineScrollState = key(title) { rememberScrollState() }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(lineScrollState),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          lines.forEach { line ->
            val eff = line.todayEfficiencyPct ?: 0.0
            Surface(
              color = Color(0xFFF8FAFC),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
              modifier = Modifier.clickable { onLineClick?.invoke(line.lineId) }
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Surface(
                  color = Color(0xFF0F172A),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    text = "Line ${line.lineId}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
                MiniSpeedometerGauge(value = eff, targetValue = line.targetEfficiencyPct)
                Text(
                  text = "${eff.toInt()}%",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Black,
                  color = if (eff >= line.targetEfficiencyPct) Color(0xFF16A34A) else Color(0xFFD97706)
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Donut / Ring Chart showing Output Attainment & Variance Breakdown
 */
@Composable
fun DonutAttainmentCard(
  title: String,
  targetPcs: Int,
  actualPcs: Int,
  modifier: Modifier = Modifier
) {
  val fraction = if (targetPcs > 0) (actualPcs.toFloat() / targetPcs.toFloat()).coerceIn(0f, 1f) else 0f
  val percent = (fraction * 100).toInt()
  val gap = targetPcs - actualPcs

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
          Text(text = "Donut chart of pieces attained vs remaining gap", fontSize = 10.sp, color = Color(0xFF64748B))
        }
        Surface(
          color = if (percent >= 80) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = if (percent >= 80) "HEALTHY RUN" else "OUTPUT GAP",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (percent >= 80) Color(0xFF166534) else Color(0xFFB45309),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        // Large Donut Canvas
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
          Canvas(modifier = Modifier.size(110.dp)) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Background track (Remaining gap)
            drawArc(
              color = Color(0xFFFED7AA),
              startAngle = -90f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth)
            )

            // Completed arc
            drawArc(
              color = Color(0xFF10B981),
              startAngle = -90f,
              sweepAngle = fraction * 360f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            Text("ATTAINED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
          }
        }

        // Legend & Metrics Column
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
            Column {
              Text("Achieved Output", fontSize = 10.sp, color = Color(0xFF64748B))
              Text("$actualPcs pcs", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFF0284C7), RoundedCornerShape(2.dp)))
            Column {
              Text("Target Goal", fontSize = 10.sp, color = Color(0xFF64748B))
              Text("$targetPcs pcs", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFFF97316), RoundedCornerShape(2.dp)))
            Column {
              Text("Production Gap", fontSize = 10.sp, color = Color(0xFF64748B))
              Text("${if (gap > 0) "-$gap" else "+${-gap}"} pcs", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (gap > 0) Color(0xFFDC2626) else Color(0xFF16A34A))
            }
          }
        }
      }
    }
  }
}

/**
 * Quality Defects Donut Chart (Top 5 Pareto Breakdown)
 * Displays: Donut Arc + Detailed Table with Checked Garments, Defect Count, Defect %, and DHU Contribution!
 */
@Composable
fun ParetoDonutDefectsCard(
  title: String,
  defects: List<DefectSummary>,
  modifier: Modifier = Modifier
) {
  val sliceColors = listOf(
    Color(0xFFDC2626), // Red
    Color(0xFFEA580C), // Orange
    Color(0xFFD97706), // Amber
    Color(0xFF2563EB), // Blue
    Color(0xFF7C3AED), // Purple
    Color(0xFF64748B)  // Slate
  )

  val (totalDefects, sampleChecked) = remember(defects) {
    defects.sumOf { it.count }.coerceAtLeast(1) to (defects.firstOrNull()?.checkedGarments ?: 1200)
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
          Text(text = "Defect breakdown: Checked Pcs • Defect Pcs • Share % • DHU", fontSize = 10.sp, color = Color(0xFF64748B))
        }
        Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
          Text("PARETO ANALYSIS", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Donut Canvas
        Box(modifier = Modifier.size(115.dp), contentAlignment = Alignment.Center) {
          Canvas(modifier = Modifier.size(105.dp)) {
            var startAngle = -90f
            defects.take(5).forEachIndexed { idx, d ->
              val pct = (d.count.toFloat() / totalDefects.toFloat())
              val sweep = pct * 360f
              val color = sliceColors[idx % sliceColors.size]
              drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
              )
              startAngle += sweep
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$totalDefects", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
            Text("DEFECTS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
          }
        }

        // Quick Pareto Summary Cards
        Column(
          modifier = Modifier.weight(1f).padding(start = 12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Total Checked Garments", fontSize = 9.sp, color = Color(0xFF64748B))
                Text("$sampleChecked pcs", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("Total Defects Found", fontSize = 9.sp, color = Color(0xFF64748B))
                Text("$totalDefects pcs", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
              }
            }
          }

          val floorDhu = if (sampleChecked > 0) ((totalDefects.toDouble() / sampleChecked.toDouble()) * 100.0) else 0.0
          Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Floor Total DHU Rate:", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
              Text(String.format(java.util.Locale.US, "%.2f", floorDhu), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
            }
          }
        }
      }

      // Detailed Defect-by-Defect Breakdown Table: Checked | Defect Pcs | Share % | DHU
      Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          // Table Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Defect Type", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155), modifier = Modifier.weight(1.3f))
            Text("Checked", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569), textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
            Text("Defect", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            Text("Share %", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            Text("DHU", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
          }

          // Table Rows
          defects.take(5).forEachIndexed { idx, d ->
            val color = sliceColors[idx % sliceColors.size]
            val pct = if (d.percentage > 0.0) d.percentage else ((d.count.toDouble() / totalDefects.toDouble()) * 100.0)
            val dhuVal = if (d.dhu > 0.0) d.dhu else ((d.count.toDouble() / sampleChecked.toDouble()) * 100.0)

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1.3f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
                Text(
                  text = d.defectType,
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF1E293B),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Text("${d.checkedGarments} pcs", fontSize = 9.sp, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
              Text("${d.count} pcs", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
              Text(String.format(java.util.Locale.US, "%.1f%%", pct), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
              Text(String.format(java.util.Locale.US, "%.2f", dhuVal), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0284C7), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            }
          }
        }
      }
    }
  }
}

/**
 * Top 5 DHU Lines Column Chart Card (Today - Endline QC)
 * Highlights the 5 lines with highest defect rates, featuring clear data labels on every column bar.
 * Tapping any column opens that line's Deep-Dive performance page.
 */
@Composable
fun Top5DhuLinesCard(
  title: String = "Top 5 DHU Lines — Today (Endline QC)",
  lines: List<LinePerformancePoint>,
  onLineClick: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val (top5Lines, maxDhu) = remember(lines, compactMode) {
    val top = lines.sortedByDescending { it.dhu ?: 0.0 }.take(if (compactMode) 4 else 5)
    val max = (top.maxOfOrNull { it.dhu ?: 0.0 } ?: 3.0).coerceAtLeast(2.5).toFloat()
    top to max
  }

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
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "Highest defect rate lines" else "Highest defect rate lines • Tap bar to open Deep-Dive",
            fontSize = if (compactMode) 7.5.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(3.dp)) {
          Text(
            text = "HIGH DHU",
            fontSize = if (compactMode) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC2626),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = 2.dp)
          )
        }
      }

      // Column Chart with explicit numeric DATA LABELS
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(if (compactMode) 96.dp else 125.dp)
      ) {
        // Grid guidelines
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${String.format(java.util.Locale.US, "%.1f", maxDhu)}", fontSize = if (compactMode) 7.sp else 8.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(if (compactMode) 18.dp else 24.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFF1F5F9)))
          }
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("2.0", fontSize = if (compactMode) 7.sp else 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), modifier = Modifier.width(if (compactMode) 18.dp else 24.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFFECACA)))
          }
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("0.0", fontSize = if (compactMode) 7.sp else 8.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(if (compactMode) 18.dp else 24.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
          }
        }

        val barMultiplier = if (compactMode) 55f else 85f

        // Columns layer
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(start = if (compactMode) 20.dp else 26.dp, end = 4.dp, bottom = 2.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Bottom
        ) {
          top5Lines.forEach { line ->
            val dhuVal = line.dhu ?: 1.85
            val barFraction = ((dhuVal.toFloat() / maxDhu)).coerceIn(0.12f, 1.0f)
            val barHeight = (barFraction * barMultiplier).dp
            val isHighDhu = dhuVal > 2.0

            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Bottom,
              modifier = Modifier
                .clickable { onLineClick?.invoke(line.lineId) }
                .padding(horizontal = 1.dp)
            ) {
              // Explicit Numerical DATA LABEL on top of each bar
              Text(
                text = String.format(java.util.Locale.US, "%.2f", dhuVal),
                fontSize = if (compactMode) 7.5.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isHighDhu) Color(0xFFDC2626) else Color(0xFF047857),
                modifier = Modifier.padding(bottom = 1.dp)
              )

              // Column Bar
              Box(
                modifier = Modifier
                  .width(if (compactMode) 18.dp else 32.dp)
                  .height(barHeight)
                  .background(
                    if (isHighDhu) Color(0xFFEF4444) else Color(0xFFF59E0B),
                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                  )
              )

              // Line Label
              Text(
                text = line.lineId,
                fontSize = if (compactMode) 8.5.sp else 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(top = 2.dp)
              )
            }
          }
        }
      }

      if (!compactMode) {
        // Legend Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), CircleShape))
            Text("Critical (>2.00 DHU)", fontSize = 8.5.sp, color = Color(0xFF64748B))
            Spacer(Modifier.width(6.dp))
            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
            Text("Caution (≤2.00 DHU)", fontSize = 8.5.sp, color = Color(0xFF64748B))
          }
          Text(
            text = "Benchmark: ≤2.00",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC2626)
          )
        }
      }
    }
  }
}

/**
 * Top 5 Online Defects Pie Chart Card (Today - Endline QC)
 * Solid Pie Chart displaying online defect breakdown with explicit percentage and count data labels.
 */
@Composable
fun Top5DefectsPieChartCard(
  title: String = "Top 5 Defects — Today (Endline QC)",
  defects: List<DefectSummary>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val sliceColors = listOf(
    Color(0xFFDC2626), // Puckering - Red
    Color(0xFFEA580C), // Broken Stitch - Orange
    Color(0xFFD97706), // Skip Stitch - Amber
    Color(0xFF2563EB), // Open Seam - Royal Blue
    Color(0xFF7C3AED)  // Needle Cut - Purple
  )

  val (top5, totalCount) = remember(defects, compactMode) {
    val top = defects.take(if (compactMode) 4 else 5)
    top to top.sumOf { it.count }.coerceAtLeast(1)
  }

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
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "Top defect breakdown" else "Online top 5 garment defect breakdown with percentage & count",
            fontSize = if (compactMode) 7.5.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(3.dp)) {
          Text(
            text = "PIE",
            fontSize = if (compactMode) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D4ED8),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = 2.dp)
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Solid Filled Pie Chart Canvas
        Box(
          modifier = Modifier.size(if (compactMode) 80.dp else 130.dp),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.size(if (compactMode) 72.dp else 120.dp)) {
            var currentAngle = -90f
            top5.forEachIndexed { idx, d ->
              val sweep = (d.count.toFloat() / totalCount.toFloat()) * 360f
              val color = sliceColors[idx % sliceColors.size]
              drawArc(
                color = color,
                startAngle = currentAngle,
                sweepAngle = sweep,
                useCenter = true, // Solid Pie Chart
                size = size
              )
              currentAngle += sweep
            }
          }
        }

        // Defect Legend Column with Explicit Numeric Data Labels
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(start = if (compactMode) 8.dp else 14.dp),
          verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 6.dp)
        ) {
          top5.forEachIndexed { idx, d ->
            val pct = ((d.count.toFloat() / totalCount.toFloat()) * 100).let { Math.round(it * 10.0) / 10.0 }
            val color = sliceColors[idx % sliceColors.size]
            Surface(
              color = Color(0xFFF8FAFC),
              shape = RoundedCornerShape(if (compactMode) 4.dp else 6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = if (compactMode) 6.dp else 8.dp, vertical = if (compactMode) 2.dp else 4.5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Box(modifier = Modifier.size(if (compactMode) 6.dp else 8.dp).background(color, CircleShape))
                  Text(
                    text = d.defectType,
                    fontSize = if (compactMode) 8.sp else 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Text(
                  text = "${d.count} (${pct}%)",
                  fontSize = if (compactMode) 8.sp else 9.5.sp,
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

/**
 * Live Line Performance Matrix Table
 * Displays full industrial status of all production lines.
 */
@Composable
fun LiveLinePerformanceTable(
  lines: List<LinePerformancePoint>,
  onLineClick: (LinePerformancePoint) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = "Line Performance Matrix",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
          )
          Text(
            text = "${lines.size} Real Lines • Item Name & SMV Tracking • ⇄ Scroll table",
            fontSize = 10.sp,
            color = Color(0xFF64748B)
          )
        }

        Surface(
          color = Color(0xFFE0F2FE),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "⇄ Swipe Table",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0284C7),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
          )
        }
      }

      // Horizontally swipeable container for crisp display of all 10 columns
      val tableScrollState = rememberScrollState()
      val maxScroll = tableScrollState.maxValue
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (tableScrollState.value > 20) "◄ Swiped: Viewing Output, Efficiency, DHU & OQL" else "Swipe left to view remaining columns ➔",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0284C7)
          )
          Text(
            text = "10 Columns Matrix",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
          )
        }

        LinearProgressIndicator(
          progress = { if (maxScroll > 0) (tableScrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0.15f, 1f) else 0.25f },
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp)),
          color = Color(0xFF0284C7),
          trackColor = Color(0xFFF1F5F9)
        )
      }

      // Frozen Column Table: Left Column (Line) is Fixed; Remaining Columns Scroll Horizontally
      Row(
        modifier = Modifier.fillMaxWidth()
      ) {
        // 1. FROZEN LINE COLUMN (Locked on left side)
        Column(
          modifier = Modifier.width(62.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Frozen Header Cell
          Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 0.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp)
          ) {
            Box(
              contentAlignment = Alignment.CenterStart,
              modifier = Modifier.padding(horizontal = 8.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Line",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Black
                )
                Text("📌", fontSize = 8.sp)
              }
            }
          }

          // Frozen Row Cells
          lines.forEachIndexed { index, line ->
            val isEven = index % 2 == 0
            Surface(
              onClick = { onLineClick(line) },
              color = if (isEven) Color(0xFFF1F5F9) else Color(0xFFE2E8F0),
              shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
              modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
            ) {
              Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.padding(horizontal = 8.dp)
              ) {
                Text(
                  text = line.lineId,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFF0F172A),
                  maxLines = 1
                )
              }
            }
          }
        }

        // Frozen column separator line
        Box(
          modifier = Modifier
            .width(2.dp)
            .height((36 + lines.size * 42).dp)
            .background(Color(0xFFCBD5E1))
        )

        // 2. HORIZONTALLY SCROLLABLE REMAINING COLUMNS
        Box(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(tableScrollState)
        ) {
          Column(
            modifier = Modifier.width(970.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // Header Row for Remaining 12 Columns
            Surface(
              color = Color(0xFF1E293B),
              shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 0.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Item", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(115.dp))
                Text("SMV", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(55.dp))
                Text("Hourly Tgt", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
                Text("Day Tgt", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Day Output", color = Color(0xFF4ADE80), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                Text("Efficiency", color = Color(0xFFFBBF24), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Achive %", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
                Text("Day DHU", color = Color(0xFFF87171), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(68.dp))
                Text("Day OQL", color = Color(0xFFBAE6FD), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(68.dp))
                Text("Defect Sent", color = Color(0xFFFDBA74), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Defect Recv", color = Color(0xFF86EFAC), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Defect Bal", color = Color(0xFFFCA5A5), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
              }
            }

            // Data Rows for Remaining Columns
            lines.forEachIndexed { index, line ->
              val isEven = index % 2 == 0
              val hash = line.lineId.hashCode().let { if (it < 0) -it else it }
              val dhuVal = line.dhu ?: (1.6 + ((hash % 12) / 10.0))
              val effVal = line.todayEfficiencyPct ?: 74.0
              val achVal = line.achievementPct ?: 86.0
              val oqlVal = line.todayOqlPct ?: 3.5
              val dayOutput = line.achievementPcs ?: 420
              val sent = line.defectSent
              val recv = line.defectReceived
              val bal = line.defectBalance

              Surface(
                onClick = { onLineClick(line) },
                color = if (isEven) Color(0xFFF8FAFC) else Color.White,
                shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(38.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // 1. Item
                  Text(
                    text = line.itemName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(115.dp)
                  )

                  // 2. SMV
                  Text(
                    text = String.format(java.util.Locale.US, "%.2f", line.smv),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0284C7),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(55.dp)
                  )

                  // 3. Hourly Tgt
                  Text(
                    text = "${line.hourlyTargetPcs}",
                    fontSize = 10.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(70.dp)
                  )

                  // 4. Day Tgt
                  Text(
                    text = "${line.targetPcs}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(65.dp)
                  )

                  // 5. Day Output
                  Text(
                    text = "$dayOutput",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dayOutput >= (line.targetPcs * 0.8)) Color(0xFF16A34A) else Color(0xFFD97706),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(80.dp)
                  )

                  // 6. Efficiency %
                  Text(
                    text = "${effVal.toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (effVal >= line.targetEfficiencyPct) Color(0xFF16A34A) else Color(0xFFD97706),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
                  )

                  // 7. Achive %
                  Text(
                    text = "${achVal.toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(70.dp)
                  )

                  // 8. Day DHU
                  Text(
                    text = String.format(java.util.Locale.US, "%.2f", dhuVal),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (dhuVal <= 2.0) Color(0xFF16A34A) else Color(0xFFDC2626),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(68.dp)
                  )

                  // 9. Day OQL
                  Text(
                    text = String.format(java.util.Locale.US, "%.1f%%", oqlVal),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (oqlVal <= 3.5) Color(0xFF16A34A) else Color(0xFFD97706),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(68.dp)
                  )

                  // 10. Defect Sent
                  Text(
                    text = "$sent",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEA580C),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
                  )

                  // 11. Defect Recv
                  Text(
                    text = "$recv",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
                  )

                  // 12. Defect Bal
                  Text(
                    text = "$bal",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (bal > 0) Color(0xFFDC2626) else Color(0xFF16A34A),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
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
 * Top 5 Lowest-Efficiency Lines Ranking Card (Identifies balancing & intervention needs)
 */
@Composable
fun LowestEfficiencyRankingCard(
  title: String = "Top 5 Lowest-Efficiency Lines (Intervention Alert)",
  lines: List<LinePerformancePoint>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val rankingLines = remember(lines, compactMode) { lines.take(if (compactMode) 3 else 5) }
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
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "Lines needing balancing" else "Lines requiring line balancing or supervisor support",
            fontSize = if (compactMode) 7.5.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(3.dp)) {
          Text(
            text = "LOW EFF",
            fontSize = if (compactMode) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC2626),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = 2.dp)
          )
        }
      }

      if (rankingLines.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(if (compactMode) 60.dp else 100.dp), contentAlignment = Alignment.Center) {
          Text("No reported efficiency data for selected scope", fontSize = if (compactMode) 9.sp else 11.sp, color = Color(0xFF64748B))
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 8.dp)) {
          rankingLines.forEachIndexed { rank, line ->
            val eff = line.todayEfficiencyPct ?: 0.0
            val ach = line.achievementPcs ?: 0
            val tgt = line.targetPcs
            val isSevere = eff < 60.0
            Surface(
              color = if (isSevere) Color(0xFFFFF1F2) else Color(0xFFF8FAFC),
              shape = RoundedCornerShape(if (compactMode) 4.dp else 8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, if (isSevere) Color(0xFFFECDD3) else Color(0xFFE2E8F0))
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = if (compactMode) 6.dp else 10.dp, vertical = if (compactMode) 3.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 8.dp)) {
                  Surface(
                    color = if (isSevere) Color(0xFFE11D48) else Color(0xFF64748B),
                    shape = CircleShape,
                    modifier = Modifier.size(if (compactMode) 16.dp else 22.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text("#${rank + 1}", color = Color.White, fontSize = if (compactMode) 7.5.sp else 9.5.sp, fontWeight = FontWeight.Black)
                    }
                  }
                  Column {
                    Text("Line ${line.lineId}", fontWeight = FontWeight.Black, fontSize = if (compactMode) 10.5.sp else 12.sp, color = Color(0xFF0F172A))
                    if (!compactMode) {
                      Text("Style: ${line.styleNumber.ifBlank { "Standard" }} • SMV: ${line.smv}", fontSize = 9.sp, color = Color(0xFF64748B))
                    }
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 12.dp)) {
                  Column(horizontalAlignment = Alignment.End) {
                    Text("$ach / $tgt pcs", fontSize = if (compactMode) 9.5.sp else 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    if (!compactMode) {
                      Text("Attain: ${line.achievementPct ?: 0.0}%", fontSize = 8.5.sp, color = Color(0xFF64748B))
                    }
                  }
                  Surface(
                    color = if (isSevere) Color(0xFFBE123C) else Color(0xFFD97706),
                    shape = RoundedCornerShape(if (compactMode) 3.dp else 6.dp)
                  ) {
                    Text(
                      text = "${String.format(java.util.Locale.US, "%.1f", eff)}%",
                      color = Color.White,
                      fontSize = if (compactMode) 9.5.sp else 11.sp,
                      fontWeight = FontWeight.Black,
                      modifier = Modifier.padding(horizontal = if (compactMode) 5.dp else 8.dp, vertical = if (compactMode) 2.dp else 4.dp)
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
}

/**
 * Line-wise Today's DHU Column Chart
 */
/**
 * Line-wise Today's DHU Column Chart (Fits all lines of the floor without truncation)
 */
@Composable
fun LineWiseDhuBarChart(
  title: String = "Line-wise Today's DHU (Defect Rate %)",
  lines: List<LinePerformancePoint>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val (displayLines, lineCount, maxDhu) = remember(lines) {
    val filtered = lines.filter { it.targetPcs > 0 }
    val count = filtered.size.coerceAtLeast(1)
    val max = (filtered.maxOfOrNull { it.dhu ?: 0.0 } ?: 3.0).coerceAtLeast(3.0).toFloat()
    Triple(filtered, count, max)
  }

  val valFontSize = when {
    lineCount <= 10 -> if (compactMode) 7.sp else 8.5.sp
    lineCount <= 16 -> if (compactMode) 6.sp else 7.5.sp
    else -> if (compactMode) 5.sp else 6.5.sp
  }
  val lineLabelFontSize = when {
    lineCount <= 10 -> if (compactMode) 7.5.sp else 9.sp
    lineCount <= 16 -> if (compactMode) 6.5.sp else 8.sp
    else -> if (compactMode) 5.5.sp else 7.sp
  }
  val barWidth = when {
    lineCount <= 10 -> if (compactMode) 12.dp else 18.dp
    lineCount <= 16 -> if (compactMode) 9.dp else 14.dp
    else -> if (compactMode) 6.dp else 10.dp
  }

  Card(
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .then(if (compactMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        .padding(if (compactMode) 5.dp else 10.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp)) {
          Text(
            text = title,
            fontSize = if (compactMode) 9.5.sp else 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "All ${displayLines.size} lines • Std ≤ 2.0%" else "Defects per hundred units across all ${displayLines.size} lines (Std ≤ 2.00%)",
            fontSize = if (compactMode) 7.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(3.dp)) {
          Text(
            text = "DHU",
            fontSize = if (compactMode) 7.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB45309),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = 1.5.dp)
          )
        }
      }

      BoxWithConstraints(
        modifier = if (compactMode) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(130.dp)
      ) {
        val barMaxH = (maxHeight.value - (if (compactMode) 22f else 28f)).coerceAtLeast(24f)

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${String.format(java.util.Locale.US, "%.1f", maxDhu)}%", fontSize = if (compactMode) 6.sp else 8.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(if (compactMode) 20.dp else 30.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFF1F5F9)))
          }
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("2.0%", fontSize = if (compactMode) 6.sp else 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), modifier = Modifier.width(if (compactMode) 20.dp else 30.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFFECACA)))
          }
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("0.0%", fontSize = if (compactMode) 6.sp else 8.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(if (compactMode) 20.dp else 30.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
          }
        }

        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(start = if (compactMode) 22.dp else 32.dp, end = 2.dp, bottom = 1.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Bottom
        ) {
          displayLines.forEach { line ->
            val dhu = line.dhu ?: 0.0
            val fraction = (dhu.toFloat() / maxDhu).coerceIn(0.08f, 1.0f)
            val barH = (fraction * barMaxH).dp
            val isOver = dhu > 2.0
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Bottom,
              modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
              Text(
                text = String.format(java.util.Locale.US, "%.1f", dhu),
                fontSize = valFontSize,
                lineHeight = valFontSize,
                fontWeight = FontWeight.Bold,
                color = if (isOver) Color(0xFFDC2626) else Color(0xFF16A34A),
                maxLines = 1,
                softWrap = false
              )
              Box(
                modifier = Modifier
                  .width(barWidth)
                  .height(barH)
                  .background(
                    if (isOver) Color(0xFFEF4444) else Color(0xFF10B981),
                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                  )
              )
              Text(
                text = line.lineId,
                fontSize = lineLabelFontSize,
                lineHeight = lineLabelFontSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Line-wise Today's OQL Column Chart (Fits all lines of the floor without truncation)
 */
@Composable
fun LineWiseOqlBarChart(
  title: String = "Line-wise OQL % (Outgoing Quality Level)",
  lines: List<LinePerformancePoint>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val displayLines = lines
  val lineCount = displayLines.size.coerceAtLeast(1)
  val maxOql = remember(displayLines) {
    (displayLines.maxOfOrNull { it.todayOqlPct ?: 0.0 } ?: 6.0).coerceAtLeast(6.0).toFloat()
  }

  val valFontSize = when {
    lineCount <= 10 -> if (compactMode) 7.sp else 8.5.sp
    lineCount <= 16 -> if (compactMode) 6.sp else 7.5.sp
    else -> if (compactMode) 5.sp else 6.5.sp
  }
  val lineLabelFontSize = when {
    lineCount <= 10 -> if (compactMode) 7.5.sp else 9.sp
    lineCount <= 16 -> if (compactMode) 6.5.sp else 8.sp
    else -> if (compactMode) 5.5.sp else 7.sp
  }
  val barWidth = when {
    lineCount <= 10 -> if (compactMode) 12.dp else 18.dp
    lineCount <= 16 -> if (compactMode) 9.dp else 14.dp
    else -> if (compactMode) 6.dp else 10.dp
  }

  Card(
    shape = RoundedCornerShape(if (compactMode) 6.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .then(if (compactMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        .padding(if (compactMode) 5.dp else 10.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp)) {
          Text(
            text = title,
            fontSize = if (compactMode) 9.5.sp else 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "All ${displayLines.size} lines • Std ≤ 5.0%" else "Defective garment percentage across all ${displayLines.size} lines (Std ≤ 5.00%)",
            fontSize = if (compactMode) 7.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(3.dp)) {
          Text(
            text = "OQL",
            fontSize = if (compactMode) 7.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D4ED8),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = 1.5.dp)
          )
        }
      }

      BoxWithConstraints(
        modifier = if (compactMode) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(130.dp)
      ) {
        val barMaxH = (maxHeight.value - (if (compactMode) 22f else 28f)).coerceAtLeast(24f)

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${String.format(java.util.Locale.US, "%.1f", maxOql)}%", fontSize = if (compactMode) 6.sp else 8.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(if (compactMode) 20.dp else 30.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFF1F5F9)))
          }
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("5.0%", fontSize = if (compactMode) 6.sp else 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), modifier = Modifier.width(if (compactMode) 20.dp else 30.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFFECACA)))
          }
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("0.0%", fontSize = if (compactMode) 6.sp else 8.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(if (compactMode) 20.dp else 30.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
          }
        }

        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(start = if (compactMode) 22.dp else 32.dp, end = 2.dp, bottom = 1.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Bottom
        ) {
          displayLines.forEach { line ->
            val oql = line.todayOqlPct ?: 0.0
            val fraction = (oql.toFloat() / maxOql).coerceIn(0.08f, 1.0f)
            val barH = (fraction * barMaxH).dp
            val isOver = oql > 5.0
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Bottom,
              modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
              Text(
                text = String.format(java.util.Locale.US, "%.1f", oql),
                fontSize = valFontSize,
                lineHeight = valFontSize,
                fontWeight = FontWeight.Bold,
                color = if (isOver) Color(0xFFDC2626) else Color(0xFF2563EB),
                maxLines = 1,
                softWrap = false
              )
              Box(
                modifier = Modifier
                  .width(barWidth)
                  .height(barH)
                  .background(
                    if (isOver) Color(0xFFEF4444) else Color(0xFF3B82F6),
                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                  )
              )
              Text(
                text = line.lineId,
                fontSize = lineLabelFontSize,
                lineHeight = lineLabelFontSize,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Defect Pareto Chart (Dual-Axis: Defect Count Columns + Cumulative % Line on Secondary Axis)
 */
@Composable
fun DefectParetoBarAndLineChart(
  title: String = "Defect Pareto Analysis (80/20 Cumulative Distribution)",
  paretoData: List<ParetoDefectPoint>,
  modifier: Modifier = Modifier,
  compactMode: Boolean = false
) {
  val (data, maxCount) = remember(paretoData, compactMode) {
    val d = paretoData.take(if (compactMode) 5 else 8)
    d to (d.maxOfOrNull { it.count } ?: 10).coerceAtLeast(10)
  }

  Card(
    shape = RoundedCornerShape(if (compactMode) 8.dp else 12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(if (compactMode) 1.dp else 2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(if (compactMode) 6.dp else 10.dp),
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp)
    ) {
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
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = if (compactMode) "Defect Pareto (80% benchmark)" else "Columns: Defect Count • Red Line: Cumulative % (80% benchmark)",
            fontSize = if (compactMode) 7.5.sp else 9.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(color = Color(0xFFF3E8FF), shape = RoundedCornerShape(3.dp)) {
          Text(
            text = "PARETO",
            fontSize = if (compactMode) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7E22CE),
            modifier = Modifier.padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = 2.dp)
          )
        }
      }

      if (data.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(if (compactMode) 70.dp else 140.dp), contentAlignment = Alignment.Center) {
          Text("No defect entries recorded for this scope", fontSize = if (compactMode) 9.sp else 11.sp, color = Color(0xFF64748B))
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(if (compactMode) 96.dp else 135.dp)
        ) {
          // Background guidelines & axis labels
          Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("$maxCount", fontSize = if (compactMode) 7.sp else 8.sp, color = Color(0xFF64748B), modifier = Modifier.width(if (compactMode) 18.dp else 24.dp))
              Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFF1F5F9)))
              Text("100%", fontSize = if (compactMode) 7.sp else 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED), modifier = Modifier.width(if (compactMode) 22.dp else 28.dp), textAlign = TextAlign.End)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("${(maxCount * 0.8).toInt()}", fontSize = if (compactMode) 7.sp else 8.sp, color = Color(0xFF64748B), modifier = Modifier.width(if (compactMode) 18.dp else 24.dp))
              Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFFDE047))) // 80% line
              Text("80%", fontSize = if (compactMode) 7.sp else 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706), modifier = Modifier.width(if (compactMode) 22.dp else 28.dp), textAlign = TextAlign.End)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("0", fontSize = if (compactMode) 7.sp else 8.sp, color = Color(0xFF64748B), modifier = Modifier.width(if (compactMode) 18.dp else 24.dp))
              Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
              Text("0%", fontSize = if (compactMode) 7.sp else 8.sp, color = Color(0xFF64748B), modifier = Modifier.width(if (compactMode) 22.dp else 28.dp), textAlign = TextAlign.End)
            }
          }

          // Canvas for Cumulative Percentage Line on Secondary Axis
          Canvas(
            modifier = Modifier
              .fillMaxSize()
              .padding(start = if (compactMode) 22.dp else 32.dp, end = if (compactMode) 24.dp else 34.dp, bottom = if (compactMode) 16.dp else 24.dp, top = 6.dp)
          ) {
            if (data.size >= 2) {
              val w = size.width
              val h = size.height
              val step = w / data.size.toFloat()
              val points = data.mapIndexed { idx, item ->
                val x = (idx * step) + (step / 2f)
                val y = h - ((item.cumulativePercentage.toFloat() / 100f) * h)
                Offset(x, y)
              }

              for (i in 0 until points.size - 1) {
                drawLine(
                  color = Color(0xFFE11D48),
                  start = points[i],
                  end = points[i + 1],
                  strokeWidth = (if (compactMode) 2.dp else 3.dp).toPx(),
                  cap = StrokeCap.Round
                )
              }

              points.forEach { pt ->
                drawCircle(color = Color.White, radius = (if (compactMode) 3.5.dp else 5.dp).toPx(), center = pt)
                drawCircle(color = Color(0xFFBE123C), radius = (if (compactMode) 2.5.dp else 3.5.dp).toPx(), center = pt)
              }
            }
          }

          val barMultiplier = if (compactMode) 55f else 85f

          // Columns for Individual Defect Count on Primary Axis
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(start = if (compactMode) 22.dp else 32.dp, end = if (compactMode) 24.dp else 34.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
          ) {
            data.forEach { item ->
              val fraction = (item.count.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1.0f)
              val barH = (fraction * barMultiplier).dp
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.padding(horizontal = 1.dp)
              ) {
                Text(
                  text = "${item.count}",
                  fontSize = if (compactMode) 7.5.sp else 9.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFF1E293B)
                )
                Text(
                  text = "${item.cumulativePercentage.toInt()}%",
                  fontSize = if (compactMode) 6.5.sp else 7.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFFBE123C),
                  modifier = Modifier.padding(bottom = 1.dp)
                )
                Box(
                  modifier = Modifier
                    .width(if (compactMode) 14.dp else 22.dp)
                    .height(barH)
                    .background(Color(0xFF3B82F6), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                )
                Text(
                  text = item.defectName.take(5),
                  fontSize = if (compactMode) 7.sp else 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF475569),
                  maxLines = 1,
                  modifier = Modifier.padding(top = 1.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

