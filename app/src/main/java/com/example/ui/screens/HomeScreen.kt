package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.NflRepository
import com.example.model.*
import com.example.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
  repository: NflRepository,
  onNavigateToProduction: () -> Unit,
  onNavigateToQuality: () -> Unit,
  onNavigateToQualityDashboard: () -> Unit = {},
  onNavigateToNpt: () -> Unit,
  onNavigateToSetup: () -> Unit,
  onNavigateToTv: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onOpenDrawer: () -> Unit = {},
  onNavigateToLineDeepDive: (String) -> Unit = {},
  onNavigateToMonthlySetup: () -> Unit = {},
  onNavigateToAdminUsers: () -> Unit = {},
  onNavigateToMonthlyBreakdown: (MonthlyMetricType) -> Unit = {},
  onNavigateToNotificationCenter: () -> Unit = {},
  onNavigateToLogin: () -> Unit = {}
) {
  val currentFloor by repository.currentFloor.collectAsStateWithLifecycle()
  val currentPeriod by repository.currentPeriod.collectAsStateWithLifecycle()
  val isDemoMode by repository.isDemoMode.collectAsStateWithLifecycle()
  val currentRole by repository.currentRole.collectAsStateWithLifecycle()
  val userName by repository.currentUserName.collectAsStateWithLifecycle()
  val currentUser by repository.currentUser.collectAsStateWithLifecycle()
  val incidents by repository.incidents.collectAsStateWithLifecycle()
  val productionEntries by repository.productionEntries.collectAsStateWithLifecycle()
  val qualityEntries by repository.qualityEntries.collectAsStateWithLifecycle()
  val weeklyQuality by repository.weeklyQuality.collectAsStateWithLifecycle()
  val targets by repository.targets.collectAsStateWithLifecycle()
  val monthlyTargets by repository.monthlyTargets.collectAsStateWithLifecycle()
  val isSyncing by repository.isSyncing.collectAsStateWithLifecycle()
  val lastRefreshTime by repository.lastRefreshTime.collectAsStateWithLifecycle()
  val syncStatusBanner by repository.syncStatusBanner.collectAsStateWithLifecycle()
  val sheetId by repository.sheetId.collectAsStateWithLifecycle()
  val refreshTrigger by repository.refreshTrigger.collectAsStateWithLifecycle()

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
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    val computed = repository.getTvDashboardData(currentFloor, currentPeriod)
    if (BuildConfig.DEBUG) {
      Log.d(
        "NflPerf",
        "HomeScreen.tvData floor=${currentFloor.apiValue} period=${currentPeriod.name} lines=${computed.linePerformance.size} took=${SystemClock.elapsedRealtime() - startMs}ms"
      )
    }
    computed
  }

  var selectedChartTab by remember { mutableStateOf("SPEEDOMETER") } // SPEEDOMETER | COLUMN | DONUT | PARETO
  var selectedLineDetail by remember { mutableStateOf<LinePerformancePoint?>(null) }
  var showClearSampleDataDialog by remember { mutableStateOf(false) }
  var showFactoryResetDialog by remember { mutableStateOf(false) }
  var showBsOqlDialog by remember { mutableStateOf(false) }

  val openIncidents = remember(incidents) {
    incidents.filter { it.status != IncidentStatus.RESOLVED }
  }

  val isAdminUser = currentUser?.isAdmin == true || currentRole == UserRole.ADMIN
  val isIeUser = currentUser?.role == UserRole.IE || currentRole == UserRole.IE
  val canViewDash = isAdminUser || isIeUser || (currentUser?.canViewDashboard ?: true)
  val canViewTv = isAdminUser || isIeUser || (currentUser?.canViewTvDashboard ?: false)
  val canViewMonthlyEff = isAdminUser || isIeUser || (currentUser?.canViewMonthlyEfficiency ?: false)
  val canViewMonthlyDhu = isAdminUser || isIeUser || (currentUser?.canViewMonthlyDhu ?: false)
  val canViewMonthlyOql = isAdminUser || isIeUser || (currentUser?.canViewMonthlyOql ?: false)
  val canViewDeepDive = isAdminUser || isIeUser || (currentUser?.canViewLineDeepDive ?: false)

  val canWriteProd = isAdminUser || isIeUser || (currentUser?.let { it.canProductionWrite || it.canFinishingWrite || it.canTargetRevisionWrite } ?: false)
  val canWriteQual = isAdminUser || isIeUser || (currentUser?.let { it.canQualityWrite || it.canOqlWrite } ?: false)
  val canWriteNpt = isAdminUser || isIeUser || (currentUser?.canNptWrite ?: false)
  val canWriteMorningSetup = isAdminUser || isIeUser || (currentUser?.let { it.canMorningTargetWrite && it.role in setOf(com.example.model.UserRole.IE, com.example.model.UserRole.PRODUCTION_REPORTER) } ?: false)
  val canWriteMonthlySetup = isAdminUser || isIeUser || (currentUser?.canMonthlyTargetWrite ?: false)
  val safeLineDeepDiveClick: (String) -> Unit = { lineId ->
    if (canViewDeepDive) onNavigateToLineDeepDive(lineId)
  }

  Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6F9))) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
      // 1. Sleek Enterprise Top Navy Header (Scrolls away off-screen)
      item {
        Surface(
          color = Color(0xFF0A2558),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Brand & Logo
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Surface(
                color = Color.White,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(24.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.ic_nfl_logo),
                    contentDescription = "NFL Logo",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Unspecified
                  )
                }
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "NFL LIVE",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Black,
                  maxLines = 1,
                  softWrap = false
                )
                if (currentUser?.isViewOnly == true) {
                  Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(3.dp)
                  ) {
                    Text(
                      text = "VIEW ONLY",
                      color = Color(0xFFB45309),
                      fontSize = 7.5.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false,
                      modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                  }
                }
              }
            }

            // Period Selector & Actions
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Surface(
                color = Color(0xFF06183B),
                shape = RoundedCornerShape(5.dp)
              ) {
                Row(modifier = Modifier.padding(1.5.dp)) {
                  PeriodScope.values().forEach { period ->
                    val isSelected = currentPeriod == period
                    Surface(
                      onClick = { repository.setPeriod(period) },
                      color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                      shape = RoundedCornerShape(3.5.dp)
                    ) {
                      Text(
                        text = when (period) {
                          PeriodScope.YESTERDAY -> "Yest"
                          PeriodScope.TODAY -> "Today"
                          PeriodScope.WEEK -> "Week"
                          PeriodScope.MONTH -> "Month"
                        },
                        color = if (isSelected) Color.White else Color(0xFF90CAF9),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                      )
                    }
                  }
                }
              }

              IconButton(
                onClick = onNavigateToNotificationCenter,
                modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).testTag("home_notification_bell_btn")
              ) {
                BadgedBox(
                  badge = {
                    if (openIncidents.isNotEmpty()) {
                      Badge(containerColor = Color(0xFFEF4444)) {
                        Text(openIncidents.size.toString(), fontSize = 9.sp, color = Color.White)
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notification Center",
                    tint = if (openIncidents.isNotEmpty()) Color(0xFFFDE047) else Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }

              IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).testTag("home_settings_btn")
              ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(17.dp))
              }
            }
          }
        }
      }

      // 2. STICKY HEADER: Sew 1 | Sew 2 | Sew 3 | All + Live Refresh Button (FROZEN AT TOP ON SCROLL)
      stickyHeader {
        Surface(
          color = Color(0xFF0A2558),
          shadowElevation = 3.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            IconButton(
              onClick = onOpenDrawer,
              modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).testTag("home_hamburger_btn")
            ) {
              Icon(Icons.Default.Menu, contentDescription = "Open navigation menu", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Surface(
              color = Color(0xFF06183B),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.weight(1f)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                FloorScope.values().forEach { floor ->
                  val isSelected = currentFloor == floor
                  Surface(
                    onClick = { repository.setFloor(floor) },
                    color = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Text(
                      text = if (floor == FloorScope.FINISHING) "Finish" else floor.displayName,
                      color = if (isSelected) Color.White else Color(0xFF90CAF9),
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      textAlign = TextAlign.Center,
                      maxLines = 1,
                      modifier = Modifier.padding(vertical = 4.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 3. Dashboard Content (Scrolls smoothly underneath the frozen tabs)
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
      // 1. Open Incident / NPT Alert Strip
      if (openIncidents.isNotEmpty()) {
        val topIncident = openIncidents.first()
        val elapsedMinutes = ((System.currentTimeMillis() - topIncident.startTimeMillis) / 60000).coerceAtLeast(0)
        val commentCount = topIncident.comments.size
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
          border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFDBA74))),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToNpt() }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFFFEDD5)),
                contentAlignment = Alignment.Center
              ) {
                Text(text = "⚠️", fontSize = 16.sp)
              }
              Column {
                Text(
                  text = "${openIncidents.size} Active Downtime / NPT Incidents",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFF9A3412)
                )
                Text(
                  text = "Line ${topIncident.lineId} (${topIncident.category.displayName}) • $elapsedMinutes mins elapsed",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFFC2410C)
                )
              }
            }

            Surface(
              color = Color(0xFFEA580C),
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = if (commentCount > 0) "💬 $commentCount • Manage →" else "💬 Comment →",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              }
            }
          }
        }
      }

      // 2. Section Header: Executive KPIs
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${currentFloor.displayName} — Executive Performance",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
          )
          Text(
            text = "Aggregated shift efficiency & quality metrics",
            fontSize = 11.sp,
            color = Color(0xFF64748B)
          )
        }

        Surface(
          color = Color(0xFFE2E8F0),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "Role: ${currentRole.displayName}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF334E68),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // Admin-only data reset controls: Clear Demo Data or Factory Reset All Operational Records (Local + Cloud).
      if (isAdminUser) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { showClearSampleDataDialog = true },
            modifier = Modifier.weight(1f).testTag("home_clear_demo_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
          ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear Demo Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = { showFactoryResetDialog = true },
            modifier = Modifier.weight(1f).testTag("home_factory_reset_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
          ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Factory Reset All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // 3. Executive KPI Cards with Clickable Breakdown Pages
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val monthlyObj = remember(currentFloor, monthlyTargets, productionEntries, qualityEntries, refreshTrigger) {
          repository.getMonthlyTarget(currentFloor.apiValue)
        }
        val monthAchEff = monthlyObj.updateEfficiencyPct
        val monthDhu = monthlyObj.targetDhuPct
        val bsOqlVal = monthlyObj.bsOqlPct

        // Card 1: Monthly Efficiency (Tap to view Line-by-Line Monthly Efficiency page)
        ExecutiveKpiCard(
          title = "Monthly Efficiency",
          valueText = "${monthAchEff}%",
          targetText = if (canViewMonthlyEff) "Tap for Line Table →" else "🔒 View Restricted",
          trendText = "EFF",
          isPositiveTrend = true,
          accentColor = Color(0xFF1E88E5),
          endVisual = {
            MiniSpeedometerGauge(value = monthAchEff, targetValue = 75.0)
          },
          onClick = if (canViewMonthlyEff) ({ onNavigateToMonthlyBreakdown(MonthlyMetricType.EFFICIENCY) }) else null,
          modifier = Modifier.weight(1f).testTag("kpi_monthly_eff_btn")
        )

        // Card 2: Monthly DHU (Tap to view Line-by-Line Monthly DHU page)
        ExecutiveKpiCard(
          title = "Monthly DHU",
          valueText = String.format(java.util.Locale.US, "%.2f", monthDhu),
          targetText = if (canViewMonthlyDhu) "Target: ≤ 1.50 • Tap →" else "🔒 View Restricted",
          trendText = "DHU",
          isPositiveTrend = monthDhu <= 2.0,
          progressFraction = (monthDhu / 3.0).toFloat().coerceIn(0f, 1f),
          accentColor = Color(0xFF7C3AED),
          onClick = if (canViewMonthlyDhu) ({ onNavigateToMonthlyBreakdown(MonthlyMetricType.DHU) }) else null,
          modifier = Modifier.weight(1f).testTag("kpi_monthly_dhu_btn")
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ExecutiveKpiCard(
          title = "${currentPeriod.displayName} Output",
          valueText = "${tvData.kpis.todayOutputPcs} pcs",
          targetText = "Target: ${tvData.kpis.todayTargetPcs} pcs",
          trendText = if (tvData.kpis.todayOutputPcs >= tvData.kpis.todayTargetPcs) "ON TARGET" else "${(tvData.kpis.todayTargetPcs - tvData.kpis.todayOutputPcs).coerceAtLeast(0)} pcs gap",
          isPositiveTrend = tvData.kpis.todayOutputPcs >= tvData.kpis.todayTargetPcs,
          accentColor = Color(0xFF10B981),
          endVisual = {
            MiniDonutProgress(
              fraction = if (tvData.kpis.todayTargetPcs > 0) (tvData.kpis.todayOutputPcs.toFloat() / tvData.kpis.todayTargetPcs.toFloat()).coerceIn(0f, 1f) else 0f,
              accentColor = Color(0xFF10B981)
            )
          },
          modifier = Modifier.weight(1f)
        )

        // Card 4: Up-to-Date OQL (Monthly OQL - Tap to view Line-by-Line Monthly OQL page)
        ExecutiveKpiCard(
          title = "Monthly OQL",
          valueText = "${tvData.kpis.todayOql}%",
          targetText = if (canViewMonthlyOql) "Benchmark: 5.00% • Tap →" else "🔒 View Restricted",
          trendText = if (tvData.kpis.todayOql <= 5.0) "PASS" else "ALERT",
          isPositiveTrend = tvData.kpis.todayOql <= 5.0,
          progressFraction = (tvData.kpis.todayOql / 10.0).toFloat().coerceIn(0f, 1f),
          accentColor = Color(0xFFF59E0B),
          onClick = if (canViewMonthlyOql) ({ onNavigateToMonthlyBreakdown(MonthlyMetricType.OQL) }) else null,
          modifier = Modifier.weight(1f).testTag("kpi_monthly_oql_btn")
        )
      }

      // =========================================================================
      // NEW CHART: Weekly Trend Chart for "BS OQL" (Insaf Life Dashboard Module)
      // Placed directly below the existing "Up-to-Date OQL" metric card
      // =========================================================================
      OqlTrendChart(
        title = "${currentFloor.displayName} — BS OQL vs NFL OQL Trend",
        subtitle = "Weekly IE-entered BS OQL compared with weekly NFL AQC OQL",
        points = weeklyQuality.sortedBy { it.weekStartIso }.takeLast(12)
      )

      // Row 3: BS OQL (Buyer Standard OQL entered by IE) & FRI Pass Rate
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val monthlyObjFloor = remember(currentFloor, monthlyTargets) { repository.getMonthlyTarget(currentFloor.apiValue) }
        val bsOqlValFloor = monthlyObjFloor.bsOqlPct
        val canEditBsOql = canWriteMonthlySetup || (currentUser?.canOqlWrite == true)

        ExecutiveKpiCard(
          title = "BS OQL",
          valueText = "${String.format(java.util.Locale.US, "%.2f", bsOqlValFloor)}%",
          targetText = if (canEditBsOql) "IE Entry • Std: ≤ 2.50% ✏️" else "Benchmark: ≤ 2.50%",
          trendText = if (bsOqlValFloor <= 2.50) "IE STANDARD" else "ATTENTION",
          isPositiveTrend = bsOqlValFloor <= 2.50,
          progressFraction = (bsOqlValFloor / 5.0).toFloat().coerceIn(0f, 1f),
          accentColor = Color(0xFF0284C7),
          onClick = if (canEditBsOql) ({ showBsOqlDialog = true }) else null,
          modifier = Modifier.weight(1f).testTag("kpi_bs_oql_btn")
        )

        ExecutiveKpiCard(
          title = "FRI Pass Rate",
          valueText = "${tvData.kpis.friPassRate.toInt()}%",
          targetText = "First-time Right Quality",
          trendText = "100% PERFECT",
          isPositiveTrend = true,
          progressFraction = 1.0f,
          accentColor = Color(0xFF059669),
          modifier = Modifier.weight(1f)
        )
      }

      if (currentPeriod == PeriodScope.TODAY || currentPeriod == PeriodScope.YESTERDAY) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OperatorMachineMetricCard(
            operatorsCount = tvData.kpis.operatorsCount,
            machinesCount = tvData.kpis.machinesCount,
            helpersCount = tvData.kpis.helpersCount,
            finishingCount = tvData.kpis.finishingStaffCount,
            modifier = Modifier.fillMaxWidth()
          )
        }
      } else {
        DayByDayTrendAnalyticsCard(
          title = "${currentFloor.displayName} — ${currentPeriod.displayName} Day-by-Day Trends",
          subtitle = if (currentPeriod == PeriodScope.WEEK) "Daily performance over the last 7 days" else "Daily performance for ${repository.getPeriodLabel(currentPeriod)}",
          periodScope = currentPeriod,
          points = tvData.weeklyQaTrend
        )
      }

      if (canViewDeepDive) {
        Surface(
          onClick = onNavigateToQualityDashboard,
          shape = RoundedCornerShape(9.dp),
          color = Color(0xFFEFF6FF),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF1D4ED8))
              Column {
                Text("Quality Analytics Dashboard", color = Color(0xFF1E3A8A), fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("Bi-hourly audit • Pass/Fail • Defect trends", color = Color(0xFF475569), fontSize = 9.sp)
              }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open quality dashboard", tint = Color(0xFF1D4ED8))
          }
        }
      }

      // 4. Quick Action Modules (Entry, Supervisor, TV, History - Strictly gated by RBAC Write Permissions)
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Factory Action Center",
              fontSize = 12.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF0F172A)
            )
            if (isAdminUser) {
              Surface(
                onClick = onNavigateToAdminUsers,
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A)
              ) {
                Text(
                  text = "👥 Access Control",
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFFDE047),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          val hasAnyWriteAction = canWriteProd || canWriteQual || canWriteMonthlySetup || canWriteMorningSetup || canWriteNpt

          if (!hasAnyWriteAction) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFEF3C7),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                Text(
                  text = "View-Only Access (${currentRole.displayName}) — Data Entry Disabled",
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF92400E)
                )
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // 1. Production Entry (Only if canWriteProd)
              if (canWriteProd) {
                Surface(
                  onClick = onNavigateToProduction,
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFEFF6FF),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE)),
                  modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
                    Text("Prod Entry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Hourly Pcs", fontSize = 9.sp, color = Color(0xFF60A5FA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }

              // 2. Quality Entry (Only if canWriteQual)
              if (canWriteQual) {
                Surface(
                  onClick = onNavigateToQuality,
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFFFFBEB),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEF3C7)),
                  modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                    Text("QA Inspect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("OQL & DHU", fontSize = 9.sp, color = Color(0xFFFBBF24), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }

              // 3. Monthly Data Entry (Only if canWriteMonthlySetup)
              if (canWriteMonthlySetup) {
                Surface(
                  onClick = onNavigateToMonthlySetup,
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFF5F3FF),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE)),
                  modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                    Text("Monthly Data", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D28D9), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Targets & MTD", fontSize = 9.sp, color = Color(0xFFA78BFA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }

              // 4. Target Setting / Morning Line Setup (Only if canWriteMorningSetup)
              if (canWriteMorningSetup) {
                Surface(
                  onClick = onNavigateToSetup,
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFF0FDF4),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)),
                  modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(20.dp))
                    Text("Target Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Lines & SMV", fontSize = 9.sp, color = Color(0xFF4ADE80), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }

              // 5. NPT Breakdown Entry (Only if canWriteNpt)
              if (canWriteNpt) {
                Surface(
                  onClick = onNavigateToNpt,
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFFEF2F2),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                  modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Text("NPT Log", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Downtime", fontSize = 9.sp, color = Color(0xFFF87171), maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
        }
      }

      if (!canViewDash) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFB45309))
            Column {
              Text("Live Dashboard View Restricted", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
              Text("Your role (${currentRole.displayName}) currently does not have Live Production Dashboard view permission enabled.", fontSize = 11.sp, color = Color(0xFFB45309))
            }
          }
        }
      } else {
        // 5. Chart Selection & Presentation (Starting from Left Edge with Smooth Horizontal Scroll)
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Visual Performance Analytics",
              fontSize = 13.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF0F172A)
            )
            Surface(
              color = Color(0xFFE2E8F0),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "⇄ Swipe Tab",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
              )
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(
              "SPEEDOMETER" to "⚡ Line-Wise Efficiency",
              "COLUMN" to "📊 Target vs Output (Pieces)",
              "EFF_BAR" to "📈 Today vs Monthly Efficiency",
              "PARETO" to "🎯 Top 5 Defects (Pareto)"
            ).forEach { (id, label) ->
              val isSelected = selectedChartTab == id
              Surface(
                onClick = { selectedChartTab = id },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1))
              ) {
                Text(
                  text = label,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) Color.White else Color(0xFF334155),
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }

        when (selectedChartTab) {
          "SPEEDOMETER" -> SpeedometerGaugeCard(
            title = "${currentFloor.displayName} — Floor & Line Efficiency",
            currentEfficiency = tvData.kpis.todayEfficiency,
            targetEfficiency = 75.0,
            lines = tvData.linePerformance,
            onLineClick = safeLineDeepDiveClick
          )
          "COLUMN" -> TargetVsOutputBarChart(
            title = "${currentFloor.displayName} — Target vs Output Pieces (Column Chart)",
            lines = tvData.linePerformance,
            onLineClick = safeLineDeepDiveClick
          )
          "EFF_BAR" -> com.example.ui.components.LineEfficiencyBarChart(
            title = "${currentFloor.displayName} — Line-Wise Today vs Monthly Efficiency",
            lines = tvData.linePerformance,
            onLineClick = safeLineDeepDiveClick
          )
          "PARETO" -> ParetoDonutDefectsCard(
            title = "${currentFloor.displayName} — Top 5 Defects (Pareto Donut)",
            defects = tvData.topDefects
          )
          else -> SpeedometerGaugeCard(
            title = "${currentFloor.displayName} — Floor & Line Efficiency",
            currentEfficiency = tvData.kpis.todayEfficiency,
            targetEfficiency = 75.0,
            lines = tvData.linePerformance,
            onLineClick = safeLineDeepDiveClick
          )
        }

        // 6. Quality Analytics: Dual Defect Rectification Status (Sewing Defect + Spot Defect Tracking)
        DualDefectTrackingCard(
          sewingSent = tvData.kpis.totalDefectSent,
          sewingReceived = tvData.kpis.totalDefectReceived,
          sewingBalance = tvData.kpis.totalDefectBalance,
          spotSent = tvData.kpis.totalSpotSent,
          spotReceived = tvData.kpis.totalSpotReceived,
          spotBalance = tvData.kpis.totalSpotBalance,
          floorName = currentFloor.displayName
        )

        // 7. Quality Analytics: Top 5 DHU Lines Column Chart (Today)
        Top5DhuLinesCard(
          title = "${currentFloor.displayName} — Top 5 DHU Lines Today",
          lines = tvData.linePerformance,
          onLineClick = safeLineDeepDiveClick
        )

        // 8. Quality Analytics: Top 5 Online Defects Solid Pie Chart (Today)
        Top5DefectsPieChartCard(
          title = "${currentFloor.displayName} — Top 5 Defects Today",
          defects = tvData.topDefects
        )

        // 8b. NPT Downtime & Non-Productive Lost Time Distribution Pie Chart
        val (floorTotalNptMins, floorLostPcs) = remember(tvData.nptBreakdown, tvData.kpis.todayTargetPcs) {
          val totalMins = tvData.nptBreakdown.sumOf { it.minutes }
          val hourlyTarget = if (tvData.kpis.todayTargetPcs > 0) (tvData.kpis.todayTargetPcs.toDouble() / 8.0) else 525.0
          val lostPcs = Math.round((hourlyTarget / 60.0) * totalMins).toInt()
          totalMins to lostPcs
        }

        NptPieChartCard(
          title = "${currentFloor.displayName} — NPT Downtime Distribution (Lost Time)",
          subtitle = "Root causes for non-productive hours & shift bottlenecks",
          breakdown = tvData.nptBreakdown,
          totalLostPcs = floorLostPcs
        )

        // 9. Line-Wise DHU & Quality Intelligence Table (With Frozen Line Column)
        LineWiseDhuCard(
          lines = tvData.linePerformance,
          floorName = currentFloor.displayName,
          onLineClick = safeLineDeepDiveClick
        )
      }

      // 7. Footer Info
      Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "NFL LIVE — Northern Fashions Ltd.",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A)
            )
            Text(
              text = "Individual Morning Target Pcs & Efficiency % per line",
              fontSize = 9.sp,
              color = Color(0xFF64748B)
            )
          }

          if (canWriteMorningSetup) {
            Button(
              onClick = onNavigateToSetup,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A2558)),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text("🌅 Morning Setup", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
  }

  if (showClearSampleDataDialog && isAdminUser) {
    AlertDialog(
      onDismissRequest = { showClearSampleDataDialog = false },
      title = { Text("Clear Demo / Sample Data?", fontWeight = FontWeight.Black) },
      text = {
        Text("This removes all records tagged as sample/demo data and disables auto-generated chart numbers. If you also want to wipe test entries saved in Cloud Firestore, use 'Factory Reset All' instead.")
      },
      confirmButton = {
        Button(
          onClick = {
            repository.removeAllDemoData()
            showClearSampleDataDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
        ) {
          Text("Clear Demo Data", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearSampleDataDialog = false }) { Text("Cancel") }
      }
    )
  }

  if (showFactoryResetDialog && isAdminUser) {
    var resetConfirmInput by remember { mutableStateOf("") }
    val canConfirmReset = resetConfirmInput.trim() == "RESET"
    AlertDialog(
      onDismissRequest = { showFactoryResetDialog = false },
      title = { Text("Factory Reset All Operational Records?", fontWeight = FontWeight.Black, color = Color(0xFFB91C1C)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("This will permanently clear ALL Production, Quality, NPT Incidents, and Daily Line Setups from both this device AND Cloud Firestore so your factory starts clean at 0.\n\nEmployee Accounts, Roles, and Factory Line Master will be preserved.\n\nType RESET below to confirm:")
          OutlinedTextField(
            value = resetConfirmInput,
            onValueChange = { resetConfirmInput = it },
            placeholder = { Text("Type RESET") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("home_factory_reset_confirm_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (canConfirmReset) {
              repository.resetAllOperationalData(clearCloud = true)
              showFactoryResetDialog = false
            }
          },
          enabled = canConfirmReset,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
          modifier = Modifier.testTag("home_factory_reset_confirm_btn")
        ) {
          Text("Confirm Factory Reset", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showFactoryResetDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Interactive BS OQL Entry Modal (IE Setup)
  if (showBsOqlDialog) {
    var editBsFloor by remember { mutableStateOf(if (currentFloor == FloorScope.ALL) FloorScope.SEW_2 else currentFloor) }
    val currentTarget = repository.getMonthlyTarget(editBsFloor.apiValue)
    var bsOqlInput by remember(editBsFloor) { mutableStateOf(currentTarget.bsOqlPct.toString()) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }
    val canEdit = currentUser?.canMonthlyTargetWrite ?: true

    AlertDialog(
      onDismissRequest = { showBsOqlDialog = false },
      containerColor = Color.White,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
          Column {
            Text("BS OQL Entry (IE Setup)", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0F172A))
            Text("Buyer Standard OQL • IE Benchmark", fontSize = 10.sp, color = Color(0xFF64748B))
          }
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Buyer Standard OQL (BS OQL) is set by Industrial Engineering (IE) / Planning department as the quality acceptance tolerance threshold.",
            fontSize = 11.sp,
            color = Color(0xFF334155)
          )

          // Floor Selector
          Text("Floor Scope:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.FINISHING).forEach { fl ->
              val isSel = editBsFloor == fl
              Surface(
                onClick = {
                  editBsFloor = fl
                  saveFeedback = null
                },
                shape = RoundedCornerShape(6.dp),
                color = if (isSel) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = when (fl) {
                    FloorScope.SEW_1 -> "Sew 1"
                    FloorScope.SEW_2 -> "Sew 2"
                    FloorScope.SEW_3 -> "Sew 3"
                    FloorScope.FINISHING -> "Finishing"
                    else -> fl.displayName
                  },
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSel) Color.White else Color(0xFF334155),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(vertical = 7.dp)
                )
              }
            }
          }

          // Input field
          OutlinedTextField(
            value = bsOqlInput,
            onValueChange = {
              bsOqlInput = it
              saveFeedback = null
            },
            label = { Text("BS OQL Target (%)") },
            placeholder = { Text("e.g. 2.40") },
            singleLine = true,
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = { Text("%", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(end = 12.dp)) },
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )

          // Quick Presets
          Text("Quick IE Standard Presets (AQL Thresholds):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            listOf("1.80", "2.00", "2.20", "2.40", "2.50").forEach { preset ->
              Surface(
                onClick = { if (canEdit) bsOqlInput = preset },
                shape = RoundedCornerShape(4.dp),
                color = if (bsOqlInput == preset) Color(0xFFBAE6FD) else Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = "$preset%",
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0369A1),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(vertical = 5.dp)
                )
              }
            }
          }

          saveFeedback?.let { msg ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFFDCFCE7),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = msg,
                color = Color(0xFF166534),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
              )
            }
          }

          if (!canEdit) {
            Surface(
              color = Color(0xFFFEF3C7),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "🔒 View-Only: Only authorized IE or Admin accounts can modify BS OQL.",
                color = Color(0xFF92400E),
                fontSize = 10.sp,
                modifier = Modifier.padding(8.dp)
              )
            }
          }

          // Full Setup Link
          TextButton(
            onClick = {
              showBsOqlDialog = false
              onNavigateToMonthlySetup()
            },
            modifier = Modifier.align(Alignment.End)
          ) {
            Text("Open Full Monthly IE Setup →", fontSize = 11.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val num = bsOqlInput.toDoubleOrNull() ?: 2.40
            val target = repository.getMonthlyTarget(editBsFloor.apiValue)
            repository.saveMonthlyTarget(
              target.copy(
                bsOqlPct = num,
                lastUpdatedBy = userName.ifEmpty { "IE Engineer" }
              )
            )
            saveFeedback = "✓ Saved BS OQL ${num}% for ${editBsFloor.displayName}"
          },
          enabled = canEdit,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
        ) {
          Text("Save BS OQL", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showBsOqlDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // Detail Modal for selected line
  selectedLineDetail?.let { line ->
    AlertDialog(
      onDismissRequest = { selectedLineDetail = null },
      containerColor = Color.White,
      title = {
        Text("Line ${line.lineId} — Live Performance", fontWeight = FontWeight.Black, fontSize = 16.sp)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Running Item:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(line.itemName, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Standard Minute Value (SMV):", fontSize = 12.sp, color = Color(0xFF475569))
            Text("${String.format("%.2f", line.smv)} min/pc", fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Floor:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(line.floor, fontWeight = FontWeight.SemiBold)
          }
          Divider(color = Color(0xFFE2E8F0))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Morning Target Pieces:", fontSize = 12.sp, color = Color(0xFF475569))
            Text("${line.targetPcs} pcs", fontWeight = FontWeight.Bold)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Morning Target Efficiency:", fontSize = 12.sp, color = Color(0xFF475569))
            Text("${line.targetEfficiencyPct.toInt()}%", fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Actual Output Produced:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(if (line.achievementPcs != null) "${line.achievementPcs} pcs" else "Pending submission", fontWeight = FontWeight.Bold)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Actual Line Efficiency:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(
              text = if (line.todayEfficiencyPct != null) "${String.format("%.1f", line.todayEfficiencyPct)}%" else "--",
              fontWeight = FontWeight.Black,
              color = when {
                line.todayEfficiencyPct == null -> Color(0xFF94A3B8)
                line.todayEfficiencyPct >= line.targetEfficiencyPct -> Color(0xFF166534)
                else -> Color(0xFFDC2626)
              }
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Target Output Achievement:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(if (line.achievementPct != null) "${String.format("%.1f", line.achievementPct)}%" else "--", fontWeight = FontWeight.Bold)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Line OQL Defect Rate:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(if (line.todayOqlPct != null) "${String.format("%.2f", line.todayOqlPct)}%" else "0.00%", fontWeight = FontWeight.Bold)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reporting Status:", fontSize = 12.sp, color = Color(0xFF475569))
            Text(line.reportingStatus, fontWeight = FontWeight.Bold, color = if (line.achievementPcs != null) Color(0xFF166534) else Color(0xFFD97706))
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { selectedLineDetail = null }) {
          Text("Close", fontWeight = FontWeight.Bold)
        }
      }
    )
  }
}
}

@Composable
private fun OqlTrendChart(
  title: String,
  subtitle: String,
  points: List<WeeklyQualityEntry>
) {
  val chartPoints = remember(points) { points.filter { it.bsOqlPct != null || it.nflAqcPct != null } }
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
      Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B))
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF1976D2)))
          Text("BS OQL", fontSize = 10.sp, color = Color(0xFF334155))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
          Text("NFL AQC OQL", fontSize = 10.sp, color = Color(0xFF334155))
        }
      }
      if (chartPoints.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(145.dp), contentAlignment = Alignment.Center) {
          Text("No weekly BS OQL / NFL AQC records entered yet", fontSize = 12.sp, color = Color(0xFF64748B))
        }
      } else {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
          val left = 12f
          val right = size.width - 12f
          val top = 10f
          val bottom = size.height - 12f
          val plotHeight = bottom - top
          val maxValue = maxOf(
            5.0,
            (chartPoints.maxOfOrNull { maxOf(it.bsOqlPct ?: 0.0, it.nflAqcPct ?: 0.0) } ?: 4.0) + 1.0
          ).toFloat()
          for (i in 0..4) {
            val y = top + plotHeight * i / 4f
            drawLine(Color(0xFFE2E8F0), Offset(left, y), Offset(right, y), strokeWidth = 1f)
          }
          fun pointAt(index: Int, value: Double): Offset {
            val x = if (chartPoints.size == 1) (left + right) / 2f
              else left + (right - left) * index / (chartPoints.size - 1).toFloat()
            val y = bottom - (value.toFloat() / maxValue) * plotHeight
            return Offset(x, y)
          }
          fun drawSeries(
            valueSelector: (WeeklyQualityEntry) -> Double?,
            otherSelector: (WeeklyQualityEntry) -> Double?,
            color: Color,
            placeAboveWhenClose: Boolean
          ) {
            var previous: Offset? = null
            chartPoints.forEachIndexed { index, point ->
              val value = valueSelector(point)
              if (value == null) {
                previous = null
              } else {
                val raw = pointAt(index, value)
                val other = otherSelector(point)
                val close = other?.let { kotlin.math.abs(raw.y - pointAt(index, it).y) < 14f } == true
                val offset = if (close) if (placeAboveWhenClose) -7f else 7f else 0f
                val plotted = Offset(raw.x, (raw.y + offset).coerceIn(top + 2f, bottom - 2f))
                previous?.let { drawLine(color, it, plotted, strokeWidth = 3f) }
                drawCircle(color, radius = 3.5f, center = plotted)
                previous = plotted
              }
            }
          }
          drawSeries({ it.bsOqlPct }, { it.nflAqcPct }, Color(0xFF1976D2), true)
          drawSeries({ it.nflAqcPct }, { it.bsOqlPct }, Color(0xFFF59E0B), false)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          chartPoints.forEachIndexed { index, point ->
            if (index == 0 || index == chartPoints.lastIndex || index % 3 == 0) {
              Text(
                text = point.weekKey.substringAfter("-W", point.weekKey).let { "W$it" },
                fontSize = 8.sp,
                color = Color(0xFF64748B),
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Dual Defect Rectification Status Card (Sewing Defect & Spot Defect Dual Tracking)
 * Displays hourly Sent, Received, and Balance for:
 * 1. Sewing Defects
 * 2. Spot / Stain Defects
 */
@Composable
fun DualDefectTrackingCard(
  sewingSent: Int,
  sewingReceived: Int,
  sewingBalance: Int,
  spotSent: Int,
  spotReceived: Int,
  spotBalance: Int,
  floorName: String,
  modifier: Modifier = Modifier
) {
  val sewRate = if (sewingSent > 0) ((sewingReceived.toDouble() / sewingSent.toDouble()) * 100.0).coerceIn(0.0, 100.0) else 100.0
  val spotRate = if (spotSent > 0) ((spotReceived.toDouble() / spotSent.toDouble()) * 100.0).coerceIn(0.0, 100.0) else 100.0

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Defect Rectification Intelligence",
              fontSize = 13.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF0F172A)
            )
            Surface(
              color = Color(0xFFEFF6FF),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = "Sewing & Spot Tracking",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = "$floorName • Hourly Sent to Line vs Rectified Received",
            fontSize = 10.sp,
            color = Color(0xFF64748B)
          )
        }

        val totalPending = sewingBalance + spotBalance
        val hasFlowRecords = sewingSent + sewingReceived + spotSent + spotReceived > 0
        Surface(
          color = if (!hasFlowRecords) Color(0xFFF1F5F9) else if (totalPending > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
          shape = RoundedCornerShape(6.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, if (!hasFlowRecords) Color(0xFFCBD5E1) else if (totalPending > 0) Color(0xFFFECACA) else Color(0xFFBBF7D0))
        ) {
          Text(
            text = if (!hasFlowRecords) "No Records" else if (totalPending > 0) "Pending: $totalPending pcs" else "100% Cleared",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (!hasFlowRecords) Color(0xFF64748B) else if (totalPending > 0) Color(0xFFDC2626) else Color(0xFF166534),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      // SECTION 1: SEWING DEFECTS
      Surface(
        color = Color(0xFFFAFAFA),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Box(modifier = Modifier.size(8.dp).background(Color(0xFFEA580C), CircleShape))
              Text("1. Sewing Defect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
            Text(
              text = "Recovery: ${String.format(java.util.Locale.US, "%.1f%%", sewRate)}",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (sewRate >= 90) Color(0xFF16A34A) else Color(0xFFD97706)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            // Sent
            Surface(
              color = Color(0xFFFFF7ED),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                Text("Sew Sent", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                Text("$sewingSent", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFEA580C))
                Text("Sent to Line", fontSize = 7.5.sp, color = Color(0xFF9A3412))
              }
            }
            // Recv
            Surface(
              color = Color(0xFFF0FDF4),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                Text("Sew Recv", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                Text("$sewingReceived", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                Text("Rectified Recv", fontSize = 7.5.sp, color = Color(0xFF166534))
              }
            }
            // Bal
            Surface(
              color = if (sewingBalance > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, if (sewingBalance > 0) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                Text("Sew Bal", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (sewingBalance > 0) Color(0xFFB91C1C) else Color(0xFF15803D))
                Text("$sewingBalance", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (sewingBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A))
                Text(if (sewingBalance > 0) "Pending Alter" else "Cleared", fontSize = 7.5.sp, color = if (sewingBalance > 0) Color(0xFF991B1B) else Color(0xFF166534))
              }
            }
          }
        }
      }

      // SECTION 2: SPOT DEFECTS
      Surface(
        color = Color(0xFFFAFAFA),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Box(modifier = Modifier.size(8.dp).background(Color(0xFF7C3AED), CircleShape))
              Text("2. Spot Defect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
            Text(
              text = "Recovery: ${String.format(java.util.Locale.US, "%.1f%%", spotRate)}",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (spotRate >= 90) Color(0xFF16A34A) else Color(0xFFD97706)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            // Spot Sent
            Surface(
              color = Color(0xFFFAF5FF),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3E8FF)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                Text("Spot Sent", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7E22CE))
                Text("$spotSent", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF9333EA))
                Text("Sent to Spot", fontSize = 7.5.sp, color = Color(0xFF6B21A8))
              }
            }
            // Spot Recv
            Surface(
              color = Color(0xFFF0FDF4),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                Text("Spot Recv", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                Text("$spotReceived", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                Text("Clean Recv", fontSize = 7.5.sp, color = Color(0xFF166534))
              }
            }
            // Spot Bal
            Surface(
              color = if (spotBalance > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, if (spotBalance > 0) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                Text("Spot Bal", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (spotBalance > 0) Color(0xFFB91C1C) else Color(0xFF15803D))
                Text("$spotBalance", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (spotBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A))
                Text(if (spotBalance > 0) "Pending Spot" else "Cleared", fontSize = 7.5.sp, color = if (spotBalance > 0) Color(0xFF991B1B) else Color(0xFF166534))
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Backward compatibility SewingDefectTrackingCard
 */
@Composable
fun SewingDefectTrackingCard(
  totalSent: Int,
  totalReceived: Int,
  totalBalance: Int,
  floorName: String,
  modifier: Modifier = Modifier
) {
  DualDefectTrackingCard(
    sewingSent = totalSent,
    sewingReceived = totalReceived,
    sewingBalance = totalBalance,
    spotSent = (totalSent * 0.4).toInt(),
    spotReceived = (totalReceived * 0.4).toInt(),
    spotBalance = (totalBalance * 0.4).toInt(),
    floorName = floorName,
    modifier = modifier
  )
}

/**
 * Live Line Performance & Quality Matrix Card (Directly on Home Screen)
 * The Line column is FROZEN (pinned on left) so scrolling horizontally keeps the Line number always visible!
 * Displays: Line (Frozen), Item, SMV, Hourly Tgt, Day Tgt, Day Output, Efficiency %, Achive %,
 * Day DHU, Day OQL, Defect Sent, Defect Recv, Defect Bal.
 */
@Composable
fun LineWiseDhuCard(
  lines: List<LinePerformancePoint>,
  floorName: String,
  onLineClick: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val horizontalScrollState = rememberScrollState()

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = "Line Performance & Quality Matrix",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
          )
          Text(
            text = "${lines.size} Lines on $floorName • ⇄ Swipe right to view full metrics",
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

      // Visual Swipe Cue & Scroll Indicator
      val maxScroll = horizontalScrollState.maxValue
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
            text = if (horizontalScrollState.value > 20) "◄ Swiped: Viewing Output, Efficiency, DHU, OQL & Defect Bal" else "Swipe left to view Efficiency, DHU & Defect Balance ➔",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0284C7)
          )
          Text(
            text = "13 Columns Matrix",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
          )
        }

        LinearProgressIndicator(
          progress = { if (maxScroll > 0) (horizontalScrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0.15f, 1f) else 0.25f },
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
        // 1. LINE COLUMN (Locked on left side)
        Column(
          modifier = Modifier.width(60.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Header Cell
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
              Text(
                text = "Line",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
              )
            }
          }

          // Frozen Row Cells
          lines.forEachIndexed { index, line ->
            val isEven = index % 2 == 0
            Surface(
              onClick = { onLineClick(line.lineId) },
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
            .width(1.dp)
            .height((36 + lines.size * 42).dp)
            .background(Color(0xFFCBD5E1))
        )

        // 2. HORIZONTALLY SCROLLABLE REMAINING COLUMNS
        Box(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(horizontalScrollState)
        ) {
          Column(
            modifier = Modifier.width(1195.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // Header Row for Remaining 15 Columns
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
                Text("Sew Sent", color = Color(0xFFFDBA74), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Sew Recv", color = Color(0xFF86EFAC), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Sew Bal", color = Color(0xFFFCA5A5), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Spot Sent", color = Color(0xFFD8B4FE), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Spot Recv", color = Color(0xFFA7F3D0), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
                Text("Spot Bal", color = Color(0xFFFECDD3), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(75.dp))
              }
            }

            // Data Rows for Remaining Columns
            lines.forEachIndexed { index, line ->
              val isEven = index % 2 == 0
              val dhuVal = line.dhu ?: 0.0
              val effVal = line.todayEfficiencyPct ?: 0.0
              val achVal = line.achievementPct ?: 0.0
              val oqlVal = line.todayOqlPct ?: 0.0
              val dayOutput = line.achievementPcs ?: 0
              val sent = line.defectSent
              val recv = line.defectReceived
              val bal = line.defectBalance
              val sSent = line.spotSent
              val sRecv = line.spotReceived
              val sBal = line.spotBalance

              Surface(
                onClick = { onLineClick(line.lineId) },
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

                  // 13. Spot Sent
                  Text(
                    text = "$sSent",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7E22CE),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
                  )

                  // 14. Spot Recv
                  Text(
                    text = "$sRecv",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D9488),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
                  )

                  // 15. Spot Bal
                  Text(
                    text = "$sBal",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (sBal > 0) Color(0xFFE11D48) else Color(0xFF0D9488),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(75.dp)
                  )
                }
              }
            }
          }
        }
      }

      // 10. Enterprise Developer Credit Footer
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp, bottom = 4.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "This app is developed by Shamim",
            color = Color(0xFF1E88E5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = "IPE and Planning • Northern Fashions Ltd.",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}
