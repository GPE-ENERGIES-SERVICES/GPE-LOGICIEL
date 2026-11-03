/** The parts of the company, in the order of the sub-navigation. */
export const COMPANY_PAGES: { href: string; label: string }[] = [
  { href: "/company", label: "Vue d'ensemble" },
  { href: "/company/identity", label: "Identité" },
  { href: "/company/activities", label: "Activités" },
  { href: "/company/staff", label: "Personnel" },
  { href: "/company/equipment", label: "Matériel" },
  { href: "/company/certifications", label: "Certifications" },
  { href: "/company/references", label: "Références" },
  { href: "/company/documents", label: "Documents entreprise" },
  { href: "/company/economics", label: "Économie" },
];

export const ACTIVITY_KIND_LABELS: Record<string, string> = {
  DOMAIN: "Domaine d'activité",
  TRADE: "Métier",
  SKILL: "Compétence technique",
  SERVICE: "Type de prestation",
  SPECIALTY: "Spécialité, savoir-faire",
};

export const CREDENTIAL_KIND_LABELS: Record<string, string> = {
  HABILITATION: "Habilitation",
  QUALIFICATION: "Qualification",
  CERTIFICATION: "Certification",
  TRAINING: "Formation",
};

export const EQUIPMENT_CATEGORY_LABELS: Record<string, string> = {
  VEHICLE: "Véhicule",
  TOOL: "Matériel et outillage",
  EQUIPMENT: "Équipement technique",
  SITE: "Moyen de chantier",
};

export const EQUIPMENT_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: "Disponible",
  IN_USE: "En service",
  MAINTENANCE: "En maintenance",
  OUT_OF_SERVICE: "Hors service",
};

export const CERTIFICATION_KIND_LABELS: Record<string, string> = {
  CERTIFICATION: "Certification",
  QUALIFICATION: "Qualification",
  LABEL: "Label",
};

export const DOCUMENT_CATEGORY_LABELS: Record<string, string> = {
  KBIS: "Extrait Kbis",
  INSURANCE: "Attestation d'assurance",
  TAX: "Attestation fiscale",
  SOCIAL: "Attestation sociale",
  VIGILANCE: "Attestation de vigilance",
  CERTIFICATE: "Certificat, qualification",
  FINANCIAL: "Document financier",
  SAFETY: "Sécurité et prévention",
  STAFF: "Moyens humains",
  EQUIPMENT: "Moyens matériels",
  REFERENCES: "Présentation, réalisations",
  OTHER: "Autre justificatif",
};

export const EXPIRY_KIND_LABELS: Record<string, { label: string; href: string }> = {
  DOCUMENT: { label: "Document", href: "/company/documents" },
  CERTIFICATION: { label: "Certification", href: "/company/certifications" },
  CREDENTIAL: { label: "Habilitation", href: "/company/staff" },
  EQUIPMENT: { label: "Contrôle de matériel", href: "/company/equipment" },
};
