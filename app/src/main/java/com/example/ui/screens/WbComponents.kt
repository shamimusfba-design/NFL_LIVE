package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** TV-only theme. Phone screens never use these. */
object WbTheme {
  val BG = Color(0xFF0A1522)
  val PANEL = Color(0xFF101E30)
  val LINE = Color(0xFF1B2E45)
  val TXT = Color(0xFFEAF0F7)
  val MUTED = Color(0xFF8FA3B8)
  val RED = Color(0xFFFF6B6B)
  val AMBER = Color(0xFFF5B73B)
  val GREEN = Color(0xFF3DD68C)
  val BLUE = Color(0xFF5AA9FF)
  val ORANGE = Color(0xFFF59E6B)
  val DIM = Color(0xFF2A4560)
  val GREY = Color(0xFF94A3B8)
}

/** One place for every colour threshold on the TV (edit here, not in the screens). */
object WbThresholds {
  const val ACH_GREEN = 90.0      // achievement % at/above = green
  const val ACH_AMBER = 60.0      // achievement % at/above = amber, below = red
  const val EFF_AMBER_GAP = 15.0  // efficiency within this many points under target = amber
  const val DHU_AMBER_FACTOR = 1.5
  // OQL limits come from the one shared definition (commitment 4.5, Level 1 below 7, red above 10).
  const val OQL_COMMITMENT = com.example.model.OqlThresholds.COMMITMENT
  const val STALE_SECONDS = 300
}

fun wbAchColor(p: Double?): Color = when {
  p == null -> WbTheme.GREY
  p >= WbThresholds.ACH_GREEN -> WbTheme.GREEN
  p >= WbThresholds.ACH_AMBER -> WbTheme.AMBER
  else -> WbTheme.RED
}

fun wbEffColor(v: Double, target: Double): Color = when {
  v >= target -> WbTheme.GREEN
  v >= target - WbThresholds.EFF_AMBER_GAP -> WbTheme.AMBER
  else -> WbTheme.RED
}

fun wbDhuColor(v: Double, limit: Double): Color = when {
  v <= limit -> WbTheme.GREEN
  v <= limit * WbThresholds.DHU_AMBER_FACTOR -> WbTheme.AMBER
  else -> WbTheme.RED
}

fun wbOqlColor(v: Double, target: Double): Color = when (com.example.model.OqlThresholds.bandOf(v, target)) {
  com.example.model.OqlBand.WITHIN_COMMITMENT -> WbTheme.GREEN
  com.example.model.OqlBand.LEVEL1 -> WbTheme.AMBER
  com.example.model.OqlBand.LEVEL1_LOST -> WbTheme.ORANGE
  com.example.model.OqlBand.CRITICAL -> WbTheme.RED
}

fun wbF1(d: Double): String = String.format(Locale.US, "%.1f", d)
fun wbF2(d: Double): String = String.format(Locale.US, "%.2f", d)
fun wbInt(n: Int): String = String.format(Locale.US, "%,d", n)

@Composable
fun WbText(
  text: String,
  size: Int,
  color: Color = WbTheme.TXT,
  weight: FontWeight = FontWeight.SemiBold,
  modifier: Modifier = Modifier,
  align: TextAlign? = null
) {
  Text(
    text = text,
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Clip,
    textAlign = align,
    modifier = modifier
  )
}

@Composable
fun WbPanel(
  title: String,
  caption: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .background(WbTheme.PANEL, RoundedCornerShape(8.dp))
      .border(1.dp, WbTheme.LINE, RoundedCornerShape(8.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      WbText(title, 15, weight = FontWeight.ExtraBold)
      if (caption.isNotEmpty()) {
        Spacer(Modifier.width(8.dp))
        WbText(caption, 12, WbTheme.MUTED, FontWeight.Medium, Modifier.weight(1f), TextAlign.End)
      }
    }
    Spacer(Modifier.height(4.dp))
    content()
  }
}

@Composable
fun WbKpiTile(
  label: String,
  value: String,
  color: Color,
  sub: String,
  modifier: Modifier = Modifier,
  valueSize: Int = 38
) {
  Column(
    modifier = modifier
      .background(WbTheme.PANEL, RoundedCornerShape(8.dp))
      .border(1.dp, WbTheme.LINE, RoundedCornerShape(8.dp))
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.Center
  ) {
    WbText(label, 13, WbTheme.MUTED)
    WbText(value, valueSize, color, FontWeight.Black)
    WbText(sub, 12, WbTheme.MUTED, FontWeight.Medium)
  }
}

@Composable
fun WbChip(text: String, bg: Color, fg: Color) {
  Box(
    Modifier
      .background(bg, RoundedCornerShape(6.dp))
      .padding(horizontal = 10.dp, vertical = 3.dp)
  ) {
    WbText(text, 14, fg, FontWeight.ExtraBold)
  }
}

/** Remote-control friendly selector (D-pad focus shows an amber border). */
@Composable
fun <T> WbSegment(
  items: List<T>,
  selected: T,
  label: (T) -> String,
  fontSp: Int,
  tagPrefix: String,
  onSelect: (T) -> Unit
) {
  Row(
    Modifier
      .background(WbTheme.PANEL, RoundedCornerShape(8.dp))
      .border(1.dp, WbTheme.LINE, RoundedCornerShape(8.dp))
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    items.forEach { item ->
      val sel = item == selected
      var focused by remember { mutableStateOf(false) }
      Surface(
        onClick = { onSelect(item) },
        color = if (sel) WbTheme.BLUE else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .testTag(tagPrefix + label(item).replace(" ", "_").lowercase(Locale.US))
          .onFocusChanged { focused = it.isFocused }
          .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) WbTheme.AMBER else Color.Transparent,
            shape = RoundedCornerShape(6.dp)
          )
          .focusable()
      ) {
        WbText(
          label(item), fontSp,
          if (sel) WbTheme.BG else WbTheme.MUTED, FontWeight.Bold,
          Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
      }
    }
  }
}

/** Vertical bars: wide bar = back series, narrow bar = front series, orange tick = marker, dashed line = tick. */
@Composable
fun WbColumnChart(
  labels: List<String>,
  back: List<Double?>,
  front: List<Double?>,
  vmax: Double,
  frontColor: (Int) -> Color,
  below: List<Pair<String, Color>>?,
  labelOnBack: Boolean,
  marker: List<Double?>?,
  tick: Double?,
  fmt: (Double) -> String,
  modifier: Modifier = Modifier
) {
  val n = labels.size
  val safeMax = if (vmax <= 0.0) 1.0 else vmax
  Column(modifier) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      Row(Modifier.fillMaxSize()) {
        for (i in 0 until n) {
          Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
            val b = back.getOrNull(i)
            val f = front.getOrNull(i)
            val m = marker?.getOrNull(i)
            if (b != null && b > 0.0) {
              Box(
                Modifier
                  .fillMaxWidth(0.72f)
                  .fillMaxHeight((b / safeMax).toFloat().coerceIn(0.01f, 1f))
                  .background(WbTheme.DIM, RoundedCornerShape(2.dp))
              )
            }
            if (f != null && f > 0.0) {
              Box(
                Modifier
                  .fillMaxWidth(0.42f)
                  .fillMaxHeight((f / safeMax).toFloat().coerceIn(0.01f, 1f))
                  .background(frontColor(i), RoundedCornerShape(2.dp))
              )
            }
            if (m != null && m > 0.0) {
              Box(
                Modifier
                  .fillMaxWidth(0.72f)
                  .fillMaxHeight((m / safeMax).toFloat().coerceIn(0.01f, 1f)),
                contentAlignment = Alignment.TopCenter
              ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(WbTheme.ORANGE))
              }
            }
            val lv = if (labelOnBack) b else f
            if (lv != null && lv > 0.0) {
              val frac = (lv / safeMax).toFloat().coerceIn(0f, 0.88f)
              Box(
                Modifier.fillMaxWidth().fillMaxHeight(frac + 0.12f),
                contentAlignment = Alignment.TopCenter
              ) {
                WbText(fmt(lv), 11, if (labelOnBack) WbTheme.MUTED else WbTheme.TXT, FontWeight.Bold)
              }
            }
          }
        }
      }
      if (tick != null) {
        Canvas(Modifier.fillMaxSize()) {
          val y = size.height * (1f - (tick / safeMax).toFloat().coerceIn(0f, 1f))
          drawLine(
            WbTheme.TXT, Offset(0f, y), Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
          )
        }
      }
    }
    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
      for (i in 0 until n) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          WbText(labels[i], 12, WbTheme.TXT, FontWeight.Bold)
          val bt = below?.getOrNull(i)
          if (bt != null && bt.first.isNotEmpty()) WbText(bt.first, 11, bt.second, FontWeight.ExtraBold)
        }
      }
    }
  }
}

data class WbRow(val name: String, val value: Double, val text: String, val color: Color)

/** Horizontal ranked bars (used for ranking lists and for charts with many lines). */
@Composable
fun WbRankBars(
  rows: List<WbRow>,
  vmax: Double,
  limit: Double?,
  nameWidth: Dp,
  fontSp: Int,
  modifier: Modifier = Modifier
) {
  val safeMax = if (vmax <= 0.0) 1.0 else vmax
  Column(modifier) {
    rows.forEach { r ->
      Row(
        Modifier.fillMaxWidth().weight(1f).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        WbText(r.name, fontSp, WbTheme.TXT, FontWeight.Bold, Modifier.width(nameWidth))
        Box(
          Modifier
            .weight(1f)
            .fillMaxHeight(0.62f)
            .heightIn(max = 24.dp)
            .background(WbTheme.BG, RoundedCornerShape(3.dp))
        ) {
          Box(
            Modifier
              .fillMaxHeight()
              .fillMaxWidth((r.value / safeMax).toFloat().coerceIn(0.01f, 1f))
              .background(r.color, RoundedCornerShape(3.dp))
          )
          if (limit != null) {
            Box(
              Modifier.fillMaxHeight().fillMaxWidth((limit / safeMax).toFloat().coerceIn(0f, 1f)),
              contentAlignment = Alignment.CenterEnd
            ) {
              Box(Modifier.width(2.dp).fillMaxHeight().background(WbTheme.TXT))
            }
          }
        }
        Spacer(Modifier.width(6.dp))
        WbText(r.text, fontSp, WbTheme.TXT, FontWeight.ExtraBold, Modifier.width((fontSp * 4.6f).dp), TextAlign.End)
      }
    }
  }
}

/** Two-part bars (e.g. sewing + spot pending). */
@Composable
fun WbStackBars(
  rows: List<Triple<String, Int, Int>>,
  colorA: Color,
  colorB: Color,
  modifier: Modifier = Modifier
) {
  val maxTotal = (rows.maxOfOrNull { it.second + it.third } ?: 1).coerceAtLeast(1)
  Column(modifier) {
    rows.forEach { r ->
      Row(
        Modifier.fillMaxWidth().weight(1f).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        WbText(r.first, 14, WbTheme.TXT, FontWeight.Bold, Modifier.width(48.dp))
        Box(
          Modifier
            .weight(1f)
            .fillMaxHeight(0.62f)
            .heightIn(max = 24.dp)
            .background(WbTheme.BG, RoundedCornerShape(3.dp))
        ) {
          Row(Modifier.fillMaxHeight().fillMaxWidth((r.second + r.third).toFloat() / maxTotal)) {
            if (r.second > 0) Box(Modifier.weight(r.second.toFloat()).fillMaxHeight().background(colorA))
            if (r.third > 0) Box(Modifier.weight(r.third.toFloat()).fillMaxHeight().background(colorB))
          }
        }
        Spacer(Modifier.width(6.dp))
        WbText((r.second + r.third).toString(), 14, WbTheme.TXT, FontWeight.ExtraBold, Modifier.width(48.dp), TextAlign.End)
      }
    }
  }
}

@Composable
fun WbGauge(
  value: Double,
  vmax: Double,
  target: Double?,
  color: Color,
  text: String,
  modifier: Modifier = Modifier
) {
  Box(modifier, contentAlignment = Alignment.BottomCenter) {
    Canvas(Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height * 0.82f
      val d = min(size.width * 0.96f, cy * 2f)
      val sw = d * 0.11f
      val r = d / 2f - sw / 2f
      val tl = Offset(cx - r, cy - r)
      val sz = Size(2f * r, 2f * r)
      drawArc(WbTheme.BG, 180f, 180f, false, tl, sz, style = Stroke(sw))
      val frac = (value / vmax).toFloat().coerceIn(0f, 1f)
      drawArc(color, 180f, 180f * frac, false, tl, sz, style = Stroke(sw))
      if (target != null) {
        val a = Math.PI * (1.0 - (target / vmax).coerceIn(0.0, 1.0))
        val r1 = r - sw * 0.7f
        val r2 = r + sw * 0.7f
        drawLine(
          WbTheme.TXT,
          Offset(cx + (r1 * cos(a)).toFloat(), cy - (r1 * sin(a)).toFloat()),
          Offset(cx + (r2 * cos(a)).toFloat(), cy - (r2 * sin(a)).toFloat()),
          strokeWidth = 3.dp.toPx()
        )
      }
    }
    WbText(text, 30, color, FontWeight.Black)
  }
}

@Composable
fun WbDonut(a: Int, b: Int, colorA: Color, colorB: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val d = min(size.width, size.height)
    val sw = d * 0.22f
    val r = d / 2f - sw / 2f
    val tl = Offset((size.width - 2f * r) / 2f, (size.height - 2f * r) / 2f)
    val sz = Size(2f * r, 2f * r)
    val total = (a + b).coerceAtLeast(1)
    val sa = 360f * a / total
    drawArc(colorA, -90f, sa, false, tl, sz, style = Stroke(sw))
    drawArc(colorB, -90f + sa, 360f - sa, false, tl, sz, style = Stroke(sw))
  }
}

data class WbSeries(val values: List<Double?>, val color: Color)

@Composable
fun WbLineChart(
  labels: List<String>,
  series: List<WbSeries>,
  lo: Double,
  hi: Double,
  limit: Double?,
  modifier: Modifier = Modifier,
  showValues: Boolean = false
) {
  val measurer = androidx.compose.ui.text.rememberTextMeasurer()
  val axisStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = WbTheme.MUTED)
  val axisWidth = 30.dp
  Column(modifier) {
    Canvas(Modifier.weight(1f).fillMaxWidth()) {
      val n = labels.size
      if (n == 0 || hi <= lo) return@Canvas
      val axisPx = axisWidth.toPx()
      val plotW = size.width - axisPx
      fun px(i: Int): Float = axisPx + plotW * (i + 0.5f) / n
      fun py(v: Double): Float = size.height * (1f - ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f))
      // grid lines with the value of each line on the left axis
      for (k in 0..4) {
        val y = size.height * k / 4f
        drawLine(WbTheme.LINE, Offset(axisPx, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val tick = hi - (hi - lo) * k / 4.0
        val tickText = if (hi - lo >= 8.0) String.format(java.util.Locale.US, "%.0f", tick)
        else String.format(java.util.Locale.US, "%.1f", tick)
        val layout = measurer.measure(tickText, axisStyle)
        val ty = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
        drawText(layout, topLeft = Offset(0f, ty))
      }
      if (limit != null) {
        drawLine(
          WbTheme.TXT, Offset(axisPx, py(limit)), Offset(size.width, py(limit)),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
      }
      series.forEachIndexed { si, s ->
        val path = Path()
        var started = false
        s.values.forEachIndexed { i, v ->
          if (v != null) {
            val x = px(i)
            val y = py(v)
            if (!started) {
              path.moveTo(x, y)
              started = true
            } else {
              path.lineTo(x, y)
            }
            drawCircle(s.color, 4.dp.toPx(), Offset(x, y))
          } else {
            started = false
          }
        }
        drawPath(path, s.color, style = Stroke(3.dp.toPx()))
        if (showValues && n <= 16) {
          // First series is labelled above its points, the others below, so the two lines do not collide.
          val valueStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 10.sp, color = s.color, fontWeight = FontWeight.Bold
          )
          s.values.forEachIndexed { i, v ->
            if (v != null) {
              val layout = measurer.measure(String.format(java.util.Locale.US, "%.2f", v), valueStyle)
              val above = si == 0
              val ty = if (above) py(v) - layout.size.height - 5.dp.toPx() else py(v) + 6.dp.toPx()
              val clampedY = ty.coerceIn(0f, size.height - layout.size.height)
              val tx = (px(i) - layout.size.width / 2f).coerceIn(axisPx, size.width - layout.size.width)
              drawText(layout, topLeft = Offset(tx, clampedY))
            }
          }
        }
      }
    }
    Row(Modifier.fillMaxWidth().padding(start = axisWidth, top = 3.dp)) {
      labels.forEach { l ->
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
          WbText(l, 12, WbTheme.TXT, FontWeight.Bold)
        }
      }
    }
  }
}

/** One line (machine line) value set that can be drawn as columns (<= 13 lines) or ranked bars (more lines). */
data class WbLineVal(
  val name: String,
  val back: Double?,
  val front: Double?,
  val frontColor: Color,
  val belowText: String,
  val belowColor: Color,
  val marker: Double?,
  val rankValue: Double,
  val rankText: String,
  val rankColor: Color,
  val sortKey: Double
)

/**
 * Adaptive line chart for 43 inch TVs:
 * up to 13 lines = vertical columns; 14 to 26 lines = two columns of horizontal bars, worst first
 * (names stay 13sp+ on the 1280x720 design canvas = about 20px at 1080p); more than 26 lines = the 26 worst only.
 */
@Composable
fun WbLineAdaptive(
  items: List<WbLineVal>,
  vmax: Double,
  tick: Double?,
  rankMax: Double,
  rankLimit: Double?,
  labelOnBack: Boolean,
  fmt: (Double) -> String,
  modifier: Modifier = Modifier
) {
  if (items.isEmpty()) {
    Box(modifier, contentAlignment = Alignment.Center) {
      WbText("No lines with a target for this selection", 16, WbTheme.MUTED, FontWeight.Medium)
    }
    return
  }
  if (items.size <= 13) {
    WbColumnChart(
      labels = items.map { it.name },
      back = items.map { it.back },
      front = items.map { it.front },
      vmax = vmax,
      frontColor = { i -> items[i].frontColor },
      below = items.map { it.belowText to it.belowColor },
      labelOnBack = labelOnBack,
      marker = items.map { it.marker },
      tick = tick,
      fmt = fmt,
      modifier = modifier
    )
  } else {
    val sorted = items.sortedBy { it.sortKey }.take(26)
    val half = (sorted.size + 1) / 2
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      listOf(sorted.take(half), sorted.drop(half)).forEach { part ->
        WbRankBars(
          rows = part.map { WbRow(it.name, it.rankValue, it.rankText, it.rankColor) },
          vmax = rankMax,
          limit = rankLimit,
          nameWidth = 44.dp,
          fontSp = 13,
          modifier = Modifier.weight(1f).fillMaxHeight()
        )
      }
    }
  }
}
