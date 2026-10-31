import type { UserRole } from '../types';

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
