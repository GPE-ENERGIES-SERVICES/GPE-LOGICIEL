"use client";

import { useSyncExternalStore } from "react";

import { THEME_STORAGE_KEY } from "@/lib/theme";

import { Icon, type IconName } from "./Icon";

type Theme = "light" | "dark" | "system";

const CHANGE_EVENT = "gpe-theme-change";

const OPTIONS: { value: Theme; label: string; icon: IconName }[] = [
  { value: "light", label: "Thème clair", icon: "sun" },
  { value: "dark", label: "Thème sombre", icon: "moon" },
  { value: "system", label: "Thème du système", icon: "system" },
];

function readTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    return "system";
  }
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** Keeps the choice in this browser and applies it to the page at once. */
function chooseTheme(value: Theme) {
  try {
    if (value === "system") {
      localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      localStorage.setItem(THEME_STORAGE_KEY, value);
    }
  } catch {
    // not kept: the choice applies to this page only
  }
  const root = document.documentElement;
  if (value === "system") {
    delete root.dataset.theme;
  } else {
    root.dataset.theme = value;
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/**
 * Light, dark, or the theme of the system (the default). Only the display
 * changes; the choice stays in this browser.
 */
export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, readTheme, () => "system" as Theme);

  return (
    <div className="theme-toggle" role="group" aria-label="Thème d'affichage">
      {OPTIONS.map(({ value, label, icon }) => (
        <button
          key={value}
          type="button"
          aria-pressed={theme === value}
          aria-label={label}
          title={label}
          onClick={() => chooseTheme(value)}
        >
          <Icon name={icon} size={16} />
        </button>
      ))}
    </div>
  );
}
