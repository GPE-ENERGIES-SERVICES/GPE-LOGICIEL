"use client";

import { useAuth } from "@/components/AuthProvider";
import { RecordTable } from "@/components/company/RecordTable";
import { PricingPanel } from "@/components/settings/PricingPanel";

/**
 * The economic data of GPE: the costs of labour by profile and the defaults
 * the estimates take (one source, read by the estimates), and the other
 * values of GPE, kept as entered until their rules are decided.
 */
export default function CompanyEconomicsPage() {
  const { user } = useAuth();

  return (
    <>
      <section className="card">
        <h2>Coûts horaires, TVA et coefficients</h2>
        <p className="muted">
          Utilisés par les chiffrages : un nouveau chiffrage reprend la TVA et les coefficients, et le détail d&apos;une ligne
          peut reprendre un coût horaire. Un chiffrage garde les valeurs qu&apos;il a reprises.
        </p>
        {user.role === "ADMIN" ? (
          <PricingPanel />
        ) : (
          <p className="muted">Réservé aux administrateurs.</p>
        )}
      </section>
      <section className="card">
        <h2>Autres paramètres économiques</h2>
        <p className="muted">
          Frais généraux, marges visées, informations tarifaires internes… Enregistrés tels quels : aucun calcul ne les utilise
          tant que leurs règles ne sont pas définies avec GPE.
        </p>
        <RecordTable
          list="economic-parameters"
          noun="un paramètre"
          empty="Aucun paramètre renseigné."
          columns={[
            { key: "label", label: "Paramètre" },
            { key: "value", label: "Valeur", className: "number" },
            { key: "unit", label: "Unité" },
            { key: "note", label: "Commentaire" },
          ]}
          fields={[
            { name: "label", label: "Paramètre", required: true, placeholder: "Frais généraux" },
            { name: "value", label: "Valeur", type: "number" },
            { name: "unit", label: "Unité", placeholder: "%, € / h…" },
            { name: "note", label: "Commentaire", type: "textarea", wide: true },
          ]}
        />
      </section>
    </>
  );
}
