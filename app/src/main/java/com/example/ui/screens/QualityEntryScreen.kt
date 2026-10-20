package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityEntryScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit,
  onViewHistory: () -> Unit,
  onNavigateToProduction: () -> Unit = {},
  onNavigateToMonthlySetup: () -> Unit = {},
  onNavigateToSetup: () -> Unit = {}
) {
  val repoFloor by repository.currentFloor.collectAsState()
  val repoAssignedLine by repository.assignedLine.collectAsState()
  val isOnline by repository.isOnline.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == com.example.model.UserRole.ADMIN
  val isWithinTimeWindow = isAdminUser || repository.isWithinDailyEntryWindow()
  val hasRoleQualityPermission = isAdminUser || (currentUser?.let {
    (it.canQualityWrite || it.canOqlWrite) &&
        it.role in setOf(com.example.model.UserRole.QUALITY_REPORTER, com.example.model.UserRole.AUDITOR)
  } ?: false)
  val canQualityWrite = hasRoleQualityPermission && isWithinTimeWindow
  val canNavigateProd = isAdminUser || (currentUser?.let {
    (it.canProductionWrite || it.canFinishingWrite) &&
        it.role in setOf(com.example.model.UserRole.SUPERVISOR, com.example.model.UserRole.PRODUCTION_REPORTER)
  } ?: false)
  val canNavigateMonthly = isAdminUser || (currentUser?.let {
    it.canMonthlyTargetWrite && it.role == com.example.model.UserRole.IE
  } ?: false)
  val canNavigateMorningSetup = isAdminUser || (currentUser?.let {
    it.canMorningTargetWrite && it.role !in setOf(
      com.example.model.UserRole.MANAGEMENT_VIEW,
      com.example.model.UserRole.PRODUCTION_MANAGER,
      com.example.model.UserRole.QUALITY_MANAGER,
      com.example.model.UserRole.IE,
      com.example.model.UserRole.QUALITY_REPORTER
    )
  } ?: false)
  val entries by repository.qualityEntries.collectAsState()
  val allTargets by repository.targets.collectAsState()
  val syncStatusBanner by repository.syncStatusBanner.collectAsState()

  var selectedFloor by remember { mutableStateOf(if (repoFloor == FloorScope.ALL) FloorScope.SEW_2 else repoFloor) }
  val availableLines = remember(selectedFloor) {
    repository.allLinesMaster.filter { it.floor == selectedFloor.apiValue }
  }
  var selectedLine by remember(selectedFloor) {
    mutableStateOf(
      if (availableLines.any { it.id == repoAssignedLine }) repoAssignedLine
      else availableLines.firstOrNull()?.id ?: "K1"
    )
  }
  var showLineDropdown by remember { mutableStateOf(false) }

  val currentBdDate = remember { repository.getCurrentBangladeshDateIso() }
  var selectedDate by remember { mutableStateOf(currentBdDate) }
  var selectedHour by remember { mutableStateOf("10:00 - 11:00") }
  var selectedStyle by remember { mutableStateOf("NFL-204") }
  var checkedGarmentsText by remember { mutableStateOf("150") }
  var defectiveGarmentsText by remember { mutableStateOf("5") }
  var defectSentText by remember { mutableStateOf("14") }
  var defectReceivedText by remember { mutableStateOf("12") }
  var spotSentText by remember { mutableStateOf("8") }
  var spotReceivedText by remember { mutableStateOf("7") }

  val lineSetup = remember(selectedLine, allTargets) { repository.getLineSetup(selectedLine) }

  // Defects list state
  var defectRows by remember {
    mutableStateOf(
      listOf(
        DefectItem("Skip Stitch", 3),
        DefectItem("Uncut Thread", 4)
      )
    )
  }

  var remarksText by remember { mutableStateOf("") }
  var showHourDropdown by remember { mutableStateOf(false) }
  var showStyleDropdown by remember { mutableStateOf(false) }
  var submissionSuccessMsg by remember { mutableStateOf<String?>(null) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val totalDefects = defectRows.sumOf { it.count }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Quality Entry",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("quality_back_btn")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White
            )
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
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Top Module Navigation Tabs (Strictly gated by user write permissions)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White, RoundedCornerShape(10.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (canNavigateProd) {
          Surface(
            onClick = onNavigateToProduction,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = "🧵 Production",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF475569),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              modifier = Modifier.padding(vertical = 7.dp),
              maxLines = 1
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF0F172A),
          modifier = Modifier.weight(1f)
        ) {
          Text(
            text = "🔍 Quality",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 7.dp),
            maxLines = 1
          )
        }
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
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              modifier = Modifier.padding(vertical = 7.dp),
              maxLines = 1
            )
          }
        }
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
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              modifier = Modifier.padding(vertical = 7.dp),
              maxLines = 1
            )
          }
        }
      }

      // 1. Floor Selector Chips (Sew 1, Sew 2, Sew 3, Finishing)
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Select Factory Floor / Section:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569)
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3).forEach { floor ->
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
                    FloorScope.SEW_1 -> "Sew-1"
                    FloorScope.SEW_2 -> "Sew-2"
                    FloorScope.SEW_3 -> "Sew-3"
                    else -> floor.displayName
                  },
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) Color.White else Color(0xFF334155),
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  modifier = Modifier.padding(vertical = 8.dp)
                )
              }
            }
          }

          if (!canQualityWrite) {
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
                  Text("View-Only Mode (কোয়ালিটি এন্ট্রি লক করা)", color = Color(0xFF92400E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  Text("You have read-only access. Only authorized QC Inspectors & Auditors can enter defect records. (Management has view-only access).", color = Color(0xFFB45309), fontSize = 10.sp)
                }
              }
            }
          }
        }
      }

      // Main Card matching Image 1 Right
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Top Info Row: Line Selector & Online dot
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Dynamic Line Dropdown for Selected Floor
            Box {
              OutlinedCard(
                onClick = { showLineDropdown = true },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFEFF6FF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD))
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text(
                    text = "Line: $selectedLine",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E40AF)
                  )
                  Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF1E40AF)
                  )
                }
              }

              DropdownMenu(
                expanded = showLineDropdown,
                onDismissRequest = { showLineDropdown = false }
              ) {
                availableLines.forEach { line ->
                  DropdownMenuItem(
                    text = { Text("Line ${line.id} (${selectedFloor.displayName})", fontWeight = FontWeight.SemiBold) },
                    onClick = {
                      selectedLine = line.id
                      showLineDropdown = false
                    }
                  )
                }
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Box(
                modifier = Modifier
                  .size(9.dp)
                  .clip(CircleShape)
                  .background(if (isOnline) NflGreenBright else NflAmber)
              )
              Text(
                text = if (isOnline) "Online" else "Offline Queue",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOnline) NflGreen else NflAmber
              )
            }
          }

          // Date & Hour Slot Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // Date box
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Date", fontSize = 12.sp, color = Color(0xFF78909C))
              OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC))
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Color(0xFF546E7A),
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = selectedDate,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF263238)
                  )
                }
              }
            }

            // Hour box with Dropdown
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Hour", fontSize = 12.sp, color = Color(0xFF78909C))
              Box {
                OutlinedCard(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHourDropdown = true }
                    .testTag("quality_hour_picker_btn"),
                  shape = RoundedCornerShape(8.dp),
                  colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC))
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF546E7A),
                        modifier = Modifier.size(16.dp)
                      )
                      Text(
                        text = selectedHour,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF263238)
                      )
                    }
                    Icon(
                      imageVector = Icons.Default.KeyboardArrowDown,
                      contentDescription = null,
                      tint = Color(0xFF546E7A),
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }

                DropdownMenu(
                  expanded = showHourDropdown,
                  onDismissRequest = { showHourDropdown = false }
                ) {
                  repository.defaultHourSlots.forEach { slot ->
                    DropdownMenuItem(
                      text = { Text(slot) },
                      onClick = {
                        selectedHour = slot
                        showHourDropdown = false
                      }
                    )
                  }
                }
              }
            }
          }

          // Morning Line Target & Item Info Banner
          Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Item: ${lineSetup.itemName}",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = "SMV: ${String.format("%.2f", lineSetup.smv)} min • Target: ${lineSetup.targetHourlyPcs} pcs/hr",
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )
              }
              Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                Text(
                  text = "Quality Audit",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFF166534),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }
          }

          // Checked Garments (pcs) & Defective Garments (pcs) Side-by-Side (Matches Screenshot)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Checked Garments (pcs)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF263238)
              )
              OutlinedTextField(
                value = checkedGarmentsText,
                onValueChange = { input ->
                  if (input.all { it.isDigit() }) {
                    checkedGarmentsText = input
                    validationError = null
                  }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("checked_garments_input"),
                textStyle = LocalTextStyle.current.copy(
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF102A43)
                ),
                shape = RoundedCornerShape(8.dp)
              )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Defective Garments (pcs)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF263238)
              )
              OutlinedTextField(
                value = defectiveGarmentsText,
                onValueChange = { input ->
                  if (input.all { it.isDigit() }) {
                    defectiveGarmentsText = input
                    validationError = null
                  }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("defective_garments_input"),
                textStyle = LocalTextStyle.current.copy(
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF102A43)
                ),
                shape = RoundedCornerShape(8.dp)
              )
            }
          }

          // Sewing Defect Rectification Tracking (সুইং ডিফেক্ট আদান-প্রদান ও রেক্টিফিকেশন)
          val liveSent = defectSentText.toIntOrNull() ?: 0
          val liveRecv = defectReceivedText.toIntOrNull() ?: 0
          val liveBal = (liveSent - liveRecv).coerceAtLeast(0)

          Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = "Sewing Defect Rectification (সুইং ডিফেক্ট ট্র্যাকিং)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                  )
                  Text(
                    text = "Hourly Sent to Line vs Received & Pending Balance",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                  )
                }

                Surface(
                  color = if (liveBal > 0) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    text = if (liveBal > 0) "Pending: $liveBal pcs" else "All Cleared",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (liveBal > 0) Color(0xFFDC2626) else Color(0xFF166534),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // 1. Defect Sent
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Defect Sent (সেন্ট)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEA580C)
                  )
                  OutlinedTextField(
                    value = defectSentText,
                    onValueChange = { if (it.all { ch -> ch.isDigit() }) defectSentText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFEA580C)
                    ),
                    shape = RoundedCornerShape(6.dp)
                  )
                }

                // 2. Defect Received
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Defect Recv (রিসিভ)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF16A34A)
                  )
                  OutlinedTextField(
                    value = defectReceivedText,
                    onValueChange = { if (it.all { ch -> ch.isDigit() }) defectReceivedText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF16A34A)
                    ),
                    shape = RoundedCornerShape(6.dp)
                  )
                }

                // 3. Defect Balance (Calculated read-only)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Balance (ব্যালেন্স)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (liveBal > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                  )
                  Surface(
                    color = if (liveBal > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (liveBal > 0) Color(0xFFFECACA) else Color(0xFFBBF7D0)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(56.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = "$liveBal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (liveBal > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                      )
                    }
                  }
                }
              }

              // Spot Defect Rectification Tracking (স্পট / দাগ ডিফেক্ট আদান-প্রদান ও রেক্টিফিকেশন)
              val liveSpotSent = spotSentText.toIntOrNull() ?: 0
              val liveSpotRecv = spotReceivedText.toIntOrNull() ?: 0
              val liveSpotBal = (liveSpotSent - liveSpotRecv).coerceAtLeast(0)

              Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = "Spot Defect Rectification (স্পট / দাগ ডিফেক্ট ট্র্যাকিং)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7E22CE)
                  )
                  Text(
                    text = "Hourly Sent to Spot Table vs Clean Received & Pending",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                  )
                }

                Surface(
                  color = if (liveSpotBal > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    text = if (liveSpotBal > 0) "Pending Spot: $liveSpotBal" else "Spot Cleared",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (liveSpotBal > 0) Color(0xFFDC2626) else Color(0xFF166534),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // 1. Spot Sent
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Spot Sent (স্পট সেন্ট)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7E22CE)
                  )
                  OutlinedTextField(
                    value = spotSentText,
                    onValueChange = { if (it.all { ch -> ch.isDigit() }) spotSentText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF7E22CE)
                    ),
                    shape = RoundedCornerShape(6.dp)
                  )
                }

                // 2. Spot Received
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Spot Recv (দাগ মুক্ত)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0D9488)
                  )
                  OutlinedTextField(
                    value = spotReceivedText,
                    onValueChange = { if (it.all { ch -> ch.isDigit() }) spotReceivedText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF0D9488)
                    ),
                    shape = RoundedCornerShape(6.dp)
                  )
                }

                // 3. Spot Balance
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Spot Bal (বকেয়া)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (liveSpotBal > 0) Color(0xFFDC2626) else Color(0xFF0D9488)
                  )
                  Surface(
                    color = if (liveSpotBal > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (liveSpotBal > 0) Color(0xFFFECACA) else Color(0xFFCCFBF1)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(56.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = "$liveSpotBal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (liveSpotBal > 0) Color(0xFFDC2626) else Color(0xFF0D9488)
                      )
                    }
                  }
                }
              }
            }
          }

          // Defects Section
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Defects",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF263238)
            )

            // Defect Rows
            defectRows.forEachIndexed { index, defectItem ->
              var showDefectTypeDropdown by remember { mutableStateOf(false) }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Defect type dropdown selector
                Box(modifier = Modifier.weight(1.3f)) {
                  OutlinedCard(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { showDefectTypeDropdown = true },
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = defectItem.defectType,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF263238)
                      )
                      Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF78909C),
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }

                  DropdownMenu(
                    expanded = showDefectTypeDropdown,
                    onDismissRequest = { showDefectTypeDropdown = false }
                  ) {
                    repository.standardDefectTypes.forEach { dType ->
                      DropdownMenuItem(
                        text = { Text(dType) },
                        onClick = {
                          defectRows = defectRows.mapIndexed { idx, itm ->
                            if (idx == index) itm.copy(defectType = dType) else itm
                          }
                          showDefectTypeDropdown = false
                        }
                      )
                    }
                    DropdownMenuItem(
                      text = { Text("✏️ + Custom Defect...", fontWeight = FontWeight.Bold, color = NflRoyalBlue) },
                      onClick = {
                        defectRows = defectRows.mapIndexed { idx, itm ->
                          if (idx == index) itm.copy(defectType = "Custom Defect") else itm
                        }
                        showDefectTypeDropdown = false
                      }
                    )
                  }
                }

                // Count input
                OutlinedTextField(
                  value = defectItem.count.toString(),
                  onValueChange = { newVal ->
                    val cnt = newVal.toIntOrNull() ?: 0
                    defectRows = defectRows.mapIndexed { idx, itm ->
                      if (idx == index) itm.copy(count = cnt) else itm
                    }
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier
                    .weight(0.7f)
                    .testTag("defect_count_input_$index"),
                  textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF102A43)
                  ),
                  shape = RoundedCornerShape(6.dp)
                )

                // Delete button
                IconButton(
                  onClick = {
                    defectRows = defectRows.filterIndexed { idx, _ -> idx != index }
                  },
                  modifier = Modifier.size(36.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove defect",
                    tint = Color(0xFF90A4AE),
                    modifier = Modifier.size(20.dp)
                  )
                }
              }

              if (defectItem.defectType !in repository.standardDefectTypes) {
                OutlinedTextField(
                  value = defectItem.defectType.replace("Custom Defect", ""),
                  onValueChange = { typedName ->
                    defectRows = defectRows.mapIndexed { idx, itm ->
                      if (idx == index) itm.copy(defectType = typedName.ifEmpty { "Custom Defect" }) else itm
                    }
                  },
                  label = { Text("Enter Custom Defect Name (কাস্টম ডিফেক্টের নাম লিখুন)") },
                  placeholder = { Text("e.g. Broken Needle, Slub, Pleat") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(6.dp)
                )
              }
            }

            // "+ Add defect" button
            TextButton(
              onClick = {
                defectRows = defectRows + DefectItem("Poor Iron", 1)
              },
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("add_defect_btn")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, tint = NflPrimaryBlue, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("+ Add defect", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NflPrimaryBlue)
            }

            // Explanatory footnote and Total defects count
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "One garment may have multiple defects.",
                fontSize = 11.sp,
                color = Color(0xFF78909C)
              )
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "Total defects:",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFF37474F)
                )
                Text(
                  text = "$totalDefects",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF102A43)
                )
              }
            }
          }

          // Optional remarks
          OutlinedTextField(
            value = remarksText,
            onValueChange = { remarksText = it },
            placeholder = { Text("Quality Remarks (Optional)", fontSize = 12.sp, color = Color(0xFF90A4AE)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
          )

          // Calculated live metrics banner
          val checkedInt = checkedGarmentsText.toIntOrNull() ?: 0
          val defectiveInt = defectiveGarmentsText.toIntOrNull() ?: 0
          if (checkedInt > 0) {
            val liveOql = (defectiveInt.toDouble() / checkedInt.toDouble()) * 100.0
            val liveDhu = (totalDefects.toDouble() / checkedInt.toDouble()) * 100.0
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFF1F8E9),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                Text(
                  text = "Computed OQL: ${"%.2f".format(liveOql)}%",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = NflGreen
                )
                Text(
                  text = "Computed DHU: ${"%.2f".format(liveDhu)}%",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF1B5E20)
                )
              }
            }
          }

          if (validationError != null) {
            Text(
              text = validationError ?: "",
              color = NflRed,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }

          if (submissionSuccessMsg != null) {
            val activeMsg = (syncStatusBanner ?: "").ifBlank { submissionSuccessMsg ?: "" }
            val isWarning = activeMsg.startsWith("⚠️") || activeMsg.startsWith("🔒")
            val isSaving = activeMsg.startsWith("⏳") || activeMsg.startsWith("🔄")
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = when {
                isWarning -> Color(0xFFFEF3C7)
                isSaving -> Color(0xFFE0F2FE)
                else -> NflGreenLight
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = when {
                    isWarning -> Color(0xFFD97706)
                    isSaving -> Color(0xFF0284C7)
                    else -> NflGreen
                  },
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = activeMsg,
                  color = when {
                    isWarning -> Color(0xFF92400E)
                    isSaving -> Color(0xFF0369A1)
                    else -> NflGreen
                  },
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          val isSlotLocked = !isAdminUser && entries.any {
            !it.isDemo && it.lineId == selectedLine && it.date == selectedDate && it.hourSlot == selectedHour
          }

          // SUBMIT QUALITY Button (Solid Dark Green Rounded Button matching Image 1 Right)
          Button(
            onClick = {
              if (!canQualityWrite || isSlotLocked) {
                validationError = if (isSlotLocked) {
                  "🔒 Quality record for Line $selectedLine ($selectedHour) is already submitted. Only Admin can modify submitted records with a reason."
                } else {
                  "You do not have permission or are outside the 06:00 AM–Midnight BD entry window."
                }
                return@Button
              }
              val checked = checkedGarmentsText.toIntOrNull()
              val defective = defectiveGarmentsText.toIntOrNull()

              if (checked == null || checked <= 0) {
                validationError = "Please enter checked garment quantity greater than 0."
                return@Button
              }
              if (defective == null || defective < 0) {
                validationError = "Defective garments cannot be negative."
                return@Button
              }
              if (defective > checked) {
                validationError = "Defective garments ($defective) cannot exceed checked garments ($checked)."
                return@Button
              }

              val sSent = defectSentText.toIntOrNull() ?: 0
              val sRecv = defectReceivedText.toIntOrNull() ?: 0
              val sBal = (sSent - sRecv).coerceAtLeast(0)

              val spotS = spotSentText.toIntOrNull() ?: 0
              val spotR = spotReceivedText.toIntOrNull() ?: 0
              val spotB = (spotS - spotR).coerceAtLeast(0)

              val record = QualityRecord(
                lineId = selectedLine,
                floor = selectedFloor.apiValue,
                date = selectedDate,
                hourSlot = selectedHour,
                styleNumber = lineSetup.itemName,
                checkedGarments = checked,
                defectiveGarments = defective,
                defects = defectRows,
                remarks = remarksText,
                defectSent = sSent,
                defectReceived = sRecv,
                defectBalance = sBal,
                spotSent = spotS,
                spotReceived = spotR,
                spotBalance = spotB,
                isConfirmedZero = (defective == 0)
              )

              repository.submitQuality(record)
              val oqlVal = (defective.toDouble() / checked.toDouble()) * 100.0
              submissionSuccessMsg = "Quality submitted for Line $selectedLine! OQL: ${"%.2f".format(oqlVal)}% • Sewing: $sSent/$sRecv (Bal: $sBal) • Spot: $spotS/$spotR (Bal: $spotB)"
              validationError = null
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("submit_quality_button"),
            enabled = canQualityWrite && !isSlotLocked,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (canQualityWrite && !isSlotLocked) NflGreen else Color(0xFF94A3B8)
            )
          ) {
            Text(
              text = when {
                isSlotLocked -> "🔒 RECORD SUBMITTED & LOCKED (ADMIN ONLY)"
                canQualityWrite -> "SUBMIT QUALITY"
                else -> "🔒 ENTRY LOCKED (VIEW ONLY)"
              },
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          // "View my entries" link text below button
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
              text = "View my entries",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = NflPrimaryBlue,
              modifier = Modifier
                .clickable { onViewHistory() }
                .padding(vertical = 4.dp)
                .testTag("view_quality_entries_link")
            )
          }
        }
      }

      // Recent Quality Inspections Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Recent Inspections on $selectedLine", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263238))
          val lineQuality = entries.filter { it.lineId == selectedLine }.take(4)
          if (lineQuality.isEmpty()) {
            Text("No inspection records yet today.", fontSize = 12.sp, color = Color(0xFF90A4AE))
          } else {
            lineQuality.forEach { entry ->
              val oql = if (entry.checkedGarments > 0) (entry.defectiveGarments.toDouble() / entry.checkedGarments) * 100 else 0.0
              val dhu = if (entry.checkedGarments > 0) (entry.totalDefects.toDouble() / entry.checkedGarments) * 100 else 0.0

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("${entry.hourSlot} • ${entry.styleNumber}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF263238))
                  Text("Checked: ${entry.checkedGarments} • Defective: ${entry.defectiveGarments}", fontSize = 11.sp, color = Color(0xFF78909C))
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text("OQL ${"%.2f".format(oql)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (oql <= 5.0) NflGreen else NflRed)
                  Text("DHU ${"%.2f".format(dhu)}%", fontSize = 11.sp, color = Color(0xFF546E7A))
                }
              }
            }
          }
        }
      }
    }
  }
}
