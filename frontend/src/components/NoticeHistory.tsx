import Link from "next/link";

import { formatDate, formatDateTime, NOTICE_KIND_LABELS } from "@/lib/format";
import type { TenderDetail, TenderMatch } from "@/types/tender";

/**
 * What each rule found in common: only a reason to check, never a certainty
 * that the two consultations are the same procedure.
 */
const MATCH_RULE_LABELS: Record<TenderMatch["rule"], string> = {
  SAME_PROCEDURE: "même identifiant de procédure eForms",
  SAME_REFERENCE: "même référence d'acheteur et même date limite (pas d'identifiant de procédure commun)",
};

/**
 * The publications of a consultation, oldest first, with the source of each:
 * a notice of another source publishing the same consultation (TED for a
 * BOAMP consultation) is only shown, it never changes the consultation.
 * Consultations of other sources proposed as the same one are listed for a
 * person to check.
 */
export function NoticeHistory({ tender }: { tender: TenderDetail }) {
  const notices = [...tender.notices].sort((a, b) => (a.publishedAt ?? "").localeCompare(b.publishedAt ?? ""));
  const matches = (tender.matches ?? []).filter((match) => match.status !== "REJECTED");
  return (
    <>
      {tender.mergedIntoId && (
        <p className="banner">
          Cette consultation est un doublon fusionné dans la{" "}
          <Link href={`/tenders/${tender.mergedIntoId}`}>consultation N° {tender.mergedIntoId}</Link> : elle
          n&apos;apparaît plus dans les listes.
        </p>
      )}
      <ol className="history">
        {notices.map((notice) => (
          <li key={`${notice.source}-${notice.externalId}`}>
            <strong>{NOTICE_KIND_LABELS[notice.kind]}</strong> du {formatDate(notice.publishedAt)} ({notice.source}{" "}
            {notice.externalId})
            {notice.deadline && <> — date limite annoncée : {formatDateTime(notice.deadline)}</>}
            {notice.source !== tender.source && (
              <p className="hint">Publication de la même consultation sur {notice.source}, conservée pour information.</p>
            )}
            {notice.details && <p className="description">{notice.details}</p>}
          </li>
        ))}
      </ol>
      {matches.length > 0 && (
        <div className="banner muted">
          <p>
            {matches.length > 1
              ? "Consultations d'autres sources proposées comme pouvant être la même"
              : "Consultation d'une autre source proposée comme pouvant être la même"}
            , à vérifier (rien n&apos;est fusionné automatiquement) :
          </p>
          <ul>
            {matches.map((match) => (
              <li key={match.id}>
                <Link href={`/tenders/${match.tenderId}`}>
                  {match.source} {match.externalId} : {match.title}
                </Link>{" "}
                — {MATCH_RULE_LABELS[match.rule] ?? `règle ${match.rule}`}
                {match.status === "CONFIRMED" && " (confirmé)"}
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}
