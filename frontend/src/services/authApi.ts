import { apiFetch } from "./apiClient";
import { AuthUser } from "@/store/authStore";

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export function register(name: string, email: string, password: string): Promise<AuthUser> {
  return apiFetch<AuthUser>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ name, email, password }),
  });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
    skipAuthRetry: true,
  });
}

export function logout(csrfToken: string | null): Promise<void> {
  return apiFetch<void>("/api/auth/logout", {
    method: "POST",
    headers: csrfToken ? { "X-CSRF-Token": csrfToken } : {},
    skipAuthRetry: true,
  });
}

export interface SessionDto {
  id: string;
  userAgent: string | null;
  ipAddress: string | null;
  lastUsedAt: string;
  createdAt: string;
  expiresAt: string;
}

export function listSessions(): Promise<SessionDto[]> {
  return apiFetch<SessionDto[]>("/api/auth/sessions");
}

export function revokeSession(id: string): Promise<void> {
  return apiFetch<void>(`/api/auth/sessions/${id}`, { method: "DELETE" });
}
