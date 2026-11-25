"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";

import { formatMoney, formatNumber, parseNumber } from "@/lib/estimate";
import { useLoader } from "@/lib/useLoader";
import { getPricingSettings, reviseMargin } from "@/services/estimateApi";
import type { Estimate, MarginTarget } from "@/types/estimate";

const percent = new Intl.NumberFormat("fr-FR", { style: "percent", maximumFractionDigits: 1 });

function share(part: number, whole: number): string {
  return whole > 0 ? percent.format(part / whole) : "—";
}

type Slice = { label: string; value: number; color: string };

/** A ring of the shares of the sale: each slice a circle stroke of its length. */
function Ring({ slices }: { slices: Slice[] }) {
  const total = slices.reduce((sum, slice) => sum + Math.max(slice.value, 0), 0);
  const radius = 15.9155; // a circumference of 100
  let offset = 25; // starts at the top
  return (
    <svg viewBox="0 0 42 42" className="estimate-ring" role="img" aria-label="Répartition du prix de vente">
      <circle cx="21" cy="21" r={radius} fill="none" stroke="var(--surface-3)" strokeWidth="7" />
      {total > 0 &&
        slices.map((slice) => {
          const length = (Math.max(slice.value, 0) / total) * 100;
          const circle = (
            <circle
              key={slice.label}
              cx="21"
              cy="21"
              r={radius}
              fill="none"
              stroke={slice.color}
              strokeWidth="7"
              strokeDasharray={`${length} ${100 - length}`}
              strokeDashoffset={offset}
            >
              <title>
                {slice.label} : {formatMoney(slice.value)} ({share(slice.value, total)})
              </title>
            </circle>
          );
          offset -= length;
          return circle;
        })}
    </svg>
  );
}

/**
 * The figures of an estimate as a costing reader checks them: the sale split
 * into supplies and labour with their margins, the hours, and the margin
 * revised by a coefficient. Every figure comes from the backend.
 */
export function EstimateSummary({
  estimate,
  editable,
  revising,
  onRevising,
  onChanged,
}: {
  estimate: Estimate;
  editable: boolean;
  /** Whether the margin revision is open, from here or from the toolbar. */
  revising: boolean;
  onRevising: (open: boolean) => void;
  onChanged: (estimate: Estimate) => void;
}) {
  const settings = useLoader(() => getPricingSettings(), "pricing-settings");
  const setRevising = onRevising;
  const b = estimate.breakdown;
  const fees = settings.data
    ? settings.data.agencyFeeRate + settings.data.overheadRate + settings.data.serviceFeeRate
    : null;
  const detailedCost = b.supplyCost + b.laborCost;
  const detailedSale = b.supplySale + b.laborSale;
  const grossRate = detailedSale > 0 ? (detailedSale - detailedCost) / detailedSale : null;
  const netRate = grossRate !== null && fees !== null ? grossRate - fees : null;

  const rows = [
    { label: "Matériel", cost: b.supplyCost, sale: b.supplySale },
    { label: "M.O.", cost: b.laborCost, sale: b.laborSale },
  ];
  const slices: Slice[] = [
    { label: "Matériel", value: b.supplyCost, color: "var(--primary)" },
    { label: "Marge matériel", value: b.supplySale - b.supplyCost, color: "var(--success-fill)" },
    { label: "M.O.", value: b.laborCost, color: "#8a94a6" },
    { label: "Marge M.O.", value: b.laborSale - b.laborCost, color: "#5f9e2f" },
  ];

  return (
    <aside className="estimate-summary" aria-label="Récapitulatif du chiffrage">
      <div className="estimate-summary-head">
        <Ring slices={slices} />
        <ul className="estimate-ring-legend">
          {slices.map((slice) => (
            <li key={slice.label}>
              <span className="level-dot" style={{ background: slice.color }} aria-hidden="true" />
              {slice.label}
              <strong>{share(Math.max(slice.value, 0), detailedSale)}</strong>
            </li>
          ))}
        </ul>
      </div>

      <table className="estimate-summary-table">
        <thead>
          <tr>
            <th />
            <th>Achat</th>
            <th>Vente</th>
            <th>Marge</th>
            <th>%</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <th>{row.label}</th>
              <td>{formatMoney(row.cost)}</td>
              <td>{formatMoney(row.sale)}</td>
              <td className="margin">{formatMoney(row.sale - row.cost)}</td>
              <td>{share(row.sale - row.cost, row.sale)}</td>
            </tr>
          ))}
          {b.undetailedSale > 0 && (
            <tr title="Prix saisis directement : leur coût n'est pas détaillé">
              <th>Prix saisis</th>
              <td>—</td>
              <td>{formatMoney(b.undetailedSale)}</td>
              <td>—</td>
              <td>—</td>
            </tr>
          )}
          <tr className="total">
            <th>Total</th>
            <td>{formatMoney(detailedCost)}</td>
            <td>{formatMoney(estimate.totalHt)}</td>
            <td className="margin">{formatMoney(detailedSale - detailedCost)}</td>
            <td>{grossRate === null ? "—" : percent.format(grossRate)}</td>
          </tr>
        </tbody>
      </table>

      <dl className="estimate-summary-facts">
        <div>
          <dt>Nb heures</dt>
          <dd>{formatNumber(b.laborHours)} h</dd>
        </div>
        <div>
          <dt>Coût M.O. HT</dt>
          <dd>{formatMoney(b.laborCost)}</dd>
        </div>
        {netRate !== null && (
          <div>
            <dt>Marge nette</dt>
            <dd>{percent.format(netRate)}</dd>
          </div>
        )}
        <div className="strong">
          <dt>Total HT</dt>
          <dd>{formatMoney(estimate.totalHt)}</dd>
        </div>
        <div className="strong">
          <dt>Total TTC</dt>
          <dd>{formatMoney(estimate.totalTtc)}</dd>
        </div>
      </dl>

      {editable && (
        <button type="button" className="primary" onClick={() => setRevising(true)}>
          Révision marge
        </button>
      )}
      {b.undetailedSale > 0 && (
        <p className="hint">
          La marge ne compte que les lignes détaillées en matériel et M.O. ; les prix saisis directement n&apos;ont pas de
          coût connu.
        </p>
      )}
      {revising && (
        <MarginDialog
          estimate={estimate}
          grossRate={grossRate}
          netRate={netRate}
          onClose={() => setRevising(false)}
          onChanged={(next) => {
            onChanged(next);
            setRevising(false);
          }}
        />
      )}
    </aside>
  );
}

/** The margin wanted, reached by the coefficient of the supplies, of the labour, or of both. */
function MarginDialog({
  estimate,
  grossRate,
  netRate,
  onClose,
  onChanged,
}: {
  estimate: Estimate;
  grossRate: number | null;
  netRate: number | null;
  onClose: () => void;
  onChanged: (estimate: Estimate) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [target, setTarget] = useState<MarginTarget>("SUPPLIES");
  const [net, setNet] = useState(false);
  const [value, setValue] = useState(grossRate === null ? "" : String(Math.round(grossRate * 1000) / 10).replace(".", ","));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const margin = parseNumber(value);
    if (margin === null || Number.isNaN(margin) || margin < 0 || margin >= 100) {
      setError("Saisissez une marge entre 0 et 100 %.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onChanged(await reviseMargin(estimate.id, target, margin / 100, net));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Révision impossible.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <dialog ref={dialog} className="margin-dialog" onClose={onClose} aria-labelledby="margin-title">
      <form onSubmit={submit}>
        <h2 id="margin-title" className="margin-band">
          Révision marge
        </h2>
        <fieldset>
          <legend>Coefficients à ajuster</legend>
          {(
            [
              ["SUPPLIES", "Coef Mat."],
              ["LABOR", "Coef M.O."],
              ["BOTH", "Coef Mat. et M.O."],
            ] as const
          ).map(([option, label]) => (
            <label key={option} className="radio">
              <input type="radio" name="target" checked={target === option} onChange={() => setTarget(option)} /> {label}
            </label>
          ))}
        </fieldset>
        <fieldset>
          <legend>Totaux actuels</legend>
          <p>
            Total HT : <strong>{formatMoney(estimate.totalHt)}</strong>
            <br />
            Marge brute : <strong>{grossRate === null ? "—" : percent.format(grossRate)}</strong>
            <br />
            Marge nette : <strong>{netRate === null ? "—" : percent.format(netRate)}</strong>
          </p>
        </fieldset>
        <fieldset>
          <legend>Nouvelle marge</legend>
          <label className="radio">
            <input type="radio" name="kind" checked={!net} onChange={() => setNet(false)} /> Marge brute
          </label>
          <label className="radio">
            <input type="radio" name="kind" checked={net} onChange={() => setNet(true)} /> Marge nette
          </label>
          <span className="margin-input">
            <input
              value={value}
              onChange={(event) => setValue(event.target.value)}
              inputMode="decimal"
              aria-label="Nouvelle marge en %"
              autoFocus
            />{" "}
            %
          </span>
        </fieldset>
        {grossRate === null && (
          <p className="banner warning">
            Aucune ligne n&apos;a encore son matériel et sa M.O. complets (prix unitaire, heures et taux M.O.) : il n&apos;y a
            pas de marge à réviser.
          </p>
        )}
        <p className="hint">
          Le coefficient choisi devient le même sur toutes les lignes détaillées ; les coefficients propres à une ligne
          pour cette famille sont remplacés.
        </p>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="submit" className="primary" disabled={saving}>
            Valider
          </button>
          <button type="button" onClick={() => dialog.current?.close()}>
            Annuler
          </button>
        </div>
      </form>
    </dialog>
  );
}
