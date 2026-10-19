import React, { useState } from 'react';
import { UserAccount, UserRole } from '../types';

interface LoginViewProps {
  users: UserAccount[];
  onLogin: (user: UserAccount) => void;
  onUpdateAdmin?: (newAdmin: UserAccount) => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ users, onLogin, onUpdateAdmin }) => {
  const [usernameInput, setUsernameInput] = useState('supervisor');
  const [passwordInput, setPasswordInput] = useState('123');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showAdminSetup, setShowAdminSetup] = useState(false);

  // Admin initial setup state
  const [adminName, setAdminName] = useState('System Admin');
  const [adminUsername, setAdminUsername] = useState('admin');
  const [adminPassword, setAdminPassword] = useState('admin123');

  const handleLoginSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!usernameInput.trim()) {
      setErrorMessage('Please enter your User ID.');
      return;
    }

    const trimmed = usernameInput.trim().toLowerCase();
    const foundUser = users.find(
      u => u.username.toLowerCase() === trimmed || u.id.toLowerCase() === trimmed
    );

    if (foundUser) {
      setErrorMessage(null);
      onLogin(foundUser);
    } else {
      setErrorMessage(`User ID "${usernameInput}" not found. Try "admin", "supervisor", "mechanic", etc.`);
    }
  };

  const handleSaveAdminSetup = (e: React.FormEvent) => {
    e.preventDefault();
    if (!adminUsername.trim()) return;

    const newAdminUser: UserAccount = {
      id: 'u-admin-custom',
      name: adminName.trim() || 'System Administrator',
      username: adminUsername.trim().toLowerCase(),
      role: 'ADMIN',
      floor: 'All'
    };

    if (onUpdateAdmin) {
      onUpdateAdmin(newAdminUser);
    }
    setShowAdminSetup(false);
    onLogin(newAdminUser);
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6">
        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 text-white font-black text-2xl shadow-lg shadow-blue-500/20">
            NOS
          </div>
          <h1 className="text-2xl font-black text-white tracking-tight">NOS Live</h1>
          <p className="text-xs text-slate-400 font-medium">
            Northern Operating System • Industrial Factory MES
          </p>
        </div>

        {/* Login Card */}
        <div className="bg-white rounded-2xl p-6 shadow-2xl border border-slate-200/80 space-y-5">
          <div>
            <h2 className="text-lg font-black text-slate-900">Sign In to Factory System</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Enter your assigned User ID and Password
            </p>
          </div>

          <form onSubmit={handleLoginSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                User ID / Username
              </label>
              <input
                type="text"
                value={usernameInput}
                onChange={e => {
                  setUsernameInput(e.target.value);
                  setErrorMessage(null);
                }}
                placeholder="e.g. admin, supervisor, mechanic"
                className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                Password
              </label>
              <input
                type="password"
                value={passwordInput}
                onChange={e => {
                  setPasswordInput(e.target.value);
                  setErrorMessage(null);
                }}
                placeholder="Enter password"
                className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {errorMessage && (
              <div className="bg-red-50 text-red-700 text-xs font-bold p-3 rounded-lg border border-red-200">
                ⚠️ {errorMessage}
              </div>
            )}

            <button
              type="submit"
              className="w-full py-2.5 rounded-lg bg-[#1976D2] hover:bg-blue-700 text-white text-sm font-bold shadow-md transition"
            >
              Sign In to System
            </button>
          </form>

          <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs">
            <button
              type="button"
              onClick={() => setShowAdminSetup(true)}
              className="text-blue-600 hover:text-blue-800 font-bold"
            >
              ⚙️ Setup Initial Admin Credentials
            </button>
            <span className="text-slate-400">Pass: 123</span>
          </div>
        </div>

        {/* Quick Role Tester Cards */}
        <div className="bg-slate-900/90 rounded-2xl p-4 border border-slate-800 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-extrabold text-slate-300">
              Quick Role Test Logins (Tap to Sign In):
            </span>
            <span className="text-[10px] text-blue-400 font-bold">1-Click</span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-xs">
            {users.slice(0, 6).map(u => (
              <button
                key={u.id}
                onClick={() => onLogin(u)}
                className="text-left p-2 rounded-xl bg-slate-950/80 hover:bg-blue-950/80 border border-slate-800 hover:border-blue-600 transition group"
              >
                <div className="font-bold text-white group-hover:text-blue-300 truncate">
                  {u.name}
                </div>
                <div className="flex items-center justify-between text-[10px] text-slate-400 mt-1">
                  <span>@{u.username}</span>
                  <span className="bg-slate-800 text-slate-200 px-1.5 py-0.5 rounded font-black text-[9px]">
                    {u.role.replace('_', ' ')}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Admin Setup Dialog Modal */}
      {showAdminSetup}
      {showAdminSetup && (
        <div className="fixed inset-0 bg-slate-950/80 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-5 shadow-2xl border border-slate-200 space-y-4">
            <div className="flex items-center justify-between border-b pb-2">
              <h3 className="text-sm font-black text-slate-900">
                ⚙️ Setup Initial Admin ID
              </h3>
              <button
                onClick={() => setShowAdminSetup(false)}
                className="text-slate-400 hover:text-slate-700 font-bold"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-slate-600">
              Set the primary Administrator ID & password to control role permissions and task assignments:
            </p>

            <form onSubmit={handleSaveAdminSetup} className="space-y-3">
              <div>
                <label className="block text-[11px] font-bold text-slate-700 mb-1">
                  Admin Full Name
                </label>
                <input
                  type="text"
                  value={adminName}
                  onChange={e => setAdminName(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-xs font-semibold"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-700 mb-1">
                  Admin ID / Username
                </label>
                <input
                  type="text"
                  value={adminUsername}
                  onChange={e => setAdminUsername(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-xs font-semibold"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-700 mb-1">
                  Admin Password
                </label>
                <input
                  type="password"
                  value={adminPassword}
                  onChange={e => setAdminPassword(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-xs font-semibold"
                />
              </div>

              <div className="pt-2 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAdminSetup(false)}
                  className="px-3 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 text-xs font-bold bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Save & Login as Admin
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
