"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Fragment, Suspense, useRef, useState, useSyncExternalStore, type ReactNode } from "react";

import { PRODUCT_NAME } from "@/lib/brand";
import { useLoader } from "@/lib/useLoader";
import { getDuplicateGroups } from "@/services/duplicateApi";

import { useAuth } from "./AuthProvider";
import { CollectDelayAlert } from "./CollectDelayAlert";
import { Icon, type IconName } from "./Icon";
import { ThemeToggle } from "./ThemeToggle";

/**
 * @param title the name above the page when the label of the menu is shortened
 * @param group the title shown above the first entry of a group, or none
 * @param admin shown to administrators only
 * @param separator a line above it, apart from what precedes
 */
type Section = { href: string; label: string; title?: string; icon: IconName; group?: string; admin?: boolean; separator?: boolean };

/** Main sections, in the order of the work; a section is active on its page and on the pages below it. */
const SECTIONS: Section[] = [
  { href: "/", label: "Tableau de bord", icon: "dashboard" },
  { href: "/tenders", label: "Tous les AO", title: "Tous les appels d'offres", icon: "tenders", group: "Appels d'offres" },
  { href: "/follows", label: "Mes suivis", icon: "star", group: "Appels d'offres" },
  { href: "/alerts", label: "Mes veilles", icon: "alerts", group: "Appels d'offres" },
  { href: "/tenders/new", label: "Nouvel appel d'offres", icon: "file", group: "Appels d'offres" },
  { href: "/awards", label: "Marchés attribués", icon: "award", group: "Appels d'offres" },
  { href: "/archives", label: "Archives", icon: "archive", group: "Appels d'offres" },
  { href: "/estimates", label: "Chiffrages", icon: "calculator", group: "Chiffrage" },
  { href: "/suppliers", label: "Tarifs fournisseurs", icon: "tag", group: "Chiffrage", admin: true },
  { href: "/memoirs", label: "Mémoires", icon: "file", group: "Mémoire technique" },
  { href: "/memoirs/library", label: "Bibliothèque", icon: "archive", group: "Mémoire technique" },
  { href: "/responses", label: "Dossiers de réponse", icon: "archive", group: "Réponse" },
  { href: "/company", label: "Entreprise", icon: "building", separator: true },
  { href: "/supervision", label: "Supervision", icon: "eye", admin: true, separator: true },
  { href: "/settings", label: "Paramètres", icon: "settings", admin: true },
];

const NAV_COLLAPSED_KEY = "gpe-nav-collapsed";
const NAV_COLLAPSED_EVENT = "gpe-nav-collapsed-change";

function subscribeNavCollapsed(onChange: () => void) {
  window.addEventListener(NAV_COLLAPSED_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(NAV_COLLAPSED_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

function readNavCollapsed(): boolean {
  try {
    return localStorage.getItem(NAV_COLLAPSED_KEY) === "true";
  } catch {
    return false;
  }
}

/** The menu reduced to its icons, kept in this browser. */
function chooseNavCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(NAV_COLLAPSED_KEY, String(collapsed));
  } catch {
    // not kept: the menu stays as it is
  }
  window.dispatchEvent(new Event(NAV_COLLAPSED_EVENT));
}

/** The section of the current page, or of the page a tender was opened from. */
function activeSection(sections: Section[], current: string): Section | undefined {
  const isActive = (href: string) => (href === "/" ? current === "/" : current.startsWith(href));
  // The most precise entry: /memoirs/library before /memoirs.
  return sections.filter(({ href }) => isActive(href)).sort((a, b) => b.href.length - a.href.length)[0];
}

/**
 * Reduced to its icons, the menu shows the first entry of each group only
 * (nav-sub hides the others); it stays lit while on another page of its group.
 */
function navClass(href: string, group: string | undefined, sub: boolean, active: Section | undefined): string {
  if (active?.href === href) {
    return sub ? "active nav-sub" : "active";
  }
  if (sub) {
    return "nav-sub";
  }
  return group && active?.group === group ? "group-active" : "";
}

function SectionNav({
  sections,
  origin,
  onNavigate,
  badges = {},
  collapsed,
}: {
  sections: Section[];
  origin: string | null;
  onNavigate: () => void;
  /** A count shown beside an entry, by its href: work waiting there. */
  badges?: Record<string, number>;
  /** Only the icons show: the label comes as a tooltip. */
  collapsed: boolean;
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
            className={navClass(href, group, Boolean(group) && group === sections[index - 1]?.group, active)}
            aria-current={active?.href === href ? "page" : undefined}
            onClick={onNavigate}
            title={collapsed ? (group ?? label) : undefined}
          >
            <Icon name={icon} />
            <span className="nav-label">{label}</span>
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
  const section = activeSection(sections, origin ?? pathname);
  return <p className="app-context">{section?.title ?? section?.label}</p>;
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
  collapsed,
}: {
  sections: Section[];
  onNavigate: () => void;
  badges: Record<string, number>;
  collapsed: boolean;
}) {
  return (
    <SectionNav sections={sections} origin={useOrigin(sections)} onNavigate={onNavigate} badges={badges} collapsed={collapsed} />
  );
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
  // On large screens: the menu reduced to its icons, or unfolded.
  const collapsed = useSyncExternalStore(subscribeNavCollapsed, readNavCollapsed, () => false);
  const closeButton = useRef<HTMLButtonElement>(null);
  const closeMenu = () => setMenuOpen(false);
  const openMenu = () => {
    setMenuOpen(true);
    // Keyboard users land in the drawer they opened.
    requestAnimationFrame(() => closeButton.current?.focus());
  };

  return (
    <div
      className={`app-shell${menuOpen ? " menu-open" : ""}${collapsed ? " nav-collapsed" : ""}`}
      onKeyDown={(e) => {
        if (menuOpen && e.key === "Escape") {
          closeMenu();
        }
      }}
    >
      <aside id="app-sidebar" className="app-sidebar">
        <div className="app-brand">
          <button
            type="button"
            className="icon-button menu-toggle"
            aria-label={collapsed ? "Déplier le menu" : "Réduire le menu"}
            title={collapsed ? "Déplier le menu" : "Réduire le menu"}
            aria-controls="app-sidebar"
            aria-expanded={!collapsed}
            onClick={() => chooseNavCollapsed(!collapsed)}
          >
            <Icon name="menu" />
          </button>
          <Link href="/" className="app-title" onClick={closeMenu}>
            <span className="product-name" lang="ar" dir="rtl">
              {PRODUCT_NAME}
            </span>
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
        <Suspense
          fallback={<SectionNav sections={sections} origin={null} onNavigate={closeMenu} badges={badges} collapsed={collapsed} />}
        >
          <SectionNavWithOrigin sections={sections} onNavigate={closeMenu} badges={badges} collapsed={collapsed} />
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
