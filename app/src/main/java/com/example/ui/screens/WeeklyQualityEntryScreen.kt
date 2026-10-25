package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NflRepository
import com.example.model.OqlBand
import com.example.model.OqlThresholds
import com.example.model.UserRole
import com.example.model.WeeklyQualityWeek
import java.util.Locale

private val WQ_TEXT = Color(0xFF0F172A)
private val WQ_MUTED = Color(0xFF475569)

private fun oqlBandColor(value: Double): Color = when (OqlThresholds.bandOf(value)) {
  OqlBand.WITHIN_COMMITMENT -> Color(0xFF15803D)
  OqlBand.LEVEL1 -> Color(0xFFB45309)
  OqlBand.LEVEL1_LOST -> Color(0xFFC2410C)
  OqlBand.CRITICAL -> Color(0xFFB91C1C)
}

private fun formatPct(v: Double): String = String.format(Locale.US, "%.2f", v)

private fun inputText(v: Double?): String = when {
  v == null -> ""
  v % 1.0 == 0.0 -> v.toInt().toString()
  else -> v.toString()
}

/** Blank means "not entered". Anything else must be a number. */
private fun parsePct(text: String): Pair<Double?, Boolean> {
  val t = text.trim().replace(',', '.')
  if (t.isEmpty()) return null to true
  val d = t.toDoubleOrNull() ?: return null to false
  return d to true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyQualityEntryScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val entries by repository.weeklyQuality.collectAsStateWithLifecycle()
  val currentUser by repository.currentUser.collectAsStateWithLifecycle()
  val canEdit = currentUser?.let { it.isAdmin || (it.role == UserRole.IE && it.canMonthlyTargetWrite) } ?: false

  val weeks = remember { WeeklyQualityWeek.recentWeeks(12, System.currentTimeMillis()) }
  var selectedKey by rememberSaveable { mutableStateOf(weeks.first().key) }
  val selected = weeks.firstOrNull { it.key == selectedKey } ?: weeks.first()
  val existing = entries.firstOrNull { it.weekKey == selected.key }

  var bsText by rememberSaveable { mutableStateOf("") }
  var aqcText by rememberSaveable { mutableStateOf("") }
  var message by rememberSaveable { mutableStateOf<String?>(null) }
  var messageIsError by rememberSaveable { mutableStateOf(false) }

  // Show what is already saved for the chosen week.
  LaunchedEffect(selected.key) {
    val saved = entries.firstOrNull { it.weekKey == selected.key }
    bsText = inputText(saved?.bsOqlPct)
    aqcText = inputText(saved?.nflAqcPct)
    message = null
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WQ_TEXT,
    unfocusedTextColor = WQ_TEXT,
    disabledTextColor = WQ_MUTED,
    focusedLabelColor = Color(0xFF1D4ED8),
    unfocusedLabelColor = WQ_MUTED,
    focusedBorderColor = Color(0xFF1D4ED8),
    unfocusedBorderColor = Color(0xFF94A3B8),
    cursorColor = Color(0xFF1D4ED8),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Weekly OQL / AQC Entry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("weekly_quality_back_btn")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    containerColor = Color(0xFFF8FAFC)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = "Factory level, entered once a week. Values are in %. Limits: commitment ${OqlThresholds.COMMITMENT}%, " +
          "Level 1 below ${OqlThresholds.LEVEL1_BELOW.toInt()}%, red above ${OqlThresholds.RED_ABOVE.toInt()}%.",
        color = WQ_MUTED,
        fontSize = 13.sp
      )

      Text("Week", color = WQ_TEXT, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(weeks, key = { it.key }) { w ->
          FilterChip(
            selected = w.key == selected.key,
            onClick = { selectedKey = w.key },
            label = { Text("${w.label}  ${w.rangeLabel}", fontSize = 12.sp) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("weekly_week_${w.key}")
          )
        }
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "${selected.label} · ${selected.rangeLabel}",
            color = WQ_TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp
          )
          if (existing != null) {
            Text(
              "Last saved by ${existing.lastUpdatedBy.ifBlank { "unknown" }}. Leave a box blank to keep its saved value.",
              color = WQ_MUTED, fontSize = 12.sp
            )
          }
          OutlinedTextField(
            value = bsText,
            onValueChange = { bsText = it.take(7) },
            label = { Text("BS OQL (%)") },
            singleLine = true,
            enabled = canEdit,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("weekly_bs_oql_input")
          )
          parsePct(bsText).first?.let { v ->
            Text(OqlThresholds.describe(v), color = oqlBandColor(v), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          }
          OutlinedTextField(
            value = aqcText,
            onValueChange = { aqcText = it.take(7) },
            label = { Text("NFL AQC (%)") },
            singleLine = true,
            enabled = canEdit,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("weekly_nfl_aqc_input")
          )

          if (!canEdit) {
            Text(
              "Only an Admin or an authorized IE can enter weekly values.",
              color = Color(0xFFB91C1C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
          }
          message?.let {
            Text(
              it,
              color = if (messageIsError) Color(0xFFB91C1C) else Color(0xFF15803D),
              fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
              modifier = Modifier.testTag("weekly_quality_message")
            )
          }
          Button(
            onClick = {
              val (bs, bsOk) = parsePct(bsText)
              val (aqc, aqcOk) = parsePct(aqcText)
              if (!bsOk || !aqcOk) {
                message = "Enter numbers only, for example 5.58"
                messageIsError = true
              } else {
                val result = repository.saveWeeklyQuality(selected.startMillis + 12L * 60L * 60L * 1000L, bs, aqc)
                message = result.message
                messageIsError = !result.success
              }
            },
            enabled = canEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("weekly_quality_save_btn")
          ) {
            Text("Save ${selected.label}", fontWeight = FontWeight.Bold)
          }
        }
      }

      Text("Saved weeks", color = WQ_TEXT, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      val recent = entries.sortedByDescending { it.weekStartIso }.take(12)
      if (recent.isEmpty()) {
        Text("Nothing entered yet.", color = WQ_MUTED, fontSize = 13.sp)
      } else {
        recent.forEach { e ->
          Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(WeeklyQualityWeek.labelFor(e.weekKey), color = WQ_TEXT, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              Column(horizontalAlignment = Alignment.End) {
                val bs = e.bsOqlPct
                Text(
                  "BS OQL  " + (bs?.let { formatPct(it) + "%" } ?: "-"),
                  color = if (bs != null) oqlBandColor(bs) else WQ_MUTED,
                  fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
                Text(
                  "NFL AQC  " + (e.nflAqcPct?.let { formatPct(it) + "%" } ?: "-"),
                  color = WQ_TEXT, fontSize = 13.sp
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
