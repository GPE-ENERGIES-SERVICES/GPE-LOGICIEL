"use client";

import type { ChangeEvent, FormEvent, ReactNode } from "react";

import { ACTIVITY_LABELS, FOLLOW_UP_LABELS, MARKET_TYPE_LABELS, PROCEDURE_LABELS } from "@/lib/format";
import { REGIONS } from "@/lib/regions";

/** URL parameters set by the filters, passed as is to GET /api/tenders. */
export type FilterKey =
  | "activity"
  | "marketType"
  | "region"
  | "department"
  | "city"
  | "maxDistanceKm"
  | "procedure"
  | "status"
  | "maxDaysLeft"
  | "publishedWithinDays"
  | "minAmount"
  | "maxAmount"
  | "hasLots"
  | "year"
  | "result"
  | "buyer";

/** Filters of the open consultations. */
export const OPEN_FILTERS: FilterKey[] = [
  "activity",
  "marketType",
  "region",
  "department",
  "city",
  "maxDistanceKm",
  "procedure",
  "status",
  "maxDaysLeft",
  "publishedWithinDays",
  "minAmount",
  "maxAmount",
  "hasLots",
];

/** Filters of the past consultations. */
export const PAST_FILTERS: FilterKey[] = [
  "year",
  "result",
  "activity",
  "marketType",
  "region",
  "department",
  "city",
  "buyer",
  "minAmount",
  "maxAmount",
];

type Props = {
  params: URLSearchParams;
  onApply: (changes: Record<string, string | null>) => void;
  fields: FilterKey[];
};

type SelectProps = {
  name: string;
  label: string;
  value: string;
  emptyLabel: string;
  options: Record<string, string>;
};

/**
 * A choice among the options. A value set elsewhere, such as several
 * statuses from a dashboard link, stays selected under its own label.
 */
function Select({ name, label, value, emptyLabel, options }: SelectProps) {
  const known = value === "" || value in options;
  return (
    <label>
      {label}
      <select name={name} defaultValue={value}>
        <option value="">{emptyLabel}</option>
        {!known && (
          <option value={value}>
            {value
              .split(",")
              .map((item) => options[item] ?? item)
              .join(", ")}
          </option>
        )}
        {Object.entries(options).map(([key, text]) => (
          <option key={key} value={key}>
            {text}
          </option>
        ))}
      </select>
    </label>
  );
}

type InputProps = { name: string; label: string; value: string; placeholder?: string; amount?: boolean };

/** A typed value; an amount is a whole number of euros. */
function Input({ name, label, value, placeholder, amount }: InputProps) {
  const amountProps = amount ? { type: "number", min: 0, step: 1000 } : {};
  return (
    <label>
      {label}
      <input name={name} defaultValue={value} placeholder={placeholder} {...amountProps} />
    </label>
  );
}

const REGION_OPTIONS = Object.fromEntries(Object.keys(REGIONS).map((region) => [region, region]));
const DISTANCE_OPTIONS = { "25": "Moins de 25 km", "50": "Moins de 50 km", "100": "Moins de 100 km", "200": "Moins de 200 km" };
const DEADLINE_OPTIONS = { "3": "Dans moins de 3 jours", "7": "Dans moins de 7 jours", "30": "Dans moins de 30 jours" };
const PUBLICATION_OPTIONS = { "1": "Aujourd'hui", "7": "7 derniers jours", "30": "30 derniers jours" };
const LOT_OPTIONS = { true: "Allotis", false: "Sans lots" };
const RESULT_OPTIONS = {
  AWARDED: "Attribuées",
  UNSUCCESSFUL: "Sans suite",
  CANCELLED: "Annulées",
  UNKNOWN: "Résultat non connu",
};
const CURRENT_YEAR = new Date().getFullYear();
const YEAR_OPTIONS = Object.fromEntries(
  [0, 1, 2, 3].map((back) => [String(CURRENT_YEAR - back), String(CURRENT_YEAR - back)]),
);

function field(key: FilterKey, value: string): ReactNode {
  switch (key) {
    case "activity":
      return <Select name={key} label="Type de travaux" value={value} emptyLabel="Tous" options={ACTIVITY_LABELS} />;
    case "marketType":
      return <Select name={key} label="Type de marché" value={value} emptyLabel="Tous" options={MARKET_TYPE_LABELS} />;
    case "region":
      return <Select name={key} label="Région" value={value} emptyLabel="Toutes" options={REGION_OPTIONS} />;
    case "department":
      return <Input name={key} label="Départements" value={value} placeholder="ex. 13, 83" />;
    case "city":
      return <Input name={key} label="Ville ou code postal" value={value} />;
    case "maxDistanceKm":
      return <Select name={key} label="Distance du siège" value={value} emptyLabel="Toutes" options={DISTANCE_OPTIONS} />;
    case "procedure":
      return <Select name={key} label="Procédure" value={value} emptyLabel="Toutes" options={PROCEDURE_LABELS} />;
    case "status":
      return <Select name={key} label="Statut" value={value} emptyLabel="Tous" options={FOLLOW_UP_LABELS} />;
    case "maxDaysLeft":
      return <Select name={key} label="Date limite" value={value} emptyLabel="Toutes" options={DEADLINE_OPTIONS} />;
    case "publishedWithinDays":
      return <Select name={key} label="Publication" value={value} emptyLabel="Toutes" options={PUBLICATION_OPTIONS} />;
    case "minAmount":
      return <Input name={key} label="Montant min. (€ HT)" value={value} amount />;
    case "maxAmount":
      return <Input name={key} label="Montant max. (€ HT)" value={value} amount />;
    case "hasLots":
      return <Select name={key} label="Lots" value={value} emptyLabel="Indifférent" options={LOT_OPTIONS} />;
    case "year":
      return <Select name={key} label="Année de publication" value={value} emptyLabel="Toutes" options={YEAR_OPTIONS} />;
    case "result":
      return <Select name={key} label="Résultat" value={value} emptyLabel="Tous" options={RESULT_OPTIONS} />;
    case "buyer":
      return <Input name={key} label="Acheteur" value={value} placeholder="ex. Département" />;
  }
}

/** Combinable filters of a list of tenders, all visible; a list applies as soon as it changes. */
export function TenderFilters({ params, onApply, fields }: Props) {
  const value = (key: string) => params.get(key) ?? "";
  const activeCount = fields.filter((key) => params.get(key)).length;

  const apply = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const changes: Record<string, string | null> = { page: null };
    for (const key of fields) {
      changes[key] = String(form.get(key) ?? "").trim() || null;
    }
    onApply(changes);
  };

  const applyList = (event: ChangeEvent<HTMLFormElement>) => {
    if (event.target instanceof HTMLSelectElement) {
      event.currentTarget.requestSubmit();
    }
  };

  const clear = () => onApply(Object.fromEntries([...fields, "page"].map((key) => [key, null])));

  return (
    <form className="card filter-bar" onSubmit={apply} onChange={applyList} key={params.toString()}>
      <div className="filter-fields">
        {fields.map((key) => (
          <div key={key}>{field(key, value(key))}</div>
        ))}
      </div>
      <div className="filter-actions">
        <span className="muted">
          {activeCount === 0 ? "Aucun filtre" : `${activeCount} filtre${activeCount > 1 ? "s" : ""} actif${activeCount > 1 ? "s" : ""}`}
        </span>
        <button type="submit">Appliquer</button>
        {activeCount > 0 && (
          <button type="button" className="link-button" onClick={clear}>
            Effacer les filtres
          </button>
        )}
        {fields.includes("minAmount") && (
          <span className="hint">Un montant non indiqué ne masque pas une consultation.</span>
        )}
      </div>
    </form>
  );
}
