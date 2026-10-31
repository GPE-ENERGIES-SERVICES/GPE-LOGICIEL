"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { useAuth } from "@/components/AuthProvider";
import { COMPANY_PAGES } from "@/lib/company";

/**
 * The company: its parts side by side, one page each. Everyone reads them;
 * an administrator changes them.
 */
export default function CompanyLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { user } = useAuth();

  return (
    <div className="company-page">
      <h1>Entreprise</h1>
      <nav className="company-nav" aria-label="Rubriques de l'entreprise">
        {COMPANY_PAGES.map(({ href, label }) => (
          <Link key={href} href={href} aria-current={pathname === href ? "page" : undefined}>
            {label}
          </Link>
        ))}
      </nav>
      {user.role !== "ADMIN" && (
        <p className="hint">Consultation seule : les informations de l&apos;entreprise sont modifiées par un administrateur.</p>
      )}
      {children}
    </div>
  );
}
