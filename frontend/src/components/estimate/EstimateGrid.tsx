"use client";

import {
  Fragment,
  useEffect,
  useRef,
  useState,
  type ClipboardEvent,
  type FocusEvent,
  type KeyboardEvent,
} from "react";

import { formatMoney, formatRate, numberInput, parseNumber, textOrNull } from "@/lib/estimate";
import { addItem, deleteItem, reorderItems, saveItems, updateItem } from "@/services/estimateApi";
import type { Estimate, EstimateItem, ItemInput, PricingRate, Suggestion } from "@/types/estimate";

import { ComponentsPanel } from "./ComponentsPanel";
import { ItemForm } from "./ItemForm";
import { Icon } from "@/components/Icon";

type Field = "reference" | "designation" | "quantity" | "unit" | "price";

/** The editable columns, in the order of a price schedule; the amount follows, computed. */
const COLUMNS: { field: Field; label: string; className: string }[] = [
  { field: "reference", label: "N°", className: "col-ref" },
  { field: "designation", label: "Désignation", className: "col-designation" },
  { field: "quantity", label: "Quantité", className: "col-number" },
  { field: "unit", label: "Unité", className: "col-unit" },
  { field: "price", label: "PU HT", className: "col-number" },
];

type Draft = Record<Field, string>;

const EMPTY_DRAFT: Draft = { reference: "", designation: "", quantity: "", unit: "", price: "" };

type Props = {
  estimate: Estimate;
  editable: boolean;
  rates: PricingRate[];
  aiAvailable: boolean;
  suggestions: Suggestion[];
  onChanged: (estimate: Estimate) => void;
  onSuggestions: () => void;
};

/** The value of a cell as shown: for the price, the price entered, the mention, or the price computed from the detail. */
function shown(item: EstimateItem, field: Field): string {
  switch (field) {
    case "reference":
      return item.reference ?? "";
    case "designation":
      return item.designation;
    case "quantity":
      return numberInput(item.quantity);
    case "unit":
      return item.unit ?? "";
    case "price":
      return item.priceMode === "TEXT"
        ? (item.textPrice ?? "")
        : numberInput(item.priceMode === "FIXED" ? item.fixedUnitPrice : item.unitPrice);
  }
}

function inputOf(item: EstimateItem): ItemInput {
  return {
    chapter: item.chapter,
    reference: item.reference,
    designation: item.designation,
    unit: item.unit,
    quantity: item.quantity,
    priceMode: item.priceMode,
    fixedUnitPrice: item.fixedUnitPrice,
    textPrice: item.textPrice,
    option: item.option,
    toCheck: item.toCheck,
  };
}

/**
 * A cell typed into a line. A price typed replaces the price computed from
 * the detail; a word (PM, inclus) is a mention; an empty price waits.
 * @throws Error with the message to show when the value is unreadable
 */
function withCell(input: ItemInput, field: Field, text: string): ItemInput {
  switch (field) {
    case "reference":
      return { ...input, reference: textOrNull(text)?.slice(0, 50) ?? null };
    case "designation": {
      const designation = textOrNull(text);
      if (!designation) {
        throw new Error("La désignation ne peut pas être vide.");
      }
      return { ...input, designation: designation.slice(0, 4000) };
    }
    case "quantity": {
      const quantity = parseNumber(text);
      if (quantity !== null && (Number.isNaN(quantity) || quantity < 0)) {
        throw new Error(`Quantité illisible : « ${text} ».`);
      }
      return { ...input, quantity };
    }
    case "unit":
      return { ...input, unit: textOrNull(text)?.slice(0, 20) ?? null };
    case "price": {
      const value = parseNumber(text);
      if (value === null) {
        return { ...input, priceMode: "FIXED", fixedUnitPrice: null, textPrice: null };
      }
      if (!Number.isNaN(value)) {
        if (value < 0) {
          throw new Error("Un prix ne peut pas être négatif.");
        }
        return { ...input, priceMode: "FIXED", fixedUnitPrice: Math.round(value * 100) / 100, textPrice: null };
      }
      return { ...input, priceMode: "TEXT", fixedUnitPrice: null, textPrice: text.trim().slice(0, 30) };
    }
  }
}

const NEW_ITEM: ItemInput = {
  chapter: null,
  reference: null,
  designation: "",
  unit: null,
  quantity: null,
  priceMode: "FIXED",
  fixedUnitPrice: null,
  textPrice: null,
  option: false,
  toCheck: false,
};

/**
 * The lines of an estimate as a spreadsheet: every cell is typed into and
 * saved when left, the amount is computed by the backend, a last empty line
 * adds a line, and lines copied from Excel are pasted at once. The detail of
 * a line (supplies, labour…) is optional, behind « Détail ».
 */
export function EstimateGrid({ estimate, editable, rates, aiAvailable, suggestions, onChanged, onSuggestions }: Props) {
  const table = useRef<HTMLTableElement>(null);
  // Saves are sent one after the other: each answer holds the whole estimate.
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [open, setOpen] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const items = estimate.items;

  // The cells not being typed into show the values saved.
  useEffect(() => {
    table.current?.querySelectorAll<HTMLInputElement>("input[data-item]").forEach((input) => {
      const item = items.find((candidate) => String(candidate.id) === input.dataset.item);
      if (item && input !== document.activeElement) {
        input.value = shown(item, input.dataset.field as Field);
      }
    });
  }, [items]);

  const enqueue = (action: () => Promise<Estimate | void>) => {
    setSaving((count) => count + 1);
    setError(null);
    queue.current = queue.current
      .then(action)
      .then((result) => {
        if (result) {
          onChanged(result);
        }
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Enregistrement impossible."))
      .finally(() => setSaving((count) => count - 1));
  };

  const commit = (item: EstimateItem, field: Field, input: HTMLInputElement) => {
    if (input.value.trim() === shown(item, field).trim()) {
      return;
    }
    try {
      const next = withCell(inputOf(item), field, input.value);
      enqueue(() => updateItem(item.id, next));
    } catch (err) {
      setError((err as Error).message);
      input.value = shown(item, field);
    }
  };

  /** The new line is saved once it has a designation and the user leaves it. */
  const leaveDraft = (event: FocusEvent<HTMLInputElement>) => {
    const row = event.currentTarget.closest("tr");
    if (row && event.relatedTarget instanceof Node && row.contains(event.relatedTarget)) {
      return;
    }
    saveDraft();
  };

  const saveDraft = () => {
    if (!draft.designation.trim()) {
      return;
    }
    try {
      const next = (Object.keys(draft) as Field[]).reduce(
        (input, field) => (draft[field].trim() ? withCell(input, field, draft[field]) : input),
        NEW_ITEM,
      );
      setDraft(EMPTY_DRAFT);
      enqueue(() => addItem(estimate.id, next));
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const focusCell = (row: number, column: number) => {
    table.current?.querySelector<HTMLInputElement>(`input[data-row="${row}"][data-col="${column}"]`)?.focus();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>, row: number, column: number, item?: EstimateItem) => {
    if (event.key === "Enter" || event.key === "ArrowDown") {
      event.preventDefault();
      if (!item && event.key === "Enter") {
        saveDraft();
      }
      focusCell(row + 1, column);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      focusCell(row - 1, column);
    } else if (event.key === "Escape" && item) {
      event.currentTarget.value = shown(item, COLUMNS[column].field);
      event.currentTarget.blur();
    }
  };

  /**
   * Cells copied from Excel (tabs between cells, a line per row), pasted from
   * the cell chosen: the lines below are changed, the lines beyond added.
   */
  const onPaste = (event: ClipboardEvent<HTMLInputElement>, row: number, column: number) => {
    const text = event.clipboardData.getData("text/plain");
    if (!text.includes("\t") && !text.trim().includes("\n")) {
      return;
    }
    event.preventDefault();
    // shortcut: cells holding a line break or a tab (quoted by Excel) are not read; enough for price schedules.
    const lines = text.replace(/\r/g, "").replace(/\n+$/, "").split("\n");
    try {
      const rows: { id: number | null; item: ItemInput }[] = [];
      lines.forEach((line, offset) => {
        const item = items[row + offset];
        let input = item ? inputOf(item) : { ...NEW_ITEM };
        line.split("\t").forEach((cell, index) => {
          const target = COLUMNS[column + index];
          const value = cell.replace(/^"(.*)"$/, "$1");
          // The amount column, and anything beyond, is not read: the backend computes the amount.
          if (target && (target.field !== "designation" || value.trim())) {
            input = withCell(input, target.field, value);
          }
        });
        if (item || input.designation.trim()) {
          rows.push({ id: item?.id ?? null, item: input });
        }
      });
      if (rows.length > 0) {
        setDraft(EMPTY_DRAFT);
        enqueue(() => saveItems(estimate.id, rows));
      }
    } catch (err) {
      setError(`Collage refusé : ${(err as Error).message}`);
    }
  };

  const move = (index: number, step: -1 | 1) => {
    const ids = items.map((item) => item.id);
    [ids[index], ids[index + step]] = [ids[index + step], ids[index]];
    enqueue(() => reorderItems(estimate.id, ids));
  };

  const toggle = (id: number) =>
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  const columnCount = COLUMNS.length + 3;
  const totals: [string, number, boolean][] = [
    ["Total HT", estimate.totalHt, true],
    [`TVA ${formatRate(estimate.vatRate)}`, estimate.totalVat, false],
    ["Total TTC", estimate.totalTtc, true],
  ];
  if (estimate.optionsHt !== 0) {
    totals.push(["Options HT (hors total)", estimate.optionsHt, false]);
  }
  // Known only once every line has its detail: not shown for an estimate made by hand.
  if (estimate.margin !== null && estimate.totalCost) {
    totals.push(["Déboursé sec", estimate.totalCost, false], ["Marge", estimate.margin, false]);
  }

  return (
    <>
      <table className="estimate-grid" ref={table}>
        <thead>
          <tr>
            <th className="col-row" aria-label="Ligne" />
            {COLUMNS.map((column) => (
              <th key={column.field} className={column.className}>
                {column.label}
              </th>
            ))}
            <th className="col-number">Montant HT</th>
            <th className="col-actions" aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => {
            const heading = item.chapter && item.chapter !== items[index - 1]?.chapter;
            return (
              <Fragment key={item.id}>
                {heading && (
                  <tr className="estimate-chapter">
                    <td colSpan={columnCount}>{item.chapter}</td>
                  </tr>
                )}
                <tr className={item.option ? "row-muted" : undefined}>
                  <td className="col-row">{index + 1}</td>
                  {COLUMNS.map((column, columnIndex) => (
                    <td key={column.field} className={column.className}>
                      <input
                        data-item={item.id}
                        data-field={column.field}
                        data-row={index}
                        data-col={columnIndex}
                        defaultValue={shown(item, column.field)}
                        readOnly={!editable}
                        aria-label={`${column.label}, ligne ${index + 1}`}
                        title={
                          column.field === "price" && item.priceMode === "COMPOSED"
                            ? "Calculé depuis le détail : saisir un prix le remplace"
                            : undefined
                        }
                        className={column.field === "price" && item.priceMode === "COMPOSED" ? "computed" : undefined}
                        inputMode={column.field === "quantity" || column.field === "price" ? "decimal" : undefined}
                        onBlur={(event) => editable && commit(item, column.field, event.currentTarget)}
                        onKeyDown={(event) => onKeyDown(event, index, columnIndex, item)}
                        onPaste={(event) => editable && onPaste(event, index, columnIndex)}
                      />
                    </td>
                  ))}
                  <td className={item.amount === null && item.priceMode !== "TEXT" ? "col-number missing" : "col-number"}>
                    {item.amount === null ? "—" : formatMoney(item.amount)}
                  </td>
                  <td className="col-actions">
                    {item.option && <span className="badge">Option</span>}
                    {item.toCheck && <span className="badge urgent">À vérifier</span>}
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => toggle(item.id)}
                      aria-expanded={open.has(item.id)}
                      title="Options et détail facultatif (fournitures, main-d'œuvre…)"
                    >
                      Détail{item.components.length > 0 && ` (${item.components.length})`}
                    </button>
                    {editable && (
                      <>
                        <button type="button" className="secondary" onClick={() => move(index, -1)} disabled={index === 0} aria-label="Monter la ligne">
                          <Icon name="arrowUp" size={14} />
                        </button>
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => move(index, 1)}
                          disabled={index === items.length - 1}
                          aria-label="Descendre la ligne"
                        >
                          <Icon name="arrowDown" size={14} />
                        </button>
                        <button
                          type="button"
                          className="secondary danger"
                          aria-label="Supprimer la ligne"
                          title="Supprimer la ligne"
                          onClick={() => {
                            if (window.confirm(`Supprimer la ligne « ${item.designation} » ?`)) {
                              enqueue(() => deleteItem(item.id));
                            }
                          }}
                        >
                          <Icon name="close" size={14} />
                        </button>
                      </>
                    )}
                  </td>
                </tr>
                {open.has(item.id) && (
                  <tr className="estimate-detail">
                    <td colSpan={columnCount}>
                      {editable && (
                        <ItemForm
                          key={estimate.updatedAt}
                          item={item}
                          submitLabel="Enregistrer les options"
                          onSubmit={async (input) => {
                            onChanged(await updateItem(item.id, input));
                          }}
                        />
                      )}
                      <h4>Détail du poste (facultatif)</h4>
                      <ComponentsPanel
                        estimate={estimate}
                        item={item}
                        rates={rates}
                        editable={editable}
                        aiAvailable={aiAvailable}
                        suggestions={suggestions.filter((suggestion) => suggestion.itemId === item.id)}
                        onChanged={onChanged}
                        onSuggestions={onSuggestions}
                      />
                      {item.priceMode !== "COMPOSED" && item.components.length > 0 && (
                        <p className="hint">
                          Le prix unitaire reste celui saisi ; pour le calculer depuis ce détail, choisissez « Calculé » dans le
                          champ Prix ci-dessus.
                        </p>
                      )}
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
          {editable && (
            <tr className="estimate-new-line">
              <td className="col-row" title="Nouvelle ligne">+</td>
              {COLUMNS.map((column, columnIndex) => (
                <td key={column.field} className={column.className}>
                  <input
                    data-row={items.length}
                    data-col={columnIndex}
                    value={draft[column.field]}
                    placeholder={column.field === "designation" ? "Nouvelle ligne : saisir ou coller depuis Excel" : undefined}
                    aria-label={`${column.label}, nouvelle ligne`}
                    inputMode={column.field === "quantity" || column.field === "price" ? "decimal" : undefined}
                    onChange={(event) => setDraft((current) => ({ ...current, [column.field]: event.target.value }))}
                    onBlur={leaveDraft}
                    onKeyDown={(event) => onKeyDown(event, items.length, columnIndex)}
                    onPaste={(event) => onPaste(event, items.length, columnIndex)}
                  />
                </td>
              ))}
              <td className="col-number" />
              <td className="col-actions" />
            </tr>
          )}
        </tbody>
        <tfoot>
          {totals.map(([label, value, strong], index) => (
            <tr key={String(label)} className={strong ? "total-strong" : undefined}>
              <td colSpan={COLUMNS.length + 1} className="col-total-label">
                {label}
              </td>
              <td className="col-number">{formatMoney(value)}</td>
              <td className="col-actions muted">{index === 0 && saving > 0 ? "Enregistrement…" : ""}</td>
            </tr>
          ))}
        </tfoot>
      </table>
      {error && <p className="banner error">{error}</p>}
      {editable && (
        <p className="hint">
          Tapez directement dans les cases · Entrée = ligne suivante · Copier-coller depuis Excel : colonnes N°, Désignation,
          Quantité, Unité, PU HT.
        </p>
      )}
    </>
  );
}
