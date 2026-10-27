import React, { useMemo, useState } from 'react';
import { LineMasterItem, LineTargetConfig } from '../types';

type FloorName = 'Sew 1' | 'Sew 2' | 'Sew 3';

interface Row {
  style: string;
  smv: string;
  op: string;
  hp: string;
  fin: string;
  eff: string;
}

interface Props {
  lines: LineMasterItem[];
  targets: LineTargetConfig[];
  date: string;
  onSaveSetup: (setup: LineTargetConfig) => void;
}

const num = (v: string) => (v.trim() === '' ? NaN : Number(v));

/** Hourly target = (operators + helpers + finishing) x 60 x target % / SMV. 0 when something is missing. */
export function autoHourlyTarget(total: number, smv: number, effPct: number): number {
  if (!(total > 0) || !(smv > 0) || !(effPct > 0)) return 0;
  return Math.round((total * 60 * (effPct / 100)) / smv);
}

const FIELDS: Array<{ key: keyof Row; label: string; width: string; decimal?: boolean }> = [
  { key: 'style', label: 'Item / Style', width: 'col-span-2 sm:col-span-1' },
  { key: 'smv', label: 'SMV', width: '', decimal: true },
  { key: 'op', label: 'Operators', width: '' },
  { key: 'hp', label: 'Helpers', width: '' },
  { key: 'fin', label: 'Finishing', width: '' },
  { key: 'eff', label: 'Target %', width: '', decimal: true }
];

export const BulkTargetSetup: React.FC<Props> = ({ lines, targets, date, onSaveSetup }) => {
  const [floor, setFloor] = useState<FloorName>('Sew 2');
  const [rows, setRows] = useState<Record<string, Row>>({});
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const floorLines = useMemo(
    () => lines.filter(l => l.isActive && l.floor === floor && !/^(iron|packing)/i.test(l.id)),
    [lines, floor]
  );

  // Show what is already saved for today; a line never set up starts blank with Target % 100.
  const rowFor = (id: string): Row => {
    if (rows[id]) return rows[id];
    const saved = targets.find(t => t.lineId === id && t.date === date && !t.isDemo);
    if (saved) {
      return {
        style: saved.styleNumber || '',
        smv: saved.smv ? String(saved.smv) : '',
        op: String(saved.operatorsCount ?? ''),
        hp: String(saved.helpersCount ?? ''),
        fin: String(saved.finishingStaffCount ?? 0),
        eff: String(saved.targetEfficiencyPct ?? 100)
      };
    }
    return { style: '', smv: '', op: '', hp: '', fin: '', eff: '100' };
  };

  const setField = (id: string, key: keyof Row, value: string) => {
    setMessage(null);
    setRows(prev => ({ ...prev, [id]: { ...rowFor(id), ...prev[id], [key]: value } }));
  };

  const problemWith = (id: string, r: Row): string | null => {
    if (!(num(r.smv) > 0)) return `Line ${id}: enter the SMV.`;
    if (isNaN(num(r.op)) || isNaN(num(r.hp)) || isNaN(num(r.fin))) {
      return `Line ${id}: enter operators, helpers and finishing staff (use 0 if there are none).`;
    }
    if (!(num(r.op) + num(r.hp) + num(r.fin) > 0)) return `Line ${id}: total manpower must be more than 0.`;
    if (!(num(r.eff) > 0)) return `Line ${id}: enter the target %.`;
    return null;
  };

  const toSetup = (id: string, r: Row): LineTargetConfig => {
    const op = num(r.op), hp = num(r.hp), fin = num(r.fin), smv = num(r.smv), eff = num(r.eff);
    const hourly = autoHourlyTarget(op + hp + fin, smv, eff);
    return {
      lineId: id,
      floor,
      date,
      dailyTargetPcs: hourly * 8,
      hourlyTargetPcs: hourly,
      targetEfficiencyPct: eff,
      buyer: '',
      styleNumber: r.style.trim(),
      smv,
      operatorsCount: op,
      machinesCount: op,
      helpersCount: hp,
      finishingStaffCount: fin,
      includeHelpersInEfficiency: true,
      workingHoursSoFar: 8,
      totalShiftHours: 8,
      isDemo: false
    };
  };

  const touched = (r: Row) => r.smv.trim() !== '' || r.op.trim() !== '' || r.hp.trim() !== '' || r.fin.trim() !== '';

  const saveAll = () => {
    const active = floorLines.map(l => ({ id: l.id, r: rowFor(l.id) })).filter(x => touched(x.r));
    const first = active.map(x => problemWith(x.id, x.r)).find(Boolean);
    if (first) { setMessage({ ok: false, text: `${first} Nothing was saved.` }); return; }
    if (active.length === 0) { setMessage({ ok: false, text: 'Enter SMV and manpower for at least one line.' }); return; }
    active.forEach(x => onSaveSetup(toSetup(x.id, x.r)));
    setMessage({ ok: true, text: `Saved ${active.length} lines for ${floor}. Hourly targets were worked out automatically.` });
  };

  const saveOne = (id: string) => {
    const r = rowFor(id);
    const p = problemWith(id, r);
    if (p) { setMessage({ ok: false, text: p }); return; }
    onSaveSetup(toSetup(id, r));
    setMessage({ ok: true, text: `Saved line ${id}.` });
  };

  const box = 'w-full rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500';

  return (
    <div className="space-y-3">
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="text-sm font-black text-slate-900">Target Setting — all lines of the floor at once</div>
        <div className="mt-1 text-[11px] text-slate-500">
          Hourly target = (operators + helpers + finishing) x 60 x target % / SMV. It is worked out for you. Daily = hourly x 8.
        </div>
        <div className="mt-3 grid grid-cols-3 gap-1.5 sm:inline-grid">
          {(['Sew 1', 'Sew 2', 'Sew 3'] as FloorName[]).map(f => (
            <button
              key={f}
              type="button"
              onClick={() => { setFloor(f); setRows({}); setMessage(null); }}
              className={`rounded-lg px-4 py-2 text-xs font-black ${floor === f ? 'bg-[#1976D2] text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {message && (
        <div className={`rounded-lg border px-3 py-2 text-xs font-bold ${message.ok ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-red-300 bg-red-50 text-red-800'}`}>
          {message.ok ? '✓ ' : '⚠️ '}{message.text}
        </div>
      )}

      <div className="space-y-2">
        {floorLines.map(l => {
          const r = rowFor(l.id);
          const total = (num(r.op) || 0) + (num(r.hp) || 0) + (num(r.fin) || 0);
          const hourly = autoHourlyTarget(total, num(r.smv), num(r.eff));
          return (
            <div key={l.id} className="rounded-xl border border-slate-200 bg-white p-3">
              <div className="mb-2 flex items-center gap-2">
                <span className="rounded bg-[#0A2558] px-2.5 py-1 text-xs font-black text-white">{l.id}</span>
                <span className="text-[11px] font-semibold text-slate-500">Total manpower: <b className="text-slate-800">{total > 0 ? total : '—'}</b></span>
                <span className="ml-auto rounded-lg bg-emerald-100 px-2.5 py-1 text-xs font-black text-emerald-800">
                  {hourly > 0 ? `${hourly} /hr · ${hourly * 8}/day` : '— /hr'}
                </span>
                <button type="button" onClick={() => saveOne(l.id)} className="rounded-lg bg-slate-100 px-2.5 py-1 text-[11px] font-black text-slate-700 hover:bg-slate-200">Save</button>
              </div>
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
                {FIELDS.map(fl => (
                  <label key={fl.key} className={fl.width}>
                    <span className="mb-0.5 block text-[10px] font-bold text-slate-500">{fl.label}</span>
                    <input
                      type={fl.key === 'style' ? 'text' : 'number'}
                      inputMode={fl.key === 'style' ? 'text' : fl.decimal ? 'decimal' : 'numeric'}
                      step={fl.decimal ? 'any' : 1}
                      min={0}
                      value={r[fl.key]}
                      onChange={e => setField(l.id, fl.key, e.target.value)}
                      className={box}
                    />
                  </label>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      <button type="button" onClick={saveAll} className="w-full rounded-xl bg-[#1976D2] py-3 text-sm font-black text-white hover:bg-blue-700">
        Save all {floorLines.length} lines for {floor}
      </button>
    </div>
  );
};
