"use client";

import { useEffect, useState } from "react";

import { Fold } from "@/components/Fold";
import { formatDateTime } from "@/lib/format";
import { refreshData, useLoader } from "@/lib/useLoader";
import { getTenderAnalysis } from "@/services/analysisApi";
import { updateFollowUp } from "@/services/tenderApi";
import type {
  Analysis,
  DecisionPoint,
  ObstacleKind,
  Verdict,
} from "@/types/analysis";
import type { FollowUpStatus } from "@/types/tender";
import { PartTitle, type Tone } from "@/components/analysis/PartTitle";
import { Block } from "@/components/analysis/SpecialAnalyses";
import { Icon } from "@/components/Icon";

const VERDICT_LABELS: Record<Verdict, string> = {
  GO: "GO : ça vaut le coup",
  GO_CONDITIONS: "GO sous conditions",
  NO_GO: "NO-GO : à laisser passer",
  INSUFFICIENT: "Informations insuffisantes",
  PRIORITY: "À étudier en priorité",
  EXAMINE: "À examiner",
  POOR_FIT: "Peu adapté a priori",
};

/** The colour of a verdict; the former verdicts keep theirs. */
const BLOCK_TONES: Record<Verdict, Tone> = {
  GO: "success",
  GO_CONDITIONS: "warning",
  NO_GO: "danger",
  INSUFFICIENT: "muted",
  PRIORITY: "success",
  EXAMINE: "warning",
  POOR_FIT: "danger",
};

const SHORT_VERDICTS: Record<Verdict, string> = {
  GO: "GO",
  GO_CONDITIONS: "GO sous conditions",
  NO_GO: "NO-GO",
  INSUFFICIENT: "Go / No-Go : infos insuffisantes",
  PRIORITY: "À étudier en priorité",
  EXAMINE: "À examiner",
  POOR_FIT: "Peu adapté",
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
  return kind === "TO_CHECK"
    ? `${count} à vérifier`
    : `${count} information${s} manquante${s}`;
}

/** Where to go to start, follow or run again the analysis. */
const ANALYSIS_LINK = <a href="#fiche">Fiche IA</a>;

/** The source of an argument or an obstacle: shown on request when its quote was found, flagged otherwise. */
function PointSource({
  point,
  analysis,
}: {
  point: DecisionPoint;
  analysis: Analysis;
}) {
  const [shown, setShown] = useState(false);
  const document = analysis.documents.find(
    (item) => item.id === point.documentId,
  );
  if (!point.verified) {
    return point.documentId !== null ? (
      <span
        className="badge urgent"
        title="L'extrait cité par l'IA n'a pas été retrouvé dans le document."
      >
        Source non retrouvée
      </span>
    ) : null;
  }
  return (
    <>
      <button
        type="button"
        className="field-toggle quiet"
        aria-expanded={shown}
        aria-label={`Source vérifiée : ${shown ? "masquer" : "afficher"} la source et l'extrait cité`}
        title={
          shown ? "Masquer la source" : "Afficher la source et l'extrait cité"
        }
        onClick={() => setShown(!shown)}
      >
        ✓
        <span className="badge-chevron">
          <Icon name={shown ? "chevronUp" : "chevronDown"} size={12} />
        </span>
      </button>
      {shown && (
        <span className="decision-source">
          {document && (
            <span className="source">
              {document.readable ? (
                <a
                  href={`/api/documents/${document.id}/pdf#page=${point.page}`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
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
              <span
                className={`decision-kind kind-${point.kind.toLowerCase().replace("_", "-")}`}
              >
                {OBSTACLE_LABELS[point.kind]}
              </span>
            )}
            {point.text}
            {point.deduced && (
              <span
                className="decision-deduced"
                title="Déduit de plusieurs éléments, non écrit tel quel dans le DCE"
              >
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
  const documentName = (id: number | null) =>
    analysis.documents.find((document) => document.id === id)?.fileName;
  const analysed = analysis.documents.filter(
    (document) => document.analysed,
  ).length;
  const notAnalysed = analysis.documents.length - analysed;
  const limits = decision.limits.slice(0, MAX_LIMITS);

  const kinds = (["CONFIRMED", "TO_CHECK", "MISSING"] as const)
    .map(
      (kind) =>
        [
          kind,
          decision.obstacles.filter((point) => point.kind === kind).length,
        ] as const,
    )
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => obstacleCount(kind, count))
    .join(" · ");

  return (
    <div className="decision">
      {decision.conclusion && <p className="decision-conclusion">{decision.conclusion}</p>}
      <ul className="decision-figures">
        <li className="tone-success">
          {decision.strengths.length} raison{decision.strengths.length > 1 ? "s" : ""} d&apos;y aller
        </li>
        <li className="tone-danger">
          {decision.obstacles.length} obstacle{decision.obstacles.length > 1 ? "s" : ""}
        </li>
        <li className="tone-warning">
          {decision.checks.length} point{decision.checks.length > 1 ? "s" : ""} à lever
        </li>
        <li className="tone-muted">
          {analysed} pièce{analysed > 1 ? "s" : ""} analysée{analysed > 1 ? "s" : ""} sur {analysis.documents.length}
          {notAnalysed > 0 && <> · {ANALYSIS_LINK}</>}
        </li>
      </ul>

      <div className="read-grid decision-columns">
        <div className="read-part tone-success">
          <h4>
            <PartTitle icon="valid" tone="success">
              Pourquoi y aller
            </PartTitle>
          </h4>
          <Points points={decision.strengths} analysis={analysis} empty="Aucun argument favorable identifié." />
        </div>
        <div className="read-part tone-danger">
          <h4>
            <PartTitle icon="warning" tone="danger">
              Obstacles et risques
            </PartTitle>
          </h4>
          {kinds && <p className="decision-kinds">{kinds}</p>}
          <Points points={decision.obstacles} analysis={analysis} empty="Aucun obstacle identifié." />
        </div>
      </div>

      {decision.checks.length > 0 && (
        <div className="read-part tone-warning decision-to-lift">
          <h4>
            <PartTitle icon="clipboard" tone="warning">
              À lever avant de s&apos;engager
            </PartTitle>
          </h4>
          <ol className="decision-checks">
            {decision.checks.map((check, index) => (
              <li key={index}>
                {check.action}
                {(check.reason || documentName(check.documentId)) && (
                  <span className="decision-check-why">
                    {check.reason}
                    {check.reason && documentName(check.documentId) && " · "}
                    {documentName(check.documentId)}
                  </span>
                )}
              </li>
            ))}
          </ol>
        </div>
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
          Avis sur l&apos;intérêt du projet (métier, taille, risques,
          chances de gagner, délais), jamais sur les formalités. Seuls les
          points marqués ✓ sont appuyés par un extrait retrouvé dans le
          document.
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
/** Where the tender stands for GPE, as its follow-up says. */
type FollowUp = {
  status: FollowUpStatus;
  by: string | null;
  at: string | null;
};

/** The decision of GPE, recorded in the follow-up of the tender: the AI only advises. */
function Choice({
  tenderId,
  followUp,
}: {
  tenderId: number;
  followUp: FollowUp;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const decide = async (status: FollowUpStatus) => {
    const comment =
      status === "DISMISSED"
        ? window.prompt("Pourquoi on passe ? (facultatif)")
        : null;
    if (status === "DISMISSED" && comment === null) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await updateFollowUp(tenderId, status, comment ?? undefined);
      refreshData(String(tenderId));
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Décision non enregistrée.",
      );
    } finally {
      setBusy(false);
    }
  };
  // An answer prepared, sent or with its result: the decision was GO.
  const answered = ["RESPONDING", "SUBMITTED", "AWARDED", "SIGNED", "REJECTED"].includes(followUp.status);
  const decided = answered
      ? "GO"
      : followUp.status === "DISMISSED"
        ? "NO-GO"
        : null;
  return (
    <div className="form-actions">
      {decided ? (
        <span className={decided === "GO" ? "badge verified decision-badge" : "badge status-dismissed decision-badge"}>
          Décision : {decided}
          {followUp.by
            ? ` (${followUp.by}${followUp.at ? `, ${formatDateTime(followUp.at)}` : ""})`
            : ""}
        </span>
      ) : (
        <span className="hint">Votre décision :</span>
      )}
      {(!answered || followUp.status === "RESPONDING") && (
        <>
          <button
            type="button"
            className={decided === "GO" ? "" : "primary"}
            disabled={busy || decided === "GO"}
            onClick={() => decide("RESPONDING")}
          >
            On y va (GO)
          </button>
          <button
            type="button"
            disabled={busy || decided === "NO-GO"}
            onClick={() => decide("DISMISSED")}
          >
            On passe (NO-GO)
          </button>
        </>
      )}
      {error && <span className="banner error">{error}</span>}
    </div>
  );
}

export function DecisionSection({
  tenderId,
  followUp,
}: {
  tenderId: number;
  followUp?: FollowUp;
}) {
  const loaded = useLoader(() => getTenderAnalysis(tenderId), String(tenderId));
  const analysis = loaded.data?.latest ?? null;
  const running =
    analysis?.status === "PENDING" ||
    analysis?.status === "PROCESSING" ||
    analysis?.status === "RETRY_WAITING";
  const { reload } = loaded;

  // An analysis in progress: refresh until it is over.
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(reload, 5000);
    return () => window.clearInterval(timer);
  }, [running, reload]);

  if (running || !analysis || analysis.status !== "DONE") {
    // The summary sheet below says where the analysis stands.
    return null;
  }
  if (!analysis.decision) {
    return (
      <p className="hint">
        Cette analyse a été faite avant l&apos;aide à la décision : relancez-la pour obtenir un Go / No-Go
        (nouvel appel à l&apos;IA, payant).
      </p>
    );
  }
  const verdict = analysis.decision.verdict;
  return (
    <Block icon="award" tone={BLOCK_TONES[verdict]} title="Go / No-Go" count={VERDICT_LABELS[verdict]}>
      <DecisionSheet analysis={analysis} />
      {followUp && <Choice tenderId={tenderId} followUp={followUp} />}
    </Block>
  );
}

/** The verdict of the AI on the head of a tender, leading to the summary sheet where it is detailed. */
export function VerdictBadge({ tenderId }: { tenderId: number }) {
  const { data } = useLoader(() => getTenderAnalysis(tenderId), String(tenderId));
  const decision = data?.latest?.status === "DONE" ? data.latest.decision : null;
  return (
    <a href="#fiche" className="badge-link" title="Voir le Go / No-Go dans la fiche IA">
      <span className={`badge verdict-badge tone-${decision ? BLOCK_TONES[decision.verdict] : "muted"}`}>
        {decision ? SHORT_VERDICTS[decision.verdict] : "Go / No-Go : pas encore analysé"}
      </span>
    </a>
  );
}
