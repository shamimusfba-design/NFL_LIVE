import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  FloorId,
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
  getBangladeshTodayIso,
  generateSyntheticDemoDataset
} from './data/masterData';
import { CalculationEngine } from './services/calculationEngine';
import {
  startLiveFirestoreListeners,
  pushProductionToCloud,
  pushQualityToCloud,
  pushIncidentToCloud,
  pushSetupToCloud,
  purgeAllOperationalCloudRecords
} from './services/firebaseLiveSync';
import { MobileDashboard } from './components/MobileDashboard';
import { TvDesktopDashboard } from './components/TvDesktopDashboard';
import { IncidentManager } from './components/IncidentManager';
import { EntryForms } from './components/EntryForms';
import { ReportsView } from './components/ReportsView';
import { AdminView } from './components/AdminView';
import { LoginView } from './components/LoginView';

const STORAGE_KEYS = {
  SAMPLE_CLEARED: 'nfl_desktop_sample_data_cleared',
  LAST_RESET_MS: 'nfl_desktop_last_operational_reset_ms',
  TARGETS: 'nfl_desktop_targets_v1',
  PRODUCTIONS: 'nfl_desktop_productions_v1',
  QUALITIES: 'nfl_desktop_qualities_v1',
  INCIDENTS: 'nfl_desktop_incidents_v1'
};

export default function App() {
  // Master state
  const [lines, setLines] = useState<LineMasterItem[]>(ALL_FACTORY_LINES);
  const [users, setUsers] = useState<UserAccount[]>(STANDARD_USERS);
  const [currentUser, setCurrentUser] = useState<UserAccount | null>(null);

  // Display mode: default to 'TV' (Widescreen Desktop) on PC screens >= 900px
  const [activeDeviceView, setActiveDeviceView] = useState<'MOBILE' | 'TV'>(() =>
    typeof window !== 'undefined' && window.innerWidth >= 900 ? 'TV' : 'MOBILE'
  );

  // Filters
  const [selectedFloor, setSelectedFloor] = useState<FloorId>('Sew 2');
  const [selectedPeriod, setSelectedPeriod] = useState<'Today' | 'Week' | 'Month'>('Today');
  const todayIso = useMemo(() => getBangladeshTodayIso(), []);

  // Datasets & Persistence flags
  const [isDemoMode, setIsDemoMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem(STORAGE_KEYS.SAMPLE_CLEARED) !== 'true';
    } catch {
      return true;
    }
  });
  const [lastResetMs, setLastResetMs] = useState<number>(() => {
    try {
      return Number(localStorage.getItem(STORAGE_KEYS.LAST_RESET_MS) || '0');
    } catch {
      return 0;
    }
  });

  const [targets, setTargets] = useState<LineTargetConfig[]>([]);
  const [productions, setProductions] = useState<ProductionEntryRecord[]>([]);
  const [qualities, setQualities] = useState<QualityEntryRecord[]>([]);
  const [incidents, setIncidents] = useState<IncidentRecord[]>([]);
  const [cloudConnected, setCloudConnected] = useState<boolean>(false);

  // Modals & Panels
  const [showIncidentModal, setShowIncidentModal] = useState(false);
  const [showEntryModal, setShowEntryModal] = useState(false);
  const [showReportsModal, setShowReportsModal] = useState(false);
  const [showAdminModal, setShowAdminModal] = useState(false);
  const [showRoleSelector, setShowRoleSelector] = useState(false);
  const [showInstallDesktopModal, setShowInstallDesktopModal] = useState(false);
  const [deferredInstallPrompt, setDeferredInstallPrompt] = useState<any>(null);

  // Audio Alarm Synth for Phone/Desktop Alarms (Web Audio API)
  const audioCtxRef = useRef<AudioContext | null>(null);
  const alarmIntervalRef = useRef<number | null>(null);

  // Capture PWA beforeinstallprompt event so Desktop Chrome/Edge users get 1-click native installation
  useEffect(() => {
    const handler = (e: any) => {
      e.preventDefault();
      setDeferredInstallPrompt(e);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  // Initialize local + demo data on first load (respecting sample_data_cleared)
  useEffect(() => {
    try {
      const sampleCleared = localStorage.getItem(STORAGE_KEYS.SAMPLE_CLEARED) === 'true';
      const savedTargets = JSON.parse(localStorage.getItem(STORAGE_KEYS.TARGETS) || '[]');
      const savedProds = JSON.parse(localStorage.getItem(STORAGE_KEYS.PRODUCTIONS) || '[]');
      const savedQuals = JSON.parse(localStorage.getItem(STORAGE_KEYS.QUALITIES) || '[]');
      const savedIncs = JSON.parse(localStorage.getItem(STORAGE_KEYS.INCIDENTS) || '[]');

      if (sampleCleared) {
        setTargets(savedTargets.filter((t: LineTargetConfig) => !t.isDemo));
        setProductions(savedProds.filter((p: ProductionEntryRecord) => !p.isDemo));
        setQualities(savedQuals.filter((q: QualityEntryRecord) => !q.isDemo));
        setIncidents(savedIncs.filter((i: IncidentRecord) => !i.isDemo));
        setIsDemoMode(false);
      } else if (
        savedTargets.length > 0 ||
        savedProds.length > 0 ||
        savedQuals.length > 0 ||
        savedIncs.length > 0
      ) {
        setTargets(savedTargets);
        setProductions(savedProds);
        setQualities(savedQuals);
        setIncidents(savedIncs);
      } else {
        const demo = generateSyntheticDemoDataset();
        setTargets(demo.targets);
        setProductions(demo.productions);
        setQualities(demo.qualities);
        setIncidents(demo.incidents);
      }
    } catch {
      const demo = generateSyntheticDemoDataset();
      setTargets(demo.targets);
      setProductions(demo.productions);
      setQualities(demo.qualities);
      setIncidents(demo.incidents);
    }
  }, []);

  // Persist operational state changes to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.TARGETS, JSON.stringify(targets));
    } catch {}
  }, [targets]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.PRODUCTIONS, JSON.stringify(productions));
    } catch {}
  }, [productions]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.QUALITIES, JSON.stringify(qualities));
    } catch {}
  }, [qualities]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.INCIDENTS, JSON.stringify(incidents));
    } catch {}
  }, [incidents]);

  // Connect real-time Firestore listeners (shared with Android Mobile & TV apps)
  useEffect(() => {
    const unsubscribe = startLiveFirestoreListeners({
      onConnectionChange: connected => setCloudConnected(connected),
      onProductionUpdate: remoteProds => {
        const validRemote = remoteProds.filter(r => r.timestamp > lastResetMs);
        setProductions(prev => {
          const map = new Map<string, ProductionEntryRecord>();
          prev.forEach(p => map.set(p.id, p));
          validRemote.forEach(r => map.set(r.id, r));
          return Array.from(map.values()).sort((a, b) => b.timestamp - a.timestamp);
        });
      },
      onQualityUpdate: remoteQuals => {
        const validRemote = remoteQuals.filter(r => r.timestamp > lastResetMs);
        setQualities(prev => {
          const map = new Map<string, QualityEntryRecord>();
          prev.forEach(q => map.set(q.id, q));
          validRemote.forEach(r => map.set(r.id, r));
          return Array.from(map.values()).sort((a, b) => b.timestamp - a.timestamp);
        });
      },
      onIncidentUpdate: remoteIncs => {
        const validRemote = remoteIncs.filter(r => r.startTimeMillis > lastResetMs);
        setIncidents(prev => {
          const map = new Map<string, IncidentRecord>();
          prev.forEach(i => map.set(i.id, i));
          validRemote.forEach(r => {
            const existing = map.get(r.id);
            map.set(r.id, {
              ...r,
              isMutedOnCurrentDevice: existing?.isMutedOnCurrentDevice ?? false
            });
          });
          return Array.from(map.values()).sort((a, b) => b.startTimeMillis - a.startTimeMillis);
        });
      },
      onSetupUpdate: remoteSetups => {
        if (remoteSetups.length === 0) return;
        setTargets(prev => {
          const map = new Map<string, LineTargetConfig>();
          prev.forEach(t => map.set(`${t.lineId}_${t.date}`, t));
          remoteSetups.forEach(r => map.set(`${r.lineId}_${r.date}`, r));
          return Array.from(map.values());
        });
      }
    });
    return () => unsubscribe();
  }, [lastResetMs]);

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
      osc.frequency.setValueAtTime(880, ctx.currentTime);
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

  // Target-based filtering using live Bangladesh date
  const targetedConfigs = useMemo(() => {
    return CalculationEngine.getTargetedLines(
      lines,
      targets,
      selectedFloor,
      todayIso,
      !isDemoMode
    );
  }, [lines, targets, selectedFloor, todayIso, isDemoMode]);

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
    pushIncidentToCloud(inc);
  };

  const handleMuteAlarm = (id: string) => {
    setIncidents(prev =>
      prev.map(i => (i.id === id ? { ...i, isMutedOnCurrentDevice: true } : i))
    );
  };

  const handleAcknowledgeIncident = (id: string, ackBy: string) => {
    setIncidents(prev =>
      prev.map(i => {
        if (i.id !== id) return i;
        const updated: IncidentRecord = {
          ...i,
          status: 'ACKNOWLEDGED',
          acknowledgedBy: ackBy,
          acknowledgedTimeMillis: Date.now()
        };
        pushIncidentToCloud(updated);
        return updated;
      })
    );
  };

  const handleResolveIncident = (id: string, resBy: string, note: string) => {
    setIncidents(prev =>
      prev.map(i => {
        if (i.id !== id) return i;
        const updated: IncidentRecord = {
          ...i,
          status: 'RESOLVED',
          resolvedBy: resBy,
          resolutionNote: note,
          endTimeMillis: Date.now()
        };
        pushIncidentToCloud(updated);
        return updated;
      })
    );
  };

  // Demo Data & Factory Reset Lifecycle
  const handleLoadDemoData = () => {
    const demo = generateSyntheticDemoDataset();
    setTargets(prev => [...prev.filter(t => !t.isDemo), ...demo.targets]);
    setProductions(prev => [...prev.filter(p => !p.isDemo), ...demo.productions]);
    setQualities(prev => [...prev.filter(q => !q.isDemo), ...demo.qualities]);
    setIncidents(prev => [...prev.filter(i => !i.isDemo), ...demo.incidents]);
    setIsDemoMode(true);
    try {
      localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'false');
    } catch {}
  };

  const handleRemoveAllDemoData = () => {
    setTargets(prev => prev.filter(t => !t.isDemo));
    setProductions(prev => prev.filter(p => !p.isDemo));
    setQualities(prev => prev.filter(q => !q.isDemo));
    setIncidents(prev => prev.filter(i => !i.isDemo));
    setIsDemoMode(false);
    try {
      localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'true');
    } catch {}
  };

  const handleFactoryResetAll = async () => {
    const resetTime = Date.now();
    setLastResetMs(resetTime);
    setTargets([]);
    setProductions([]);
    setQualities([]);
    setIncidents([]);
    setIsDemoMode(false);
    try {
      localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'true');
      localStorage.setItem(STORAGE_KEYS.LAST_RESET_MS, String(resetTime));
      localStorage.setItem(STORAGE_KEYS.TARGETS, '[]');
      localStorage.setItem(STORAGE_KEYS.PRODUCTIONS, '[]');
      localStorage.setItem(STORAGE_KEYS.QUALITIES, '[]');
      localStorage.setItem(STORAGE_KEYS.INCIDENTS, '[]');
    } catch {}
    return await purgeAllOperationalCloudRecords();
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
    pushProductionToCloud(newRecord);
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
    pushQualityToCloud(newRecord);
  };

  const handleSaveSetup = (setup: LineTargetConfig) => {
    setTargets(prev => {
      const filtered = prev.filter(t => !(t.lineId === setup.lineId && t.date === setup.date));
      return [setup, ...filtered];
    });
    pushSetupToCloud(setup);
  };

  const handleToggleLineActive = (lineId: string) => {
    setLines(prev =>
      prev.map(l => (l.id === lineId ? { ...l, isActive: !l.isActive } : l))
    );
  };

  const handleTriggerInstallDesktop = async () => {
    if (deferredInstallPrompt) {
      deferredInstallPrompt.prompt();
      await deferredInstallPrompt.userChoice;
      setDeferredInstallPrompt(null);
    } else {
      setShowInstallDesktopModal(true);
    }
  };

  const demoStats = {
    targetsCount: targets.filter(t => t.isDemo).length,
    productionsCount: productions.filter(p => p.isDemo).length,
    qualitiesCount: qualities.filter(q => q.isDemo).length,
    incidentsCount: incidents.filter(i => i.isDemo).length
  };

  const totalStats = {
    targetsCount: targets.length,
    productionsCount: productions.length,
    qualitiesCount: qualities.length,
    incidentsCount: incidents.length
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
      {/* Top Application Global Desktop & Mobile Control Bar */}
      <div className="bg-[#061938] text-white px-3 py-1.5 flex flex-wrap items-center justify-between gap-2 text-xs border-b border-blue-900/60 shadow-sm z-40">
        <div className="flex items-center gap-2.5 flex-wrap">
          <div className="bg-white text-[#061938] font-black text-xs px-2 py-0.5 rounded shadow tracking-wider">
            NFL
          </div>
          <span className="font-extrabold tracking-wide hidden sm:inline">
            DESKTOP & TV SYSTEM
          </span>

          {/* Cloud Sync Badge */}
          <span
            className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
              cloudConnected
                ? 'bg-emerald-950/80 text-emerald-300 border-emerald-700'
                : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
            title="Real-time Firebase Firestore Sync Status"
          >
            {cloudConnected ? '☁️ Cloud Live' : '💾 Local + Cloud Ready'}
          </span>

          {/* User Role Switcher Pill */}
          <button
            onClick={() => setShowRoleSelector(true)}
            className="flex items-center gap-1.5 bg-blue-950/80 hover:bg-blue-900 px-2.5 py-1 rounded-lg border border-blue-800 transition"
          >
            <span className="text-[10px] text-blue-300">User:</span>
            <span className="font-bold text-white">{currentUser.name}</span>
            <span className="bg-blue-600 text-[9px] px-1.5 py-0.2 rounded font-extrabold">
              {currentUser.role}
            </span>
          </button>

          {/* Logout Button */}
          <button
            onClick={() => setCurrentUser(null)}
            className="text-[10px] bg-red-950/70 hover:bg-red-900 text-red-200 border border-red-800/80 px-2 py-1 rounded-lg font-bold transition flex items-center gap-1"
            title="Log out to Login screen"
          >
            <span>🚪</span>
            <span>Logout</span>
          </button>
        </div>

        {/* Desktop Quick Action Toolbar + View Switcher */}
        <div className="flex items-center gap-1.5 flex-wrap">
          {hasUnmutedAlarmForRole && (
            <button
              onClick={() => setShowIncidentModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-black text-[11px] px-2.5 py-1 rounded-lg animate-bounce flex items-center gap-1 shadow"
            >
              <span>🚨 ALARM ACTIVE</span>
            </button>
          )}

          {/* Direct Desktop Action Buttons (Always accessible in TV/Desktop 16:9 View) */}
          <button
            onClick={() => setShowEntryModal(true)}
            className="bg-emerald-700 hover:bg-emerald-600 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
          >
            📝 Data Entry
          </button>

          <button
            onClick={() => setShowReportsModal(true)}
            className="bg-indigo-700 hover:bg-indigo-600 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
          >
            📊 Reports / Excel
          </button>

          <button
            onClick={() => setShowIncidentModal(true)}
            className="bg-amber-700 hover:bg-amber-600 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm relative"
          >
            🚨 Incidents {openIncidents.length > 0 ? `(${openIncidents.length})` : ''}
          </button>

          <button
            onClick={() => setShowAdminModal(true)}
            className="bg-blue-800 hover:bg-blue-700 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
            title="Admin Console, Clear Demo Data & Factory Reset"
          >
            ⚙️ Admin & Reset
          </button>

          <button
            onClick={handleTriggerInstallDesktop}
            className="bg-cyan-700 hover:bg-cyan-600 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
            title="Install Free Desktop App on Windows / Mac / Linux"
          >
            🖥️ Install Desktop App
          </button>

          <div className="bg-blue-950 p-0.5 rounded-lg flex text-[11px] font-bold border border-blue-900">
            <button
              onClick={() => setActiveDeviceView('TV')}
              className={`px-2.5 py-1 rounded-md transition ${
                activeDeviceView === 'TV'
                  ? 'bg-[#1976D2] text-white shadow'
                  : 'text-blue-300 hover:text-white'
              }`}
            >
              🖥️ Desktop / TV (16:9)
            </button>
            <button
              onClick={() => setActiveDeviceView('MOBILE')}
              className={`px-2.5 py-1 rounded-md transition ${
                activeDeviceView === 'MOBILE'
                  ? 'bg-[#1976D2] text-white shadow'
                  : 'text-blue-300 hover:text-white'
              }`}
            >
              📱 Mobile View
            </button>
          </div>
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

            {/* Mobile Bottom Navigation */}
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
                      ID: <b>{u.username}</b> • Role: <b className="text-blue-800">{u.role}</b> • Floor: {u.floor || 'All'}
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

      {/* MODAL 5: ADMIN, CLEAR DEMO DATA & FACTORY RESET */}
      {showAdminModal && (
        <AdminView
          lines={lines}
          users={users}
          demoStats={demoStats}
          totalStats={totalStats}
          cloudConnected={cloudConnected}
          onLoadDemoData={handleLoadDemoData}
          onRemoveAllDemoData={handleRemoveAllDemoData}
          onFactoryResetAll={handleFactoryResetAll}
          onToggleLineActive={handleToggleLineActive}
          onClose={() => setShowAdminModal(false)}
        />
      )}

      {/* MODAL 6: FREE DESKTOP PWA INSTALLATION GUIDE */}
      {showInstallDesktopModal && (
        <div className="fixed inset-0 bg-slate-950/70 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-5 space-y-4 shadow-2xl border border-slate-200">
            <div className="flex justify-between items-center border-b pb-2">
              <h3 className="text-base font-black text-slate-900">
                🖥️ Install Free NFL LIVE Desktop App (Windows / Mac)
              </h3>
              <button
                onClick={() => setShowInstallDesktopModal(false)}
                className="text-slate-400 hover:text-slate-700 font-bold"
              >
                ✕
              </button>
            </div>
            <div className="space-y-3 text-xs text-slate-700 leading-relaxed">
              <div className="p-3 rounded-xl bg-blue-50 border border-blue-200 text-blue-900">
                <b>100% Free Desktop Options Included:</b>
                <ul className="list-disc pl-5 mt-1 space-y-1">
                  <li>
                    <b>Option A (Instant Standalone PC File):</b> Open{' '}
                    <code className="bg-white px-1.5 py-0.5 rounded border font-mono">
                      NFL_LIVE_Desktop_App.html
                    </code>{' '}
                    on any Windows or Mac computer in Chrome or Edge. No server or installation needed!
                  </li>
                  <li>
                    <b>Option B (Install as Native Desktop App Icon):</b> In Google Chrome or Microsoft Edge on your PC, click the{' '}
                    <b>Install App icon (⊕)</b> on the right side of the address bar (or menu <b>⋮ → Save and share → Install page as app...</b>) to pin <b>NFL LIVE Desktop</b> directly to your Windows Taskbar and Desktop.
                  </li>
                  <li>
                    <b>Option C (Free Cloud Hosting):</b> Run{' '}
                    <code className="bg-white px-1.5 py-0.5 rounded border font-mono">
                      npm run build
                    </code>{' '}
                    inside <code className="font-mono">web-dashboard-and-mobile/</code> and deploy to <b>Firebase Hosting</b>, <b>GitHub Pages</b>, or <b>Vercel</b> for $0/month.
                  </li>
                </ul>
              </div>
              <p className="text-[11px] text-slate-500">
                All Desktop versions automatically sync in real time with the same Firebase Firestore database (<code className="font-mono">nfl-live-72213</code>) used by your Android Phones and Smart TVs.
              </p>
            </div>
            <div className="flex justify-end">
              <button
                onClick={() => setShowInstallDesktopModal(false)}
                className="bg-[#0A2558] hover:bg-blue-900 text-white font-bold text-xs px-4 py-2 rounded-lg"
              >
                Got It
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
