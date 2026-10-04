"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";

import { CollectStatus } from "@/components/CollectStatus";
import { FILTER_KEYS, TenderFilters } from "@/components/TenderFilters";
import { TenderRow } from "@/components/TenderRow";
import { TENDER_TAB_LABELS } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getTenderCounts, getTenders, updateFollowUp } from "@/services/tenderApi";
import type { FollowUpStatus, TenderSummary, TenderTab } from "@/types/tender";

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

const TAB_HINTS: Partial<Record<TenderTab, string>> = {
  TO_PROCESS: "Appels d'offres à analyser ou en préparation.",
  FOLLOWED: "Appels d'offres que vous suivez. Le suivi est propre à chaque utilisateur.",
  ARCHIVED: "Appels d'offres clos, annulés ou sans suite.",
  FILTERED_OUT:
    "Avis écartés par les critères de l'entreprise. Vérifiez ici que le filtre ne fait pas disparaître de bons marchés.",
};

function TenderList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab") as TenderTab | null;
  const tab = tabParam && tabParam in TENDER_TAB_LABELS ? tabParam : "NEW";
  const page = Number(searchParams.get("page") ?? "0");
  const text = searchParams.get("q") ?? "";
  const filtered = Boolean(text) || FILTER_KEYS.some((key) => searchParams.get(key));
  const query = searchParams.toString();
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // The tab counts follow the search and the filters, so a result in another tab is visible.
  const countParams = new URLSearchParams(searchParams);
  for (const key of ["tab", "page", "size", "sort"]) {
    countParams.delete(key);
  }
  const countQuery = countParams.toString();

  const list = useLoader(() => {
    const params = new URLSearchParams(searchParams);
    params.set("tab", tab);
    return getTenders(params);
  }, query);
  const counts = useLoader(() => getTenderCounts(countQuery), countQuery);

  const navigate = (changes: Record<string, string | null>) => {
    const params = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === "") {
        params.delete(key);
      } else {
        params.set(key, value);
      }
    }
    router.replace(`${pathname}?${params}`);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    navigate({ q: String(new FormData(event.currentTarget).get("q") ?? "").trim() || null, page: null });
  };

  const followUp = async (tender: TenderSummary, status: FollowUpStatus) => {
    setBusyId(tender.id);
    setActionError(null);
    try {
      await updateFollowUp(tender.id, status);
      list.reload();
      counts.reload();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <h1>Appels d&apos;offres</h1>
      <CollectStatus />

      <form className="filters" role="search" onSubmit={search} key={text}>
        <input
          name="q"
          type="search"
          aria-label="Rechercher"
          placeholder="Rechercher par titre, référence, acheteur, ville ou type de travaux"
          defaultValue={text}
        />
        <button type="submit" className="primary">
          Rechercher
        </button>
        {text && (
          <button type="button" className="link-button" onClick={() => navigate({ q: null, page: null })}>
            Effacer la recherche
          </button>
        )}
      </form>

      <div className="tabs" role="tablist">
        {(Object.entries(TENDER_TAB_LABELS) as [TenderTab, string][]).map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            className={tab === value ? "tab active" : "tab"}
            onClick={() => navigate({ tab: value, page: null })}
          >
            {label}
            {counts.data && <span className="count">{counts.data[value]}</span>}
          </button>
        ))}
      </div>

      <TenderFilters params={searchParams} onApply={navigate} />

      {TAB_HINTS[tab] && <p className="hint">{TAB_HINTS[tab]}</p>}
      {actionError && <p className="banner error">{actionError}</p>}
      {list.error && <p className="banner error">{list.error}</p>}
      {list.loading && !list.data && <p className="page-message">Chargement…</p>}

      {list.data && (
        <>
          <div className="list-toolbar">
            <p className="muted result-count">
              {list.data.totalElements} appel{list.data.totalElements > 1 ? "s" : ""} d&apos;offres
              {text && <> pour « {text} »</>}
              {list.loading && " · chargement…"}
            </p>
            <label className="inline">
              Trier par
              <select
                value={searchParams.get("sort") ?? ""}
                onChange={(e) => navigate({ sort: e.target.value, page: null })}
              >
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
                value={searchParams.get("size") ?? "20"}
                onChange={(e) => navigate({ size: e.target.value === "20" ? null : e.target.value, page: null })}
              >
                {PAGE_SIZES.map((size) => (
                  <option key={size}>{size}</option>
                ))}
              </select>
            </label>
          </div>
          {list.data.content.length === 0 ? (
            <p className="page-message">
              {filtered
                ? "Aucun appel d'offres ne correspond à la recherche et aux filtres dans cet onglet."
                : "Aucun appel d'offres dans cet onglet."}
            </p>
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
                  {list.data.content.map((tender) => (
                    <TenderRow
                      key={tender.id}
                      tender={tender}
                      busy={busyId === tender.id}
                      onFollowUp={(status) => followUp(tender, status)}
                      onFollowChanged={() => {
                        list.reload();
                        counts.reload();
                      }}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {list.data.totalPages > 1 && (
            <nav className="pagination">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => navigate({ page: String(page - 1) })}
              >
                Précédent
              </button>
              <span>
                Page {page + 1} sur {list.data.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= list.data.totalPages}
                onClick={() => navigate({ page: String(page + 1) })}
              >
                Suivant
              </button>
            </nav>
          )}
        </>
      )}
    </>
  );
}

export default function TendersPage() {
  return (
    <Suspense>
      <TenderList />
    </Suspense>
  );
}
