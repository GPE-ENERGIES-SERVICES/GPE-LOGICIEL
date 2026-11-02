"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { formatDateTime, lotTitle } from "@/lib/format";
import { MEMOIR_STATUS_LABELS } from "@/lib/memoir";
import { useLoader } from "@/lib/useLoader";
import { createMemoir, getMemoirs } from "@/services/memoirApi";
import { getTender, getTenders } from "@/services/tenderApi";
import type { TenderDetail, TenderSummary } from "@/types/tender";

/** The technical memoirs, the last changed first, and the creation of a new one, linked or not to a tender. */
export default function MemoirsPage() {
  const router = useRouter();
  const memoirs = useLoader(() => getMemoirs(), "memoirs");
  const [query, setQuery] = useState("");
  const [found, setFound] = useState<TenderSummary[] | null>(null);
  const [tender, setTender] = useState<TenderDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = async () => {
    setError(null);
    try {
      const params = new URLSearchParams({ q: query, tab: "ALL", size: "8" });
      setFound((await getTenders(params.toString())).content);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Recherche impossible.");
    }
  };

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const memoir = await createMemoir({
        title: String(form.get("title") ?? "").trim() || null,
        tenderId: tender?.id ?? null,
        lotNumber: String(form.get("lot") ?? "") || null,
      });
      router.push(`/memoirs/${memoir.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Mémoires techniques</h1>

      <section className="card">
        <h2>Nouveau mémoire</h2>
        <form onSubmit={create}>
          <div className="form-grid">
            <label className="wide">
              Appel d&apos;offres
              {tender ? (
                <span className="memoir-tender-chosen">
                  {tender.title}{" "}
                  <button type="button" className="secondary" onClick={() => setTender(null)}>
                    Changer
                  </button>
                </span>
              ) : (
                <span className="inline-form memoir-tender-search">
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Rechercher par mots de l'objet, acheteur, référence…"
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        search();
                      }
                    }}
                  />
                  <button type="button" onClick={search} disabled={query.trim().length < 2}>
                    Chercher
                  </button>
                </span>
              )}
            </label>
            {tender && tender.lots.length > 0 && (
              <label>
                Lot
                <select name="lot" defaultValue="">
                  <option value="">Tous lots</option>
                  {tender.lots.map((lot) => (
                    <option key={lot.number} value={lot.number}>
                      Lot {lot.number}
                      {lot.title ? ` – ${lotTitle(lot.number, lot.title)}` : ""}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="wide">
              Titre
              <input name="title" maxLength={300} placeholder={tender ? `Mémoire technique – ${tender.title}` : "Titre du mémoire"} />
            </label>
          </div>
          {!tender && found && (
            <ul className="plain-list memoir-tender-results">
              {found.length === 0 && <li className="muted">Aucun appel d&apos;offres trouvé.</li>}
              {found.map((candidate) => (
                <li key={candidate.id}>
                  <button
                    type="button"
                    className="link-button"
                    onClick={async () => {
                      setTender(await getTender(candidate.id));
                      setFound(null);
                    }}
                  >
                    {candidate.title}
                  </button>
                  <span className="muted"> · {candidate.buyer ?? "Acheteur non indiqué"}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="form-actions">
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Création…" : "Créer le mémoire"}
            </button>
            <span className="hint">
              Associé à un appel d&apos;offres, le mémoire peut s&apos;appuyer sur son DCE pour proposer le sommaire.
            </span>
          </div>
        </form>
        {error && <p className="banner error">{error}</p>}
      </section>

      <section className="card">
        {memoirs.error && <p className="banner error">{memoirs.error}</p>}
        {memoirs.data && memoirs.data.length === 0 && <p className="muted">Aucun mémoire pour l&apos;instant.</p>}
        {memoirs.data && memoirs.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Mémoire</th>
                <th>Appel d&apos;offres</th>
                <th>État</th>
                <th>Avancement</th>
                <th>Modifié</th>
              </tr>
            </thead>
            <tbody>
              {memoirs.data.map((memoir) => (
                <tr key={memoir.id}>
                  <td>
                    <Link href={`/memoirs/${memoir.id}`}>{memoir.title}</Link>
                  </td>
                  <td>
                    {memoir.tenderId ? (
                      <Link href={`/tenders/${memoir.tenderId}`}>
                        {memoir.tenderTitle}
                        {memoir.lotNumber && ` · lot ${memoir.lotNumber}`}
                      </Link>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                  <td>
                    <span className={memoir.status === "DONE" ? "badge verified" : "badge"}>{MEMOIR_STATUS_LABELS[memoir.status]}</span>
                  </td>
                  <td>
                    {memoir.sections === 0 ? (
                      <span className="muted">Sommaire vide</span>
                    ) : (
                      `${memoir.written} / ${memoir.sections} sections rédigées`
                    )}
                  </td>
                  <td>
                    {formatDateTime(memoir.updatedAt)}
                    {memoir.updatedBy && <span className="muted"> · {memoir.updatedBy}</span>}
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
