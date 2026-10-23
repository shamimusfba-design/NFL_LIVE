package com.example.ui.screens

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.PeriodScope
import com.example.model.TvDashboardData
import com.example.ui.components.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TvViewMode(val label: String, val shortLabel: String) {
  PRODUCTION("Production", "Production"),
  QUALITY("Quality", "Quality"),
  ALL_GRAPHS("All charts", "All")
}

private val TV_SLIDE_INTERVAL_OPTIONS = listOf(10, 15, 30, 60)

@Composable
fun TvDashboardScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val currentFloor by repository.currentFloor.collectAsState()
  val currentPeriod by repository.currentPeriod.collectAsState()
  val isDemoMode by repository.isDemoMode.collectAsState()
  val incidents by repository.incidents.collectAsState()
  val productionEntries by repository.productionEntries.collectAsState()
  val qualityEntries by repository.qualityEntries.collectAsState()
  val targets by repository.targets.collectAsState()
  val monthlyTargets by repository.monthlyTargets.collectAsState()
  val lastRefreshTime by repository.lastRefreshTime.collectAsState()
  val lastSyncTimeMillis by repository.lastSyncTimeMillis.collectAsState()
  val refreshTrigger by repository.refreshTrigger.collectAsState()

  var viewMode by remember { mutableStateOf(TvViewMode.PRODUCTION) }
  var isAutoSlideEnabled by remember { mutableStateOf(true) }
  var slideIntervalSec by remember { mutableIntStateOf(30) }
  var countdownRemainingSec by remember { mutableIntStateOf(30) }
  var isLifecycleResumed by remember { mutableStateOf(true) }
  var nowClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleResumed = false
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val tvData = remember(
    currentFloor, currentPeriod, isDemoMode, incidents, productionEntries,
    qualityEntries, targets, monthlyTargets, lastRefreshTime, lastSyncTimeMillis, refreshTrigger
  ) {
    repository.getTvDashboardData(currentFloor, currentPeriod)
  }

  LaunchedEffect(isAutoSlideEnabled, viewMode, slideIntervalSec, isLifecycleResumed) {
    countdownRemainingSec = slideIntervalSec
    while (isLifecycleResumed) {
      delay(1000L)
      nowClockMillis = System.currentTimeMillis()
      if (isAutoSlideEnabled) {
        if (countdownRemainingSec > 1) {
          countdownRemainingSec -= 1
        } else {
          viewMode = when (viewMode) {
            TvViewMode.PRODUCTION -> TvViewMode.QUALITY
            TvViewMode.QUALITY -> TvViewMode.ALL_GRAPHS
            TvViewMode.ALL_GRAPHS -> TvViewMode.PRODUCTION
          }
          countdownRemainingSec = slideIntervalSec
        }
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(TvBgDark)) {
    val scale = rememberTvWallboardScale(maxWidth = maxWidth, maxHeight = maxHeight)

    Column(
      modifier = Modifier.fillMaxSize().padding(scale.dp(10f)),
      verticalArrangement = Arrangement.spacedBy(scale.dp(8f))
    ) {
      TvHeaderBar(
        viewMode = viewMode,
        onSelectViewMode = { picked ->
          viewMode = picked
          countdownRemainingSec = slideIntervalSec
        },
        currentFloor = currentFloor,
        onSelectFloor = { floor -> repository.setFloor(floor) },
        currentPeriod = currentPeriod,
        onSelectPeriod = { period -> repository.setPeriod(period) },
        onNavigateBack = onNavigateBack,
        scale = scale
      )

      TvStatusStrip(
        tvData = tvData,
        viewMode = viewMode,
        isAutoSlideEnabled = isAutoSlideEnabled,
        onToggleAutoSlide = {
          isAutoSlideEnabled = !isAutoSlideEnabled
          countdownRemainingSec = slideIntervalSec
        },
        slideIntervalSec = slideIntervalSec,
        onCycleInterval = {
          val nextIdx = (TV_SLIDE_INTERVAL_OPTIONS.indexOf(slideIntervalSec) + 1) % TV_SLIDE_INTERVAL_OPTIONS.size
          slideIntervalSec = TV_SLIDE_INTERVAL_OPTIONS[nextIdx]
          countdownRemainingSec = slideIntervalSec
        },
        countdownRemainingSec = countdownRemainingSec,
        nowClockMillis = nowClockMillis,
        scale = scale
      )

      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        when (viewMode) {
          TvViewMode.PRODUCTION -> TvProductionPage(tvData = tvData, scale = scale)
          TvViewMode.QUALITY -> TvQualityPage(tvData = tvData, scale = scale)
          TvViewMode.ALL_GRAPHS -> TvAllGraphsPage(tvData = tvData, scale = scale)
        }
      }
    }
  }
}

@Composable
private fun TvHeaderBar(
  viewMode: TvViewMode,
  onSelectViewMode: (TvViewMode) -> Unit,
  currentFloor: FloorScope,
  onSelectFloor: (FloorScope) -> Unit,
  currentPeriod: PeriodScope,
  onSelectPeriod: (PeriodScope) -> Unit,
  onNavigateBack: () -> Unit,
  scale: TvWallboardScale
) {
  var brandFocused by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth().height(scale.dp(44f)),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(scale.dp(12f)),
      modifier = Modifier
        .onFocusChanged { brandFocused = it.isFocused }
        .onPreviewKeyEvent { ev ->
          if (ev.type == KeyEventType.KeyDown &&
            (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)
          ) {
            onNavigateBack()
            true
          } else false
        }
        .focusable()
        .clickable { onNavigateBack() }
        .then(if (brandFocused) Modifier.border(2.dp, TvAmber, RoundedCornerShape(scale.dp(6f))) else Modifier)
        .padding(horizontal = scale.dp(4f), vertical = scale.dp(2f))
    ) {
      Text(text = "NFL LIVE", style = tvTabularStyle(scale.sp(24f), FontWeight.Black, TvTextPrimary))
      Box(modifier = Modifier.width(1.dp).height(scale.dp(24f)).background(TvPanelBorder))
      Column {
        Text(text = "Production & Quality", style = tvTabularStyle(scale.sp(12.5f), FontWeight.SemiBold, TvTextMuted))
        Text(text = "Wallboard", style = tvTabularStyle(scale.sp(12.5f), FontWeight.SemiBold, TvTextMuted))
      }
    }

    TvSelectorGroup(scale = scale) {
      TvViewMode.entries.forEach { mode ->
        TvFocusableButton(
          label = mode.shortLabel, selected = viewMode == mode, scale = scale,
          horizontalPadding = 16f, verticalPadding = 7f, fontSizeUnits = 15f,
          onClick = { onSelectViewMode(mode) }
        )
      }
    }

    val floorOptions = remember {
      listOf(
        FloorScope.SEW_1 to "Sew 1",
        FloorScope.SEW_2 to "Sew 2",
        FloorScope.SEW_3 to "Sew 3",
        FloorScope.ALL to "All Floors"
      )
    }
    TvSelectorGroup(scale = scale) {
      floorOptions.forEach { (floor, label) ->
        TvFocusableButton(
          label = label, selected = currentFloor == floor, scale = scale,
          horizontalPadding = 14f, verticalPadding = 7f, fontSizeUnits = 15f,
          onClick = { onSelectFloor(floor) }
        )
      }
    }

    val periodOptions = remember {
      listOf(
        PeriodScope.TODAY to "Today",
        PeriodScope.YESTERDAY to "Yesterday",
        PeriodScope.WEEK to "Week",
        PeriodScope.MONTH to "Month"
      )
    }
    TvSelectorGroup(scale = scale) {
      periodOptions.forEach { (period, label) ->
        TvFocusableButton(
          label = label, selected = currentPeriod == period, scale = scale,
          horizontalPadding = 10f, verticalPadding = 6f, fontSizeUnits = 13f,
          onClick = { onSelectPeriod(period) }
        )
      }
    }
  }
}

@Composable
private fun TvStatusStrip(
  tvData: TvDashboardData,
  viewMode: TvViewMode,
  isAutoSlideEnabled: Boolean,
  onToggleAutoSlide: () -> Unit,
  slideIntervalSec: Int,
  onCycleInterval: () -> Unit,
  countdownRemainingSec: Int,
  nowClockMillis: Long,
  scale: TvWallboardScale
) {
  val floorTitle = when (tvData.floorScope) {
    FloorScope.SEW_1 -> "Sewing 1"
    FloorScope.SEW_2 -> "Sewing 2"
    FloorScope.SEW_3 -> "Sewing 3"
    FloorScope.ALL -> "All Floors"
    FloorScope.FINISHING -> "Finishing"
  }
  val runningLines = tvData.linePerformance
  val kpis = tvData.kpis

  val elapsedSyncSec = ((nowClockMillis - tvData.lastSyncTimeMillis) / 1000L).coerceAtLeast(0L)
  val isSyncStale = elapsedSyncSec > 300L
  val elapsedText = when {
    elapsedSyncSec < 60L -> "${elapsedSyncSec}s ago"
    elapsedSyncSec < 3600L -> "${elapsedSyncSec / 60L}m ago"
    else -> "${elapsedSyncSec / 3600L}h ago"
  }
  val syncLabel = if (isSyncStale) {
    "STALE (>5m) · Synced ${tvData.lastRefreshTime} · $elapsedText"
  } else {
    "Synced ${tvData.lastRefreshTime} · $elapsedText"
  }

  Row(
    modifier = Modifier.fillMaxWidth().height(scale.dp(26f)),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(scale.dp(6f)),
      modifier = Modifier.weight(1f, fill = false)
    ) {
      Text(
        text = "$floorTitle · ${viewMode.label} · ${tvData.periodScope.displayName}",
        style = tvTabularStyle(scale.sp(16f), FontWeight.Bold, TvTextPrimary),
        maxLines = 1,
        modifier = Modifier.padding(end = scale.dp(6f))
      )

      if (viewMode == TvViewMode.QUALITY) {
        val goodDhu = runningLines.count { it.dhu != null && (it.dhu ?: 0.0) <= 2.0 }
        val warnDhu = runningLines.count { it.dhu != null && (it.dhu ?: 0.0) > 2.0 && (it.dhu ?: 0.0) <= 3.0 }
        val badDhu = runningLines.count { it.dhu != null && (it.dhu ?: 0.0) > 3.0 }
        val noQa = runningLines.count { it.dhu == null }

        TvStatusChip("$goodDhu lines ≤ 2.0 DHU", TvGreen, TvGreenDarkText, scale)
        TvStatusChip("$warnDhu lines 2–3", TvAmber, TvAmberDarkText, scale)
        TvStatusChip("$badDhu lines above 3", TvRed, TvRedDarkText, scale)
        if (noQa > 0) TvStatusChip("$noQa not reported", TvGreyChipBg, TvGreyChipText, scale)
        if (kpis.unscheduledLinesCount > 0) TvStatusChip("${kpis.unscheduledLinesCount} not running", TvSlateBar, TvTextPrimary, scale)
      } else {
        if (!kpis.targetAvailableForPeriod) {
          val noOut = runningLines.count { it.achievementPcs == null || (it.achievementPcs ?: 0) == 0 }
          TvStatusChip("target not available for this period", TvAmber, TvAmberDarkText, scale)
          TvStatusChip("$noOut no output", TvGreyChipBg, TvGreyChipText, scale)
          if (kpis.unscheduledLinesCount > 0) TvStatusChip("${kpis.unscheduledLinesCount} not running", TvSlateBar, TvTextPrimary, scale)
        } else {
          val onTarget = runningLines.count { (it.achievementPct ?: 0.0) >= 90.0 }
          val slightlyBehind = runningLines.count {
            val p = it.achievementPct ?: -1.0
            p >= 60.0 && p < 90.0
          }
          val farBehind = runningLines.count {
            val p = it.achievementPct
            val pcs = it.achievementPcs
            p != null && pcs != null && pcs > 0 && p < 60.0
          }
          val noOutput = runningLines.count { it.achievementPcs == null || (it.achievementPcs ?: 0) == 0 }

          TvStatusChip("$onTarget on target", TvGreen, TvGreenDarkText, scale)
          TvStatusChip("$slightlyBehind slightly behind", TvAmber, TvAmberDarkText, scale)
          TvStatusChip("$farBehind far behind", TvRed, TvRedDarkText, scale)
          TvStatusChip("$noOutput no output", TvGreyChipBg, TvGreyChipText, scale)
          if (kpis.unscheduledLinesCount > 0) TvStatusChip("${kpis.unscheduledLinesCount} not running", TvSlateBar, TvTextPrimary, scale)
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scale.dp(6f))) {
      TvFocusableButton(
        label = if (isAutoSlideEnabled) "Auto-slide · ${countdownRemainingSec} s" else "Auto-slide · PAUSED",
        selected = false, scale = scale, horizontalPadding = 8f, verticalPadding = 3f, fontSizeUnits = 12.5f,
        onClick = onToggleAutoSlide
      )
      TvFocusableButton(
        label = "${slideIntervalSec}s", selected = false, scale = scale,
        horizontalPadding = 6f, verticalPadding = 3f, fontSizeUnits = 12f,
        onClick = onCycleInterval
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scale.dp(4f)),
        modifier = Modifier.padding(end = scale.dp(4f))
      ) {
        TvViewMode.entries.forEach { mode ->
          Box(
            modifier = Modifier
              .width(scale.dp(24f))
              .height(scale.dp(6f))
              .background(if (viewMode == mode) TvBlue else TvSlateBar, RoundedCornerShape(scale.dp(3f)))
          )
        }
      }
      if (tvData.isDemoMode) {
        TvStatusChip("SAMPLE DATA", TvSlateBar, TvTextPrimary, scale)
      }
      TvStatusChip(
        text = syncLabel,
        backgroundColor = TvPanelBg,
        textColor = if (isSyncStale) TvRed else TvGreen,
        borderColor = if (isSyncStale) TvRed else TvPanelBorder,
        scale = scale
      )
    }
  }
}

@Composable
private fun TvProductionPage(tvData: TvDashboardData, scale: TvWallboardScale) {
  val kpis = tvData.kpis
  val targetAvailable = kpis.targetAvailableForPeriod

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
    Row(
      modifier = Modifier.fillMaxWidth().height(scale.dp(100f)),
      horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))
    ) {
      val effColor = when {
        !targetAvailable -> TvAmber
        kpis.todayEfficiency >= kpis.todayTargetEfficiency -> TvGreen
        kpis.todayEfficiency >= 60.0 -> TvAmber
        else -> TvRed
      }
      val effGapPts = (kpis.todayTargetEfficiency - kpis.todayEfficiency).roundToInt()
      val effSubtitle = if (!targetAvailable) {
        "target not available for this period"
      } else if (effGapPts > 0) {
        "Month ${String.format(Locale.US, "%.1f", kpis.monthlyEfficiency)}% · $effGapPts pts below target"
      } else {
        "Month ${String.format(Locale.US, "%.1f", kpis.monthlyEfficiency)}% · ${abs(effGapPts)} pts above target"
      }
      TvKpiTile(
        title = "EFFICIENCY · target ${kpis.todayTargetEfficiency.roundToInt()}%",
        primaryValue = if (targetAvailable) "${String.format(Locale.US, "%.1f", kpis.todayEfficiency)}%" else "--",
        subtitle = effSubtitle, valueColor = effColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val achPct = if (targetAvailable && kpis.todayTargetPcs > 0) {
        ((kpis.todayOutputPcs.toDouble() / kpis.todayTargetPcs.toDouble()) * 100.0).roundToInt()
      } else 0
      val outGap = (kpis.todayTargetPcs - kpis.todayOutputPcs).coerceAtLeast(0)
      val outColor = when {
        !targetAvailable -> TvTextPrimary
        achPct >= 90 -> TvGreen
        achPct >= 60 -> TvAmber
        else -> TvRed
      }
      TvKpiTile(
        title = "OUTPUT vs TARGET",
        primaryValue = formatTvInt(kpis.todayOutputPcs),
        secondaryValue = if (targetAvailable) "/ ${formatTvInt(kpis.todayTargetPcs)}" else "pcs",
        subtitle = if (targetAvailable) "$achPct% of day target · gap ${formatTvInt(outGap)} pcs" else "target not available for this period",
        valueColor = outColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val repColor = when {
        kpis.scheduledLinesCount == 0 -> TvTextMuted
        kpis.reportedLinesCount >= kpis.scheduledLinesCount -> TvGreen
        kpis.reportedLinesCount > 0 -> TvAmber
        else -> TvRed
      }
      val missingText = when {
        kpis.missingReportLineIds.isEmpty() && kpis.unscheduledLinesCount > 0 ->
          "all reported · ${kpis.unscheduledLinesCount} not running"
        kpis.missingReportLineIds.isEmpty() -> "all running lines reported"
        kpis.missingReportLineIds.size == 1 -> "${kpis.missingReportLineIds.first()} has no output entry"
        kpis.missingReportLineIds.size <= 4 -> "${kpis.missingReportLineIds.joinToString(", ")} not reported"
        else -> "${kpis.missingReportLineIds.take(3).joinToString(", ")} +${kpis.missingReportLineIds.size - 3} not reported"
      }
      TvKpiTile(
        title = "LINES REPORTED",
        primaryValue = "${kpis.reportedLinesCount} of ${kpis.scheduledLinesCount}",
        subtitle = missingText, valueColor = repColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val dtColor = when {
        kpis.downtimeMinutes == 0 && kpis.openIncidentsCount == 0 -> TvGreen
        kpis.downtimeMinutes >= 45 || kpis.openIncidentsCount >= 2 -> TvRed
        else -> TvAmber
      }
      val dtTitle = if (tvData.periodScope == PeriodScope.TODAY) "DOWNTIME TODAY" else "DOWNTIME · ${tvData.periodScope.displayName.uppercase(Locale.US)}"
      TvKpiTile(
        title = dtTitle, primaryValue = kpis.downtimeMinutes.toString(), secondaryValue = "min",
        subtitle = "≈ ${formatTvInt(kpis.estimatedLostPcs)} pcs lost · ${kpis.openIncidentsCount} open",
        valueColor = dtColor, scale = scale, modifier = Modifier.weight(1f)
      )

      TvKpiTile(
        title = "OPERATORS / MACHINES · planned",
        primaryValue = "${kpis.operatorsCount} / ${kpis.machinesCount}",
        subtitle = "planned counts, not attendance",
        valueColor = TvTextPrimary, scale = scale, modifier = Modifier.weight(1f)
      )
    }

    Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
      Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
        TvTargetVsAchievementChart(tvData.linePerformance, targetAvailable, scale, Modifier.weight(5f).fillMaxHeight())
        TvEfficiencyByLineChart(tvData.linePerformance, targetAvailable, scale, Modifier.weight(4f).fillMaxHeight())
        TvLowestEfficiencyLinesCard(tvData.lowestEfficiencyLines, targetAvailable, scale, Modifier.weight(3f).fillMaxHeight())
      }
      Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
        TvHourlyOutputChart(tvData.hourlySlots, targetAvailable, scale, Modifier.weight(5f).fillMaxHeight())
        TvDowntimeByCauseCard(tvData.nptBreakdown, scale, Modifier.weight(4f).fillMaxHeight())
        TvOpenStoppagesCard(tvData.openStoppages, scale, Modifier.weight(3f).fillMaxHeight())
      }
    }
  }
}

@Composable
private fun TvQualityPage(tvData: TvDashboardData, scale: TvWallboardScale) {
  val kpis = tvData.kpis
  val hasQa = kpis.totalCheckedGarments > 0

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
    Row(
      modifier = Modifier.fillMaxWidth().height(scale.dp(100f)),
      horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))
    ) {
      val dhuColor = when {
        !hasQa -> TvTextMuted
        kpis.todayDhu <= 2.0 -> TvGreen
        kpis.todayDhu <= 3.0 -> TvAmber
        else -> TvRed
      }
      val worstDhuLine = tvData.highestDhuLines.firstOrNull()
      val aboveLimitCount = tvData.linePerformance.count { (it.dhu ?: 0.0) > 2.0 }
      val dhuSubtitle = if (!hasQa) {
        "no inspection records in period"
      } else if (worstDhuLine != null) {
        "worst ${worstDhuLine.lineId} ${String.format(Locale.US, "%.1f", worstDhuLine.dhu)} · $aboveLimitCount lines above 2.0"
      } else {
        "all inspected lines within 2.0 limit"
      }
      TvKpiTile(
        title = "DHU · limit 2.0",
        primaryValue = if (hasQa) String.format(Locale.US, "%.2f", kpis.todayDhu) else "--",
        subtitle = dhuSubtitle, valueColor = dhuColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val oqlColor = when {
        !hasQa -> TvTextMuted
        kpis.todayOql <= 3.5 -> TvGreen
        kpis.todayOql <= 5.0 -> TvAmber
        else -> TvRed
      }
      val oqlStatusLabel = when {
        !hasQa -> "no QA records"
        kpis.todayOql <= 5.0 -> "within 5.0% limit"
        else -> "above 5.0% limit"
      }
      TvKpiTile(
        title = "OQL · limit 5.0%",
        primaryValue = if (hasQa) "${String.format(Locale.US, "%.2f", kpis.todayOql)}%" else "--",
        subtitle = "monthly ${String.format(Locale.US, "%.1f", kpis.monthlyOql)}% · $oqlStatusLabel",
        valueColor = oqlColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val ftrPct = (100.0 - kpis.todayOql).coerceIn(0.0, 100.0)
      val ftrColor = when {
        !hasQa -> TvTextMuted
        ftrPct >= 96.5 -> TvGreen
        ftrPct >= 95.0 -> TvAmber
        else -> TvRed
      }
      TvKpiTile(
        title = "FIRST-TIME RIGHT · derived",
        primaryValue = if (hasQa) "${String.format(Locale.US, "%.1f", ftrPct)}%" else "--",
        subtitle = "derived: 100 − OQL",
        valueColor = ftrColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val pendingTotal = (kpis.totalDefectBalance + kpis.totalSpotBalance).coerceAtLeast(0)
      val pendingColor = when {
        pendingTotal == 0 -> TvGreen
        pendingTotal <= 30 -> TvAmber
        else -> TvRed
      }
      TvKpiTile(
        title = "DEFECTS PENDING",
        primaryValue = formatTvInt(pendingTotal), secondaryValue = "pcs",
        subtitle = "sewing ${kpis.totalDefectBalance} · spot ${kpis.totalSpotBalance} not returned",
        valueColor = pendingColor, scale = scale, modifier = Modifier.weight(1f)
      )

      val checkedTitle = if (tvData.periodScope == PeriodScope.TODAY) "CHECKED TODAY" else "CHECKED · ${tvData.periodScope.displayName.uppercase(Locale.US)}"
      TvKpiTile(
        title = checkedTitle, primaryValue = formatTvInt(kpis.totalCheckedGarments), secondaryValue = "pcs",
        subtitle = "${kpis.inspectedLinesCount} of ${kpis.scheduledLinesCount} lines inspected",
        valueColor = TvTextPrimary, scale = scale, modifier = Modifier.weight(1f)
      )
    }

    Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
      Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
        TvDhuByLineChart(tvData.linePerformance, scale, Modifier.weight(5f).fillMaxHeight())
        TvOqlByLineChart(tvData.linePerformance, scale, Modifier.weight(4f).fillMaxHeight())
        TvDefectParetoCard(tvData.paretoDefects, scale, Modifier.weight(3f).fillMaxHeight())
      }
      Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
        TvHourlyDhuOqlChart(tvData.hourlySlots, scale, Modifier.weight(5f).fillMaxHeight())
        TvDefectRectificationPendingCard(tvData.linePerformance, scale, Modifier.weight(4f).fillMaxHeight())
        TvMainDefectWorstLinesCard(tvData.linePerformance, tvData.topDefects, scale, Modifier.weight(3f).fillMaxHeight())
      }
    }
  }
}

@Composable
private fun TvAllGraphsPage(tvData: TvDashboardData, scale: TvWallboardScale) {
  val kpis = tvData.kpis
  val targetAvailable = kpis.targetAvailableForPeriod
  val hasQa = kpis.totalCheckedGarments > 0

  Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
    Row(
      modifier = Modifier.fillMaxWidth().height(scale.dp(160f)),
      horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))
    ) {
      TvSemiGaugeCard(
        title = "Today Live Efficiency",
        subtitle = "target ${kpis.todayTargetEfficiency.roundToInt()}%",
        value = kpis.todayEfficiency, maxValue = 100.0, targetOrLimit = kpis.todayTargetEfficiency,
        valueSuffix = "%", isLowerBetter = false,
        unavailableMessage = if (!targetAvailable) "target not available for this period" else null,
        scale = scale, modifier = Modifier.weight(1.1f).fillMaxHeight()
      )

      val mDiff = Math.round((kpis.monthlyEfficiency - kpis.todayTargetEfficiency) * 10.0) / 10.0
      val mColor = when {
        kpis.monthlyEfficiency >= kpis.todayTargetEfficiency -> TvGreen
        kpis.monthlyEfficiency >= 65.0 -> TvAmber
        else -> TvRed
      }
      TvBigMetricCard(
        title = "Monthly Efficiency", subtitle = "target ${kpis.todayTargetEfficiency.roundToInt()}%",
        valueText = "${String.format(Locale.US, "%.1f", kpis.monthlyEfficiency)}%",
        footerText = if (mDiff >= 0.0) "${abs(mDiff)} pts above target" else "${abs(mDiff)} pts below target",
        valueColor = mColor, scale = scale, modifier = Modifier.weight(0.9f).fillMaxHeight()
      )

      TvOperatorVsMachineDonutCard(kpis.operatorsCount, kpis.machinesCount, scale, Modifier.weight(1.0f).fillMaxHeight())

      TvFloorFactoryEfficiencyCard(tvData.floorEfficiencyComparison, targetAvailable, scale, Modifier.weight(1.4f).fillMaxHeight())

      val storedBsOql = if (kpis.bsQaOql > 0.0) kpis.bsQaOql else null
      val bsColor = when {
        storedBsOql == null -> TvTextMuted
        storedBsOql <= 2.5 -> TvGreen
        storedBsOql <= 5.5 -> TvAmber
        else -> TvRed
      }
      TvBigMetricCard(
        title = "BS QA OQL", subtitle = "buyer QA (IE)",
        valueText = if (storedBsOql != null) "${String.format(Locale.US, "%.2f", storedBsOql)}%" else "--",
        footerText = if (storedBsOql != null) "stored IE monthly target" else "not set by IE (--)",
        valueColor = bsColor, scale = scale, modifier = Modifier.weight(0.9f).fillMaxHeight()
      )

      TvSemiGaugeCard(
        title = "Today Live OQL", subtitle = "limit 5.0%",
        value = if (hasQa) kpis.todayOql else null, maxValue = 10.0, targetOrLimit = 5.0,
        valueSuffix = "%", isLowerBetter = true,
        unavailableMessage = if (!hasQa) "no QA report in period" else null,
        scale = scale, modifier = Modifier.weight(1.0f).fillMaxHeight()
      )

      val friColor = when {
        kpis.friPassRate >= 95.0 -> TvGreen
        kpis.friPassRate >= 90.0 -> TvAmber
        else -> TvRed
      }
      TvBigMetricCard(
        title = "BS QA FRI Pass", subtitle = "first-time right",
        valueText = "${String.format(Locale.US, "%.1f", kpis.friPassRate)}%",
        footerText = if (kpis.friPassRate >= 95.0) "above 95% benchmark" else "below 95% benchmark",
        valueColor = friColor, scale = scale, modifier = Modifier.weight(0.9f).fillMaxHeight()
      )
    }

    Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
      Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
        TvTargetVsAchievementChart(tvData.linePerformance, targetAvailable, scale, Modifier.weight(5f).fillMaxHeight())
        TvOqlByLineChart(
          lines = tvData.linePerformance, scale = scale,
          title = "Today Live OQL by Line", subtitle = "tick = monthly · dashed = 5.0% limit",
          modifier = Modifier.weight(5f).fillMaxHeight()
        )
        TvTop5DefectsDhuCard(tvData.topDefects, scale, Modifier.weight(3f).fillMaxHeight())
      }
      Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(scale.dp(8f))) {
        TvEfficiencyByLineChart(
          lines = tvData.linePerformance, targetAvailableForPeriod = targetAvailable, scale = scale,
          title = "Monthly vs Today Efficiency %", subtitle = "bar = month · narrow = today · dashed = 75%",
          modifier = Modifier.weight(5f).fillMaxHeight()
        )
        TvWeeklyBsOqlChartCard(scale = scale, modifier = Modifier.weight(5f).fillMaxHeight())
        TvPoor5DhuLinesCard(tvData.highestDhuLines, scale, Modifier.weight(3f).fillMaxHeight())
      }
    }
  }
}
