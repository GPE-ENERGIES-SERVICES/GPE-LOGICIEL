import type { ReactNode } from "react";

import { Icon, type IconName } from "@/components/Icon";

export type Tone = "accent" | "success" | "warning" | "danger" | "muted";

/**
 * The title of a part: its mark and its words carry the colour, what it holds stays in the colour of the text.
 * @param icon its drawing, or "valid" for the sign used everywhere for what is valid
 */
export function PartTitle({ icon, tone, children }: { icon: IconName | "valid"; tone: Tone; children: ReactNode }) {
  return (
    <span className={`part-title tone-${tone}`}>
      {icon === "valid" ? <span aria-hidden="true">✓</span> : <Icon name={icon} size={16} />}
      {children}
    </span>
  );
}

const KEY_VALUES = /(\d{2}\/\d{2}\/\d{4}(?: à \d{2}:\d{2})?|\d[\d\s.,]*\s?(?:%|k€|M€|€(?:\s?(?:HT|TTC))?))/g;

/** A text with its dates, amounts and percentages in bold: what the eye looks for. */
export function KeyValues({ text }: { text: string }) {
  return (
    <>
      {text.split(KEY_VALUES).map((part, index) => (index % 2 === 1 ? <strong key={index}>{part}</strong> : part))}
    </>
  );
}
