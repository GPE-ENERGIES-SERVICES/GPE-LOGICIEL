/**
 * Demo data used while the backend does not provide every field. Generated
 * with a fixed seed: the same tenders on every load, with dates relative to
 * today so that deadlines stay current.
 */

import type {
  CurrentUser,
  DocumentInfo,
  Dossier,
  FollowUpStatus,
  MarketType,
  PieceType,
  ProcedureType,
  TenderDetail,
  TenderStatus,
} from "../types";

export const MOCK_USER: CurrentUser = {
  id: 1,
  email: "demo@gpefrance.eu",
  displayName: "Compte de démonstration",
  role: "ADMIN",
};

const TENDER_COUNT = 120;

let seed = 20261004;

function random(): number {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

function pick<T>(items: readonly T[]): T {
  return items[Math.floor(random() * items.length)];
}

function between(min: number, max: number): number {
  return min + Math.floor(random() * (max - min + 1));
}

function chance(probability: number): boolean {
  return random() < probability;
}

/** Places with their road distance from Marseille, the reference point of the demo data. */
const PLACES = [
  { city: "Marseille", department: "13", distanceKm: 0 },
  { city: "Marseille", department: "13", distanceKm: 0 },
  { city: "Aix-en-Provence", department: "13", distanceKm: 31 },
  { city: "Aubagne", department: "13", distanceKm: 17 },
  { city: "La Ciotat", department: "13", distanceKm: 32 },
  { city: "Martigues", department: "13", distanceKm: 40 },
  { city: "Vitrolles", department: "13", distanceKm: 26 },
  { city: "Marignane", department: "13", distanceKm: 27 },
  { city: "Istres", department: "13", distanceKm: 55 },
  { city: "Salon-de-Provence", department: "13", distanceKm: 53 },
  { city: "Arles", department: "13", distanceKm: 92 },
  { city: "Gardanne", department: "13", distanceKm: 26 },
  { city: "Toulon", department: "83", distanceKm: 65 },
  { city: "La Seyne-sur-Mer", department: "83", distanceKm: 62 },
  { city: "Hyères", department: "83", distanceKm: 84 },
  { city: "Fréjus", department: "83", distanceKm: 136 },
  { city: "Draguignan", department: "83", distanceKm: 121 },
  { city: "Brignoles", department: "83", distanceKm: 64 },
  { city: "Avignon", department: "84", distanceKm: 100 },
  { city: "Carpentras", department: "84", distanceKm: 108 },
  { city: "Cavaillon", department: "84", distanceKm: 75 },
  { city: "Pertuis", department: "84", distanceKm: 48 },
  { city: "Nice", department: "06", distanceKm: 200 },
  { city: "Cannes", department: "06", distanceKm: 164 },
  { city: "Antibes", department: "06", distanceKm: 177 },
  { city: "Manosque", department: "04", distanceKm: 87 },
  { city: "Digne-les-Bains", department: "04", distanceKm: 144 },
  { city: "Gap", department: "05", distanceKm: 182 },
  { city: "Nîmes", department: "30", distanceKm: 123 },
  { city: "Montpellier", department: "34", distanceKm: 170 },
  { city: "Lyon", department: "69", distanceKm: 315 },
  { city: "Grenoble", department: "38", distanceKm: 275 },
];

const COMPANY_DEPARTMENTS = ["04", "05", "06", "13", "83", "84"];

const DEPARTMENT_BUYERS: Record<string, string[]> = {
  "13": [
    "Métropole Aix-Marseille-Provence",
    "Département des Bouches-du-Rhône",
    "13 Habitat",
    "Logirem",
    "Assistance Publique - Hôpitaux de Marseille",
    "SDIS des Bouches-du-Rhône",
    "Aix-Marseille Université",
    "Grand Port Maritime de Marseille",
    "Syndicat mixte d'énergie du département des Bouches-du-Rhône",
  ],
  "83": [
    "Département du Var",
    "Métropole Toulon Provence Méditerranée",
    "Var Habitat",
    "Centre hospitalier intercommunal Toulon La Seyne-sur-Mer",
  ],
  "84": ["Département de Vaucluse", "Communauté d'agglomération du Grand Avignon", "Grand Delta Habitat"],
  "06": ["Métropole Nice Côte d'Azur", "Département des Alpes-Maritimes", "Côte d'Azur Habitat"],
  "04": ["Département des Alpes-de-Haute-Provence", "Durance Luberon Verdon Agglomération"],
  "05": ["Département des Hautes-Alpes"],
  "30": ["Nîmes Métropole", "Département du Gard"],
  "34": ["Montpellier Méditerranée Métropole", "Département de l'Hérault"],
  "69": ["Métropole de Lyon", "Hospices Civils de Lyon"],
  "38": ["Grenoble-Alpes Métropole"],
};

const SITES = [
  "du groupe scolaire Jean Jaurès",
  "de l'hôtel de ville",
  "du gymnase municipal",
  "de la médiathèque",
  "du centre technique municipal",
  "de l'école maternelle Les Oliviers",
  "du collège Marcel Pagnol",
  "du lycée Saint-Exupéry",
  "de la piscine intercommunale",
  "du stade Louis Brun",
  "de la crèche Les Calanques",
  "de l'EHPAD Les Cigales",
  "du centre de secours",
  "du parc des expositions",
];

type Activity = {
  code: string;
  weight: number;
  marketType: MarketType;
  titles: string[];
  description: string;
  cpv: string[];
  lots: string[];
  amount: [number, number];
  qualifications: string[];
  related: string[];
};

const ACTIVITIES: Activity[] = [
  {
    code: "ELECTRICITY",
    weight: 4,
    marketType: "WORKS",
    titles: [
      "Rénovation des installations électriques {site}",
      "Mise en conformité électrique des bâtiments communaux de {city}",
      "Remplacement du TGBT et des tableaux divisionnaires {site}",
      "Travaux de courants forts et courants faibles {site}",
    ],
    description:
      "Les travaux comprennent la dépose des installations existantes, la fourniture et la pose des tableaux " +
      "électriques, la reprise des circuits d'éclairage et de prises de courant, ainsi que les courants faibles " +
      "(SSI, contrôle d'accès, VDI). Les travaux sont réalisés en site occupé.",
    cpv: ["45310000-3", "45311000-0", "45314000-1"],
    lots: ["Courants forts", "Courants faibles", "Éclairage intérieur", "Système de sécurité incendie"],
    amount: [40_000, 900_000],
    qualifications: ["Qualifelec électricité générale", "Habilitations électriques B2V / BR"],
    related: ["PUBLIC_LIGHTING"],
  },
  {
    code: "PHOTOVOLTAIC",
    weight: 4,
    marketType: "WORKS",
    titles: [
      "Fourniture et pose d'une centrale photovoltaïque en toiture {site}",
      "Création d'ombrières photovoltaïques sur les parkings de {city}",
      "Installation de centrales photovoltaïques en autoconsommation sur 6 bâtiments communaux",
      "Solarisation des toitures {site}",
    ],
    description:
      "Conception, fourniture, pose et mise en service d'installations photovoltaïques en autoconsommation " +
      "collective, y compris le raccordement au réseau public de distribution, la supervision et la maintenance " +
      "pendant deux ans.",
    cpv: ["09331200-0", "45261215-4", "45311200-2"],
    lots: ["Centrale en toiture", "Ombrières de parking", "Raccordement et supervision", "Maintenance"],
    amount: [80_000, 2_500_000],
    qualifications: ["QualiPV Elec", "Qualifelec SPV", "Assurance décennale couvrant le photovoltaïque"],
    related: ["ELECTRICITY"],
  },
  {
    code: "EV_CHARGING",
    weight: 3,
    marketType: "WORKS",
    titles: [
      "Déploiement d'infrastructures de recharge pour véhicules électriques à {city}",
      "Fourniture, installation et exploitation de bornes de recharge sur l'espace public",
      "Installation de bornes de recharge pour la flotte de véhicules {site}",
    ],
    description:
      "Fourniture, pose, raccordement et mise en service de points de recharge (22 kW et charge rapide 50 kW), " +
      "génie civil associé, supervision et maintenance des équipements.",
    cpv: ["31158100-9", "45315000-8", "45232200-4"],
    lots: ["Bornes de recharge normale", "Bornes de recharge rapide", "Génie civil et raccordement", "Exploitation"],
    amount: [50_000, 1_500_000],
    qualifications: ["Qualifelec IRVE mention P2", "Habilitations électriques"],
    related: ["ELECTRICITY"],
  },
  {
    code: "HVAC",
    weight: 3,
    marketType: "WORKS",
    titles: [
      "Remplacement de la chaufferie {site}",
      "Installation de pompes à chaleur air/eau {site}",
      "Rénovation des installations de chauffage, ventilation et climatisation {site}",
      "Remplacement des groupes froids {site}",
    ],
    description:
      "Dépose des équipements existants, fourniture et pose de pompes à chaleur ou de chaudières à condensation, " +
      "reprise des réseaux hydrauliques, de la ventilation double flux et de la régulation, mise en service et " +
      "formation des exploitants.",
    cpv: ["45331000-6", "45331100-7", "45331210-1"],
    lots: ["Chauffage", "Ventilation", "Climatisation", "Régulation et GTB", "Plomberie"],
    amount: [60_000, 1_800_000],
    qualifications: ["Qualibat 5313", "Attestation de capacité fluides frigorigènes", "RGE"],
    related: ["ENERGY_RENOVATION"],
  },
  {
    code: "ENERGY_RENOVATION",
    weight: 3,
    marketType: "WORKS",
    titles: [
      "Rénovation énergétique {site}",
      "Travaux d'amélioration de la performance énergétique {site}",
      "Isolation thermique par l'extérieur et remplacement des menuiseries {site}",
      "Réhabilitation thermique de 48 logements à {city}",
    ],
    description:
      "Isolation thermique par l'extérieur, remplacement des menuiseries, isolation des combles, ventilation " +
      "performante et remplacement des équipements de chauffage, avec un objectif de réduction des " +
      "consommations de 40 % (décret tertiaire).",
    cpv: ["45453000-7", "45321000-3", "45421000-4"],
    lots: [
      "Isolation thermique par l'extérieur",
      "Menuiseries extérieures",
      "Chauffage et ventilation",
      "Électricité",
      "Plâtrerie et faux plafonds",
      "Étanchéité",
    ],
    amount: [150_000, 4_500_000],
    qualifications: ["RGE", "Qualibat 7131"],
    related: ["HVAC", "ELECTRICITY"],
  },
  {
    code: "PUBLIC_LIGHTING",
    weight: 2,
    marketType: "WORKS",
    titles: [
      "Rénovation de l'éclairage public en LED à {city}",
      "Travaux d'éclairage public et de mise en lumière du centre-ville de {city}",
      "Travaux d'éclairage public et de signalisation lumineuse tricolore",
    ],
    description:
      "Remplacement des luminaires par des luminaires LED avec abaissement de puissance, reprise des armoires de " +
      "commande, remplacement des mâts vétustes et mise à jour de la cartographie du patrimoine.",
    cpv: ["45316110-9", "34993000-4", "45316000-5"],
    lots: ["Travaux neufs", "Rénovation LED", "Signalisation lumineuse tricolore"],
    amount: [50_000, 2_000_000],
    qualifications: ["Qualifelec éclairage public", "AIPR opérateur"],
    related: ["ELECTRICITY"],
  },
  {
    code: "VRD",
    weight: 2,
    marketType: "WORKS",
    titles: [
      "Aménagement de voirie et réseaux divers de la zone d'activités de {city}",
      "Travaux de VRD pour l'aménagement du parking {site}",
      "Requalification de l'avenue de la République à {city}",
    ],
    description:
      "Terrassements, réseaux humides et secs (assainissement, eau potable, fourreaux électriques et télécom), " +
      "voirie, bordures, revêtements et signalisation.",
    cpv: ["45233140-2", "45232400-6", "45231300-8"],
    lots: ["Terrassements et voirie", "Réseaux humides", "Réseaux secs", "Espaces verts"],
    amount: [100_000, 6_500_000],
    qualifications: ["AIPR encadrant", "FNTP 3"],
    related: ["PUBLIC_LIGHTING"],
  },
  {
    code: "MAINTENANCE",
    weight: 2,
    marketType: "SERVICES",
    titles: [
      "Maintenance préventive et corrective des installations électriques et CVC des bâtiments de {city}",
      "Entretien et maintenance des installations photovoltaïques",
      "Maintenance des bornes de recharge pour véhicules électriques",
      "Accord-cadre de maintenance des installations de chauffage {site}",
    ],
    description:
      "Visites de maintenance préventive, dépannages avec délai d'intervention garanti, astreinte 24 h/24, tenue " +
      "du carnet d'entretien et rapport annuel.",
    cpv: ["50700000-2", "50711000-2", "50720000-8"],
    lots: ["Électricité", "Chauffage et climatisation", "Photovoltaïque", "Bornes de recharge"],
    amount: [30_000, 800_000],
    qualifications: ["Habilitations électriques", "Attestation de capacité fluides frigorigènes"],
    related: ["HVAC", "ELECTRICITY"],
  },
  {
    code: "STUDIES",
    weight: 2,
    marketType: "SERVICES",
    titles: [
      "Mission de maîtrise d'œuvre pour la rénovation énergétique {site}",
      "Étude de faisabilité pour l'installation de centrales photovoltaïques sur le patrimoine de {city}",
      "Audit énergétique des bâtiments communaux de {city}",
      "Assistance à maîtrise d'ouvrage pour le schéma directeur des bornes de recharge",
    ],
    description:
      "Diagnostic de l'existant, relevés, scénarios de travaux chiffrés avec les gains énergétiques attendus, aide " +
      "au choix du scénario et, le cas échéant, maîtrise d'œuvre de conception et de suivi des travaux.",
    cpv: ["71321000-4", "71314300-5", "71241000-9"],
    lots: ["Audit énergétique", "Étude photovoltaïque", "Maîtrise d'œuvre"],
    amount: [15_000, 400_000],
    qualifications: ["OPQIBI 1905", "Assurance responsabilité civile professionnelle"],
    related: ["ENERGY_RENOVATION"],
  },
];

const PLATFORMS = [
  "https://www.marches-securises.fr",
  "https://marchespublics.ampmetropole.fr",
  "https://www.achatpublic.com",
  "https://www.marches-publics.info",
  "https://www.marches-publics.gouv.fr",
  "https://marches.maregionsud.fr",
];

const SELECTION_CRITERIA = [
  "Prix 60 %, valeur technique 40 %",
  "Valeur technique 50 %, prix 40 %, délais 10 %",
  "Prix 40 %, valeur technique 50 %, performance environnementale 10 %",
];

const CONSTRAINTS = [
  "Travaux en site occupé",
  "Intervention pendant les vacances scolaires uniquement",
  "Clause d'insertion sociale : 5 % des heures travaillées",
  "Délai d'exécution : 4 mois",
];

const COMMENTS: Partial<Record<FollowUpStatus, string[]>> = {
  TO_STUDY: ["Vérifier les qualifications demandées.", "Voir si la visite est obligatoire.", ""],
  RESPONDING: ["Devis fournisseur demandé.", "Mémoire technique en cours de rédaction.", ""],
  SUBMITTED: ["Offre déposée sur la plateforme.", ""],
  DISMISSED: ["Trop loin pour nos équipes.", "Montant trop élevé pour nos capacités.", "Hors de notre cœur de métier."],
};

const COLLEAGUES = ["Sophie Martin", "Karim Benali", "Julien Roux"];

const WORKS_THRESHOLD = 5_538_000;
const SERVICES_THRESHOLD = 221_000;

const DAY = 86_400_000;
const today = new Date();
today.setHours(0, 0, 0, 0);

function dateOnly(daysFromToday: number): string {
  const date = new Date(today.getTime() + daysFromToday * DAY);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Noon on the given day, as an instant. */
function noon(daysFromToday: number): string {
  const date = new Date(today.getTime() + daysFromToday * DAY);
  date.setHours(12, 0, 0, 0);
  return date.toISOString();
}

/** "de Marseille", "d'Avignon". */
function ofCity(city: string): string {
  return /^[AEIOUH]/.test(city) ? `d'${city}` : `de ${city}`;
}

function pickActivity(): Activity {
  let draw = random() * ACTIVITIES.reduce((sum, activity) => sum + activity.weight, 0);
  for (const activity of ACTIVITIES) {
    draw -= activity.weight;
    if (draw < 0) {
      return activity;
    }
  }
  return ACTIVITIES[0];
}

function procedureFor(marketType: MarketType, amount: number | null): ProcedureType | null {
  if (chance(0.06)) {
    return null;
  }
  if (amount === null) {
    return pick<ProcedureType>(["ADAPTED", "ADAPTED", "OPEN", "NEGOTIATED"]);
  }
  const threshold = marketType === "WORKS" ? WORKS_THRESHOLD : SERVICES_THRESHOLD;
  if (amount >= threshold) {
    return pick<ProcedureType>(["OPEN", "OPEN", "RESTRICTED", "COMPETITIVE_DIALOGUE"]);
  }
  return pick<ProcedureType>(["ADAPTED", "ADAPTED", "ADAPTED", "OPEN"]);
}

function followUpStatusFor(retained: boolean, publishedDaysAgo: number): FollowUpStatus {
  if (!retained || (publishedDaysAgo <= 3 && chance(0.85))) {
    return "NEW";
  }
  const draw = random();
  if (draw < 0.3) return "NEW";
  if (draw < 0.55) return "TO_STUDY";
  if (draw < 0.7) return "RESPONDING";
  if (draw < 0.8) return "SUBMITTED";
  return "DISMISSED";
}

function relevanceFor(retained: boolean, activity: Activity, distanceKm: number | null): number | null {
  if (chance(0.05)) {
    return null;
  }
  if (!retained) {
    return between(8, 40);
  }
  const business = ["PHOTOVOLTAIC", "EV_CHARGING", "ELECTRICITY"].includes(activity.code) ? 25 : 12;
  const proximity = distanceKm === null ? 5 : distanceKm <= 50 ? 15 : distanceKm <= 120 ? 8 : 0;
  return Math.max(0, Math.min(100, 50 + business + proximity + between(-10, 8)));
}

function documentsFor(tenderId: number, marketType: MarketType, lotCount: number): DocumentInfo[] {
  const pdf = (fileName: string, pieceType: PieceType, pageCount: number): DocumentInfo => ({
    id: 0,
    fileName,
    sizeBytes: pageCount * between(35_000, 90_000),
    pieceType,
    pieceTypeCorrected: false,
    state: "READ",
    stateDetail: null,
    pageCount,
    readable: true,
  });
  const sheet = (fileName: string, pieceType: PieceType): DocumentInfo => ({
    ...pdf(fileName, pieceType, 1),
    sizeBytes: between(40_000, 400_000),
    state: "NOT_ANALYZED",
    stateDetail: "Tableur conservé, non analysé dans cette version",
    pageCount: null,
    readable: false,
  });

  const documents = [
    pdf("01_RC.pdf", "RC", between(8, 24)),
    pdf("02_CCAP.pdf", "CCAP", between(20, 60)),
    pdf("03_AE.pdf", "AE", between(3, 6)),
  ];
  if (lotCount > 0) {
    for (let lot = 1; lot <= lotCount; lot++) {
      documents.push(pdf(`04_CCTP_Lot${String(lot).padStart(2, "0")}.pdf`, "CCTP", between(15, 80)));
      documents.push(sheet(`05_DPGF_Lot${String(lot).padStart(2, "0")}.xlsx`, "DPGF"));
    }
  } else {
    documents.push(pdf("04_CCTP.pdf", "CCTP", between(15, 80)));
    documents.push(marketType === "WORKS" ? sheet("05_DPGF.xlsx", "DPGF") : sheet("05_BPU.xlsx", "BPU"));
    if (marketType === "SERVICES") {
      documents.push(sheet("06_DQE.xlsx", "DQE"));
    }
  }
  if (marketType === "WORKS" && chance(0.6)) {
    documents.push(pdf("07_Plans.pdf", "PLAN", between(4, 20)));
  }
  return documents
    .sort((a, b) => a.fileName.localeCompare(b.fileName))
    .map((document, index) => ({ ...document, id: tenderId * 100 + index + 1 }));
}

function makeTender(id: number): { tender: TenderDetail; dossier: Dossier | null } {
  const activity = pickActivity();
  const regionWide = chance(0.05);
  const place = regionWide || chance(0.05) ? null : pick(PLACES);
  const city = place?.city ?? "la commune";
  const site = pick(SITES);
  const title = pick(activity.titles)
    .replace("{site}", site)
    .replace("de {city}", ofCity(city))
    .replace("{city}", city);

  let buyer: string | null;
  if (regionWide) {
    buyer = "Région Provence-Alpes-Côte d'Azur";
  } else if (!place) {
    buyer = chance(0.5) ? null : pick(DEPARTMENT_BUYERS["13"]);
  } else if (chance(0.45)) {
    buyer = place.city === "Marseille" ? "Ville de Marseille" : `Commune ${ofCity(place.city)}`;
  } else {
    buyer = pick(DEPARTMENT_BUYERS[place.department]);
  }
  const departments = regionWide ? COMPANY_DEPARTMENTS : place ? [place.department] : chance(0.5) ? ["13"] : [];
  const distanceKm = place ? place.distanceKm + between(0, 4) : null;

  const amount = chance(0.3)
    ? null
    : Math.round(between(activity.amount[0], activity.amount[1]) / 1000) * 1000 * (chance(0.15) ? 0.4 : 1);

  const publishedDaysAgo = Math.floor(random() ** 1.3 * 50);
  const publishedAt = dateOnly(-publishedDaysAgo);
  let deadlineDay: number | null = chance(0.03) ? null : -publishedDaysAgo + between(12, 45);
  const notices: TenderDetail["notices"] = [];

  // Above the European thresholds, some notices come from the Official Journal of the EU.
  const threshold = activity.marketType === "WORKS" ? WORKS_THRESHOLD : SERVICES_THRESHOLD;
  const source = amount !== null && amount >= threshold && chance(0.5) ? "JOUE" : "BOAMP";
  const externalId = source === "JOUE" ? `${between(600000, 690000)}-2026` : `26-${between(100000, 160000)}`;
  notices.push({
    externalId,
    kind: "INITIAL",
    publishedAt,
    deadline: deadlineDay === null ? null : noon(deadlineDay),
    details: null,
  });
  if (deadlineDay !== null && publishedDaysAgo > 6 && chance(0.12)) {
    deadlineDay += 7;
    notices.push({
      externalId: `26-${between(160000, 170000)}`,
      kind: "RECTIFICATION",
      publishedAt: dateOnly(-publishedDaysAgo + 5),
      deadline: noon(deadlineDay),
      details: "Report de la date limite de remise des offres.",
    });
  }

  // As in the backend, a tender whose deadline has passed is closed.
  let status: TenderStatus = deadlineDay !== null && noon(deadlineDay) < new Date().toISOString() ? "CLOSED" : "ACTIVE";
  if (status === "ACTIVE" && chance(0.04)) {
    status = "CANCELLED";
    notices.push({
      externalId: `26-${between(170000, 180000)}`,
      kind: "CANCELLATION",
      publishedAt: dateOnly(-Math.max(0, publishedDaysAgo - 4)),
      deadline: null,
      details: "Procédure déclarée sans suite pour motif d'intérêt général.",
    });
  }

  const inZone = departments.length === 0 || departments.some((code) => COMPANY_DEPARTMENTS.includes(code));
  const tooSmall = amount !== null && amount < 25_000;
  const retained = inZone && !tooSmall;
  const cpvCodes = activity.cpv.slice(0, between(1, 3));
  let filterReason: string;
  if (!inZone) {
    filterReason = `Écarté : hors zone (${departments.join(", ")})`;
  } else if (tooSmall) {
    filterReason = "Écarté : montant inférieur au minimum (25 000 € HT)";
  } else {
    const zone = departments.length ? `département ${departments.join(", ")}` : "département non indiqué";
    filterReason = `Retenu : ${zone} ; CPV ${cpvCodes[0]}`;
  }

  let followUpStatus = followUpStatusFor(retained, publishedDaysAgo);
  if (status === "CLOSED" && followUpStatus === "RESPONDING") {
    followUpStatus = "SUBMITTED";
  }
  const decided = followUpStatus !== "NEW";
  const decidedDay = -publishedDaysAgo + Math.min(publishedDaysAgo, between(1, 4));

  const withLots = amount === null ? chance(0.25) : chance(amount > 500_000 ? 0.6 : 0.2);
  const lotTitles = withLots ? activity.lots.slice(0, between(2, Math.min(6, activity.lots.length))) : [];
  const lots = lotTitles.map((lotTitle, index) => ({
    number: String(index + 1),
    title: chance(0.05) ? null : lotTitle,
    estimatedAmount:
      amount === null ? null : Math.round(((amount / lotTitles.length) * between(70, 130)) / 100_000) * 1000,
  }));

  const activities = chance(0.35) ? [activity.code, pick(activity.related)] : [activity.code];
  const hasDossier = retained && decided && chance(0.45);
  const documents = hasDossier ? documentsFor(id, activity.marketType, lots.length) : [];
  const dossier: Dossier | null = hasDossier
    ? {
        id,
        version: 1,
        status: "DONE",
        errorMessage: null,
        uploadedBy: pick(COLLEAGUES),
        uploadedAt: noon(decidedDay),
        processedAt: noon(decidedDay),
        documents,
      }
    : null;

  const tender: TenderDetail = {
    id,
    title,
    buyer,
    location: regionWide ? "Région Provence-Alpes-Côte d'Azur" : (place?.city ?? null),
    departments,
    marketTypes: [activity.marketType],
    estimatedAmount: amount,
    publishedAt,
    deadline: deadlineDay === null ? null : noon(deadlineDay),
    status,
    followUpStatus,
    retained,
    filterReason,
    reference: chance(0.15)
      ? null
      : pick([`2026-${between(1, 300)}`, `MAPA2026-${between(1, 60)}`, `26S${between(1000, 9999)}`]),
    procedureType: procedureFor(activity.marketType, amount),
    source,
    lotCount: lots.length,
    activities: [...new Set(activities)],
    distanceKm,
    relevanceScore: relevanceFor(retained, activity, distanceKm),
    followed: decided && followUpStatus !== "DISMISSED" && chance(0.35),
    documentCount: documents.length,
    externalId,
    description: chance(0.08) ? null : activity.description,
    conditions: chance(0.25)
      ? null
      : {
          qualifications: activity.qualifications.slice(0, between(0, activity.qualifications.length)),
          siteVisit: pick(["Obligatoire", "Facultative", null]),
          selectionCriteria: chance(0.15) ? null : pick(SELECTION_CRITERIA),
          constraints: chance(0.4) ? null : pick(CONSTRAINTS),
        },
    cpvCodes,
    noticeUrl:
      source === "JOUE"
        ? `https://ted.europa.eu/fr/notice/-/detail/${externalId}`
        : `https://www.boamp.fr/pages/avis/?q=idweb:%22${externalId}%22`,
    platformUrl: chance(0.15) ? null : regionWide ? "https://marches.maregionsud.fr" : pick(PLATFORMS),
    followUpComment: decided ? pick(COMMENTS[followUpStatus] ?? [""]) || null : null,
    followUpBy: decided ? pick(COLLEAGUES) : null,
    followUpAt: decided ? noon(decidedDay) : null,
    lots,
    notices,
  };
  return { tender, dossier };
}

const generated = Array.from({ length: TENDER_COUNT }, (_, index) => makeTender(index + 1));

export const MOCK_TENDERS: TenderDetail[] = generated.map(({ tender }) => tender);

/** Consultation files already uploaded, by tender id. */
export const MOCK_DOSSIERS: Record<number, Dossier[]> = Object.fromEntries(
  generated.map(({ tender, dossier }) => [tender.id, dossier ? [dossier] : []]),
);
