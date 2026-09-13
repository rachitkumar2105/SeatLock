"use client";

import { useCallback } from "react";
import { useAuthStore } from "@/store/authStore";
import * as authApi from "@/services/authApi";
import { readCookie } from "@/services/cookies";

export function useAuth() {
  const { accessToken, user, hydrated, setAuth, clearAuth } = useAuthStore();

  const doLogin = useCallback(
    async (email: string, password: string) => {
      const response = await authApi.login(email, password);
      setAuth(response.accessToken, response.user);
      return response.user;
    },
    [setAuth]
  );

  const doRegister = useCallback(async (name: string, email: string, password: string) => {
    return authApi.register(name, email, password);
  }, []);

  const doLogout = useCallback(async () => {
    const csrfToken = readCookie("csrf_token");
    try {
      await authApi.logout(csrfToken);
    } finally {
      clearAuth();
    }
  }, [clearAuth]);

  return {
    accessToken,
    user,
    hydrated,
    isAuthenticated: Boolean(accessToken && user),
    login: doLogin,
    register: doRegister,
    logout: doLogout,
  };
}
