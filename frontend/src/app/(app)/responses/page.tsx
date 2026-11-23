"use client";

import Link from "next/link";

import { usePaged } from "@/components/Pager";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getResponses } from "@/services/responseApi";

/** The answers in progress: each one is prepared on the page of its tender. */
export default function ResponsesPage() {
  const responses = useLoader(() => getResponses(), "responses");
  const paged = usePaged(responses.data ?? [], "Pages des dossiers");

  return (
    <div>
      <h1>Dossiers de réponse</h1>
      <p className="muted">
        Se prépare depuis la fiche de l&apos;appel d&apos;offres, onglet « Dossier de réponse ».
      </p>
      <section className="card">
        {responses.error && <p className="banner error">{responses.error}</p>}
        {responses.data && responses.data.length === 0 && <p className="muted">Aucun dossier en préparation.</p>}
        {responses.data && responses.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Appel d&apos;offres</th>
                <th>Date limite</th>
                <th>Pièces vérifiées</th>
              </tr>
            </thead>
            <tbody>
              {paged.rows.map((response) => (
                <tr key={response.tenderId}>
                  <td>
                    <Link href={`/tenders/${response.tenderId}?from=/responses#reponse`}>{response.title}</Link>
                    {response.buyer && <span className="muted"> · {response.buyer}</span>}
                  </td>
                  <td>{response.deadline ? formatDateTime(response.deadline) : <span className="muted">Non indiquée</span>}</td>
                  <td>
                    <span className={response.total > 0 && response.ready === response.total ? "badge verified" : "badge warning"}>
                      {response.ready} / {response.total}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {paged.pager}
      </section>
    </div>
  );
}
