"use client";

import { useState, type FormEvent } from "react";

import { ACTIVITY_LABELS, formatDate } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { deleteFormerMemoir, formerMemoirUrl, getLibrary, importFormerMemoir, updateFormerMemoir } from "@/services/memoirApi";

/**
 * The former memoirs of GPE: references for the style, the structure and
 * the methods, chosen one by one for the AI; never copied as they are.
 */
export default function MemoirLibraryPage() {
  const library = useLoader(() => getLibrary(), "memoir-library");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const element = event.currentTarget;
    const form = new FormData(element);
    const file = form.get("file");
    if (!(file instanceof File) || file.size === 0) {
      setError("Choisissez le fichier du mémoire.");
      return;
    }
    for (const key of ["writtenOn", "context", "marketType", "activityCode"]) {
      if (!String(form.get(key) ?? "").trim()) {
        form.delete(key);
      }
    }
    form.set("useForAi", form.get("useForAi") === "on" ? "true" : "false");
    setBusy(true);
    setError(null);
    try {
      await importFormerMemoir(form);
      element.reset();
      library.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import impossible.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Bibliothèque des mémoires</h1>
      <section className="card">
        <h2>Importer un ancien mémoire</h2>
        <p className="muted">
          L&apos;IA s&apos;en inspire pour le style, le vocabulaire et la façon de présenter GPE ; elle ne reprend jamais un ancien
          chantier ou une ancienne information sans qu&apos;ils figurent dans les données actuelles de l&apos;entreprise.
        </p>
        <form onSubmit={submit}>
          <div className="form-grid">
            <label className="wide">
              Fichier (PDF ou Word) *
              <input type="file" name="file" accept=".pdf,.docx,.doc" required />
            </label>
            <label className="wide">
              Titre *
              <input name="title" required maxLength={300} placeholder="Éclairage public – Ville de Marignane" />
            </label>
            <label>
              Date
              <input type="date" name="writtenOn" />
            </label>
            <label>
              Type de marché
              <input name="marketType" maxLength={100} placeholder="Travaux, maintenance…" />
            </label>
            <label>
              Type de travaux
              <select name="activityCode" defaultValue="">
                <option value="">—</option>
                {Object.entries(ACTIVITY_LABELS).map(([code, label]) => (
                  <option key={code} value={code}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label className="wide">
              Contexte
              <input name="context" maxLength={4000} placeholder="Acheteur, nature du marché, résultat…" />
            </label>
          </div>
          <label>
            <input type="checkbox" name="useForAi" defaultChecked /> Utilisable par l&apos;IA comme référence
          </label>
          <div className="form-actions">
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Import…" : "Importer"}
            </button>
          </div>
        </form>
        {error && <p className="banner error">{error}</p>}
      </section>

      <section className="card">
        {library.error && <p className="banner error">{library.error}</p>}
        {library.data && library.data.length === 0 && <p className="muted">Aucun ancien mémoire importé.</p>}
        {library.data && library.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Mémoire</th>
                <th>Date</th>
                <th>Type de travaux</th>
                <th>Texte lu</th>
                <th>Référence pour l&apos;IA</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {library.data.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    <a href={formerMemoirUrl(entry.id)}>{entry.title}</a>
                    {entry.context && <div className="muted">{entry.context}</div>}
                  </td>
                  <td>{entry.writtenOn ? formatDate(entry.writtenOn) : ""}</td>
                  <td>{entry.activityCode ? ACTIVITY_LABELS[entry.activityCode] : ""}</td>
                  <td>
                    {entry.textChars > 0 ? (
                      `${entry.textChars.toLocaleString("fr-FR")} caractères`
                    ) : (
                      <span className="badge urgent">Aucun texte (scan ?)</span>
                    )}
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      aria-label={`Utiliser « ${entry.title} » comme référence pour l'IA`}
                      checked={entry.useForAi}
                      onChange={async (event) => {
                        try {
                          await updateFormerMemoir(entry.id, { ...entry, useForAi: event.target.checked });
                          library.reload();
                        } catch (err) {
                          setError(err instanceof Error ? err.message : "Changement impossible.");
                        }
                      }}
                    />
                  </td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="secondary danger"
                      onClick={async () => {
                        if (window.confirm(`Supprimer « ${entry.title} » de la bibliothèque ?`)) {
                          try {
                            await deleteFormerMemoir(entry.id);
                            library.reload();
                          } catch (err) {
                            setError(err instanceof Error ? err.message : "Suppression impossible.");
                          }
                        }
                      }}
                    >
                      Supprimer
                    </button>
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
