import type { ReactNode } from "react";

/**
 * A titled part of a page that opens on request, its title followed by what
 * it holds (a count) so that it can be judged while closed.
 */
export function Fold({
  title,
  count,
  open = false,
  className,
  children,
}: {
  title: ReactNode;
  count?: ReactNode;
  open?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <details className={className ? `fold ${className}` : "fold"} open={open}>
      <summary>
        <span className="fold-title">{title}</span>
        {count !== undefined && count !== null && <span className="fold-count">{count}</span>}
      </summary>
      <div className="fold-body">{children}</div>
    </details>
  );
}
