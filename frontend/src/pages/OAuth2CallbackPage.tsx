import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

// Pagina di atterraggio dopo il login OAuth2 (Google / GitHub).
// Il success handler del user-service fa un redirect su:
//   /oauth2/callback?token=<jwt>&username=<username>
// Questa pagina legge i parametri, inizializza la sessione e manda l'utente alla home.
export default function OAuth2CallbackPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');
    const username = params.get('username');

    if (token && username) {
      login(token, username, refreshToken || undefined);
      navigate('/', { replace: true });
    } else {
      // Parametri mancanti: torna al login
      navigate('/login', { replace: true });
    }
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-white dark:from-slate-900 dark:to-slate-950">
      <div className="flex flex-col items-center gap-4">
        <div className="animate-spin h-10 w-10 border-4 border-primary-500 border-t-transparent rounded-full" />
        <p className="text-slate-500 dark:text-slate-400 text-sm">Accesso in corso...</p>
      </div>
    </div>
  );
}
