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
