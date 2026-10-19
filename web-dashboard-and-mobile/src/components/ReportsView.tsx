import React, { useState } from 'react';
import {
  FloorId,
  LinePerformanceSummary,
  IncidentRecord,
  FloorKpis
} from '../types';

interface ReportsViewProps {
  selectedFloor: FloorId;
  kpis: FloorKpis;
  lineSummaries: LinePerformanceSummary[];
  incidents: IncidentRecord[];
  topDefects: Array<{ defect: string; count: number; dhu: number }>;
  onClose: () => void;
}

export const ReportsView: React.FC<ReportsViewProps> = ({
  selectedFloor,
  kpis,
  lineSummaries,
  incidents,
  topDefects,
  onClose
}) => {
  const [periodType, setPeriodType] = useState<'Daily' | 'Weekly' | 'Monthly' | 'Yearly' | 'Custom'>('Daily');
  const [reportSection, setReportSection] = useState<'PRODUCTION' | 'QUALITY' | 'INCIDENTS'>('PRODUCTION');

  /**
   * Sanitizes cells to prevent CSV formula injection
   */
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
        'Target Pcs',
        'Target Due Pcs',
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
        l.target,
        l.targetDue,
        l.output ?? 'Not reported',
        l.gap,
        l.achievementPct !== null ? `${l.achievementPct}%` : 'N/A',
        l.efficiencyPct !== null ? `${l.efficiencyPct}%` : 'N/A',
        l.reportingStatus
      ]);
    } else if (reportSection === 'QUALITY') {
      headers = ['Floor', 'Line', 'Style', 'Output Pcs', 'Today OQL Pct', 'DHU Pct', 'Reporting Status'];
      rows = lineSummaries.map(l => [
        l.floor,
        l.lineId,
        l.styleNumber,
        l.output ?? 'Not reported',
        l.todayOqlPct !== null ? `${l.todayOqlPct}%` : 'N/A',
        l.dhu !== null ? `${l.dhu}%` : 'N/A',
        l.reportingStatus
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
      [headers.map(sanitizeCsvField).join(','), ...rows.map(r => r.map(sanitizeCsvField).join(','))].join(
        '\n'
      );

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute(
      'download',
      `NFL_${reportSection}_Report_${selectedFloor}_${periodType}_2026-10-09.csv`
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="fixed inset-0 bg-slate-900/70 z-50 flex items-center justify-center p-3 md:p-6 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl max-h-[92vh] flex flex-col overflow-hidden border border-slate-200">
        <div className="bg-[#002D62] text-white px-5 py-3.5 flex items-center justify-between">
          <div>
            <h2 className="text-base font-black tracking-wide">
              Production, Quality & Incident Reports
            </h2>
            <div className="text-xs text-blue-200">
              Weighted aggregates • Shift boundary: Asia/Dhaka • Formula Injection Safe
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
          <div className="flex items-center gap-2">
            <span className="text-slate-500 uppercase">Period:</span>
            {(['Daily', 'Weekly', 'Monthly', 'Yearly', 'Custom'] as const).map(p => (
              <button
                key={p}
                onClick={() => setPeriodType(p)}
                className={`px-3 py-1 rounded-lg transition ${
                  periodType === p ? 'bg-[#1976D2] text-white shadow-sm' : 'bg-white text-slate-700 border'
                }`}
              >
                {p}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <span className="text-slate-500 uppercase">Module:</span>
            {(['PRODUCTION', 'QUALITY', 'INCIDENTS'] as const).map(mod => (
              <button
                key={mod}
                onClick={() => setReportSection(mod)}
                className={`px-3 py-1 rounded-lg transition ${
                  reportSection === mod ? 'bg-[#002D62] text-white shadow-sm' : 'bg-white text-slate-700 border'
                }`}
              >
                {mod}
              </button>
            ))}
          </div>

          <button
            onClick={downloadCsv}
            className="bg-emerald-600 hover:bg-emerald-700 text-white font-black px-4 py-1.5 rounded-lg shadow-sm flex items-center gap-1.5 transition ml-auto"
          >
            <span>📥 Export CSV</span>
          </button>
        </div>

        {/* Report Content Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4 bg-[#F8FAFC]">
          {/* Executive Summary Card */}
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm grid grid-cols-4 gap-4 text-center">
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Weighted Efficiency</div>
              <div className="text-2xl font-black text-[#002D62]">{kpis.todayEfficiencyPct}%</div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Target vs Output</div>
              <div className="text-lg font-black text-slate-800">
                {kpis.todayOutputPcs} / {kpis.todayTargetPcs} pcs
              </div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Average OQL</div>
              <div className="text-2xl font-black text-amber-600">{kpis.todayOqlPct}%</div>
            </div>
            <div>
              <div className="text-[10px] text-slate-500 font-bold uppercase">Open Incidents</div>
              <div className="text-2xl font-black text-red-600">{incidents.filter(i => i.status !== 'RESOLVED').length}</div>
            </div>
          </div>

          {/* Table display */}
          <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
            <div className="px-4 py-2.5 bg-slate-100 font-bold text-xs text-slate-700 border-b border-slate-200">
              Detailed Line Records ({selectedFloor} • {periodType})
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-50 text-slate-600 font-extrabold border-b">
                  {reportSection === 'PRODUCTION' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3">Style</th>
                      <th className="py-2.5 px-3 text-right">Target</th>
                      <th className="py-2.5 px-3 text-right">Output</th>
                      <th className="py-2.5 px-3 text-right">Achievement</th>
                      <th className="py-2.5 px-3 text-right">Efficiency</th>
                      <th className="py-2.5 px-3 text-center">Status</th>
                    </tr>
                  )}
                  {reportSection === 'QUALITY' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3">Style</th>
                      <th className="py-2.5 px-3 text-right">Output</th>
                      <th className="py-2.5 px-3 text-right">Today OQL</th>
                      <th className="py-2.5 px-3 text-right">DHU</th>
                      <th className="py-2.5 px-3 text-center">Reporting</th>
                    </tr>
                  )}
                  {reportSection === 'INCIDENTS' && (
                    <tr>
                      <th className="py-2.5 px-3">Line</th>
                      <th className="py-2.5 px-3">Category</th>
                      <th className="py-2.5 px-3">Description</th>
                      <th className="py-2.5 px-3">Impact</th>
                      <th className="py-2.5 px-3 text-center">Status</th>
                      <th className="py-2.5 px-3 text-right">Reporter</th>
                    </tr>
                  )}
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {reportSection === 'PRODUCTION' &&
                    lineSummaries.map(l => (
                      <tr key={l.lineId} className="hover:bg-slate-50">
                        <td className="py-2 px-3 font-bold text-slate-900">{l.lineId}</td>
                        <td className="py-2 px-3 text-slate-700">{l.styleNumber}</td>
                        <td className="py-2 px-3 text-right font-medium">{l.target}</td>
                        <td className="py-2 px-3 text-right font-bold text-slate-900">
                          {l.output ?? <span className="text-red-500 font-semibold">Not reported</span>}
                        </td>
                        <td className="py-2 px-3 text-right font-bold">
                          {l.achievementPct !== null ? `${l.achievementPct}%` : '-'}
                        </td>
                        <td className="py-2 px-3 text-right font-bold text-blue-700">
                          {l.efficiencyPct !== null ? `${l.efficiencyPct}%` : '-'}
                        </td>
                        <td className="py-2 px-3 text-center font-semibold">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] ${
                              l.reportingStatus === 'Complete'
                                ? 'bg-green-100 text-green-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {l.reportingStatus}
                          </span>
                        </td>
                      </tr>
                    ))}

                  {reportSection === 'QUALITY' &&
                    lineSummaries.map(l => (
                      <tr key={l.lineId} className="hover:bg-slate-50">
                        <td className="py-2 px-3 font-bold text-slate-900">{l.lineId}</td>
                        <td className="py-2 px-3 text-slate-700">{l.styleNumber}</td>
                        <td className="py-2 px-3 text-right font-bold">{l.output ?? '-'}</td>
                        <td className="py-2 px-3 text-right font-bold text-amber-600">
                          {l.todayOqlPct !== null ? `${l.todayOqlPct}%` : '-'}
                        </td>
                        <td className="py-2 px-3 text-right font-bold text-red-600">
                          {l.dhu !== null ? `${l.dhu}%` : '-'}
                        </td>
                        <td className="py-2 px-3 text-center font-semibold">{l.reportingStatus}</td>
                      </tr>
                    ))}

                  {reportSection === 'INCIDENTS' &&
                    incidents.map(i => (
                      <tr key={i.id} className="hover:bg-slate-50">
                        <td className="py-2 px-3 font-bold text-slate-900">{i.lineId}</td>
                        <td className="py-2 px-3 font-medium text-slate-700">{i.category}</td>
                        <td className="py-2 px-3 text-slate-800">{i.description}</td>
                        <td className="py-2 px-3 font-medium text-red-600">{i.impactLevel}</td>
                        <td className="py-2 px-3 text-center">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              i.status === 'RESOLVED'
                                ? 'bg-green-100 text-green-800'
                                : i.status === 'ACKNOWLEDGED'
                                ? 'bg-amber-100 text-amber-800'
                                : 'bg-red-100 text-red-800'
                            }`}
                          >
                            {i.status}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-right text-slate-500">{i.reportedBy}</td>
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
