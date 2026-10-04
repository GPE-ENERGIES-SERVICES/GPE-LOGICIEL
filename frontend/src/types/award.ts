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
};

/** A page of awarded contracts, with figures on all the contracts found. */
export type AwardSearch = Page<AwardedContract> & {
  /** Distinct winners. */
  winnerCount: number;
  /** Sum of the known amounts. */
  totalAmount: number;
};
