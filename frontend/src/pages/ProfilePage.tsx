import { useState, useEffect } from 'react';
import { getProfile, updateProfile } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import type { UserProfile } from '../types';

export default function ProfilePage() {
  const { setUsername: setAuthUsername } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      const res = await getProfile();
      setProfile(res.data);
      setUsername(res.data.username);
      setEmail(res.data.email);
    } catch {
      setError('Errore nel caricamento del profilo');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (newPassword && newPassword !== confirmPassword) {
      setError('Le password non corrispondono');
      return;
    }

    setLoading(true);
    try {
      const data: { username?: string; email?: string; newPassword?: string } = {};
      if (username !== profile?.username) data.username = username;
      if (email !== profile?.email) data.email = email;
      if (newPassword) data.newPassword = newPassword;

      if (Object.keys(data).length === 0) {
        setError('Nessuna modifica da salvare');
        setLoading(false);
        return;
      }

      const res = await updateProfile(data);
      setProfile(res.data);
      if (data.username) setAuthUsername(res.data.username);
      setNewPassword('');
      setConfirmPassword('');
      setSuccess('Profilo aggiornato con successo');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Errore nel salvataggio');
    } finally {
      setLoading(false);
    }
  };

  if (!profile) {
    return (
      <div className="flex items-center justify-center py-20">
        <svg className="animate-spin h-6 w-6 text-primary-600" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">Profilo</h1>
        <p className="text-sm text-slate-500 mt-1 dark:text-slate-400">Gestisci le tue informazioni personali</p>
      </div>

      {/* Profile header */}
      <div className="bg-white/80 backdrop-blur-sm rounded-2xl border border-slate-200/60 p-6 mb-4 dark:bg-slate-800/80 dark:border-slate-700/60">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 bg-gradient-to-br from-primary-400 to-primary-600 rounded-2xl flex items-center justify-center">
            <span className="text-2xl font-bold text-white uppercase">
              {profile.username.charAt(0)}
            </span>
          </div>
          <div>
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{profile.username}</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400">{profile.email}</p>
            <div className="flex gap-1.5 mt-1">
              {profile.roles.map((role) => (
                <span
                  key={role}
                  className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                    role === 'ROLE_ADMIN'
                      ? 'bg-red-50 text-red-600 border border-red-200 dark:bg-red-950/50 dark:text-red-400 dark:border-red-800'
                      : 'bg-primary-50 text-primary-600 border border-primary-200 dark:bg-primary-950/50 dark:text-primary-400 dark:border-primary-800'
                  }`}
                >
                  {role.replace('ROLE_', '')}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Edit form */}
      <div className="bg-white/80 backdrop-blur-sm rounded-2xl border border-slate-200/60 p-6 dark:bg-slate-800/80 dark:border-slate-700/60">
        <h3 className="text-sm font-semibold text-slate-900 mb-4 dark:text-slate-100">Modifica informazioni</h3>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl mb-4 text-sm flex items-center gap-2 dark:bg-red-950/50 dark:border-red-800 dark:text-red-400">
            <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
            </svg>
            {error}
          </div>
        )}
        {success && (
          <div className="bg-emerald-50 border border-emerald-200 text-emerald-700 px-4 py-3 rounded-xl mb-4 text-sm flex items-center gap-2 dark:bg-emerald-950/50 dark:border-emerald-800 dark:text-emerald-400">
            <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
            {success}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5 dark:text-slate-300">Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-2.5 bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200 text-sm dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5 dark:text-slate-300">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-2.5 bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200 text-sm dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200"
            />
          </div>

          <hr className="border-slate-100 dark:border-slate-700" />

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5 dark:text-slate-300">Nuova password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Lascia vuoto per non cambiarla"
              className="w-full px-4 py-2.5 bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200 text-sm dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200 dark:placeholder-slate-500"
            />
          </div>
          {newPassword && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5 dark:text-slate-300">Conferma password</label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="w-full px-4 py-2.5 bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200 text-sm dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200"
              />
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-gradient-to-r from-primary-600 to-primary-700 text-white py-2.5 rounded-xl hover:from-primary-700 hover:to-primary-800 disabled:opacity-50 font-medium text-sm transition-all duration-200 shadow-lg shadow-primary-500/25"
          >
            {loading ? 'Salvataggio...' : 'Salva modifiche'}
          </button>
        </form>
      </div>
    </div>
  );
}
