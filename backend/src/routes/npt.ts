import { Router, Response } from 'express';
import { z } from 'zod';
import { v4 as uuidv4 } from 'uuid';
import { AuthenticatedRequest, authenticate, requireRole } from '../middleware/auth';
import { sheetsService } from '../services/sheetsService';
import { CalculationEngine } from '../services/calculationEngine';
import { NptEvent } from '../types';

export const nptRouter = Router();

const StartNptSchema = z.object({
  lineId: z.string().min(1),
  styleNumber: z.string().min(1),
  reason: z.string().min(1),
  isFullLine: z.boolean(),
  affectedManpower: z.number().int().positive(),
  machineOrOpRef: z.string().optional(),
  remarks: z.string().optional()
});

const EndNptSchema = z.object({
  eventId: z.string().uuid(),
  manualEndTimeMillis: z.number().optional(),
  auditReason: z.string().optional()
});

nptRouter.post(
  '/start',
  authenticate,
  requireRole(['ADMIN_IE', 'SUPERVISOR']),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const parsed = StartNptSchema.parse(req.body);
      const line = CalculationEngine.normalizeLineId(parsed.lineId);

      // Overlap detection
      const active = sheetsService.getInMemoryNpt().filter(e => !e.isClosed && e.lineId === line);
      const overlapFlagged = active.length > 0;

      const event: NptEvent = {
        id: uuidv4(),
        submissionId: uuidv4(),
        lineId: line,
        styleNumber: parsed.styleNumber,
        reason: parsed.reason,
        isFullLine: parsed.isFullLine,
        affectedManpower: parsed.affectedManpower,
        machineOrOpRef: parsed.machineOrOpRef,
        remarks: parsed.remarks,
        startTimeMillis: Date.now(),
        endTimeMillis: null,
        isClosed: false,
        lostManMinutes: 0,
        overlapFlagged,
        createdBy: req.user?.username || 'Supervisor'
      };

      await sheetsService.recordNpt(event);

      return res.status(201).json({
        success: true,
        data: event,
        overlapWarning: overlapFlagged
          ? 'Multiple downtime events currently active on this line. Overlap flagged.'
          : undefined
      });
    } catch (error: any) {
      if (error instanceof z.ZodError) {
        return res.status(400).json({ error: 'Validation failed', details: error.errors });
      }
      return res.status(500).json({ error: 'Failed to start NPT', details: error.message });
    }
  }
);

nptRouter.post(
  '/end',
  authenticate,
  requireRole(['ADMIN_IE', 'SUPERVISOR']),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const parsed = EndNptSchema.parse(req.body);
      const event = sheetsService.getInMemoryNpt().find(e => e.id === parsed.eventId);

      if (!event) {
        return res.status(404).json({ error: 'NPT event not found.' });
      }

      if (event.isClosed) {
        return res.status(400).json({ error: 'NPT event is already closed.' });
      }

      const endTime = parsed.manualEndTimeMillis || Date.now();
      if (endTime < event.startTimeMillis) {
        return res.status(400).json({ error: 'End time cannot be earlier than start time.' });
      }

      const lostManMinutes = CalculationEngine.calculateLostManMinutes(
        event.startTimeMillis,
        endTime,
        event.affectedManpower
      );

      const updated: NptEvent = {
        ...event,
        endTimeMillis: endTime,
        isClosed: true,
        lostManMinutes
      };

      await sheetsService.recordNpt(updated);

      return res.json({
        success: true,
        data: updated,
        message: `NPT closed. Total lost man-minutes: ${lostManMinutes}`
      });
    } catch (error: any) {
      if (error instanceof z.ZodError) {
        return res.status(400).json({ error: 'Validation failed', details: error.errors });
      }
      return res.status(500).json({ error: 'Failed to close NPT', details: error.message });
    }
  }
);

nptRouter.get('/active', authenticate, (_req: AuthenticatedRequest, res: Response) => {
  const active = sheetsService.getInMemoryNpt().filter(e => !e.isClosed);
  return res.json({ success: true, data: active });
});

nptRouter.get('/history', authenticate, (_req: AuthenticatedRequest, res: Response) => {
  const closed = sheetsService.getInMemoryNpt().filter(e => e.isClosed);
  return res.json({ success: true, data: closed.slice(0, 100) });
});
