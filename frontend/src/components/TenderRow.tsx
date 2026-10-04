"use client";

import Link from "next/link";

import {
  ACTIVITY_LABELS,
  formatAmount,
  formatDate,
  formatDateTime,
  FOLLOW_UP_LABELS,
  NOT_INDICATED,
  PROCEDURE_LABELS,
  STATUS_LABELS,
} from "@/lib/format";
import type { FollowUpStatus, TenderSummary } from "@/types/tender";

import { DeadlineBadge } from "./DeadlineBadge";
import { FollowButton } from "./FollowButton";

type Props = {
  tender: TenderSummary;
  onFollowUp: (status: FollowUpStatus) => void;
  onFollowChanged: () => void;
  busy: boolean;
};

/** One line of the tender table: what is needed to sort out many tenders quickly. */
export function TenderRow({ tender, onFollowUp, onFollowChanged, busy }: Props) {
  const place = [tender.location, tender.departments.length ? `(${tender.departments.join(", ")})` : null]
    .filter(Boolean)
    .join(" ");
  const documentCount = tender.documentCount ?? 0;
  const about = [
    tender.buyer ?? "Acheteur non indiqué",
    tender.reference && `Réf. ${tender.reference}`,
    tender.source,
    documentCount > 0 && `DCE : ${documentCount} document${documentCount > 1 ? "s" : ""}`,
  ]
    .filter(Boolean)
    .join(" · ");
  const activities = (tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code).join(", ");
  const lotCount = tender.lotCount ?? 0;

  return (
    <tr>
      <td>
        <FollowButton tenderId={tender.id} followed={tender.followed ?? false} onChanged={onFollowChanged} />
      </td>
      <td className="tender-title">
        <Link href={`/tenders/${tender.id}`}>{tender.title}</Link>
        <div className="small muted">{about}</div>
      </td>
      <td>
        {place || NOT_INDICATED}
        {tender.distanceKm != null && <div className="small muted">à {tender.distanceKm} km</div>}
      </td>
      <td>
        {activities || NOT_INDICATED}
        <div className="small muted">
          {lotCount > 0 ? `${lotCount} lot${lotCount > 1 ? "s" : ""}` : "Sans lots"}
          {tender.procedureType && <> · {PROCEDURE_LABELS[tender.procedureType]}</>}
        </div>
      </td>
      <td className="nowrap">{formatAmount(tender.estimatedAmount)}</td>
      <td className="nowrap">{formatDate(tender.publishedAt)}</td>
      <td className="nowrap">
        {tender.deadline ? formatDateTime(tender.deadline) : NOT_INDICATED}
        <div>
          <DeadlineBadge deadline={tender.deadline} />
        </div>
      </td>
      <td className="nowrap">
        {tender.relevanceScore != null ? `${tender.relevanceScore} %` : "–"}
        <div className="small muted" title={tender.filterReason ?? undefined}>
          {tender.retained === false ? "Hors critères" : "Retenu"}
        </div>
      </td>
      <td>
        <select
          value={tender.followUpStatus}
          disabled={busy}
          aria-label="Statut"
          onChange={(e) => onFollowUp(e.target.value as FollowUpStatus)}
        >
          {(Object.keys(FOLLOW_UP_LABELS) as FollowUpStatus[]).map((status) => (
            <option key={status} value={status}>
              {FOLLOW_UP_LABELS[status]}
            </option>
          ))}
        </select>
        {tender.status !== "ACTIVE" && (
          <div>
            <span className="badge">{STATUS_LABELS[tender.status]}</span>
          </div>
        )}
      </td>
    </tr>
  );
}
