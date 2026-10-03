"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";

import { api, ApiError } from "@/lib/api";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await api("/api/auth/login", { method: "POST", body: { email, password } });
      const next = searchParams.get("next");
      // Only local paths: never redirect to another site.
      router.replace(next && next.startsWith("/") && !next.startsWith("//") ? next : "/");
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? "Identifiant ou mot de passe incorrect."
          : err instanceof Error
            ? err.message
            : "Connexion impossible.",
      );
      setSubmitting(false);
    }
  };

  return (
    <form className="login-card" onSubmit={submit}>
      <h1>Veille appels d&apos;offres</h1>
      <label>
        Adresse e-mail
        <input
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      <label>
        Mot de passe
        <input
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? "Connexion…" : "Se connecter"}
      </button>
    </form>
  );
}

export default function LoginPage() {
  return (
    <main className="login-page">
      <Suspense>
        <LoginForm />
      </Suspense>
    </main>
  );
}
