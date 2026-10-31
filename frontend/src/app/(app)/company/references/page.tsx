"use client";

import { dateCell, RecordTable } from "@/components/company/RecordTable";
import { formatMoney } from "@/lib/estimate";

/** The works done by GPE, given as references in the bids, with their photos and proofs. */
export default function CompanyReferencesPage() {
  return (
    <section className="card">
      <h2>Références et réalisations</h2>
      <RecordTable
        list="references"
        noun="une référence"
        empty="Aucune référence renseignée."
        detailLabel="Photos et pièces"
        columns={[
          { key: "projectName", label: "Projet" },
          { key: "client", label: "Client, maître d'ouvrage" },
          { key: "nature", label: "Nature des travaux" },
          {
            key: "amount",
            label: "Montant HT",
            className: "number",
            render: (row) => (row.amount === null ? "" : formatMoney(Number(row.amount))),
          },
          { key: "finishedOn", label: "Achevé le", render: dateCell("finishedOn") },
        ]}
        fields={[
          { name: "projectName", label: "Nom du projet", required: true, wide: true },
          { name: "client", label: "Client, maître d'ouvrage" },
          { name: "location", label: "Lieu" },
          { name: "nature", label: "Nature des travaux" },
          { name: "amount", label: "Montant du marché HT (€)", type: "number" },
          { name: "startedOn", label: "Début", type: "date" },
          { name: "finishedOn", label: "Fin", type: "date" },
          { name: "description", label: "Prestations réalisées", type: "textarea", wide: true },
          { name: "skills", label: "Compétences démontrées", type: "textarea", wide: true },
        ]}
      />
    </section>
  );
}
