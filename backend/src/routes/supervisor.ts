import { Router, Response } from 'express';
import { z } from 'zod';
import { AuthenticatedRequest, authenticate, requireRole } from '../middleware/auth';
import { sheetsService } from '../services/sheetsService';
import { CalculationEngine } from '../services/calculationEngine';
import { SupervisorSetup } from '../types';

export const supervisorRouter = Router();

const SetupSchema = z.object({
  lineId: z.string().min(1),
  date: z.string().min(1),
  styleNumber: z.string().min(1),
  orderNumber: z.string().min(1),
  smv: z.number().positive(),
  targetHourlyPcs: z.number().int().positive(),
  targetEfficiencyPct: z.number().positive(),
  operatorsCount: z.number().int().positive(),
  helpersCount: z.number().int().nonnegative(),
  includeHelpersInEfficiency: z.boolean(),
  workingMinutesPerHour: z.number().int().positive().default(60)
});

supervisorRouter.post(
  '/setup',
  authenticate,
  requireRole(['ADMIN_IE', 'SUPERVISOR']),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const parsed = SetupSchema.parse(req.body);
      const line = CalculationEngine.normalizeLineId(parsed.lineId);

      const setup: SupervisorSetup = {
        lineId: line,
        date: parsed.date,
        styleNumber: parsed.styleNumber,
        orderNumber: parsed.orderNumber,
        smv: parsed.smv,
        targetHourlyPcs: parsed.targetHourlyPcs,
        targetEfficiencyPct: parsed.targetEfficiencyPct,
        operatorsCount: parsed.operatorsCount,
        helpersCount: parsed.helpersCount,
        includeHelpersInEfficiency: parsed.includeHelpersInEfficiency,
        workingMinutesPerHour: parsed.workingMinutesPerHour,
        snapshotTimestamp: Date.now()
      };

      await sheetsService.saveLineSetup(setup);

      return res.status(201).json({
        success: true,
        data: setup,
        message: `Line setup snapshot recorded for Line ${line}. Historical calculations remain untouched.`
      });
    } catch (error: any) {
      if (error instanceof z.ZodError) {
        return res.status(400).json({ error: 'Validation failed', details: error.errors });
      }
      return res.status(500).json({ error: 'Failed to record setup', details: error.message });
    }
  }
);

supervisorRouter.get(
  '/setup/:lineId',
  authenticate,
  (req: AuthenticatedRequest, res: Response) => {
    const line = CalculationEngine.normalizeLineId(req.params.lineId);
    const setup = sheetsService.getLineSetup(line);

    if (!setup) {
      // Default fallback
      return res.json({
        success: true,
        data: {
          lineId: line,
          date: '08 Oct 2026',
          styleNumber: 'NFL-204',
          orderNumber: 'PO-DEFAULT',
          smv: 14.5,
          targetHourlyPcs: 90,
          targetEfficiencyPct: 75.0,
          operatorsCount: 20,
          helpersCount: 4,
          includeHelpersInEfficiency: false,
          workingMinutesPerHour: 60,
          snapshotTimestamp: Date.now()
        }
      });
    }

    return res.json({ success: true, data: setup });
  }
);
