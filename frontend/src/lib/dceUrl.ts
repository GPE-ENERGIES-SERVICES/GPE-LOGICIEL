/** Older address of the Atexo platforms (PLACE, Maximilien, Mégalis...): a search listing the consultation. */
const ATEXO_SEARCH = /^(https?:\/\/[^/?#]+)\/(?:index\.php)?\?page=Entreprise\.EntrepriseAdvancedSearch&AllCons&id=(\d+)&orgAcronyme=([\w-]+)$/i;

/**
 * The address to open for a consultation, as given by a notice. The older
 * address of the Atexo platforms opens a search page, one click away from the
 * consultation: its detail page, at the address these platforms use now, is
 * opened instead. Any other address is kept as it is.
 */
export function consultationUrl(url: string): string {
  const atexo = ATEXO_SEARCH.exec(url.trim());
  return atexo ? `${atexo[1]}/entreprise/consultation/${atexo[2]}?orgAcronyme=${atexo[3]}` : url;
}
