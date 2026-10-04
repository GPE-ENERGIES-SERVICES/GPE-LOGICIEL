"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Suspense, type ReactNode } from "react";

import { useAuth } from "./AuthProvider";

type Section = { href: string; label: string };

/** Main sections; a section is active on its page and on the pages below it. */
const SECTIONS: Section[] = [
  { href: "/", label: "Tableau de bord" },
  { href: "/tenders", label: "Appels d'offres" },
  { href: "/alerts", label: "Mes veilles" },
  { href: "/follows", label: "Mes suivis" },
  { href: "/archives", label: "Archives" },
  { href: "/awards", label: "Marchés attribués" },
];

function SectionNav({ sections, origin }: { sections: Section[]; origin: string | null }) {
  const pathname = usePathname();
  const current = origin ?? pathname;
  const isActive = (href: string) => (href === "/" ? current === "/" : current.startsWith(href));
  return (
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
  );
}

/** The details of a tender belong to the section they were opened from (?from=…). */
function SectionNavWithOrigin({ sections }: { sections: Section[] }) {
  const pathname = usePathname();
  const from = useSearchParams().get("from")?.split("?")[0] ?? null;
  const origin = pathname.startsWith("/tenders/") && sections.some(({ href }) => href === from) ? from : null;
  return <SectionNav sections={sections} origin={origin} />;
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const sections = user.role === "ADMIN" ? [...SECTIONS, { href: "/settings", label: "Paramètres" }] : SECTIONS;

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
        <Suspense fallback={<SectionNav sections={sections} origin={null} />}>
          <SectionNavWithOrigin sections={sections} />
        </Suspense>
      </header>
      <main className="app-main">{children}</main>
    </>
  );
}
