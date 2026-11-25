"use client";

import Link from "next/link";
import type { ReactNode } from "react";

import {
  ACTIVITY_SHORT,
  followUpClass,
  followUpChoices,
  formatAmount,
  formatDate,
  formatDateTime,
  FOLLOW_UP_LABELS,
  MARKET_TYPE_LABELS,
  NOT_INDICATED,
  PROCEDURE_LABELS,
  STATUS_LABELS,
} from "@/lib/format";
import { available } from "@/services/http";
import { MANUAL_SOURCE } from "@/services/tenderApi";
import type { FollowUpStatus, TenderSummary } from "@/types/tender";

import { DeadlineBadge } from "./DeadlineBadge";
import { FollowButton } from "./FollowButton";
import { Icon } from "./Icon";

const RESULT_LABELS: Record<string, string> = {
  AWARDED: "Attribuée",
  UNSUCCESSFUL: "Sans suite",
  CANCELLED: "Annulée",
};

/** The place of a tender: city, then postal code or departments. */
export function placeOf(tender: Pick<TenderSummary, "location" | "postalCode" | "departments">): string | null {
  const area = tender.postalCode ?? (tender.departments.length ? tender.departments.join(", ") : null);
  return [tender.location, area && `(${area})`].filter(Boolean).join(" ") || null;
}

/** The domains of a tender as small labels. */
export function DomainChips({ activities }: { activities?: string[] }) {
  return (
    <>
      {(activities ?? []).map((code) => (
        <span key={code} className="chip" data-domain={code.toLowerCase()}>
          {ACTIVITY_SHORT[code] ?? code}
        </span>
      ))}
    </>
  );
}

type BannerProps = {
  /** First line: the deadline, the award… */
  kicker?: ReactNode;
  buyer: string | null;
  place?: string | null;
  /** On the right of the first line: statuses and alerts. */
  badges?: ReactNode;
  /** Before the buyer, such as the follow star. */
  lead?: ReactNode;
};

/**
 * The band that identifies a tender at a glance, the same in every list and
 * on its page: one colour for all, the buyer and the place, its statuses.
 * The domains are in the body, with the type of market.
 */
export function TenderBanner({ kicker, buyer, place, badges, lead }: BannerProps) {
  return (
    <div className="tender-banner">
      <div className="tender-banner-top">
        {kicker && <span className="tender-banner-kicker">{kicker}</span>}
        {badges && <span className="tender-banner-badges">{badges}</span>}
      </div>
      <div className="tender-banner-main">
        {lead}
        <span className="tender-banner-buyer">{buyer ?? "Acheteur non indiqué"}</span>
        {place && <span className="tender-banner-place">({place})</span>}
      </div>
    </div>
  );
}

/** A banner and the compact body of a tender. */
export function TenderCard({ banner, children }: { banner: BannerProps; children: ReactNode }) {
  return (
    <article className="tender-card">
      <TenderBanner {...banner} />
      <div className="tender-card-body">{children}</div>
    </article>
  );
}

/** What the banner says first: the deadline of an open tender, the outcome of a past one. */
export function tenderKicker(tender: TenderSummary): ReactNode {
  if (tender.status !== "ACTIVE") {
    const outcome = tender.result ? RESULT_LABELS[tender.result.kind] : `${STATUS_LABELS[tender.status]}, résultat non connu`;
    return (
      <>
        <Icon name="clock" size={14} /> {outcome}
        {tender.winners?.length ? ` · ${tender.winners.join(", ")}` : ""}
      </>
    );
  }
  return (
    <>
      <Icon name="clock" size={14} /> Date limite :{" "}
      <strong>{tender.deadline ? formatDateTime(tender.deadline) : NOT_INDICATED}</strong>{" "}
      <DeadlineBadge deadline={tender.deadline} />
    </>
  );
}

/** The badges of the banner: the status of the team, then what must catch the eye. */
export function TenderBadges({ tender }: { tender: TenderSummary }) {
  return (
    <>
      <span className={`badge ${followUpClass(tender.followUpStatus)}`}>{FOLLOW_UP_LABELS[tender.followUpStatus]}</span>
      {tender.status !== "ACTIVE" && <span className="badge muted-badge">{STATUS_LABELS[tender.status]}</span>}
      {tender.retained === false && (
        <span className="badge warning" title={tender.filterReason ?? undefined}>
          Hors critères
        </span>
      )}
      {tender.source === MANUAL_SOURCE && <span className="badge info">Ajouté manuellement</span>}
    </>
  );
}

type ItemProps = {
  tender: TenderSummary;
  /** Link to the details, remembering the list. */
  href: string;
  /** To change the status in the list; without it the status is only shown. */
  onFollowUp?: (status: FollowUpStatus) => void;
  onFollowChanged?: () => void;
  busy?: boolean;
};

/** A tender of a list: what is needed to sort out many tenders quickly. */
export function TenderItem({ tender, href, onFollowUp, onFollowChanged, busy = false }: ItemProps) {
  const documentCount = tender.documentCount ?? 0;
  const lotCount = tender.lotCount ?? 0;
  const meta = [
    tender.procedureType && PROCEDURE_LABELS[tender.procedureType],
    tender.reference && `Réf. ${tender.reference}`,
    tender.source === MANUAL_SOURCE ? null : tender.source,
  ]
    .filter(Boolean)
    .join(" | ");

  return (
    <TenderCard
      banner={{
        kicker: tenderKicker(tender),
        buyer: tender.buyer,
        place: placeOf(tender),
        badges: <TenderBadges tender={tender} />,
        lead: onFollowChanged && (
          <FollowButton
            key={String(tender.followed)}
            tenderId={tender.id}
            followed={tender.followed ?? false}
            onChanged={onFollowChanged}
          />
        ),
      }}
    >
      {meta && <p className="tender-card-meta">{meta}</p>}
      <h3 className="tender-card-title">
        <Link href={href} title={tender.title}>
          {tender.title}
        </Link>
      </h3>
      <div className="tender-card-section tender-card-columns">
        <div>
          <span>
            <Icon name="pin" size={15} /> {placeOf(tender) ?? NOT_INDICATED}
            {tender.distanceKm != null && <span className="muted"> · à {tender.distanceKm} km du siège</span>}
          </span>
          <span className="chips">
            {available("activity") && <DomainChips activities={tender.activities} />}
            {tender.marketTypes.map((type) => (
              <span key={type} className="chip">
                {MARKET_TYPE_LABELS[type]}
              </span>
            ))}
          </span>
        </div>
        <div>
          <span>
            Montant estimé : <strong>{formatAmount(tender.estimatedAmount)}</strong>
          </span>
          {tender.awardedAmount != null && (
            <span>
              Montant attribué : <strong>{formatAmount(tender.awardedAmount)}</strong>
            </span>
          )}
          <span>
            {lotCount > 0 ? `${lotCount} lot${lotCount > 1 ? "s" : ""}` : "Lot unique"}
            {documentCount > 0 && ` · DCE : ${documentCount} document${documentCount > 1 ? "s" : ""}`}
            {available("relevance") && tender.relevanceScore != null && (
              <>
                {" "}
                · Pertinence : <strong>{tender.relevanceScore} %</strong>
              </>
            )}
          </span>
        </div>
      </div>
      <div className="tender-card-foot">
        <span className="tender-card-published">Publié le {formatDate(tender.publishedAt)}</span>
        {onFollowUp && (
          <select
            className={`status-select ${followUpClass(tender.followUpStatus)}`}
            value={tender.followUpStatus}
            disabled={busy}
            aria-label="Statut"
            onChange={(e) => onFollowUp(e.target.value as FollowUpStatus)}
          >
            {followUpChoices(tender.followUpStatus).map(({ status, blocked }) => (
              <option key={status} value={status} disabled={blocked !== null}>
                {FOLLOW_UP_LABELS[status]}
                {blocked && ` (${blocked})`}
              </option>
            ))}
          </select>
        )}
      </div>
    </TenderCard>
  );
}
