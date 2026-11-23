"use client";

import Link from "next/link";

import { RecordTable } from "@/components/company/RecordTable";

/**
 * The economic values of GPE, kept as entered until their rules are decided;
 * the defaults of the estimates are with the estimates.
 */
export default function CompanyEconomicsPage() {
  return (
    <>
      <p className="hint">
        Les coûts horaires, la TVA et les coefficients sont dans{" "}
        <Link href="/estimate-settings">
          Chiffrage › Paramètres du chiffrage
        </Link>
        .
      </p>
      <section className="card">
        <h2>Autres paramètres économiques</h2>
        <p className="muted">
          Frais généraux, marges visées, informations tarifaires internes…
          Enregistrés tels quels : aucun calcul ne les utilise tant que leurs
          règles ne sont pas définies avec GPE.
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
            {
              name: "label",
              label: "Paramètre",
              required: true,
              placeholder: "Frais généraux",
            },
            { name: "value", label: "Valeur", type: "number" },
            { name: "unit", label: "Unité", placeholder: "%, € / h…" },
            {
              name: "note",
              label: "Commentaire",
              type: "textarea",
              wide: true,
            },
          ]}
        />
      </section>
    </>
  );
}
