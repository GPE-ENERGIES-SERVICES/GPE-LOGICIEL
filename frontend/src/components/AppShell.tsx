"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Fragment, Suspense, useRef, useState, type ReactNode } from "react";

import { useLoader } from "@/lib/useLoader";
import { getDuplicateGroups } from "@/services/duplicateApi";

import { useAuth } from "./AuthProvider";
import { CollectDelayAlert } from "./CollectDelayAlert";
import { Icon, type IconName } from "./Icon";
import { ThemeToggle } from "./ThemeToggle";

/**
 * @param group the title shown above the first entry of a group, or none
 * @param admin shown to administrators only
 * @param separator a line above it, apart from what precedes
 */
type Section = { href: string; label: string; icon: IconName; group?: string; admin?: boolean; separator?: boolean };

/** Main sections, in the order of the work; a section is active on its page and on the pages below it. */
const SECTIONS: Section[] = [
  { href: "/", label: "Tableau de bord", icon: "dashboard" },
  { href: "/tenders", label: "Tous les appels d'offres", icon: "tenders", group: "Appels d'offres" },
  { href: "/follows", label: "Mes suivis", icon: "star", group: "Appels d'offres" },
  { href: "/alerts", label: "Mes veilles", icon: "alerts", group: "Appels d'offres" },
  { href: "/awards", label: "Marchés attribués", icon: "award", group: "Appels d'offres" },
  { href: "/archives", label: "Archives", icon: "archive", group: "Appels d'offres" },
  { href: "/estimates", label: "Chiffrages", icon: "calculator", group: "Chiffrage" },
  { href: "/suppliers", label: "Tarifs fournisseurs", icon: "tag", group: "Chiffrage", admin: true },
  { href: "/company", label: "Entreprise", icon: "building", separator: true },
  { href: "/supervision", label: "Supervision", icon: "eye", admin: true, separator: true },
  { href: "/settings", label: "Paramètres", icon: "settings", admin: true },
];

/** The section of the current page, or of the page a tender was opened from. */
function activeSection(sections: Section[], current: string): Section | undefined {
  const isActive = (href: string) => (href === "/" ? current === "/" : current.startsWith(href));
  return sections.find(({ href }) => isActive(href));
}

function SectionNav({
  sections,
  origin,
  onNavigate,
  badges = {},
}: {
  sections: Section[];
  origin: string | null;
  onNavigate: () => void;
  /** A count shown beside an entry, by its href: work waiting there. */
  badges?: Record<string, number>;
}) {
  const pathname = usePathname();
  const active = activeSection(sections, origin ?? pathname);
  return (
    <nav className="app-nav" aria-label="Rubriques">
      {sections.map(({ href, label, icon, group, separator }, index) => (
        <Fragment key={href}>
          {group && group !== sections[index - 1]?.group && <span className="app-nav-group">{group}</span>}
          {separator && <span className="app-nav-group" aria-hidden="true" />}
          <Link
            href={href}
            className={active?.href === href ? "active" : ""}
            aria-current={active?.href === href ? "page" : undefined}
            onClick={onNavigate}
          >
            <Icon name={icon} />
            <span>{label}</span>
            {Boolean(badges[href]) && (
              <span className="nav-badge" aria-label={`${badges[href]} à traiter`}>
                {badges[href]}
              </span>
            )}
          </Link>
        </Fragment>
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

function SectionNavWithOrigin({
  sections,
  onNavigate,
  badges,
}: {
  sections: Section[];
  onNavigate: () => void;
  badges: Record<string, number>;
}) {
  return <SectionNav sections={sections} origin={useOrigin(sections)} onNavigate={onNavigate} badges={badges} />;
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
  const admin = user.role === "ADMIN";
  const sections = SECTIONS.filter((section) => !section.admin || admin);
  // shortcut: read when the application opens, not again after each decision; a reload updates it.
  const duplicates = useLoader(() => (admin ? getDuplicateGroups() : Promise.resolve([])), "nav-duplicates");
  const badges = { "/supervision": duplicates.data?.length ?? 0 };
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
        <Suspense fallback={<SectionNav sections={sections} origin={null} onNavigate={closeMenu} badges={badges} />}>
          <SectionNavWithOrigin sections={sections} onNavigate={closeMenu} badges={badges} />
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
