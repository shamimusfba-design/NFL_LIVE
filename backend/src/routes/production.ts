import { Router, Response } from 'express';
import { z } from 'zod';
import { AuthenticatedRequest, authenticate, requireRole } from '../middleware/auth';
import { sheetsService } from '../services/sheetsService';
import { CalculationEngine } from '../services/calculationEngine';
import { ProductionEntry } from '../types';

export const productionRouter = Router();

const ProductionSchema = z.object({
  submissionId: z.string().uuid(),
  lineId: z.string().min(1),
  date: z.string().min(1),
  hourSlot: z.string().min(1),
  styleNumber: z.string().min(1),
  outputQty: z.number().int().nonnegative(),
  remarks: z.string().optional()
});

productionRouter.post(
  '/',
  authenticate,
  requireRole(['ADMIN_IE', 'SUPERVISOR', 'PRODUCTION']),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const parsed = ProductionSchema.parse(req.body);

      const entry: ProductionEntry = {
        id: parsed.submissionId,
        submissionId: parsed.submissionId,
        lineId: CalculationEngine.normalizeLineId(parsed.lineId),
        date: parsed.date,
        hourSlot: parsed.hourSlot,
        styleNumber: parsed.styleNumber,
        outputQty: parsed.outputQty,
        remarks: parsed.remarks,
        timestamp: Date.now(),
        revision: 1,
        modifiedBy: req.user?.username || 'Production'
      };

      const result = await sheetsService.appendProduction(entry);

      return res.status(201).json({
        success: true,
        data: entry,
        isDuplicate: result.duplicate,
        message: result.duplicate
          ? 'Idempotent replay: already recorded.'
          : 'Hourly production recorded successfully.'
      });
    } catch (error: any) {
      if (error instanceof z.ZodError) {
        return res.status(400).json({ error: 'Validation failed', details: error.errors });
      }
      return res.status(500).json({ error: 'Failed to record production', details: error.message });
    }
  }
);

productionRouter.get('/recent', authenticate, (req: AuthenticatedRequest, res: Response) => {
  const lineId = req.query.lineId ? String(req.query.lineId) : undefined;
  let list = sheetsService.getInMemoryProduction();
  if (lineId) {
    const norm = CalculationEngine.normalizeLineId(lineId);
    list = list.filter(item => item.lineId === norm);
  }
  return res.json({ success: true, data: list.slice(0, 50) });
});
