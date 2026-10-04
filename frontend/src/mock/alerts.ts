/** Saved searches of the demo user. */

import type { Alert } from "@/types/alert";

import { at } from "./helpers";

export const MOCK_ALERTS: Alert[] = [
  {
    id: 1,
    name: "GPE Énergie PACA",
    keywords: ["photovoltaïque", "électricité", "IRVE"],
    region: "Provence-Alpes-Côte d'Azur",
    departments: ["13", "83", "84"],
    marketTypes: ["WORKS"],
    activities: [],
    cpvPrefixes: [],
    minAmount: null,
    maxAmount: null,
    active: true,
    createdAt: at(-90, 10),
    lastViewedAt: at(-4, 9),
  },
  {
    id: 2,
    name: "Photovoltaïque",
    keywords: ["photovoltaïque", "solaire"],
    region: "Provence-Alpes-Côte d'Azur",
    departments: [],
    marketTypes: [],
    activities: ["PHOTOVOLTAIC"],
    cpvPrefixes: ["0933"],
    minAmount: 50_000,
    maxAmount: null,
    active: true,
    createdAt: at(-60, 14),
    lastViewedAt: at(-7, 8, 30),
  },
  {
    id: 3,
    name: "IRVE",
    keywords: ["borne de recharge", "IRVE"],
    region: null,
    departments: [],
    marketTypes: ["WORKS", "SERVICES"],
    activities: ["EV_CHARGING"],
    cpvPrefixes: [],
    minAmount: null,
    maxAmount: null,
    active: true,
    createdAt: at(-30, 11),
    lastViewedAt: at(-2, 17),
  },
];
