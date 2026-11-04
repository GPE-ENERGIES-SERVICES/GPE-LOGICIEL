"use client";

import { useState, type FormEvent } from "react";

import { formatMoney, formatRate } from "@/lib/estimate";
import { formatDate, formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import {
  createSupplier,
  fillCatalogue,
  getCatalogueFilling,
  getSupplierConnectors,
  getSupplierOffers,
  getSuppliers,
  importOffers,
} from "@/services/estimateApi";
import type { CatalogueFilling, OfferImport, Supplier } from "@/types/estimate";

/**
 * The suppliers of GPE, the import of their price files (CSV, XLSX) and the
 * state of the connections to their services.
 */
export function SuppliersPanel() {
  const suppliers = useLoader(() => getSuppliers(), "suppliers");
  const connectors = useLoader(() => getSupplierConnectors(), "supplier-connectors");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OfferImport | null>(null);
  const [busy, setBusy] = useState(false);
  const [filling, setFilling] = useState<CatalogueFilling | null>(null);

  const fill = async (code: string) => {
    setError(null);
    try {
      let state = await fillCatalogue(code);
      setFilling(state);
      while (state.running) {
        await new Promise((resolve) => setTimeout(resolve, 3000));
        state = await getCatalogueFilling(code);
        setFilling(state);
      }
      suppliers.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Remplissage impossible.");
    }
  };

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError(null);
    try {
      await createSupplier(String(form.get("code") ?? "").trim(), String(form.get("name") ?? "").trim());
      formElement.reset();
      suppliers.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
    }
  };

  const upload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const file = form.get("file");
    if (!(file instanceof File) || file.size === 0) {
      setError("Choisissez le fichier de prix.");
      return;
    }
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await importOffers(Number(form.get("supplier")), file, String(form.get("validFrom") ?? "") || null));
      suppliers.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import impossible.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {connectors.data?.map((connector) => (
        <p key={connector.supplierCode} className={connector.environment === "TEST" && connector.available ? "banner info" : "hint"}>
          Connexion {connector.supplierName} :{" "}
          {connector.available ? `active, environnement ${connector.environment}` : "désactivée ou incomplète, aucune demande n'est envoyée"}.{" "}
          {connector.available && (
            <button type="button" className="secondary" disabled={filling?.running} onClick={() => fill(connector.supplierCode)}>
              Remplir le catalogue depuis mes commandes
            </button>
          )}
        </p>
      ))}
      {filling && (
        <p className={filling.error ? "banner error" : "banner info"}>
          {filling.running ? "Lecture des commandes en cours… " : ""}
          {filling.ordersRead} commande(s) lue(s), {filling.products} produit(s) trouvé(s), {filling.imported} ajouté(s) au catalogue
          {filling.notPriced > 0 && `, ${filling.notPriced} sans prix écarté(s)`}.{filling.error && ` ${filling.error}`}
        </p>
      )}
      {suppliers.data && suppliers.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Code</th>
              <th>Fournisseur</th>
              <th className="number">Références</th>
              <th>Dernier import</th>
            </tr>
          </thead>
          <tbody>
            {suppliers.data.map((supplier) => (
              <tr key={supplier.id}>
                <td>{supplier.code}</td>
                <td>{supplier.name}</td>
                <td className="number">{supplier.offers}</td>
                <td>{supplier.lastImport ? formatDateTime(supplier.lastImport) : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {suppliers.data
        ?.filter((supplier) => supplier.offers > 0)
        .map((supplier) => <OfferList key={supplier.id} supplier={supplier} />)}

      <form className="inline-form" onSubmit={create}>
        <input name="code" required maxLength={30} pattern="[A-Za-z0-9_\-]+" placeholder="Code (REXEL)" />
        <input name="name" required maxLength={100} placeholder="Nom" />
        <button type="submit">Ajouter un fournisseur</button>
      </form>

      {suppliers.data && suppliers.data.length > 0 && (
        <form className="inline-form" onSubmit={upload}>
          <select name="supplier" required>
            {suppliers.data.map((supplier) => (
              <option key={supplier.id} value={supplier.id}>
                {supplier.name}
              </option>
            ))}
          </select>
          <input type="file" name="file" accept=".csv,.xlsx,.xls,.ods" required />
          <label>
            Prix valables au <input type="date" name="validFrom" />
          </label>
          <button type="submit" className="primary" disabled={busy}>
            {busy ? "Import…" : "Importer les prix"}
          </button>
        </form>
      )}
      <p className="hint">
        Le fichier doit avoir une ligne d&apos;en-tête avec au moins une référence, une désignation et un prix (net, ou tarif et
        remise). Une colonne « Trigramme » permet ensuite de demander le prix et le stock au fournisseur connecté. Un nouvel
        import remplace le prix de chaque référence du fichier, l&apos;ancien reste dans l&apos;historique ; les chiffrages gardent
        le prix copié.
      </p>
      {result && (
        <p className="banner info">
          {result.imported} prix importé(s), {result.rejected} ligne(s) écartée(s) (sans référence, désignation ou prix). Colonnes
          reconnues : {Object.values(result.columns).join(", ")}.
          {result.warnings.map((warning) => (
            <span key={warning}>
              <br />
              {warning}
            </span>
          ))}
        </p>
      )}
      {(error || suppliers.error) && <p className="banner error">{error ?? suppliers.error}</p>}
    </>
  );
}

/** The prices of a supplier, filtered on the page: at most the 2000 first by designation. */
function OfferList({ supplier }: { supplier: Supplier }) {
  const offers = useLoader(() => getSupplierOffers(supplier.id), `${supplier.id}#${supplier.offers}#${supplier.lastImport}`);
  const [filter, setFilter] = useState("");
  const words = filter.toLowerCase().split(/\s+/).filter(Boolean);
  const shown = (offers.data ?? []).filter((offer) => {
    const text = `${offer.designation} ${offer.supplierRef} ${offer.manufacturerCode ?? ""} ${offer.manufacturerRef ?? ""}`.toLowerCase();
    return words.every((word) => text.includes(word));
  });

  return (
    <>
      <h2>Prix {supplier.name}</h2>
      {offers.error && <p className="banner error">{offers.error}</p>}
      <div className="inline-form">
        <input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="Filtrer : R2V, H07VR, DNX3…" aria-label={`Filtrer les prix ${supplier.name}`} />
        <span className="muted">
          {shown.length} produit(s) sur {supplier.offers}
        </span>
      </div>
      <table>
        <thead>
          <tr>
            <th>Désignation</th>
            <th>Réf. fournisseur</th>
            <th>Trigramme</th>
            <th>Réf. commerciale</th>
            <th className="number">Prix de base</th>
            <th className="number">Remise</th>
            <th className="number">Prix net HT</th>
            <th>Date du prix</th>
          </tr>
        </thead>
        <tbody>
          {shown.map((offer) => (
            <tr key={offer.id}>
              <td>{offer.designation}</td>
              <td>{offer.supplierRef}</td>
              <td>{offer.manufacturerCode ?? "—"}</td>
              <td>{offer.manufacturerRef ?? "—"}</td>
              <td className="number">{offer.listPrice === null ? "—" : formatMoney(offer.listPrice)}</td>
              <td className="number">{offer.discountRate === null ? "—" : formatRate(offer.discountRate)}</td>
              <td className="number">
                <strong>{formatMoney(offer.netPrice)}</strong>
                {offer.priceSource === "REXEL_TEST" && " (TEST)"}
              </td>
              <td>{formatDate(offer.validFrom ?? offer.importedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
