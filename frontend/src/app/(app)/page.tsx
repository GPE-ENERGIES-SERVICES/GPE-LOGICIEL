"use client";

import Link from "next/link";

import { CollectStatus } from "@/components/CollectStatus";
import { api } from "@/lib/api";
import { TENDER_TAB_LABELS } from "@/lib/format";
import type { TenderTab } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

/** Tabs that call for an action, shown as figures. */
const KEY_TABS: TenderTab[] = ["NEW", "TO_PROCESS", "FOLLOWED", "FILTERED_OUT"];

export default function DashboardPage() {
  const counts = useLoader(() => api<Record<TenderTab, number>>("/api/tenders/counts"), "counts");

  return (
    <>
      <h1>Tableau de bord</h1>
      <CollectStatus />
      {counts.error && <p className="banner error">{counts.error}</p>}
      <div className="stats">
        {KEY_TABS.map((tab) => (
          <Link key={tab} href={`/tenders?tab=${tab}`} className="card stat">
            <strong>{counts.data?.[tab] ?? "–"}</strong>
            <span>{TENDER_TAB_LABELS[tab]}</span>
          </Link>
        ))}
      </div>
    </>
  );
}
