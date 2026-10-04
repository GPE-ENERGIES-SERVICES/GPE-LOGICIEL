"use client";

import type { FormEvent, ReactNode } from "react";

type Props = {
  value: string;
  placeholder: string;
  /** The new text, or null to clear the search. */
  onSearch: (text: string | null) => void;
  /** Further actions next to the button. */
  children?: ReactNode;
};

export function SearchForm({ value, placeholder, onSearch, children }: Props) {
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSearch(String(new FormData(event.currentTarget).get("q") ?? "").trim() || null);
  };

  return (
    <form className="filters" role="search" onSubmit={submit} key={value}>
      <input name="q" type="search" aria-label="Rechercher" placeholder={placeholder} defaultValue={value} />
      <button type="submit" className="primary">
        Rechercher
      </button>
      {value && (
        <button type="button" className="link-button" onClick={() => onSearch(null)}>
          Effacer la recherche
        </button>
      )}
      {children}
    </form>
  );
}
