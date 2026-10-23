/**
 * Calls the backend through the same-origin proxy. The session travels in a
 * cookie; requests that change data carry the CSRF token read from the
 * XSRF-TOKEN cookie.
 *
 * With NEXT_PUBLIC_DATA_SOURCE=mock, the calls are answered by the simulated
 * backend (src/mock) instead: same URLs, same responses. The services call
 * these functions and never know where the data comes from.
 */

/** True when the screens run on the demo data of src/mock. */
export const DEMO_DATA = process.env.NEXT_PUBLIC_DATA_SOURCE === "mock";

/**
 * What the screens offer and the backend does not provide yet. With the real
 * backend they are hidden rather than failing; the demo data provides them
 * all. See docs/contrat-api-interface.md.
 */
export type Feature =
  /** Saved searches: Mes veilles. */
  | "alerts"
  /** Awarded contracts and winners. */
  | "awards"
  /** Search in the text of the documents of a consultation. */
  | "documentSearch"
  /** Type of works filter, from the activities of the company. */
  | "activity"
  /** Distance from the headquarters: filter and sort. */
  | "distance"
  /** Relevance score: sort. */
  | "relevance"
  /** Award criteria and key information read in the notice. */
  | "noticeDetails";

const NOT_IN_BACKEND = new Set<Feature>([
  "awards",
  "documentSearch",
  "activity",
  "distance",
  "relevance",
  "noticeDetails",
]);

export function available(feature: Feature): boolean {
  return DEMO_DATA || !NOT_IN_BACKEND.has(feature);
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfToken(): Promise<string | null> {
  const token = csrfToken();
  if (token) {
    return token;
  }
  await fetch("/api/auth/csrf", { credentials: "same-origin" });
  return csrfToken();
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = await response.json();
    if (typeof problem?.detail === "string" && problem.detail) {
      return problem.detail;
    }
  } catch {
    // no JSON body
  }
  switch (response.status) {
    case 401:
      return "Votre session a expiré. Veuillez vous reconnecter.";
    case 403:
      return "Vous n'avez pas les droits pour cette action.";
    case 404:
      return "Élément introuvable.";
    default:
      return "Une erreur est survenue. Veuillez réessayer.";
  }
}

/**
 * Sends files as multipart form data.
 */
export async function upload<T>(path: string, form: FormData): Promise<T> {
  if (DEMO_DATA) {
    throw new ApiError(501, "Le dépôt de fichiers n'est pas disponible avec les données de démonstration.");
  }
  const token = await ensureCsrfToken();
  const response = await fetch(path, {
    method: "POST",
    headers: token ? { "X-XSRF-TOKEN": token, Accept: "application/json" } : { Accept: "application/json" },
    credentials: "same-origin",
    body: form,
  });
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  return (await response.json()) as T;
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? "GET";
  if (DEMO_DATA) {
    // Loaded on demand: the demo data is never downloaded with the real backend.
    const { mockApi } = await import("@/mock/server");
    return mockApi<T>(method, path, options.body);
  }
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (UNSAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) {
      headers["X-XSRF-TOKEN"] = token;
    }
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
