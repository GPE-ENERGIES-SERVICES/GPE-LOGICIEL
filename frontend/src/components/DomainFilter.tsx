"use client";

import { ACTIVITY_LABELS } from "@/lib/format";

/** The domain of GPE a list is filtered on (CFO/CFA, PV), the same on every library. */
export function DomainFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <label>
      Domaine
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">Tous</option>
        {Object.entries(ACTIVITY_LABELS).map(([code, label]) => (
          <option key={code} value={code}>
            {label}
          </option>
        ))}
      </select>
    </label>
  );
}
