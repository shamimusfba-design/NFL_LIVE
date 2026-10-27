import React, { useState } from 'react';
import { UserAccount, UserRole, FloorId } from '../types';
import {
  signInWebEmployee,
  createWebEmployeeAccount,
  fetchOwnUserRecord,
  bootstrapOwnerAdminRecord,
  bindFirebaseUidToEmployee,
  submitWebRegistration,
  signOutWebCloudSession
} from '../services/firebaseLiveSync';

interface LoginViewProps {
  // Kept for compatibility with the caller. Sign-in is checked by Firebase, never against these.
  users?: UserAccount[];
  onLogin: (user: UserAccount) => void;
  onUpdateAdmin?: (newAdmin: UserAccount) => void;
}

const KNOWN_ROLES: UserRole[] = [
  'ADMIN', 'PRODUCTION_REPORTER', 'QUALITY_REPORTER', 'AUDITOR', 'SUPERVISOR', 'PRODUCTION_MANAGER',
  'QUALITY_MANAGER', 'MECHANIC', 'IE', 'INPUT_MATERIAL', 'MANAGEMENT_VIEW'
];

/** Same designations, departments, roles and floor rules as the Android app (DesignationPolicy). */
const DESIGNATIONS: Array<{ name: string; department: string; role: string; scope: 'ALL' | 'FINISHING' | null }> = [
  { name: 'Management', department: 'Management', role: 'MANAGEMENT_VIEW', scope: 'ALL' },
  { name: 'Factory Management', department: 'Management', role: 'MANAGEMENT_VIEW', scope: 'ALL' },
  { name: 'Head of Production', department: 'Production', role: 'PRODUCTION_MANAGER', scope: 'ALL' },
  { name: 'Head of Quality', department: 'Quality', role: 'QUALITY_MANAGER', scope: 'ALL' },
  { name: 'IE', department: 'IE & Planning', role: 'IE', scope: null },
  { name: 'Reporter', department: 'IE & Planning', role: 'PRODUCTION_REPORTER', scope: null },
  { name: 'Production Supervisor', department: 'Production', role: 'SUPERVISOR', scope: null },
  { name: 'Production Officer', department: 'Production', role: 'SUPERVISOR', scope: null },
  { name: 'Quality Supervisor', department: 'Quality', role: 'QUALITY_REPORTER', scope: null },
  { name: 'Quality Officer', department: 'Quality', role: 'AUDITOR', scope: null },
  { name: 'Finishing Supervisor', department: 'Finishing', role: 'SUPERVISOR', scope: 'FINISHING' },
  { name: 'Finishing In-charge', department: 'Finishing', role: 'SUPERVISOR', scope: 'FINISHING' },
  { name: 'Finishing Manager', department: 'Finishing', role: 'PRODUCTION_MANAGER', scope: 'FINISHING' },
  { name: 'Maintenance', department: 'Maintenance', role: 'MECHANIC', scope: null },
  { name: 'Mechanic', department: 'Maintenance', role: 'MECHANIC', scope: null },
  { name: 'Other', department: 'Other', role: 'MANAGEMENT_VIEW', scope: null }
];

const FLOOR_CHOICES: Array<{ key: 'SEW_1' | 'SEW_2' | 'SEW_3'; label: string }> = [
  { key: 'SEW_1', label: 'Sew 1' },
  { key: 'SEW_2', label: 'Sew 2' },
  { key: 'SEW_3', label: 'Sew 3' }
];

function toFloorId(raw: string): FloorId {
  const v = raw.toLowerCase();
  if (v.includes('1')) return 'Sew 1' as FloorId;
  if (v.includes('2')) return 'Sew 2' as FloorId;
  if (v.includes('3')) return 'Sew 3' as FloorId;
  return 'All' as FloorId;
}

const OWNER_EMPLOYEE_ID = '1015198';

export const LoginView: React.FC<LoginViewProps> = ({ onLogin }) => {
  const [mode, setMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');
  // Nothing is pre-filled: no default ID and no default password.
  const [usernameInput, setUsernameInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [regName, setRegName] = useState('');
  const [regId, setRegId] = useState('');
  const [regDesignation, setRegDesignation] = useState('Production Supervisor');
  const [regFloor, setRegFloor] = useState<'SEW_1' | 'SEW_2' | 'SEW_3'>('SEW_2');
  const [regPassword, setRegPassword] = useState('');
  const [regConfirm, setRegConfirm] = useState('');

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    const input = usernameInput.trim();
    if (!input || !passwordInput.trim()) {
      setErrorMessage('Enter your Employee ID and password.');
      return;
    }
    setBusy(true);
    setErrorMessage(null);
    setInfoMessage(null);
    try {
      const signedIn = await signInWebEmployee(input, passwordInput);
      if (!signedIn.ok) {
        setErrorMessage(signedIn.message);
        return;
      }
      const ownerStyle = input.includes('@');
      const employeeId = ownerStyle ? OWNER_EMPLOYEE_ID : input;
      const uid = signedIn.user.uid;
      let rec = await fetchOwnUserRecord(employeeId);
      if (rec === undefined) {
        await signOutWebCloudSession();
        setErrorMessage('Could not reach the server. Check the internet and try again.');
        return;
      }
      if (rec === null && ownerStyle) {
        rec = await bootstrapOwnerAdminRecord(uid, signedIn.user.email || '');
      }
      if (!rec) {
        await signOutWebCloudSession();
        setErrorMessage(`No registration was found for Employee ID ${employeeId}. Register first, then wait for Admin approval.`);
        return;
      }
      if (rec.firebaseUid && rec.firebaseUid !== uid) {
        await signOutWebCloudSession();
        setErrorMessage('This Employee ID is registered to a different account. Please contact the Administrator.');
        return;
      }
      const status = String(rec.accountStatus || '');
      if (status === 'PENDING_APPROVAL') {
        await signOutWebCloudSession();
        setInfoMessage('Your account is waiting for Admin approval. You can sign in after you are approved.');
        return;
      }
      if (status !== 'APPROVED' || rec.isActive === false) {
        await signOutWebCloudSession();
        setErrorMessage('This account is not approved or is not active. Please contact the Administrator.');
        return;
      }
      const bound = await bindFirebaseUidToEmployee(uid, String(rec.id || employeeId), signedIn.user.email || '');
      if (!bound) {
        await signOutWebCloudSession();
        setErrorMessage('Could not link this sign-in to your Employee ID. Please contact the Administrator.');
        return;
      }
      const role = (KNOWN_ROLES as string[]).includes(String(rec.role))
        ? (String(rec.role) as UserRole)
        : ('MANAGEMENT_VIEW' as UserRole); // unknown role: view only
      setPasswordInput('');
      onLogin({
        id: String(rec.id || employeeId),
        name: String(rec.name || employeeId),
        username: String(rec.id || employeeId).toLowerCase(),
        role,
        floor: toFloorId(String(rec.authorizedFloorScope || rec.floor || 'ALL'))
      });
    } catch (err: any) {
      setErrorMessage(err?.message ? String(err.message) : 'Could not sign in. Check the internet connection.');
    } finally {
      setBusy(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    const id = regId.trim();
    const name = regName.trim();
    const pw = regPassword.trim();
    if (name.length < 2) return setErrorMessage('Please enter your full name (at least 2 characters).');
    if (!/^[A-Za-z0-9_-]{2,24}$/.test(id)) return setErrorMessage('Employee ID must contain only letters, numbers, hyphens or underscores.');
    if (id === OWNER_EMPLOYEE_ID || id.toLowerCase() === 'admin') {
      return setErrorMessage(`Employee ID ${id} is reserved for system administration and cannot be registered.`);
    }
    if (pw.length < 6) return setErrorMessage('Password must be at least 6 characters long.');
    if (pw !== regConfirm.trim()) return setErrorMessage('Password and Confirm Password do not match.');
    const d = DESIGNATIONS.find(x => x.name === regDesignation) || DESIGNATIONS[DESIGNATIONS.length - 1];
    let physical: 'SEW_1' | 'SEW_2' | 'SEW_3' | 'FINISHING' = regFloor;
    let scopeName: string = regFloor;
    let scopeDisplay: string = FLOOR_CHOICES.find(f => f.key === regFloor)?.label || 'Sew 2';
    if (d.scope === 'FINISHING') { physical = 'FINISHING'; scopeName = 'FINISHING'; scopeDisplay = 'Finishing'; }
    else if (d.scope === 'ALL') { scopeName = 'ALL'; scopeDisplay = 'All Floor'; }
    setBusy(true);
    setErrorMessage(null);
    setInfoMessage(null);
    try {
      const created = await createWebEmployeeAccount(id, pw);
      if (!created.ok) {
        setErrorMessage(created.message);
        return;
      }
      const sent = await submitWebRegistration({
        employeeId: id, name, designationName: d.name, department: d.department, role: d.role,
        physicalFloor: physical, scopeName, scopeDisplay
      });
      await signOutWebCloudSession();
      if (!sent.ok) {
        setErrorMessage(sent.message);
        return;
      }
      setInfoMessage(sent.message);
      setUsernameInput(id);
      setRegName(''); setRegId(''); setRegPassword(''); setRegConfirm('');
      setMode('LOGIN');
    } finally {
      setBusy(false);
    }
  };

  const inputClass = 'w-full px-3 py-2 rounded-lg border border-slate-300 text-sm text-slate-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500';
  const d = DESIGNATIONS.find(x => x.name === regDesignation);

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6">
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 text-white font-black text-2xl shadow-lg">
            NFL
          </div>
          <h1 className="text-2xl font-black text-white tracking-tight">NFL LIVE — Desktop Edition</h1>
          <p className="text-xs text-slate-400 font-medium">
            Sign in with the same Employee ID and password as the Android app.
          </p>
        </div>

        <div className="bg-white rounded-2xl p-6 shadow-2xl border border-slate-200/80 space-y-5">
          {mode === 'LOGIN' ? (
            <form onSubmit={handleLoginSubmit} className="space-y-4" autoComplete="off">
              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-600">Employee ID</label>
                <input
                  type="text"
                  value={usernameInput}
                  onChange={e => { setUsernameInput(e.target.value); setErrorMessage(null); }}
                  placeholder="Employee ID"
                  autoComplete="off"
                  className={inputClass}
                />
              </div>
              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-600">Password</label>
                <input
                  type="password"
                  value={passwordInput}
                  onChange={e => { setPasswordInput(e.target.value); setErrorMessage(null); }}
                  placeholder="Password"
                  autoComplete="new-password"
                  className={inputClass}
                />
              </div>
              {infoMessage && (
                <div className="text-xs font-semibold text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">{infoMessage}</div>
              )}
              {errorMessage && (
                <div className="text-xs font-semibold text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                  ⚠️ {errorMessage}
                </div>
              )}
              <button
                type="submit"
                disabled={busy}
                className="w-full py-2.5 rounded-lg bg-[#1976D2] hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-bold shadow"
              >
                {busy ? 'Checking...' : 'Sign In'}
              </button>
              <button
                type="button"
                onClick={() => { setMode('REGISTER'); setErrorMessage(null); setInfoMessage(null); }}
                className="w-full py-2.5 rounded-lg border-2 border-[#1976D2] text-[#1976D2] text-sm font-bold hover:bg-blue-50"
              >
                New Employee? Create Account
              </button>
            </form>
          ) : (
            <form onSubmit={handleRegisterSubmit} className="space-y-3" autoComplete="off">
              <div className="text-sm font-black text-slate-900">Employee Registration</div>
              <div className="text-[11px] text-slate-500">Create your official account. You can sign in only after the Administrator approves it.</div>
              <input className={inputClass} placeholder="Full name" value={regName} onChange={e => { setRegName(e.target.value); setErrorMessage(null); }} />
              <input className={inputClass} placeholder="Official Employee ID" value={regId} onChange={e => { setRegId(e.target.value); setErrorMessage(null); }} />
              <select className={inputClass} value={regDesignation} onChange={e => setRegDesignation(e.target.value)}>
                {DESIGNATIONS.map(x => <option key={x.name} value={x.name}>{x.name}</option>)}
              </select>
              {d && d.scope === null ? (
                <select className={inputClass} value={regFloor} onChange={e => setRegFloor(e.target.value as 'SEW_1' | 'SEW_2' | 'SEW_3')}>
                  {FLOOR_CHOICES.map(f => <option key={f.key} value={f.key}>{f.label}</option>)}
                </select>
              ) : (
                <div className="text-[11px] text-slate-500">Dept: {d?.department} · Floor access: {d?.scope === 'ALL' ? 'All Floor' : 'Finishing'} (automatic)</div>
              )}
              <input type="password" className={inputClass} placeholder="Password (at least 6 characters)" value={regPassword} autoComplete="new-password" onChange={e => { setRegPassword(e.target.value); setErrorMessage(null); }} />
              <input type="password" className={inputClass} placeholder="Re-enter password" value={regConfirm} autoComplete="new-password" onChange={e => { setRegConfirm(e.target.value); setErrorMessage(null); }} />
              {errorMessage && (
                <div className="text-xs font-semibold text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">⚠️ {errorMessage}</div>
              )}
              <button type="submit" disabled={busy} className="w-full py-2.5 rounded-lg bg-[#1976D2] hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-bold shadow">
                {busy ? 'Sending...' : 'Submit Registration for Approval'}
              </button>
              <button type="button" onClick={() => { setMode('LOGIN'); setErrorMessage(null); }} className="w-full text-xs font-bold text-[#1976D2] py-1">
                Back to Sign In
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
