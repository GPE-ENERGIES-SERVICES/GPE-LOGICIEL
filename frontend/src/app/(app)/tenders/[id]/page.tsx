"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState, type ReactNode } from "react";

import { AnalysisSection } from "@/components/analysis/AnalysisSection";
import { DocumentsSection } from "@/components/DocumentsSection";
import { FollowButton } from "@/components/FollowButton";
import { FollowUpForm } from "@/components/FollowUpForm";
import { api } from "@/lib/api";
import {
  ACTIVITY_LABELS,
  formatAmount,
  formatDate,
  formatDateTime,
  formatDaysLeft,
  formatLongDateTime,
  FOLLOW_UP_LABELS,
  isDeadlineClose,
  MARKET_TYPE_LABELS,
  NOT_INDICATED,
  NOTICE_KIND_LABELS,
  orNotIndicated,
  PROCEDURE_LABELS,
  STATUS_LABELS,
} from "@/lib/format";
import type { TenderDetail } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function listOrNotIndicated(values: string[]): string {
  return values.length ? values.join(", ") : NOT_INDICATED;
}

export default function TenderPage() {
  const { id } = useParams<{ id: string }>();
  const loaded = useLoader(() => api<TenderDetail>(`/api/tenders/${id}`), id);
  // The tender as last saved on this page, or as loaded.
  const [saved, setSaved] = useState<TenderDetail | null>(null);
  const tender = saved ?? loaded.data;

  if (loaded.error) {
    return <p className="banner error">{loaded.error}</p>;
  }
  if (!tender) {
    return <p className="page-message">Chargement…</p>;
  }

  // Rule RM2: when a rectification moved the deadline, the change is highlighted.
  const announcedDeadlines = new Set(tender.notices.map((notice) => notice.deadline).filter(Boolean));
  const deadlineChanged = announcedDeadlines.size > 1;
  const conditions = tender.conditions;
  const activities = (tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code);

  return (
    <article className="tender-page">
      <p>
        <Link href="/tenders">← Retour à la liste</Link>
      </p>

      <header className="tender-header">
        <h1>
          <FollowButton
            tenderId={tender.id}
            followed={tender.followed ?? false}
            onChanged={(followed) => setSaved({ ...tender, followed })}
          />{" "}
          {tender.title}
        </h1>
        <p className="muted">{orNotIndicated(tender.buyer)}</p>
        <div className="badges">
          <span className="badge">{STATUS_LABELS[tender.status]}</span>
          <span className="badge">{FOLLOW_UP_LABELS[tender.followUpStatus]}</span>
          {tender.retained === false && <span className="badge">Hors critères</span>}
        </div>
      </header>

      <section className="card">
        <h2>Informations générales</h2>
        <dl className="facts grid">
          <Fact label="Date limite de réponse">
            <strong>{formatLongDateTime(tender.deadline)}</strong>
            <br />
            <span className={isDeadlineClose(tender.deadline) ? "badge urgent" : "badge"}>
              {formatDaysLeft(tender.deadline)}
            </span>
            {deadlineChanged && <span className="badge urgent">Modifiée par un rectificatif</span>}
          </Fact>
          <Fact label="Montant estimé">{formatAmount(tender.estimatedAmount)}</Fact>
          <Fact label="Pertinence">
            {tender.relevanceScore != null ? `${tender.relevanceScore} %` : "Non calculée"}
          </Fact>
          <Fact label="Référence">{orNotIndicated(tender.reference)}</Fact>
          <Fact label="Procédure">
            {tender.procedureType ? PROCEDURE_LABELS[tender.procedureType] : NOT_INDICATED}
          </Fact>
          <Fact label="Type de marché">
            {listOrNotIndicated(tender.marketTypes.map((type) => MARKET_TYPE_LABELS[type]))}
          </Fact>
          <Fact label="Lieu d'exécution">
            {orNotIndicated(tender.location)}
            {tender.distanceKm != null && <span className="muted"> · à {tender.distanceKm} km du siège</span>}
          </Fact>
          <Fact label="Départements">{listOrNotIndicated(tender.departments)}</Fact>
          <Fact label="Publication">{formatDate(tender.publishedAt)}</Fact>
          <Fact label="Source">
            {tender.source} · {tender.externalId}
          </Fact>
        </dl>
        {tender.filterReason && <p className="reason">{tender.filterReason}</p>}
        <div className="links">
          {tender.platformUrl ? (
            <a href={tender.platformUrl} target="_blank" rel="noopener noreferrer" className="button primary">
              Plateforme de l&apos;acheteur (DCE)
            </a>
          ) : (
            <span className="muted">Plateforme de l&apos;acheteur non indiquée</span>
          )}
          {tender.noticeUrl && (
            <a href={tender.noticeUrl} target="_blank" rel="noopener noreferrer" className="button">
              Avis sur le {tender.source}
            </a>
          )}
        </div>
      </section>

      <section className="card">
        <h2>Suivi</h2>
        <FollowUpForm tender={tender} onSaved={setSaved} key={tender.followUpAt ?? "new"} />
      </section>

      <section className="card">
        <h2>Travaux</h2>
        <dl className="facts grid">
          <Fact label="Type de travaux">{listOrNotIndicated(activities)}</Fact>
          <Fact label="Codes CPV">{listOrNotIndicated(tender.cpvCodes)}</Fact>
          <Fact label="Allotissement">
            {tender.lots.length
              ? `${tender.lots.length} lot${tender.lots.length > 1 ? "s" : ""}`
              : "Pas d'allotissement indiqué"}
          </Fact>
        </dl>
        <h3 className="section-title">Description</h3>
        <p className="description">{orNotIndicated(tender.description)}</p>
        {tender.lots.length > 0 && (
          <>
            <h3 className="section-title">Lots</h3>
            <table>
              <thead>
                <tr>
                  <th>N°</th>
                  <th>Intitulé</th>
                  <th>Montant estimé</th>
                </tr>
              </thead>
              <tbody>
                {tender.lots.map((lot) => (
                  <tr key={lot.number}>
                    <td>{lot.number}</td>
                    <td>{orNotIndicated(lot.title)}</td>
                    <td>{formatAmount(lot.estimatedAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      <section className="card">
        <h2>Conditions de participation</h2>
        {conditions ? (
          <dl className="facts grid">
            <Fact label="Qualifications et certifications">
              {conditions.qualifications.length ? conditions.qualifications.join(" ; ") : "Aucune indiquée"}
            </Fact>
            <Fact label="Visite du site">
              {conditions.siteVisit === "Obligatoire" ? (
                <span className="badge urgent">Obligatoire</span>
              ) : (
                orNotIndicated(conditions.siteVisit)
              )}
            </Fact>
            <Fact label="Critères d'attribution">{orNotIndicated(conditions.selectionCriteria)}</Fact>
            <Fact label="Contraintes">{orNotIndicated(conditions.constraints)}</Fact>
          </dl>
        ) : (
          <p className="muted">
            Conditions non indiquées dans l&apos;avis. Elles seront relevées dans le dossier de consultation par la
            fiche de synthèse.
          </p>
        )}
      </section>

      <section className="card">
        <h2>Documents (DCE)</h2>
        <DocumentsSection tenderId={tender.id} />
      </section>

      <section className="card">
        <h2>Fiche de synthèse</h2>
        <AnalysisSection tenderId={tender.id} />
      </section>

      <section className="card">
        <h2>Historique des publications</h2>
        <ol className="history">
          {tender.notices.map((notice) => (
            <li key={notice.externalId}>
              <strong>{NOTICE_KIND_LABELS[notice.kind]}</strong> du {formatDate(notice.publishedAt)} (
              {notice.externalId})
              {notice.deadline && <> — date limite annoncée : {formatDateTime(notice.deadline)}</>}
              {notice.details && <p className="description">{notice.details}</p>}
            </li>
          ))}
        </ol>
      </section>
    </article>
  );
}
