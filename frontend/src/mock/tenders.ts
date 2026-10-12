/**
 * Consultations of the demo: about 60 open ones and 80 past ones. Each one
 * comes from a project (kind of work on a kind of building) so that buyer,
 * place, activity, amount, procedure, lots and dates stay consistent. The
 * first ids are written by hand (see detailedTenders.ts) to show complete
 * pages.
 */

import type {
  AwardCriterion,
  FollowUpStatus,
  MarketType,
  ProcedureType,
  TenderDetail,
  TenderKeyInfo,
  TenderStatus,
} from "@/types/tender";

import { DETAILED_TENDERS } from "./detailedTenders";
import { at, day, seededRandom } from "./helpers";
import {
  COMPANY_DEPARTMENTS,
  CPV_LABELS,
  DEPARTMENT_OF,
  DISTANT_PLACES,
  HOUSING_BODY_OF,
  PLACES,
  PLATFORMS,
  REGION_OF,
  type Place,
} from "./reference";

const OPEN_COUNT = 54;
const PAST_COUNT = 79;
const FIRST_PAST_ID = 101;

/** European thresholds 2026-2027, in euros excluding tax. */
const WORKS_THRESHOLD = 5_404_000;
const SERVICES_THRESHOLD = 216_000;

/** Minimum amount of the company's filter criteria. */
const MIN_AMOUNT = 25_000;

type BuyerKind =
  | "commune"
  | "intercommunality"
  | "department"
  | "region"
  | "housing"
  | "hospital"
  | "fireService"
  | "energySyndicate";

type SiteKind =
  | "school"
  | "nursery"
  | "gym"
  | "townhall"
  | "library"
  | "technical"
  | "pool"
  | "college"
  | "lycee"
  | "housing"
  | "hospital"
  | "fireStation";

/** Buildings, and the buyer owning each kind. */
const SITES: Record<SiteKind, { buyer: BuyerKind; names: string[]; occupied?: string }> = {
  school: {
    buyer: "commune",
    names: ["du groupe scolaire Jean Jaurès", "de l'école élémentaire Les Oliviers", "du groupe scolaire Paul Cézanne"],
    occupied: "Travaux en site occupé : interventions bruyantes pendant les vacances scolaires uniquement",
  },
  nursery: { buyer: "commune", names: ["de la crèche municipale Les Calanques"] },
  gym: { buyer: "commune", names: ["du gymnase Marcel Cerdan", "du complexe sportif municipal"] },
  townhall: { buyer: "commune", names: ["de l'hôtel de ville"] },
  library: { buyer: "intercommunality", names: ["de la médiathèque intercommunale"] },
  technical: { buyer: "commune", names: ["du centre technique municipal"] },
  pool: { buyer: "intercommunality", names: ["du centre aquatique intercommunal"] },
  college: {
    buyer: "department",
    names: ["du collège Marcel Pagnol", "du collège Frédéric Mistral", "du collège Edmond Rostand"],
    occupied: "Travaux en site occupé : phasage imposé, interventions lourdes pendant les congés d'été",
  },
  lycee: {
    buyer: "region",
    names: ["du lycée Saint-Exupéry", "du lycée professionnel Louis Lumière", "du lycée Victor Hugo"],
    occupied: "Travaux en site occupé : phasage imposé, interventions lourdes pendant les congés d'été",
  },
  housing: {
    buyer: "housing",
    names: ["de la résidence Les Pins (48 logements)", "de la résidence Le Clos des Vignes (72 logements)"],
    occupied: "Logements occupés : information des locataires au moins 8 jours avant chaque intervention",
  },
  hospital: {
    buyer: "hospital",
    names: ["du bâtiment de consultations", "du pôle gériatrique", "du bâtiment logistique"],
    occupied: "Intervention en établissement de santé occupé : continuité de service exigée",
  },
  fireStation: { buyer: "fireService", names: ["du centre d'incendie et de secours"] },
};

type Project = {
  /** Activity codes, the main one first. */
  activities: string[];
  marketType: MarketType;
  /** With {site} for a building, {city} for the whole commune. */
  title: string;
  sites?: SiteKind[];
  buyers?: BuyerKind[];
  amount: [number, number];
  /** Lots when the consultation is divided, in order. */
  lots: string[];
  cpv: string[];
  description: string;
};

const ELECTRICAL_LOTS = ["Courants forts", "Courants faibles", "Sécurité incendie"];
const RENOVATION_LOTS = [
  "Isolation thermique par l'extérieur",
  "Menuiseries extérieures",
  "Chauffage – ventilation",
  "Électricité – éclairage",
  "Plâtrerie – faux plafonds",
  "Étanchéité",
];
const ROAD_LOTS = ["Terrassements – voirie", "Réseaux humides", "Réseaux secs – éclairage public", "Espaces verts"];

const PROJECTS: Project[] = [
  {
    activities: ["ELECTRICITY"],
    marketType: "WORKS",
    title: "Rénovation des installations électriques {site}",
    sites: ["school", "college", "lycee", "gym", "townhall", "hospital"],
    amount: [80_000, 900_000],
    lots: ELECTRICAL_LOTS,
    cpv: ["45311000-0", "45312100-8", "45315600-4"],
    description:
      "Dépose des installations existantes, création d'un nouveau TGBT et des tableaux divisionnaires, reprise " +
      "des circuits d'éclairage et de prises, remplacement du système de sécurité incendie et des réseaux VDI.",
  },
  {
    activities: ["ELECTRICITY"],
    marketType: "WORKS",
    title: "Mise en conformité électrique des bâtiments communaux de {city}",
    buyers: ["commune"],
    amount: [40_000, 250_000],
    lots: [],
    cpv: ["45310000-3", "45315600-4"],
    description:
      "Levée des observations du dernier rapport de vérification périodique sur une quinzaine de bâtiments " +
      "communaux : remplacement de protections, reprise des liaisons équipotentielles, mise à jour des schémas.",
  },
  {
    activities: ["ELECTRICITY"],
    marketType: "WORKS",
    title: "Remplacement du TGBT et des tableaux divisionnaires {site}",
    sites: ["townhall", "hospital", "library", "pool"],
    amount: [40_000, 300_000],
    lots: [],
    cpv: ["45315600-4", "45311000-0"],
    description:
      "Remplacement du tableau général basse tension et des tableaux divisionnaires, avec coupures programmées " +
      "de nuit pour maintenir l'activité du bâtiment, et mise à jour des schémas électriques.",
  },
  {
    activities: ["PHOTOVOLTAIC", "ELECTRICITY"],
    marketType: "WORKS",
    title: "Installation d'une centrale photovoltaïque en toiture {site}",
    sites: ["school", "college", "lycee", "gym", "technical"],
    amount: [90_000, 700_000],
    lots: ["Centrale photovoltaïque", "Étanchéité et renforcement de charpente"],
    cpv: ["09331200-0", "45261215-4", "45311000-0"],
    description:
      "Fourniture, pose et mise en service d'une centrale photovoltaïque en surimposition, en autoconsommation " +
      "avec vente du surplus, y compris onduleurs, raccordement au réseau public, supervision et maintenance " +
      "pendant deux ans.",
  },
  {
    activities: ["PHOTOVOLTAIC", "ELECTRICITY"],
    marketType: "WORKS",
    title: "Création d'ombrières photovoltaïques sur le parking {site}",
    sites: ["hospital", "townhall", "pool", "gym", "lycee"],
    amount: [300_000, 2_500_000],
    lots: ["Ombrières et centrale photovoltaïque", "Raccordement et supervision", "Éclairage du parking"],
    cpv: ["09332000-5", "45261215-4", "45310000-3"],
    description:
      "Conception et réalisation d'ombrières de parking équipées de modules photovoltaïques, fondations, " +
      "raccordement au réseau public, éclairage LED intégré et pré-équipement pour bornes de recharge.",
  },
  {
    activities: ["PHOTOVOLTAIC"],
    marketType: "WORKS",
    title: "Solarisation de six bâtiments communaux de {city} en autoconsommation collective",
    buyers: ["commune"],
    amount: [250_000, 1_200_000],
    lots: ["Groupes scolaires", "Équipements sportifs", "Bâtiments administratifs"],
    cpv: ["09331200-0", "45261215-4"],
    description:
      "Installation de centrales photovoltaïques sur six bâtiments communaux et mise en place d'une opération " +
      "d'autoconsommation collective, avec la personne morale organisatrice et le suivi de production.",
  },
  {
    activities: ["EV_CHARGING", "ELECTRICITY"],
    marketType: "WORKS",
    title: "Déploiement de bornes de recharge pour véhicules électriques sur l'espace public de {city}",
    buyers: ["commune", "intercommunality", "energySyndicate"],
    amount: [150_000, 1_800_000],
    lots: ["Bornes de recharge normale (22 kW)", "Bornes de recharge rapide (50 kW)", "Supervision et exploitation"],
    cpv: ["31158100-9", "45310000-3", "45232000-2"],
    description:
      "Fourniture, pose, raccordement et mise en service de points de recharge accessibles au public, génie civil " +
      "associé, signalétique, supervision interopérable et maintenance.",
  },
  {
    activities: ["EV_CHARGING"],
    marketType: "WORKS",
    title: "Installation de bornes de recharge pour la flotte de véhicules {site}",
    sites: ["technical", "hospital", "fireStation"],
    amount: [40_000, 250_000],
    lots: [],
    cpv: ["31158100-9", "45310000-3"],
    description:
      "Installation de points de recharge sur le parking des véhicules de service, avec pilotage énergétique " +
      "pour limiter la puissance souscrite et badges d'identification des agents.",
  },
  {
    activities: ["HEATING"],
    marketType: "WORKS",
    title: "Remplacement de la chaufferie {site}",
    sites: ["school", "college", "lycee", "gym", "pool", "housing", "hospital"],
    amount: [120_000, 1_200_000],
    lots: ["Chauffage", "Électricité", "Régulation et supervision"],
    cpv: ["45331100-7", "45331000-6"],
    description:
      "Dépose des chaudières existantes, installation de chaudières à condensation en cascade, reprise des " +
      "réseaux hydrauliques en chaufferie, régulation communicante et raccordement à la GTB.",
  },
  {
    activities: ["HEATING", "ENERGY_RENOVATION"],
    marketType: "WORKS",
    title: "Installation de pompes à chaleur air/eau {site}",
    sites: ["school", "college", "townhall", "nursery", "library"],
    amount: [80_000, 600_000],
    lots: [],
    cpv: ["42511110-5", "45331000-6"],
    description:
      "Remplacement de la production de chaleur au fioul par des pompes à chaleur air/eau, avec appoint, " +
      "reprise des émetteurs et de la régulation, éligible aux certificats d'économies d'énergie.",
  },
  {
    activities: ["AIR_CONDITIONING"],
    marketType: "WORKS",
    title: "Rafraîchissement des salles de classe {site}",
    sites: ["school", "college", "lycee"],
    amount: [60_000, 500_000],
    lots: [],
    cpv: ["45331220-4", "39717200-3"],
    description:
      "Installation de systèmes de rafraîchissement dans les salles de classe exposées, avec unités extérieures " +
      "en toiture, alimentation électrique dédiée et régulation par sondes de température.",
  },
  {
    activities: ["AIR_CONDITIONING"],
    marketType: "WORKS",
    title: "Remplacement des groupes froids {site}",
    sites: ["hospital", "library", "pool", "townhall"],
    amount: [100_000, 900_000],
    lots: [],
    cpv: ["45331220-4", "39717200-3"],
    description:
      "Remplacement des groupes de production d'eau glacée arrivés en fin de vie par des équipements à fluide " +
      "frigorigène à faible potentiel de réchauffement, avec maintien de la production pendant les travaux.",
  },
  {
    activities: ["ENERGY_RENOVATION", "HEATING", "ELECTRICITY"],
    marketType: "WORKS",
    title: "Rénovation énergétique {site}",
    sites: ["school", "college", "lycee", "gym", "townhall", "library"],
    amount: [400_000, 4_500_000],
    lots: RENOVATION_LOTS,
    cpv: ["45453000-7", "45321000-3", "45421100-5"],
    description:
      "Rénovation globale visant une réduction des consommations d'au moins 40 % (décret tertiaire) : isolation " +
      "par l'extérieur, menuiseries, ventilation double flux, production de chaleur et éclairage LED.",
  },
  {
    activities: ["ENERGY_RENOVATION", "HEATING"],
    marketType: "WORKS",
    title: "Réhabilitation thermique {site}",
    sites: ["housing"],
    amount: [800_000, 6_000_000],
    lots: RENOVATION_LOTS,
    cpv: ["45453000-7", "45321000-3", "45421100-5"],
    description:
      "Réhabilitation thermique en milieu occupé : isolation des façades et des toitures-terrasses, remplacement " +
      "des menuiseries, ventilation hygroréglable et remplacement des chaudières individuelles.",
  },
  {
    activities: ["PUBLIC_LIGHTING"],
    marketType: "WORKS",
    title: "Rénovation de l'éclairage public en LED de {city}",
    buyers: ["commune", "intercommunality", "energySyndicate"],
    amount: [100_000, 1_500_000],
    lots: ["Fourniture et pose des luminaires", "Armoires de commande et réseaux"],
    cpv: ["45316110-9", "34928500-3"],
    description:
      "Remplacement des luminaires vétustes par des luminaires LED avec abaissement de puissance en milieu de " +
      "nuit, remplacement des armoires de commande et mise à jour de la cartographie du réseau.",
  },
  {
    activities: ["NETWORKS", "PUBLIC_LIGHTING"],
    marketType: "WORKS",
    title: "Enfouissement des réseaux secs du centre ancien de {city}",
    buyers: ["commune", "energySyndicate"],
    amount: [150_000, 1_200_000],
    lots: ["Réseaux électriques", "Réseaux de télécommunication", "Éclairage public"],
    cpv: ["45232000-2", "45231400-9", "45316110-9"],
    description:
      "Mise en souterrain des réseaux électriques basse tension et de télécommunication, reprise des " +
      "branchements des riverains et pose d'un nouvel éclairage public.",
  },
  {
    activities: ["VRD", "NETWORKS"],
    marketType: "WORKS",
    title: "Aménagement VRD de la zone d'activités de {city}",
    buyers: ["commune", "intercommunality"],
    amount: [400_000, 6_500_000],
    lots: ROAD_LOTS,
    cpv: ["45233140-2", "45112500-0", "45232000-2"],
    description:
      "Création des voiries de desserte, des réseaux humides et secs, de l'éclairage public et des espaces verts " +
      "d'une extension de zone d'activités d'une dizaine d'hectares.",
  },
  {
    activities: ["VRD"],
    marketType: "WORKS",
    title: "Requalification de l'avenue Jean Jaurès à {city}",
    buyers: ["commune", "intercommunality"],
    amount: [300_000, 3_000_000],
    lots: ROAD_LOTS,
    cpv: ["45233140-2", "45112500-0"],
    description:
      "Réaménagement complet de l'avenue : chaussée, trottoirs, pistes cyclables, plantations, reprise des " +
      "réseaux et de l'éclairage public, avec maintien de la circulation par demi-chaussée.",
  },
  {
    activities: ["MAINTENANCE", "ELECTRICITY", "HEATING"],
    marketType: "SERVICES",
    title: "Maintenance des installations électriques et de chauffage des bâtiments de {city}",
    buyers: ["commune"],
    amount: [40_000, 400_000],
    lots: ["Électricité", "Chauffage – climatisation"],
    cpv: ["50711000-2", "50720000-8"],
    description:
      "Maintenance préventive et corrective, petits travaux et astreinte pour l'ensemble du patrimoine " +
      "communal, sous forme d'accord-cadre à bons de commande d'une durée de quatre ans.",
  },
  {
    activities: ["MAINTENANCE", "PHOTOVOLTAIC"],
    marketType: "SERVICES",
    title: "Maintenance préventive et corrective des installations photovoltaïques",
    buyers: ["commune", "intercommunality", "department"],
    amount: [20_000, 150_000],
    lots: [],
    cpv: ["50711000-2"],
    description:
      "Maintenance des centrales photovoltaïques en toiture : visites annuelles, nettoyage des modules, " +
      "thermographie, suivi de production et remplacement des onduleurs défaillants.",
  },
  {
    activities: ["MAINTENANCE", "EV_CHARGING"],
    marketType: "SERVICES",
    title: "Exploitation et maintenance des bornes de recharge",
    buyers: ["intercommunality", "energySyndicate"],
    amount: [50_000, 300_000],
    lots: [],
    cpv: ["50711000-2", "31158100-9"],
    description:
      "Supervision, maintenance préventive et corrective du parc de bornes de recharge, gestion de l'itinérance " +
      "et assistance aux usagers 24 h/24.",
  },
  {
    activities: ["STUDIES", "ENERGY_RENOVATION"],
    marketType: "SERVICES",
    title: "Mission de maîtrise d'œuvre pour la rénovation énergétique {site}",
    sites: ["school", "college", "lycee", "gym"],
    amount: [30_000, 250_000],
    lots: [],
    cpv: ["71221000-3", "71321000-4"],
    description:
      "Mission de maîtrise d'œuvre complète (diagnostic à assistance aux opérations de réception) pour une " +
      "rénovation énergétique, avec simulation thermique dynamique.",
  },
  {
    activities: ["STUDIES"],
    marketType: "SERVICES",
    title: "Audit énergétique du patrimoine bâti de {city}",
    buyers: ["commune"],
    amount: [15_000, 90_000],
    lots: [],
    cpv: ["71314300-5"],
    description:
      "Audit énergétique d'une trentaine de bâtiments, plan pluriannuel de travaux chiffré et accompagnement " +
      "pour les déclarations sur la plateforme OPERAT.",
  },
  {
    activities: ["STUDIES", "PHOTOVOLTAIC"],
    marketType: "SERVICES",
    title: "Étude de faisabilité photovoltaïque sur le patrimoine de {city}",
    buyers: ["commune", "intercommunality"],
    amount: [15_000, 60_000],
    lots: [],
    cpv: ["71314300-5", "71321000-4"],
    description:
      "Étude du potentiel photovoltaïque des toitures et parkings, vérification de la structure, scénarios " +
      "d'autoconsommation et plan de financement.",
  },
];

/** Qualifications and certifications usually required, by main activity. */
const REQUIREMENTS: Record<string, { qualifications: string[]; certifications: string[] }> = {
  ELECTRICITY: { qualifications: ["Qualifelec – électrotechnique", "Habilitations électriques B2V / BR"], certifications: [] },
  PHOTOVOLTAIC: { qualifications: ["QualiPV Elec", "Qualifelec SPV"], certifications: ["RGE"] },
  EV_CHARGING: { qualifications: ["Qualifelec IRVE mention P2"], certifications: [] },
  HEATING: { qualifications: ["Qualibat 5313 – installations thermiques"], certifications: ["RGE"] },
  AIR_CONDITIONING: { qualifications: ["Attestation de capacité fluides frigorigènes (catégorie V)"], certifications: [] },
  ENERGY_RENOVATION: { qualifications: ["Qualibat 7131 – isolation thermique par l'extérieur"], certifications: ["RGE"] },
  PUBLIC_LIGHTING: { qualifications: ["Qualifelec éclairage public", "AIPR opérateur"], certifications: [] },
  NETWORKS: { qualifications: ["AIPR encadrant", "FNTP 3 – réseaux secs"], certifications: [] },
  VRD: { qualifications: ["FNTP 3", "AIPR encadrant"], certifications: [] },
  MAINTENANCE: {
    qualifications: ["Habilitations électriques", "Attestation de capacité fluides frigorigènes"],
    certifications: [],
  },
  STUDIES: { qualifications: ["OPQIBI 1905 – audit énergétique"], certifications: [] },
};

const REQUIRED_DOCUMENTS = [
  "DC1 – lettre de candidature",
  "DC2 – déclaration du candidat",
  "Mémoire technique",
  "Planning prévisionnel",
  "Attestations d'assurance (responsabilité civile et décennale)",
  "Références de moins de 5 ans",
];

const CRITERIA: AwardCriterion[][] = [
  [
    { label: "Valeur technique", weight: 60 },
    { label: "Prix", weight: 40 },
  ],
  [
    { label: "Prix", weight: 60 },
    { label: "Valeur technique", weight: 40 },
  ],
  [
    { label: "Valeur technique", weight: 50 },
    { label: "Prix", weight: 40 },
    { label: "Délais d'exécution", weight: 10 },
  ],
  [
    { label: "Valeur technique", weight: 50 },
    { label: "Prix", weight: 40 },
    { label: "Performance environnementale", weight: 10 },
  ],
];

const COMMENTS: Partial<Record<FollowUpStatus, string[]>> = {
  TO_STUDY: ["Vérifier les qualifications demandées.", "Voir si la visite est obligatoire.", ""],
  RESPONDING: ["Devis fournisseurs demandés.", "Mémoire technique en cours de rédaction.", ""],
  SUBMITTED: ["Offre déposée sur la plateforme.", ""],
  DISMISSED: ["Trop loin pour nos équipes.", "Montant trop élevé pour nos capacités.", "Hors de notre cœur de métier."],
};

export const COLLEAGUES = ["Sophie Martin", "Karim Benali", "Julien Roux"];

const rnd = seededRandom(20261004);

function ofCity(city: string): string {
  return /^[AEIOUH]/.test(city) ? `d'${city}` : `de ${city}`;
}

function buyerName(kind: BuyerKind, place: Place): string {
  const department = DEPARTMENT_OF[place.department];
  switch (kind) {
    case "commune":
      return place.commune;
    case "intercommunality":
      return place.intercommunality;
    case "department":
      return `Département ${department}`;
    case "region":
      return REGION_OF[place.department];
    case "housing":
      return HOUSING_BODY_OF[place.department];
    case "hospital":
      return `Centre hospitalier ${ofCity(place.city)}`;
    case "fireService":
      return `Service départemental d'incendie et de secours ${department}`;
    case "energySyndicate":
      return `Syndicat d'énergie ${department}`;
  }
}

function platformFor(kind: BuyerKind, place: Place) {
  if (kind === "region") {
    return PLATFORMS.region;
  }
  if (kind === "intercommunality" && place.intercommunality === "Métropole Aix-Marseille-Provence") {
    return PLATFORMS.metropole;
  }
  return rnd.pick([PLATFORMS.place, PLATFORMS.achatpublic, PLATFORMS.securises, PLATFORMS.info]);
}

function procedureFor(marketType: MarketType, amount: number | null): ProcedureType | null {
  if (rnd.chance(0.05)) {
    return null;
  }
  const threshold = marketType === "WORKS" ? WORKS_THRESHOLD : SERVICES_THRESHOLD;
  if (amount !== null && amount >= threshold) {
    return rnd.pick<ProcedureType>(["OPEN", "OPEN", "OPEN", "RESTRICTED"]);
  }
  return rnd.pick<ProcedureType>(["ADAPTED", "ADAPTED", "ADAPTED", "OPEN"]);
}

/** Lot amounts that add up to the total, rounded to the thousand. */
function splitAmount(total: number | null, count: number): (number | null)[] {
  if (total === null) {
    return Array(count).fill(null);
  }
  const weights = Array.from({ length: count }, () => rnd.between(60, 140));
  const sum = weights.reduce((a, b) => a + b, 0);
  const amounts = weights.map((weight) => Math.round((total * weight) / sum / 1000) * 1000);
  amounts[0] += total - amounts.reduce((a, b) => a + b, 0);
  return amounts;
}

function reference(year: string): string | null {
  if (rnd.chance(0.1)) {
    return null;
  }
  const short = year.slice(2);
  return rnd.pick([
    `${year}-${String(rnd.between(1, 300)).padStart(3, "0")}`,
    `MAPA${year}-${String(rnd.between(1, 60)).padStart(2, "0")}`,
    `${short}S${rnd.between(1000, 9999)}`,
    `AO-${year}-${String(rnd.between(1, 150)).padStart(3, "0")}`,
  ]);
}

function relevance(retained: boolean, activity: string, distanceKm: number | null): number | null {
  if (rnd.chance(0.04)) {
    return null;
  }
  if (!retained) {
    return rnd.between(8, 40);
  }
  const business = ["PHOTOVOLTAIC", "EV_CHARGING", "ELECTRICITY", "PUBLIC_LIGHTING"].includes(activity)
    ? 25
    : ["HEATING", "AIR_CONDITIONING", "ENERGY_RENOVATION", "MAINTENANCE", "NETWORKS"].includes(activity)
      ? 14
      : 4;
  const proximity = distanceKm === null ? 5 : distanceKm <= 50 ? 15 : distanceKm <= 120 ? 8 : 0;
  return Math.max(0, Math.min(99, 52 + business + proximity + rnd.between(-10, 6)));
}

function keyInfo(project: Project, siteKind: SiteKind | null, publishedOn: number, deadlineOn: number): TenderKeyInfo | null {
  if (rnd.chance(0.2)) {
    return null;
  }
  const requirements = REQUIREMENTS[project.activities[0]];
  const works = project.marketType === "WORKS";
  const visitRequired = works && rnd.chance(siteKind && SITES[siteKind].occupied ? 0.7 : 0.35);
  const visitDay = rnd.between(publishedOn + 4, Math.max(publishedOn + 5, deadlineOn - 7));
  const documents = rnd.some(REQUIRED_DOCUMENTS, rnd.between(3, 5));
  const conditions = [
    siteKind ? SITES[siteKind].occupied : undefined,
    rnd.chance(0.3) ? "Clause d'insertion sociale : 5 % des heures travaillées" : undefined,
  ].filter(Boolean);
  return {
    siteVisit: visitRequired
      ? { required: true, date: at(visitDay, rnd.pick([9, 10, 14]), rnd.pick([0, 30])) }
      : rnd.chance(0.5)
        ? { required: false, date: null }
        : null,
    requiredDocuments: visitRequired ? [...documents, "Attestation de visite"] : documents,
    specialConditions: conditions.length ? conditions.join(". ") + "." : null,
    qualifications: rnd.some(requirements.qualifications, rnd.between(1, requirements.qualifications.length)),
    certifications: rnd.chance(0.7) ? requirements.certifications : [],
  };
}

function followUpFor(retained: boolean, past: boolean, publishedOn: number): FollowUpStatus {
  if (!retained) {
    return "NEW";
  }
  const draw = rnd.random();
  if (past) {
    return draw < 0.18 ? "SUBMITTED" : draw < 0.4 ? "DISMISSED" : "NEW";
  }
  if (publishedOn >= -2 && rnd.chance(0.85)) {
    return "NEW";
  }
  return draw < 0.35 ? "NEW" : draw < 0.6 ? "TO_STUDY" : draw < 0.75 ? "RESPONDING" : draw < 0.8 ? "SUBMITTED" : "DISMISSED";
}

function makeTender(id: number, past: boolean): TenderDetail {
  const project = rnd.pick(PROJECTS);
  const place = rnd.chance(past ? 0.05 : 0.12) ? rnd.pick(DISTANT_PLACES) : rnd.pick(PLACES);
  const siteKind = project.sites ? rnd.pick(project.sites) : null;
  const buyerKind = siteKind ? SITES[siteKind].buyer : rnd.pick<BuyerKind>(project.buyers ?? ["commune"]);
  const title = siteKind
    ? `${project.title.replace("{site}", rnd.pick(SITES[siteKind].names))} à ${place.city}`
    : project.title.replace("de {city}", ofCity(place.city)).replace("{city}", place.city);
  const amount = rnd.chance(0.25) ? null : rnd.amount(project.amount[0], project.amount[1]);
  const procedureType = procedureFor(project.marketType, amount);
  const threshold = project.marketType === "WORKS" ? WORKS_THRESHOLD : SERVICES_THRESHOLD;
  const source = amount !== null && amount >= threshold && rnd.chance(0.5) ? "JOUE" : "BOAMP";

  // Dates in days from today.
  const duration = project.marketType === "WORKS" ? rnd.between(21, 45) : rnd.between(18, 35);
  const publishedOn = past ? -rnd.between(60, 1370) : -Math.floor(rnd.random() ** 1.4 * 36);
  let deadlineOn = publishedOn + duration;
  if (!past && deadlineOn < 1) {
    deadlineOn = rnd.between(1, 10);
  }
  const deadline = at(deadlineOn, rnd.pick([12, 12, 12, 16]));
  const publishedAt = day(publishedOn);
  const year = publishedAt.slice(0, 4);

  const externalId =
    source === "JOUE" ? `${rnd.between(600000, 690000)}-${year}` : `${year.slice(2)}-${rnd.between(100000, 160000)}`;
  const notices: TenderDetail["notices"] = [];
  const rectified = rnd.chance(past ? 0.08 : 0.12) && deadlineOn - publishedOn > 14;
  notices.push({
    source,
    externalId,
    kind: "INITIAL",
    publishedAt,
    deadline: rectified ? at(deadlineOn - 7, 12) : deadline,
    details: null,
  });
  if (rectified) {
    notices.push({
      source,
      externalId: `${year.slice(2)}-${rnd.between(160000, 170000)}`,
      kind: "RECTIFICATION",
      publishedAt: day(Math.min(0, publishedOn + 6)),
      deadline,
      details: "Report de la date limite de remise des offres.",
    });
  }

  // Outcome of past consultations; an award notice is completed from the awarded contract.
  let status: TenderStatus = past ? "CLOSED" : "ACTIVE";
  let result: TenderDetail["result"] = null;
  if (past) {
    const draw = rnd.random();
    if (draw < 0.42) {
      result = { kind: "AWARDED" };
      notices.push({
        source,
        externalId: `${year.slice(2)}-${rnd.between(170000, 199999)}`,
        kind: "AWARD",
        publishedAt: day(Math.max(deadlineOn + 5, Math.min(deadlineOn + rnd.between(30, 75), -2))),
        deadline: null,
        details: null,
      });
    } else if (draw < 0.56) {
      status = "UNSUCCESSFUL";
      result = { kind: "UNSUCCESSFUL" };
      notices.push({
        source,
        externalId: `${year.slice(2)}-${rnd.between(170000, 199999)}`,
        kind: "CANCELLATION",
        publishedAt: day(Math.min(deadlineOn + rnd.between(15, 40), -2)),
        deadline: null,
        details: "Procédure déclarée sans suite pour motif d'intérêt général.",
      });
    } else if (draw < 0.66) {
      status = "CANCELLED";
      result = { kind: "CANCELLED" };
    }
  } else if (rnd.chance(0.03)) {
    status = "CANCELLED";
    result = { kind: "CANCELLED" };
  }
  if (status === "CANCELLED") {
    notices.push({
      source,
      externalId: `${year.slice(2)}-${rnd.between(170000, 199999)}`,
      kind: "CANCELLATION",
      publishedAt: day(Math.min(0, publishedOn + rnd.between(3, 10))),
      deadline: null,
      details: "Avis annulé : erreur dans le dossier de consultation, un nouvel avis sera publié.",
    });
  }

  const cpvCodes = project.cpv.slice(0, rnd.between(1, project.cpv.length));
  const inZone = COMPANY_DEPARTMENTS.includes(place.department);
  const tooSmall = amount !== null && amount < MIN_AMOUNT;
  const retained = inZone && !tooSmall;
  const filterReason = !inZone
    ? `Écarté : hors zone (${place.department})`
    : tooSmall
      ? "Écarté : montant inférieur au minimum (25 000 € HT)"
      : `Retenu : département ${place.department} ; CPV ${cpvCodes[0]}`;

  const followUpStatus = followUpFor(retained, past, publishedOn);
  const decided = followUpStatus !== "NEW";
  const decidedOn = Math.min(0, publishedOn + rnd.between(1, 4));
  const allotted = project.lots.length > 0 && rnd.chance(amount !== null && amount >= 400_000 ? 0.7 : 0.25);
  const lotTitles = allotted ? project.lots.slice(0, rnd.between(2, Math.min(5, project.lots.length))) : [];
  const lotAmounts = splitAmount(amount, lotTitles.length);
  const platform = rnd.chance(0.08) ? null : platformFor(buyerKind, place);
  const distanceKm = place.distanceKm + rnd.between(0, 3);

  return {
    id,
    title,
    buyer: buyerName(buyerKind, place),
    location: place.city,
    postalCode: place.postalCode,
    departments: [place.department],
    marketTypes: [project.marketType],
    estimatedAmount: amount,
    publishedAt,
    deadline,
    status,
    result,
    followUpStatus,
    retained,
    filterReason,
    reference: reference(year),
    procedureType,
    source,
    activities: project.activities.slice(0, rnd.between(1, project.activities.length)),
    distanceKm,
    relevanceScore: relevance(retained, project.activities[0], distanceKm),
    followed: decided && followUpStatus !== "DISMISSED" && rnd.chance(past ? 0.3 : 0.45),
    externalId,
    description: rnd.chance(0.06) ? null : project.description,
    cpvCodes,
    cpv: cpvCodes.map((code) => ({ code, label: CPV_LABELS[code] })),
    awardCriteria: rnd.chance(0.1) ? [] : rnd.pick(CRITERIA),
    keyInfo: keyInfo(project, siteKind, publishedOn, deadlineOn),
    platformName: platform?.name ?? null,
    platformUrl: platform?.url ?? null,
    noticeUrl:
      source === "JOUE"
        ? `https://ted.europa.eu/fr/notice/-/detail/${externalId}`
        : `https://www.boamp.fr/pages/avis/?q=idweb:%22${externalId}%22`,
    followUpComment: decided ? rnd.pick(COMMENTS[followUpStatus] ?? [""]) || null : null,
    followUpBy: decided ? rnd.pick(COLLEAGUES) : null,
    followUpAt: decided ? at(decidedOn, rnd.between(8, 18), rnd.pick([0, 15, 30, 45])) : null,
    lots: lotTitles.map((lotTitle, index) => ({
      number: String(index + 1),
      title: lotTitle,
      estimatedAmount: lotAmounts[index],
    })),
    notices,
  };
}

const openTenders = Array.from({ length: OPEN_COUNT }, (_, index) =>
  makeTender(DETAILED_TENDERS.length + index + 1, false),
);
const pastTenders = Array.from({ length: PAST_COUNT }, (_, index) => makeTender(FIRST_PAST_ID + index, true));

/** All consultations, open and past. */
export const MOCK_TENDERS: TenderDetail[] = [...DETAILED_TENDERS, ...openTenders, ...pastTenders];
