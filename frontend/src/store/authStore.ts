import { create } from "zustand";

export type Role = "USER" | "ORGANIZER" | "ADMIN";

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role: Role;
}

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  hydrated: boolean;
  setAuth: (accessToken: string, user: AuthUser) => void;
  clearAuth: () => void;
  setHydrated: () => void;
}

// Access token lives in memory only (never localStorage) per the blueprint's auth design —
// this shrinks the XSS blast radius. It's lost on a hard refresh, which is why useAuth()
// silently calls /api/auth/refresh on mount to recover a session from the httpOnly cookie.
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  hydrated: false,
  setAuth: (accessToken, user) => set({ accessToken, user }),
  clearAuth: () => set({ accessToken: null, user: null }),
  setHydrated: () => set({ hydrated: true }),
}));
