"use client";

import type { FormEvent } from "react";

import { ACTIVITY_LABELS, FOLLOW_UP_LABELS, PROCEDURE_LABELS } from "@/lib/format";
import { REGIONS } from "@/lib/regions";

/** URL parameters set by the panel, passed as is to GET /api/tenders. Lists are comma separated. */
export const FILTER_KEYS = [
  "region",
  "department",
  "city",
  "maxDistanceKm",
  "activity",
  "publishedWithinDays",
  "deadlineFrom",
  "deadlineTo",
  "maxDaysLeft",
  "minDaysLeft",
  "minAmount",
  "maxAmount",
  "amountKnown",
  "procedure",
  "hasLots",
  "minLots",
  "status",
] as const;

type Props = {
  params: URLSearchParams;
  onApply: (changes: Record<string, string | null>) => void;
};

/** Combinable filters of the tender list, folded while none is active. */
export function TenderFilters({ params, onApply }: Props) {
  const activeCount = FILTER_KEYS.filter((key) => params.get(key)).length;
  const value = (key: string) => params.get(key) ?? "";
  const checked = (key: string, item: string) => value(key).split(",").includes(item);

  const apply = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const changes: Record<string, string | null> = { page: null };
    for (const key of FILTER_KEYS) {
      const values = form.getAll(key).map((item) => String(item).trim());
      changes[key] = values.filter(Boolean).join(",") || null;
    }
    onApply(changes);
  };

  const clear = () => onApply(Object.fromEntries([...FILTER_KEYS, "page"].map((key) => [key, null])));

  const checkboxes = (key: string, labels: Record<string, string>) =>
    Object.entries(labels).map(([code, label]) => (
      <label key={code} className="inline">
        <input type="checkbox" name={key} value={code} defaultChecked={checked(key, code)} />
        {label}
      </label>
    ));

  return (
    <details className="card filter-panel" open={activeCount > 0}>
      <summary>
        Filtres
        {activeCount > 0 && ` (${activeCount} actif${activeCount > 1 ? "s" : ""})`}
      </summary>
      <form onSubmit={apply} key={params.toString()}>
        <div className="filter-groups">
          <fieldset>
            <legend>Localisation</legend>
            <label>
              Région
              <select name="region" defaultValue={value("region")}>
                <option value="">Toutes</option>
                {Object.keys(REGIONS).map((region) => (
                  <option key={region}>{region}</option>
                ))}
              </select>
            </label>
            <label>
              Départements
              <input name="department" placeholder="ex. 13, 83" defaultValue={value("department")} />
            </label>
            <label>
              Ville
              <input name="city" defaultValue={value("city")} />
            </label>
            <label>
              Distance du siège
              <select name="maxDistanceKm" defaultValue={value("maxDistanceKm")}>
                <option value="">Toutes</option>
                {[25, 50, 100, 200].map((km) => (
                  <option key={km} value={km}>
                    Moins de {km} km
                  </option>
                ))}
              </select>
            </label>
          </fieldset>

          <fieldset>
            <legend>Type de travaux</legend>
            {checkboxes("activity", ACTIVITY_LABELS)}
          </fieldset>

          <fieldset>
            <legend>Dates</legend>
            <label>
              Publiés depuis
              <select name="publishedWithinDays" defaultValue={value("publishedWithinDays")}>
                <option value="">Toujours</option>
                <option value="1">24 heures</option>
                <option value="7">7 jours</option>
                <option value="30">30 jours</option>
              </select>
            </label>
            <label>
              Date limite à partir du
              <input type="date" name="deadlineFrom" defaultValue={value("deadlineFrom")} />
            </label>
            <label>
              Date limite jusqu&apos;au
              <input type="date" name="deadlineTo" defaultValue={value("deadlineTo")} />
            </label>
            <label>
              Échéance proche
              <select name="maxDaysLeft" defaultValue={value("maxDaysLeft")}>
                <option value="">Indifférent</option>
                {[3, 7, 15, 30].map((days) => (
                  <option key={days} value={days}>
                    Dans moins de {days} jours
                  </option>
                ))}
              </select>
            </label>
            <label>
              Délai minimum pour répondre (jours)
              <input type="number" name="minDaysLeft" min={0} defaultValue={value("minDaysLeft")} />
            </label>
          </fieldset>

          <fieldset>
            <legend>Montant estimé (€ HT)</legend>
            <label>
              Minimum
              <input type="number" name="minAmount" min={0} step={1000} defaultValue={value("minAmount")} />
            </label>
            <label>
              Maximum
              <input type="number" name="maxAmount" min={0} step={1000} defaultValue={value("maxAmount")} />
            </label>
            <label>
              Montant
              <select name="amountKnown" defaultValue={value("amountKnown")}>
                <option value="">Indiqué ou non</option>
                <option value="true">Indiqué</option>
                <option value="false">Non indiqué</option>
              </select>
            </label>
            <p className="hint">Un montant non indiqué ne masque pas l&apos;appel d&apos;offres, sauf avec « Indiqué ».</p>
          </fieldset>

          <fieldset>
            <legend>Procédure</legend>
            {checkboxes("procedure", PROCEDURE_LABELS)}
          </fieldset>

          <fieldset>
            <legend>Lots</legend>
            <label>
              Allotissement
              <select name="hasLots" defaultValue={value("hasLots")}>
                <option value="">Indifférent</option>
                <option value="true">Avec lots</option>
                <option value="false">Sans lots</option>
              </select>
            </label>
            <label>
              Nombre minimum de lots
              <input type="number" name="minLots" min={1} defaultValue={value("minLots")} />
            </label>
          </fieldset>

          <fieldset>
            <legend>Statut</legend>
            {checkboxes("status", FOLLOW_UP_LABELS)}
          </fieldset>
        </div>

        <div className="form-actions">
          <button type="submit" className="primary">
            Appliquer
          </button>
          {activeCount > 0 && (
            <button type="button" className="link-button" onClick={clear}>
              Effacer les filtres
            </button>
          )}
        </div>
      </form>
    </details>
  );
}
