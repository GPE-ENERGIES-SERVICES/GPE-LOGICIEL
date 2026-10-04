"use client";

import Link from "next/link";
import { Suspense } from "react";

import { SearchForm } from "@/components/SearchForm";
import { TenderResults } from "@/components/TenderResults";
import { FOLLOW_UP_LABELS } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { useUrlParams } from "@/lib/useUrlParams";
import { getStatusCounts, getTenders } from "@/services/tenderApi";
import type { FollowUpStatus } from "@/types/tender";

const STATUSES = Object.keys(FOLLOW_UP_LABELS) as FollowUpStatus[];

/**
 * Consultations followed by the user, open or past, by status in the
 * company's process.
 */
function FollowList() {
  const [searchParams, navigate] = useUrlParams();
  const statusParam = searchParams.get("status") as FollowUpStatus | null;
  const status = statusParam && STATUSES.includes(statusParam) ? statusParam : null;
  const text = searchParams.get("q") ?? "";

  const params = new URLSearchParams(searchParams);
  params.set("tab", "FOLLOWED");
  const countParams = new URLSearchParams(params);
  for (const key of ["status", "page", "size", "sort"]) {
    countParams.delete(key);
  }

  const list = useLoader(() => getTenders(params), params.toString());
  const counts = useLoader(() => getStatusCounts(countParams), countParams.toString());
  const total = counts.data ? Object.values(counts.data).reduce((sum, count) => sum + count, 0) : null;

  const statusTab = (value: FollowUpStatus | null, label: string, count: number | null | undefined) => (
    <button
      key={value ?? "ALL"}
      type="button"
      role="tab"
      aria-selected={status === value}
      className={status === value ? "tab active" : "tab"}
      onClick={() => navigate({ status: value, page: null })}
    >
      {label}
      {count != null && <span className="count">{count}</span>}
    </button>
  );

  return (
    <>
      <h1>Mes suivis</h1>
      <p className="muted">
        Les consultations que vous suivez avec l&apos;étoile, en cours ou passées. Le suivi vous est propre ; le statut
        est partagé par l&apos;équipe.
      </p>

      <SearchForm
        value={text}
        placeholder="Rechercher dans mes suivis : objet, référence, acheteur, ville…"
        onSearch={(value) => navigate({ q: value, page: null })}
      />

      <div className="tabs" role="tablist">
        {statusTab(null, "Tous", total)}
        {STATUSES.map((value) => statusTab(value, FOLLOW_UP_LABELS[value], counts.data?.[value]))}
      </div>

      {list.error && <p className="banner error">{list.error}</p>}
      {list.loading && !list.data && <p className="page-message">Chargement…</p>}

      {total === 0 && !text ? (
        <p className="page-message">
          Vous ne suivez aucune consultation. Cliquez sur l&apos;étoile ☆ d&apos;une consultation, dans{" "}
          <Link href="/tenders">Appels d&apos;offres</Link> ou sur sa fiche, pour la retrouver ici.
        </p>
      ) : (
        list.data && (
          <TenderResults
            data={list.data}
            loading={list.loading}
            params={searchParams}
            onNavigate={navigate}
            caption={text && <> pour « {text} »</>}
            emptyMessage={
              text ? "Aucune consultation suivie ne correspond à la recherche." : "Aucune consultation suivie avec ce statut."
            }
            onChanged={() => {
              list.reload();
              counts.reload();
            }}
          />
        )
      )}
    </>
  );
}

export default function FollowsPage() {
  return (
    <Suspense>
      <FollowList />
    </Suspense>
  );
}
