package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.DataCleanScope

private val CLEAN_ROWS = listOf(
  DataCleanScope.PRODUCTION to "Hourly output of every line, all floors.",
  DataCleanScope.QUALITY to "Hourly DHU, bi-hourly audit, defects and rectification.",
  DataCleanScope.NPT to "Stoppages with stages, times and comments.",
  DataCleanScope.SETUP to "Item / style, SMV, line manpower, targets and the floor totals of machines, operators and helpers.",
  DataCleanScope.MONTHLY to "Monthly figures go back to zero; floor targets stay.",
  DataCleanScope.WEEKLY to "All saved weeks of BS OQL and NFL AQC."
)

/**
 * Data Clean (Administrator only). Removes demo or test data so the factory can start clean.
 * Each action asks for a typed word and the Administrator's own password. Users, roles, the line master
 * and the audit log are never removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCleanCard(repository: NflRepository) {
  var pending by remember { mutableStateOf<DataCleanScope?>(null) }
  var typed by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var working by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

  fun phraseFor(s: DataCleanScope) = when (s) {
    DataCleanScope.EVERYTHING -> "CLEAN EVERYTHING"
    DataCleanScope.DEMO_ONLY -> "CLEAN DEMO"
    else -> "CLEAN"
  }

  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
    modifier = Modifier.fillMaxWidth().testTag("data_clean_card")
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Data Clean (Admin only)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFB91C1C))
      Text(
        "Deletes the chosen records from this phone and from the cloud, so they disappear on every phone and TV. " +
          "This cannot be undone. Users, roles, the line list and the audit log are never deleted.",
        fontSize = 11.sp, color = Color(0xFF546E7A)
      )
      Button(
        onClick = { pending = DataCleanScope.DEMO_ONLY; typed = ""; password = ""; result = null },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("clean_demo_btn")
      ) { Text("Clear demo data only", fontWeight = FontWeight.Bold) }
      CLEAN_ROWS.forEach { (scope, hint) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(scope.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF0F172A))
            Text(hint, fontSize = 10.5.sp, color = Color(0xFF64748B))
          }
          OutlinedButton(
            onClick = { pending = scope; typed = ""; password = ""; result = null },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("clean_${scope.name}_btn")
          ) { Text("Clear", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold) }
        }
      }
      Button(
        onClick = { pending = DataCleanScope.EVERYTHING; typed = ""; password = ""; result = null },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("clean_everything_btn")
      ) { Text("Clean everything", fontWeight = FontWeight.Black) }
      result?.let { (ok, msg) ->
        Text(msg, color = if (ok) Color(0xFF15803D) else Color(0xFFB91C1C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("clean_result"))
      }
    }
  }

  val scope = pending
  if (scope != null) {
    val phrase = phraseFor(scope)
    AlertDialog(
      onDismissRequest = { if (!working) pending = null },
      title = { Text(if (scope == DataCleanScope.EVERYTHING) "Clean everything?" else "Clear ${scope.displayName.lowercase()}?", fontWeight = FontWeight.Black, color = Color(0xFFB91C1C)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            when (scope) {
              DataCleanScope.DEMO_ONLY -> "Only records marked as demo are removed. Real records stay."
              DataCleanScope.EVERYTHING -> "Production, quality, NPT, setups and manpower, monthly data and weekly OQL / AQC will be permanently deleted on every device."
              else -> "${scope.displayName} will be permanently deleted on every phone, TV and PC."
            },
            fontSize = 13.sp
          )
          OutlinedTextField(
            value = typed, onValueChange = { typed = it }, singleLine = true,
            label = { Text("Type $phrase to confirm") },
            modifier = Modifier.fillMaxWidth().testTag("clean_phrase_input")
          )
          OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true,
            label = { Text("Your Admin password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("clean_password_input")
          )
        }
      },
      confirmButton = {
        Button(
          enabled = !working && typed.trim() == phrase && password.isNotEmpty(),
          onClick = {
            working = true
            repository.cleanData(scope, password) { ok, msg ->
              working = false
              result = ok to msg
              pending = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
          modifier = Modifier.testTag("clean_confirm_btn")
        ) { Text(if (working) "Deleting..." else "Delete permanently", fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { if (!working) pending = null }) { Text("Cancel") } }
    )
  }
}
