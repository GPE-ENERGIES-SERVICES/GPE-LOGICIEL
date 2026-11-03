"use client";

import Link from "next/link";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getResponses } from "@/services/responseApi";

/** The answers in progress: each one is prepared on the page of its tender. */
export default function ResponsesPage() {
  const responses = useLoader(() => getResponses(), "responses");

  return (
    <div>
      <h1>Dossiers de réponse</h1>
      <p className="muted">
        Un dossier se prépare sur la page de son appel d&apos;offres, onglet « Dossier de réponse », une fois la fiche IA, le
        chiffrage et le mémoire avancés.
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
              {responses.data.map((response) => (
                <tr key={response.tenderId}>
                  <td>
                    <Link href={`/tenders/${response.tenderId}?from=/responses#reponse`}>{response.title}</Link>
                    {response.buyer && <span className="muted"> · {response.buyer}</span>}
                  </td>
                  <td>{response.deadline ? formatDateTime(response.deadline) : <span className="muted">Non indiquée</span>}</td>
                  <td>
                    <span className={response.total > 0 && response.ready === response.total ? "badge verified" : "badge"}>
                      {response.ready} / {response.total}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
