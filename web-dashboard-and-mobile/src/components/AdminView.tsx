import { UserAccessPanel } from './UserAccessPanel';
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
  liveStats?: {
    targetsCount: number;
    productionsCount: number;
    qualitiesCount: number;
    incidentsCount: number;
  };
  onLoadDemoData: () => void;
  onRemoveAllDemoData: () => void;
  onFactoryResetAll?: () => Promise<number>;
  onToggleLineActive: (lineId: string) => void;
  onApproveRegistration?: (userId: string) => Promise<void>;
  onRejectRegistration?: (userId: string) => Promise<void>;
  onApproveDeviceOrTv?: (userId: string, deviceId: string, deviceName: string) => Promise<void>;
  initialTab?: 'DEMO' | 'LINES' | 'USERS';
  onSaveAccess?: (u: UserAccount, permissions: string[] | null, allowedLines: string[] | null) => Promise<boolean>;
  onBlockUser?: (u: UserAccount, blocked: boolean) => Promise<boolean>;
  onDeleteUser?: (u: UserAccount) => Promise<boolean>;
  onClose: () => void;
}

export const AdminView: React.FC<AdminViewProps> = ({
  lines,
  users,
  demoStats,
  liveStats,
  onLoadDemoData,
  onRemoveAllDemoData,
  onFactoryResetAll,
  onToggleLineActive,
  onApproveRegistration,
  onRejectRegistration,
  onApproveDeviceOrTv,
  initialTab,
  onSaveAccess,
  onBlockUser,
  onDeleteUser,
  onClose
}) => {
  const pendingRegistrations = users.filter(u => u.accountStatus === 'PENDING_APPROVAL');
  const pendingDeviceOrTvUsers = users.filter(
    u =>
      u.rejectionReason?.startsWith('TV_PENDING:') ||
      u.rejectionReason?.startsWith('DEV_PENDING:')
  );
  const totalPendingRequests = pendingRegistrations.length + pendingDeviceOrTvUsers.length;

  const [adminTab, setAdminTab] = useState<'DEMO' | 'LINES' | 'USERS'>(
    initialTab ?? (totalPendingRequests > 0 ? 'USERS' : 'DEMO')
  );
  const [confirmRemoval, setConfirmRemoval] = useState(false);
  const [confirmFactoryReset, setConfirmFactoryReset] = useState(false);
  const [isResettingCloud, setIsResettingCloud] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const totalDemoRecords =
    demoStats.targetsCount +
    demoStats.productionsCount +
    demoStats.qualitiesCount +
    demoStats.incidentsCount;

  const totalLiveRecords = liveStats
    ? liveStats.targetsCount +
      liveStats.productionsCount +
      liveStats.qualitiesCount +
      liveStats.incidentsCount
    : 0;

  const handleLoadDemo = () => {
    onLoadDemoData();
    setActionMessage('✓ Synthetic demo dataset loaded idempotently across all floors!');
    setTimeout(() => setActionMessage(null), 4000);
  };

  const handleRemoveDemo = () => {
    onRemoveAllDemoData();
    setConfirmRemoval(false);
    setActionMessage('✓ All demo records purged and saved! Real records and line master safely preserved.');
    setTimeout(() => setActionMessage(null), 4000);
  };

  const handleFactoryReset = async () => {
    if (!onFactoryResetAll) return;
    setIsResettingCloud(true);
    try {
      const deletedCount = await onFactoryResetAll();
      if (deletedCount < 0) {
        setConfirmFactoryReset(false);
        setActionMessage('Factory Reset was cancelled. Nothing was deleted.');
        return;
      }
      setConfirmFactoryReset(false);
      setActionMessage(
        `✓ Factory Reset Complete! Wiped all local records and ${deletedCount} Cloud Firestore documents. User accounts & Line Master preserved.`
      );
    } finally {
      setIsResettingCloud(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-slate-900/70 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-black tracking-wide">
              Administrator Master Console & Data Reset
            </h2>
            <div className="text-xs text-blue-200">
              Master Data • Cloud Firestore Reset • Demo Lifecycle Management
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
            Data Reset & Demo Lifecycle
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
            className={`pb-2.5 px-4 border-b-2 transition flex items-center gap-1.5 ${
              adminTab === 'USERS'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500'
            }`}
          >
            <span>Users, TV &amp; Device Approvals ({users.length})</span>
            {totalPendingRequests > 0 && (
              <span className="bg-amber-500 text-slate-950 font-black text-[10px] px-1.5 py-0.5 rounded-full">
                {totalPendingRequests} Pending
              </span>
            )}
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4 bg-[#F8FAFC]">
          {actionMessage && (
            <div className="p-3 bg-emerald-50 border border-emerald-300 text-emerald-950 text-xs font-bold rounded-xl shadow-sm">
              {actionMessage}
            </div>
          )}

          {/* TAB 1: DEMO & FACTORY DATA RESET */}
          {adminTab === 'DEMO' && (
            <div className="space-y-4">
              <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm space-y-3">
                <h3 className="text-sm font-black text-slate-900">
                  Live Cloud & Demo Record Inventory
                </h3>
                <p className="text-xs text-slate-600 leading-relaxed">
                  Manage sample/demo records or perform a full <b>Factory Reset</b> across both this PC and Cloud Firestore (Phones & Smart TVs) when starting clean production.
                </p>

                <div className="grid grid-cols-4 gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200 text-center text-xs">
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Targets (Demo / Live)</div>
                    <div className="text-lg font-black text-slate-900">
                      {demoStats.targetsCount} / {liveStats?.targetsCount ?? 0}
                    </div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Output (Demo / Live)</div>
                    <div className="text-lg font-black text-slate-900">
                      {demoStats.productionsCount} / {liveStats?.productionsCount ?? 0}
                    </div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Quality (Demo / Live)</div>
                    <div className="text-lg font-black text-slate-900">
                      {demoStats.qualitiesCount} / {liveStats?.qualitiesCount ?? 0}
                    </div>
                  </div>
                  <div>
                    <div className="text-[10px] text-slate-500 font-bold">Incidents (Demo / Live)</div>
                    <div className="text-lg font-black text-red-600">
                      {demoStats.incidentsCount} / {liveStats?.incidentsCount ?? 0}
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs font-bold text-slate-700 pt-1">
                  <span>
                    Active Demo Records: <b className="text-amber-700">{totalDemoRecords}</b>
                  </span>
                  <span>
                    Live / Cloud Records: <b className="text-blue-700">{totalLiveRecords}</b>
                  </span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="bg-white rounded-xl p-5 border border-slate-200 shadow-sm space-y-4">
                <h3 className="text-sm font-black text-slate-900">Data Reset & Lifecycle Operations</h3>

                <div className="flex items-center justify-between p-3 border rounded-xl bg-slate-50">
                  <div>
                    <div className="text-xs font-bold text-slate-900">Load / Refresh Demo Data</div>
                    <div className="text-[11px] text-slate-500">
                      Idempotently seeds all floors with realistic targets, outputs, and live incidents.
                    </div>
                  </div>
                  <button
                    onClick={handleLoadDemo}
                    className="bg-[#1976D2] hover:bg-blue-700 text-white font-black text-xs px-4 py-2 rounded-xl shadow transition"
                  >
                    Load Demo Data
                  </button>
                </div>

                <div className="flex items-center justify-between p-3 border rounded-xl bg-amber-50/60 border-amber-200">
                  <div>
                    <div className="text-xs font-bold text-amber-950">Clear Demo Data Only</div>
                    <div className="text-[11px] text-amber-800">
                      Removes only sample/demo rows ({totalDemoRecords}) and disables auto-seed numbers. Preserves real cloud entries.
                    </div>
                  </div>
                  {!confirmRemoval ? (
                    <button
                      onClick={() => setConfirmRemoval(true)}
                      className="bg-amber-600 hover:bg-amber-700 text-white font-black text-xs px-4 py-2 rounded-xl shadow transition"
                    >
                      Clear Demo Data
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
                        className="bg-amber-700 hover:bg-amber-800 text-white font-black text-xs px-4 py-1.5 rounded-lg shadow"
                      >
                        Confirm Clear Demo
                      </button>
                    </div>
                  )}
                </div>

                <div className="flex items-center justify-between p-3.5 border-2 rounded-xl bg-red-50/70 border-red-300">
                  <div className="pr-3">
                    <div className="text-xs font-black text-red-950">
                      Factory Reset All Operational Records (Local + Cloud Firestore)
                    </div>
                    <div className="text-[11px] text-red-800 mt-0.5">
                      Permanently wipes ALL Production, Quality, NPT Incidents, and Daily Line Setups ({totalLiveRecords} live + {totalDemoRecords} demo) from both this PC and Cloud Firestore so Phones, TVs, and PCs start at 0. User accounts & Line Master are preserved.
                    </div>
                  </div>
                  {!confirmFactoryReset ? (
                    <button
                      onClick={() => setConfirmFactoryReset(true)}
                      className="bg-red-700 hover:bg-red-800 text-white font-black text-xs px-4 py-2.5 rounded-xl shadow transition shrink-0"
                    >
                      Factory Reset All
                    </button>
                  ) : (
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => setConfirmFactoryReset(false)}
                        disabled={isResettingCloud}
                        className="text-xs font-bold px-3 py-1.5 rounded-lg border bg-white text-slate-600"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={handleFactoryReset}
                        disabled={isResettingCloud}
                        className="bg-red-800 hover:bg-red-950 text-white font-black text-xs px-4 py-2 rounded-lg shadow"
                      >
                        {isResettingCloud ? 'Purging Cloud...' : 'Confirm Full Reset'}
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
                  All {lines.length} Configured Lines Across Sewing Floors
                </h3>
                <span className="text-[11px] text-slate-500">
                  Sew 1 (22) • Sew 2 (17) • Sew 3 (23)
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

          {/* TAB 3: USERS, PENDING REGISTRATIONS & TV/DEVICE REQUESTS */}
          {adminTab === 'USERS' && (
            <div className="space-y-4">
              {onSaveAccess && onBlockUser && onDeleteUser && (
                <UserAccessPanel users={users} lines={lines} onSaveAccess={onSaveAccess} onBlock={onBlockUser} onDelete={onDeleteUser} />
              )}
              {pendingRegistrations.length > 0 && (
                <div className="bg-amber-50 border border-amber-300 rounded-xl p-4 space-y-2.5 shadow-sm">
                  <div className="text-xs font-black text-amber-950">
                    ⏳ Pending Employee Registration Requests ({pendingRegistrations.length})
                  </div>
                  {pendingRegistrations.map(u => (
                    <div
                      key={u.id}
                      className="bg-white border border-amber-200 rounded-lg p-3 flex flex-wrap items-center justify-between gap-2 text-xs"
                    >
                      <div>
                        <div className="font-black text-slate-900">
                          {u.name} <span className="font-mono text-slate-600">(ID: {u.id})</span>
                        </div>
                        <div className="text-[11px] text-slate-600">
                          Designation: <b>{u.designation || u.role}</b> • Floor: <b>{u.floor || 'All'}</b>
                          {u.authorizedDeviceName ? ` • Device: ${u.authorizedDeviceName}` : ''}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={async () => {
                            await onApproveRegistration?.(u.id);
                            setActionMessage(`✓ Approved employee registration for ${u.name} (${u.id}).`);
                          }}
                          className="bg-emerald-600 hover:bg-emerald-700 text-white font-black text-xs px-3 py-1.5 rounded-lg shadow"
                        >
                          Approve Registration
                        </button>
                        <button
                          onClick={async () => {
                            await onRejectRegistration?.(u.id);
                            setActionMessage(`Rejected employee registration for ${u.name} (${u.id}).`);
                          }}
                          className="bg-red-600 hover:bg-red-700 text-white font-bold text-xs px-3 py-1.5 rounded-lg"
                        >
                          Reject
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {pendingDeviceOrTvUsers.length > 0 && (
                <div className="bg-cyan-50 border border-cyan-300 rounded-xl p-4 space-y-2.5 shadow-sm">
                  <div className="text-xs font-black text-cyan-950">
                    📺 Pending Smart TV &amp; Device Replacement Requests ({pendingDeviceOrTvUsers.length})
                  </div>
                  {pendingDeviceOrTvUsers.map(u => {
                    const parts = (u.rejectionReason || '').split(':');
                    const isTv = u.rejectionReason?.startsWith('TV_PENDING:');
                    const devId = parts[2] || u.authorizedDeviceId || '';
                    const devName = parts[3] || u.authorizedDeviceName || (isTv ? 'Smart TV Display' : 'Android Phone');
                    const codeOrType = parts[4] || '';
                    return (
                      <div
                        key={`${u.id}-${devId}`}
                        className="bg-white border border-cyan-200 rounded-lg p-3 flex flex-wrap items-center justify-between gap-2 text-xs"
                      >
                        <div>
                          <div className="font-black text-slate-900">
                            {isTv ? '📺 First-Time Smart TV Login Request' : `📱 Device Request: ${u.name} (${u.id})`}
                          </div>
                          <div className="text-[11px] text-slate-600">
                            Device: <b>{devName}</b> ({devId.slice(-8)})
                            {codeOrType ? ` • ${isTv ? `TV Code: ${codeOrType}` : codeOrType}` : ''}
                          </div>
                        </div>
                        <button
                          onClick={async () => {
                            await onApproveDeviceOrTv?.(u.id, devId, devName);
                            setActionMessage(
                              `✓ Approved ${isTv ? 'Smart TV Display' : `device ${devName} for ${u.name}`}!`
                            );
                          }}
                          className="bg-emerald-600 hover:bg-emerald-700 text-white font-black text-xs px-3 py-1.5 rounded-lg shadow"
                        >
                          {isTv ? 'Approve Smart TV' : 'Approve Device'}
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}

              <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
                <div className="p-3 bg-slate-100 text-xs font-bold text-slate-800 border-b">
                  Configured System Accounts &amp; Role Routing
                </div>
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-50 text-slate-600 border-b">
                    <tr>
                      <th className="py-2 px-3">Name</th>
                      <th className="py-2 px-3">Employee ID</th>
                      <th className="py-2 px-3">Role</th>
                      <th className="py-2 px-3">Status</th>
                      <th className="py-2 px-3">Scope</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {users.map(u => (
                      <tr key={u.id}>
                        <td className="py-2 px-3 font-bold text-slate-900">{u.name}</td>
                        <td className="py-2 px-3 text-slate-600 font-mono">{u.username}</td>
                        <td className="py-2 px-3">
                          <span className="bg-blue-100 text-blue-900 font-bold text-[10px] px-2 py-0.5 rounded">
                            {u.role}
                          </span>
                        </td>
                        <td className="py-2 px-3">
                          <span
                            className={`font-bold text-[10px] px-2 py-0.5 rounded ${
                              u.accountStatus === 'PENDING_APPROVAL'
                                ? 'bg-amber-100 text-amber-900'
                                : u.accountStatus === 'REJECTED'
                                ? 'bg-red-100 text-red-900'
                                : 'bg-emerald-100 text-emerald-900'
                            }`}
                          >
                            {u.accountStatus || 'APPROVED'}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-slate-600">
                          {u.floor || 'All'} {u.assignedLine ? `(${u.assignedLine})` : ''}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
