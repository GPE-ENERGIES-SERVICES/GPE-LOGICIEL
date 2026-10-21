"use client";

import { useEffect } from "react";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getCollectSources } from "@/services/settingsApi";
import type { CollectSource } from "@/types/settings";

/** The state of the sources is read again every 5 minutes. */
const REFRESH_MS = 5 * 60 * 1000;

function message(source: CollectSource): string {
  const since = source.lastSuccess
    ? `aucune collecte réussie depuis le ${formatDateTime(source.lastSuccess.startedAt)} (${source.workingHoursSinceSuccess} heures ouvrées)`
    : "aucune collecte réussie à ce jour";
  const next = source.catchingUp
    ? "rattrapage en cours"
    : source.nextRun
      ? `rattrapage automatique au démarrage et chaque heure ; prochaine collecte prévue le ${formatDateTime(source.nextRun)}`
      : "rattrapage automatique au démarrage et chaque heure";
  const error = source.lastFailure?.errorMessage ? ` Dernière erreur : ${source.lastFailure.errorMessage}.` : "";
  return `Collecte ${source.source} en retard : ${since}, ${next}.${error}`;
}

/**
 * Shown on every page while an active source has had no successful
 * collection for more than 24 working hours (Monday to Friday), or never: the
 * list may be missing recent notices.
 */
export function CollectDelayAlert() {
  const { data, reload } = useLoader(getCollectSources, "collect-delay");

  useEffect(() => {
    const timer = window.setInterval(reload, REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [reload]);

  const late = (data ?? []).filter((source) => source.active && source.overdue);
  if (late.length === 0) {
    return null;
  }
  return (
    <div className="banner warning" role="alert">
      {late.map((source) => (
        <p key={source.source}>{message(source)}</p>
      ))}
    </div>
  );
}
