"use client";

import { useAuth } from "@/components/AuthProvider";
import { Fold } from "@/components/Fold";
import { AnalysisJournal } from "@/components/settings/AnalysisJournal";
import { CollectJournal } from "@/components/settings/CollectJournal";
import { DcePanel } from "@/components/settings/DcePanel";
import { DuplicatesPanel } from "@/components/settings/DuplicatesPanel";
import { useLoader } from "@/lib/useLoader";
import { getDuplicateGroups } from "@/services/duplicateApi";

/**
 * What an administrator checks: the duplicates to decide first, the only
 * work to do here, then the journals. Each part is folded; the duplicates open
 * by themselves when some wait.
 */
export default function SupervisionPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  // shortcut: the count reads again what the panel reads; one more small request.
  const duplicates = useLoader(() => (admin ? getDuplicateGroups() : Promise.resolve([])), "supervision-duplicates");

  if (!admin) {
    return <p className="banner error">Cette page est réservée aux administrateurs.</p>;
  }

  const duplicateCount = duplicates.data?.length ?? 0;

  return (
    <div className="settings">
      <h1>Supervision</h1>
      <Fold
        className="card settings-fold"
        title="Doublons à vérifier"
        count={duplicates.data ? (duplicateCount === 0 ? "aucun" : duplicateCount) : undefined}
        open={duplicateCount > 0}
      >
        <DuplicatesPanel />
      </Fold>
      <Fold className="card settings-fold" title="Journal de collecte">
        <CollectJournal />
      </Fold>
      <Fold className="card settings-fold" title="Récupération des DCE">
        <DcePanel />
      </Fold>
      <Fold className="card settings-fold" title="Analyses par IA : journal et qualité">
        <AnalysisJournal />
      </Fold>
    </div>
  );
}
