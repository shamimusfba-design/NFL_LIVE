package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.SupervisorLineSetup
import com.example.ui.theme.NflGreen
import com.example.ui.theme.NflRoyalBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorSetupScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val assignedLine by repository.assignedLine.collectAsState()
  val allTargets by repository.targets.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val canMorningTargetWrite = currentUser?.let { it.isAdmin || it.canMorningTargetWrite } ?: false

  var selectedFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  var selectedLine by remember { mutableStateOf(assignedLine) }
  var showLineDropdown by remember { mutableStateOf(false) }

  // Quick garment item preset chips (Requested specifically by user)
  val garmentItems = listOf(
    "SS T-Shirt",
    "LS T-Shirt",
    "Sweatshirt",
    "Pant",
    "Leggings",
    "Jacket",
    "Hoodie",
    "Hoodie with Zipper",
    "Skirt",
    "Bodysuit",
    "Jumpsuit",
    "Dress"
  )

  // Load current setup for selected line
  val currentSetup = remember(selectedLine, allTargets) { repository.getLineSetup(selectedLine) }

  var itemNameText by remember(selectedLine) { mutableStateOf(currentSetup.itemName) }
  var smvText by remember(selectedLine) { mutableStateOf(currentSetup.smv.toString()) }
  var targetDailyPcsText by remember(selectedLine) { mutableStateOf(currentSetup.targetDailyPcs.toString()) }
  var targetEffText by remember(selectedLine) { mutableStateOf(currentSetup.targetEfficiencyPct.toString()) }
  var operatorsText by remember(selectedLine) { mutableStateOf(currentSetup.operatorsCount.toString()) }
  var machinesText by remember(selectedLine) { mutableStateOf(currentSetup.machinesCount.toString()) }
  var helpersText by remember(selectedLine) { mutableStateOf(currentSetup.helpersCount.toString()) }
  var finishingText by remember(selectedLine) { mutableStateOf(currentSetup.finishingStaffCount.toString()) }
  var includeHelpers by remember(selectedLine) { mutableStateOf(currentSetup.includeHelpersInEfficiency) }

  var saveSuccessMsg by remember { mutableStateOf<String?>(null) }
  var showBatchDialog by remember { mutableStateOf(false) }

  // Filter lines by selected floor
  val floorLines = remember(selectedFloor) {
    repository.allLinesMaster.filter {
      selectedFloor == FloorScope.ALL || it.floor == selectedFloor.apiValue
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Morning Line Target Setup",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = "সকালবেলার লাইন টার্গেট, এসএমবি (SMV) ও এফিসিয়েন্সি সেটআপ",
              color = Color(0xFFBAE6FD),
              fontSize = 10.sp
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("setup_back_btn")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White
            )
          }
        },
        actions = {
          if (canMorningTargetWrite) {
            IconButton(onClick = { showBatchDialog = true }) {
              Icon(Icons.Default.AutoAwesome, contentDescription = "Batch Setup", tint = Color(0xFFFDE047))
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NflRoyalBlue)
      )
    },
    containerColor = Color(0xFFF8FAFC)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Floor Selector Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.ALL).forEach { floor ->
          val isSelected = selectedFloor == floor
          Surface(
            onClick = { selectedFloor = floor },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF0F172A) else Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
            modifier = Modifier.weight(1f)
          ) {
            Column(
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = when (floor) {
                  FloorScope.SEW_1 -> "Sew-1"
                  FloorScope.SEW_2 -> "Sew-2"
                  FloorScope.SEW_3 -> "Sew-3"
                  FloorScope.ALL -> "All"
                  else -> floor.displayName
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color(0xFF334155)
              )
              Text(
                text = when (floor) {
                  FloorScope.SEW_1 -> "24 Lines"
                  FloorScope.SEW_2 -> "16 Lines"
                  FloorScope.SEW_3 -> "26 Lines"
                  FloorScope.ALL -> "66 Lines"
                  else -> ""
                },
                fontSize = 8.sp,
                color = if (isSelected) Color(0xFF94A3B8) else Color(0xFF64748B)
              )
            }
          }
        }
      }

      if (!canMorningTargetWrite) {
        Surface(
          color = Color(0xFFFEF3C7),
          shape = RoundedCornerShape(8.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
            Column {
              Text("View-Only Mode (টার্গেট সেটআপ লক করা)", color = Color(0xFF92400E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
              Text("You have read-only access. Only floor supervisors & IE can adjust line targets and SMV. (Management has view-only access).", color = Color(0xFFB45309), fontSize = 10.sp)
            }
          }
        }
      }

      // 2. Morning Target Editor Card for Selected Line
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
              Text(
                text = "Target Configuration: Line $selectedLine",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
              )
            }

            // Line selector dropdown
            Box {
              OutlinedButton(
                onClick = { showLineDropdown = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text("Change Line", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
              DropdownMenu(
                expanded = showLineDropdown,
                onDismissRequest = { showLineDropdown = false }
              ) {
                floorLines.forEach { line ->
                  DropdownMenuItem(
                    text = { Text("Line ${line.id} (${line.floor})") },
                    onClick = {
                      selectedLine = line.id
                      showLineDropdown = false
                    }
                  )
                }
              }
            }
          }

          // Quick Item Selection Chips
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Garment Item Presets (আইটেম সিলেক্ট করুন):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              garmentItems.forEach { item ->
                Surface(
                  onClick = {
                    itemNameText = item
                  },
                  shape = RoundedCornerShape(6.dp),
                  color = if (itemNameText == item) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                  border = androidx.compose.foundation.BorderStroke(1.dp, if (itemNameText == item) Color(0xFF3B82F6) else Color(0xFFE2E8F0))
                ) {
                  Text(
                    text = item,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (itemNameText == item) Color(0xFF1D4ED8) else Color(0xFF475569),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }
              // Custom Item option
              Surface(
                onClick = { itemNameText = "" },
                shape = RoundedCornerShape(6.dp),
                color = if (itemNameText !in garmentItems && itemNameText.isNotBlank()) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (itemNameText !in garmentItems && itemNameText.isNotBlank()) Color(0xFF3B82F6) else Color(0xFFE2E8F0))
              ) {
                Text(
                  text = "✏️ Custom",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (itemNameText !in garmentItems && itemNameText.isNotBlank()) Color(0xFF1D4ED8) else Color(0xFF475569),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          // Row 1: Item Name & SMV
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = itemNameText,
              onValueChange = { itemNameText = it },
              label = { Text("Item Name (আইটেমের নাম)") },
              placeholder = { Text("e.g. Polo Shirt") },
              modifier = Modifier.weight(1.5f),
              singleLine = true
            )

            OutlinedTextField(
              value = smvText,
              onValueChange = { smvText = it },
              label = { Text("SMV (মিনিট)") },
              placeholder = { Text("e.g. 14.50") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
          }

          // Row 2: Target Daily Pieces & Target Efficiency %
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = targetDailyPcsText,
              onValueChange = { targetDailyPcsText = it },
              label = { Text("Target Output (Pcs)") },
              placeholder = { Text("e.g. 480") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
              value = targetEffText,
              onValueChange = { targetEffText = it },
              label = { Text("Target Efficiency (%)") },
              placeholder = { Text("e.g. 75.0") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
          }

          // Row 3: Manpower & Machines (Operators, Running Machines, Helpers)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = operatorsText,
              onValueChange = { operatorsText = it },
              label = { Text("Operators (অপারেটর)") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
              value = machinesText,
              onValueChange = { machinesText = it },
              label = { Text("Machines (মেশিন)") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
              value = helpersText,
              onValueChange = { helpersText = it },
              label = { Text("Helpers (হেলপার)") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
          }

          // Helpers Policy Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Include Helpers in Available Minutes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              Text("Off: Manpower = Operators | On: Manpower = Ops + Helpers", fontSize = 9.sp, color = Color(0xFF64748B))
            }
            Switch(checked = includeHelpers, onCheckedChange = { includeHelpers = it })
          }

          // IE Calculation Verification Card
          val smvVal = smvText.toDoubleOrNull() ?: 14.50
          val targetVal = targetDailyPcsText.toIntOrNull() ?: 480
          val opsVal = operatorsText.toIntOrNull() ?: 20
          val theoreticalEff = if (opsVal > 0 && smvVal > 0) {
            val earned = targetVal * smvVal
            val avail = opsVal * 8 * 60
            ((earned / avail) * 100).let { Math.round(it * 10.0) / 10.0 }
          } else 0.0

          // BS OQL (Buyer Standard OQL - IE Entry Benchmark)
          val floorMonthlyTarget = repository.getMonthlyTarget(selectedFloor.apiValue)
          Surface(
            color = Color(0xFFF0F9FF),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBAE6FD)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("BS OQL (Buyer Standard) — IE Entry:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                Text("Standard Benchmark: ${String.format(java.util.Locale.US, "%.2f", floorMonthlyTarget.bsOqlPct)}% (Tolerance: ≤ 2.50%)", fontSize = 9.sp, color = Color(0xFF0284C7))
              }
              Surface(
                color = Color(0xFF0284C7),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "BS OQL: ${floorMonthlyTarget.bsOqlPct}%",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("IE Calculation Verification:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                Text(
                  text = "At $targetVal pcs target & $smvVal SMV, needed efficiency is $theoreticalEff%",
                  fontSize = 9.sp,
                  color = Color(0xFF64748B)
                )
              }
              Surface(
                color = if (Math.abs(theoreticalEff - (targetEffText.toDoubleOrNull() ?: 75.0)) <= 10.0) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "IE VERIFIED",
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF166534),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          // Success Message Banner
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

          // Save Button
          Button(
            onClick = {
              if (!canMorningTargetWrite) return@Button
              val lineObj = repository.allLinesMaster.find { it.id == selectedLine }
              val floorName = lineObj?.floor ?: selectedFloor.apiValue

              val setup = SupervisorLineSetup(
                lineId = selectedLine,
                floor = floorName,
                date = repository.getCurrentBangladeshDateIso(),
                itemName = itemNameText.trim().ifEmpty { "Polo Shirt" },
                smv = smvText.toDoubleOrNull() ?: 14.50,
                targetDailyPcs = targetDailyPcsText.toIntOrNull() ?: 480,
                targetHourlyPcs = ((targetDailyPcsText.toIntOrNull() ?: 480) / 8).coerceAtLeast(1),
                targetEfficiencyPct = targetEffText.toDoubleOrNull() ?: 75.0,
                operatorsCount = operatorsText.toIntOrNull() ?: 20,
                machinesCount = machinesText.toIntOrNull() ?: (operatorsText.toIntOrNull() ?: 20),
                helpersCount = helpersText.toIntOrNull() ?: 2,
                finishingStaffCount = finishingText.toIntOrNull() ?: 4,
                includeHelpersInEfficiency = includeHelpers,
                workingMinutesPerHour = 60,
                workingHoursSoFar = 7,
                totalShiftHours = 8,
                snapshotTimestamp = System.currentTimeMillis()
              )
              repository.saveLineSetup(setup)
              saveSuccessMsg = "✓ Line $selectedLine Morning Target Saved (${setup.date}): ${setup.itemName} (SMV: ${setup.smv}), Target: ${setup.targetDailyPcs} pcs @ ${setup.targetEfficiencyPct}% Eff!"
            },
            enabled = canMorningTargetWrite,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("save_line_setup_btn"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue)
          ) {
            Icon(if (canMorningTargetWrite) Icons.Default.Check else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (canMorningTargetWrite) "Save Morning Target & Efficiency" else "🔒 View-Only Mode (Morning Target Disabled)",
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }

  // Batch setup dialog
  if (showBatchDialog) {
    AlertDialog(
      onDismissRequest = { showBatchDialog = false },
      title = { Text("Apply Standard Targets to Floor", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Text(
          text = "Apply standard target (480 Pcs, 75% Target Efficiency, 20 Operators) across all lines in ${selectedFloor.displayName}?",
          fontSize = 13.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            floorLines.forEach { line ->
              repository.updateLineMorningTarget(
                lineId = line.id,
                floor = line.floor,
                itemName = "Polo Shirt",
                smv = 14.50,
                targetDailyPcs = 480,
                targetEfficiencyPct = 75.0,
                operatorsCount = 20,
                helpersCount = 2
              )
            }
            showBatchDialog = false
            saveSuccessMsg = "✓ Standard 75% Target & 480 Pcs applied to all ${floorLines.size} lines in ${selectedFloor.displayName}!"
          },
          colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue)
        ) {
          Text("Apply to Floor")
        }
      },
      dismissButton = {
        TextButton(onClick = { showBatchDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
