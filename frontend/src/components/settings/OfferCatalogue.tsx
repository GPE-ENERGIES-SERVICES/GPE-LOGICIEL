"use client";

import { useState, type FormEvent } from "react";

import { formatMoney, formatRate } from "@/lib/estimate";
import { formatDate } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { addOffer, deleteOffer, getOfferPage, OFFER_PAGE_SIZE } from "@/services/estimateApi";
import { Pager } from "@/components/Pager";
import type { Supplier } from "@/types/estimate";

const SOURCE_LABELS: Record<string, string> = { IMPORT: "Fichier", MANUAL: "À la main" };

const DECIMAL = "[0-9]+([.,][0-9]+)?";

/** A price entered by hand, for one supplier: it replaces the price of the same reference. */
function ManualOfferForm({ suppliers, onAdded }: { suppliers: Supplier[]; onAdded: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const number = (value: FormDataEntryValue | null) => {
    const text = String(value ?? "").trim().replace(",", ".");
    return text === "" ? null : Number(text);
  };
  const text = (value: FormDataEntryValue | null) => String(value ?? "").trim() || null;

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const target = event.currentTarget;
    const form = new FormData(target);
    const discount = number(form.get("discount"));
    setBusy(true);
    setError(null);
    try {
      await addOffer(Number(form.get("supplier")), {
        supplierRef: String(form.get("supplierRef")).trim(),
        manufacturerRef: text(form.get("manufacturerRef")),
        designation: String(form.get("designation")).trim(),
        unit: text(form.get("unit")),
        packQuantity: number(form.get("pack")),
        listPrice: number(form.get("listPrice")),
        discountRate: discount === null ? null : discount / 100,
        netPrice: number(form.get("netPrice")) ?? 0,
        validFrom: text(form.get("validFrom")),
      });
      target.reset();
      onAdded();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Tarif non enregistré.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <label>
        Fournisseur
        <select name="supplier" required>
          {suppliers.map((supplier) => (
            <option key={supplier.id} value={supplier.id}>
              {supplier.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Réf. fournisseur
        <input name="supplierRef" required maxLength={100} size={12} />
      </label>
      <label>
        Réf. fabricant
        <input name="manufacturerRef" maxLength={100} size={12} />
      </label>
      <label>
        Désignation
        <input name="designation" required maxLength={500} size={30} />
      </label>
      <label>
        Unité
        <input name="unit" maxLength={20} size={4} placeholder="U, M…" />
      </label>
      <label>
        Conditionnement
        <input name="pack" inputMode="decimal" size={5} placeholder="1" pattern={DECIMAL} />
      </label>
      <label>
        Prix de base HT
        <input name="listPrice" inputMode="decimal" size={8} pattern={DECIMAL} />
      </label>
      <label>
        Remise %
        <input name="discount" inputMode="decimal" size={4} pattern="[0-9]{1,2}([.,][0-9]+)?" />
      </label>
      <label>
        Prix net HT
        <input name="netPrice" required inputMode="decimal" size={8} pattern={DECIMAL} />
      </label>
      <label>
        Date du prix
        <input type="date" name="validFrom" />
      </label>
      <button type="submit" className="primary" disabled={busy}>
        {busy ? "Enregistrement…" : "Enregistrer le tarif"}
      </button>
      {error && <p className="banner error">{error}</p>}
      <p className="hint">
        Le prix net HT est celui payé pour le conditionnement (100 pour une couronne de 100 m). Une référence déjà connue prend ce
        nouveau prix ; l&apos;ancien reste dans l&apos;historique.
      </p>
    </form>
  );
}

/**
 * Every price, one page at a time: of one supplier or all, searched by
 * reference or words of the designation, and a price added by hand.
 */
export function OfferCatalogue({ suppliers, onChanged }: { suppliers: Supplier[]; onChanged: () => void }) {
  const [supplierId, setSupplierId] = useState<number | null>(null);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [version, setVersion] = useState(0);
  const total = suppliers.reduce((sum, supplier) => sum + supplier.offers, 0);
  const offers = useLoader(
    () => getOfferPage({ supplierId, q: query, page }),
    `offers#${supplierId}#${query}#${page}#${version}#${total}`,
  );
  const data = offers.data;
  const pages = data ? Math.max(1, Math.ceil(data.total / OFFER_PAGE_SIZE)) : 1;

  return (
    <>
      <h2>Tarifs</h2>
      {offers.error && <p className="banner error">{offers.error}</p>}
      <form
        className="inline-form"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery(String(new FormData(event.currentTarget).get("q") ?? ""));
          setPage(0);
        }}
      >
        <label>
          Fournisseur
          <select
            value={supplierId ?? ""}
            onChange={(event) => {
              setSupplierId(event.target.value ? Number(event.target.value) : null);
              setPage(0);
            }}
          >
            <option value="">Tous ({total})</option>
            {suppliers.map((supplier) => (
              <option key={supplier.id} value={supplier.id}>
                {supplier.name} ({supplier.offers})
              </option>
            ))}
          </select>
        </label>
        <input
          name="q"
          defaultValue={query}
          maxLength={200}
          placeholder="Référence ou désignation : R2V, H07VR, DNX3…"
          aria-label="Rechercher un tarif"
        />
        <button type="submit">Rechercher</button>
        {suppliers.length > 0 && (
          <button type="button" className={adding ? "" : "primary"} aria-expanded={adding} onClick={() => setAdding(!adding)}>
            {adding ? "Fermer" : "Ajouter un tarif"}
          </button>
        )}
      </form>
      {adding && (
        <ManualOfferForm
          suppliers={suppliers}
          onAdded={() => {
            setVersion(version + 1);
            onChanged();
          }}
        />
      )}
      {data && (
        <p className="muted">
          {data.total} tarif{data.total > 1 ? "s" : ""}
          {data.total > 0 && ` · page ${data.page + 1} sur ${pages}`}
        </p>
      )}
      {data && data.content.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Désignation</th>
              <th>Fournisseur</th>
              <th>Réf. fournisseur</th>
              <th>Trigramme</th>
              <th>Réf. commerciale</th>
              <th className="number">Prix de base</th>
              <th className="number">Remise</th>
              <th className="number">Prix net HT</th>
              <th>Date du prix</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.content.map((offer) => (
              <tr key={offer.id}>
                <td>
                  {offer.designation}
                  {offer.packQuantity !== 1 && (
                    <div className="small muted">
                      par {offer.packQuantity} {offer.purchaseUnit ?? ""}
                    </div>
                  )}
                </td>
                <td>{offer.supplierName}</td>
                <td>{offer.supplierRef}</td>
                <td>{offer.manufacturerCode ?? "—"}</td>
                <td>{offer.manufacturerRef ?? "—"}</td>
                <td className="number">{offer.listPrice === null ? "—" : formatMoney(offer.listPrice)}</td>
                <td className="number">{offer.discountRate === null ? "—" : formatRate(offer.discountRate)}</td>
                <td className="number">
                  <strong>{formatMoney(offer.netPrice)}</strong>
                  {offer.priceSource === "REXEL_TEST" && " (TEST)"}
                </td>
                <td>
                  {formatDate(offer.validFrom ?? offer.importedAt)}
                  <div className="small muted">{SOURCE_LABELS[offer.priceSource] ?? "Service fournisseur"}</div>
                </td>
                <td className="row-actions">
                  <button
                    type="button"
                    className="secondary danger"
                    onClick={async () => {
                      if (window.confirm(`Supprimer le tarif « ${offer.designation} » ?`)) {
                        try {
                          await deleteOffer(offer.id);
                        } catch (err) {
                          window.alert(err instanceof Error ? err.message : "Suppression impossible.");
                        }
                        setVersion((current) => current + 1);
                        onChanged();
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
      {data && (
        <Pager page={page} total={data.total} size={OFFER_PAGE_SIZE} busy={offers.loading} label="Pages des tarifs" onChange={setPage} />
      )}
    </>
  );
}
