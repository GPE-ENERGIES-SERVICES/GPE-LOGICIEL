/** Where a dated item stands: no end, valid, ending within the warning days, expired. */
export type Validity = "NONE" | "VALID" | "EXPIRING" | "EXPIRED";

export type CompanyFile = {
  id: number;
  fileName: string;
  sizeBytes: number;
  uploadedBy: string | null;
  uploadedAt: string;
};

/**
 * A line of a list of the company, as stored: its own fields, plus its
 * validity when it ends and its files when its list has some.
 */
export type CompanyRow = {
  id: number;
  updatedBy: string | null;
  updatedAt: string;
  validity: Validity | null;
  /** The last uploaded first; null when the list has no file. */
  files: CompanyFile[] | null;
  [field: string]: unknown;
};

export type CompanyProfile = {
  legalName: string | null;
  tradeName: string | null;
  legalForm: string | null;
  siren: string | null;
  siret: string | null;
  vatNumber: string | null;
  apeCode: string | null;
  rcs: string | null;
  shareCapital: number | null;
  addressLine: string | null;
  postalCode: string | null;
  city: string | null;
  country: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  interventionDepartments: string[];
  interventionNote: string | null;
  iban: string | null;
  bic: string | null;
  signatoryName: string | null;
  signatoryTitle: string | null;
  updatedBy: string | null;
  updatedAt: string;
  logo: CompanyFile | null;
};

export type Expiry = {
  kind: "DOCUMENT" | "CERTIFICATION" | "CREDENTIAL" | "EQUIPMENT";
  id: number;
  title: string;
  detail: string | null;
  endsOn: string;
  validity: Validity;
};

export type CompanyOverview = {
  counts: Record<string, number>;
  expiries: Expiry[];
  warningDays: number;
  documentsWithoutFile: number;
};
