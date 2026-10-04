"use client";

import type { ChangeEvent, FormEvent } from "react";

import { ACTIVITY_LABELS, FOLLOW_UP_LABELS, MARKET_TYPE_LABELS, PROCEDURE_LABELS } from "@/lib/format";
import { REGIONS } from "@/lib/regions";

/** URL parameters set by the filters, passed as is to GET /api/tenders. */
export const FILTER_KEYS = [
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
] as const;

type Props = {
  params: URLSearchParams;
  onApply: (changes: Record<string, string | null>) => void;
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

const REGION_OPTIONS = Object.fromEntries(Object.keys(REGIONS).map((region) => [region, region]));
const DISTANCE_OPTIONS = { "25": "Moins de 25 km", "50": "Moins de 50 km", "100": "Moins de 100 km", "200": "Moins de 200 km" };
const DEADLINE_OPTIONS = { "3": "Dans moins de 3 jours", "7": "Dans moins de 7 jours", "30": "Dans moins de 30 jours" };
const PUBLICATION_OPTIONS = { "1": "Aujourd'hui", "7": "7 derniers jours", "30": "30 derniers jours" };
const LOT_OPTIONS = { true: "Allotis", false: "Sans lots" };

/** Combinable filters of the tender list, all visible; a list applies as soon as it changes. */
export function TenderFilters({ params, onApply }: Props) {
  const value = (key: string) => params.get(key) ?? "";
  const activeCount = FILTER_KEYS.filter((key) => params.get(key)).length;

  const apply = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const changes: Record<string, string | null> = { page: null };
    for (const key of FILTER_KEYS) {
      changes[key] = String(form.get(key) ?? "").trim() || null;
    }
    onApply(changes);
  };

  const applyList = (event: ChangeEvent<HTMLFormElement>) => {
    if (event.target instanceof HTMLSelectElement) {
      event.currentTarget.requestSubmit();
    }
  };

  const clear = () => onApply(Object.fromEntries([...FILTER_KEYS, "page"].map((key) => [key, null])));

  return (
    <form className="card filter-bar" onSubmit={apply} onChange={applyList} key={params.toString()}>
      <div className="filter-fields">
        <Select
          name="activity"
          label="Type de travaux"
          value={value("activity")}
          emptyLabel="Tous"
          options={ACTIVITY_LABELS}
        />
        <Select
          name="marketType"
          label="Type de marché"
          value={value("marketType")}
          emptyLabel="Tous"
          options={MARKET_TYPE_LABELS}
        />
        <Select name="region" label="Région" value={value("region")} emptyLabel="Toutes" options={REGION_OPTIONS} />
        <label>
          Départements
          <input name="department" placeholder="ex. 13, 83" defaultValue={value("department")} />
        </label>
        <label>
          Ville ou code postal
          <input name="city" defaultValue={value("city")} />
        </label>
        <Select
          name="maxDistanceKm"
          label="Distance du siège"
          value={value("maxDistanceKm")}
          emptyLabel="Toutes"
          options={DISTANCE_OPTIONS}
        />
        <Select
          name="procedure"
          label="Procédure"
          value={value("procedure")}
          emptyLabel="Toutes"
          options={PROCEDURE_LABELS}
        />
        <Select name="status" label="Statut" value={value("status")} emptyLabel="Tous" options={FOLLOW_UP_LABELS} />
        <Select
          name="maxDaysLeft"
          label="Date limite"
          value={value("maxDaysLeft")}
          emptyLabel="Toutes"
          options={DEADLINE_OPTIONS}
        />
        <Select
          name="publishedWithinDays"
          label="Publication"
          value={value("publishedWithinDays")}
          emptyLabel="Toutes"
          options={PUBLICATION_OPTIONS}
        />
        <label>
          Montant min. (€ HT)
          <input type="number" name="minAmount" min={0} step={1000} defaultValue={value("minAmount")} />
        </label>
        <label>
          Montant max. (€ HT)
          <input type="number" name="maxAmount" min={0} step={1000} defaultValue={value("maxAmount")} />
        </label>
        <Select name="hasLots" label="Lots" value={value("hasLots")} emptyLabel="Indifférent" options={LOT_OPTIONS} />
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
        <span className="hint">Un montant non indiqué ne masque pas une consultation.</span>
      </div>
    </form>
  );
}
