/** A point read in a piece of the DCE, with where it was found ("CCAP art. 10.2", "RC p. 4"). */
export type ReadPoint = {
  label: string;
  text: string;
  source: string | null;
  /** For a point of vigilance: how much it weighs on the price or the risk. */
  level?: "HIGH" | "MEDIUM" | "LOW";
};

export type ReadPart = { title: string; points: ReadPoint[] };

/** The RC and the CCAP read as a costing reader does: how to answer, then what the contract costs. */
export type RcCcapAnalysis = {
  example: boolean;
  rc: ReadPart[];
  ccap: ReadPart[];
};

export type EquipmentLine = {
  designation: string;
  /** Brand, reference or "ou équivalent" required by the CCTP. */
  requirement: string | null;
  quantity: string | null;
  source: string | null;
};

export type PowerLine = { item: string; power: string; note: string | null; source: string | null };

/** The CCTP read for the costing, in the order a costing reader works. */
export type CctpAnalysis = {
  example: boolean;
  equipment: EquipmentLine[];
  choices: ReadPoint[];
  power: PowerLine[];
  totalPower: string | null;
  site: ReadPoint[];
  vigilance: ReadPoint[];
};
