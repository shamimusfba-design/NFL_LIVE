import { AlertComment } from '../types';
import { CommentThread } from './CommentThread';
import React, { useState, useEffect } from 'react';
import {
  IncidentRecord,
  IncidentCategory,
  UserRole,
  FloorId,
  LineMasterItem
} from '../types';

interface IncidentManagerProps {
  incidents: IncidentRecord[];
  lines: LineMasterItem[];
  currentUserRole: UserRole;
  currentUserName: string;
  onReportIncident: (incident: Omit<IncidentRecord, 'id' | 'startTimeMillis' | 'status'>) => void;
  onMuteDeviceAlarm: (incidentId: string) => void;
  onAcknowledgeIncident: (incidentId: string, acknowledgedBy: string) => void;
  onResolveIncident: (incidentId: string, resolvedBy: string, resolutionNote: string) => void;
  comments?: AlertComment[];
  onAddComment?: (targetId: string, text: string, parentId: string | null) => void;
  /** Lines whose OQL today is above 10%. They are notifications too. */
  highOqlLines?: Array<{ lineId: string; floor: string; oqlPct: number; checked: number; date: string }>;
  onClose: () => void;
}

export const IncidentManager: React.FC<IncidentManagerProps> = ({
  incidents,
  lines,
  currentUserRole,
  currentUserName,
  onReportIncident,
  onMuteDeviceAlarm,
  onAcknowledgeIncident,
  onResolveIncident,
  comments = [],
  onAddComment,
  highOqlLines = [],
  onClose
}) => {
  const [activeTab, setActiveTab] = useState<'ACTIVE' | 'REPORT' | 'INBOX' | 'HISTORY'>('ACTIVE');

  // Form State
  const [formFloor, setFormFloor] = useState<'Sew 1' | 'Sew 2' | 'Sew 3'>('Sew 2');
  const [formLine, setFormLine] = useState('K1');
  const [formCategory, setFormCategory] = useState<IncidentCategory>('MACHINE_BREAKDOWN');
  const [formMachineRef, setFormMachineRef] = useState('SNLS-04');
  const [formDescription, setFormDescription] = useState('');
  const [formCause, setFormCause] = useState('');
  const [formImpact, setFormImpact] = useState<'LOW' | 'MEDIUM' | 'HIGH' | 'FULL_LINE'>('HIGH');
  const [formAffectedManpower, setFormAffectedManpower] = useState(6);
  const [submitSuccess, setSubmitSuccess] = useState(false);

  // Resolution modal state
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [resolutionNote, setResolutionNote] = useState('');

  // Live seconds ticker to force re-render of elapsed duration every second
  const [, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const formatElapsed = (startTimeMillis: number, endTimeMillis?: number | null) => {
    const end = endTimeMillis ?? Date.now();
    const diffSec = Math.max(0, Math.floor((end - startTimeMillis) / 1000));
    const hours = Math.floor(diffSec / 3600);
    const mins = Math.floor((diffSec % 3600) / 60);
    const secs = diffSec % 60;
    if (hours > 0) {
      return `${hours}h ${mins}m ${secs}s`;
    }
    return `${mins}m ${secs}s`;
  };

  const getTargetRoleForCategory = (cat: IncidentCategory): string => {
    switch (cat) {
      case 'MACHINE_BREAKDOWN':
        return 'Mechanic / Maintenance';
      case 'QUALITY_PROBLEM':
        return 'Quality Manager / Officer';
      case 'LINE_BALANCING':
        return 'IE (Industrial Engineering)';
      case 'INPUT_MATERIAL':
        return 'Input / Material Team';
      case 'LOW_PRODUCTION':
        return 'Production Manager / Officer';
      case 'OTHER':
        return 'Floor Supervisor';
    }
  };

  const isRoleTargeted = (cat: IncidentCategory): boolean => {
    if (currentUserRole === 'ADMIN' || currentUserRole === 'MANAGEMENT_VIEW') return true;
    if (cat === 'MACHINE_BREAKDOWN' && currentUserRole === 'MECHANIC') return true;
    if (cat === 'QUALITY_PROBLEM' && (currentUserRole === 'QUALITY_MANAGER' || currentUserRole === 'QUALITY_REPORTER')) return true;
    if (cat === 'LINE_BALANCING' && currentUserRole === 'IE') return true;
    if (cat === 'INPUT_MATERIAL' && currentUserRole === 'INPUT_MATERIAL') return true;
    if (cat === 'LOW_PRODUCTION' && (currentUserRole === 'PRODUCTION_MANAGER' || currentUserRole === 'PRODUCTION_REPORTER')) return true;
    if (currentUserRole === 'SUPERVISOR') return true;
    return false;
  };

  const openIncidents = incidents.filter(i => i.status !== 'RESOLVED');
  const roleInboxIncidents = openIncidents.filter(i => isRoleTargeted(i.category));
  const resolvedIncidents = incidents.filter(i => i.status === 'RESOLVED');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formDescription.trim()) return;

    onReportIncident({
      floor: formFloor,
      lineId: formLine,
      category: formCategory,
      machineRef: formMachineRef,
      description: formDescription,
      reportedCause: formCause,
      impactLevel: formImpact,
      affectedManpower: Number(formAffectedManpower),
      reportedBy: currentUserName,
      isDemo: false
    });

    setFormDescription('');
    setFormCause('');
    setSubmitSuccess(true);
    setTimeout(() => {
      setSubmitSuccess(false);
      setActiveTab('ACTIVE');
    }, 1200);
  };

  return (
    <div className="fixed inset-0 bg-slate-900/70 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        {/* Header */}
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-xl">🚨</span>
            <div>
              <h2 className="text-base font-black tracking-wide">
                Live Incident Response & Problem Routing
              </h2>
              <div className="text-xs text-blue-200">
                Authoritative Timers • Role-based Alerting • Mute / Acknowledge / Resolve
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white bg-blue-900/60 hover:bg-blue-800 p-1.5 rounded-lg text-lg font-bold transition"
          >
            ✕
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-200 bg-slate-50 px-4 pt-2 gap-2 text-xs font-bold">
          <button
            onClick={() => setActiveTab('ACTIVE')}
            className={`pb-2.5 px-3 border-b-2 flex items-center gap-1.5 transition ${
              activeTab === 'ACTIVE'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <span>Active Incidents</span>
            <span className="bg-red-500 text-white text-[10px] px-1.5 py-0.2 rounded-full font-black">
              {openIncidents.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('REPORT')}
            className={`pb-2.5 px-3 border-b-2 flex items-center gap-1.5 transition ${
              activeTab === 'REPORT'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <span>➕ Report Problem</span>
          </button>

          <button
            onClick={() => setActiveTab('INBOX')}
            className={`pb-2.5 px-3 border-b-2 flex items-center gap-1.5 transition ${
              activeTab === 'INBOX'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <span>My Role Inbox ({currentUserRole})</span>
            {roleInboxIncidents.length > 0 && (
              <span className="bg-amber-500 text-white text-[10px] px-1.5 py-0.2 rounded-full font-black">
                {roleInboxIncidents.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('HISTORY')}
            className={`pb-2.5 px-3 border-b-2 flex items-center gap-1.5 transition ${
              activeTab === 'HISTORY'
                ? 'border-[#002D62] text-[#002D62]'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <span>Resolved History ({resolvedIncidents.length})</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-[#F8FAFC]">
          {/* TAB 1: ACTIVE INCIDENTS */}
          {activeTab === 'ACTIVE' && (
            <div className="space-y-3">
              {highOqlLines.length > 0 && (
                <div className="space-y-2 rounded-xl border border-red-300 bg-red-50 p-3">
                  <div className="text-sm font-black text-red-800">High OQL today (above 10%): {highOqlLines.length} line(s)</div>
                  {highOqlLines.map(h => (
                    <div key={h.lineId} className="rounded-lg border border-red-200 bg-white p-2.5">
                      <div className="flex items-center gap-2">
                        <span className="rounded bg-[#0A2558] px-2 py-0.5 text-xs font-black text-white">{h.lineId}</span>
                        <span className="text-xs text-slate-500">{h.floor}</span>
                        <span className="ml-auto text-sm font-black text-red-700">OQL {h.oqlPct.toFixed(1)}%</span>
                        <span className="text-[11px] text-slate-500">{h.checked} pcs checked</span>
                      </div>
                      <CommentThread targetId={`oql|${h.lineId}|${h.date}`} comments={comments} onAdd={(t, x, p) => onAddComment?.(t, x, p)} />
                    </div>
                  ))}
                </div>
              )}
              {openIncidents.length === 0 ? (
                <div className="text-center py-12 text-slate-500">
                  <span className="text-4xl">✅</span>
                  <div className="font-bold text-sm mt-2 text-slate-700">No active incidents</div>
                  <div className="text-xs">All sewing lines operating without reported downtime.</div>
                </div>
              ) : (
                openIncidents.map(inc => {
                  const isAck = inc.status === 'ACKNOWLEDGED';
                  const targetedRole = getTargetRoleForCategory(inc.category);

                  return (
                    <div
                      key={inc.id}
                      className={`bg-white rounded-xl border p-4 shadow-sm transition ${
                        isAck ? 'border-amber-300 bg-amber-50/20' : 'border-red-400 bg-red-50/30 ring-1 ring-red-300'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex items-start gap-3">
                          <span className="text-2xl mt-0.5">
                            {inc.category === 'MACHINE_BREAKDOWN'
                              ? '🔧'
                              : inc.category === 'QUALITY_PROBLEM'
                              ? '🔍'
                              : inc.category === 'LOW_PRODUCTION'
                              ? '📉'
                              : '⚠️'}
                          </span>
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-black text-sm text-slate-900">
                                Line {inc.lineId} ({inc.floor})
                              </span>
                              <span className="text-xs font-bold px-2 py-0.5 rounded bg-slate-200 text-slate-800">
                                {inc.category.replace('_', ' ')}
                              </span>
                              {inc.machineRef && (
                                <span className="text-xs font-mono bg-blue-100 text-blue-800 px-1.5 py-0.5 rounded font-bold">
                                  {inc.machineRef}
                                </span>
                              )}
                              <span
                                className={`text-[10px] font-black px-2 py-0.5 rounded-full ${
                                  isAck ? 'bg-amber-100 text-amber-800' : 'bg-red-500 text-white animate-pulse'
                                }`}
                              >
                                {inc.status}
                              </span>
                            </div>
                            <p className="text-xs text-slate-800 font-semibold mt-1.5">
                              {inc.description}
                            </p>
                            {inc.reportedCause && (
                              <p className="text-[11px] text-slate-500 mt-0.5">
                                <span className="font-bold">Reported Cause:</span> {inc.reportedCause}
                              </p>
                            )}
                            <div className="flex items-center gap-4 text-[10px] text-slate-500 mt-2 font-medium">
                              <span>Reported by: <b className="text-slate-700">{inc.reportedBy}</b></span>
                              <span>Targeted: <b className="text-blue-700">{targetedRole}</b></span>
                              <span>Impact: <b className="text-red-700">{inc.impactLevel} ({inc.affectedManpower} ops)</b></span>
                            </div>
                          </div>
                        </div>

                        {/* Live Elapsed Stopwatch Badge */}
                        <div className="text-right flex flex-col items-end gap-1.5">
                          <div className="bg-slate-900 text-white font-mono text-xs font-black px-2.5 py-1 rounded-lg shadow-inner flex items-center gap-1.5">
                            <span className="w-2 h-2 rounded-full bg-red-400 animate-ping"></span>
                            <span>{formatElapsed(inc.startTimeMillis)}</span>
                          </div>
                          <span className="text-[10px] text-slate-400 font-semibold">Elapsed Downtime</span>
                        </div>
                      </div>

                      {/* 3 Separate Action Buttons: Mute, Acknowledge, Resolve */}
                      <div className="flex items-center justify-end gap-2 mt-3 pt-3 border-t border-slate-100">
                        {/* 1. MUTE (silences alarm on this device only) */}
                        <button
                          onClick={() => onMuteDeviceAlarm(inc.id)}
                          className={`text-xs font-bold px-3 py-1.5 rounded-lg border transition flex items-center gap-1 ${
                            inc.isMutedOnCurrentDevice
                              ? 'bg-slate-100 text-slate-400 border-slate-200'
                              : 'bg-white hover:bg-slate-50 text-slate-700 border-slate-300'
                          }`}
                          title="Silences alarm on this device only. Does not close incident or stop timer."
                        >
                          <span>{inc.isMutedOnCurrentDevice ? '🔇 Muted' : '🔔 Mute Alarm'}</span>
                        </button>

                        {/* 2. ACKNOWLEDGE (records that responsible officer accepted) */}
                        {!isAck ? (
                          <button
                            onClick={() => onAcknowledgeIncident(inc.id, currentUserName)}
                            className="bg-amber-500 hover:bg-amber-600 text-slate-900 font-black text-xs px-3.5 py-1.5 rounded-lg shadow-sm transition"
                          >
                            ✓ Acknowledge
                          </button>
                        ) : (
                          <span className="text-[11px] font-bold text-amber-800 bg-amber-100 px-2 py-1 rounded-md">
                            Acknowledged by {inc.acknowledgedBy}
                          </span>
                        )}

                        {/* 3. RESOLVE / STOP (prompts note and stops authoritative timer) */}
                        <button
                          onClick={() => {
                            setResolvingId(inc.id);
                            setResolutionNote('');
                          }}
                          className="bg-emerald-600 hover:bg-emerald-700 text-white font-black text-xs px-4 py-1.5 rounded-lg shadow-sm transition"
                        >
                          ⏹ Resolve & Stop Timer
                        </button>
                      </div>
                      <CommentThread targetId={inc.id} comments={comments} onAdd={(t, x, p) => onAddComment?.(t, x, p)} />
                    </div>
                  );
                })
              )}
            </div>
          )}

          {/* TAB 2: REPORT PROBLEM FORM */}
          {activeTab === 'REPORT' && (
            <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-slate-200 p-5 space-y-4 shadow-sm">
              <h3 className="text-sm font-black text-slate-900 border-b border-slate-100 pb-2">
                Supervisor Problem Reporting & Route Dispatch
              </h3>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Floor</label>
                  <select
                    value={formFloor}
                    onChange={e => setFormFloor(e.target.value as any)}
                    className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                  >
                    <option value="Sew 1">Sew 1</option>
                    <option value="Sew 2">Sew 2</option>
                    <option value="Sew 3">Sew 3</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Line Affected</label>
                  <select
                    value={formLine}
                    onChange={e => setFormLine(e.target.value)}
                    className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                  >
                    {lines
                      .filter(l => l.floor === formFloor)
                      .map(l => (
                        <option key={l.id} value={l.id}>
                          Line {l.id}
                        </option>
                      ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Problem Category</label>
                  <select
                    value={formCategory}
                    onChange={e => setFormCategory(e.target.value as any)}
                    className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                  >
                    <option value="MACHINE_BREAKDOWN">🔧 Machine Breakdown (→ Mechanic)</option>
                    <option value="QUALITY_PROBLEM">🔍 Quality Problem (→ Quality Mgr)</option>
                    <option value="LINE_BALANCING">⚖️ Line Balancing (→ IE Officer)</option>
                    <option value="INPUT_MATERIAL">📦 Input / Material (→ Input Team)</option>
                    <option value="LOW_PRODUCTION">📉 Low Production (→ Prod Mgr)</option>
                    <option value="OTHER">⚠️ Other Operational</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Machine Reference / ID (if applicable)
                  </label>
                  <input
                    type="text"
                    value={formMachineRef}
                    onChange={e => setFormMachineRef(e.target.value)}
                    placeholder="e.g. SNLS-04, Overlock-02"
                    className="w-full text-xs border border-slate-300 rounded-lg p-2"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Problem Description *
                </label>
                <textarea
                  required
                  rows={2}
                  value={formDescription}
                  onChange={e => setFormDescription(e.target.value)}
                  placeholder="Describe the stoppage or fault clearly..."
                  className="w-full text-xs border border-slate-300 rounded-lg p-2"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Reported Reason / Cause (Optional)
                </label>
                <input
                  type="text"
                  value={formCause}
                  onChange={e => setFormCause(e.target.value)}
                  placeholder="e.g. Belt broken, tension error, waiting for bundles"
                  className="w-full text-xs border border-slate-300 rounded-lg p-2"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Impact Level</label>
                  <select
                    value={formImpact}
                    onChange={e => setFormImpact(e.target.value as any)}
                    className="w-full text-xs font-bold border border-slate-300 rounded-lg p-2 bg-white"
                  >
                    <option value="LOW">Low (Single machine/operator)</option>
                    <option value="MEDIUM">Medium (Section of line affected)</option>
                    <option value="HIGH">High (Bottle-neck operation)</option>
                    <option value="FULL_LINE">Full Line Complete Stoppage</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Affected Manpower Count
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={50}
                    value={formAffectedManpower}
                    onChange={e => setFormAffectedManpower(Number(e.target.value))}
                    className="w-full text-xs border border-slate-300 rounded-lg p-2 font-bold"
                  />
                </div>
              </div>

              {submitSuccess && (
                <div className="p-3 bg-green-50 text-green-800 text-xs font-bold rounded-lg border border-green-200">
                  ✓ Incident reported and routed to responsible team with active stopwatch!
                </div>
              )}

              <div className="pt-2 flex justify-end">
                <button
                  type="submit"
                  className="bg-[#002D62] hover:bg-blue-900 text-white font-black text-xs px-6 py-2.5 rounded-xl shadow transition"
                >
                  🚀 Submit & Start Incident Timer
                </button>
              </div>
            </form>
          )}

          {/* TAB 3: ROLE INBOX */}
          {activeTab === 'INBOX' && (
            <div className="space-y-3">
              <div className="bg-blue-50 border border-blue-200 p-3 rounded-xl text-xs text-blue-900">
                <b>{currentUserRole} Priority Inbox:</b> Incidents specifically routed to your responsibility group.
              </div>
              {roleInboxIncidents.length === 0 ? (
                <div className="text-center py-8 text-slate-500 text-xs">
                  No pending incidents waiting for your role action.
                </div>
              ) : (
                roleInboxIncidents.map(inc => (
                  <div key={inc.id} className="bg-white border rounded-xl p-3 shadow-sm space-y-2">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-xs text-slate-900">
                        Line {inc.lineId} — {inc.category}
                      </span>
                      <span className="font-mono text-xs font-bold text-red-600">
                        Elapsed: {formatElapsed(inc.startTimeMillis)}
                      </span>
                    </div>
                    <p className="text-xs text-slate-700">{inc.description}</p>
                    <div className="flex justify-end gap-2 pt-2 border-t">
                      <button
                        onClick={() => onAcknowledgeIncident(inc.id, currentUserName)}
                        className="text-xs bg-amber-500 text-slate-900 font-bold px-3 py-1 rounded"
                      >
                        Acknowledge
                      </button>
                      <button
                        onClick={() => {
                          setResolvingId(inc.id);
                          setResolutionNote('');
                        }}
                        className="text-xs bg-emerald-600 text-white font-bold px-3 py-1 rounded"
                      >
                        Resolve
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}

          {/* TAB 4: RESOLVED HISTORY */}
          {activeTab === 'HISTORY' && (
            <div className="space-y-2">
              {resolvedIncidents.length === 0 ? (
                <div className="text-center py-8 text-slate-500 text-xs">
                  No incidents resolved yet today.
                </div>
              ) : (
                resolvedIncidents.map(inc => (
                  <div key={inc.id} className="bg-white border border-slate-200 rounded-xl p-3 text-xs space-y-1">
                    <div className="flex justify-between font-bold text-slate-800">
                      <span>Line {inc.lineId} ({inc.category})</span>
                      <span className="text-green-700">✓ Total Duration: {formatElapsed(inc.startTimeMillis, inc.endTimeMillis)}</span>
                    </div>
                    <div className="text-slate-600">{inc.description}</div>
                    <div className="text-[11px] text-slate-500 font-medium">
                      <b>Resolution note:</b> {inc.resolutionNote || 'Resolved and restored to normal production'} (Resolved by: {inc.resolvedBy})
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>

      {/* RESOLUTION MODAL */}
      {resolvingId && (
        <div className="fixed inset-0 bg-slate-950/80 z-60 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl p-5 max-w-md w-full space-y-4 shadow-2xl border border-slate-300">
            <h3 className="text-sm font-black text-slate-900">
              Close Incident & Record Resolution Note
            </h3>
            <p className="text-xs text-slate-600">
              This will record the authoritative resolution timestamp, stop the live problem timer, and update indicators.
            </p>
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                Resolution Note *
              </label>
              <textarea
                required
                rows={3}
                value={resolutionNote}
                onChange={e => setResolutionNote(e.target.value)}
                placeholder="e.g. Belt replaced, needle bar readjusted, test run 5 pieces passed."
                className="w-full text-xs border border-slate-300 rounded-lg p-2"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setResolvingId(null)}
                className="text-xs font-bold px-3 py-1.5 rounded-lg border text-slate-600"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onResolveIncident(resolvingId, currentUserName, resolutionNote);
                  setResolvingId(null);
                }}
                className="text-xs font-black px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white shadow"
              >
                Confirm Resolution & Stop Timer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
