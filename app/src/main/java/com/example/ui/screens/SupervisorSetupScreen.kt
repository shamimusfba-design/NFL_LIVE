package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.SupervisorLineSetup
import com.example.model.UserRole
import com.example.ui.theme.NflGreen
import com.example.ui.theme.NflRoyalBlue

private data class LineRowDraft(
  val lineId: String,
  val floor: String,
  val itemName: String,
  val smvText: String,
  val targetText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorSetupScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val allTargets by repository.targets.collectAsState()
  val currentUser by repository.currentUser.collectAsState()
  val canMorningTargetWrite = currentUser?.let { user ->
    user.isAdmin || user.role in setOf(UserRole.IE, UserRole.SUPERVISOR, UserRole.PRODUCTION_MANAGER, UserRole.PRODUCTION_REPORTER) || user.canMorningTargetWrite
  } ?: true

  var selectedFloor by remember { mutableStateOf(FloorScope.SEW_2) }
  var saveSuccessMsg by remember { mutableStateOf<String?>(null) }

  // Filter lines by selected floor (Sewing lines for Sew-1/2/3/All, Iron/Packing for Finishing)
  val floorLines = remember(selectedFloor) {
    repository.allLinesMaster.filter { line ->
      val isFinishing = line.id.startsWith("Iron", ignoreCase = true) || line.id.startsWith("Packing", ignoreCase = true)
      if (selectedFloor == FloorScope.FINISHING) {
        isFinishing
      } else {
        !isFinishing && (selectedFloor == FloorScope.ALL || line.floor == selectedFloor.apiValue)
      }
    }
  }

  // Mutable state map holding the 4-column editable row draft for every line on the floor:
  // 1. Line
  // 2. Item
  // 3. SMV
  // 4. Hourly Target (pcs/hr) — Daily Target calculated as Hourly * 8
  val rowDrafts = remember(selectedFloor) {
    mutableStateMapOf<String, LineRowDraft>().apply {
      floorLines.forEach { lineInfo ->
        val setup = repository.getLineSetup(lineInfo.id)
        val hourlyTgt = if (setup.targetHourlyPcs > 0) {
          setup.targetHourlyPcs
        } else if (setup.targetDailyPcs > 0) {
          (setup.targetDailyPcs / 8).coerceAtLeast(1)
        } else 0
        put(
          lineInfo.id,
          LineRowDraft(
            lineId = lineInfo.id,
            floor = lineInfo.floor,
            itemName = setup.itemName,
            smvText = if (setup.smv > 0) setup.smv.toString() else "",
            targetText = if (hourlyTgt > 0) hourlyTgt.toString() else ""
          )
        )
      }
    }
  }

  fun buildSetupFromDraft(draft: LineRowDraft): SupervisorLineSetup {
    val existing = repository.getLineSetup(draft.lineId)
    val smvVal = draft.smvText.toDoubleOrNull()?.coerceAtLeast(0.1) ?: existing.smv
    val hourlyVal = draft.targetText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val dailyVal = hourlyVal * 8

    return existing.copy(
      lineId = draft.lineId,
      floor = draft.floor,
      date = repository.getCurrentBangladeshDateIso(),
      itemName = draft.itemName.trim().ifBlank { existing.itemName },
      smv = smvVal,
      targetDailyPcs = dailyVal,
      targetHourlyPcs = hourlyVal
    )
  }

  fun saveAllFloorLines() {
    if (!canMorningTargetWrite) return
    val setupsToSave = floorLines.mapNotNull { line ->
      rowDrafts[line.id]?.let { buildSetupFromDraft(it) }
    }
    repository.saveBatchLineSetups(setupsToSave)
    saveSuccessMsg = "✓ Saved all ${setupsToSave.size} lines for ${selectedFloor.displayName} (Synced to Cloud & TV Dashboard)"
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
          Column {
            Text(
              text = "Daily Morning Target Setup",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
            Text(
              text = "Line-wise Hourly Target (pcs/hr) • 8-Hour Shift (Daily = Hourly × 8)",
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
            Button(
              onClick = { saveAllFloorLines() },
              colors = ButtonDefaults.buttonColors(containerColor = NflGreen),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .padding(end = 8.dp)
                .testTag("save_all_floor_targets_btn")
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save All", fontSize = 11.5.sp, fontWeight = FontWeight.Black)
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
        .fillMaxWidth()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. Floor Selector Tabs (Sew-1 | Sew-2 | Sew-3 | All)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.ALL).forEach { floor ->
          val isSelected = selectedFloor == floor
          val lineCountForFloor = repository.allLinesMaster.count { line ->
            val isFinishing = line.id.startsWith("Iron", ignoreCase = true) || line.id.startsWith("Packing", ignoreCase = true)
            !isFinishing && (floor == FloorScope.ALL || line.floor == floor.apiValue)
          }
          Surface(
            onClick = {
              selectedFloor = floor
              saveSuccessMsg = null
            },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF0F172A) else Color.White,
            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
            modifier = Modifier.weight(1f)
          ) {
            Column(
              modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = floor.displayName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color(0xFF334155)
              )
              Text(
                text = "$lineCountForFloor Lines",
                fontSize = 8.5.sp,
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
          border = BorderStroke(1.dp, Color(0xFFF59E0B)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
            Column {
              Text("View-Only Mode (Target Setup Locked)", color = Color(0xFF92400E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
              Text("Only authorized IE and Production Reporter accounts can edit line targets and SMV.", color = Color(0xFFB45309), fontSize = 10.sp)
            }
          }
        }
      }

      AnimatedVisibility(visible = saveSuccessMsg != null) {
        saveSuccessMsg?.let { msg ->
          Surface(
            color = Color(0xFFDCFCE7),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF22C55E)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
              Text(msg, color = Color(0xFF166534), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 2. Exactly 4-Column Morning Target Spreadsheet Card:
      // Column 1: Line
      // Column 2: Item
      // Column 3: SMV
      // Column 4: 8-Hr Target
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Card Title
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
              Column {
                Text(
                  text = "${selectedFloor.displayName} — Daily Target Sheet (${floorLines.size} Lines)",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Black,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = "Columns: 1. Line | 2. Item | 3. SMV | 4. 8-Hr Target (pcs)",
                  fontSize = 9.5.sp,
                  color = Color(0xFF64748B)
                )
              }
            }
          }

          // Table Header Row & Data Rows (Scrollable horizontally if screen is narrow)
          val tableScrollState = rememberScrollState()
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(tableScrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // Table Header: Line | Item | SMV | Hourly Target | Save
            Row(
              modifier = Modifier
                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              TableHeaderCell("Line", 54.dp, TextAlign.Center)
              TableHeaderCell("Item / Style", 124.dp, TextAlign.Start)
              TableHeaderCell("SMV", 68.dp, TextAlign.Center)
              TableHeaderCell("Hourly Target (pcs/hr)", 96.dp, TextAlign.Center)
              if (canMorningTargetWrite) {
                TableHeaderCell("Save", 50.dp, TextAlign.Center)
              }
            }

            // All Lines Rows
            floorLines.forEachIndexed { idx, lineInfo ->
              val draft = rowDrafts[lineInfo.id] ?: return@forEachIndexed
              val isEven = idx % 2 == 0

              Row(
                modifier = Modifier
                  .background(
                    if (isEven) Color(0xFFF8FAFC) else Color.White,
                    RoundedCornerShape(6.dp)
                  )
                  .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                  .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                // Column 1: Line Badge
                Surface(
                  color = Color(0xFF0A2558),
                  shape = RoundedCornerShape(5.dp),
                  modifier = Modifier.width(54.dp).height(34.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = lineInfo.id,
                      color = Color.White,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Black,
                      maxLines = 1
                    )
                  }
                }

                // Column 2: Item / Style Text Field
                CompactTableTextField(
                  value = draft.itemName,
                  onValueChange = { newVal ->
                    rowDrafts[lineInfo.id] = draft.copy(itemName = newVal)
                  },
                  width = 124.dp,
                  enabled = canMorningTargetWrite,
                  placeholder = "Style / Item",
                  textAlign = TextAlign.Start,
                  testTag = if (idx == 0) "setup_item_first_input" else null
                )

                // Column 3: SMV Decimal Field
                CompactTableNumberField(
                  value = draft.smvText,
                  onValueChange = { newVal ->
                    val clean = newVal.filter { it.isDigit() || it == '.' }
                    rowDrafts[lineInfo.id] = draft.copy(smvText = clean)
                  },
                  width = 68.dp,
                  enabled = canMorningTargetWrite,
                  isDecimal = true,
                  placeholder = "SMV",
                  testTag = if (idx == 0) "setup_smv_input" else null
                )

                // Column 4: Hourly Target (pcs/hr) Field
                CompactTableNumberField(
                  value = draft.targetText,
                  onValueChange = { newVal ->
                    val clean = newVal.filter { it.isDigit() }
                    rowDrafts[lineInfo.id] = draft.copy(targetText = clean)
                  },
                  width = 96.dp,
                  enabled = canMorningTargetWrite,
                  isDecimal = false,
                  placeholder = "pcs/hr",
                  testTag = if (idx == 0) "setup_target_pcs_input" else null
                )

                // Column 5: Quick Single-Row Save Button
                if (canMorningTargetWrite) {
                  Surface(
                    onClick = {
                      val setup = buildSetupFromDraft(draft)
                      repository.saveLineSetup(setup)
                      saveSuccessMsg = "✓ Saved Line ${draft.lineId}: Hourly Target ${setup.targetHourlyPcs} pcs/hr (${setup.targetDailyPcs} pcs/day)"
                    },
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier
                      .width(50.dp)
                      .height(34.dp)
                      .testTag("setup_save_row_${lineInfo.id}")
                  ) {
                    Row(
                      modifier = Modifier.fillMaxSize(),
                      horizontalArrangement = Arrangement.Center,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Line ${lineInfo.id}",
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(13.dp)
                      )
                      Spacer(modifier = Modifier.width(2.dp))
                      Text("Save", color = Color(0xFF15803D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
          }

          // Bottom Save All Button
          if (canMorningTargetWrite) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
              onClick = { saveAllFloorLines() },
              colors = ButtonDefaults.buttonColors(containerColor = NflGreen),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("setup_save_btn")
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Save All ${floorLines.size} Lines for ${selectedFloor.displayName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TableHeaderCell(
  text: String,
  width: Dp,
  align: TextAlign = TextAlign.Center
) {
  Text(
    text = text,
    color = Color.White,
    fontSize = 10.5.sp,
    fontWeight = FontWeight.Black,
    textAlign = align,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.width(width)
  )
}

@Composable
private fun CompactTableTextField(
  value: String,
  onValueChange: (String) -> Unit,
  width: Dp,
  enabled: Boolean,
  placeholder: String = "",
  textAlign: TextAlign = TextAlign.Start,
  testTag: String? = null
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    singleLine = true,
    textStyle = TextStyle(
      color = if (enabled) Color(0xFF0A0F1D) else Color(0xFF1E293B),
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      textAlign = textAlign
    ),
    cursorBrush = SolidColor(Color(0xFF1D4ED8)),
    modifier = Modifier
      .width(width)
      .height(34.dp)
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    decorationBox = { innerTextField ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            if (enabled) Color(0xFFEFF6FF) else Color(0xFFE2E8F0),
            RoundedCornerShape(6.dp)
          )
          .border(
            1.5.dp,
            if (enabled) Color(0xFF2563EB) else Color(0xFF64748B),
            RoundedCornerShape(6.dp)
          )
          .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
          Text(
            text = placeholder,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
          )
        }
        innerTextField()
      }
    }
  )
}

@Composable
private fun CompactTableNumberField(
  value: String,
  onValueChange: (String) -> Unit,
  width: Dp,
  enabled: Boolean,
  isDecimal: Boolean,
  placeholder: String = "",
  testTag: String? = null
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    singleLine = true,
    textStyle = TextStyle(
      color = if (enabled) Color(0xFF0A0F1D) else Color(0xFF1E293B),
      fontSize = 12.5.sp,
      fontWeight = FontWeight.Black,
      textAlign = TextAlign.Center
    ),
    cursorBrush = SolidColor(Color(0xFF1D4ED8)),
    keyboardOptions = KeyboardOptions(
      keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
    ),
    modifier = Modifier
      .width(width)
      .height(34.dp)
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    decorationBox = { innerTextField ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            if (enabled) Color(0xFFEFF6FF) else Color(0xFFE2E8F0),
            RoundedCornerShape(6.dp)
          )
          .border(
            1.5.dp,
            if (enabled) Color(0xFF2563EB) else Color(0xFF64748B),
            RoundedCornerShape(6.dp)
          )
          .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
      ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
          Text(
            text = placeholder,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
          )
        }
        innerTextField()
      }
    }
  )
}
