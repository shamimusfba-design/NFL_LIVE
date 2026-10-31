import React, { useState } from 'react';
import {
  FloorId,
  LinePerformanceSummary,
  IncidentRecord,
  FloorKpis,
  ProductionEntryRecord,
  QualityEntryRecord,
  WeeklyQualityRecord,
  UserRole
} from '../types';
import { formatHourSlot12h,
  hourLabelOfSlot, isBiHourlyAuditSlot } from '../data/masterData';

interface ReportsViewProps {
  selectedFloor: FloorId;
  kpis: FloorKpis;
  lineSummaries: LinePerformanceSummary[];
  productions?: ProductionEntryRecord[];
  qualities?: QualityEntryRecord[];
  weeklyQuality?: WeeklyQualityRecord[];
  incidents: IncidentRecord[];
  topDefects: Array<{ defect: string; count: number; dhu: number }>;
  currentUserRole?: UserRole;
  onDeleteProduction?: (id: string, reason: string) => void;
  onDeleteQuality?: (id: string, reason: string) => void;
  onClose: () => void;
}

export const ReportsView: React.FC<ReportsViewProps> = ({
  selectedFloor,
  kpis,
  lineSummaries,
  productions = [],
  qualities = [],
  weeklyQuality = [],
  incidents,
  currentUserRole = 'ADMIN',
  onDeleteProduction,
  onDeleteQuality,
  onClose
}) => {
  const [periodType, setPeriodType] = useState<'Daily' | 'Weekly' | 'Monthly'>('Daily');
  const [reportSection, setReportSection] = useState<
    'PRODUCTION' | 'QUALITY' | 'HOURLY_LOG' | 'WEEKLY_OQL' | 'INCIDENTS'
  >('PRODUCTION');

  const isAdmin = currentUserRole === 'ADMIN';

  const sanitizeCsvField = (val: any): string => {
    const str = String(val ?? '');
    if (str.startsWith('=') || str.startsWith('+') || str.startsWith('-') || str.startsWith('@')) {
      return `"'${str.replace(/"/g, '""')}"`;
    }
    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };

  const downloadCsv = () => {
    let headers: string[] = [];
    let rows: any[][] = [];

    if (reportSection === 'PRODUCTION') {
      headers = [
        'Floor',
        'Line',
        'Style',
        'SMV',
        'Hourly Target',
        'Day Target Pcs',
        'Output Pcs',
        'Gap Pcs',
        'Achievement Pct',
        'Efficiency Pct',
        'Status'
      ];
      rows = lineSummaries.map(l => [
        l.floor,
        l.lineId,
        l.styleNumber,
        l.smv,
        l.hourlyTarget,
        l.target,
        l.output ?? 'Not reported',
        l.gap,
        l.achievementPct !== null ? `${l.achievementPct}%` : 'N/A',
        l.efficiencyPct !== null ? `${l.efficiencyPct}%` : 'N/A',
        l.reportingStatus
      ]);
    } else if (reportSection === 'QUALITY') {
      headers = [
        'Floor',
        'Line',
        'Style',
        'Checked Pcs',
        'Defective Pcs',
        'Total Defects',
        'Today OQL Pct',
        'DHU Pct',
        'Sew Sent',
        'Sew Recv',
        'Sew Bal',
        'Spot Sent',
        'Spot Recv',
        'Spot Bal'
      ];
      rows = lineSummaries.map(l => [
        l.floor,
        l.lineId,
        l.styleNumber,
        l.checkedGarments,
        l.defectiveGarments,
        l.totalDefects,
        l.todayOqlPct !== null ? `${l.todayOqlPct}%` : 'N/A',
        l.dhu !== null ? `${l.dhu}%` : 'N/A',
        l.sewSent,
        l.sewRecv,
        l.sewBal,
        l.spotSent,
        l.spotRecv,
        l.spotBal
      ]);
    } else if (reportSection === 'HOURLY_LOG') {
      headers = [
        'Record Type',
        'Floor',
        'Line',
        'Date',
        'Slot (12h AM/PM)',
        'Output / Checked Pcs',
        'Defective Pcs',
        'Total Defects',
        'Reported By'
      ];
      rows = [
        ...productions.map(p => [
          '1-Hr Production',
          p.floor,
          p.lineId,
          p.date,
          hourLabelOfSlot(p.lineId, p.hourSlot),
          p.outputQty,
          '',
          '',
          p.reportedBy
        ]),
        ...qualities.map(q => [
          isBiHourlyAuditSlot(q.hourSlot) ? '2-Hr Bi-Hourly Audit (OQL)' : '1-Hr Quality DHU',
          q.floor,
          q.lineId,
          q.date,
          hourLabelOfSlot(q.lineId, q.hourSlot),
          q.checkedGarments,
          q.defectiveGarments,
          q.totalDefects,
          q.reportedBy
        ])
      ];
    } else if (reportSection === 'WEEKLY_OQL') {
      headers = ['ISO Week', 'Week Start Date', 'BS OQL (%)', 'NFL AQC (%)', 'Updated By'];
      rows = weeklyQuality.map(w => [
        w.weekKey,
        w.weekStartIso,
        w.bsOqlPct ?? '',
        w.nflAqcPct ?? '',
        w.lastUpdatedBy
      ]);
    } else {
      headers = [
        'Floor',
        'Line',
        'Category',
        'Machine Ref',
        'Description',
        'Reported Cause',
        'Impact',
        'Affected Manpower',
        'Status',
        'Reported By',
        'Duration Mins'
      ];
      rows = incidents.map(i => {
        const durationMins = Math.round(
          ((i.endTimeMillis ?? Date.now()) - i.startTimeMillis) / 60000
        );
        return [
          i.floor,
          i.lineId,
          i.category,
          i.machineRef ?? '',
          i.description,
          i.reportedCause ?? '',
          i.impactLevel,
          i.affectedManpower,
          i.status,
          i.reportedBy,
          durationMins
        ];
      });
    }

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [
        headers.map(sanitizeCsvField).join(','),
        ...rows.map(r => r.map(sanitizeCsvField).join(','))
      ].join('\n');

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute(
      'download',
      `NFL_${reportSection}_${selectedFloor}_${periodType}_12h.csv`
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="fixed inset-0 bg-slate-900/75 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-5xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-black tracking-wide">
              Production, Quality, Bi-Hourly Audit &amp; History Ledger (12-Hour AM/PM)
            </h2>
            <div className="text-xs text-blue-200">
              12-Hour AM/PM Time Format • CSV / Excel Export • Admin Record Correction
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white bg-blue-900/60 hover:bg-blue-800 p-1.5 rounded-lg text-lg font-bold"
          >
            ✕
          </button>
        </div>

        {/* Filters Toolbar */}
        <div className="bg-slate-50 border-b border-slate-200 px-5 py-3 flex flex-wrap items-center justify-between gap-3 text-xs font-bold">
          <div className="flex items-center gap-1.5">
            <span className="text-slate-500 uppercase">Period:</span>
            {(['Daily', 'Weekly', 'Monthly'] as const).map(p => (
              <button
                key={p}
                onClick={() => setPeriodType(p)}
                className={`px-2.5 py-1 rounded-lg transition ${
                  periodType === p
                    ? 'bg-[#1976D2] text-white shadow-sm'
                    : 'bg-white text-slate-700 border'
                }`}
              >
                {p}
              </button>
            ))}
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            {(
              [
                { id: 'PRODUCTION', label: 'Line Production' },
                { id: 'QUALITY', label: 'Quality & Alter' },
                { id: 'HOURLY_LOG', label: '12h AM/PM Log & Audit' },
                { id: 'WEEKLY_OQL', label: 'Weekly BS OQL / AQC' },
                { id: 'INCIDENTS', label: 'NPT Stoppages' }
              ] as const
            ).map(mod => (
              <button
                key={mod.id}
                onClick={() => setReportSection(mod.id)}
                className={`px-2.5 py-1 rounded-lg transition ${
                  reportSection === mod.id
                    ? 'bg-[#002D62] text-white shadow-sm'
                    : 'bg-white text-slate-700 border'
                }`}
              >
                {mod.label}
              </button>
            ))}
          </div>

          <button
            onClick={downloadCsv}
            className="bg-emerald-600 hover:bg-emerald-700 text-white font-black px-4 py-1.5 rounded-lg shadow-sm flex items-center gap-1.5 transition"
          >
            <span>📥 Export CSV (12h AM/PM)</span>
          </button>
        </div>

        {/* Report Content Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4 bg-[#F8FAFC]">
          {/* Executive Summary Card */}
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm grid grid-cols-2 sm:grid-cols-5 gap-4 text-center">
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Weighted Efficiency</div>
              <div className="text-2xl font-black text-[#002D62]">{kpis.todayEfficiencyPct}%</div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Output / Target</div>
              <div className="text-lg font-black text-slate-800">
                {kpis.todayOutputPcs} / {kpis.todayTargetPcs}
              </div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Endline DHU %</div>
              <div className="text-2xl font-black text-amber-600">{kpis.todayDhuPct}%</div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Today OQL %</div>
              <div className="text-2xl font-black text-sky-600">{kpis.todayOqlPct}%</div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Defects Pending</div>
              <div className="text-2xl font-black text-red-600">{kpis.defectsPending} pcs</div>
            </div>
          </div>

          {/* Table display */}
          <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-100 text-slate-700 font-extrabold border-b">
                  {reportSection === 'PRODUCTION' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3">Style</th>
                      <th className="py-2.5 px-3 text-right">SMV</th>
                      <th className="py-2.5 px-3 text-right">Hourly Tgt</th>
                      <th className="py-2.5 px-3 text-right">Day Tgt</th>
                      <th className="py-2.5 px-3 text-right">Output</th>
                      <th className="py-2.5 px-3 text-right">Achievement</th>
                      <th className="py-2.5 px-3 text-right">Efficiency</th>
                    </tr>
                  )}
                  {reportSection === 'QUALITY' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3 text-right">Checked</th>
                      <th className="py-2.5 px-3 text-right">Defective</th>
                      <th className="py-2.5 px-3 text-right">Today OQL</th>
                      <th className="py-2.5 px-3 text-right">DHU</th>
                      <th className="py-2.5 px-3 text-right">Sew Bal</th>
                      <th className="py-2.5 px-3 text-right">Spot Bal</th>
                    </tr>
                  )}
                  {reportSection === 'HOURLY_LOG' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3">Date</th>
                      <th className="py-2.5 px-3">Time Slot (12h AM/PM)</th>
                      <th className="py-2.5 px-3">Category</th>
                      <th className="py-2.5 px-3">Details</th>
                      <th className="py-2.5 px-3">Reported By</th>
                      {isAdmin && <th className="py-2.5 px-3 text-right">Admin Action</th>}
                    </tr>
                  )}
                  {reportSection === 'WEEKLY_OQL' && (
                    <tr>
                      <th className="py-2.5 px-3">ISO Week</th>
                      <th className="py-2.5 px-3">Week Start</th>
                      <th className="py-2.5 px-3 text-right">BS OQL (%)</th>
                      <th className="py-2.5 px-3 text-right">NFL AQC (%)</th>
                      <th className="py-2.5 px-3">Updated By</th>
                    </tr>
                  )}
                  {reportSection === 'INCIDENTS' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3">Category</th>
                      <th className="py-2.5 px-3">Description</th>
                      <th className="py-2.5 px-3">Status</th>
                    </tr>
                  )}
                </thead>
                <tbody className="divide-y divide-slate-200 font-semibold">
                  {reportSection === 'PRODUCTION' &&
                    lineSummaries.map(l => (
                      <tr key={l.lineId}>
                        <td className="py-2 px-3 font-black text-[#002D62]">{l.lineId}</td>
                        <td className="py-2 px-3">{l.styleNumber}</td>
                        <td className="py-2 px-3 text-right">{l.smv.toFixed(1)}</td>
                        <td className="py-2 px-3 text-right">{l.hourlyTarget}</td>
                        <td className="py-2 px-3 text-right">{l.target}</td>
                        <td className="py-2 px-3 text-right font-black">{l.output ?? '—'}</td>
                        <td className="py-2 px-3 text-right">{l.achievementPct ?? '—'}%</td>
                        <td className="py-2 px-3 text-right font-black">{l.efficiencyPct ?? '—'}%</td>
                      </tr>
                    ))}

                  {reportSection === 'QUALITY' &&
                    lineSummaries.map(l => (
                      <tr key={l.lineId}>
                        <td className="py-2 px-3 font-black text-[#002D62]">{l.lineId}</td>
                        <td className="py-2 px-3 text-right">{l.checkedGarments}</td>
                        <td className="py-2 px-3 text-right">{l.defectiveGarments}</td>
                        <td className="py-2 px-3 text-right font-bold">{l.todayOqlPct ?? '—'}%</td>
                        <td className="py-2 px-3 text-right font-bold">{l.dhu ?? '—'}%</td>
                        <td className="py-2 px-3 text-right font-black text-amber-700">{l.sewBal}</td>
                        <td className="py-2 px-3 text-right font-black text-sky-700">{l.spotBal}</td>
                      </tr>
                    ))}

                  {reportSection === 'HOURLY_LOG' && (
                    <>
                      {productions.slice(0, 40).map(p => (
                        <tr key={p.id}>
                          <td className="py-2 px-3 font-black text-[#002D62]">{p.lineId}</td>
                          <td className="py-2 px-3">{p.date}</td>
                          <td className="py-2 px-3 font-black">{hourLabelOfSlot(p.lineId, p.hourSlot)}</td>
                          <td className="py-2 px-3 text-blue-700">1-Hr Production</td>
                          <td className="py-2 px-3">Output: {p.outputQty} pcs ({p.styleNumber})</td>
                          <td className="py-2 px-3">{p.reportedBy}</td>
                          {isAdmin && (
                            <td className="py-2 px-3 text-right">
                              <button
                                onClick={() =>
                                  onDeleteProduction?.(p.id, 'Admin deleted from Web Ledger')
                                }
                                className="text-red-600 hover:underline font-bold text-[11px]"
                              >
                                Delete
                              </button>
                            </td>
                          )}
                        </tr>
                      ))}
                      {qualities.slice(0, 40).map(q => (
                        <tr key={q.id}>
                          <td className="py-2 px-3 font-black text-[#002D62]">{q.lineId}</td>
                          <td className="py-2 px-3">{q.date}</td>
                          <td className="py-2 px-3 font-black">{hourLabelOfSlot(q.lineId, q.hourSlot)}</td>
                          <td className="py-2 px-3 text-emerald-700">
                            {isBiHourlyAuditSlot(q.hourSlot)
                              ? '2-Hr Bi-Hourly Audit (OQL)'
                              : '1-Hr Quality DHU'}
                          </td>
                          <td className="py-2 px-3">
                            Checked: {q.checkedGarments} · Defective: {q.defectiveGarments} · Defects:{' '}
                            {q.totalDefects}
                          </td>
                          <td className="py-2 px-3">{q.reportedBy}</td>
                          {isAdmin && (
                            <td className="py-2 px-3 text-right">
                              <button
                                onClick={() =>
                                  onDeleteQuality?.(q.id, 'Admin deleted from Web Ledger')
                                }
                                className="text-red-600 hover:underline font-bold text-[11px]"
                              >
                                Delete
                              </button>
                            </td>
                          )}
                        </tr>
                      ))}
                    </>
                  )}

                  {reportSection === 'WEEKLY_OQL' &&
                    weeklyQuality.map(w => (
                      <tr key={w.weekKey}>
                        <td className="py-2 px-3 font-black text-[#002D62]">{w.weekKey}</td>
                        <td className="py-2 px-3">{w.weekStartIso}</td>
                        <td className="py-2 px-3 text-right font-black">
                          {w.bsOqlPct !== null ? `${w.bsOqlPct.toFixed(2)}%` : '—'}
                        </td>
                        <td className="py-2 px-3 text-right font-black">
                          {w.nflAqcPct !== null ? `${w.nflAqcPct.toFixed(2)}%` : '—'}
                        </td>
                        <td className="py-2 px-3">{w.lastUpdatedBy}</td>
                      </tr>
                    ))}

                  {reportSection === 'INCIDENTS' &&
                    incidents.map(inc => (
                      <tr key={inc.id}>
                        <td className="py-2 px-3 font-black text-[#002D62]">{inc.lineId}</td>
                        <td className="py-2 px-3">{inc.category}</td>
                        <td className="py-2 px-3">{inc.description}</td>
                        <td className="py-2 px-3 font-black">{inc.status}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
