import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  FloorId,
  PeriodId,
  UserAccount,
  LineTargetConfig,
  MonthlyTargetConfig,
  WeeklyQualityRecord,
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
  signOutWebCloudSession,
  pushProductionToCloud,
  pushQualityToCloud,
  pushIncidentToCloud,
  pushSetupToCloud,
  pushWeeklyQualityToCloud,
  pushMonthlyTargetToCloud,
  softDeleteCloudRecord,
  canonicalProdDocId,
  canonicalQaDocId,
  purgeAllOperationalCloudRecords,
  approveCloudUserRegistration,
  rejectCloudUserRegistration,
  approveCloudDeviceOrTvRequest
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
  INCIDENTS: 'nfl_desktop_incidents_v1',
  WEEKLY_QUALITY: 'nfl_desktop_weekly_quality_v1',
  MONTHLY_TARGETS: 'nfl_desktop_monthly_targets_v1'
};

export default function App() {
  const [lines, setLines] = useState<LineMasterItem[]>(ALL_FACTORY_LINES);
  const [users, setUsers] = useState<UserAccount[]>(STANDARD_USERS);
  const [currentUser, setCurrentUser] = useState<UserAccount | null>(null);

  const [activeDeviceView, setActiveDeviceView] = useState<'MOBILE' | 'TV'>(() =>
    typeof window !== 'undefined' && window.innerWidth >= 900 ? 'TV' : 'MOBILE'
  );

  const [selectedFloor, setSelectedFloor] = useState<FloorId>('Sew 2');
  const [selectedPeriod, setSelectedPeriod] = useState<PeriodId>('Today');
  const todayIso = useMemo(() => getBangladeshTodayIso(), []);

  const [isDemoMode, setIsDemoMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem(STORAGE_KEYS.SAMPLE_CLEARED) === 'false';
    } catch {
      return false;
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
  const [weeklyQuality, setWeeklyQuality] = useState<WeeklyQualityRecord[]>([]);
  const [monthlyTargets, setMonthlyTargets] = useState<MonthlyTargetConfig[]>([]);
  const [cloudConnected, setCloudConnected] = useState<boolean>(false);

  const [showIncidentModal, setShowIncidentModal] = useState(false);
  const [showEntryModal, setShowEntryModal] = useState(false);
  const [showReportsModal, setShowReportsModal] = useState(false);
  const [showAdminModal, setShowAdminModal] = useState(false);
  const [showRoleSelector, setShowRoleSelector] = useState(false);
  const [isAuthenticatedAdmin, setIsAuthenticatedAdmin] = useState(false);
  const [showInstallDesktopModal, setShowInstallDesktopModal] = useState(false);
  const [deferredInstallPrompt, setDeferredInstallPrompt] = useState<any>(null);

  const audioCtxRef = useRef<AudioContext | null>(null);
  const alarmIntervalRef = useRef<number | null>(null);

  useEffect(() => {
    const handler = (e: any) => {
      e.preventDefault();
      setDeferredInstallPrompt(e);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  useEffect(() => {
    try {
      const sampleCleared = localStorage.getItem(STORAGE_KEYS.SAMPLE_CLEARED) !== 'false';
      const savedTargets = JSON.parse(localStorage.getItem(STORAGE_KEYS.TARGETS) || '[]');
      const savedProds = JSON.parse(localStorage.getItem(STORAGE_KEYS.PRODUCTIONS) || '[]');
      const savedQuals = JSON.parse(localStorage.getItem(STORAGE_KEYS.QUALITIES) || '[]');
      const savedIncs = JSON.parse(localStorage.getItem(STORAGE_KEYS.INCIDENTS) || '[]');
      const savedWeekly = JSON.parse(localStorage.getItem(STORAGE_KEYS.WEEKLY_QUALITY) || '[]');
      const savedMonthly = JSON.parse(localStorage.getItem(STORAGE_KEYS.MONTHLY_TARGETS) || '[]');

      if (sampleCleared) {
        setTargets(savedTargets.filter((t: LineTargetConfig) => !t.isDemo));
        setProductions(savedProds.filter((p: ProductionEntryRecord) => !p.isDemo));
        setQualities(savedQuals.filter((q: QualityEntryRecord) => !q.isDemo));
        setIncidents(savedIncs.filter((i: IncidentRecord) => !i.isDemo));
        setWeeklyQuality(savedWeekly.filter((w: WeeklyQualityRecord) => !w.isDemo));
        setMonthlyTargets(savedMonthly);
        setIsDemoMode(false);
      } else if (
        savedTargets.length > 0 ||
        savedProds.length > 0 ||
        savedQuals.length > 0 ||
        savedIncs.length > 0
      ) {
        const demo = generateSyntheticDemoDataset();
        setTargets(savedTargets);
        setProductions(savedProds);
        setQualities(savedQuals);
        setIncidents(savedIncs);
        setWeeklyQuality(savedWeekly.length > 0 ? savedWeekly : demo.weeklyQuality);
        setMonthlyTargets(savedMonthly.length > 0 ? savedMonthly : demo.monthlyTargets);
      } else {
        const demo = generateSyntheticDemoDataset();
        setTargets(demo.targets);
        setProductions(demo.productions);
        setQualities(demo.qualities);
        setIncidents(demo.incidents);
        setWeeklyQuality(demo.weeklyQuality);
        setMonthlyTargets(demo.monthlyTargets);
      }
    } catch {
      setTargets([]);
      setProductions([]);
      setQualities([]);
      setIncidents([]);
      setWeeklyQuality([]);
      setMonthlyTargets([]);
    }
  }, []);

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

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.WEEKLY_QUALITY, JSON.stringify(weeklyQuality));
    } catch {}
  }, [weeklyQuality]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.MONTHLY_TARGETS, JSON.stringify(monthlyTargets));
    } catch {}
  }, [monthlyTargets]);

  // Connect real-time Firestore listeners (shared with Android Mobile & TV apps). Only after a person has signed in.
  useEffect(() => {
    if (!currentUser) return;
    const unsubscribe = startLiveFirestoreListeners({
      onStatusChanged: status => setCloudConnected(status.startsWith('Live')),
      onConnectionChange: connected => setCloudConnected(connected),
      onProductionUpdated: remoteProds => {
        const validRemote = remoteProds.filter(r => r.timestamp > lastResetMs);
        setProductions(prev => {
          const map = new Map<string, ProductionEntryRecord>();
          prev.forEach(p => map.set(p.id, p));
          validRemote.forEach(r => map.set(r.id, r));
          return Array.from(map.values()).sort((a, b) => b.timestamp - a.timestamp);
        });
      },
      onQualityUpdated: remoteQuals => {
        const validRemote = remoteQuals.filter(r => r.timestamp > lastResetMs);
        setQualities(prev => {
          const map = new Map<string, QualityEntryRecord>();
          prev.forEach(q => map.set(q.id, q));
          validRemote.forEach(r => map.set(r.id, r));
          return Array.from(map.values()).sort((a, b) => b.timestamp - a.timestamp);
        });
      },
      onIncidentsUpdated: remoteIncs => {
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
      onSetupsUpdated: remoteSetups => {
        if (remoteSetups.length === 0) return;
        setTargets(prev => {
          const map = new Map<string, LineTargetConfig>();
          prev.forEach(t => map.set(`${t.lineId}_${t.date}`, t));
          remoteSetups.forEach(r => map.set(`${r.lineId}_${r.date}`, r));
          return Array.from(map.values());
        });
      },
      onWeeklyQualityUpdate: remoteWeekly => {
        if (remoteWeekly.length === 0) return;
        setWeeklyQuality(prev => {
          const map = new Map<string, WeeklyQualityRecord>();
          prev.forEach(w => map.set(w.weekKey, w));
          remoteWeekly.forEach(r => map.set(r.weekKey, r));
          return Array.from(map.values()).sort((a, b) => a.weekKey.localeCompare(b.weekKey));
        });
      },
      onMonthlyTargetsUpdate: remoteMonthly => {
        if (remoteMonthly.length === 0) return;
        setMonthlyTargets(prev => {
          const map = new Map<string, MonthlyTargetConfig>();
          prev.forEach(m => map.set(m.id, m));
          remoteMonthly.forEach(r => map.set(r.id, r));
          return Array.from(map.values());
        });
      },
      onUsersUpdated: remoteUsers => {
        if (remoteUsers.length === 0) return;
        setUsers(prev => {
          const map = new Map<string, UserAccount>();
          prev.forEach(u => map.set(u.id.toLowerCase(), u));
          remoteUsers.forEach(r => map.set(r.id.toLowerCase(), r));
          return Array.from(map.values());
        });
      }
    });
    return () => unsubscribe();
  }, [lastResetMs, currentUser?.id]);

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
    } catch {}
  };

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

  const targetedConfigs = useMemo(() => {
    return CalculationEngine.getTargetedLines(
      lines,
      targets,
      selectedFloor,
      todayIso,
      !isDemoMode
    );
  }, [lines, targets, selectedFloor, todayIso, isDemoMode]);

  const lineSummaries = useMemo(() => {
    return CalculationEngine.computeLineSummaries(
      targetedConfigs,
      productions,
      qualities,
      incidents,
      todayIso,
      selectedPeriod
    );
  }, [targetedConfigs, productions, qualities, incidents, todayIso, selectedPeriod]);

  const floorKpis = useMemo(() => {
    return CalculationEngine.computeFloorKpis(
      lineSummaries,
      incidents,
      monthlyTargets,
      selectedFloor
    );
  }, [lineSummaries, incidents, monthlyTargets, selectedFloor]);

  const floorBenchmarks = useMemo(() => {
    const scopes: Array<{ id: FloorId; label: string }> = [
      { id: 'Sew 1', label: 'Sew 1' },
      { id: 'Sew 2', label: 'Sew 2' },
      { id: 'Sew 3', label: 'Sew 3' },
      { id: 'All', label: 'Factory' }
    ];
    return scopes.map(sc => {
      const cfgs = CalculationEngine.getTargetedLines(lines, targets, sc.id, todayIso, !isDemoMode);
      const sums = CalculationEngine.computeLineSummaries(
        cfgs,
        productions,
        qualities,
        incidents,
        todayIso,
        selectedPeriod
      );
      const k = CalculationEngine.computeFloorKpis(sums, incidents, monthlyTargets, sc.id);
      return {
        floor: sc.label,
        todayEff: k.todayEfficiencyPct,
        monthEff: k.monthlyEfficiencyPct
      };
    });
  }, [lines, targets, productions, qualities, incidents, monthlyTargets, todayIso, selectedPeriod, isDemoMode]);

  const hourlySeries = useMemo(() => {
    return CalculationEngine.computeHourlySeries(
      lineSummaries,
      productions,
      qualities,
      selectedFloor,
      todayIso,
      selectedPeriod
    );
  }, [lineSummaries, productions, qualities, selectedFloor, todayIso, selectedPeriod]);

  const topDefects = useMemo(() => {
    return CalculationEngine.computeTopDefects(qualities, selectedFloor, todayIso, selectedPeriod);
  }, [qualities, selectedFloor, todayIso, selectedPeriod]);

  const highestDhuLines = useMemo(() => {
    return CalculationEngine.computeHighestDhuLines(lineSummaries);
  }, [lineSummaries]);

  const openIncidents = useMemo(() => {
    return incidents.filter(
      i => i.status !== 'RESOLVED' && (selectedFloor === 'All' || i.floor === selectedFloor)
    );
  }, [incidents, selectedFloor]);

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

  const handleLoadDemoData = () => {
    const demo = generateSyntheticDemoDataset();
    setTargets(prev => [...prev.filter(t => !t.isDemo), ...demo.targets]);
    setProductions(prev => [...prev.filter(p => !p.isDemo), ...demo.productions]);
    setQualities(prev => [...prev.filter(q => !q.isDemo), ...demo.qualities]);
    setIncidents(prev => [...prev.filter(i => !i.isDemo), ...demo.incidents]);
    setWeeklyQuality(prev => [...prev.filter(w => !w.isDemo), ...demo.weeklyQuality]);
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
    setWeeklyQuality(prev => prev.filter(w => !w.isDemo));
    setIsDemoMode(false);
    try {
      localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'true');
    } catch {}
  };

  const handleFactoryResetAll = async (): Promise<number> => {
    // Destructive: Admin only, and the person must type RESET. Deletes production data for every device.
    if (currentUser?.role !== 'ADMIN') return -1;
    if ((window.prompt('This deletes ALL production, quality, incident and setup records from the cloud for every phone and TV.\nType RESET to continue.') || '').trim() !== 'RESET') return -1;
    const resetTime = Date.now();
    setLastResetMs(resetTime);
    setTargets([]);
    setProductions([]);
    setQualities([]);
    setIncidents([]);
    setWeeklyQuality([]);
    setIsDemoMode(false);
    try {
      localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'true');
      localStorage.setItem(STORAGE_KEYS.LAST_RESET_MS, String(resetTime));
      localStorage.setItem(STORAGE_KEYS.TARGETS, '[]');
      localStorage.setItem(STORAGE_KEYS.PRODUCTIONS, '[]');
      localStorage.setItem(STORAGE_KEYS.QUALITIES, '[]');
      localStorage.setItem(STORAGE_KEYS.INCIDENTS, '[]');
      localStorage.setItem(STORAGE_KEYS.WEEKLY_QUALITY, '[]');
    } catch {}
    return await purgeAllOperationalCloudRecords();
  };

  const handleSaveProduction = (
    entry: Omit<ProductionEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>
  ) => {
    const newRecord: ProductionEntryRecord = {
      ...entry,
      id: `p-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      submissionId: `sub-p-${Date.now()}`,
      timestamp: Date.now(),
      revision: 1
    };
    setProductions(prev => [
      newRecord,
      ...prev.filter(
        p => !(p.lineId === newRecord.lineId && p.date === newRecord.date && p.hourSlot === newRecord.hourSlot)
      )
    ]);
    pushProductionToCloud(newRecord);
  };

  const handleSaveQuality = (
    entry: Omit<QualityEntryRecord, 'id' | 'submissionId' | 'timestamp' | 'revision'>
  ) => {
    const newRecord: QualityEntryRecord = {
      ...entry,
      id: `q-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      submissionId: `sub-q-${Date.now()}`,
      timestamp: Date.now(),
      revision: 1
    };
    setQualities(prev => [
      newRecord,
      ...prev.filter(
        q => !(q.lineId === newRecord.lineId && q.date === newRecord.date && q.hourSlot === newRecord.hourSlot)
      )
    ]);
    pushQualityToCloud(newRecord);
  };

  const handleSaveWeeklyQuality = (record: WeeklyQualityRecord) => {
    setWeeklyQuality(prev => {
      const filtered = prev.filter(w => w.weekKey !== record.weekKey);
      return [...filtered, record].sort((a, b) => a.weekKey.localeCompare(b.weekKey));
    });
    pushWeeklyQualityToCloud(record);
  };

  const handleSaveMonthlyTarget = (target: MonthlyTargetConfig) => {
    setMonthlyTargets(prev => {
      const filtered = prev.filter(m => m.id !== target.id);
      return [...filtered, target];
    });
    pushMonthlyTargetToCloud(target);
  };

  const handleDeleteProduction = (id: string, reason: string) => {
    const rec = productions.find(p => p.id === id);
    setProductions(prev => prev.filter(p => p.id !== id));
    if (rec && currentUser) {
      softDeleteCloudRecord(
        'production_entries',
        canonicalProdDocId(rec),
        currentUser.name,
        reason
      );
    }
  };

  const handleDeleteQuality = (id: string, reason: string) => {
    const rec = qualities.find(q => q.id === id);
    setQualities(prev => prev.filter(q => q.id !== id));
    if (rec && currentUser) {
      softDeleteCloudRecord('quality_entries', canonicalQaDocId(rec), currentUser.name, reason);
    }
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

  const liveStats = {
    targetsCount: targets.filter(t => !t.isDemo).length,
    productionsCount: productions.filter(p => !p.isDemo).length,
    qualitiesCount: qualities.filter(q => !q.isDemo).length,
    incidentsCount: incidents.filter(i => !i.isDemo).length
  };

  if (!currentUser) {
    return (
      <LoginView
        users={users}
        onLogin={u => {
          setCurrentUser(u);
          setIsAuthenticatedAdmin(u.role === 'ADMIN');
        }}
        onUpdateAdmin={newAdmin => {
          setUsers(prev => [newAdmin, ...prev.filter(u => u.username !== newAdmin.username)]);
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#0A1522] text-slate-900 font-sans flex flex-col">
      {/* Top Application Global Desktop & Mobile Control Bar */}
      <div className="bg-[#06111D] text-white px-3 py-1.5 flex flex-wrap items-center justify-between gap-2 text-xs border-b border-[#1B2E45] shadow-sm z-40">
        <div className="flex items-center gap-2.5 flex-wrap">
          <div className="bg-white text-[#061938] font-black text-xs px-2 py-0.5 rounded shadow tracking-wider">
            NFL
          </div>
          <span className="font-extrabold tracking-wide hidden sm:inline">
            NFL LIVE WEB &amp; TV SYSTEM
          </span>

          <span
            className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
              cloudConnected
                ? 'bg-emerald-950/80 text-emerald-300 border-emerald-700'
                : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
          >
            {cloudConnected ? '☁️ Cloud Live' : '💾 Local + Cloud Ready'}
          </span>

          <button
            onClick={() => {
              if (isAuthenticatedAdmin) {
                setShowRoleSelector(true);
              }
            }}
            title={
              isAuthenticatedAdmin
                ? 'Admin Role Preview Switcher'
                : `Logged in as ${currentUser.name} (${currentUser.role})`
            }
            className={`flex items-center gap-1.5 bg-blue-950/80 px-2.5 py-1 rounded-lg border border-blue-800 transition ${
              isAuthenticatedAdmin ? 'hover:bg-blue-900 cursor-pointer' : 'cursor-default'
            }`}
          >
            <span className="text-[10px] text-blue-300">User:</span>
            <span className="font-bold text-white">{currentUser.name}</span>
            <span className="bg-blue-600 text-[9px] px-1.5 py-0.2 rounded font-extrabold">
              {currentUser.role}
            </span>
          </button>

          <button
            onClick={() => {
              void signOutWebCloudSession();
              setCurrentUser(null);
              setIsAuthenticatedAdmin(false);
              setShowRoleSelector(false);
            }}
            className="text-[10px] bg-red-950/70 hover:bg-red-900 text-red-200 border border-red-800/80 px-2 py-1 rounded-lg font-bold transition flex items-center gap-1"
          >
            <span>🚪</span>
            <span>Logout</span>
          </button>
        </div>

        <div className="flex items-center gap-1.5 flex-wrap">
          {hasUnmutedAlarmForRole && (
            <button
              onClick={() => setShowIncidentModal(true)}
              className="bg-red-600 hover:bg-red-700 text-white font-black text-[11px] px-2.5 py-1 rounded-lg animate-bounce flex items-center gap-1 shadow"
            >
              <span>🚨 ALARM ACTIVE</span>
            </button>
          )}

          <button
            onClick={() => setShowEntryModal(true)}
            className="bg-emerald-700 hover:bg-emerald-600 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
          >
            📝 Data Entry (12h AM/PM &amp; Audit)
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

          {currentUser?.role === 'ADMIN' && (
            <button
              onClick={() => setShowAdminModal(true)}
              className="bg-blue-800 hover:bg-blue-700 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
              title="Admin Console, Clear Demo Data & Factory Reset"
            >
              ⚙️ Admin &amp; Reset
            </button>
          )}

          <button
            onClick={handleTriggerInstallDesktop}
            className="bg-cyan-700 hover:bg-cyan-600 text-white px-2.5 py-1 rounded-md text-[11px] font-bold transition shadow-sm"
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
              🖥️ TV Wallboard (16:9)
            </button>
            <button
              onClick={() => setActiveDeviceView('MOBILE')}
              className={`px-2.5 py-1 rounded-md transition ${
                activeDeviceView === 'MOBILE'
                  ? 'bg-[#1976D2] text-white shadow'
                  : 'text-blue-300 hover:text-white'
              }`}
            >
              📱 Compact View
            </button>
          </div>
        </div>
      </div>

      {/* Main View Area */}
      <main className="flex-1">
        {activeDeviceView === 'MOBILE' ? (
          <div className="max-w-lg mx-auto min-h-screen bg-[#0A1522] shadow-xl relative border-x border-[#1B2E45]">
            <MobileDashboard
              selectedFloor={selectedFloor}
              onSelectFloor={setSelectedFloor}
              selectedPeriod={selectedPeriod}
              onSelectPeriod={setSelectedPeriod}
              kpis={floorKpis}
              floorBenchmarks={floorBenchmarks}
              lineSummaries={lineSummaries}
              hourlySeries={hourlySeries}
              weeklyQuality={weeklyQuality}
              productions={productions}
              qualities={qualities}
              topDefects={topDefects}
              highestDhuLines={highestDhuLines}
              isDemoMode={isDemoMode}
              onToggleDemo={() => {
                if (isDemoMode) handleRemoveAllDemoData();
                else handleLoadDemoData();
              }}
              openIncidents={openIncidents}
              allIncidents={incidents}
              onOpenIncidentPanel={() => setShowIncidentModal(true)}
              onNavigateToReports={() => setShowReportsModal(true)}
            />
          </div>
        ) : (
          <TvDesktopDashboard
            selectedFloor={selectedFloor}
            onSelectFloor={setSelectedFloor}
            selectedPeriod={selectedPeriod}
            onSelectPeriod={setSelectedPeriod}
            kpis={floorKpis}
            floorBenchmarks={floorBenchmarks}
            lineSummaries={lineSummaries}
            hourlySeries={hourlySeries}
            weeklyQuality={weeklyQuality}
            productions={productions}
            qualities={qualities}
            topDefects={topDefects}
            highestDhuLines={highestDhuLines}
            isDemoMode={isDemoMode}
            onToggleDemo={() => {
              if (isDemoMode) handleRemoveAllDemoData();
              else handleLoadDemoData();
            }}
            openIncidents={openIncidents}
            allIncidents={incidents}
            onOpenIncidentPanel={() => setShowIncidentModal(true)}
          />
        )}
      </main>

      {/* MODAL 1: ROLE SWITCHER (ADMIN ONLY) */}
      {showRoleSelector && isAuthenticatedAdmin && (
        <div className="fixed inset-0 bg-slate-950/70 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-5 space-y-4 shadow-2xl border border-slate-200">
            <div className="flex justify-between items-center border-b pb-2">
              <h3 className="text-sm font-black text-slate-900">
                Select Authorized Role &amp; User
              </h3>
              <button
                onClick={() => setShowRoleSelector(false)}
                className="text-slate-400 hover:text-slate-700 font-bold"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-slate-600">
              Permissions (including Admin &amp; Auditor access for Bi-Hourly Audit and Weekly BS OQL / NFL AQC) are enforced based on your active role:
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
          currentUserRole={currentUser.role}
          weeklyRecords={weeklyQuality}
          onSaveProduction={handleSaveProduction}
          onSaveQuality={handleSaveQuality}
          onSaveSetup={handleSaveSetup}
          onSaveWeeklyQuality={handleSaveWeeklyQuality}
          onSaveMonthlyTarget={handleSaveMonthlyTarget}
          onClose={() => setShowEntryModal(false)}
        />
      )}

      {/* MODAL 4: REPORTS & CSV EXPORT */}
      {showReportsModal && (
        <ReportsView
          selectedFloor={selectedFloor}
          kpis={floorKpis}
          lineSummaries={lineSummaries}
          productions={productions}
          qualities={qualities}
          weeklyQuality={weeklyQuality}
          incidents={incidents}
          topDefects={topDefects}
          currentUserRole={currentUser.role}
          onDeleteProduction={handleDeleteProduction}
          onDeleteQuality={handleDeleteQuality}
          onClose={() => setShowReportsModal(false)}
        />
      )}

      {/* MODAL 5: ADMIN, CLEAR DEMO DATA & FACTORY RESET */}
      {showAdminModal && (
        <AdminView
          lines={lines}
          users={users}
          demoStats={demoStats}
          liveStats={liveStats}
          onLoadDemoData={handleLoadDemoData}
          onRemoveAllDemoData={handleRemoveAllDemoData}
          onFactoryResetAll={handleFactoryResetAll}
          onToggleLineActive={handleToggleLineActive}
          onApproveRegistration={async userId => {
            await approveCloudUserRegistration(userId, currentUser.name);
          }}
          onRejectRegistration={async userId => {
            await rejectCloudUserRegistration(userId, 'Rejected by Administrator');
          }}
          onApproveDeviceOrTv={async (userId, deviceId, deviceName) => {
            await approveCloudDeviceOrTvRequest(userId, deviceId, deviceName, currentUser.name);
          }}
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
                    <b>Option A (Standalone PWA Folder):</b> Open <code className="font-mono">desktop-pwa/index.html</code> or deploy <code className="font-mono">web-dashboard-and-mobile/</code>.
                  </li>
                  <li>
                    <b>Option B (Install as Native Desktop App Icon):</b> In Chrome or Edge, click the <b>Install App icon (⊕)</b> on the right side of the address bar to pin <b>NFL LIVE Desktop</b> to your Windows Taskbar and Desktop.
                  </li>
                </ul>
              </div>
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
