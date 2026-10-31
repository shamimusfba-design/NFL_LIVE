import React, { useState } from 'react';
import { ProductionEntryRecord, LineTargetConfig } from '../types';
import { hourOptionsForLine, ordinalHr, finishingUnitId } from '../data/masterData';

type SewFloor = 'Sew 1' | 'Sew 2' | 'Sew 3';

interface Props {
  date: string;
  userName: string;
  targets: LineTargetConfig[];
  onSaveProduction: (entry: Omit<ProductionEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>) => void;
}

/** Iron and Packing output of a sewing floor, hour by hour, on the floor total (not line-wise). */
export const FinishingEntry: React.FC<Props> = ({ date, userName, targets, onSaveProduction }) => {
  const [floor, setFloor] = useState<SewFloor>('Sew 2');
  const [hourNo, setHourNo] = useState(1);
  const [iron, setIron] = useState('');
  const [pack, setPack] = useState('');
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const ironId = finishingUnitId('IRON', floor);
  const packId = finishingUnitId('PACK', floor);
  const hours = hourOptionsForLine(ironId);
  const hourlyTargetOf = (id: string) =>
    targets.find(t => t.lineId === id && t.date === date && !t.isDemo)?.hourlyTargetPcs ?? 0;

  const save = () => {
    setMsg(null);
    const entries: Array<[string, string, string]> = [[ironId, iron, 'Iron'], [packId, pack, 'Packing']];
    const filled = entries.filter(([, v]) => v.trim() !== '');
    if (filled.length === 0) { setMsg({ ok: false, text: 'Enter the Iron output, the Packing output, or both.' }); return; }
    if (filled.some(([, v]) => !(Number(v) >= 0))) { setMsg({ ok: false, text: 'Output must be 0 or more pieces.' }); return; }
    const slot = hours[hourNo - 1]?.key ?? hours[0].key;
    filled.forEach(([id, v]) => {
      const q = Math.round(Number(v));
      onSaveProduction({
        lineId: id,
        floor,
        date,
        hourSlot: slot,
        styleNumber: '',
        outputQty: q,
        isConfirmedZero: q === 0,
        remarks: '',
        reportedBy: userName,
        isDemo: false
      });
    });
    setMsg({ ok: true, text: `Saved ${filled.map(([, v, n]) => `${n} ${floor.replace('Sew ', '')}: ${Math.round(Number(v))} pcs`).join(' · ')} for the ${ordinalHr(hourNo)} hr.` });
    setIron(''); setPack('');
  };

  const box = 'w-full rounded-lg border border-slate-300 bg-white p-2.5 text-lg font-black text-slate-900';
  return (
    <div className="space-y-3">
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="text-sm font-black text-slate-900">Iron &amp; Packing output (floor total)</div>
        <div className="mt-1 text-[11px] text-slate-500">Hour by hour, for the whole floor. The hourly target comes from Target Setting.</div>
        <div className="mt-3 grid grid-cols-3 gap-1.5 sm:inline-grid">
          {(['Sew 1', 'Sew 2', 'Sew 3'] as SewFloor[]).map(f => (
            <button key={f} type="button" onClick={() => { setFloor(f); setMsg(null); }}
              className={`rounded-lg px-4 py-2 text-xs font-black ${floor === f ? 'bg-[#1976D2] text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}>
              {f}
            </button>
          ))}
        </div>
        <label className="mt-3 block">
          <span className="mb-1 block text-xs font-bold text-slate-600">Hour</span>
          <select value={hourNo} onChange={e => setHourNo(Number(e.target.value))} className="w-full rounded-lg border border-blue-400 bg-blue-50 p-2 text-sm font-black text-[#002D62]">
            {hours.map(h => <option key={h.no} value={h.no}>{h.label}</option>)}
          </select>
        </label>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {([['Iron', ironId, iron, setIron], ['Packing', packId, pack, setPack]] as Array<[string, string, string, (v: string) => void]>).map(([name, id, v, set]) => (
          <label key={id} className="rounded-xl border border-slate-200 bg-white p-4">
            <span className="flex items-center justify-between text-sm font-black text-slate-900">
              {name} {floor.replace('Sew ', '')}
              <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-[11px] font-bold text-emerald-800">
                Target {hourlyTargetOf(id) > 0 ? `${hourlyTargetOf(id)} /hr` : 'not set'}
              </span>
            </span>
            <span className="mt-2 mb-1 block text-xs font-bold text-slate-600">Output this hour (pcs)</span>
            <input type="number" inputMode="numeric" min={0} value={v} onChange={e => { set(e.target.value); setMsg(null); }} className={box} />
          </label>
        ))}
      </div>
      {msg && (
        <div className={`rounded-lg border px-3 py-2 text-xs font-bold ${msg.ok ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-red-300 bg-red-50 text-red-800'}`}>
          {msg.ok ? '✓ ' : '⚠️ '}{msg.text}
        </div>
      )}
      <button type="button" onClick={save} className="w-full rounded-xl bg-[#1976D2] py-3 text-sm font-black text-white hover:bg-blue-700">
        Save {floor} · {ordinalHr(hourNo)} hr
      </button>
    </div>
  );
};
