import { useState, useEffect } from 'react';
import {
  adminGetAllUsers,
  adminUpdateUser,
  adminDeleteUser,
  adminGetAllDocuments,
} from '../services/api';
import type { UserProfile, Document } from '../types';

type Tab = 'users' | 'documents';

export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('users');
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [editingUser, setEditingUser] = useState<UserProfile | null>(null);
  const [editForm, setEditForm] = useState({ username: '', email: '', newPassword: '', roles: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (tab === 'users') loadUsers();
    else loadDocuments();
  }, [tab]);

  const loadUsers = async () => {
    try {
      const res = await adminGetAllUsers();
      setUsers(res.data);
    } catch {
      setError('Errore nel caricamento utenti');
    }
  };

  const loadDocuments = async () => {
    try {
      const res = await adminGetAllDocuments();
      setDocuments(res.data);
    } catch {
      setError('Errore nel caricamento documenti');
    }
  };

  const startEdit = (user: UserProfile) => {
    setEditingUser(user);
    setEditForm({
      username: user.username,
      email: user.email,
      newPassword: '',
      roles: user.roles.join(', '),
    });
    setError('');
    setSuccess('');
  };

  const handleSaveUser = async () => {
    if (!editingUser) return;
    setError('');
    setSuccess('');
    try {
      const data: { username?: string; email?: string; newPassword?: string; roles?: string[] } = {};
      if (editForm.username !== editingUser.username) data.username = editForm.username;
      if (editForm.email !== editingUser.email) data.email = editForm.email;
      if (editForm.newPassword) data.newPassword = editForm.newPassword;
      const newRoles = editForm.roles.split(',').map((r) => r.trim()).filter(Boolean);
      if (JSON.stringify(newRoles) !== JSON.stringify(editingUser.roles)) data.roles = newRoles;

      await adminUpdateUser(editingUser.id, data);
      setEditingUser(null);
      setSuccess('Utente aggiornato');
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Errore nel salvataggio');
    }
  };

  const handleDeleteUser = async (id: number, username: string) => {
    if (!confirm(`Eliminare l'utente "${username}"?`)) return;
    try {
      await adminDeleteUser(id);
      setUsers((prev) => prev.filter((u) => u.id !== id));
      setSuccess('Utente eliminato');
    } catch {
      setError('Errore nella cancellazione');
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const statusConfig: Record<string, { bg: string; text: string; dot: string; label: string }> = {
    READY: { bg: 'bg-emerald-50 dark:bg-emerald-950/50', text: 'text-emerald-700 dark:text-emerald-400', dot: 'bg-emerald-500', label: 'Pronto' },
    PROCESSING: { bg: 'bg-amber-50 dark:bg-amber-950/50', text: 'text-amber-700 dark:text-amber-400', dot: 'bg-amber-500', label: 'Elaborazione' },
    FAILED: { bg: 'bg-red-50 dark:bg-red-950/50', text: 'text-red-700 dark:text-red-400', dot: 'bg-red-500', label: 'Errore' },
    UPLOADED: { bg: 'bg-blue-50 dark:bg-blue-950/50', text: 'text-blue-700 dark:text-blue-400', dot: 'bg-blue-500', label: 'Caricato' },
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">Pannello Admin</h1>
        <p className="text-sm text-slate-500 mt-1 dark:text-slate-400">Gestisci utenti e documenti della piattaforma</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-xl p-1 mb-6 w-fit dark:bg-slate-800">
        <button
          onClick={() => setTab('users')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
            tab === 'users'
              ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-slate-100'
              : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          Utenti ({users.length})
        </button>
        <button
          onClick={() => setTab('documents')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
            tab === 'documents'
              ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-slate-100'
              : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300'
          }`}
        >
          Documenti ({documents.length})
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl mb-4 text-sm flex items-center gap-2 dark:bg-red-950/50 dark:border-red-800 dark:text-red-400">
          <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
          </svg>
          {error}
          <button onClick={() => setError('')} className="ml-auto text-red-400 hover:text-red-600 dark:text-red-500 dark:hover:text-red-300">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      )}
      {success && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-700 px-4 py-3 rounded-xl mb-4 text-sm flex items-center gap-2 dark:bg-emerald-950/50 dark:border-emerald-800 dark:text-emerald-400">
          <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
          </svg>
          {success}
          <button onClick={() => setSuccess('')} className="ml-auto text-emerald-400 hover:text-emerald-600 dark:text-emerald-500 dark:hover:text-emerald-300">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      )}

      {/* Users tab */}
      {tab === 'users' && (
        <div className="grid gap-3">
          {/* Edit modal */}
          {editingUser && (
            <div className="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
              <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-md dark:bg-slate-800">
                <h3 className="text-lg font-semibold text-slate-900 mb-4 dark:text-slate-100">
                  Modifica utente: {editingUser.username}
                </h3>
                <div className="space-y-3">
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1 dark:text-slate-300">Username</label>
                    <input
                      type="text"
                      value={editForm.username}
                      onChange={(e) => setEditForm({ ...editForm, username: e.target.value })}
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1 dark:text-slate-300">Email</label>
                    <input
                      type="email"
                      value={editForm.email}
                      onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1 dark:text-slate-300">Nuova password</label>
                    <input
                      type="password"
                      value={editForm.newPassword}
                      onChange={(e) => setEditForm({ ...editForm, newPassword: e.target.value })}
                      placeholder="Lascia vuoto per non cambiarla"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200 dark:placeholder-slate-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1 dark:text-slate-300">Ruoli</label>
                    <input
                      type="text"
                      value={editForm.roles}
                      onChange={(e) => setEditForm({ ...editForm, roles: e.target.value })}
                      placeholder="ROLE_USER, ROLE_ADMIN"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 dark:bg-slate-700 dark:border-slate-600 dark:text-slate-200 dark:placeholder-slate-500"
                    />
                    <p className="text-[11px] text-slate-400 mt-1 dark:text-slate-500">Separati da virgola</p>
                  </div>
                </div>
                {error && (
                  <div className="bg-red-50 text-red-700 px-3 py-2 rounded-lg mt-3 text-sm dark:bg-red-950/50 dark:text-red-400">{error}</div>
                )}
                <div className="flex gap-2 mt-5">
                  <button
                    onClick={() => setEditingUser(null)}
                    className="flex-1 px-4 py-2 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors dark:border-slate-600 dark:text-slate-400 dark:hover:bg-slate-700"
                  >
                    Annulla
                  </button>
                  <button
                    onClick={handleSaveUser}
                    className="flex-1 bg-gradient-to-r from-primary-600 to-primary-700 text-white px-4 py-2 rounded-xl text-sm font-medium hover:from-primary-700 hover:to-primary-800 transition-all shadow-md shadow-primary-500/20"
                  >
                    Salva
                  </button>
                </div>
              </div>
            </div>
          )}

          {users.map((user) => (
            <div
              key={user.id}
              className="bg-white/80 backdrop-blur-sm rounded-xl border border-slate-200/60 p-4 hover:border-slate-300 transition-all duration-200 group dark:bg-slate-800/80 dark:border-slate-700/60 dark:hover:border-slate-600"
            >
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 bg-gradient-to-br from-primary-400 to-primary-600 rounded-xl flex items-center justify-center shrink-0">
                  <span className="text-sm font-bold text-white uppercase">
                    {user.username.charAt(0)}
                  </span>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">{user.username}</h3>
                    {user.roles.map((role) => (
                      <span
                        key={role}
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                          role === 'ROLE_ADMIN'
                            ? 'bg-red-50 text-red-600 border border-red-200 dark:bg-red-950/50 dark:text-red-400 dark:border-red-800'
                            : 'bg-slate-50 text-slate-500 border border-slate-200 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600'
                        }`}
                      >
                        {role.replace('ROLE_', '')}
                      </span>
                    ))}
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5 dark:text-slate-500">{user.email} &middot; ID: {user.id}</p>
                </div>
                <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    onClick={() => startEdit(user)}
                    className="p-2 rounded-lg text-slate-400 hover:text-primary-600 hover:bg-primary-50 transition-all dark:text-slate-500 dark:hover:text-primary-400 dark:hover:bg-primary-950/50"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L10.582 16.07a4.5 4.5 0 0 1-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0 1 15.75 21H5.25A2.25 2.25 0 0 1 3 18.75V8.25A2.25 2.25 0 0 1 5.25 6H10" />
                    </svg>
                  </button>
                  <button
                    onClick={() => handleDeleteUser(user.id, user.username)}
                    className="p-2 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-all dark:text-slate-500 dark:hover:text-red-400 dark:hover:bg-red-950/50"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Documents tab */}
      {tab === 'documents' && (
        <div className="grid gap-3">
          {documents.length === 0 ? (
            <div className="bg-white/80 backdrop-blur-sm rounded-2xl border border-slate-200/60 p-12 text-center dark:bg-slate-800/80 dark:border-slate-700/60">
              <p className="text-sm text-slate-400 dark:text-slate-500">Nessun documento nella piattaforma</p>
            </div>
          ) : (
            documents.map((doc) => {
              const config = statusConfig[doc.status] || { bg: 'bg-slate-50 dark:bg-slate-800', text: 'text-slate-700 dark:text-slate-300', dot: 'bg-slate-500', label: doc.status };
              return (
                <div
                  key={doc.id}
                  className="bg-white/80 backdrop-blur-sm rounded-xl border border-slate-200/60 p-4 hover:border-slate-300 transition-all duration-200 dark:bg-slate-800/80 dark:border-slate-700/60 dark:hover:border-slate-600"
                >
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 bg-red-50 rounded-xl flex items-center justify-center shrink-0 dark:bg-red-950/50">
                      <svg className="w-5 h-5 text-red-500 dark:text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z" />
                      </svg>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-3">
                        <h3 className="text-sm font-medium text-slate-900 truncate dark:text-slate-100">{doc.originalFilename}</h3>
                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${config.bg} ${config.text}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${config.dot} ${doc.status === 'PROCESSING' ? 'animate-pulse' : ''}`} />
                          {config.label}
                        </span>
                      </div>
                      <div className="flex items-center gap-4 mt-1 text-xs text-slate-400 dark:text-slate-500">
                        <span>Utente ID: {doc.userId}</span>
                        <span>{formatSize(doc.fileSize)}</span>
                        <span>{doc.totalChunks ?? '-'} chunk</span>
                        <span>{new Date(doc.createdAt).toLocaleDateString('it-IT')}</span>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
