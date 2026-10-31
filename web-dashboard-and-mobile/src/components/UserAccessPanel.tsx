import React, { useMemo, useState } from 'react';
import { UserAccount, LineMasterItem } from '../types';
import { ENTRY_MODULES } from '../data/entryModules';
import { lineDisplayName, isFinishingUnit } from '../data/masterData';

interface Props {
  users: UserAccount[];
  lines: LineMasterItem[];
  onSaveAccess: (u: UserAccount, permissions: string[] | null, allowedLines: string[] | null) => Promise<boolean>;
  onBlock: (u: UserAccount, blocked: boolean) => Promise<boolean>;
  onDelete: (u: UserAccount) => Promise<boolean>;
}

/** Admin: for every registered person, which entry modules and lines they may use; block or delete. */
export const UserAccessPanel: React.FC<Props> = ({ users, lines, onSaveAccess, onBlock, onDelete }) => {
  const [openId, setOpenId] = useState<string | null>(null);
  const [mods, setMods] = useState<string[] | null>(null);
  const [lineSel, setLineSel] = useState<string[] | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [q, setQ] = useState('');

  const people = useMemo(
    () => users
      .filter(u => u.accountStatus === 'APPROVED' || u.accountStatus === 'SUSPENDED')
      .filter(u => !q.trim() || `${u.name} ${u.id} ${u.designation || ''}`.toLowerCase().includes(q.trim().toLowerCase()))
      .sort((a, b) => a.name.localeCompare(b.name)),
    [users, q]
  );

  const open = (u: UserAccount) => {
    if (openId === u.id) { setOpenId(null); return; }
    setOpenId(u.id);
    setMods(Array.isArray(u.permissions) ? [...u.permissions] : null);
    setLineSel(Array.isArray(u.allowedLines) && u.allowedLines.length > 0 ? [...u.allowedLines] : null);
    setMsg(null);
  };
  const floorLines = (u: UserAccount) =>
    lines.filter(l => l.isActive && !isFinishingUnit(l.id) && (!u.floor || u.floor === 'Factory' || u.floor === 'All Sewing' || l.floor === u.floor));

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="text-sm font-black text-slate-900 flex-1">Access control: who may enter what</div>
        <input value={q} onChange={e => setQ(e.target.value)} placeholder="Search name or ID" className="rounded-lg border border-slate-300 p-2 text-xs" />
      </div>
      <div className="text-[11px] text-slate-500">
        Everyone can see the dashboards. Here you choose, for each person, the entry modules they may write and the lines they may enter for. Rules in the database enforce it.
      </div>
      {msg && <div className="rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-800">{msg}</div>}
      <div className="divide-y divide-slate-100">
        {people.map(u => {
          const blocked = u.accountStatus === 'SUSPENDED';
          const owner = u.id === '1015198';
          return (
            <div key={u.id} className="py-2">
              <div className="flex flex-wrap items-center gap-2">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-black text-slate-900">{u.name} <span className="font-mono text-[11px] text-slate-500">{u.id}</span></div>
                  <div className="text-[11px] text-slate-500">
                    {u.designation || u.role} · {u.floor || 'All'} · {Array.isArray(u.permissions) ? `${u.permissions.length} module(s)` : 'role defaults'} · {Array.isArray(u.allowedLines) && u.allowedLines.length ? `${u.allowedLines.length} line(s)` : 'all lines'}
                    {blocked && <b className="ml-1 text-red-600">BLOCKED</b>}
                  </div>
                </div>
                {!owner && (
                  <>
                    <button type="button" onClick={() => open(u)} className="rounded-lg bg-slate-100 px-3 py-1.5 text-[11px] font-black text-slate-700">Access</button>
                    <button type="button" onClick={async () => { if (await onBlock(u, !blocked)) setMsg(`${u.name} ${blocked ? 'unblocked' : 'blocked'}.`); }}
                      className={`rounded-lg px-3 py-1.5 text-[11px] font-black ${blocked ? 'bg-emerald-600 text-white' : 'bg-amber-100 text-amber-900'}`}>
                      {blocked ? 'Unblock' : 'Block'}
                    </button>
                    <button type="button"
                      onClick={async () => {
                        if (!window.confirm(`Delete ${u.name} (${u.id})? They lose all access. To register again with this ID, their sign-in must also be removed in Firebase Authentication.`)) return;
                        if (await onDelete(u)) setMsg(`${u.name} deleted.`);
                      }}
                      className="rounded-lg bg-red-600 px-3 py-1.5 text-[11px] font-black text-white">Delete</button>
                  </>
                )}
              </div>
              {openId === u.id && (
                <div className="mt-2 space-y-3 rounded-lg bg-slate-50 p-3">
                  <div>
                    <label className="flex items-center gap-2 text-xs font-bold text-slate-800">
                      <input type="checkbox" checked={mods === null} onChange={e => setMods(e.target.checked ? null : [])} />
                      Use the role's default modules
                    </label>
                    {mods !== null && (
                      <div className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2">
                        {ENTRY_MODULES.map(m => (
                          <label key={m.id} className="flex items-center gap-2 text-xs text-slate-800">
                            <input type="checkbox" checked={mods.includes(m.id)} onChange={e => setMods(e.target.checked ? [...mods, m.id] : mods.filter(x => x !== m.id))} />
                            {m.label}
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                  <div>
                    <label className="flex items-center gap-2 text-xs font-bold text-slate-800">
                      <input type="checkbox" checked={lineSel === null} onChange={e => setLineSel(e.target.checked ? null : [])} />
                      All lines of their floor
                    </label>
                    {lineSel !== null && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {floorLines(u).map(l => {
                          const on = lineSel.includes(l.id);
                          return (
                            <button key={l.id} type="button" onClick={() => setLineSel(on ? lineSel.filter(x => x !== l.id) : [...lineSel, l.id])}
                              className={`rounded-md px-2 py-1 text-[11px] font-black ${on ? 'bg-[#1976D2] text-white' : 'bg-white border border-slate-300 text-slate-700'}`}>
                              {lineDisplayName(l.id)}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                  <button type="button"
                    onClick={async () => {
                      const lns = lineSel && lineSel.length > 0 ? lineSel : null;
                      if (await onSaveAccess(u, mods, lns)) { setMsg(`Access saved for ${u.name}.`); setOpenId(null); }
                    }}
                    className="w-full rounded-lg bg-[#0A2558] py-2 text-xs font-black text-white">Save access for {u.name}</button>
                </div>
              )}
            </div>
          );
        })}
        {people.length === 0 && <div className="py-3 text-xs text-slate-500">No approved people yet.</div>}
      </div>
    </div>
  );
};
