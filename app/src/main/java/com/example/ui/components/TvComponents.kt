package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.model.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

val TvBgDark = Color(0xFF0A1522)
val TvPanelBg = Color(0xFF101E30)
val TvPanelBorder = Color(0xFF1B2E45)
val TvTextPrimary = Color(0xFFEAF0F7)
val TvTextMuted = Color(0xFF8FA3B8)
val TvRed = Color(0xFFFF6B6B)
val TvRedDarkText = Color(0xFF3B0A0A)
val TvAmber = Color(0xFFF5B73B)
val TvAmberDarkText = Color(0xFF3B2A00)
val TvGreen = Color(0xFF3DD68C)
val TvGreenDarkText = Color(0xFF052E22)
val TvBlue = Color(0xFF5AA9FF)
val TvBlueDarkText = Color(0xFF0A1522)
val TvSlateBar = Color(0xFF2A4560)
val TvGreyChipBg = Color(0xFF94A3B8)
val TvGreyChipText = Color(0xFF0F172A)
val TvOrangeTick = Color(0xFFF59E6B)

val STATIC_NFL_AQC_WEEKLY_PCT: List<Double> = listOf(
  7.50, 7.67, 7.63, 7.72, 7.30, 7.11, 7.00, 6.70, 6.35, 6.43, 6.41, 6.39
)

@Stable
class TvWallboardScale(
  val viewportWidthPx: Float,
  val viewportHeightPx: Float,
  val density: Float,
  val fontScale: Float
) {
  val unitPx: Float = min(viewportWidthPx / 1280f, viewportHeightPx / 720f).coerceAtLeast(0.5f)
  private val minTextPxAtViewport: Float = 16f * (viewportHeightPx / 1080f).coerceAtLeast(0.66f)

  fun dp(designUnits: Float): Dp = ((designUnits * unitPx) / density).dp

  fun sp(designUnits: Float): TextUnit {
    val enforcedPx = maxOf(designUnits * unitPx, minTextPxAtViewport)
    return with(Density(density, fontScale)) { enforcedPx.toSp() }
  }

  fun px(designUnits: Float): Float = maxOf(designUnits * unitPx, minTextPxAtViewport)
}

@Composable
fun rememberTvWallboardScale(maxWidth: Dp, maxHeight: Dp): TvWallboardScale {
  val d = LocalDensity.current
  return remember(maxWidth, maxHeight, d.density, d.fontScale) {
    val wPx = with(d) { maxWidth.toPx() }
    val hPx = with(d) { maxHeight.toPx() }
    TvWallboardScale(wPx, hPx, d.density, d.fontScale)
  }
}

fun tvTabularStyle(
  fontSize: TextUnit,
  fontWeight: FontWeight = FontWeight.Bold,
  color: Color = TvTextPrimary,
  textAlign: TextAlign = TextAlign.Start
): TextStyle = TextStyle(
  fontSize = fontSize,
  fontWeight = fontWeight,
  color = color,
  textAlign = textAlign,
  fontFeatureSettings = "tnum"
)

fun formatTvInt(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun makeTvPaint(
  textSizePx: Float,
  color: Color,
  align: Paint.Align = Paint.Align.CENTER,
  bold: Boolean = true
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
  this.textSize = textSizePx
  this.color = color.toArgb()
  this.textAlign = align
  this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
  this.fontFeatureSettings = "tnum"
}

@Composable
fun TvSelectorGroup(
  scale: TvWallboardScale,
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit
) {
  Row(
    modifier = modifier
      .background(TvPanelBg, RoundedCornerShape(scale.dp(8f)))
      .border(1.dp, TvPanelBorder, RoundedCornerShape(scale.dp(8f)))
      .padding(scale.dp(3f)),
    horizontalArrangement = Arrangement.spacedBy(scale.dp(2f)),
    verticalAlignment = Alignment.CenterVertically,
    content = content
  )
}

@Composable
fun TvFocusableButton(
  label: String,
  selected: Boolean,
  scale: TvWallboardScale,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  horizontalPadding: Float = 14f,
  verticalPadding: Float = 6f,
  fontSizeUnits: Float = 14f,
  activeBgColor: Color = TvBlue,
  activeTextColor: Color = TvBlueDarkText,
  inactiveTextColor: Color = TvTextMuted
) {
  var isFocused by remember { mutableStateOf(false) }
  val bgColor = when {
    selected -> activeBgColor
    isFocused -> TvSlateBar
    else -> Color.Transparent
  }
  val txtColor = when {
    selected -> activeTextColor
    isFocused -> TvTextPrimary
    else -> inactiveTextColor
  }
  val shape = RoundedCornerShape(scale.dp(6f))

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .onFocusChanged { isFocused = it.isFocused }
      .onPreviewKeyEvent { ev ->
        if (ev.type == KeyEventType.KeyDown &&
          (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)
        ) {
          onClick()
          true
        } else false
      }
      .focusable()
      .clickable { onClick() }
      .background(bgColor, shape)
      .then(if (isFocused) Modifier.border(2.dp, TvAmber, shape) else Modifier)
      .padding(horizontal = scale.dp(horizontalPadding), vertical = scale.dp(verticalPadding))
  ) {
    Text(
      text = label,
      style = tvTabularStyle(scale.sp(fontSizeUnits), FontWeight.Bold, txtColor),
      maxLines = 1
    )
  }
}

@Composable
fun TvStatusChip(
  text: String,
  backgroundColor: Color,
  textColor: Color,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier,
  borderColor: Color? = null
) {
  val shape = RoundedCornerShape(scale.dp(6f))
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .background(backgroundColor, shape)
      .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
      .padding(horizontal = scale.dp(10f), vertical = scale.dp(3f))
  ) {
    Text(
      text = text,
      style = tvTabularStyle(scale.sp(13f), FontWeight.Bold, textColor),
      maxLines = 1
    )
  }
}

@Composable
fun TvKpiTile(
  title: String,
  primaryValue: String,
  secondaryValue: String = "",
  subtitle: String,
  valueColor: Color,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(scale.dp(8f))
  Column(
    modifier = modifier
      .fillMaxHeight()
      .background(TvPanelBg, shape)
      .border(1.dp, TvPanelBorder, shape)
      .padding(horizontal = scale.dp(14f), vertical = scale.dp(8f)),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = title,
      style = tvTabularStyle(scale.sp(13f), FontWeight.SemiBold, TvTextMuted),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(scale.dp(2f))) {
      Text(
        text = primaryValue,
        style = tvTabularStyle(scale.sp(34f), FontWeight.Black, valueColor),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (secondaryValue.isNotBlank()) {
        Text(
          text = secondaryValue,
          style = tvTabularStyle(scale.sp(17f), FontWeight.Bold, TvTextMuted),
          maxLines = 1,
          modifier = Modifier.padding(bottom = scale.dp(3f))
        )
      }
    }
    Text(
      text = subtitle,
      style = tvTabularStyle(scale.sp(12f), FontWeight.SemiBold, TvTextMuted),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun TvChartPanel(
  title: String,
  subtitle: String,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier,
  badgeText: String? = null,
  badgeBgColor: Color = TvAmber,
  badgeTextColor: Color = TvAmberDarkText,
  headerLegend: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit
) {
  val shape = RoundedCornerShape(scale.dp(8f))
  Column(
    modifier = modifier
      .background(TvPanelBg, shape)
      .border(1.dp, TvPanelBorder, shape)
      .padding(horizontal = scale.dp(10f), vertical = scale.dp(8f)),
    verticalArrangement = Arrangement.spacedBy(scale.dp(4f))
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scale.dp(6f)),
        modifier = Modifier.weight(1f, fill = false)
      ) {
        Text(
          text = title,
          style = tvTabularStyle(scale.sp(15f), FontWeight.ExtraBold, TvTextPrimary),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (!badgeText.isNullOrBlank()) {
          Box(
            modifier = Modifier
              .background(badgeBgColor, RoundedCornerShape(scale.dp(4f)))
              .padding(horizontal = scale.dp(6f), vertical = scale.dp(2f))
          ) {
            Text(
              text = badgeText,
              style = tvTabularStyle(scale.sp(11f), FontWeight.Black, badgeTextColor),
              maxLines = 1
            )
          }
        }
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scale.dp(6f))
      ) {
        Text(
          text = subtitle,
          style = tvTabularStyle(scale.sp(12f), FontWeight.SemiBold, TvTextMuted, TextAlign.End),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (headerLegend != null) headerLegend()
      }
    }
    Box(modifier = Modifier.fillMaxWidth().weight(1f), content = content)
  }
}

@Composable
fun TvLegendSwatches(items: List<Pair<String, Color>>, scale: TvWallboardScale) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
    items.forEach { (label, color) ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.dp(4f))) {
        Box(modifier = Modifier.size(scale.dp(10f)).background(color, RoundedCornerShape(scale.dp(2f))))
        Text(text = label, style = tvTabularStyle(scale.sp(11f), FontWeight.SemiBold, TvTextMuted))
      }
    }
  }
}

@Composable
fun TvEmptyOrNoticeBox(
  message: String,
  subMessage: String = "",
  scale: TvWallboardScale,
  accentColor: Color = TvTextMuted
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(scale.dp(12f)),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = message,
      style = tvTabularStyle(scale.sp(15f), FontWeight.ExtraBold, accentColor, TextAlign.Center)
    )
    if (subMessage.isNotBlank()) {
      Spacer(modifier = Modifier.height(scale.dp(4f)))
      Text(
        text = subMessage,
        style = tvTabularStyle(scale.sp(12f), FontWeight.SemiBold, TvTextMuted, TextAlign.Center)
      )
    }
  }
}

@Composable
fun TvTargetVsAchievementChart(
  lines: List<LinePerformancePoint>,
  targetAvailableForPeriod: Boolean,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  val subtitle = when {
    !targetAvailableForPeriod -> "target not available for this period"
    lines.size > 13 -> "worst first · % = achieved"
    else -> "bar = target pcs · % = achieved"
  }
  TvChartPanel(title = "Live Target vs Achievement", subtitle = subtitle, scale = scale, modifier = modifier) {
    if (!targetAvailableForPeriod) {
      val reportedCount = lines.count { it.achievementPcs != null }
      val totalOut = lines.sumOf { it.achievementPcs ?: 0 }
      TvEmptyOrNoticeBox(
        message = "Target not available for this period",
        subMessage = "Setup stores today's target only · Period output: ${formatTvInt(totalOut)} pcs ($reportedCount reported lines)",
        scale = scale,
        accentColor = TvAmber
      )
    } else if (lines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No running lines with target assigned", scale = scale)
    } else if (lines.size <= 13) {
      val smallPx = scale.px(11f)
      val medPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = scale.px(34f)
        val rightPad = scale.px(6f)
        val topPad = scale.px(16f)
        val bottomPad = scale.px(32f)
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val bottomY = topPad + plotH
        val maxVal = maxOf(1200, ((lines.maxOfOrNull { maxOf(it.targetPcs, it.achievementPcs ?: 0) } ?: 1000) + 299) / 300 * 300)

        val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
        val targetPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)
        val idPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)

        for (i in 0..4) {
          val v = (maxVal * i) / 4
          val y = bottomY - (v.toFloat() / maxVal.toFloat()) * plotH
          drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
          drawContext.canvas.nativeCanvas.drawText(v.toString(), leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
        }

        val slotW = plotW / lines.size.toFloat()
        val outerBarW = (slotW * 0.68f).coerceAtMost(scale.px(26f))
        val innerBarW = (outerBarW * 0.58f).coerceAtLeast(scale.px(8f))
        val cr = CornerRadius(scale.px(2f), scale.px(2f))

        lines.forEachIndexed { idx, line ->
          val centerX = leftPad + slotW * (idx + 0.5f)
          if (line.targetPcs > 0) {
            val tgtH = ((line.targetPcs.toFloat() / maxVal.toFloat()) * plotH).coerceIn(2f, plotH)
            val tgtTop = bottomY - tgtH
            drawRoundRect(TvSlateBar, Offset(centerX - outerBarW / 2f, tgtTop), Size(outerBarW, tgtH), cr)
            drawContext.canvas.nativeCanvas.drawText(line.targetPcs.toString(), centerX, (tgtTop - scale.px(3f)).coerceAtLeast(smallPx), targetPaint)
          }
          val achPcs = line.achievementPcs
          val achPct = line.achievementPct
          val pctColor = when {
            achPcs == null || achPct == null -> TvTextMuted
            achPct >= 90.0 -> TvGreen
            achPct >= 60.0 -> TvAmber
            else -> TvRed
          }
          if (achPcs != null && achPcs > 0) {
            val achH = ((achPcs.toFloat() / maxVal.toFloat()) * plotH).coerceIn(2f, plotH)
            drawRoundRect(pctColor, Offset(centerX - innerBarW / 2f, bottomY - achH), Size(innerBarW, achH), cr)
          }
          drawContext.canvas.nativeCanvas.drawText(line.lineId, centerX, bottomY + medPx * 1.05f, idPaint)
          val pctText = if (achPcs == null || achPct == null) "—" else "${achPct.roundToInt()}%"
          val pctPaint = makeTvPaint(smallPx, pctColor, Paint.Align.CENTER)
          drawContext.canvas.nativeCanvas.drawText(pctText, centerX, bottomY + medPx * 1.05f + smallPx * 1.1f, pctPaint)
        }
      }
    } else {
      TvManyLinesHorizontalBars(
        lines = lines,
        scale = scale,
        sortSelector = { it.achievementPct ?: -1.0 },
        sortAscending = true,
        isOkLine = { (it.achievementPct ?: 0.0) >= 90.0 },
        maxScale = 100.0,
        dashedValue = 90.0,
        valueExtractor = { it.achievementPct },
        colorForValue = { pct ->
          when {
            pct == null -> TvTextMuted
            pct >= 90.0 -> TvGreen
            pct >= 60.0 -> TvAmber
            else -> TvRed
          }
        },
        formatValue = { pct -> if (pct == null) "N/R" else "${pct.roundToInt()}%" }
      )
    }
  }
}

@Composable
fun TvEfficiencyByLineChart(
  lines: List<LinePerformancePoint>,
  targetAvailableForPeriod: Boolean,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier,
  title: String = "Efficiency by Line",
  subtitle: String = "bar = month · narrow = today"
) {
  val resolvedSub = when {
    !targetAvailableForPeriod -> "target not available for this period"
    lines.size > 13 -> "worst first · dashed = 75% target"
    else -> subtitle
  }
  TvChartPanel(title = title, subtitle = resolvedSub, scale = scale, modifier = modifier) {
    if (!targetAvailableForPeriod) {
      TvEmptyOrNoticeBox(
        message = "Target not available for this period",
        subMessage = "Efficiency requires period-specific daily targets and working hours",
        scale = scale,
        accentColor = TvAmber
      )
    } else if (lines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No running lines with target assigned", scale = scale)
    } else if (lines.size <= 13) {
      val smallPx = scale.px(11f)
      val medPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = scale.px(30f)
        val rightPad = scale.px(6f)
        val topPad = scale.px(16f)
        val bottomPad = scale.px(32f)
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val bottomY = topPad + plotH

        val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
        val monthPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)
        val idPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)

        listOf(0, 25, 50, 75, 100).forEach { step ->
          val y = bottomY - (step / 100f) * plotH
          drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
          drawContext.canvas.nativeCanvas.drawText(step.toString(), leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
        }

        val slotW = plotW / lines.size.toFloat()
        val outerBarW = (slotW * 0.65f).coerceAtMost(scale.px(22f))
        val innerBarW = (outerBarW * 0.58f).coerceAtLeast(scale.px(8f))
        val cr = CornerRadius(scale.px(2f), scale.px(2f))

        lines.forEachIndexed { idx, line ->
          val centerX = leftPad + slotW * (idx + 0.5f)
          val mEff = line.monthlyEfficiencyPct.coerceIn(0.0, 100.0)
          if (mEff > 0.0) {
            val mH = ((mEff / 100.0) * plotH).toFloat().coerceIn(2f, plotH)
            val mTop = bottomY - mH
            drawRoundRect(TvSlateBar, Offset(centerX - outerBarW / 2f, mTop), Size(outerBarW, mH), cr)
            drawContext.canvas.nativeCanvas.drawText(mEff.roundToInt().toString(), centerX, (mTop - scale.px(3f)).coerceAtLeast(smallPx), monthPaint)
          }
          val tEff = line.todayEfficiencyPct
          val barColor = when {
            tEff == null -> TvTextMuted
            tEff >= 75.0 -> TvGreen
            tEff >= 60.0 -> TvAmber
            else -> TvRed
          }
          if (tEff != null && tEff > 0.0) {
            val tH = ((tEff.coerceIn(0.0, 100.0) / 100.0) * plotH).toFloat().coerceIn(2f, plotH)
            drawRoundRect(barColor, Offset(centerX - innerBarW / 2f, bottomY - tH), Size(innerBarW, tH), cr)
          }
          drawContext.canvas.nativeCanvas.drawText(line.lineId, centerX, bottomY + medPx * 1.05f, idPaint)
          val effText = if (tEff == null) "—" else "${tEff.roundToInt()}%"
          val effPaint = makeTvPaint(smallPx, barColor, Paint.Align.CENTER)
          drawContext.canvas.nativeCanvas.drawText(effText, centerX, bottomY + medPx * 1.05f + smallPx * 1.1f, effPaint)
        }

        val targetY = bottomY - 0.75f * plotH
        drawLine(TvTextPrimary, Offset(leftPad, targetY), Offset(size.width - rightPad, targetY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
        val targetPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.RIGHT)
        drawContext.canvas.nativeCanvas.drawText("75", size.width - rightPad, targetY - scale.px(3f), targetPaint)
      }
    } else {
      TvManyLinesHorizontalBars(
        lines = lines,
        scale = scale,
        sortSelector = { it.todayEfficiencyPct ?: -1.0 },
        sortAscending = true,
        isOkLine = { (it.todayEfficiencyPct ?: 0.0) >= 75.0 },
        maxScale = 100.0,
        dashedValue = 75.0,
        valueExtractor = { it.todayEfficiencyPct },
        colorForValue = { eff ->
          when {
            eff == null -> TvTextMuted
            eff >= 75.0 -> TvGreen
            eff >= 60.0 -> TvAmber
            else -> TvRed
          }
        },
        formatValue = { eff -> if (eff == null) "N/R" else "${eff.roundToInt()}%" }
      )
    }
  }
}

@Composable
fun TvManyLinesHorizontalBars(
  lines: List<LinePerformancePoint>,
  scale: TvWallboardScale,
  sortSelector: (LinePerformancePoint) -> Double,
  sortAscending: Boolean,
  isOkLine: (LinePerformancePoint) -> Boolean,
  maxScale: Double,
  dashedValue: Double,
  valueExtractor: (LinePerformancePoint) -> Double?,
  colorForValue: (Double?) -> Color,
  formatValue: (Double?) -> String
) {
  val reported = lines.filter { valueExtractor(it) != null }
  val unreported = lines.filter { valueExtractor(it) == null }
  val sortedReported = if (sortAscending) reported.sortedBy { sortSelector(it) } else reported.sortedByDescending { sortSelector(it) }
  val ordered = sortedReported + unreported

  val maxDisplay = if (ordered.size > 26) 25 else 26
  val displayed = ordered.take(maxDisplay)
  val extraLinesCount = (ordered.size - displayed.size).coerceAtLeast(0)
  val okCountInExtra = ordered.drop(maxDisplay).count { isOkLine(it) }

  val leftCol = displayed.take(13)
  val rightCol = displayed.drop(13)

  Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
    TvHorizontalBarColumnCanvas(
      items = leftCol, maxScale = maxScale, dashedValue = dashedValue,
      valueExtractor = valueExtractor, colorForValue = colorForValue, formatValue = formatValue,
      extraSummaryLabel = null, scale = scale, modifier = Modifier.weight(1f).fillMaxHeight()
    )
    TvHorizontalBarColumnCanvas(
      items = rightCol, maxScale = maxScale, dashedValue = dashedValue,
      valueExtractor = valueExtractor, colorForValue = colorForValue, formatValue = formatValue,
      extraSummaryLabel = if (extraLinesCount > 0) {
        if (okCountInExtra > 0) "$okCountInExtra lines OK (+$extraLinesCount)" else "+$extraLinesCount lines OK"
      } else null,
      scale = scale, modifier = Modifier.weight(1f).fillMaxHeight()
    )
  }
}

@Composable
private fun TvHorizontalBarColumnCanvas(
  items: List<LinePerformancePoint>,
  maxScale: Double,
  dashedValue: Double,
  valueExtractor: (LinePerformancePoint) -> Double?,
  colorForValue: (Double?) -> Color,
  formatValue: (Double?) -> String,
  extraSummaryLabel: String?,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  val labelPx = scale.px(11.5f)
  Canvas(modifier = modifier) {
    val rowH = size.height / 13f
    val barH = (rowH * 0.62f).coerceAtLeast(scale.px(8f))
    val leftLabelW = scale.px(36f)
    val rightValueW = scale.px(44f)
    val trackW = (size.width - leftLabelW - rightValueW).coerceAtLeast(10f)
    val cr = CornerRadius(scale.px(2f), scale.px(2f))
    val idPaint = makeTvPaint(labelPx, TvTextPrimary, Paint.Align.LEFT)

    items.forEachIndexed { idx, line ->
      val centerY = rowH * (idx + 0.5f)
      val barTop = centerY - barH / 2f
      val v = valueExtractor(line)
      val c = colorForValue(v)

      drawContext.canvas.nativeCanvas.drawText(line.lineId, 0f, centerY + labelPx * 0.35f, idPaint)
      drawRoundRect(TvBgDark, Offset(leftLabelW, barTop), Size(trackW, barH), cr)
      if (v != null && v > 0.0) {
        val fillW = ((v / maxScale).coerceIn(0.0, 1.0) * trackW).toFloat().coerceAtLeast(2f)
        drawRoundRect(c, Offset(leftLabelW, barTop), Size(fillW, barH), cr)
      }
      val valPaint = makeTvPaint(labelPx, if (v == null) TvTextMuted else TvTextPrimary, Paint.Align.RIGHT)
      drawContext.canvas.nativeCanvas.drawText(formatValue(v), size.width, centerY + labelPx * 0.35f, valPaint)
    }

    if (extraSummaryLabel != null) {
      val centerY = rowH * 12.5f
      val okPaint = makeTvPaint(labelPx, TvGreen, Paint.Align.RIGHT)
      drawContext.canvas.nativeCanvas.drawText(extraSummaryLabel, size.width, centerY + labelPx * 0.35f, okPaint)
    }

    if (dashedValue > 0.0 && dashedValue <= maxScale) {
      val dashX = leftLabelW + ((dashedValue / maxScale) * trackW).toFloat()
      drawLine(TvTextPrimary, Offset(dashX, 0f), Offset(dashX, size.height), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
    }
  }
}

@Composable
fun TvLowestEfficiencyLinesCard(
  lowestLines: List<LinePerformancePoint>,
  targetAvailableForPeriod: Boolean,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Lowest Efficiency Lines", subtitle = "balancing alert", scale = scale, modifier = modifier) {
    if (!targetAvailableForPeriod) {
      TvEmptyOrNoticeBox(message = "Target not available for this period", scale = scale, accentColor = TvAmber)
    } else if (lowestLines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No reported efficiency data yet", scale = scale)
    } else {
      val textPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val rowH = size.height / 5f
        val barH = (rowH * 0.58f).coerceAtMost(scale.px(24f))
        val leftW = scale.px(34f)
        val rightW = scale.px(46f)
        val trackW = (size.width - leftW - rightW).coerceAtLeast(10f)
        val cr = CornerRadius(scale.px(2f), scale.px(2f))
        val idPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.LEFT)
        val valPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.RIGHT)

        lowestLines.take(5).forEachIndexed { idx, line ->
          val centerY = rowH * (idx + 0.5f)
          val barTop = centerY - barH / 2f
          val eff = (line.todayEfficiencyPct ?: 0.0).coerceIn(0.0, 100.0)
          val barColor = when {
            eff >= 75.0 -> TvGreen
            eff >= 60.0 -> TvAmber
            else -> TvRed
          }
          drawContext.canvas.nativeCanvas.drawText(line.lineId, 0f, centerY + textPx * 0.35f, idPaint)
          drawRoundRect(TvBgDark, Offset(leftW, barTop), Size(trackW, barH), cr)
          val fillW = ((eff / 100.0) * trackW).toFloat().coerceAtLeast(2f)
          drawRoundRect(barColor, Offset(leftW, barTop), Size(fillW, barH), cr)
          drawContext.canvas.nativeCanvas.drawText("${eff.roundToInt()}%", size.width, centerY + textPx * 0.35f, valPaint)
        }
        val targetX = leftW + 0.75f * trackW
        drawLine(TvTextPrimary, Offset(targetX, 0f), Offset(targetX, size.height), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
      }
    }
  }
}

@Composable
fun TvHourlyOutputChart(
  hourlySlots: List<TvHourlySlotPoint>,
  targetAvailableForPeriod: Boolean,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  val hourlyTarget = hourlySlots.firstOrNull()?.targetPcs ?: 0
  val subtitle = if (targetAvailableForPeriod && hourlyTarget > 0) {
    "floor total · dashed = ${formatTvInt(hourlyTarget)} pcs/hr"
  } else {
    "floor total · target not available for this period"
  }
  TvChartPanel(title = "Hourly Output vs Target", subtitle = subtitle, scale = scale, modifier = modifier) {
    if (hourlySlots.isEmpty() || hourlySlots.none { it.hasProduction }) {
      TvEmptyOrNoticeBox(message = "No hourly production entries recorded in this period", scale = scale)
    } else {
      val smallPx = scale.px(11f)
      val medPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = scale.px(34f)
        val rightPad = scale.px(6f)
        val topPad = scale.px(16f)
        val bottomPad = scale.px(22f)
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val bottomY = topPad + plotH
        val rawMax = maxOf(if (targetAvailableForPeriod) hourlyTarget else 0, hourlySlots.maxOfOrNull { it.outputPcs } ?: 0, 100)
        val maxVal = ((rawMax + 99) / 100) * 100

        val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
        val valPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.CENTER)
        val slotPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)
        val nrPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)

        for (i in 0..4) {
          val v = (maxVal * i) / 4
          val y = bottomY - (v.toFloat() / maxVal.toFloat()) * plotH
          drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
          drawContext.canvas.nativeCanvas.drawText(v.toString(), leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
        }

        val slotW = plotW / hourlySlots.size.toFloat()
        val barW = (slotW * 0.45f).coerceAtMost(scale.px(26f))
        val cr = CornerRadius(scale.px(2f), scale.px(2f))

        hourlySlots.forEachIndexed { idx, slot ->
          val centerX = leftPad + slotW * (idx + 0.5f)
          if (slot.hasProduction && slot.outputPcs > 0) {
            val barH = ((slot.outputPcs.toFloat() / maxVal.toFloat()) * plotH).coerceIn(2f, plotH)
            val barTop = bottomY - barH
            val barColor = when {
              !targetAvailableForPeriod || hourlyTarget <= 0 -> TvBlue
              slot.outputPcs >= hourlyTarget * 0.9 -> TvGreen
              slot.outputPcs >= hourlyTarget * 0.6 -> TvAmber
              else -> TvRed
            }
            drawRoundRect(barColor, Offset(centerX - barW / 2f, barTop), Size(barW, barH), cr)
            drawContext.canvas.nativeCanvas.drawText(slot.outputPcs.toString(), centerX, (barTop - scale.px(3f)).coerceAtLeast(smallPx), valPaint)
          } else if (!slot.hasProduction) {
            drawContext.canvas.nativeCanvas.drawText("—", centerX, bottomY - scale.px(4f), nrPaint)
          }
          drawContext.canvas.nativeCanvas.drawText(slot.shortRangeLabel, centerX, bottomY + medPx * 1.1f, slotPaint)
        }

        if (targetAvailableForPeriod && hourlyTarget > 0) {
          val tgtY = bottomY - ((hourlyTarget.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f) * plotH)
          drawLine(TvTextPrimary, Offset(leftPad, tgtY), Offset(size.width - rightPad, tgtY), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
          val tgtPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.RIGHT)
          drawContext.canvas.nativeCanvas.drawText(hourlyTarget.toString(), size.width - rightPad, (tgtY - scale.px(3f)).coerceAtLeast(smallPx), tgtPaint)
        }
      }
    }
  }
}

@Composable
fun TvDowntimeByCauseCard(
  nptSlices: List<NptCategorySlice>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Downtime by Cause (min)", subtitle = "incidents in period", scale = scale, modifier = modifier) {
    if (nptSlices.isEmpty()) {
      TvEmptyOrNoticeBox(message = "0 min downtime", subMessage = "No downtime incidents recorded in this period", scale = scale, accentColor = TvGreen)
    } else {
      val textPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val rowH = size.height / 5f
        val barH = (rowH * 0.58f).coerceAtMost(scale.px(24f))
        val leftW = scale.px(126f)
        val rightW = scale.px(48f)
        val trackW = (size.width - leftW - rightW).coerceAtLeast(10f)
        val maxMins = (nptSlices.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)
        val cr = CornerRadius(scale.px(2f), scale.px(2f))
        val namePaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.LEFT)
        val valPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.RIGHT)

        nptSlices.take(5).forEachIndexed { idx, slice ->
          val centerY = rowH * (idx + 0.5f)
          val barTop = centerY - barH / 2f
          val barColor = when (idx) {
            0 -> TvRed
            1 -> TvAmber
            else -> TvBlue
          }
          drawContext.canvas.nativeCanvas.drawText(slice.categoryName, 0f, centerY + textPx * 0.35f, namePaint)
          drawRoundRect(TvBgDark, Offset(leftW, barTop), Size(trackW, barH), cr)
          val fillW = ((slice.minutes.toFloat() / maxMins.toFloat()) * trackW).coerceIn(2f, trackW)
          drawRoundRect(barColor, Offset(leftW, barTop), Size(fillW, barH), cr)
          drawContext.canvas.nativeCanvas.drawText("${slice.minutes}m", size.width, centerY + textPx * 0.35f, valPaint)
        }
      }
    }
  }
}

@Composable
fun TvOpenStoppagesCard(
  stoppages: List<TvOpenStoppageItem>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Open Stoppages", subtitle = "line · cause · open", scale = scale, modifier = modifier) {
    if (stoppages.isEmpty()) {
      TvEmptyOrNoticeBox(message = "0 Open Stoppages", subMessage = "All lines clear", scale = scale, accentColor = TvGreen)
    } else {
      Column(modifier = Modifier.fillMaxSize().padding(top = scale.dp(4f)), verticalArrangement = Arrangement.Top) {
        stoppages.take(4).forEach { item ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .border(BorderStroke(0.5.dp, TvPanelBorder))
              .padding(vertical = scale.dp(6f), horizontal = scale.dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scale.dp(6f))
          ) {
            Text(
              text = item.lineId,
              style = tvTabularStyle(scale.sp(18f), FontWeight.Black, TvTextPrimary),
              modifier = Modifier.width(scale.dp(40f)),
              maxLines = 1
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.causeName,
                style = tvTabularStyle(scale.sp(13.5f), FontWeight.Bold, TvTextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = item.responseStatusText,
                style = tvTabularStyle(scale.sp(11.5f), FontWeight.SemiBold, TvTextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            val minColor = if (item.minutesOpen >= 30) TvRed else TvAmber
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = item.minutesOpen.toString(),
                style = tvTabularStyle(scale.sp(17f), FontWeight.Black, minColor, TextAlign.End)
              )
              Text(
                text = " min",
                style = tvTabularStyle(scale.sp(11f), FontWeight.SemiBold, TvTextMuted)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TvDhuByLineChart(
  lines: List<LinePerformancePoint>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(
    title = "DHU by Line",
    subtitle = if (lines.size > 13) "worst first · dashed = 2.0 limit" else "dashed = 2.0 limit",
    scale = scale,
    modifier = modifier
  ) {
    if (lines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No running lines with target assigned", scale = scale)
    } else if (lines.size <= 13) {
      val smallPx = scale.px(11f)
      val medPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = scale.px(32f)
        val rightPad = scale.px(6f)
        val topPad = scale.px(16f)
        val bottomPad = scale.px(22f)
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val bottomY = topPad + plotH
        val maxDhu = maxOf(5.0, (lines.mapNotNull { it.dhu }.maxOrNull() ?: 2.5) + 0.5)

        val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
        val valPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.CENTER)
        val idPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)
        val nrPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)

        for (i in 0..4) {
          val v = (maxDhu * i) / 4.0
          val y = bottomY - ((v / maxDhu) * plotH).toFloat()
          drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
          val lbl = if (v == v.toInt().toDouble()) v.toInt().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
          drawContext.canvas.nativeCanvas.drawText(lbl, leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
        }

        val slotW = plotW / lines.size.toFloat()
        val barW = (slotW * 0.48f).coerceAtMost(scale.px(18f))
        val cr = CornerRadius(scale.px(2f), scale.px(2f))

        lines.forEachIndexed { idx, line ->
          val centerX = leftPad + slotW * (idx + 0.5f)
          val dhu = line.dhu
          if (dhu != null && dhu > 0.0) {
            val barH = ((dhu.coerceAtMost(maxDhu) / maxDhu) * plotH).toFloat().coerceIn(2f, plotH)
            val barTop = bottomY - barH
            val barColor = when {
              dhu <= 2.0 -> TvGreen
              dhu <= 3.0 -> TvAmber
              else -> TvRed
            }
            drawRoundRect(barColor, Offset(centerX - barW / 2f, barTop), Size(barW, barH), cr)
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", dhu), centerX, (barTop - scale.px(3f)).coerceAtLeast(smallPx), valPaint)
          } else if (dhu == null) {
            drawContext.canvas.nativeCanvas.drawText("—", centerX, bottomY - scale.px(4f), nrPaint)
          }
          drawContext.canvas.nativeCanvas.drawText(line.lineId, centerX, bottomY + medPx * 1.1f, idPaint)
        }

        val limitY = bottomY - ((2.0 / maxDhu) * plotH).toFloat()
        drawLine(TvTextPrimary, Offset(leftPad, limitY), Offset(size.width - rightPad, limitY), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
        val limitPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.RIGHT)
        drawContext.canvas.nativeCanvas.drawText("2.0", size.width - rightPad, limitY - scale.px(3f), limitPaint)
      }
    } else {
      val maxDhu = maxOf(5.0, (lines.mapNotNull { it.dhu }.maxOrNull() ?: 2.5) + 0.5)
      TvManyLinesHorizontalBars(
        lines = lines, scale = scale, sortSelector = { it.dhu ?: -1.0 }, sortAscending = false,
        isOkLine = { (it.dhu ?: 99.0) <= 2.0 }, maxScale = maxDhu, dashedValue = 2.0,
        valueExtractor = { it.dhu },
        colorForValue = { dhu ->
          when {
            dhu == null -> TvTextMuted
            dhu <= 2.0 -> TvGreen
            dhu <= 3.0 -> TvAmber
            else -> TvRed
          }
        },
        formatValue = { dhu -> if (dhu == null) "N/R" else String.format(Locale.US, "%.1f", dhu) }
      )
    }
  }
}

@Composable
fun TvOqlByLineChart(
  lines: List<LinePerformancePoint>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier,
  title: String = "OQL by Line",
  subtitle: String = "tick = monthly · dashed = 5.0%"
) {
  TvChartPanel(
    title = title,
    subtitle = if (lines.size > 13) "worst first · dashed = 5.0%" else subtitle,
    scale = scale,
    modifier = modifier
  ) {
    if (lines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No running lines with target assigned", scale = scale)
    } else if (lines.size <= 13) {
      val smallPx = scale.px(11f)
      val medPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = scale.px(34f)
        val rightPad = scale.px(6f)
        val topPad = scale.px(16f)
        val bottomPad = scale.px(22f)
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val bottomY = topPad + plotH
        val maxOql = maxOf(5.5, (lines.mapNotNull { it.todayOqlPct }.maxOrNull() ?: 4.0) + 0.5)

        val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
        val valPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.CENTER)
        val idPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)
        val nrPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)

        for (i in 0..4) {
          val v = (maxOql * i) / 4.0
          val y = bottomY - ((v / maxOql) * plotH).toFloat()
          drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
          val lbl = if (v == 0.0) "0" else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
          drawContext.canvas.nativeCanvas.drawText(lbl, leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
        }

        val slotW = plotW / lines.size.toFloat()
        val barW = (slotW * 0.44f).coerceAtMost(scale.px(15f))
        val tickHalfW = (slotW * 0.36f).coerceAtMost(scale.px(11f))
        val cr = CornerRadius(scale.px(2f), scale.px(2f))

        lines.forEachIndexed { idx, line ->
          val centerX = leftPad + slotW * (idx + 0.5f)
          val oql = line.todayOqlPct
          if (oql != null && oql > 0.0) {
            val barH = ((oql.coerceAtMost(maxOql) / maxOql) * plotH).toFloat().coerceIn(2f, plotH)
            val barTop = bottomY - barH
            val barColor = when {
              oql <= 3.5 -> TvGreen
              oql <= 5.0 -> TvAmber
              else -> TvRed
            }
            drawRoundRect(barColor, Offset(centerX - barW / 2f, barTop), Size(barW, barH), cr)
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.2f", oql), centerX, (barTop - scale.px(3f)).coerceAtLeast(smallPx), valPaint)
          } else if (oql == null) {
            drawContext.canvas.nativeCanvas.drawText("—", centerX, bottomY - scale.px(4f), nrPaint)
          }
          if (line.monthlyOqlPct > 0.0) {
            val tickY = bottomY - ((line.monthlyOqlPct.coerceAtMost(maxOql) / maxOql) * plotH).toFloat()
            drawLine(TvOrangeTick, Offset(centerX - tickHalfW, tickY), Offset(centerX + tickHalfW, tickY), strokeWidth = scale.px(3f))
          }
          drawContext.canvas.nativeCanvas.drawText(line.lineId, centerX, bottomY + medPx * 1.1f, idPaint)
        }

        val limitY = bottomY - ((5.0 / maxOql) * plotH).toFloat()
        drawLine(TvTextPrimary, Offset(leftPad, limitY), Offset(size.width - rightPad, limitY), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
        val limitPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.RIGHT)
        drawContext.canvas.nativeCanvas.drawText("5.00", size.width - rightPad, limitY - scale.px(3f), limitPaint)
      }
    } else {
      val maxOql = maxOf(5.5, (lines.mapNotNull { it.todayOqlPct }.maxOrNull() ?: 4.0) + 0.5)
      TvManyLinesHorizontalBars(
        lines = lines, scale = scale, sortSelector = { it.todayOqlPct ?: -1.0 }, sortAscending = false,
        isOkLine = { (it.todayOqlPct ?: 99.0) <= 3.5 }, maxScale = maxOql, dashedValue = 5.0,
        valueExtractor = { it.todayOqlPct },
        colorForValue = { oql ->
          when {
            oql == null -> TvTextMuted
            oql <= 3.5 -> TvGreen
            oql <= 5.0 -> TvAmber
            else -> TvRed
          }
        },
        formatValue = { oql -> if (oql == null) "N/R" else "${String.format(Locale.US, "%.1f", oql)}%" }
      )
    }
  }
}

@Composable
fun TvDefectParetoCard(
  paretoDefects: List<ParetoDefectPoint>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Top Defects (Pareto)", subtitle = "count · cumulative %", scale = scale, modifier = modifier) {
    if (paretoDefects.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No defects recorded in this period", scale = scale)
    } else {
      val textPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val rowH = size.height / 5f
        val barH = (rowH * 0.58f).coerceAtMost(scale.px(24f))
        val leftW = scale.px(94f)
        val rightW = scale.px(72f)
        val trackW = (size.width - leftW - rightW).coerceAtLeast(10f)
        val maxCount = (paretoDefects.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        val cr = CornerRadius(scale.px(2f), scale.px(2f))
        val namePaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.LEFT)
        val valPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.RIGHT)

        paretoDefects.take(5).forEachIndexed { idx, item ->
          val centerY = rowH * (idx + 0.5f)
          val barTop = centerY - barH / 2f
          drawContext.canvas.nativeCanvas.drawText(item.defectName, 0f, centerY + textPx * 0.35f, namePaint)
          drawRoundRect(TvBgDark, Offset(leftW, barTop), Size(trackW, barH), cr)
          val fillW = ((item.count.toFloat() / maxCount.toFloat()) * trackW).coerceIn(2f, trackW)
          drawRoundRect(TvBlue, Offset(leftW, barTop), Size(fillW, barH), cr)
          drawContext.canvas.nativeCanvas.drawText("${item.count} · ${item.cumulativePercentage.roundToInt()}%", size.width, centerY + textPx * 0.35f, valPaint)
        }
      }
    }
  }
}

@Composable
fun TvTop5DefectsDhuCard(
  topDefects: List<DefectSummary>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Last Day Top 5 Defects", subtitle = "DHU", scale = scale, modifier = modifier) {
    if (topDefects.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No defects recorded in this period", scale = scale)
    } else {
      val textPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val rowH = size.height / 5f
        val barH = (rowH * 0.58f).coerceAtMost(scale.px(21f))
        val leftW = scale.px(94f)
        val rightW = scale.px(44f)
        val trackW = (size.width - leftW - rightW).coerceAtLeast(10f)
        val maxDhu = (topDefects.maxOfOrNull { it.dhu } ?: 0.5).coerceAtLeast(0.1)
        val cr = CornerRadius(scale.px(2f), scale.px(2f))
        val namePaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.LEFT)
        val valPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.RIGHT)

        topDefects.take(5).forEachIndexed { idx, item ->
          val centerY = rowH * (idx + 0.5f)
          val barTop = centerY - barH / 2f
          drawContext.canvas.nativeCanvas.drawText(item.defectType, 0f, centerY + textPx * 0.35f, namePaint)
          drawRoundRect(TvBgDark, Offset(leftW, barTop), Size(trackW, barH), cr)
          val fillW = ((item.dhu / maxDhu) * trackW).toFloat().coerceIn(2f, trackW)
          drawRoundRect(TvBlue, Offset(leftW, barTop), Size(fillW, barH), cr)
          drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.2f", item.dhu), size.width, centerY + textPx * 0.35f, valPaint)
        }
      }
    }
  }
}

@Composable
fun TvHourlyDhuOqlChart(
  hourlySlots: List<TvHourlySlotPoint>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(
    title = "Hourly DHU and OQL",
    subtitle = "floor total",
    scale = scale,
    modifier = modifier,
    headerLegend = { TvLegendSwatches(items = listOf("DHU" to TvRed, "OQL" to TvAmber), scale = scale) }
  ) {
    if (hourlySlots.none { it.checkedPcs > 0 }) {
      TvEmptyOrNoticeBox(message = "No hourly quality inspection records in this period", scale = scale)
    } else {
      val smallPx = scale.px(11f)
      val medPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val leftPad = scale.px(30f)
        val rightPad = scale.px(8f)
        val topPad = scale.px(14f)
        val bottomPad = scale.px(22f)
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val bottomY = topPad + plotH
        val maxVal = maxOf(5.0, (hourlySlots.mapNotNull { maxOf(it.dhuPct ?: 0.0, it.oqlPct ?: 0.0) }.maxOrNull() ?: 4.0) + 0.5)

        val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
        val hourPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)

        for (i in 0..4) {
          val v = (maxVal * i) / 4.0
          val y = bottomY - ((v / maxVal) * plotH).toFloat()
          drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
          drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", v).trimEnd('0').trimEnd('.'), leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
        }

        val limitY = bottomY - ((2.0 / maxVal) * plotH).toFloat()
        drawLine(TvTextPrimary, Offset(leftPad, limitY), Offset(size.width - rightPad, limitY), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
        val limitPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.RIGHT)
        drawContext.canvas.nativeCanvas.drawText("limit 2", size.width - rightPad, limitY - scale.px(3f), limitPaint)

        val slotW = plotW / hourlySlots.size.toFloat()
        fun drawMetricSeries(selector: (TvHourlySlotPoint) -> Double?, color: Color) {
          val pts = mutableListOf<Triple<Float, Float, Double>>()
          hourlySlots.forEachIndexed { idx, slot ->
            val v = selector(slot)
            if (v != null) {
              val x = leftPad + slotW * (idx + 0.5f)
              val y = bottomY - ((v.coerceIn(0.0, maxVal) / maxVal) * plotH).toFloat()
              pts.add(Triple(x, y, v))
            }
          }
          if (pts.size >= 2) {
            val path = Path()
            pts.forEachIndexed { i, (x, y, _) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            drawPath(path, color, style = Stroke(width = scale.px(3f), cap = StrokeCap.Round, join = StrokeJoin.Round))
          }
          pts.forEach { (x, y, _) -> drawCircle(color, radius = scale.px(3.5f), center = Offset(x, y)) }
          pts.lastOrNull()?.let { (lx, ly, lv) ->
            val lblPaint = makeTvPaint(medPx, color, Paint.Align.CENTER)
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", lv), lx, (ly - scale.px(6f)).coerceAtLeast(medPx), lblPaint)
          }
        }

        drawMetricSeries({ it.dhuPct }, TvRed)
        drawMetricSeries({ it.oqlPct }, TvAmber)
        hourlySlots.forEachIndexed { idx, slot ->
          drawContext.canvas.nativeCanvas.drawText(slot.shortHourLabel, leftPad + slotW * (idx + 0.5f), bottomY + medPx * 1.1f, hourPaint)
        }
      }
    }
  }
}

@Composable
fun TvDefectRectificationPendingCard(
  lines: List<LinePerformancePoint>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  val pendingLines = remember(lines) {
    lines.filter { (it.defectBalance + it.spotBalance) > 0 }
      .sortedByDescending { it.defectBalance + it.spotBalance }
      .take(5)
  }
  TvChartPanel(
    title = "Defect Rectification Pending",
    subtitle = "pcs",
    scale = scale,
    modifier = modifier,
    headerLegend = { TvLegendSwatches(items = listOf("sewing" to TvAmber, "spot" to TvBlue), scale = scale) }
  ) {
    if (pendingLines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "0 Pending Defects", subMessage = "All sewing & spot repairs returned", scale = scale, accentColor = TvGreen)
    } else {
      val textPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val rowH = size.height / 5f
        val barH = (rowH * 0.58f).coerceAtMost(scale.px(24f))
        val leftW = scale.px(44f)
        val rightW = scale.px(44f)
        val trackW = (size.width - leftW - rightW).coerceAtLeast(10f)
        val maxTotal = (pendingLines.maxOfOrNull { it.defectBalance + it.spotBalance } ?: 1).coerceAtLeast(1)
        val cr = CornerRadius(scale.px(2f), scale.px(2f))
        val idPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.LEFT)
        val valPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.RIGHT)

        pendingLines.forEachIndexed { idx, line ->
          val centerY = rowH * (idx + 0.5f)
          val barTop = centerY - barH / 2f
          val sewBal = line.defectBalance.coerceAtLeast(0)
          val spotBal = line.spotBalance.coerceAtLeast(0)
          drawContext.canvas.nativeCanvas.drawText(line.lineId, 0f, centerY + textPx * 0.35f, idPaint)
          drawRoundRect(TvBgDark, Offset(leftW, barTop), Size(trackW, barH), cr)
          val sewW = ((sewBal.toFloat() / maxTotal.toFloat()) * trackW).coerceIn(0f, trackW)
          val spotW = ((spotBal.toFloat() / maxTotal.toFloat()) * trackW).coerceIn(0f, trackW - sewW)
          if (sewW > 0f) drawRoundRect(TvAmber, Offset(leftW, barTop), Size(sewW, barH), cr)
          if (spotW > 0f) drawRoundRect(TvBlue, Offset(leftW + sewW, barTop), Size(spotW, barH), cr)
          drawContext.canvas.nativeCanvas.drawText((sewBal + spotBal).toString(), size.width, centerY + textPx * 0.35f, valPaint)
        }
      }
    }
  }
}

@Composable
fun TvMainDefectWorstLinesCard(
  lines: List<LinePerformancePoint>,
  topDefects: List<DefectSummary>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  val worstLines = remember(lines) {
    lines.filter { it.dhu != null && (it.dhu ?: 0.0) > 0.0 }
      .sortedByDescending { it.dhu ?: 0.0 }
      .take(5)
  }
  val fallbackDefect = topDefects.firstOrNull()?.defectType ?: "—"
  TvChartPanel(title = "Main Defect by Worst Lines", subtitle = "line · defect · DHU", scale = scale, modifier = modifier) {
    if (worstLines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No line DHU records in this period", scale = scale)
    } else {
      Column(modifier = Modifier.fillMaxSize().padding(top = scale.dp(4f)), verticalArrangement = Arrangement.Top) {
        worstLines.forEach { line ->
          val dhu = line.dhu ?: 0.0
          val dhuColor = when {
            dhu <= 2.0 -> TvGreen
            dhu <= 3.0 -> TvAmber
            else -> TvRed
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .border(BorderStroke(0.5.dp, TvPanelBorder))
              .padding(vertical = scale.dp(6f), horizontal = scale.dp(4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scale.dp(6f))
          ) {
            Text(
              text = line.lineId,
              style = tvTabularStyle(scale.sp(18f), FontWeight.Black, TvTextPrimary),
              modifier = Modifier.width(scale.dp(40f)),
              maxLines = 1
            )
            Text(
              text = line.mainDefectName.ifBlank { fallbackDefect },
              style = tvTabularStyle(scale.sp(14f), FontWeight.Bold, TvTextPrimary),
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = String.format(Locale.US, "%.1f", dhu),
              style = tvTabularStyle(scale.sp(17f), FontWeight.Black, dhuColor, TextAlign.End),
              modifier = Modifier.width(scale.dp(48f)),
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

@Composable
fun TvSemiGaugeCard(
  title: String,
  subtitle: String,
  value: Double?,
  maxValue: Double,
  targetOrLimit: Double,
  valueSuffix: String,
  isLowerBetter: Boolean,
  unavailableMessage: String? = null,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = title, subtitle = subtitle, scale = scale, modifier = modifier) {
    if (unavailableMessage != null) {
      TvEmptyOrNoticeBox(message = unavailableMessage, scale = scale, accentColor = TvAmber)
    } else {
      val v = (value ?: 0.0).coerceIn(0.0, maxValue)
      val arcColor = if (isLowerBetter) {
        when {
          v <= targetOrLimit * 0.7 -> TvGreen
          v <= targetOrLimit -> TvAmber
          else -> TvRed
        }
      } else {
        when {
          v >= targetOrLimit -> TvGreen
          v >= targetOrLimit * 0.8 -> TvAmber
          else -> TvRed
        }
      }
      val bigPx = scale.px(28f)
      val smallPx = scale.px(11f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeW = scale.px(16f)
        val radius = min(size.width * 0.40f, size.height * 0.68f).coerceAtLeast(20f)
        val centerX = size.width / 2f
        val centerY = size.height * 0.78f
        val arcTopLeft = Offset(centerX - radius, centerY - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        drawArc(TvBgDark, 180f, 180f, false, arcTopLeft, arcSize, style = Stroke(width = strokeW, cap = StrokeCap.Butt))
        val sweep = ((v / maxValue).coerceIn(0.0, 1.0) * 180.0).toFloat()
        drawArc(arcColor, 180f, sweep, false, arcTopLeft, arcSize, style = Stroke(width = strokeW, cap = StrokeCap.Butt))

        val angleRad = Math.toRadians(180.0 + (targetOrLimit / maxValue).coerceIn(0.0, 1.0) * 180.0)
        val innerR = radius - strokeW * 0.65f
        val outerR = radius + strokeW * 0.65f
        drawLine(
          TvTextPrimary,
          Offset(centerX + (innerR * cos(angleRad)).toFloat(), centerY + (innerR * sin(angleRad)).toFloat()),
          Offset(centerX + (outerR * cos(angleRad)).toFloat(), centerY + (outerR * sin(angleRad)).toFloat()),
          strokeWidth = scale.px(2.5f)
        )

        val valText = if (value == null) "--" else "${String.format(Locale.US, if (maxValue <= 15.0) "%.2f" else "%.1f", v)}$valueSuffix"
        drawContext.canvas.nativeCanvas.drawText(valText, centerX, centerY - scale.px(4f), makeTvPaint(bigPx, arcColor, Paint.Align.CENTER))
        val minPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)
        drawContext.canvas.nativeCanvas.drawText("0", centerX - radius, centerY + smallPx * 1.25f, minPaint)
        drawContext.canvas.nativeCanvas.drawText("${maxValue.toInt()}$valueSuffix", centerX + radius, centerY + smallPx * 1.25f, minPaint)
      }
    }
  }
}

@Composable
fun TvBigMetricCard(
  title: String,
  subtitle: String,
  valueText: String,
  footerText: String,
  valueColor: Color,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = title, subtitle = subtitle, scale = scale, modifier = modifier) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text = valueText, style = tvTabularStyle(scale.sp(38f), FontWeight.Black, valueColor, TextAlign.Center), maxLines = 1)
      Spacer(modifier = Modifier.height(scale.dp(4f)))
      Text(
        text = footerText,
        style = tvTabularStyle(scale.sp(12f), FontWeight.SemiBold, TvTextMuted, TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun TvOperatorVsMachineDonutCard(
  operatorsCount: Int,
  machinesCount: Int,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Operator vs Machine", subtitle = "planned", scale = scale, modifier = modifier) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
        val strokeW = scale.px(16f)
        val radius = (min(size.width, size.height) * 0.36f).coerceAtLeast(14f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val rectTopLeft = Offset(center.x - radius, center.y - radius)
        val rectSize = Size(radius * 2f, radius * 2f)
        val total = (operatorsCount + machinesCount).coerceAtLeast(1)
        val opSweep = (operatorsCount.toFloat() / total.toFloat()) * 360f

        drawArc(TvBlue, 0f, 360f, false, rectTopLeft, rectSize, style = Stroke(width = strokeW))
        drawArc(TvOrangeTick, -90f, opSweep, false, rectTopLeft, rectSize, style = Stroke(width = strokeW))
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.dp(4f))) {
          Text(text = "■ $operatorsCount", style = tvTabularStyle(scale.sp(12.5f), FontWeight.ExtraBold, TvOrangeTick))
          Text(text = "Operators (planned)", style = tvTabularStyle(scale.sp(12f), FontWeight.Bold, TvTextPrimary))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.dp(4f))) {
          Text(text = "■ $machinesCount", style = tvTabularStyle(scale.sp(12.5f), FontWeight.ExtraBold, TvBlue))
          Text(text = "Machines (planned)", style = tvTabularStyle(scale.sp(12f), FontWeight.Bold, TvTextPrimary))
        }
      }
    }
  }
}

@Composable
fun TvFloorFactoryEfficiencyCard(
  points: List<TvFloorEfficiencyPoint>,
  targetAvailableForPeriod: Boolean,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(
    title = "Floor & Factory Efficiency",
    subtitle = if (targetAvailableForPeriod) "bar = month · narrow = today" else "bar = month · target N/A for period",
    scale = scale,
    modifier = modifier
  ) {
    val smallPx = scale.px(11f)
    val medPx = scale.px(11.5f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val leftPad = scale.px(28f)
      val rightPad = scale.px(4f)
      val topPad = scale.px(14f)
      val bottomPad = scale.px(28f)
      val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
      val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
      val bottomY = topPad + plotH

      val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
      val monthPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.CENTER)
      val labelPaint = makeTvPaint(medPx, TvTextPrimary, Paint.Align.CENTER)

      listOf(0, 25, 50, 75, 100).forEach { step ->
        val y = bottomY - (step / 100f) * plotH
        drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(step.toString(), leftPad - scale.px(3f), y + smallPx * 0.35f, axisPaint)
      }

      if (points.isNotEmpty()) {
        val slotW = plotW / points.size.toFloat()
        val outerW = (slotW * 0.65f).coerceAtMost(scale.px(32f))
        val innerW = (outerW * 0.58f).coerceAtLeast(scale.px(12f))
        val cr = CornerRadius(scale.px(2f), scale.px(2f))

        points.forEachIndexed { idx, pt ->
          val centerX = leftPad + slotW * (idx + 0.5f)
          val mEff = pt.monthlyEfficiencyPct.coerceIn(0.0, 100.0)
          if (mEff > 0.0) {
            val mH = ((mEff / 100.0) * plotH).toFloat().coerceIn(2f, plotH)
            val mTop = bottomY - mH
            drawRoundRect(TvSlateBar, Offset(centerX - outerW / 2f, mTop), Size(outerW, mH), cr)
            drawContext.canvas.nativeCanvas.drawText(mEff.roundToInt().toString(), centerX, (mTop - scale.px(2f)).coerceAtLeast(smallPx), monthPaint)
          }
          val tEff = pt.todayEfficiencyPct
          val tColor = when {
            tEff == null -> TvTextMuted
            tEff >= 75.0 -> TvGreen
            tEff >= 65.0 -> TvAmber
            else -> TvRed
          }
          if (tEff != null && tEff > 0.0) {
            val tH = ((tEff.coerceIn(0.0, 100.0) / 100.0) * plotH).toFloat().coerceIn(2f, plotH)
            drawRoundRect(tColor, Offset(centerX - innerW / 2f, bottomY - tH), Size(innerW, tH), cr)
          }
          drawContext.canvas.nativeCanvas.drawText(pt.label, centerX, bottomY + medPx, labelPaint)
          val pctText = if (tEff == null) "—" else "${String.format(Locale.US, "%.1f", tEff)}%"
          drawContext.canvas.nativeCanvas.drawText(pctText, centerX, bottomY + medPx + smallPx * 1.05f, makeTvPaint(smallPx, tColor, Paint.Align.CENTER))
        }
      }
      val targetY = bottomY - 0.75f * plotH
      drawLine(TvTextPrimary, Offset(leftPad, targetY), Offset(size.width - rightPad, targetY), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
    }
  }
}

@Composable
fun TvWeeklyBsOqlChartCard(
  bsOqlData: List<BsOqlWeeklyPoint> = DEFAULT_BS_OQL_WEEKLY_DATA,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(
    title = "Weekly BS OQL and NFL AQC",
    subtitle = "W32–W43",
    badgeText = "STATIC DATA",
    badgeBgColor = TvAmber,
    badgeTextColor = TvAmberDarkText,
    scale = scale,
    modifier = modifier,
    headerLegend = { TvLegendSwatches(items = listOf("BS OQL" to TvBlue, "NFL AQC (static)" to TvOrangeTick), scale = scale) }
  ) {
    val smallPx = scale.px(11f)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val leftPad = scale.px(30f)
      val rightPad = scale.px(8f)
      val topPad = scale.px(14f)
      val bottomPad = scale.px(20f)
      val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
      val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
      val bottomY = topPad + plotH
      val yMin = 4.0
      val yMax = 8.5

      val axisPaint = makeTvPaint(smallPx, TvTextMuted, Paint.Align.RIGHT)
      val weekPaint = makeTvPaint(smallPx, TvTextPrimary, Paint.Align.CENTER)
      val bsPaint = makeTvPaint(smallPx, TvBlue, Paint.Align.CENTER)
      val aqcPaint = makeTvPaint(smallPx, TvOrangeTick, Paint.Align.CENTER)

      listOf(4.0, 5.1, 6.2, 7.4, 8.5).forEach { v ->
        val y = bottomY - (((v - yMin) / (yMax - yMin)) * plotH).toFloat()
        drawLine(TvPanelBorder, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", v), leftPad - scale.px(4f), y + smallPx * 0.35f, axisPaint)
      }

      if (bsOqlData.isNotEmpty()) {
        val count = bsOqlData.size
        fun xAt(i: Int): Float = if (count <= 1) leftPad + plotW / 2f else leftPad + (plotW * i.toFloat() / (count - 1).toFloat())
        fun yAt(v: Double): Float = bottomY - (((v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin)) * plotH).toFloat()

        val aqcPath = Path()
        bsOqlData.indices.forEach { i ->
          val aqcVal = STATIC_NFL_AQC_WEEKLY_PCT.getOrElse(i) { 6.5 }
          val px = xAt(i)
          val py = yAt(aqcVal)
          if (i == 0) aqcPath.moveTo(px, py) else aqcPath.lineTo(px, py)
        }
        drawPath(aqcPath, TvOrangeTick, style = Stroke(width = scale.px(2.8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        bsOqlData.indices.forEach { i ->
          val aqcVal = STATIC_NFL_AQC_WEEKLY_PCT.getOrElse(i) { 6.5 }
          val px = xAt(i)
          val py = yAt(aqcVal)
          drawCircle(TvOrangeTick, radius = scale.px(3.2f), center = Offset(px, py))
          drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.2f", aqcVal), px, (py - scale.px(5f)).coerceAtLeast(smallPx), aqcPaint)
        }

        val bsPath = Path()
        bsOqlData.forEachIndexed { i, pt ->
          val px = xAt(i)
          val py = yAt(pt.oqlPct)
          if (i == 0) bsPath.moveTo(px, py) else bsPath.lineTo(px, py)
        }
        drawPath(bsPath, TvBlue, style = Stroke(width = scale.px(2.8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        bsOqlData.forEachIndexed { i, pt ->
          val px = xAt(i)
          val py = yAt(pt.oqlPct)
          drawCircle(TvBlue, radius = scale.px(3.2f), center = Offset(px, py))
          drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.2f", pt.oqlPct), px, (py + smallPx * 1.15f).coerceAtMost(bottomY - scale.px(2f)), bsPaint)
          drawContext.canvas.nativeCanvas.drawText("W" + pt.week.replace("W", "").trim(), px, bottomY + smallPx * 1.15f, weekPaint)
        }
      }
    }
  }
}

@Composable
fun TvPoor5DhuLinesCard(
  highestDhuLines: List<LineDhuSummary>,
  scale: TvWallboardScale,
  modifier: Modifier = Modifier
) {
  TvChartPanel(title = "Last Day Poor 5 DHU Lines", subtitle = "dashed = limit 2.0", scale = scale, modifier = modifier) {
    if (highestDhuLines.isEmpty()) {
      TvEmptyOrNoticeBox(message = "No line DHU data in this period", scale = scale)
    } else {
      val textPx = scale.px(12f)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val rowH = size.height / 5f
        val barH = (rowH * 0.58f).coerceAtMost(scale.px(21f))
        val leftW = scale.px(52f)
        val rightW = scale.px(48f)
        val trackW = (size.width - leftW - rightW).coerceAtLeast(10f)
        val maxDhu = maxOf(5.0, (highestDhuLines.maxOfOrNull { it.dhu } ?: 3.0) + 0.5)
        val cr = CornerRadius(scale.px(2f), scale.px(2f))
        val idPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.LEFT)
        val valPaint = makeTvPaint(textPx, TvTextPrimary, Paint.Align.RIGHT)

        highestDhuLines.take(5).forEachIndexed { idx, item ->
          val centerY = rowH * (idx + 0.5f)
          val barTop = centerY - barH / 2f
          val barColor = when {
            item.dhu <= 2.0 -> TvGreen
            item.dhu <= 3.0 -> TvAmber
            else -> TvRed
          }
          drawContext.canvas.nativeCanvas.drawText(item.lineId, 0f, centerY + textPx * 0.35f, idPaint)
          drawRoundRect(TvBgDark, Offset(leftW, barTop), Size(trackW, barH), cr)
          val fillW = ((item.dhu / maxDhu) * trackW).toFloat().coerceIn(2f, trackW)
          drawRoundRect(barColor, Offset(leftW, barTop), Size(fillW, barH), cr)
          drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.2f", item.dhu), size.width, centerY + textPx * 0.35f, valPaint)
        }
        val limitX = leftW + ((2.0 / maxDhu) * trackW).toFloat()
        drawLine(TvTextPrimary, Offset(limitX, 0f), Offset(limitX, size.height), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
      }
    }
  }
}
