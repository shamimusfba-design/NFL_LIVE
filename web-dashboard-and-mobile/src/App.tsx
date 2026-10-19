import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  FloorId,
  UserRole,
  UserAccount,
  LineTargetConfig,
  ProductionEntryRecord,
  QualityEntryRecord,
  IncidentRecord,
  LineMasterItem
} from './types';
import {
  ALL_FACTORY_LINES,
  STANDARD_USERS,
  generateSyntheticDemoDataset
} from './data/masterData';
import { CalculationEngine } from './services/calculationEngine';
import { MobileDashboard } from './components/MobileDashboard';
import { TvDesktopDashboard } from './components/TvDesktopDashboard';
import { IncidentManager } from './components/IncidentManager';
import { EntryForms } from './components/EntryForms';
import { ReportsView } from './components/ReportsView';
import { AdminView } from './components/AdminView';
import { LoginView } from './components/LoginView';

export default function App() {
  // Master state
  const [lines, setLines] = useState<LineMasterItem[]>(ALL_FACTORY_LINES);
  const [users, setUsers] = useState<UserAccount[]>(STANDARD_USERS);
  const [currentUser, setCurrentUser] = useState<UserAccount | null>(null); // Start null so Login interface appears!

  // Display mode: 'MOBILE' or 'TV'
  const [activeDeviceView, setActiveDeviceView] = useState<'MOBILE' | 'TV'>('MOBILE');

  // Filters
  const [selectedFloor, setSelectedFloor] = useState<FloorId>('Sew 2');
  const [selectedPeriod, setSelectedPeriod] = useState<'Today' | 'Week' | 'Month'>('Today');

  // Datasets
  const [isDemoMode, setIsDemoMode] = useState<boolean>(true);
  const [targets, setTargets] = useState<LineTargetConfig[]>([]);
  const [productions, setProductions] = useState<ProductionEntryRecord[]>([]);
  const [qualities, setQualities] = useState<QualityEntryRecord[]>([]);
  const [incidents, setIncidents] = useState<IncidentRecord[]>([]);

  // Modals & Panels
  const [showIncidentModal, setShowIncidentModal] = useState(false);
  const [showEntryModal, setShowEntryModal] = useState(false);
  const [showReportsModal, setShowReportsModal] = useState(false);
  const [showAdminModal, setShowAdminModal] = useState(false);
  const [showRoleSelector, setShowRoleSelector] = useState(false);

  // Audio Alarm Synth for Phone Alarms (Web Audio API)
  const audioCtxRef = useRef<AudioContext | null>(null);
  const alarmIntervalRef = useRef<number | null>(null);

  // Initialize demo data on first load
  useEffect(() => {
    const demo = generateSyntheticDemoDataset();
    setTargets(demo.targets);
    setProductions(demo.productions);
    setQualities(demo.qualities);
    setIncidents(demo.incidents);
  }, []);

  // Web Audio Alarm tone generator for incident alerts
  const triggerBeep = () => {
    try {
      if (!audioCtxRef.current) {
        audioCtxRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      }
      const ctx = audioCtxRef.current;
      if (ctx.state === 'suspended') {
        ctx.resume();
      }
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, ctx.currentTime); // 880Hz alert beep
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    } catch {
      // Audio autoplay policy fallback
    }
  };

  // Check if there are unmuted active incidents targeted at current user role
  const hasUnmutedAlarmForRole = useMemo(() => {
    if (!currentUser) return false;
    return incidents.some(inc => {
      if (inc.status === 'RESOLVED' || inc.isMutedOnCurrentDevice) return false;
      if (currentUser.role === 'ADMIN') return true;
      if (inc.category === 'MACHINE_BREAKDOWN' && currentUser.role === 'MECHANIC') return true;
      if (inc.category === 'QUALITY_PROBLEM' && currentUser.role === 'QUALITY_MANAGER') return true;
      if (inc.category === 'LINE_BALANCING' && currentUser.role === 'IE') return true;
      if (inc.category === 'INPUT_MATERIAL' && currentUser.role === 'INPUT_MATERIAL') return true;
      if (inc.category === 'LOW_PRODUCTION' && currentUser.role === 'PRODUCTION_MANAGER') return true;
      return false;
    });
  }, [incidents, currentUser]);

  // Handle background beeper when alarm is triggered
  useEffect(() => {
    if (hasUnmutedAlarmForRole) {
      if (!alarmIntervalRef.current) {
        alarmIntervalRef.current = window.setInterval(triggerBeep, 2500);
      }
    } else {
      if (alarmIntervalRef.current) {
        clearInterval(alarmIntervalRef.current);
        alarmIntervalRef.current = null;
      }
    }
    return () => {
      if (alarmIntervalRef.current) {
        clearInterval(alarmIntervalRef.current);
        alarmIntervalRef.current = null;
      }
    };
  }, [hasUnmutedAlarmForRole]);

  // Target-based filtering:
  // FIXED: Now shows ALL lines from master data, not just those with targets.
  const targetedConfigs = useMemo(() => {
    return CalculationEngine.getTargetedLines(lines, targets, selectedFloor, '2026-10-09');
  }, [lines, targets, selectedFloor]);

  // Compute line summaries & floor KPIs
  const lineSummaries = useMemo(() => {
    return CalculationEngine.computeLineSummaries(
      targetedConfigs,
      productions,
      qualities,
      incidents
    );
  }, [targetedConfigs, productions, qualities, incidents]);

  const floorKpis = useMemo(() => {
    return CalculationEngine.computeFloorKpis(lineSummaries, incidents);
  }, [lineSummaries, incidents]);

  const topDefects = useMemo(() => {
    return CalculationEngine.computeTopDefects(qualities);
  }, [qualities]);

  const highestDhuLines = useMemo(() => {
    return CalculationEngine.computeHighestDhuLines(lineSummaries);
  }, [lineSummaries]);

  // Open incidents list
  const openIncidents = useMemo(() => {
    return incidents.filter(i => i.status !== 'RESOLVED');
  }, [incidents]);

  // Incident Actions
  const handleReportIncident = (newInc: Omit<IncidentRecord, 'id' | 'startTimeMillis' | 'status'>) => {
    const inc: IncidentRecord = {
      ...newInc,
      id: `inc-${Date.now()}`,
      startTimeMillis: Date.now(),
      status: 'OPEN',
      isMutedOnCurrentDevice: false
    };
    setIncidents(prev => [inc, ...prev]);
  };

  const handleMuteAlarm = (id: string) => {
    setIncidents(prev =>
      prev.map(i => (i.id === id ? { ...i, isMutedOnCurrentDevice: true } : i))
    );
  };

  const handleAcknowledgeIncident = (id: string, ackBy: string) => {
    setIncidents(prev =>
      prev.map(i =>
        i.id === id
          ? {
              ...i,
              status: 'ACKNOWLEDGED',
              acknowledgedBy: ackBy,
              acknowledgedTimeMillis: Date.now()
            }
          : i
      )
    );
  };

  const handleResolveIncident = (id: string, resBy: string, note: string) => {
    setIncidents(prev =>
      prev.map(i =>
        i.id === id
          ? {
              ...i,
              status: 'RESOLVED',
              resolvedBy: resBy,
              resolutionNote: note,
              endTimeMillis: Date.now()
            }
          : i
      )
    );
  };

  // Demo Data Lifecycle
  const handleLoadDemoData = () => {
    const demo = generateSyntheticDemoDataset();
    // Idempotent merge: remove prior demo records, keep real ones
    setTargets(prev => [...prev.filter(t => !t.isDemo), ...demo.targets]);
    setProductions(prev => [...prev.filter(p => !p.isDemo), ...demo.productions]);
    setQualities(prev => [...prev.filter(q => !q.isDemo), ...demo.qualities]);
    setIncidents(prev => [...prev.filter(i => !i.isDemo), ...demo.incidents]);
    setIsDemoMode(true);
  };

  const handleRemoveAllDemoData = () => {
    setTargets(prev => prev.filter(t => !t.isDemo));
    setProductions(prev => prev.filter(p => !p.isDemo));
    setQualities(prev => prev.filter(q => !q.isDemo));
    setIncidents(prev => prev.filter(i => !i.isDemo));
    setIsDemoMode(false);
  };

  // Production and Quality Entry Handlers
  const handleSaveProduction = (entry: Omit<ProductionEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>) => {
    const newRecord: ProductionEntryRecord = {
      ...entry,
      id: `p-${Date.now()}`,
      submissionId: `sub-p-${Date.now()}`,
      timestamp: Date.now(),
      revision: 1
    };
    setProductions(prev => [newRecord, ...prev]);
  };

  const handleSaveQuality = (entry: Omit<QualityEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>) => {
    const newRecord: QualityEntryRecord = {
      ...entry,
      id: `q-${Date.now()}`,
      submissionId: `sub-q-${Date.now()}`,
      timestamp: Date.now(),
      revision: 1
    };
    setQualities(prev => [newRecord, ...prev]);
  };

  const handleSaveSetup = (setup: LineTargetConfig) => {
    setTargets(prev => {
      const filtered = prev.filter(t => !(t.lineId === setup.lineId && t.date === setup.date));
      return [setup, ...filtered];
    });
  };

  const handleToggleLineActive = (lineId: string) => {
    setLines(prev =>
      prev.map(l => (l.id === lineId ? { ...l, isActive: !l.isActive } : l))
    );
  };

  const demoStats = {
    targetsCount: targets.filter(t => t.isDemo).length,
    productionsCount: productions.filter(p => p.isDemo).length,
    qualitiesCount: qualities.filter(q => q.isDemo).length,
    incidentsCount: incidents.filter(i => i.isDemo).length
  };

  // If user is not logged in, display the Login Interface
  if (!currentUser) {
    return (
      <LoginView
        users={users}
        onLogin={u => setCurrentUser(u)}
        onUpdateAdmin={newAdmin => {
          setUsers(prev => [newAdmin, ...prev.filter(u => u.username !== newAdmin.username)]);
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#F0F4F8] text-slate-900 font-sans flex flex-col">
      {/* Top Application Global Control Bar */}
      <div className="bg-[#061938] text-white px-3 py-1.5 flex items-center justify-between text-xs border-b border-blue-900/60 shadow-sm z-40">
        <div className="flex items-center gap-3">
          <div className="bg-white text-[#061938] font-black text-xs px-2 py-0.5 rounded shadow tracking-wider">
            NFL
          </div>
          <span className="font-extrabold tracking-wide hidden sm:inline">
            FACTORY SYSTEM
          </span>

          {/* User Role Switcher Pill */}
          <button
            onClick={() => setShowRoleSelector(true)}
            className="flex items-center gap-1.5 bg-blue-950/80 hover:bg-blue-900 px-2.5 py-1 rounded-lg border border-blue-800 transition"
          >
            <span className="text-[10px] text-blue-300">Role:</span>
            <span className="font-bold text-white">{currentUser.name}</span>
            <span className="bg-blue-600 text-[9px] px-1.5 py-0.2 rounded font-extrabold">
              {currentUser.role}
            </span>
          </button>

          {/* Logout Button to return to Login */}
          <button
            onClick={() => setCurrentUser(null)}
            className="text-[10px] bg-red-950/70 hover:bg-red-900 text-red-200 border border-red-800/80 px-2 py-1 rounded-lg font-bold transition flex items-center gap-1"
            title="Log out to Login screen"
          >
            <span>🚪</span>
            <span>Logout</span>
          </button>
        </div>

        {/* View Switcher: Mobile vs TV */}
        <div className="flex items-center gap-2">
          {hasUnmutedAlarmForRole && (
            <button
              onClick={() => setShowIncidentModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-black text-[11px] px-2.5 py-1 rounded-lg animate-bounce flex items-center gap-1 shadow"
            >
              <span>🚨 ALARM ACTIVE</span>
            </button>
          )}

          <div className="bg-blue-950 p-0.5 rounded-lg flex text-[11px] font-bold border border-blue-900">
            <button
              onClick={() => setActiveDeviceView('MOBILE')}
              className={`px-3 py-1 rounded-md transition ${
                activeDeviceView === 'MOBILE'
                  ? 'bg-[#1976D2] text-white shadow'
                  : 'text-blue-300 hover:text-white'
              }`}
            >
              📱 Mobile View
            </button>
            <button
              onClick={() => setActiveDeviceView('TV')}
              className={`px-3 py-1 rounded-md transition ${
                activeDeviceView === 'TV'
                  ? 'bg-[#1976D2] text-white shadow'
                  : 'text-blue-300 hover:text-white'
              }`}
            >
              📺 TV / Desktop (16:9)
            </button>
          </div>

          <button
            onClick={() => setShowAdminModal(true)}
            className="bg-blue-900/60 hover:bg-blue-800 text-blue-200 hover:text-white px-2 py-1 rounded-md text-xs font-bold transition"
            title="Admin Console & Demo Data Lifecycle"
          >
            ⚙️ Admin
          </button>
        </div>
      </div>

      {/* Main View Area */}
      <main className="flex-1">
        {activeDeviceView === 'MOBILE' ? (
          <div className="max-w-md mx-auto min-h-screen bg-white shadow-xl relative border-x border-slate-200">
            <MobileDashboard
              selectedFloor={selectedFloor}
              onSelectFloor={setSelectedFloor}
              selectedPeriod={selectedPeriod}
              onSelectPeriod={setSelectedPeriod}
              kpis={floorKpis}
              lineSummaries={lineSummaries}
              topDefects={topDefects}
              isDemoMode={isDemoMode}
              onToggleDemo={() => {
                if (isDemoMode) handleRemoveAllDemoData();
                else handleLoadDemoData();
              }}
              openIncidents={openIncidents}
              onOpenIncidentPanel={() => setShowIncidentModal(true)}
              onNavigateToReports={() => setShowReportsModal(true)}
            />

            {/* Mobile Bottom Navigation matching Image 1: Dashboard, Entry, Reports, More */}
            <div className="fixed bottom-0 max-w-md w-full bg-white border-t border-slate-200 flex justify-around py-2 z-40 shadow-lg text-[10px] font-bold text-slate-500">
              <button
                onClick={() => {}}
                className="flex flex-col items-center gap-0.5 text-[#1976D2]"
              >
                <span className="text-base">📊</span>
                <span>Dashboard</span>
              </button>

              <button
                onClick={() => setShowEntryModal(true)}
                className="flex flex-col items-center gap-0.5 hover:text-slate-800"
              >
                <span className="text-base">📝</span>
                <span>Entry</span>
              </button>

              <button
                onClick={() => setShowReportsModal(true)}
                className="flex flex-col items-center gap-0.5 hover:text-slate-800"
              >
                <span className="text-base">📄</span>
                <span>Reports</span>
              </button>

              <button
                onClick={() => setShowIncidentModal(true)}
                className="flex flex-col items-center gap-0.5 hover:text-slate-800 relative"
              >
                <span className="text-base">🚨</span>
                <span>Incidents</span>
                {openIncidents.length > 0 && (
                  <span className="absolute -top-1 -right-1 bg-red-600 text-white w-4 h-4 rounded-full flex items-center justify-center text-[9px] font-black">
                    {openIncidents.length}
                  </span>
                )}
              </button>
            </div>
          </div>
        ) : (
          <TvDesktopDashboard
            selectedFloor={selectedFloor}
            onSelectFloor={setSelectedFloor}
            selectedPeriod={selectedPeriod}
            onSelectPeriod={setSelectedPeriod}
            kpis={floorKpis}
            lineSummaries={lineSummaries}
            topDefects={topDefects}
            highestDhuLines={highestDhuLines}
            isDemoMode={isDemoMode}
            onToggleDemo={() => {
              if (isDemoMode) handleRemoveAllDemoData();
              else handleLoadDemoData();
            }}
            openIncidents={openIncidents}
            onOpenIncidentPanel={() => setShowIncidentModal(true)}
          />
        )}
      </main>

      {/* MODAL 1: ROLE SWITCHER */}
      {showRoleSelector && (
        <div className="fixed inset-0 bg-slate-950/70 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-5 space-y-4 shadow-2xl border border-slate-200">
            <div className="flex justify-between items-center border-b pb-2">
              <h3 className="text-sm font-black text-slate-900">
                Select Authorized Role & User
              </h3>
              <button
                onClick={() => setShowRoleSelector(false)}
                className="text-slate-400 hover:text-slate-700 font-bold"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-slate-600">
              Permissions and incident notifications are strictly routed based on your active role:
            </p>
            <div className="max-h-80 overflow-y-auto space-y-1.5">
              {users.map(u => (
                <div
                  key={u.id}
                  onClick={() => {
                    setCurrentUser(u);
                    setShowRoleSelector(false);
                  }}
                  className={`p-2.5 rounded-xl border flex items-center justify-between cursor-pointer transition ${
                    currentUser.id === u.id
                      ? 'border-blue-600 bg-blue-50/60 ring-1 ring-blue-500'
                      : 'border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <div>
                    <div className="text-xs font-black text-slate-900">{u.name}</div>
                    <div className="text-[10px] text-slate-500">
                      Role: <b className="text-blue-800">{u.role}</b> • Floor: {u.floor || 'All'}
                    </div>
                  </div>
                  {currentUser.id === u.id && (
                    <span className="text-blue-600 font-bold text-xs">✓ Active</span>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* MODAL 2: INCIDENT MANAGER & STOPWATCH */}
      {showIncidentModal && (
        <IncidentManager
          incidents={incidents}
          lines={lines}
          currentUserRole={currentUser.role}
          currentUserName={currentUser.name}
          onReportIncident={handleReportIncident}
          onMuteDeviceAlarm={handleMuteAlarm}
          onAcknowledgeIncident={handleAcknowledgeIncident}
          onResolveIncident={handleResolveIncident}
          onClose={() => setShowIncidentModal(false)}
        />
      )}

      {/* MODAL 3: DATA ENTRY FORMS */}
      {showEntryModal && (
        <EntryForms
          lines={lines}
          currentUserName={currentUser.name}
          onSaveProduction={handleSaveProduction}
          onSaveQuality={handleSaveQuality}
          onSaveSetup={handleSaveSetup}
          onClose={() => setShowEntryModal(false)}
        />
      )}

      {/* MODAL 4: REPORTS & CSV EXPORT */}
      {showReportsModal && (
        <ReportsView
          selectedFloor={selectedFloor}
          kpis={floorKpis}
          lineSummaries={lineSummaries}
          incidents={incidents}
          topDefects={topDefects}
          onClose={() => setShowReportsModal(false)}
        />
      )}

      {/* MODAL 5: ADMIN & DEMO LIFECYCLE */}
      {showAdminModal && (
        <AdminView
          lines={lines}
          users={users}
          demoStats={demoStats}
          onLoadDemoData={handleLoadDemoData}
          onRemoveAllDemoData={handleRemoveAllDemoData}
          onToggleLineActive={handleToggleLineActive}
          onClose={() => setShowAdminModal(false)}
        />
      )}
    </div>
  );
}
