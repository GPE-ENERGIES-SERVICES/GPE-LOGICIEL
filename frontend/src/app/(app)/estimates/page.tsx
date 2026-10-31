"use client";

import Link from "next/link";

import { ESTIMATE_STATUS_LABELS, formatMoney } from "@/lib/estimate";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getRecentEstimates } from "@/services/estimateApi";

/** Every estimate, the last changed first. An estimate is created from the page of its tender. */
export default function EstimatesPage() {
  const estimates = useLoader(() => getRecentEstimates(), "estimates");

  return (
    <div>
      <h1>Chiffrages</h1>
      <section className="card">
        <p>
          Un chiffrage se crée depuis la fiche d&apos;un appel d&apos;offres : ouvrez-la depuis{" "}
          <Link href="/tenders">Appels d&apos;offres</Link> ou <Link href="/follows">Mes suivis</Link>, onglet{" "}
          <strong>« Chiffrage »</strong>, bouton <strong>« Nouveau chiffrage »</strong>. Les tarifs fournisseurs sont dans{" "}
          <Link href="/suppliers">Tarifs fournisseurs</Link>, la TVA, les coefficients et les coûts horaires dans{" "}
          <Link href="/company/economics">Entreprise › Économie</Link>.
        </p>
      </section>
      <section className="card">
        {estimates.error && <p className="banner error">{estimates.error}</p>}
        {!estimates.data && !estimates.error && <p className="page-message">Chargement…</p>}
        {estimates.data && estimates.data.length === 0 && <p className="muted">Aucun chiffrage pour l&apos;instant.</p>}
        {estimates.data && estimates.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Appel d&apos;offres</th>
                <th>Chiffrage</th>
                <th>Statut</th>
                <th className="number">Total HT</th>
                <th>Modifié</th>
              </tr>
            </thead>
            <tbody>
              {estimates.data.map(({ tenderId, tenderTitle, estimate }) => (
                <tr key={estimate.id}>
                  <td>
                    <Link href={`/tenders/${tenderId}#chiffrage`}>{tenderTitle ?? `Marché ${tenderId}`}</Link>
                  </td>
                  <td>
                    <Link href={`/estimates/${estimate.id}`}>
                      Version {estimate.version}
                      {estimate.lotNumber && ` · lot ${estimate.lotNumber}`}
                      {estimate.label && ` · ${estimate.label}`}
                    </Link>
                  </td>
                  <td>
                    <span className={estimate.status === "VALIDATED" ? "badge verified" : "badge"}>
                      {ESTIMATE_STATUS_LABELS[estimate.status]}
                    </span>
                    {estimate.incompleteItems > 0 && <span className="badge urgent">{estimate.incompleteItems} incomplet(s)</span>}
                  </td>
                  <td className="number">{formatMoney(estimate.totalHt)}</td>
                  <td>
                    {formatDateTime(estimate.updatedAt)}
                    {estimate.updatedBy && <span className="muted"> · {estimate.updatedBy}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
