"use client";

import { useState } from "react";

import { useLoader } from "@/lib/useLoader";
import { addPiece, getAdministrativeForms, getResponse } from "@/services/responseApi";
import type { AdministrativeForm, PieceCategory, ResponsePiece, ResponseView } from "@/types/response";

import { FORM_STATUS_CLASSES, FORM_STATUS_LABELS, FormPanel } from "./FormPanel";
import { PieceWriter } from "./ResponseSection";

/** The forms the software fills today; others are added as pieces of the answer. */
const STANDARD_FORMS: { label: string; category: PieceCategory; present: RegExp }[] = [
  { label: "Formulaire DC1", category: "APPLICATION", present: /\bdc ?1\b/i },
  { label: "Formulaire DC2", category: "APPLICATION", present: /\bdc ?2\b/i },
  { label: "Acte d'engagement", category: "OFFER", present: /engagement|\bae\b/i },
];

function FormItem({
  form,
  piece,
  run,
  lots,
  onChange,
}: {
  form: AdministrativeForm;
  /** The piece of the answer this form is, to write it when the DCE gives no Word form. */
  piece: ResponsePiece | undefined;
  run: (action: () => Promise<ResponseView>) => void;
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
          withoutTemplate={
            piece && (
              <>
                <p className="hint">
                  Le DCE ne fournit pas ce formulaire en Word : rédigez-le ici, à la main ou avec l&apos;IA. Il est joint au
                  dossier de réponse en Word ; vous pouvez aussi y déposer votre propre fichier.
                </p>
                <PieceWriter piece={piece} run={run} />
              </>
            )
          }
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
  const response = useLoader(() => getResponse(tenderId), `administrative-response-${tenderId}`);
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
        Les formulaires du DCE, remplis ici à la main ou par l&apos;IA, puis joints au dossier de réponse. Signature et déclarations
        sur l&apos;honneur restent à faire vous-même.
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
        <p className="muted">
          Aucun formulaire pour l&apos;instant : ils sont repris de la fiche IA dans l&apos;onglet « Dossier de réponse », ou ajoutez-les
          ci-dessous.
        </p>
      )}
      <ul className="admin-forms">
        {list.map((form) => (
          <FormItem
            key={form.pieceId}
            form={form}
            piece={response.data?.pieces.find((piece) => piece.id === form.pieceId)}
            run={async (action) => {
              setError(null);
              try {
                await action();
              } catch (err) {
                setError(err instanceof Error ? err.message : "Action impossible.");
              }
              response.reload();
              forms.reload();
            }}
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
    </div>
  );
}
