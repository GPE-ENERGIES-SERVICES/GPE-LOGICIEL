"use client";

import Link from "next/link";

import { ValidityBadge } from "@/components/company/RecordTable";
import { EXPIRY_KIND_LABELS } from "@/lib/company";
import { formatDate } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getOverview } from "@/services/companyApi";

const COUNTS: { key: string; label: string; href: string }[] = [
  { key: "activities", label: "Activités", href: "/company/activities" },
  { key: "employees", label: "Salariés", href: "/company/staff" },
  { key: "equipment", label: "Matériels", href: "/company/equipment" },
  { key: "certifications", label: "Certifications", href: "/company/certifications" },
  { key: "references", label: "Références", href: "/company/references" },
  { key: "documents", label: "Documents", href: "/company/documents" },
];

/** What the company holds, and what expires or has expired: documents, certifications, habilitations, checks. */
export default function CompanyOverviewPage() {
  const overview = useLoader(() => getOverview(), "company-overview");

  if (overview.error) {
    return <p className="banner error">{overview.error}</p>;
  }
  if (!overview.data) {
    return <p className="page-message">Chargement…</p>;
  }
  const { counts, expiries, warningDays, documentsWithoutFile } = overview.data;

  return (
    <>
      <dl className="key-facts company-counts">
        {COUNTS.map(({ key, label, href }) => (
          <div key={key} className="key-fact">
            <dt>
              <Link href={href}>{label}</Link>
            </dt>
            <dd>{counts[key] ?? 0}</dd>
          </div>
        ))}
      </dl>

      <section className="card">
        <h2>Échéances</h2>
        <p className="muted">Ce qui a expiré ou expire dans les {warningDays} prochains jours.</p>
        {documentsWithoutFile > 0 && (
          <p className="banner warning">
            {documentsWithoutFile} document(s) sans fichier : <Link href="/company/documents">les compléter</Link>.
          </p>
        )}
        {expiries.length === 0 ? (
          <p>Rien n&apos;arrive à échéance.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Nature</th>
                <th>Élément</th>
                <th>Échéance</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              {expiries.map((expiry) => (
                <tr key={`${expiry.kind}-${expiry.id}`}>
                  <td>
                    <Link href={EXPIRY_KIND_LABELS[expiry.kind].href}>{EXPIRY_KIND_LABELS[expiry.kind].label}</Link>
                  </td>
                  <td>
                    {expiry.title}
                    {expiry.detail && expiry.kind === "CREDENTIAL" && <span className="muted"> · {expiry.detail}</span>}
                  </td>
                  <td>{formatDate(expiry.endsOn)}</td>
                  <td>
                    <ValidityBadge validity={expiry.validity} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
