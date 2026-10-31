"use client";

import { useState, type FormEvent } from "react";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { createSupplier, getSuppliers, importOffers } from "@/services/estimateApi";
import type { OfferImport } from "@/types/estimate";

/**
 * The suppliers of GPE and the import of their price files (CSV, XLSX). No
 * supplier is contacted by the application: the prices are those of the
 * files given by the suppliers.
 */
export function SuppliersPanel() {
  const suppliers = useLoader(() => getSuppliers(), "suppliers");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OfferImport | null>(null);
  const [busy, setBusy] = useState(false);

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
        remise). Un nouvel import remplace le prix de chaque référence du fichier ; les chiffrages gardent le prix copié.
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
