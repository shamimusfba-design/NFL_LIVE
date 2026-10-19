import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { databaseService } from '../../services/databaseService';
import { ProductionLine } from '../../types/nfl-types';
import { AlertCircle, Loader2, CheckCircle } from 'lucide-react';

const qualitySchema = z.object({
  lineId: z.string().min(1, "Select a line"),
  floorId: z.string().min(1, "Select a floor"),
  defectQty: z.number().min(0, "Cannot be negative"),
  totalQty: z.number().min(1, "Total Qty must be > 0"),
  date: z.string().min(1, "Date required"),
});

type QualityFormData = z.infer<typeof qualitySchema>;

export const QualityForm = ({ lines, floorId }: { lines: ProductionLine[], floorId: string }) => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const { register, handleSubmit, watch, formState: { errors } } = useForm<QualityFormData>({
    resolver: zodResolver(qualitySchema),
    defaultValues: { date: new Date().toISOString().split('T')[0] }
  });

  const defectQty = watch('defectQty');
  const totalQty = watch('totalQty');
  const dhu = (defectQty && totalQty) ? ((defectQty / totalQty) * 100).toFixed(2) : "0.00";

  const onSubmit = async (data: QualityFormData) => {
    setIsSubmitting(true);
    try {
      await databaseService.saveQuality({
        lineId: data.lineId,
        floorId: data.floorId,
        defectQty: data.defectQty,
        dhu: parseFloat(dhu),
        date: data.date,
        enteredBy: "auth_user_uid", // IMPORTANT: Ensure this is a real UID
      });
      setSuccess(true);
      setTimeout(() => setSuccess(false), 5000);
    } catch (error) {
      alert("Access Denied: Only Admins can enter Quality data.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white rounded-2xl shadow-lg border border-rose-200 overflow-hidden">
      <div className="bg-rose-600 p-4">
        <h2 className="text-white text-lg font-bold flex items-center gap-2">
          <AlertCircle size={20} /> Quality Entry (Admin Only)
        </h2>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-5">
        <div>
          <label className="block text-sm font-semibold text-slate-700 mb-1">Production Line</label>
          <select {...register('lineId')} className="w-full p-3 rounded-xl border border-slate-300 bg-slate-50">
            <option value="">Select Line...</option>
            {lines.map(line => <option key={line.id} value={line.id}>{line.name}</option>)}
          </select>
        </div>

        <input type="hidden" {...register('floorId')} value={floorId} />

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-1">Defect Qty</label>
            <input type="number" {...register('defectQty', { valueAsNumber: true })} className="w-full p-3 rounded-xl border border-slate-300 bg-slate-50" />
          </div>
          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-1">Total Qty</label>
            <input type="number" {...register('totalQty', { valueAsNumber: true })} className="w-full p-3 rounded-xl border border-slate-300 bg-slate-50" />
          </div>
        </div>

        {/* DHU Preview */}
        <div className="p-3 rounded-xl text-center font-bold text-lg bg-rose-50 text-rose-700">
          Calculated DHU: {dhu}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full py-4 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-bold text-lg shadow-md transition-all disabled:opacity-50 flex justify-center items-center gap-2"
        >
          {isSubmitting ? <Loader2 className="animate-spin" /> : "Submit Quality Data"}
        </button>

        {success && (
          <div className="flex items-center justify-center gap-2 text-emerald-600 font-semibold animate-bounce">
            <CheckCircle size={20} /> Quality Record Saved!
          </div>
        )}
      </form>
    </div>
  );
};