"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { AnalysisSection } from "@/components/analysis/AnalysisSection";
import { DecisionSection } from "@/components/analysis/DecisionSection";
import { FinancialSection } from "@/components/analysis/FinancialSection";
import { DocumentsSection } from "@/components/DocumentsSection";
import { Fold } from "@/components/Fold";
import { ProcessingPanel } from "@/components/ProcessingPanel";
import { Tabs } from "@/components/Tabs";
import { useLoader } from "@/lib/useLoader";
import {
  attachDceReview,
  deleteDceReview,
  getDceReview,
  renameDceReview,
  reviewOwner,
} from "@/services/documentApi";
import { getTenders } from "@/services/tenderApi";
import type { DceReview } from "@/types/document";
import type { TenderSummary } from "@/types/tender";

/** Renames, deletes the analysis or gives it to a tender. */
function ReviewActions({ review, onRenamed }: { review: DceReview; onRenamed: () => void }) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [found, setFound] = useState<TenderSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = async (action: () => Promise<unknown>, after: () => void) => {
    setError(null);
    try {
      await action();
      after();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    }
  };

  const rename = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    run(() => renameDceReview(review.id, name), onRenamed);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const params = new URLSearchParams({ q: query, tab: "ALL", size: "8" });
    run(
      async () => setFound((await getTenders(params.toString())).content),
      () => undefined,
    );
  };

  const attach = (tender: TenderSummary) => {
    if (window.confirm(`Rattacher ce DCE et ses analyses à « ${tender.title} » ? Cette analyse libre n'existera plus.`)) {
      run(
        () => attachDceReview(review.id, tender.id),
        () => router.push(`/tenders/${tender.id}#dce`),
      );
    }
  };

  const remove = () => {
    if (window.confirm(`Supprimer « ${review.name} », ses analyses et ses fichiers ?`)) {
      run(
        () => deleteDceReview(review.id),
        () => router.push("/dce-reviews"),
      );
    }
  };

  return (
    <Fold title="Renommer, rattacher, supprimer">
      <form className="inline-form" onSubmit={rename}>
        <input key={review.name} name="name" required maxLength={300} defaultValue={review.name} aria-label="Nom de l'analyse" />
        <button type="submit">Renommer</button>
      </form>
      <h3>Rattacher à un appel d&apos;offres</h3>
      <p className="hint">Le DCE et ses analyses passent sur sa page, où le chiffrage et le mémoire sont disponibles.</p>
      <form className="inline-form" onSubmit={search}>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Rechercher par mots de l'objet, acheteur, référence…"
          aria-label="Rechercher un appel d'offres"
        />
        <button type="submit" disabled={query.trim().length < 2}>
          Chercher
        </button>
      </form>
      {found && (
        <ul className="plain-list">
          {found.length === 0 && <li className="muted">Aucun appel d&apos;offres trouvé.</li>}
          {found.map((tender) => (
            <li key={tender.id}>
              <button type="button" className="link-button" onClick={() => attach(tender)}>
                {tender.title}
              </button>
              <span className="muted"> · {tender.buyer ?? "Acheteur non indiqué"}</span>
            </li>
          ))}
        </ul>
      )}
      <h3>Supprimer</h3>
      <button type="button" className="secondary" onClick={remove}>
        Supprimer cette analyse et ses fichiers
      </button>
      {error && <p className="banner error">{error}</p>}
    </Fold>
  );
}

/** A consultation file analysed without any tender: the same sections as on the page of a tender. */
export default function DceReviewPage() {
  const id = Number(useParams<{ id: string }>().id);
  const review = useLoader(() => getDceReview(id), String(id));
  const owner = reviewOwner(id);

  return (
    <div>
      <p>
        <Link href="/dce-reviews">← Analyser un DCE</Link>
      </p>
      {review.error && <p className="banner error">{review.error}</p>}
      {review.data && (
        <>
          <h1>{review.data.name}</h1>
          <ReviewActions review={review.data} onRenamed={review.reload} />
          <ProcessingPanel owner={owner} retained={null} />
          <DecisionSection owner={owner} />
          <Tabs
            label="Sections de l'analyse du DCE"
            tabs={[
              {
                id: "dce",
                label: "DCE et documents",
                content: (
                  <section className="card">
                    <h2>Documents (DCE)</h2>
                    <DocumentsSection owner={owner} />
                  </section>
                ),
              },
              {
                id: "fiche",
                label: "Fiche IA",
                content: (
                  <section className="card">
                    <h2>Analyse du DCE</h2>
                    <p className="muted">
                      Une fois le DCE déposé et lu, l&apos;analyse en tire une fiche de synthèse : informations clés, points de
                      vigilance, pièces à fournir, qualifications et échéances, chacune avec sa source (document et page), à
                      vérifier avant usage.
                    </p>
                    <AnalysisSection owner={owner} />
                  </section>
                ),
              },
              {
                id: "finance",
                label: "Analyse financière",
                content: (
                  <section className="card">
                    <h2>Analyse financière (DPGF, BPU, DQE)</h2>
                    <p className="muted">
                      À la demande : les montants des tableurs sont contrôlés par le logiciel (sommes, totaux, TVA), puis
                      l&apos;IA résume les postes principaux et les points à vérifier, chacun avec sa source (document, feuille,
                      ligne).
                    </p>
                    <FinancialSection owner={owner} />
                  </section>
                ),
              },
            ]}
          />
        </>
      )}
    </div>
  );
}
