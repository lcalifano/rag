import { useState, useEffect, useRef, useCallback } from 'react';
import {
  listSessions,
  createSession,
  updateSession,
  getHistory,
  sendMessage,
} from '../services/api';
import type { ChatSession, ChatMessage } from '../types';

export default function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSession, setActiveSession] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleInput, setTitleInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    loadSessions();
    return () => closeEventSource();
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const loadSessions = async () => {
    try {
      const res = await listSessions();
      setSessions(res.data);
    } catch {
      /* ignore */
    }
  };

  const handleNewSession = async () => {
    try {
      const res = await createSession('Nuova chat');
      setSessions((prev) => [res.data, ...prev]);
      setActiveSession(res.data.id);
      setMessages([]);
    } catch {
      /* ignore */
    }
  };

  const closeEventSource = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  const connectSse = useCallback((sessionId: number) => {
    closeEventSource();

    const token = localStorage.getItem('token');
    const es = new EventSource(`/api/chat/sessions/${sessionId}/stream?token=${token}`);
    eventSourceRef.current = es;

    es.addEventListener('message', (event) => {
      const data = JSON.parse(event.data);
      setMessages((prev) => {
        // Rimuovi eventuali messaggi PENDING e aggiungi la risposta
        const filtered = prev.filter((m) => m.content !== '__PENDING__');
        return [
          ...filtered,
          {
            id: data.messageId,
            role: data.role,
            content: data.content,
            createdAt: new Date().toISOString(),
          },
        ];
      });
      setLoading(false);
      closeEventSource();
    });

    es.onerror = () => {
      // SSE disconnesso — potrebbe essere un timeout o errore di rete.
      // Fallback: ricarica la history per sincronizzarsi
      closeEventSource();
      if (sessionId) {
        getHistory(sessionId).then((res) => {
          const msgs: ChatMessage[] = res.data;
          setMessages(msgs);
          const hasPending = msgs.some(
            (m) => m.role === 'ASSISTANT' && m.content === '__PENDING__'
          );
          if (!hasPending) {
            setLoading(false);
          } else {
            // Riprova la connessione SSE
            connectSse(sessionId);
          }
        }).catch(() => { /* ignore */ });
      }
    };
  }, [closeEventSource]);

  const handleSelectSession = async (sessionId: number) => {
    closeEventSource();
    setLoading(false);
    setActiveSession(sessionId);
    try {
      const res = await getHistory(sessionId);
      const msgs: ChatMessage[] = res.data;
      setMessages(msgs);
      // Se c'e' un messaggio PENDING, connetti SSE
      const hasPending = msgs.some(
        (m) => m.role === 'ASSISTANT' && m.content === '__PENDING__'
      );
      if (hasPending) {
        setLoading(true);
        connectSse(sessionId);
      }
    } catch {
      /* ignore */
    }
  };

  const handleSend = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!input.trim() || !activeSession || loading) return;

    const userMsg = input;
    setInput('');
    if (inputRef.current) inputRef.current.style.height = 'auto';
    setMessages((prev) => [
      ...prev,
      { id: Date.now(), role: 'USER', content: userMsg, createdAt: new Date().toISOString() },
    ]);
    setLoading(true);

    try {
      // Connetti SSE PRIMA di inviare il messaggio, per non perdere eventi
      connectSse(activeSession);
      await sendMessage(activeSession, userMsg);
    } catch {
      closeEventSource();
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'ASSISTANT',
          content: 'Errore nella generazione della risposta. Verifica la configurazione del modello.',
          createdAt: new Date().toISOString(),
        },
      ]);
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleTextareaInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    e.target.style.height = 'auto';
    e.target.style.height = Math.min(e.target.scrollHeight, 150) + 'px';
  };

  const handleRenameSession = async () => {
    if (!activeSession || !titleInput.trim()) {
      setEditingTitle(false);
      return;
    }
    try {
      const res = await updateSession(activeSession, titleInput.trim());
      setSessions((prev) =>
        prev.map((s) => (s.id === activeSession ? { ...s, title: res.data.title } : s))
      );
    } catch {
      /* ignore */
    }
    setEditingTitle(false);
  };

  const startEditingTitle = () => {
    const session = sessions.find(s => s.id === activeSession);
    if (session) {
      setTitleInput(session.title);
      setEditingTitle(true);
    }
  };

  const activeSessionData = sessions.find(s => s.id === activeSession);

  return (
    <div className="flex h-[calc(100vh-6.5rem)] gap-3">
      {/* Sidebar */}
      <div className={`${sidebarOpen ? 'w-72' : 'w-0'} transition-all duration-300 overflow-hidden shrink-0`}>
        <div className="w-72 h-full bg-white/80 backdrop-blur-sm rounded-2xl border border-slate-200/60 flex flex-col dark:bg-slate-800/80 dark:border-slate-700/60">
          <div className="p-3">
            <button
              onClick={handleNewSession}
              className="w-full bg-gradient-to-r from-primary-600 to-primary-700 text-white py-2.5 rounded-xl hover:from-primary-700 hover:to-primary-800 text-sm font-medium transition-all duration-200 shadow-md shadow-primary-500/20 flex items-center justify-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
              </svg>
              Nuova Chat
            </button>
          </div>
          <div className="flex-1 overflow-y-auto px-2 pb-2">
            {sessions.map((session) => (
              <button
                key={session.id}
                onClick={() => handleSelectSession(session.id)}
                className={`w-full text-left px-3 py-2.5 rounded-xl mb-1 text-sm transition-all duration-200 ${
                  activeSession === session.id
                    ? 'bg-primary-50 text-primary-700 shadow-sm dark:bg-primary-900/40 dark:text-primary-300'
                    : 'text-slate-600 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-700/50'
                }`}
              >
                <div className="font-medium truncate flex items-center gap-2">
                  <svg className="w-3.5 h-3.5 shrink-0 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z" />
                  </svg>
                  {session.title}
                </div>
                <div className="text-xs text-slate-400 mt-0.5 pl-5.5 dark:text-slate-500">
                  {new Date(session.updatedAt).toLocaleDateString('it-IT')}
                </div>
              </button>
            ))}
            {sessions.length === 0 && (
              <div className="flex flex-col items-center justify-center py-12 text-slate-400 dark:text-slate-500">
                <svg className="w-8 h-8 mb-2 opacity-40" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 8.511c.884.284 1.5 1.128 1.5 2.097v4.286c0 1.136-.847 2.1-1.98 2.193-.34.027-.68.052-1.02.072v3.091l-3-3c-1.354 0-2.694-.055-4.02-.163a2.115 2.115 0 0 1-.825-.242m9.345-8.334a2.126 2.126 0 0 0-.476-.095 48.64 48.64 0 0 0-8.048 0c-1.131.094-1.976 1.057-1.976 2.192v4.286c0 .837.46 1.58 1.155 1.951m9.345-8.334V6.637c0-1.621-1.152-3.026-2.76-3.235A48.455 48.455 0 0 0 11.25 3c-2.115 0-4.198.137-6.24.402-1.608.209-2.76 1.614-2.76 3.235v6.226c0 1.621 1.152 3.026 2.76 3.235.577.075 1.157.14 1.74.194V21l4.155-4.155" />
                </svg>
                <p className="text-sm">Nessuna chat</p>
                <p className="text-xs mt-1">Creane una nuova!</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Chat area */}
      <div className="flex-1 bg-white/80 backdrop-blur-sm rounded-2xl border border-slate-200/60 flex flex-col min-w-0 dark:bg-slate-800/80 dark:border-slate-700/60">
        {/* Header */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-100 dark:border-slate-700">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 transition-colors dark:hover:bg-slate-700 dark:text-slate-400"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
            </svg>
          </button>
          {activeSessionData && (
            editingTitle ? (
              <input
                autoFocus
                value={titleInput}
                onChange={(e) => setTitleInput(e.target.value)}
                onBlur={handleRenameSession}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleRenameSession();
                  if (e.key === 'Escape') setEditingTitle(false);
                }}
                className="text-sm font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-lg px-2 py-1 focus:outline-none focus:ring-2 focus:ring-primary-500 w-64 dark:text-slate-200 dark:bg-slate-700 dark:border-slate-600"
              />
            ) : (
              <h2
                onClick={startEditingTitle}
                className="text-sm font-medium text-slate-700 truncate cursor-pointer hover:text-primary-600 transition-colors dark:text-slate-200 dark:hover:text-primary-400"
                title="Clicca per rinominare"
              >
                {activeSessionData.title}
              </h2>
            )
          )}
        </div>

        {activeSession ? (
          <>
            {/* Messages */}
            <div className="flex-1 overflow-y-auto px-4 py-6">
              <div className="max-w-3xl mx-auto space-y-6">
                {messages.filter((m) => m.content !== '__PENDING__').map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex gap-3 ${msg.role === 'USER' ? 'justify-end' : 'justify-start'}`}
                  >
                    {msg.role !== 'USER' && (
                      <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center shrink-0 shadow-sm">
                        <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 0 0-2.455 2.456Z" />
                        </svg>
                      </div>
                    )}
                    <div
                      className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap ${
                        msg.role === 'USER'
                          ? 'bg-gradient-to-br from-primary-600 to-primary-700 text-white shadow-md shadow-primary-500/20'
                          : 'bg-slate-50 text-slate-800 border border-slate-100 dark:bg-slate-700 dark:text-slate-200 dark:border-slate-600'
                      }`}
                    >
                      {msg.content}
                    </div>
                    {msg.role === 'USER' && (
                      <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-slate-200 to-slate-300 flex items-center justify-center shrink-0 dark:from-slate-600 dark:to-slate-700">
                        <svg className="w-4 h-4 text-slate-600 dark:text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" />
                        </svg>
                      </div>
                    )}
                  </div>
                ))}
                {loading && (
                  <div className="flex gap-3 justify-start">
                    <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center shrink-0 shadow-sm">
                      <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 0 0-2.455 2.456Z" />
                      </svg>
                    </div>
                    <div className="bg-slate-50 border border-slate-100 rounded-2xl px-4 py-3 dark:bg-slate-700 dark:border-slate-600">
                      <div className="flex gap-1.5">
                        <div className="w-2 h-2 bg-primary-400 rounded-full typing-dot" />
                        <div className="w-2 h-2 bg-primary-400 rounded-full typing-dot" />
                        <div className="w-2 h-2 bg-primary-400 rounded-full typing-dot" />
                      </div>
                    </div>
                  </div>
                )}
                <div ref={messagesEndRef} />
              </div>
            </div>

            {/* Input */}
            <div className="p-4 border-t border-slate-100 dark:border-slate-700">
              <div className="max-w-3xl mx-auto">
                <div className="flex items-end gap-2 bg-slate-50 rounded-2xl border border-slate-200 p-2 focus-within:border-primary-300 focus-within:ring-2 focus-within:ring-primary-500/20 transition-all duration-200 dark:bg-slate-700 dark:border-slate-600 dark:focus-within:border-primary-500">
                  <textarea
                    ref={inputRef}
                    value={input}
                    onChange={handleTextareaInput}
                    onKeyDown={handleKeyDown}
                    placeholder="Scrivi un messaggio..."
                    disabled={loading}
                    rows={1}
                    className="flex-1 bg-transparent px-2 py-1.5 text-sm text-slate-800 placeholder-slate-400 focus:outline-none resize-none dark:text-slate-200 dark:placeholder-slate-500"
                  />
                  <button
                    onClick={() => handleSend()}
                    disabled={loading || !input.trim()}
                    className="p-2 bg-gradient-to-r from-primary-600 to-primary-700 text-white rounded-xl hover:from-primary-700 hover:to-primary-800 disabled:opacity-30 disabled:hover:from-primary-600 transition-all duration-200 shadow-sm"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 12 3.269 3.125A59.769 59.769 0 0 1 21.485 12 59.768 59.768 0 0 1 3.27 20.875L5.999 12Zm0 0h7.5" />
                    </svg>
                  </button>
                </div>
                <p className="text-[11px] text-slate-400 mt-2 text-center dark:text-slate-500">
                  Shift+Enter per andare a capo
                </p>
              </div>
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-primary-100 to-primary-200 rounded-2xl flex items-center justify-center mx-auto mb-4 dark:from-primary-900 dark:to-primary-800">
                <svg className="w-8 h-8 text-primary-600 dark:text-primary-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8.625 12a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H8.25m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0H12m4.125 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 0 1-2.555-.337A5.972 5.972 0 0 1 5.41 20.97a5.969 5.969 0 0 1-.474-.065 4.48 4.48 0 0 0 .978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25Z" />
                </svg>
              </div>
              <h3 className="text-lg font-semibold text-slate-700 mb-1 dark:text-slate-200">Inizia una conversazione</h3>
              <p className="text-sm text-slate-400 mb-6 max-w-sm dark:text-slate-500">
                Seleziona una chat esistente o creane una nuova per iniziare a parlare con l'assistente AI.
              </p>
              <button
                onClick={handleNewSession}
                className="bg-gradient-to-r from-primary-600 to-primary-700 text-white px-6 py-2.5 rounded-xl hover:from-primary-700 hover:to-primary-800 text-sm font-medium transition-all duration-200 shadow-lg shadow-primary-500/25 inline-flex items-center gap-2"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                </svg>
                Nuova Chat
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
