"use client";

import { useState, type FormEvent } from "react";

import { useAuth } from "@/components/AuthProvider";
import { DomainFilter } from "@/components/DomainFilter";
import { formatMoney } from "@/lib/estimate";
import { ACTIVITY_LABELS, formatDate, normalizeText } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import {
  deletePriceLibraryImport,
  getPriceLibraryImports,
  importPriceSchedule,
  priceLibraryFileUrl,
  searchPriceLibrary,
  type PriceLibraryItem,
} from "@/services/priceLibraryApi";

/**
 * The prices GPE already gave: the price schedules it filled and sent, searched
 * line by line. Read by every user, filled by an administrator.
 */
export default function PriceLibraryPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  const imports = useLoader(() => getPriceLibraryImports(), "price-library-imports");
  const [results, setResults] = useState<PriceLibraryItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [domain, setDomain] = useState("");
  const [listDomain, setListDomain] = useState("");
  const [listFilter, setListFilter] = useState("");
  const listWords = normalizeText(listFilter).split(/\s+/).filter(Boolean);
  const listed = (imports.data ?? []).filter(
    (entry) =>
      (!listDomain || entry.domain === listDomain) &&
      listWords.every((word) => normalizeText(`${entry.label} ${entry.fileName}`).includes(word)),
  );

  const search = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setError(null);
    try {
      setResults(await searchPriceLibrary(String(form.get("q") ?? ""), String(form.get("unit") ?? ""), domain));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Recherche impossible.");
    }
  };

  const add = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const target = event.currentTarget;
    const form = new FormData(target);
    const file = form.get("file");
    if (!(file instanceof File) || file.size === 0) {
      setError("Choisissez le DPGF rempli à ajouter.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const year = Number(form.get("year"));
      await importPriceSchedule(file, String(form.get("label")).trim(), year || null, String(form.get("domain")) || null);
      target.reset();
      imports.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import impossible.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: number, label: string) => {
    if (!window.confirm(`Retirer « ${label} » de la bibliothèque ? Ses prix ne seront plus proposés.`)) {
      return;
    }
    try {
      await deletePriceLibraryImport(id);
      imports.reload();
      setResults(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Suppression impossible.");
    }
  };

  return (
    <div>
      <h1>Bibliothèque de prix</h1>
      {(error || imports.error) && <p className="banner error">{error ?? imports.error}</p>}

      <section className="card">
        <h2>Rechercher un prix</h2>
        <form className="inline-form" onSubmit={search}>
          <label>
            Ouvrage
            <input name="q" required minLength={2} placeholder="ex. PC encastrée, câble 3x1,5, TGBT" />
          </label>
          <label>
            Unité
            <input name="unit" maxLength={30} size={6} placeholder="U, ml…" />
          </label>
          <DomainFilter value={domain} onChange={setDomain} />
          <button type="submit" className="primary">
            Rechercher
          </button>
        </form>
        {results && results.length === 0 && <p className="muted">Aucun ouvrage trouvé.</p>}
        {results && results.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Ouvrage</th>
                <th>U</th>
                <th className="number">P.U. HT</th>
                <th>Affaire</th>
              </tr>
            </thead>
            <tbody>
              {results.map((item) => (
                <tr key={item.id}>
                  <td>
                    {item.designation}
                    {item.chapter && <div className="small muted">{item.chapter}</div>}
                  </td>
                  <td>{item.unit ?? "–"}</td>
                  <td className="number nowrap">{formatMoney(item.unitPrice)}</td>
                  <td>
                    {item.label}
                    {item.priceYear && <span className="muted"> · {item.priceYear}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>DPGF de la bibliothèque</h2>
        {imports.data && imports.data.length > 0 && (
          <div className="inline-form">
            <DomainFilter value={listDomain} onChange={setListDomain} />
            <input
              value={listFilter}
              onChange={(event) => setListFilter(event.target.value)}
              placeholder="Affaire, fichier…"
              aria-label="Filtrer les DPGF"
            />
            <span className="muted">
              {listed.length} DPGF · {listed.reduce((sum, entry) => sum + entry.itemCount, 0).toLocaleString("fr-FR")} lignes de prix
            </span>
          </div>
        )}
        {imports.data && listed.length === 0 && (
          <p className="muted">
            {imports.data.length === 0 ? "Aucun DPGF pour l'instant." : "Aucun DPGF ne correspond à ce filtre."}
          </p>
        )}
        {listed.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Affaire</th>
                <th>Domaine</th>
                <th className="number" title="Ouvrages avec un prix unitaire lus dans ce DPGF, réutilisables dans les chiffrages">
                  Lignes de prix
                </th>
                <th>Ajouté</th>
                {admin && <th />}
              </tr>
            </thead>
            <tbody>
              {listed.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    {entry.label}
                    {entry.priceYear && <span className="muted"> · {entry.priceYear}</span>}
                    <div className="small">
                      {entry.hasFile ? (
                        <a href={priceLibraryFileUrl(entry.id)} title="Ouvrir le DPGF tel qu'il a été envoyé">
                          {entry.fileName}
                        </a>
                      ) : (
                        <span className="muted" title="Ajouté avant que les fichiers ne soient gardés : réimportez-le pour pouvoir l'ouvrir">
                          {entry.fileName}
                        </span>
                      )}
                    </div>
                  </td>
                  <td>{entry.domain ? ACTIVITY_LABELS[entry.domain] : "–"}</td>
                  <td className="number">{entry.itemCount}</td>
                  <td className="nowrap">{formatDate(entry.importedAt)}</td>
                  {admin && (
                    <td className="row-actions">
                      <button type="button" className="secondary danger" onClick={() => remove(entry.id, entry.label)}>
                        Retirer
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {admin && (
          <form className="inline-form" onSubmit={add}>
            <label>
              DPGF rempli
              <input type="file" name="file" accept=".xlsx,.xls,.xlsm,.ods,.csv" required />
            </label>
            <label>
              Affaire
              <input name="label" required maxLength={200} placeholder="ex. Chambre funéraire Istres" />
            </label>
            <label>
              Année des prix
              <input name="year" type="number" min={1990} max={2100} size={6} />
            </label>
            <label>
              Domaine
              <select name="domain" defaultValue="">
                <option value="">–</option>
                {Object.entries(ACTIVITY_LABELS).map(([code, label]) => (
                  <option key={code} value={code}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Import…" : "Ajouter"}
            </button>
          </form>
        )}
      </section>
    </div>
  );
}
