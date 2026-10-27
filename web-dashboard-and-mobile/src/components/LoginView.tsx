import React, { useState } from 'react';
import { UserAccount } from '../types';
import {
  signInWebEmployee,
  createWebEmployeeAccount,
  sendOwnerPasswordReset,
  fetchOwnUserRecord,
  bootstrapOwnerAdminRecord,
  bindFirebaseUidToEmployee,
  submitWebRegistration,
  signOutWebCloudSession
} from '../services/firebaseLiveSync';
import { accountFromRecord, saveSessionCache } from '../services/sessionUser';

interface LoginViewProps {
  // Kept for compatibility with the caller. Sign-in is checked by Firebase, never against these.
  users?: UserAccount[];
  onLogin: (user: UserAccount) => void;
  onUpdateAdmin?: (newAdmin: UserAccount) => void;
}

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

const OWNER_EMPLOYEE_ID = '1015198';


const NflLogo: React.FC<{ size?: number }> = ({ size = 72 }) => (
  <svg viewBox="0 0 108 108" width={size} height={size} xmlns="http://www.w3.org/2000/svg" aria-label="NFL logo">
    <path fill="#FFFFFF" d="M0,0h108v108h-108z" /><path fill="#7D909D" d="M10,70 C20,53 35,35 56,29 C64,27 68,28 69,30 C66,32 60,33 54,35 C35,40 22,57 14,70 Z" /><path fill="#92A3AF" d="M16,70 C26,53 39,38 58,34 C65,33 69,33 70,35 C67,37 62,38 56,40 C39,45 27,59 20,70 Z" /><path fill="#A8B7C1" d="M22,70 C31,54 44,42 61,38 C67,37 71,38 72,40 C69,41 64,43 58,44 C43,49 32,61 26,70 Z" /><path fill="#0F172A" d="M54,36 C64,36 72,38 75,41 C77,44 76,53 69,56 L54,56 C62,50 63,45 61,42 C59,39 55,39 51,39 Z" /><path fill="#0F172A" d="M35,49 L55,49 C46,55 45,61 47,65 C49,68 54,68 58,68 C47,70 39,68 36,65 C33,61 34,54 35,49 Z" /><path fill="#44F518" d="M43,73 C54,72 67,68 79,54 C85,47 89,42 91,40 L88,40 C82,46 74,56 65,64 C56,70 48,73 43,73 Z" /><path fill="#34E00A" d="M49,76 C60,75 73,69 85,55 C90,48 94,42 96,40 L93,40 C87,47 78,58 70,66 C61,73 54,75 49,76 Z" /><path fill="#24CC00" d="M56,78 C67,77 79,71 90,57 C95,49 99,43 101,40 L98,40 C92,48 83,60 75,68 C67,75 61,77 56,78 Z" />
  </svg>
);

const Icon: React.FC<{ d: string; size?: number }> = ({ d, size = 22 }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round">
    <path d={d} />
  </svg>
);

const ICON = {
  id: 'M5.5 5h13A2.5 2.5 0 0 1 21 7.5v10a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-10A2.5 2.5 0 0 1 5.5 5zM9 12a2 2 0 1 0 0-.01M5.8 17c.6-1.7 1.8-2.4 3.2-2.4s2.6.7 3.2 2.4M14.5 10h4M14.5 13.5h4',
  lock: 'M7.5 10.5h9a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 10.5V8a4 4 0 0 1 8 0v2.5',
  eye: 'M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z',
  eyeOff: 'M2 12s3.6-6.5 10-6.5c2 0 3.7.6 5.1 1.4M22 12s-3.6 6.5-10 6.5c-2 0-3.7-.6-5.1-1.4M4 4l16 16',
  login: 'M10 17l5-5-5-5M15 12H3M13 4h6a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6',
  addUser: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.6-3.6 3.2-5.5 6.5-5.5s5.9 1.9 6.5 5.5M19 8v6M16 11h6',
  user: 'M12 11.5a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2zM4.5 20c.7-4 3.6-6 7.5-6s6.8 2 7.5 6',
  badge: 'M3 8h18v11H3zM9 8V5h6v3M3 13h18',
  back: 'M19 12H5M11 6l-6 6 6 6',
  bars: 'M6 20V11M12 20V4M18 20v-6',
  verified: 'M12 2.8l2.4 1.7 2.9-.1 1 2.7 2.3 1.8-.9 2.8.9 2.8-2.3 1.8-1 2.7-2.9-.1L12 21.2l-2.4-1.7-2.9.1-1-2.7-2.3-1.8.9-2.8-.9-2.8 2.3-1.8 1-2.7 2.9.1zM8.5 12.2l2.4 2.4 4.6-4.9',
  gear: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2',
  sync: 'M20 11a8 8 0 0 0-14-4M4 4v4h4M4 13a8 8 0 0 0 14 4M20 20v-4h-4'
};

const fieldStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 12, background: '#F8FAFC', border: '1.5px solid #DCE5F2',
  borderRadius: 16, padding: '0 14px', height: 56
};
const bareInput: React.CSSProperties = {
  flex: 1, minWidth: 0, background: 'transparent', border: 0, outline: 'none', color: '#0F172A', fontSize: 15.5, height: '100%'
};

/** A field with a left icon, like the Android app. Password fields get an eye button. */
const Field: React.FC<{
  icon: string; value: string; placeholder: string; onChange: (v: string) => void;
  type?: 'text' | 'password'; autoComplete?: string;
}> = ({ icon, value, placeholder, onChange, type = 'text', autoComplete = 'off' }) => {
  const [show, setShow] = useState(false);
  const [focus, setFocus] = useState(false);
  const isPassword = type === 'password';
  return (
    <div style={{ ...fieldStyle, borderColor: focus ? '#0755F5' : '#DCE5F2', boxShadow: focus ? '0 0 0 3px rgba(7,85,245,.12)' : 'none' }}>
      <span style={{ color: focus ? '#0755F5' : '#64748B', display: 'grid' }}><Icon d={icon} /></span>
      <input
        type={isPassword && !show ? 'password' : 'text'}
        value={value}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        onChange={e => onChange(e.target.value)}
        onFocus={() => setFocus(true)}
        onBlur={() => setFocus(false)}
        style={bareInput}
      />
      {isPassword && (
        <button
          type="button"
          aria-label={show ? 'Hide password' : 'Show password'}
          onClick={() => setShow(v => !v)}
          style={{ background: 'none', border: 0, color: '#64748B', cursor: 'pointer', display: 'grid', padding: 4, borderRadius: 8 }}
        >
          <Icon d={show ? ICON.eyeOff : ICON.eye} />
        </button>
      )}
    </div>
  );
};

const primaryBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, height: 54, width: '100%', border: 0,
  borderRadius: 16, background: '#0755F5', color: '#fff', fontSize: 16, fontWeight: 800, cursor: 'pointer',
  boxShadow: '0 6px 16px rgba(7,85,245,.27)'
};
const outlineBtn: React.CSSProperties = {
  ...primaryBtn, background: '#fff', color: '#1D4ED8', border: '2px solid #0755F5', boxShadow: 'none'
};

const Tile: React.FC<{ color: string; icon: string; label: string }> = ({ color, icon, label }) => (
  <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, textAlign: 'center', padding: '0 4px' }}>
    <span style={{ width: 46, height: 46, borderRadius: 12, display: 'grid', placeItems: 'center', color: '#fff', background: color }}>
      <Icon d={icon} size={24} />
    </span>
    <b style={{ fontSize: 11, fontWeight: 600, color: '#E2E8F0', lineHeight: 1.25 }}>{label}</b>
  </div>
);

const Message: React.FC<{ kind: 'error' | 'info'; text: string }> = ({ kind, text }) => (
  <div style={{
    background: kind === 'error' ? '#FEF2F2' : '#FFFBEB', border: `1px solid ${kind === 'error' ? '#FCA5A5' : '#FCD34D'}`,
    color: kind === 'error' ? '#7F1D1D' : '#92400E', borderRadius: 12, padding: '10px 14px', fontSize: 13.5
  }}>{text}</div>
);

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
      setPasswordInput('');
      const account = accountFromRecord(rec, employeeId);
      saveSessionCache(uid, account);
      onLogin(account);
    } catch (err: any) {
      setErrorMessage(err?.message ? String(err.message) : 'Could not sign in. Check the internet connection.');
    } finally {
      setBusy(false);
    }
  };

  const handleForgotPassword = async () => {
    if (busy) return;
    setErrorMessage(null);
    const input = usernameInput.trim();
    if (!input.includes('@')) {
      setInfoMessage('Employees: ask the Administrator to reset your password. Administrator: type your e-mail address in the Employee ID box, then press Forgot password.');
      return;
    }
    setBusy(true);
    const res = await sendOwnerPasswordReset(input);
    setBusy(false);
    if (res.ok) { setInfoMessage(res.message); } else { setInfoMessage(null); setErrorMessage(res.message); }
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

  const d = DESIGNATIONS.find(x => x.name === regDesignation);
  const bg = `${import.meta.env.BASE_URL}login-bg.jpg`;
  const selectStyle: React.CSSProperties = { ...fieldStyle, width: '100%', color: '#0F172A', fontSize: 15.5, outline: 'none', appearance: 'auto' };

  return (
    <div style={{ minHeight: '100vh', background: '#061B43', position: 'relative', overflow: 'hidden' }}>
      <div style={{
        position: 'absolute', left: 0, right: 0, top: 0, height: 'clamp(180px, 35vh, 310px)',
        background: `linear-gradient(180deg, rgba(6,27,67,.2), rgba(6,27,67,.53), rgba(6,27,67,.93), #061B43), url(${bg}) center/cover no-repeat`
      }} />
      <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '34px 20px 26px', minHeight: '100vh' }}>
        <div style={{ width: 86, height: 86, borderRadius: '50%', background: '#fff', boxShadow: '0 8px 22px rgba(0,0,0,.45)', display: 'grid', placeItems: 'center', overflow: 'hidden' }}>
          <NflLogo size={72} />
        </div>
        <div style={{ fontSize: 34, fontWeight: 900, letterSpacing: 1.2, color: '#fff', margin: '14px 0 20px', lineHeight: 1 }}>
          NFL <span style={{ color: '#1677FF' }}>LIVE</span>
        </div>

        <div style={{ width: '100%', maxWidth: 440, background: '#fff', borderRadius: 26, padding: '24px 22px', boxShadow: '0 18px 50px rgba(0,0,0,.6)', color: '#0F172A' }}>
          {mode === 'LOGIN' ? (
            <form onSubmit={handleLoginSubmit} style={{ display: 'grid', gap: 14 }} autoComplete="off">
              <Field icon={ICON.id} value={usernameInput} placeholder="Employee ID" onChange={v => { setUsernameInput(v); setErrorMessage(null); }} />
              <Field icon={ICON.lock} type="password" autoComplete="new-password" value={passwordInput} placeholder="Password" onChange={v => { setPasswordInput(v); setErrorMessage(null); }} />
              <button type="button" onClick={handleForgotPassword} style={{ background: 'none', border: 0, color: '#1D4ED8', fontWeight: 700, fontSize: 13, textAlign: 'right', cursor: 'pointer', padding: 0 }}>Forgot password?</button>
              {infoMessage && <Message kind="info" text={infoMessage} />}
              {errorMessage && <Message kind="error" text={errorMessage} />}
              <button type="submit" disabled={busy} style={{ ...primaryBtn, opacity: busy ? 0.65 : 1 }}>
                <Icon d={ICON.login} /> <span>{busy ? 'Checking...' : 'Sign In'}</span>
              </button>
              <button type="button" style={outlineBtn} onClick={() => { setMode('REGISTER'); setErrorMessage(null); setInfoMessage(null); }}>
                <Icon d={ICON.addUser} /> <span>New Employee? Create Account</span>
              </button>
            </form>
          ) : (
            <form onSubmit={handleRegisterSubmit} style={{ display: 'grid', gap: 12 }} autoComplete="off">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '-6px 0 2px' }}>
                <button type="button" aria-label="Back to Sign In" onClick={() => { setMode('LOGIN'); setErrorMessage(null); }}
                  style={{ background: 'none', border: 0, color: '#0F172A', cursor: 'pointer', display: 'grid', padding: 8, borderRadius: 10 }}>
                  <Icon d={ICON.back} />
                </button>
                <div>
                  <b style={{ display: 'block', fontSize: 16 }}>Employee Registration</b>
                  <small style={{ color: '#64748B', fontSize: 11.5 }}>Create your official account for approval</small>
                </div>
              </div>
              <Field icon={ICON.user} value={regName} placeholder="Enter your full official name" onChange={v => { setRegName(v); setErrorMessage(null); }} />
              <Field icon={ICON.id} value={regId} placeholder="Enter official Employee ID" onChange={v => { setRegId(v); setErrorMessage(null); }} />
              <select value={regDesignation} onChange={e => setRegDesignation(e.target.value)} style={selectStyle}>
                {DESIGNATIONS.map(x => <option key={x.name} value={x.name}>{x.name}</option>)}
              </select>
              {d && d.scope === null ? (
                <select value={regFloor} onChange={e => setRegFloor(e.target.value as 'SEW_1' | 'SEW_2' | 'SEW_3')} style={selectStyle}>
                  {FLOOR_CHOICES.map(f => <option key={f.key} value={f.key}>{f.label}</option>)}
                </select>
              ) : (
                <div style={{ fontSize: 11.5, color: '#64748B', margin: '0 4px' }}>
                  Dept: {d?.department} · Auto-Scope: {d?.scope === 'ALL' ? 'All Floor' : 'Finishing'}
                </div>
              )}
              <Field icon={ICON.lock} type="password" autoComplete="new-password" value={regPassword} placeholder="At least 6 characters" onChange={v => { setRegPassword(v); setErrorMessage(null); }} />
              <Field icon={ICON.lock} type="password" autoComplete="new-password" value={regConfirm} placeholder="Re-enter password" onChange={v => { setRegConfirm(v); setErrorMessage(null); }} />
              {errorMessage && <Message kind="error" text={errorMessage} />}
              <button type="submit" disabled={busy} style={{ ...primaryBtn, opacity: busy ? 0.65 : 1 }}>
                {busy ? 'Sending...' : 'Submit Registration for Approval'}
              </button>
              <div style={{ textAlign: 'center', fontSize: 11.5, color: '#64748B' }}>You get no access until an Administrator approves you.</div>
            </form>
          )}
        </div>

        <div style={{
          width: '100%', maxWidth: 440, marginTop: 24, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
          background: 'rgba(12,37,86,.65)', border: '1px solid rgba(30,58,138,.45)', borderRadius: 18, padding: '14px 8px'
        }}>
          <Tile color="#1D72FE" icon={ICON.bars} label="Production Monitoring" />
          <span style={{ width: 1, alignSelf: 'stretch', margin: '6px 0', background: 'rgba(56,189,248,.25)' }} />
          <Tile color="#10B981" icon={ICON.verified} label="Quality Management" />
          <span style={{ width: 1, alignSelf: 'stretch', margin: '6px 0', background: 'rgba(56,189,248,.25)' }} />
          <Tile color="#F59E0B" icon={ICON.gear} label="Operations Control" />
          <span style={{ width: 1, alignSelf: 'stretch', margin: '6px 0', background: 'rgba(56,189,248,.25)' }} />
          <Tile color="#8B5CF6" icon={ICON.sync} label="Real-time Data" />
        </div>

        <div style={{ marginTop: 22, fontSize: 11, letterSpacing: 2, color: '#94A3B8' }}>NORTHERN FASHIONS LIMITED</div>
        <div style={{ width: 100, height: 3, borderRadius: 2, background: '#1677FF', marginTop: 8 }} />
      </div>
    </div>
  );
};
