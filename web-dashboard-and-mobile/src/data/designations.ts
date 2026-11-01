import type { UserRole, UserAccount, AlertComment } from '../types';

/** Floors a person can be registered on */
export const FLOOR_CHOICES: Array<{ key: string; label: string }> = [
  { key: 'SEW_1', label: 'Sew 1' },
  { key: 'SEW_2', label: 'Sew 2' },
  { key: 'SEW_3', label: 'Sew 3' },
  { key: 'Cutting', label: 'Cutting' },
  { key: 'Print', label: 'Print' },
  { key: 'Embroidery', label: 'Embroidery' }
];

/** Designations offered at registration: department, role and floor scope come from here */
export const DESIGNATIONS: Array<{ name: string; department: string; role: string; scope: 'ALL' | 'FINISHING' | null }> = [
  { name: 'Management', department: 'Management', role: 'MANAGEMENT_VIEW', scope: 'ALL' },
  { name: 'Factory Management', department: 'Management', role: 'MANAGEMENT_VIEW', scope: 'ALL' },
  { name: 'Head of Production', department: 'Production', role: 'PRODUCTION_MANAGER', scope: 'ALL' },
  { name: 'Head of Quality', department: 'Quality', role: 'QUALITY_MANAGER', scope: 'ALL' },
  { name: 'IE', department: 'IE & Planning', role: 'IE', scope: null },
  { name: 'IEM', department: 'IE & Planning', role: 'IE', scope: 'ALL' },
  { name: 'Reporter', department: 'IE & Planning', role: 'PRODUCTION_REPORTER', scope: null },
  { name: 'Production Supervisor', department: 'Production', role: 'SUPERVISOR', scope: null },
  { name: 'Production Officer', department: 'Production', role: 'SUPERVISOR', scope: null },
  { name: 'Quality Supervisor', department: 'Quality', role: 'QUALITY_REPORTER', scope: null },
  { name: 'Quality Officer', department: 'Quality', role: 'AUDITOR', scope: null },
  { name: 'QI (Quality Inspector)', department: 'Quality', role: 'QUALITY_REPORTER', scope: null },
  { name: 'Iron Supervisor', department: 'Finishing', role: 'SUPERVISOR', scope: null },
  { name: 'Packing Supervisor', department: 'Finishing', role: 'SUPERVISOR', scope: null },
  { name: 'Cutting Supervisor', department: 'Cutting', role: 'SUPERVISOR', scope: null },
  { name: 'Print Supervisor', department: 'Print', role: 'SUPERVISOR', scope: null },
  { name: 'Embroidery Supervisor', department: 'Embroidery', role: 'SUPERVISOR', scope: null },
  { name: 'Finishing Supervisor', department: 'Finishing', role: 'SUPERVISOR', scope: 'FINISHING' },
  { name: 'Finishing In-charge', department: 'Finishing', role: 'SUPERVISOR', scope: 'FINISHING' },
  { name: 'Finishing Manager', department: 'Finishing', role: 'PRODUCTION_MANAGER', scope: 'FINISHING' },
  { name: 'Maintenance', department: 'Maintenance', role: 'MECHANIC', scope: null },
  { name: 'Mechanic', department: 'Maintenance', role: 'MECHANIC', scope: null },
  { name: 'Other', department: 'Other', role: 'MANAGEMENT_VIEW', scope: null }
];

const RAW_ROLE_CODES = new Set([
  'ADMIN',
  'AUDITOR',
  'PRODUCTION_REPORTER',
  'QUALITY_REPORTER',
  'SUPERVISOR',
  'PRODUCTION_MANAGER',
  'QUALITY_MANAGER',
  'MECHANIC',
  'IE',
  'INPUT_MATERIAL',
  'MANAGEMENT_VIEW',
  'MANAGEMENT',
  'QUALITY',
  'PACKING',
  'TV_VIEWER',
  'OTHER'
]);

/**
 * Returns the proper human-readable designation for a user or comment author.
 * For people who do not have a separate designation (especially Management / Admin or role-only records),
 * returns the appropriate title (e.g. "Management", "Head of Production", "Head of Quality", etc.).
 */
export function formatUserDesignation(input: {
  designation?: string | null;
  role?: string | null;
  department?: string | null;
  floor?: string | null;
}): string {
  const rawDesig = (input.designation || '').trim();
  const rawRole = (input.role || '').trim().toUpperCase();
  const rawDept = (input.department || '').trim();
  const rawFloor = (input.floor || '').trim();

  if (
    rawDesig &&
    rawDesig.toLowerCase() !== 'other' &&
    !RAW_ROLE_CODES.has(rawDesig.toUpperCase())
  ) {
    if (rawDesig.toLowerCase() === 'admin' || rawDesig.toLowerCase() === 'administrator') {
      return 'Management';
    }
    return rawDesig;
  }

  switch (rawRole) {
    case 'MANAGEMENT_VIEW':
    case 'MANAGEMENT':
    case 'ADMIN':
      return 'Management';
    case 'PRODUCTION_MANAGER':
      return /finish/i.test(rawFloor) || /finish/i.test(rawDept)
        ? 'Finishing Manager'
        : 'Head of Production';
    case 'QUALITY_MANAGER':
      return 'Head of Quality';
    case 'IE':
      return rawFloor === 'ALL' || rawFloor === 'All Floor' || rawFloor === 'Factory' ? 'IEM' : 'IE';
    case 'AUDITOR':
      return 'Quality Officer';
    case 'QUALITY_REPORTER':
    case 'QUALITY':
      return 'Quality Supervisor';
    case 'PRODUCTION_REPORTER':
      return 'Reporter';
    case 'PACKING':
      return 'Packing Supervisor';
    case 'SUPERVISOR':
      if (/cut/i.test(rawFloor) || /cut/i.test(rawDept)) return 'Cutting Supervisor';
      if (/print/i.test(rawFloor) || /print/i.test(rawDept)) return 'Print Supervisor';
      if (/emb/i.test(rawFloor) || /emb/i.test(rawDept)) return 'Embroidery Supervisor';
      if (/finish/i.test(rawFloor) || /finish/i.test(rawDept)) return 'Finishing Supervisor';
      return 'Production Supervisor';
    case 'MECHANIC':
      return 'Maintenance';
    case 'INPUT_MATERIAL':
      return 'Input / Material';
    default:
      if (/manage/i.test(rawDept)) return 'Management';
      return rawDesig && rawDesig.toLowerCase() !== 'other' ? rawDesig : 'Management';
  }
}

export function resolveCommentDesignation(c: AlertComment, users?: UserAccount[]): string {
  if (users && users.length > 0) {
    const matched = users.find(
      u =>
        (c.authorId && (u.id.toLowerCase() === c.authorId.toLowerCase() || u.username.toLowerCase() === c.authorId.toLowerCase())) ||
        (c.authorName && u.name.trim().toLowerCase() === c.authorName.trim().toLowerCase())
    );
    if (matched) {
      return formatUserDesignation({
        designation: c.authorDesignation || matched.designation,
        role: matched.role || c.authorRole,
        department: matched.department,
        floor: matched.floor
      });
    }
  }
  return formatUserDesignation({
    designation: c.authorDesignation || c.authorRole,
    role: c.authorRole
  });
}

/** The user-record fields for a designation + floor, exactly as registration writes them. */
export function designationFields(designationName: string, floorKey: string) {
  const d = DESIGNATIONS.find(x => x.name === designationName) || DESIGNATIONS[DESIGNATIONS.length - 1];
  let physical = floorKey;
  let scopeName = floorKey;
  let scopeDisplay = FLOOR_CHOICES.find(f => f.key === floorKey)?.label || floorKey;
  if (d.scope === 'FINISHING') { physical = 'FINISHING'; scopeName = 'FINISHING'; scopeDisplay = 'Finishing'; }
  else if (d.scope === 'ALL') { scopeName = 'ALL'; scopeDisplay = 'All Floor'; }
  return {
    designation: d.name,
    department: d.department,
    role: d.role as UserRole,
    floor: physical,
    requestedFloor: physical,
    assignedFloor: scopeDisplay,
    authorizedFloorScope: scopeName
  };
}
