package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.*
import com.example.service.FactorySoundMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationCenterScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val incidents by repository.incidents.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val currentRole by repository.currentRole.collectAsState()
  val soundMode by repository.soundMode.collectAsState()
  val isSoundMuted by remember(soundMode) { derivedStateOf { repository.isSoundMuted() } }

  var selectedStatusFilter by remember { mutableStateOf("ALL") }
  var selectedSeverityFilter by remember { mutableStateOf("ALL") }
  var selectedCategoryFilter by remember { mutableStateOf("ALL") }
  var searchQuery by remember { mutableStateOf("") }

  var resolvingIncident by remember { mutableStateOf<IncidentItem?>(null) }
  var resolutionNoteInput by remember { mutableStateOf("") }

  var verifyingIncident by remember { mutableStateOf<IncidentItem?>(null) }
  var correctiveActionInput by remember { mutableStateOf("") }

  val filteredIncidents = remember(
    incidents,
    selectedStatusFilter,
    selectedSeverityFilter,
    selectedCategoryFilter,
    searchQuery
  ) {
    incidents.filter { item ->
      val matchesStatus = when (selectedStatusFilter) {
        "UNRESOLVED" -> item.status != IncidentStatus.RESOLVED && item.status != IncidentStatus.VERIFIED
        "OPEN" -> item.status == IncidentStatus.OPEN || item.status == IncidentStatus.REPORTED
        "ACKNOWLEDGED" -> item.status == IncidentStatus.ACKNOWLEDGED
        "IN_PROGRESS" -> item.status == IncidentStatus.IN_PROGRESS
        "RESOLVED" -> item.status == IncidentStatus.RESOLVED
        "VERIFIED" -> item.status == IncidentStatus.VERIFIED
        else -> true
      }
      val matchesSeverity = when (selectedSeverityFilter) {
        "CRITICAL" -> item.severity == AlertSeverity.CRITICAL
        "HIGH" -> item.severity == AlertSeverity.HIGH
        "MEDIUM" -> item.severity == AlertSeverity.MEDIUM
        "LOW_INFO" -> item.severity == AlertSeverity.LOW_INFO
        else -> true
      }
      val matchesCategory = when (selectedCategoryFilter) {
        "BREAKDOWN" -> item.category == IncidentCategory.MACHINE_BREAKDOWN
        "QUALITY" -> item.category == IncidentCategory.QUALITY_PROBLEM
        "PRODUCTION" -> item.category == IncidentCategory.LOW_PRODUCTION || item.category == IncidentCategory.INPUT_MATERIAL
        else -> true
      }
      val matchesSearch = if (searchQuery.isBlank()) true else {
        item.lineId.contains(searchQuery, ignoreCase = true) ||
            item.description.contains(searchQuery, ignoreCase = true) ||
            item.machineId.contains(searchQuery, ignoreCase = true) ||
            item.floor.contains(searchQuery, ignoreCase = true)
      }
      matchesStatus && matchesSeverity && matchesCategory && matchesSearch
    }.sortedByDescending { it.startTimeMillis }
  }

  val unresolvedCount = remember(incidents) {
    incidents.count { it.status != IncidentStatus.RESOLVED && it.status != IncidentStatus.VERIFIED }
  }
  val criticalCount = remember(incidents) {
    incidents.count { it.severity == AlertSeverity.CRITICAL && it.status != IncidentStatus.RESOLVED }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              "Smart Factory Notification Center",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              "Role-based Alerts & Audible Alarms",
              fontSize = 11.sp,
              color = Color(0xFF94A3B8)
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        actions = {
          IconButton(onClick = { repository.silenceActiveAlarm() }) {
            Icon(
              imageVector = if (isSoundMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Silence Alarm",
              tint = if (isSoundMuted) Color(0xFFF87171) else Color(0xFF4ADE80)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    containerColor = Color(0xFF0A1120)
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .imePadding()
        .navigationBarsPadding()
    ) {
      // 1. User Sound Controls Header (Sound ON, Mute, 15m, 1h, End of Shift, Silence)
      Surface(
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(if (isSoundMuted) Color(0xFFEF4444) else Color(0xFF10B981))
              )
              Text(
                text = repository.getMuteStatusText(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSoundMuted) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
              )
            }

            Surface(
              onClick = { repository.silenceActiveAlarm() },
              color = Color(0xFF7F1D1D),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, Color(0xFFEF4444))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text("Silence Alarm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
              }
            }
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              SoundModeChip(
                label = "Sound ON",
                isSelected = soundMode == FactorySoundMode.SOUND_ON && !isSoundMuted,
                onClick = { repository.setSoundMode(FactorySoundMode.SOUND_ON) }
              )
            }
            item {
              SoundModeChip(
                label = "Mute Sound",
                isSelected = soundMode == FactorySoundMode.MUTE_ALL,
                onClick = { repository.setSoundMode(FactorySoundMode.MUTE_ALL) }
              )
            }
            item {
              SoundModeChip(
                label = "Mute 15 Mins",
                isSelected = soundMode == FactorySoundMode.MUTE_15_MIN && isSoundMuted,
                onClick = { repository.setSoundMode(FactorySoundMode.MUTE_15_MIN) }
              )
            }
            item {
              SoundModeChip(
                label = "Mute 1 Hour",
                isSelected = soundMode == FactorySoundMode.MUTE_1_HOUR && isSoundMuted,
                onClick = { repository.setSoundMode(FactorySoundMode.MUTE_1_HOUR) }
              )
            }
            item {
              SoundModeChip(
                label = "Until End of Shift",
                isSelected = soundMode == FactorySoundMode.MUTE_UNTIL_END_OF_SHIFT && isSoundMuted,
                onClick = { repository.setSoundMode(FactorySoundMode.MUTE_UNTIL_END_OF_SHIFT) }
              )
            }
          }
        }
      }

      // 2. Summary KPI Ribbon
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SummaryChip(
          title = "Unresolved",
          count = unresolvedCount,
          color = Color(0xFFEF4444),
          modifier = Modifier.weight(1f)
        )
        SummaryChip(
          title = "Critical",
          count = criticalCount,
          color = Color(0xFFDC2626),
          modifier = Modifier.weight(1f)
        )
        SummaryChip(
          title = "Total Alerts",
          count = incidents.size,
          color = Color(0xFF38BDF8),
          modifier = Modifier.weight(1f)
        )
      }

      // 3. Search & Filter Bar
      Surface(
        color = Color(0xFF111C2E),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter by line (e.g. K1), machine, floor...", fontSize = 12.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFF1E293B),
              unfocusedContainerColor = Color(0xFF0F172A),
              focusedBorderColor = Color(0xFF38BDF8),
              unfocusedBorderColor = Color(0xFF334155),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            )
          )

          // Status Filters
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val statusOptions = listOf(
              "ALL" to "All Status",
              "UNRESOLVED" to "Unresolved",
              "OPEN" to "Open",
              "ACKNOWLEDGED" to "Acknowledged",
              "IN_PROGRESS" to "In Progress",
              "RESOLVED" to "Resolved",
              "VERIFIED" to "Verified"
            )
            items(statusOptions) { (key, label) ->
              FilterChipItem(
                label = label,
                isSelected = selectedStatusFilter == key,
                onClick = { selectedStatusFilter = key }
              )
            }
          }

          // Category Filters
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val catOptions = listOf(
              "ALL" to "All Categories",
              "BREAKDOWN" to "Machine Breakdown",
              "QUALITY" to "Quality & DHU",
              "PRODUCTION" to "Production & NPT"
            )
            items(catOptions) { (key, label) ->
              FilterChipItem(
                label = label,
                isSelected = selectedCategoryFilter == key,
                onClick = { selectedCategoryFilter = key }
              )
            }
          }
        }
      }

      // 4. Alert List
      if (filteredIncidents.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
            Text("No alerts matching selected filters", color = Color(0xFF94A3B8), fontSize = 14.sp)
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredIncidents, key = { it.id }) { incident ->
            AlertItemCard(
              incident = incident,
              currentUser = currentUser,
              currentRole = currentRole,
              onAcknowledge = {
                repository.acknowledgeIncident(incident.id, currentUser?.name ?: currentRole.displayName)
              },
              onStartProgress = {
                repository.startMaintenanceIncident(incident.id, currentUser?.name ?: "Mechanic")
              },
              onResolve = {
                resolvingIncident = incident
                resolutionNoteInput = ""
              },
              onVerify = {
                verifyingIncident = incident
                correctiveActionInput = ""
              },
              onEscalate = {
                repository.escalateIncident(incident.id, "Escalated by ${currentUser?.name ?: currentRole.displayName}")
              },
              onSilence = {
                repository.silenceActiveAlarm()
              },
              onAddComment = { commentText ->
                repository.addIncidentComment(
                  incident.id,
                  commentText,
                  currentUser?.name ?: currentRole.displayName,
                  currentUser?.role?.displayName ?: currentRole.displayName
                )
              }
            )
          }
        }
      }
    }
  }

  // Dialog: Resolve Incident
  if (resolvingIncident != null) {
    val inc = resolvingIncident!!
    AlertDialog(
      onDismissRequest = { resolvingIncident = null },
      containerColor = Color.White,
      title = { Text("Resolve Issue [${inc.lineId}]", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Category: ${inc.category.displayName}", fontSize = 12.sp, color = Color(0xFF475569))
          Text("Description: ${inc.description}", fontSize = 12.sp, color = Color(0xFF0F172A))
          OutlinedTextField(
            value = resolutionNoteInput,
            onValueChange = { resolutionNoteInput = it },
            label = { Text("Resolution Note / Actions Taken", color = Color(0xFF1E293B)) },
            placeholder = { Text("e.g. Belt replaced, needle adjusted", color = Color(0xFF475569)) },
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            repository.resolveIncident(
              inc.id,
              currentUser?.name ?: currentRole.displayName,
              resolutionNoteInput.ifBlank { "Resolved normally." }
            )
            resolvingIncident = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
          Text("Mark Resolved", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { resolvingIncident = null }) {
          Text("Cancel", color = Color(0xFF334155))
        }
      }
    )
  }

  // Dialog: Verify Issue
  if (verifyingIncident != null) {
    val inc = verifyingIncident!!
    AlertDialog(
      onDismissRequest = { verifyingIncident = null },
      containerColor = Color.White,
      title = { Text("Verify Corrective Action [${inc.lineId}]", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Verified Resolution: ${inc.resolutionNote}", fontSize = 12.sp, color = Color(0xFF475569))
          OutlinedTextField(
            value = correctiveActionInput,
            onValueChange = { correctiveActionInput = it },
            label = { Text("Verification / QA Observation", color = Color(0xFF1E293B)) },
            placeholder = { Text("Confirmed line running at target pace", color = Color(0xFF475569)) },
            colors = com.example.ui.theme.nflInputColors(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            repository.verifyIncident(
              inc.id,
              currentUser?.name ?: currentRole.displayName,
              correctiveActionInput.ifBlank { "Verified inspection passed." }
            )
            verifyingIncident = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
        ) {
          Text("Confirm Verified", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { verifyingIncident = null }) {
          Text("Cancel", color = Color(0xFF334155))
        }
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertItemCard(
  incident: IncidentItem,
  currentUser: UserAccount?,
  currentRole: UserRole,
  onAcknowledge: () -> Unit,
  onStartProgress: () -> Unit,
  onResolve: () -> Unit,
  onVerify: () -> Unit,
  onEscalate: () -> Unit,
  onSilence: () -> Unit,
  onAddComment: (String) -> Unit = {}
) {
  val borderColor = when (incident.severity) {
    AlertSeverity.CRITICAL -> Color(0xFFEF4444)
    AlertSeverity.HIGH -> Color(0xFFF97316)
    AlertSeverity.MEDIUM -> Color(0xFFEAB308)
    AlertSeverity.LOW_INFO -> Color(0xFF64748B)
  }

  val statusColor = when (incident.status) {
    IncidentStatus.OPEN, IncidentStatus.REPORTED -> Color(0xFFEF4444)
    IncidentStatus.ACKNOWLEDGED -> Color(0xFFF97316)
    IncidentStatus.IN_PROGRESS -> Color(0xFF38BDF8)
    IncidentStatus.RESOLVED -> Color(0xFF10B981)
    IncidentStatus.VERIFIED -> Color(0xFF0284C7)
  }

  val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.US) }
  val reportedTime = remember(incident.startTimeMillis) {
    timeFormatter.format(Date(incident.startTimeMillis))
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Header: Severity, Line, Floor, Status
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Surface(
            color = borderColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, borderColor)
          ) {
            Text(
              text = incident.severity.displayName.uppercase(),
              fontSize = 9.sp,
              fontWeight = FontWeight.Black,
              color = borderColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Text(
            text = "Line ${incident.lineId}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = "• ${incident.floor}",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
          )
          if (incident.machineId.isNotBlank()) {
            Text(
              text = "• M/C: ${incident.machineId}",
              fontSize = 11.sp,
              color = Color(0xFF38BDF8),
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        Surface(
          color = statusColor.copy(alpha = 0.15f),
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, statusColor.copy(alpha = 0.8f))
        ) {
          Text(
            text = incident.status.displayName,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // Title & Description
      Text(
        text = "${incident.category.displayName} — ${incident.nptType}",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFE2E8F0)
      )
      Text(
        text = incident.description,
        fontSize = 12.sp,
        color = Color(0xFFCBD5E1)
      )

      if (incident.isEscalated) {
        Surface(
          color = Color(0xFF7F1D1D).copy(alpha = 0.4f),
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
            Text(
              text = "ESCALATED TO MANAGEMENT: ${incident.escalationReason ?: "Unresolved beyond threshold"}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFFCA5A5)
            )
          }
        }
      }

      // Metadata footer
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Reported at $reportedTime by ${incident.reportedBy}",
          fontSize = 11.sp,
          color = Color(0xFF64748B)
        )
        if (incident.acknowledgedBy != null) {
          Text(
            text = "Ack: ${incident.acknowledgedBy}",
            fontSize = 11.sp,
            color = Color(0xFF38BDF8)
          )
        }
      }

      if (incident.resolutionNote.isNotBlank()) {
        Surface(
          color = Color(0xFF0F2942),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Text("Resolution by ${incident.resolvedBy ?: "Staff"}:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            Text(incident.resolutionNote, fontSize = 12.sp, color = Color(0xFFE2E8F0))
          }
        }
      }

      if (incident.correctiveAction.isNotBlank()) {
        Surface(
          color = Color(0xFF064E3B).copy(alpha = 0.5f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Text("Verified by ${incident.verifiedBy ?: "QA / Management"}:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
            Text(incident.correctiveAction, fontSize = 12.sp, color = Color(0xFFD1FAE5))
          }
        }
      }

      // Action Buttons
      HorizontalDivider(color = Color(0xFF1E293B))
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (incident.severity == AlertSeverity.CRITICAL && incident.status != IncidentStatus.RESOLVED) {
          OutlinedButton(
            onClick = onSilence,
            modifier = Modifier.heightIn(min = 38.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, Color(0xFFEF4444))
          ) {
            Icon(Icons.Default.VolumeMute, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("Silence", fontSize = 11.sp, color = Color(0xFFEF4444))
          }
        }

        if (incident.status == IncidentStatus.OPEN || incident.status == IncidentStatus.REPORTED) {
          Button(
            onClick = onAcknowledge,
            modifier = Modifier.heightIn(min = 38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text("Acknowledge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        if (incident.status == IncidentStatus.ACKNOWLEDGED && incident.category == IncidentCategory.MACHINE_BREAKDOWN) {
          Button(
            onClick = onStartProgress,
            modifier = Modifier.heightIn(min = 38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text("Start Repair", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        if (incident.status != IncidentStatus.RESOLVED && incident.status != IncidentStatus.VERIFIED) {
          Button(
            onClick = onResolve,
            modifier = Modifier.heightIn(min = 38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text("Resolve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          if (!incident.isEscalated && (currentRole == UserRole.ADMIN || currentRole == UserRole.PRODUCTION_MANAGER || currentRole == UserRole.SUPERVISOR)) {
            OutlinedButton(
              onClick = onEscalate,
              modifier = Modifier.heightIn(min = 38.dp),
              border = BorderStroke(1.dp, Color(0xFFEF4444)),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text("Escalate", fontSize = 11.sp, color = Color(0xFFEF4444))
            }
          }
        } else if (incident.status == IncidentStatus.RESOLVED && (currentRole == UserRole.ADMIN || currentRole == UserRole.QUALITY_MANAGER || currentRole == UserRole.AUDITOR || currentRole == UserRole.PRODUCTION_MANAGER)) {
          Button(
            onClick = onVerify,
            modifier = Modifier.heightIn(min = 38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text("Verify Corrective Action", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // Notification / Incident Comments Section
      var showComments by remember(incident.id) { mutableStateOf(incident.comments.isNotEmpty()) }
      var commentInput by remember(incident.id) { mutableStateOf("") }
      val authorName = currentUser?.name ?: currentRole.displayName

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .clickable { showComments = !showComments }
            .padding(horizontal = 8.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
            Text(
              text = if (incident.comments.isEmpty()) "💬 Add Comments / Remarks" else "💬 Comments (${incident.comments.size})",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF38BDF8)
            )
          }
          Icon(
            imageVector = if (showComments) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (showComments) "Collapse comments" else "Expand comments",
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(16.dp)
          )
        }

        if (showComments) {
          if (incident.comments.isEmpty()) {
            Text(
              text = "No remarks yet. Anyone can add remarks below.",
              fontSize = 11.sp,
              color = Color(0xFF94A3B8),
              modifier = Modifier.padding(horizontal = 4.dp)
            )
          } else {
            incident.comments.forEach { c ->
              val timeStr = SimpleDateFormat("hh:mm a, dd MMM", Locale.US).format(Date(c.timestamp))
              Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "👤 ${c.userName} ${if (c.userRole.isNotBlank()) "(${c.userRole})" else ""}",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFF1F5F9)
                    )
                    Text(
                      text = timeStr,
                      fontSize = 10.sp,
                      color = Color(0xFF64748B)
                    )
                  }
                  Text(
                    text = c.commentText,
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            OutlinedTextField(
              value = commentInput,
              onValueChange = { commentInput = it },
              placeholder = { Text("Comment as $authorName...", fontSize = 11.sp, color = Color(0xFF64748B)) },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              modifier = Modifier.weight(1f),
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A)
              ),
              textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White)
            )
            Button(
              onClick = {
                if (commentInput.isNotBlank()) {
                  onAddComment(commentInput)
                  commentInput = ""
                }
              },
              enabled = commentInput.isNotBlank(),
              shape = RoundedCornerShape(6.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
              modifier = Modifier.heightIn(min = 48.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text("Post", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryChip(
  title: String,
  count: Int,
  color: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Color(0xFF1E293B),
    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(title, fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
      Text(count.toString(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
  }
}

@Composable
private fun SoundModeChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155))
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) Color.White else Color(0xFFCBD5E1),
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
    )
  }
}

@Composable
private fun FilterChipItem(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0F172A),
    border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155))
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF94A3B8),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}
