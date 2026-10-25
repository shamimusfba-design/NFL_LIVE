package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.SyncStatus
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySyncScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val prodEntries by repository.productionEntries.collectAsState()
  val qualityEntries by repository.qualityEntries.collectAsState()
  val auditLogs by repository.auditLogs.collectAsState()
  val isSyncing by repository.isSyncing.collectAsState()
  val lastRefreshTime by repository.lastRefreshTime.collectAsState()
  val sheetId by repository.sheetId.collectAsState()
  val syncBanner by repository.syncStatusBanner.collectAsState()
  var selectedTab by remember { mutableStateOf(0) } // 0: Production, 1: Quality, 2: Audit Log

  val pendingCount = prodEntries.count { it.syncStatus != SyncStatus.SYNCED } +
      qualityEntries.count { it.syncStatus != SyncStatus.SYNCED }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("System Ledger & Audit History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Last Updated: $lastRefreshTime", color = Color(0xFFBAE6FD), fontSize = 10.sp)
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("history_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NflRoyalBlue)
      )
    },
    containerColor = Color(0xFFF1F5F9)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Sync Status Header Card
      Surface(
        color = if (pendingCount == 0) Color(0xFFE8F5E9) else Color(0xFFFFF8E1),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CloudSync,
              contentDescription = null,
              tint = if (pendingCount == 0) NflGreen else NflAmber
            )
            Column {
              Text(
                text = if (pendingCount == 0) "All factory records synchronized" else "$pendingCount pending entries in queue",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (pendingCount == 0) NflGreen else Color(0xFFB78103)
              )
              Text(
                text = "Production • Quality • NPT • Line Targets",
                fontSize = 10.5.sp,
                color = Color(0xFF607D8B)
              )
            }
          }

          if (pendingCount > 0) {
            Button(
              onClick = { repository.retryPendingSubmissions() },
              colors = ButtonDefaults.buttonColors(containerColor = NflAmber),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(if (isSyncing) "Syncing..." else "Sync now / Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Tab Row
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.White,
        contentColor = NflRoyalBlue
      ) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Production (${prodEntries.size})") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Quality (${qualityEntries.size})") })
        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Audit Trail (${auditLogs.size})") })
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        when (selectedTab) {
          0 -> {
            if (prodEntries.isEmpty()) {
              item { Text("No production submissions yet.", color = Color(0xFF90A4AE)) }
            } else {
              items(prodEntries) { item ->
                Card(
                  shape = RoundedCornerShape(8.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  elevation = CardDefaults.cardElevation(1.dp)
                ) {
                  Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("Line ${item.lineId} • ${repository.formatHourSlot12h(item.hourSlot)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Style: ${item.styleNumber} • ${item.date}", fontSize = 11.sp, color = Color(0xFF78909C))
                        Text("Submission ID: ${item.submissionId.take(8)}... (Rev ${item.revision}) • By: ${item.modifiedBy}", fontSize = 10.sp, color = Color(0xFF90A4AE))
                        if (item.remarks.isNotBlank()) {
                          Text("Remarks: ${item.remarks}", fontSize = 11.sp, color = Color(0xFF546E7A))
                        }
                      }

                      Column(horizontalAlignment = Alignment.End) {
                        Text("${item.outputQty} pcs", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NflRoyalBlue)
                        Surface(
                          shape = RoundedCornerShape(4.dp),
                          color = when (item.syncStatus) {
                            SyncStatus.SYNCED -> Color(0xFFE8F5E9)
                            SyncStatus.PENDING -> Color(0xFFFFF8E1)
                            SyncStatus.FAILED -> Color(0xFFFFEBEE)
                          }
                        ) {
                          Text(
                            text = item.syncStatus.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (item.syncStatus) {
                              SyncStatus.SYNCED -> NflGreen
                              SyncStatus.PENDING -> NflAmber
                              SyncStatus.FAILED -> NflRed
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                        }
                      }
                    }

                    val currentUser by repository.currentUser.collectAsState()
                    val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == com.example.model.UserRole.ADMIN
                    if (isAdminUser) {
                      var showCorrectDialog by remember { mutableStateOf(false) }
                      var showDeleteDialog by remember { mutableStateOf(false) }
                      var newQtyInput by remember(item.outputQty) { mutableStateOf(item.outputQty.toString()) }
                      var newRemarksInput by remember(item.remarks) { mutableStateOf(item.remarks) }
                      var reasonInput by remember { mutableStateOf("") }
                      var actionError by remember { mutableStateOf<String?>(null) }

                      Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                      ) {
                        TextButton(
                          onClick = {
                            reasonInput = ""
                            actionError = null
                            showCorrectDialog = true
                          },
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                          modifier = Modifier.height(28.dp)
                        ) {
                          Text("✏️ Admin Correct", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NflRoyalBlue)
                        }
                        TextButton(
                          onClick = {
                            reasonInput = ""
                            actionError = null
                            showDeleteDialog = true
                          },
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                          modifier = Modifier.height(28.dp)
                        ) {
                          Text("🗑️ Admin Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NflRed)
                        }
                      }

                      if (showCorrectDialog) {
                        AlertDialog(
                          onDismissRequest = { showCorrectDialog = false },
                          containerColor = Color.White,
                          title = { Text("Admin Record Correction (Line ${item.lineId})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A)) },
                          text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              Text("Date: ${item.date} • Slot: ${repository.formatHourSlot12h(item.hourSlot)}", fontSize = 12.sp, color = Color(0xFF334155))
                              OutlinedTextField(
                                value = newQtyInput,
                                onValueChange = { if (it.all { ch -> ch.isDigit() }) newQtyInput = it },
                                label = { Text("Corrected Output Qty (pcs)", color = Color(0xFF1E293B)) },
                                singleLine = true,
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              OutlinedTextField(
                                value = newRemarksInput,
                                onValueChange = { newRemarksInput = it },
                                label = { Text("Remarks", color = Color(0xFF1E293B)) },
                                singleLine = true,
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              OutlinedTextField(
                                value = reasonInput,
                                onValueChange = { reasonInput = it; actionError = null },
                                label = { Text("Mandatory Correction Reason (Audit Log) *", color = Color(0xFF1E293B)) },
                                singleLine = false,
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              if (actionError != null) {
                                Text(actionError ?: "", color = NflRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                              }
                            }
                          },
                          confirmButton = {
                            Button(
                              onClick = {
                                val qty = newQtyInput.toIntOrNull()
                                if (qty == null || qty < 0) {
                                  actionError = "Enter a valid quantity."
                                  return@Button
                                }
                                val res = repository.adminCorrectProductionRecord(
                                  submissionId = item.submissionId,
                                  newOutputQty = qty,
                                  newRemarks = newRemarksInput,
                                  reason = reasonInput
                                )
                                if (res.success) {
                                  showCorrectDialog = false
                                } else {
                                  actionError = res.message
                                }
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue)
                            ) {
                              Text("Save Correction", fontWeight = FontWeight.Bold)
                            }
                          },
                          dismissButton = {
                            TextButton(onClick = { showCorrectDialog = false }) { Text("Cancel") }
                          }
                        )
                      }

                      if (showDeleteDialog) {
                        AlertDialog(
                          onDismissRequest = { showDeleteDialog = false },
                          containerColor = Color.White,
                          title = { Text("Admin Delete Record (Line ${item.lineId})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A)) },
                          text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              Text("Deleting ${item.outputQty} pcs for Line ${item.lineId} (${item.date} • ${repository.formatHourSlot12h(item.hourSlot)}).", fontSize = 12.sp, color = Color(0xFF0F172A))
                              OutlinedTextField(
                                value = reasonInput,
                                onValueChange = { reasonInput = it; actionError = null },
                                label = { Text("Mandatory Deletion Reason (Audit Log) *", color = Color(0xFF1E293B)) },
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              if (actionError != null) {
                                Text(actionError ?: "", color = NflRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                              }
                            }
                          },
                          confirmButton = {
                            Button(
                              onClick = {
                                val res = repository.adminDeleteProductionRecord(item.submissionId, reasonInput)
                                if (res.success) {
                                  showDeleteDialog = false
                                } else {
                                  actionError = res.message
                                }
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = NflRed)
                            ) {
                              Text("Confirm Delete", fontWeight = FontWeight.Bold)
                            }
                          },
                          dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                          }
                        )
                      }
                    } else {
                      Text(
                        text = "🔒 Submitted & Locked (Only Admin can edit/delete with audit reason)",
                        fontSize = 9.5.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 4.dp)
                      )
                    }
                  }
                }
              }
            }
          }
          1 -> {
            if (qualityEntries.isEmpty()) {
              item { Text("No quality submissions yet.", color = Color(0xFF90A4AE)) }
            } else {
              items(qualityEntries) { item ->
                val oql = if (item.checkedGarments > 0) (item.defectiveGarments.toDouble() / item.checkedGarments) * 100.0 else 0.0

                Card(
                  shape = RoundedCornerShape(8.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  elevation = CardDefaults.cardElevation(1.dp)
                ) {
                  Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        val qaModeLabel = if (repository.isBiHourlyAuditSlot(item.hourSlot)) "Bi-Hourly Audit" else "Hourly DHU"
                        Text("Line ${item.lineId} • ${repository.formatHourSlot12h(item.hourSlot)} • $qaModeLabel (${item.date})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Checked: ${item.checkedGarments} • Defective: ${item.defectiveGarments} • Defects: ${item.totalDefects}", fontSize = 11.sp, color = Color(0xFF78909C))
                        Text(
                          "Breakdown: " + item.defects.joinToString(", ") { "${it.defectType} (${it.count})" },
                          fontSize = 11.sp,
                          color = Color(0xFF546E7A)
                        )
                      }

                      Column(horizontalAlignment = Alignment.End) {
                        Text("OQL ${"%.2f".format(oql)}%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (oql <= 5.0) NflGreen else NflRed)
                        Surface(
                          shape = RoundedCornerShape(4.dp),
                          color = when (item.syncStatus) {
                            SyncStatus.SYNCED -> Color(0xFFE8F5E9)
                            SyncStatus.PENDING -> Color(0xFFFFF8E1)
                            SyncStatus.FAILED -> Color(0xFFFFEBEE)
                          }
                        ) {
                          Text(
                            text = item.syncStatus.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (item.syncStatus) {
                              SyncStatus.SYNCED -> NflGreen
                              SyncStatus.PENDING -> NflAmber
                              SyncStatus.FAILED -> NflRed
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                        }
                      }
                    }

                    val currentUser by repository.currentUser.collectAsState()
                    val isAdminUser = currentUser?.isAdmin == true && currentUser?.role == com.example.model.UserRole.ADMIN
                    if (isAdminUser) {
                      var showCorrectDialog by remember { mutableStateOf(false) }
                      var showDeleteDialog by remember { mutableStateOf(false) }
                      var newCheckedInput by remember(item.checkedGarments) { mutableStateOf(item.checkedGarments.toString()) }
                      var newDefectiveInput by remember(item.defectiveGarments) { mutableStateOf(item.defectiveGarments.toString()) }
                      var reasonInput by remember { mutableStateOf("") }
                      var actionError by remember { mutableStateOf<String?>(null) }

                      Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                      ) {
                        TextButton(
                          onClick = {
                            reasonInput = ""
                            actionError = null
                            showCorrectDialog = true
                          },
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                          modifier = Modifier.height(28.dp)
                        ) {
                          Text("✏️ Admin Correct", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NflRoyalBlue)
                        }
                        TextButton(
                          onClick = {
                            reasonInput = ""
                            actionError = null
                            showDeleteDialog = true
                          },
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                          modifier = Modifier.height(28.dp)
                        ) {
                          Text("🗑️ Admin Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NflRed)
                        }
                      }

                      if (showCorrectDialog) {
                        AlertDialog(
                          onDismissRequest = { showCorrectDialog = false },
                          containerColor = Color.White,
                          title = { Text("Admin QA Record Correction (Line ${item.lineId})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A)) },
                          text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              Text("Date: ${item.date} • Slot: ${repository.formatHourSlot12h(item.hourSlot)}", fontSize = 12.sp, color = Color(0xFF334155))
                              OutlinedTextField(
                                value = newCheckedInput,
                                onValueChange = { if (it.all { ch -> ch.isDigit() }) newCheckedInput = it },
                                label = { Text("Checked Garments", color = Color(0xFF1E293B)) },
                                singleLine = true,
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              OutlinedTextField(
                                value = newDefectiveInput,
                                onValueChange = { if (it.all { ch -> ch.isDigit() }) newDefectiveInput = it },
                                label = { Text("Defective Garments", color = Color(0xFF1E293B)) },
                                singleLine = true,
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              OutlinedTextField(
                                value = reasonInput,
                                onValueChange = { reasonInput = it; actionError = null },
                                label = { Text("Mandatory Correction Reason (Audit Log) *", color = Color(0xFF1E293B)) },
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              if (actionError != null) {
                                Text(actionError ?: "", color = NflRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                              }
                            }
                          },
                          confirmButton = {
                            Button(
                              onClick = {
                                val chk = newCheckedInput.toIntOrNull()
                                val def = newDefectiveInput.toIntOrNull()
                                if (chk == null || chk <= 0 || def == null || def < 0 || def > chk) {
                                  actionError = "Enter valid checked/defective counts."
                                  return@Button
                                }
                                val res = repository.adminCorrectQualityRecord(
                                  submissionId = item.submissionId,
                                  newChecked = chk,
                                  newDefective = def,
                                  reason = reasonInput
                                )
                                if (res.success) {
                                  showCorrectDialog = false
                                } else {
                                  actionError = res.message
                                }
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue)
                            ) {
                              Text("Save Correction", fontWeight = FontWeight.Bold)
                            }
                          },
                          dismissButton = {
                            TextButton(onClick = { showCorrectDialog = false }) { Text("Cancel") }
                          }
                        )
                      }

                      if (showDeleteDialog) {
                        AlertDialog(
                          onDismissRequest = { showDeleteDialog = false },
                          containerColor = Color.White,
                          title = { Text("Admin Delete QA Record (Line ${item.lineId})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A)) },
                          text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                              Text("Deleting QA inspection for Line ${item.lineId} (${item.date} • ${repository.formatHourSlot12h(item.hourSlot)}).", fontSize = 12.sp, color = Color(0xFF0F172A))
                              OutlinedTextField(
                                value = reasonInput,
                                onValueChange = { reasonInput = it; actionError = null },
                                label = { Text("Mandatory Deletion Reason (Audit Log) *", color = Color(0xFF1E293B)) },
                                colors = com.example.ui.theme.nflInputColors(),
                                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                              )
                              if (actionError != null) {
                                Text(actionError ?: "", color = NflRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                              }
                            }
                          },
                          confirmButton = {
                            Button(
                              onClick = {
                                val res = repository.adminDeleteQualityRecord(item.submissionId, reasonInput)
                                if (res.success) {
                                  showDeleteDialog = false
                                } else {
                                  actionError = res.message
                                }
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = NflRed)
                            ) {
                              Text("Confirm Delete", fontWeight = FontWeight.Bold)
                            }
                          },
                          dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                          }
                        )
                      }
                    } else {
                      Text(
                        text = "🔒 Submitted & Locked (Only Admin can edit/delete with audit reason)",
                        fontSize = 9.5.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 4.dp)
                      )
                    }
                  }
                }
              }
            }
          }
          2 -> {
            items(auditLogs) { log ->
              Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
              ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("${log.action} (${log.entityType}: ${log.entityId})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NflNavyHeader)
                    Text("By: ${log.changedBy}", fontSize = 11.sp, color = Color(0xFF78909C))
                  }
                  Text(log.details, fontSize = 11.sp, color = Color(0xFF37474F))
                  if (log.reason.isNotBlank()) {
                    Text("Audit Reason: ${log.reason}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
