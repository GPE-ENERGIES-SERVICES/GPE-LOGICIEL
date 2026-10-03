"use client";

import { usePathname, useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

import { api, ApiError } from "@/lib/api";
import type { CurrentUser } from "@/lib/types";

type AuthContextValue = {
  user: CurrentUser;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return value;
}

/**
 * Loads the logged-in user and redirects to the login page when there is none.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<CurrentUser>("/api/auth/me")
      .then(setUser)
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 401) {
          router.replace(`/login?next=${encodeURIComponent(pathname)}`);
        } else {
          setError(err instanceof Error ? err.message : "Une erreur est survenue.");
        }
      });
    // Checked once per page load; later expirations are handled per request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return <p className="page-message error">{error}</p>;
  }
  if (!user) {
    return <p className="page-message">Chargement…</p>;
  }

  const logout = async () => {
    await api("/api/auth/logout", { method: "POST" });
    router.replace("/login");
  };

  return <AuthContext.Provider value={{ user, logout }}>{children}</AuthContext.Provider>;
}
