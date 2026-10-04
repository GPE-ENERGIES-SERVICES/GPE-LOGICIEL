import { deadlineLevel, formatDaysLeft, type DeadlineLevel } from "@/lib/format";

const LEVEL_CLASSES: Record<DeadlineLevel, string> = {
  unknown: "badge",
  passed: "badge",
  critical: "badge critical",
  close: "badge urgent",
  normal: "badge",
};

/** Days left before the deadline, coloured by urgency. */
export function DeadlineBadge({ deadline }: { deadline: string | null }) {
  return <span className={LEVEL_CLASSES[deadlineLevel(deadline)]}>{formatDaysLeft(deadline)}</span>;
}
