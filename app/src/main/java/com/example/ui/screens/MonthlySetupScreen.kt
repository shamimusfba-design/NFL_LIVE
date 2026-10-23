package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import com.example.model.ProductionRecord
import com.example.model.QualityRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.MonthlyFactoryTarget
import com.example.ui.theme.NflRoyalBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySetupScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val monthlyTargets by repository.monthlyTargets.collectAsState()
  val productionEntries by repository.productionEntries.collectAsState()
  val qualityEntries by repository.qualityEntries.collectAsState()
  val context = LocalContext.current
  val currentMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
  val monthlyPrefs = remember(context) { context.getSharedPreferences("nfl_monthly_data_settings", android.content.Context.MODE_PRIVATE) }
  var autoMonthlyMode by remember(currentMonthKey) { mutableStateOf(monthlyPrefs.getBoolean("auto_$currentMonthKey", false)) }
  val userName by repository.currentUserName.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val canMonthlyTargetWrite = currentUser?.let { it.isAdmin || it.role == com.example.model.UserRole.IE || it.canMonthlyTargetWrite } ?: false
  val canEditLineMonthlyData = currentUser?.let { it.isAdmin || it.role == com.example.model.UserRole.IE || it.canMonthlyTargetWrite } ?: false
  var selectedFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  val selectedFloorName = if (selectedFloor == FloorScope.ALL) "All Floor" else selectedFloor.apiValue
  val autoFloorTarget = remember(selectedFloor, productionEntries, qualityEntries, currentMonthKey) {
    calculateAutoMonthlyTarget(repository, selectedFloor, null, currentMonthKey, productionEntries, qualityEntries)
  }
  val floorLines = remember(selectedFloor) {
    repository.allLinesMaster.filter { line -> selectedFloor == FloorScope.ALL || repository.isInSelectedFloor(line.floor, selectedFloor) }
  }
  val currentTarget = remember(selectedFloor, monthlyTargets) {
    repository.getMonthlyTarget(selectedFloorName)
  }

  val effectiveFloorTarget = if (autoMonthlyMode) autoFloorTarget else currentTarget
  var updateEffText by remember(selectedFloor, autoMonthlyMode, effectiveFloorTarget.updateEfficiencyPct, effectiveFloorTarget.lastUpdatedMillis) { mutableStateOf(effectiveFloorTarget.updateEfficiencyPct.toString()) }
  var floorTargetPcsText by remember(selectedFloor, autoMonthlyMode, effectiveFloorTarget.monthlyTargetPcs, effectiveFloorTarget.lastUpdatedMillis) { mutableStateOf(effectiveFloorTarget.monthlyTargetPcs.toString()) }
  var floorAchievementPcsText by remember(selectedFloor, autoMonthlyMode, effectiveFloorTarget.monthlyAchievementPcs, effectiveFloorTarget.lastUpdatedMillis) { mutableStateOf(effectiveFloorTarget.monthlyAchievementPcs.toString()) }
  var updateOqlText by remember(selectedFloor, autoMonthlyMode, effectiveFloorTarget.targetOqlPct, effectiveFloorTarget.lastUpdatedMillis) { mutableStateOf(effectiveFloorTarget.targetOqlPct.toString()) }
  var updateDhuText by remember(selectedFloor, autoMonthlyMode, effectiveFloorTarget.targetDhuPct, effectiveFloorTarget.lastUpdatedMillis) { mutableStateOf(effectiveFloorTarget.targetDhuPct.toString()) }
  var friPassText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.friPassRatePct.toString()) }
  var bsOqlText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.bsOqlPct.toString()) }

  var saveSuccessMsg by remember { mutableStateOf<String?>(null) }
  var lineDrafts by remember(selectedFloor, autoMonthlyMode, monthlyTargets) {
    mutableStateOf(
      floorLines.associate { line ->
        val target = monthlyTargets.find {
          it.lineId.equals(line.id, true) &&
            it.floor.replace("-", " ").equals((if (selectedFloor == FloorScope.ALL) line.floor else selectedFloorName).replace("-", " "), true)
        }
        line.id to LineMonthlyDraft(
          efficiency = target?.updateEfficiencyPct?.toString() ?: "",
          monthlyTarget = target?.monthlyTargetPcs?.toString() ?: "",
          achievement = target?.monthlyAchievementPcs?.toString() ?: "",
          dhu = target?.targetDhuPct?.toString() ?: "",
          oql = target?.targetOqlPct?.toString() ?: ""
        )
      }
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
          Column {
            Text("Monthly Achieved Data Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Text("Manual Entry for Monthly Achieved Efficiency, OQL & DHU", fontSize = 10.sp, color = Color(0xFFBAE6FD))
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("monthly_back_btn")) {
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
        .fillMaxWidth()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. Floor Selector Chips
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select Factory Floor:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(FloorScope.ALL, FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.FINISHING).forEach { floor ->
              val isSelected = selectedFloor == floor
              Surface(
                onClick = { selectedFloor = floor },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = when (floor) {
                    FloorScope.ALL -> "All Floors"
                    FloorScope.SEW_1 -> "Sew 1"
                    FloorScope.SEW_2 -> "Sew 2"
                    FloorScope.SEW_3 -> "Sew 3"
                    FloorScope.FINISHING -> "Finish"
                    else -> floor.displayName
                  },
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) Color.White else Color(0xFF334155),
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  modifier = Modifier.padding(vertical = 8.dp)
                )
              }
            }
          }
        }
      }

      Text(
        text = if (selectedFloor == FloorScope.ALL) "All Floors — Monthly Data" else "Floor-wise Monthly Data",
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF0F172A)
      )

      // 2. Achieved Data Fields Card
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF0284C7))
            Text(
              text = "${if (selectedFloor == FloorScope.ALL) "All Floors" else selectedFloor.displayName} — Monthly Data (${currentMonthKey})",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFF0F172A)
            )
          }

          Surface(
            color = Color(0xFFF0FDF4),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = if (autoMonthlyMode) "AUTO ON — calculated from actual daily production and quality entries." else "MANUAL MODE — enter monthly figures separately. Line-wise data is independent.",
              fontSize = 10.sp,
              color = Color(0xFF166534),
              modifier = Modifier.padding(8.dp)
            )
          }

          // Row 1: Monthly Achieved Efficiency (%)
          OutlinedTextField(
            value = updateEffText,
            onValueChange = { updateEffText = it },
            label = { Text("Monthly Achieved Efficiency (%) — Up-to-Date", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
            placeholder = { Text("e.g. 71.5", color = Color(0xFF475569)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = canMonthlyTargetWrite && !autoMonthlyMode,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
          )

          // Floor-level Monthly Target and Achievement (entered separately from line data)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = floorTargetPcsText,
              onValueChange = { floorTargetPcsText = it },
              label = { Text("Floor Monthly Target (pcs)", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite && !autoMonthlyMode,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
            )
            OutlinedTextField(
              value = floorAchievementPcsText,
              onValueChange = { floorAchievementPcsText = it },
              label = { Text("Floor Monthly Achievement (pcs)", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite && !autoMonthlyMode,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
            )
          }

          // Row 2: Monthly Achieved OQL (%) & Monthly Achieved DHU
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = updateOqlText,
              onValueChange = { updateOqlText = it },
              label = { Text("Monthly Achieved OQL (%)", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
              placeholder = { Text("e.g. 3.20", color = Color(0xFF475569)) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite && !autoMonthlyMode,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
            )

            OutlinedTextField(
              value = updateDhuText,
              onValueChange = { updateDhuText = it },
              label = { Text("Monthly Achieved DHU", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
              placeholder = { Text("e.g. 1.75", color = Color(0xFF475569)) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite && !autoMonthlyMode,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
            )
          }

          // Row 3: FRI Pass Rate & BS OQL (IE Entry)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = friPassText,
              onValueChange = { friPassText = it },
              label = { Text("FRI Pass Rate (%)", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
              placeholder = { Text("e.g. 100.0", color = Color(0xFF475569)) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite && !autoMonthlyMode,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
            )

            OutlinedTextField(
              value = bsOqlText,
              onValueChange = { bsOqlText = it },
              label = { Text("BS OQL (%) — IE Entry", color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) },
              placeholder = { Text("e.g. 2.40", color = Color(0xFF475569)) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite && !autoMonthlyMode,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Black, fontSize = 14.sp)
            )
          }

          // Feedback
          AnimatedVisibility(visible = saveSuccessMsg != null) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFFDCFCE7),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = saveSuccessMsg ?: "",
                color = Color(0xFF166534),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(10.dp)
              )
            }
          }

          if (!canMonthlyTargetWrite) {
            Surface(
              color = Color(0xFFFEF3C7),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "🔒 View-Only Mode — You do not have permission to edit monthly factory targets. Only Admin, IE, or Production Reporter accounts can modify these values.",
                  color = Color(0xFF92400E),
                  fontSize = 11.5.sp
                )
              }
            }
          }

          // Save Button
          Button(
            onClick = {
              val target = MonthlyFactoryTarget(
                floor = selectedFloorName,
                targetEfficiencyPct = updateEffText.toDoubleOrNull() ?: 71.5,
                updateEfficiencyPct = updateEffText.toDoubleOrNull() ?: 71.5,
                targetOqlPct = updateOqlText.toDoubleOrNull() ?: 3.20,
                targetDhuPct = updateDhuText.toDoubleOrNull() ?: 1.75,
                monthlyTargetPcs = floorTargetPcsText.toDoubleOrNull() ?: 0.0,
                monthlyAchievementPcs = floorAchievementPcsText.toDoubleOrNull() ?: 0.0,
                friPassRatePct = friPassText.toDoubleOrNull() ?: 100.0,
                bsOqlPct = bsOqlText.toDoubleOrNull() ?: 2.40,
                lastUpdatedBy = userName
              )
              repository.saveMonthlyTarget(target)
              saveSuccessMsg = "✓ Saved Monthly Targets & BS OQL for ${selectedFloor.displayName} (Eff: ${target.updateEfficiencyPct}%, OQL: ${target.targetOqlPct}%, DHU: ${target.targetDhuPct}%, BS OQL: ${target.bsOqlPct}%)"
            },
            enabled = canMonthlyTargetWrite && !autoMonthlyMode,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (canMonthlyTargetWrite) NflRoyalBlue else Color(0xFF94A3B8)
            )
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(when { !canMonthlyTargetWrite -> "Entry Locked (View Only)"; autoMonthlyMode -> "AUTO MODE — Daily Data Linked"; else -> "Save Monthly Achieved Data" }, fontWeight = FontWeight.Bold)
          }
        }
      }

      Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Monthly Data Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
            Text(if (autoMonthlyMode) "Auto: actual daily entries" else "Manual: IE/Admin entry", fontSize = 10.sp, color = Color(0xFF64748B))
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (autoMonthlyMode) "AUTO" else "MANUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (autoMonthlyMode) Color(0xFF15803D) else Color(0xFF1D4ED8))
            Switch(checked = autoMonthlyMode, onCheckedChange = { enabled ->
              autoMonthlyMode = enabled
              monthlyPrefs.edit().putBoolean("auto_$currentMonthKey", enabled).apply()
            }, enabled = canMonthlyTargetWrite)
          }
        }
      }

      // Line-wise monthly data table: separate records for every line, not derived from floor totals.
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "${if (selectedFloor == FloorScope.ALL) "All Floors" else selectedFloor.displayName} — Line-wise Monthly Data",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
          )
          Text(
            text = if (autoMonthlyMode) "Line-wise values are calculated from real daily entries; switch Auto off to enter manually." else "Enter each line separately. Floor-wise figures above remain independent.",
            fontSize = 11.sp,
            color = Color(0xFF64748B)
          )
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            TableHeaderCell("Line", 78.dp)
            TableHeaderCell("Monthly Efficiency %", 120.dp)
            TableHeaderCell("Monthly Target", 110.dp)
            TableHeaderCell("Monthly Achievement", 130.dp)
            TableHeaderCell("Monthly DHU %", 105.dp)
            TableHeaderCell("Monthly OQL %", 105.dp)
          }
          Divider(color = Color(0xFFE2E8F0))
          floorLines.forEach { line ->
            val manualLineTarget = monthlyTargets.find {
              it.lineId.equals(line.id, ignoreCase = true) &&
                it.floor.replace("-", " ").equals((if (selectedFloor == FloorScope.ALL) line.floor else selectedFloorName).replace("-", " "), ignoreCase = true)
            }
            val lineTarget = if (autoMonthlyMode) calculateAutoMonthlyTarget(repository, selectedFloor, line.id, currentMonthKey, productionEntries, qualityEntries) else manualLineTarget
            val draft = lineDrafts[line.id] ?: LineMonthlyDraft()
            LineMonthlyDataRow(
              lineId = line.id,
              draft = if (autoMonthlyMode) LineMonthlyDraft(
                efficiency = lineTarget?.updateEfficiencyPct?.toString() ?: "",
                monthlyTarget = lineTarget?.monthlyTargetPcs?.toString() ?: "",
                achievement = lineTarget?.monthlyAchievementPcs?.toString() ?: "",
                dhu = lineTarget?.targetDhuPct?.toString() ?: "",
                oql = lineTarget?.targetOqlPct?.toString() ?: ""
              ) else draft,
              canEdit = canEditLineMonthlyData && !autoMonthlyMode,
              onDraftChange = { updated -> lineDrafts = lineDrafts + (line.id to updated) }
            )
            Divider(color = Color(0xFFF1F5F9))
          }
          Button(
            onClick = {
              var savedCount = 0
              floorLines.forEach { line ->
                val draft = lineDrafts[line.id] ?: LineMonthlyDraft()
                val eff = draft.efficiency.toDoubleOrNull()
                val targetPcs = draft.monthlyTarget.toDoubleOrNull()
                val achievedPcs = draft.achievement.toDoubleOrNull()
                val dhuPct = draft.dhu.toDoubleOrNull()
                val oqlPct = draft.oql.toDoubleOrNull()
                if (eff != null && targetPcs != null && achievedPcs != null && dhuPct != null && oqlPct != null) {
                  repository.saveMonthlyTarget(
                    MonthlyFactoryTarget(
                      floor = if (selectedFloor == FloorScope.ALL) line.floor else selectedFloorName,
                      lineId = line.id,
                      monthYear = currentMonthKey,
                      updateEfficiencyPct = eff,
                      targetEfficiencyPct = eff,
                      monthlyTargetPcs = targetPcs,
                      monthlyAchievementPcs = achievedPcs,
                      targetDhuPct = dhuPct,
                      targetOqlPct = oqlPct,
                      lastUpdatedBy = userName
                    )
                  )
                  savedCount++
                }
              }
              saveSuccessMsg = if (savedCount == floorLines.size) "Saved all $savedCount line records." else "Saved $savedCount of ${floorLines.size}. Complete all five fields per line and retry."
            },
            enabled = canEditLineMonthlyData && !autoMonthlyMode && floorLines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save All Line-wise Monthly Data", fontWeight = FontWeight.Bold)
          }
          saveSuccessMsg?.let { Text(it, color = Color(0xFF166534), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
          if (!canEditLineMonthlyData) {
            Text(
              text = "View only — only IE or Admin can enter or update line-wise monthly data.",
              color = Color(0xFF92400E),
              fontSize = 11.sp
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TableHeaderCell(label: String, width: androidx.compose.ui.unit.Dp) {
  Surface(
    color = Color(0xFF0F172A),
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.width(width)
  ) {
    Text(
      text = label,
      color = Color.White,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp)
    )
  }
}

private fun calculateAutoMonthlyTarget(
  repository: NflRepository, floorScope: FloorScope, lineId: String?, monthKey: String,
  productionEntries: List<ProductionRecord>, qualityEntries: List<QualityRecord>
): MonthlyFactoryTarget {
  val productions = productionEntries.filter { !it.isDemo && it.date.replace("/", "-").startsWith(monthKey) &&
    (lineId == null || it.lineId.equals(lineId, true)) &&
    (floorScope == FloorScope.ALL || repository.isInSelectedFloor(it.floor, floorScope)) }
  val qualities = qualityEntries.filter { !it.isDemo && it.date.replace("/", "-").startsWith(monthKey) &&
    (lineId == null || it.lineId.equals(lineId, true)) &&
    (floorScope == FloorScope.ALL || repository.isInSelectedFloor(it.floor, floorScope)) }
  val output = productions.sumOf { it.outputQty }
  val checked = qualities.sumOf { it.checkedGarments }
  val defective = qualities.sumOf { it.defectiveGarments }
  val defects = qualities.sumOf { it.totalDefects }
  val daysByLine = productions.groupBy { it.lineId }.mapValues { (_, rows) -> rows.map { it.date }.distinct().size }
  val target = daysByLine.entries.sumOf { (id, days) -> repository.getLineSetup(id).targetDailyPcs.toDouble() * days }
  val earned = productions.sumOf { it.outputQty * repository.getLineSetup(it.lineId).smv }
  val available = daysByLine.entries.sumOf { (id, days) ->
    val setup = repository.getLineSetup(id); setup.includedManpower.toDouble() * setup.totalShiftHours * 60.0 * days
  }
  return MonthlyFactoryTarget(
    floor = if (floorScope == FloorScope.ALL) "All Floor" else floorScope.apiValue,
    monthYear = monthKey, lineId = lineId ?: "", targetEfficiencyPct = 75.0,
    updateEfficiencyPct = if (available > 0) Math.round(earned / available * 1000.0) / 10.0 else 0.0,
    monthlyTargetPcs = target, monthlyAchievementPcs = output.toDouble(),
    targetOqlPct = if (checked > 0) Math.round(defective.toDouble() / checked * 10000.0) / 100.0 else 0.0,
    targetDhuPct = if (checked > 0) Math.round(defects.toDouble() / checked * 10000.0) / 100.0 else 0.0,
    friPassRatePct = 0.0, bsOqlPct = 0.0, lastUpdatedBy = "AUTO",
    lastUpdatedMillis = productions.maxOfOrNull { it.timestamp } ?: qualities.maxOfOrNull { it.timestamp } ?: 0L
  )
}

private data class LineMonthlyDraft(
  val efficiency: String = "",
  val monthlyTarget: String = "",
  val achievement: String = "",
  val dhu: String = "",
  val oql: String = ""
)

@Composable
private fun LineMonthlyDataRow(
  lineId: String,
  draft: LineMonthlyDraft,
  canEdit: Boolean,
  onDraftChange: (LineMonthlyDraft) -> Unit
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Surface(
      color = Color(0xFFEFF6FF),
      shape = RoundedCornerShape(6.dp),
      modifier = Modifier.width(78.dp)
    ) {
      Text(
        when { lineId.startsWith("Iron", true) -> "Iron"; lineId.startsWith("Packing", true) -> "Packing"; else -> lineId },
        fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), fontSize = 12.sp,
        modifier = Modifier.padding(8.dp)
      )
    }
    OutlinedTextField(
      value = draft.efficiency, onValueChange = { onDraftChange(draft.copy(efficiency = it)) },
      label = { Text("Efficiency %", fontSize = 9.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = com.example.ui.theme.nflInputColors(),
      modifier = Modifier.width(120.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0F1D))
    )
    OutlinedTextField(
      value = draft.monthlyTarget, onValueChange = { onDraftChange(draft.copy(monthlyTarget = it)) },
      label = { Text("Target pcs", fontSize = 9.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = com.example.ui.theme.nflInputColors(),
      modifier = Modifier.width(110.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0F1D))
    )
    OutlinedTextField(
      value = draft.achievement, onValueChange = { onDraftChange(draft.copy(achievement = it)) },
      label = { Text("Achieved pcs", fontSize = 9.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = com.example.ui.theme.nflInputColors(),
      modifier = Modifier.width(130.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0F1D))
    )
    OutlinedTextField(
      value = draft.dhu, onValueChange = { onDraftChange(draft.copy(dhu = it)) },
      label = { Text("DHU %", fontSize = 9.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = com.example.ui.theme.nflInputColors(),
      modifier = Modifier.width(105.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0F1D))
    )
    OutlinedTextField(
      value = draft.oql, onValueChange = { onDraftChange(draft.copy(oql = it)) },
      label = { Text("OQL %", fontSize = 9.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = com.example.ui.theme.nflInputColors(),
      modifier = Modifier.width(105.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0F1D))
    )
  }
}
