package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NflRepository
import com.example.model.*

private const val AUDIT_PASS_LIMIT_PCT = 3.5

private data class QualityAuditSlotSummary(
  val lineId: String,
  val date: String,
  val hourSlot: String,
  val auditCount: Int,
  val passedAudits: Int,
  val failedAudits: Int,
  val checkedPcs: Int,
  val passedPcs: Int,
  val failedPcs: Int
)

private fun isAuditRecordPassed(record: QualityRecord): Boolean {
  if (record.checkedGarments <= 0) return false
  val oql = record.defectiveGarments.toDouble() * 100.0 / record.checkedGarments
  return oql <= AUDIT_PASS_LIMIT_PCT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityDashboardScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit,
  onNavigateToLine: (String) -> Unit
) {
  val currentFloor by repository.currentFloor.collectAsStateWithLifecycle()
  val currentPeriod by repository.currentPeriod.collectAsStateWithLifecycle()
  val qualityEntries by repository.qualityEntries.collectAsStateWithLifecycle()
  val refreshTrigger by repository.refreshTrigger.collectAsStateWithLifecycle()

  val filteredRecords = remember(currentFloor, currentPeriod, qualityEntries, refreshTrigger) {
    qualityEntries.filter { record ->
      repository.isInSelectedFloor(record.floor, currentFloor) &&
        repository.isDateInPeriod(record.date, currentPeriod)
    }.groupBy { "${it.lineId.lowercase()}|${it.date}|${NflRepository.normalizeHourSlot24h(it.hourSlot)}" }
      .values.mapNotNull { records -> records.maxByOrNull { it.revision } }
  }

  val auditRecords = remember(filteredRecords) {
    filteredRecords.filter { repository.isBiHourlyAuditSlot(it.hourSlot) }
  }
  val auditSlots = remember(auditRecords) {
    auditRecords.groupBy { "${it.lineId}|${it.date}|${NflRepository.normalizeHourSlot24h(it.hourSlot)}" }
      .mapNotNull { (_, records) ->
        val latest = records.maxByOrNull { it.revision } ?: return@mapNotNull null
        val passed = isAuditRecordPassed(latest)
        QualityAuditSlotSummary(
          lineId = latest.lineId,
          date = latest.date,
          hourSlot = NflRepository.normalizeHourSlot24h(latest.hourSlot),
          auditCount = 1,
          passedAudits = if (passed) 1 else 0,
          failedAudits = if (passed) 0 else 1,
          checkedPcs = latest.checkedGarments,
          passedPcs = (latest.checkedGarments - latest.defectiveGarments).coerceAtLeast(0),
          failedPcs = latest.defectiveGarments
        )
      }.sortedWith(compareBy<QualityAuditSlotSummary>({ it.lineId }, { it.date }, { NflRepository.parseSlotHours24(it.hourSlot)?.first ?: 0 }))
  }

  val totalAudits = auditSlots.sumOf { it.auditCount }
  val passedAudits = auditSlots.sumOf { it.passedAudits }
  val failedAudits = auditSlots.sumOf { it.failedAudits }
  val checkedPcs = auditSlots.sumOf { it.checkedPcs }
  val passedPcs = auditSlots.sumOf { it.passedPcs }
  val failedPcs = auditSlots.sumOf { it.failedPcs }
  val passRate = if (totalAudits > 0) passedAudits * 100.0 / totalAudits else 0.0

  val lineAuditSummaries = remember(auditSlots) {
    auditSlots.groupBy { it.lineId }.map { (lineId, rows) ->
      Triple(lineId, rows.sumOf { it.passedAudits }, rows.sumOf { it.failedAudits })
    }.sortedBy { it.first }
  }

  val lineDhu = remember(filteredRecords) {
    filteredRecords.filterNot { repository.isBiHourlyAuditSlot(it.hourSlot) }
      .groupBy { it.lineId }
      .mapNotNull { (lineId, rows) ->
        val checked = rows.sumOf { it.checkedGarments }
        if (checked <= 0) null else lineId to (rows.sumOf { it.totalDefects } * 100.0 / checked)
      }.sortedByDescending { it.second }
  }

  val topDefects = remember(filteredRecords) {
    val counts = filteredRecords.flatMap { it.defects }
      .filter { it.count > 0 }
      .groupBy { it.defectType }
      .mapValues { (_, defects) -> defects.sumOf { it.count } }
    val total = counts.values.sum().coerceAtLeast(1)
    counts.entries.sortedByDescending { it.value }.take(5).map { Triple(it.key, it.value, it.value * 100.0 / total) }
  }

  val auditTopDefects = remember(auditRecords) {
    val counts = auditRecords.flatMap { it.defects }
      .filter { it.count > 0 }
      .groupBy { it.defectType }
      .mapValues { (_, defects) -> defects.sumOf { it.count } }
    val total = counts.values.sum().coerceAtLeast(1)
    counts.entries.sortedByDescending { it.value }.take(5).map { Triple(it.key, it.value, it.value * 100.0 / total) }
  }

  val dashboardData = remember(currentFloor, currentPeriod, filteredRecords, refreshTrigger) {
    repository.getTvDashboardData(currentFloor, currentPeriod)
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Quality Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("${currentFloor.displayName} • ${currentPeriod.displayName} • Live quality analytics", color = Color(0xFFBFDBFE), fontSize = 10.sp)
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A2558))
      )
    },
    containerColor = Color(0xFFF4F6F9)
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            FloorScope.values().forEach { floor ->
              FilterChip(
                selected = currentFloor == floor,
                onClick = { repository.setFloor(floor) },
                label = { Text(if (floor == FloorScope.FINISHING) "Finish" else floor.displayName, fontSize = 9.sp) },
                modifier = Modifier.weight(1f)
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(PeriodScope.TODAY, PeriodScope.YESTERDAY, PeriodScope.WEEK, PeriodScope.MONTH).forEach { period ->
              FilterChip(
                selected = currentPeriod == period,
                onClick = { repository.setPeriod(period) },
                label = { Text(when (period) {
                  PeriodScope.TODAY -> "Today"
                  PeriodScope.YESTERDAY -> "Yesterday"
                  PeriodScope.WEEK -> "Weekly"
                  PeriodScope.MONTH -> "Monthly"
                }, fontSize = 9.sp) },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Text("Bi-Hourly Audit Summary", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F172A))
          Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QualityMetricCard("Total Audits", totalAudits.toString(), Color(0xFF1D4ED8), Modifier.weight(1f))
            QualityMetricCard("Passed", passedAudits.toString(), Color(0xFF15803D), Modifier.weight(1f))
            QualityMetricCard("Failed", failedAudits.toString(), Color(0xFFDC2626), Modifier.weight(1f))
          }
          Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QualityMetricCard("Pass Rate", "${String.format(java.util.Locale.US, "%.1f", passRate)}%", Color(0xFF0F766E), Modifier.weight(1f))
            QualityMetricCard("Checked pcs", checkedPcs.toString(), Color(0xFF334155), Modifier.weight(1f))
            QualityMetricCard("Pass / Fail pcs", "$passedPcs / $failedPcs", Color(0xFF7C3AED), Modifier.weight(1f))
          }
          Text("Audit Pass/Fail classification uses the existing OQL acceptance limit of 3.50%. Each bi-hourly audit record is counted once.", fontSize = 9.sp, color = Color(0xFF64748B))
        }
      }

      item {
        QualityPanel(title = "Line-Wise Bi-Hourly Audit — Pass vs Fail", subtitle = "Tap a line to open its complete deep-dive analytics") {
          if (lineAuditSummaries.isEmpty()) {
            QualityEmptyState("No bi-hourly audit records for this floor and period.")
          } else {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.Bottom
            ) {
              val maxCount = lineAuditSummaries.maxOf { it.second + it.third }.coerceAtLeast(1)
              lineAuditSummaries.forEach { (lineId, passed, failed) ->
                Column(
                  modifier = Modifier.width(66.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text("P $passed / F $failed", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155), maxLines = 1)
                  Row(
                    modifier = Modifier.height(92.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                  ) {
                    Box(Modifier.width(18.dp).height((passed.toFloat() / maxCount * 76f).coerceAtLeast(if (passed > 0) 3f else 0f).dp).background(Color(0xFF16A34A), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    Box(Modifier.width(18.dp).height((failed.toFloat() / maxCount * 76f).coerceAtLeast(if (failed > 0) 3f else 0f).dp).background(Color(0xFFDC2626), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                  }
                  Surface(
                    onClick = { onNavigateToLine(lineId) },
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(lineId, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8), modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                  }
                }
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              QualityLegend(Color(0xFF16A34A), "Pass")
              QualityLegend(Color(0xFFDC2626), "Fail")
            }
          }
        }
      }

      item {
        QualityPanel(title = "Bi-Hourly Audit Detail", subtitle = "Each row represents one saved audit record; pass/fail is calculated from its OQL result.") {
          if (auditSlots.isEmpty()) {
            QualityEmptyState("No audit details available.")
          } else {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
              Row(Modifier.background(Color(0xFF0F172A), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 8.dp)) {
                QualityTableCell("Line", 52.dp, Color.White, true)
                QualityTableCell("Date", 82.dp, Color.White, true)
                QualityTableCell("Audit Slot", 116.dp, Color.White, true)
                QualityTableCell("Audits", 52.dp, Color.White, true)
                QualityTableCell("Pass", 48.dp, Color(0xFF86EFAC), true)
                QualityTableCell("Fail", 48.dp, Color(0xFFFCA5A5), true)
                QualityTableCell("Checked", 62.dp, Color.White, true)
                QualityTableCell("Pass pcs", 66.dp, Color(0xFF86EFAC), true)
                QualityTableCell("Fail pcs", 62.dp, Color(0xFFFCA5A5), true)
              }
              auditSlots.forEachIndexed { index, row ->
                Row(
                  Modifier.background(if (index % 2 == 0) Color.White else Color(0xFFF8FAFC))
                    .padding(horizontal = 7.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  QualityTableCell(row.lineId, 52.dp, Color(0xFF0F172A), true)
                  QualityTableCell(row.date.takeLast(5), 82.dp, Color(0xFF334155))
                  QualityTableCell(NflRepository.formatHourSlot12h(row.hourSlot), 116.dp, Color(0xFF334155))
                  QualityTableCell(row.auditCount.toString(), 52.dp, Color(0xFF334155))
                  QualityTableCell(row.passedAudits.toString(), 48.dp, Color(0xFF15803D), true)
                  QualityTableCell(row.failedAudits.toString(), 48.dp, Color(0xFFDC2626), true)
                  QualityTableCell(row.checkedPcs.toString(), 62.dp, Color(0xFF334155))
                  QualityTableCell(row.passedPcs.toString(), 66.dp, Color(0xFF15803D), true)
                  QualityTableCell(row.failedPcs.toString(), 62.dp, Color(0xFFDC2626), true)
                }
              }
            }
          }
        }
      }

      item {
        QualityPanel(title = "Line-Wise DHU", subtitle = "Calculated from hourly endline quality inspections; bi-hourly audit records are excluded.") {
          if (lineDhu.isEmpty()) QualityEmptyState("No hourly DHU records available.")
          else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              lineDhu.take(12).forEach { (lineId, dhu) ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Surface(onClick = { onNavigateToLine(lineId) }, color = Color(0xFFEFF6FF), shape = RoundedCornerShape(4.dp)) {
                    Text(lineId, modifier = Modifier.width(42.dp).padding(vertical = 5.dp), textAlign = TextAlign.Center, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                  }
                  Box(Modifier.weight(1f).height(12.dp).background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth((dhu / (lineDhu.firstOrNull()?.second ?: 1.0)).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(if (dhu <= 2.0) Color(0xFF16A34A) else Color(0xFFF59E0B), RoundedCornerShape(8.dp)))
                  }
                  Text("${String.format(java.util.Locale.US, "%.2f", dhu)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155), modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                }
              }
            }
          }
        }
      }

      item {
        QualityPanel(title = "Top 5 Defects", subtitle = "Defect quantities from the selected floor and period.") {
          if (topDefects.isEmpty()) QualityEmptyState("No defect records available.")
          else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
              topDefects.forEachIndexed { index, entry ->
                val (defectName, count, pct) = entry
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${index + 1}. $defectName", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155), modifier = Modifier.weight(1f))
                    Text("$count pcs • ${String.format(java.util.Locale.US, "%.1f", pct)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                  }
                  Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFEDE9FE), RoundedCornerShape(6.dp))) {
                    Box(Modifier.fillMaxWidth((pct / (topDefects.firstOrNull()?.third ?: 1.0)).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF7C3AED), RoundedCornerShape(6.dp)))
                  }
                }
              }
            }
          }
        }
      }

      item {
        QualityPanel(title = "Top 5 Bi-Hourly Audit Defects", subtitle = "Defect breakdown recorded by auditors during two-hourly audits.") {
          if (auditTopDefects.isEmpty()) QualityEmptyState("No bi-hourly audit defect breakdown is available.")
          else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              auditTopDefects.forEachIndexed { index, entry ->
                val (name, count, pct) = entry
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${index + 1}. $name", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155), modifier = Modifier.weight(1f))
                    Text("$count pcs • ${String.format(java.util.Locale.US, "%.1f", pct)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                  }
                  Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFEDE9FE), RoundedCornerShape(6.dp))) {
                    Box(Modifier.fillMaxWidth((pct / (auditTopDefects.firstOrNull()?.third ?: 1.0)).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF7C3AED), RoundedCornerShape(6.dp)))
                  }
                }
              }
            }
          }
        }
      }

      item {
        QualityPanel(title = "Defect Sent vs Received by Line", subtitle = "Sewing alteration and spot defect movement from the live line summary.") {
          val lines = dashboardData.linePerformance.filter { it.defectSent + it.defectReceived + it.spotSent + it.spotReceived > 0 }
          if (lines.isEmpty()) QualityEmptyState("No defect movement records available.")
          else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
              lines.forEach { line ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                  Surface(onClick = { onNavigateToLine(line.lineId) }, color = Color(0xFFEFF6FF), shape = RoundedCornerShape(4.dp)) {
                    Text(line.lineId, modifier = Modifier.width(40.dp).padding(vertical = 4.dp), textAlign = TextAlign.Center, color = Color(0xFF1D4ED8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                  }
                  Spacer(Modifier.width(8.dp))
                  Text("Sew ${line.defectSent}/${line.defectReceived}", fontSize = 9.sp, color = Color(0xFFB45309), modifier = Modifier.weight(1f))
                  Text("Spot ${line.spotSent}/${line.spotReceived}", fontSize = 9.sp, color = Color(0xFF7C3AED), modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }

      item {
        QualityPanel(title = "Finishing Parameter Analysis", subtitle = "This section will populate when finishing parameter inspection records are available in the current data model.") {
          QualityEmptyState("No finishing parameter records or parameter definitions are currently stored in this repository. No sample values are shown.")
        }
      }
    }
  }
}

@Composable
private fun QualityMetricCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
  Surface(modifier, shape = RoundedCornerShape(9.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(title, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
      Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent)
    }
  }
}

@Composable
private fun QualityPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(11.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(1.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(17.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
      }
      Text(subtitle, fontSize = 9.sp, color = Color(0xFF64748B))
      content()
    }
  }
}

@Composable
private fun QualityEmptyState(message: String) {
  Surface(Modifier.fillMaxWidth(), color = Color(0xFFF8FAFC), shape = RoundedCornerShape(7.dp)) {
    Text(message, modifier = Modifier.padding(12.dp), color = Color(0xFF64748B), fontSize = 10.sp)
  }
}

@Composable
private fun QualityLegend(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
    Text(label, fontSize = 9.sp, color = Color(0xFF475569))
  }
}

@Composable
private fun QualityTableCell(text: String, width: androidx.compose.ui.unit.Dp, color: Color, bold: Boolean = false) {
  Text(text, modifier = Modifier.width(width), color = color, fontSize = 9.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
}
