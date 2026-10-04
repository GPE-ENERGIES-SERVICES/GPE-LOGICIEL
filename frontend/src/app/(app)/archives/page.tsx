"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense } from "react";

import { ListToolbar } from "@/components/ListToolbar";
import { Pagination } from "@/components/Pagination";
import { SearchForm } from "@/components/SearchForm";
import { PAST_FILTERS, TenderFilters } from "@/components/TenderFilters";
import { ACTIVITY_LABELS, formatAmount, formatDate, MARKET_TYPE_LABELS } from "@/lib/format";
import { tenderHref } from "@/lib/navigation";
import { useLoader } from "@/lib/useLoader";
import { useUrlParams } from "@/lib/useUrlParams";
import { getTenders } from "@/services/tenderApi";
import type { TenderResult, TenderSummary } from "@/types/tender";

/** The most recent first by default. */
const SORT_OPTIONS = [
  { value: "", label: "Plus récentes" },
  { value: "published", label: "Plus anciennes" },
  { value: "-amount", label: "Montant décroissant" },
  { value: "amount", label: "Montant croissant" },
];

const RESULT_LABELS: Record<TenderResult["kind"], string> = {
  AWARDED: "Attribuée",
  UNSUCCESSFUL: "Sans suite",
  CANCELLED: "Annulée",
};

/** The backend gives the outcome in the detail only; the list falls back on the status of the consultation. */
function resultLabel(tender: TenderSummary): string {
  const kind = tender.result?.kind ?? (tender.status === "CLOSED" || tender.status === "ACTIVE" ? null : tender.status);
  return kind ? RESULT_LABELS[kind] : "Close, résultat non connu";
}

function ArchiveRow({ tender, href }: { tender: TenderSummary; href: string }) {
  const area = tender.postalCode ?? tender.departments.join(", ");
  const about = [tender.buyer ?? "Acheteur non indiqué", tender.reference && `Réf. ${tender.reference}`]
    .filter(Boolean)
    .join(" · ");
  return (
    <tr>
      <td className="tender-title">
        <Link href={href}>{tender.title}</Link>
        <div className="small muted">{about}</div>
      </td>
      <td>
        {tender.location ?? "Non indiqué"}
        {area && <div className="small muted">{area}</div>}
      </td>
      <td>
        {(tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code).join(", ")}
        <div className="small muted">{tender.marketTypes.map((type) => MARKET_TYPE_LABELS[type]).join(", ")}</div>
      </td>
      <td className="nowrap">{formatDate(tender.publishedAt)}</td>
      <td className="nowrap">{formatAmount(tender.estimatedAmount)}</td>
      <td className="nowrap">
        {resultLabel(tender)}
        {tender.followUpStatus === "SUBMITTED" && <div className="small muted">Réponse envoyée</div>}
      </td>
      <td>
        {tender.winners?.length ? tender.winners.join(", ") : "–"}
        {tender.winners?.length ? <div className="small muted">{formatAmount(tender.awardedAmount ?? null)}</div> : null}
      </td>
    </tr>
  );
}

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
        Les consultations passées : closes, attribuées, sans suite ou annulées, avec leur résultat et
        l&apos;attributaire lorsqu&apos;ils sont connus.
      </p>

      <SearchForm
        value={text}
        placeholder="Rechercher dans les archives : objet, référence, acheteur, ville, type de travaux, CPV…"
        onSearch={(value) => navigate({ q: value, page: null })}
      />
      <TenderFilters params={searchParams} onApply={navigate} fields={PAST_FILTERS} />

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
            <div className="card table-card">
              <table className="tender-table">
                <thead>
                  <tr>
                    <th>Consultation</th>
                    <th>Lieu</th>
                    <th>Travaux</th>
                    <th>Publication</th>
                    <th>Montant estimé</th>
                    <th>Résultat</th>
                    <th>Attributaire</th>
                  </tr>
                </thead>
                <tbody>
                  {list.data.content.map((tender) => (
                    <ArchiveRow key={tender.id} tender={tender} href={tenderHref(tender.id, from)} />
                  ))}
                </tbody>
              </table>
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
