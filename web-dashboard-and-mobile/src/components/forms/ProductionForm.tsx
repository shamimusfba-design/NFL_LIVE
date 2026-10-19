import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { databaseService } from '../../services/databaseService';
import { ProductionLine } from '../../types/nfl-types';
import { CheckCircle, AlertCircle, Loader2 } from 'lucide-react';

// Validation Schema
const productionSchema = z.object({
  lineId: z.string().min(1, "Please select a production line"),
  floorId: z.string().min(1, "Please select a floor"),
  qty: z.number().min(1, "Quantity must be at least 1"),
  target: z.number().min(1, "Target must be at least 1"),
  date: z.string().min(1, "Date is required"),
});

type ProductionFormData = z.infer<typeof productionSchema>;

export const ProductionForm = ({ lines, floorId }: { lines: ProductionLine[], floorId: string }) => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [previewEff, setPreviewEff] = useState<number | null>(null);

  const { register, handleSubmit, watch, formState: { errors } } = useForm<ProductionFormData>({
    resolver: zodResolver(productionSchema),
    defaultValues: {
      date: new Date().toISOString().split('T')[0] // Default to today
    }
  });

  // Watch qty and target to show real-time efficiency preview
  const qty = watch('qty');
  const target = watch('target');

  useEffect(() => {
    if (qty && target && target > 0) {
      setPreviewEff(Math.round((qty / target) * 100));
    } else {
      setPreviewEff(null);
    }
  }, [qty, target]);

  const onSubmit = async (data: ProductionFormData) => {
    setIsSubmitting(true);
    try {
      await databaseService.saveProduction({
        lineId: data.lineId,
        floorId: data.floorId,
        qty: data.qty,
        target: data.target,
        date: data.date,
        efficiency: previewEff || 0,
        enteredBy: "auth_user_uid", // This should come from your AuthContext
      });
      setSuccess(true);
      setTimeout(() => setSuccess(false), 5000);
    } catch (error) {
      console.error("Submission failed", error);
      alert("Failed to save production data.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white rounded-2xl shadow-lg border border-slate-200 overflow-hidden">
      <div className="bg-blue-600 p-4">
        <h2 className="text-white text-lg font-bold flex items-center gap-2">
          <CheckCircle size={20} /> Production Entry
        </h2>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-5">
        {/* Line Selection */}
        <div>
          <label className="block text-sm font-semibold text-slate-700 mb-1">Production Line</label>
          <select 
            {...register('lineId')}
            className="w-full p-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-blue-500 outline-none bg-slate-50"
          >
            <option value="">Select Line...</option>
            {lines.map(line => (
              <option key={line.id} value={line.id}>{line.name}</option>
            ))}
          </select>
          {errors.lineId && <p className="text-rose-500 text-xs mt-1">{errors.lineId.message}</p>}
        </div>

        {/* Hidden Floor ID (passed from context) */}
        <input type="hidden" {...register('floorId')} value={floorId} />

        <div className="grid grid-cols-2 gap-4">
          {/* Quantity Input */}
          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-1">Actual Qty</label>
            <input 
              type="number" 
              {...register('qty', { valueAsNumber: true })}
              placeholder="0"
              className="w-full p-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-blue-500 outline-none bg-slate-50"
            />
            {errors.qty && <p className="text-rose-500 text-xs mt-1">{errors.qty.message}</p>}
          </div>

          {/* Target Input */}
          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-1">Target</label>
            <input 
              type="number" 
              {...register('target', { valueAsNumber: true })}
              placeholder="0"
              className="w-full p-3 rounded-xl border border-slate-300 focus:ring-2 focus:ring-blue-500 outline-none bg-slate-50"
            />
            {errors.target && <p className="text-rose-500 text-xs mt-1">{errors.target.message}</p>}
          </div>
        </div>

        {/* Efficiency Preview */}
        {previewEff !== null && (
          <div className={`p-3 rounded-xl text-center font-bold text-lg ${previewEff >= 90 ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
            Preview Efficiency: {previewEff}%
          </div>
        )}

        {/* Submit Button */}
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full py-4 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold text-lg shadow-md transition-all disabled:opacity-50 flex justify-center items-center gap-2"
        >
          {isSubmitting ? <Loader2 className="animate-spin" /> : "Submit Production"}
        </button>

        {success && (
          <div className="flex items-center justify-center gap-2 text-emerald-600 font-semibold animate-bounce">
            <CheckCircle size={20} /> Data Saved Successfully!
          </div>
        )}
      </form>
    </div>
  );
};