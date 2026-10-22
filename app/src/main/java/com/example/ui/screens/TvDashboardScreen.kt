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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.NflRepository
import com.example.model.*
import com.example.ui.components.*
import kotlinx.coroutines.delay

enum class TvViewMode(val label: String, val shortLabel: String) {
  ALL_GRAPHS("All Main Charts (Fit Screen)", "Main Charts"),
  PRODUCTION("Production Focus", "Production"),
  QUALITY("Quality & OQL Focus", "Quality"),
  TABLE("Detailed Matrix Table", "Table")
}

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
  val isSyncing by repository.isSyncing.collectAsState()
  val lastRefreshTime by repository.lastRefreshTime.collectAsState()
  val refreshTrigger by repository.refreshTrigger.collectAsState()

  var refreshCounter by remember { mutableIntStateOf(30) }
  var isScreenActive by remember { mutableStateOf(true) }
  var viewMode by remember { mutableStateOf(TvViewMode.ALL_GRAPHS) }

  // 30-second Auto-Sliding Carousel state (cycles Main Charts -> Production -> Quality every 30s)
  var isAutoSlideEnabled by remember { mutableStateOf(true) }
  var slideCountdown by remember { mutableIntStateOf(30) }

  // Lifecycle awareness: Pause refresh & auto-slide ticker when TV screen is paused
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    isScreenActive = true
  }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
    isScreenActive = false
  }

  val tvData = remember(
    currentFloor,
    currentPeriod,
    isDemoMode,
    incidents,
    productionEntries,
    qualityEntries,
    targets,
    monthlyTargets,
    refreshTrigger
  ) {
    repository.getTvDashboardData(currentFloor, currentPeriod)
  }

  // Isolated TV 30-second automatic data refresh + 30-second view mode Auto-Sliding loop
  LaunchedEffect(isScreenActive, isAutoSlideEnabled, viewMode) {
    while (isScreenActive) {
      delay(1000)
      if (refreshCounter > 1) {
        refreshCounter -= 1
      } else {
        refreshCounter = 30
        if (!repository.isSyncing.value) {
          repository.refreshFromGoogleSheet()
        }
      }

      if (isAutoSlideEnabled && viewMode != TvViewMode.TABLE) {
        if (slideCountdown > 1) {
          slideCountdown -= 1
        } else {
          slideCountdown = 30
          viewMode = when (viewMode) {
            TvViewMode.ALL_GRAPHS -> TvViewMode.PRODUCTION
            TvViewMode.PRODUCTION -> TvViewMode.QUALITY
            TvViewMode.QUALITY -> TvViewMode.ALL_GRAPHS
            TvViewMode.TABLE -> TvViewMode.TABLE
          }
        }
      }
    }
  }

  val activeIncidents = tvData.activeIncidents

  val tvFloorOptions = listOf(
    FloorScope.SEW_1,
    FloorScope.SEW_2,
    FloorScope.SEW_3,
    FloorScope.ALL
  )

  val tvPeriodOptions = listOf(
    PeriodScope.TODAY,
    PeriodScope.YESTERDAY,
    PeriodScope.WEEK,
    PeriodScope.MONTH
  )

  val baseDensity = LocalDensity.current

  // Auto-Adjustable Fullscreen TV Container: dynamically calculates optimal density scale
  // from physical TV pixel dimensions (720p, 768p, 1080p, 4K, or Android TV Box)
  // so 100% of headers, KPI cards, and all floor lines (A1-H2B, J1-R1, S1-ZA2B) fit on 1 screen!
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF1F5F9))
  ) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    val isLandscapeScreen = widthPx >= heightPx
    val targetVirtualWidthDp = if (isLandscapeScreen) 1140f else 680f
    val targetVirtualHeightDp = if (isLandscapeScreen) 640f else 980f

    val computedDensity = if (widthPx > 0f && heightPx > 0f) {
      minOf(widthPx / targetVirtualWidthDp, heightPx / targetVirtualHeightDp).coerceIn(0.45f, 4.5f)
    } else {
      baseDensity.density
    }

    CompositionLocalProvider(
      LocalDensity provides Density(density = computedDensity, fontScale = 1.0f)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFFF1F5F9))
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
    // 1. Ultra-Minimal 2-Layer Executive TV Header
    // Layer 1 (Top): Title + View Mode Buttons (Main Charts / Production / Quality / Table) + 30s Auto-Slide Toggle + Refresh
    // Layer 2 (Bottom): Floor Buttons (Sew-1 / Sew-2 / Sew-3 / All Floor) + Inline Stoppage Alert + Period Filter
    Surface(
      color = Color(0xFF002D62),
      shape = RoundedCornerShape(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        // TOP LAYER: Brand & Title on Left | View Mode Buttons & Auto-Slide on Right (Above Floor Layer)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            var isBackFocused by remember { mutableStateOf(false) }
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier
                .size(22.dp)
                .testTag("tv_back_button")
                .onFocusChanged { isBackFocused = it.isFocused }
                .border(
                  width = if (isBackFocused) 1.5.dp else 0.dp,
                  color = if (isBackFocused) Color(0xFFF59E0B) else Color.Transparent,
                  shape = CircleShape
                )
                .focusable()
            ) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Navigation",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
              )
            }

            Surface(
              color = Color.White,
              shape = RoundedCornerShape(3.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Icon(
                  painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_nfl_logo),
                  contentDescription = "NFL Logo",
                  modifier = Modifier.size(12.dp),
                  tint = Color.Unspecified
                )
                Text(
                  text = "NFL LIVE",
                  color = Color(0xFF002D62),
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Black
                )
              }
            }

            Text(
              text = "NFL Live Production & Quality Wallboard",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(5.dp)
                  .background(Color(0xFF10B981), CircleShape)
              )
              Text(
                text = "Sync ${lastRefreshTime.takeLast(8)}",
                color = Color(0xFF90CAF9),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          // Top Right: View Mode Buttons (Main Charts, Production, Quality, Table) + Auto-Slide 30s + Refresh
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // View Mode Selector Pills (Placed on Top Layer above Floor Layer as requested)
            Surface(
              color = Color(0xFF001B3D),
              shape = RoundedCornerShape(4.dp)
            ) {
              Row(modifier = Modifier.padding(1.5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TvViewMode.values().forEach { mode ->
                  val isSelected = viewMode == mode
                  var isFocused by remember { mutableStateOf(false) }
                  Surface(
                    onClick = {
                      viewMode = mode
                      slideCountdown = 30
                    },
                    color = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                      .testTag("tv_mode_${mode.name.lowercase()}")
                      .onFocusChanged { isFocused = it.isFocused }
                      .border(
                        width = if (isFocused) 1.5.dp else 0.dp,
                        color = if (isFocused) Color(0xFFF59E0B) else Color.Transparent,
                        shape = RoundedCornerShape(3.dp)
                      )
                      .focusable()
                  ) {
                    Text(
                      text = mode.shortLabel,
                      color = if (isSelected) Color.White else Color(0xFFBAE6FD),
                      fontSize = 8.5.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                  }
                }
              }
            }

            // 30-Second Auto-Sliding Carousel Toggle Pill
            var isSlideFocused by remember { mutableStateOf(false) }
            Surface(
              onClick = {
                isAutoSlideEnabled = !isAutoSlideEnabled
                slideCountdown = 30
              },
              color = if (isAutoSlideEnabled) Color(0xFF0D9488) else Color(0xFF334155),
              shape = RoundedCornerShape(4.dp),
              modifier = Modifier
                .testTag("tv_auto_slide_toggle")
                .onFocusChanged { isSlideFocused = it.isFocused }
                .border(
                  width = if (isSlideFocused) 1.5.dp else 0.dp,
                  color = if (isSlideFocused) Color(0xFFF59E0B) else Color.Transparent,
                  shape = RoundedCornerShape(4.dp)
                )
                .focusable()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Icon(
                  imageVector = if (isAutoSlideEnabled) Icons.Default.Autorenew else Icons.Default.Pause,
                  contentDescription = "Auto Slide 30s",
                  tint = Color.White,
                  modifier = Modifier.size(10.dp)
                )
                Text(
                  text = if (isAutoSlideEnabled) "Auto Slide ${slideCountdown}s" else "Slide Paused",
                  color = Color.White,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Refresh button
            var isRefreshFocused by remember { mutableStateOf(false) }
            Surface(
              onClick = {
                refreshCounter = 30
                repository.refreshFromGoogleSheet()
              },
              color = if (isSyncing) Color(0xFF0284C7) else Color(0xFF059669),
              shape = RoundedCornerShape(4.dp),
              modifier = Modifier
                .testTag("tv_refresh_btn")
                .onFocusChanged { isRefreshFocused = it.isFocused }
                .border(
                  width = if (isRefreshFocused) 1.5.dp else 0.dp,
                  color = if (isRefreshFocused) Color(0xFFF59E0B) else Color.Transparent,
                  shape = RoundedCornerShape(4.dp)
                )
                .focusable()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = "Refresh",
                  tint = Color.White,
                  modifier = Modifier.size(10.dp)
                )
                Text(
                  text = if (isSyncing) "Sync" else "${refreshCounter}s",
                  color = Color.White,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        // BOTTOM LAYER: Floor Selector Buttons + Active Line Count + Inline Stoppage Alert | Period Filter
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Floor Scope Pills (Sew-1, Sew-2, Sew-3, All Floor)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "Floor:",
              fontSize = 8.5.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF90CAF9)
            )
            tvFloorOptions.forEach { floor ->
              val isSelected = currentFloor == floor
              var isFocused by remember { mutableStateOf(false) }
              Surface(
                onClick = { repository.setFloor(floor) },
                color = if (isSelected) Color(0xFF1E88E5) else Color(0xFF001B3D),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                  .testTag("tv_floor_${floor.name.lowercase()}")
                  .onFocusChanged { isFocused = it.isFocused }
                  .border(
                    width = if (isFocused) 1.5.dp else 0.dp,
                    color = if (isFocused) Color(0xFFF59E0B) else Color.Transparent,
                    shape = RoundedCornerShape(3.dp)
                  )
                  .focusable()
              ) {
                Text(
                  text = floor.displayName,
                  color = if (isSelected) Color.White else Color(0xFF90CAF9),
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                )
              }
            }

            Surface(
              color = Color(0xFF0F172A),
              shape = RoundedCornerShape(3.dp)
            ) {
              Text(
                text = "${tvData.linePerformance.size} Lines",
                color = Color(0xFF38BDF8),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
              )
            }

            // Inline Stoppage Alert Chip (Saves vertical screen height!)
            if (activeIncidents.isNotEmpty()) {
              Surface(
                color = Color(0xFF991B1B),
                shape = RoundedCornerShape(3.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(5.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFF87171))
                  )
                  val firstInc = activeIncidents.first()
                  val mins = ((System.currentTimeMillis() - firstInc.startTimeMillis) / 60000).coerceAtLeast(0)
                  Text(
                    text = "⚠️ ${activeIncidents.size} Stoppage: L-${firstInc.lineId} ${firstInc.category.displayName} (${mins}m)",
                    color = Color(0xFFFEE2E2),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }

          // Period filters (Today, Yesterday, Week, Month)
          Surface(
            color = Color(0xFF001B3D),
            shape = RoundedCornerShape(3.dp)
          ) {
            Row(modifier = Modifier.padding(1.5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              tvPeriodOptions.forEach { period ->
                val isSelected = currentPeriod == period
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                  onClick = { repository.setPeriod(period) },
                  color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                  shape = RoundedCornerShape(2.5.dp),
                  modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .border(
                      width = if (isFocused) 1.5.dp else 0.dp,
                      color = if (isFocused) Color(0xFFF59E0B) else Color.Transparent,
                      shape = RoundedCornerShape(2.5.dp)
                    )
                    .focusable()
                ) {
                  Text(
                    text = period.displayName,
                    color = if (isSelected) Color.White else Color(0xFF90CAF9),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    // 2. Ultra-Slim Top KPI Metric Ribbon (6 Compact TV Cards in a single slim strip)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      ExecutiveKpiCard(
        title = "Today Efficiency",
        valueText = "${tvData.kpis.todayEfficiency}%",
        targetText = "Target: 75.0%",
        trendText = if (tvData.kpis.todayEfficiency >= 75) "ON TGT" else "UNDER",
        isPositiveTrend = tvData.kpis.todayEfficiency >= 75,
        progressFraction = (tvData.kpis.todayEfficiency / 100.0).toFloat(),
        accentColor = Color(0xFF1E88E5),
        compactMode = true,
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Monthly Eff.",
        valueText = "${tvData.kpis.monthlyEfficiency}%",
        targetText = "Cumulative Avg",
        trendText = "MONTH",
        isPositiveTrend = true,
        progressFraction = (tvData.kpis.monthlyEfficiency / 100.0).toFloat(),
        accentColor = Color(0xFF0284C7),
        compactMode = true,
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Output vs Target",
        valueText = "${tvData.kpis.todayOutputPcs} pcs",
        targetText = "Tgt: ${tvData.kpis.todayTargetPcs} pcs",
        trendText = "${tvData.kpis.todayOutputPcs - tvData.kpis.todayTargetPcs}",
        isPositiveTrend = tvData.kpis.todayOutputPcs >= tvData.kpis.todayTargetPcs,
        progressFraction = if (tvData.kpis.todayTargetPcs > 0) (tvData.kpis.todayOutputPcs.toFloat() / tvData.kpis.todayTargetPcs.toFloat()) else 0f,
        accentColor = Color(0xFF10B981),
        compactMode = true,
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Today DHU %",
        valueText = "${String.format(java.util.Locale.US, "%.2f", tvData.kpis.todayOql * 0.4)}%",
        targetText = "Std: ≤ 2.00%",
        trendText = "DHU",
        isPositiveTrend = true,
        progressFraction = 0.5f,
        accentColor = Color(0xFF059669),
        compactMode = true,
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Up-to-Date OQL",
        valueText = "${tvData.kpis.todayOql}%",
        targetText = "BS Std: ≤ 5.00%",
        trendText = if (tvData.kpis.todayOql <= 5.0) "GOOD" else "ALERT",
        isPositiveTrend = tvData.kpis.todayOql <= 5.0,
        progressFraction = (tvData.kpis.todayOql / 10.0).toFloat(),
        accentColor = Color(0xFFF59E0B),
        compactMode = true,
        modifier = Modifier.weight(1f)
      )

      OperatorMachineMetricCard(
        operatorsCount = tvData.kpis.operatorsCount,
        machinesCount = tvData.kpis.machinesCount,
        helpersCount = tvData.kpis.helpersCount,
        finishingCount = tvData.kpis.finishingStaffCount,
        compactMode = true,
        modifier = Modifier.weight(1f)
      )
    }

    // 3. Dynamic Graphs Container — Scaled to 100% of remaining TV screen height
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      when (viewMode) {
        TvViewMode.ALL_GRAPHS -> {
          // Row 1: ONLY 2 WIDE LINE-WISE CHARTS (50% width each) so all 10-26 lines on the floor are 100% visible!
          // Row 2: Line-wise DHU + BS OQL Weekly Trend (W32-W43) + Defect Pareto
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1.08f),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TargetVsOutputBarChart(
                  title = "${currentFloor.displayName} — Today Line-wise Target vs Achievement",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  fitAllLinesToWidth = true,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LineEfficiencyBarChart(
                  title = "${currentFloor.displayName} — Line-wise Today vs Monthly Efficiency %",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  fitAllLinesToWidth = true,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .weight(0.92f),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                LineWiseDhuBarChart(
                  title = "${currentFloor.displayName} — Line-wise Today's DHU %",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Box(modifier = Modifier.weight(1.05f).fillMaxHeight()) {
                BsOqlWeeklyTrendChartCard(
                  compactMode = true,
                  modifier = Modifier.fillMaxSize().testTag("tv_bs_oql_trend_chart")
                )
              }

              Box(modifier = Modifier.weight(0.85f).fillMaxHeight()) {
                DefectParetoBarAndLineChart(
                  title = "${currentFloor.displayName} — Defect Pareto 80/20",
                  paretoData = tvData.paretoDefects,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }

        TvViewMode.PRODUCTION -> {
          // Production Slide: Row 1 has 2 full-width Line-wise Charts (50% each); Row 2 has Lowest Efficiency & NPT
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TargetVsOutputBarChart(
                  title = "${currentFloor.displayName} — Today Line-wise Target vs Achievement",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  fitAllLinesToWidth = true,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LineEfficiencyBarChart(
                  title = "${currentFloor.displayName} — Line-wise Today vs Monthly Efficiency %",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  fitAllLinesToWidth = true,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .weight(0.85f),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LowestEfficiencyRankingCard(
                  title = "Top Lowest-Efficiency Lines (Balancing Alert)",
                  lines = tvData.lowestEfficiencyLines,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NptPieChartCard(
                  title = "${currentFloor.displayName} — NPT Downtime Breakdown",
                  breakdown = tvData.nptBreakdown,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }

        TvViewMode.QUALITY -> {
          // Quality & OQL Slide: Row 1 has 2 full-width Line-wise Quality Charts; Row 2 has BS OQL Trend, Pareto, Top DHU
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1.05f),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LineWiseDhuBarChart(
                  title = "${currentFloor.displayName} — Line-wise Today's DHU %",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LineWiseOqlBarChart(
                  title = "${currentFloor.displayName} — Line-wise Today's OQL %",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .weight(0.95f),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(modifier = Modifier.weight(1.15f).fillMaxHeight()) {
                BsOqlWeeklyTrendChartCard(
                  compactMode = true,
                  modifier = Modifier.fillMaxSize().testTag("tv_bs_oql_trend_chart")
                )
              }

              Box(modifier = Modifier.weight(0.95f).fillMaxHeight()) {
                DefectParetoBarAndLineChart(
                  title = "${currentFloor.displayName} — Defect Pareto (80/20 Rule)",
                  paretoData = tvData.paretoDefects,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Box(modifier = Modifier.weight(0.9f).fillMaxHeight()) {
                Top5DhuLinesCard(
                  title = "Highest DHU Defect Lines",
                  lines = tvData.linePerformance,
                  compactMode = true,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }

        TvViewMode.TABLE -> {
          // Complete verified live line performance table (Scrollable for deep inspection)
          Box(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
          ) {
            LiveLinePerformanceTable(
              lines = tvData.linePerformance
            )
          }
        }
      }
    }
      }
    }
  }
}
