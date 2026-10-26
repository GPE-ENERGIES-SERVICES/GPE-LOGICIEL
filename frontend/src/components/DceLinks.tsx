"use client";

import { useState } from "react";

import { consultationUrl } from "@/lib/dceUrl";
import { formatDate, formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getDceLinkEvents, getDceLinks } from "@/services/documentApi";
import type { DceLink, DceLinkStatus, EligibilityState } from "@/types/document";

export const DCE_STATUS_LABELS: Record<DceLinkStatus, string> = {
  AVAILABLE: "Lien seulement : téléchargement manuel",
  NOT_ELIGIBLE: "Non récupéré : aucune veille active ne correspond",
  ELIGIBLE_PENDING: "En attente de récupération",
  IN_PROGRESS: "Récupération en cours",
  FETCHED: "Récupéré",
  TEMPORARY_ERROR: "Erreur temporaire : nouvelle tentative prévue",
  PERMANENT_ERROR: "Échec de la récupération",
  LINK_UNAVAILABLE: "Lien indisponible",
  ACCESS_REQUIRED: "Accès requis : action manuelle",
  AUTOMATED_ACCESS_NOT_AUTHORIZED: "Accès automatisé non autorisé : téléchargement manuel",
  STORAGE_LIMIT_REACHED: "Quota de stockage atteint",
};

const ELIGIBILITY_LABELS: Record<EligibilityState, string> = {
  ELIGIBLE: "Éligible",
  NOT_ELIGIBLE: "Non éligible",
  INDETERMINATE: "Indéterminée",
};

const ALERTING: DceLinkStatus[] = [
  "PERMANENT_ERROR",
  "ACCESS_REQUIRED",
  "LINK_UNAVAILABLE",
  "STORAGE_LIMIT_REACHED",
  "TEMPORARY_ERROR",
];

function History({ linkId }: { linkId: number }) {
  const { data, error } = useLoader(() => getDceLinkEvents(linkId), `dce-events-${linkId}`);
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }
  if (data.length === 0) {
    return <p className="muted">Aucun changement d&apos;état enregistré.</p>;
  }
  return (
    <ul className="history">
      {data.map((event) => (
        <li key={event.id}>
          {formatDateTime(event.occurredAt)} : {DCE_STATUS_LABELS[event.toStatus]}
          {event.detail && <span className="muted"> — {event.detail}</span>}
        </li>
      ))}
    </ul>
  );
}

function Retrieval({ link }: { link: DceLink }) {
  if (link.status === "FETCHED") {
    return (
      <p className="hint">
        {link.fileCount ?? 0} fichier(s){link.fetchedAt ? `, récupéré(s) le ${formatDateTime(link.fetchedAt)}` : ""} :
        voir les dossiers ci-dessous.
      </p>
    );
  }
  return (
    <>
      {link.checkedAt && (
        <p className="hint">
          Dernière tentative le {formatDateTime(link.checkedAt)} ({link.attempts} tentative
          {link.attempts > 1 ? "s" : ""})
          {link.nextAttemptAt && link.status === "TEMPORARY_ERROR"
            ? `, prochaine après le ${formatDateTime(link.nextAttemptAt)}`
            : ""}
        </p>
      )}
      {link.actionRequired && (
        <p className="hint">
          Action manuelle : ouvrez la plateforme, téléchargez le DCE dans le respect de ses conditions, puis déposez-le
          dans « Documents » ci-dessous.
        </p>
      )}
    </>
  );
}

/**
 * Where the consultation file can be obtained, as the notices of each source
 * give it, with the eligibility of the tender (an active saved search matches
 * it) and where its retrieval stands. These are links to the platforms of the
 * buyers, never files: an address is not a file available. Nothing is
 * retrieved automatically from a platform that has not explicitly allowed it.
 */
export function DceLinks({ tenderId }: { tenderId: number }) {
  const { data, error } = useLoader(() => getDceLinks(tenderId), `dce-links-${tenderId}`);
  const [historyOf, setHistoryOf] = useState<number | null>(null);
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return null;
  }
  if (data.length === 0) {
    return <p className="muted">Aucune adresse de plateforme indiquée dans les avis.</p>;
  }
  const first = data[0];
  return (
    <>
      <p>
        <strong>Éligibilité du DCE :</strong>{" "}
        {first.eligibility ? ELIGIBILITY_LABELS[first.eligibility] : "Pas encore évaluée"}
        {first.eligibilityReason && <span className="muted"> — {first.eligibilityReason}</span>}
        {first.yourAlerts.length > 0 && (
          <span className="muted"> (vos veilles : {first.yourAlerts.map((name) => `« ${name} »`).join(", ")})</span>
        )}
      </p>
      <table>
        <thead>
          <tr>
            <th>Plateforme</th>
            <th>Indiquée par</th>
            <th>État du DCE</th>
            <th>Vue le</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {data.map((link) => (
            <tr key={link.id}>
              <td>
                {link.platform ?? link.url}
                <p className="hint">
                  {link.platformAllowed ? "Récupération automatique autorisée" : "Accès automatisé non autorisé"}
                </p>
              </td>
              <td>Avis {link.source}</td>
              <td>
                <span className={ALERTING.includes(link.status) ? "badge urgent" : "badge"}>
                  {DCE_STATUS_LABELS[link.status]}
                </span>
                {link.statusDetail && <p className="hint">{link.statusDetail}</p>}
                <Retrieval link={link} />
                {historyOf === link.id && <History linkId={link.id} />}
              </td>
              <td>{formatDate(link.lastSeenAt)}</td>
              <td className="actions">
                <a href={consultationUrl(link.url)} target="_blank" rel="noopener noreferrer">
                  Ouvrir la plateforme
                </a>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => setHistoryOf(historyOf === link.id ? null : link.id)}
                >
                  {historyOf === link.id ? "Masquer l'historique" : "Historique"}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
