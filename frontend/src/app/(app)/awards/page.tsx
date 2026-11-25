"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense } from "react";

import { ListToolbar } from "@/components/ListToolbar";
import { Pagination } from "@/components/Pagination";
import { SearchForm } from "@/components/SearchForm";
import { AWARD_FILTERS, TenderFilters } from "@/components/TenderFilters";
import { Icon } from "@/components/Icon";
import { DomainChips, TenderCard } from "@/components/TenderCard";
import { formatAmount, formatDate, MARKET_TYPE_LABELS } from "@/lib/format";
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

/** An award in the banner of its tender: who won it, for how much. */
function AwardItem({ award, from, onWinner }: RowProps) {
  const place = [award.location, award.postalCode && `(${award.postalCode})`].filter(Boolean).join(" ") || null;
  return (
    <TenderCard
      banner={{
        kicker: (
          <>
            <Icon name="award" size={14} /> Attribué le <strong>{formatDate(award.awardedAt)}</strong>
          </>
        ),
        buyer: award.buyer,
        place,
        badges: (
          <>
            {award.won && <span className="badge success">Gagné par GPE</span>}
            {award.source && (
              <span className="badge muted-badge">{award.source === "NOTICE" ? "Avis publié" : "Confirmé manuellement"}</span>
            )}
          </>
        ),
      }}
    >
      <p className="tender-card-meta">
        {[MARKET_TYPE_LABELS[award.marketType], award.lot].filter(Boolean).join(" | ")}
      </p>
      <h3 className="tender-card-title">
        {award.tenderId ? <Link href={tenderHref(award.tenderId, from)}>{award.title}</Link> : award.title}
      </h3>
      <div className="tender-card-award">
        <span className="tender-card-label">Attributaire :</span>
        <button type="button" className="link-button" title="Voir ses marchés" onClick={() => onWinner(award.winner)}>
          {award.winner}
        </button>
        {award.winnerCity && <span className="muted">({award.winnerCity})</span>}
        <span>
          pour un montant de <strong>{formatAmount(award.amount)}</strong>
        </span>
        {award.offersReceived != null && <span className="muted">· {award.offersReceived} offres reçues</span>}
      </div>
      <div className="tender-card-foot">
        <span className="chips">
          <DomainChips activities={award.activities} />
        </span>
      </div>
    </TenderCard>
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
        Résultats d&apos;attribution, un par lot : attributaire et montant, dont les marchés gagnés par GPE. Cliquez sur
        un attributaire pour voir ses marchés.
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
            <div className="tender-list">
              {data.content.map((award) => (
                <AwardItem
                  key={award.id}
                  award={award}
                  from={from}
                  onWinner={(winner) => navigate({ winner, page: null })}
                />
              ))}
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
