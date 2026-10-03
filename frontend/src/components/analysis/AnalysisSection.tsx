"use client";

import { useEffect, useState } from "react";

import { FieldItem } from "@/components/analysis/FieldItem";
import {
  ANALYSIS_STATUS_LABELS,
  attentionPoints,
  checkReason,
  fieldsByKey,
  formatValue,
  NOT_IN_DOCUMENTS,
} from "@/lib/analysis";
import { api } from "@/lib/api";
import { DOCUMENT_STATE_LABELS, formatDateTime, PIECE_TYPE_LABELS } from "@/lib/format";
import type { Analysis, Dossier, GridField, SummarySection, TenderAnalysis } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

const costFormat = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 4 });

const SECTION_TITLES: Record<SummarySection, string> = {
  AT_A_GLANCE: "1. En un coup d'œil",
  ATTENTION: "2. Points d'attention",
  REQUESTED: "3. Ce qui est demandé",
  TO_ANSWER: "4. Pour répondre",
};

function FieldGroup({
  grid,
  analysis,
  onChanged,
}: {
  grid: GridField;
  analysis: Analysis;
  onChanged: (analysis: TenderAnalysis) => void;
}) {
  const fields = fieldsByKey(analysis).get(grid.key) ?? [];
  const readable = new Set(analysis.documents.filter((document) => document.readable).map((document) => document.id));
  return (
    <div className="field-group">
      <h4>{grid.label}</h4>
      {grid.key === "deadline" && analysis.noticeDeadline && (
        <p className="hint">Date limite publiée dans l&apos;avis : {formatDateTime(analysis.noticeDeadline)}</p>
      )}
      {fields.length === 0 && <p className="muted">{NOT_IN_DOCUMENTS}</p>}
      {fields.map((field) => (
        <FieldItem
          key={field.id}
          field={field}
          grid={grid}
          readable={field.documentId !== null && readable.has(field.documentId)}
          onChanged={onChanged}
        />
      ))}
    </div>
  );
}

function SummarySheet({
  data,
  analysis,
  onChanged,
  onValidated,
}: {
  data: TenderAnalysis;
  analysis: Analysis;
  onChanged: (analysis: TenderAnalysis) => void;
  onValidated: (analysis: TenderAnalysis) => void;
}) {
  const [validating, setValidating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const groups = fieldsByKey(analysis);
  const points = attentionPoints(analysis, data.grid);
  const toCheck = data.grid.flatMap((grid) =>
    (groups.get(grid.key) ?? [])
      .map((field) => ({ field, reason: checkReason(field) }))
      .filter((item) => item.reason !== null)
      .map(({ field, reason }) => ({
        id: field.id,
        text: grid.multiple && field.value ? `${grid.label} : ${formatValue(grid.kind, field.value)}` : grid.label,
        reason,
      })),
  );
  const analysed = analysis.documents.filter((document) => document.analysed);
  const notAnalysed = analysis.documents.filter((document) => !document.analysed);

  const validate = async () => {
    setValidating(true);
    setError(null);
    try {
      onValidated(await api<TenderAnalysis>(`/api/analyses/${analysis.id}/validation`, { method: "POST" }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Validation impossible.");
    } finally {
      setValidating(false);
    }
  };

  const section = (name: SummarySection) =>
    data.grid
      .filter((grid) => grid.section === name)
      .map((grid) => <FieldGroup key={grid.key} grid={grid} analysis={analysis} onChanged={onChanged} />);

  return (
    <div className="summary">
      <div className="sheet-state">
        {analysis.validatedBy ? (
          <span className="badge verified">
            Fiche validée par {analysis.validatedBy} le {formatDateTime(analysis.validatedAt)}
          </span>
        ) : (
          <>
            <span className="badge urgent">Analysée, non vérifiée</span>
            <button type="button" className="primary" onClick={validate} disabled={validating}>
              Valider la fiche
            </button>
          </>
        )}
        {error && <p className="error">{error}</p>}
      </div>
      <p className="hint">
        Chaque information renvoie à sa source. « Source vérifiée » signifie que l&apos;extrait cité a été retrouvé
        dans le document : l&apos;interprétation reste à confirmer en le lisant.
      </p>

      <h3>{SECTION_TITLES.AT_A_GLANCE}</h3>
      {section("AT_A_GLANCE")}

      <h3>{SECTION_TITLES.ATTENTION}</h3>
      {points.length > 0 && (
        <ul className="attention">
          {points.map((point) => (
            <li key={point}>{point}</li>
          ))}
        </ul>
      )}
      {section("ATTENTION")}

      <h3>{SECTION_TITLES.REQUESTED}</h3>
      {section("REQUESTED")}

      <h3>{SECTION_TITLES.TO_ANSWER}</h3>
      {section("TO_ANSWER")}

      <h3>5. À vérifier</h3>
      {toCheck.length === 0 && notAnalysed.length === 0 ? (
        <p className="muted">Rien à signaler.</p>
      ) : (
        <ul className="to-check">
          {toCheck.map((item) => (
            <li key={item.id}>
              {item.text} — {item.reason}
            </li>
          ))}
          {notAnalysed.length > 0 && (
            <li>
              {notAnalysed.length} document{notAnalysed.length > 1 ? "s" : ""} non analysé
              {notAnalysed.length > 1 ? "s" : ""} : une information indiquée « non indiquée » peut s&apos;y trouver.
            </li>
          )}
        </ul>
      )}

      <h3>6. Documents analysés</h3>
      <ul className="documents-read">
        {analysed.map((document) => (
          <li key={document.id}>
            {document.fileName} <span className="muted">({PIECE_TYPE_LABELS[document.pieceType]})</span>
          </li>
        ))}
      </ul>
      {notAnalysed.length > 0 && (
        <>
          <p className="hint">Non analysés :</p>
          <ul className="documents-read muted">
            {notAnalysed.map((document) => (
              <li key={document.id}>
                {document.fileName} — {notAnalysedReason(document)}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function notAnalysedReason(document: Analysis["documents"][number]): string {
  if (document.state !== "READ" && document.state !== "SCANNED") {
    return document.stateDetail ?? DOCUMENT_STATE_LABELS[document.state];
  }
  if (["CCAP", "CCTP", "DPGF", "BPU", "DQE", "PLAN"].includes(document.pieceType)) {
    return `${PIECE_TYPE_LABELS[document.pieceType]} non analysé dans cette version`;
  }
  return "volume trop important pour une seule analyse";
}

/**
 * Summary sheet of the consultation file: state of the AI analysis, the
 * extracted information with their sources, and the human validation.
 */
export function AnalysisSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(() => api<TenderAnalysis>(`/api/tenders/${tenderId}/analysis`), String(tenderId));
  const dossiers = useLoader(() => api<Dossier[]>(`/api/tenders/${tenderId}/dossiers`), String(tenderId));
  // The analysis as returned by the last decision, until the next reload.
  const [saved, setSaved] = useState<TenderAnalysis | null>(null);
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  const data = saved ?? loaded.data;
  const analysis = data?.latest ?? null;
  const latestDossier = dossiers.data?.[0] ?? null;
  const analysing = analysis?.status === "PENDING" || analysis?.status === "PROCESSING";
  const reading = latestDossier?.status === "PENDING" || latestDossier?.status === "PROCESSING";
  const { reload: reloadAnalysis } = loaded;
  const { reload: reloadDossiers } = dossiers;

  // Reading and analysis run in the background: refresh until they are over.
  useEffect(() => {
    if (!analysing && !reading) {
      return;
    }
    const timer = window.setInterval(() => {
      setSaved(null);
      reloadAnalysis();
      reloadDossiers();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [analysing, reading, reloadAnalysis, reloadDossiers]);

  const start = async () => {
    if (!latestDossier) {
      return;
    }
    setStarting(true);
    setStartError(null);
    // Content already analysed is analysed again only on an explicit request (RM9).
    const force = analysis?.dossierId === latestDossier.id;
    try {
      await api(`/api/dossiers/${latestDossier.id}/analyses?force=${force}`, { method: "POST" });
      setSaved(null);
      reloadAnalysis();
    } catch (err) {
      setStartError(err instanceof Error ? err.message : "Impossible de lancer l'analyse.");
    } finally {
      setStarting(false);
    }
  };

  if (loaded.error) {
    return <p className="error">{loaded.error}</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }

  return (
    <div>
      {!data.configured && (
        <p className="banner info">
          Aucun fournisseur d&apos;IA n&apos;est configuré : l&apos;analyse automatique n&apos;est pas disponible. Les
          documents déposés restent consultables.
        </p>
      )}

      {!latestDossier && <p className="muted">Déposez le DCE ci-dessus pour obtenir la fiche de synthèse.</p>}
      {reading && <p className="muted">Lecture du dossier en cours…</p>}

      {analysis && (
        <p className="analysis-meta">
          <span className={analysis.status === "FAILED" ? "badge urgent" : "badge"}>
            {ANALYSIS_STATUS_LABELS[analysis.status]}
          </span>{" "}
          Dossier version {analysis.dossierVersion}, analyse du {formatDateTime(analysis.createdAt)}
          {analysis.cost !== null && <> — coût {costFormat.format(analysis.cost)}</>}
          {data.history.length > 1 && (
            <span className="muted">
              {" "}
              ({data.history.length - 1} analyse{data.history.length > 2 ? "s" : ""} précédente
              {data.history.length > 2 ? "s" : ""} conservée{data.history.length > 2 ? "s" : ""})
            </span>
          )}
        </p>
      )}
      {analysis?.errorMessage && <p className="error">{analysis.errorMessage}</p>}

      {data.configured && latestDossier?.status === "DONE" && !analysing && (
        <div className="form-actions">
          <button type="button" className={analysis ? "secondary" : "primary"} onClick={start} disabled={starting}>
            {analysis ? "Relancer l'analyse" : "Lancer l'analyse"}
          </button>
          {analysis && analysis.dossierId !== latestDossier.id && (
            <span className="hint">Une version plus récente du dossier a été déposée.</span>
          )}
        </div>
      )}
      {startError && <p className="error">{startError}</p>}

      {analysis?.status === "DONE" && (
        <SummarySheet data={data} analysis={analysis} onChanged={setSaved} onValidated={setSaved} />
      )}
    </div>
  );
}
