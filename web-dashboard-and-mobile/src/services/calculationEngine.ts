import {
  FloorId,
  LineMasterItem,
  LineTargetConfig,
  ProductionEntryRecord,
  QualityEntryRecord,
  IncidentRecord,
  LinePerformanceSummary,
  FloorKpis
} from '../types';

export class CalculationEngine {
  /**
   * Maps all active factory lines to their date-matched targets, defaulting to 0 when no setup exists.
   */
  public static getTargetedLines(
    allLines: LineMasterItem[],
    targets: LineTargetConfig[],
    floor: FloorId,
    date: string
  ): LineTargetConfig[] {
    return allLines
      .filter(line => line.isActive !== false && (floor === 'All' || line.floor === floor))
      .map(line => {
        const target =
          targets.find(t => t.lineId === line.id && t.date === date) ||
          targets.find(t => t.lineId === line.id);

        return {
          lineId: line.id,
          floor: line.floor,
          date: date,
          dailyTargetPcs: target?.dailyTargetPcs || 0,
          hourlyTargetPcs: target ? Math.round(target.dailyTargetPcs / 8) : 0,
          buyer: target?.buyer || 'N/A',
          styleNumber: target?.styleNumber || 'N/A',
          smv: target?.smv || 0,
          operatorsCount: target?.operatorsCount || 0,
          machinesCount: target?.machinesCount || 0,
          helpersCount: target?.helpersCount || 0,
          includeHelpersInEfficiency: target?.includeHelpersInEfficiency || false,
          workingHoursSoFar: target?.workingHoursSoFar || 0,
          totalShiftHours: target?.totalShiftHours || 8
        };
      });
  }

  /**
   * Computes comprehensive line summaries with zero synthetic fallback when cleared.
   */
  public static computeLineSummaries(
    targetedConfigs: LineTargetConfig[],
    productions: ProductionEntryRecord[],
    qualities: QualityEntryRecord[],
    incidents: IncidentRecord[]
  ): LinePerformanceSummary[] {
    return targetedConfigs.map(config => {
      const lineProds = productions.filter(
        p => p.lineId === config.lineId && (!config.date || p.date === config.date)
      );
      const lineQas = qualities.filter(
        q => q.lineId === config.lineId && (!config.date || q.date === config.date)
      );

      const hasReportedProd = lineProds.length > 0;
      const totalOutput = hasReportedProd
        ? lineProds.reduce((sum, p) => sum + p.outputQty, 0)
        : null;

      const isConfirmedZero = lineProds.some(p => p.isConfirmedZero || p.outputQty === 0);

      const targetDue =
        config.totalShiftHours > 0
          ? Math.round((config.dailyTargetPcs / config.totalShiftHours) * config.workingHoursSoFar)
          : 0;

      const gap = totalOutput !== null ? totalOutput - targetDue : -targetDue;

      const achievementPct =
        totalOutput !== null && config.dailyTargetPcs > 0
          ? Number(((totalOutput / config.dailyTargetPcs) * 100).toFixed(1))
          : null;

      const earnedMinutes = totalOutput !== null ? totalOutput * (config.smv || 0) : 0;
      const availableMinutes =
        (config.operatorsCount + (config.includeHelpersInEfficiency ? config.helpersCount : 0)) *
        (config.workingHoursSoFar || 1) *
        60;

      const efficiencyPct =
        totalOutput !== null && availableMinutes > 0 && config.smv > 0
          ? Number(((earnedMinutes / availableMinutes) * 100).toFixed(1))
          : null;

      const checkedGarments = lineQas.reduce((s, q) => s + q.checkedGarments, 0);
      const defectiveGarments = lineQas.reduce((s, q) => s + q.defectiveGarments, 0);
      const totalDefects = lineQas.reduce((s, q) => s + q.totalDefects, 0);

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

      const monthlyEfficiencyPct = efficiencyPct !== null ? efficiencyPct : 0;
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
        target: config.dailyTargetPcs,
        targetDue,
        output: totalOutput,
        isConfirmedZero,
        gap,
        achievementPct,
        efficiencyPct,
        todayOqlPct,
        monthlyOqlPct,
        monthlyEfficiencyPct,
        dhu,
        operators: config.operatorsCount,
        machines: config.machinesCount,
        reportingStatus,
        openIncidentsCount: openIncidents,
        lastUpdate: lineProds.length > 0 ? 'Live Synced' : 'Not reported'
      };
    });
  }

  public static computeFloorKpis(
    summaries: LinePerformanceSummary[],
    incidents: IncidentRecord[]
  ): FloorKpis {
    const activeTargetLines = summaries.filter(s => s.target > 0 || s.output !== null);
    const targetedCount = activeTargetLines.length;
    const reportedSummaries = summaries.filter(s => s.output !== null);
    const reportedCount = reportedSummaries.length;

    const todayTargetPcs = summaries.reduce((s, i) => s + i.target, 0);
    const todayTargetDuePcs = summaries.reduce((s, i) => s + i.targetDue, 0);
    const todayOutputPcs = reportedSummaries.reduce((s, i) => s + (i.output ?? 0), 0);

    const operatorsCount = summaries.reduce((s, i) => s + i.operators, 0);
    const machinesCount = summaries.reduce((s, i) => s + i.machines, 0);

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

    const monthlyEfficiencyPct = todayEfficiencyPct;
    const monthlyEfficiencyDelta = 0;

    const oqlReported = summaries.filter(s => s.todayOqlPct !== null);
    const oqlSum = oqlReported.reduce((s, i) => s + (i.todayOqlPct ?? 0), 0);
    const todayOqlPct =
      oqlReported.length > 0 ? Number((oqlSum / oqlReported.length).toFixed(2)) : 0;

    const bsQaOqlPct = 2.4;
    const friPassRatePct = reportedCount > 0 ? 100 : 0;
    const reportingCoveragePct =
      targetedCount > 0 ? Math.round((reportedCount / targetedCount) * 100) : 0;

    const openIncidentsCount = incidents.filter(i => i.status !== 'RESOLVED').length;

    return {
      todayEfficiencyPct,
      monthlyEfficiencyPct,
      monthlyEfficiencyDelta,
      todayOutputPcs,
      todayTargetPcs,
      todayTargetDuePcs,
      achievementPct,
      operatorsCount,
      machinesCount,
      todayOqlPct,
      bsQaOqlPct,
      friPassRatePct,
      reportingCoveragePct,
      reportedLinesCount: reportedCount,
      targetedLinesCount: targetedCount,
      openIncidentsCount
    };
  }

  public static computeTopDefects(
    qualities: QualityEntryRecord[]
  ): Array<{ defect: string; count: number; dhu: number }> {
    const defectCounts: Record<string, number> = {};
    let totalChecked = 0;

    for (const q of qualities) {
      totalChecked += q.checkedGarments;
      for (const d of q.defects) {
        defectCounts[d.defectType] = (defectCounts[d.defectType] || 0) + d.count;
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