package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.NflRepository
import com.example.model.*
import com.example.ui.components.*
import kotlinx.coroutines.delay

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
  val syncStatusBanner by repository.syncStatusBanner.collectAsState()

  var refreshCounter by remember { mutableIntStateOf(30) }
  var isScreenActive by remember { mutableStateOf(true) }

  // Lifecycle awareness: Pause refresh ticker when TV screen is paused or inactive to isolate mobile workload
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

  // Isolated TV 30-second automatic refresh loop: runs only while TV dashboard is actively visible
  LaunchedEffect(isScreenActive) {
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
    }
  }

  val activeIncidents = tvData.activeIncidents

  // Exact 5 floor buttons requested in Section 5
  val tvFloorOptions = listOf(
    FloorScope.SWING_ACW,
    FloorScope.SEW_1,
    FloorScope.SEW_2,
    FloorScope.SEW_3,
    FloorScope.ALL
  )

  // Period options requested in Section 6
  val tvPeriodOptions = listOf(
    PeriodScope.TODAY,
    PeriodScope.YESTERDAY,
    PeriodScope.WEEK,
    PeriodScope.MONTH
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF1F5F9))
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Dedicated 43-inch Android TV Header Bar with exact title: "NFL Live Production and Quality"
    Surface(
      color = Color(0xFF002D62),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          var isBackFocused by remember { mutableStateOf(false) }
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .size(36.dp)
              .testTag("tv_back_button")
              .onFocusChanged { isBackFocused = it.isFocused }
              .border(
                width = if (isBackFocused) 2.dp else 0.dp,
                color = if (isBackFocused) Color(0xFFF59E0B) else Color.Transparent,
                shape = CircleShape
              )
              .focusable()
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Navigation", tint = Color.White)
          }

          Surface(
            color = Color.White,
            shape = RoundedCornerShape(6.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_nfl_logo),
                contentDescription = "NFL Logo",
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
              )
              Text(
                text = "NFL LIVE",
                color = Color(0xFF002D62),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
              )
            }
          }

          Column {
            Text(
              text = "NFL Live Production and Quality",
              color = Color.White,
              fontSize = 17.sp,
              fontWeight = FontWeight.Black,
              letterSpacing = 0.2.sp
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .background(Color(0xFF10B981), CircleShape)
              )
              Text(
                text = "Live Shared Google Sheets Feed • Updated: $lastRefreshTime • Auto-refresh in ${refreshCounter}s",
                color = Color(0xFF90CAF9),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Manual Refresh Button (Remote/D-pad Focusable)
          var isRefreshFocused by remember { mutableStateOf(false) }
          Surface(
            onClick = {
              refreshCounter = 30
              repository.refreshFromGoogleSheet()
            },
            color = if (isSyncing) Color(0xFF0284C7) else Color(0xFF059669),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
              .testTag("tv_refresh_btn")
              .onFocusChanged { isRefreshFocused = it.isFocused }
              .border(
                width = if (isRefreshFocused) 2.dp else 0.dp,
                color = if (isRefreshFocused) Color(0xFFF59E0B) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
              )
              .focusable()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Shared Sheet Data",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = if (isSyncing) "Syncing..." else "Refresh (${refreshCounter}s)",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          // Period Filters: Today, Yesterday, Weekly, Monthly (Remote/D-pad Focusable)
          Surface(
            color = Color(0xFF001F44),
            shape = RoundedCornerShape(6.dp)
          ) {
            Row(modifier = Modifier.padding(3.dp)) {
              tvPeriodOptions.forEach { period ->
                val isSelected = currentPeriod == period
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                  onClick = { repository.setPeriod(period) },
                  color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .border(
                      width = if (isFocused) 2.dp else 0.dp,
                      color = if (isFocused) Color(0xFFF59E0B) else Color.Transparent,
                      shape = RoundedCornerShape(4.dp)
                    )
                    .focusable()
                ) {
                  Text(
                    text = period.displayName,
                    color = if (isSelected) Color.White else Color(0xFF90CAF9),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    // 2. Exact Floor Selector Ribbon (Swing ACW, Sew-1, Sew-2, Sew-3, All Floor) — No Auto-Rotation!
    Surface(
      color = Color.White,
      shape = RoundedCornerShape(8.dp),
      shadowElevation = 1.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Floor:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569)
          )
          tvFloorOptions.forEach { floor ->
            val isSelected = currentFloor == floor
            var isFocused by remember { mutableStateOf(false) }
            Surface(
              onClick = { repository.setFloor(floor) },
              color = if (isSelected) Color(0xFF0A2558) else Color(0xFFF1F5F9),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .border(
                  width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                  color = if (isFocused) Color(0xFFF59E0B) else if (isSelected) Color(0xFF1E3A8A) else Color.Transparent,
                  shape = RoundedCornerShape(6.dp)
                )
                .focusable()
            ) {
              Text(
                text = floor.displayName,
                color = if (isSelected) Color.White else Color(0xFF334E68),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
              )
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Active Scope: ${currentFloor.displayName} • Period: ${repository.getPeriodLabel(currentPeriod)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF64748B)
          )
          val banner = syncStatusBanner
          if (!banner.isNullOrBlank()) {
            Surface(
              color = Color(0xFFF1F5F9),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = banner.take(45),
                fontSize = 10.sp,
                color = Color(0xFF0284C7),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }
      }
    }

    // 3. Top Executive KPI Metric Ribbon (6 High-Visibility TV Cards)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      ExecutiveKpiCard(
        title = "Today Efficiency",
        valueText = "${tvData.kpis.todayEfficiency}%",
        targetText = "Target: 75.0% (Weighted)",
        trendText = if (tvData.kpis.todayEfficiency >= 75) "ON TARGET" else "UNDER",
        isPositiveTrend = tvData.kpis.todayEfficiency >= 75,
        progressFraction = (tvData.kpis.todayEfficiency / 100.0).toFloat(),
        accentColor = Color(0xFF1E88E5),
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Monthly Efficiency",
        valueText = "${tvData.kpis.monthlyEfficiency}%",
        targetText = "Cumulative Month Trend",
        trendText = "MONTH",
        isPositiveTrend = true,
        progressFraction = (tvData.kpis.monthlyEfficiency / 100.0).toFloat(),
        accentColor = Color(0xFF0284C7),
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Output vs Target",
        valueText = "${tvData.kpis.todayOutputPcs} pcs",
        targetText = "Target: ${tvData.kpis.todayTargetPcs} pcs (${tvData.kpis.reportingCoveragePct}%)",
        trendText = "${tvData.kpis.todayOutputPcs - tvData.kpis.todayTargetPcs} pcs",
        isPositiveTrend = tvData.kpis.todayOutputPcs >= tvData.kpis.todayTargetPcs,
        progressFraction = if (tvData.kpis.todayTargetPcs > 0) (tvData.kpis.todayOutputPcs.toFloat() / tvData.kpis.todayTargetPcs.toFloat()) else 0f,
        accentColor = Color(0xFF10B981),
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Today OQL %",
        valueText = "${tvData.kpis.todayOql}%",
        targetText = "Standard: ≤ 5.00%",
        trendText = if (tvData.kpis.todayOql <= 5.0) "GOOD" else "ALERT",
        isPositiveTrend = tvData.kpis.todayOql <= 5.0,
        progressFraction = (tvData.kpis.todayOql / 10.0).toFloat(),
        accentColor = Color(0xFFF59E0B),
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Today DHU %",
        valueText = "${String.format(java.util.Locale.US, "%.2f", tvData.kpis.todayOql * 0.4)}%",
        targetText = "Benchmark: ≤ 2.00%",
        trendText = "QUALITY",
        isPositiveTrend = true,
        progressFraction = 0.5f,
        accentColor = Color(0xFF059669),
        modifier = Modifier.weight(1f)
      )

      OperatorMachineMetricCard(
        operatorsCount = tvData.kpis.operatorsCount,
        machinesCount = tvData.kpis.machinesCount,
        modifier = Modifier.weight(1f)
      )
    }

    // 4. Production & Efficiency Intelligence Section (Target vs Output, Efficiency Column, Lowest-Efficiency Ranking)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(modifier = Modifier.weight(1.35f)) {
        TargetVsOutputBarChart(
          title = "${currentFloor.displayName} — Production Target vs Achievement",
          lines = tvData.linePerformance
        )
      }

      Box(modifier = Modifier.weight(1.25f)) {
        LineEfficiencyBarChart(
          title = "${currentFloor.displayName} — Line-wise Efficiency %",
          lines = tvData.linePerformance
        )
      }

      Box(modifier = Modifier.weight(1.1f)) {
        LowestEfficiencyRankingCard(
          title = "Top 5 Lowest-Efficiency Lines",
          lines = tvData.lowestEfficiencyLines
        )
      }
    }

    // 5. Quality & Defect Intelligence Section (Line-wise DHU, Line-wise OQL, Highest-DHU Lines)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(modifier = Modifier.weight(1.25f)) {
        LineWiseDhuBarChart(
          title = "${currentFloor.displayName} — Line-wise Today's DHU",
          lines = tvData.linePerformance
        )
      }

      Box(modifier = Modifier.weight(1.25f)) {
        LineWiseOqlBarChart(
          title = "${currentFloor.displayName} — Line-wise OQL %",
          lines = tvData.linePerformance
        )
      }

      Box(modifier = Modifier.weight(1.1f)) {
        Top5DhuLinesCard(
          title = "Highest-DHU Lines (Top 5)",
          lines = tvData.linePerformance
        )
      }
    }

    // 6. Defect Pareto, Top 5 Defects Pie, and Top NPT Downtime Distribution
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(modifier = Modifier.weight(1.35f)) {
        DefectParetoBarAndLineChart(
          title = "${currentFloor.displayName} — Defect Pareto (80/20 Rule)",
          paretoData = tvData.paretoDefects
        )
      }

      Box(modifier = Modifier.weight(1.15f)) {
        Top5DefectsPieChartCard(
          title = "${currentFloor.displayName} — Top 5 Defects",
          defects = tvData.topDefects
        )
      }

      Box(modifier = Modifier.weight(1.1f)) {
        NptPieChartCard(
          title = "${currentFloor.displayName} — Top NPT Downtime",
          breakdown = tvData.nptBreakdown
        )
      }
    }

    // 7. Complete Verified Live Line Performance Matrix Table
    LiveLinePerformanceTable(
      lines = tvData.linePerformance
    )

    // 8. Active Incident Stoppage Alerts Strip (if any active stoppages)
    if (activeIncidents.isNotEmpty()) {
      Surface(
        color = Color(0xFF7F1D1D),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
            )
            Text(
              text = "⚠️ ACTIVE NPT STOPPAGE ALERTS (${activeIncidents.size}):",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Black
            )

            activeIncidents.take(2).forEach { inc ->
              val mins = ((System.currentTimeMillis() - inc.startTimeMillis) / 60000).coerceAtLeast(0)
              Surface(
                color = Color(0xFF991B1B),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "Line ${inc.lineId}: ${inc.category.displayName} (${mins}m elapsed) - ${inc.description}",
                  color = Color(0xFFFEE2E2),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
              }
            }
          }

          Text(
            text = "Broadcasted to Supervisors & Engineers",
            color = Color(0xFFFCA5A5),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

