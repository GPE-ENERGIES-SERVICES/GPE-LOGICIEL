/**
 * Where a long task stands: a bar filled to the share done, or moving when
 * the share cannot be known (a call to the AI), with what is going on.
 * @param value share done, from 0 to 1; null when it cannot be known
 */
export function ProgressBar({ value, label, detail }: { value: number | null; label: string; detail?: string }) {
  const percent = value === null ? null : Math.round(Math.min(Math.max(value, 0), 1) * 100);
  return (
    <div className="progress">
      <div className="progress-text">
        <span>{label}</span>
        {percent !== null && <span className="progress-percent">{percent} %</span>}
      </div>
      <div
        className={percent === null ? "progress-track indeterminate" : "progress-track"}
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
      >
        <div className="progress-fill" style={percent === null ? undefined : { width: `${percent}%` }} />
      </div>
      {detail && <p className="hint">{detail}</p>}
    </div>
  );
}
