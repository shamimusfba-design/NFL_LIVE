import React, { useMemo, useState } from 'react';
import { LineMasterItem, LineTargetConfig, FloorManpowerRecord } from '../types';

type FloorName = 'Sew 1' | 'Sew 2' | 'Sew 3';

interface Row {
  style: string;
  smv: string;
  total: string;
  eff: string;
}

interface Props {
  lines: LineMasterItem[];
  targets: LineTargetConfig[];
  floorTotals: FloorManpowerRecord[];
  date: string;
  userName: string;
  onSaveSetup: (setup: LineTargetConfig) => void;
  onSaveFloorManpower: (record: FloorManpowerRecord) => void;
}

const num = (v: string) => (v.trim() === '' ? NaN : Number(v));
const pct = (a: number, b: number) => (b > 0 ? `${Math.round((a * 100) / b)}%` : '—');

/** Hourly target = total manpower x 60 x target % / SMV. 0 when something is missing. */
export function autoHourlyTarget(total: number, smv: number, effPct: number): number {
  if (!(total > 0) || !(smv > 0) || !(effPct > 0)) return 0;
  return Math.round((total * 60 * (effPct / 100)) / smv);
}

const box = 'w-full rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500';

export const BulkTargetSetup: React.FC<Props> = ({ lines, targets, floorTotals, date, userName, onSaveSetup, onSaveFloorManpower }) => {
  const [floor, setFloor] = useState<FloorName>('Sew 2');
  const [rows, setRows] = useState<Record<string, Row>>({});
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [fm, setFm] = useState<Record<string, string>>({});
  const [fmMessage, setFmMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const floorLines = useMemo(
    () => lines.filter(l => l.isActive && l.floor === floor && !/^(iron|packing)/i.test(l.id)),
    [lines, floor]
  );

  // What is already saved for today is shown. A line never set up starts blank with Target % 100.
  const rowFor = (id: string): Row => {
    if (rows[id]) return rows[id];
    const saved = targets.find(t => t.lineId === id && t.date === date && !t.isDemo);
    if (saved) {
      const total = (saved.operatorsCount || 0) + (saved.helpersCount || 0) + (saved.finishingStaffCount || 0);
      return {
        style: saved.styleNumber || '',
        smv: saved.smv ? String(saved.smv) : '',
        total: total > 0 ? String(total) : '',
        eff: String(saved.targetEfficiencyPct ?? 100)
      };
    }
    return { style: '', smv: '', total: '', eff: '100' };
  };

  const setField = (id: string, key: keyof Row, value: string) => {
    setMessage(null);
    setRows(prev => ({ ...prev, [id]: { ...rowFor(id), ...prev[id], [key]: value } }));
  };

  const problemWith = (id: string, r: Row): string | null => {
    if (!(num(r.smv) > 0)) return `Line ${id}: enter the SMV.`;
    if (!(num(r.total) > 0)) return `Line ${id}: enter the total manpower.`;
    if (!(num(r.eff) > 0)) return `Line ${id}: enter the target %.`;
    return null;
  };

  const toSetup = (id: string, r: Row): LineTargetConfig => {
    const total = num(r.total), smv = num(r.smv), eff = num(r.eff);
    const hourly = autoHourlyTarget(total, smv, eff);
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
      // The line's total manpower is kept in operatorsCount; helpers and finishing stay 0, so the total is the same everywhere.
      operatorsCount: total,
      machinesCount: total,
      helpersCount: 0,
      finishingStaffCount: 0,
      includeHelpersInEfficiency: true,
      workingHoursSoFar: 8,
      totalShiftHours: 8,
      isDemo: false
    };
  };

  const touched = (r: Row) => r.smv.trim() !== '' || r.total.trim() !== '';

  const saveAll = () => {
    const active = floorLines.map(l => ({ id: l.id, r: rowFor(l.id) })).filter(x => touched(x.r));
    const first = active.map(x => problemWith(x.id, x.r)).find(Boolean);
    if (first) { setMessage({ ok: false, text: `${first} Nothing was saved.` }); return; }
    if (active.length === 0) { setMessage({ ok: false, text: 'Enter SMV and total manpower for at least one line.' }); return; }
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

  // ---------- floor-wise totals (typed once for the floor, not line by line) ----------
  const savedFloor = floorTotals.find(f => f.floor === floor && f.date === date);
  const fmVal = (k: keyof FloorManpowerRecord & string, saved: number | undefined) =>
    fm[`${floor}|${k}`] !== undefined ? fm[`${floor}|${k}`] : saved !== undefined ? String(saved) : '';
  const setFmVal = (k: string, v: string) => { setFmMessage(null); setFm(prev => ({ ...prev, [`${floor}|${k}`]: v })); };
  const n = (k: keyof FloorManpowerRecord & string, saved: number | undefined) => Number(fmVal(k, saved)) || 0;
  const tm = n('totalMachines', savedFloor?.totalMachines);
  const mr = n('machinesRunning', savedFloor?.machinesRunning);
  const op = n('operatorsPresent', savedFloor?.operatorsPresent);
  const hp = n('helpersPresent', savedFloor?.helpersPresent);
  const hw = n('operatorsOnHandwork', savedFloor?.operatorsOnHandwork);
  const plannedManpower = floorLines.reduce((s, l) => {
    const r = rowFor(l.id);
    return s + (num(r.total) || 0);
  }, 0);

  const saveFloor = () => {
    const keys: Array<[keyof FloorManpowerRecord & string, number | undefined]> = [
      ['totalMachines', savedFloor?.totalMachines], ['machinesRunning', savedFloor?.machinesRunning],
      ['operatorsPresent', savedFloor?.operatorsPresent], ['helpersPresent', savedFloor?.helpersPresent],
      ['operatorsOnHandwork', savedFloor?.operatorsOnHandwork]
    ];
    if (keys.some(([k, s]) => fmVal(k, s).trim() === '')) { setFmMessage({ ok: false, text: 'Fill all five boxes (use 0 if none).' }); return; }
    if (mr > tm) { setFmMessage({ ok: false, text: 'Machines running cannot be more than total machines.' }); return; }
    if (hw > op) { setFmMessage({ ok: false, text: 'Operators on handwork cannot be more than operators present.' }); return; }
    onSaveFloorManpower({
      floor, date, totalMachines: tm, machinesRunning: mr, operatorsPresent: op, helpersPresent: hp,
      operatorsOnHandwork: hw, updatedBy: userName, updatedMillis: Date.now()
    });
    setFmMessage({ ok: true, text: `Saved ${floor} totals for today.` });
  };

  const fmFields: Array<[keyof FloorManpowerRecord & string, string, number | undefined]> = [
    ['totalMachines', 'Total machines', savedFloor?.totalMachines],
    ['machinesRunning', 'Total running machines today', savedFloor?.machinesRunning],
    ['operatorsPresent', 'Operators present today', savedFloor?.operatorsPresent],
    ['helpersPresent', 'Helpers present today', savedFloor?.helpersPresent],
    ['operatorsOnHandwork', 'Operators doing handwork (helper work)', savedFloor?.operatorsOnHandwork]
  ];

  return (
    <div className="space-y-3">
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="text-sm font-black text-slate-900">Target Setting: all lines of the floor at once</div>
        <div className="mt-1 text-[11px] text-slate-500">
          Hourly target = total manpower x 60 x target % / SMV. It is worked out for you. Daily = hourly x 8.
          A line without a target today is treated as not running that day.
        </div>
        <div className="mt-3 grid grid-cols-3 gap-1.5 sm:inline-grid">
          {(['Sew 1', 'Sew 2', 'Sew 3'] as FloorName[]).map(f => (
            <button
              key={f}
              type="button"
              onClick={() => { setFloor(f); setRows({}); setMessage(null); setFmMessage(null); }}
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
          const hourly = autoHourlyTarget(num(r.total) || 0, num(r.smv), num(r.eff));
          return (
            <div key={l.id} className="rounded-xl border border-slate-200 bg-white p-3">
              <div className="mb-2 flex items-center gap-2">
                <span className="rounded bg-[#0A2558] px-2.5 py-1 text-xs font-black text-white">{l.id}</span>
                <span className="ml-auto rounded-lg bg-emerald-100 px-2.5 py-1 text-xs font-black text-emerald-800">
                  {hourly > 0 ? `${hourly} /hr · ${hourly * 8}/day` : '— /hr'}
                </span>
                <button type="button" onClick={() => saveOne(l.id)} className="rounded-lg bg-slate-100 px-2.5 py-1 text-[11px] font-black text-slate-700 hover:bg-slate-200">Save</button>
              </div>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <label className="col-span-2 sm:col-span-1">
                  <span className="mb-0.5 block text-[10px] font-bold text-slate-500">Item / Style</span>
                  <input type="text" value={r.style} onChange={e => setField(l.id, 'style', e.target.value)} className={box} />
                </label>
                <label>
                  <span className="mb-0.5 block text-[10px] font-bold text-slate-500">SMV</span>
                  <input type="number" inputMode="decimal" step="any" min={0} value={r.smv} onChange={e => setField(l.id, 'smv', e.target.value)} className={box} />
                </label>
                <label>
                  <span className="mb-0.5 block text-[10px] font-bold text-slate-500">Total manpower</span>
                  <input type="number" inputMode="numeric" min={0} value={r.total} onChange={e => setField(l.id, 'total', e.target.value)} className={box} />
                </label>
                <label>
                  <span className="mb-0.5 block text-[10px] font-bold text-slate-500">Target %</span>
                  <input type="number" inputMode="decimal" step="any" min={0} value={r.eff} onChange={e => setField(l.id, 'eff', e.target.value)} className={box} />
                </label>
              </div>
            </div>
          );
        })}
      </div>

      <button type="button" onClick={saveAll} className="w-full rounded-xl bg-[#1976D2] py-3 text-sm font-black text-white hover:bg-blue-700">
        Save all {floorLines.length} lines for {floor}
      </button>

      {/* ---------- floor-wise total data ---------- */}
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="text-sm font-black text-slate-900">{floor}: floor-wise total data (today)</div>
        <div className="mt-1 text-[11px] text-slate-500">
          Typed once for the whole floor, not line by line. When helpers are short, operators do the helper work: put how many on handwork here.
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
          {fmFields.map(([k, label, saved]) => (
            <label key={k}>
              <span className="mb-0.5 block text-[10px] font-bold text-slate-500">{label}</span>
              <input type="number" inputMode="numeric" min={0} value={fmVal(k, saved)} onChange={e => setFmVal(k, e.target.value)} className={box} />
            </label>
          ))}
        </div>
        <div className="mt-3 space-y-1 rounded-lg bg-slate-50 p-3 text-xs text-slate-800">
          <div>Running machines: <b>{mr}</b> of {tm} ({pct(mr, tm)})</div>
          <div>Running machines per present operator: <b>{pct(mr, op)}</b></div>
          <div>Helpers as % of machines: <b>{pct(hp, tm)}</b></div>
          <div>Operators on handwork: <b>{hw}</b> ({pct(hw, op)} of operators present)</div>
          <div>Present operators + helpers: <b>{op + hp}</b> of {plannedManpower} total manpower set for the lines ({pct(op + hp, plannedManpower)})</div>
        </div>
        {fmMessage && (
          <div className={`mt-3 rounded-lg border px-3 py-2 text-xs font-bold ${fmMessage.ok ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-red-300 bg-red-50 text-red-800'}`}>
            {fmMessage.ok ? '✓ ' : '⚠️ '}{fmMessage.text}
          </div>
        )}
        <button type="button" onClick={saveFloor} className="mt-3 w-full rounded-xl bg-[#0A2558] py-2.5 text-sm font-black text-white hover:bg-blue-900">
          Save {floor} totals
        </button>
      </div>
    </div>
  );
};
