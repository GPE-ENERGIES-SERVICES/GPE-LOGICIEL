/**
 * Awarded contracts of the demo, one per lot of each past consultation that
 * was awarded. The winner answers the activity of the consultation; the
 * company wins some of the consultations it answered.
 */

import type { AwardedContract } from "@/types/award";
import type { TenderDetail } from "@/types/tender";

import { seededRandom } from "./helpers";
import { COMPETITORS, OWN_COMPANY } from "./reference";
import { MOCK_TENDERS } from "./tenders";

const rnd = seededRandom(20261005);

/** Hand-written result of consultation 6: the company won the heating lot. */
const FIXED_WINNERS: Record<string, { winner: string; city: string; amount: number }> = {
  "6/1": { winner: "Bâtir Durable Provence", city: "Aubagne", amount: 798_500 },
  "6/2": { winner: "Bâtir Durable Provence", city: "Aubagne", amount: 552_000 },
  "6/3": { winner: OWN_COMPANY, city: "Marseille", amount: 587_400 },
  "6/4": { winner: "Thermatec Méditerranée", city: "Marseille", amount: 196_300 },
};

function winnerFor(tender: TenderDetail): { winner: string; city: string } {
  if (tender.followUpStatus === "SUBMITTED" && rnd.chance(0.45)) {
    return { winner: OWN_COMPANY, city: "Marseille" };
  }
  const activity = tender.activities?.[0] ?? "";
  const candidates = COMPETITORS.filter((company) => company.activities.includes(activity));
  const company = rnd.pick(candidates.length ? candidates : COMPETITORS);
  return { winner: company.name, city: company.city };
}

function awardsOf(tender: TenderDetail): Omit<AwardedContract, "id">[] {
  const notice = tender.notices.find((candidate) => candidate.kind === "AWARD");
  // The decision is taken a few weeks before the notice is published.
  const awardedAt = new Date(new Date(`${notice?.publishedAt}T00:00:00`).getTime() - rnd.between(10, 25) * 86_400_000);
  const lots = tender.lots.length ? tender.lots : [{ number: "", title: null, estimatedAmount: tender.estimatedAmount }];
  return lots.map((lot) => {
    const fixed = FIXED_WINNERS[`${tender.id}/${lot.number}`];
    const { winner, city } = fixed ? { winner: fixed.winner, city: fixed.city } : winnerFor(tender);
    const estimate = lot.estimatedAmount ?? rnd.amount(60_000, 800_000);
    return {
      tenderId: tender.id,
      title: tender.title,
      buyer: tender.buyer ?? "Acheteur non indiqué",
      location: tender.location,
      postalCode: tender.postalCode ?? null,
      department: tender.departments[0] ?? null,
      marketType: tender.marketTypes[0] ?? "WORKS",
      activities: tender.activities ?? [],
      lotNumber: lot.number || null,
      lot: lot.number ? `Lot ${lot.number} – ${lot.title ?? "sans intitulé"}` : null,
      winner,
      winnerCity: city,
      amount: fixed?.amount ?? (rnd.chance(0.05) ? null : Math.round((estimate * rnd.between(82, 106)) / 100 / 100) * 100),
      awardedAt: awardedAt.toLocaleDateString("sv-SE"),
      offersReceived: rnd.chance(0.15) ? null : rnd.between(2, 9),
    };
  });
}

export const MOCK_AWARDS: AwardedContract[] = MOCK_TENDERS.filter((tender) => tender.result?.kind === "AWARDED")
  .flatMap(awardsOf)
  .map((award, index) => ({ ...award, id: index + 1 }));
