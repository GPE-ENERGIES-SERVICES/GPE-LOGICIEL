"use client";

import { useAuth } from "@/components/AuthProvider";
import { PricingPanel } from "@/components/settings/PricingPanel";

/**
 * The defaults of the estimates of GPE: the costs of labour by profile, the
 * VAT, the coefficients and the fees. One source: a new estimate takes them
 * and keeps the values it took.
 */
export default function EstimateSettingsPage() {
  const { user } = useAuth();

  return (
    <div>
      <h1>Paramètres du chiffrage</h1>
      <section className="card">
        <h2>Coûts horaires, TVA et coefficients</h2>
        <p className="muted">
          Utilisés par les chiffrages : un nouveau chiffrage reprend la TVA et
          les coefficients, et la main-d&apos;œuvre d&apos;une ligne prend son
          coût horaire ici (l&apos;IA aussi, quand elle chiffre). Un chiffrage
          garde les valeurs qu&apos;il a reprises.
        </p>
        {user.role === "ADMIN" ? (
          <PricingPanel />
        ) : (
          <p className="muted">Modifiables par un administrateur.</p>
        )}
      </section>
    </div>
  );
}
