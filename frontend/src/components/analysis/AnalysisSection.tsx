"use client";

import { useEffect, useState, type ReactNode } from "react";

import { FieldItem } from "@/components/analysis/FieldItem";
import { PartTitle } from "@/components/analysis/PartTitle";
import { ProgressBar } from "@/components/ProgressBar";
import { Fold } from "@/components/Fold";
import {
  ANALYSIS_STATUS_LABELS,
  attentionPoints,
  checkReason,
  effectiveValue,
  fieldsByKey,
  formatValue,
  NOT_IN_DOCUMENTS,
} from "@/lib/analysis";
import { DOCUMENT_STATE_LABELS, formatDateTime, PIECE_TYPE_LABELS } from "@/lib/format";
import { analysisProgress, readingProgress } from "@/lib/progress";
import { useLoader } from "@/lib/useLoader";
import {
  cancelAnalysis,
  getTenderAnalysis,
  sendAnalysisNow,
  startAnalysis,
  validateAnalysis,
} from "@/services/analysisApi";
import { getDossiers } from "@/services/documentApi";
import type { Analysis, ExtractedField, GridField, SummarySection, TenderAnalysis } from "@/types/analysis";

const costFormat = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 4 });

const SECTION_TITLES: Record<SummarySection, ReactNode> = {
  AT_A_GLANCE: (
    <PartTitle icon="eye" tone="accent">
      En un coup d&apos;œil
    </PartTitle>
  ),
  ATTENTION: (
    <PartTitle icon="warning" tone="warning">
      Points d&apos;attention
    </PartTitle>
  ),
  REQUESTED: (
    <PartTitle icon="file" tone="accent">
      Ce qui est demandé
    </PartTitle>
  ),
  TO_ANSWER: (
    <PartTitle icon="clipboard" tone="success">
      Pour répondre
    </PartTitle>
  ),
};

/** A value long enough to need the whole width of the grid. */
const LONG_VALUE = 60;

/** Several short values read on one line; a long one keeps its own. */
const SHORT_ITEM = 45;

function isFound(fields: ExtractedField[]): boolean {
  return fields.some((field) => field.state !== "NOT_INDICATED" || field.decision !== null);
}

/** What the documents do not say, on one line: each name opens the form that gives its value. */
function MissingFields({
  grids,
  analysis,
  onChanged,
}: {
  grids: GridField[];
  analysis: Analysis;
  onChanged: (analysis: TenderAnalysis) => void;
}) {
  const [filling, setFilling] = useState<string | null>(null);
  const groups = fieldsByKey(analysis);
  if (grids.length === 0) {
    return null;
  }
  const chosen = grids.find((grid) => grid.key === filling);
  const field = chosen ? groups.get(chosen.key)?.[0] : undefined;
  return (
    <div className="field-missing">
      <span className="muted">Non indiqué dans les documents : </span>
      {grids.map((grid, index) => (
        <span key={grid.key}>
          {index > 0 && <span className="muted"> · </span>}
          {groups.get(grid.key)?.length ? (
            <button
              type="button"
              className="link-button"
              title="Renseigner cette valeur"
              aria-expanded={filling === grid.key}
              onClick={() => setFilling(filling === grid.key ? null : grid.key)}
            >
              {grid.label}
            </button>
          ) : (
            <span className="muted">{grid.label}</span>
          )}
        </span>
      ))}
      {chosen && field && (
        <div className="field-filling">
          <strong>{chosen.label}</strong>
          <FieldItem key={field.id} field={field} grid={chosen} readable={false} onChanged={onChanged} filling />
        </div>
      )}
    </div>
  );
}

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
  const lengths = fields.map((field) => (effectiveValue(field) ?? field.value ?? "").length);
  const classes = ["field-group"];
  if (fields.length > 2 || lengths.some((length) => length > LONG_VALUE)) {
    classes.push("wide");
  }
  if (fields.length > 1 && lengths.every((length) => length <= SHORT_ITEM)) {
    classes.push("short-values");
  }
  return (
    <div className={classes.join(" ")}>
      <h4>{grid.label}</h4>
      {grid.key === "deadline" && analysis.noticeDeadline && (
        <p className="hint">Date limite publiée dans l&apos;avis : {formatDateTime(analysis.noticeDeadline)}</p>
      )}
      {fields.length === 0 && <p className="muted">{NOT_IN_DOCUMENTS}</p>}
      <div className="field-values">
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
      onValidated(await validateAnalysis(analysis.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Validation impossible.");
    } finally {
      setValidating(false);
    }
  };

  const section = (name: SummarySection) => {
    const grids = data.grid.filter((grid) => grid.section === name);
    return (
      <>
        <div className={name === "AT_A_GLANCE" ? "field-grid glance" : "field-grid"}>
          {grids
            .filter((grid) => isFound(groups.get(grid.key) ?? []))
            .map((grid) => (
              <FieldGroup key={grid.key} grid={grid} analysis={analysis} onChanged={onChanged} />
            ))}
        </div>
        <MissingFields
          grids={grids.filter((grid) => !isFound(groups.get(grid.key) ?? []))}
          analysis={analysis}
          onChanged={onChanged}
        />
      </>
    );
  };

  /** How many questions of a part found an answer in the documents. */
  const found = (name: SummarySection) => {
    const grids = data.grid.filter((grid) => grid.section === name);
    const answered = grids.filter((grid) =>
      (groups.get(grid.key) ?? []).some((field) => field.state !== "NOT_INDICATED" || field.decision !== null),
    ).length;
    return `${answered} sur ${grids.length} renseignée${grids.length > 1 ? "s" : ""}`;
  };
  const plural = (count: number, word: string) => `${count} ${word}${count > 1 ? "s" : ""}`;

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
        ✓ : extrait retrouvé dans le document ; l&apos;interprétation reste à confirmer en le lisant.
      </p>

      <Fold title={SECTION_TITLES.AT_A_GLANCE} count={found("AT_A_GLANCE")} open>
        {section("AT_A_GLANCE")}
      </Fold>

      <Fold
        open
        title={SECTION_TITLES.ATTENTION}
        count={points.length > 0 ? plural(points.length, "point") + " à surveiller" : found("ATTENTION")}
      >
        {points.length > 0 && (
          <ul className="attention">
            {points.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        )}
        {section("ATTENTION")}
      </Fold>

      <Fold title={SECTION_TITLES.REQUESTED} count={found("REQUESTED")}>
        {section("REQUESTED")}
      </Fold>

      <Fold title={SECTION_TITLES.TO_ANSWER} count={found("TO_ANSWER")}>
        {section("TO_ANSWER")}
      </Fold>

      <Fold
        title={
          <PartTitle icon="warning" tone={toCheck.length === 0 && notAnalysed.length === 0 ? "muted" : "warning"}>
            À vérifier
          </PartTitle>
        }
        count={toCheck.length === 0 && notAnalysed.length === 0 ? "rien à signaler" : plural(toCheck.length, "point")}
      >
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
      </Fold>

      <Fold
        title={
          <PartTitle icon="archive" tone="muted">
            Documents
          </PartTitle>
        }
        count={`${analysed.length} analysé${analysed.length > 1 ? "s" : ""} sur ${analysis.documents.length}`}
      >
        {notAnalysed.length === 0 ? (
          <p className="muted">Tous les documents du dossier ont été analysés.</p>
        ) : (
          <>
            <p className="hint">
              Non analysés : une information indiquée « non indiquée » peut s&apos;y trouver.
            </p>
            <ul className="documents-read muted">
              {notAnalysed.map((document) => (
                <li key={document.id}>
                  {document.fileName} — {notAnalysedReason(document)}
                </li>
              ))}
            </ul>
          </>
        )}
      </Fold>
    </div>
  );
}

function notAnalysedReason(document: Analysis["documents"][number]): string {
  // Why the rules kept it out; a piece meant to be sent but missing was left out for lack of room.
  if (
    document.aiReason &&
    !["DPGF", "BPU", "DQE"].includes(document.pieceType) &&
    !/^(Pièce essentielle|Document utile|Ajouté à la main)/.test(document.aiReason)
  ) {
    return document.aiReason;
  }
  if (document.state !== "READ" && document.state !== "SCANNED") {
    return document.stateDetail ?? DOCUMENT_STATE_LABELS[document.state];
  }
  if (["DPGF", "BPU", "DQE"].includes(document.pieceType)) {
    return `${PIECE_TYPE_LABELS[document.pieceType]} : lu par l'analyse financière`;
  }
  if (document.pieceType === "PLAN") {
    return "plan non analysé dans cette version";
  }
  if (/\.(xlsx?|xlsm|ods|csv)$/i.test(document.fileName)) {
    return "tableur lu : utilisé par l'analyse financière s'il est classé DPGF, BPU ou DQE";
  }
  return "volume ou nombre de documents trop important pour une seule analyse";
}

/**
 * Summary sheet of the consultation file: state of the AI analysis, the
 * extracted information with their sources, and the human validation.
 */
export function AnalysisSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(() => getTenderAnalysis(tenderId), String(tenderId));
  const dossiers = useLoader(() => getDossiers(tenderId), String(tenderId));
  // The analysis as returned by the last decision, until the next reload.
  const [saved, setSaved] = useState<TenderAnalysis | null>(null);
  const [starting, setStarting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  // The time shown with an analysis in progress, refreshed with the data.
  const [now, setNow] = useState(() => Date.now());
  const data = saved ?? loaded.data;
  const analysis = data?.latest ?? null;
  const latestDossier = dossiers.data?.[0] ?? null;
  const analysing = analysis?.status === "PENDING" || analysis?.status === "PROCESSING";
  // Not sent to the provider yet: can still be cancelled.
  const waiting = analysis?.status === "PENDING" || analysis?.status === "RETRY_WAITING";
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
      setNow(Date.now());
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
      await startAnalysis(latestDossier.id, force);
      setSaved(null);
      reloadAnalysis();
    } catch (err) {
      setStartError(err instanceof Error ? err.message : "Impossible de lancer l'analyse.");
    } finally {
      setStarting(false);
    }
  };

  const cancel = async () => {
    if (!analysis) {
      return;
    }
    setCancelling(true);
    setStartError(null);
    try {
      setSaved(await cancelAnalysis(analysis.id));
    } catch (err) {
      setStartError(err instanceof Error ? err.message : "Impossible d'annuler l'analyse.");
    } finally {
      setCancelling(false);
    }
  };

  if (loaded.error) {
    return <p className="error">{loaded.error}</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }
  const readingNow = reading && latestDossier ? readingProgress(latestDossier) : null;
  const analysisNow = analysis ? analysisProgress(analysis, data.history, now) : null;

  return (
    <div>
      {!data.configured && (
        <p className="banner info">
          Analyse par IA indisponible : {data.unavailableReason ?? "aucun fournisseur d'IA n'est configuré."} Les
          documents déposés restent consultables.
        </p>
      )}

      {!latestDossier && <p className="muted">Déposez le DCE ci-dessus pour obtenir la fiche de synthèse.</p>}
      {readingNow && <ProgressBar {...readingNow} />}

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
      {analysisNow && <ProgressBar {...analysisNow} />}
      {analysis?.errorMessage && (
        <p className={analysis.status === "CANCELLED" ? "muted" : "error"}>{analysis.errorMessage}</p>
      )}
      {analysis?.status === "RETRY_WAITING" && analysis.nextAttemptAt && (
        <p className="hint">
          Tentative {analysis.attempts} échouée ; nouvelle tentative automatique après{" "}
          {formatDateTime(analysis.nextAttemptAt)}.
        </p>
      )}
      {waiting && (
        <div className="form-actions">
          {analysis?.nextAttemptAt && new Date(analysis.nextAttemptAt).getTime() > now && (
            <button
              type="button"
              className="primary"
              onClick={async () => {
                setStartError(null);
                try {
                  setSaved(await sendAnalysisNow(analysis.id));
                } catch (err) {
                  setStartError(err instanceof Error ? err.message : "Envoi impossible.");
                }
              }}
            >
              Lancer maintenant
            </button>
          )}
          <button type="button" className="secondary" onClick={cancel} disabled={cancelling}>
            Annuler l&apos;analyse
          </button>
        </div>
      )}

      {data.configured && latestDossier?.status === "DONE" && !analysing && !waiting && (
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
