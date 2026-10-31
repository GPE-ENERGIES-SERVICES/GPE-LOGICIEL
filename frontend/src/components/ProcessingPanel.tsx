"use client";

import { useEffect, useState, type ReactNode } from "react";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { cancelAnalysis, getFinancialAnalysis, getTenderAnalysis, sendAnalysisNow } from "@/services/analysisApi";
import { allowAutoAnalysis, getDossiers } from "@/services/documentApi";
import type { Analysis } from "@/types/analysis";
import type { Dossier } from "@/types/document";

/** What an analysis takes before any has been measured on this server. */
const DEFAULT_SUMMARY_SECONDS = 180;

const DEFAULT_FINANCIAL_SECONDS = 90;

/** Wait before an analysis started by itself is sent (analysis.auto-start-delay). */
const AUTO_START_SECONDS = 120;

type StepState = "todo" | "running" | "done" | "error" | "off";

type Step = {
  title: string;
  state: StepState;
  /** Share done, null when it cannot be known while running. */
  value?: number | null;
  detail: ReactNode;
  /** Seconds left, when they can be estimated. */
  remaining?: number | null;
  action?: ReactNode;
};

const STATE_LABELS: Record<StepState, string> = {
  todo: "À venir",
  running: "En cours",
  done: "Terminé",
  error: "À reprendre",
  off: "Non concerné",
};

function duration(seconds: number): string {
  if (seconds < 60) {
    return `${Math.max(1, Math.round(seconds))} s`;
  }
  return `${Math.round(seconds / 60)} min`;
}

const isRunning = (analysis: Analysis | null | undefined) =>
  analysis?.status === "PENDING" || analysis?.status === "PROCESSING" || analysis?.status === "RETRY_WAITING";

/** The reading of the latest dossier. */
function readingStep(dossier: Dossier | null, now: number): Step {
  if (!dossier) {
    return { title: "Lecture des documents", state: "todo", detail: "Après le dépôt du DCE." };
  }
  const total = dossier.documents.length;
  const read = dossier.documents.filter((document) => document.state !== "PENDING").length;
  if (dossier.status === "PENDING" || dossier.status === "PROCESSING") {
    const elapsed = (now - new Date(dossier.uploadedAt).getTime()) / 1000;
    return {
      title: "Lecture des documents",
      state: "running",
      value: total === 0 ? null : read / total,
      detail: `${read} / ${total} fichiers lus · les pages scannées des pièces utiles sont lues par OCR`,
      remaining: read > 0 && total > read ? (elapsed / read) * (total - read) : null,
    };
  }
  if (dossier.status === "FAILED") {
    return { title: "Lecture des documents", state: "error", detail: dossier.errorMessage ?? "La lecture a échoué." };
  }
  const unread = dossier.documents.filter(
    (document) => document.state === "FAILED" || document.state === "NOT_ANALYZED",
  ).length;
  return {
    title: "Lecture des documents",
    state: "done",
    detail: `${total - unread} fichiers lus${unread ? `, ${unread} non lus (plans, images, formats non pris en charge)` : ""}`,
  };
}

/**
 * Where the analysis of the prices or of the summary stands, with what can
 * be done about it.
 */
function analysisStep(
  title: string,
  analysis: Analysis | null,
  typical: number,
  now: number,
  cancel: (analysis: Analysis) => void,
  sendNow: (analysis: Analysis) => void,
  idle: Step,
): Step {
  if (analysis?.status === "PENDING" || analysis?.status === "RETRY_WAITING") {
    const due = analysis.nextAttemptAt ? (new Date(analysis.nextAttemptAt).getTime() - now) / 1000 : 0;
    return {
      title,
      state: "running",
      value: null,
      detail:
        due > 0
          ? analysis.status === "RETRY_WAITING"
            ? `Nouvelle tentative dans ${duration(due)}`
            : `Envoi automatique à l'IA dans ${duration(due)} : vous pouvez encore l'annuler`
          : "En attente d'envoi à l'IA",
      remaining: Math.max(due, 0) + typical,
      action: (
        <>
          {due > 0 && (
            <button type="button" className="primary" onClick={() => sendNow(analysis)}>
              Lancer maintenant
            </button>
          )}{" "}
          <button type="button" className="secondary" onClick={() => cancel(analysis)}>
            Annuler l&apos;analyse
          </button>
        </>
      ),
    };
  }
  if (analysis?.status === "PROCESSING") {
    const elapsed = (now - new Date(analysis.startedAt ?? analysis.createdAt).getTime()) / 1000;
    const left = typical - elapsed;
    return {
      title,
      state: "running",
      value: Math.min(elapsed / typical, 0.95),
      detail: `Envoyée à l'IA il y a ${duration(elapsed)} · ${left > 0 ? `environ ${duration(left)} restantes` : "presque terminée"} · ne peut plus être annulée`,
      remaining: Math.max(left, 0),
    };
  }
  if (analysis?.status === "DONE") {
    return {
      title,
      state: "done",
      detail: `Terminée le ${formatDateTime(analysis.finishedAt ?? analysis.createdAt)}${
        analysis.cost !== null ? ` · coût ${analysis.cost.toFixed(2).replace(".", ",")} €` : ""
      }`,
    };
  }
  if (analysis?.status === "FAILED") {
    return { title, state: "error", detail: analysis.errorMessage ?? "L'analyse a échoué." };
  }
  return idle;
}

/**
 * Where the processing of the consultation file stands, step by step: the
 * file uploaded, its reading, the analysis of the summary and the analysis
 * of the prices, each with its progress and the time left when it can be
 * estimated. Folds into one line once everything is over.
 */
export function ProcessingPanel({ tenderId, retained }: { tenderId: number; retained: boolean | null }) {
  const dossiers = useLoader(() => getDossiers(tenderId), String(tenderId));
  const summary = useLoader(() => getTenderAnalysis(tenderId), String(tenderId));
  const financial = useLoader(() => getFinancialAnalysis(tenderId), String(tenderId));
  const [now, setNow] = useState(() => Date.now());
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dossier = dossiers.data?.[0] ?? null;
  const reading = dossier?.status === "PENDING" || dossier?.status === "PROCESSING";
  const working = reading || isRunning(summary.data?.latest) || isRunning(financial.data?.latest);
  const { reload: reloadDossiers } = dossiers;
  const { reload: reloadSummary } = summary;
  const { reload: reloadFinancial } = financial;

  // While something runs: the time every second, the data every three.
  useEffect(() => {
    if (!working) {
      return;
    }
    let tick = 0;
    const timer = window.setInterval(() => {
      setNow(Date.now());
      tick++;
      if (tick % 3 === 0) {
        reloadDossiers();
        reloadSummary();
        reloadFinancial();
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [working, reloadDossiers, reloadSummary, reloadFinancial]);

  const run = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      reloadDossiers();
      reloadSummary();
      reloadFinancial();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    }
  };
  const cancel = (analysis: Analysis) => run(() => cancelAnalysis(analysis.id));
  const sendNow = (analysis: Analysis) => run(() => sendAnalysisNow(analysis.id));

  if (!dossiers.data || !summary.data) {
    return null;
  }

  const typicalSummary = summary.data.typicalSeconds ?? DEFAULT_SUMMARY_SECONDS;
  const typicalFinancial = financial.data?.typicalSeconds ?? DEFAULT_FINANCIAL_SECONDS;
  const latest = summary.data.latest;
  const ofThisDossier = latest && dossier && latest.dossierId === dossier.id ? latest : null;

  const upload: Step = dossier
    ? {
        title: "Dépôt du DCE",
        state: "done",
        detail: `${dossier.documents.length} fichiers, version ${dossier.version} du ${formatDateTime(dossier.uploadedAt)}`,
      }
    : {
        title: "Dépôt du DCE",
        state: "todo",
        detail: "Aucun DCE déposé : téléchargez-le sur la plateforme de l'acheteur puis déposez-le.",
        action: (
          <a href="#dce" className="button primary">
            Déposer le DCE
          </a>
        ),
      };

  const automatic = retained === true && dossier?.autoAnalysis !== false && summary.data.configured;
  const summaryIdle: Step = !summary.data.configured
    ? { title: "Fiche de synthèse IA", state: "off", detail: "Analyse par IA désactivée sur ce serveur." }
    : reading && automatic
      ? {
          title: "Fiche de synthèse IA",
          state: "todo",
          detail: `Partira toute seule ${duration(AUTO_START_SECONDS)} après la lecture (vous pourrez encore l'annuler), puis environ ${duration(typicalSummary)}.`,
          remaining: AUTO_START_SECONDS + typicalSummary,
          action: (
            <button type="button" className="secondary" onClick={() => run(() => allowAutoAnalysis(dossier!.id, false))}>
              Ne pas lancer automatiquement
            </button>
          ),
        }
      : reading && retained === true && dossier?.autoAnalysis === false
        ? {
            title: "Fiche de synthèse IA",
            state: "off",
            detail: "Lancement automatique arrêté : vous la lancerez depuis l'onglet « Fiche IA ».",
            action: (
              <button type="button" className="link-button" onClick={() => run(() => allowAutoAnalysis(dossier!.id, true))}>
                Relancer automatiquement
              </button>
            ),
          }
        : {
            title: "Fiche de synthèse IA",
            state: "todo",
            detail: dossier
              ? latest
                ? "Faite sur une version précédente du dossier : à relancer si besoin."
                : `À lancer depuis l'onglet « Fiche IA » (environ ${duration(typicalSummary)}).`
              : "Après la lecture du DCE.",
            action:
              dossier && !reading ? (
                <a href="#fiche" className="button">
                  Ouvrir la fiche IA
                </a>
              ) : undefined,
          };
  const summaryStep = analysisStep(
    "Fiche de synthèse IA",
    ofThisDossier ?? (isRunning(latest) ? latest : null),
    typicalSummary,
    now,
    cancel,
    sendNow,
    summaryIdle,
  );

  const pieces = financial.data?.pieces ?? [];
  const financialStep = analysisStep(
    "Analyse financière",
    financial.data?.latest ?? null,
    typicalFinancial,
    now,
    cancel,
    sendNow,
    pieces.length === 0
      ? {
          title: "Analyse financière",
          state: "off",
          detail: dossier ? "Aucune pièce de prix (DPGF, BPU, DQE) dans le dossier." : "Après la lecture du DCE.",
        }
      : {
          title: "Analyse financière",
          state: "todo",
          detail: `À lancer à la demande (environ ${duration(typicalFinancial)}).`,
          action: !reading ? (
            <a href="#finance" className="button">
              Ouvrir l&apos;analyse financière
            </a>
          ) : undefined,
        },
  );

  const steps = [upload, readingStep(dossier, now), summaryStep, financialStep];
  const left = steps
    .filter((step) => step.state === "running" || (step.state === "todo" && step.remaining))
    .reduce((sum, step) => sum + (step.remaining ?? 0), 0);
  const current = steps.find((step) => step.state === "running");
  const expanded = working || open;

  return (
    <section className={working ? "card processing working" : "card processing"} aria-live="polite">
      <div className="processing-head">
        <h2>Traitement du DCE</h2>
        <span className="processing-summary">
          {working
            ? `${current?.title ?? "En cours"}${left > 0 ? ` · environ ${duration(left)} restantes` : ""}`
            : steps.some((step) => step.state === "error")
              ? "Une étape est à reprendre"
              : dossier
                ? "Aucun traitement en cours"
                : "DCE à déposer"}
        </span>
        {!working && (
          <button type="button" className="link-button" aria-expanded={open} onClick={() => setOpen(!open)}>
            {open ? "Masquer les étapes" : "Voir les étapes"}
          </button>
        )}
      </div>
      {expanded && (
        <ol className="steps">
          {steps.map((step, index) => (
            <li key={step.title} className={`step step-${step.state}`}>
              <span className="step-marker" aria-hidden="true">
                {step.state === "done" ? "✓" : step.state === "error" ? "!" : index + 1}
              </span>
              <div className="step-body">
                <div className="step-title">
                  <strong>{step.title}</strong>
                  <span className="step-state">{STATE_LABELS[step.state]}</span>
                  {step.state === "running" && step.remaining ? (
                    <span className="step-time">≈ {duration(step.remaining)}</span>
                  ) : null}
                </div>
                {step.state === "running" && (
                  <div
                    className={step.value === null || step.value === undefined ? "progress-track indeterminate" : "progress-track"}
                    role="progressbar"
                    aria-label={step.title}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={step.value == null ? undefined : Math.round(step.value * 100)}
                  >
                    <div
                      className="progress-fill"
                      style={step.value == null ? undefined : { width: `${Math.round(step.value * 100)}%` }}
                    />
                  </div>
                )}
                <p className="step-detail">{step.detail}</p>
                {step.action && <div className="step-action">{step.action}</div>}
              </div>
            </li>
          ))}
        </ol>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  );
}
