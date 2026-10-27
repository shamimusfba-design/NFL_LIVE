import { UserAccount, UserRole, FloorId } from '../types';

const KNOWN_ROLES: UserRole[] = [
  'ADMIN', 'PRODUCTION_REPORTER', 'QUALITY_REPORTER', 'AUDITOR', 'SUPERVISOR', 'PRODUCTION_MANAGER',
  'QUALITY_MANAGER', 'MECHANIC', 'IE', 'INPUT_MATERIAL', 'MANAGEMENT_VIEW'
];

function toFloorId(raw: string): FloorId {
  const v = raw.toLowerCase();
  if (v.includes('1')) return 'Sew 1' as FloorId;
  if (v.includes('2')) return 'Sew 2' as FloorId;
  if (v.includes('3')) return 'Sew 3' as FloorId;
  return 'All' as FloorId;
}

/** Turns an approved cloud user record into the account the web app works with. */
export function accountFromRecord(rec: Record<string, any>, fallbackId: string): UserAccount {
  const id = String(rec.id || fallbackId);
  const role = (KNOWN_ROLES as string[]).includes(String(rec.role))
    ? (String(rec.role) as UserRole)
    : ('MANAGEMENT_VIEW' as UserRole); // unknown role: view only
  return {
    id,
    name: String(rec.name || fallbackId),
    username: id.toLowerCase(),
    role,
    floor: toFloorId(String(rec.authorizedFloorScope || rec.floor || 'ALL'))
  };
}

const CACHE_KEY = 'nfl_session_cache_v1';

/** What was last known about the signed-in person, kept only to survive a slow or failed network at start-up. */
export function saveSessionCache(uid: string, account: UserAccount): void {
  try { localStorage.setItem(CACHE_KEY, JSON.stringify({ uid, account })); } catch {}
}

export function readSessionCache(): { uid: string; account: UserAccount } | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const v = JSON.parse(raw);
    return v && v.uid && v.account && v.account.id ? v : null;
  } catch { return null; }
}

export function clearSessionCache(): void {
  try { localStorage.removeItem(CACHE_KEY); } catch {}
}
