"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { useAuth } from "./AuthProvider";

/** Main sections; a section is active on its page and on the pages below it. */
const SECTIONS = [
  { href: "/", label: "Tableau de bord" },
  { href: "/tenders", label: "Appels d'offres" },
  { href: "/alerts", label: "Mes veilles" },
  { href: "/follows", label: "Mes suivis" },
  { href: "/archives", label: "Archives" },
  { href: "/awards", label: "Marchés attribués" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const sections = user.role === "ADMIN" ? [...SECTIONS, { href: "/settings", label: "Paramètres" }] : SECTIONS;
  const isActive = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));

  return (
    <>
      <header className="app-header">
        <Link href="/" className="app-title">
          Veille appels d&apos;offres
        </Link>
        <div className="app-user">
          <span>{user.displayName}</span>
          <button type="button" className="link-button" onClick={logout}>
            Se déconnecter
          </button>
        </div>
        <nav className="app-nav">
          {sections.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className={isActive(href) ? "active" : ""}
              aria-current={isActive(href) ? "page" : undefined}
            >
              {label}
            </Link>
          ))}
        </nav>
      </header>
      <main className="app-main">{children}</main>
    </>
  );
}
