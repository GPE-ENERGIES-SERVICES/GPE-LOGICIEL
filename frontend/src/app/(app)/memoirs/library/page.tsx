"use client";

import { useState, type FormEvent } from "react";

import { DomainFilter } from "@/components/DomainFilter";
import { Pager, pageOf } from "@/components/Pager";
import { ACTIVITY_LABELS, formatDate, normalizeText } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { deleteFormerMemoir, formerMemoirUrl, getLibrary, importFormerMemoir, updateFormerMemoir } from "@/services/memoirApi";

/**
 * The former memoirs of GPE: references for the style, the structure and
 * the methods, chosen one by one for the AI; never copied as they are. Laid
 * out as the price library: the list, filtered by domain, then the import.
 */
export default function MemoirLibraryPage() {
  const library = useLoader(() => getLibrary(), "memoir-library");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [domain, setDomain] = useState("");
  const [filter, setFilter] = useState("");
  const [page, setPage] = useState(0);
  const words = normalizeText(filter).split(/\s+/).filter(Boolean);
  const listed = (library.data ?? []).filter(
    (entry) =>
      (!domain || entry.activityCode === domain) &&
      words.every((word) => normalizeText(`${entry.title} ${entry.context ?? ""} ${entry.fileName}`).includes(word)),
  );
  const forAi = listed.filter((entry) => entry.useForAi && entry.textChars > 0).length;

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
      {(error || library.error) && <p className="banner error">{error ?? library.error}</p>}

      <section className="card">
        <h2>Mémoires de la bibliothèque</h2>
        <p className="muted">
          L&apos;IA s&apos;en inspire pour le style, le vocabulaire et la façon de présenter GPE ; elle ne reprend jamais un ancien
          chantier ou une ancienne information sans qu&apos;ils figurent dans les données actuelles de l&apos;entreprise.
        </p>
        {library.data && library.data.length > 0 && (
          <div className="inline-form">
            <DomainFilter
              value={domain}
              onChange={(value) => {
                setDomain(value);
                setPage(0);
              }}
            />
            <input
              value={filter}
              onChange={(event) => {
                setFilter(event.target.value);
                setPage(0);
              }}
              placeholder="Affaire, acheteur, fichier…"
              aria-label="Filtrer les mémoires"
            />
            <span className="muted">
              {listed.length} mémoire{listed.length > 1 ? "s" : ""} · {forAi} utilisé{forAi > 1 ? "s" : ""} par l&apos;IA
            </span>
          </div>
        )}
        {library.data && listed.length === 0 && (
          <p className="muted">
            {library.data.length === 0 ? "Aucun ancien mémoire pour l'instant." : "Aucun mémoire ne correspond à ce filtre."}
          </p>
        )}
        {listed.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Affaire</th>
                <th>Domaine</th>
                <th>Texte lu</th>
                <th>Référence pour l&apos;IA</th>
                <th>Ajouté</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pageOf(listed, page).map((entry) => (
                <tr key={entry.id}>
                  <td>
                    {entry.title}
                    {entry.writtenOn && <span className="muted"> · {formatDate(entry.writtenOn)}</span>}
                    {entry.context && <div className="small muted">{entry.context}</div>}
                    <div className="small">
                      <a href={formerMemoirUrl(entry.id)} title="Ouvrir le mémoire tel qu'il a été envoyé">
                        {entry.fileName}
                      </a>
                    </div>
                  </td>
                  <td>{entry.activityCode ? ACTIVITY_LABELS[entry.activityCode] : "–"}</td>
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
                  <td className="nowrap">{formatDate(entry.uploadedAt)}</td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="secondary danger"
                      onClick={async () => {
                        if (window.confirm(`Retirer « ${entry.title} » de la bibliothèque ? L'IA ne s'en servira plus.`)) {
                          try {
                            await deleteFormerMemoir(entry.id);
                            library.reload();
                          } catch (err) {
                            setError(err instanceof Error ? err.message : "Suppression impossible.");
                          }
                        }
                      }}
                    >
                      Retirer
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pager page={page} total={listed.length} label="Pages des mémoires" onChange={setPage} />

        <h3>Ajouter un mémoire</h3>
        <form onSubmit={submit}>
          <div className="form-grid">
            <label className="wide">
              Mémoire envoyé (PDF ou Word) *
              <input type="file" name="file" accept=".pdf,.docx,.doc" required />
            </label>
            <label className="wide">
              Affaire *
              <input name="title" required maxLength={300} placeholder="ex. Chambre funéraire Istres" />
            </label>
            <label>
              Date
              <input type="date" name="writtenOn" />
            </label>
            <label>
              Domaine
              <select name="activityCode" defaultValue="">
                <option value="">–</option>
                {Object.entries(ACTIVITY_LABELS).map(([code, label]) => (
                  <option key={code} value={code}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Type de marché
              <input name="marketType" maxLength={100} placeholder="Travaux, maintenance…" />
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
              {busy ? "Import…" : "Ajouter"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
