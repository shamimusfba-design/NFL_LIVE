import { UserRole } from '../types';
import { canWriteWeeklyQualityOrBiHourlyAudit } from './masterData';

/** One data-entry module per kind of person, like the Android app. */
export type EntryModuleId = 'PRODUCTION' | 'TARGET' | 'FINISHING' | 'DHU' | 'AUDIT' | 'MONTHLY' | 'WEEKLY';

export const ENTRY_MODULES: Array<{ id: EntryModuleId; label: string; who: string; icon: string }> = [
  { id: 'PRODUCTION', label: 'Hourly Production', who: 'Reporter · Supervisor', icon: 'M3 21V9l6 4V9l6 4V5h3v16z' },
  { id: 'TARGET', label: 'Target Setting', who: 'IE · Reporter', icon: 'M6 4h12v17H6zM9 4V2h6v2M9 11h6M9 15h6' },
  { id: 'FINISHING', label: 'Iron & Packing Output', who: 'Iron · Packing supervisor', icon: 'M4 17h16l-2-7H9l-2 4H4zM9 10V6h6' },
  { id: 'DHU', label: 'Hourly DHU (Quality)', who: 'Quality', icon: 'M4 12l5 5L20 6' },
  { id: 'AUDIT', label: 'Bi-Hourly OQL (Audit)', who: 'Auditor', icon: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM8.5 12l2.5 2.5 4.5-5' },
  { id: 'MONTHLY', label: 'Monthly Data', who: 'IE', icon: 'M3 5h18v16H3zM3 10h18M8 3v4M16 3v4' },
  { id: 'WEEKLY', label: 'Weekly BS OQL / NFL AQC', who: 'Auditor · IE', icon: 'M3 17l6-6 4 4 8-9M15 6h6v6' }
];

export function canUseEntryModule(id: EntryModuleId, role?: UserRole | null): boolean {
  if (!role) return false;
  if (role === 'ADMIN') return true;
  switch (id) {
    case 'PRODUCTION':
      return ['IE', 'SUPERVISOR', 'PRODUCTION_REPORTER', 'PRODUCTION_MANAGER'].includes(role);
    case 'TARGET':
      return ['IE', 'PRODUCTION_REPORTER'].includes(role);
    case 'FINISHING':
      return ['IE', 'SUPERVISOR', 'PRODUCTION_REPORTER', 'PRODUCTION_MANAGER'].includes(role);
    case 'DHU':
      return ['QUALITY_REPORTER', 'QUALITY_MANAGER', 'AUDITOR'].includes(role);
    case 'AUDIT':
    case 'WEEKLY':
      return canWriteWeeklyQualityOrBiHourlyAudit(role);
    case 'MONTHLY':
      return role === 'IE';
    default:
      return false;
  }
}
