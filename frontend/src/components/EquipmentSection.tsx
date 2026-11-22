"use client";

import Link from "next/link";
import { useState } from "react";

import { normalizeText } from "@/lib/format";
import { refreshData, useLoader } from "@/lib/useLoader";
import {
  chooseTenderEquipment,
  EQUIPMENT_CATEGORY_LABELS,
  equipmentSheetUrl,
  equipmentTableUrl,
  getEquipmentSheets,
  getTenderEquipment,
  type EquipmentSheet,
} from "@/services/equipmentApi";

/** The sheets of the library to tick, by category in the order of the equipment table. */
function Chooser({
  chosen,
  onSave,
  onCancel,
}: {
  chosen: EquipmentSheet[];
  onSave: (ids: number[]) => Promise<void>;
  onCancel: () => void;
}) {
  const library = useLoader(() => getEquipmentSheets(), "equipment-sheets");
  const [ticked, setTicked] = useState<Set<number>>(
    () => new Set(chosen.map((sheet) => sheet.id)),
  );
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState(false);
  const words = normalizeText(filter).split(/\s+/).filter(Boolean);
  const shown = (library.data ?? []).filter((sheet) =>
    words.every((word) =>
      normalizeText(
        `${sheet.brand ?? ""} ${sheet.reference} ${sheet.fileName}`,
      ).includes(word),
    ),
  );

  if (library.data && library.data.length === 0) {
    return (
      <p className="muted">
        Aucune fiche dans la bibliothèque : ajoutez-les d&apos;abord dans{" "}
        <Link href="/equipment-sheets">Fiches techniques</Link>.
      </p>
    );
  }

  const toggle = (id: number) => {
    const next = new Set(ticked);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setTicked(next);
  };

  return (
    <div className="sheet-panel">
      <div className="inline-form">
        <input
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
          placeholder="Marque, référence…"
          aria-label="Filtrer le matériel"
        />
        <span className="muted">{ticked.size} retenu(s)</span>
        <button
          type="button"
          className="primary"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            try {
              await onSave([...ticked]);
            } finally {
              setBusy(false);
            }
          }}
        >
          Enregistrer le matériel
        </button>
        <button type="button" className="link-button" onClick={onCancel}>
          Annuler
        </button>
      </div>
      {Object.entries(EQUIPMENT_CATEGORY_LABELS).map(([code, label]) => {
        const sheets = shown.filter((sheet) => sheet.category === code);
        if (sheets.length === 0) {
          return null;
        }
        return (
          <fieldset key={code} className="equipment-group">
            <legend>{label}</legend>
            {sheets.map((sheet) => (
              <label key={sheet.id} className="equipment-choice">
                <input
                  type="checkbox"
                  checked={ticked.has(sheet.id)}
                  onChange={() => toggle(sheet.id)}
                />{" "}
                {sheet.brand && <strong>{sheet.brand} </strong>}
                {sheet.reference}
              </label>
            ))}
          </fieldset>
        );
      })}
    </div>
  );
}

/**
 * The equipment chosen for a tender: its sheets go with the offer (06-Fiches
 * techniques), it makes the equipment table of GPE and the AI writes the memoir
 * with it, beginning from the template memoir of its mounting system.
 */
export function EquipmentSection({ tenderId }: { tenderId: number }) {
  const equipment = useLoader(
    () => getTenderEquipment(tenderId),
    `equipment-${tenderId}`,
  );
  const [choosing, setChoosing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chosen = equipment.data ?? [];

  const save = async (ids: number[]) => {
    setError(null);
    try {
      await chooseTenderEquipment(tenderId, ids);
      setChoosing(false);
      refreshData(`equipment-${tenderId}`, `response-${tenderId}`);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    }
  };

  return (
    <div>
      <h3>Matériel retenu</h3>
      <p className="muted">
        Ses fiches techniques partent avec l&apos;offre (06-Fiches techniques) ;
        il fait le tableau matériel, et l&apos;IA rédige le mémoire avec lui, à
        partir du mémoire type de son système de fixation.
      </p>
      {error && <p className="banner error">{error}</p>}
      {equipment.data && chosen.length === 0 && !choosing && (
        <p className="muted">Aucun matériel retenu pour l&apos;instant.</p>
      )}
      {chosen.length > 0 && !choosing && (
        <table>
          <thead>
            <tr>
              <th>Matériel</th>
              <th>Marque</th>
              <th>Référence</th>
            </tr>
          </thead>
          <tbody>
            {chosen.map((sheet) => (
              <tr key={sheet.id}>
                <td>
                  {EQUIPMENT_CATEGORY_LABELS[sheet.category] ?? sheet.category}
                </td>
                <td>{sheet.brand ?? "–"}</td>
                <td>
                  <a
                    href={equipmentSheetUrl(sheet.id)}
                    target="_blank"
                    rel="noopener noreferrer"
                    title="Ouvrir la fiche technique"
                  >
                    {sheet.reference}
                  </a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {choosing ? (
        <Chooser
          chosen={chosen}
          onSave={save}
          onCancel={() => setChoosing(false)}
        />
      ) : (
        <div className="inline-form">
          <button
            type="button"
            className={chosen.length === 0 ? "primary" : ""}
            onClick={() => setChoosing(true)}
          >
            {chosen.length === 0
              ? "Choisir le matériel"
              : "Modifier le matériel"}
          </button>
          {chosen.length > 0 && (
            <a className="button" href={equipmentTableUrl(tenderId)} download>
              Tableau matériel (Excel)
            </a>
          )}
        </div>
      )}
    </div>
  );
}
