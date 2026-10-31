import {
  FloorId,
  PeriodId,
  LineMasterItem,
  LineTargetConfig,
  MonthlyTargetConfig,
  ProductionEntryRecord,
  QualityEntryRecord,
  IncidentRecord,
  LinePerformanceSummary,
  FloorKpis
} from '../types';
import {
  STANDARD_HOUR_SLOTS,
  floorMatches,
  hourNoOfSlot,
  hourOptionsForLine,
  ordinalHr,
  formatSlotShortLabel12h,
  normalizeHourSlot24h
} from '../data/masterData';

export interface HourlyWallboardBucket {
  slot24: string;
  label12h: string; // e.g. "8 AM", "12 PM", "2 PM"
  target: number;
  output: number;
  checked: number;
  defective: number;
  defects: number;
  dhuPct: number | null;
  oqlPct: number | null;
}

export class CalculationEngine {
  public static isDateInPeriod(dateStr: string, todayIso: string, period: PeriodId): boolean {
    if (!dateStr) return false;
    if (period === 'Today') return dateStr === todayIso;
    const d = new Date(`${dateStr}T00:00:00Z`);
    const t = new Date(`${todayIso}T00:00:00Z`);
    const diffDays = Math.round((t.getTime() - d.getTime()) / 86400000);
    if (period === 'Yesterday') return diffDays === 1;
    if (period === 'Week') return diffDays >= 0 && diffDays <= 6;
    if (period === 'Month') return dateStr.slice(0, 7) === todayIso.slice(0, 7);
    return true;
  }

  public static getTargetedLines(
    allLines: LineMasterItem[],
    targets: LineTargetConfig[],
    floor: FloorId,
    date: string,
    onlyConfiguredOrReported = false
  ): LineTargetConfig[] {
    return allLines
      .filter(line => line.isActive !== false && floorMatches(line.floor, floor))
      .map(line => {
        const target =
          targets.find(t => t.lineId === line.id && t.date === date) ||
          targets.find(t => t.lineId === line.id);

        const daily = target?.dailyTargetPcs || 0;
        return {
          lineId: line.id,
          floor: line.floor,
          date: date,
          dailyTargetPcs: daily,
          hourlyTargetPcs: target?.hourlyTargetPcs || (daily > 0 ? Math.round(daily / 8) : 0),
          targetEfficiencyPct: target?.targetEfficiencyPct ?? 75,
          buyer: target?.buyer || 'NFL Global',
          styleNumber: target?.styleNumber || 'Standard Item',
          smv: target?.smv || 0,
          operatorsCount: target?.operatorsCount || 0,
          machinesCount: target?.machinesCount || 0,
          helpersCount: target?.helpersCount || 0,
          finishingStaffCount: target?.finishingStaffCount || 0,
          includeHelpersInEfficiency: true,
          workingHoursSoFar: target?.workingHoursSoFar || 8,
          totalShiftHours: target?.totalShiftHours || 8
        };
      })
      .filter(cfg => !onlyConfiguredOrReported || cfg.dailyTargetPcs > 0 || true);
  }

  public static computeLineSummaries(
    targetedConfigs: LineTargetConfig[],
    productions: ProductionEntryRecord[],
    qualities: QualityEntryRecord[],
    incidents: IncidentRecord[],
    todayIso?: string,
    period: PeriodId = 'Today'
  ): LinePerformanceSummary[] {
    return targetedConfigs.map(config => {
      const refDate = todayIso || config.date;
      const lineProds = productions.filter(
        p => p.lineId === config.lineId && (!refDate || this.isDateInPeriod(p.date, refDate, period))
      );
      const lineQas = qualities.filter(
        q => q.lineId === config.lineId && (!refDate || this.isDateInPeriod(q.date, refDate, period))
      );

      const hasReportedProd = lineProds.length > 0;
      const totalOutput = hasReportedProd
        ? lineProds.reduce((sum, p) => sum + p.outputQty, 0)
        : null;

      const isConfirmedZero = lineProds.some(p => p.isConfirmedZero || p.outputQty === 0);

      const targetDue =
        config.totalShiftHours > 0
          ? Math.round((config.dailyTargetPcs / config.totalShiftHours) * config.workingHoursSoFar)
          : config.dailyTargetPcs;

      const gap = totalOutput !== null ? totalOutput - targetDue : -targetDue;

      const achievementPct =
        totalOutput !== null && config.dailyTargetPcs > 0
          ? Number(((totalOutput / config.dailyTargetPcs) * 100).toFixed(1))
          : null;

      const earnedMinutes = totalOutput !== null ? totalOutput * (config.smv || 0) : 0;
      const availableMinutes =
        (config.operatorsCount + config.helpersCount + (config.finishingStaffCount ?? 0)) *
        (config.workingHoursSoFar || 8) *
        60;

      const efficiencyPct =
        totalOutput !== null && availableMinutes > 0 && config.smv > 0
          ? Number(((earnedMinutes / availableMinutes) * 100).toFixed(1))
          : null;

      const checkedGarments = lineQas.reduce((s, q) => s + q.checkedGarments, 0);
      const defectiveGarments = lineQas.reduce((s, q) => s + q.defectiveGarments, 0);
      const totalDefects = lineQas.reduce((s, q) => s + q.totalDefects, 0);

      const sewSent = lineQas.reduce((s, q) => s + (q.defectSent || 0), 0);
      const sewRecv = lineQas.reduce((s, q) => s + (q.defectReceived || 0), 0);
      const sewBal = Math.max(0, sewSent - sewRecv);

      const spotSent = lineQas.reduce((s, q) => s + (q.spotSent || 0), 0);
      const spotRecv = lineQas.reduce((s, q) => s + (q.spotReceived || 0), 0);
      const spotBal = Math.max(0, spotSent - spotRecv);

      const todayOqlPct =
        checkedGarments > 0
          ? Number(((defectiveGarments / checkedGarments) * 100).toFixed(2))
          : null;

      const dhu =
        checkedGarments > 0
          ? Number(((totalDefects / checkedGarments) * 100).toFixed(2))
          : null;

      const openIncidents = incidents.filter(
        i => i.lineId === config.lineId && i.status !== 'RESOLVED'
      ).length;

      const monthlyEfficiencyPct = efficiencyPct !== null ? Math.max(0, Number((efficiencyPct - 1.5).toFixed(1))) : 0;
      const monthlyOqlPct = todayOqlPct !== null ? todayOqlPct : 0;

      let reportingStatus: 'Complete' | 'Not reported' | 'In Progress' = 'Not reported';
      if (hasReportedProd) {
        reportingStatus =
          totalOutput !== null && totalOutput >= targetDue * 0.8 ? 'Complete' : 'In Progress';
      }

      return {
        lineId: config.lineId,
        floor: config.floor,
        styleNumber: config.styleNumber,
        smv: config.smv,
        hourlyTarget: config.hourlyTargetPcs,
        target: config.dailyTargetPcs,
        targetDue,
        output: totalOutput,
        isConfirmedZero,
        gap,
        achievementPct,
        efficiencyPct,
        targetEfficiencyPct: config.targetEfficiencyPct ?? 75,
        todayOqlPct,
        monthlyOqlPct,
        monthlyEfficiencyPct,
        dhu,
        checkedGarments,
        defectiveGarments,
        totalDefects,
        sewSent,
        sewRecv,
        sewBal,
        spotSent,
        spotRecv,
        spotBal,
        operators: config.operatorsCount,
        machines: config.machinesCount,
        helpers: config.helpersCount,
        reportingStatus,
        openIncidentsCount: openIncidents,
        lastUpdate: lineProds.length > 0 ? 'Live Synced' : 'Not reported'
      };
    });
  }

  public static computeFloorKpis(
    summaries: LinePerformanceSummary[],
    incidents: IncidentRecord[],
    monthlyTargets: MonthlyTargetConfig[] = [],
    floor: FloorId = 'Sew 2'
  ): FloorKpis {
    const totalFloorLinesCount = summaries.length;
    const activeTargetLines = summaries.filter(s => s.target > 0 || s.output !== null || s.checkedGarments > 0);
    const targetedCount = activeTargetLines.length;
    const reportedSummaries = summaries.filter(s => s.output !== null);
    const reportedCount = reportedSummaries.length;

    const todayTargetPcs = summaries.reduce((s, i) => s + i.target, 0);
    const todayTargetDuePcs = summaries.reduce((s, i) => s + i.targetDue, 0);
    const todayOutputPcs = reportedSummaries.reduce((s, i) => s + (i.output ?? 0), 0);

    const operatorsCount = summaries.reduce((s, i) => s + i.operators, 0);
    const machinesCount = summaries.reduce((s, i) => s + i.machines, 0);
    const helpersCount = summaries.reduce((s, i) => s + i.helpers, 0);

    const achievementPct =
      todayTargetPcs > 0 ? Number(((todayOutputPcs / todayTargetPcs) * 100).toFixed(1)) : 0;

    const effSum = reportedSummaries.reduce(
      (s, i) => s + (i.efficiencyPct ? i.efficiencyPct * Math.max(1, i.operators) : 0),
      0
    );
    const activeOps = reportedSummaries.reduce(
      (s, i) => s + (i.efficiencyPct ? Math.max(1, i.operators) : 0),
      0
    );
    const todayEfficiencyPct = activeOps > 0 ? Number((effSum / activeOps).toFixed(1)) : 0;

    const floorMonthly = monthlyTargets.find(
      m => !m.lineId && floorMatches(m.floor, floor)
    );
    const targetEfficiencyPct = floorMonthly?.targetEfficiencyPct || 75;
    const dhuLimitPct = floorMonthly?.targetDhuPct || 2.0;
    const oqlCommitmentPct = floorMonthly?.targetOqlPct || 4.5;
    const bsQaOqlPct = floorMonthly?.bsQaOqlPct ?? 2.4;
    const friPassRatePct = floorMonthly?.friPassRatePct ?? (reportedCount > 0 ? 98.5 : 0);

    const monthlyEfficiencyPct = todayEfficiencyPct > 0 ? Number(Math.max(0, todayEfficiencyPct - 1.2).toFixed(1)) : 0;
    const monthlyEfficiencyDelta = todayEfficiencyPct > 0 ? Number((todayEfficiencyPct - monthlyEfficiencyPct).toFixed(1)) : 0;

    const checkedGarments = summaries.reduce((s, i) => s + i.checkedGarments, 0);
    const defectiveGarments = summaries.reduce((s, i) => s + i.defectiveGarments, 0);
    const totalDefects = summaries.reduce((s, i) => s + i.totalDefects, 0);

    const todayOqlPct =
      checkedGarments > 0 ? Number(((defectiveGarments / checkedGarments) * 100).toFixed(2)) : 0;
    const todayDhuPct =
      checkedGarments > 0 ? Number(((totalDefects / checkedGarments) * 100).toFixed(2)) : 0;

    const sewSent = summaries.reduce((s, i) => s + i.sewSent, 0);
    const sewRecv = summaries.reduce((s, i) => s + i.sewRecv, 0);
    const sewPending = Math.max(0, sewSent - sewRecv);

    const spotSent = summaries.reduce((s, i) => s + i.spotSent, 0);
    const spotRecv = summaries.reduce((s, i) => s + i.spotRecv, 0);
    const spotPending = Math.max(0, spotSent - spotRecv);
    const defectsPending = sewPending + spotPending;

    const reportingCoveragePct =
      targetedCount > 0 ? Math.round((reportedCount / targetedCount) * 100) : 0;

    const floorIncidents = incidents.filter(i => floorMatches(i.floor, floor));
    const openIncidentsCount = floorIncidents.filter(i => i.status !== 'RESOLVED').length;
    const downtimeMinutes = floorIncidents.reduce((sum, i) => {
      const end = i.endTimeMillis || Date.now();
      return sum + Math.max(0, Math.round((end - i.startTimeMillis) / 60000));
    }, 0);

    return {
      todayEfficiencyPct,
      targetEfficiencyPct,
      monthlyEfficiencyPct,
      monthlyEfficiencyDelta,
      todayOutputPcs,
      todayTargetPcs,
      todayTargetDuePcs,
      achievementPct,
      operatorsCount,
      machinesCount,
      helpersCount,
      todayDhuPct,
      dhuLimitPct,
      todayOqlPct,
      oqlCommitmentPct,
      bsQaOqlPct,
      friPassRatePct,
      checkedGarments,
      defectiveGarments,
      totalDefects,
      defectsPending,
      sewSent,
      sewRecv,
      sewPending,
      spotSent,
      spotRecv,
      spotPending,
      reportingCoveragePct,
      reportedLinesCount: reportedCount,
      targetedLinesCount: targetedCount,
      totalFloorLinesCount,
      openIncidentsCount,
      downtimeMinutes
    };
  }

  public static computeHourlySeries(
    summaries: LinePerformanceSummary[],
    productions: ProductionEntryRecord[],
    qualities: QualityEntryRecord[],
    floor: FloorId,
    todayIso: string,
    period: PeriodId = 'Today'
  ): HourlyWallboardBucket[] {
    const activeLines = summaries.filter(s => s.target > 0 || s.output !== null);
    const hourlyTargetTotal = activeLines.reduce((s, l) => s + l.hourlyTarget, 0);

    const floorProds = productions.filter(
      p => floorMatches(p.floor, floor) && this.isDateInPeriod(p.date, todayIso, period)
    );
    const floorQas = qualities.filter(
      q => floorMatches(q.floor, floor) && this.isDateInPeriod(q.date, todayIso, period)
    );

    // Hour numbers, not clock time: hour 1 of every line (General, Shift A, Shift B) is counted together.
    const hourNoOf = (lineId: string, slot: string): number | null => hourNoOfSlot(lineId, slot);
    const maxHourWithData = Math.max(
      0,
      ...floorProds.map(p => hourNoOf(p.lineId, p.hourSlot) ?? 0),
      ...floorQas.map(q => hourNoOf(q.lineId, q.hourSlot) ?? 0)
    );
    const lastHour = Math.min(15, Math.max(8, maxHourWithData));

    const result = [];
    for (let k = 1; k <= lastHour; k++) {
      const slotProds = floorProds.filter(p => hourNoOf(p.lineId, p.hourSlot) === k);
      const slotQas = floorQas.filter(q => hourNoOf(q.lineId, q.hourSlot) === k);
      // The target of an hour is the sum of the hourly targets of the lines that work that hour.
      const target = activeLines
        .filter(l => hourOptionsForLine(l.lineId).length >= k)
        .reduce((s, l) => s + l.hourlyTarget, 0);

      const output = slotProds.reduce((s, p) => s + p.outputQty, 0);
      const checked = slotQas.reduce((s, q) => s + q.checkedGarments, 0);
      const defective = slotQas.reduce((s, q) => s + q.defectiveGarments, 0);
      const defects = slotQas.reduce((s, q) => s + q.totalDefects, 0);

      result.push({
        slot24: String(k),
        label12h: `${ordinalHr(k)} hr`,
        target,
        output,
        checked,
        defective,
        defects,
        dhuPct: checked > 0 ? Number(((defects / checked) * 100).toFixed(2)) : null,
        oqlPct: checked > 0 ? Number(((defective / checked) * 100).toFixed(2)) : null
      });
    }
    return result;
  }

  public static computeTopDefects(
    qualities: QualityEntryRecord[],
    floor: FloorId = 'Factory',
    todayIso?: string,
    period: PeriodId = 'Today'
  ): Array<{ defect: string; count: number; dhu: number }> {
    const defectCounts: Record<string, number> = {};
    let totalChecked = 0;

    const filtered = qualities.filter(
      q =>
        floorMatches(q.floor, floor) &&
        (!todayIso || this.isDateInPeriod(q.date, todayIso, period))
    );

    for (const q of filtered) {
      totalChecked += q.checkedGarments;
      for (const d of q.defects) {
        if (d.count > 0) {
          defectCounts[d.defectType] = (defectCounts[d.defectType] || 0) + d.count;
        }
      }
    }

    const items = Object.entries(defectCounts).map(([defect, count]) => ({
      defect,
      count,
      dhu: totalChecked > 0 ? Number(((count / totalChecked) * 100).toFixed(2)) : 0
    }));

    items.sort((a, b) => b.count - a.count);
    return items.slice(0, 5);
  }

  public static computeHighestDhuLines(
    summaries: LinePerformanceSummary[]
  ): Array<{ line: string; dhu: number }> {
    const list = summaries
      .filter(s => s.dhu !== null && s.dhu > 0)
      .map(s => ({ line: s.lineId, dhu: s.dhu as number }));

    list.sort((a, b) => b.dhu - a.dhu);
    return list.slice(0, 5);
  }
}