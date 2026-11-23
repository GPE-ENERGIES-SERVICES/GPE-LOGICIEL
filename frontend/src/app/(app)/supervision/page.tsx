"use client";

import Link from "next/link";
import { useState } from "react";

import { useAuth } from "@/components/AuthProvider";
import { CollectStatus } from "@/components/CollectStatus";
import { Fold } from "@/components/Fold";
import { Icon } from "@/components/Icon";
import { CollectButton } from "@/components/settings/CollectButton";
import { DuplicatesPanel } from "@/components/settings/DuplicatesPanel";
import { AiBudgetCard, AiHealth, AiUsagePanel } from "@/components/supervision/AiUsage";
import { formatCost } from "@/lib/aiServices";
import { useLoader } from "@/lib/useLoader";
import { getAiUsage } from "@/services/aiUsageApi";
import { getDuplicateGroups } from "@/services/duplicateApi";
import { getTenderCounts } from "@/services/tenderApi";

/** Consultations published since yesterday. */
const RECENT_QUERY = "publishedWithinDays=1";

/**
 * What an administrator checks: the collect and the processing of the AI are
 * running (a collection started by hand), what the AI costs (by service, by
 * tender, call by call) and the duplicates to decide.
 */
export default function SupervisionPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  // shortcut: the count reads again what the panel reads; one more small request.
  const duplicates = useLoader(() => (admin ? getDuplicateGroups() : Promise.resolve([])), "supervision-duplicates");
  const recent = useLoader(() => (admin ? getTenderCounts(RECENT_QUERY) : Promise.resolve(null)), "supervision-recent");
  const usage = useLoader(() => (admin ? getAiUsage() : Promise.resolve(null)), "supervision-ai-usage");
  const [view, setView] = useState<"services" | "tenders" | "history">("services");
  const [historyStatus, setHistoryStatus] = useState("");

  if (!admin) {
    return <p className="banner error">Cette page est réservée aux administrateurs.</p>;
  }

  const duplicateCount = duplicates.data?.length ?? 0;
  const newCount = recent.data ? recent.data.ALL + recent.data.FILTERED_OUT : undefined;
  const finished = usage.data?.perTender.finished;
  const ongoing = usage.data?.perTender.ongoing;
  const error = [recent, usage, duplicates].find((loader) => loader.error)?.error;

  const showErrors = () => {
    setHistoryStatus("FAILED");
    setView("history");
    document.getElementById("consommation-ia")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <div className="settings">
      <h1>Supervision</h1>
      <div className="supervision-status">
        <CollectStatus />
        <CollectButton />
        <AiHealth overview={usage.data} onShowErrors={showErrors} />
      </div>
      {error && <p className="banner error">{error}</p>}
      <div className="stats">
        <AiBudgetCard overview={usage.data} />
        <div className="card stat tone-violet">
          <span className="stat-head">
            <Icon name="file" size={18} />
            <strong>{finished ? formatCost(finished.average) : "–"}</strong>
          </span>
          <span>coût IA moyen complet par AO terminé</span>
          {finished && ongoing && (
            <span className="small muted">
              {finished.tenders} AO terminé{finished.tenders > 1 ? "s" : ""} · {ongoing.tenders} en cours (
              {formatCost(ongoing.average)}, provisoire)
            </span>
          )}
        </div>
        <Link href={`/tenders?tab=ALL&${RECENT_QUERY}`} className={`card stat tone-${newCount ? "info" : "warning"}`}>
          <span className="stat-head">
            <Icon name="tenders" size={18} />
            <strong>{newCount ?? "–"}</strong>
          </span>
          <span>nouvelles consultations depuis hier</span>
        </Link>
        <a href="#doublons" className={`card stat tone-${duplicateCount ? "warning" : "success"}`}>
          <span className="stat-head">
            <Icon name="warning" size={18} />
            <strong>{duplicates.data ? duplicateCount : "–"}</strong>
          </span>
          <span>groupes de doublons à vérifier</span>
        </a>
      </div>
      {usage.data && (
        <AiUsagePanel overview={usage.data} view={view} historyStatus={historyStatus} onView={setView} />
      )}
      <div id="doublons">
        <Fold
          className="card settings-fold"
          title="Doublons à vérifier"
          count={duplicates.data ? (duplicateCount === 0 ? "aucun" : duplicateCount) : undefined}
          open={duplicateCount > 0}
        >
          <DuplicatesPanel />
        </Fold>
      </div>
    </div>
  );
}
