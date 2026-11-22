"use client";

import { useState, type FormEvent } from "react";

import { useAuth } from "@/components/AuthProvider";
import { Pager, pageOf } from "@/components/Pager";
import { formatDate, normalizeText } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import {
  addEquipmentSheet,
  deleteEquipmentSheet,
  EQUIPMENT_CATEGORY_LABELS,
  equipmentSheetUrl,
  getEquipmentSheets,
  updateEquipmentSheet,
  type EquipmentSheet,
} from "@/services/equipmentApi";

function CategorySelect({
  value,
  onChange,
  all,
}: {
  value: string;
  onChange: (value: string) => void;
  all?: boolean;
}) {
  return (
    <select
      value={value}
      onChange={(event) => onChange(event.target.value)}
      aria-label="Catégorie"
    >
      {all && <option value="">Toutes</option>}
      {Object.entries(EQUIPMENT_CATEGORY_LABELS).map(([code, label]) => (
        <option key={code} value={code}>
          {label}
        </option>
      ))}
    </select>
  );
}

/** A line of the library; an administrator corrects what was guessed from the file name. */
function SheetRow({
  sheet,
  admin,
  onChanged,
  onError,
}: {
  sheet: EquipmentSheet;
  admin: boolean;
  onChanged: () => void;
  onError: (message: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [category, setCategory] = useState(sheet.category);
  const [brand, setBrand] = useState(sheet.brand ?? "");
  const [reference, setReference] = useState(sheet.reference);

  const save = async () => {
    try {
      await updateEquipmentSheet(sheet.id, {
        category,
        brand: brand.trim() || null,
        reference: reference.trim(),
      });
      setEditing(false);
      onChanged();
    } catch (err) {
      onError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    }
  };

  return (
    <tr>
      <td>
        {editing ? (
          <CategorySelect value={category} onChange={setCategory} />
        ) : (
          (EQUIPMENT_CATEGORY_LABELS[sheet.category] ?? sheet.category)
        )}
      </td>
      <td>
        {editing ? (
          <input
            value={brand}
            maxLength={100}
            size={12}
            onChange={(event) => setBrand(event.target.value)}
            aria-label="Marque"
          />
        ) : (
          (sheet.brand ?? "–")
        )}
      </td>
      <td>
        {editing ? (
          <input
            value={reference}
            maxLength={200}
            size={30}
            required
            onChange={(event) => setReference(event.target.value)}
            aria-label="Référence"
          />
        ) : (
          sheet.reference
        )}
        <div className="small">
          <a
            href={equipmentSheetUrl(sheet.id)}
            target="_blank"
            rel="noopener noreferrer"
            title="Ouvrir la fiche technique"
          >
            {sheet.fileName}
          </a>
        </div>
      </td>
      <td className="nowrap">{formatDate(sheet.uploadedAt)}</td>
      {admin && (
        <td className="row-actions">
          {editing ? (
            <>
              <button
                type="button"
                className="primary"
                disabled={!reference.trim()}
                onClick={save}
              >
                Enregistrer
              </button>
              <button
                type="button"
                className="link-button"
                onClick={() => setEditing(false)}
              >
                Annuler
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                className="secondary"
                onClick={() => setEditing(true)}
              >
                Corriger
              </button>
              <button
                type="button"
                className="secondary danger"
                onClick={async () => {
                  if (
                    window.confirm(
                      `Retirer « ${sheet.reference} » ? Elle sera aussi retirée des appels d'offres qui l'utilisent.`,
                    )
                  ) {
                    try {
                      await deleteEquipmentSheet(sheet.id);
                      onChanged();
                    } catch (err) {
                      onError(
                        err instanceof Error
                          ? err.message
                          : "Suppression impossible.",
                      );
                    }
                  }
                }}
              >
                Retirer
              </button>
            </>
          )}
        </td>
      )}
    </tr>
  );
}

/**
 * The technical sheets of the equipment GPE installs: chosen for a tender,
 * they go with its offer and make its equipment table. Laid out as the other
 * libraries; read by every user, filled by an administrator.
 */
export default function EquipmentSheetsPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  const sheets = useLoader(() => getEquipmentSheets(), "equipment-sheets");
  const [category, setCategory] = useState("");
  const [filter, setFilter] = useState("");
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const words = normalizeText(filter).split(/\s+/).filter(Boolean);
  const listed = (sheets.data ?? []).filter(
    (sheet) =>
      (!category || sheet.category === category) &&
      words.every((word) =>
        normalizeText(
          `${sheet.brand ?? ""} ${sheet.reference} ${sheet.fileName}`,
        ).includes(word),
      ),
  );

  const add = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const target = event.currentTarget;
    const form = new FormData(target);
    const files = form
      .getAll("file")
      .filter((file): file is File => file instanceof File && file.size > 0);
    if (files.length === 0) {
      setError("Choisissez au moins une fiche technique (PDF).");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      // Several sheets at once: what each one is comes from its file name, to correct after.
      for (const file of files) {
        await addEquipmentSheet(file, {});
      }
      target.reset();
      sheets.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ajout impossible.");
      sheets.reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Fiches techniques</h1>
      {(error || sheets.error) && (
        <p className="banner error">{error ?? sheets.error}</p>
      )}

      <section className="card">
        <h2>Fiches de la bibliothèque</h2>
        <p className="muted">
          Le matériel retenu pour un appel d&apos;offres (onglet « Dossier de
          réponse ») joint ses fiches à l&apos;offre, dans 06-Fiches techniques,
          et fait le tableau matériel du mémoire.
        </p>
        {sheets.data && sheets.data.length > 0 && (
          <div className="inline-form">
            <label>
              Catégorie{" "}
              <CategorySelect
                value={category}
                all
                onChange={(value) => {
                  setCategory(value);
                  setPage(0);
                }}
              />
            </label>
            <input
              value={filter}
              onChange={(event) => {
                setFilter(event.target.value);
                setPage(0);
              }}
              placeholder="Marque, référence, fichier…"
              aria-label="Filtrer les fiches"
            />
            <span className="muted">
              {listed.length} fiche{listed.length > 1 ? "s" : ""}
            </span>
            {admin && (
              <button
                type="button"
                className={adding ? "" : "primary"}
                aria-expanded={adding}
                onClick={() => setAdding(!adding)}
              >
                {adding ? "Fermer" : "Ajouter des fiches"}
              </button>
            )}
          </div>
        )}
        {(adding || (admin && sheets.data?.length === 0)) && (
          <form className="inline-form" onSubmit={add}>
            <label>
              Fiches techniques (PDF)
              <input type="file" name="file" accept=".pdf" multiple required />
            </label>
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Ajout…" : "Ajouter"}
            </button>
            <p className="hint">
              La catégorie, la marque et la référence sont déduites du nom du
              fichier : corrigez-les ensuite si besoin. Une fiche déjà présente
              n&apos;est pas ajoutée deux fois.
            </p>
          </form>
        )}
        {sheets.data && listed.length === 0 && (
          <p className="muted">
            {sheets.data.length === 0
              ? "Aucune fiche pour l'instant."
              : "Aucune fiche ne correspond à ce filtre."}
          </p>
        )}
        {listed.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Matériel</th>
                <th>Marque</th>
                <th>Référence</th>
                <th>Ajoutée</th>
                {admin && <th />}
              </tr>
            </thead>
            <tbody>
              {pageOf(listed, page).map((sheet) => (
                <SheetRow
                  key={sheet.id}
                  sheet={sheet}
                  admin={admin}
                  onChanged={sheets.reload}
                  onError={setError}
                />
              ))}
            </tbody>
          </table>
        )}
        <Pager
          page={page}
          total={listed.length}
          label="Pages des fiches techniques"
          onChange={setPage}
        />
      </section>
    </div>
  );
}
