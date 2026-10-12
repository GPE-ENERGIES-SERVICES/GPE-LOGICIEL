"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";

import {
  DOCUMENT_STATE_LABELS,
  DOSSIER_STATUS_LABELS,
  fileFormat,
  formatDateTime,
  formatSize,
  PIECE_TYPE_LABELS,
} from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { changePieceType, getDossiers, retryDossier, uploadDossier } from "@/services/documentApi";
import { available, DEMO_DATA } from "@/services/http";
import type { DocumentInfo, Dossier, PieceType } from "@/types/document";

import { DceLinks } from "./DceLinks";
import { DocumentSearch } from "./DocumentSearch";

const ACCEPTED = ".zip,.pdf,.doc,.docx,.odt,.rtf,.xls,.xlsx,.xlsm,.ods,.csv,.dwg,.dxf,.jpg,.jpeg,.png,.tif,.tiff";

function DocumentRow({ document, onChanged }: { document: DocumentInfo; onChanged: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const analysed = document.state === "READ" || document.state === "SCANNED";

  const changeType = async (pieceType: PieceType) => {
    setError(null);
    try {
      await changePieceType(document.id, pieceType);
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Modification impossible.");
    }
  };

  return (
    <tr className={analysed ? "" : "row-muted"}>
      <td className="file-name">{document.fileName}</td>
      <td>{fileFormat(document.fileName)}</td>
      <td>
        <select
          value={document.pieceType}
          onChange={(e) => changeType(e.target.value as PieceType)}
          aria-label="Type de pièce"
        >
          {(Object.keys(PIECE_TYPE_LABELS) as PieceType[]).map((type) => (
            <option key={type} value={type}>
              {PIECE_TYPE_LABELS[type]}
            </option>
          ))}
        </select>
        {error && <p className="error">{error}</p>}
      </td>
      <td>
        <span className={analysed ? "badge" : "badge urgent"}>{DOCUMENT_STATE_LABELS[document.state]}</span>
        {document.stateDetail && <p className="hint">{document.stateDetail}</p>}
      </td>
      <td>{document.pageCount ?? "–"}</td>
      <td>{formatSize(document.sizeBytes)}</td>
      <td className="actions">
        {!DEMO_DATA && (
          <>
            {document.readable && (
              <a href={`/api/documents/${document.id}/pdf`} target="_blank" rel="noopener noreferrer">
                Ouvrir
              </a>
            )}
            <a href={`/api/documents/${document.id}/file`}>Télécharger</a>
          </>
        )}
      </td>
    </tr>
  );
}

function DossierView({ dossier, onChanged }: { dossier: Dossier; onChanged: () => void }) {
  const [retrying, setRetrying] = useState(false);
  const failed = dossier.documents.filter((document) => document.state === "FAILED").length;
  const notAnalysed = dossier.documents.filter(
    (document) => document.state === "FAILED" || document.state === "NOT_ANALYZED",
  ).length;

  const retry = async () => {
    setRetrying(true);
    try {
      await retryDossier(dossier.id);
      onChanged();
    } finally {
      setRetrying(false);
    }
  };

  return (
    <div className="dossier">
      <p>
        <strong>Version {dossier.version}</strong>{" "}
        {dossier.origin === "RETRIEVED"
          ? `récupérée sur la plateforme le ${formatDateTime(dossier.uploadedAt)}`
          : `déposée le ${formatDateTime(dossier.uploadedAt)}${dossier.uploadedBy ? ` par ${dossier.uploadedBy}` : ""}`}
        <span className={dossier.status === "FAILED" ? "badge urgent" : "badge"}>
          {DOSSIER_STATUS_LABELS[dossier.status]}
        </span>
      </p>
      {dossier.errorMessage && <p className="error">{dossier.errorMessage}</p>}
      {notAnalysed > 0 && (
        <p className="hint">
          {notAnalysed} document{notAnalysed > 1 ? "s" : ""} non analysé{notAnalysed > 1 ? "s" : ""} : les
          informations qu&apos;ils contiennent ne seront pas prises en compte.
        </p>
      )}
      {DEMO_DATA && (
        <p className="hint">
          Données de démonstration : les fichiers eux-mêmes ne sont pas disponibles. Les liens Ouvrir et
          Télécharger apparaîtront avec le backend.
        </p>
      )}
      {failed > 0 && dossier.status === "DONE" && (
        <button type="button" onClick={retry} disabled={retrying}>
          Relancer la lecture des documents en échec
        </button>
      )}
      <table>
        <thead>
          <tr>
            <th>Fichier</th>
            <th>Format</th>
            <th>Type de pièce</th>
            <th>État</th>
            <th>Pages</th>
            <th>Taille</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {dossier.documents.map((document) => (
            <DocumentRow key={document.id} document={document} onChanged={onChanged} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Consultation file (DCE) of a tender: upload, versions and reading state
 * of each document.
 */
export function DocumentsSection({ tenderId }: { tenderId: number }) {
  const { data, error, reload } = useLoader(() => getDossiers(tenderId), String(tenderId));
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const reading = data?.some((dossier) => dossier.status === "PENDING" || dossier.status === "PROCESSING") ?? false;

  // Documents are read in the background: refresh until it is over.
  useEffect(() => {
    if (!reading) {
      return;
    }
    const timer = window.setInterval(reload, 3000);
    return () => window.clearInterval(timer);
  }, [reading, reload]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const files = fileInput.current?.files;
    if (!files || files.length === 0) {
      setUploadError("Choisissez au moins un fichier.");
      return;
    }
    setUploading(true);
    setUploadError(null);
    try {
      await uploadDossier(tenderId, files);
      if (fileInput.current) {
        fileInput.current.value = "";
      }
      reload();
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : "Dépôt impossible.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <h3>Accès au DCE</h3>
      <DceLinks tenderId={tenderId} />
      <h3>Dossiers déposés</h3>
      <form className="upload" onSubmit={submit}>
        <p className="hint">
          Téléchargez le DCE sur la plateforme de l&apos;acheteur puis déposez-le ici (archive ZIP ou fichiers). Chaque
          dépôt crée une nouvelle version du dossier.
        </p>
        <input ref={fileInput} type="file" multiple accept={ACCEPTED} aria-label="Fichiers du DCE" />
        <button type="submit" className="primary" disabled={uploading}>
          {uploading ? "Dépôt en cours…" : "Déposer le dossier"}
        </button>
        {uploadError && <p className="error">{uploadError}</p>}
      </form>
      {error && <p className="error">{error}</p>}
      {data && data.length === 0 && <p className="muted">Aucun dossier déposé.</p>}
      {data?.[0]?.status === "DONE" && available("documentSearch") && <DocumentSearch tenderId={tenderId} />}
      {data?.map((dossier, index) =>
        index === 0 ? (
          <DossierView key={dossier.id} dossier={dossier} onChanged={reload} />
        ) : (
          <details key={dossier.id} className="previous-dossier">
            <summary>
              Version {dossier.version} du {formatDateTime(dossier.uploadedAt)} ({dossier.documents.length} fichiers)
            </summary>
            <DossierView dossier={dossier} onChanged={reload} />
          </details>
        ),
      )}
    </div>
  );
}
