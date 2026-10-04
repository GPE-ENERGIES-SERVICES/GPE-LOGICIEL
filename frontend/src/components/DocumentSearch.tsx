"use client";

import { useState, type FormEvent, type ReactNode } from "react";

import { normalizeText, PIECE_TYPE_LABELS } from "@/lib/format";
import { searchDocuments } from "@/services/documentApi";
import { DEMO_DATA } from "@/services/http";
import type { DocumentHit } from "@/types/document";

/** The searched words in the excerpt, marked whatever their case and accents. */
function highlight(excerpt: string, text: string): ReactNode[] {
  const words = normalizeText(text)
    .split(/\s+/)
    .filter((word) => word.length > 2);
  return excerpt.split(/(\s+)/).map((part, index) => {
    const bare = normalizeText(part).replace(/[^a-z0-9]/g, "");
    return words.some((word) => bare.startsWith(word)) ? <mark key={index}>{part}</mark> : part;
  });
}

/**
 * Search in the text read from the documents of the latest consultation
 * file: each result gives the document and the page, opened at that page.
 */
export function DocumentSearch({ tenderId }: { tenderId: number }) {
  const [text, setText] = useState("");
  const [hits, setHits] = useState<DocumentHit[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = String(new FormData(event.currentTarget).get("q") ?? "").trim();
    if (!value) {
      return;
    }
    setSearching(true);
    setError(null);
    try {
      setHits(await searchDocuments(tenderId, value));
      setText(value);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Recherche impossible.");
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="document-search">
      <form className="filters" role="search" onSubmit={search}>
        <input name="q" type="search" aria-label="Rechercher dans les documents" placeholder="Rechercher dans les documents, ex. visite obligatoire" />
        <button type="submit" disabled={searching}>
          {searching ? "Recherche…" : "Rechercher"}
        </button>
      </form>
      <p className="hint">
        La recherche porte sur le texte lu dans les documents (PDF et Word) ; les tableurs et les plans scannés ne
        sont pas couverts.
      </p>
      {error && <p className="error">{error}</p>}
      {hits && hits.length === 0 && <p className="muted">Aucune page ne contient « {text} ».</p>}
      {hits && hits.length > 0 && (
        <>
          <p className="muted">
            {hits.length} page{hits.length > 1 ? "s" : ""} pour « {text} »
          </p>
          <ul className="search-hits">
            {hits.map((hit) => {
              const where = `${hit.fileName} — page ${hit.pageNumber}`;
              return (
                <li key={`${hit.documentId}-${hit.pageNumber}`}>
                  {hit.readable && !DEMO_DATA ? (
                    <a href={`/api/documents/${hit.documentId}/pdf#page=${hit.pageNumber}`} target="_blank" rel="noopener noreferrer">
                      {where}
                    </a>
                  ) : (
                    <strong>{where}</strong>
                  )}
                  <span className="muted"> · {PIECE_TYPE_LABELS[hit.pieceType]}</span>
                  <p className="quote">« {highlight(hit.excerpt, text)} »</p>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}
