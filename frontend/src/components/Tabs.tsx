"use client";

import { useSyncExternalStore, type KeyboardEvent, type ReactNode } from "react";

export type Tab = { id: string; label: string; content: ReactNode };

function subscribe(onChange: () => void) {
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
}

const hash = () => window.location.hash;

/** Opens a tab: it is kept in the address, so that a link "#fiche" opens it too. */
function open(id: string) {
  window.history.replaceState(null, "", `#${id}`);
  window.dispatchEvent(new HashChangeEvent("hashchange"));
}

/**
 * Parts of a page shown one at a time. The open tab is the one named in the
 * address ("#dce"), the first one otherwise; arrows move between tabs.
 */
export function Tabs({ label, tabs }: { label: string; tabs: Tab[] }) {
  const current = useSyncExternalStore(subscribe, hash, () => "");
  const active = tabs.find((tab) => `#${tab.id}` === current) ?? tabs[0];

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") {
      return;
    }
    const index = tabs.indexOf(active);
    const next = tabs[(index + (event.key === "ArrowRight" ? 1 : tabs.length - 1)) % tabs.length];
    open(next.id);
    document.getElementById(next.id)?.focus();
  };

  return (
    <div className="tender-tabs">
      <div className="tabs" role="tablist" aria-label={label} onKeyDown={onKeyDown}>
        {tabs.map((tab) => (
          <button
            key={tab.id}
            id={tab.id}
            type="button"
            role="tab"
            className={tab === active ? "tab active" : "tab"}
            aria-selected={tab === active}
            aria-controls={`${tab.id}-panneau`}
            tabIndex={tab === active ? 0 : -1}
            onClick={() => open(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="tab-panel" role="tabpanel" id={`${active.id}-panneau`} aria-labelledby={active.id}>
        {active.content}
      </div>
    </div>
  );
}
