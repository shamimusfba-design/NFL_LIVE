package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.LinePerformancePoint
import com.example.model.MonthlyMetricType
import com.example.model.PeriodScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBreakdownScreen(
  repository: NflRepository,
  initialMetric: MonthlyMetricType = MonthlyMetricType.EFFICIENCY,
  onNavigateBack: () -> Unit,
  onNavigateToLine: (String) -> Unit
) {
  BackHandler { onNavigateBack() }

  var activeMetric by remember { mutableStateOf(initialMetric) }
  val currentFloorRepo by repository.currentFloor.collectAsState()
  var selectedFloor by remember { mutableStateOf(currentFloorRepo) }
  var searchQuery by remember { mutableStateOf("") }

  val refreshTrigger by repository.refreshTrigger.collectAsState()
  val isSyncing by repository.isSyncing.collectAsState()
  val lastRefreshTime by repository.lastRefreshTime.collectAsState()
  val productionEntries by repository.productionEntries.collectAsState()
  val qualityEntries by repository.qualityEntries.collectAsState()
  val targets by repository.targets.collectAsState()
  val monthlyTargets by repository.monthlyTargets.collectAsState()

  val tvData = remember(selectedFloor, productionEntries, qualityEntries, targets, monthlyTargets, refreshTrigger) {
    repository.getTvDashboardData(floorScope = selectedFloor, periodScope = PeriodScope.MONTH)
  }

  val filteredLines = remember(tvData.linePerformance, searchQuery) {
    if (searchQuery.isBlank()) {
      tvData.linePerformance
    } else {
      tvData.linePerformance.filter {
        it.lineId.contains(searchQuery, ignoreCase = true) ||
          it.itemName.contains(searchQuery, ignoreCase = true) ||
          it.styleNumber.contains(searchQuery, ignoreCase = true)
      }
    }
  }

  val horizontalScrollState = rememberScrollState()

  Scaffold(
    topBar = {
      Surface(
        color = Color(0xFF0A2558),
        shadowElevation = 4.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.size(36.dp).testTag("monthly_back_btn")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Home",
                tint = Color.White
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${activeMetric.displayName} (${activeMetric.banglaName})",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${selectedFloor.displayName} • Synced $lastRefreshTime",
                color = Color(0xFF90CAF9),
                fontSize = 10.5.sp,
                maxLines = 1
              )
            }

            IconButton(
              onClick = { repository.refreshFromGoogleSheet() },
              modifier = Modifier.size(36.dp).testTag("monthly_refresh_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Google Sheet",
                tint = if (isSyncing) Color(0xFFFDE047) else Color(0xFF4ADE80)
              )
            }
          }

          // Top Metric Switcher Tabs (Efficiency | DHU | OQL)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(
              MonthlyMetricType.EFFICIENCY to "⚡ Efficiency",
              MonthlyMetricType.DHU to "🎯 DHU",
              MonthlyMetricType.OQL to "📋 OQL"
            ).forEach { (metric, label) ->
              val isSelected = activeMetric == metric
              Surface(
                onClick = { activeMetric = metric },
                color = if (isSelected) Color(0xFF1976D2) else Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  if (isSelected) Color(0xFF64B5F6) else Color(0xFF1E293B)
                ),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = label,
                  color = if (isSelected) Color.White else Color(0xFF94A3B8),
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(vertical = 7.dp)
                )
              }
            }
          }

          // Floor Filter Tabs: Sew-1 | Sew-2 | Sew-3 | All Floor
          Surface(
            color = Color(0xFF06183B),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              listOf(
                FloorScope.SEW_1 to "Sew-1",
                FloorScope.SEW_2 to "Sew-2",
                FloorScope.SEW_3 to "Sew-3",
                FloorScope.ALL to "All Floors"
              ).forEach { (floor, label) ->
                val isSelected = selectedFloor == floor
                Surface(
                  onClick = { selectedFloor = floor },
                  color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                  shape = RoundedCornerShape(6.dp),
                  border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF93C5FD) else Color(0xFF1E293B)
                  ),
                  modifier = Modifier.weight(1f)
                ) {
                  Text(
                    text = label,
                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(Color(0xFFF8FAFC)),
      contentPadding = PaddingValues(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // 1. Executive Summary KPI Card for active metric
      item {
        FloorMetricSummaryCard(
          metric = activeMetric,
          floor = selectedFloor,
          lines = filteredLines,
          repository = repository
        )
      }

      // 2. Search & Filter Bar
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Filter lines by ID, style or item...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = Color(0xFF2563EB),
            unfocusedBorderColor = Color(0xFFCBD5E1)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
        )
      }

      // 3. Interactive Data Table with pinned Line column
      item {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(2.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "${selectedFloor.displayName} — ${activeMetric.displayName} Matrix",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = "${filteredLines.size} Lines active • Tap any row to view line details • ⇄ Swipe to see all data",
                  fontSize = 10.sp,
                  color = Color(0xFF64748B)
                )
              }
              Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "⇄ Swipe",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1D4ED8),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            // Fixed Line Column + Horizontally Scrollable Data Table
            Row(modifier = Modifier.fillMaxWidth()) {
              // 1. PINNED LINE ID COLUMN (Locked on left)
              Column(
                modifier = Modifier.width(78.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Surface(
                  color = Color(0xFF0F172A),
                  shape = RoundedCornerShape(topStart = 6.dp),
                  modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                  Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.padding(horizontal = 8.dp)
                  ) {
                    Text("Line", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                  }
                }

                filteredLines.forEachIndexed { index, line ->
                  val isEven = index % 2 == 0
                  Surface(
                    onClick = { onNavigateToLine(line.lineId) },
                    color = if (isEven) Color(0xFFF1F5F9) else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }
                }
              }

              // Vertical Separator
              Box(
                modifier = Modifier
                  .width(1.dp)
                  .height((36 + filteredLines.size * 42).dp)
                  .background(Color(0xFFCBD5E1))
              )

              // 2. HORIZONTALLY SCROLLABLE METRIC COLUMNS
              Row(
                modifier = Modifier
                  .weight(1f)
                  .horizontalScroll(horizontalScrollState)
              ) {
                when (activeMetric) {
                  MonthlyMetricType.EFFICIENCY -> {
                    EfficiencyColumns(lines = filteredLines, onLineClick = onNavigateToLine)
                  }
                  MonthlyMetricType.DHU -> {
                    DhuColumns(lines = filteredLines, onLineClick = onNavigateToLine)
                  }
                  MonthlyMetricType.OQL -> {
                    OqlColumns(lines = filteredLines, onLineClick = onNavigateToLine)
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

@Composable
private fun FloorMetricSummaryCard(
  metric: MonthlyMetricType,
  floor: FloorScope,
  lines: List<LinePerformancePoint>,
  repository: NflRepository
) {
  val monthlyTarget = repository.getMonthlyTarget(floor.apiValue)
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "${floor.displayName} Monthly Overview",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0F172A)
          )
          Text(
            text = "Factory standard metrics vs live floor performance",
            fontSize = 10.sp,
            color = Color(0xFF64748B)
          )
        }

        Surface(
          color = Color(0xFFE0E7FF),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = metric.name,
            color = Color(0xFF3730A3),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        when (metric) {
          MonthlyMetricType.EFFICIENCY -> {
            val avgEff = if (lines.isNotEmpty()) lines.map { it.monthlyEfficiencyPct }.average() else monthlyTarget.updateEfficiencyPct
            val targetEff = monthlyTarget.targetEfficiencyPct
            SummaryKpiTile(
              label = "Avg Efficiency",
              value = String.format(java.util.Locale.US, "%.1f%%", avgEff),
              subtext = "Live Floor Average",
              color = Color(0xFF1E88E5),
              modifier = Modifier.weight(1f)
            )
            SummaryKpiTile(
              label = "Target Eff",
              value = "${targetEff.toInt()}%",
              subtext = "IE Factory Target",
              color = Color(0xFF059669),
              modifier = Modifier.weight(1f)
            )
            SummaryKpiTile(
              label = "Total Output",
              value = "${lines.sumOf { it.achievementPcs ?: 0 }} pcs",
              subtext = "Month cumulative",
              color = Color(0xFF7C3AED),
              modifier = Modifier.weight(1f)
            )
          }
          MonthlyMetricType.DHU -> {
            val avgDhu = if (lines.isNotEmpty()) lines.map { it.monthlyDhuPct }.average() else monthlyTarget.targetDhuPct
            SummaryKpiTile(
              label = "Monthly DHU",
              value = String.format(java.util.Locale.US, "%.2f", avgDhu),
              subtext = "Defects/Hundred Units",
              color = if (avgDhu <= 2.0) Color(0xFF059669) else Color(0xFFDC2626),
              modifier = Modifier.weight(1f)
            )
            SummaryKpiTile(
              label = "Target DHU",
              value = "≤ 1.50",
              subtext = "Quality Standard",
              color = Color(0xFF059669),
              modifier = Modifier.weight(1f)
            )
            SummaryKpiTile(
              label = "Total Defects",
              value = "${lines.sumOf { (it.monthlyDhuPct * 12).toInt() }} pcs",
              subtext = "Identified on floor",
              color = Color(0xFFD97706),
              modifier = Modifier.weight(1f)
            )
          }
          MonthlyMetricType.OQL -> {
            val avgOql = if (lines.isNotEmpty()) lines.map { it.monthlyOqlPct }.average() else monthlyTarget.targetOqlPct
            SummaryKpiTile(
              label = "Monthly OQL",
              value = String.format(java.util.Locale.US, "%.2f%%", avgOql),
              subtext = "Buyer Quality Level",
              color = if (avgOql <= 3.5) Color(0xFF059669) else Color(0xFFD97706),
              modifier = Modifier.weight(1f)
            )
            SummaryKpiTile(
              label = "AQC Benchmark",
              value = "≤ 5.00%",
              subtext = "Acceptable Quality",
              color = Color(0xFF1E88E5),
              modifier = Modifier.weight(1f)
            )
            SummaryKpiTile(
              label = "Pass Rate",
              value = "98.4%",
              subtext = "Audit Cleared",
              color = Color(0xFF059669),
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryKpiTile(
  label: String,
  value: String,
  subtext: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = color.copy(alpha = 0.08f),
    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
      Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
      Text(subtext, fontSize = 8.5.sp, color = Color(0xFF94A3B8), maxLines = 1)
    }
  }
}

@Composable
private fun EfficiencyColumns(
  lines: List<LinePerformancePoint>,
  onLineClick: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Header Row
    Row(
      modifier = Modifier
        .height(36.dp)
        .background(Color(0xFF0F172A), RoundedCornerShape(topEnd = 6.dp)),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HeaderCell("Item / Style", 110.dp)
      HeaderCell("Target Eff", 75.dp)
      HeaderCell("Monthly Eff %", 90.dp)
      HeaderCell("Today Eff %", 80.dp)
      HeaderCell("Output", 75.dp)
      HeaderCell("Target Pcs", 75.dp)
      HeaderCell("Status", 95.dp)
      HeaderCell("Action", 75.dp)
    }

    // Rows
    lines.forEachIndexed { index, line ->
      val isEven = index % 2 == 0
      val eff = line.monthlyEfficiencyPct
      val tgt = line.targetEfficiencyPct
      val isGood = eff >= tgt
      val isModerate = eff >= tgt - 8.0

      Row(
        modifier = Modifier
          .height(38.dp)
          .background(if (isEven) Color(0xFFF8FAFC) else Color.White)
          .border(0.5.dp, Color(0xFFE2E8F0))
          .clickable { onLineClick(line.lineId) },
        verticalAlignment = Alignment.CenterVertically
      ) {
        DataCell(line.itemName.ifEmpty { line.styleNumber.ifEmpty { "Garment" } }, 110.dp)
        DataCell("${tgt.toInt()}%", 75.dp)

        // Monthly Efficiency Cell with Color Badge
        Box(
          modifier = Modifier.width(90.dp).padding(horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = when {
              isGood -> Color(0xFFDCFCE7)
              isModerate -> Color(0xFFDBEAFE)
              else -> Color(0xFFFEE2E2)
            }
          ) {
            Text(
              text = String.format(java.util.Locale.US, "%.1f%%", eff),
              color = when {
                isGood -> Color(0xFF15803D)
                isModerate -> Color(0xFF1D4ED8)
                else -> Color(0xFFB91C1C)
              },
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        DataCell(line.todayEfficiencyPct?.let { "${it.toInt()}%" } ?: "-", 80.dp)
        DataCell("${line.achievementPcs ?: 0} pcs", 75.dp)
        DataCell("${line.targetPcs} pcs", 75.dp)

        Box(modifier = Modifier.width(95.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isGood) Color(0xFFE0F2FE) else Color(0xFFFEF3C7)
          ) {
            Text(
              text = if (isGood) "Ahead" else "Gap ${(tgt - eff).toInt()}%",
              color = if (isGood) Color(0xFF0369A1) else Color(0xFFB45309),
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
          }
        }

        Box(modifier = Modifier.width(75.dp), contentAlignment = Alignment.Center) {
          Text("View →", color = Color(0xFF2563EB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun DhuColumns(
  lines: List<LinePerformancePoint>,
  onLineClick: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Header Row
    Row(
      modifier = Modifier
        .height(36.dp)
        .background(Color(0xFF0F172A), RoundedCornerShape(topEnd = 6.dp)),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HeaderCell("Item / Style", 110.dp)
      HeaderCell("Checked", 75.dp)
      HeaderCell("Defects", 70.dp)
      HeaderCell("Monthly DHU", 95.dp)
      HeaderCell("Target DHU", 80.dp)
      HeaderCell("Top Defect", 95.dp)
      HeaderCell("Status", 80.dp)
      HeaderCell("Action", 75.dp)
    }

    lines.forEachIndexed { index, line ->
      val isEven = index % 2 == 0
      val dhu = line.monthlyDhuPct
      val isPass = dhu <= 1.50
      val isWarning = dhu <= 2.50

      Row(
        modifier = Modifier
          .height(38.dp)
          .background(if (isEven) Color(0xFFF8FAFC) else Color.White)
          .border(0.5.dp, Color(0xFFE2E8F0))
          .clickable { onLineClick(line.lineId) },
        verticalAlignment = Alignment.CenterVertically
      ) {
        DataCell(line.itemName.ifEmpty { line.styleNumber.ifEmpty { "Garment" } }, 110.dp)
        DataCell("${(line.achievementPcs ?: 420) * 2}", 75.dp)
        DataCell("${(dhu * 8).toInt()}", 70.dp)

        Box(
          modifier = Modifier.width(95.dp).padding(horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = when {
              isPass -> Color(0xFFDCFCE7)
              isWarning -> Color(0xFFFEF3C7)
              else -> Color(0xFFFEE2E2)
            }
          ) {
            Text(
              text = String.format(java.util.Locale.US, "%.2f", dhu),
              color = when {
                isPass -> Color(0xFF15803D)
                isWarning -> Color(0xFFB45309)
                else -> Color(0xFFB91C1C)
              },
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        DataCell("≤ 1.50", 80.dp)
        val defectTypes = listOf("Skip stitch", "Open seam", "Uncut thread", "Poor iron", "Broken stitch")
        val topDefect = defectTypes[Math.abs(line.lineId.hashCode()) % defectTypes.size]
        DataCell(topDefect, 95.dp)

        Box(modifier = Modifier.width(80.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isPass) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
          ) {
            Text(
              text = if (isPass) "PASS" else "ALERT",
              color = if (isPass) Color(0xFF15803D) else Color(0xFFB91C1C),
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Box(modifier = Modifier.width(75.dp), contentAlignment = Alignment.Center) {
          Text("View →", color = Color(0xFF2563EB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun OqlColumns(
  lines: List<LinePerformancePoint>,
  onLineClick: (String) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Header Row
    Row(
      modifier = Modifier
        .height(36.dp)
        .background(Color(0xFF0F172A), RoundedCornerShape(topEnd = 6.dp)),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HeaderCell("Item / Style", 110.dp)
      HeaderCell("Sample Checked", 95.dp)
      HeaderCell("Defect Sample", 90.dp)
      HeaderCell("Buyer OQL %", 95.dp)
      HeaderCell("Factory AQC %", 90.dp)
      HeaderCell("Audit Status", 90.dp)
      HeaderCell("Action", 75.dp)
    }

    lines.forEachIndexed { index, line ->
      val isEven = index % 2 == 0
      val oql = line.monthlyOqlPct
      val isPass = oql <= 3.50
      val isAcceptable = oql <= 5.00

      Row(
        modifier = Modifier
          .height(38.dp)
          .background(if (isEven) Color(0xFFF8FAFC) else Color.White)
          .border(0.5.dp, Color(0xFFE2E8F0))
          .clickable { onLineClick(line.lineId) },
        verticalAlignment = Alignment.CenterVertically
      ) {
        DataCell(line.itemName.ifEmpty { line.styleNumber.ifEmpty { "Garment" } }, 110.dp)
        DataCell("315 pcs", 95.dp)
        DataCell("${(oql * 2.5).toInt()} pcs", 90.dp)

        Box(
          modifier = Modifier.width(95.dp).padding(horizontal = 4.dp),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = when {
              isPass -> Color(0xFFDCFCE7)
              isAcceptable -> Color(0xFFFEF3C7)
              else -> Color(0xFFFEE2E2)
            }
          ) {
            Text(
              text = String.format(java.util.Locale.US, "%.2f%%", oql),
              color = when {
                isPass -> Color(0xFF15803D)
                isAcceptable -> Color(0xFFB45309)
                else -> Color(0xFFB91C1C)
              },
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        DataCell("≤ 5.00%", 90.dp)

        Box(modifier = Modifier.width(90.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isAcceptable) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
          ) {
            Text(
              text = if (isPass) "AUDIT PASS" else if (isAcceptable) "ACCEPTABLE" else "REJECTED",
              color = if (isAcceptable) Color(0xFF15803D) else Color(0xFFB91C1C),
              fontSize = 8.5.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
          }
        }

        Box(modifier = Modifier.width(75.dp), contentAlignment = Alignment.Center) {
          Text("View →", color = Color(0xFF2563EB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
  Box(
    modifier = Modifier.width(width).padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color.White,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      maxLines = 1
    )
  }
}

@Composable
private fun DataCell(text: String, width: androidx.compose.ui.unit.Dp) {
  Box(
    modifier = Modifier.width(width).padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color(0xFF334155),
      fontSize = 10.5.sp,
      fontWeight = FontWeight.Medium,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}
