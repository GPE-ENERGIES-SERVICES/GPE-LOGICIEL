"use client";

type Props = {
  /** Current page, from 0. */
  page: number;
  totalPages: number;
  onNavigate: (changes: Record<string, string | null>) => void;
};

export function Pagination({ page, totalPages, onNavigate }: Props) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination">
      <button type="button" disabled={page <= 0} onClick={() => onNavigate({ page: String(page - 1) })}>
        Précédent
      </button>
      <span>
        Page {page + 1} sur {totalPages}
      </span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onNavigate({ page: String(page + 1) })}>
        Suivant
      </button>
    </nav>
  );
}
