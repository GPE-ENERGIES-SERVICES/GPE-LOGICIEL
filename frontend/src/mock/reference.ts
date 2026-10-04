/**
 * Reference data of the demo: places, buyers, platforms, CPV codes and
 * companies. Communes and public authorities are real; housing bodies,
 * syndicates and companies are invented.
 */

export type Place = {
  city: string;
  postalCode: string;
  department: string;
  distanceKm: number;
  /** The commune as a buyer. */
  commune: string;
  intercommunality: string;
};

/** Road distance from Marseille, the reference point of the demo data. */
export const PLACES: Place[] = [
  {
    city: "Marseille",
    postalCode: "13003",
    department: "13",
    distanceKm: 3,
    commune: "Ville de Marseille",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Marseille",
    postalCode: "13013",
    department: "13",
    distanceKm: 11,
    commune: "Ville de Marseille",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Aix-en-Provence",
    postalCode: "13100",
    department: "13",
    distanceKm: 31,
    commune: "Ville d'Aix-en-Provence",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Aubagne",
    postalCode: "13400",
    department: "13",
    distanceKm: 17,
    commune: "Commune d'Aubagne",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "La Ciotat",
    postalCode: "13600",
    department: "13",
    distanceKm: 32,
    commune: "Commune de La Ciotat",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Martigues",
    postalCode: "13500",
    department: "13",
    distanceKm: 40,
    commune: "Commune de Martigues",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Vitrolles",
    postalCode: "13127",
    department: "13",
    distanceKm: 26,
    commune: "Commune de Vitrolles",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Marignane",
    postalCode: "13700",
    department: "13",
    distanceKm: 27,
    commune: "Commune de Marignane",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Istres",
    postalCode: "13800",
    department: "13",
    distanceKm: 55,
    commune: "Commune d'Istres",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Salon-de-Provence",
    postalCode: "13300",
    department: "13",
    distanceKm: 53,
    commune: "Commune de Salon-de-Provence",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Arles",
    postalCode: "13200",
    department: "13",
    distanceKm: 92,
    commune: "Ville d'Arles",
    intercommunality: "Communauté d'agglomération Arles Crau Camargue Montagnette",
  },
  {
    city: "Gardanne",
    postalCode: "13120",
    department: "13",
    distanceKm: 26,
    commune: "Commune de Gardanne",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Toulon",
    postalCode: "83000",
    department: "83",
    distanceKm: 65,
    commune: "Ville de Toulon",
    intercommunality: "Métropole Toulon Provence Méditerranée",
  },
  {
    city: "La Seyne-sur-Mer",
    postalCode: "83500",
    department: "83",
    distanceKm: 62,
    commune: "Commune de La Seyne-sur-Mer",
    intercommunality: "Métropole Toulon Provence Méditerranée",
  },
  {
    city: "Hyères",
    postalCode: "83400",
    department: "83",
    distanceKm: 84,
    commune: "Commune d'Hyères",
    intercommunality: "Métropole Toulon Provence Méditerranée",
  },
  {
    city: "Fréjus",
    postalCode: "83600",
    department: "83",
    distanceKm: 136,
    commune: "Commune de Fréjus",
    intercommunality: "Estérel Côte d'Azur Agglomération",
  },
  {
    city: "Draguignan",
    postalCode: "83300",
    department: "83",
    distanceKm: 121,
    commune: "Commune de Draguignan",
    intercommunality: "Dracénie Provence Verdon Agglomération",
  },
  {
    city: "Brignoles",
    postalCode: "83170",
    department: "83",
    distanceKm: 64,
    commune: "Commune de Brignoles",
    intercommunality: "Communauté d'agglomération de la Provence Verte",
  },
  {
    city: "Avignon",
    postalCode: "84000",
    department: "84",
    distanceKm: 100,
    commune: "Ville d'Avignon",
    intercommunality: "Communauté d'agglomération du Grand Avignon",
  },
  {
    city: "Carpentras",
    postalCode: "84200",
    department: "84",
    distanceKm: 108,
    commune: "Commune de Carpentras",
    intercommunality: "Communauté d'agglomération Ventoux-Comtat Venaissin",
  },
  {
    city: "Cavaillon",
    postalCode: "84300",
    department: "84",
    distanceKm: 75,
    commune: "Commune de Cavaillon",
    intercommunality: "Luberon Monts de Vaucluse Agglomération",
  },
  {
    city: "Pertuis",
    postalCode: "84120",
    department: "84",
    distanceKm: 48,
    commune: "Commune de Pertuis",
    intercommunality: "Métropole Aix-Marseille-Provence",
  },
  {
    city: "Nice",
    postalCode: "06000",
    department: "06",
    distanceKm: 200,
    commune: "Ville de Nice",
    intercommunality: "Métropole Nice Côte d'Azur",
  },
  {
    city: "Cannes",
    postalCode: "06400",
    department: "06",
    distanceKm: 164,
    commune: "Ville de Cannes",
    intercommunality: "Communauté d'agglomération Cannes Pays de Lérins",
  },
  {
    city: "Antibes",
    postalCode: "06600",
    department: "06",
    distanceKm: 177,
    commune: "Ville d'Antibes",
    intercommunality: "Communauté d'agglomération Sophia Antipolis",
  },
  {
    city: "Manosque",
    postalCode: "04100",
    department: "04",
    distanceKm: 87,
    commune: "Commune de Manosque",
    intercommunality: "Durance Luberon Verdon Agglomération",
  },
  {
    city: "Digne-les-Bains",
    postalCode: "04000",
    department: "04",
    distanceKm: 144,
    commune: "Commune de Digne-les-Bains",
    intercommunality: "Provence Alpes Agglomération",
  },
  {
    city: "Gap",
    postalCode: "05000",
    department: "05",
    distanceKm: 182,
    commune: "Ville de Gap",
    intercommunality: "Communauté d'agglomération Gap-Tallard-Durance",
  },
];

/** Places outside the company's area: their tenders are excluded by the filter. */
export const DISTANT_PLACES: Place[] = [
  {
    city: "Nîmes",
    postalCode: "30000",
    department: "30",
    distanceKm: 123,
    commune: "Ville de Nîmes",
    intercommunality: "Nîmes Métropole",
  },
  {
    city: "Montpellier",
    postalCode: "34000",
    department: "34",
    distanceKm: 170,
    commune: "Ville de Montpellier",
    intercommunality: "Montpellier Méditerranée Métropole",
  },
  {
    city: "Lyon",
    postalCode: "69003",
    department: "69",
    distanceKm: 315,
    commune: "Ville de Lyon",
    intercommunality: "Métropole de Lyon",
  },
  {
    city: "Grenoble",
    postalCode: "38000",
    department: "38",
    distanceKm: 275,
    commune: "Ville de Grenoble",
    intercommunality: "Grenoble-Alpes Métropole",
  },
];

/** Departments where the company works, as in its filter criteria. */
export const COMPANY_DEPARTMENTS = ["04", "05", "06", "13", "83", "84"];

/** "des Bouches-du-Rhône", to write "Département des Bouches-du-Rhône". */
export const DEPARTMENT_OF: Record<string, string> = {
  "04": "des Alpes-de-Haute-Provence",
  "05": "des Hautes-Alpes",
  "06": "des Alpes-Maritimes",
  "13": "des Bouches-du-Rhône",
  "30": "du Gard",
  "34": "de l'Hérault",
  "38": "de l'Isère",
  "69": "du Rhône",
  "83": "du Var",
  "84": "de Vaucluse",
};

export const REGION_OF: Record<string, string> = {
  "04": "Région Provence-Alpes-Côte d'Azur",
  "05": "Région Provence-Alpes-Côte d'Azur",
  "06": "Région Provence-Alpes-Côte d'Azur",
  "13": "Région Provence-Alpes-Côte d'Azur",
  "83": "Région Provence-Alpes-Côte d'Azur",
  "84": "Région Provence-Alpes-Côte d'Azur",
  "30": "Région Occitanie",
  "34": "Région Occitanie",
  "38": "Région Auvergne-Rhône-Alpes",
  "69": "Région Auvergne-Rhône-Alpes",
};

export const HOUSING_BODY_OF: Record<string, string> = {
  "04": "Habitat Alpes du Sud",
  "05": "Habitat Alpes du Sud",
  "06": "Azur Habitat Social",
  "13": "Habitat Provence Méditerranée",
  "30": "Gard Habitat Social",
  "34": "Hérault Logis",
  "38": "Isère Logement Social",
  "69": "Rhône Habitat Social",
  "83": "Logis du Var",
  "84": "Vaucluse Logement",
};

export type Platform = { name: string; url: string };

export const PLATFORMS: Record<string, Platform> = {
  region: { name: "Marchés publics de la Région Sud", url: "https://marchespublics.maregionsud.fr" },
  metropole: { name: "Plateforme de la Métropole Aix-Marseille-Provence", url: "https://marchespublics.ampmetropole.fr" },
  place: { name: "PLACE (marches-publics.gouv.fr)", url: "https://www.marches-publics.gouv.fr" },
  achatpublic: { name: "achatpublic.com", url: "https://www.achatpublic.com" },
  securises: { name: "marches-securises.fr", url: "https://www.marches-securises.fr" },
  info: { name: "marches-publics.info", url: "https://www.marches-publics.info" },
};

export const CPV_LABELS: Record<string, string> = {
  "09331200-0": "Modules solaires photovoltaïques",
  "09332000-5": "Installations solaires",
  "31158100-9": "Chargeurs de batteries",
  "34928500-3": "Matériel d'éclairage public",
  "39717200-3": "Appareils de climatisation",
  "42511110-5": "Pompes à chaleur",
  "45112500-0": "Travaux de terrassement",
  "45231400-9": "Travaux de construction de lignes électriques",
  "45232000-2": "Travaux auxiliaires pour canalisations et câbles",
  "45233140-2": "Travaux routiers",
  "45261215-4": "Travaux de couverture de toits de panneaux solaires",
  "45310000-3": "Travaux d'équipement électrique",
  "45311000-0": "Travaux de câblage et d'installations électriques",
  "45312100-8": "Travaux d'installation d'alarmes incendie",
  "45315600-4": "Installations basse tension",
  "45316110-9": "Installation d'équipements d'éclairage public",
  "45321000-3": "Travaux d'isolation thermique",
  "45331000-6": "Travaux d'installation de chauffage, de ventilation et de climatisation",
  "45331100-7": "Travaux d'installation de chauffage central",
  "45331220-4": "Travaux d'installation de climatisation",
  "45421100-5": "Pose de portes et de fenêtres",
  "45453000-7": "Travaux de remise en état et de remise à neuf",
  "50711000-2": "Services de réparation et d'entretien d'équipements électriques de bâtiments",
  "50720000-8": "Services de réparation et d'entretien de chauffage central",
  "71221000-3": "Services d'architecte pour les bâtiments",
  "71314300-5": "Services de conseil en rendement énergétique",
  "71321000-4": "Services de conception technique d'installations mécaniques et électriques de bâtiments",
};

/** The company using the software, as it appears in award notices. */
export const OWN_COMPANY = "GPE Énergies Services";

/** Competitors of the demo, with the activities they answer. */
export const COMPETITORS: { name: string; city: string; activities: string[] }[] = [
  {
    name: "Sudelec Énergies",
    city: "Vitrolles",
    activities: ["ELECTRICITY", "PUBLIC_LIGHTING", "EV_CHARGING", "MAINTENANCE"],
  },
  { name: "Provence Solaire Installation", city: "Aix-en-Provence", activities: ["PHOTOVOLTAIC", "ELECTRICITY"] },
  { name: "Électricité Générale du Littoral", city: "La Seyne-sur-Mer", activities: ["ELECTRICITY", "MAINTENANCE"] },
  { name: "Thermatec Méditerranée", city: "Marseille", activities: ["HEATING", "AIR_CONDITIONING", "MAINTENANCE"] },
  { name: "Climatisation Durance Services", city: "Pertuis", activities: ["AIR_CONDITIONING", "HEATING"] },
  { name: "Lumiroute Sud", city: "Avignon", activities: ["PUBLIC_LIGHTING", "NETWORKS"] },
  { name: "Réseaux et Travaux Provençaux", city: "Gardanne", activities: ["NETWORKS", "VRD", "PUBLIC_LIGHTING"] },
  { name: "Bâtir Durable Provence", city: "Aubagne", activities: ["ENERGY_RENOVATION", "HEATING"] },
  { name: "Varelec", city: "Toulon", activities: ["ELECTRICITY", "EV_CHARGING", "PHOTOVOLTAIC"] },
  { name: "Azur Bornes Services", city: "Nice", activities: ["EV_CHARGING", "MAINTENANCE"] },
  { name: "Terrassements Méridionaux", city: "Salon-de-Provence", activities: ["VRD", "NETWORKS"] },
  { name: "BET Énergie Conseil", city: "Marseille", activities: ["STUDIES"] },
];

/**
 * Trade vocabulary of the search: a term, or an expression of several words,
 * also finds the words listed. Written without accents, in lower case.
 */
export const SEARCH_SYNONYMS: Record<string, string[]> = {
  photovoltaique: ["solaire", "panneaux solaires", "centrale solaire", "production solaire"],
  pv: ["photovoltaique", "solaire"],
  solaire: ["photovoltaique"],
  "panneaux solaires": ["photovoltaique", "solaire"],
  "panneau solaire": ["photovoltaique", "solaire"],
  "centrale solaire": ["photovoltaique", "solaire"],
  "production solaire": ["photovoltaique", "solaire"],
  irve: ["recharge", "vehicules electriques"],
  recharge: ["irve"],
  "borne de recharge": ["irve", "recharge"],
  "bornes de recharge": ["irve", "recharge"],
  "vehicule electrique": ["irve", "recharge", "vehicules electriques"],
  cvc: ["chauffage", "chaufferie", "pompes a chaleur", "climatisation", "rafraichissement", "groupes froids", "ventilation"],
  chauffage: ["chaufferie", "chaudiere", "pompes a chaleur"],
  pac: ["pompes a chaleur", "pompe a chaleur"],
  "pompe a chaleur": ["pompes a chaleur"],
  climatisation: ["rafraichissement", "groupes froids"],
  eclairage: ["luminaires", "led"],
  led: ["eclairage", "luminaires"],
  electricite: ["electrique", "tgbt", "courants forts", "courants faibles"],
  vrd: ["voirie", "reseaux divers", "terrassements"],
  reseaux: ["enfouissement", "reseaux secs", "telecommunication"],
  isolation: ["isolation thermique", "rehabilitation thermique"],
  "renovation energetique": ["performance energetique", "rehabilitation thermique", "isolation"],
  maintenance: ["entretien", "exploitation"],
  audit: ["diagnostic", "etude"],
};
