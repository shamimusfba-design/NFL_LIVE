package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

  var refreshCounter by remember { mutableIntStateOf(30) }
  var autoRotate by remember { mutableStateOf(false) }

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

  // Refresh ticker that triggers live Google Sheet sync every 30s
  LaunchedEffect(Unit) {
    while (true) {
      delay(1000)
      if (refreshCounter > 1) {
        refreshCounter -= 1
      } else {
        refreshCounter = 30
        repository.refreshFromGoogleSheet()
      }
    }
  }

  // Auto rotation ticker
  LaunchedEffect(autoRotate, currentFloor) {
    if (autoRotate) {
      val floors = FloorScope.values()
      while (autoRotate) {
        delay(20000) // 20s
        val nextIdx = (floors.indexOf(currentFloor) + 1) % floors.size
        repository.setFloor(floors[nextIdx])
      }
    }
  }

  val activeIncidents = incidents.filter { it.status != IncidentStatus.RESOLVED }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF1F5F9))
      .verticalScroll(rememberScrollState())
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Enterprise Top Smart TV Header Bar
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(30.dp).testTag("tv_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }

          Surface(
            color = Color.White,
            shape = RoundedCornerShape(4.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_nfl_logo),
                contentDescription = "NFL Logo",
                modifier = Modifier.size(18.dp),
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
              text = "NFL LIVE — Northern Fashions Ltd.",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.Black
            )
            Text(
              text = "Live Factory Feed • Last Updated: $lastRefreshTime",
              color = Color(0xFF90CAF9),
              fontSize = 9.sp
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            onClick = {
              refreshCounter = 30
              repository.refreshFromGoogleSheet()
            },
            color = if (isSyncing) Color(0xFF0284C7) else Color(0xFF059669),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.testTag("tv_refresh_btn")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Live Feed",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = if (isSyncing) "Updating..." else "Refresh Now (${refreshCounter}s)",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Text(
            text = "📅 ${repository.getPeriodLabel(currentPeriod)}",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )

          Surface(
            color = Color(0xFF001F44),
            shape = RoundedCornerShape(6.dp)
          ) {
            Row(modifier = Modifier.padding(2.dp)) {
              PeriodScope.values().forEach { period ->
                val isSelected = currentPeriod == period
                Surface(
                  onClick = { repository.setPeriod(period) },
                  color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    text = period.displayName,
                    color = if (isSelected) Color.White else Color(0xFF90CAF9),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    // 2. Floor Selector Ribbon
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(text = "Floor Scope:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          FloorScope.values().forEach { floor ->
            val isSelected = currentFloor == floor
            Surface(
              onClick = { repository.setFloor(floor) },
              color = if (isSelected) Color(0xFF0A2558) else Color(0xFFF1F5F9),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = floor.displayName,
                color = if (isSelected) Color.White else Color(0xFF334E68),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
              )
            }
          }
        }

        Text(
          text = "Real-time sync active across Sew 1, Sew 2, and Sew 3 lines",
          fontSize = 10.sp,
          color = Color(0xFF64748B)
        )

        Button(
          onClick = { autoRotate = !autoRotate },
          colors = ButtonDefaults.buttonColors(containerColor = if (autoRotate) Color(0xFFEA580C) else Color(0xFF002D62)),
          shape = RoundedCornerShape(6.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text(text = if (autoRotate) "Auto-Rotate ON" else "TV Auto-Rotate", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    // 3. Top Executive KPI Metric Ribbon (6 Crisp Cards - NO CLOCK GAUGES!)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      ExecutiveKpiCard(
        title = "Today Efficiency",
        valueText = "${tvData.kpis.todayEfficiency}%",
        targetText = "Target: 75.0% (Δ -7.0 pp)",
        trendText = if (tvData.kpis.todayEfficiency >= 75) "ON TARGET" else "UNDER",
        isPositiveTrend = tvData.kpis.todayEfficiency >= 75,
        progressFraction = (tvData.kpis.todayEfficiency / 100.0).toFloat(),
        accentColor = Color(0xFF1E88E5),
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Monthly Efficiency",
        valueText = "${tvData.kpis.monthlyEfficiency}%",
        targetText = "vs last month (68.1%)",
        trendText = "▲ +${tvData.kpis.monthlyEfficiencyDelta} pp",
        isPositiveTrend = true,
        progressFraction = (tvData.kpis.monthlyEfficiency / 100.0).toFloat(),
        accentColor = Color(0xFF0284C7),
        modifier = Modifier.weight(1f)
      )

      ExecutiveKpiCard(
        title = "Output vs Target",
        valueText = "${tvData.kpis.todayOutputPcs} pcs",
        targetText = "Goal: ${tvData.kpis.todayTargetPcs} pcs (82.1%)",
        trendText = "-750 pcs gap",
        isPositiveTrend = false,
        progressFraction = (tvData.kpis.todayOutputPcs.toFloat() / tvData.kpis.todayTargetPcs.toFloat()),
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
        title = "FRI Pass Rate",
        valueText = "${tvData.kpis.friPassRate.toInt()}%",
        targetText = "Final Random Inspection",
        trendText = "100% PASS",
        isPositiveTrend = true,
        progressFraction = 1.0f,
        accentColor = Color(0xFF059669),
        modifier = Modifier.weight(1f)
      )

      OperatorMachineMetricCard(
        operatorsCount = tvData.kpis.operatorsCount,
        machinesCount = tvData.kpis.machinesCount,
        modifier = Modifier.weight(1f)
      )
    }

    // 4. Middle Charts Grid (Target vs Output, Line Efficiency, Top Defects)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(modifier = Modifier.weight(1.3f)) {
        TargetVsOutputBarChart(
          title = "${currentFloor.displayName} — Target vs Output Pieces",
          lines = tvData.linePerformance
        )
      }

      Box(modifier = Modifier.weight(1.2f)) {
        LineEfficiencyBarChart(
          title = "${currentFloor.displayName} — Line Efficiency (Today vs Month)",
          lines = tvData.linePerformance
        )
      }

      Box(modifier = Modifier.weight(1.1f)) {
        TopDefectsBarChart(
          title = "${currentFloor.displayName} — Top 5 Defects",
          defects = tvData.topDefects
        )
      }
    }

    // 5. Complete Live Line Performance Matrix Table
    LiveLinePerformanceTable(
      lines = tvData.linePerformance
    )

    // 6. Running NPT / Incident Ticker Strip at the Bottom
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
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
