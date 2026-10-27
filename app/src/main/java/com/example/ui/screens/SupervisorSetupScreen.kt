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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NflRepository
import com.example.model.FloorScope
import com.example.model.ManpowerSummary
import com.example.model.autoHourlyTarget
import com.example.model.SupervisorLineSetup
import com.example.model.UserRole
import com.example.ui.theme.NflGreen
import com.example.ui.theme.NflRoyalBlue

internal data class LineRowDraft(
  val lineId: String,
  val floor: String,
  val itemName: String,
  val smvText: String,
  val operatorsText: String = "",
  val helpersText: String = "",
  val finishingText: String = "",
  val effText: String = "100",
  val targetText: String = ""
)

private const val DRAFT_FIELD_COUNT = 9

internal val LineRowDraftMapSaver: Saver<SnapshotStateMap<String, LineRowDraft>, ArrayList<String>> = Saver(
  save = { map ->
    val list = ArrayList<String>(map.size * DRAFT_FIELD_COUNT)
    map.values.forEach { d ->
      list.add(d.lineId); list.add(d.floor); list.add(d.itemName); list.add(d.smvText)
      list.add(d.operatorsText); list.add(d.helpersText); list.add(d.finishingText); list.add(d.effText); list.add(d.targetText)
    }
    list
  },
  restore = { list ->
    mutableStateMapOf<String, LineRowDraft>().apply {
      var i = 0
      while (i + DRAFT_FIELD_COUNT - 1 < list.size) {
        val draft = LineRowDraft(
          lineId = list[i], floor = list[i + 1], itemName = list[i + 2], smvText = list[i + 3],
          operatorsText = list[i + 4], helpersText = list[i + 5], finishingText = list[i + 6], effText = list[i + 7], targetText = list[i + 8]
        )
        put(draft.lineId, draft)
        i += DRAFT_FIELD_COUNT
      }
    }
  }
)

/** Hourly target worked out from what is typed in the row: manpower x 60 x target % / SMV. */
private fun LineRowDraft.totalManpower(): Int =
  (operatorsText.toIntOrNull() ?: 0) + (helpersText.toIntOrNull() ?: 0) + (finishingText.toIntOrNull() ?: 0)

private fun LineRowDraft.autoHourly(): Int =
  autoHourlyTarget(totalManpower(), smvText.toDoubleOrNull() ?: 0.0, effText.toDoubleOrNull() ?: 0.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupervisorSetupScreen(
  repository: NflRepository,
  onNavigateBack: () -> Unit
) {
  val currentUser by repository.currentUser.collectAsState()
  val canMorningTargetWrite = currentUser?.let { user ->
    user.isAdmin || user.role in setOf(UserRole.IE, UserRole.SUPERVISOR, UserRole.PRODUCTION_MANAGER, UserRole.PRODUCTION_REPORTER) || user.canMorningTargetWrite
  } ?: true

  var selectedFloorName by rememberSaveable { mutableStateOf(FloorScope.SEW_2.name) }
  val selectedFloor = remember(selectedFloorName) {
    FloorScope.entries.find { it.name == selectedFloorName } ?: FloorScope.SEW_2
  }
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

  val rowDrafts = rememberSaveable(saver = LineRowDraftMapSaver) {
    mutableStateMapOf()
  }
  var lastSetupContextKey by rememberSaveable { mutableStateOf("") }

  fun populateRowDrafts() {
    val realSetups = repository.targets.value.filter { !it.isDemo }
    floorLines.forEach { lineInfo ->
      val setup = repository.getLineSetup(lineInfo.id)
      // Manpower is shown only when it was really typed before. Nothing is made up.
      val hasReal = realSetups.any { it.lineId.equals(lineInfo.id, ignoreCase = true) }
      rowDrafts[lineInfo.id] = LineRowDraft(
        lineId = lineInfo.id,
        floor = lineInfo.floor,
        itemName = setup.itemName,
        smvText = if (setup.smv > 0) setup.smv.toString() else "",
        operatorsText = if (hasReal) setup.operatorsCount.toString() else "",
        helpersText = if (hasReal) setup.helpersCount.toString() else "",
        finishingText = if (hasReal) setup.finishingStaffCount.toString() else "",
        // Target % starts at 100 for a line that was never set up; a saved line shows what was saved.
        effText = if (hasReal && setup.targetEfficiencyPct > 0) setup.targetEfficiencyPct.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } else "100"
      )
    }
  }

  LaunchedEffect(selectedFloor.name) {
    if (lastSetupContextKey != selectedFloor.name) {
      populateRowDrafts()
      lastSetupContextKey = selectedFloor.name
    }
  }

  // Clear drafts on logout
  LaunchedEffect(currentUser?.id) {
    if (currentUser == null) {
      rowDrafts.clear()
      lastSetupContextKey = ""
    }
  }

  /** Returns a short message when the row cannot be saved, otherwise null. */
  fun problemWith(draft: LineRowDraft): String? {
    if ((draft.smvText.toDoubleOrNull() ?: 0.0) <= 0.0) return "Line ${draft.lineId}: enter the SMV."
    if (draft.operatorsText.isBlank() || draft.helpersText.isBlank() || draft.finishingText.isBlank()) {
      return "Line ${draft.lineId}: enter operators, helpers and finishing staff (use 0 if there are none)."
    }
    if (draft.totalManpower() <= 0) return "Line ${draft.lineId}: total manpower must be more than 0."
    if ((draft.effText.toDoubleOrNull() ?: 0.0) <= 0.0) return "Line ${draft.lineId}: enter the target %."
    return null
  }

  fun buildSetupFromDraft(draft: LineRowDraft): SupervisorLineSetup {
    val existing = repository.getLineSetup(draft.lineId)
    val smvVal = draft.smvText.toDoubleOrNull()?.coerceAtLeast(0.1) ?: existing.smv
    val hourlyVal = draft.autoHourly()
    val dailyVal = hourlyVal * 8

    return existing.copy(
      lineId = draft.lineId,
      floor = draft.floor,
      date = repository.getCurrentBangladeshDateIso(),
      itemName = draft.itemName.trim().ifBlank { existing.itemName },
      smv = smvVal,
      operatorsCount = draft.operatorsText.toIntOrNull() ?: 0,
      helpersCount = draft.helpersText.toIntOrNull() ?: 0,
      finishingStaffCount = draft.finishingText.toIntOrNull() ?: 0,
      targetEfficiencyPct = draft.effText.toDoubleOrNull() ?: existing.targetEfficiencyPct,
      includeHelpersInEfficiency = true,
      targetDailyPcs = dailyVal,
      targetHourlyPcs = hourlyVal
    )
  }

  fun saveAllFloorLines() {
    if (!canMorningTargetWrite) return
    // A completely untouched row (no SMV and no manpower) is skipped. Every other row must be complete.
    val active = floorLines.mapNotNull { line -> rowDrafts[line.id] }
      .filter { it.smvText.isNotBlank() || it.operatorsText.isNotBlank() || it.helpersText.isNotBlank() || it.finishingText.isNotBlank() }
    val firstProblem = active.firstNotNullOfOrNull { problemWith(it) }
    if (firstProblem != null) {
      saveSuccessMsg = "⚠️ $firstProblem Nothing was saved."
      return
    }
    if (active.isEmpty()) {
      saveSuccessMsg = "⚠️ Enter SMV and manpower for at least one line."
      return
    }
    repository.saveBatchLineSetups(active.map { buildSetupFromDraft(it) })
    populateRowDrafts()
    saveSuccessMsg = "✓ Saved ${active.size} lines for ${selectedFloor.displayName} (hourly targets worked out automatically, synced to Cloud & TV Dashboard)"
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
              text = "Item, SMV, manpower and target % • hourly target is worked out automatically (Daily = Hourly × 8)",
              color = Color(0xFFBAE6FD),
              fontSize = 11.sp
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("setup_back_btn")
          ) {
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
                .heightIn(min = 40.dp)
                .testTag("save_all_floor_targets_btn")
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save All", fontSize = 12.sp, fontWeight = FontWeight.Black)
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
        .imePadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. Floor Selector Tabs (Sew-1 | Sew-2 | Sew-3 | All)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf(FloorScope.SEW_1, FloorScope.SEW_2, FloorScope.SEW_3, FloorScope.ALL).forEach { floor ->
          val isSelected = selectedFloor == floor
          val lineCountForFloor = repository.allLinesMaster.count { line ->
            val isFinishing = line.id.startsWith("Iron", ignoreCase = true) || line.id.startsWith("Packing", ignoreCase = true)
            !isFinishing && (floor == FloorScope.ALL || line.floor == floor.apiValue)
          }
          Surface(
            onClick = {
              selectedFloorName = floor.name
              saveSuccessMsg = null
            },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) Color(0xFF0F172A) else Color.White,
            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
          ) {
            Column(
              modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = floor.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color(0xFF334155)
              )
              Text(
                text = "$lineCountForFloor Lines",
                fontSize = 11.sp,
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
              Text("View-Only Mode (Target Setup Locked)", color = Color(0xFF92400E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
              Text("Only authorized IE and Production Reporter accounts can edit line targets and SMV.", color = Color(0xFFB45309), fontSize = 11.sp)
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
              Text(msg, color = Color(0xFF166534), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 2. 4-Column Morning Target Spreadsheet Card
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
                  text = "Hourly target = (operators + helpers + finishing) × 60 × target % ÷ SMV",
                  fontSize = 11.sp,
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
              TableHeaderCell("SMV", 62.dp, TextAlign.Center)
              TableHeaderCell("Operators", 66.dp, TextAlign.Center)
              TableHeaderCell("Helpers", 62.dp, TextAlign.Center)
              TableHeaderCell("Finishing", 62.dp, TextAlign.Center)
              TableHeaderCell("Total", 54.dp, TextAlign.Center)
              TableHeaderCell("Target %", 62.dp, TextAlign.Center)
              TableHeaderCell("Hourly (auto)", 84.dp, TextAlign.Center)
              if (canMorningTargetWrite) {
                TableHeaderCell("Save", 54.dp, TextAlign.Center)
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
                  modifier = Modifier.width(54.dp).heightIn(min = 36.dp)
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
                  width = 62.dp,
                  enabled = canMorningTargetWrite,
                  isDecimal = true,
                  placeholder = "SMV",
                  imeAction = ImeAction.Next,
                  testTag = if (idx == 0) "setup_smv_input" else null
                )

                // Columns 4-6: Operators | Helpers | Finishing
                CompactTableNumberField(
                  value = draft.operatorsText,
                  onValueChange = { v -> rowDrafts[lineInfo.id] = draft.copy(operatorsText = v.filter { it.isDigit() }.take(3)) },
                  width = 66.dp, enabled = canMorningTargetWrite, isDecimal = false, placeholder = "Opr",
                  imeAction = ImeAction.Next, testTag = if (idx == 0) "setup_operators_input" else null
                )
                CompactTableNumberField(
                  value = draft.helpersText,
                  onValueChange = { v -> rowDrafts[lineInfo.id] = draft.copy(helpersText = v.filter { it.isDigit() }.take(3)) },
                  width = 62.dp, enabled = canMorningTargetWrite, isDecimal = false, placeholder = "Hlp",
                  imeAction = ImeAction.Next, testTag = if (idx == 0) "setup_helpers_input" else null
                )
                CompactTableNumberField(
                  value = draft.finishingText,
                  onValueChange = { v -> rowDrafts[lineInfo.id] = draft.copy(finishingText = v.filter { it.isDigit() }.take(3)) },
                  width = 62.dp, enabled = canMorningTargetWrite, isDecimal = false, placeholder = "Fin",
                  imeAction = ImeAction.Next, testTag = if (idx == 0) "setup_finishing_input" else null
                )
                // Column 7: Total manpower (worked out)
                Surface(color = Color(0xFFE0F2FE), shape = RoundedCornerShape(5.dp), modifier = Modifier.width(54.dp).heightIn(min = 36.dp)) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(text = draft.totalManpower().takeIf { it > 0 }?.toString() ?: "—", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF075985))
                  }
                }
                // Column 8: Target efficiency %
                CompactTableNumberField(
                  value = draft.effText,
                  onValueChange = { v -> rowDrafts[lineInfo.id] = draft.copy(effText = v.filter { it.isDigit() || it == '.' }.take(5)) },
                  width = 62.dp, enabled = canMorningTargetWrite, isDecimal = true, placeholder = "%",
                  imeAction = if (idx == floorLines.lastIndex) ImeAction.Done else ImeAction.Next, testTag = if (idx == 0) "setup_target_eff_input" else null
                )
                // Column 9: Hourly target, worked out automatically
                Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(5.dp), modifier = Modifier.width(84.dp).heightIn(min = 36.dp)) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = draft.autoHourly().takeIf { it > 0 }?.let { "$it /hr" } ?: "—",
                      fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF166534),
                      modifier = Modifier.testTag("setup_hourly_auto_${lineInfo.id}")
                    )
                  }
                }
                // Column 5: Quick Single-Row Save Button
                if (canMorningTargetWrite) {
                  Surface(
                    onClick = {
                      val problem = problemWith(draft)
                      if (problem != null) {
                        saveSuccessMsg = "⚠️ $problem"
                      } else {
                        val setup = buildSetupFromDraft(draft)
                        repository.saveLineSetup(setup)
                        saveSuccessMsg = "✓ Saved Line ${draft.lineId}: Hourly Target ${setup.targetHourlyPcs} pcs/hr (${setup.targetDailyPcs} pcs/day)"
                      }
                    },
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier
                      .width(54.dp)
                      .heightIn(min = 36.dp)
                      .testTag("setup_save_row_${lineInfo.id}")
                  ) {
                    Row(
                      modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
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
                      Text("Save", color = Color(0xFF15803D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                .heightIn(min = 48.dp)
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

      // 3. Floor totals (not line by line): machines, operators, helpers and handwork staff present today
      FloorManpowerCard(
        repository = repository,
        floor = selectedFloor,
        plannedOperators = floorLines.sumOf { rowDrafts[it.id]?.operatorsText?.toIntOrNull() ?: 0 },
        canWrite = canMorningTargetWrite
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorManpowerCard(
  repository: NflRepository,
  floor: FloorScope,
  plannedOperators: Int,
  canWrite: Boolean
) {
  val entries by repository.floorManpower.collectAsState()
  val isSingleFloor = floor == FloorScope.SEW_1 || floor == FloorScope.SEW_2 || floor == FloorScope.SEW_3
  val draft = rememberSaveable(floor.name, saver = StringMapSaver) { mutableStateMapOf<String, String>() }
  var message by remember { mutableStateOf<String?>(null) }
  var isError by remember { mutableStateOf(false) }

  LaunchedEffect(floor.name, entries.size) {
    if (isSingleFloor && draft.isEmpty()) {
      val today = repository.getCurrentBangladeshDateIso()
      val mine = entries.filter { it.floor.equals(floor.apiValue, ignoreCase = true) }
      val todayRow = mine.find { it.date == today }
      val last = mine.maxByOrNull { it.date }
      if (todayRow != null) {
        draft["tm"] = todayRow.totalMachines.toString(); draft["mr"] = todayRow.machinesRunning.toString()
        draft["op"] = todayRow.operatorsPresent.toString(); draft["hp"] = todayRow.helpersPresent.toString(); draft["hw"] = todayRow.operatorsOnHandwork.toString()
      } else if (last != null) {
        draft["tm"] = last.totalMachines.toString() // total machines hardly changes day to day
      }
    }
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth().testTag("floor_manpower_card")
  ) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("${floor.displayName} — Floor Manpower & Machines (today)", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
      if (!isSingleFloor) {
        Text("Choose Sew 1, Sew 2 or Sew 3 above to enter that floor's totals.", fontSize = 12.sp, color = Color(0xFF64748B))
        return@Column
      }
      Text("Typed once for the whole floor, not line by line. When helpers are short, operators do handwork: type how many operators are on handwork (HW) now. They are part of the operators present.", fontSize = 11.sp, color = Color(0xFF64748B))
      val fields = listOf("tm" to "Total machines", "mr" to "Machines running", "op" to "Operators present", "hp" to "Helpers present", "hw" to "Operators doing handwork (HW)")
      fields.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          row.forEach { (code, label) ->
            OutlinedTextField(
              value = draft[code].orEmpty(),
              onValueChange = { v -> if (canWrite && v.length <= 4 && v.all { it.isDigit() }) draft[code] = v },
              label = { Text(label, fontSize = 10.sp, maxLines = 1) },
              singleLine = true,
              enabled = canWrite,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
              modifier = Modifier.weight(1f).testTag("floor_mp_$code")
            )
          }
          if (row.size == 1) Spacer(Modifier.weight(1f))
        }
      }
      fun n(code: String) = draft[code].orEmpty().toIntOrNull() ?: 0
      val live = ManpowerSummary(1, plannedOperators, n("op"), n("hp"), n("hw"), n("tm"), n("mr"))
      Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Operators present: ${live.operatorsPresent} of ${live.operatorsPlanned} planned (${live.operatorsPresentPct.toInt()}%)", fontSize = 12.sp, color = Color(0xFF0F172A))
          Text("Machines running: ${live.machinesRunning} of ${live.totalMachines} (${live.machinesRunningPct.toInt()}%)", fontSize = 12.sp, color = Color(0xFF0F172A))
          Text("Running machines per present operator: ${live.machinesPerOperatorPct.toInt()}%", fontSize = 12.sp, color = Color(0xFF0F172A))
          Text("Helpers as % of machines: ${live.helpersPctOfMachines.toInt()}%", fontSize = 12.sp, color = Color(0xFF0F172A))
          Text("Operators on handwork (HW): ${live.operatorsOnHandwork} (${live.handworkPctOfOperators.toInt()}% of operators present)", fontSize = 12.sp, color = Color(0xFF0F172A))
        }
      }
      message?.let {
        Text(it, color = if (isError) Color(0xFFB91C1C) else Color(0xFF15803D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.testTag("floor_mp_message"))
      }
      Button(
        onClick = {
          val v = listOf("tm", "mr", "op", "hp", "hw").map { draft[it].orEmpty().trim() }
          if (v.any { it.isEmpty() }) { message = "Fill all five boxes (use 0 if none)."; isError = true }
          else {
            val r = repository.saveFloorManpower(floor.apiValue, v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt(), v[4].toInt())
            message = r.message; isError = !r.success
          }
        },
        enabled = canWrite,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NflRoyalBlue),
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("floor_mp_save_btn")
      ) { Text("Save ${floor.displayName} totals", fontWeight = FontWeight.Bold) }
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
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    textStyle = TextStyle(
      color = if (enabled) Color(0xFF0A0F1D) else Color(0xFF1E293B),
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      textAlign = textAlign
    ),
    cursorBrush = SolidColor(Color(0xFF1D4ED8)),
    modifier = Modifier
      .width(width)
      .heightIn(min = 36.dp)
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
          .padding(horizontal = 6.dp, vertical = 6.dp),
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
  imeAction: ImeAction = ImeAction.Next,
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
      keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
      imeAction = imeAction
    ),
    modifier = Modifier
      .width(width)
      .heightIn(min = 36.dp)
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
          .padding(horizontal = 4.dp, vertical = 6.dp),
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

