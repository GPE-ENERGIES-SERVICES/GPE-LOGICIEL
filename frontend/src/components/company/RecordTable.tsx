"use client";

import { Fragment, useState, type FormEvent, type ReactNode } from "react";

import { useAuth } from "@/components/AuthProvider";
import { formatDate, formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import {
  companyFileUrl,
  createRow,
  deleteCompanyFile,
  deleteRow,
  getRows,
  updateRow,
  uploadRowFile,
  type CompanyList,
} from "@/services/companyApi";
import type { CompanyRow, Validity } from "@/types/company";

/** A field of the form of a line. */
export type Field = {
  name: string;
  label: string;
  type?: "text" | "textarea" | "date" | "number" | "select" | "checkbox" | "list" | "email";
  /** For a select: value → label. */
  options?: Record<string, string>;
  required?: boolean;
  placeholder?: string;
  /** Takes two columns of the form. */
  wide?: boolean;
};

/** A column of the table; without render, the field of the same name, as text. */
export type Column = {
  key: string;
  label: string;
  render?: (row: CompanyRow) => ReactNode;
  className?: string;
};

type Props = {
  list: CompanyList;
  columns: Column[];
  fields: Field[];
  /** Name of a line, for the buttons: « Ajouter un salarié ». */
  noun: string;
  empty: string;
  /** Lines shown, for a list split between several tables (credentials of an employee). */
  filter?: (row: CompanyRow) => boolean;
  /** Values sent with every line, not typed (the employee of a credential). */
  fixed?: Record<string, unknown>;
  /** What is shown below a line opened (the credentials of an employee). */
  detail?: (row: CompanyRow) => ReactNode;
  /** Text of the button opening a line. */
  detailLabel?: string;
};

export const VALIDITY_LABELS: Record<Validity, string> = {
  NONE: "Sans échéance",
  VALID: "Valide",
  EXPIRING: "Expire bientôt",
  EXPIRED: "Expiré",
};

const VALIDITY_CLASSES: Record<Validity, string> = {
  NONE: "badge muted-badge",
  VALID: "badge verified",
  EXPIRING: "badge urgent",
  EXPIRED: "badge critical",
};

export function ValidityBadge({ validity }: { validity: Validity | null }) {
  return validity ? <span className={VALIDITY_CLASSES[validity]}>{VALIDITY_LABELS[validity]}</span> : null;
}

/** A date field shown as dd/MM/yyyy, or nothing. */
export function dateCell(field: string) {
  return (row: CompanyRow) => (row[field] ? formatDate(String(row[field])) : "");
}

function text(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  return Array.isArray(value) ? value.join(", ") : String(value);
}

/** The value of a field typed in the form, as the API takes it. */
function read(form: FormData, field: Field): unknown {
  const value = String(form.get(field.name) ?? "").trim();
  switch (field.type) {
    case "checkbox":
      return form.get(field.name) === "on";
    case "number":
      return value === "" ? null : Number(value.replace(/\s/g, "").replace(",", "."));
    case "list":
      return value === "" ? [] : value.split(/[\s,;]+/).filter(Boolean);
    default:
      return value === "" ? null : value;
  }
}

/**
 * A list of the company as a table: read by everyone, changed by an
 * administrator in a short form above the table. Lines with an end show
 * their validity; lines with files list them, the last first, once opened.
 */
export function RecordTable({ list, columns, fields, noun, empty, filter, fixed, detail, detailLabel }: Props) {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  const rows = useLoader(() => getRows(list), list);
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [open, setOpen] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const shown = (rows.data ?? []).filter((row) => !filter || filter(row));
  const withFiles = shown.some((row) => row.files !== null) || (rows.data?.[0]?.files ?? null) !== null;
  const withValidity = shown.some((row) => row.validity !== null);
  const opens = withFiles || Boolean(detail);
  const columnCount = columns.length + (withValidity ? 1 : 0) + 1;

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      rows.reload();
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
      return false;
    } finally {
      setBusy(false);
    }
  };

  const save = async (event: FormEvent<HTMLFormElement>, row?: CompanyRow) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body: Record<string, unknown> = { ...fixed };
    for (const field of fields) {
      body[field.name] = read(form, field);
    }
    if (await run(() => (row ? updateRow(list, row.id, body) : createRow(list, body)))) {
      setEditing(null);
    }
  };

  const form = (row?: CompanyRow) => (
    <form className="record-form" onSubmit={(event) => save(event, row)} key={row?.id ?? "new"}>
      <div className="form-grid">
        {fields.map((field) => (
          <FieldInput key={field.name} field={field} value={row?.[field.name]} />
        ))}
      </div>
      <div className="form-actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy ? "Enregistrement…" : "Enregistrer"}
        </button>
        <button type="button" className="secondary" onClick={() => setEditing(null)} disabled={busy}>
          Annuler
        </button>
      </div>
    </form>
  );

  return (
    <div className="record-table">
      {rows.error && <p className="banner error">{rows.error}</p>}
      {admin && editing === "new" && form()}
      {admin && editing !== "new" && (
        <div className="form-actions record-add">
          <button type="button" onClick={() => setEditing("new")}>
            Ajouter {noun}
          </button>
        </div>
      )}
      {error && <p className="banner error">{error}</p>}
      {rows.data && shown.length === 0 && <p className="muted">{empty}</p>}
      {shown.length > 0 && (
        <table>
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column.key} className={column.className}>
                  {column.label}
                </th>
              ))}
              {withValidity && <th>Validité</th>}
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {shown.map((row) => (
              <Fragment key={row.id}>
                {editing === row.id ? (
                  <tr>
                    <td colSpan={columnCount}>{form(row)}</td>
                  </tr>
                ) : (
                  <tr>
                    {columns.map((column) => (
                      <td key={column.key} className={column.className}>
                        {column.render ? column.render(row) : text(row[column.key])}
                      </td>
                    ))}
                    {withValidity && (
                      <td>
                        <ValidityBadge validity={row.validity} />
                      </td>
                    )}
                    <td className="row-actions">
                      {opens && (
                        <button
                          type="button"
                          className="secondary"
                          aria-expanded={open === row.id}
                          onClick={() => setOpen(open === row.id ? null : row.id)}
                        >
                          {detailLabel ?? "Fichiers"}
                          {withFiles && !detail && ` (${row.files?.length ?? 0})`}
                        </button>
                      )}
                      {admin && (
                        <>
                          <button type="button" className="secondary" onClick={() => setEditing(row.id)} disabled={busy}>
                            Modifier
                          </button>
                          <button
                            type="button"
                            className="secondary"
                            disabled={busy}
                            onClick={() => {
                              if (window.confirm(`Supprimer ${noun} et ses fichiers ?`)) {
                                run(() => deleteRow(list, row.id));
                              }
                            }}
                          >
                            Supprimer
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                )}
                {open === row.id && (
                  <tr className="record-detail">
                    <td colSpan={columnCount}>
                      {row.files !== null && (
                        <Files row={row} admin={admin} busy={busy} run={run} list={list} />
                      )}
                      {detail?.(row)}
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function FieldInput({ field, value }: { field: Field; value: unknown }) {
  const label = `${field.label}${field.required ? " *" : ""}`;
  const initial = value === null || value === undefined ? "" : Array.isArray(value) ? value.join(", ") : String(value);
  if (field.type === "checkbox") {
    return (
      <label className="checkbox-field">
        <input type="checkbox" name={field.name} defaultChecked={value === undefined ? true : Boolean(value)} /> {field.label}
      </label>
    );
  }
  return (
    <label className={field.wide ? "wide" : undefined}>
      {label}
      {field.type === "select" ? (
        <select name={field.name} required={field.required} defaultValue={initial}>
          {!field.required && <option value="">—</option>}
          {Object.entries(field.options ?? {}).map(([optionValue, optionLabel]) => (
            <option key={optionValue} value={optionValue}>
              {optionLabel}
            </option>
          ))}
        </select>
      ) : field.type === "textarea" ? (
        <textarea name={field.name} required={field.required} defaultValue={initial} rows={3} placeholder={field.placeholder} />
      ) : (
        <input
          name={field.name}
          type={field.type === "date" ? "date" : field.type === "email" ? "email" : "text"}
          inputMode={field.type === "number" ? "decimal" : undefined}
          required={field.required}
          defaultValue={initial}
          placeholder={field.placeholder}
        />
      )}
    </label>
  );
}

/** The files of a line, the last first (for a document, the current one), and adding one. */
function Files({
  row,
  admin,
  busy,
  run,
  list,
}: {
  row: CompanyRow;
  admin: boolean;
  busy: boolean;
  run: (action: () => Promise<unknown>) => Promise<boolean>;
  list: CompanyList;
}) {
  const files = row.files ?? [];
  return (
    <div className="record-files">
      {files.length === 0 ? (
        <p className="muted">Aucun fichier.</p>
      ) : (
        <ul className="plain-list">
          {files.map((file, index) => (
            <li key={file.id}>
              <a href={companyFileUrl(file.id)} target="_blank" rel="noopener noreferrer">
                {file.fileName}
              </a>{" "}
              <span className="muted">
                · {formatDateTime(file.uploadedAt)}
                {file.uploadedBy && ` par ${file.uploadedBy}`}
                {list === "documents" && (index === 0 ? " · version en vigueur" : " · ancienne version")}
              </span>
              {admin && (
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => {
                    if (window.confirm(`Supprimer le fichier « ${file.fileName} » ?`)) {
                      run(() => deleteCompanyFile(file.id));
                    }
                  }}
                >
                  Supprimer
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {admin && (
        <label className="record-upload">
          {list === "documents" && files.length > 0 ? "Remplacer par un nouveau fichier : " : "Ajouter un fichier : "}
          <input
            type="file"
            accept=".pdf,.png,.jpg,.jpeg,.tif,.tiff,.doc,.docx,.xls,.xlsx,.odt,.ods"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                run(() => uploadRowFile(list, row.id, file));
                event.target.value = "";
              }
            }}
          />
        </label>
      )}
      {list === "documents" && admin && (
        <p className="hint">Pensez à mettre à jour les dates du document (bouton Modifier) après l&apos;avoir remplacé.</p>
      )}
    </div>
  );
}
