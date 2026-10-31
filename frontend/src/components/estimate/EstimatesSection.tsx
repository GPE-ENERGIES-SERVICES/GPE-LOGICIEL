"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { ESTIMATE_STATUS_LABELS, formatMoney } from "@/lib/estimate";
import { formatDateTime, lotTitle } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { createEstimate, getEstimates } from "@/services/estimateApi";
import type { TenderDetail } from "@/types/tender";

/**
 * The estimates of a tender, by lot and version, and the creation of a new
 * one with the settings of GPE.
 */
export function EstimatesSection({ tender }: { tender: TenderDetail }) {
  const router = useRouter();
  const estimates = useLoader(() => getEstimates(tender.id), String(tender.id));
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const lot = String(form.get("lot") ?? "");
    setCreating(true);
    setError(null);
    try {
      const created = await createEstimate(tender.id, {
        lotNumber: lot || null,
        label: String(form.get("label") ?? "").trim() || null,
        vatRate: null,
        coefficients: {},
      });
      router.push(`/estimates/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
      setCreating(false);
    }
  };

  const lotLabel = (number: string | null) => {
    if (number === null) {
      return tender.lots.length ? "Tous lots" : "Marché";
    }
    const lot = tender.lots.find((candidate) => candidate.number === number);
    return `Lot ${number}${lot?.title ? ` – ${lotTitle(lot.number, lot.title)}` : ""}`;
  };

  return (
    <>
      {estimates.error && <p className="banner error">{estimates.error}</p>}
      {estimates.data && estimates.data.length === 0 && <p className="muted">Aucun chiffrage pour ce marché.</p>}
      {estimates.data && estimates.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Lot</th>
              <th>Version</th>
              <th>Statut</th>
              <th className="number">Total HT</th>
              <th className="number">Total TTC</th>
              <th>Modifié</th>
            </tr>
          </thead>
          <tbody>
            {estimates.data.map((estimate) => (
              <tr key={estimate.id}>
                <td>{lotLabel(estimate.lotNumber)}</td>
                <td>
                  <Link href={`/estimates/${estimate.id}`}>
                    Version {estimate.version}
                    {estimate.label && ` · ${estimate.label}`}
                  </Link>
                </td>
                <td>
                  <span className={estimate.status === "VALIDATED" ? "badge verified" : "badge"}>
                    {ESTIMATE_STATUS_LABELS[estimate.status]}
                  </span>
                  {estimate.incompleteItems > 0 && (
                    <span className="badge urgent">{estimate.incompleteItems} poste(s) incomplet(s)</span>
                  )}
                </td>
                <td className="number">{formatMoney(estimate.totalHt)}</td>
                <td className="number">{formatMoney(estimate.totalTtc)}</td>
                <td>
                  {formatDateTime(estimate.updatedAt)}
                  {estimate.updatedBy && <span className="muted"> · {estimate.updatedBy}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form estimate-create" onSubmit={create}>
        {tender.lots.length > 0 && (
          <label>
            Lot{" "}
            <select name="lot" defaultValue="">
              <option value="">Tous lots</option>
              {tender.lots.map((lot) => (
                <option key={lot.number} value={lot.number}>
                  {lotLabel(lot.number)}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Libellé <input name="label" maxLength={200} placeholder="ex. Offre de base" />
        </label>
        <button type="submit" className="primary" disabled={creating}>
          {creating ? "Création…" : "Nouveau chiffrage"}
        </button>
        {error && <span className="error">{error}</span>}
      </form>
      <p className="hint">
        Un nouveau chiffrage reprend le taux de TVA et les coefficients des paramètres de GPE. Les montants sont calculés par le
        logiciel ; une version validée est figée.
      </p>
    </>
  );
}
