import type { CctpAnalysis, RcCcapAnalysis } from "@/types/specialAnalysis";

/** Example shown until the server reads the RC, the CCAP and the CCTP. */
export const RC_CCAP_EXAMPLE: RcCcapAnalysis = {
  example: true,
  rc: [
    {
      title: "Remise de l'offre",
      points: [
        { label: "Date limite", text: "05/12/2026 à 12:00, sur PLACE uniquement", source: "RC art. 7" },
        { label: "Validité des offres", text: "120 jours", source: "RC art. 2.4" },
        { label: "Variantes", text: "Non autorisées", source: "RC art. 2.5" },
        { label: "PSE", text: "PSE n° 1 : contrat de maintenance 2 ans, à chiffrer obligatoirement", source: "RC art. 2.6" },
        { label: "Négociation", text: "Possible avec les 3 meilleurs candidats", source: "RC art. 9.3" },
      ],
    },
    {
      title: "Notation",
      points: [
        { label: "Prix", text: "60 %", source: "RC art. 9.1" },
        { label: "Valeur technique", text: "40 % : méthodologie 20 %, moyens 10 %, planning 10 %", source: "RC art. 9.1" },
      ],
    },
    {
      title: "Pièces à remettre",
      points: [
        { label: "Candidature", text: "DC1, DC2, attestations d'assurance, références < 5 ans", source: "RC art. 6.1" },
        { label: "Offre", text: "AE, DPGF, mémoire technique, planning", source: "RC art. 6.2" },
      ],
    },
  ],
  ccap: [
    {
      title: "Prix et paiement",
      points: [
        { label: "Forme du prix", text: "Global et forfaitaire", source: "CCAP art. 4.1" },
        { label: "Révision", text: "Révisable, index BT40, mois zéro = mois de remise de l'offre", source: "CCAP art. 4.3" },
        { label: "Avance", text: "10 % si le marché dépasse 50 000 € HT", source: "CCAP art. 5.1" },
        { label: "Paiement", text: "30 jours, acomptes mensuels", source: "CCAP art. 5.2" },
      ],
    },
    {
      title: "Garanties et pénalités",
      points: [
        { label: "Retenue de garantie", text: "5 %, remplaçable par une caution", source: "CCAP art. 6" },
        { label: "Pénalités de retard", text: "1/1000 du montant HT par jour calendaire", source: "CCAP art. 9.1" },
        { label: "Absence en réunion", text: "150 € par absence", source: "CCAP art. 9.3" },
      ],
    },
    {
      title: "Délais et exécution",
      points: [
        { label: "Délai global", text: "4 mois dont 1 mois de préparation", source: "CCAP art. 8.1" },
        { label: "Sous-traitance", text: "Autorisée, à déclarer (DC4)", source: "CCAP art. 3.4" },
        { label: "Assurances", text: "RC et décennale exigées avant le démarrage", source: "CCAP art. 11" },
      ],
    },
  ],
};

export const CCTP_EXAMPLE: CctpAnalysis = {
  example: true,
  equipment: [
    { designation: "Groupe de production d'eau glacée 350 kW", requirement: "Fluide R1234ze, ou équivalent", quantity: "2", source: "CCTP § 3.2" },
    { designation: "Groupe provisoire de location 300 kW", requirement: null, quantity: "1 (4 mois)", source: "CCTP § 2.5" },
    { designation: "Pompes doubles à vitesse variable", requirement: "Grundfos TPED ou équivalent", quantity: "2", source: "CCTP § 3.4" },
    { designation: "Armoire électrique de puissance", requirement: "Indice IP55", quantity: "1", source: "CCTP § 4.1" },
    { designation: "Câbles d'alimentation U1000 R2V", requirement: null, quantity: "≈ 120 ml", source: "CCTP § 4.3" },
  ],
  choices: [
    { label: "Fluide", text: "Fluide frigorigène à faible PRG (< 10) imposé", source: "CCTP § 3.2" },
    { label: "Régulation", text: "Raccordement à la GTB existante en BACnet", source: "CCTP § 3.6" },
    { label: "Protection", text: "Départs dédiés avec différentiel 300 mA", source: "CCTP § 4.2" },
  ],
  power: [
    { item: "Groupe froid n° 1", power: "112 kW", note: "Démarrage progressif", source: "CCTP § 3.2" },
    { item: "Groupe froid n° 2", power: "112 kW", note: null, source: "CCTP § 3.2" },
    { item: "Pompes", power: "2 × 5,5 kW", note: null, source: "CCTP § 3.4" },
    { item: "Groupe provisoire", power: "100 kW", note: "Sur le TGBT existant pendant les travaux", source: "CCTP § 2.5" },
  ],
  totalPower: "235 kW en régime définitif : vérifier la réserve du TGBT (400 A annoncés)",
  site: [
    { label: "Site occupé", text: "Médiathèque ouverte au public pendant les travaux", source: "CCTP § 2.1" },
    { label: "Accès", text: "Toiture terrasse : levage par grue, voirie à fermer", source: "CCTP § 2.3" },
    { label: "Horaires", text: "Travaux bruyants avant 10 h ou le lundi", source: "CCTP § 2.4" },
    { label: "Continuité", text: "Production d'eau glacée maintenue de mai à septembre", source: "CCTP § 2.5" },
  ],
  vigilance: [
    { label: "Puissance", text: "Réserve du TGBT non prouvée : risque de remplacer le disjoncteur général", source: "CCTP § 4.1", level: "HIGH" },
    { label: "Levage", text: "Grue et arrêté de voirie à la charge du titulaire", source: "CCTP § 2.3", level: "HIGH" },
    { label: "Désamiantage", text: "Repérage amiante non joint au DCE", source: null, level: "MEDIUM" },
    { label: "DOE", text: "DOE en 3 exemplaires papier et un numérique", source: "CCTP § 6", level: "LOW" },
  ],
};
