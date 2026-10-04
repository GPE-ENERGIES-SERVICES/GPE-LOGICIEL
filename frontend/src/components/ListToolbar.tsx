"use client";

import type { ReactNode } from "react";

const PAGE_SIZES = [20, 50, 100];

type Props = {
  /** Number of results and what it refers to. */
  summary: ReactNode;
  /** Values of the sort parameter, the first one being the default. */
  sortOptions: { value: string; label: string }[];
  params: URLSearchParams;
  onNavigate: (changes: Record<string, string | null>) => void;
};

/** Above a list: the number of results, the sort and the number of lines per page. */
export function ListToolbar({ summary, sortOptions, params, onNavigate }: Props) {
  return (
    <div className="list-toolbar">
      <p className="muted result-count">{summary}</p>
      <label className="inline">
        Trier par
        <select value={params.get("sort") ?? ""} onChange={(e) => onNavigate({ sort: e.target.value, page: null })}>
          {sortOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label className="inline">
        Par page
        <select
          value={params.get("size") ?? "20"}
          onChange={(e) => onNavigate({ size: e.target.value === "20" ? null : e.target.value, page: null })}
        >
          {PAGE_SIZES.map((size) => (
            <option key={size}>{size}</option>
          ))}
        </select>
      </label>
    </div>
  );
}
