package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ManpowerSummary
import kotlin.math.roundToInt

/** Dashboard card with the four manpower and machine numbers. Shows nothing invented when nothing was entered. */
@Composable
fun ManpowerMachinesCard(summary: ManpowerSummary, modifier: Modifier = Modifier) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = modifier.fillMaxWidth().testTag("manpower_machines_card")
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Manpower & Machines", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
        Text(if (summary.hasData) "${summary.floorsEntered} floor(s) today" else "not entered today", fontSize = 10.5.sp, color = Color(0xFF64748B))
      }
      if (!summary.hasData) {
        Text("Enter the floor totals at the bottom of Morning Line Setup (tap this card).", fontSize = 11.5.sp, color = Color(0xFF64748B))
      } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          McTile("Operators present", "${summary.operatorsPresent} / ${summary.operatorsPlanned}", "${summary.operatorsPresentPct.roundToInt()}% of planned", summary.operatorsPresentPct, Modifier.weight(1f))
          McTile("Machines running", "${summary.machinesRunning} / ${summary.totalMachines}", "${summary.machinesRunningPct.roundToInt()}% of all machines", summary.machinesRunningPct, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          McTile("Machines per present operator", "${summary.machinesPerOperatorPct.roundToInt()}%", "running ÷ present operators", summary.machinesPerOperatorPct, Modifier.weight(1f))
          McTile("Helpers % of machines", "${summary.helpersPctOfMachines.roundToInt()}%", "${summary.helpersPresent} helpers ÷ ${summary.totalMachines}", summary.helpersPctOfMachines, Modifier.weight(1f))
        }
        if (summary.operatorsOnHandwork > 0) {
          Text("Operators on handwork (HW): ${summary.operatorsOnHandwork} (${summary.handworkPctOfOperators.roundToInt()}% of operators present)", fontSize = 11.sp, color = Color(0xFF64748B))
        }
      }
    }
  }
}

@Composable
private fun McTile(label: String, value: String, sub: String, pct: Double, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(label, fontSize = 10.5.sp, color = Color(0xFF64748B), maxLines = 1)
    Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
    LinearProgressIndicator(
      progress = { (pct / 100.0).coerceIn(0.0, 1.0).toFloat() },
      modifier = Modifier.fillMaxWidth().height(5.dp),
      color = Color(0xFF1D4ED8),
      trackColor = Color(0xFFE2E8F0)
    )
    Text(sub, fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1)
  }
}
