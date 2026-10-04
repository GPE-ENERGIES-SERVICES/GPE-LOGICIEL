"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * Search parameters of the page URL, where lists keep their search, filters,
 * sort and page, and a function to change some of them; an empty value
 * removes the parameter.
 */
export function useUrlParams(): [URLSearchParams, (changes: Record<string, string | null>) => void] {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const navigate = (changes: Record<string, string | null>) => {
    const params = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === "") {
        params.delete(key);
      } else {
        params.set(key, value);
      }
    }
    router.replace(`${pathname}?${params}`);
  };

  return [new URLSearchParams(searchParams), navigate];
}
