"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { createDceReview, getDceReviews } from "@/services/documentApi";

/** The consultation files analysed without any tender, and the creation of a new analysis under a free name. */
export default function DceReviewsPage() {
  const router = useRouter();
  const reviews = useLoader(() => getDceReviews(), "dce-reviews");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    setBusy(true);
    setError(null);
    try {
      const review = await createDceReview(name);
      router.push(`/dce-reviews/${review.id}#dce`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Analyser un DCE</h1>
      <p className="muted">
        Pour un DCE reçu hors de la veille : il suit la même lecture et la même analyse par IA qu&apos;un appel d&apos;offres,
        sans y être rattaché.
      </p>

      <section className="card">
        <h2>Nouvelle analyse</h2>
        <form onSubmit={create}>
          <div className="form-grid">
            <label className="wide">
              Pour quel appel d&apos;offres ?
              <input name="name" required maxLength={300} placeholder="Ex. : Réfection de la toiture – Mairie de Lyon" />
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Création…" : "Créer et déposer le DCE"}
            </button>
          </div>
        </form>
        {error && <p className="banner error">{error}</p>}
      </section>

      <section className="card">
        {reviews.error && <p className="banner error">{reviews.error}</p>}
        {reviews.data && reviews.data.length === 0 && <p className="muted">Aucune analyse pour l&apos;instant.</p>}
        {reviews.data && reviews.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Nom</th>
                <th>Créée</th>
              </tr>
            </thead>
            <tbody>
              {reviews.data.map((review) => (
                <tr key={review.id}>
                  <td>
                    <Link href={`/dce-reviews/${review.id}`}>{review.name}</Link>
                  </td>
                  <td>
                    {formatDateTime(review.createdAt)}
                    {review.createdBy && <span className="muted"> · {review.createdBy}</span>}
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
