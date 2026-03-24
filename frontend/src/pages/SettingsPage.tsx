import { useState, useEffect } from 'react';
import { getMyLlmConfigs, saveLlmConfig } from '../services/api';
import type { LlmConfig } from '../types';

const PROVIDERS = ['OPENAI', 'ANTHROPIC', 'OLLAMA'] as const;

export default function SettingsPage() {
  const [configs, setConfigs] = useState<LlmConfig[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const [provider, setProvider] = useState('OPENAI');
  const [modelName, setModelName] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [ollamaUrl, setOllamaUrl] = useState('http://localhost:11434');
  const [temperature, setTemperature] = useState(0.7);
  const [isActive, setIsActive] = useState(true);

  useEffect(() => {
    loadConfigs();
  }, []);

  const loadConfigs = async () => {
    try {
      const res = await getMyLlmConfigs();
      setConfigs(res.data);
    } catch {
      /* ignore */
    }
  };

  const resetForm = () => {
    setProvider('OPENAI');
    setModelName('');
    setApiKey('');
    setOllamaUrl('http://localhost:11434');
    setTemperature(0.7);
    setIsActive(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await saveLlmConfig({
        provider,
        modelName,
        apiKey: provider !== 'OLLAMA' ? apiKey : undefined,
        ollamaUrl: provider === 'OLLAMA' ? ollamaUrl : undefined,
        temperature,
        isActive,
      });
      setSuccess('Configurazione salvata!');
      setShowForm(false);
      resetForm();
      loadConfigs();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Errore nel salvataggio');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Impostazioni Modelli</h1>
        <button
          onClick={() => {
            setShowForm(!showForm);
            setError('');
            setSuccess('');
          }}
          className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 text-sm font-medium"
        >
          {showForm ? 'Annulla' : '+ Aggiungi Provider'}
        </button>
      </div>

      {error && <div className="bg-red-50 text-red-700 p-3 rounded mb-4 text-sm">{error}</div>}
      {success && <div className="bg-green-50 text-green-700 p-3 rounded mb-4 text-sm">{success}</div>}

      {showForm && (
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Nuova Configurazione</h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Provider</label>
                <select
                  value={provider}
                  onChange={(e) => setProvider(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {PROVIDERS.map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Nome Modello</label>
                <input
                  type="text"
                  value={modelName}
                  onChange={(e) => setModelName(e.target.value)}
                  placeholder={
                    provider === 'OPENAI' ? 'gpt-4' :
                    provider === 'ANTHROPIC' ? 'claude-sonnet-4-20250514' :
                    'llama3'
                  }
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            {provider !== 'OLLAMA' ? (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
                <input
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="sk-..."
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            ) : (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ollama URL</label>
                <input
                  type="text"
                  value={ollamaUrl}
                  onChange={(e) => setOllamaUrl(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Temperature: {temperature}
                </label>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.1"
                  value={temperature}
                  onChange={(e) => setTemperature(parseFloat(e.target.value))}
                  className="w-full"
                />
              </div>
              <div className="flex items-end">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={isActive}
                    onChange={(e) => setIsActive(e.target.checked)}
                    className="rounded"
                  />
                  Attivo
                </label>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 disabled:opacity-50 text-sm font-medium"
            >
              {loading ? 'Salvataggio...' : 'Salva'}
            </button>
          </form>
        </div>
      )}

      {/* Lista configurazioni */}
      <div className="space-y-4">
        {configs.length === 0 && !showForm && (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-400">
            Nessun modello configurato. Aggiungi un provider per iniziare.
          </div>
        )}
        {configs.map((config) => (
          <div key={config.id} className="bg-white rounded-lg shadow p-4 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                config.provider === 'OPENAI' ? 'bg-green-100 text-green-700' :
                config.provider === 'ANTHROPIC' ? 'bg-purple-100 text-purple-700' :
                'bg-orange-100 text-orange-700'
              }`}>
                {config.provider}
              </span>
              <div>
                <div className="text-sm font-medium text-gray-900">{config.modelName}</div>
                <div className="text-xs text-gray-500">Temp: {config.temperature}</div>
              </div>
            </div>
            <span className={`text-xs font-medium ${config.isActive ? 'text-green-600' : 'text-gray-400'}`}>
              {config.isActive ? 'Attivo' : 'Inattivo'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
