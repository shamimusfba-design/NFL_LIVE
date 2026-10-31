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
  LineMasterItem,
  FloorManpowerRecord,
  AlertComment
} from './types';
import {
  ALL_FACTORY_LINES,
  STANDARD_USERS,
  getBangladeshTodayIso,
  generateSyntheticDemoDataset,
  floorMatches
} from './data/masterData';
import { CalculationEngine } from './services/calculationEngine';
import {
  startLiveFirestoreListeners,
  pushFloorManpowerToCloud,
  pushAlertCommentToCloud,
  pushDemoModeToCloud,
  signOutWebCloudSession,
  restoreWebSession,
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
import { NflLogo } from './components/NflLogo';
import { LoadingScreen } from './components/LoadingScreen';
import { ENTRY_MODULES, canUseEntryModule, EntryModuleId } from './data/entryModules';
import { accountFromRecord, saveSessionCache, readSessionCache, clearSessionCache } from './services/sessionUser';

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
  const [authChecking, setAuthChecking] = useState(true);
  const [restoreNotice, setRestoreNotice] = useState<string | undefined>(undefined);
  const [menuOpen, setMenuOpen] = useState(false);
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    try {
      const q = new URLSearchParams(window.location.search).get('theme');
      if (q === 'light' || q === 'dark') return q;
      const saved = localStorage.getItem('nfl_theme') || new URLSearchParams(window.location.search).get('defaultTheme');
      return saved === 'light' ? 'light' : 'dark';
    } catch { return 'dark'; }
  });
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('nfl_theme', theme); } catch {}
  }, [theme]);

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
  const [entryModule, setEntryModule] = useState<EntryModuleId | undefined>(undefined);
  const [floorTotals, setFloorTotals] = useState<FloorManpowerRecord[]>([]);
  const [alertComments, setAlertComments] = useState<AlertComment[]>([]);
  const demoSwitchRef = useRef<(on: boolean) => void>(() => {});
  const [sidebarHidden, setSidebarHidden] = useState<boolean>(() => { try { return localStorage.getItem('nfl_sidebar_hidden') === '1'; } catch { return false; } });
  const [pageRequest, setPageRequest] = useState<{ page: 'PRODUCTION' | 'QUALITY' | 'ALL' | 'TABLE'; n: number } | undefined>(undefined);
  const [adminStart, setAdminStart] = useState<'DEMO' | 'LINES' | 'USERS' | undefined>(undefined);
  const [showReportsModal, setShowReportsModal] = useState(false);
  const [showAdminModal, setShowAdminModal] = useState(false);
  const [showRoleSelector, setShowRoleSelector] = useState(false);
  const [isAuthenticatedAdmin, setIsAuthenticatedAdmin] = useState(false);
  const [showInstallDesktopModal, setShowInstallDesktopModal] = useState(false);
  const [deferredInstallPrompt, setDeferredInstallPrompt] = useState<any>(null);

  const audioCtxRef = useRef<AudioContext | null>(null);

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

  // The browser remembers the sign-in: bring the person back instead of asking again and again.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const res = await restoreWebSession();
      if (cancelled) return;
      if (res.status === 'ok') {
        const u = accountFromRecord(res.rec, String(res.rec.id || ''));
        saveSessionCache(res.uid, u);
        setCurrentUser(u);
        setIsAuthenticatedAdmin(u.role === 'ADMIN');
      } else if (res.status === 'error') {
        // The network failed while checking. Keep the person in, if this very browser knew them.
        const cached = readSessionCache();
        if (cached && cached.uid === res.uid) {
          setCurrentUser(cached.account);
          setIsAuthenticatedAdmin(cached.account.role === 'ADMIN');
        }
      } else {
        clearSessionCache();
        if (res.status === 'denied') {
          setRestoreNotice(`Signed out: ${res.reason}`);
          void signOutWebCloudSession();
        }
      }
      setAuthChecking(false);
    })();
    return () => { cancelled = true; };
  }, []);

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
      onFloorManpowerUpdated: remote => setFloorTotals(remote),
      onAlertCommentsUpdated: remote => setAlertComments(remote),
      onDemoModeUpdated: enabled => { demoSwitchRef.current(enabled); },
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

  // Notifications are: (1) NPT incidents that were raised, (2) lines whose OQL today is above 10%.
  // Hourly quality entries are never notifications by themselves.
  const highOqlToday = useMemo(() => {
    const byLine = new Map<string, { checked: number; defective: number; floor: string }>();
    qualities
      .filter(q => q.date === todayIso && (isDemoMode || !q.isDemo))
      .forEach(q => {
        const v = byLine.get(q.lineId) || { checked: 0, defective: 0, floor: q.floor };
        v.checked += q.checkedGarments || 0;
        v.defective += q.defectiveGarments || 0;
        byLine.set(q.lineId, v);
      });
    const hasTarget = (id: string) =>
      isDemoMode || targets.some(t => !t.isDemo && t.lineId === id && t.date === todayIso && (t.hourlyTargetPcs > 0 || t.dailyTargetPcs > 0));
    return Array.from(byLine.entries())
      .filter(([id, v]) => v.checked > 0 && hasTarget(id) && (v.defective / v.checked) * 100 > 10)
      .map(([id, v]) => ({ lineId: id, floor: v.floor, oqlPct: (v.defective / v.checked) * 100, checked: v.checked, date: todayIso }))
      .sort((a, b) => b.oqlPct - a.oqlPct);
  }, [qualities, targets, todayIso, isDemoMode]);

  // The alert sounds ONLY for an incident that is new, once, for about 3 seconds, with vibration on a phone.
  // Incidents that were already there when the page opened never make a sound.
  const seenIncidentsRef = useRef<Set<string>>(
    (() => {
      try { return new Set<string>(JSON.parse(localStorage.getItem('nfl_seen_incidents') || '[]')); } catch { return new Set<string>(); }
    })()
  );
  const alertArmedRef = useRef(false);

  const playNewIncidentAlert = () => {
    for (let k = 0; k < 6; k++) window.setTimeout(triggerBeep, k * 500); // 6 beeps over about 3 seconds
    try { (navigator as any).vibrate?.([350, 150, 350, 150, 350]); } catch {}
  };

  useEffect(() => {
    alertArmedRef.current = false;
    if (!currentUser) return;
    // Give the cloud a few seconds to load the existing incidents silently before alerts are switched on.
    const timer = window.setTimeout(() => { alertArmedRef.current = true; }, 8000);
    return () => window.clearTimeout(timer);
  }, [currentUser?.id]);

  useEffect(() => {
    if (!currentUser) return;
    const seen = seenIncidentsRef.current;
    const fresh = incidents.filter(inc => {
      if (seen.has(inc.id)) return false;
      if (inc.status === 'RESOLVED' || inc.isMutedOnCurrentDevice) return false;
      if (currentUser.role === 'ADMIN') return true;
      if (inc.category === 'MACHINE_BREAKDOWN' && currentUser.role === 'MECHANIC') return true;
      if (inc.category === 'QUALITY_PROBLEM' && currentUser.role === 'QUALITY_MANAGER') return true;
      if (inc.category === 'LINE_BALANCING' && currentUser.role === 'IE') return true;
      if (inc.category === 'INPUT_MATERIAL' && currentUser.role === 'INPUT_MATERIAL') return true;
      if (inc.category === 'LOW_PRODUCTION' && currentUser.role === 'PRODUCTION_MANAGER') return true;
      return false;
    });
    const oqlRoles = ['ADMIN', 'IE', 'AUDITOR', 'QUALITY_MANAGER', 'QUALITY_REPORTER', 'PRODUCTION_MANAGER'];
    const freshOql = oqlRoles.includes(currentUser.role)
      ? highOqlToday.map(h => `oql|${h.lineId}|${h.date}`).filter(k => !seen.has(k))
      : [];
    freshOql.forEach(k => seen.add(k));
    if (fresh.length === 0 && freshOql.length === 0) return;
    fresh.forEach(inc => seen.add(inc.id));
    try { localStorage.setItem('nfl_seen_incidents', JSON.stringify(Array.from(seen).slice(-500))); } catch {}
    if (alertArmedRef.current) playNewIncidentAlert();
  }, [incidents, currentUser, highOqlToday]);

  // Cutting, Print and Embroidery count different pieces from sewing, so they are never added into sewing totals.
  // "Factory" therefore shows all sewing in its charts plus a separate department strip.
  const sewingView: FloorId = selectedFloor === 'Factory' ? 'All Sewing' : selectedFloor;

  const targetedConfigs = useMemo(() => {
    const all = CalculationEngine.getTargetedLines(lines, targets, sewingView, todayIso, !isDemoMode);
    if (isDemoMode) return all;
    // A line is running on a day only when its target was set for that day.
    // Lines without a target in the selected period are not shown anywhere (charts, KPIs, tables).
    const active = new Set(
      targets
        .filter(t => !t.isDemo && (t.hourlyTargetPcs > 0 || t.dailyTargetPcs > 0) && CalculationEngine.isDateInPeriod(t.date, todayIso, selectedPeriod))
        .map(t => t.lineId)
    );
    return all.filter(c => active.has(c.lineId));
  }, [lines, targets, sewingView, todayIso, isDemoMode, selectedPeriod]);

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
      sewingView
    );
  }, [lineSummaries, incidents, monthlyTargets, sewingView]);

  const floorBenchmarks = useMemo(() => {
    const scopes: Array<{ id: FloorId; label: string }> = [
      { id: 'Sew 1', label: 'Sew 1' },
      { id: 'Sew 2', label: 'Sew 2' },
      { id: 'Sew 3', label: 'Sew 3' },
      { id: 'All Sewing', label: 'All Sewing' }
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
      sewingView,
      todayIso,
      selectedPeriod
    );
  }, [lineSummaries, productions, qualities, sewingView, todayIso, selectedPeriod]);

  const departmentSummaries = useMemo(() => {
    return (['Cutting', 'Print', 'Embroidery'] as FloorId[]).map(dep => {
      const cfgs = CalculationEngine.getTargetedLines(lines, targets, dep, todayIso, !isDemoMode).filter(
        c => isDemoMode || targets.some(t => !t.isDemo && t.lineId === c.lineId && (t.hourlyTargetPcs > 0 || t.dailyTargetPcs > 0) && CalculationEngine.isDateInPeriod(t.date, todayIso, selectedPeriod))
      );
      const sums = CalculationEngine.computeLineSummaries(cfgs, productions, qualities, incidents, todayIso, selectedPeriod);
      const target = sums.reduce((a, x) => a + (x.target || 0), 0);
      const output = sums.reduce((a, x) => a + (x.output || 0), 0);
      const checked = sums.reduce((a, x) => a + (x.checkedGarments || 0), 0);
      const defective = sums.reduce((a, x) => a + (x.defectiveGarments || 0), 0);
      return {
        floor: dep as string,
        units: sums.length,
        target,
        output,
        achievementPct: target > 0 ? (output / target) * 100 : null,
        checked,
        passed: Math.max(0, checked - defective)
      };
    });
  }, [lines, targets, productions, qualities, incidents, todayIso, selectedPeriod, isDemoMode]);

  const topDefects = useMemo(() => {
    return CalculationEngine.computeTopDefects(qualities, sewingView, todayIso, selectedPeriod);
  }, [qualities, sewingView, todayIso, selectedPeriod]);

  const highestDhuLines = useMemo(() => {
    return CalculationEngine.computeHighestDhuLines(lineSummaries);
  }, [lineSummaries]);

  const openIncidents = useMemo(() => {
    return incidents.filter(
      i => i.status !== 'RESOLVED' && floorMatches(i.floor, selectedFloor)
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

  // Turns the demo data on or off on THIS screen only. Used for the Admin's own click and for the shared switch.
  const applyDemo = (on: boolean) => {
    if (on) {
      const demo = generateSyntheticDemoDataset();
      setTargets(prev => [...prev.filter(t => !t.isDemo), ...demo.targets]);
      setProductions(prev => [...prev.filter(p => !p.isDemo), ...demo.productions]);
      setQualities(prev => [...prev.filter(q => !q.isDemo), ...demo.qualities]);
      setIncidents(prev => [...prev.filter(i => !i.isDemo), ...demo.incidents]);
      setWeeklyQuality(prev => [...prev.filter(w => !w.isDemo), ...demo.weeklyQuality]);
      setIsDemoMode(true);
      try { localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'false'); } catch {}
    } else {
      setTargets(prev => prev.filter(t => !t.isDemo));
      setProductions(prev => prev.filter(p => !p.isDemo));
      setQualities(prev => prev.filter(q => !q.isDemo));
      setIncidents(prev => prev.filter(i => !i.isDemo));
      setWeeklyQuality(prev => prev.filter(w => !w.isDemo));
      setIsDemoMode(false);
      try { localStorage.setItem(STORAGE_KEYS.SAMPLE_CLEARED, 'true'); } catch {}
    }
  };

  // The Admin's switch: it changes the shared setting, so every signed-in person gets the same demo (or loses it).
  demoSwitchRef.current = (on: boolean) => { if (on !== isDemoMode) applyDemo(on); };

  const handleLoadDemoData = () => {
    applyDemo(true);
    void pushDemoModeToCloud(true, currentUser?.name || '');
  };

  const handleRemoveAllDemoData = () => {
    applyDemo(false);
    void pushDemoModeToCloud(false, currentUser?.name || '');
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

  const handleAddComment = (targetId: string, text: string, parentId: string | null) => {
    if (!currentUser) return;
    const c: AlertComment = {
      id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      targetId,
      parentId,
      authorId: currentUser.id,
      authorName: currentUser.name,
      authorRole: currentUser.role,
      text: text.slice(0, 1000),
      createdMillis: Date.now()
    };
    setAlertComments(prev => [c, ...prev]);
    void pushAlertCommentToCloud(c);
  };

  const handleSaveFloorManpower = (record: FloorManpowerRecord) => {
    setFloorTotals(prev => [record, ...prev.filter(r => !(r.floor === record.floor && r.date === record.date))]);
    void pushFloorManpowerToCloud(record);
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

  const closeMenu = () => setMenuOpen(false);
  const toggleMenu = () => {
    if (window.matchMedia('(min-width: 768px)').matches) {
      setSidebarHidden(v => {
        const next = !v;
        try { localStorage.setItem('nfl_sidebar_hidden', next ? '1' : '0'); } catch {}
        return next;
      });
    } else {
      setMenuOpen(v => !v);
    }
  };
  const notificationCount = openIncidents.length + highOqlToday.length;

  if (authChecking) {
    return <LoadingScreen />;
  }

  if (!currentUser) {
    return (
      <LoginView
        notice={restoreNotice}
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
    <div className="min-h-screen bg-[var(--wb-bg)] text-slate-900 font-sans flex">
      {/* ---------- Side menu: slides in on a phone, stays open on a computer ---------- */}
      {menuOpen && <div className="fixed inset-0 z-30 bg-black/60 md:hidden" onClick={() => setMenuOpen(false)} />}
      <aside
        className={`fixed md:sticky top-0 z-40 h-screen w-64 shrink-0 flex-col border-r border-[var(--shell-line)] bg-[var(--shell-side)] text-[var(--shell-side-txt)] transition-transform duration-200 ${
          menuOpen ? 'flex translate-x-0' : `hidden -translate-x-full ${sidebarHidden ? '' : 'md:flex md:translate-x-0'}`
        }`}
      >
        <div className="flex items-center gap-3 px-4 py-4">
          <div className="grid h-10 w-10 place-items-center overflow-hidden rounded-[10px] bg-white shadow">
            <NflLogo size={34} />
          </div>
          <div className="leading-tight">
            <div className="text-[15px] font-extrabold">NFL LIVE</div>
            <div className="text-[11px] text-slate-400">Production &amp; Quality</div>
          </div>
        </div>

        <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 pb-2">
          <div className="px-3 pb-1 pt-3 text-[10px] font-bold uppercase tracking-widest text-slate-400">Dashboard</div>
          <button
            onClick={() => { closeMenu(); setActiveDeviceView('TV'); setPageRequest({ page: 'ALL', n: Date.now() }); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10" /></svg>
            <span className="flex-1">Overview (All)</span>
          </button>
          <button
            onClick={() => { closeMenu(); setActiveDeviceView('TV'); setPageRequest({ page: 'PRODUCTION', n: Date.now() }); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M3 21V9l6 4V9l6 4V5h3v16z" /></svg>
            <span className="flex-1">Production</span>
          </button>
          <button
            onClick={() => { closeMenu(); setActiveDeviceView('TV'); setPageRequest({ page: 'QUALITY', n: Date.now() }); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M4 12l5 5L20 6" /></svg>
            <span className="flex-1">Quality</span>
          </button>
          <button
            onClick={() => { closeMenu(); setActiveDeviceView('TV'); setPageRequest({ page: 'TABLE', n: Date.now() }); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M3 5h18v14H3zM3 10h18M3 15h18M9 5v14" /></svg>
            <span className="flex-1">Line table</span>
          </button>
          <button
            onClick={() => { closeMenu(); setActiveDeviceView('MOBILE'); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2" /></svg>
            <span className="flex-1">Compact view</span>
          </button>
          <div className="px-3 pb-1 pt-3 text-[10px] font-bold uppercase tracking-widest text-slate-400">Data entry</div>
          {ENTRY_MODULES.filter(m => canUseEntryModule(m.id, currentUser?.role)).map(m => (
            <button
              key={m.id}
              onClick={() => { closeMenu(); setEntryModule(m.id); setShowEntryModal(true); }}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
            >
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d={m.icon} /></svg>
              <span className="flex-1">{m.label}</span>
            </button>
          ))}
          <div className="px-3 pb-1 pt-3 text-[10px] font-bold uppercase tracking-widest text-slate-400">Work</div>
          <button
            onClick={() => { closeMenu(); setShowIncidentModal(true) }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3l10 18H2L12 3zM12 10v5M12 18v.01" /></svg>
            <span className="flex-1">Notifications (NPT &amp; OQL)</span>{notificationCount > 0 && (
              <span className="rounded-full bg-red-500 px-2 py-0.5 text-[10px] font-bold text-white">{notificationCount}</span>
            )}
          </button>
      
          <button
            onClick={() => { closeMenu(); setShowReportsModal(true) }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M7 3h7l5 5v13H7V3zM14 3v5h5M10 13h6M10 17h6" /></svg>
            <span className="flex-1">Reports / Excel</span>
          </button>
      {currentUser?.role === 'ADMIN' && (
        <>
          <div className="px-3 pb-1 pt-3 text-[10px] font-bold uppercase tracking-widest text-slate-400">Admin</div>
          <button
            onClick={() => { closeMenu(); setAdminStart('USERS'); setShowAdminModal(true); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.6-3.6 3.2-5.5 6.5-5.5s5.9 1.9 6.5 5.5M19 8v6M16 11h6" /></svg>
            <span className="flex-1">Users & approvals</span>
          </button>
          <button
            onClick={() => { closeMenu(); setAdminStart('LINES'); setShowAdminModal(true); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18M3 12h18M3 18h18" /></svg>
            <span className="flex-1">Line master</span>
          </button>
          <button
            onClick={() => { closeMenu(); setAdminStart('DEMO'); setShowAdminModal(true); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" /></svg>
            <span className="flex-1">Data clean & reset</span>
          </button>
        </>
      )}
          <div className="px-3 pb-1 pt-3 text-[10px] font-bold uppercase tracking-widest text-slate-400">More</div>
      
          <button
            onClick={() => { closeMenu(); handleTriggerInstallDesktop() }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-[13px] font-semibold text-[var(--shell-side-txt)] hover:bg-[var(--shell-hover)] hover:text-white"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M12 4v11M7 11l5 5 5-5M5 20h14" /></svg>
            <span className="flex-1">Install desktop app</span>
          </button>
        </nav>

        <div className="border-t border-[var(--shell-line)] p-3">
          <button
            onClick={() => { if (isAuthenticatedAdmin) { setShowRoleSelector(true); setMenuOpen(false); } }}
            title={isAuthenticatedAdmin ? 'Admin role preview' : `Signed in as ${currentUser.name}`}
            className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-[var(--shell-hover)]"
          >
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#1d3b5f] text-[13px] font-extrabold">
              {currentUser.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()}
            </span>
            <span className="min-w-0 flex-1 leading-tight">
              <span className="block truncate text-[13px] font-bold">{currentUser.name}</span>
              <span className="block text-[11px] text-slate-400">{currentUser.role}</span>
            </span>
          </button>
          <button
            onClick={() => {
              void signOutWebCloudSession();
              clearSessionCache();
              setCurrentUser(null);
              setIsAuthenticatedAdmin(false);
              setShowRoleSelector(false);
              setMenuOpen(false);
            }}
            className="mt-2 flex w-full items-center justify-center gap-2 rounded-lg border border-red-800/70 bg-red-950/60 py-2 text-[12px] font-bold text-red-200 hover:bg-red-900"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M9 4H5v16h4M16 8l4 4-4 4M20 12H9" /></svg>
            Logout
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* ---------- Slim top bar ---------- */}
        <header className="sticky top-0 z-20 flex items-center gap-2 border-b border-[var(--shell-top-line)] bg-[var(--shell-top)] px-3 py-2 text-[var(--shell-top-txt)] backdrop-blur">
          <button
            onClick={toggleMenu}
            aria-label="Open menu"
            title="Show or hide the menu"
            className="grid h-9 w-9 place-items-center rounded-lg border border-[var(--shell-top-line)] bg-[var(--shell-top-btn)]"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
          <div className="min-w-0 flex-1 truncate text-[14px] font-extrabold md:text-[16px]">NFL LIVE Production &amp; Quality</div>
          {/* Two clear choices; the one in use is filled. */}
          <div
            role="group"
            aria-label="Day or Night mode"
            className="flex items-center rounded-full border border-[var(--shell-top-line)] bg-[var(--shell-top-btn)] p-0.5 text-[11px] font-bold"
          >
            <button
              onClick={() => setTheme('light')}
              aria-pressed={theme === 'light'}
              title="Day mode"
              className={`flex items-center gap-1 rounded-full px-2.5 py-1 ${theme === 'light' ? 'bg-[#1A62D6] text-white shadow' : 'opacity-70 hover:opacity-100'}`}
            >
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
              <span>Day</span>
            </button>
            <button
              onClick={() => setTheme('dark')}
              aria-pressed={theme === 'dark'}
              title="Night mode"
              className={`flex items-center gap-1 rounded-full px-2.5 py-1 ${theme === 'dark' ? 'bg-[#1A62D6] text-white shadow' : 'opacity-70 hover:opacity-100'}`}
            >
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
              <span>Night</span>
            </button>
          </div>
          {notificationCount > 0 && (
            <button
              onClick={() => setShowIncidentModal(true)}
              title="NPT incidents and lines with OQL above 10% today"
              className="rounded-lg bg-red-600 px-2.5 py-1 text-[11px] font-black text-white"
            >
              {notificationCount} alerts
            </button>
          )}
          <span
            className={`whitespace-nowrap rounded-full border px-2 py-0.5 text-[10px] font-bold ${
              cloudConnected
                ? 'border-emerald-700 bg-emerald-950/80 text-emerald-300'
                : 'border-slate-700 bg-slate-800 text-slate-300'
            }`}
          >
            {cloudConnected ? 'Cloud Live' : 'Local'}
          </span>
        </header>

      {/* Main View Area */}
      <main className="flex-1">
        {activeDeviceView === 'MOBILE' ? (
          <div className="max-w-lg mx-auto min-h-screen bg-[var(--wb-bg)] shadow-xl relative border-x border-[var(--wb-line)]">
            <MobileDashboard
              pageRequest={pageRequest}
              selectedFloor={selectedFloor}
              onSelectFloor={setSelectedFloor}
              selectedPeriod={selectedPeriod}
              onSelectPeriod={setSelectedPeriod}
              kpis={floorKpis}
              floorBenchmarks={floorBenchmarks}
              lineSummaries={lineSummaries}
              hourlySeries={hourlySeries}
            departmentSummaries={departmentSummaries}
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
            pageRequest={pageRequest}
            selectedFloor={selectedFloor}
            onSelectFloor={setSelectedFloor}
            selectedPeriod={selectedPeriod}
            onSelectPeriod={setSelectedPeriod}
            kpis={floorKpis}
            floorBenchmarks={floorBenchmarks}
            lineSummaries={lineSummaries}
            hourlySeries={hourlySeries}
            departmentSummaries={departmentSummaries}
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
          comments={alertComments}
          onAddComment={handleAddComment}
          highOqlLines={highOqlToday}
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
          module={entryModule}
          targets={targets}
          floorTotals={floorTotals}
          onSaveFloorManpower={handleSaveFloorManpower}
          onClose={() => { setShowEntryModal(false); setEntryModule(undefined); }}
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
          initialTab={adminStart}
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
          onClose={() => { setShowAdminModal(false); setAdminStart(undefined); }}
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
    </div>
  );
}
