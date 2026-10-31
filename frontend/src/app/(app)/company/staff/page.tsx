"use client";

import { dateCell, RecordTable } from "@/components/company/RecordTable";
import { CREDENTIAL_KIND_LABELS } from "@/lib/company";

/** The staff of GPE; each employee opens on their qualifications and habilitations, with their end. */
export default function CompanyStaffPage() {
  return (
    <section className="card">
      <h2>Personnel</h2>
      <RecordTable
        list="employees"
        noun="un salarié"
        empty="Aucun salarié renseigné."
        detailLabel="Habilitations"
        columns={[
          { key: "lastName", label: "Nom", render: (row) => `${row.lastName} ${row.firstName}` },
          { key: "jobTitle", label: "Fonction" },
          { key: "phone", label: "Téléphone" },
          { key: "active", label: "Présent", render: (row) => (row.active ? "Oui" : "Non") },
        ]}
        fields={[
          { name: "lastName", label: "Nom", required: true },
          { name: "firstName", label: "Prénom", required: true },
          { name: "jobTitle", label: "Fonction, poste", placeholder: "Électricien, chef d'équipe…" },
          { name: "phone", label: "Téléphone" },
          { name: "email", label: "E-mail", type: "email" },
          { name: "hiredOn", label: "Entrée", type: "date" },
          { name: "active", label: "Présent dans l'entreprise", type: "checkbox" },
          { name: "skills", label: "Compétences", type: "textarea", wide: true },
          { name: "experience", label: "Expérience professionnelle", type: "textarea", wide: true },
        ]}
        detail={(employee) => (
          <div className="record-nested">
            <h4>Qualifications et habilitations</h4>
            <RecordTable
              list="credentials"
              noun="une habilitation"
              empty="Aucune qualification ni habilitation."
              filter={(row) => row.employeeId === employee.id}
              fixed={{ employeeId: employee.id }}
              columns={[
                { key: "kind", label: "Nature", render: (row) => CREDENTIAL_KIND_LABELS[String(row.kind)] },
                { key: "label", label: "Intitulé" },
                { key: "issuer", label: "Organisme" },
                { key: "expiresOn", label: "Fin de validité", render: dateCell("expiresOn") },
              ]}
              fields={[
                { name: "kind", label: "Nature", type: "select", options: CREDENTIAL_KIND_LABELS, required: true },
                { name: "label", label: "Intitulé", required: true, placeholder: "B2V, BR, H0B0, CACES…" },
                { name: "number", label: "Numéro" },
                { name: "issuer", label: "Organisme" },
                { name: "obtainedOn", label: "Obtenue le", type: "date" },
                { name: "expiresOn", label: "Valable jusqu'au", type: "date" },
              ]}
            />
          </div>
        )}
      />
    </section>
  );
}
