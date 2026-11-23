"use client";

import { useState } from "react";

import { useLoader } from "@/lib/useLoader";
import Link from "next/link";

import { addPiece, changePiece, getAdministrativeForms, getResponse, prepareResponse } from "@/services/responseApi";
import type { AdministrativeForm, PieceCategory } from "@/types/response";

import { FORM_STATUS_CLASSES, FORM_STATUS_LABELS, FormPanel } from "./FormPanel";
import { documentLabel } from "./ResponseSection";

/** The forms the software fills today; others are added as pieces of the answer. */
const STANDARD_FORMS: { label: string; category: PieceCategory; present: RegExp }[] = [
  { label: "Formulaire DC1", category: "APPLICATION", present: /\bdc ?1\b/i },
  { label: "Formulaire DC2", category: "APPLICATION", present: /\bdc ?2\b/i },
  { label: "Acte d'engagement", category: "OFFER", present: /engagement|\bae\b/i },
];

function FormItem({
  form,
  lots,
  onChange,
}: {
  form: AdministrativeForm;
  lots: { number: string; title: string | null }[];
  onChange: () => void;
}) {
  const [open, setOpen] = useState(form.status === "TO_VALIDATE" || form.status === "REJECTED");
  const [error, setError] = useState<string | null>(null);

  return (
    <li>
      <div className="inline-form">
        <strong>{form.label}</strong>
        <span className={FORM_STATUS_CLASSES[form.status]}>{FORM_STATUS_LABELS[form.status]}</span>
        {form.status !== "TODO" && form.status !== "VALIDATED" && (form.missing > 0 || form.toCheck > 0) && (
          <span className="muted">
            {form.missing} manquant{form.missing > 1 ? "s" : ""} · {form.toCheck} à vérifier
          </span>
        )}
        <button type="button" className="link-button" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? "Fermer" : form.status === "TODO" ? "Remplir" : "Ouvrir"}
        </button>
      </div>
      {error && <p className="banner error">{error}</p>}
      {open && (
        <FormPanel
          pieceId={form.pieceId}
          lots={lots}
          onChange={onChange}
          label={form.label}
          onGenerated={(action) => {
            setError(null);
            action().then(onChange, (err) => setError(err instanceof Error ? err.message : "Génération impossible."));
          }}
        />
      )}
    </li>
  );
}

/**
 * The administrative work of a tender: each form the consultation file asks,
 * filled by hand or delegated to the AI, then validated or rejected by a user.
 * A validated form is joined to the answer.
 */
export function AdministrativeSection({ tenderId, lots }: { tenderId: number; lots: { number: string; title: string | null }[] }) {
  const forms = useLoader(() => getAdministrativeForms(tenderId), `administrative-${tenderId}`);
  const [error, setError] = useState<string | null>(null);
  const list = forms.data ?? [];
  const absent = STANDARD_FORMS.filter((standard) => !list.some((form) => standard.present.test(form.label)));
  const validated = list.filter((form) => form.status === "VALIDATED").length;

  const add = async (label: string, category: PieceCategory) => {
    setError(null);
    try {
      await addPiece(tenderId, category, label);
      forms.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ajout impossible.");
    }
  };

  return (
    <div>
      <p className="muted">
        Les documents administratifs demandés par le DCE, d&apos;après l&apos;analyse. Pour chacun : 1. le modèle (trouvé dans le DCE,
        officiel ou déposé ici), 2. le remplissage par l&apos;IA, que vous vérifiez et validez. Signature et déclarations sur
        l&apos;honneur restent à faire vous-même.
      </p>
      {forms.error && <p className="banner error">{forms.error}</p>}
      {error && <p className="banner error">{error}</p>}
      {forms.data && list.length > 0 && (
        <p>
          <span className={validated === list.length ? "badge verified" : "badge"}>
            {validated} / {list.length} validé{validated > 1 ? "s" : ""}
          </span>
        </p>
      )}
      {forms.data && list.length === 0 && (
        <div className="inline-form">
          <button
            type="button"
            className="primary"
            onClick={async () => {
              setError(null);
              try {
                await prepareResponse(tenderId);
                forms.reload();
              } catch (err) {
                setError(err instanceof Error ? err.message : "Lecture de l'analyse impossible.");
              }
            }}
          >
            Reprendre les documents demandés par l&apos;analyse
          </button>
          <span className="muted">ou ajoutez-les ci-dessous.</span>
        </div>
      )}
      <ul className="admin-forms">
        {list.map((form) => (
          <FormItem
            key={form.pieceId}
            form={form}
            lots={lots}
            onChange={forms.reload}
          />
        ))}
      </ul>
      {forms.data && absent.length > 0 && (
        <div className="inline-form">
          <span className="muted">Ajouter :</span>
          {absent.map((standard) => (
            <button key={standard.label} type="button" onClick={() => add(standard.label, standard.category)}>
              {standard.label}
            </button>
          ))}
        </div>
      )}
      <CompanyDocuments tenderId={tenderId} />
    </div>
  );
}

/**
 * The standard documents the DCE asks (Kbis, URSSAF, tax and social certificates,
 * insurance, qualifications): taken from the company page, the latest still valid,
 * as soon as the pieces come from the analysis; another one can be chosen here.
 */
function CompanyDocuments({ tenderId }: { tenderId: number }) {
  const response = useLoader(() => getResponse(tenderId), `administrative-company-${tenderId}`);
  const [error, setError] = useState<string | null>(null);
  const view = response.data;
  const pieces = (view?.pieces ?? []).filter((piece) => piece.fill === null && piece.state !== "NOT_NEEDED");
  if (!view || pieces.length === 0) {
    return null;
  }
  const choose = async (piece: (typeof pieces)[number], companyDocumentId: number | null) => {
    setError(null);
    try {
      await changePiece(piece.id, { label: piece.label, notNeeded: false, companyDocumentId });
      response.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Choix impossible.");
    }
  };
  return (
    <>
      <h3>Documents de l&apos;entreprise demandés</h3>
      {error && <p className="banner error">{error}</p>}
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Document demandé</th>
              <th>Pris dans la fiche Entreprise</th>
              <th>État</th>
            </tr>
          </thead>
          <tbody>
            {pieces.map((piece) => {
              const document = view.documents.find((item) => item.id === piece.companyDocumentId);
              const [badge, text] = !document
                ? ["badge danger", "Absent de l'Entreprise"]
                : !document.hasFile || document.validity === "EXPIRED"
                  ? ["badge danger", document.hasFile ? "Expiré" : "Sans fichier"]
                  : document.validity === "EXPIRING"
                    ? ["badge warning", "Expire bientôt"]
                    : ["badge success", "Joint automatiquement"];
              return (
                <tr key={piece.id}>
                  <td>{piece.label}</td>
                  <td>
                    <select
                      value={piece.companyDocumentId ?? ""}
                      aria-label={`Document pour ${piece.label}`}
                      onChange={(event) => choose(piece, event.target.value ? Number(event.target.value) : null)}
                    >
                      <option value="">Aucun</option>
                      {view.documents.map((item) => (
                        <option key={item.id} value={item.id}>
                          {documentLabel(item)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <span className={badge}>{text}</span>
                    {(!document || !document.hasFile || document.validity === "EXPIRED") && (
                      <div className="small">
                        <Link href="/company">Ajouter dans Entreprise</Link>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}
