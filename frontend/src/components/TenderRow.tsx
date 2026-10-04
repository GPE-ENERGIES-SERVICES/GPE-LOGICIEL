"use client";

import Link from "next/link";

import {
  ACTIVITY_LABELS,
  formatAmount,
  formatDate,
  formatDateTime,
  formatDaysLeft,
  FOLLOW_UP_LABELS,
  isDeadlineClose,
  NOT_INDICATED,
  PROCEDURE_LABELS,
  STATUS_LABELS,
} from "@/lib/format";
import type { FollowUpStatus, TenderSummary } from "@/lib/types";

type Props = {
  tender: TenderSummary;
  onFollowUp: (status: FollowUpStatus) => void;
  busy: boolean;
};

/** One line of the tender table: what is needed to sort out many tenders quickly. */
export function TenderRow({ tender, onFollowUp, busy }: Props) {
  const place = [tender.location, tender.departments.length ? `(${tender.departments.join(", ")})` : null]
    .filter(Boolean)
    .join(" ");
  const about = [tender.buyer ?? "Acheteur non indiqué", tender.reference && `Réf. ${tender.reference}`, tender.source]
    .filter(Boolean)
    .join(" · ");
  const activities = (tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code).join(", ");
  const lotCount = tender.lotCount ?? 0;

  return (
    <tr>
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
          <span className={isDeadlineClose(tender.deadline) ? "badge urgent" : "badge"}>
            {formatDaysLeft(tender.deadline)}
          </span>
        </div>
      </td>
      <td className="nowrap">
        {tender.relevanceScore != null ? `${tender.relevanceScore} %` : "–"}
        <div className="small muted" title={tender.filterReason ?? undefined}>
          {tender.retained === false ? "Hors critères" : "Retenu"}
        </div>
      </td>
      <td className="tender-actions">
        <span className="badge">{FOLLOW_UP_LABELS[tender.followUpStatus]}</span>
        {tender.status !== "ACTIVE" && <span className="badge">{STATUS_LABELS[tender.status]}</span>}
        {/* Nothing left to decide on a closed tender. */}
        <div hidden={tender.status !== "ACTIVE"}>
          {tender.followUpStatus === "NEW" && (
            <button type="button" disabled={busy} onClick={() => onFollowUp("TO_STUDY")}>
              À étudier
            </button>
          )}
          {tender.followUpStatus === "TO_STUDY" && (
            <button type="button" disabled={busy} onClick={() => onFollowUp("RESPONDING")}>
              En réponse
            </button>
          )}
          {tender.followUpStatus !== "DISMISSED" ? (
            <button type="button" className="secondary" disabled={busy} onClick={() => onFollowUp("DISMISSED")}>
              Écarter
            </button>
          ) : (
            <button type="button" className="secondary" disabled={busy} onClick={() => onFollowUp("NEW")}>
              Rétablir
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}
