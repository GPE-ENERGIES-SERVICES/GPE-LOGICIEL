import type { AiAction, CompanyBlock, MemoirSection, MemoirStatus, RequirementCategory } from "@/types/memoir";

export const MEMOIR_STATUS_LABELS: Record<MemoirStatus, string> = {
  DRAFT: "Brouillon",
  OUTLINE_TO_VALIDATE: "Sommaire à valider",
  READY_TO_WRITE: "Prêt à rédiger",
  WRITING: "Rédaction en cours",
  TO_REVIEW: "À relire",
  DONE: "Terminé",
};

export const REQUIREMENT_LABELS: Record<RequirementCategory, string> = {
  CRITERION: "Critère",
  SUBCRITERION: "Sous-critère",
  MANDATORY: "Obligatoire",
  FORM: "Forme",
  MEANS: "Moyens",
  DEADLINE: "Délai",
  METHOD: "Méthode",
  OTHER: "Autre",
};

export const AI_ACTION_LABELS: Record<AiAction, string> = {
  WRITE: "Rédiger la section",
  REWRITE: "Réécrire",
  EXPAND: "Développer",
  SHORTEN: "Raccourcir",
  IMPROVE: "Améliorer la formulation",
  CORRECT: "Corriger le texte",
  DETAIL: "Ajouter des précisions",
  ADAPT: "Adapter à une exigence du DCE",
};

export const COMPANY_BLOCK_LABELS: Record<CompanyBlock, string> = {
  PRESENTATION: "Présentation de l'entreprise",
  STAFF: "Moyens humains",
  EQUIPMENT: "Moyens matériels",
  CERTIFICATIONS: "Certifications et qualifications",
  REFERENCES: "Références",
};

export const PLACEHOLDER_LABELS: Record<string, string> = {
  photo: "Photo à insérer",
  schema: "Schéma à insérer",
  tableau: "Tableau à insérer",
  plan: "Plan à insérer",
};

type Placement = { id: number; parentId: number | null; position: number };

/** The children of each section, in order, from the sections in reading order. */
function tree(sections: MemoirSection[]): Map<number | null, number[]> {
  const children = new Map<number | null, number[]>();
  for (const section of sections) {
    children.set(section.parentId, [...(children.get(section.parentId) ?? []), section.id]);
  }
  return children;
}

function placements(children: Map<number | null, number[]>): Placement[] {
  const result: Placement[] = [];
  for (const [parentId, ids] of children) {
    ids.forEach((id, index) => result.push({ id, parentId, position: index + 1 }));
  }
  return result;
}

/**
 * The outline after a move: up or down among its siblings, in (under the
 * section before it) or out (after its parent). Null when the move is not possible.
 */
export function moved(sections: MemoirSection[], id: number, move: "up" | "down" | "in" | "out"): Placement[] | null {
  const section = sections.find((candidate) => candidate.id === id);
  if (!section) {
    return null;
  }
  const children = tree(sections);
  const siblings = [...(children.get(section.parentId) ?? [])];
  const index = siblings.indexOf(id);
  if (move === "up" || move === "down") {
    const other = index + (move === "up" ? -1 : 1);
    if (other < 0 || other >= siblings.length) {
      return null;
    }
    [siblings[index], siblings[other]] = [siblings[other], siblings[index]];
    children.set(section.parentId, siblings);
    return placements(children);
  }
  if (move === "in") {
    if (index === 0 || section.level >= 4) {
      return null;
    }
    const newParent = siblings[index - 1];
    children.set(section.parentId, siblings.filter((candidate) => candidate !== id));
    children.set(newParent, [...(children.get(newParent) ?? []), id]);
    return placements(children);
  }
  if (section.parentId === null) {
    return null;
  }
  const parent = sections.find((candidate) => candidate.id === section.parentId);
  const parentSiblings = [...(children.get(parent?.parentId ?? null) ?? [])];
  children.set(section.parentId, siblings.filter((candidate) => candidate !== id));
  parentSiblings.splice(parentSiblings.indexOf(section.parentId) + 1, 0, id);
  children.set(parent?.parentId ?? null, parentSiblings);
  return placements(children);
}
