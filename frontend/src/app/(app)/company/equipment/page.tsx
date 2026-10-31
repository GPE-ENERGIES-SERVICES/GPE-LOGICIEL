"use client";

import { dateCell, RecordTable } from "@/components/company/RecordTable";
import { EQUIPMENT_CATEGORY_LABELS, EQUIPMENT_STATUS_LABELS } from "@/lib/company";

/** Vehicles, tools, equipment and means of site; the next check is followed as an expiry. */
export default function CompanyEquipmentPage() {
  return (
    <section className="card">
      <h2>Matériel et moyens techniques</h2>
      <RecordTable
        list="equipment"
        noun="un matériel"
        empty="Aucun matériel renseigné."
        columns={[
          { key: "category", label: "Catégorie", render: (row) => EQUIPMENT_CATEGORY_LABELS[String(row.category)] },
          { key: "label", label: "Désignation" },
          { key: "identifier", label: "Immatriculation, n° série" },
          { key: "quantity", label: "Qté", className: "number" },
          { key: "status", label: "État", render: (row) => EQUIPMENT_STATUS_LABELS[String(row.status)] },
          { key: "nextCheckOn", label: "Prochain contrôle", render: dateCell("nextCheckOn") },
        ]}
        fields={[
          { name: "category", label: "Catégorie", type: "select", options: EQUIPMENT_CATEGORY_LABELS, required: true },
          { name: "label", label: "Désignation", required: true, placeholder: "Fourgon, nacelle, testeur…" },
          { name: "model", label: "Marque, modèle" },
          { name: "identifier", label: "Immatriculation, n° de série" },
          { name: "quantity", label: "Quantité", type: "number", placeholder: "1" },
          { name: "status", label: "État", type: "select", options: EQUIPMENT_STATUS_LABELS, required: true },
          { name: "nextCheckOn", label: "Prochain contrôle", type: "date" },
          { name: "notes", label: "Caractéristiques, notes", type: "textarea", wide: true },
        ]}
      />
    </section>
  );
}
