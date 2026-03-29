import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 300_000, // 5 minuti - Ollama su CPU puo essere lento
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Flag per evitare loop infinito di refresh
let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function onRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Se 401 e non è già un retry, prova il refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('refreshToken');

      if (refreshToken) {
        if (isRefreshing) {
          // Un refresh è già in corso — accoda la richiesta
          return new Promise((resolve) => {
            refreshSubscribers.push((newToken: string) => {
              originalRequest.headers.Authorization = `Bearer ${newToken}`;
              resolve(api(originalRequest));
            });
          });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const res = await axios.post('/api/auth/refresh', { refreshToken });
          const { token: newToken, refreshToken: newRefreshToken } = res.data;
          localStorage.setItem('token', newToken);
          localStorage.setItem('refreshToken', newRefreshToken);
          isRefreshing = false;
          onRefreshed(newToken);

          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return api(originalRequest);
        } catch {
          isRefreshing = false;
          refreshSubscribers = [];
          // Refresh fallito — logout
          localStorage.removeItem('token');
          localStorage.removeItem('refreshToken');
          localStorage.removeItem('username');
          window.location.href = '/login';
          return Promise.reject(error);
        }
      }

      // Nessun refresh token — logout
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('username');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Auth
export const getSetupStatus = () =>
  api.get<{ setupCompleted: boolean }>('/auth/setup-status');

export const login = (username: string, password: string) =>
  api.post('/auth/login', { username, password });

export const register = (username: string, email: string, password: string) =>
  api.post('/auth/register', { username, email, password });

export const refreshToken = (token: string) =>
  api.post('/auth/refresh', { refreshToken: token });

export const logout = () =>
  api.post('/auth/logout');

// SSE Ticket — genera un ticket monouso per aprire EventSource senza JWT nell'URL
export const getSseTicket = () =>
  api.get<{ ticket: string }>('/auth/sse-ticket');

// User Profile
export const getProfile = () => api.get('/users/me');
export const updateProfile = (data: { username?: string; email?: string; newPassword?: string }) =>
  api.put('/users/me', data);

// Chat Sessions
export const listSessions = () => api.get('/chat/sessions');
export const createSession = (title: string) =>
  api.post('/chat/sessions', { title });
export const updateSession = (sessionId: number, title: string) =>
  api.put(`/chat/sessions/${sessionId}`, { title });
export const getHistory = (sessionId: number) =>
  api.get(`/chat/sessions/${sessionId}/messages`);
export const sendMessage = (sessionId: number, message: string) =>
  api.post(`/chat/sessions/${sessionId}/messages`, { message });

// Documents
export const listDocuments = () => api.get('/documents/');
export const uploadDocument = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const deleteDocument = (id: number) => api.delete(`/documents/${id}`);
export const downloadDocument = (id: number) =>
  api.get(`/documents/${id}/download`, { responseType: 'blob' });

// Admin
export const adminGetAllUsers = () => api.get('/admin/users');
export const adminUpdateUser = (id: number, data: {
  username?: string; email?: string; newPassword?: string; roles?: string[];
}) => api.put(`/admin/users/${id}`, data);
export const adminDeleteUser = (id: number) => api.delete(`/admin/users/${id}`);
export const adminGetAllDocuments = () => api.get('/documents/admin/all');

// LLM Config
export const getMyLlmConfigs = () => api.get('/model/settings/my');
export const saveLlmConfig = (config: {
  provider: string;
  modelName: string;
  ollamaUrl?: string;
  ollamaApi?: string;
  apiKey?: string;
  baseUrl?: string;
  temperature?: number;
  isActive?: boolean;
}) => api.post('/model/settings/', config);

export default api;
