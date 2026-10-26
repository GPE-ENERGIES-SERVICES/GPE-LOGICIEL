"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Suspense, useRef, useState, type ReactNode } from "react";

import { useAuth } from "./AuthProvider";
import { CollectDelayAlert } from "./CollectDelayAlert";
import { Icon, type IconName } from "./Icon";
import { ThemeToggle } from "./ThemeToggle";

type Section = { href: string; label: string; icon: IconName };

/** Main sections; a section is active on its page and on the pages below it. */
const SECTIONS: Section[] = [
  { href: "/", label: "Tableau de bord", icon: "dashboard" },
  { href: "/tenders", label: "Appels d'offres", icon: "tenders" },
  { href: "/alerts", label: "Mes veilles", icon: "alerts" },
  { href: "/follows", label: "Mes suivis", icon: "star" },
  { href: "/archives", label: "Archives", icon: "archive" },
  { href: "/awards", label: "Marchés attribués", icon: "award" },
];

const SETTINGS: Section = { href: "/settings", label: "Paramètres", icon: "settings" };

/** The section of the current page, or of the page a tender was opened from. */
function activeSection(sections: Section[], current: string): Section | undefined {
  const isActive = (href: string) => (href === "/" ? current === "/" : current.startsWith(href));
  return sections.find(({ href }) => isActive(href));
}

function SectionNav({
  sections,
  origin,
  onNavigate,
}: {
  sections: Section[];
  origin: string | null;
  onNavigate: () => void;
}) {
  const pathname = usePathname();
  const active = activeSection(sections, origin ?? pathname);
  return (
    <nav className="app-nav" aria-label="Rubriques">
      {sections.map(({ href, label, icon }) => (
        <Link
          key={href}
          href={href}
          className={active?.href === href ? "active" : ""}
          aria-current={active?.href === href ? "page" : undefined}
          onClick={onNavigate}
        >
          <Icon name={icon} />
          <span>{label}</span>
        </Link>
      ))}
    </nav>
  );
}

function SectionTitle({ sections, origin }: { sections: Section[]; origin: string | null }) {
  const pathname = usePathname();
  return <p className="app-context">{activeSection(sections, origin ?? pathname)?.label}</p>;
}

/** The details of a tender belong to the section they were opened from (?from=…). */
function useOrigin(sections: Section[]) {
  const pathname = usePathname();
  const from = useSearchParams().get("from")?.split("?")[0] ?? null;
  return pathname.startsWith("/tenders/") && sections.some(({ href }) => href === from) ? from : null;
}

function SectionNavWithOrigin({ sections, onNavigate }: { sections: Section[]; onNavigate: () => void }) {
  return <SectionNav sections={sections} origin={useOrigin(sections)} onNavigate={onNavigate} />;
}

function SectionTitleWithOrigin({ sections }: { sections: Section[] }) {
  return <SectionTitle sections={sections} origin={useOrigin(sections)} />;
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return (words.length > 1 ? words[0][0] + words[words.length - 1][0] : name.slice(0, 2)).toUpperCase();
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const sections = user.role === "ADMIN" ? [...SECTIONS, SETTINGS] : SECTIONS;
  // The navigation is a drawer on small screens only.
  const [menuOpen, setMenuOpen] = useState(false);
  const closeButton = useRef<HTMLButtonElement>(null);
  const closeMenu = () => setMenuOpen(false);
  const openMenu = () => {
    setMenuOpen(true);
    // Keyboard users land in the drawer they opened.
    requestAnimationFrame(() => closeButton.current?.focus());
  };

  return (
    <div
      className={menuOpen ? "app-shell menu-open" : "app-shell"}
      onKeyDown={(e) => {
        if (menuOpen && e.key === "Escape") {
          closeMenu();
        }
      }}
    >
      <aside id="app-sidebar" className="app-sidebar">
        <div className="app-brand">
          <Link href="/" className="app-title" onClick={closeMenu}>
            Logiciel GPE
          </Link>
          <button
            ref={closeButton}
            type="button"
            className="icon-button menu-close"
            aria-label="Fermer le menu"
            onClick={closeMenu}
          >
            <Icon name="close" />
          </button>
        </div>
        <Suspense fallback={<SectionNav sections={sections} origin={null} onNavigate={closeMenu} />}>
          <SectionNavWithOrigin sections={sections} onNavigate={closeMenu} />
        </Suspense>
        <div className="app-user">
          <span className="avatar" aria-hidden="true">
            {initials(user.displayName)}
          </span>
          <span className="app-user-name">
            <strong>{user.displayName}</strong>
            <span className="small muted">{user.email}</span>
          </span>
          <button type="button" className="icon-button" onClick={logout} aria-label="Se déconnecter" title="Se déconnecter">
            <Icon name="logout" />
          </button>
        </div>
      </aside>
      {menuOpen && <div className="app-backdrop" onClick={closeMenu} aria-hidden="true" />}

      <div className="app-body">
        <header className="app-topbar">
          <button
            type="button"
            className="icon-button menu-open-button"
            aria-label="Ouvrir le menu"
            aria-controls="app-sidebar"
            aria-expanded={menuOpen}
            onClick={openMenu}
          >
            <Icon name="menu" />
          </button>
          <Suspense fallback={<SectionTitle sections={sections} origin={null} />}>
            <SectionTitleWithOrigin sections={sections} />
          </Suspense>
          <ThemeToggle />
        </header>
        <main className="app-main">
          <CollectDelayAlert />
          {children}
        </main>
      </div>
    </div>
  );
}
