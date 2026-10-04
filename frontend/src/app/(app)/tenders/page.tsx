"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";

import { CollectStatus } from "@/components/CollectStatus";
import { TenderRow } from "@/components/TenderRow";
import { api } from "@/lib/api";
import { TENDER_TAB_LABELS } from "@/lib/format";
import type { FollowUpStatus, Page, TenderSummary, TenderTab } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

const TAB_HINTS: Partial<Record<TenderTab, string>> = {
  TO_PROCESS: "Appels d'offres en cours d'analyse ou de préparation.",
  FOLLOWED: "Appels d'offres que vous suivez. Le suivi est propre à chaque utilisateur.",
  ARCHIVED: "Appels d'offres clos, annulés ou sans suite.",
  FILTERED_OUT:
    "Avis écartés par les critères de l'entreprise. Vérifiez ici que le filtre ne fait pas disparaître de bons marchés.",
};

const FILTER_KEYS = ["department", "q", "minDaysLeft", "maxAmount"] as const;

function TenderList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab") as TenderTab | null;
  const tab = tabParam && tabParam in TENDER_TAB_LABELS ? tabParam : "NEW";
  const page = Number(searchParams.get("page") ?? "0");
  const query = searchParams.toString();
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const list = useLoader(() => {
    const params = new URLSearchParams(searchParams);
    params.set("tab", tab);
    return api<Page<TenderSummary>>(`/api/tenders?${params}`);
  }, query);
  const counts = useLoader(() => api<Record<TenderTab, number>>("/api/tenders/counts"), "counts");

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

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const changes: Record<string, string | null> = { page: null };
    for (const key of FILTER_KEYS) {
      changes[key] = String(form.get(key) ?? "").trim() || null;
    }
    navigate(changes);
  };

  const followUp = async (tender: TenderSummary, status: FollowUpStatus) => {
    setBusyId(tender.id);
    setActionError(null);
    try {
      await api(`/api/tenders/${tender.id}/follow-up`, { method: "PUT", body: { status } });
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
      <CollectStatus />

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

      <form className="filters" onSubmit={applyFilters} key={query}>
        <input name="q" placeholder="Mots-clés" defaultValue={searchParams.get("q") ?? ""} />
        <input
          name="department"
          placeholder="Département"
          size={10}
          defaultValue={searchParams.get("department") ?? ""}
        />
        <input
          name="minDaysLeft"
          type="number"
          min={0}
          placeholder="Délai min. (jours)"
          defaultValue={searchParams.get("minDaysLeft") ?? ""}
        />
        <input
          name="maxAmount"
          type="number"
          min={0}
          placeholder="Montant max. (€ HT)"
          defaultValue={searchParams.get("maxAmount") ?? ""}
        />
        <button type="submit">Filtrer</button>
        {FILTER_KEYS.some((key) => searchParams.get(key)) && (
          <button
            type="button"
            className="link-button"
            onClick={() => navigate({ department: null, q: null, minDaysLeft: null, maxAmount: null, page: null })}
          >
            Effacer les filtres
          </button>
        )}
      </form>

      {TAB_HINTS[tab] && <p className="hint">{TAB_HINTS[tab]}</p>}
      {actionError && <p className="banner error">{actionError}</p>}
      {list.error && <p className="banner error">{list.error}</p>}
      {list.loading && !list.data && <p className="page-message">Chargement…</p>}

      {list.data && (
        <>
          <p className="muted result-count">
            {list.data.totalElements} appel{list.data.totalElements > 1 ? "s" : ""} d&apos;offres
            {list.loading && " · chargement…"}
          </p>
          {list.data.content.length === 0 ? (
            <p className="page-message">Aucun appel d&apos;offres dans cet onglet.</p>
          ) : (
            <div className="card table-card">
              <table className="tender-table">
                <thead>
                  <tr>
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
