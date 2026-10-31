package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.PeriodScope
import com.example.model.QualityRecord
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityDashboardScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit,
  onNavigateToLineDeepDive: (String) -> Unit
) {
  val qualityEntries by repository.qualityEntries.collectAsStateWithLifecycle()
  val refreshTrigger by repository.refreshTrigger.collectAsStateWithLifecycle()
  var selectedFloor by remember { mutableStateOf(FloorScope.ALL) }
  var selectedPeriod by remember { mutableStateOf(PeriodScope.TODAY) }
  var selectedLineId by remember { mutableStateOf("") }

  val lines = remember(selectedFloor, refreshTrigger) {
    repository.allLinesMaster.filter { repository.isInSelectedFloor(it.floor, selectedFloor) }
  }
  val audits = remember(qualityEntries, selectedPeriod, selectedFloor, refreshTrigger) {
    qualityEntries
      .filter { repository.isBiHourlyAuditSlot(it.hourSlot) && it.checkedGarments > 0 }
      .filter { repository.isDateInPeriod(it.date, selectedPeriod) }
      .filter { repository.isInSelectedFloor(it.floor, selectedFloor) }
      .groupBy { it.submissionId.ifBlank { it.id } }
      .values.mapNotNull { it.maxByOrNull(QualityRecord::timestamp) }
  }
  val selectedLine = remember(lines, selectedLineId) {
    lines.firstOrNull { it.id.equals(selectedLineId, true) } ?: lines.firstOrNull()
  }
  val selectedLineAudits = remember(audits, selectedLine) {
    audits.filter { selectedLine != null && it.lineId.equals(selectedLine.id, true) }
  }
  val selectedLineThreshold = remember(selectedLine, refreshTrigger) {
    selectedLine?.let { repository.getMonthlyTarget(it.floor).targetOqlPct } ?: 3.5
  }
  fun passed(record: QualityRecord, threshold: Double): Boolean {
    if (record.checkedGarments <= 0) return false
    val oql = record.defectiveGarments.toDouble() / record.checkedGarments * 100.0
    return oql <= threshold
  }
  val totalPassed = audits.count { record ->
    val line = lines.firstOrNull { it.id.equals(record.lineId, true) }
    passed(record, line?.let { repository.getMonthlyTarget(it.floor).targetOqlPct } ?: 3.5)
  }
  val totalFailed = audits.size - totalPassed
  val totalChecked = audits.sumOf { it.checkedGarments }
  val passRate = if (audits.isNotEmpty()) totalPassed * 100.0 / audits.size else null
  val hourlyRows = remember(selectedLineAudits, selectedLineThreshold) {
    selectedLineAudits.groupBy { repository.normalizeHourSlot24h(it.hourSlot) }
      .toSortedMap()
      .map { (slot, rows) ->
        val pass = rows.count { passed(it, selectedLineThreshold) }
        Triple(slot, rows.size, pass)
      }
  }
  val topDefects = remember(qualityEntries, selectedPeriod, selectedFloor, refreshTrigger) {
    qualityEntries
      .filter { repository.isDateInPeriod(it.date, selectedPeriod) }
      .filter { repository.isInSelectedFloor(it.floor, selectedFloor) }
      .flatMap { it.defects }
      .filter { it.count > 0 }
      .groupBy { it.defectType }
      .mapValues { (_, items) -> items.sumOf { it.count } }
      .entries.sortedByDescending { it.value }.take(5)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Quality Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Line-wise bi-hourly audit & defect analytics", color = Color(0xFFBFDBFE), fontSize = 10.sp)
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    containerColor = Color(0xFFF1F5F9)
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(FloorScope.ALL, FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.FINISHING).forEach { floor ->
          FilterChip(
            selected = selectedFloor == floor,
            onClick = { selectedFloor = floor; selectedLineId = "" },
            label = { Text(if (floor == FloorScope.ALL) "All" else floor.displayName, fontSize = 10.sp) }
          )
        }
      }
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(PeriodScope.TODAY, PeriodScope.YESTERDAY, PeriodScope.WEEK, PeriodScope.MONTH).forEach { period ->
          FilterChip(
            selected = selectedPeriod == period,
            onClick = { selectedPeriod = period },
            label = { Text(period.displayName, fontSize = 10.sp) }
          )
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QualityMetricCard("Total Audits", audits.size.toString(), Color(0xFF2563EB), Modifier.weight(1f))
        QualityMetricCard("Passed", totalPassed.toString(), Color(0xFF16A34A), Modifier.weight(1f))
        QualityMetricCard("Failed", totalFailed.toString(), Color(0xFFDC2626), Modifier.weight(1f))
        QualityMetricCard("Pass Rate", passRate?.let { String.format(Locale.US, "%.1f%%", it) } ?: "N/A", Color(0xFF7C3AED), Modifier.weight(1f))
      }

      Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF2563EB))
            Text("Line-wise Bi-Hourly Audit", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 14.sp)
          }
          Text("Select a line to inspect its audit history. Pass/fail is evaluated against the floor's saved monthly OQL standard.", color = Color(0xFF64748B), fontSize = 10.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AuditLegend(Color(0xFF2563EB), "Total")
            AuditLegend(Color(0xFF16A34A), "Pass")
            AuditLegend(Color(0xFFDC2626), "Fail")
          }
          val maxAuditCount = lines.maxOfOrNull { line ->
            audits.count { it.lineId.equals(line.id, true) }
          }?.coerceAtLeast(1) ?: 1
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(138.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
          ) {
            lines.forEach { line ->
              val rows = audits.filter { it.lineId.equals(line.id, true) }
              val threshold = repository.getMonthlyTarget(line.floor).targetOqlPct
              val pass = rows.count { passed(it, threshold) }
              val fail = rows.size - pass
              Column(
                modifier = Modifier.width(54.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
              ) {
                Row(
                  modifier = Modifier.weight(1f).fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.Bottom
                ) {
                  AuditColumn(rows.size, maxAuditCount, Color(0xFF2563EB))
                  AuditColumn(pass, maxAuditCount, Color(0xFF16A34A))
                  AuditColumn(fail, maxAuditCount, Color(0xFFDC2626))
                }
                Text(line.id, color = Color(0xFF0F172A), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
              }
            }
          }
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { line ->
              val rows = audits.filter { it.lineId.equals(line.id, true) }
              val threshold = repository.getMonthlyTarget(line.floor).targetOqlPct
              val pass = rows.count { passed(it, threshold) }
              val fail = rows.size - pass
              val selected = selectedLine?.id.equals(line.id, true)
              Surface(
                onClick = { selectedLineId = line.id },
                color = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(104.dp)
              ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text("Line ${line.id}", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 12.sp)
                  Text("Checks: ${rows.size}", color = Color(0xFF475569), fontSize = 10.sp)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("P $pass", color = Color(0xFF15803D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("F $fail", color = Color(0xFFB91C1C), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
          if (audits.isEmpty()) {
            Text("No bi-hourly audit records for this floor and period.", color = Color(0xFF64748B), fontSize = 11.sp)
          } else {
            Text("Total checked pieces: ${totalChecked}", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
          }
        }
      }

      Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Selected Line — Hour-wise Audit", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
            selectedLine?.let {
              TextButton(onClick = { onNavigateToLineDeepDive(it.id) }) { Text("Open Deep Dive →", fontSize = 10.sp) }
            }
          }
          Text(selectedLine?.let { "Line ${it.id} • Pass standard ≤ ${String.format(Locale.US, "%.2f", selectedLineThreshold)}% OQL" } ?: "No line selected", color = Color(0xFF64748B), fontSize = 10.sp)
          if (hourlyRows.isEmpty()) {
            Text("No audit slots recorded for this line in the selected period.", color = Color(0xFF64748B), fontSize = 11.sp)
          } else {
            hourlyRows.forEach { (slot, total, pass) ->
              val fail = total - pass
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(repository.formatHourSlot12h(slot), color = Color(0xFF334155), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                  Text("Total $total  •  Pass $pass  •  Fail $fail", color = Color(0xFF334155), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFEE2E2))) {
                  if (total > 0 && pass > 0) Box(Modifier.fillMaxHeight().weight(pass.toFloat()).background(Color(0xFF16A34A)))
                  if (total > pass) Box(Modifier.fillMaxHeight().weight(fail.toFloat()).background(Color(0xFFDC2626)))
                }
              }
            }
          }
        }
      }

      Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Top 5 Defects", fontWeight = FontWeight.Black, color = Color(0xFF0F172A), fontSize = 13.sp)
          if (topDefects.isEmpty()) {
            Text("No defect records for this floor and period.", color = Color(0xFF64748B), fontSize = 11.sp)
          } else {
            val maxCount = topDefects.maxOf { it.value }.coerceAtLeast(1)
            topDefects.forEachIndexed { index, entry ->
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("${index + 1}. ${entry.key}", color = Color(0xFF334155), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                  Text(entry.value.toString(), color = Color(0xFFB91C1C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF1F5F9))) {
                  Box(Modifier.fillMaxHeight().fillMaxWidth(entry.value.toFloat() / maxCount).background(Color(0xFFDC6B35)))
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
private fun AuditLegend(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
    Text(label, color = Color(0xFF475569), fontSize = 9.sp)
  }
}

@Composable
private fun RowScope.AuditColumn(value: Int, maxValue: Int, color: Color) {
  Box(
    Modifier
      .weight(1f)
      .fillMaxHeight((value.toFloat() / maxValue).coerceIn(0f, 1f))
      .padding(horizontal = 1.dp)
      .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
      .background(if (value > 0) color else Color(0xFFE2E8F0))
  )
}

@Composable
private fun QualityMetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(9.dp)) {
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(label, color = Color(0xFF475569), fontSize = 8.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
      Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
  }
}
