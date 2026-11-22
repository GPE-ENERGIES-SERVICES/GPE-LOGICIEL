"use client";

import Link from "next/link";
import { useState } from "react";

import { FORM_STATUS_CLASSES, FORM_STATUS_LABELS } from "@/components/FormPanel";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getAdministrativeForms } from "@/services/responseApi";
import type { FormStatus } from "@/types/response";

/** The forms to fill of every answer begun: each one is filled and validated on the page of its tender. */
export default function AdministrativePage() {
  const forms = useLoader(() => getAdministrativeForms(), "administrative");
  const [status, setStatus] = useState<FormStatus | "">("");
  const list = (forms.data ?? []).filter((form) => !status || form.status === status);
  const count = (wanted: FormStatus) => (forms.data ?? []).filter((form) => form.status === wanted).length;

  return (
    <div>
      <h1>Administratif</h1>
      <p className="muted">
        Les formulaires demandés par les DCE (DC1, DC2, acte d&apos;engagement, déclarations, attestations, cadres de réponse…) de tous les appels d&apos;offres en réponse. Chacun se
        remplit dans l&apos;onglet « Administratif » de son appel d&apos;offres : à la main, ou par l&apos;IA puis validé ou refusé par
        vous.
      </p>
      <section className="card">
        {forms.error && <p className="banner error">{forms.error}</p>}
        <div className="inline-form">
          <label>
            État{" "}
            <select value={status} onChange={(event) => setStatus(event.target.value as FormStatus | "")}>
              <option value="">Tous</option>
              {(Object.keys(FORM_STATUS_LABELS) as FormStatus[]).map((code) => (
                <option key={code} value={code}>
                  {FORM_STATUS_LABELS[code]} ({count(code)})
                </option>
              ))}
            </select>
          </label>
        </div>
        {forms.data && list.length === 0 && (
          <p className="muted">
            {status ? "Aucun formulaire dans cet état." : "Aucun formulaire : ils apparaissent quand un dossier de réponse est préparé."}
          </p>
        )}
        {list.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Appel d&apos;offres</th>
                <th>Date limite</th>
                <th>Formulaire</th>
                <th>État</th>
              </tr>
            </thead>
            <tbody>
              {list.map((form) => (
                <tr key={form.pieceId}>
                  <td>
                    <Link href={`/tenders/${form.tenderId}?from=/administrative#administratif`}>{form.tenderTitle}</Link>
                    {form.buyer && <span className="muted"> · {form.buyer}</span>}
                  </td>
                  <td>{form.deadline ? formatDateTime(form.deadline) : <span className="muted">Non indiquée</span>}</td>
                  <td>{form.label}</td>
                  <td>
                    <span className={FORM_STATUS_CLASSES[form.status]}>{FORM_STATUS_LABELS[form.status]}</span>
                    {form.status !== "TODO" && form.status !== "VALIDATED" && (form.missing > 0 || form.toCheck > 0) && (
                      <span className="muted">
                        {" "}
                        {form.missing} manquant{form.missing > 1 ? "s" : ""} · {form.toCheck} à vérifier
                      </span>
                    )}
                    {form.status === "VALIDATED" && form.reviewedBy && <span className="muted"> par {form.reviewedBy}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
