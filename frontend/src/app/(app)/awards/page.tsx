"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense } from "react";

import { ListToolbar } from "@/components/ListToolbar";
import { Pagination } from "@/components/Pagination";
import { SearchForm } from "@/components/SearchForm";
import { AWARD_FILTERS, TenderFilters } from "@/components/TenderFilters";
import { ACTIVITY_LABELS, formatAmount, formatDate, MARKET_TYPE_LABELS } from "@/lib/format";
import { tenderHref } from "@/lib/navigation";
import { NotConnected } from "@/components/NotConnected";
import { useLoader } from "@/lib/useLoader";
import { useUrlParams } from "@/lib/useUrlParams";
import { getAwardedContracts } from "@/services/awardApi";
import { available } from "@/services/http";
import type { AwardedContract } from "@/types/award";

/** The most recent award first by default. */
const SORT_OPTIONS = [
  { value: "", label: "Plus récentes" },
  { value: "awardedAt", label: "Plus anciennes" },
  { value: "-amount", label: "Montant décroissant" },
  { value: "amount", label: "Montant croissant" },
];

type RowProps = {
  award: AwardedContract;
  from: string;
  onWinner: (winner: string) => void;
};

function AwardRow({ award, from, onWinner }: RowProps) {
  return (
    <tr>
      <td className="tender-title">
        {award.tenderId ? <Link href={tenderHref(award.tenderId, from)}>{award.title}</Link> : award.title}
        {award.lot && <div className="small muted">{award.lot}</div>}
      </td>
      <td>{award.buyer}</td>
      <td>
        {award.location ?? "Non indiqué"}
        {award.postalCode && <div className="small muted">{award.postalCode}</div>}
      </td>
      <td>
        {award.activities.map((code) => ACTIVITY_LABELS[code] ?? code).join(", ")}
        <div className="small muted">{MARKET_TYPE_LABELS[award.marketType]}</div>
      </td>
      <td>
        <button type="button" className="link-button" title="Voir ses marchés" onClick={() => onWinner(award.winner)}>
          {award.winner}
        </button>
        {award.winnerCity && <div className="small muted">{award.winnerCity}</div>}
      </td>
      <td className="nowrap">{formatAmount(award.amount)}</td>
      <td className="nowrap">{formatDate(award.awardedAt)}</td>
      <td className="number">{award.offersReceived ?? "–"}</td>
    </tr>
  );
}

/** Results of the award notices: who won which contract, for how much. */
function AwardList() {
  const [searchParams, navigate] = useUrlParams();
  const pathname = usePathname();
  const text = searchParams.get("q") ?? "";
  const filtered = Boolean(text) || AWARD_FILTERS.some((key) => searchParams.get(key));
  const page = Number(searchParams.get("page") ?? "0");
  const query = searchParams.toString();
  const from = query ? `${pathname}?${query}` : pathname;
  const list = useLoader(() => getAwardedContracts(searchParams), query);
  const data = list.data;

  return (
    <>
      <h1>Marchés attribués</h1>
      <p className="muted">
        Les résultats des avis d&apos;attribution, un par lot : quelle entreprise a remporté quel marché, pour quel
        montant. Un clic sur un attributaire affiche ses marchés.
      </p>

      <SearchForm
        value={text}
        placeholder="Rechercher : objet, acheteur, attributaire, ville, type de travaux…"
        onSearch={(value) => navigate({ q: value, page: null })}
      />
      <TenderFilters params={searchParams} onApply={navigate} fields={AWARD_FILTERS} />

      {list.error && <p className="banner error">{list.error}</p>}
      {list.loading && !data && <p className="page-message">Chargement…</p>}

      {data && (
        <>
          <ListToolbar
            summary={
              <>
                {data.totalElements} attribution{data.totalElements > 1 ? "s" : ""} · {data.winnerCount} attributaire
                {data.winnerCount > 1 ? "s" : ""} · {formatAmount(data.totalAmount)} attribués
                {text && <> pour « {text} »</>}
                {list.loading && " · chargement…"}
              </>
            }
            sortOptions={SORT_OPTIONS}
            params={searchParams}
            onNavigate={navigate}
          />
          {data.content.length === 0 ? (
            <p className="page-message">
              {filtered ? "Aucune attribution ne correspond à la recherche et aux filtres." : "Aucune attribution."}
            </p>
          ) : (
            <div className="card table-card">
              <table className="tender-table">
                <thead>
                  <tr>
                    <th>Marché</th>
                    <th>Acheteur</th>
                    <th>Lieu</th>
                    <th>Travaux</th>
                    <th>Attributaire</th>
                    <th>Montant attribué</th>
                    <th>Attribution</th>
                    <th>Offres</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((award) => (
                    <AwardRow
                      key={award.id}
                      award={award}
                      from={from}
                      onWinner={(winner) => navigate({ winner, page: null })}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={page} totalPages={data.totalPages} onNavigate={navigate} />
        </>
      )}
    </>
  );
}

export default function AwardsPage() {
  if (!available("awards")) {
    return <NotConnected title="Marchés attribués" />;
  }
  return (
    <Suspense>
      <AwardList />
    </Suspense>
  );
}
