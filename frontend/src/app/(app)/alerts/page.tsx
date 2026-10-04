"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

import { AlertForm } from "@/components/AlertForm";
import { ACTIVITY_LABELS, formatAmount, formatDateTime, MARKET_TYPE_LABELS } from "@/lib/format";
import { alertResultsHref } from "@/lib/navigation";
import { NotConnected } from "@/components/NotConnected";
import { useLoader } from "@/lib/useLoader";
import { createAlert, deleteAlert, getAlerts, markAlertViewed, updateAlert } from "@/services/alertApi";
import { available } from "@/services/http";
import type { AlertInput, AlertSummary } from "@/types/alert";
import type { MarketType } from "@/types/tender";

const EMPTY: AlertInput = {
  name: "",
  keywords: [],
  region: null,
  departments: [],
  marketTypes: [],
  activities: [],
  cpvPrefixes: [],
  minAmount: null,
  maxAmount: null,
  active: true,
};

/** A saved search started from a search of the tender list (?new=1&q=…&department=…). */
function fromSearch(params: URLSearchParams): AlertInput {
  const list = (key: string) =>
    (params.get(key) ?? "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  const amount = (key: string) => (params.get(key) ? Number(params.get(key)) : null);
  const text = params.get("q")?.trim();
  return {
    ...EMPTY,
    keywords: text ? [text] : [],
    region: params.get("region"),
    departments: list("department"),
    marketTypes: list("marketType") as MarketType[],
    activities: list("activity"),
    minAmount: amount("minAmount"),
    maxAmount: amount("maxAmount"),
  };
}

function inputOf(alert: AlertSummary): AlertInput {
  return {
    name: alert.name,
    keywords: alert.keywords,
    region: alert.region,
    departments: alert.departments,
    marketTypes: alert.marketTypes,
    activities: alert.activities,
    cpvPrefixes: alert.cpvPrefixes,
    minAmount: alert.minAmount,
    maxAmount: alert.maxAmount,
    active: alert.active,
  };
}

function amountRange(min: number | null, max: number | null): string | null {
  if (min !== null && max !== null) {
    return `de ${formatAmount(min)} à ${formatAmount(max)}`;
  }
  if (min !== null) {
    return `à partir de ${formatAmount(min)}`;
  }
  return max !== null ? `jusqu'à ${formatAmount(max)}` : null;
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count > 1 ? many : one}`;
}

/** The criteria actually set. */
function Criteria({ alert }: { alert: AlertSummary }) {
  const rows: [string, string | null][] = [
    ["Mots-clés", alert.keywords.join(", ")],
    ["Région", alert.region],
    ["Départements", alert.departments.join(", ")],
    ["Type de marché", alert.marketTypes.map((type) => MARKET_TYPE_LABELS[type]).join(", ")],
    ["Types de travaux", alert.activities.map((code) => ACTIVITY_LABELS[code] ?? code).join(", ")],
    ["Codes CPV", alert.cpvPrefixes.join(", ")],
    ["Montant", amountRange(alert.minAmount, alert.maxAmount)],
  ];
  return (
    <dl className="facts">
      {rows
        .filter(([, value]) => value)
        .map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
    </dl>
  );
}

function AlertItem({ alert, onEdit, onChanged }: { alert: AlertSummary; onEdit: () => void; onChanged: () => void }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusy(false);
    }
  };

  // The newest first, so that what was new is on top.
  const openResults = async () => {
    setBusy(true);
    try {
      await markAlertViewed(alert.id);
    } catch {
      // the results are shown all the same
    }
    router.push(alertResultsHref(alert.id));
  };

  return (
    <section className={alert.active ? "card" : "card inactive"}>
      <h2>
        {alert.name}
        {!alert.active && <span className="badge">Désactivée</span>}
      </h2>
      <p>
        <strong>{plural(alert.newCount, "nouvelle consultation", "nouvelles consultations")}</strong>
        {alert.lastViewedAt ? ` depuis votre dernière visite le ${formatDateTime(alert.lastViewedAt)}` : ""}
        <span className="muted"> · {plural(alert.matchCount, "consultation en cours", "consultations en cours")}</span>
      </p>
      <Criteria alert={alert} />
      {error && <p className="error">{error}</p>}
      <div className="form-actions">
        <button type="button" className="primary" onClick={openResults} disabled={busy}>
          Voir les résultats
        </button>
        <button type="button" onClick={onEdit} disabled={busy}>
          Modifier
        </button>
        <button
          type="button"
          className="secondary"
          disabled={busy}
          onClick={() => run(() => updateAlert(alert.id, { ...inputOf(alert), active: !alert.active }))}
        >
          {alert.active ? "Désactiver" : "Activer"}
        </button>
        {confirming ? (
          <>
            <span>Supprimer cette veille ?</span>
            <button type="button" disabled={busy} onClick={() => run(() => deleteAlert(alert.id))}>
              Supprimer
            </button>
            <button type="button" className="link-button" onClick={() => setConfirming(false)}>
              Annuler
            </button>
          </>
        ) : (
          <button type="button" className="secondary" disabled={busy} onClick={() => setConfirming(true)}>
            Supprimer
          </button>
        )}
      </div>
    </section>
  );
}

function AlertList() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const fromList = searchParams.get("new") === "1";
  const { data, error, loading, reload } = useLoader(getAlerts, "alerts");
  const [creating, setCreating] = useState(fromList);
  const [editingId, setEditingId] = useState<number | null>(null);

  const closeCreation = () => {
    setCreating(false);
    if (fromList) {
      router.replace("/alerts");
    }
  };

  const create = async (alert: AlertInput) => {
    await createAlert(alert);
    closeCreation();
    reload();
  };

  return (
    <>
      <h1>Mes veilles</h1>
      <p className="muted">
        Une veille est une recherche enregistrée : elle signale les consultations en cours qui correspondent à ses
        critères, et celles publiées depuis votre dernière visite.
      </p>
      {!creating && (
        <p>
          <button type="button" className="primary" onClick={() => setCreating(true)}>
            Créer une veille
          </button>
        </p>
      )}
      {creating && (
        <AlertForm
          title="Nouvelle veille"
          initial={fromList ? fromSearch(searchParams) : EMPTY}
          submitLabel="Créer la veille"
          onSubmit={create}
          onCancel={closeCreation}
        />
      )}
      {error && <p className="banner error">{error}</p>}
      {loading && !data && <p className="page-message">Chargement…</p>}
      {data?.length === 0 && !creating && (
        <p className="page-message">
          Aucune veille. Créez-en une, ou enregistrez une recherche depuis la page Appels d&apos;offres.
        </p>
      )}
      {data?.map((alert) =>
        editingId === alert.id ? (
          <AlertForm
            key={alert.id}
            title={`Modifier la veille « ${alert.name} »`}
            initial={inputOf(alert)}
            submitLabel="Enregistrer"
            onSubmit={async (changes) => {
              await updateAlert(alert.id, changes);
              setEditingId(null);
              reload();
            }}
            onCancel={() => setEditingId(null)}
          />
        ) : (
          <AlertItem key={alert.id} alert={alert} onEdit={() => setEditingId(alert.id)} onChanged={reload} />
        ),
      )}
    </>
  );
}

export default function AlertsPage() {
  if (!available("alerts")) {
    return <NotConnected title="Mes veilles" />;
  }
  return (
    <Suspense>
      <AlertList />
    </Suspense>
  );
}
