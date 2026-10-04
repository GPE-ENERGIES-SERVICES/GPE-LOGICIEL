"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError } from "@/services/http";

type LoaderState<T> = {
  data: T | null;
  error: string | null;
  loading: boolean;
  reload: () => void;
};

type Result<T> = {
  request: string;
  data: T | null;
  error: string | null;
};

/**
 * Loads data when the component mounts, whenever the key changes and on
 * reload. The previous data stays displayed while the next one loads. An
 * expired session sends the user back to the login page.
 */
export function useLoader<T>(load: () => Promise<T>, key: string): LoaderState<T> {
  const router = useRouter();
  const [version, setVersion] = useState(0);
  const [result, setResult] = useState<Result<T>>({ request: "", data: null, error: null });
  const loadRef = useRef(load);
  const request = `${key}#${version}`;

  useEffect(() => {
    loadRef.current = load;
  });

  useEffect(() => {
    let cancelled = false;
    loadRef
      .current()
      .then((data) => {
        if (!cancelled) {
          setResult({ request, data, error: null });
        }
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        if (err instanceof ApiError && err.status === 401) {
          router.replace(`/login?next=${encodeURIComponent(window.location.pathname)}`);
          return;
        }
        setResult((previous) => ({
          request,
          data: previous.data,
          error: err instanceof Error ? err.message : "Une erreur est survenue.",
        }));
      });
    return () => {
      cancelled = true;
    };
  }, [request, router]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { data: result.data, error: result.error, loading: result.request !== request, reload };
}
