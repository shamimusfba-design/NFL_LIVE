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
  val userName by repository.currentUserName.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val canMonthlyTargetWrite = currentUser?.let { it.isAdmin || (it.role == com.example.model.UserRole.IE && it.canMonthlyTargetWrite) } ?: false
  val canEditLineMonthlyData = currentUser?.let { it.isAdmin || (it.role == com.example.model.UserRole.IE && it.canMonthlyTargetWrite) } ?: false
  var selectedFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  val floorLines = remember(selectedFloor) {
    repository.allLinesMaster.filter { line ->
      line.floor.replace("-", " ").equals(selectedFloor.apiValue.replace("-", " "), ignoreCase = true) ||
        (selectedFloor == FloorScope.FINISHING && line.floor.contains("Finish", ignoreCase = true))
    }
  }
  val currentTarget = remember(selectedFloor, monthlyTargets) {
    repository.getMonthlyTarget(selectedFloor.apiValue)
  }

  var updateEffText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.updateEfficiencyPct.toString()) }
  var floorTargetPcsText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.monthlyTargetPcs.toString()) }
  var floorAchievementPcsText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.monthlyAchievementPcs.toString()) }
  var updateOqlText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.targetOqlPct.toString()) }
  var updateDhuText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.targetDhuPct.toString()) }
  var friPassText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.friPassRatePct.toString()) }
  var bsOqlText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.bsOqlPct.toString()) }

  var saveSuccessMsg by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Monthly Achieved Data Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Text("মাসিক আপটুডেট এচিভড এফিসিয়েন্সি, ওকিউএল ও ডিএইচইউ ম্যানুয়াল এন্ট্রি", fontSize = 10.sp, color = Color(0xFFBAE6FD))
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
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
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
            listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.FINISHING).forEach { floor ->
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
        text = "Floor-wise Monthly Data",
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
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF0284C7))
            Text(
              text = "${selectedFloor.displayName} — Monthly Up-to-Date Achieved (October 2026)",
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
              text = "Enter the selected floor's monthly figures here. Line-wise data below is entered and saved independently.",
              fontSize = 10.sp,
              color = Color(0xFF166534),
              modifier = Modifier.padding(8.dp)
            )
          }

          // Row 1: Monthly Achieved Efficiency (%)
          OutlinedTextField(
            value = updateEffText,
            onValueChange = { updateEffText = it },
            label = { Text("Monthly Achieved Efficiency (%) — আপটুডেট এচিভ") },
            placeholder = { Text("e.g. 71.5") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = canMonthlyTargetWrite,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

          // Floor-level Monthly Target and Achievement (entered separately from line data)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = floorTargetPcsText,
              onValueChange = { floorTargetPcsText = it },
              label = { Text("Floor Monthly Target (pcs)") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              enabled = canMonthlyTargetWrite,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
              value = floorAchievementPcsText,
              onValueChange = { floorAchievementPcsText = it },
              label = { Text("Floor Monthly Achievement (pcs)") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
          }

          // Row 2: Monthly Achieved OQL (%) & Monthly Achieved DHU
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = updateOqlText,
              onValueChange = { updateOqlText = it },
              label = { Text("Monthly Achieved OQL (%)") },
              placeholder = { Text("e.g. 3.20") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
              value = updateDhuText,
              onValueChange = { updateDhuText = it },
              label = { Text("Monthly Achieved DHU") },
              placeholder = { Text("e.g. 1.75") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
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
              label = { Text("FRI Pass Rate (%)") },
              placeholder = { Text("e.g. 100.0") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
              value = bsOqlText,
              onValueChange = { bsOqlText = it },
              label = { Text("BS OQL (%) — IE Entry") },
              placeholder = { Text("e.g. 2.40") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
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
                  text = "🔒 ভিউ অনলি মোড (View Only) — আপনার মাসিক ফ্যাক্টরি টার্গেট সংশোধনের অনুমতি নেই। শুধুমাত্র Admin, IE বা Production Reporter এটি পরিবর্তন করতে পারবেন।",
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
                floor = selectedFloor.apiValue,
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
            enabled = canMonthlyTargetWrite,
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
            Text(if (canMonthlyTargetWrite) "Save Monthly Achieved Data" else "🔒 Entry Locked (View Only)", fontWeight = FontWeight.Bold)
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
            text = "${selectedFloor.displayName} — Line-wise Monthly Data",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
          )
          Text(
            text = "Enter each line's monthly figures separately. Floor-wise figures above remain independent.",
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
            TableHeaderCell("Action", 90.dp)
          }
          Divider(color = Color(0xFFE2E8F0))
          floorLines.forEach { line ->
            val lineTarget = monthlyTargets.find {
              it.lineId.equals(line.id, ignoreCase = true) &&
                it.floor.replace("-", " ").equals(selectedFloor.apiValue.replace("-", " "), ignoreCase = true)
            }
            LineMonthlyDataRow(
              lineId = line.id,
              floorName = selectedFloor.apiValue,
              target = lineTarget,
              canEdit = canEditLineMonthlyData,
              userName = userName,
              repository = repository
            )
            Divider(color = Color(0xFFF1F5F9))
          }
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

@Composable
private fun LineMonthlyDataRow(
  lineId: String,
  floorName: String,
  target: MonthlyFactoryTarget?,
  canEdit: Boolean,
  userName: String,
  repository: NflRepository
) {
  var efficiency by remember(lineId, target?.lastUpdatedMillis) {
    mutableStateOf(target?.updateEfficiencyPct?.toString() ?: "")
  }
  var monthlyTarget by remember(lineId, target?.lastUpdatedMillis) {
    mutableStateOf(target?.monthlyTargetPcs?.toString() ?: "")
  }
  var achievement by remember(lineId, target?.lastUpdatedMillis) {
    mutableStateOf(target?.monthlyAchievementPcs?.toString() ?: "")
  }
  var dhu by remember(lineId, target?.lastUpdatedMillis) {
    mutableStateOf(target?.targetDhuPct?.toString() ?: "")
  }
  var oql by remember(lineId, target?.lastUpdatedMillis) {
    mutableStateOf(target?.targetOqlPct?.toString() ?: "")
  }
  var savedMessage by remember(lineId) { mutableStateOf(false) }

  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Surface(
      color = Color(0xFFEFF6FF),
      shape = RoundedCornerShape(6.dp),
      modifier = Modifier.width(78.dp)
    ) {
      Text(lineId, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), fontSize = 12.sp,
        modifier = Modifier.padding(8.dp))
    }
    OutlinedTextField(
      value = efficiency, onValueChange = { efficiency = it; savedMessage = false },
      label = { Text("Efficiency %", fontSize = 9.sp) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.width(120.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
    OutlinedTextField(
      value = monthlyTarget, onValueChange = { monthlyTarget = it; savedMessage = false },
      label = { Text("Target pcs", fontSize = 9.sp) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.width(110.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
    OutlinedTextField(
      value = achievement, onValueChange = { achievement = it; savedMessage = false },
      label = { Text("Achieved pcs", fontSize = 9.sp) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.width(130.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
    OutlinedTextField(
      value = dhu, onValueChange = { dhu = it; savedMessage = false },
      label = { Text("DHU %", fontSize = 9.sp) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.width(105.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
    OutlinedTextField(
      value = oql, onValueChange = { oql = it; savedMessage = false },
      label = { Text("OQL %", fontSize = 9.sp) }, singleLine = true, enabled = canEdit,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.width(105.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
    Column(modifier = Modifier.width(90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Button(
        onClick = {
          val eff = efficiency.toDoubleOrNull()
          val targetPcs = monthlyTarget.toDoubleOrNull()
          val achievedPcs = achievement.toDoubleOrNull()
          val dhuPct = dhu.toDoubleOrNull()
          val oqlPct = oql.toDoubleOrNull()
          if (eff != null && targetPcs != null && achievedPcs != null && dhuPct != null && oqlPct != null) {
            repository.saveMonthlyTarget(
              MonthlyFactoryTarget(
                floor = floorName,
                lineId = lineId,
                updateEfficiencyPct = eff,
                targetEfficiencyPct = eff,
                monthlyTargetPcs = targetPcs,
                monthlyAchievementPcs = achievedPcs,
                targetDhuPct = dhuPct,
                targetOqlPct = oqlPct,
                lastUpdatedBy = userName
              )
            )
            savedMessage = true
          }
        },
        enabled = canEdit,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
      ) { Text("Save", fontSize = 10.sp) }
      if (savedMessage) Text("Saved", color = Color(0xFF15803D), fontSize = 9.sp)
    }
  }
}
