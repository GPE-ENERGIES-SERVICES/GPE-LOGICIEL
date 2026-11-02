"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { formatDateTime, lotTitle } from "@/lib/format";
import { MEMOIR_STATUS_LABELS } from "@/lib/memoir";
import { useLoader } from "@/lib/useLoader";
import { createMemoir, getMemoirs } from "@/services/memoirApi";
import type { TenderDetail } from "@/types/tender";

/** The technical memoirs of a tender, and the creation of a new one for it. */
export function MemoirsSection({ tender }: { tender: TenderDetail }) {
  const router = useRouter();
  // shortcut: all memoirs read and filtered here; a filter by tender in the API when they are many.
  const memoirs = useLoader(() => getMemoirs(), `memoirs-${tender.id}`);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mine = (memoirs.data ?? []).filter((memoir) => memoir.tenderId === tender.id);

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setCreating(true);
    setError(null);
    try {
      const memoir = await createMemoir({
        title: null,
        tenderId: tender.id,
        lotNumber: String(form.get("lot") ?? "") || null,
      });
      router.push(`/memoirs/${memoir.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
      setCreating(false);
    }
  };

  return (
    <>
      {memoirs.error && <p className="banner error">{memoirs.error}</p>}
      {memoirs.data && mine.length === 0 && <p className="muted">Aucun mémoire technique pour cet appel d&apos;offres.</p>}
      {mine.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Mémoire</th>
              <th>État</th>
              <th>Avancement</th>
              <th>Modifié</th>
            </tr>
          </thead>
          <tbody>
            {mine.map((memoir) => (
              <tr key={memoir.id}>
                <td>
                  <Link href={`/memoirs/${memoir.id}`}>{memoir.title}</Link>
                  {memoir.lotNumber && <span className="muted"> · lot {memoir.lotNumber}</span>}
                </td>
                <td>
                  <span className={memoir.status === "DONE" ? "badge verified" : "badge"}>{MEMOIR_STATUS_LABELS[memoir.status]}</span>
                </td>
                <td>{memoir.sections === 0 ? <span className="muted">Sommaire vide</span> : `${memoir.written} / ${memoir.sections} sections`}</td>
                <td>
                  {formatDateTime(memoir.updatedAt)}
                  {memoir.updatedBy && <span className="muted"> · {memoir.updatedBy}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="inline-form estimate-create" onSubmit={create}>
        {tender.lots.length > 0 && (
          <label>
            Lot{" "}
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
        <button type="submit" className="primary" disabled={creating}>
          {creating ? "Création…" : "Nouveau mémoire technique"}
        </button>
        {error && <span className="error">{error}</span>}
      </form>
      <p className="hint">Le mémoire s&apos;appuie sur le DCE de cet appel d&apos;offres pour relever les exigences et proposer le sommaire.</p>
    </>
  );
}
