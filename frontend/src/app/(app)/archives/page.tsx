"use client";

import { usePathname } from "next/navigation";
import { Suspense } from "react";

import { ListToolbar } from "@/components/ListToolbar";
import { Pagination } from "@/components/Pagination";
import { SearchForm } from "@/components/SearchForm";
import { TenderItem } from "@/components/TenderCard";
import { PAST_FILTERS, TenderFilters } from "@/components/TenderFilters";
import { tenderHref } from "@/lib/navigation";
import { useLoader } from "@/lib/useLoader";
import { useUrlParams } from "@/lib/useUrlParams";
import { getTenders } from "@/services/tenderApi";

/** The most recent first by default. */
const SORT_OPTIONS = [
  { value: "", label: "Plus récentes" },
  { value: "published", label: "Plus anciennes" },
  { value: "-amount", label: "Montant décroissant" },
  { value: "amount", label: "Montant croissant" },
];

/** Past consultations: closed, awarded, abandoned or cancelled, with their outcome when known. */
function ArchiveList() {
  const [searchParams, navigate] = useUrlParams();
  const pathname = usePathname();
  const text = searchParams.get("q") ?? "";
  const filtered = Boolean(text) || PAST_FILTERS.some((key) => searchParams.get(key));
  const page = Number(searchParams.get("page") ?? "0");
  const query = searchParams.toString();
  const from = query ? `${pathname}?${query}` : pathname;

  const params = new URLSearchParams(searchParams);
  params.set("tab", "ARCHIVED");
  if (!params.get("sort")) {
    params.set("sort", "-published");
  }
  const list = useLoader(() => getTenders(params), params.toString());
  const total = list.data?.totalElements ?? 0;

  return (
    <>
      <h1>Archives</h1>
      <p className="muted">
        Consultations closes, attribuées, sans suite ou annulées, avec résultat et attributaire quand ils sont connus.
      </p>

      <SearchForm
        value={text}
        placeholder="Rechercher dans les archives : objet, référence, acheteur, ville, type de travaux, CPV…"
        onSearch={(value) => navigate({ q: value, page: null })}
      />
      <TenderFilters params={searchParams} onApply={navigate} fields={PAST_FILTERS} collapsible />

      {list.error && <p className="banner error">{list.error}</p>}
      {list.loading && !list.data && <p className="page-message">Chargement…</p>}

      {list.data && (
        <>
          <ListToolbar
            summary={
              <>
                {total} consultation{total > 1 ? "s" : ""} passée{total > 1 ? "s" : ""}
                {text && <> pour « {text} »</>}
                {list.loading && " · chargement…"}
              </>
            }
            sortOptions={SORT_OPTIONS}
            params={searchParams}
            onNavigate={navigate}
          />
          {list.data.content.length === 0 ? (
            <p className="page-message">
              {filtered
                ? "Aucune consultation passée ne correspond à la recherche et aux filtres."
                : "Aucune consultation passée."}
            </p>
          ) : (
            <div className="tender-list">
              {list.data.content.map((tender) => (
                <TenderItem key={tender.id} tender={tender} href={tenderHref(tender.id, from)} />
              ))}
            </div>
          )}
          <Pagination page={page} totalPages={list.data.totalPages} onNavigate={navigate} />
        </>
      )}
    </>
  );
}

export default function ArchivesPage() {
  return (
    <Suspense>
      <ArchiveList />
    </Suspense>
  );
}
