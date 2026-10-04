"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Suspense, useEffect } from "react";

import { CollectStatus } from "@/components/CollectStatus";
import { SearchForm } from "@/components/SearchForm";
import { OPEN_FILTERS, TenderFilters } from "@/components/TenderFilters";
import { TenderResults } from "@/components/TenderResults";
import { TENDER_TAB_LABELS } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { useUrlParams } from "@/lib/useUrlParams";
import { getAlerts } from "@/services/alertApi";
import { getTenderCounts, getTenders } from "@/services/tenderApi";
import type { TenderTab } from "@/types/tender";

/** Views of the open consultations; followed and past ones have their own sections. */
const TABS: TenderTab[] = ["NEW", "TO_PROCESS", "ALL", "DISMISSED", "FILTERED_OUT"];

/** Former tabs of this page, now sections of their own. */
const MOVED_TABS: Partial<Record<TenderTab, string>> = { FOLLOWED: "/follows", ARCHIVED: "/archives" };

const TAB_HINTS: Partial<Record<TenderTab, string>> = {
  TO_PROCESS: "Consultations à analyser ou en préparation.",
  ALL: "Toutes les consultations en cours, sauf celles écartées par les critères de l'entreprise.",
  FILTERED_OUT:
    "Avis écartés par les critères de l'entreprise. Vérifiez ici que le filtre ne fait pas disparaître de bons marchés.",
};

function TenderList() {
  const router = useRouter();
  const [searchParams, navigate] = useUrlParams();
  const tabParam = searchParams.get("tab") as TenderTab | null;
  const tab = tabParam && TABS.includes(tabParam) ? tabParam : "NEW";
  const movedTo = tabParam ? MOVED_TABS[tabParam] : undefined;
  const text = searchParams.get("q") ?? "";
  const alertId = searchParams.get("alert");
  const filtered = Boolean(text || alertId) || OPEN_FILTERS.some((key) => searchParams.get(key));
  const query = searchParams.toString();

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
  const alerts = useLoader(() => (alertId ? getAlerts() : Promise.resolve([])), alertId ?? "");
  const alert = alerts.data?.find((candidate) => String(candidate.id) === alertId);

  // A saved search started from the current search and filters.
  const alertParams = new URLSearchParams(countParams);
  alertParams.set("new", "1");

  useEffect(() => {
    if (movedTo) {
      router.replace(movedTo);
    }
  }, [movedTo, router]);

  if (movedTo) {
    return null;
  }

  return (
    <>
      <h1>Appels d&apos;offres</h1>
      <CollectStatus />

      <SearchForm
        value={text}
        placeholder="Rechercher : objet, référence, acheteur, ville, code postal, type de travaux, CPV…"
        onSearch={(value) => navigate({ q: value, page: null })}
      >
        {filtered && !alertId && <Link href={`/alerts?${alertParams}`}>Enregistrer comme veille</Link>}
      </SearchForm>

      {alertId && (
        <p className="banner info">
          {alerts.data && !alert ? "Cette veille n'existe plus." : `Résultats de la veille « ${alert?.name ?? "…"} ».`}{" "}
          <Link href="/alerts">Mes veilles</Link>
          {" · "}
          <button type="button" className="link-button" onClick={() => navigate({ alert: null, page: null })}>
            Quitter la veille
          </button>
        </p>
      )}

      <TenderFilters params={searchParams} onApply={navigate} fields={OPEN_FILTERS} />

      <div className="tabs" role="tablist">
        {TABS.map((value) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            className={tab === value ? "tab active" : "tab"}
            onClick={() => navigate({ tab: value, page: null })}
          >
            {TENDER_TAB_LABELS[value]}
            {counts.data && <span className="count">{counts.data[value]}</span>}
          </button>
        ))}
      </div>

      {TAB_HINTS[tab] && <p className="hint">{TAB_HINTS[tab]}</p>}
      {list.error && <p className="banner error">{list.error}</p>}
      {list.loading && !list.data && <p className="page-message">Chargement…</p>}

      {list.data && (
        <TenderResults
          data={list.data}
          loading={list.loading}
          params={searchParams}
          onNavigate={navigate}
          caption={text && <> pour « {text} »</>}
          emptyMessage={
            filtered
              ? "Aucun appel d'offres ne correspond à la recherche et aux filtres dans cet onglet."
              : "Aucun appel d'offres dans cet onglet."
          }
          onChanged={() => {
            list.reload();
            counts.reload();
          }}
        />
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
