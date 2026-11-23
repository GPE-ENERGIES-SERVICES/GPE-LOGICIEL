"use client";

import { useState } from "react";

/** Lines shown per page in the libraries and the prices. */
export const PAGE_SIZE = 50;

/** The choices of lines per page of a list loaded whole. */
const PAGE_SIZES = [10, 20, 50];

/** The pages to offer around the current one: the first, the last, the neighbours, null for a gap. */
export function pageNumbers(current: number, pages: number): (number | null)[] {
  const shown = new Set([0, pages - 1, current - 1, current, current + 1].filter((n) => n >= 0 && n < pages));
  const sorted = [...shown].sort((a, b) => a - b);
  const result: (number | null)[] = [];
  sorted.forEach((n, i) => {
    if (i > 0 && n - sorted[i - 1] > 1) {
      result.push(n - sorted[i - 1] === 2 ? n - 1 : null);
    }
    result.push(n);
  });
  return result;
}

/** Previous, the numbered pages, next: the same in every list. */
export function PageNav({
  page,
  pages,
  busy = false,
  label,
  onChange,
}: {
  /** From 0. */
  page: number;
  pages: number;
  busy?: boolean;
  label: string;
  onChange: (page: number) => void;
}) {
  return (
    <nav className="pagination" aria-label={label}>
      <button type="button" disabled={page === 0 || busy} onClick={() => onChange(page - 1)}>
        ← Précédent
      </button>
      {pageNumbers(page, pages).map((n, i) =>
        n === null ? (
          <span key={`gap${i}`} className="page-gap" aria-hidden="true">
            …
          </span>
        ) : (
          <button
            key={n}
            type="button"
            disabled={busy}
            aria-current={n === page ? "page" : undefined}
            aria-label={`Page ${n + 1}`}
            onClick={() => n !== page && onChange(n)}
          >
            {n + 1}
          </button>
        ),
      )}
      <button type="button" disabled={page + 1 >= pages || busy} onClick={() => onChange(page + 1)}>
        Suivant →
      </button>
    </nav>
  );
}

/** The pages of a long list, the same everywhere; nothing while it fits on one page. */
export function Pager({
  page,
  total,
  size = PAGE_SIZE,
  busy = false,
  label,
  onChange,
  onSizeChange,
}: {
  /** From 0. */
  page: number;
  total: number;
  size?: number;
  busy?: boolean;
  label: string;
  onChange: (page: number) => void;
  /** Offers 10, 20 or 50 lines per page. */
  onSizeChange?: (size: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1 && !(onSizeChange && total > PAGE_SIZES[0])) {
    return null;
  }
  // The list got shorter (a line removed): the last page, as pageOf shows it.
  const current = Math.min(page, pages - 1);
  return (
    <div className="pager">
      <span>
        {current * size + 1}–{Math.min(total, (current + 1) * size)} sur {total}
      </span>
      {pages > 1 && <PageNav page={current} pages={pages} busy={busy} label={label} onChange={onChange} />}
      {onSizeChange && (
        <label>
          Par page
          <select value={size} onChange={(e) => onSizeChange(Number(e.target.value))}>
            {PAGE_SIZES.map((choice) => (
              <option key={choice}>{choice}</option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}

/** The lines of the page asked, back to the last page when the list got shorter. */
export function pageOf<T>(rows: T[], page: number, size = PAGE_SIZE): T[] {
  const last = Math.max(0, Math.ceil(rows.length / size) - 1);
  const start = Math.min(page, last) * size;
  return rows.slice(start, start + size);
}

/**
 * A list loaded whole, shown a page at a time: the lines of the page and its
 * pager. Back to the first page when `resetKey` (the search, the filters) changes.
 */
export function usePaged<T>(rows: T[], label: string, resetKey = "", initialSize = 20) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(initialSize);
  const [key, setKey] = useState(resetKey);
  if (key !== resetKey) {
    setKey(resetKey);
    setPage(0);
  }
  return {
    rows: pageOf(rows, page, size),
    pager: (
      <Pager
        page={page}
        total={rows.length}
        size={size}
        label={label}
        onChange={setPage}
        onSizeChange={(next) => {
          setSize(next);
          setPage(0);
        }}
      />
    ),
  };
}
