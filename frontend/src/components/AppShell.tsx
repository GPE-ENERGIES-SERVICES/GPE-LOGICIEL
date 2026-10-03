"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { useAuth } from "./AuthProvider";

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const isSettings = pathname.startsWith("/settings");

  return (
    <>
      <header className="app-header">
        <Link href="/" className="app-title">
          Veille appels d&apos;offres
        </Link>
        <nav className="app-nav">
          <Link href="/" className={isSettings ? "" : "active"}>
            Appels d&apos;offres
          </Link>
          {user.role === "ADMIN" && (
            <Link href="/settings" className={isSettings ? "active" : ""}>
              Paramètres
            </Link>
          )}
        </nav>
        <div className="app-user">
          <span>{user.displayName}</span>
          <button type="button" className="link-button" onClick={logout}>
            Se déconnecter
          </button>
        </div>
      </header>
      <main className="app-main">{children}</main>
    </>
  );
}
