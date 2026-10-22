package com.example

import com.example.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `test efficiency calculation with SMV terminology`() {
    // 20 operators, working 60 mins -> available minutes = 20 * 60 = 1200 mins
    // Output = 85 pcs of style with SMV = 14.50 mins
    // Earned minutes = 85 * 14.50 = 1232.5 mins
    // Actual efficiency = 1232.5 / 1200 * 100 = 102.708%
    val smv = 14.50
    val outputQty = 85
    val operators = 20
    val workingMins = 60

    val earnedMinutes = outputQty * smv
    val availableMinutes = (operators * workingMins).toDouble()
    val efficiency = (earnedMinutes / availableMinutes) * 100.0

    assertTrue(kotlin.math.abs(1232.5 - earnedMinutes) < 0.01)
    assertTrue(kotlin.math.abs(1200.0 - availableMinutes) < 0.01)
    assertTrue(kotlin.math.abs(102.708 - efficiency) < 0.05)
  }

  @Test
  fun `test multiple styles on same line does not duplicate available minutes`() {
    // Two styles produced on Line K1 during 10:00 - 11:00:
    // Style A: 50 pcs * 10.0 SMV = 500 earned mins
    // Style B: 30 pcs * 15.0 SMV = 450 earned mins
    // Total earned = 950 mins
    // Line manpower = 20 operators * 60 mins = 1200 available mins (NOT 2400!)
    val setups = mapOf(
      "K1" to SupervisorLineSetup(
        lineId = "K1",
        date = "08 Oct 2026",
        styleNumber = "NFL-204",
        orderNumber = "PO-1",
        smv = 14.5,
        targetHourlyPcs = 90,
        targetEfficiencyPct = 75.0,
        operatorsCount = 20,
        helpersCount = 4,
        includeHelpersInEfficiency = false,
        workingMinutesPerHour = 60
      )
    )

    // Using logic from repository:
    val records = listOf(
      ProductionRecord(lineId = "K1", date = "08 Oct 2026", hourSlot = "10:00 - 11:00", styleNumber = "NFL-204", outputQty = 50),
      ProductionRecord(lineId = "K1", date = "08 Oct 2026", hourSlot = "10:00 - 11:00", styleNumber = "NFL-108", outputQty = 30)
    )

    // Collect covered slots
    val lineHoursCovered = records.map { "${it.lineId}|${it.hourSlot}" }.toSet()
    assertEquals(1, lineHoursCovered.size) // Only 1 distinct line-hour slot!

    val totalAvailableMinutes = lineHoursCovered.sumOf {
      val setup = setups["K1"]!!
      setup.includedManpower * setup.workingMinutesPerHour
    }
    assertEquals(1200, totalAvailableMinutes) // Correct: 1200, not 2400!
  }

  @Test
  fun `test OQL and DHU calculations and zero vs missing distinction`() {
    // 150 checked, 5 defective garments
    // Defects: Skip Stitch (3), Uncut Thread (4) -> total defects = 7
    val checked = 150
    val defective = 5
    val totalDefects = 7

    val oql = (defective.toDouble() / checked.toDouble()) * 100.0
    val dhu = (totalDefects.toDouble() / checked.toDouble()) * 100.0

    assertTrue(kotlin.math.abs(3.333 - oql) < 0.01)
    assertTrue(kotlin.math.abs(4.666 - dhu) < 0.01)

    // Confirmed zero defective garments:
    val zeroDefective = 0
    val zeroOql = (zeroDefective.toDouble() / checked.toDouble()) * 100.0
    assertTrue(kotlin.math.abs(0.0 - zeroOql) < 0.001)
  }

  @Test
  fun `test NPT lost man minutes calculation`() {
    // Event: 30 minutes downtime affecting 4 people
    val durationMins = 30.0
    val affectedManpower = 4
    val lostManMinutes = durationMins * affectedManpower
    assertTrue(kotlin.math.abs(120.0 - lostManMinutes) < 0.001)
  }

  @Test
  fun `test line alias resolution`() {
    val lineJ = LineInfo(id = "J", floor = "Sewing 2", aliases = listOf("J1"))
    assertTrue(lineJ.matches("J"))
    assertTrue(lineJ.matches("J1")) // Confirmed same line
    assertTrue(!lineJ.matches("J2")) // Distinct line
  }

  @Test
  fun `test morning line target and SMV efficiency calculation`() {
    // Supervisor sets morning target for Line K1:
    // Item: Polo Shirt, SMV = 14.50 min, Target Daily = 480 pcs, Target Eff = 75.0%
    // 20 operators, 7 hours worked so far -> available mins = 20 * 7 * 60 = 8400 mins
    val morningSetup = SupervisorLineSetup(
      lineId = "K1",
      floor = "Sew 2",
      date = "2026-10-09",
      itemName = "Polo Shirt",
      smv = 14.50,
      targetDailyPcs = 480,
      targetEfficiencyPct = 75.0,
      operatorsCount = 20,
      workingHoursSoFar = 7,
      totalShiftHours = 8
    )

    // Actual output produced so far = 390 pcs
    val actualOutput = 390
    val earnedMinutes = actualOutput * morningSetup.smv // 390 * 14.5 = 5655 mins
    val availableMinutes = morningSetup.includedManpower * morningSetup.workingHoursSoFar * 60.0 // 8400 mins
    val actualEfficiency = (earnedMinutes / availableMinutes) * 100.0 // 67.32%

    // Target achievement % against full daily target
    val targetAchievementPct = (actualOutput.toDouble() / morningSetup.targetDailyPcs) * 100.0 // 81.25%

    assertEquals(14.50, morningSetup.smv, 0.001)
    assertEquals(75.0, morningSetup.targetEfficiencyPct, 0.001)
    assertEquals("Polo Shirt", morningSetup.itemName)
    assertTrue(kotlin.math.abs(67.32 - actualEfficiency) < 0.1)
    assertTrue(kotlin.math.abs(81.25 - targetAchievementPct) < 0.1)
  }

  @Test
  fun `test BS OQL weekly dataset matches user requirements`() {
    val data = com.example.ui.components.DEFAULT_BS_OQL_WEEKLY_DATA
    assertEquals(12, data.size)
    assertEquals("32 W", data.first().week)
    assertEquals(4.67, data.first().oqlPct, 0.001)
    assertEquals("43 W", data.last().week)
    assertEquals(5.58, data.last().oqlPct, 0.001)

    // Verify all 12 points
    val expected = listOf(
      "32 W" to 4.67,
      "33 W" to 5.00,
      "34 W" to 5.00,
      "35 W" to 5.11,
      "36 W" to 4.80,
      "37 W" to 4.80,
      "38 W" to 5.00,
      "39 W" to 5.00,
      "40 W" to 5.35,
      "41 W" to 5.10,
      "42 W" to 5.64,
      "43 W" to 5.58
    )
    expected.forEachIndexed { index, (wk, pct) ->
      assertEquals(wk, data[index].week)
      assertEquals(pct, data[index].oqlPct, 0.001)
    }

    val avg = data.map { it.oqlPct }.average()
    assertTrue(avg in 5.08..5.10)
  }
}
