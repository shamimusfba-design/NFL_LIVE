import {
  UserAccount,
  AlertComment,
  FloorManpowerRecord,
  IncidentRecord
} from '../types';
import { generateSyntheticDemoDataset, getBangladeshTodayIso } from './masterData';

export const CLIENT_DEMO_ID = 'DEMO';
export const CLIENT_DEMO_PASS = 'demo1234';
export const DEFAULT_UPWORK_URL = 'https://www.upwork.com/';

export function isClientDemoUrl(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const path = window.location.pathname.toLowerCase();
    const params = new URLSearchParams(window.location.search);
    const hash = window.location.hash.toLowerCase();
    return (
      path.endsWith('/demo') ||
      path.endsWith('/demo/') ||
      path.includes('/demo/index.html') ||
      params.has('demo') ||
      params.get('mode') === 'demo' ||
      params.get('view') === 'client' ||
      hash.includes('demo')
    );
  } catch {
    return false;
  }
}

export function matchesClientDemoCredentials(idInput: string, passInput: string): boolean {
  const id = idInput.trim().toLowerCase();
  const pw = passInput.trim();
  const validIds = ['demo', 'client', 'buyer', 'upwork', '100888', 'demo@nfl-live.app'];
  return validIds.includes(id) && (pw === CLIENT_DEMO_PASS || pw.toLowerCase() === 'demo1234' || pw.toLowerCase() === 'demo');
}

export function isClientDemoUser(user: UserAccount | null | undefined): boolean {
  if (!user) return false;
  return user.id === 'DEMO_CLIENT' || user.username.toUpperCase() === 'DEMO';
}

export function isClientDemoSession(): boolean {
  if (isClientDemoUrl()) return true;
  if (typeof window === 'undefined') return false;
  try {
    const raw = sessionStorage.getItem('nfl_active_user');
    if (!raw) return false;
    const parsed = JSON.parse(raw) as UserAccount;
    return isClientDemoUser(parsed);
  } catch {
    return false;
  }
}

export function createClientDemoAccount(): UserAccount {
  return {
    id: 'DEMO_CLIENT',
    username: CLIENT_DEMO_ID,
    name: 'Client Showcase Viewer',
    role: 'ADMIN',
    designation: 'Client Demo (Read-Only)',
    department: 'Management',
    floor: 'Factory',
    accountStatus: 'APPROVED',
    isActive: true,
    permissions: null,
    allowedLines: null
  };
}

export function getUpworkUrl(): string {
  if (typeof window === 'undefined') return DEFAULT_UPWORK_URL;
  try {
    const params = new URLSearchParams(window.location.search);
    const fromParam = params.get('upwork');
    if (fromParam && /^https?:\/\//i.test(fromParam.trim())) {
      localStorage.setItem('nfl_demo_upwork_url', fromParam.trim());
      return fromParam.trim();
    }
    const saved = localStorage.getItem('nfl_demo_upwork_url');
    if (saved && /^https?:\/\//i.test(saved.trim())) {
      return saved.trim();
    }
  } catch {}
  return DEFAULT_UPWORK_URL;
}

export function setCustomUpworkUrl(url: string): void {
  try {
    if (url.trim()) {
      localStorage.setItem('nfl_demo_upwork_url', url.trim());
    } else {
      localStorage.removeItem('nfl_demo_upwork_url');
    }
  } catch {}
}

export function generateRichClientDemoBundle(todayIso: string = getBangladeshTodayIso()) {
  const base = generateSyntheticDemoDataset(todayIso);

  const sanitizedTargets = base.targets.map(t => ({
    ...t,
    buyer: t.buyer ? t.buyer.replace(/NFL/gi, 'DEMO') : 'DEMO Global'
  }));

  const sanitizedQualities = base.qualities.map(q => ({
    ...q,
    reportedBy: q.reportedBy ? q.reportedBy.replace(/NFL/gi, 'DEMO') : 'DEMO Auditor'
  }));

  const sanitizedWeeklyQuality = base.weeklyQuality.map(w => ({
    ...w,
    lastUpdatedBy: w.lastUpdatedBy ? w.lastUpdatedBy.replace(/NFL/gi, 'DEMO') : 'DEMO Auditor'
  }));

  const extraIncidents: IncidentRecord[] = [
    ...base.incidents.map(inc => ({
      ...inc,
      reportedBy: inc.reportedBy ? inc.reportedBy.replace(/NFL/gi, 'DEMO') : 'DEMO Supervisor'
    })),
    {
      id: 'demo-inc-3',
      floor: 'Sew 3',
      lineId: 'S2',
      category: 'QUALITY_PROBLEM',
      description: 'High collar shading & skip stitch noticed during 2-hr inline audit',
      reportedCause: 'Thread tension & needle size mismatch',
      impactLevel: 'HIGH',
      affectedManpower: 6,
      startTimeMillis: Date.now() - 32 * 60 * 1000,
      status: 'OPEN',
      reportedBy: 'DEMO Quality Auditor',
      isDemo: true
    },
    {
      id: 'demo-inc-4',
      floor: 'Print',
      lineId: 'PRT-Machine-2',
      category: 'MACHINE_BREAKDOWN',
      machineRef: 'AUTO-OVAL-02',
      description: 'Flash cure dryer sensor calibration alert on Print Machine 2',
      reportedCause: 'IR temperature relay check',
      impactLevel: 'MEDIUM',
      affectedManpower: 3,
      startTimeMillis: Date.now() - 41 * 60 * 1000,
      status: 'ACKNOWLEDGED',
      acknowledgedBy: 'Chief Maintenance',
      acknowledgedTimeMillis: Date.now() - 19 * 60 * 1000,
      reportedBy: 'DEMO Print Supervisor',
      isDemo: true
    }
  ];

  const demoComments: AlertComment[] = [
    {
      id: 'demo-cmt-1',
      targetId: 'demo-inc-1',
      parentId: null,
      authorId: '1015198',
      authorName: 'Demo Operations Director',
      authorRole: 'Management',
      authorDesignation: 'Management',
      text: 'Mechanic team already assigned to Line K1 (SNLS-04). Replace drive belt and verify RPM within 10 mins.',
      createdMillis: Date.now() - 6 * 60 * 1000
    },
    {
      id: 'demo-cmt-2',
      targetId: 'demo-inc-1',
      parentId: 'demo-cmt-1',
      authorId: '102030',
      authorName: 'Rafiqul Islam',
      authorRole: 'Chief Mechanic · Maintenance',
      authorDesignation: 'Chief Mechanic · Maintenance',
      text: 'Belt replaced and needle bar timing calibrated. Testing 10 garments now before closing NPT.',
      createdMillis: Date.now() - 3 * 60 * 1000
    },
    {
      id: 'demo-cmt-3',
      targetId: 'demo-inc-3',
      parentId: null,
      authorId: '103040',
      authorName: 'Mahmudul Hasan',
      authorRole: 'Quality Manager · Quality',
      authorDesignation: 'Quality Manager · Quality',
      text: '100% re-check initiated on Line S2 bundles. Inline QC instructed to hold shipment until OQL < 4.5%.',
      createdMillis: Date.now() - 12 * 60 * 1000
    }
  ];

  const floorTotals: FloorManpowerRecord[] = [
    { floor: 'Sew 1', date: todayIso, totalMachines: 460, machinesRunning: 440, operatorsPresent: 440, helpersPresent: 66, operatorsOnHandwork: 8, updatedBy: 'IE Team', updatedMillis: Date.now() - 3600_000 },
    { floor: 'Sew 2', date: todayIso, totalMachines: 380, machinesRunning: 360, operatorsPresent: 360, helpersPresent: 54, operatorsOnHandwork: 6, updatedBy: 'IE Team', updatedMillis: Date.now() - 3600_000 },
    { floor: 'Sew 3', date: todayIso, totalMachines: 480, machinesRunning: 460, operatorsPresent: 460, helpersPresent: 70, operatorsOnHandwork: 10, updatedBy: 'IE Team', updatedMillis: Date.now() - 3600_000 },
    { floor: 'Cutting', date: todayIso, totalMachines: 24, machinesRunning: 22, operatorsPresent: 85, helpersPresent: 25, operatorsOnHandwork: 0, updatedBy: 'Cutting Incharge', updatedMillis: Date.now() - 3600_000 },
    { floor: 'Print', date: todayIso, totalMachines: 12, machinesRunning: 10, operatorsPresent: 60, helpersPresent: 20, operatorsOnHandwork: 0, updatedBy: 'Print Incharge', updatedMillis: Date.now() - 3600_000 },
    { floor: 'Embroidery', date: todayIso, totalMachines: 14, machinesRunning: 12, operatorsPresent: 55, helpersPresent: 15, operatorsOnHandwork: 0, updatedBy: 'Embroidery Incharge', updatedMillis: Date.now() - 3600_000 }
  ];

  return {
    ...base,
    targets: sanitizedTargets,
    qualities: sanitizedQualities,
    weeklyQuality: sanitizedWeeklyQuality,
    incidents: extraIncidents,
    comments: demoComments,
    floorTotals
  };
}
