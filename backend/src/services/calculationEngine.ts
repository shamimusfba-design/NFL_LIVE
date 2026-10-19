import { ProductionEntry, QualityEntry, SupervisorSetup, NptEvent } from '../types';

export class CalculationEngine {
  /**
   * Resolves Line ID aliases:
   * J and J1 are confirmed to be the same line.
   * J2 is unconfirmed and kept distinct.
   */
  public static normalizeLineId(lineInput: string): string {
    const trimmed = lineInput.trim().toUpperCase();
    if (trimmed === 'J1') return 'J';
    return trimmed;
  }

  /**
   * Calculates actual efficiency using SMV terminology.
   * Earned minutes = sum(output quantity * applicable style SMV)
   * Available minutes = sum(included manpower * applicable working minutes)
   * Actual efficiency (%) = total earned minutes / total available minutes * 100
   *
   * Crucial rules enforced:
   * 1. Do NOT multiply by target efficiency.
   * 2. Do NOT count available manpower minutes multiple times across multiple styles on the same line in the same hour slot!
   * 3. Aggregate minutes first across lines/days/floors before dividing.
   */
  public static calculateEfficiency(
    productions: ProductionEntry[],
    setups: Map<string, SupervisorSetup>
  ): { earnedMinutes: number; availableMinutes: number; efficiencyPct: number } {
    let totalEarnedMinutes = 0;
    const coveredSlots = new Set<string>();

    for (const prod of productions) {
      const line = this.normalizeLineId(prod.lineId);
      const setup = setups.get(line);
      const smv = setup?.smv ?? 14.5; // Default standard SMV

      totalEarnedMinutes += prod.outputQty * smv;
      coveredSlots.add(`${line}|${prod.hourSlot}|${prod.date}`);
    }

    let totalAvailableMinutes = 0;
    for (const slotKey of coveredSlots) {
      const [line] = slotKey.split('|');
      const setup = setups.get(line);
      const includedManpower = setup
        ? setup.operatorsCount + (setup.includeHelpersInEfficiency ? setup.helpersCount : 0)
        : 20;
      const workingMinutes = setup?.workingMinutesPerHour ?? 60;

      totalAvailableMinutes += includedManpower * workingMinutes;
    }

    const efficiencyPct =
      totalAvailableMinutes > 0 ? (totalEarnedMinutes / totalAvailableMinutes) * 100 : 0;

    return {
      earnedMinutes: Number(totalEarnedMinutes.toFixed(2)),
      availableMinutes: Number(totalAvailableMinutes.toFixed(2)),
      efficiencyPct: Number(efficiencyPct.toFixed(2))
    };
  }

  /**
   * Quality metrics calculation:
   * OQL (%) = defective garments / checked garments * 100
   * DHU = total defects / checked garments * 100
   *
   * Rules:
   * 1. Checked > 0 and defective = 0 means exactly 0.00% OQL.
   * 2. One garment may have multiple defects. Defective garments and total defects are distinct!
   * 3. Aggregates counts first across records before dividing.
   */
  public static calculateQuality(
    inspections: QualityEntry[]
  ): { totalChecked: number; totalDefective: number; totalDefects: number; oqlPct: number; dhu: number } {
    const totalChecked = inspections.reduce((sum, item) => sum + item.checkedGarments, 0);
    const totalDefective = inspections.reduce((sum, item) => sum + item.defectiveGarments, 0);
    const totalDefects = inspections.reduce((sum, item) => sum + item.totalDefects, 0);

    const oqlPct = totalChecked > 0 ? (totalDefective / totalChecked) * 100 : 0;
    const dhu = totalChecked > 0 ? (totalDefects / totalChecked) * 100 : 0;

    return {
      totalChecked,
      totalDefective,
      totalDefects,
      oqlPct: Number(oqlPct.toFixed(2)),
      dhu: Number(dhu.toFixed(2))
    };
  }

  /**
   * NPT lost man-minutes calculation:
   * Lost man-minutes = working-time duration (mins) * affected manpower
   * Nonworking breaks (e.g. lunch hour) are excluded.
   * Running losses remain provisional until closed.
   */
  public static calculateLostManMinutes(
    startMillis: number,
    endMillis: number,
    affectedManpower: number
  ): number {
    if (endMillis <= startMillis) return 0;
    const durationMinutes = (endMillis - startMillis) / (1000 * 60);
    return Number((durationMinutes * affectedManpower).toFixed(2));
  }
}
