"use client";

import { useEffect, useState } from "react";

import { Fold } from "@/components/Fold";
import { useLoader } from "@/lib/useLoader";
import { getTenderAnalysis } from "@/services/analysisApi";
import type { Analysis, DecisionPoint, ObstacleKind, Verdict } from "@/types/analysis";

const VERDICT_LABELS: Record<Verdict, string> = {
  PRIORITY: "À étudier en priorité",
  EXAMINE: "À examiner",
  POOR_FIT: "Peu adapté a priori",
  INSUFFICIENT: "Informations insuffisantes",
};

const OBSTACLE_LABELS: Record<ObstacleKind, string> = {
  CONFIRMED: "Confirmé",
  TO_CHECK: "À vérifier",
  MISSING: "Information manquante",
};

/** How many obstacles of a kind, for the title of the closed list. */
function obstacleCount(kind: ObstacleKind, count: number): string {
  const s = count > 1 ? "s" : "";
  if (kind === "CONFIRMED") {
    return `${count} confirmé${s}`;
  }
  return kind === "TO_CHECK" ? `${count} à vérifier` : `${count} information${s} manquante${s}`;
}

/** Where to go to start, follow or run again the analysis. */
const ANALYSIS_LINK = (
  <a href="#fiche">Fiche IA</a>
);

/** The source of an argument or an obstacle: shown on request when its quote was found, flagged otherwise. */
function PointSource({ point, analysis }: { point: DecisionPoint; analysis: Analysis }) {
  const [shown, setShown] = useState(false);
  const document = analysis.documents.find((item) => item.id === point.documentId);
  if (!point.verified) {
    return point.documentId !== null ? (
      <span className="badge urgent" title="L'extrait cité par l'IA n'a pas été retrouvé dans le document.">
        Source non retrouvée
      </span>
    ) : null;
  }
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
      {shown && (
        <span className="decision-source">
          {document && (
            <span className="source">
              {document.readable ? (
                <a href={`/api/documents/${document.id}/pdf#page=${point.page}`} target="_blank" rel="noopener noreferrer">
                  {document.fileName}, page {point.page}
                </a>
              ) : (
                <>
                  {document.fileName}, page {point.page}
                </>
              )}
            </span>
          )}
          <span className="quote">« {point.quote} »</span>
        </span>
      )}
    </>
  );
}

function Points({
  points,
  analysis,
  empty,
}: {
  points: DecisionPoint[];
  analysis: Analysis;
  empty: string;
}) {
  if (points.length === 0) {
    return <p className="muted">{empty}</p>;
  }
  return (
    <ul className="decision-points">
      {points.map((point, index) => (
        <li key={index}>
          <span className="decision-point-text">
            {point.kind && (
              <span className={`decision-kind kind-${point.kind.toLowerCase().replace("_", "-")}`}>
                {OBSTACLE_LABELS[point.kind]}
              </span>
            )}
            {point.text}
            {point.deduced && (
              <span className="decision-deduced" title="Déduit de plusieurs éléments, non écrit tel quel dans le DCE">
                {" "}
                (déduction)
              </span>
            )}
          </span>
          <PointSource point={point} analysis={analysis} />
        </li>
      ))}
    </ul>
  );
}

/** Limits are a reminder, not a report: the details stay in the analysis section. */
const MAX_LIMITS = 3;

function DecisionSheet({ analysis }: { analysis: Analysis }) {
  const decision = analysis.decision!;
  const documentName = (id: number | null) => analysis.documents.find((document) => document.id === id)?.fileName;
  const analysed = analysis.documents.filter((document) => document.analysed).length;
  const notAnalysed = analysis.documents.length - analysed;
  const limits = decision.limits.slice(0, MAX_LIMITS);

  const kinds = (["CONFIRMED", "TO_CHECK", "MISSING"] as const)
    .map((kind) => [kind, decision.obstacles.filter((point) => point.kind === kind).length] as const)
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => obstacleCount(kind, count))
    .join(" · ");

  return (
    <div className="decision">
      <div className={`verdict verdict-${decision.verdict.toLowerCase().replace("_", "-")}`}>
        <span className="verdict-label">{VERDICT_LABELS[decision.verdict]}</span>
        {decision.conclusion && <p className="verdict-conclusion">{decision.conclusion}</p>}
      </div>
      <p className="decision-scope">
        {analysed} pièce{analysed > 1 ? "s" : ""} analysée{analysed > 1 ? "s" : ""} sur {analysis.documents.length}
        {notAnalysed > 0 && <> (détail dans {ANALYSIS_LINK})</>}
      </p>

      <Fold title="Pourquoi l'étudier" count={decision.strengths.length > 0 ? `${decision.strengths.length}` : "aucun argument"}>
        <Points points={decision.strengths} analysis={analysis} empty="Aucun argument favorable identifié dans les documents." />
      </Fold>

      <Fold title="Obstacles et risques" count={kinds || "aucun"}>
        <Points points={decision.obstacles} analysis={analysis} empty="Aucun obstacle identifié dans les documents analysés." />
      </Fold>

      {decision.checks.length > 0 && (
        <Fold title="À vérifier avant de décider" count={`${decision.checks.length}`}>
          <ol className="decision-checks">
            {decision.checks.map((check, index) => (
              <li key={index}>
                <strong>{check.action}</strong>
                {check.reason && <> — {check.reason}</>}
                {documentName(check.documentId) && <span className="muted"> ({documentName(check.documentId)})</span>}
              </li>
            ))}
          </ol>
        </Fold>
      )}

      <Fold title="Limites de l'analyse" className="decision-limits">
        {limits.length > 0 && (
          <ul>
            {limits.map((limit) => (
              <li key={limit}>{limit}</li>
            ))}
          </ul>
        )}
        <p>
          Capacités de GPE inconnues du logiciel ; seuls les points « Source vérifiée » sont appuyés par un extrait.
        </p>
      </Fold>
    </div>
  );
}

/**
 * Decision help read with the analysis of the consultation file: verdict,
 * arguments, obstacles, checks and limits. It only shows what was stored:
 * displaying it never starts an analysis.
 */
export function DecisionSection({ tenderId }: { tenderId: number }) {
  const loaded = useLoader(() => getTenderAnalysis(tenderId), String(tenderId));
  const analysis = loaded.data?.latest ?? null;
  const running =
    analysis?.status === "PENDING" || analysis?.status === "PROCESSING" || analysis?.status === "RETRY_WAITING";
  const { reload } = loaded;

  // An analysis in progress: refresh until it is over.
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(reload, 5000);
    return () => window.clearInterval(timer);
  }, [running, reload]);

  let content;
  if (loaded.error && !loaded.data) {
    content = <p className="muted">Aide à la décision indisponible pour le moment. Le reste de la fiche n&apos;est pas concerné.</p>;
  } else if (!loaded.data) {
    content = <p className="muted">Chargement…</p>;
  } else if (!analysis) {
    content = loaded.data.configured ? (
      <p className="muted">
        Aucune analyse du DCE pour l&apos;instant : aucune recommandation n&apos;est proposée. Déposez le DCE dans la
        section {ANALYSIS_LINK} ; l&apos;aide à la décision est produite avec la fiche de synthèse.
      </p>
    ) : (
      <p className="muted">
        Aide à la décision indisponible : l&apos;analyse par IA n&apos;est pas active
        {loaded.data.unavailableReason ? ` (${loaded.data.unavailableReason.replace(/\.$/, "")})` : ""}.
      </p>
    );
  } else if (running) {
    content = <p className="muted">Analyse du DCE en cours : la recommandation s&apos;affichera ici à la fin.</p>;
  } else if (analysis.status !== "DONE") {
    content = (
      <p className="muted">
        La dernière analyse n&apos;a pas abouti : aucune recommandation n&apos;est proposée. Détails dans la section{" "}
        {ANALYSIS_LINK}.
      </p>
    );
  } else if (!analysis.decision) {
    content = (
      <p className="muted">
        Cette analyse a été faite avant l&apos;aide à la décision. Pour obtenir une recommandation, relancez
        l&apos;analyse dans la section {ANALYSIS_LINK} (nouvel appel à l&apos;IA, payant).
      </p>
    );
  } else {
    content = <DecisionSheet analysis={analysis} />;
  }

  return (
    <section className="card decision-card" aria-labelledby="decision-title">
      <h2 id="decision-title">Analyse IA — Aide à la décision</h2>
      {content}
    </section>
  );
}
