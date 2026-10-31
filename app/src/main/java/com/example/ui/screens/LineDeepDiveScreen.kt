package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.NflRepository
import com.example.model.PeriodScope
import com.example.model.QualityRecord
import com.example.model.ParetoDefectPoint
import com.example.ui.components.NptPieChartCard
import com.example.ui.components.SpeedometerGauge
import com.example.ui.theme.NflRoyalBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDeepDiveScreen(
  repository: NflRepository,
  lineId: String,
  onNavigateBack: () -> Unit
) {
  var selectedPeriod by remember { mutableStateOf(PeriodScope.TODAY) }
  var selectedLineId by remember { mutableStateOf(lineId) }
  var showLineSwitcher by remember { mutableStateOf(false) }
  var selectedQualityChartTab by remember { mutableStateOf("DHU") }

  val refreshTrigger by repository.refreshTrigger.collectAsStateWithLifecycle()
  val isSyncing by repository.isSyncing.collectAsStateWithLifecycle()
  val lastRefreshTime by repository.lastRefreshTime.collectAsStateWithLifecycle()
  val productionEntries by repository.productionEntries.collectAsStateWithLifecycle()
  val qualityEntries by repository.qualityEntries.collectAsStateWithLifecycle()
  val targets by repository.targets.collectAsStateWithLifecycle()
  val monthlyTargets by repository.monthlyTargets.collectAsStateWithLifecycle()
  val incidents by repository.incidents.collectAsStateWithLifecycle()

  val lineSetup = remember(selectedLineId, targets, refreshTrigger) { repository.getLineSetup(selectedLineId) }
  val lineObj = remember(selectedLineId) { repository.allLinesMaster.find { it.id == selectedLineId } }
  val floorName = lineObj?.floor ?: lineSetup.floor
  val floorLinesForDropdown = remember(floorName) { repository.allLinesMaster.filter { it.floor == floorName } }

  val monthlyTarget = remember(floorName, monthlyTargets, refreshTrigger) { repository.getMonthlyTarget(floorName) }
  val lineMonthlyTarget = remember(floorName, selectedLineId, monthlyTargets, refreshTrigger) {
    repository.getMonthlyLineTarget(floorName, selectedLineId) ?: monthlyTarget
  }

  val hourlyProd = remember(selectedLineId, selectedPeriod, productionEntries, targets, refreshTrigger) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    val rows = repository.getHourlyProductionData(selectedLineId, selectedPeriod)
    if (BuildConfig.DEBUG) {
      Log.d("NflPerf", "LineDeepDiveScreen.hourlyProd line=$selectedLineId period=${selectedPeriod.name} rows=${rows.size} took=${SystemClock.elapsedRealtime() - startMs}ms")
    }
    rows
  }

  val hourlyQuality = remember(selectedLineId, selectedPeriod, qualityEntries, refreshTrigger) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    val rows = repository.getHourlyQualityData(selectedLineId, selectedPeriod)
    if (BuildConfig.DEBUG) {
      Log.d("NflPerf", "LineDeepDiveScreen.hourlyQuality line=$selectedLineId period=${selectedPeriod.name} rows=${rows.size} took=${SystemClock.elapsedRealtime() - startMs}ms")
    }
    rows
  }

  val lineQualityRecords = remember(selectedLineId, selectedPeriod, qualityEntries, refreshTrigger) {
    qualityEntries.filter {
      it.lineId.equals(selectedLineId, ignoreCase = true) &&
        repository.isDateInPeriod(it.date, selectedPeriod)
    }.groupBy { "${NflRepository.normalizeHourSlot24h(it.hourSlot)}|${it.date}" }
      .values.mapNotNull { records -> records.maxByOrNull { it.revision } }
  }

  val lineBiHourlyAudits = remember(lineQualityRecords) {
    lineQualityRecords.filter { repository.isBiHourlyAuditSlot(it.hourSlot) }
  }

  val paretoDefects = remember(lineQualityRecords) {
    val counts = lineQualityRecords.flatMap { it.defects }
      .filter { it.count > 0 }
      .groupBy { it.defectType }
      .mapValues { (_, defects) -> defects.sumOf { it.count } }
    val total = counts.values.sum().coerceAtLeast(1)
    var running = 0
    counts.entries.sortedByDescending { it.value }.take(5).map { entry ->
      running += entry.value
      ParetoDefectPoint(
        defectName = entry.key,
        count = entry.value,
        percentage = Math.round(entry.value.toDouble() / total * 1000.0) / 10.0,
        cumulativePercentage = Math.round(running.toDouble() / total * 1000.0) / 10.0
      )
    }
  }

  val linePerformancePoint = remember(selectedLineId, selectedPeriod, productionEntries, qualityEntries, targets, incidents, refreshTrigger) {
    val startMs = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
    val pt = repository.getLinePerformancePoint(selectedLineId, periodScope = selectedPeriod)
    if (BuildConfig.DEBUG) {
      Log.d("NflPerf", "LineDeepDiveScreen.linePerformancePoint line=$selectedLineId period=${selectedPeriod.name} took=${SystemClock.elapsedRealtime() - startMs}ms")
    }
    pt
  }

  val lineHourlyDefects = remember(selectedLineId, selectedPeriod, qualityEntries, refreshTrigger) {
    repository.getLineHourlyDefectBreakdown(selectedLineId, selectedPeriod)
  }

  val lineIssues = remember(selectedLineId, incidents, refreshTrigger) { repository.getLineIssues(selectedLineId) }
  val lineNptBreakdown = remember(selectedLineId, incidents, refreshTrigger) { repository.getLineNptBreakdown(selectedLineId) }

  // Summary Metrics (memoized on hourlyProd, hourlyQuality, lineSetup, lineIssues)
  val totalTarget = remember(hourlyProd, lineSetup.targetDailyPcs) {
    hourlyProd.lastOrNull()?.totalTarget ?: lineSetup.targetDailyPcs
  }
  val totalAchieve = remember(hourlyProd) {
    hourlyProd.lastOrNull()?.totalAchieve ?: 0
  }
  val overallAchievePct = remember(totalTarget, totalAchieve) {
    if (totalTarget > 0) Math.round((totalAchieve.toDouble() / totalTarget) * 1000.0) / 10.0 else 0.0
  }
  val todayEffPct = remember(linePerformancePoint) {
    linePerformancePoint.todayEfficiencyPct?.let { Math.round(it * 10.0) / 10.0 } ?: 0.0
  }
  val avgOee = remember(hourlyProd) {
    hourlyProd.filter { it.oeePct > 0 }.map { it.oeePct }.average().let { if (it.isNaN()) 0.0 else Math.round(it * 10.0) / 10.0 }
  }
  val avgDhu = remember(lineQualityRecords) {
    val hourlyRecords = lineQualityRecords.filterNot { repository.isBiHourlyAuditSlot(it.hourSlot) }
    val checked = hourlyRecords.sumOf { it.checkedGarments }
    if (checked > 0) Math.round(hourlyRecords.sumOf { it.totalDefects }.toDouble() / checked * 10000.0) / 100.0 else 0.0
  }
  val avgOql = remember(lineBiHourlyAudits, lineQualityRecords) {
    val auditSource = lineBiHourlyAudits.ifEmpty { lineQualityRecords }
    val checked = auditSource.sumOf { it.checkedGarments }
    if (checked > 0) Math.round(auditSource.sumOf { it.defectiveGarments }.toDouble() / checked * 10000.0) / 100.0 else 0.0
  }
  val totalNptMins = remember(lineIssues) { lineIssues.sumOf { it.durationMins } }
  val totalLostPcs = remember(lineIssues) { lineIssues.sumOf { it.outputImpactPcs } }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
          Column {
            Text(
              text = "Line $selectedLineId — Deep-Dive",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text(
              text = "$floorName • ${lineSetup.itemName} (SMV: ${String.format("%.2f", lineSetup.smv)}) • Synced $lastRefreshTime",
              color = Color(0xFFBAE6FD),
              fontSize = 10.sp
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("deep_dive_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        actions = {
          Box {
            TextButton(onClick = { showLineSwitcher = true }) {
              Text("Switch Line", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            DropdownMenu(expanded = showLineSwitcher, onDismissRequest = { showLineSwitcher = false }) {
              floorLinesForDropdown.forEach { line ->
                DropdownMenuItem(
                  text = { Text("Line ${line.id}") },
                  onClick = {
                    selectedLineId = line.id
                    showLineSwitcher = false
                  }
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    containerColor = Color(0xFFF8FAFC)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. Time Horizon Filter Chips (Today, Yesterday, Weekly, Monthly)
      item(key = "time_horizon_chips") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf("Today", "Yesterday", "Weekly", "Monthly").forEach { periodLabel ->
          val isSelected = when (periodLabel) {
            "Today" -> selectedPeriod == PeriodScope.TODAY
            "Yesterday" -> selectedPeriod == PeriodScope.YESTERDAY
            "Weekly" -> selectedPeriod == PeriodScope.WEEK
            "Monthly" -> selectedPeriod == PeriodScope.MONTH
            else -> false
          }
          Surface(
            onClick = {
              selectedPeriod = when (periodLabel) {
                "Yesterday" -> PeriodScope.YESTERDAY
                "Weekly" -> PeriodScope.WEEK
                "Monthly" -> PeriodScope.MONTH
                else -> PeriodScope.TODAY
              }
            },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF0F172A) else Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = periodLabel,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) Color.White else Color(0xFF334155),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 8.dp)
            )
          }
        }
      }
      }

      // ==========================================
      // SECTION A: PRODUCTION PERFORMANCE & HOURLY
      // ==========================================
      item(key = "section_a_production") {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Section Title
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF0284C7))
              Text("Line $selectedLineId — Production & Efficiency", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F172A))
            }
            Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(4.dp)) {
              Text("Item: ${lineSetup.itemName}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
          }

          // 6 Core Metrics Requested: Today Eff, Up to Date Eff, Hourly Target vs Achieve, Hourly OEE, Today DHU, Up to Date DHU
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: Efficiency & Production Output
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              KpiCard(
                title = if (selectedPeriod == PeriodScope.YESTERDAY) "Yesterday Eff" else "Today Efficiency",
                value = "${todayEffPct}%",
                subtext = if (selectedPeriod == PeriodScope.YESTERDAY) "Recorded Final" else "Target: ${lineSetup.targetEfficiencyPct.toInt()}%",
                valueColor = if (todayEffPct >= lineSetup.targetEfficiencyPct) Color(0xFF16A34A) else Color(0xFFD97706),
                modifier = Modifier.weight(1f)
              )
              KpiCard(
                title = "Monthly Efficiency",
                value = "${lineMonthlyTarget.updateEfficiencyPct}%",
                subtext = "MTD Average",
                valueColor = Color(0xFF2563EB),
                modifier = Modifier.weight(1f)
              )
              KpiCard(
                title = "Line $selectedLineId Tgt/Ach",
                value = "$totalAchieve / $totalTarget",
                subtext = "Ratio: ${overallAchievePct}%",
                valueColor = if (overallAchievePct >= 85) Color(0xFF16A34A) else Color(0xFF0F172A),
                modifier = Modifier.weight(1f)
              )
            }

            // Row 2: Quality & OEE
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              KpiCard(
                title = "Hourly OEE",
                value = "${avgOee}%",
                subtext = "Equip. Effectiveness",
                valueColor = Color(0xFF0284C7),
                modifier = Modifier.weight(1f)
              )
              KpiCard(
                title = if (selectedPeriod == PeriodScope.YESTERDAY) "Yesterday DHU" else "Today DHU",
                value = String.format(java.util.Locale.US, "%.2f", avgDhu),
                subtext = if (selectedPeriod == PeriodScope.YESTERDAY) "Final QC Rate" else "Endline QC (DHU)",
                valueColor = if (avgDhu <= 2.0) Color(0xFF16A34A) else Color(0xFFDC2626),
                modifier = Modifier.weight(1f)
              )
              KpiCard(
                title = "Up to Date DHU",
                value = String.format(java.util.Locale.US, "%.2f", monthlyTarget.targetDhuPct),
                subtext = "MTD Benchmark",
                valueColor = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f)
              )
            }
          }

          // Line Efficiency Speedometer Dial
          SpeedometerGauge(
            value = todayEffPct,
            targetValue = lineSetup.targetEfficiencyPct,
            modifier = Modifier.fillMaxWidth()
          )

          // Column / Bar Chart: Hourly Target vs Output
          Text("Line $selectedLineId — Hourly Target vs Output (Pieces)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
          HourlyTargetVsOutputChart(hourlyProd)
        }
      }
      }

      // ==========================================
      // SECTION B: QUALITY & INSPECTION ANALYTICS
      // ==========================================
      item(key = "section_b_quality") {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Section Title
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF16A34A))
              Text("Quality & Defect Intelligence", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F172A))
            }
            Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
              Text("QA Audited", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
          }

          // Source Attribution Notes (Requested by user)
          Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("📌 DHU data sourced from Endline Quality Inspector inspection.", fontSize = 10.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
              Text("📌 OQL data sourced from Auditor inspections (conducted bi-hourly).", fontSize = 10.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
            }
          }

          // Quality KPI Cards Row (Today DHU, Today OQL, BS OQL, Monthly DHU, Monthly OQL)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(
              title = "Today DHU",
              value = String.format(java.util.Locale.US, "%.2f", avgDhu),
              subtext = "Endline QC (DHU)",
              valueColor = if (avgDhu <= 2.0) Color(0xFF16A34A) else Color(0xFFDC2626),
              modifier = Modifier.weight(1f)
            )
            KpiCard(
              title = "Today OQL",
              value = "${avgOql}%",
              subtext = "Bi-hourly Audit",
              valueColor = if (avgOql <= 3.5) Color(0xFF16A34A) else Color(0xFFD97706),
              modifier = Modifier.weight(1f)
            )
            KpiCard(
              title = "BS OQL",
              value = "${String.format(java.util.Locale.US, "%.2f", linePerformancePoint.bsOqlPct)}%",
              subtext = "IE Benchmark (≤ 2.5%)",
              valueColor = Color(0xFF0284C7),
              modifier = Modifier.weight(1f)
            )
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCard(
              title = "Monthly DHU",
              value = String.format(java.util.Locale.US, "%.2f", monthlyTarget.targetDhuPct),
              subtext = "Benchmark Standard",
              valueColor = Color(0xFF2563EB),
              modifier = Modifier.weight(1f)
            )
            KpiCard(
              title = "Monthly OQL",
              value = "${monthlyTarget.targetOqlPct}%",
              subtext = "AQL 2.5 Standard",
              valueColor = Color(0xFF475569),
              modifier = Modifier.weight(1f)
            )
          }

          // Top 5 Defects Solid Pie Chart for this Line (Requested order: right below production section)
          Text("Line Top 5 Defects — ${selectedPeriod.displayName} (Endline QC)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
          ParetoDefectPieChart(paretoDefects)

          // Bi-hourly audit pass/fail analytics for this line and selected period.
          BiHourlyAuditPerformanceCard(
            lineId = selectedLineId,
            records = lineBiHourlyAudits
          )

          // Sliding Selector: Hourly DHU vs Hourly OQL Column Charts
          Text("Hourly Quality Trend Analysis (Column Charts):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
              .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Surface(
              onClick = { selectedQualityChartTab = "DHU" },
              shape = RoundedCornerShape(6.dp),
              color = if (selectedQualityChartTab == "DHU") Color(0xFF0F172A) else Color.Transparent,
              modifier = Modifier.weight(1f)
            ) {
              Text(
                text = "📊 Hourly DHU (Column)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedQualityChartTab == "DHU") Color.White else Color(0xFF475569),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 7.dp)
              )
            }

            Surface(
              onClick = { selectedQualityChartTab = "OQL" },
              shape = RoundedCornerShape(6.dp),
              color = if (selectedQualityChartTab == "OQL") Color(0xFF0F172A) else Color.Transparent,
              modifier = Modifier.weight(1f)
            ) {
              Text(
                text = "📈 Hourly OQL (Column)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedQualityChartTab == "OQL") Color.White else Color(0xFF475569),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 7.dp)
              )
            }
          }

          // Sliding Display of the selected column chart
          if (selectedQualityChartTab == "DHU") {
            HourVsDhuColumnChart(hourlyQuality)
          } else {
            HourVsOqlColumnChart(hourlyQuality)
          }
        }
      }
      }

      // =========================================================================
      // LINE-WISE DUAL DEFECT RECTIFICATION INTELLIGENCE (SEWING & SPOT DEFECTS)
      // Requested by user: Line-wise Sewing Defect & Spot Defect Sent, Recv, Balance
      // =========================================================================
      item(key = "dual_defect_tracking") {
        DualDefectTrackingCard(
          sewingSent = linePerformancePoint.defectSent,
          sewingReceived = linePerformancePoint.defectReceived,
          sewingBalance = linePerformancePoint.defectBalance,
          spotSent = linePerformancePoint.spotSent,
          spotReceived = linePerformancePoint.spotReceived,
          spotBalance = linePerformancePoint.spotBalance,
          floorName = "Line $selectedLineId ($floorName)"
        )
      }

      // Line-wise Hourly Alter & Spot Flow Detail Breakdown Table
      item(key = "hourly_defect_table") {
        LineHourlyDefectTableCard(
          lineId = selectedLineId,
          hourlyRecords = lineHourlyDefects
        )
      }

      // ==========================================
      // SECTION C: TODAY'S LINE ISSUES & NPT SUMMARY
      // ==========================================
      item(key = "section_c_npt") {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
              Text("Today's Line Issues & NPT Downtime Summary", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF0F172A))
            }
            Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
              Text("NPT Analysis", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
          }

          Text(
            text = "Point-by-point root causes for downtime, line balancing disparity, and output loss on Line $selectedLineId today:",
            fontSize = 10.sp,
            color = Color(0xFF64748B)
          )

          // Net Impact Banner
          Surface(
            color = Color(0xFFFFF7ED),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFED7AA)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text("Total Non-Productive Time (NPT)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412))
                Text("$totalNptMins Minutes Lost", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFC2410C))
              }
              Surface(color = Color(0xFFEA580C), shape = RoundedCornerShape(6.dp)) {
                Text("-$totalLostPcs pcs Output Impact", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
              }
            }
          }

          // Line NPT Category Distribution Pie Chart
          NptPieChartCard(
            title = "Line $selectedLineId — NPT Downtime Pie Chart",
            subtitle = "Root causes for non-productive hours on this line",
            breakdown = lineNptBreakdown,
            totalLostPcs = totalLostPcs
          )

          // Point-by-point Issue Cards
          lineIssues.forEachIndexed { index, issue ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFF8FAFC),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                      color = when (issue.category) {
                        "Machine Breakdown" -> Color(0xFFFEE2E2)
                        "Line Balancing" -> Color(0xFFFEF3C7)
                        "Input Material N/A" -> Color(0xFFE0F2FE)
                        else -> Color(0xFFF3E8FF)
                      },
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        text = "${index + 1}. ${issue.category}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = when (issue.category) {
                          "Machine Breakdown" -> Color(0xFFDC2626)
                          "Line Balancing" -> Color(0xFFB45309)
                          "Input Material N/A" -> Color(0xFF0369A1)
                          else -> Color(0xFF7E22CE)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                    Text(issue.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                  }

                  Surface(
                    color = Color(0xFFDC2626).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = "⏱️ ${issue.durationMins}m NPT",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFFDC2626),
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                  }
                }

                // Root cause
                Text(
                  text = "📌 Root Cause: ${issue.cause}",
                  fontSize = 10.sp,
                  color = Color(0xFF334155),
                  fontWeight = FontWeight.Medium
                )

                // Impact and Action
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "📉 Output Gap: ~${issue.outputImpactPcs} pcs",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC2410C)
                  )
                  Text(
                    text = "✅ ${issue.actionTaken}",
                    fontSize = 9.sp,
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

private data class LineAuditSlotSummary(
  val slot: String,
  val audits: Int,
  val passed: Int,
  val failed: Int,
  val checked: Int,
  val passedPcs: Int,
  val failedPcs: Int
)

@Composable
private fun BiHourlyAuditPerformanceCard(lineId: String, records: List<QualityRecord>) {
  val summaries = remember(records) {
    records.groupBy { NflRepository.normalizeHourSlot24h(it.hourSlot) }
      .map { (slot, slotRecords) ->
        val passed = slotRecords.count { record ->
          record.checkedGarments > 0 &&
            record.defectiveGarments.toDouble() * 100.0 / record.checkedGarments <= 3.5
        }
        val failed = slotRecords.size - passed
        LineAuditSlotSummary(
          slot = slot,
          audits = slotRecords.size,
          passed = passed,
          failed = failed,
          checked = slotRecords.sumOf { it.checkedGarments },
          passedPcs = slotRecords.sumOf { (it.checkedGarments - it.defectiveGarments).coerceAtLeast(0) },
          failedPcs = slotRecords.sumOf { it.defectiveGarments }
        )
      }.sortedBy { NflRepository.parseSlotHours24(it.slot)?.first ?: Int.MAX_VALUE }
  }
  val totalAudits = summaries.sumOf { it.audits }
  val totalPassed = summaries.sumOf { it.passed }
  val totalFailed = summaries.sumOf { it.failed }
  val totalChecked = summaries.sumOf { it.checked }
  val totalPassPcs = summaries.sumOf { it.passedPcs }
  val totalFailPcs = summaries.sumOf { it.failedPcs }
  val passRate = if (totalAudits > 0) totalPassed * 100.0 / totalAudits else 0.0

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      Text("Line $lineId — Bi-Hourly Audit Pass / Fail", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
      Text("Audit result is classified using the existing OQL acceptance limit of 3.50%.", fontSize = 9.sp, color = Color(0xFF64748B))
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KpiCard("Total Audits", totalAudits.toString(), "Saved audit records", Color(0xFF1D4ED8), Modifier.weight(1f))
        KpiCard("Passed", totalPassed.toString(), "≤ 3.50% OQL", Color(0xFF15803D), Modifier.weight(1f))
        KpiCard("Failed", totalFailed.toString(), "> 3.50% OQL", Color(0xFFDC2626), Modifier.weight(1f))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KpiCard("Pass Rate", String.format(java.util.Locale.US, "%.1f%%", passRate), "Audit pass rate", Color(0xFF0F766E), Modifier.weight(1f))
        KpiCard("Checked pcs", totalChecked.toString(), "Inspected garments", Color(0xFF334155), Modifier.weight(1f))
        KpiCard("Pass / Fail pcs", "$totalPassPcs / $totalFailPcs", "Garment quantity", Color(0xFF7C3AED), Modifier.weight(1f))
      }
      if (summaries.isEmpty()) {
        Surface(Modifier.fillMaxWidth(), color = Color(0xFFF8FAFC), shape = RoundedCornerShape(7.dp)) {
          Text("No bi-hourly audit records are available for this line and period.", modifier = Modifier.padding(12.dp), color = Color(0xFF64748B), fontSize = 10.sp)
        }
      } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
          val maxAudits = summaries.maxOf { it.audits }.coerceAtLeast(1)
          summaries.forEach { row ->
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text("P ${row.passed} / F ${row.failed}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
              Row(Modifier.height(78.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.width(18.dp).height((row.passed.toFloat() / maxAudits * 64f).coerceAtLeast(if (row.passed > 0) 3f else 0f).dp).background(Color(0xFF16A34A), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                Box(Modifier.width(18.dp).height((row.failed.toFloat() / maxAudits * 64f).coerceAtLeast(if (row.failed > 0) 3f else 0f).dp).background(Color(0xFFDC2626), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
              }
              Text(NflRepository.formatHourSlot12h(row.slot), fontSize = 7.5.sp, color = Color(0xFF475569), textAlign = TextAlign.Center, maxLines = 2)
            }
          }
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
          Row(Modifier.background(Color(0xFF0F172A), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text("Audit Slot", Modifier.width(112.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Audits", Modifier.width(48.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Pass", Modifier.width(42.dp), color = Color(0xFF86EFAC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Fail", Modifier.width(42.dp), color = Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Checked", Modifier.width(58.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Pass pcs", Modifier.width(60.dp), color = Color(0xFF86EFAC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Fail pcs", Modifier.width(58.dp), color = Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
          }
          summaries.forEachIndexed { index, row ->
            Row(Modifier.background(if (index % 2 == 0) Color.White else Color(0xFFF8FAFC)).padding(horizontal = 7.dp, vertical = 6.dp)) {
              Text(NflRepository.formatHourSlot12h(row.slot), Modifier.width(112.dp), color = Color(0xFF334155), fontSize = 9.sp)
              Text(row.audits.toString(), Modifier.width(48.dp), color = Color(0xFF334155), fontSize = 9.sp)
              Text(row.passed.toString(), Modifier.width(42.dp), color = Color(0xFF15803D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
              Text(row.failed.toString(), Modifier.width(42.dp), color = Color(0xFFDC2626), fontSize = 9.sp, fontWeight = FontWeight.Bold)
              Text(row.checked.toString(), Modifier.width(58.dp), color = Color(0xFF334155), fontSize = 9.sp)
              Text(row.passedPcs.toString(), Modifier.width(60.dp), color = Color(0xFF15803D), fontSize = 9.sp)
              Text(row.failedPcs.toString(), Modifier.width(58.dp), color = Color(0xFFDC2626), fontSize = 9.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun KpiCard(
  title: String,
  value: String,
  subtext: String,
  valueColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Color(0xFFF8FAFC),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
      Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = valueColor)
      Text(subtext, fontSize = 8.sp, color = Color(0xFF94A3B8))
    }
  }
}

// Chart 1: Hourly Target vs Output Column Chart with explicit Data Labels
@Composable
private fun HourlyTargetVsOutputChart(rows: List<com.example.model.HourlyProductionRow>) {
  val maxVal = rows.maxOfOrNull { maxOf(it.hourlyTarget, it.achieveOutput) }?.coerceAtLeast(1) ?: 100

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Hourly Target vs Achieved (pcs)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF94A3B8), RoundedCornerShape(2.dp)))
            Text("Target", fontSize = 9.sp, color = Color(0xFF64748B))
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF16A34A), RoundedCornerShape(2.dp)))
            Text("Output", fontSize = 9.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
          }
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(92.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        rows.take(8).forEach { r ->
          val tgtFrac = (r.hourlyTarget.toFloat() / maxVal).coerceIn(0.12f, 1.0f)
          val achFrac = (r.achieveOutput.toFloat() / maxVal).coerceIn(0.04f, 1.0f)

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.weight(1f)
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalAlignment = Alignment.Bottom
            ) {
              // Target bar with data label
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "${r.hourlyTarget}",
                  fontSize = 7.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF94A3B8)
                )
                Box(
                  modifier = Modifier
                    .width(8.dp)
                    .height((tgtFrac * 60).dp)
                    .background(Color(0xFFCBD5E1), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                )
              }

              // Actual Output bar with data label
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = if (r.achieveOutput > 0) "${r.achieveOutput}" else "-",
                  fontSize = 8.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (r.achieveOutput >= r.hourlyTarget) Color(0xFF16A34A) else Color(0xFF0284C7)
                )
                Box(
                  modifier = Modifier
                    .width(10.dp)
                    .height((achFrac * 60).dp)
                    .background(
                      if (r.achieveOutput >= r.hourlyTarget) Color(0xFF16A34A) else Color(0xFF0284C7),
                      RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                    )
                )
              }
            }
            Text(
              text = NflRepository.formatHourShort12h(r.hourSlot),
              fontSize = 8.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF64748B),
              modifier = Modifier.padding(top = 2.dp)
            )
          }
        }
      }
    }
  }
}

// Chart 2: Hour vs DHU % Column Chart
@Composable
private fun HourVsDhuColumnChart(rows: List<com.example.model.HourlyQualityRow>) {
  val maxDhu = 3.5f

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Standard DHU Benchmark: ≤2.00", fontSize = 9.sp, color = Color(0xFF94A3B8))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
          Text("Endline DHU", fontSize = 9.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(88.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        rows.take(8).forEach { r ->
          val dhuFrac = (r.dhuPct.toFloat() / maxDhu).coerceIn(0.12f, 1.0f)
          val isHigh = r.dhuPct > 2.0
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.weight(1f)
          ) {
            // Explicit Numeric DATA LABEL (Without % symbol)
            Text(
              text = String.format(java.util.Locale.US, "%.2f", r.dhuPct),
              fontSize = 8.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (isHigh) Color(0xFFDC2626) else Color(0xFF16A34A)
            )
            Box(
              modifier = Modifier
                .width(14.dp)
                .height((dhuFrac * 60).dp)
                .background(
                  if (isHigh) Color(0xFFEF4444) else Color(0xFF10B981),
                  RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                )
            )
            Text(
              text = NflRepository.formatHourShort12h(r.hourSlot),
              fontSize = 8.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF64748B),
              modifier = Modifier.padding(top = 2.dp)
            )
          }
        }
      }
    }
  }
}

// Chart 3: Hour vs OQL % Column Chart
@Composable
private fun HourVsOqlColumnChart(rows: List<com.example.model.HourlyQualityRow>) {
  val maxOql = 5.0f

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Standard Auditor OQL Benchmark: ≤3.50%", fontSize = 9.sp, color = Color(0xFF94A3B8))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(8.dp).background(Color(0xFF2563EB), RoundedCornerShape(2.dp)))
          Text("Auditor OQL %", fontSize = 9.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(88.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        rows.take(8).forEach { r ->
          val oqlFrac = (r.oqlPct.toFloat() / maxOql).coerceIn(0.12f, 1.0f)
          val isHigh = r.oqlPct > 3.5
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.weight(1f)
          ) {
            // Explicit Numeric DATA LABEL
            Text(
              text = "${r.oqlPct}%",
              fontSize = 8.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (isHigh) Color(0xFFDC2626) else Color(0xFF2563EB)
            )
            Box(
              modifier = Modifier
                .width(14.dp)
                .height((oqlFrac * 60).dp)
                .background(
                  if (isHigh) Color(0xFFEA580C) else Color(0xFF0284C7),
                  RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                )
            )
            Text(
              text = NflRepository.formatHourShort12h(r.hourSlot),
              fontSize = 8.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF64748B),
              modifier = Modifier.padding(top = 2.dp)
            )
          }
        }
      }
    }
  }
}

// Chart 4: Pareto Solid Pie Chart for Top 5 Defects with explicit Data Labels
@Composable
private fun ParetoDefectPieChart(defects: List<com.example.model.ParetoDefectPoint>) {
  val sliceColors = listOf(
    Color(0xFFDC2626), // Puckering - Red
    Color(0xFFEA580C), // Broken Stitch - Orange
    Color(0xFFD97706), // Skip Stitch - Amber
    Color(0xFF2563EB), // Uneven Seam - Royal Blue
    Color(0xFF7C3AED), // Needle Cut - Purple
    Color(0xFF64748B)  // Others
  )

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Solid Pie Chart Canvas
      Box(
        modifier = Modifier.size(115.dp),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.size(105.dp)) {
          var startAngle = -90f
          defects.forEachIndexed { idx, p ->
            val sweep = (p.percentage.toFloat() / 100f) * 360f
            val color = sliceColors[idx % sliceColors.size]
            drawArc(
              color = color,
              startAngle = startAngle,
              sweepAngle = sweep,
              useCenter = true, // Solid Pie Chart
              size = size
            )
            startAngle += sweep
          }
        }
      }

      // Legend with Explicit Data Labels (% and count)
      Column(
        modifier = Modifier.weight(1f).padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        defects.forEachIndexed { idx, p ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
              Box(modifier = Modifier.size(7.dp).background(sliceColors[idx % sliceColors.size], CircleShape))
              Text(p.defectName, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            }
            Text("${p.count} (${p.percentage}%)", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = sliceColors[idx % sliceColors.size])
          }
        }
      }
    }
  }
}

@Composable
private fun LineHourlyDefectTableCard(
  lineId: String,
  hourlyRecords: List<com.example.model.LineHourlyDefectRecord>
) {
  var isExpanded by remember { mutableStateOf(true) }
  val (totalSewSent, totalSewRecv, totalSewBal) = remember(hourlyRecords) {
    Triple(
      hourlyRecords.sumOf { it.sewingSent },
      hourlyRecords.sumOf { it.sewingReceived },
      hourlyRecords.sumOf { it.sewingBalance }
    )
  }
  val (totalSpotSent, totalSpotRecv, totalSpotBal) = remember(hourlyRecords) {
    Triple(
      hourlyRecords.sumOf { it.spotSent },
      hourlyRecords.sumOf { it.spotReceived },
      hourlyRecords.sumOf { it.spotBalance }
    )
  }
  val totalPending = totalSewBal + totalSpotBal

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
          Column {
            Text(
              text = "Line $lineId — Hourly Alter & Spot Flow",
              fontWeight = FontWeight.Black,
              fontSize = 13.sp,
              color = Color(0xFF0F172A)
            )
            Text(
              text = "Hourly Sewing & Spot Sent / Received / Balance Summary",
              fontSize = 10.sp,
              color = Color(0xFF64748B)
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Surface(
            color = if (totalPending > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (totalPending > 0) Color(0xFFFECACA) else Color(0xFFBBF7D0))
          ) {
            Text(
              text = if (totalPending > 0) "Pending: $totalPending pcs" else "All Clear ✓",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (totalPending > 0) Color(0xFFDC2626) else Color(0xFF166534),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = if (isExpanded) "Collapse" else "Expand",
              tint = Color(0xFF64748B)
            )
          }
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val scrollState = rememberScrollState()
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(scrollState)
          ) {
            // Header Row
            Surface(
              color = Color(0xFF0F172A),
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Hour Slot", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(105.dp))
                Text("Sew Sent", color = Color(0xFFFED7AA), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Sew Recv", color = Color(0xFFBBF7D0), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Sew Bal", color = Color(0xFFFECDD3), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Spot Sent", color = Color(0xFFE9D5FF), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Spot Recv", color = Color(0xFFA7F3D0), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Spot Bal", color = Color(0xFFFBCFE8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("Status", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(75.dp))
              }
            }

            // Data Rows
            hourlyRecords.forEachIndexed { index, row ->
              val rowPending = row.sewingBalance + row.spotBalance
              Surface(
                color = if (index % 2 == 0) Color.White else Color(0xFFF8FAFC),
                shape = RoundedCornerShape(4.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(NflRepository.formatHourSlot12h(row.hourSlot), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155), modifier = Modifier.width(105.dp))
                  Text("${row.sewingSent}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                  Text("${row.sewingReceived}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                  Text("${row.sewingBalance}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (row.sewingBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                  Text("${row.spotSent}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9333EA), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                  Text("${row.spotReceived}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                  Text("${row.spotBalance}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (row.spotBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                  Box(modifier = Modifier.width(75.dp), contentAlignment = Alignment.Center) {
                    Surface(
                      color = if (rowPending > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                      shape = RoundedCornerShape(3.dp)
                    ) {
                      Text(
                        text = if (rowPending > 0) "$rowPending Bal" else "Clear ✓",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rowPending > 0) Color(0xFFDC2626) else Color(0xFF16A34A),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                      )
                    }
                  }
                }
              }
            }

            // Total Summary Row
            Surface(
              color = Color(0xFFF1F5F9),
              shape = RoundedCornerShape(6.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("TOTAL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A), modifier = Modifier.width(85.dp))
                Text("$totalSewSent", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color(0xFFEA580C), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("$totalSewRecv", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("$totalSewBal", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = if (totalSewBal > 0) Color(0xFFDC2626) else Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("$totalSpotSent", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF9333EA), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("$totalSpotRecv", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Text("$totalSpotBal", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = if (totalSpotBal > 0) Color(0xFFDC2626) else Color(0xFF16A34A), textAlign = TextAlign.End, modifier = Modifier.width(65.dp))
                Box(modifier = Modifier.width(75.dp), contentAlignment = Alignment.Center) {
                  Text(
                    text = if (totalPending > 0) "$totalPending pcs" else "Cleared",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = if (totalPending > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
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
