"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { createManualTender } from "@/services/tenderApi";

/** Adds a tender absent from the sources followed: it then goes the way of any other. */
export default function NewTenderPage() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const deadline = String(form.get("deadline") ?? "");
    setBusy(true);
    setError(null);
    try {
      const tender = await createManualTender({
        title: String(form.get("title") ?? "").trim(),
        buyer: String(form.get("buyer") ?? "").trim() || null,
        deadline: deadline ? new Date(deadline).toISOString() : null,
      });
      router.push(`/tenders/${tender.id}#dce`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Ajouter un appel d&apos;offres</h1>
      <p className="muted">
        Pour un appel d&apos;offres absent de la veille : vous déposez son DCE, puis analyse, chiffrage et mémoire se font
        comme pour les autres.
      </p>
      <section className="card">
        <form onSubmit={create}>
          <div className="form-grid">
            <label className="wide">
              Nom de l&apos;appel d&apos;offres
              <input name="title" required maxLength={500} placeholder="Ex. : Réfection de la toiture de la mairie" />
            </label>
            <label>
              Acheteur (facultatif)
              <input name="buyer" maxLength={500} />
            </label>
            <label>
              Date limite (facultatif)
              <input name="deadline" type="datetime-local" />
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
    </div>
  );
}
