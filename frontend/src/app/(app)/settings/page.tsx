"use client";

import { useAuth } from "@/components/AuthProvider";
import { CollectJournal } from "@/components/settings/CollectJournal";
import { CriteriaForm } from "@/components/settings/CriteriaForm";
import { UsersPanel } from "@/components/settings/UsersPanel";

export default function SettingsPage() {
  const { user } = useAuth();

  if (user.role !== "ADMIN") {
    return <p className="banner error">Cette page est réservée aux administrateurs.</p>;
  }

  return (
    <div className="settings">
      <h1>Paramètres</h1>
      <section className="card">
        <h2>Critères de filtrage</h2>
        <CriteriaForm />
      </section>
      <section className="card">
        <h2>Journal de collecte</h2>
        <CollectJournal />
      </section>
      <section className="card">
        <h2>Comptes utilisateurs</h2>
        <UsersPanel />
      </section>
    </div>
  );
}
