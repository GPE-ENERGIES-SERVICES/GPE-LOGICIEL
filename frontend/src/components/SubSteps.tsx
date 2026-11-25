"use client";

import { useState, type ReactNode } from "react";

import type { Tone } from "@/components/analysis/PartTitle";

export type SubStep = { id: string; label: string; tone: Tone; content: ReactNode };

/**
 * The parts of a step as the tabs of a browser, each in the colour of its
 * card: the open one joins the card below it, one card on screen at a time.
 */
export function SubSteps({ label, steps }: { label: string; steps: SubStep[] }) {
  const [active, setActive] = useState(steps[0]?.id);
  const current = steps.find((step) => step.id === active) ?? steps[0];
  if (!current) {
    return null;
  }
  return (
    <div className="substeps">
      <div className="substeps-bar" role="tablist" aria-label={label}>
        {steps.map((step) => (
          <button
            key={step.id}
            type="button"
            role="tab"
            aria-selected={step === current}
            className={`substep tone-${step.tone}${step === current ? " active" : ""}`}
            onClick={() => setActive(step.id)}
          >
            {step.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" aria-label={current.label} className="substeps-panel">
        {current.content}
      </div>
    </div>
  );
}
