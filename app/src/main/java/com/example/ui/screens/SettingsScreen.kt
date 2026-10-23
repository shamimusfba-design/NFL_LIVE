package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GoogleSheetsStorageService
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val currentRole by repository.currentRole.collectAsState()
  val assignedLine by repository.assignedLine.collectAsState()
  val currentFloor by repository.currentFloor.collectAsState()
  val isDemoMode by repository.isDemoMode.collectAsState()
  val targets by repository.targets.collectAsState()
  val prods by repository.productionEntries.collectAsState()
  val qas by repository.qualityEntries.collectAsState()
  val incs by repository.incidents.collectAsState()

  val sheetId by repository.sheetId.collectAsState()
  val webAppUrl by repository.webAppUrl.collectAsState()
  val autoRefreshEnabled by repository.autoRefreshEnabled.collectAsState()
  val isSyncing by repository.isSyncing.collectAsState()
  val lastRefreshTime by repository.lastRefreshTime.collectAsState()
  val syncBanner by repository.syncStatusBanner.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val firestoreReadStats by repository.firestoreReadStats.collectAsState()

  var editSheetId by remember(sheetId) { mutableStateOf(sheetId) }
  var editWebAppUrl by remember(webAppUrl) { mutableStateOf(webAppUrl) }
  var editAutoRefresh by remember(autoRefreshEnabled) { mutableStateOf(autoRefreshEnabled) }
  var showAppsScriptDialog by remember { mutableStateOf(false) }

  var showRoleDropdown by remember { mutableStateOf(false) }
  var showLineDropdown by remember { mutableStateOf(false) }
  var showFloorDropdown by remember { mutableStateOf(false) }
  var confirmPurgeDialog by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  val clipboardManager = LocalClipboardManager.current

  val demoTargetsCount = targets.count { it.isDemo }
  val demoProdsCount = prods.count { it.isDemo }
  val demoQasCount = qas.count { it.isDemo }
  val demoIncsCount = incs.count { it.isDemo }
  val totalDemoRows = demoTargetsCount + demoProdsCount + demoQasCount + demoIncsCount

  val liveTargetsCount = targets.count { !it.isDemo }
  val liveProdsCount = prods.count { !it.isDemo }
  val liveQasCount = qas.count { !it.isDemo }
  val liveIncsCount = incs.count { !it.isDemo }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("System Settings & Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Last Updated: $lastRefreshTime", color = Color(0xFFBAE6FD), fontSize = 10.sp)
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF002D62))
      )
    },
    containerColor = Color(0xFFF1F5F9)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (statusMessage != null) {
        Surface(
          color = Color(0xFFE8F5E9),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = statusMessage ?: "",
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(12.dp)
          )
        }
      }

      // 1. Live Data Sync & Refresh Card
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF059669))
              Column {
                Text("Live Factory Data Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF102A43))
                Text("Real-time synchronization for Production, Quality, NPT & Targets", fontSize = 10.5.sp, color = Color(0xFF546E7A))
              }
            }
            Surface(
              color = Color(0xFFDCFCE7),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = if (isSyncing) "UPDATING..." else "ONLINE",
                color = Color(0xFF166534),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Surface(
            color = Color(0xFFF0FDF4),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Live Stored Records — Production: $liveProdsCount • Quality: $liveQasCount • Setups: $liveTargetsCount • NPT: $liveIncsCount",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF065F46)
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Auto-Refresh Live Changes", fontWeight = FontWeight.Medium, fontSize = 12.sp)
              Text("Automatically refresh live factory metrics on TV & Dashboard", fontSize = 10.sp, color = Color.Gray)
            }
            Switch(
              checked = editAutoRefresh,
              onCheckedChange = {
                editAutoRefresh = it
                repository.updateSheetConfig(editSheetId, editWebAppUrl, it)
              }
            )
          }

          Button(
            onClick = {
              repository.refreshFromGoogleSheet { _, msg ->
                statusMessage = msg
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("settings_refresh_sheet_btn")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isSyncing) "Syncing..." else "Sync now / Retry", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
          }

          if (currentUser?.isAdmin == true) {
            Surface(
              color = Color(0xFFF8FAFC),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
              modifier = Modifier.fillMaxWidth().testTag("admin_firestore_usage_card")
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val totalDocs = firestoreReadStats.values.sumOf { it.totalChangesReceived }
                val totalServer = firestoreReadStats.values.sumOf { it.serverChangesReceived }
                val totalCache = firestoreReadStats.values.sumOf { it.cacheChangesReceived }
                Text(
                  text = "Firestore Session Read Counter (Admin Debug)",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = "Total docs received: $totalDocs (Server: $totalServer • Cache: $totalCache)",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF334155)
                )
                firestoreReadStats.entries.forEach { (colName, stats) ->
                  Text(
                    text = "• $colName: ${stats.totalChangesReceived} docs (server=${stats.serverChangesReceived}, cache=${stats.cacheChangesReceived}, lastCache=${stats.lastIsFromCache})",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF475569)
                  )
                }
              }
            }
          }
        }
      }

      // 2. Role & Floor Assignment Card
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("User Role & Floor Scope", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF102A43))

          // Role Selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Active User Role:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
              Text("Permissions & alert routing", fontSize = 10.sp, color = Color.Gray)
            }
            val currentUser by repository.currentUser.collectAsState()
            val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == UserRole.ADMIN
            Box {
              OutlinedButton(
                onClick = { if (isAdminUser) showRoleDropdown = true },
                enabled = isAdminUser
              ) {
                Text(
                  text = if (isAdminUser) currentRole.displayName else "🔒 ${currentRole.displayName}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              if (isAdminUser) {
                DropdownMenu(expanded = showRoleDropdown, onDismissRequest = { showRoleDropdown = false }) {
                  UserRole.entries.forEach { role ->
                    DropdownMenuItem(
                      text = {
                        Column {
                          Text(role.displayName, fontWeight = FontWeight.Bold)
                          Text(role.group, fontSize = 10.sp, color = Color.Gray)
                        }
                      },
                      onClick = {
                        repository.setRole(role)
                        showRoleDropdown = false
                      }
                    )
                  }
                }
              }
            }
          }

          // Floor Scope
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Default Floor Scope:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Box {
              OutlinedButton(onClick = { showFloorDropdown = true }) {
                Text(currentFloor.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
              DropdownMenu(expanded = showFloorDropdown, onDismissRequest = { showFloorDropdown = false }) {
                FloorScope.entries.forEach { floor ->
                  DropdownMenuItem(
                    text = { Text(floor.displayName) },
                    onClick = {
                      repository.setFloor(floor)
                      showFloorDropdown = false
                    }
                  )
                }
              }
            }
          }

          // Assigned Line
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Assigned Line Station:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Box {
              OutlinedButton(onClick = { showLineDropdown = true }) {
                Text("Line $assignedLine", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
              DropdownMenu(expanded = showLineDropdown, onDismissRequest = { showLineDropdown = false }) {
                repository.allLinesMaster
                  .filter { currentFloor == FloorScope.ALL || it.floor == currentFloor.apiValue }
                  .take(15)
                  .forEach { line ->
                    DropdownMenuItem(
                      text = { Text("Line ${line.id} (${line.floor})") },
                      onClick = {
                        repository.setAssignedLine(line.id)
                        showLineDropdown = false
                      }
                    )
                  }
              }
            }
          }
        }
      }

      // 3. Demo Data Lifecycle Management Card
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Demo Data Lifecycle (Admin)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF102A43))
          Text(
            text = "Demo data provides realistic test metrics across Sew 1, Sew 2, and Sew 3. Demo rows are isolated from real Google Sheet entries.",
            fontSize = 11.sp,
            color = Color(0xFF546E7A)
          )

          Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Current Demo Inventory:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263238))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo Line Targets: $demoTargetsCount", fontSize = 10.sp, color = Color.Gray)
                Text("Demo Output Entries: $demoProdsCount", fontSize = 10.sp, color = Color.Gray)
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo Quality Logs: $demoQasCount", fontSize = 10.sp, color = Color.Gray)
                Text("Demo Active Incidents: $demoIncsCount", fontSize = 10.sp, color = Color.Gray)
              }
              Text(
                text = "Total Demo Records: $totalDemoRows",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (isDemoMode) Color(0xFF1976D2) else Color.Gray
              )
            }
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                repository.loadSeedDemoData()
                statusMessage = "✓ Demo data loaded idempotently across all floors!"
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Load Demo Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = { confirmPurgeDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Remove All Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }

  if (showAppsScriptDialog) {
    AlertDialog(
      onDismissRequest = { showAppsScriptDialog = false },
      title = { Text("Google Apps Script Bridge (2-Way Sync)", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "1. Open your Google Sheet -> Extensions -> Apps Script.\n2. Paste the script below and click Deploy -> New Deployment -> Web App (Who has access: Anyone).\n3. Paste the Web App URL into Settings above.",
            fontSize = 11.sp,
            color = Color(0xFF334155)
          )
          Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
          ) {
            Column(
              modifier = Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
            ) {
              Text(
                text = GoogleSheetsStorageService.GOOGLE_APPS_SCRIPT_TEMPLATE,
                color = Color(0xFF38BDF8),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboardManager.setText(AnnotatedString(GoogleSheetsStorageService.GOOGLE_APPS_SCRIPT_TEMPLATE))
            showAppsScriptDialog = false
            statusMessage = "✓ Copied Google Apps Script code to clipboard!"
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("Copy Script", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAppsScriptDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // Confirmation dialog for removing all demo data
  if (confirmPurgeDialog) {
    AlertDialog(
      onDismissRequest = { confirmPurgeDialog = false },
      title = { Text("Confirm Removal of Demo Data", fontWeight = FontWeight.Bold) },
      text = {
        Text("This will purge all $totalDemoRows demo records. Real user accounts, settings, line master, and Google Sheet records will be completely preserved.")
      },
      confirmButton = {
        Button(
          onClick = {
            repository.removeAllDemoData()
            confirmPurgeDialog = false
            statusMessage = "✓ All demo data removed! Switched to clean live Google Sheet state."
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
          Text("Confirm Delete", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmPurgeDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
