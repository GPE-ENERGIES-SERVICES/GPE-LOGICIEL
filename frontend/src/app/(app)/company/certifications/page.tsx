"use client";

import { dateCell, RecordTable } from "@/components/company/RecordTable";
import { CERTIFICATION_KIND_LABELS } from "@/lib/company";

/** The certifications, qualifications and labels of GPE, with their proof and their end. */
export default function CompanyCertificationsPage() {
  return (
    <section className="card">
      <h2>Certifications et qualifications</h2>
      <RecordTable
        list="certifications"
        noun="une certification"
        empty="Aucune certification renseignée."
        columns={[
          { key: "kind", label: "Nature", render: (row) => CERTIFICATION_KIND_LABELS[String(row.kind)] },
          { key: "name", label: "Intitulé" },
          { key: "organism", label: "Organisme" },
          { key: "number", label: "Numéro" },
          { key: "obtainedOn", label: "Obtenue le", render: dateCell("obtainedOn") },
          { key: "expiresOn", label: "Valable jusqu'au", render: dateCell("expiresOn") },
        ]}
        fields={[
          { name: "kind", label: "Nature", type: "select", options: CERTIFICATION_KIND_LABELS, required: true },
          { name: "name", label: "Intitulé", required: true, placeholder: "Qualifelec, RGE, MASE…" },
          { name: "organism", label: "Organisme certificateur" },
          { name: "number", label: "Numéro" },
          { name: "obtainedOn", label: "Obtenue le", type: "date" },
          { name: "expiresOn", label: "Valable jusqu'au", type: "date" },
          { name: "scope", label: "Périmètre, mentions", type: "textarea", wide: true },
        ]}
      />
    </section>
  );
}
