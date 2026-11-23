"use client";

import Link from "next/link";

import { usePaged } from "@/components/Pager";
import { ESTIMATE_STATUS_LABELS, formatMoney } from "@/lib/estimate";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getRecentEstimates } from "@/services/estimateApi";

/** Every estimate, the last changed first. An estimate is created from the page of its tender. */
export default function EstimatesPage() {
  const estimates = useLoader(() => getRecentEstimates(), "estimates");
  const paged = usePaged(estimates.data ?? [], "Pages des chiffrages");

  return (
    <div>
      <h1>Chiffrages</h1>
      <p className="muted">
        Nouveau chiffrage : depuis la fiche de l&apos;appel d&apos;offres, onglet « Chiffrage ». Voir aussi{" "}
        <Link href="/suppliers">Tarifs fournisseurs</Link> et{" "}
        <Link href="/estimate-settings">Paramètres du chiffrage</Link>.
      </p>
      <section className="card">
        {estimates.error && <p className="banner error">{estimates.error}</p>}
        {!estimates.data && !estimates.error && (
          <p className="page-message">Chargement…</p>
        )}
        {estimates.data && estimates.data.length === 0 && (
          <p className="muted">Aucun chiffrage pour l&apos;instant.</p>
        )}
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
              {paged.rows.map(({ tenderId, tenderTitle, estimate }) => (
                <tr key={estimate.id}>
                  <td>
                    <Link href={`/tenders/${tenderId}#chiffrage`}>
                      {tenderTitle ?? `Marché ${tenderId}`}
                    </Link>
                  </td>
                  <td>
                    <Link href={`/estimates/${estimate.id}`}>
                      Version {estimate.version}
                      {estimate.lotNumber && ` · lot ${estimate.lotNumber}`}
                      {estimate.label && ` · ${estimate.label}`}
                    </Link>
                  </td>
                  <td>
                    <span
                      className={
                        estimate.status === "VALIDATED"
                          ? "badge verified"
                          : estimate.status === "DRAFT"
                            ? "badge info"
                            : "badge"
                      }
                    >
                      {ESTIMATE_STATUS_LABELS[estimate.status]}
                    </span>
                    {estimate.incompleteItems > 0 && (
                      <span className="badge urgent">
                        {estimate.incompleteItems} incomplet(s)
                      </span>
                    )}
                  </td>
                  <td className="number">{formatMoney(estimate.totalHt)}</td>
                  <td>
                    {formatDateTime(estimate.updatedAt)}
                    {estimate.updatedBy && (
                      <span className="muted"> · {estimate.updatedBy}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {paged.pager}
      </section>
    </div>
  );
}
