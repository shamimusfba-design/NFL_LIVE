import React, { useState } from 'react';
import { UserAccount, UserRole, FloorId } from '../types';
import { fetchCloudLoginUser, verifyCloudPassword } from '../services/firebaseLiveSync';

interface LoginViewProps {
  // Kept for compatibility with the caller. Login is checked against the cloud accounts, never against these.
  users?: UserAccount[];
  onLogin: (user: UserAccount) => void;
  onUpdateAdmin?: (newAdmin: UserAccount) => void;
}

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

export const LoginView: React.FC<LoginViewProps> = ({ onLogin }) => {
  // Nothing is pre-filled: no default ID and no default password.
  const [usernameInput, setUsernameInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    const id = usernameInput.trim();
    if (!id || !passwordInput.trim()) {
      setErrorMessage('Enter your Employee ID and password.');
      return;
    }
    setBusy(true);
    setErrorMessage(null);
    try {
      const account = await fetchCloudLoginUser(id);
      // One message for "no such ID" and "wrong password" so IDs cannot be probed.
      const ok = account ? await verifyCloudPassword(passwordInput, account.passwordHash, account.passwordSalt) : false;
      if (!account || !ok) {
        setErrorMessage('Employee ID or password is not correct.');
        return;
      }
      if (account.accountStatus !== 'APPROVED' || !account.isActive) {
        setErrorMessage('This account is not approved or is not active. Please contact the Administrator.');
        return;
      }
      if (account.mustChangePassword) {
        setErrorMessage('You must change your password first. Sign in once on the Android app and set a new password.');
        return;
      }
      const role = (KNOWN_ROLES as string[]).includes(account.role)
        ? (account.role as UserRole)
        : ('MANAGEMENT_VIEW' as UserRole); // unknown role: view only
      setPasswordInput('');
      onLogin({
        id: account.id,
        name: account.name,
        username: account.id.toLowerCase(),
        role,
        floor: toFloorId(account.floor)
      });
    } catch (err: any) {
      setErrorMessage(err?.message ? String(err.message) : 'Could not sign in. Check the internet connection.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6">
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 text-white font-black text-2xl shadow-lg shadow-blue-500/20">
            NFL
          </div>
          <h1 className="text-2xl font-black text-white tracking-tight">NFL LIVE — Desktop Edition</h1>
          <p className="text-xs text-slate-400 font-medium">
            Sign in with the same Employee ID and password as the Android app.
          </p>
        </div>

        <div className="bg-white rounded-2xl p-6 shadow-2xl border border-slate-200/80 space-y-5">
          <form onSubmit={handleLoginSubmit} className="space-y-4" autoComplete="off">
            <div className="space-y-1">
              <label className="block text-xs font-bold text-slate-600">Employee ID</label>
              <input
                type="text"
                value={usernameInput}
                onChange={e => { setUsernameInput(e.target.value); setErrorMessage(null); }}
                placeholder="Employee ID"
                autoComplete="off"
                className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm text-slate-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm text-slate-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {errorMessage && (
              <div className="text-xs font-semibold text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                ⚠️ {errorMessage}
              </div>
            )}

            <button
              type="submit"
              disabled={busy}
              className="w-full py-2.5 rounded-lg bg-[#1976D2] hover:bg-blue-700 disabled:opacity-60 text-white text-sm font-bold shadow-md transition"
            >
              {busy ? 'Checking...' : 'Sign In'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
