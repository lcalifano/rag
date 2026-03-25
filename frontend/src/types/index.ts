export interface AuthResponse {
  token: string;
  username: string;
  email: string;
  message: string;
}

export interface ChatSession {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  createdAt: string;
}

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  roles: string[];
}

export interface Document {
  id: number;
  userId?: number;
  originalFilename: string;
  fileSize: number;
  totalChunks: number;
  status: string;
  createdAt: string;
}

export interface LlmConfig {
  id: number;
  userId: number;
  provider: string;
  modelName: string;
  ollamaUrl: string;
  ollamaApi: string;
  apiKey: string;
  baseUrl: string;
  temperature: number;
  isActive: boolean;
  createdAt: string;
}
