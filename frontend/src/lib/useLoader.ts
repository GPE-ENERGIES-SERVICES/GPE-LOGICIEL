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

const REFRESH = "gpe:refresh";

/**
 * Tells the loaders of these keys that their data changed, so that every part
 * of the page showing it is up to date without reloading the page: a key
 * ending with « * » stands for every key starting so.
 */
export function refreshData(...keys: string[]) {
  window.dispatchEvent(new CustomEvent<string[]>(REFRESH, { detail: keys }));
}

function concerns(keys: string[], key: string): boolean {
  return keys.some((wanted) => (wanted.endsWith("*") ? key.startsWith(wanted.slice(0, -1)) : wanted === key));
}

/**
 * Loads data when the component mounts, whenever the key changes, on reload
 * and when refreshData names its key. The previous data stays displayed while
 * the next one loads. An expired session sends the user back to the login page.
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
    const onRefresh = (event: Event) => {
      if (concerns((event as CustomEvent<string[]>).detail, key)) {
        setVersion((v) => v + 1);
      }
    };
    window.addEventListener(REFRESH, onRefresh);
    return () => window.removeEventListener(REFRESH, onRefresh);
  }, [key]);

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
