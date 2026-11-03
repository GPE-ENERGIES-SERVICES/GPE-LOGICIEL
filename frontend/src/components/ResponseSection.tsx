"use client";

import { useState, type FormEvent } from "react";

import { formatDate, formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { DEMO_DATA } from "@/services/http";
import {
  addPiece,
  changePiece,
  checkPiece,
  deletePiece,
  getResponse,
  prepareResponse,
  removePieceFile,
  uploadPieceFile,
} from "@/services/responseApi";
import type { DocumentChoice, PieceCategory, PieceState, ResponsePiece, ResponseView } from "@/types/response";
import { Icon } from "./Icon";

const STATE_LABELS: Record<PieceState, string> = {
  MISSING: "Manquante",
  EXPIRED: "Expirée",
  TO_CHECK: "À vérifier",
  READY: "Prête",
  NOT_NEEDED: "Non concernée",
};

const STATE_CLASSES: Record<PieceState, string> = {
  MISSING: "badge urgent",
  EXPIRED: "badge urgent",
  TO_CHECK: "badge",
  READY: "badge verified",
  NOT_NEEDED: "badge muted-badge",
};

function documentLabel(document: DocumentChoice): string {
  const end = document.expiresOn ? ` (jusqu'au ${formatDate(document.expiresOn)})` : "";
  const problem = !document.hasFile ? " – sans fichier" : document.validity === "EXPIRED" ? " – expiré" : "";
  return `${document.name}${end}${problem}`;
}

function PieceRow({
  piece,
  documents,
  run,
}: {
  piece: ResponsePiece;
  documents: DocumentChoice[];
  run: (action: () => Promise<ResponseView>) => void;
}) {
  const change = (next: Partial<{ label: string; notNeeded: boolean; companyDocumentId: number | null }>) =>
    run(() =>
      changePiece(piece.id, {
        label: piece.label,
        notNeeded: piece.state === "NOT_NEEDED",
        companyDocumentId: piece.companyDocumentId,
        ...next,
      }),
    );
  const canCheck = piece.state === "TO_CHECK" || piece.state === "READY";

  return (
    <tr className={piece.state === "NOT_NEEDED" ? "row-muted" : ""}>
      <td>
        {/* The key follows the saved label: a change made elsewhere shows at once. */}
        <input
          key={piece.label}
          defaultValue={piece.label}
          maxLength={500}
          aria-label="Nom de la pièce"
          onBlur={(event) => {
            const label = event.target.value.trim();
            if (label && label !== piece.label) {
              change({ label });
            }
          }}
        />
        {!piece.fromAnalysis && <p className="hint">Ajoutée à la main</p>}
      </td>
      <td>
        <span className={STATE_CLASSES[piece.state]}>{STATE_LABELS[piece.state]}</span>
        <p className="hint">{piece.detail}</p>
        {piece.checkedBy && piece.checkedAt && (
          <p className="hint">
            Vérifiée par {piece.checkedBy} le {formatDateTime(piece.checkedAt)}
          </p>
        )}
      </td>
      <td>
        <select
          value={piece.companyDocumentId ?? ""}
          aria-label="Document de l'entreprise"
          disabled={piece.fileName !== null}
          onChange={(event) => change({ companyDocumentId: event.target.value ? Number(event.target.value) : null })}
        >
          <option value="">Aucun document de l&apos;entreprise</option>
          {documents.map((document) => (
            <option key={document.id} value={document.id}>
              {documentLabel(document)}
            </option>
          ))}
        </select>
        {piece.fileName ? (
          <p className="hint">
            <a href={`/api/response-pieces/${piece.id}/file`}>{piece.fileName}</a>{" "}
            <button type="button" className="link-button danger" onClick={() => run(() => removePieceFile(piece.id))}>
              Retirer
            </button>
          </p>
        ) : null}
        {!DEMO_DATA && (
          <label className="link-button">
            {piece.fileName ? "Remplacer le fichier" : "Déposer un fichier"}
            <input
              type="file"
              className="file-input"
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = "";
                if (file) {
                  run(() => uploadPieceFile(piece.id, file));
                }
              }}
            />
          </label>
        )}
      </td>
      <td className="response-actions">
        <label>
          <input
            type="checkbox"
            checked={piece.state === "READY"}
            disabled={!canCheck}
            onChange={(event) => run(() => checkPiece(piece.id, event.target.checked))}
          />{" "}
          Vérifiée
        </label>
        <button
          type="button"
          className="link-button"
          onClick={() => change({ notNeeded: piece.state !== "NOT_NEEDED" })}
        >
          {piece.state === "NOT_NEEDED" ? "Remettre dans le dossier" : "Non concernée"}
        </button>
        {!piece.fromAnalysis && (
          <button
            type="button"
            className="link-button danger"
            onClick={() => {
              if (window.confirm(`Supprimer « ${piece.label} » ?`)) {
                run(() => deletePiece(piece.id));
              }
            }}
          >
            Supprimer
          </button>
        )}
      </td>
    </tr>
  );
}

function PieceTable({
  title,
  pieces,
  documents,
  run,
}: {
  title: string;
  pieces: ResponsePiece[];
  documents: DocumentChoice[];
  run: (action: () => Promise<ResponseView>) => void;
}) {
  if (pieces.length === 0) {
    return null;
  }
  return (
    <>
      <h3>{title}</h3>
      <table>
        <thead>
          <tr>
            <th>Pièce</th>
            <th>État</th>
            <th>Ce qui est joint</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {pieces.map((piece) => (
            <PieceRow key={piece.id} piece={piece} documents={documents} run={run} />
          ))}
        </tbody>
      </table>
    </>
  );
}

/**
 * The answer to a tender, its last step: the pieces asked by the buyer, what
 * the logiciel proposes for each, and what a user changes and checks.
 */
export function ResponseSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(() => getResponse(tenderId), `response-${tenderId}`);
  // The state returned by the last change, until the next reload.
  const [saved, setSaved] = useState<ResponseView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const data = saved ?? loaded.data;

  const run = async (action: () => Promise<ResponseView>) => {
    setBusy(true);
    setError(null);
    try {
      setSaved(await action());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusy(false);
    }
  };

  const add = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const target = event.currentTarget;
    const form = new FormData(target);
    const label = String(form.get("label") ?? "").trim();
    if (label) {
      run(() => addPiece(tenderId, String(form.get("category")) as PieceCategory, label));
      target.reset();
    }
  };

  if (!data) {
    return loaded.error ? <p className="banner error">{loaded.error}</p> : null;
  }

  const needed = data.pieces.filter((piece) => piece.state !== "NOT_NEEDED");
  const ready = needed.filter((piece) => piece.state === "READY").length;
  const problems = needed.filter((piece) => piece.state !== "READY");

  return (
    <div aria-busy={busy}>
      <ul className="response-steps">
        {data.steps.map((step) => (
          <li key={step.title}>
            <a href={`#${step.anchor}`}>{step.title}</a> <span className={step.ready ? "field-toggle quiet" : "muted"}>{step.ready ? <Icon name="check" size={14} /> : step.detail}</span>
          </li>
        ))}
      </ul>

      <div className="form-actions">
        <button type="button" className="primary" onClick={() => run(() => prepareResponse(tenderId))} disabled={busy || !data.analysisDone}>
          {data.pieces.length === 0 ? "Préparer le dossier depuis la fiche IA" : "Ajouter les pièces de la dernière fiche IA"}
        </button>
        {!data.analysisDone && <span className="hint">Sans fiche IA, ajoutez les pièces à la main ci-dessous.</span>}
      </div>
      {error && <p className="banner error">{error}</p>}

      {needed.length > 0 && (
        <p>
          <strong>
            {ready} pièce{ready > 1 ? "s" : ""} prête{ready > 1 ? "s" : ""} sur {needed.length}
          </strong>
        </p>
      )}
      <PieceTable
        title="Pièces de candidature"
        pieces={data.pieces.filter((piece) => piece.category === "APPLICATION")}
        documents={data.documents}
        run={run}
      />
      <PieceTable
        title="Pièces de l'offre"
        pieces={data.pieces.filter((piece) => piece.category === "OFFER")}
        documents={data.documents}
        run={run}
      />

      <h3>Ajouter une pièce</h3>
      <form className="inline-form" onSubmit={add}>
        <select name="category" defaultValue="APPLICATION" aria-label="Partie du dossier">
          <option value="APPLICATION">Candidature</option>
          <option value="OFFER">Offre</option>
        </select>
        <input name="label" required maxLength={500} placeholder="Nom de la pièce" aria-label="Nom de la pièce à ajouter" />
        <button type="submit" disabled={busy}>
          Ajouter
        </button>
      </form>

      {data.pieces.length > 0 && !DEMO_DATA && (
        <>
          <h3>Exporter</h3>
          <p className="hint">
            Le ZIP contient les fichiers joints, rangés en Candidature et Offre. Relisez-le avant de déposer : la signature et
            le dépôt restent de votre côté, et l&apos;export du chiffrage (vos coûts) n&apos;en fait jamais partie.
          </p>
          <a
            className="button"
            href={`/api/tenders/${tenderId}/response/archive`}
            onClick={(event) => {
              if (
                problems.length > 0 &&
                !window.confirm(
                  `${problems.length} pièce(s) ne sont pas prêtes : ${problems.map((piece) => piece.label).join(", ")}. Exporter quand même ?`,
                )
              ) {
                event.preventDefault();
              }
            }}
          >
            Télécharger le dossier (ZIP)
          </a>
        </>
      )}
    </div>
  );
}
