"use client";

import { PageNav } from "./Pager";

type Props = {
  /** Current page, from 0. */
  page: number;
  totalPages: number;
  onNavigate: (changes: Record<string, string | null>) => void;
};

/** The pages of a list paged by the server, kept in the address. */
export function Pagination({ page, totalPages, onNavigate }: Props) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <PageNav
      page={Math.min(page, totalPages - 1)}
      pages={totalPages}
      label="Pages des résultats"
      onChange={(next) => onNavigate({ page: next === 0 ? null : String(next) })}
    />
  );
}
