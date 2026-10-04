/**
 * Links between the lists and the details of a tender: the details page
 * receives the address of the list it was opened from, with its search,
 * filters, sort and page, to go back to it.
 */

export function tenderHref(id: number, from?: string): string {
  return from ? `/tenders/${id}?from=${encodeURIComponent(from)}` : `/tenders/${id}`;
}

const BACK_LABELS: Record<string, string> = {
  "/tenders": "Retour aux résultats",
  "/follows": "Retour à Mes suivis",
  "/archives": "Retour aux archives",
  "/awards": "Retour aux marchés attribués",
  "/alerts": "Retour à Mes veilles",
  "/": "Retour au tableau de bord",
};

/**
 * The page to go back to. Only the sections of the application are accepted,
 * never another site; without one, a past tender goes back to the archives.
 */
export function backLink(from: string | null, past: boolean): { href: string; label: string } {
  const path = from?.split("?")[0] ?? "";
  if (from && path in BACK_LABELS) {
    return { href: from, label: BACK_LABELS[path] };
  }
  return past
    ? { href: "/archives", label: "Retour aux archives" }
    : { href: "/tenders", label: "Retour aux appels d'offres" };
}
