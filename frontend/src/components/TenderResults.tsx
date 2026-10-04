"use client";

import { useState, type ReactNode } from "react";

import { updateFollowUp } from "@/services/tenderApi";
import type { Page } from "@/types/common";
import type { FollowUpStatus, TenderSummary } from "@/types/tender";

import { TenderRow } from "./TenderRow";

/** Values of the sort parameter of GET /api/tenders; "-" sorts in descending order. */
const SORT_OPTIONS = [
  { value: "", label: "Date limite la plus proche" },
  { value: "-deadline", label: "Date limite la plus éloignée" },
  { value: "-published", label: "Plus récents" },
  { value: "published", label: "Plus anciens" },
  { value: "-relevance", label: "Pertinence" },
  { value: "-amount", label: "Montant décroissant" },
  { value: "amount", label: "Montant croissant" },
  { value: "distance", label: "Distance" },
];

const PAGE_SIZES = [20, 50, 100];

type Props = {
  data: Page<TenderSummary>;
  loading: boolean;
  /** Page URL parameters: sort, size and page are read here. */
  params: URLSearchParams;
  onNavigate: (changes: Record<string, string | null>) => void;
  /** Shown after the number of results, for example the search. */
  caption?: ReactNode;
  emptyMessage: string;
  /** After a change of status or follow, to reload the results and the counts. */
  onChanged: () => void;
};

/** A page of tenders: number of results, sort, table and pages. */
export function TenderResults({ data, loading, params, onNavigate, caption, emptyMessage, onChanged }: Props) {
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const page = Number(params.get("page") ?? "0");

  const followUp = async (tender: TenderSummary, status: FollowUpStatus) => {
    setBusyId(tender.id);
    setActionError(null);
    try {
      await updateFollowUp(tender.id, status);
      onChanged();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      {actionError && <p className="banner error">{actionError}</p>}
      <div className="list-toolbar">
        <p className="muted result-count">
          {data.totalElements} appel{data.totalElements > 1 ? "s" : ""} d&apos;offres
          {caption}
          {loading && " · chargement…"}
        </p>
        <label className="inline">
          Trier par
          <select value={params.get("sort") ?? ""} onChange={(e) => onNavigate({ sort: e.target.value, page: null })}>
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="inline">
          Par page
          <select
            value={params.get("size") ?? "20"}
            onChange={(e) => onNavigate({ size: e.target.value === "20" ? null : e.target.value, page: null })}
          >
            {PAGE_SIZES.map((size) => (
              <option key={size}>{size}</option>
            ))}
          </select>
        </label>
      </div>
      {data.content.length === 0 ? (
        <p className="page-message">{emptyMessage}</p>
      ) : (
        <div className="card table-card">
          <table className="tender-table">
            <thead>
              <tr>
                <th aria-label="Suivi" />
                <th>Appel d&apos;offres</th>
                <th>Lieu</th>
                <th>Travaux</th>
                <th>Montant estimé</th>
                <th>Publication</th>
                <th>Date limite</th>
                <th>Pertinence</th>
                <th>Statut</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((tender) => (
                <TenderRow
                  key={tender.id}
                  tender={tender}
                  busy={busyId === tender.id}
                  onFollowUp={(status) => followUp(tender, status)}
                  onFollowChanged={onChanged}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data.totalPages > 1 && (
        <nav className="pagination">
          <button type="button" disabled={page <= 0} onClick={() => onNavigate({ page: String(page - 1) })}>
            Précédent
          </button>
          <span>
            Page {page + 1} sur {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => onNavigate({ page: String(page + 1) })}
          >
            Suivant
          </button>
        </nav>
      )}
    </>
  );
}
