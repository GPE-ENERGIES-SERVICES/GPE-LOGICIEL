"use client";

import { useState, type FormEvent } from "react";
import { usePaged } from "@/components/Pager";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import {
  createSupplier,
  deleteSupplier,
  fillCatalogue,
  getCatalogueFilling,
  getPriceUpdate,
  getSupplierConnectors,
  getSuppliers,
  importOffers,
  startPriceUpdate,
} from "@/services/estimateApi";
import type {
  CatalogueFilling,
  OfferImport,
  PriceUpdate,
} from "@/types/estimate";

import { OfferCatalogue } from "./OfferCatalogue";

/**
 * The suppliers of GPE, the import of their price files (CSV, XLSX) and the
 * state of the connections to their services.
 */
export function SuppliersPanel() {
  const suppliers = useLoader(() => getSuppliers(), "suppliers");
  const paged = usePaged(suppliers.data ?? [], "Pages des fournisseurs");
  const connectors = useLoader(
    () => getSupplierConnectors(),
    "supplier-connectors",
  );
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OfferImport | null>(null);
  const [busy, setBusy] = useState(false);
  const [filling, setFilling] = useState<CatalogueFilling | null>(null);
  const [update, setUpdate] = useState<PriceUpdate | null>(null);
  // One form shown at a time, only when asked.
  const [open, setOpen] = useState<"supplier" | "file" | null>(null);

  const updatePrices = async (code: string) => {
    setError(null);
    try {
      let state = await startPriceUpdate(code);
      setUpdate(state);
      while (state.running) {
        await new Promise((resolve) => setTimeout(resolve, 3000));
        state = await getPriceUpdate(code);
        setUpdate(state);
      }
      suppliers.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Mise à jour impossible.");
    }
  };

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
      await createSupplier(
        String(form.get("code") ?? "").trim(),
        String(form.get("name") ?? "").trim(),
      );
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
      setResult(
        await importOffers(
          Number(form.get("supplier")),
          file,
          String(form.get("validFrom") ?? "") || null,
        ),
      );
      suppliers.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import impossible.");
    } finally {
      setBusy(false);
    }
  };

  const connected = (code: string) =>
    connectors.data?.find(
      (connector) =>
        connector.supplierCode === code.toUpperCase() && connector.available,
    );

  return (
    <>
      {connectors.data?.map((connector) => (
        <p key={connector.supplierCode} className="hint">
          Connexion {connector.supplierName} :{" "}
          {connector.available
            ? `active, environnement ${connector.environment}`
            : "désactivée ou incomplète, aucune demande n'est envoyée"}
          .
        </p>
      ))}
      {update && (
        <p className={update.error ? "banner error" : "banner info"}>
          {update.running
            ? "Mise à jour des prix en cours… "
            : "Prix mis à jour : "}
          {update.updated} prix relu(s) sur {update.offers}
          {update.fresh > 0 && `, ${update.fresh} déjà à jour`}
          {update.notPriced > 0 &&
            `, ${update.notPriced} sans prix du fournisseur (prix inchangé)`}
          .{update.error && ` ${update.error}`}
        </p>
      )}
      {filling && (
        <p className={filling.error ? "banner error" : "banner info"}>
          {filling.running ? "Lecture des commandes en cours… " : ""}
          {filling.ordersRead} commande(s) lue(s), {filling.products} produit(s)
          trouvé(s), {filling.imported} ajouté(s) au catalogue
          {filling.notPriced > 0 &&
            `, ${filling.notPriced} sans prix écarté(s)`}
          .{filling.error && ` ${filling.error}`}
        </p>
      )}
      {suppliers.data && suppliers.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Code</th>
              <th>Fournisseur</th>
              <th className="number">Références</th>
              <th>Dernière mise à jour</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {paged.rows.map((supplier) => (
              <tr key={supplier.id}>
                <td>{supplier.code}</td>
                <td>{supplier.name}</td>
                <td className="number">{supplier.offers}</td>
                <td>
                  {supplier.lastImport
                    ? formatDateTime(supplier.lastImport)
                    : "—"}
                </td>
                <td className="row-actions">
                  {connected(supplier.code) && (
                    <>
                      <button
                        type="button"
                        className="primary"
                        disabled={
                          update?.running ||
                          filling?.running ||
                          supplier.offers === 0
                        }
                        title={`Demander à ${supplier.name} le prix actuel de chaque référence du catalogue`}
                        onClick={() => updatePrices(supplier.code)}
                      >
                        {update?.running
                          ? "Mise à jour…"
                          : "Mettre à jour les prix"}
                      </button>
                      <button
                        type="button"
                        className="link-button"
                        disabled={update?.running || filling?.running}
                        title="Ajouter au catalogue les produits de vos commandes passées"
                        onClick={() => fill(supplier.code)}
                      >
                        Remplir depuis mes commandes
                      </button>
                    </>
                  )}
                  <button
                    type="button"
                    className="secondary danger"
                    onClick={async () => {
                      if (
                        !window.confirm(
                          `Supprimer le fournisseur « ${supplier.name} » et ses ${supplier.offers} tarifs ? Les chiffrages gardent leurs prix.`,
                        )
                      ) {
                        return;
                      }
                      setError(null);
                      try {
                        await deleteSupplier(supplier.id);
                        suppliers.reload();
                      } catch (err) {
                        setError(err instanceof Error ? err.message : "Suppression impossible.");
                      }
                    }}
                  >
                    Supprimer
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {paged.pager}

      <div className="inline-form">
        <button
          type="button"
          aria-expanded={open === "supplier"}
          onClick={() => setOpen(open === "supplier" ? null : "supplier")}
        >
          {open === "supplier" ? "Fermer" : "Ajouter un fournisseur"}
        </button>
        {suppliers.data && suppliers.data.length > 0 && (
          <button
            type="button"
            aria-expanded={open === "file"}
            onClick={() => setOpen(open === "file" ? null : "file")}
          >
            {open === "file" ? "Fermer" : "Importer un fichier de prix"}
          </button>
        )}
      </div>

      {open === "supplier" && (
        <form className="inline-form" onSubmit={create}>
          <input
            name="code"
            required
            maxLength={30}
            pattern="[A-Za-z0-9_\-]+"
            placeholder="Code (REXEL)"
          />
          <input name="name" required maxLength={100} placeholder="Nom" />
          <button type="submit" className="primary">
            Enregistrer le fournisseur
          </button>
        </form>
      )}

      {open === "file" && suppliers.data && suppliers.data.length > 0 && (
        <>
          <form className="inline-form" onSubmit={upload}>
            <select name="supplier" required>
              {suppliers.data.map((supplier) => (
                <option key={supplier.id} value={supplier.id}>
                  {supplier.name}
                </option>
              ))}
            </select>
            <input
              type="file"
              name="file"
              accept=".csv,.xlsx,.xls,.ods"
              required
            />
            <label>
              Prix valables au <input type="date" name="validFrom" />
            </label>
            <button type="submit" className="primary" disabled={busy}>
              {busy ? "Import…" : "Importer les prix"}
            </button>
          </form>
          <p className="hint">
            Le fichier doit avoir une ligne d&apos;en-tête avec au moins une
            référence, une désignation et un prix (net, ou tarif et remise). Une
            colonne « Trigramme » permet ensuite de demander le prix et le stock
            au fournisseur connecté. Un nouvel import remplace le prix de chaque
            référence du fichier, l&apos;ancien reste dans l&apos;historique ;
            les chiffrages gardent le prix copié.
          </p>
        </>
      )}
      {result && (
        <p className="banner info">
          {result.imported} prix importé(s), {result.rejected} ligne(s)
          écartée(s) (sans référence, désignation ou prix). Colonnes reconnues :{" "}
          {Object.values(result.columns).join(", ")}.
          {result.warnings.map((warning) => (
            <span key={warning}>
              <br />
              {warning}
            </span>
          ))}
        </p>
      )}
      {(error || suppliers.error) && (
        <p className="banner error">{error ?? suppliers.error}</p>
      )}

      {suppliers.data && (
        <OfferCatalogue
          suppliers={suppliers.data}
          onChanged={suppliers.reload}
        />
      )}
    </>
  );
}
