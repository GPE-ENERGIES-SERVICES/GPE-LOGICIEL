import type { Page } from "./common";
import type { MarketType } from "./tender";

/** Result of an award notice: who won a contract or one of its lots, for how much. */
export type AwardedContract = {
  id: number;
  /** The consultation, when it was collected. */
  tenderId: number | null;
  title: string;
  buyer: string;
  location: string | null;
  postalCode: string | null;
  department: string | null;
  marketType: MarketType;
  /** Activity codes, see ACTIVITY_LABELS. */
  activities: string[];
  /** Number of the lot, null when the consultation is not divided. */
  lotNumber: string | null;
  /** "Lot 3 – Chauffage", null when the consultation is not divided. */
  lot: string | null;
  winner: string;
  winnerCity: string | null;
  amount: number | null;
  awardedAt: string;
  offersReceived: number | null;
  /** NOTICE: read in the award notice; MANUAL: confirmed by a user. */
  source?: "NOTICE" | "MANUAL";
  /** Won by GPE; otherwise by another company. */
  won?: boolean;
};

/** The result of a tender recorded by a user, for the whole contract or one lot. */
export type AwardRequest = {
  won: boolean;
  winner: string;
  amount: number | null;
  awardedAt: string;
  source: "NOTICE" | "MANUAL";
  lotNumber: string | null;
  reason: string | null;
};

/** A page of awarded contracts, with figures on all the contracts found. */
export type AwardSearch = Page<AwardedContract> & {
  /** Distinct winners. */
  winnerCount: number;
  /** Sum of the known amounts. */
  totalAmount: number;
};
