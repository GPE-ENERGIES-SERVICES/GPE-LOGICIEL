"use client";

import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";

import { tenderHref } from "@/lib/navigation";
import { available } from "@/services/http";
import { updateFollowUp } from "@/services/tenderApi";
import type { Page } from "@/types/common";
import type { FollowUpStatus, TenderSummary } from "@/types/tender";

import { ListToolbar } from "./ListToolbar";
import { Pagination } from "./Pagination";
import { TenderRow } from "./TenderRow";

/** Values of the sort parameter of GET /api/tenders; "-" sorts in descending order. */
const SORT_OPTIONS = [
  { value: "", label: "Date limite la plus proche" },
  { value: "-deadline", label: "Date limite la plus éloignée" },
  { value: "-published", label: "Plus récents" },
  { value: "published", label: "Plus anciens" },
  ...(available("relevance") ? [{ value: "-relevance", label: "Pertinence" }] : []),
  { value: "-amount", label: "Montant décroissant" },
  { value: "amount", label: "Montant croissant" },
  ...(available("distance") ? [{ value: "distance", label: "Distance" }] : []),
];

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
  const pathname = usePathname();
  const query = params.toString();
  const from = query ? `${pathname}?${query}` : pathname;

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
      <ListToolbar
        summary={
          <>
            {data.totalElements} appel{data.totalElements > 1 ? "s" : ""} d&apos;offres
            {caption}
            {loading && " · chargement…"}
          </>
        }
        sortOptions={SORT_OPTIONS}
        params={params}
        onNavigate={onNavigate}
      />
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
                  href={tenderHref(tender.id, from)}
                  busy={busyId === tender.id}
                  onFollowUp={(status) => followUp(tender, status)}
                  onFollowChanged={onChanged}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={data.totalPages} onNavigate={onNavigate} />
    </>
  );
}
