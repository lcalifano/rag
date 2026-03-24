import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Auth
export const login = (username: string, password: string) =>
  api.post('/auth/login', { username, password });

export const register = (username: string, email: string, password: string) =>
  api.post('/auth/register', { username, email, password });

// Chat Sessions
export const listSessions = () => api.get('/chat/sessions');
export const createSession = (title: string) =>
  api.post('/chat/sessions', { title });
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
