import { Router, Response } from 'express';
import { z } from 'zod';
import { AuthenticatedRequest, authenticate, requireRole } from '../middleware/auth';
import { sheetsService } from '../services/sheetsService';
import { CalculationEngine } from '../services/calculationEngine';
import { QualityEntry } from '../types';

export const qualityRouter = Router();

const QualitySchema = z
  .object({
    submissionId: z.string().uuid(),
    lineId: z.string().min(1),
    date: z.string().min(1),
    hourSlot: z.string().min(1),
    styleNumber: z.string().min(1),
    checkedGarments: z.number().int().positive(),
    defectiveGarments: z.number().int().nonnegative(),
    defects: z.array(
      z.object({
        defectType: z.string().min(1),
        count: z.number().int().positive()
      })
    ),
    remarks: z.string().optional()
  })
  .refine(data => data.defectiveGarments <= data.checkedGarments, {
    message: 'Defective garments cannot exceed checked garments count',
    path: ['defectiveGarments']
  });

qualityRouter.post(
  '/',
  authenticate,
  requireRole(['ADMIN_IE', 'SUPERVISOR', 'QUALITY']),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const parsed = QualitySchema.parse(req.body);
      const totalDefects = parsed.defects.reduce((acc, curr) => acc + curr.count, 0);

      const entry: QualityEntry = {
        id: parsed.submissionId,
        submissionId: parsed.submissionId,
        lineId: CalculationEngine.normalizeLineId(parsed.lineId),
        date: parsed.date,
        hourSlot: parsed.hourSlot,
        styleNumber: parsed.styleNumber,
        checkedGarments: parsed.checkedGarments,
        defectiveGarments: parsed.defectiveGarments,
        defects: parsed.defects,
        totalDefects,
        remarks: parsed.remarks,
        timestamp: Date.now(),
        revision: 1,
        modifiedBy: req.user?.username || 'Quality'
      };

      const result = await sheetsService.appendQuality(entry);

      const oql = (entry.defectiveGarments / entry.checkedGarments) * 100;
      const dhu = (entry.totalDefects / entry.checkedGarments) * 100;

      return res.status(201).json({
        success: true,
        data: entry,
        computedMetrics: {
          oqlPct: Number(oql.toFixed(2)),
          dhu: Number(dhu.toFixed(2))
        },
        isDuplicate: result.duplicate
      });
    } catch (error: any) {
      if (error instanceof z.ZodError) {
        return res.status(400).json({ error: 'Validation failed', details: error.errors });
      }
      return res.status(500).json({ error: 'Failed to record quality', details: error.message });
    }
  }
);

qualityRouter.get('/recent', authenticate, (req: AuthenticatedRequest, res: Response) => {
  const lineId = req.query.lineId ? String(req.query.lineId) : undefined;
  let list = sheetsService.getInMemoryQuality();
  if (lineId) {
    const norm = CalculationEngine.normalizeLineId(lineId);
    list = list.filter(item => item.lineId === norm);
  }
  return res.json({ success: true, data: list.slice(0, 50) });
});
