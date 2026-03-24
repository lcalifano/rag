import { useState, useEffect, useRef } from 'react';
import {
  listSessions,
  createSession,
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
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    loadSessions();
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

  const handleSelectSession = async (sessionId: number) => {
    setActiveSession(sessionId);
    try {
      const res = await getHistory(sessionId);
      setMessages(res.data);
    } catch {
      /* ignore */
    }
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || !activeSession || loading) return;

    const userMsg = input;
    setInput('');
    setMessages((prev) => [
      ...prev,
      { id: Date.now(), role: 'USER', content: userMsg, createdAt: new Date().toISOString() },
    ]);
    setLoading(true);

    try {
      const res = await sendMessage(activeSession, userMsg);
      setMessages((prev) => [...prev, res.data]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'ASSISTANT',
          content: 'Errore nella generazione della risposta. Verifica la configurazione del modello.',
          createdAt: new Date().toISOString(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-[calc(100vh-8rem)] gap-4">
      {/* Sidebar sessioni */}
      <div className="w-64 bg-white rounded-lg shadow flex flex-col">
        <div className="p-3 border-b">
          <button
            onClick={handleNewSession}
            className="w-full bg-blue-600 text-white py-2 rounded-md hover:bg-blue-700 text-sm font-medium"
          >
            + Nuova Chat
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {sessions.map((session) => (
            <button
              key={session.id}
              onClick={() => handleSelectSession(session.id)}
              className={`w-full text-left px-4 py-3 text-sm border-b border-gray-100 hover:bg-gray-50 ${
                activeSession === session.id ? 'bg-blue-50 text-blue-700' : 'text-gray-700'
              }`}
            >
              <div className="font-medium truncate">{session.title}</div>
              <div className="text-xs text-gray-400 mt-1">
                {new Date(session.updatedAt).toLocaleDateString('it-IT')}
              </div>
            </button>
          ))}
          {sessions.length === 0 && (
            <p className="text-sm text-gray-400 p-4 text-center">
              Nessuna chat. Creane una nuova!
            </p>
          )}
        </div>
      </div>

      {/* Area chat */}
      <div className="flex-1 bg-white rounded-lg shadow flex flex-col">
        {activeSession ? (
          <>
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex ${msg.role === 'USER' ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-[70%] rounded-lg px-4 py-2 text-sm whitespace-pre-wrap ${
                      msg.role === 'USER'
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-100 text-gray-800'
                    }`}
                  >
                    {msg.content}
                  </div>
                </div>
              ))}
              {loading && (
                <div className="flex justify-start">
                  <div className="bg-gray-100 rounded-lg px-4 py-2 text-sm text-gray-500">
                    Sto pensando...
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
            <form onSubmit={handleSend} className="p-4 border-t flex gap-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Scrivi un messaggio..."
                disabled={loading}
                className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
              />
              <button
                type="submit"
                disabled={loading || !input.trim()}
                className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 disabled:opacity-50 text-sm font-medium"
              >
                Invia
              </button>
            </form>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-400">
            Seleziona o crea una chat per iniziare
          </div>
        )}
      </div>
    </div>
  );
}
