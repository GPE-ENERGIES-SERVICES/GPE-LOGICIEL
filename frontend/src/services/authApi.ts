import type { CurrentUser } from "@/types/user";

import { api } from "./http";

export function getCurrentUser(): Promise<CurrentUser> {
  return api("/api/auth/me");
}

export function login(email: string, password: string): Promise<void> {
  return api("/api/auth/login", { method: "POST", body: { email, password } });
}

export function logout(): Promise<void> {
  return api("/api/auth/logout", { method: "POST" });
}
