import { createContext, useContext, useState, type ReactNode } from 'react';

interface AuthContextType {
  isAuthenticated: boolean;
  username: string | null;
  roles: string[];
  isAdmin: boolean;
  login: (token: string, username: string, roles?: string[]) => void;
  logout: () => void;
  setUsername: (username: string) => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

function parseRolesFromToken(token: string): string[] {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.roles || [];
  } catch {
    return [];
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!localStorage.getItem('token'));
  const [username, setUsernameState] = useState<string | null>(() => localStorage.getItem('username'));
  const [roles, setRoles] = useState<string[]>(() => {
    const token = localStorage.getItem('token');
    return token ? parseRolesFromToken(token) : [];
  });

  const login = (token: string, username: string) => {
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    const tokenRoles = parseRolesFromToken(token);
    setIsAuthenticated(true);
    setUsernameState(username);
    setRoles(tokenRoles);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    setIsAuthenticated(false);
    setUsernameState(null);
    setRoles([]);
  };

  const setUsername = (newUsername: string) => {
    localStorage.setItem('username', newUsername);
    setUsernameState(newUsername);
  };

  const isAdmin = roles.includes('ROLE_ADMIN');

  return (
    <AuthContext.Provider value={{ isAuthenticated, username, roles, isAdmin, login, logout, setUsername }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
