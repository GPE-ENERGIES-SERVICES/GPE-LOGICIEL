"use client";

import { useAuth } from "@/components/AuthProvider";
import { Fold } from "@/components/Fold";
import { AnalysisConfigurationPanel } from "@/components/settings/AnalysisJournal";
import { CriteriaForm } from "@/components/settings/CriteriaForm";
import { UsersPanel } from "@/components/settings/UsersPanel";
import { useLoader } from "@/lib/useLoader";
import { getAnalysisConfiguration } from "@/services/analysisApi";
import { getUsers } from "@/services/settingsApi";

/**
 * What an administrator sets, each part folded under its title with a
 * summary. What is checked (duplicates, journals) is in « Supervision »; the
 * costs and coefficients of the estimates are in Entreprise › Économie.
 */
export default function SettingsPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  // shortcut: the summaries read again what the panels read; one more small request each.
  const users = useLoader(() => (admin ? getUsers() : Promise.resolve([])), "settings-users");
  const analysis = useLoader(() => (admin ? getAnalysisConfiguration() : Promise.resolve(null)), "settings-analysis");

  if (!admin) {
    return <p className="banner error">Cette page est réservée aux administrateurs.</p>;
  }

  return (
    <div className="settings">
      <h1>Paramètres</h1>
      <Fold
        className="card settings-fold"
        title="Comptes utilisateurs"
        count={users.data ? `${users.data.length} compte${users.data.length > 1 ? "s" : ""}` : undefined}
      >
        <UsersPanel />
      </Fold>
      <Fold className="card settings-fold" title="Critères de filtrage">
        <CriteriaForm />
      </Fold>
      <Fold
        className="card settings-fold"
        title="Analyses par IA : configuration"
        count={analysis.data ? (analysis.data.enabled ? "activée" : "désactivée") : undefined}
      >
        <AnalysisConfigurationPanel />
      </Fold>
    </div>
  );
}
