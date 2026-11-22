"use client";

import { useState } from "react";

import { EquipmentSection } from "@/components/EquipmentSection";
import { formatDateTime } from "@/lib/format";
import { refreshData, useLoader } from "@/lib/useLoader";
import {
  getTechnicalChoices,
  saveTechnicalChoices,
  validateTechnicalChoices,
  VALORISATION_LABELS,
  type TechnicalChoices,
  type TechnicalView,
} from "@/services/technicalApi";

const decimal = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 3 });

/** A number as typed in French (« 162,84 »), empty when nothing usable. */
function number(text: string): number | null {
  const value = Number(text.trim().replace(/\s/g, "").replace(",", "."));
  return text.trim() === "" || !Number.isFinite(value) ? null : value;
}

function shown(value: number | null): string {
  return value === null ? "" : String(value).replace(".", ",");
}

/** The form, started from what is saved; what the file or the equipment says is applied only on a click. */
function ChoicesForm({
  tenderId,
  view,
}: {
  tenderId: number;
  view: TechnicalView;
}) {
  const initial = view.choices;
  const [valorisation, setValorisation] = useState(initial.valorisation ?? "");
  const [roof, setRoof] = useState(initial.roof ?? "");
  const [requestedKwc, setRequestedKwc] = useState(shown(initial.requestedKwc));
  const [moduleCount, setModuleCount] = useState(shown(initial.moduleCount));
  const [moduleWc, setModuleWc] = useState(shown(initial.moduleWc));
  const [inverterCount, setInverterCount] = useState(
    shown(initial.inverterCount),
  );
  const [inverterKva, setInverterKva] = useState(shown(initial.inverterKva));
  const [constraints, setConstraints] = useState(initial.constraints ?? "");
  const [openPoints, setOpenPoints] = useState(initial.openPoints ?? "");
  const [notes, setNotes] = useState(initial.notes ?? "");
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const edit = (set: (value: string) => void) => (value: string) => {
    set(value);
    setDirty(true);
  };

  const choices = (): TechnicalChoices => ({
    valorisation: valorisation || null,
    roof: roof.trim() || null,
    requestedKwc: number(requestedKwc),
    moduleCount: number(moduleCount),
    moduleWc: number(moduleWc),
    inverterCount: number(inverterCount),
    inverterKva: number(inverterKva),
    constraints: constraints.trim() || null,
    openPoints: openPoints.trim() || null,
    notes: notes.trim() || null,
  });

  const act = async (action: () => Promise<TechnicalView>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setDirty(false);
      // The page and the memoir see the new choices at once.
      refreshData(`technical-${tenderId}`);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    } finally {
      setBusy(false);
    }
  };

  const sizing = view.sizing;

  return (
    <div>
      <h3>Choix de GPE</h3>
      <div className="form-grid">
        <label>
          Valorisation de l&apos;énergie
          <select
            value={valorisation}
            onChange={(event) => edit(setValorisation)(event.target.value)}
          >
            <option value="">À choisir</option>
            {Object.entries(VALORISATION_LABELS).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Puissance demandée (kWc)
          <input
            inputMode="decimal"
            value={requestedKwc}
            onChange={(event) => edit(setRequestedKwc)(event.target.value)}
          />
        </label>
        <label className="wide">
          Toiture et support
          <textarea
            rows={2}
            value={roof}
            maxLength={2000}
            placeholder="Bac acier, toiture terrasse, charpente vérifiée par note de calcul…"
            onChange={(event) => edit(setRoof)(event.target.value)}
          />
        </label>
      </div>
      {view.powers.length > 0 && (
        <p className="hint">
          {view.powers.length > 1
            ? "Le DCE cite plusieurs puissances : "
            : "Le DCE cite : "}
          {view.powers.map((power) => (
            <button
              key={power.kwc}
              type="button"
              className="link-button"
              title={power.source}
              onClick={() => edit(setRequestedKwc)(shown(power.kwc))}
            >
              utiliser {decimal.format(power.kwc)} kWc
            </button>
          ))}
          {view.powers.length > 1 &&
            " — à faire confirmer par l'acheteur (survolez pour voir la phrase du DCE)."}
        </p>
      )}

      <div className="form-grid">
        <label>
          Nombre de modules
          <input
            inputMode="numeric"
            value={moduleCount}
            onChange={(event) => edit(setModuleCount)(event.target.value)}
          />
        </label>
        <label>
          Puissance d&apos;un module (Wc)
          <input
            inputMode="decimal"
            value={moduleWc}
            onChange={(event) => edit(setModuleWc)(event.target.value)}
          />
        </label>
        <label>
          Nombre d&apos;onduleurs
          <input
            inputMode="numeric"
            value={inverterCount}
            onChange={(event) => edit(setInverterCount)(event.target.value)}
          />
        </label>
        <label>
          Puissance d&apos;un onduleur (kVA)
          <input
            inputMode="decimal"
            value={inverterKva}
            onChange={(event) => edit(setInverterKva)(event.target.value)}
          />
        </label>
      </div>
      {view.moduleWc !== null && number(moduleWc) !== view.moduleWc && (
        <p className="hint">
          Le module retenu fait {view.moduleWc} Wc :{" "}
          <button
            type="button"
            className="link-button"
            onClick={() => edit(setModuleWc)(shown(view.moduleWc))}
          >
            utiliser cette puissance
          </button>
        </p>
      )}

      <div className="form-grid">
        <label className="wide">
          Contraintes du projet
          <textarea
            rows={3}
            value={constraints}
            maxLength={8000}
            placeholder="Site en exploitation, accès toiture, coffret pompiers, ENEDIS, distances de câbles…"
            onChange={(event) => edit(setConstraints)(event.target.value)}
          />
        </label>
        <label className="wide">
          Points ouverts et questions à l&apos;acheteur
          <textarea
            rows={3}
            value={openPoints}
            maxLength={8000}
            placeholder="Puissance à confirmer, passage des câbles à voir à la visite…"
            onChange={(event) => edit(setOpenPoints)(event.target.value)}
          />
        </label>
        <label className="wide">
          Choix et remarques de GPE
          <textarea
            rows={3}
            value={notes}
            maxLength={8000}
            onChange={(event) => edit(setNotes)(event.target.value)}
          />
        </label>
      </div>

      <h3>Dimensionnement</h3>
      {sizing.installedKwc === null && sizing.inverterKva === null ? (
        <p className="muted">
          Renseignez les modules et les onduleurs puis enregistrez : le logiciel
          calcule la puissance et le ratio.
        </p>
      ) : (
        <dl className="facts rows">
          {sizing.installedKwc !== null && (
            <div className="key-fact">
              <dt>Puissance installée</dt>
              <dd>
                {decimal.format(sizing.installedKwc)} kWc
                {sizing.gapPercent !== null && (
                  <span className="muted">
                    {" "}
                    ({sizing.gapPercent > 0 ? "+" : ""}
                    {decimal.format(sizing.gapPercent)} % par rapport à la
                    puissance demandée)
                  </span>
                )}
              </dd>
            </div>
          )}
          {sizing.inverterKva !== null && (
            <div className="key-fact">
              <dt>Puissance des onduleurs</dt>
              <dd>
                {decimal.format(sizing.inverterKva)} kVA
                {sizing.dcAcRatio !== null && (
                  <span className="muted">
                    {" "}
                    · ratio DC/AC {decimal.format(sizing.dcAcRatio)}
                  </span>
                )}
              </dd>
            </div>
          )}
        </dl>
      )}
      {sizing.warnings.map((warning) => (
        <p key={warning} className="banner error">
          {warning}
        </p>
      ))}
      {dirty && (
        <p className="hint">
          Modifications non enregistrées : le dimensionnement sera recalculé à
          l&apos;enregistrement.
        </p>
      )}

      {error && <p className="banner error">{error}</p>}
      <div className="form-actions">
        <button
          type="button"
          className="primary"
          disabled={busy || !dirty}
          onClick={() => act(() => saveTechnicalChoices(tenderId, choices()))}
        >
          {busy ? "Enregistrement…" : "Enregistrer les choix"}
        </button>
        {view.saved && !view.validatedAt && (
          <button
            type="button"
            disabled={busy || dirty}
            title={dirty ? "Enregistrez d'abord vos modifications" : undefined}
            onClick={() => act(() => validateTechnicalChoices(tenderId))}
          >
            Valider les choix techniques
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * The technical choices of GPE for a tender, between its analysis and its
 * estimate: the equipment retained, how the energy is used, the roof, the
 * power and its make-up, the constraints and the questions left. With or
 * without a consultation file, everything is written and changed by hand.
 */
export function TechnicalSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(
    () => getTechnicalChoices(tenderId),
    `technical-${tenderId}`,
  );
  const view = loaded.data;

  if (!view) {
    return loaded.error ? (
      <p className="banner error">{loaded.error}</p>
    ) : (
      <p className="muted">Chargement…</p>
    );
  }

  return (
    <div>
      <p>
        {view.validatedAt ? (
          <span className="badge verified">
            Validés par {view.validatedBy} le {formatDateTime(view.validatedAt)}
          </span>
        ) : (
          <span className="badge">
            {view.saved ? "À valider" : "À renseigner"}
          </span>
        )}
        {view.updatedAt && (
          <span className="muted">
            {" "}
            Dernière modification : {view.updatedBy}, le{" "}
            {formatDateTime(view.updatedAt)}.
          </span>
        )}
      </p>
      {!view.hasFile && (
        <p className="banner info">
          Pas de DCE déposé : renseignez les choix à la main. Ils se modifient à
          tout moment ; une modification après la validation la retire.
        </p>
      )}
      <EquipmentSection tenderId={tenderId} />
      {/* Started again from what is saved after each save or validation. */}
      <ChoicesForm
        key={`${view.updatedAt}#${view.validatedAt}`}
        tenderId={tenderId}
        view={view}
      />
    </div>
  );
}
