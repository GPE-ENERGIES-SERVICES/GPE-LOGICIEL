"use client";

import Link from "next/link";

import {
  formatAmount,
  formatDateTime,
  formatDaysLeft,
  isDeadlineClose,
  orNotIndicated,
  STATUS_LABELS,
} from "@/lib/format";
import type { FollowUpStatus, TenderSummary } from "@/lib/types";

type Props = {
  tender: TenderSummary;
  onFollowUp: (status: FollowUpStatus) => void;
  busy: boolean;
};

export function TenderCard({ tender, onFollowUp, busy }: Props) {
  const place = [tender.location, tender.departments.length ? `(${tender.departments.join(", ")})` : null]
    .filter(Boolean)
    .join(" ");

  return (
    <article className="card tender-card">
      <div className="tender-card-main">
        <h3>
          <Link href={`/tenders/${tender.id}`}>{tender.title}</Link>
        </h3>
        <p className="muted">{orNotIndicated(tender.buyer)}</p>
        <dl className="facts">
          <div>
            <dt>Lieu</dt>
            <dd>{orNotIndicated(place)}</dd>
          </div>
          <div>
            <dt>Date limite</dt>
            <dd>
              {formatDateTime(tender.deadline)}
              <span className={isDeadlineClose(tender.deadline) ? "badge urgent" : "badge"}>
                {formatDaysLeft(tender.deadline)}
              </span>
            </dd>
          </div>
          <div>
            <dt>Budget estimé</dt>
            <dd>{formatAmount(tender.estimatedAmount)}</dd>
          </div>
        </dl>
        {tender.filterReason && <p className="reason">{tender.filterReason}</p>}
      </div>
      <div className="tender-card-actions">
        {tender.status !== "ACTIVE" && <span className="badge">{STATUS_LABELS[tender.status]}</span>}
        {tender.followUpStatus !== "TO_STUDY" && tender.followUpStatus !== "RESPONDING" && (
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
    </article>
  );
}
