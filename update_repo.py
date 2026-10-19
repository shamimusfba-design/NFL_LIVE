with open('app/src/main/java/com/example/data/NflRepository.kt', 'r') as f:
    text = f.read()

new_seeder_and_dashboard = '''  // Idempotent demo data seeder
  fun loadSeedDemoData() {
    val date = "2026-10-09"
    val demoTargets = mutableListOf<SupervisorLineSetup>()
    val demoProds = mutableListOf<ProductionRecord>()
    val demoQas = mutableListOf<QualityRecord>()

    data class LineSeedData(
      val lineId: String,
      val itemName: String,
      val smv: Double,
      val targetPcs: Int,
      val targetEffPct: Double,
      val outputPcs: Int?
    )

    fun populateFloorWithSeeds(floorName: String, seeds: List<LineSeedData>) {
      seeds.forEach { seed ->
        demoTargets.add(
          SupervisorLineSetup(
            lineId = seed.lineId,
            floor = floorName,
            date = date,
            styleNumber = "",
            itemName = seed.itemName,
            targetDailyPcs = seed.targetPcs,
            targetHourlyPcs = (seed.targetPcs / 8).coerceAtLeast(1),
            targetEfficiencyPct = seed.targetEffPct,
            smv = seed.smv,
            operatorsCount = 20,
            machinesCount = 20,
            workingHoursSoFar = 7,
            totalShiftHours = 8,
            isDemo = true
          )
        )

        if (seed.outputPcs != null) {
          demoProds.add(
            ProductionRecord(
              lineId = seed.lineId,
              floor = floorName,
              date = date,
              hourSlot = "15:00 - 16:00",
              styleNumber = "",
              outputQty = seed.outputPcs,
              isConfirmedZero = seed.outputPcs == 0,
              modifiedBy = "Md. Rafiqul",
              isDemo = true
            )
          )

          demoQas.add(
            QualityRecord(
              lineId = seed.lineId,
              floor = floorName,
              date = date,
              hourSlot = "15:00 - 16:00",
              styleNumber = "",
              checkedGarments = 100,
              defectiveGarments = if (seed.lineId == "K1") 4 else 3,
              defects = listOf(
                DefectItem("Poor iron", 2),
                DefectItem("Uncut thread", 2)
              ),
              modifiedBy = "Nasrin Sultana",
              isDemo = true
            )
          )
        }
      }
    }

    // Sew 1 (22 real factory lines)
    val sew1Seeds = listOf(
      LineSeedData("A1", "Track Jacket", 15.20, 460, 75.0, 430),
      LineSeedData("A2", "Sports Vest", 11.00, 500, 75.0, 470),
      LineSeedData("B", "Sports Vest", 11.00, 500, 75.0, 480),
      LineSeedData("C", "Polo Shirt", 14.50, 420, 75.0, 390),
      LineSeedData("D1A", "Sports Vest", 11.00, 480, 72.0, 440),
      LineSeedData("D2A", "Basic T-Shirt", 10.50, 540, 78.0, 490),
      LineSeedData("D1B", "Polo Shirt", 14.50, 460, 75.0, 420),
      LineSeedData("D2B", "Crew Neck Tee", 12.20, 510, 75.0, 460),
      LineSeedData("E1A", "Zip Hoodie", 16.80, 400, 70.0, null),
      LineSeedData("E2A", "Raglan Tee", 13.00, 470, 74.0, 420),
      LineSeedData("E1B", "Fleece Jogger", 17.50, 390, 70.0, 340),
      LineSeedData("E2B", "Basic T-Shirt", 10.50, 530, 78.0, 480),
      LineSeedData("FA", "Track Jacket", 15.20, 480, 75.0, 430),
      LineSeedData("FB", "Polo Pique", 14.80, 450, 75.0, 400),
      LineSeedData("G1A", "Sports Vest", 11.00, 500, 72.0, 450),
      LineSeedData("G2A", "Polo Shirt", 14.50, 440, 75.0, 390),
      LineSeedData("G1B", "Zip Hoodie", 16.80, 410, 70.0, 360),
      LineSeedData("G2B", "Crew Neck Tee", 12.20, 520, 75.0, 470),
      LineSeedData("H1A", "Track Jacket", 15.20, 470, 75.0, 420),
      LineSeedData("H2A", "Basic T-Shirt", 10.50, 560, 78.0, 510),
      LineSeedData("H1B", "Raglan Tee", 13.00, 480, 74.0, 430),
      LineSeedData("H2B", "Sports Vest", 11.00, 490, 72.0, 440)
    )

    // Sew 2 (14 real factory lines)
    val sew2Seeds = listOf(
      LineSeedData("J1", "Track Jacket", 15.20, 500, 75.0, 450),
      LineSeedData("K1", "Sports Vest", 11.00, 420, 72.0, 340),
      LineSeedData("L1", "Polo Shirt", 14.50, 480, 75.0, 390),
      LineSeedData("L2", "Basic T-Shirt", 10.50, 550, 78.0, 490),
      LineSeedData("M1", "Zip Hoodie", 16.80, 380, 70.0, 310),
      LineSeedData("M2", "Polo Shirt", 14.50, 460, 75.0, 400),
      LineSeedData("N1", "Sports Vest", 11.00, 450, 72.0, null),
      LineSeedData("N2", "Track Jacket", 15.20, 620, 76.0, 560),
      LineSeedData("O1", "Crew Neck Tee", 12.20, 520, 75.0, 480),
      LineSeedData("O2", "Raglan Tee", 13.00, 490, 74.0, 430),
      LineSeedData("P1", "Polo Pique", 14.80, 480, 75.0, 420),
      LineSeedData("Q1", "Fleece Jogger", 17.50, 400, 70.0, 350),
      LineSeedData("Q2", "Basic T-Shirt", 10.50, 580, 78.0, 510),
      LineSeedData("R1", "Zip Hoodie", 16.80, 380, 70.0, 320)
    )

    // Sew 3 (24 real factory lines)
    val sew3Seeds = listOf(
      LineSeedData("S1", "Track Jacket", 15.20, 520, 75.0, 490),
      LineSeedData("S2", "Polo Shirt", 14.50, 470, 75.0, 420),
      LineSeedData("T1", "Sports Vest", 11.00, 480, 72.0, 450),
      LineSeedData("T2", "Basic T-Shirt", 10.50, 550, 78.0, 500),
      LineSeedData("U", "Polo Shirt", 14.50, 440, 75.0, 410),
      LineSeedData("UA", "Crew Neck Tee", 12.20, 510, 75.0, 460),
      LineSeedData("UB", "Zip Hoodie", 16.80, 400, 70.0, 350),
      LineSeedData("V1A", "Sports Vest", 11.00, 500, 72.0, 470),
      LineSeedData("V1B", "Fleece Jogger", 17.50, 390, 70.0, 340),
      LineSeedData("V2A", "Polo Pique", 14.80, 460, 75.0, 410),
      LineSeedData("V2B", "Track Jacket", 15.20, 480, 75.0, 430),
      LineSeedData("W1A", "Zip Hoodie", 16.80, 460, 70.0, null),
      LineSeedData("W1B", "Basic T-Shirt", 10.50, 540, 78.0, 480),
      LineSeedData("W2A", "Raglan Tee", 13.00, 490, 74.0, 440),
      LineSeedData("W2B", "Polo Shirt", 14.50, 450, 75.0, 400),
      LineSeedData("X1", "Track Jacket", 15.20, 550, 75.0, 510),
      LineSeedData("Y1", "Sports Vest", 11.00, 490, 72.0, 440),
      LineSeedData("Y2", "Crew Neck Tee", 12.20, 520, 75.0, 470),
      LineSeedData("Z1", "Zip Hoodie", 16.80, 410, 70.0, 360),
      LineSeedData("Z2", "Polo Shirt", 14.50, 460, 75.0, 410),
      LineSeedData("ZA1A", "Fleece Jogger", 17.50, 380, 70.0, 330),
      LineSeedData("ZA1B", "Basic T-Shirt", 10.50, 530, 78.0, 480),
      LineSeedData("ZA2A", "Raglan Tee", 13.00, 470, 74.0, 420),
      LineSeedData("ZA2B", "Track Jacket", 15.20, 500, 75.0, 450)
    )

    populateFloorWithSeeds("Sew 1", sew1Seeds)
    populateFloorWithSeeds("Sew 2", sew2Seeds)
    populateFloorWithSeeds("Sew 3", sew3Seeds)

    // Active incidents with authoritative timestamps surviving reload
    val now = System.currentTimeMillis()
    val demoIncidents = listOf(
      IncidentItem(
        lineId = "K1",
        floor = "Sew 2",
        category = IncidentCategory.MACHINE_BREAKDOWN,
        machineRef = "SNLS-04",
        description = "Motor overheated and needle bar vibrating severely",
        reportedCause = "Bearing worn out in drive belt",
        impactLevel = "HIGH",
        affectedManpower = 6,
        startTimeMillis = now - 22 * 60 * 1000L,
        acknowledgedTimeMillis = now - 15 * 60 * 1000L,
        acknowledgedBy = "Jahangir Hossain (Mechanic)",
        status = IncidentStatus.ACKNOWLEDGED,
        reportedBy = "Tariq Hasan (Supervisor)",
        isDemo = true
      ),
      IncidentItem(
        lineId = "L2",
        floor = "Sew 2",
        category = IncidentCategory.QUALITY_PROBLEM,
        machineRef = "Overlock-02",
        description = "Recurring puckering along side seam on Jacket B",
        reportedCause = "Thread tension miscalibrated on needle 2",
        impactLevel = "MEDIUM",
        affectedManpower = 4,
        startTimeMillis = now - 11 * 60 * 1000L,
        status = IncidentStatus.OPEN,
        reportedBy = "Nasrin Sultana (QA Inspector)",
        isDemo = true
      ),
      IncidentItem(
        lineId = "N1",
        floor = "Sew 2",
        category = IncidentCategory.LOW_PRODUCTION,
        description = "Output gap > 25% behind target due for slot 15:00-16:00",
        reportedCause = "Waiting for collar rib input from cutting section",
        impactLevel = "HIGH",
        affectedManpower = 20,
        startTimeMillis = now - 35 * 60 * 1000L,
        status = IncidentStatus.OPEN,
        reportedBy = "System Low-Production Monitor",
        isDemo = true
      )
    )

    _targets.value = _targets.value.filter { !it.isDemo } + demoTargets
    _productionEntries.value = _productionEntries.value.filter { !it.isDemo } + demoProds
    _qualityEntries.value = _qualityEntries.value.filter { !it.isDemo } + demoQas
    _incidents.value = _incidents.value.filter { !it.isDemo } + demoIncidents
    _isDemoMode.value = true
  }

  fun updateLineMorningTarget(
    lineId: String,
    floor: String = "Sew 2",
    itemName: String,
    smv: Double,
    targetDailyPcs: Int,
    targetEfficiencyPct: Double,
    operatorsCount: Int = 20,
    helpersCount: Int = 2
  ) {
    val existing = getLineSetup(lineId)
    val updated = existing.copy(
      lineId = lineId,
      floor = floor,
      itemName = itemName,
      smv = smv,
      targetDailyPcs = targetDailyPcs,
      targetHourlyPcs = (targetDailyPcs / 8).coerceAtLeast(1),
      targetEfficiencyPct = targetEfficiencyPct,
      operatorsCount = operatorsCount,
      helpersCount = helpersCount,
      snapshotTimestamp = System.currentTimeMillis()
    )
    saveLineSetup(updated)
  }

  fun removeAllDemoData() {
    _targets.value = _targets.value.filter { !it.isDemo }
    _productionEntries.value = _productionEntries.value.filter { !it.isDemo }
    _qualityEntries.value = _qualityEntries.value.filter { !it.isDemo }
    _incidents.value = _incidents.value.filter { !it.isDemo }
    _isDemoMode.value = false
    addAuditLog("DEMO", "ALL", "PURGE_DEMO", "Admin", "Removed all demo records while preserving real logs")
  }

  // Submissions
  fun submitProduction(entry: ProductionRecord): ProductionRecord {
    _productionEntries.value = listOf(entry) + _productionEntries.value
    addAuditLog("PRODUCTION", entry.lineId, "ADD_OUTPUT", entry.modifiedBy, "Qty: ${entry.outputQty}")
    return entry
  }

  fun submitQuality(entry: QualityRecord): QualityRecord {
    _qualityEntries.value = listOf(entry) + _qualityEntries.value
    addAuditLog("QUALITY", entry.lineId, "ADD_INSPECTION", entry.modifiedBy, "Checked: ${entry.checkedGarments}, Defective: ${entry.defectiveGarments}")
    return entry
  }

  fun saveLineSetup(setup: SupervisorLineSetup) {
    val filtered = _targets.value.filter { !(it.lineId == setup.lineId && it.date == setup.date) }
    _targets.value = listOf(setup) + filtered
    addAuditLog("SETUP", setup.lineId, "SAVE_TARGET", "Supervisor", "Daily target: ${setup.targetDailyPcs}, Eff: ${setup.targetEfficiencyPct}%")
  }

  // Incident Actions
  fun reportIncident(item: IncidentItem): IncidentItem {
    _incidents.value = listOf(item) + _incidents.value
    addAuditLog("INCIDENT", item.lineId, "REPORT_INCIDENT", item.reportedBy, item.description)
    return item
  }

  fun muteIncident(id: String) {
    _incidents.value = _incidents.value.map {
      if (it.id == id) it.copy(isMutedOnCurrentDevice = true) else it
    }
  }

  fun acknowledgeIncident(id: String, ackBy: String) {
    _incidents.value = _incidents.value.map {
      if (it.id == id) it.copy(
        status = IncidentStatus.ACKNOWLEDGED,
        acknowledgedBy = ackBy,
        acknowledgedTimeMillis = System.currentTimeMillis()
      ) else it
    }
    addAuditLog("INCIDENT", id, "ACKNOWLEDGE", ackBy, "Accepted ownership")
  }

  fun resolveIncident(id: String, resolvedBy: String, note: String) {
    _incidents.value = _incidents.value.map {
      if (it.id == id) it.copy(
        status = IncidentStatus.RESOLVED,
        resolvedBy = resolvedBy,
        resolutionNote = note,
        endTimeMillis = System.currentTimeMillis()
      ) else it
    }
    addAuditLog("INCIDENT", id, "RESOLVE", resolvedBy, note)
  }

  private fun addAuditLog(type: String, id: String, action: String, by: String, details: String) {
    _auditLogs.value = listOf(
      AuditLogEntry(entityType = type, entityId = id, action = action, changedBy = by, details = details)
    ) + _auditLogs.value
  }

  // Computes dashboard data ensuring ALL real lines of the floor are always present
  fun getTvDashboardData(floorScope: FloorScope = _currentFloor.value): TvDashboardData {
    val floorFilter = floorScope.apiValue

    // Ensure all real lines for this floor are always present
    val floorLines = allLinesMaster.filter { floorScope == FloorScope.ALL || it.floor == floorFilter }

    val summaries = floorLines.map { lineInfo ->
      val setup = _targets.value.find { it.lineId == lineInfo.id } ?: getLineSetup(lineInfo.id).copy(floor = lineInfo.floor)
      val prods = _productionEntries.value.filter { it.lineId == setup.lineId }
      val qas = _qualityEntries.value.filter { it.lineId == setup.lineId }

      val hasReported = prods.isNotEmpty()
      val outputQty = if (hasReported) prods.sumOf { it.outputQty } else null
      val isConfirmedZero = prods.any { it.isConfirmedZero || it.outputQty == 0 }

      val targetDue = Math.round((setup.targetDailyPcs.toDouble() / setup.totalShiftHours) * setup.workingHoursSoFar).toInt()

      val achPct = if (outputQty != null && setup.targetDailyPcs > 0) {
        ((outputQty.toDouble() / setup.targetDailyPcs) * 100).let { Math.round(it * 10.0) / 10.0 }
      } else null

      val earnedMins = (outputQty ?: 0) * setup.smv
      val availMins = setup.includedManpower * setup.workingHoursSoFar * 60
      val effPct = if (outputQty != null && availMins > 0) {
        ((earnedMins / availMins) * 100).let { Math.round(it * 10.0) / 10.0 }
      } else null

      val checked = qas.sumOf { it.checkedGarments }
      val defective = qas.sumOf { it.defectiveGarments }
      val defectsTotal = qas.sumOf { it.totalDefects }

      val oqlPct = if (checked > 0) ((defective.toDouble() / checked) * 100).let { Math.round(it * 100.0) / 100.0 } else null
      val dhuVal = if (checked > 0) ((defectsTotal.toDouble() / checked) * 100).let { Math.round(it * 100.0) / 100.0 } else null

      val openIncs = _incidents.value.count { it.lineId == setup.lineId && it.status != IncidentStatus.RESOLVED }

      LinePerformancePoint(
        lineId = setup.lineId,
        floor = setup.floor,
        styleNumber = setup.styleNumber,
        itemName = setup.itemName,
        smv = setup.smv,
        targetPcs = setup.targetDailyPcs,
        targetDuePcs = targetDue,
        targetEfficiencyPct = setup.targetEfficiencyPct,
        achievementPcs = outputQty,
        isConfirmedZero = isConfirmedZero,
        achievementPct = achPct,
        todayEfficiencyPct = effPct,
        monthlyEfficiencyPct = 65.0,
        todayOqlPct = oqlPct,
        monthlyOqlPct = 3.6,
        dhu = dhuVal,
        reportingStatus = if (hasReported) "Complete" else "Not reported",
        openIncidentsCount = openIncs
      )
    }

    val reportedOnly = summaries.filter { it.achievementPcs != null }
    val totalOutput = reportedOnly.sumOf { it.achievementPcs ?: 0 }
    val totalTarget = summaries.sumOf { it.targetPcs }
    val totalTargetDue = summaries.sumOf { it.targetDuePcs }

    val effSum = reportedOnly.mapNotNull { it.todayEfficiencyPct }.average().let { if (it.isNaN()) 68.0 else Math.round(it * 10.0) / 10.0 }
    val targetEffAvg = summaries.map { it.targetEfficiencyPct }.average().let { if (it.isNaN()) 75.0 else Math.round(it * 10.0) / 10.0 }
    val oqlAvg = reportedOnly.mapNotNull { it.todayOqlPct }.average().let { if (it.isNaN()) 3.81 else Math.round(it * 100.0) / 100.0 }

    val kpi = FloorKpiSummary(
      todayEfficiency = effSum,
      todayTargetEfficiency = targetEffAvg,
      monthlyEfficiency = 72.2,
      monthlyEfficiencyDelta = 4.1,
      todayOutputPcs = totalOutput,
      todayTargetPcs = totalTarget,
      todayTargetDuePcs = totalTargetDue,
      todayOql = oqlAvg,
      bsQaOql = 5.00,
      friPassRate = 100.0,
      operatorsCount = summaries.sumOf { getLineSetup(it.lineId).operatorsCount },
      machinesCount = summaries.sumOf { getLineSetup(it.lineId).machinesCount },
      reportingCoveragePct = if (summaries.isNotEmpty()) Math.round((reportedOnly.size.toDouble() / summaries.size) * 100).toInt() else 95,
      openIncidentsCount = _incidents.value.count { it.status != IncidentStatus.RESOLVED }
    )'''

start_marker = '  // Idempotent demo data seeder'
end_marker = '    val topDefects = listOf('

idx_start = text.find(start_marker)
idx_end = text.find(end_marker)

if idx_start != -1 and idx_end != -1:
    new_text = text[:idx_start] + new_seeder_and_dashboard + '\n\n' + text[idx_end:]
    with open('app/src/main/java/com/example/data/NflRepository.kt', 'w') as f:
        f.write(new_text)
    print('Successfully updated NflRepository.kt!')
else:
    print('Failed to find markers!', idx_start, idx_end)
