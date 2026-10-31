"use client";

import { useEffect, useState } from "react";

import { Fold } from "@/components/Fold";
import { ANALYSIS_STATUS_LABELS } from "@/lib/analysis";
import { DOCUMENT_STATE_LABELS, formatDateTime, PIECE_TYPE_LABELS } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getFinancialAnalysis, startFinancialAnalysis } from "@/services/analysisApi";
import { getDossiers } from "@/services/documentApi";
import type { AnalysisDocument, FinancialPoint, FinancialResult, PriceCheck } from "@/types/analysis";

const amountFormat = new Intl.NumberFormat("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const costFormat = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 4 });

const ROLE_LABELS: Record<PriceCheck["columns"][number]["role"], string> = {
  REFERENCE: "N°",
  DESIGNATION: "Désignation",
  UNIT: "Unité",
  QUANTITY: "Quantité",
  UNIT_PRICE: "Prix unitaire",
  AMOUNT: "Montant",
};

const TOTAL_LABELS = { HT: "Total", VAT: "TVA", TTC: "TTC" } as const;

function amount(value: number | null): string {
  return value === null ? "—" : `${amountFormat.format(value)} €`;
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count > 1 ? "s" : ""}`;
}

/** Where a point comes from: the row of a sheet or the page of a PDF, shown on request when verified. */
function PointSource({ point, documents }: { point: FinancialPoint; documents: AnalysisDocument[] }) {
  const [shown, setShown] = useState(false);
  const document = documents.find((item) => item.id === point.documentId);
  if (!point.verified) {
    return document ? (
      <span className="badge urgent" title="L'extrait cité par l'IA n'a pas été retrouvé à l'endroit indiqué.">
        Source non retrouvée
      </span>
    ) : null;
  }
  const place = point.sheet
    ? `feuille « ${point.sheet} », ligne ${point.row}`
    : `page ${point.page}`;
  return (
    <>
      <button
        type="button"
        className="badge verified badge-toggle"
        aria-expanded={shown}
        title={shown ? "Masquer la source" : "Afficher la source et l'extrait cité"}
        onClick={() => setShown(!shown)}
      >
        Source vérifiée
        <span className="badge-chevron" aria-hidden="true">
          {shown ? "▴" : "▾"}
        </span>
      </button>
      {shown && document && (
        <span className="decision-source">
          <span className="source">
            {!point.sheet && document.readable ? (
              <a href={`/api/documents/${document.id}/pdf#page=${point.page}`} target="_blank" rel="noopener noreferrer">
                {document.fileName}, {place}
              </a>
            ) : (
              <>
                {document.fileName}, {place}
              </>
            )}
          </span>
          <span className="quote">« {point.quote} »</span>
        </span>
      )}
    </>
  );
}

function Points({ points, documents, empty }: { points: FinancialPoint[]; documents: AnalysisDocument[]; empty: string }) {
  if (points.length === 0) {
    return <p className="muted">{empty}</p>;
  }
  return (
    <ul className="decision-points">
      {points.map((point, index) => (
        <li key={index}>
          <span className="decision-point-text">{point.text}</span>
          <PointSource point={point} documents={documents} />
        </li>
      ))}
    </ul>
  );
}

/** What the software found in one sheet, without AI. */
function SheetCheck({ check }: { check: PriceCheck }) {
  const inconsistent = check.issues.filter((issue) => issue.kind.endsWith("MISMATCH")).length;
  const count =
    check.headerRow === null
      ? "colonnes non reconnues"
      : [
          plural(check.items, "poste"),
          check.blankPrices ? "prix à remplir par les candidats" : `${check.withUnitPrice} prix unitaires`,
          inconsistent > 0 ? plural(inconsistent, "incohérence") : null,
        ]
          .filter(Boolean)
          .join(" · ");
  return (
    <Fold title={`${check.fileName} — ${check.sheet}`} count={count} className="price-sheet">
      {check.headerRow !== null && (
        <dl className="facts grid">
          <div>
            <dt>Colonnes (ligne {check.headerRow})</dt>
            <dd>{check.columns.map((column) => `${ROLE_LABELS[column.role]} ${column.ref}`).join(", ")}</dd>
          </div>
          <div>
            <dt>Postes</dt>
            <dd>
              {check.items} · {check.withQuantity} avec quantité · {check.withUnitPrice} avec prix unitaire ·{" "}
              {check.withAmount} avec montant
              {check.textPrices > 0 && <> · {check.textPrices} en toutes lettres (PM, inclus…)</>}
            </dd>
          </div>
          <div>
            <dt>Somme des montants (calculée)</dt>
            <dd>
              {amount(check.computedTotal)}
              {check.amountBasis && ` ${check.amountBasis}`}
            </dd>
          </div>
        </dl>
      )}
      {check.totals.length > 0 && (
        <table className="price-totals">
          <thead>
            <tr>
              <th>Total écrit</th>
              <th>Cellule</th>
              <th>Écrit</th>
              <th>Calculé</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {check.totals.map((total) => (
              <tr key={total.ref}>
                <td>
                  {total.label} <span className="muted">({TOTAL_LABELS[total.kind]})</span>
                </td>
                <td>{total.ref}</td>
                <td>{amount(total.declared)}</td>
                <td>{amount(total.computed)}</td>
                <td>
                  {total.consistent === true && <span className="badge verified">Cohérent</span>}
                  {total.consistent === false && <span className="badge urgent">Incohérent</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {check.issues.length > 0 && (
        <ul className="to-check">
          {check.issues.map((issue, index) => (
            <li key={index}>
              {issue.ref && <strong>{issue.ref} : </strong>}
              {issue.message}
            </li>
          ))}
          {check.moreIssues > 0 && <li>… et {check.moreIssues} autres.</li>}
        </ul>
      )}
    </Fold>
  );
}

function Result({ result, documents }: { result: FinancialResult; documents: AnalysisDocument[] }) {
  const inconsistent = result.checks.reduce(
    (sum, check) => sum + check.issues.filter((issue) => issue.kind.endsWith("MISMATCH")).length,
    0,
  );
  return (
    <div className="decision financial">
      {result.overview && <p className="verdict-conclusion">{result.overview}</p>}

      <Fold
        title="Contrôles des montants (sans IA)"
        count={
          result.checks.length === 0
            ? "aucun tableur"
            : `${plural(result.checks.length, "feuille")} · ${plural(inconsistent, "incohérence")}`
        }
      >
        {result.checks.length === 0 ? (
          <p className="muted">Pièces en PDF uniquement : les montants n&apos;ont pas pu être contrôlés par le logiciel.</p>
        ) : (
          result.checks.map((check) => <SheetCheck key={`${check.documentId}-${check.sheet}`} check={check} />)
        )}
      </Fold>

      <Fold title="Postes principaux" count={`${result.items.length}`}>
        <Points points={result.items} documents={documents} empty="Aucun poste relevé." />
      </Fold>

      <Fold title="Points à vérifier" count={`${result.points.length}`}>
        <Points points={result.points} documents={documents} empty="Rien à signaler." />
      </Fold>

      <Fold title="Limites de l'analyse" className="decision-limits">
        <ul>
          {result.limits.map((limit) => (
            <li key={limit}>{limit}</li>
          ))}
          {result.notSent.map((reason) => (
            <li key={reason}>Non analysé : {reason}</li>
          ))}
        </ul>
        <p>
          Les totaux et incohérences sont calculés par le logiciel sur les cellules ; la synthèse est rédigée par
          l&apos;IA, qui ne propose aucun prix ni aucune rentabilité.
        </p>
      </Fold>
    </div>
  );
}

/**
 * Analysis of the price schedules (DPGF, BPU, DQE), started by a user only:
 * checks of the amounts made by the software, then the synthesis of the AI
 * with the source of each point. Displaying it never starts an analysis.
 */
export function FinancialSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(() => getFinancialAnalysis(tenderId), String(tenderId));
  const dossiers = useLoader(() => getDossiers(tenderId), String(tenderId));
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const data = loaded.data;
  const analysis = data?.latest ?? null;
  const latestDossier = dossiers.data?.[0] ?? null;
  const running =
    analysis?.status === "PENDING" || analysis?.status === "PROCESSING" || analysis?.status === "RETRY_WAITING";
  const readable = (data?.pieces ?? []).filter((piece) => piece.state === "READ" || piece.state === "SCANNED");
  const { reload } = loaded;

  // An analysis in progress: refresh until it is over.
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(reload, 4000);
    return () => window.clearInterval(timer);
  }, [running, reload]);

  const start = async () => {
    if (!latestDossier) {
      return;
    }
    setStarting(true);
    setError(null);
    try {
      await startFinancialAnalysis(latestDossier.id, analysis?.dossierId === latestDossier.id);
      reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Impossible de lancer l'analyse financière.");
    } finally {
      setStarting(false);
    }
  };

  if (loaded.error && !data) {
    return <p className="muted">Analyse financière indisponible pour le moment.</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }

  return (
    <div>
      {data.pieces.length === 0 ? (
        <p className="muted">
          Aucun DPGF, BPU ou DQE dans le dernier dossier déposé. Si une pièce de prix est mal classée, corrigez son type
          dans « Documents (DCE) ».
        </p>
      ) : (
        <ul className="documents-read">
          {data.pieces.map((piece) => (
            <li key={piece.id}>
              {piece.fileName} <span className="muted">({PIECE_TYPE_LABELS[piece.pieceType]})</span>{" "}
              {piece.state === "READ" || piece.state === "SCANNED" ? (
                piece.stateDetail && <span className="hint">— {piece.stateDetail}</span>
              ) : (
                <span className="badge urgent">{piece.stateDetail ?? DOCUMENT_STATE_LABELS[piece.state]}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      {analysis && (
        <p className="analysis-meta">
          <span className={analysis.status === "FAILED" ? "badge urgent" : "badge"}>
            {ANALYSIS_STATUS_LABELS[analysis.status]}
          </span>{" "}
          Analyse du {formatDateTime(analysis.createdAt)}
          {analysis.cost !== null && <> — coût {costFormat.format(analysis.cost)}</>}
          {data.previous > 0 && <span className="muted"> ({plural(data.previous, "analyse précédente")} conservée{data.previous > 1 ? "s" : ""})</span>}
        </p>
      )}
      {analysis?.errorMessage && (
        <p className={analysis.status === "CANCELLED" ? "muted" : "error"}>{analysis.errorMessage}</p>
      )}

      {!data.configured && readable.length > 0 && (
        <p className="banner info">
          Analyse par IA indisponible : {data.unavailableReason ?? "aucun fournisseur d'IA n'est configuré."}
        </p>
      )}
      {data.configured && readable.length > 0 && latestDossier?.status === "DONE" && !running && (
        <div className="form-actions">
          <button type="button" className={analysis ? "secondary" : "primary"} onClick={start} disabled={starting}>
            {analysis ? "Relancer l'analyse financière" : "Lancer l'analyse financière"}
          </button>
          <span className="hint">Appel à l&apos;IA, payant : lancé seulement sur ce bouton.</span>
        </div>
      )}
      {running && <p className="muted">Analyse financière en cours…</p>}
      {error && <p className="error">{error}</p>}

      {analysis?.status === "DONE" && data.result && <Result result={data.result} documents={analysis.documents} />}
    </div>
  );
}
