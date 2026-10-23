package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NptSupervisorScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val incidents by repository.incidents.collectAsState()
  val userName by repository.currentUserName.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val canNptWrite = currentUser?.let { it.isAdmin || it.role == UserRole.IE || it.canNptWrite } ?: false

  var selectedTab by remember { mutableIntStateOf(0) } // 0: Active NPT, 1: NPT History
  var filterCategory by remember { mutableStateOf<String>("ALL") }
  var showFilterDropdown by remember { mutableStateOf(false) }

  var showReportNptDialog by remember { mutableStateOf(false) }
  var resolvingIncidentId by remember { mutableStateOf<String?>(null) }
  var resolutionNoteText by remember { mutableStateOf("") }
  var attendingIncidentId by remember { mutableStateOf<String?>(null) }
  var mechanicNameInput by remember { mutableStateOf(if (currentUser?.role == UserRole.MECHANIC) userName else "Jahangir (Mechanic)") }

  // Form State for Report NPT
  var formFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  val floorLines = remember(formFloor) {
    repository.allLinesMaster.filter { it.floor == formFloor.apiValue }
  }
  var formLineId by remember(formFloor) { mutableStateOf(floorLines.firstOrNull()?.id ?: "K1") }
  var showLineDropdown by remember { mutableStateOf(false) }

  val commonNptTypes = listOf(
    "Machine Breakdown",
    "Quality Issue",
    "Input N/A",
    "Operator N/A",
    "Power / Utility Outage",
    "Pattern / Marker Issue",
    "Custom / Other"
  )
  var formNptType by remember { mutableStateOf("Machine Breakdown") }
  var showNptTypeDropdown by remember { mutableStateOf(false) }
  var formCustomNptType by remember { mutableStateOf("") }

  val commonMachineTypes = listOf(
    "Single Needle Lockstitch (SNLS)",
    "Overlock 4-Thread (4T-OL)",
    "Flatlock Cylinder Bed",
    "Kansai Multi-Needle",
    "Buttonhole Machine",
    "Button Attach Machine",
    "Bartack Machine",
    "Feed of the Arm",
    "Other / Custom"
  )
  var formMachineType by remember { mutableStateOf("Single Needle Lockstitch (SNLS)") }
  var showMachineTypeDropdown by remember { mutableStateOf(false) }
  var formMachineRef by remember { mutableStateOf("SNLS-04") }
  var formProcessName by remember { mutableStateOf("Collar Attach") }
  var formDescription by remember { mutableStateOf("") }
  var formReportedCause by remember { mutableStateOf("") }

  // 1-second live countdown ticker
  var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1000)
      ticker = System.currentTimeMillis()
    }
  }

  val activeIncidents = remember(incidents, filterCategory) {
    incidents.filter { it.status != IncidentStatus.RESOLVED }.filter {
      filterCategory == "ALL" || it.nptType.equals(filterCategory, ignoreCase = true) || it.category.displayName.equals(filterCategory, ignoreCase = true)
    }
  }

  val resolvedIncidents = remember(incidents, filterCategory) {
    incidents.filter { it.status == IncidentStatus.RESOLVED }.filter {
      filterCategory == "ALL" || it.nptType.equals(filterCategory, ignoreCase = true) || it.category.displayName.equals(filterCategory, ignoreCase = true)
    }
  }

  fun formatElapsed(startMillis: Long, endMillis: Long? = null): String {
    val end = endMillis ?: ticker
    val diffSec = Math.max(0, (end - startMillis) / 1000)
    val hours = diffSec / 3600
    val mins = (diffSec % 3600) / 60
    val secs = diffSec % 60
    return String.format("%02dh %02dm %02ds", hours, mins, secs)
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
          Column {
            Text(
              text = "NPT & Maintenance Tracking",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text(
              text = "Logged-in Supervisor: $userName",
              color = Color(0xFFBAE6FD),
              fontSize = 11.sp
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("npt_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        actions = {
          if (canNptWrite) {
            IconButton(onClick = { showReportNptDialog = true }) {
              Icon(Icons.Default.AddAlert, contentDescription = "Report NPT", tint = Color(0xFFFDE047))
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    floatingActionButton = {
      if (canNptWrite) {
        ExtendedFloatingActionButton(
          onClick = { showReportNptDialog = true },
          containerColor = Color(0xFFDC2626),
          contentColor = Color.White,
          modifier = Modifier.testTag("start_incident_fab")
        ) {
          Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Report NPT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
      }
    },
    containerColor = Color(0xFFF8FAFC)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (!canNptWrite) {
        Surface(
          color = Color(0xFFFEF3C7),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          shape = RoundedCornerShape(8.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = "View-Only Mode — You do not have permission to log or resolve NPT incidents.",
              color = Color(0xFF92400E),
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
      // 1. Tab Selector (Active Stoppages vs Stoppage History)
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.White,
        contentColor = Color(0xFF0F172A)
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = {
            Text(
              text = "Active NPT (${activeIncidents.size})",
              fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
            )
          }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = {
            Text(
              text = "NPT History (${resolvedIncidents.size})",
              fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
            )
          }
        )
      }

      // 2. Filter Bar
      Surface(
        color = Color(0xFFF1F5F9),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Filter by NPT Type:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569)
          )

          Box {
            OutlinedCard(
              onClick = { showFilterDropdown = true },
              shape = RoundedCornerShape(6.dp),
              colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = if (filterCategory == "ALL") "All Types" else filterCategory,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
              }
            }

            DropdownMenu(
              expanded = showFilterDropdown,
              onDismissRequest = { showFilterDropdown = false }
            ) {
              DropdownMenuItem(
                text = { Text("All Types", fontWeight = FontWeight.Bold) },
                onClick = {
                  filterCategory = "ALL"
                  showFilterDropdown = false
                }
              )
              commonNptTypes.forEach { type ->
                DropdownMenuItem(
                  text = { Text(type) },
                  onClick = {
                    filterCategory = type
                    showFilterDropdown = false
                  }
                )
              }
            }
          }
        }
      }

      // 3. Stoppages List
      val currentList = if (selectedTab == 0) activeIncidents else resolvedIncidents

      if (currentList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text("✅", fontSize = 36.sp)
            Text(
              text = if (selectedTab == 0) "No Active NPT Stoppages" else "No Stoppage History Found",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF1E293B)
            )
            Text(
              text = if (selectedTab == 0) "All sewing and finishing lines are running smoothly." else "No resolved incidents recorded today.",
              fontSize = 12.sp,
              color = Color(0xFF64748B)
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(currentList, key = { it.id }) { inc ->
            val isAck = inc.status == IncidentStatus.ACKNOWLEDGED
            val isResolved = inc.status == IncidentStatus.RESOLVED

            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(
                containerColor = when {
                  isResolved -> Color.White
                  isAck -> Color(0xFFFFFBEB)
                  else -> Color(0xFFFEF2F2)
                }
              ),
              elevation = CardDefaults.cardElevation(2.dp),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                  isResolved -> Color(0xFFE2E8F0)
                  isAck -> Color(0xFFFDE68A)
                  else -> Color(0xFFFECACA)
                }
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Header: Line Name & NPT Type Badges
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                      text = "Line ${inc.lineId} (${inc.floor})",
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Black,
                      color = Color(0xFF0F172A)
                    )

                    Surface(
                      color = when {
                        isResolved -> Color(0xFFDCFCE7)
                        isAck -> Color(0xFFFEF3C7)
                        else -> Color(0xFFFEE2E2)
                      },
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        text = if (isResolved) "RESOLVED" else if (isAck) "IN MAINTENANCE" else "OPEN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                          isResolved -> Color(0xFF166534)
                          isAck -> Color(0xFF92400E)
                          else -> Color(0xFF991B1B)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }

                  Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = inc.nptType,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF334155),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }

                // Process & Machine details tags
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (inc.processName.isNotBlank()) {
                    Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(4.dp)) {
                      Text(
                        text = "Process: ${inc.processName}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                  if (inc.machineType.isNotBlank()) {
                    Surface(color = Color(0xFFF3E8FF), shape = RoundedCornerShape(4.dp)) {
                      Text(
                        text = "Machine: ${inc.machineType}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B21A8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                  if (inc.machineRef.isNotBlank()) {
                    Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(4.dp)) {
                      Text(
                        text = "ID: ${inc.machineRef}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                      )
                    }
                  }
                }

                // Problem Description
                Text(
                  text = inc.description,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF1E293B)
                )

                if (inc.reportedCause.isNotBlank()) {
                  Text(
                    text = "Reported cause: ${inc.reportedCause}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                  )
                }

                // FULL-WIDTH LIVE DOWNTIME COUNTER BAR (Guaranteed NEVER to wrap single letters!)
                Surface(
                  color = if (isResolved) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(if (isResolved) Color(0xFF10B981) else Color(0xFFEF4444))
                      )
                      Text(
                        text = if (isResolved) "TOTAL DOWNTIME DURATION:" else "DOWNTIME COUNTER:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isResolved) Color(0xFF475569) else Color(0xFF94A3B8)
                      )
                    }
                    Text(
                      text = formatElapsed(inc.startTimeMillis, inc.endTimeMillis),
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Black,
                      color = if (isResolved) Color(0xFF0F172A) else Color.White
                    )
                  }
                }

                // Mechanic working banner
                if (inc.isMaintenanceStarted) {
                  Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "🔧 Attended by: ${inc.mechanicName ?: "Mechanic"}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                      )
                      Text(
                        text = "Active Repair: ${formatElapsed(inc.mechanicStartTimeMillis ?: inc.startTimeMillis, inc.endTimeMillis)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                      )
                    }
                  }
                }

                // Resolution Note for resolved stoppage
                if (isResolved && inc.resolutionNote.isNotBlank()) {
                  Text(
                    text = "Resolution Action: ${inc.resolutionNote} (by ${inc.resolvedBy ?: "Supervisor"})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF166534)
                  )
                }

                // Actions Row for Active Stoppage
                if (!isResolved) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    // Mute
                    OutlinedButton(
                      onClick = { repository.muteIncident(inc.id) },
                      shape = RoundedCornerShape(6.dp),
                      modifier = Modifier.padding(end = 6.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                      Text(if (inc.isMutedOnCurrentDevice) "🔇 Muted" else "🔔 Mute", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (canNptWrite) {
                      // Mechanic Start Maintenance Button - ONLY for Machine Breakdown incidents
                      val isMachineBreakdown = inc.nptType.equals("Machine Breakdown", ignoreCase = true) ||
                        inc.category == IncidentCategory.MACHINE_BREAKDOWN
                      if (!inc.isMaintenanceStarted && isMachineBreakdown) {
                        Button(
                          onClick = {
                            attendingIncidentId = inc.id
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                          shape = RoundedCornerShape(6.dp),
                          modifier = Modifier.padding(end = 6.dp),
                          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                          Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("Start Maintenance", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                      }

                      // Supervisor Stop NPT Button
                      Button(
                        onClick = {
                          resolvingIncidentId = inc.id
                          resolutionNoteText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                      ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop NPT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                      }
                    } else {
                      Text("🔒 View-Only", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    }
                  }
                }

                // Interactive Comments Section (Anyone can read & add comments with auto user name)
                NptIncidentCommentsSection(
                  incident = inc,
                  repository = repository,
                  currentUserName = currentUser?.name ?: userName,
                  currentUserRole = currentUser?.role?.displayName ?: "Staff"
                )
              }
            }
          }
        }
      }
    }
  }

  // DIALOG: Report NPT & Start Downtime Timer
  if (showReportNptDialog) {
    AlertDialog(
      onDismissRequest = { showReportNptDialog = false },
      containerColor = Color.White,
      title = {
        Column {
          Text("Report NPT & Start Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text("Supervisor: $userName", fontSize = 11.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
        }
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Floor Selector
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.FINISHING).forEach { flr ->
              Surface(
                onClick = { formFloor = flr },
                shape = RoundedCornerShape(6.dp),
                color = if (formFloor == flr) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = when (flr) {
                    FloorScope.SEW_1 -> "Sew 1"
                    FloorScope.SEW_2 -> "Sew 2"
                    FloorScope.SEW_3 -> "Sew 3"
                    FloorScope.FINISHING -> "Finish"
                    else -> flr.displayName
                  },
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (formFloor == flr) Color.White else Color(0xFF334155),
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  modifier = Modifier.padding(vertical = 6.dp)
                )
              }
            }
          }

          // Line Dropdown
          Box {
            OutlinedCard(
              onClick = { showLineDropdown = true },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Line: $formLineId (${formFloor.displayName})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
              }
            }
            DropdownMenu(expanded = showLineDropdown, onDismissRequest = { showLineDropdown = false }) {
              floorLines.forEach { line ->
                DropdownMenuItem(
                  text = { Text("Line ${line.id}") },
                  onClick = {
                    formLineId = line.id
                    showLineDropdown = false
                  }
                )
              }
            }
          }

          // NPT Type Dropdown
          Box {
            OutlinedCard(
              onClick = { showNptTypeDropdown = true },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("NPT Type: $formNptType", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
              }
            }
            DropdownMenu(expanded = showNptTypeDropdown, onDismissRequest = { showNptTypeDropdown = false }) {
              commonNptTypes.forEach { type ->
                DropdownMenuItem(
                  text = { Text(type) },
                  onClick = {
                    formNptType = type
                    showNptTypeDropdown = false
                  }
                )
              }
            }
          }

          // If Custom NPT Type chosen
          if (formNptType == "Custom / Other") {
            OutlinedTextField(
              value = formCustomNptType,
              onValueChange = { formCustomNptType = it },
              label = { Text("Enter Custom NPT Type *") },
              placeholder = { Text("e.g. Needle Breakage, Boiler Off") },
              singleLine = true,
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
              modifier = Modifier.fillMaxWidth()
            )
          }

          // Process Name
          OutlinedTextField(
            value = formProcessName,
            onValueChange = { formProcessName = it },
            label = { Text("Process Name") },
            placeholder = { Text("e.g. Collar Attach, Side Seam, Hemming") },
            singleLine = true,
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )

          // Machine Breakdown specifics
          if (formNptType == "Machine Breakdown") {
            Box {
              OutlinedCard(
                onClick = { showMachineTypeDropdown = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(formMachineType, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
              }
              DropdownMenu(expanded = showMachineTypeDropdown, onDismissRequest = { showMachineTypeDropdown = false }) {
                commonMachineTypes.forEach { mType ->
                  DropdownMenuItem(
                    text = { Text(mType) },
                    onClick = {
                      formMachineType = mType
                      showMachineTypeDropdown = false
                    }
                  )
                }
              }
            }

            OutlinedTextField(
              value = formMachineRef,
              onValueChange = { formMachineRef = it },
              label = { Text("Machine Reference / Asset ID") },
              placeholder = { Text("e.g. SNLS-04") },
              singleLine = true,
              colors = com.example.ui.theme.nflInputColors(),
              textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
              modifier = Modifier.fillMaxWidth()
            )
          }

          // NPT Description
          OutlinedTextField(
            value = formDescription,
            onValueChange = { formDescription = it },
            label = { Text("NPT Description *") },
            placeholder = { Text("e.g. Motor heated, needle bar vibrating, stitch skipping") },
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )

          // Reported Cause
          OutlinedTextField(
            value = formReportedCause,
            onValueChange = { formReportedCause = it },
            label = { Text("Reported Cause (Optional)") },
            placeholder = { Text("e.g. Bearing loose, belt cut") },
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (formDescription.isNotBlank()) {
              val resolvedNptType = if (formNptType == "Custom / Other" && formCustomNptType.isNotBlank()) formCustomNptType else formNptType

              repository.reportIncident(
                IncidentItem(
                  lineId = formLineId,
                  floor = formFloor.apiValue,
                  category = when (formNptType) {
                    "Machine Breakdown" -> IncidentCategory.MACHINE_BREAKDOWN
                    "Quality Issue" -> IncidentCategory.QUALITY_PROBLEM
                    "Input N/A" -> IncidentCategory.INPUT_MATERIAL
                    "Operator N/A" -> IncidentCategory.OPERATOR_NA
                    "Power / Utility Outage" -> IncidentCategory.POWER_OUTAGE
                    else -> IncidentCategory.OTHER
                  },
                  nptType = resolvedNptType,
                  customNptTypeName = formCustomNptType,
                  processName = formProcessName,
                  machineType = if (formNptType == "Machine Breakdown") formMachineType else "",
                  machineRef = formMachineRef,
                  description = formDescription,
                  reportedCause = formReportedCause,
                  reportedBy = userName,
                  isDemo = false
                )
              )
              showReportNptDialog = false
              formDescription = ""
              formReportedCause = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
          Text("Start NPT", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportNptDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // DIALOG: Mechanic Start Maintenance
  if (attendingIncidentId != null) {
    AlertDialog(
      onDismissRequest = { attendingIncidentId = null },
      containerColor = Color.White,
      title = { Text("Mechanic Attendance", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Mechanic attending this stoppage:", fontSize = 12.sp, color = Color(0xFF334155))
          OutlinedTextField(
            value = mechanicNameInput,
            onValueChange = { mechanicNameInput = it },
            label = { Text("Mechanic Name") },
            singleLine = true,
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            attendingIncidentId?.let { id ->
              repository.startMechanicMaintenance(id, mechanicNameInput.ifEmpty { "Mechanic" })
            }
            attendingIncidentId = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
        ) {
          Text("Start Maintenance", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { attendingIncidentId = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // DIALOG: Stop NPT / Resolve
  if (resolvingIncidentId != null) {
    AlertDialog(
      onDismissRequest = { resolvingIncidentId = null },
      containerColor = Color.White,
      title = { Text("Stop NPT & Resolve Stoppage", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Record the restoration action taken so that NPT duration is finalized:", fontSize = 12.sp, color = Color(0xFF334155))
          OutlinedTextField(
            value = resolutionNoteText,
            onValueChange = { resolutionNoteText = it },
            label = { Text("Resolution Note *") },
            placeholder = { Text("e.g. Machine repaired, test run pass, production resumed") },
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            resolvingIncidentId?.let { id ->
              repository.resolveIncident(
                id = id,
                resolvedBy = userName,
                note = resolutionNoteText.ifEmpty { "Maintenance complete, line running." }
              )
            }
            resolvingIncidentId = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
        ) {
          Text("Confirm Stop NPT", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { resolvingIncidentId = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun NptIncidentCommentsSection(
  incident: IncidentItem,
  repository: NflRepository,
  currentUserName: String,
  currentUserRole: String
) {
  var isExpanded by remember(incident.id) { mutableStateOf(incident.comments.isNotEmpty()) }
  var commentInput by remember(incident.id) { mutableStateOf("") }
  val comments = incident.comments

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // Header toggle
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .background(Color(0xFFF1F5F9))
        .clickable { isExpanded = !isExpanded }
        .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(15.dp))
        Text(
          text = if (comments.isEmpty()) "💬 Add Comments / Remarks" else "💬 Comments (${comments.size})",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF0369A1)
        )
      }
      Icon(
        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = Color(0xFF0369A1),
        modifier = Modifier.size(16.dp)
      )
    }

    if (isExpanded) {
      if (comments.isEmpty()) {
        Text(
          text = "No remarks yet. Anyone can write comments below.",
          fontSize = 10.5.sp,
          color = Color(0xFF64748B),
          modifier = Modifier.padding(horizontal = 4.dp)
        )
      } else {
        comments.forEach { c ->
          val formattedTime = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.US).format(java.util.Date(c.timestamp))
          Surface(
            color = Color.White,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "👤 ${c.userName} ${if (c.userRole.isNotBlank()) "(${c.userRole})" else ""}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = formattedTime,
                  fontSize = 9.sp,
                  color = Color(0xFF64748B)
                )
              }
              Text(
                text = c.commentText,
                fontSize = 11.5.sp,
                color = Color(0xFF334155)
              )
            }
          }
        }
      }

      // Input row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        OutlinedTextField(
          value = commentInput,
          onValueChange = { commentInput = it },
          placeholder = { Text("Comment as $currentUserName...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
          modifier = Modifier.weight(1f),
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          colors = com.example.ui.theme.nflInputColors(),
          textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF0F172A))
        )
        Button(
          onClick = {
            if (commentInput.isNotBlank()) {
              repository.addIncidentComment(incident.id, commentInput, currentUserName, currentUserRole)
              commentInput = ""
            }
          },
          enabled = commentInput.isNotBlank(),
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text("Post", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

