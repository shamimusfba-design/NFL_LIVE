package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionEntryScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit,
  onViewHistory: () -> Unit,
  onNavigateToQuality: () -> Unit = {},
  onNavigateToMonthlySetup: () -> Unit = {},
  onNavigateToSetup: () -> Unit = {}
) {
  val repoFloor by repository.currentFloor.collectAsState()
  val repoAssignedLine by repository.assignedLine.collectAsState()
  val isOnline by repository.isOnline.collectAsState()
  val entries by repository.productionEntries.collectAsState()
  val allTargets by repository.targets.collectAsState()
  val syncStatusBanner by repository.syncStatusBanner.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == com.example.model.UserRole.ADMIN
  val isIeUser = currentUser?.role == com.example.model.UserRole.IE
  val isWithinTimeWindow = isAdminUser || isIeUser || repository.isWithinDailyEntryWindow()
  val hasRoleProdPermission = isAdminUser || isIeUser || (currentUser?.let {
    (it.canProductionWrite || it.canFinishingWrite) &&
        it.role in setOf(com.example.model.UserRole.SUPERVISOR, com.example.model.UserRole.PRODUCTION_REPORTER, com.example.model.UserRole.IE)
  } ?: false)
  val canWrite = hasRoleProdPermission && isWithinTimeWindow
  val canNavigateQuality = isAdminUser || isIeUser || (currentUser?.let {
    (it.canQualityWrite || it.canOqlWrite) &&
        it.role in setOf(com.example.model.UserRole.QUALITY_REPORTER, com.example.model.UserRole.AUDITOR, com.example.model.UserRole.IE)
  } ?: false)
  val canNavigateMonthly = isAdminUser || isIeUser || (currentUser?.let {
    it.canMonthlyTargetWrite && it.role == com.example.model.UserRole.IE
  } ?: false)
  val canNavigateMorningSetup = isAdminUser || isIeUser || (currentUser?.let {
    it.canMorningTargetWrite && it.role in setOf(
      com.example.model.UserRole.IE,
      com.example.model.UserRole.PRODUCTION_REPORTER
    )
  } ?: false)

  var selectedFloor by remember { mutableStateOf(if (repoFloor == FloorScope.ALL) FloorScope.SEW_2 else repoFloor) }
  val availableLines = remember(selectedFloor) {
    repository.allLinesMaster.filter { it.floor == selectedFloor.apiValue }
  }

  val currentBdDate = remember { repository.getCurrentBangladeshDateIso() }
  var selectedDate by remember { mutableStateOf(currentBdDate) }
  var selectedHour by remember { mutableStateOf("10:00 - 11:00") }
  var showHourDropdown by remember { mutableStateOf(false) }

  // Batch vs Single Line Mode Toggle (Batch is default for faster floor entry)
  var isBatchMode by remember { mutableStateOf(true) }

  // Map to hold batch outputs for each line: lineId -> pieces text (blank by default)
  val batchOutputs = remember(selectedFloor, selectedDate, selectedHour) {
    mutableStateMapOf<String, String>().apply {
      availableLines.forEach { line ->
        val existingReal = entries.find { !it.isDemo && it.lineId.equals(line.id, ignoreCase = true) && it.date == selectedDate && it.hourSlot == selectedHour }
        if (existingReal != null && (existingReal.outputQty > 0 || existingReal.isConfirmedZero)) {
          this[line.id] = existingReal.outputQty.toString()
        } else {
          this[line.id] = ""
        }
      }
    }
  }

  LaunchedEffect(entries, selectedFloor, selectedDate, selectedHour) {
    availableLines.forEach { line ->
      val existingReal = entries.find { !it.isDemo && it.lineId.equals(line.id, ignoreCase = true) && it.date == selectedDate && it.hourSlot == selectedHour }
      if (existingReal != null && (existingReal.outputQty > 0 || existingReal.isConfirmedZero)) {
        batchOutputs[line.id] = existingReal.outputQty.toString()
      } else {
        batchOutputs[line.id] = ""
      }
    }
  }

  // Single line mode states
  var selectedSingleLine by remember(selectedFloor) {
    mutableStateOf(
      if (availableLines.any { it.id == repoAssignedLine }) repoAssignedLine
      else availableLines.firstOrNull()?.id ?: "K1"
    )
  }
  var showLineDropdown by remember { mutableStateOf(false) }
  var singleOutputQtyText by remember { mutableStateOf("") }
  var singleRemarksText by remember { mutableStateOf("") }

  val existingSingleReal = remember(entries, selectedSingleLine, selectedDate, selectedHour) {
    entries.find {
      !it.isDemo &&
      it.lineId.equals(selectedSingleLine, ignoreCase = true) &&
      it.date == selectedDate &&
      it.hourSlot == selectedHour
    }
  }

  LaunchedEffect(selectedSingleLine, selectedDate, selectedHour, existingSingleReal?.timestamp, existingSingleReal?.revision) {
    if (existingSingleReal != null) {
      singleOutputQtyText = if (existingSingleReal.outputQty > 0 || existingSingleReal.isConfirmedZero) existingSingleReal.outputQty.toString() else ""
      singleRemarksText = existingSingleReal.remarks
    } else {
      singleOutputQtyText = ""
      singleRemarksText = ""
    }
  }

  var submissionSuccessMsg by remember { mutableStateOf<String?>(null) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val singleLineSetup = remember(selectedSingleLine, allTargets) { repository.getLineSetup(selectedSingleLine) }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
          Column {
            Text(
              text = "Production Entry",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text(
              text = "Hourly Production Output Data Entry",
              color = Color(0xFFBAE6FD),
              fontSize = 10.sp
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("prod_back_btn")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White
            )
          }
        },
        actions = {
          IconButton(onClick = onViewHistory) {
            Icon(Icons.Default.History, contentDescription = "History", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = NflRoyalBlue
        )
      )
    },
    containerColor = Color(0xFFF0F4F8)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. TOP MODULE NAVIGATION TABS (Strictly gated by user write permissions)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White, RoundedCornerShape(10.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // Tab 1: Hourly Production (Active)
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF0F172A),
          modifier = Modifier.weight(1f)
        ) {
          Text(
            text = "🧵 Production",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 7.dp),
            maxLines = 1
          )
        }

        // Tab 2: Quality Entry (Only if permitted)
        if (canNavigateQuality) {
          Surface(
            onClick = onNavigateToQuality,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = "🔍 Quality",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF475569),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 7.dp),
              maxLines = 1
            )
          }
        }

        // Tab 3: Monthly Data (Only if permitted)
        if (canNavigateMonthly) {
          Surface(
            onClick = onNavigateToMonthlySetup,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = "📅 Monthly",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF475569),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 7.dp),
              maxLines = 1
            )
          }
        }

        // Tab 4: Target Setup (Only if permitted)
        if (canNavigateMorningSetup) {
          Surface(
            onClick = onNavigateToSetup,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = "🌅 Targets",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF475569),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 7.dp),
              maxLines = 1
            )
          }
        }
      }

      // 2. Floor Selector Chips (Sew 1, Sew 2, Sew 3, Finishing)
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Select Factory Floor / Section:",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF475569)
            )
            Text(
              text = "${availableLines.size} Lines Available",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0284C7)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3).forEach { floor ->
              val isSelected = selectedFloor == floor
              Surface(
                onClick = {
                  selectedFloor = floor
                  submissionSuccessMsg = null
                  validationError = null
                },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = when (floor) {
                    FloorScope.SEW_1 -> "Sew-1"
                    FloorScope.SEW_2 -> "Sew-2"
                    FloorScope.SEW_3 -> "Sew-3"
                    else -> floor.displayName
                  },
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) Color.White else Color(0xFF334155),
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(vertical = 8.dp)
                )
              }
            }
          }

          if (!canWrite) {
            Surface(
              color = Color(0xFFFEF3C7),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                Column {
                  Text("View-Only Mode (Data Entry Locked)", color = Color(0xFF92400E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  Text("You have read-only access. Only authorized production reporters can enter records. (Management has view-only access).", color = Color(0xFFB45309), fontSize = 10.sp)
                }
              }
            }
          }
        }
      }

      // 3. Date & Hour Slot Selector Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Date box
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Reporting Date", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFF8FAFC),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(15.dp))
                Text(selectedDate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
              }
            }
          }

          // Hour slot box with dropdown
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hour Slot", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
            Box {
              Surface(
                onClick = { showHourDropdown = true },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEFF6FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(15.dp))
                    Text(selectedHour, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E3A8A))
                  }
                  Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                }
              }

              DropdownMenu(
                expanded = showHourDropdown,
                onDismissRequest = { showHourDropdown = false },
                modifier = Modifier.background(Color.White)
              ) {
                repository.defaultHourSlots.forEach { slot ->
                  DropdownMenuItem(
                    text = { Text(slot, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                    onClick = {
                      selectedHour = slot
                      showHourDropdown = false
                      submissionSuccessMsg = null
                    }
                  )
                }
              }
            }
          }
        }
      }

      // 4. Entry Mode Selector (Batch All Lines vs Single Line)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Surface(
          onClick = { isBatchMode = true },
          shape = RoundedCornerShape(6.dp),
          color = if (isBatchMode) Color(0xFF0F172A) else Color.Transparent,
          modifier = Modifier.weight(1f)
        ) {
          Text(
            text = "⚡ Batch All Lines (${availableLines.size} Lines)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isBatchMode) Color.White else Color(0xFF475569),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 7.dp)
          )
        }

        Surface(
          onClick = { isBatchMode = false },
          shape = RoundedCornerShape(6.dp),
          color = if (!isBatchMode) Color(0xFF0F172A) else Color.Transparent,
          modifier = Modifier.weight(1f)
        ) {
          Text(
            text = "✏️ Single Line Entry",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (!isBatchMode) Color.White else Color(0xFF475569),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 7.dp)
          )
        }
      }

      // Success / Sync Status Feedback Banner
      AnimatedVisibility(visible = submissionSuccessMsg != null) {
        val activeMsg = (syncStatusBanner ?: "").ifBlank { submissionSuccessMsg ?: "" }
        val isWarning = activeMsg.startsWith("⚠️") || activeMsg.startsWith("🔒")
        val isSaving = activeMsg.startsWith("⏳") || activeMsg.startsWith("🔄")
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = when {
            isWarning -> Color(0xFFFEF3C7)
            isSaving -> Color(0xFFE0F2FE)
            else -> Color(0xFFDCFCE7)
          },
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
              isWarning -> Color(0xFFF59E0B)
              isSaving -> Color(0xFF38BDF8)
              else -> Color(0xFF86EFAC)
            }
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
              imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
              contentDescription = null,
              tint = when {
                isWarning -> Color(0xFFD97706)
                isSaving -> Color(0xFF0284C7)
                else -> Color(0xFF16A34A)
              },
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = activeMsg,
              color = when {
                isWarning -> Color(0xFF92400E)
                isSaving -> Color(0xFF0369A1)
                else -> Color(0xFF166534)
              },
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      AnimatedVisibility(visible = validationError != null) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFFEE2E2),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            Text(validationError ?: "", color = Color(0xFF991B1B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // =========================================================================
      // MODE A: BATCH ALL LINES FOR THE FLOOR (Solved Item 2: no 14x line switching)
      // =========================================================================
      if (isBatchMode) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(2.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "${selectedFloor.displayName} — All Lines Production",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = "Enter pieces produced during slot $selectedHour",
                  fontSize = 10.sp,
                  color = Color(0xFF64748B)
                )
              }
              Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(4.dp)) {
                Text(
                  text = "$selectedHour",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFF0369A1),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }

            // Lines Entry List
            availableLines.forEachIndexed { index, line ->
              val setup = repository.getLineSetup(line.id)
              val isEven = index % 2 == 0
              val isSlotLocked = !isAdminUser && entries.any {
                !it.isDemo && it.lineId == line.id && it.date == selectedDate && it.hourSlot == selectedHour
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSlotLocked) Color(0xFFF1F5F9) else if (isEven) Color(0xFFF8FAFC) else Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  // Line & Style Info
                  Column(modifier = Modifier.weight(1.3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Surface(color = Color(0xFF1E3A8A), shape = RoundedCornerShape(4.dp)) {
                        Text(
                          text = "Line ${line.id}",
                          color = Color.White,
                          fontWeight = FontWeight.Black,
                          fontSize = 11.sp,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                      Text(
                        text = setup.itemName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      if (isSlotLocked) {
                        Text(
                          text = "🔒 Locked",
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFFB45309)
                        )
                      }
                    }
                    Text(
                      text = "Tgt: ${setup.targetHourlyPcs} pcs/hr • SMV: ${String.format("%.2f", setup.smv)}",
                      fontSize = 9.sp,
                      color = Color(0xFF64748B)
                    )
                  }

                  // Direct Output Input Field for this Line
                  OutlinedTextField(
                    value = batchOutputs[line.id] ?: "",
                    onValueChange = { input ->
                      if (canWrite && !isSlotLocked && input.all { it.isDigit() }) {
                        batchOutputs[line.id] = input
                        validationError = null
                      }
                    },
                    enabled = canWrite && !isSlotLocked,
                    placeholder = { Text(if (isSlotLocked) "Locked" else "Pcs", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = com.example.ui.theme.nflInputColors(),
                    textStyle = LocalTextStyle.current.copy(
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Black,
                      textAlign = TextAlign.Center,
                      color = Color(0xFF0F172A)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(105.dp),
                    shape = RoundedCornerShape(6.dp)
                  )
                }
              }
            }

            // ONE BUTTON SUBMISSION FOR ALL LINES
            Button(
              onClick = {
                if (!canWrite) {
                  validationError = if (!isWithinTimeWindow) {
                    "Daily entry is only allowed between 06:00 AM and midnight Bangladesh time."
                  } else {
                    "You do not have permission for Production Entry."
                  }
                  return@Button
                }
                val records = availableLines.mapNotNull { line ->
                  val isLocked = !isAdminUser && entries.any {
                    !it.isDemo && it.lineId == line.id && it.date == selectedDate && it.hourSlot == selectedHour
                  }
                  val qty = batchOutputs[line.id]?.toIntOrNull()
                  if (!isLocked && qty != null && qty >= 0) {
                    val setup = repository.getLineSetup(line.id)
                    ProductionRecord(
                      lineId = line.id,
                      floor = selectedFloor.apiValue,
                      date = selectedDate,
                      hourSlot = selectedHour,
                      styleNumber = setup.itemName,
                      outputQty = qty,
                      remarks = "Batch reporter entry",
                      isConfirmedZero = (qty == 0)
                    )
                  } else null
                }

                if (records.isEmpty()) {
                  validationError = "Please enter output quantity for at least one unlocked line."
                } else {
                  repository.submitBatchProduction(records)
                  submissionSuccessMsg = "✓ Successfully saved hourly production for ${records.size} lines in ${selectedFloor.displayName} ($selectedHour)!"
                  validationError = null
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("submit_all_lines_button"),
              enabled = canWrite,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (canWrite) NflRoyalBlue else Color(0xFF94A3B8)
              )
            ) {
              Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (canWrite) "Submit All ${availableLines.size} Lines Production ($selectedHour)" else "🔒 Entry Locked (View Only)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        }
      } else {
        // =========================================================================
        // MODE B: SINGLE LINE ENTRY (Traditional Fallback)
        // =========================================================================
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(2.dp),
          border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Line Selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box {
                OutlinedCard(
                  onClick = { showLineDropdown = true },
                  shape = RoundedCornerShape(8.dp),
                  colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF0FDF4)),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = "Line: $selectedSingleLine",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Black,
                      color = Color(0xFF14532D)
                    )
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF14532D))
                  }
                }

                DropdownMenu(
                  expanded = showLineDropdown,
                  onDismissRequest = { showLineDropdown = false },
                  modifier = Modifier.background(Color.White)
                ) {
                  availableLines.forEach { line ->
                    DropdownMenuItem(
                      text = { Text("Line ${line.id} (${selectedFloor.displayName})", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
                      onClick = {
                        selectedSingleLine = line.id
                        showLineDropdown = false
                      }
                    )
                  }
                }
              }

              Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(4.dp)) {
                Text(
                  text = "Target: ${singleLineSetup.targetHourlyPcs} pcs",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1D4ED8),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }

            // Running Item Card
            Surface(
              color = Color(0xFFF8FAFC),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(text = "Running: ${singleLineSetup.itemName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                  Text(text = "SMV: ${singleLineSetup.smv} • Target Eff: ${singleLineSetup.targetEfficiencyPct.toInt()}%", fontSize = 10.sp, color = Color(0xFF64748B))
                }
              }
            }

            val isSingleSlotLocked = !isAdminUser && entries.any {
              !it.isDemo && it.lineId == selectedSingleLine && it.date == selectedDate && it.hourSlot == selectedHour
            }

            // Hourly Output Input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Hourly Output (Pieces)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF263238))
              OutlinedTextField(
                value = singleOutputQtyText,
                onValueChange = { input ->
                  if (canWrite && !isSingleSlotLocked && input.all { it.isDigit() }) {
                    singleOutputQtyText = input
                    validationError = null
                  }
                },
                enabled = canWrite && !isSingleSlotLocked,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("single_hourly_output_input"),
                colors = com.example.ui.theme.nflInputColors(),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
              )
            }

            // Remarks
            OutlinedTextField(
              value = singleRemarksText,
              onValueChange = { if (canWrite && !isSingleSlotLocked) singleRemarksText = it },
              enabled = canWrite && !isSingleSlotLocked,
              placeholder = { Text("Downtime / Remarks (Optional)", fontSize = 12.sp, color = Color(0xFF64748B)) },
              modifier = Modifier.fillMaxWidth(),
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A)),
              singleLine = true,
              shape = RoundedCornerShape(8.dp)
            )

            // Submit Button
            Button(
              onClick = {
                if (!canWrite || isSingleSlotLocked) {
                  validationError = if (isSingleSlotLocked) {
                    "🔒 Line $selectedSingleLine ($selectedHour) is already submitted. Only Admin can modify submitted records with a reason."
                  } else {
                    "You do not have permission or are outside the 06:00 AM–Midnight BD entry window."
                  }
                  return@Button
                }
                val qty = singleOutputQtyText.toIntOrNull()
                if (qty == null || qty < 0) {
                  validationError = "Please enter a valid output quantity."
                  return@Button
                }

                val record = ProductionRecord(
                  lineId = selectedSingleLine,
                  floor = selectedFloor.apiValue,
                  date = selectedDate,
                  hourSlot = selectedHour,
                  styleNumber = singleLineSetup.itemName,
                  outputQty = qty,
                  remarks = singleRemarksText,
                  isConfirmedZero = (qty == 0)
                )
                repository.submitProduction(record)
                submissionSuccessMsg = "✓ Output saved for Line $selectedSingleLine ($selectedHour: $qty pcs)!"
                validationError = null
              },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              enabled = canWrite && !isSingleSlotLocked,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (canWrite && !isSingleSlotLocked) NflRoyalBlue else Color(0xFF94A3B8)
              )
            ) {
              Text(
                when {
                  isSingleSlotLocked -> "🔒 RECORD SUBMITTED & LOCKED (ADMIN ONLY)"
                  canWrite -> "SUBMIT PRODUCTION"
                  else -> "🔒 ENTRY LOCKED (VIEW ONLY)"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
          }
        }
      }
    }
  }
}
