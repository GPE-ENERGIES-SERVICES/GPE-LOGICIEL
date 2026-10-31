"use client";

import { RecordTable } from "@/components/company/RecordTable";
import { ACTIVITY_KIND_LABELS } from "@/lib/company";
import { ACTIVITY_LABELS } from "@/lib/format";

/**
 * What GPE does. The type of works and the CPV codes are those of the tenders:
 * they will be compared with them to find the tenders matching GPE.
 */
export default function CompanyActivitiesPage() {
  return (
    <section className="card">
      <h2>Activités et compétences</h2>
      <p className="muted">
        Domaines, métiers, compétences, prestations, spécialités et savoir-faire de GPE. Le type de travaux et les codes CPV
        servent à rapprocher les appels d&apos;offres des capacités de l&apos;entreprise.
      </p>
      <RecordTable
        list="activities"
        noun="une activité"
        empty="Aucune activité renseignée."
        columns={[
          { key: "kind", label: "Nature", render: (row) => ACTIVITY_KIND_LABELS[String(row.kind)] },
          { key: "label", label: "Libellé" },
          {
            key: "activityCode",
            label: "Type de travaux",
            render: (row) => (row.activityCode ? ACTIVITY_LABELS[String(row.activityCode)] : ""),
          },
          { key: "cpvPrefixes", label: "Codes CPV" },
        ]}
        fields={[
          { name: "kind", label: "Nature", type: "select", options: ACTIVITY_KIND_LABELS, required: true },
          { name: "label", label: "Libellé", required: true, placeholder: "Courants forts, IRVE…" },
          { name: "activityCode", label: "Type de travaux", type: "select", options: ACTIVITY_LABELS },
          { name: "cpvPrefixes", label: "Codes CPV (début)", type: "list", placeholder: "4531, 4526…" },
          { name: "description", label: "Description", type: "textarea", wide: true },
        ]}
      />
    </section>
  );
}
