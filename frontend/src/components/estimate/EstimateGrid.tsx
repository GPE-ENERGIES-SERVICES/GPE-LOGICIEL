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

import { formatMoney, formatNumber, formatRate, numberInput, parseNumber, textOrNull } from "@/lib/estimate";
import {
  addComponent,
  addItem,
  deleteItem,
  reorderItems,
  saveItems,
  updateComponent,
  updateItem,
} from "@/services/estimateApi";
import type {
  ComponentInput,
  Estimate,
  EstimateComponent,
  EstimateItem,
  ItemInput,
  PriceLevel,
  PricingRate,
  Suggestion,
} from "@/types/estimate";

import { ComponentsPanel } from "./ComponentsPanel";
import { ItemForm } from "./ItemForm";
import { Icon } from "@/components/Icon";

type Field = "reference" | "designation" | "quantity" | "unit" | "price";

/** The cells of the split of a line: the supplies (every cost but labour) and the labour, for one unit. */
type SplitField = "supplyUnitCost" | "supplyCoefficient" | "laborHours" | "laborCoefficient" | "laborRate";

type Computed = "supplyAmount" | "laborTotal" | "laborAmount" | "amount" | "vat";

type Column =
  | { kind: "item"; field: Field; label: string; className: string }
  | { kind: "split"; field: SplitField; label: string; className: string }
  | { kind: "computed"; field: Computed; label: string; className: string };

/** The columns of a costing sheet: the line, its sale price, then the supplies and the labour. */
const COLUMNS: Column[] = [
  { kind: "item", field: "reference", label: "Réf.", className: "col-ref" },
  { kind: "item", field: "designation", label: "Désignation", className: "col-designation" },
  { kind: "item", field: "quantity", label: "Qté", className: "col-number" },
  { kind: "item", field: "unit", label: "Unité", className: "col-unit" },
  { kind: "item", field: "price", label: "PV unit.", className: "col-number col-sale" },
  { kind: "split", field: "supplyUnitCost", label: "Prix unitaire", className: "col-number col-supply" },
  { kind: "split", field: "supplyCoefficient", label: "Coef Mat.", className: "col-number col-supply" },
  { kind: "computed", field: "supplyAmount", label: "Total Mat.", className: "col-number col-supply" },
  { kind: "split", field: "laborHours", label: "M.O. unit.", className: "col-number col-labor" },
  { kind: "split", field: "laborCoefficient", label: "Coef M.O.", className: "col-number col-labor" },
  { kind: "split", field: "laborRate", label: "Taux M.O.", className: "col-number col-labor" },
  { kind: "computed", field: "laborTotal", label: "M.O. total", className: "col-number col-labor" },
  { kind: "computed", field: "laborAmount", label: "Total M.O. HT", className: "col-number col-labor" },
  { kind: "computed", field: "amount", label: "Total", className: "col-number col-total" },
  { kind: "computed", field: "vat", label: "TVA", className: "col-number" },
];

/** The columns a new line is typed into: those of a price schedule. */
const ITEM_COLUMNS = COLUMNS.filter((column): column is Extract<Column, { kind: "item" }> => column.kind === "item");

const LEVEL_LABELS: Record<PriceLevel, string> = {
  GREEN: "Prix sûr (tarif fournisseur, taux GPE ou validé)",
  ORANGE: "Prix estimé, ancien ou à vérifier",
  RED: "Sans prix",
};

const isLabor = (component: EstimateComponent) => component.type === "LABOR";

/** The single cost of a family, the one a cell of the split changes; undefined when there are several. */
function single(item: EstimateItem, labor: boolean): EstimateComponent | null | undefined {
  const costs = item.components.filter((component) => isLabor(component) === labor);
  return costs.length === 0 ? null : costs.length === 1 ? costs[0] : undefined;
}

function splitShown(item: EstimateItem, field: SplitField): string {
  return numberInput(item[field]);
}

function componentInput(component: EstimateComponent): ComponentInput {
  return {
    type: component.type,
    label: component.label,
    quantity: component.quantity,
    unit: component.unit,
    unitCost: component.unitCost,
    wasteRate: component.wasteRate,
    coefficient: component.coefficient,
    supplierName: component.supplierName,
    supplierRef: component.supplierRef,
    manufacturerRef: component.manufacturerRef,
    costDate: component.costDate,
    rateCode: component.rateCode,
    offerId: component.offerId,
  };
}

const EMPTY_COMPONENT: Omit<ComponentInput, "type" | "label"> = {
  quantity: 1,
  unit: null,
  unitCost: null,
  wasteRate: null,
  coefficient: null,
  supplierName: null,
  supplierRef: null,
  manufacturerRef: null,
  costDate: null,
  rateCode: null,
  offerId: null,
};

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
  // A second scroll bar above the grid, moved with the one below: the sheet is wider than the screen.
  const topBar = useRef<HTMLDivElement>(null);
  const scroller = useRef<HTMLDivElement>(null);
  const [gridWidth, setGridWidth] = useState(0);

  useEffect(() => {
    const grid = table.current;
    if (!grid) {
      return;
    }
    const observer = new ResizeObserver(() => setGridWidth(grid.scrollWidth));
    observer.observe(grid);
    return () => observer.disconnect();
  }, []);

  const follow = (from: HTMLDivElement | null, to: HTMLDivElement | null) => {
    if (from && to && to.scrollLeft !== from.scrollLeft) {
      to.scrollLeft = from.scrollLeft;
    }
  };
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
        input.value =
          input.dataset.kind === "split"
            ? splitShown(item, input.dataset.field as SplitField)
            : shown(item, input.dataset.field as Field);
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

  /**
   * A cell of the split typed into: it changes the single cost of its family,
   * or adds it (« Matériel », or labour at the first rate of GPE), and the line
   * is then priced from its costs, as in a costing sheet.
   */
  const commitSplit = (item: EstimateItem, field: SplitField, input: HTMLInputElement) => {
    if (input.value.trim() === splitShown(item, field).trim()) {
      return;
    }
    const value = parseNumber(input.value);
    const restore = (message: string) => {
      setError(message);
      input.value = splitShown(item, field);
    };
    if (value === null || Number.isNaN(value) || value < 0) {
      restore(`Valeur illisible : « ${input.value} ».`);
      return;
    }
    const labor = field.startsWith("labor");
    const component = single(item, labor);
    if (component === undefined) {
      restore("Plusieurs coûts sur cette ligne : modifiez-les dans « Détail ».");
      return;
    }
    let change: () => Promise<Estimate>;
    if (component) {
      const next = componentInput(component);
      const waste = 1 + (component.wasteRate ?? 0);
      if (field === "supplyUnitCost") {
        next.unitCost = component.quantity > 0 ? value / (component.quantity * waste) : value;
      } else if (field === "laborHours") {
        next.quantity = value / waste;
      } else if (field === "laborRate") {
        next.unitCost = value;
        next.rateCode = null;
      } else {
        next.coefficient = value;
      }
      change = () => updateComponent(component.id, next);
    } else if (field === "supplyUnitCost") {
      change = () => addComponent(item.id, { ...EMPTY_COMPONENT, type: "SUPPLY", label: "Matériel", unitCost: value });
    } else if (field === "laborHours") {
      const rate = rates.find((candidate) => candidate.active && candidate.type === "LABOR");
      change = () =>
        addComponent(item.id, {
          ...EMPTY_COMPONENT,
          type: "LABOR",
          label: rate?.label ?? "Main-d'œuvre",
          quantity: value,
          unit: "h",
          unitCost: rate?.cost ?? null,
          rateCode: rate?.code ?? null,
        });
    } else {
      restore(labor ? "Saisissez d'abord le temps unitaire (M.O. unit.)." : "Saisissez d'abord le prix unitaire.");
      return;
    }
    enqueue(async () => {
      const changed = await change();
      return item.priceMode === "COMPOSED" ? changed : updateItem(item.id, { ...inputOf(item), priceMode: "COMPOSED" });
    });
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
      const target = COLUMNS[column];
      event.currentTarget.value =
        target.kind === "item" ? shown(item, target.field) : target.kind === "split" ? splitShown(item, target.field) : "";
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
          const target = ITEM_COLUMNS[column + index];
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

  const columnCount = COLUMNS.length + 2;
  const totals: [string, number, boolean][] = [
    ["Total HT", estimate.totalHt, true],
    [`TVA ${formatRate(estimate.vatRate)}`, estimate.totalVat, false],
    ["Total TTC", estimate.totalTtc, true],
  ];
  if (estimate.optionsHt !== 0) {
    totals.push(["Options HT (hors total)", estimate.optionsHt, false]);
  }

  /** A computed cell: the backend gives every figure, the screen only shows them. */
  const computed = (item: EstimateItem, field: Computed): string => {
    switch (field) {
      case "supplyAmount":
        return item.supplyAmount === null ? "" : formatMoney(item.supplyAmount);
      case "laborTotal":
        return item.laborHours === null || item.quantity === null
          ? ""
          : `${formatNumber(Math.round(item.laborHours * item.quantity * 100) / 100)} h`;
      case "laborAmount":
        return item.laborAmount === null ? "" : formatMoney(item.laborAmount);
      case "amount":
        return item.amount === null ? (item.priceMode === "TEXT" ? item.textPrice ?? "" : "—") : formatMoney(item.amount);
      case "vat":
        return formatRate(estimate.vatRate);
    }
  };

  const cell = (item: EstimateItem, column: Column, index: number, columnIndex: number) => {
    if (column.kind === "computed") {
      return (
        <td key={column.field} className={column.field === "amount" && item.amount === null && item.priceMode !== "TEXT" ? `${column.className} missing` : column.className}>
          {computed(item, column.field)}
        </td>
      );
    }
    const split = column.kind === "split";
    // A family with several costs is changed in « Détail »: its cell is only shown.
    if (split && single(item, column.field.startsWith("labor")) === undefined) {
      return (
        <td key={column.field} className={column.className} title="Plusieurs coûts : voir « Détail »">
          {formatNumber(item[column.field])}
        </td>
      );
    }
    const value = split ? splitShown(item, column.field) : shown(item, column.field);
    // Hours without a rate: the line has no price until the rate is typed.
    const rateMissing = column.field === "laborRate" && item.laborHours !== null && item.laborRate === null;
    return (
      <td key={column.field} className={rateMissing ? `${column.className} missing` : column.className}>
        <input
          data-item={item.id}
          data-kind={column.kind}
          data-field={column.field}
          data-row={index}
          data-col={columnIndex}
          defaultValue={value}
          placeholder={rateMissing ? "à saisir" : undefined}
          readOnly={!editable}
          aria-label={`${column.label}, ligne ${index + 1}`}
          title={
            column.field === "price" && item.priceMode === "COMPOSED"
              ? "Calculé depuis le matériel et la M.O. : saisir un prix le remplace"
              : undefined
          }
          className={column.field === "price" && item.priceMode === "COMPOSED" ? "computed" : undefined}
          inputMode={column.field === "designation" || column.field === "reference" || column.field === "unit" ? undefined : "decimal"}
          onBlur={(event) =>
            editable &&
            (column.kind === "split"
              ? commitSplit(item, column.field, event.currentTarget)
              : commit(item, column.field, event.currentTarget))
          }
          onKeyDown={(event) => onKeyDown(event, index, columnIndex, item)}
          onPaste={(event) => editable && column.kind === "item" && onPaste(event, index, columnIndex)}
        />
      </td>
    );
  };

  return (
    <>
      <p className="estimate-legend">
        {(Object.keys(LEVEL_LABELS) as PriceLevel[]).map((level) => (
          <span key={level}>
            <span className={`level-dot level-${level.toLowerCase()}`} aria-hidden="true" /> {LEVEL_LABELS[level]}
          </span>
        ))}
      </p>
      <div className="estimate-scroll-top" ref={topBar} onScroll={() => follow(topBar.current, scroller.current)} aria-hidden="true">
        <div style={{ width: gridWidth }} />
      </div>
      <div className="estimate-scroll" ref={scroller} onScroll={() => follow(scroller.current, topBar.current)}>
      <table className="estimate-grid" ref={table}>
        <thead>
          <tr>
            <th className="col-row" aria-label="Ligne" />
            {COLUMNS.map((column) => (
              <th key={column.field} className={column.className}>
                {column.label}
              </th>
            ))}
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
                <tr className={`level-${item.priceLevel.toLowerCase()}${item.option ? " row-muted" : ""}`}>
                  <td className="col-row" title={LEVEL_LABELS[item.priceLevel]}>
                    {index + 1}
                  </td>
                  {COLUMNS.map((column, columnIndex) => cell(item, column, index, columnIndex))}
                  <td className="col-actions">
                    {item.option && <span className="badge">Option</span>}
                    {editable && item.toCheck && item.amount !== null && (
                      <button
                        type="button"
                        className="secondary level-validate"
                        title="Prix vérifié : la ligne passe au vert"
                        onClick={() => enqueue(() => updateItem(item.id, { ...inputOf(item), toCheck: false }))}
                      >
                        ✓ Valider
                      </button>
                    )}
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => toggle(item.id)}
                      aria-expanded={open.has(item.id)}
                      title="Options et détail (fournitures, main-d'œuvre…)"
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
                      <h4>Détail du poste</h4>
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
              {COLUMNS.map((column, columnIndex) =>
                column.kind === "item" ? (
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
                ) : (
                  <td key={column.field} className={column.className} />
                ),
              )}
              <td className="col-actions" />
            </tr>
          )}
        </tbody>
        <tfoot>
          {totals.map(([label, value, strong], index) => (
            <tr key={String(label)} className={strong ? "total-strong" : undefined}>
              <td colSpan={COLUMNS.length - 1} className="col-total-label">
                {label}
              </td>
              <td className="col-number">{formatMoney(value)}</td>
              <td className="col-number" />
              <td className="col-actions muted">{index === 0 && saving > 0 ? "Enregistrement…" : ""}</td>
            </tr>
          ))}
        </tfoot>
      </table>
      </div>
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
