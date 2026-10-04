/**
 * Six consultations written by hand, complete in every section, to check the
 * important pages: details, lots, criteria, key information, documents,
 * history and award. Ids 1 to 6.
 */

import type { TenderDetail } from "@/types/tender";

import { at, day } from "./helpers";
import { CPV_LABELS, PLATFORMS } from "./reference";

function cpv(...codes: string[]) {
  return { cpvCodes: codes, cpv: codes.map((code) => ({ code, label: CPV_LABELS[code] })) };
}

function boamp(id: string) {
  return { source: "BOAMP", externalId: id, noticeUrl: `https://www.boamp.fr/pages/avis/?q=idweb:%22${id}%22` };
}

const STANDARD_DOCUMENTS = ["DC1 – lettre de candidature", "DC2 – déclaration du candidat", "Mémoire technique"];

export const DETAILED_TENDERS: TenderDetail[] = [
  {
    id: 1,
    title: "Installation de centrales photovoltaïques en toiture de quatre groupes scolaires",
    buyer: "Ville d'Aix-en-Provence",
    reference: "2026-TX-084",
    location: "Aix-en-Provence",
    postalCode: "13100",
    departments: ["13"],
    distanceKm: 31,
    marketTypes: ["WORKS"],
    procedureType: "OPEN",
    activities: ["PHOTOVOLTAIC", "ELECTRICITY"],
    ...cpv("09331200-0", "45261215-4", "45311000-0"),
    estimatedAmount: 1_240_000,
    publishedAt: day(-6),
    deadline: at(16, 12),
    status: "ACTIVE",
    retained: true,
    filterReason: "Retenu : département 13 ; CPV 09331200-0, 45261215-4 ; mot-clé « photovoltaïque »",
    relevanceScore: 94,
    followUpStatus: "TO_STUDY",
    followUpComment: "Visite prévue avec Karim. Vérifier la note de calcul de structure demandée au lot 2.",
    followUpBy: "Sophie Martin",
    followUpAt: at(-4, 9, 40),
    followed: true,
    ...boamp("26-118452"),
    description:
      "La Ville d'Aix-en-Provence engage la solarisation de quatre groupes scolaires dans le cadre de son plan " +
      "de transition énergétique. Les travaux comprennent la fourniture et la pose de centrales photovoltaïques en " +
      "surimposition (puissance totale de 388 kWc), les onduleurs, le raccordement au réseau public en " +
      "autoconsommation avec vente du surplus, la supervision à distance et la maintenance pendant deux ans. Les " +
      "travaux sont réalisés en site occupé.",
    lots: [
      { number: "1", title: "Groupe scolaire Jean Jaurès – 96 kWc", estimatedAmount: 310_000 },
      { number: "2", title: "Groupe scolaire Paul Cézanne – 120 kWc", estimatedAmount: 365_000 },
      { number: "3", title: "École élémentaire Les Oliviers – 72 kWc", estimatedAmount: 255_000 },
      { number: "4", title: "École maternelle Les Lavandes – 100 kWc", estimatedAmount: 310_000 },
    ],
    awardCriteria: [
      { label: "Valeur technique", weight: 60 },
      { label: "Prix", weight: 40 },
    ],
    keyInfo: {
      siteVisit: { required: true, date: at(4, 9, 30) },
      requiredDocuments: [
        ...STANDARD_DOCUMENTS,
        "Note de calcul de productible",
        "Attestation d'assurance décennale couvrant le photovoltaïque",
        "Attestation de visite",
      ],
      specialConditions:
        "Travaux en site occupé : interventions en toiture uniquement pendant les vacances de la Toussaint et de " +
        "février. Note de calcul de la charpente à fournir avant travaux pour le lot 2.",
      qualifications: ["QualiPV Elec", "Qualifelec SPV"],
      certifications: ["RGE"],
    },
    platformName: PLATFORMS.securises.name,
    platformUrl: PLATFORMS.securises.url,
    notices: [
      { externalId: "26-118452", kind: "INITIAL", publishedAt: day(-6), deadline: at(9, 12), details: null },
      {
        externalId: "26-121907",
        kind: "RECTIFICATION",
        publishedAt: day(-1),
        deadline: at(16, 12),
        details: "Report de la date limite de remise des offres et publication des réponses aux questions des candidats.",
      },
    ],
  },
  {
    id: 2,
    title: "Déploiement de 40 points de recharge pour véhicules électriques sur l'espace public",
    buyer: "Métropole Toulon Provence Méditerranée",
    reference: "2026-0412",
    location: "Toulon",
    postalCode: "83000",
    departments: ["83"],
    distanceKm: 65,
    marketTypes: ["WORKS"],
    procedureType: "OPEN",
    activities: ["EV_CHARGING", "ELECTRICITY"],
    ...cpv("31158100-9", "45310000-3", "45232000-2"),
    estimatedAmount: 1_860_000,
    publishedAt: day(-3),
    deadline: at(27, 16),
    status: "ACTIVE",
    retained: true,
    filterReason: "Retenu : département 83 ; CPV 31158100-9 ; mot-clé « IRVE »",
    relevanceScore: 91,
    followUpStatus: "NEW",
    followUpComment: null,
    followUpBy: null,
    followUpAt: null,
    followed: false,
    ...boamp("26-120388"),
    description:
      "La Métropole déploie 40 points de recharge accessibles au public sur douze communes de son territoire. Le " +
      "marché comprend la fourniture et la pose des bornes, le génie civil, les raccordements au réseau public, la " +
      "signalétique, puis la supervision et la maintenance pendant quatre ans.",
    lots: [
      { number: "1", title: "Fourniture et pose des bornes de recharge normale (22 kW)", estimatedAmount: 980_000 },
      { number: "2", title: "Bornes de recharge rapide (50 kW) et génie civil", estimatedAmount: 640_000 },
      { number: "3", title: "Supervision, exploitation et maintenance (4 ans)", estimatedAmount: 240_000 },
    ],
    awardCriteria: [
      { label: "Valeur technique", weight: 50 },
      { label: "Prix", weight: 40 },
      { label: "Performance environnementale", weight: 10 },
    ],
    keyInfo: {
      siteVisit: { required: false, date: null },
      requiredDocuments: [...STANDARD_DOCUMENTS, "Fiches techniques des bornes", "Références IRVE de moins de 3 ans"],
      specialConditions: "Interopérabilité des bornes exigée (protocole OCPP 1.6 au minimum).",
      qualifications: ["Qualifelec IRVE mention P2"],
      certifications: ["ISO 9001"],
    },
    platformName: PLATFORMS.place.name,
    platformUrl: PLATFORMS.place.url,
    notices: [{ externalId: "26-120388", kind: "INITIAL", publishedAt: day(-3), deadline: at(27, 16), details: null }],
  },
  {
    id: 3,
    title: "Rénovation énergétique du collège Jean Moulin à Aubagne",
    buyer: "Département des Bouches-du-Rhône",
    reference: "26S0731",
    location: "Aubagne",
    postalCode: "13400",
    departments: ["13"],
    distanceKm: 17,
    marketTypes: ["WORKS"],
    procedureType: "OPEN",
    activities: ["ENERGY_RENOVATION", "HEATING", "ELECTRICITY"],
    ...cpv("45453000-7", "45321000-3", "45421100-5"),
    estimatedAmount: 3_450_000,
    publishedAt: day(-20),
    deadline: at(5, 12),
    status: "ACTIVE",
    retained: true,
    filterReason: "Retenu : département 13 ; CPV 45453000-7",
    relevanceScore: 78,
    followUpStatus: "RESPONDING",
    followUpComment: "Réponse sur le lot 4 uniquement. Chiffrage DPGF en cours, retour fournisseur luminaires attendu.",
    followUpBy: "Julien Roux",
    followUpAt: at(-8, 16, 10),
    followed: true,
    ...boamp("26-109214"),
    description:
      "Rénovation énergétique globale du collège (5 200 m²) visant le niveau BBC rénovation : isolation thermique " +
      "par l'extérieur, remplacement des menuiseries, ventilation double flux, remplacement de la chaufferie gaz par " +
      "une pompe à chaleur avec appoint, relamping LED et gestion technique du bâtiment.",
    lots: [
      { number: "1", title: "Isolation thermique par l'extérieur", estimatedAmount: 1_050_000 },
      { number: "2", title: "Menuiseries extérieures", estimatedAmount: 620_000 },
      { number: "3", title: "Chauffage – ventilation", estimatedAmount: 780_000 },
      { number: "4", title: "Électricité – éclairage", estimatedAmount: 410_000 },
      { number: "5", title: "Plâtrerie – faux plafonds", estimatedAmount: 330_000 },
      { number: "6", title: "Étanchéité", estimatedAmount: 260_000 },
    ],
    awardCriteria: [
      { label: "Valeur technique (dont planning et phasage)", weight: 60 },
      { label: "Prix", weight: 40 },
    ],
    keyInfo: {
      siteVisit: { required: true, date: at(-12, 14) },
      requiredDocuments: [...STANDARD_DOCUMENTS, "Planning détaillé par phase", "Attestation de visite"],
      specialConditions:
        "Travaux en site occupé, phasage imposé sur deux périodes estivales. Clause d'insertion sociale : 5 % des " +
        "heures travaillées.",
      qualifications: ["Qualifelec – électrotechnique"],
      certifications: ["RGE"],
    },
    platformName: PLATFORMS.place.name,
    platformUrl: PLATFORMS.place.url,
    notices: [{ externalId: "26-109214", kind: "INITIAL", publishedAt: day(-20), deadline: at(5, 12), details: null }],
  },
  {
    id: 4,
    title: "Accord-cadre de maintenance des installations électriques et de chauffage-climatisation des bâtiments communaux",
    buyer: "Commune de Martigues",
    reference: "AO-2026-019",
    location: "Martigues",
    postalCode: "13500",
    departments: ["13"],
    distanceKm: 40,
    marketTypes: ["SERVICES"],
    procedureType: "OPEN",
    activities: ["MAINTENANCE", "ELECTRICITY", "HEATING"],
    ...cpv("50711000-2", "50720000-8"),
    estimatedAmount: 480_000,
    publishedAt: day(-30),
    deadline: at(2, 12),
    status: "ACTIVE",
    retained: true,
    filterReason: "Retenu : département 13 ; CPV 50711000-2",
    relevanceScore: 72,
    followUpStatus: "SUBMITTED",
    followUpComment: "Offre déposée sur le lot 1 (électricité). Pas de réponse sur le lot 2.",
    followUpBy: "Karim Benali",
    followUpAt: at(-1, 11, 20),
    followed: true,
    ...boamp("26-101577"),
    description:
      "Accord-cadre à bons de commande d'une durée de quatre ans pour la maintenance préventive et corrective des " +
      "installations électriques et de chauffage-climatisation de 64 bâtiments communaux, avec astreinte et petits " +
      "travaux.",
    lots: [
      { number: "1", title: "Électricité courants forts et courants faibles", estimatedAmount: 260_000 },
      { number: "2", title: "Chauffage, ventilation et climatisation", estimatedAmount: 220_000 },
    ],
    awardCriteria: [
      { label: "Prix", weight: 50 },
      { label: "Valeur technique", weight: 40 },
      { label: "Délai d'intervention", weight: 10 },
    ],
    keyInfo: {
      siteVisit: { required: false, date: null },
      requiredDocuments: [...STANDARD_DOCUMENTS, "Organisation de l'astreinte", "Liste des moyens humains et matériels"],
      specialConditions: "Astreinte 24 h/24 et 7 j/7, délai d'intervention de 4 heures pour les urgences.",
      qualifications: ["Habilitations électriques B2V / BR / BC", "Attestation de capacité fluides frigorigènes"],
      certifications: [],
    },
    platformName: PLATFORMS.achatpublic.name,
    platformUrl: PLATFORMS.achatpublic.url,
    notices: [{ externalId: "26-101577", kind: "INITIAL", publishedAt: day(-30), deadline: at(2, 12), details: null }],
  },
  {
    id: 5,
    title: "Rénovation de l'éclairage public en LED – tranche 2",
    buyer: "Commune de La Ciotat",
    reference: "MAPA2026-27",
    location: "La Ciotat",
    postalCode: "13600",
    departments: ["13"],
    distanceKm: 32,
    marketTypes: ["WORKS"],
    procedureType: "ADAPTED",
    activities: ["PUBLIC_LIGHTING"],
    ...cpv("45316110-9", "34928500-3"),
    estimatedAmount: 385_000,
    publishedAt: day(-12),
    deadline: at(1, 12),
    status: "ACTIVE",
    retained: true,
    filterReason: "Retenu : département 13 ; CPV 45316110-9",
    relevanceScore: 88,
    followUpStatus: "NEW",
    followUpComment: null,
    followUpBy: null,
    followUpAt: null,
    followed: false,
    ...boamp("26-113940"),
    description:
      "Deuxième tranche du programme de rénovation de l'éclairage public : remplacement de 620 luminaires " +
      "sodium par des luminaires LED avec abaissement de puissance, remplacement de 14 armoires de commande et " +
      "mise à jour du système d'information géographique de la commune.",
    lots: [],
    awardCriteria: [
      { label: "Valeur technique", weight: 60 },
      { label: "Prix", weight: 40 },
    ],
    keyInfo: {
      siteVisit: null,
      requiredDocuments: [...STANDARD_DOCUMENTS, "Fiches techniques des luminaires", "Étude photométrique"],
      specialConditions: "Maintien de l'éclairage des voies pendant toute la durée des travaux.",
      qualifications: ["Qualifelec éclairage public", "AIPR opérateur"],
      certifications: [],
    },
    platformName: PLATFORMS.info.name,
    platformUrl: PLATFORMS.info.url,
    notices: [{ externalId: "26-113940", kind: "INITIAL", publishedAt: day(-12), deadline: at(1, 12), details: null }],
  },
  {
    id: 6,
    title: "Réhabilitation énergétique de la résidence Les Lavandes (64 logements)",
    buyer: "Habitat Provence Méditerranée",
    reference: "2026-017",
    location: "Salon-de-Provence",
    postalCode: "13300",
    departments: ["13"],
    distanceKm: 53,
    marketTypes: ["WORKS"],
    procedureType: "OPEN",
    activities: ["ENERGY_RENOVATION", "HEATING"],
    ...cpv("45453000-7", "45321000-3", "45331100-7"),
    estimatedAmount: 2_180_000,
    publishedAt: day(-240),
    deadline: at(-205, 12),
    status: "CLOSED",
    result: { kind: "AWARDED" },
    retained: true,
    filterReason: "Retenu : département 13 ; CPV 45331100-7",
    relevanceScore: 81,
    followUpStatus: "SUBMITTED",
    followUpComment: "Offre remise sur le lot 3 (chauffage collectif et eau chaude sanitaire).",
    followUpBy: "Julien Roux",
    followUpAt: at(-206, 15, 30),
    followed: true,
    ...boamp("26-034871"),
    description:
      "Réhabilitation énergétique en milieu occupé d'une résidence de 64 logements construite en 1974 : isolation " +
      "des façades et des toitures-terrasses, remplacement des menuiseries, remplacement de la chaufferie collective " +
      "et de la production d'eau chaude sanitaire, ventilation hygroréglable.",
    lots: [
      { number: "1", title: "Isolation thermique par l'extérieur", estimatedAmount: 820_000 },
      { number: "2", title: "Menuiseries extérieures", estimatedAmount: 540_000 },
      { number: "3", title: "Chauffage collectif et eau chaude sanitaire", estimatedAmount: 610_000 },
      { number: "4", title: "Ventilation", estimatedAmount: 210_000 },
    ],
    awardCriteria: [
      { label: "Valeur technique", weight: 55 },
      { label: "Prix", weight: 45 },
    ],
    keyInfo: {
      siteVisit: { required: true, date: at(-225, 10) },
      requiredDocuments: [...STANDARD_DOCUMENTS, "Attestation de visite", "Références en réhabilitation occupée"],
      specialConditions: "Logements occupés : information des locataires au moins 8 jours avant chaque intervention.",
      qualifications: ["Qualibat 5313 – installations thermiques"],
      certifications: ["RGE"],
    },
    platformName: PLATFORMS.achatpublic.name,
    platformUrl: PLATFORMS.achatpublic.url,
    notices: [
      { externalId: "26-034871", kind: "INITIAL", publishedAt: day(-240), deadline: at(-205, 12), details: null },
      { externalId: "26-071206", kind: "AWARD", publishedAt: day(-150), deadline: null, details: null },
    ],
  },
];
