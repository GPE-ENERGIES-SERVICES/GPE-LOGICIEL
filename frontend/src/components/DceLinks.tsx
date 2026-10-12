"use client";

import { formatDate } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getDceLinks } from "@/services/documentApi";
import type { DceLinkStatus } from "@/types/document";

const STATUS_LABELS: Record<DceLinkStatus, string> = {
  AVAILABLE: "Lien seulement : téléchargement manuel",
  TO_FETCH: "Récupération prévue",
  FETCHED: "Récupéré",
  FAILED: "Échec de la récupération",
  ACCESS_REQUIRED: "Accès requis : action manuelle",
};

/**
 * Where the consultation file can be obtained, as the notices of each source
 * give it. These are links to the buyer's platforms, never files: the files
 * obtained are the dossiers below.
 */
export function DceLinks({ tenderId }: { tenderId: number }) {
  const { data, error } = useLoader(() => getDceLinks(tenderId), `dce-links-${tenderId}`);
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return null;
  }
  if (data.length === 0) {
    return <p className="muted">Aucune adresse de plateforme indiquée dans les avis.</p>;
  }
  return (
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
            <td>{link.platform ?? link.url}</td>
            <td>Avis {link.source}</td>
            <td>
              <span className={link.status === "FAILED" || link.status === "ACCESS_REQUIRED" ? "badge urgent" : "badge"}>
                {STATUS_LABELS[link.status]}
              </span>
              {link.statusDetail && <p className="hint">{link.statusDetail}</p>}
            </td>
            <td>{formatDate(link.lastSeenAt)}</td>
            <td className="actions">
              <a href={link.url} target="_blank" rel="noopener noreferrer">
                Ouvrir la plateforme
              </a>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
