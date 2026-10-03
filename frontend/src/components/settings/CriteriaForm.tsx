"use client";

import { useState, type FormEvent } from "react";

import { api } from "@/lib/api";
import { formatDateTime, MARKET_TYPE_LABELS } from "@/lib/format";
import type { FilterCriteria, MarketType } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

/** One value per line or separated by commas. */
function toList(text: string): string[] {
  return text
    .split(/[\n,;]/)
    .map((value) => value.trim())
    .filter(Boolean);
}

function toNumber(text: string): number | null {
  return text.trim() === "" ? null : Number(text);
}

function CriteriaFields({ criteria, onSaved }: { criteria: FilterCriteria; onSaved: () => void }) {
  const [departments, setDepartments] = useState(criteria.departments.join(", "));
  const [marketTypes, setMarketTypes] = useState<MarketType[]>(criteria.marketTypes);
  const [minDays, setMinDays] = useState(criteria.minDaysBeforeDeadline?.toString() ?? "");
  const [cpvPrefixes, setCpvPrefixes] = useState(criteria.cpvPrefixes.join(", "));
  const [keywords, setKeywords] = useState(criteria.keywords.join("\n"));
  const [excludedKeywords, setExcludedKeywords] = useState(criteria.excludedKeywords.join("\n"));
  const [minAmount, setMinAmount] = useState(criteria.minAmount?.toString() ?? "");
  const [maxAmount, setMaxAmount] = useState(criteria.maxAmount?.toString() ?? "");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ error: boolean; text: string } | null>(null);

  const toggleMarketType = (type: MarketType) =>
    setMarketTypes((current) => (current.includes(type) ? current.filter((t) => t !== type) : [...current, type]));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      await api<FilterCriteria>("/api/filter-criteria", {
        method: "PUT",
        body: {
          departments: toList(departments),
          marketTypes,
          minDaysBeforeDeadline: toNumber(minDays),
          cpvPrefixes: toList(cpvPrefixes),
          keywords: toList(keywords),
          excludedKeywords: toList(excludedKeywords),
          minAmount: toNumber(minAmount),
          maxAmount: toNumber(maxAmount),
        },
      });
      setMessage({ error: false, text: "Critères enregistrés. Tous les appels d'offres ont été filtrés à nouveau." });
      onSaved();
    } catch (err) {
      setMessage({ error: true, text: err instanceof Error ? err.message : "Enregistrement impossible." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="criteria" onSubmit={submit}>
      <p className="hint">
        Un appel d&apos;offres est retenu s&apos;il respecte tous les critères obligatoires et au moins un critère
        métier (code CPV ou mot-clé). Une information absente de l&apos;avis ne l&apos;écarte jamais. Un champ vide
        signifie que le critère n&apos;est pas utilisé.
      </p>

      <fieldset>
        <legend>Critères obligatoires</legend>
        <label>
          Départements
          <input
            value={departments}
            onChange={(e) => setDepartments(e.target.value)}
            placeholder="69, 01, 38, 2A"
          />
        </label>
        <div className="field">
          <span>Types de marché</span>
          <div className="checkboxes">
            {(Object.keys(MARKET_TYPE_LABELS) as MarketType[]).map((type) => (
              <label key={type} className="inline">
                <input
                  type="checkbox"
                  checked={marketTypes.includes(type)}
                  onChange={() => toggleMarketType(type)}
                />
                {MARKET_TYPE_LABELS[type]}
              </label>
            ))}
          </div>
        </div>
        <label>
          Délai minimum avant la date limite (jours)
          <input type="number" min={0} max={365} value={minDays} onChange={(e) => setMinDays(e.target.value)} />
        </label>
        <div className="row">
          <label>
            Montant minimum (€ HT)
            <input type="number" min={0} value={minAmount} onChange={(e) => setMinAmount(e.target.value)} />
          </label>
          <label>
            Montant maximum (€ HT)
            <input type="number" min={0} value={maxAmount} onChange={(e) => setMaxAmount(e.target.value)} />
          </label>
        </div>
        <label>
          Mots-clés exclus (un par ligne)
          <textarea rows={3} value={excludedKeywords} onChange={(e) => setExcludedKeywords(e.target.value)} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Critères métier (au moins un doit correspondre)</legend>
        <label>
          Débuts de codes CPV
          <input
            value={cpvPrefixes}
            onChange={(e) => setCpvPrefixes(e.target.value)}
            placeholder="4531, 45261215, 09331"
          />
        </label>
        <label>
          Mots-clés recherchés dans le titre, la description et les lots (un par ligne)
          <textarea rows={5} value={keywords} onChange={(e) => setKeywords(e.target.value)} />
        </label>
      </fieldset>

      {message && <p className={message.error ? "error" : "success"}>{message.text}</p>}
      <div className="form-actions">
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Enregistrement…" : "Enregistrer les critères"}
        </button>
        {criteria.updatedAt && (
          <span className="muted">
            Dernière modification le {formatDateTime(criteria.updatedAt)}
            {criteria.updatedBy ? ` par ${criteria.updatedBy}` : ""}
          </span>
        )}
      </div>
    </form>
  );
}

export function CriteriaForm() {
  const { data, error, reload } = useLoader(() => api<FilterCriteria>("/api/filter-criteria"), "criteria");
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }
  return <CriteriaFields key={data.updatedAt} criteria={data} onSaved={reload} />;
}
