"use client";

import { dateCell, RecordTable } from "@/components/company/RecordTable";
import { DOCUMENT_CATEGORY_LABELS } from "@/lib/company";

/**
 * The official documents asked in the bids. The current file is the last
 * uploaded; the former ones stay as history.
 */
export default function CompanyDocumentsPage() {
  return (
    <section className="card">
      <h2>Documents administratifs</h2>
      <p className="muted">
        Kbis, assurances, attestations fiscales, sociales et de vigilance… Pour remplacer un document arrivé à échéance,
        ouvrez ses fichiers, déposez le nouveau puis mettez à jour ses dates.
      </p>
      <RecordTable
        list="documents"
        noun="un document"
        empty="Aucun document renseigné."
        columns={[
          { key: "category", label: "Catégorie", render: (row) => DOCUMENT_CATEGORY_LABELS[String(row.category)] },
          { key: "name", label: "Nom" },
          { key: "issuedOn", label: "Émis le", render: dateCell("issuedOn") },
          { key: "expiresOn", label: "Expire le", render: dateCell("expiresOn") },
          {
            key: "file",
            label: "Fichier",
            render: (row) => (row.files && row.files.length > 0 ? row.files[0].fileName : <span className="muted">manquant</span>),
          },
        ]}
        fields={[
          { name: "category", label: "Catégorie", type: "select", options: DOCUMENT_CATEGORY_LABELS, required: true },
          { name: "name", label: "Nom", required: true, placeholder: "Attestation de vigilance URSSAF" },
          { name: "issuedOn", label: "Émis le", type: "date" },
          { name: "expiresOn", label: "Expire le", type: "date" },
          { name: "notes", label: "Notes", type: "textarea", wide: true },
        ]}
      />
    </section>
  );
}
