import React, { useState } from 'react';
import { LineMasterItem, UserAccount } from '../types';

interface AdminViewProps {
  lines: LineMasterItem[];
  users: UserAccount[];
  demoStats: {
    targetsCount: number;
    productionsCount: number;
    qualitiesCount: number;
    incidentsCount: number;
  };
  onLoadDemoData: () => void;
  onRemoveAllDemoData: () => void;
  onToggleLineActive: (lineId: string) => void;
  onClose: () => void;
}

export const AdminView: React.FC<AdminViewProps> = ({
  lines,
  users,
  demoStats,
  onLoadDemoData,
  onRemoveAllDemoData,
  onToggleLineActive,
  onClose
}) => {
  const [adminTab, setAdminTab] = useState<'DEMO' | 'LINES' | 'USERS'>('DEMO');
  const [confirmRemoval, setConfirmRemoval] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const totalDemoRecords =
    demoStats.targetsCount +
    demoStats.productionsCount +
    demoStats.qualitiesCount +
    demoStats.incidentsCount;

  const handleLoadDemo = () => {
    onLoadDemoData();
    setActionMessage('✓ Synthetic demo dataset loaded idempotently across all three floors!');
    setTimeout(() => setActionMessage(null), 3000);
  };

  const handleRemoveDemo = () => {
    onRemoveAllDemoData();
    setConfirmRemoval(false);
    setActionMessage('✓ All demo records purged! Real records and line master safely preserved.');
    setTimeout(() => setActionMessage(null), 3000);
  };

  return (
    <div className="fixed inset-0 bg-slate-900/70 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-black tracking-wide">
              Administrator Master Console & Demo Data
            </h2>
            <div className="text-xs text-blue-200">
              Master Data • Floor Census • Demo Lifecycle Management
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white bg-blue-900/60 hover:bg-blue-800 p-1.5 rounded-lg text-lg font-bold"
          >
            ✕
          </button>
        </div>

        {/* Tab switcher */}
        <div className="flex border-b border-slate-200 bg-slate-50 px-5 pt-2 gap-2 text-xs font-bold">
          <button
            onClick={() => setAdminTab('DEMO')}
            className={`pb-2.5 px-4 border-b-2 transition ${
              adminTab === 'DEMO'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            Demo Data Lifecycle
          </button>
          <button
            onClick={() => setAdminTab('LINES')}
            className={`pb-2.5 px-4 border-b-2 transition ${
              adminTab === 'LINES'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            Line Master ({lines.length} Lines)
          </button>
          <button
            onClick={() => setAdminTab('USERS')}
            className={`pb-2.5 px-4 border-b-2 transition ${
              adminTab === 'USERS'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            Users & Roles ({users.length})
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4 bg-[#F8FAFC]">
          {actionMessage && (
            <div className="p-3 bg-blue-50 border border-blue-200 text-blue-900 text-xs font-bold rounded-xl shadow-sm">
              {actionMessage}
            </div>
          )}

          {/* TAB 1: DEMO DATA */}
          {adminTab === 'DEMO' && (
            <div className="space-y-4">
              <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm space-y-3">
                <h3 className="text-sm font-black text-slate-900">
                  Demo Data Status & Inventory
                </h3>
                <p className="text-xs text-slate-600 leading-relaxed">
                  Demo data provides realistic test figures matching the mobile and TV visual references across Sew 1, Sew 2, and Sew 3. Demo records are isolated with an internal demo identifier.
                </p>

                <div className="grid grid-cols-4 gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200 text-center text-xs">
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Demo Targets</div>
                    <div className="text-lg font-black text-slate-900">{demoStats.targetsCount}</div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Demo Output Rows</div>
                    <div className="text-lg font-black text-slate-900">{demoStats.productionsCount}</div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Demo Quality Rows</div>
                    <div className="text-lg font-black text-slate-900">{demoStats.qualitiesCount}</div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Demo Incidents</div>
                    <div className="text-lg font-black text-red-600">{demoStats.incidentsCount}</div>
                  </div>
                </div>

                <div className="text-xs font-bold text-slate-700">
                  Total Active Demo Records: <span className="text-blue-700 font-black">{totalDemoRecords}</span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm space-y-4">
                <h3 className="text-sm font-black text-slate-900">Lifecycle Operations</h3>

                <div className="flex items-center justify-between p-3 border rounded-xl bg-slate-50">
                  <div>
                    <div className="text-xs font-bold text-slate-900">Load / Refresh Demo Data</div>
                    <div className="text-[11px] text-slate-500">
                      Idempotently seeds all 3 floors with realistic targets, outputs, and live incidents.
                    </div>
                  </div>
                  <button
                    onClick={handleLoadDemo}
                    className="bg-[#1976D2] hover:bg-blue-700 text-white font-black text-xs px-4 py-2 rounded-xl shadow transition"
                  >
                    Load Demo Data
                  </button>
                </div>

                <div className="flex items-center justify-between p-3 border rounded-xl bg-red-50/40 border-red-200">
                  <div>
                    <div className="text-xs font-bold text-red-950">Remove All Demo Data</div>
                    <div className="text-[11px] text-red-700">
                      Purges all demo rows. Preserves real entered data, real users, settings, and line master.
                    </div>
                  </div>
                  {!confirmRemoval ? (
                    <button
                      onClick={() => setConfirmRemoval(true)}
                      className="bg-red-600 hover:bg-red-700 text-white font-black text-xs px-4 py-2 rounded-xl shadow transition"
                    >
                      Remove All Demo Data
                    </button>
                  ) : (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setConfirmRemoval(false)}
                        className="text-xs font-bold px-3 py-1.5 rounded-lg border bg-white text-slate-600"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={handleRemoveDemo}
                        className="bg-red-700 hover:bg-red-800 text-white font-black text-xs px-4 py-1.5 rounded-lg shadow"
                      >
                        Confirm Delete ({totalDemoRecords} rows)
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: LINE MASTER */}
          {adminTab === 'LINES' && (
            <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-3 shadow-sm">
              <div className="flex justify-between items-center pb-2 border-b">
                <h3 className="text-xs font-bold text-slate-800">
                  All 60 Configured Lines Across Sewing Floors
                </h3>
                <span className="text-[11px] text-slate-500">
                  Sew 1 (22) • Sew 2 (14) • Sew 3 (24)
                </span>
              </div>

              <div className="max-h-96 overflow-y-auto space-y-2">
                {(['Sew 1', 'Sew 2', 'Sew 3'] as const).map(floor => (
                  <div key={floor} className="space-y-1">
                    <div className="text-xs font-black text-blue-900 bg-blue-50 px-2 py-1 rounded">
                      {floor}
                    </div>
                    <div className="grid grid-cols-4 gap-2 p-1">
                      {lines
                        .filter(l => l.floor === floor)
                        .map(l => (
                          <div
                            key={l.id}
                            className={`flex items-center justify-between p-2 rounded-lg border text-xs ${
                              l.isActive ? 'bg-white border-slate-200' : 'bg-slate-100 border-slate-300 opacity-60'
                            }`}
                          >
                            <span className="font-bold text-slate-900">{l.id}</span>
                            <button
                              onClick={() => onToggleLineActive(l.id)}
                              className={`text-[10px] font-extrabold px-1.5 py-0.5 rounded ${
                                l.isActive ? 'bg-green-100 text-green-800' : 'bg-slate-300 text-slate-600'
                              }`}
                            >
                              {l.isActive ? 'Active' : 'Inactive'}
                            </button>
                          </div>
                        ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 3: USERS & ROLES */}
          {adminTab === 'USERS' && (
            <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
              <div className="p-3 bg-slate-100 text-xs font-bold text-slate-800 border-b">
                10 Configured User Roles & Floor Assignments
              </div>
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-50 text-slate-600 font-extrabold border-b">
                  <tr>
                    <th className="py-2 px-3">Name</th>
                    <th className="py-2 px-3">Username</th>
                    <th className="py-2 px-3">Role</th>
                    <th className="py-2 px-3">Floor Scope</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.map(u => (
                    <tr key={u.id} className="hover:bg-slate-50">
                      <td className="py-2 px-3 font-bold text-slate-900">{u.name}</td>
                      <td className="py-2 px-3 font-mono text-slate-600">{u.username}</td>
                      <td className="py-2 px-3 font-bold text-blue-800">{u.role}</td>
                      <td className="py-2 px-3 text-slate-600">{u.floor || 'All'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
