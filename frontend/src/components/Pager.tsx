"use client";

/** Lines shown per page in the libraries and the prices. */
export const PAGE_SIZE = 50;

/** The pages of a long list, the same everywhere; nothing while it fits on one page. */
export function Pager({
  page,
  total,
  size = PAGE_SIZE,
  busy = false,
  label,
  onChange,
}: {
  /** From 0. */
  page: number;
  total: number;
  size?: number;
  busy?: boolean;
  label: string;
  onChange: (page: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) {
    return null;
  }
  // The list got shorter (a line removed): the last page, as pageOf shows it.
  const current = Math.min(page, pages - 1);
  return (
    <div className="inline-form" role="navigation" aria-label={label}>
      <button type="button" disabled={current === 0 || busy} onClick={() => onChange(current - 1)}>
        ← Précédent
      </button>
      <span className="muted">
        Page {current + 1} sur {pages}
      </span>
      <button type="button" disabled={current + 1 >= pages || busy} onClick={() => onChange(current + 1)}>
        Suivant →
      </button>
    </div>
  );
}

/** The lines of the page asked, back to the last page when the list got shorter. */
export function pageOf<T>(rows: T[], page: number, size = PAGE_SIZE): T[] {
  const last = Math.max(0, Math.ceil(rows.length / size) - 1);
  const start = Math.min(page, last) * size;
  return rows.slice(start, start + size);
}
