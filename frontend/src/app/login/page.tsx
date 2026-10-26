"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";

import { ThemeToggle } from "@/components/ThemeToggle";
import { login } from "@/services/authApi";
import { ApiError } from "@/services/http";

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
      await login(email, password);
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
      <div className="login-brand">
        <h1>Logiciel GPE</h1>
        <p className="muted">Connectez-vous avec votre compte de l&apos;entreprise.</p>
      </div>
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
      <div className="login-theme">
        <ThemeToggle />
      </div>
      <Suspense>
        <LoginForm />
      </Suspense>
    </main>
  );
}
