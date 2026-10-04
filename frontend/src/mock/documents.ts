/**
 * Consultation files (DCE) of the demo, uploaded for part of the tenders the
 * team works on, and a few page excerpts of each document for the search in
 * the documents. No real file exists.
 */

import { formatDateTime } from "@/lib/format";
import type { DocumentInfo, Dossier, PieceType } from "@/types/document";
import type { TenderDetail } from "@/types/tender";

import { at, daysFromToday, seededRandom } from "./helpers";
import { COLLEAGUES, MOCK_TENDERS } from "./tenders";

const rnd = seededRandom(20261006);

export type PageExcerpt = { pageNumber: number; text: string };

/** One technical sentence of the CCTP, by main activity. */
const TECHNICAL_CLAUSES: Record<string, string> = {
  ELECTRICITY:
    "Les installations seront réalisées conformément à la norme NF C 15-100 ; le TGBT sera dimensionné avec une " +
    "réserve de 20 %.",
  PHOTOVOLTAIC:
    "Les modules photovoltaïques seront de technologie monocristalline, d'une puissance unitaire minimale de " +
    "400 Wc, certifiés IEC 61215 et IEC 61730.",
  EV_CHARGING:
    "Les bornes seront conformes à la norme IEC 61851 et communiqueront avec la supervision par le protocole " +
    "OCPP 1.6 au minimum.",
  HEATING:
    "Les chaudières à condensation auront un rendement minimal de 108 % sur PCI ; la régulation sera " +
    "communicante (BACnet).",
  AIR_CONDITIONING: "Le fluide frigorigène aura un potentiel de réchauffement global inférieur à 750 (R32 ou équivalent).",
  ENERGY_RENOVATION: "L'isolant des façades aura une résistance thermique minimale R = 3,7 m².K/W.",
  PUBLIC_LIGHTING:
    "Les luminaires LED auront une température de couleur de 3000 K au plus et une efficacité lumineuse " +
    "supérieure à 140 lm/W.",
  NETWORKS: "Les fourreaux seront posés à 0,80 m de profondeur au minimum sous chaussée, avec grillage avertisseur.",
  VRD: "La structure de chaussée sera dimensionnée pour un trafic de classe T3.",
  MAINTENANCE:
    "Les visites de maintenance préventive ont lieu deux fois par an ; le délai d'intervention en urgence est de " +
    "4 heures.",
  STUDIES: "Le titulaire réalise une simulation thermique dynamique de l'état existant et des scénarios proposés.",
};

type File = { fileName: string; pieceType: PieceType; pages: number; lot?: string };

function filesOf(tender: TenderDetail): File[] {
  const works = tender.marketTypes.includes("WORKS");
  const lots = tender.lots;
  const files: File[] = [
    { fileName: "01 - RC.pdf", pieceType: "RC", pages: rnd.between(10, 24) },
    { fileName: "02 - AE.docx", pieceType: "AE", pages: rnd.between(4, 6) },
    { fileName: "03 - CCAP.pdf", pieceType: "CCAP", pages: rnd.between(20, 50) },
  ];
  if (lots.length) {
    for (const lot of lots) {
      files.push({
        fileName: `04 - CCTP lot ${lot.number}.pdf`,
        pieceType: "CCTP",
        pages: rnd.between(15, 70),
        lot: lot.title ?? undefined,
      });
    }
  } else {
    files.push({ fileName: "04 - CCTP.pdf", pieceType: "CCTP", pages: rnd.between(15, 70) });
  }
  if (works) {
    for (const lot of lots.length ? lots : [null]) {
      files.push({ fileName: lot ? `05 - DPGF lot ${lot.number}.xlsx` : "05 - DPGF.xlsx", pieceType: "DPGF", pages: 0 });
    }
    if (rnd.chance(0.6)) {
      files.push({ fileName: "06 - Plans.pdf", pieceType: "PLAN", pages: rnd.between(4, 18) });
    }
  } else {
    files.push({ fileName: "05 - BPU.xlsx", pieceType: "BPU", pages: 0 });
    files.push({ fileName: "06 - DQE.xlsx", pieceType: "DQE", pages: 0 });
  }
  if (rnd.chance(0.5)) {
    files.push({ fileName: "07 - Cadre de réponse technique.docx", pieceType: "OTHER", pages: rnd.between(6, 10) });
  }
  return files;
}

function documentOf(file: File, id: number): DocumentInfo {
  const extension = file.fileName.split(".").pop();
  const base = { id, fileName: file.fileName, pieceType: file.pieceType, pieceTypeCorrected: false };
  if (extension === "xlsx") {
    return {
      ...base,
      sizeBytes: rnd.between(60_000, 400_000),
      state: "NOT_ANALYZED",
      stateDetail: "Tableur conservé, non analysé dans cette version",
      pageCount: null,
      readable: false,
    };
  }
  if (file.pieceType === "PLAN") {
    return {
      ...base,
      sizeBytes: file.pages * rnd.between(300_000, 800_000),
      state: "SCANNED",
      stateDetail: "Plans : peu de texte exploitable",
      pageCount: file.pages,
      readable: true,
    };
  }
  if (rnd.chance(0.04)) {
    return {
      ...base,
      sizeBytes: file.pages * rnd.between(50_000, 110_000),
      state: "FAILED",
      stateDetail: "PDF protégé par un mot de passe : non analysé",
      pageCount: null,
      readable: false,
    };
  }
  return {
    ...base,
    sizeBytes: file.pages * rnd.between(extension === "docx" ? 15_000 : 50_000, extension === "docx" ? 30_000 : 110_000),
    state: "READ",
    stateDetail: null,
    pageCount: file.pages,
    readable: true,
  };
}

function excerptsOf(file: File, tender: TenderDetail): PageExcerpt[] {
  const info = tender.keyInfo;
  const visit = info?.siteVisit;
  switch (file.pieceType) {
    case "RC":
      return [
        { pageNumber: 2, text: `Objet de la consultation : ${tender.title}. Pouvoir adjudicateur : ${tender.buyer}.` },
        {
          pageNumber: 4,
          text: `Le dossier de candidature comprendra : ${(info?.requiredDocuments ?? ["DC1", "DC2"]).join(", ")}.`,
        },
        {
          pageNumber: 5,
          text: visit?.required
            ? `La visite du site est obligatoire. Elle aura lieu le ${formatDateTime(visit.date)}. Une attestation ` +
              "de visite sera remise aux candidats et devra être jointe à l'offre."
            : "La visite du site est facultative. Les candidats peuvent la demander auprès du service technique.",
        },
        {
          pageNumber: 7,
          text:
            "Les offres seront jugées selon les critères suivants : " +
            ((tender.awardCriteria ?? []).map((c) => `${c.label} (${c.weight} %)`).join(", ") || "prix (100 %)") +
            ".",
        },
      ];
    case "CCAP":
      return [
        {
          pageNumber: 3,
          text: `Délai d'exécution : ${rnd.pick(["4", "6", "8", "12"])} mois à compter de l'ordre de service.`,
        },
        { pageNumber: 9, text: "Pénalités de retard : 1/1000e du montant du marché par jour calendaire de retard." },
        { pageNumber: 12, text: "Une retenue de garantie de 5 % est appliquée sur chaque acompte." },
      ];
    case "CCTP":
      return [
        { pageNumber: 4, text: `Le présent cahier a pour objet : ${file.lot ?? tender.title}.` },
        { pageNumber: 8, text: TECHNICAL_CLAUSES[tender.activities?.[0] ?? ""] ?? "" },
        {
          pageNumber: 15,
          text: `Qualifications exigées : ${(info?.qualifications ?? []).join(", ") || "références équivalentes"}.`,
        },
      ];
    case "AE":
      return [{ pageNumber: 1, text: `Acte d'engagement – ${tender.buyer} – ${tender.reference ?? tender.title}.` }];
    case "OTHER":
      return [
        {
          pageNumber: 1,
          text: "Cadre de réponse technique : moyens humains, méthodologie, planning et gestion des déchets de chantier.",
        },
      ];
    default:
      return [];
  }
}

/** Tenders the team worked on have a consultation file, written ones always. */
function hasDossier(tender: TenderDetail): boolean {
  if ([1, 3, 4, 6].includes(tender.id)) {
    return true;
  }
  if (tender.id <= 6 || !tender.retained) {
    return false;
  }
  if (tender.result) {
    return tender.followUpStatus === "SUBMITTED" && rnd.chance(0.7);
  }
  return ["TO_STUDY", "RESPONDING", "SUBMITTED"].includes(tender.followUpStatus) && rnd.chance(0.65);
}

export const MOCK_DOSSIERS: Record<number, Dossier[]> = {};

/** Page excerpts by document id. */
export const MOCK_PAGES: Record<number, PageExcerpt[]> = {};

for (const tender of MOCK_TENDERS) {
  if (!hasDossier(tender)) {
    MOCK_DOSSIERS[tender.id] = [];
    continue;
  }
  const files = filesOf(tender);
  const documents = files.map((file, index) => documentOf(file, tender.id * 100 + index + 1));
  files.forEach((file, index) => {
    if (documents[index].state !== "FAILED") {
      MOCK_PAGES[documents[index].id] = excerptsOf(file, tender);
    }
  });
  const uploadedOn = Math.min(0, daysFromToday(tender.publishedAt ?? "") + rnd.between(1, 5));
  MOCK_DOSSIERS[tender.id] = [
    {
      id: tender.id,
      version: 1,
      status: "DONE",
      errorMessage: null,
      uploadedBy: tender.followUpBy ?? rnd.pick(COLLEAGUES),
      uploadedAt: at(uploadedOn, rnd.between(8, 17), 15),
      processedAt: at(uploadedOn, 18, 0),
      documents,
    },
  ];
}
