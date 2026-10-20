package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
  val canMonthlyTargetWrite = currentUser?.let { it.isAdmin || it.canMonthlyTargetWrite } ?: false

  var selectedFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  val currentTarget = remember(selectedFloor, monthlyTargets) {
    repository.getMonthlyTarget(selectedFloor.apiValue)
  }

  var updateEffText by remember(selectedFloor, currentTarget.lastUpdatedMillis) { mutableStateOf(currentTarget.updateEfficiencyPct.toString()) }
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
              text = "📌 নির্দেশিকা: এখানে মাসিক টার্গেট দেওয়ার প্রয়োজন নেই। পুরো মাসের আপটুডেট এচিভ কত হয়েছে (Achieved Efficiency, Achieved OQL, Achieved DHU) এগুলো সরাসরি এন্ট্রি দিন। সিস্টেম স্বয়ংক্রিয়ভাবে এগুলো ড্যাশবোর্ডে শো করবে।",
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

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
    }
  }
}
