"use client";

import { useState } from "react";

import { EquipmentSection } from "@/components/EquipmentSection";
import { formatDateTime } from "@/lib/format";
import { refreshData, useLoader } from "@/lib/useLoader";
import {
  addTechnicalLine,
  deleteTechnicalLine,
  detailEquipment,
  getTechnicalChoices,
  prefillTechnicalChoices,
  QUESTION_STATUS_LABELS,
  REQUIREMENT_STATUS_LABELS,
  saveTechnicalChoices,
  updateTechnicalLine,
  validateTechnicalChoices,
  VALORISATION_LABELS,
  type ChosenEquipment,
  type LineInput,
  type LineKind,
  type TechnicalChoices,
  type TechnicalLine,
  type TechnicalSource,
  type TechnicalView,
} from "@/services/technicalApi";

const decimal = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 3 });

/** A number as typed in French (« 162,84 »), empty when nothing usable. */
function number(text: string): number | null {
  const value = Number(text.trim().replace(/\s/g, "").replace(",", "."));
  return text.trim() === "" || !Number.isFinite(value) ? null : value;
}

function shown(value: number | null | undefined): string {
  return value === null || value === undefined
    ? ""
    : String(value).replace(".", ",");
}

/** Where the AI read a value, shown beside it: to check before validating. */
function AiBadge({ source }: { source: TechnicalSource | null | undefined }) {
  if (!source) {
    return null;
  }
  const where = [
    source.fileName?.replace(/^.*\//, ""),
    source.page ? `p. ${source.page}` : null,
  ]
    .filter(Boolean)
    .join(" ");
  return (
    <span className="badge urgent" title={source.quote ?? undefined}>
      IA{where ? ` · ${where}` : ""}
    </span>
  );
}

/** The tab, the memoir and the estimate read the choices again. */
function refresh(tenderId: number) {
  refreshData(`technical-${tenderId}`, `equipment-${tenderId}`);
}

/** The quantity, unit power and note of an equipment chosen, saved when a field is left. */
function EquipmentDetail({
  tenderId,
  item,
  onError,
}: {
  tenderId: number;
  item: ChosenEquipment;
  onError: (message: string) => void;
}) {
  const [quantity, setQuantity] = useState(shown(item.quantity));
  const [power, setPower] = useState(
    item.powerGuessed ? "" : shown(item.unitPower),
  );
  const [note, setNote] = useState(item.note ?? "");
  const unit =
    item.sheet.category === "MODULE"
      ? "Wc"
      : item.sheet.category === "INVERTER"
        ? "kVA"
        : null;

  const save = async () => {
    try {
      await detailEquipment(tenderId, item.sheet.id, {
        quantity: number(quantity),
        unitPower: number(power),
        note: note.trim() || null,
      });
      refresh(tenderId);
    } catch (err) {
      onError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    }
  };

  return (
    <div className="inline-form">
      <input
        inputMode="decimal"
        size={6}
        value={quantity}
        placeholder="Qté"
        aria-label="Quantité"
        onChange={(event) => setQuantity(event.target.value)}
        onBlur={save}
      />
      {unit && (
        <>
          <input
            inputMode="decimal"
            size={6}
            value={power}
            placeholder={
              item.powerGuessed && item.unitPower !== null
                ? shown(item.unitPower)
                : unit
            }
            aria-label={`Puissance unitaire (${unit})`}
            title={
              item.powerGuessed
                ? "Lue dans la référence : saisissez-la pour la confirmer ou la changer"
                : undefined
            }
            onChange={(event) => setPower(event.target.value)}
            onBlur={save}
          />
          <span className="muted">
            {unit}
            {item.powerGuessed && " (lue dans la référence)"}
          </span>
        </>
      )}
      <input
        size={14}
        value={note}
        placeholder="Remarque"
        aria-label="Remarque"
        onChange={(event) => setNote(event.target.value)}
        onBlur={save}
      />
    </div>
  );
}

type ChoiceField = keyof TechnicalChoices;

/** The values of the choices, started from what is saved; what the file says is applied only on a click. */
function ChoicesForm({
  tenderId,
  view,
  pv,
}: {
  tenderId: number;
  view: TechnicalView;
  pv: boolean;
}) {
  const initial = view.choices;
  const [values, setValues] = useState<Record<ChoiceField, string>>({
    valorisation: initial.valorisation ?? "",
    roof: initial.roof ?? "",
    requestedKwc: shown(initial.requestedKwc),
    yieldKwhKwc: shown(initial.yieldKwhKwc),
    productionMwh: shown(initial.productionMwh),
    constraints: initial.constraints ?? "",
    siteAccess: initial.siteAccess ?? "",
    safety: initial.safety ?? "",
    subcontracting: initial.subcontracting ?? "",
    planning: initial.planning ?? "",
    notes: initial.notes ?? "",
  });
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (field: ChoiceField) => (value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setDirty(true);
  };
  const badge = (field: ChoiceField) => (
    <AiBadge source={view.aiSources[field]} />
  );

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await saveTechnicalChoices(tenderId, {
        valorisation: values.valorisation || null,
        roof: values.roof.trim() || null,
        requestedKwc: number(values.requestedKwc),
        yieldKwhKwc: number(values.yieldKwhKwc),
        productionMwh: number(values.productionMwh),
        constraints: values.constraints.trim() || null,
        siteAccess: values.siteAccess.trim() || null,
        safety: values.safety.trim() || null,
        subcontracting: values.subcontracting.trim() || null,
        planning: values.planning.trim() || null,
        notes: values.notes.trim() || null,
      });
      setDirty(false);
      refresh(tenderId);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    } finally {
      setBusy(false);
    }
  };

  const text = (field: ChoiceField, label: string, placeholder?: string) => (
    <label className="wide">
      {label} {badge(field)}
      <textarea
        rows={2}
        maxLength={8000}
        value={values[field]}
        placeholder={placeholder}
        onChange={(event) => set(field)(event.target.value)}
      />
    </label>
  );

  return (
    <div>
      {pv && (
        <>
          <h3>Installation photovoltaïque</h3>
          <div className="form-grid">
            <label>
              Valorisation de l&apos;énergie {badge("valorisation")}
              <select
                value={values.valorisation}
                onChange={(event) => set("valorisation")(event.target.value)}
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
              Puissance demandée (kWc) {badge("requestedKwc")}
              <input
                inputMode="decimal"
                value={values.requestedKwc}
                onChange={(event) => set("requestedKwc")(event.target.value)}
              />
            </label>
            <label>
              Productible (kWh/kWc/an) {badge("yieldKwhKwc")}
              <input
                inputMode="decimal"
                value={values.yieldKwhKwc}
                onChange={(event) => set("yieldKwhKwc")(event.target.value)}
              />
            </label>
            <label>
              Production annoncée (MWh/an) {badge("productionMwh")}
              <input
                inputMode="decimal"
                value={values.productionMwh}
                onChange={(event) => set("productionMwh")(event.target.value)}
              />
            </label>
            {text(
              "roof",
              "Toiture et support",
              "Bac acier, toiture terrasse, ombrière ; pose, avis technique…",
            )}
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
                  onClick={() => set("requestedKwc")(shown(power.kwc))}
                >
                  utiliser {decimal.format(power.kwc)} kWc
                </button>
              ))}
            </p>
          )}
        </>
      )}

      <h3>Chantier</h3>
      <div className="form-grid">
        {text(
          "constraints",
          "Contraintes du projet",
          "Site en exploitation, emplacement imposé, coupures…",
        )}
        {text(
          "siteAccess",
          "Site et accès",
          "Horaires, accès des véhicules, stockage…",
        )}
        {text(
          "safety",
          "Sécurité et levage",
          "Travaux en hauteur, ligne de vie, nacelle, consignation…",
        )}
        {text(
          "subcontracting",
          "Sous-traitance",
          "Lots ou tâches sous-traités, qualifications exigées…",
        )}
        {text(
          "planning",
          "Hypothèses de planning",
          "Durées, période, phasage…",
        )}
        {text("notes", "Choix et remarques de GPE")}
      </div>
      {error && <p className="banner error">{error}</p>}
      <div className="form-actions">
        <button
          type="button"
          className="primary"
          disabled={busy || !dirty}
          onClick={save}
        >
          {busy ? "Enregistrement…" : "Enregistrer les choix"}
        </button>
        {dirty && <span className="hint">Modifications non enregistrées.</span>}
      </div>
    </div>
  );
}

const LINE_TITLES: Record<LineKind, string> = {
  REQUIREMENT: "Exigences du DCE",
  QUESTION: "Questions à l'acheteur",
  QUANTITY: "Quantités clés",
  PLANE: "Calepinage par pan",
};

const LINE_HINTS: Record<LineKind, string> = {
  REQUIREMENT:
    "Ce que le DCE impose, et la réponse de GPE : sert à la demande d'agrément et au mémoire.",
  QUESTION:
    "À poser avant la date limite des questions ; notez la réponse reçue.",
  QUANTITY:
    "Les quantités qui font le chiffrage : longueurs de câbles, nombre de points, surfaces…",
  PLANE:
    "Chaque pan ou zone équipé : ses modules, son orientation et son inclinaison.",
};

function inputOf(line: TechnicalLine): LineInput {
  return {
    text: line.text,
    answer: line.answer,
    status: line.status,
    value: line.value,
    unit: line.unit,
    moduleCount: line.moduleCount,
    orientation: line.orientation,
    tilt: line.tilt,
  };
}

/** A line to read and change in place; saved when a field is left. */
function LineRow({
  tenderId,
  line,
  onError,
}: {
  tenderId: number;
  line: TechnicalLine;
  onError: (message: string) => void;
}) {
  const [input, setInput] = useState<LineInput>(inputOf(line));
  const statuses =
    line.kind === "REQUIREMENT"
      ? REQUIREMENT_STATUS_LABELS
      : line.kind === "QUESTION"
        ? QUESTION_STATUS_LABELS
        : null;

  const save = async (next: LineInput) => {
    setInput(next);
    if (!next.text.trim()) {
      onError("Écrivez le texte de la ligne, ou retirez-la.");
      return;
    }
    try {
      await updateTechnicalLine(tenderId, line.id, next);
      refresh(tenderId);
    } catch (err) {
      onError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    }
  };
  const changed = (next: Partial<LineInput>) =>
    setInput((current) => ({ ...current, ...next }));
  const leave = () => {
    if (JSON.stringify(input) !== JSON.stringify(inputOf(line))) {
      save(input);
    }
  };

  return (
    <tr>
      <td>
        <input
          value={input.text}
          maxLength={4000}
          aria-label="Texte"
          onChange={(event) => changed({ text: event.target.value })}
          onBlur={leave}
        />{" "}
        {line.origin === "AI" && (
          <AiBadge
            source={
              line.source ?? {
                documentId: null,
                fileName: null,
                page: null,
                quote: null,
              }
            }
          />
        )}
      </td>
      {statuses && (
        <td>
          <select
            value={input.status ?? ""}
            aria-label="État"
            onChange={(event) => save({ ...input, status: event.target.value })}
          >
            {Object.entries(statuses).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </td>
      )}
      {statuses && (
        <td>
          <input
            value={input.answer ?? ""}
            maxLength={4000}
            placeholder={
              line.kind === "REQUIREMENT"
                ? "Réponse de GPE (matériel, valeur…)"
                : "Réponse de l'acheteur"
            }
            aria-label="Réponse"
            onChange={(event) =>
              changed({ answer: event.target.value || null })
            }
            onBlur={leave}
          />
        </td>
      )}
      {line.kind === "QUANTITY" && (
        <td className="nowrap">
          <input
            inputMode="decimal"
            size={8}
            value={shown(input.value)}
            aria-label="Valeur"
            onChange={(event) => changed({ value: number(event.target.value) })}
            onBlur={leave}
          />{" "}
          <input
            size={5}
            value={input.unit ?? ""}
            maxLength={20}
            placeholder="unité"
            aria-label="Unité"
            onChange={(event) => changed({ unit: event.target.value || null })}
            onBlur={leave}
          />
        </td>
      )}
      {line.kind === "PLANE" && (
        <td className="nowrap">
          <input
            inputMode="numeric"
            size={5}
            value={shown(input.moduleCount)}
            placeholder="modules"
            aria-label="Modules"
            onChange={(event) =>
              changed({ moduleCount: number(event.target.value) })
            }
            onBlur={leave}
          />{" "}
          <input
            size={8}
            value={input.orientation ?? ""}
            maxLength={60}
            placeholder="orientation"
            aria-label="Orientation"
            onChange={(event) =>
              changed({ orientation: event.target.value || null })
            }
            onBlur={leave}
          />{" "}
          <input
            inputMode="decimal"
            size={4}
            value={shown(input.tilt)}
            placeholder="°"
            aria-label="Inclinaison"
            onChange={(event) => changed({ tilt: number(event.target.value) })}
            onBlur={leave}
          />
        </td>
      )}
      <td className="row-actions">
        <button
          type="button"
          className="link-button danger"
          onClick={async () => {
            try {
              await deleteTechnicalLine(tenderId, line.id);
              refresh(tenderId);
            } catch (err) {
              onError(
                err instanceof Error ? err.message : "Suppression impossible.",
              );
            }
          }}
        >
          Retirer
        </button>
      </td>
    </tr>
  );
}

function Lines({
  tenderId,
  kind,
  lines,
  onError,
}: {
  tenderId: number;
  kind: LineKind;
  lines: TechnicalLine[];
  onError: (message: string) => void;
}) {
  const [text, setText] = useState("");
  return (
    <div>
      <h3>{LINE_TITLES[kind]}</h3>
      <p className="hint">{LINE_HINTS[kind]}</p>
      {lines.length > 0 && (
        <table>
          <tbody>
            {lines.map((line) => (
              <LineRow
                key={`${line.id}#${line.origin}#${line.status}`}
                tenderId={tenderId}
                line={line}
                onError={onError}
              />
            ))}
          </tbody>
        </table>
      )}
      <form
        className="inline-form"
        onSubmit={async (event) => {
          event.preventDefault();
          if (!text.trim()) {
            return;
          }
          try {
            await addTechnicalLine(tenderId, kind, { text: text.trim() });
            setText("");
            refresh(tenderId);
          } catch (err) {
            onError(err instanceof Error ? err.message : "Ajout impossible.");
          }
        }}
      >
        <input
          value={text}
          maxLength={4000}
          placeholder="Ajouter une ligne…"
          aria-label={`Ajouter : ${LINE_TITLES[kind]}`}
          onChange={(event) => setText(event.target.value)}
        />
        <button type="submit" disabled={!text.trim()}>
          Ajouter
        </button>
      </form>
    </div>
  );
}

function Sizing({ view }: { view: TechnicalView }) {
  const sizing = view.sizing;
  return (
    <div>
      <h3>Dimensionnement</h3>
      {sizing.installedKwc === null && sizing.inverterKva === null ? (
        <p className="muted">
          Renseignez la quantité et la puissance des modules et des onduleurs
          retenus : le logiciel calcule la puissance et le ratio.
        </p>
      ) : (
        <dl className="facts rows">
          {sizing.installedKwc !== null && (
            <div className="key-fact">
              <dt>Puissance installée</dt>
              <dd>
                {decimal.format(sizing.installedKwc)} kWc (
                {decimal.format(sizing.modules)} modules)
                {sizing.gapPercent !== null && (
                  <span className="muted">
                    {" "}
                    · {sizing.gapPercent > 0 ? "+" : ""}
                    {decimal.format(sizing.gapPercent)} % par rapport à la
                    puissance demandée
                  </span>
                )}
              </dd>
            </div>
          )}
          {sizing.inverterKva !== null && (
            <div className="key-fact">
              <dt>Onduleurs</dt>
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
          {sizing.expectedMwh !== null && (
            <div className="key-fact">
              <dt>Production attendue</dt>
              <dd>
                {decimal.format(sizing.expectedMwh)} MWh/an (puissance ×
                productible)
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
    </div>
  );
}

/**
 * The technical choices of GPE for a tender, between its analysis and its
 * estimate, for every kind of work: the equipment and its quantities, the
 * requirements of the file and GPE's answer, the questions to the buyer, the key
 * quantities and the site; for PV, the use of the energy, the roof, its planes,
 * the sizing. The analysis of the file proposes what it read (marked « IA »
 * with its source); with or without a file, everything is written and changed
 * by hand, and any change takes the validation back.
 */
export function TechnicalSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(
    () => getTechnicalChoices(tenderId),
    `technical-${tenderId}`,
  );
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const view = loaded.data;

  if (!view) {
    return loaded.error ? (
      <p className="banner error">{loaded.error}</p>
    ) : (
      <p className="muted">Chargement…</p>
    );
  }
  const pv = view.domains.length === 0 || view.domains.includes("PHOTOVOLTAIC");
  const byId = new Map(view.equipment.map((item) => [item.sheet.id, item]));

  const prefill = async () => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const { added } = await prefillTechnicalChoices(tenderId);
      setNotice(
        added > 0
          ? `${added} élément(s) repris de la fiche IA, à vérifier.`
          : "Rien de nouveau dans la fiche IA.",
      );
      refresh(tenderId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Reprise impossible.");
    } finally {
      setBusy(false);
    }
  };

  const validate = async () => {
    setBusy(true);
    setError(null);
    try {
      await validateTechnicalChoices(tenderId);
      refresh(tenderId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Validation impossible.");
    } finally {
      setBusy(false);
    }
  };

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
          tout moment.
        </p>
      )}
      {view.analysisDone && (
        <p className="hint">
          {view.prefilledAt
            ? `Pré-remplis depuis l'analyse du DCE le ${formatDateTime(view.prefilledAt)} : les valeurs marquées « IA » sont à vérifier (survolez-les pour la phrase du DCE). `
            : "L'analyse du DCE peut proposer ce qu'elle a lu. "}
          <button
            type="button"
            className="link-button"
            disabled={busy}
            title="Ne remplit que ce qui est vide : rien de ce que vous avez écrit n'est changé"
            onClick={prefill}
          >
            Reprendre la fiche IA
          </button>
        </p>
      )}
      {notice && <p className="banner info">{notice}</p>}
      {error && <p className="banner error">{error}</p>}

      <EquipmentSection
        tenderId={tenderId}
        details={{
          of: (sheetId) => {
            const item = byId.get(sheetId);
            return item ? (
              <EquipmentDetail
                key={`${sheetId}#${item.quantity}#${item.unitPower}#${item.note}`}
                tenderId={tenderId}
                item={item}
                onError={setError}
              />
            ) : null;
          },
        }}
      />
      {pv && <Sizing view={view} />}

      <ChoicesForm
        key={`${view.updatedAt}#${view.prefilledAt}`}
        tenderId={tenderId}
        view={view}
        pv={pv}
      />

      <Lines
        tenderId={tenderId}
        kind="REQUIREMENT"
        lines={view.requirements}
        onError={setError}
      />
      {pv && (
        <Lines
          tenderId={tenderId}
          kind="PLANE"
          lines={view.planes}
          onError={setError}
        />
      )}
      <Lines
        tenderId={tenderId}
        kind="QUANTITY"
        lines={view.quantities}
        onError={setError}
      />
      <Lines
        tenderId={tenderId}
        kind="QUESTION"
        lines={view.questions}
        onError={setError}
      />

      {view.saved && !view.validatedAt && (
        <div className="form-actions">
          <button
            type="button"
            className="primary"
            disabled={busy}
            onClick={validate}
          >
            Valider les choix techniques
          </button>
        </div>
      )}
    </div>
  );
}
