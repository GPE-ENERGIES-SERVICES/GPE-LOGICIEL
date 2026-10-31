/** Kind of cost of an item, each with its own sale coefficient. */
export type ComponentType =
  | "SUPPLY"
  | "LABOR"
  | "SERVICE"
  | "TRAVEL"
  | "RENTAL"
  | "SUBCONTRACT"
  | "LUMP_SUM"
  | "OTHER";

/** COMPOSED: price from the costs; FIXED: price entered; TEXT: PM, inclus... */
export type PriceMode = "COMPOSED" | "FIXED" | "TEXT";

export type EstimateStatus = "DRAFT" | "VALIDATED" | "SUPERSEDED";

export type EstimateComponent = {
  id: number;
  position: number;
  type: ComponentType;
  label: string;
  /** Quantity for one unit of the item. */
  quantity: number;
  unit: string | null;
  unitCost: number | null;
  wasteRate: number;
  /** Its own coefficient, or null for that of the estimate. */
  coefficient: number | null;
  supplierName: string | null;
  supplierRef: string | null;
  manufacturerRef: string | null;
  costDate: string | null;
  /** The rate of GPE its cost was copied from. */
  rateCode: string | null;
  /** The offer of a supplier its cost was copied from. */
  offerId: number | null;
};

export type EstimateItem = {
  id: number;
  position: number;
  chapter: string | null;
  reference: string | null;
  designation: string;
  unit: string | null;
  quantity: number | null;
  priceMode: PriceMode;
  fixedUnitPrice: number | null;
  textPrice: string | null;
  option: boolean;
  /** Computed by the backend. */
  unitCost: number | null;
  unitPrice: number | null;
  amount: number | null;
  sourceDocumentId: number | null;
  sourceSheet: string | null;
  sourceRow: number | null;
  toCheck: boolean;
  components: EstimateComponent[];
};

/** Every figure is computed by the backend; the screens compute none. */
export type Estimate = {
  id: number;
  tenderId: number;
  lotNumber: string | null;
  version: number;
  status: EstimateStatus;
  label: string | null;
  vatRate: number;
  coefficients: Record<ComponentType, number>;
  totalCost: number | null;
  totalHt: number;
  totalVat: number;
  totalTtc: number;
  optionsHt: number;
  margin: number | null;
  incompleteItems: number;
  itemsToCheck: number;
  createdBy: string | null;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
  validatedBy: string | null;
  validatedAt: string | null;
  items: EstimateItem[];
};

export type EstimateSummary = Pick<
  Estimate,
  "id" | "lotNumber" | "version" | "status" | "label" | "totalHt" | "totalTtc" | "incompleteItems" | "updatedBy" | "updatedAt"
>;

export type EstimateInput = {
  lotNumber?: string | null;
  label: string | null;
  vatRate: number | null;
  coefficients: Partial<Record<ComponentType, number>>;
};

export type ItemInput = {
  chapter: string | null;
  reference: string | null;
  designation: string;
  unit: string | null;
  quantity: number | null;
  priceMode: PriceMode;
  fixedUnitPrice: number | null;
  textPrice: string | null;
  option: boolean;
  toCheck: boolean;
};

export type ComponentInput = {
  type: ComponentType;
  label: string;
  quantity: number;
  unit: string | null;
  unitCost: number | null;
  wasteRate: number | null;
  coefficient: number | null;
  supplierName: string | null;
  supplierRef: string | null;
  manufacturerRef: string | null;
  costDate: string | null;
  rateCode: string | null;
  offerId: number | null;
};

export type EstimateEvent = {
  id: number;
  kind: "CREATED" | "NEW_VERSION" | "IMPORTED" | "RATES_COPIED" | "VALIDATED" | "SUPERSEDED" | "SUGGESTION_ACCEPTED";
  detail: string | null;
  createdBy: string | null;
  createdAt: string;
};

export type PricingSettings = {
  vatRate: number;
  coefficients: Record<ComponentType, number>;
  updatedBy: string | null;
  updatedAt: string;
};

export type PricingRate = {
  id: number;
  code: string;
  label: string;
  type: ComponentType;
  unit: string;
  cost: number;
  active: boolean;
  updatedBy: string | null;
  updatedAt: string;
};

export type RateInput = Pick<PricingRate, "code" | "label" | "type" | "unit" | "cost" | "active">;

export type Supplier = {
  id: number;
  code: string;
  name: string;
  offers: number;
  lastImport: string | null;
};

export type SupplierOffer = {
  id: number;
  supplierId: number;
  supplierName: string;
  supplierRef: string;
  manufacturerRef: string | null;
  ean: string | null;
  designation: string;
  purchaseUnit: string | null;
  /** Units sold for the price (100 for a reel of 100 m). */
  packQuantity: number;
  listPrice: number | null;
  discountRate: number | null;
  netPrice: number;
  validFrom: string | null;
  validUntil: string | null;
  importedAt: string;
};

export type OfferImport = {
  imported: number;
  rejected: number;
  columns: Record<string, string>;
  warnings: string[];
};

export type Suggestion = {
  id: number;
  itemId: number | null;
  kind: "SUPPLY" | "CHECK";
  offerId: number | null;
  quantity: number | null;
  text: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  model: string | null;
  createdAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
};

export type AssistantAvailability = { available: boolean; message: string };

/** An estimate of any tender, in the list of all estimates. */
export type ListedEstimate = { tenderId: number; tenderTitle: string | null; estimate: EstimateSummary };
