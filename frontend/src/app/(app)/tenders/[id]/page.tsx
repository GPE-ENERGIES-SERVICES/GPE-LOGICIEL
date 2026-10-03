"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

import { AnalysisSection } from "@/components/analysis/AnalysisSection";
import { DocumentsSection } from "@/components/DocumentsSection";
import { FollowUpForm } from "@/components/FollowUpForm";
import { api } from "@/lib/api";
import {
  formatAmount,
  formatDate,
  formatDateTime,
  formatDaysLeft,
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

  return (
    <article className="tender-page">
      <p>
        <Link href="/">← Retour à la liste</Link>
      </p>

      <header className="tender-header">
        <h1>{tender.title}</h1>
        <p className="muted">{orNotIndicated(tender.buyer)}</p>
        <div className="badges">
          <span className="badge">{STATUS_LABELS[tender.status]}</span>
          <span className="badge">{FOLLOW_UP_LABELS[tender.followUpStatus]}</span>
          <span className={isDeadlineClose(tender.deadline) ? "badge urgent" : "badge"}>
            {formatDaysLeft(tender.deadline)}
          </span>
        </div>
      </header>

      <section className="card">
        <h2>En un coup d&apos;œil</h2>
        <dl className="facts grid">
          <div>
            <dt>Date limite de réponse</dt>
            <dd>
              {formatDateTime(tender.deadline)}
              {deadlineChanged && <span className="badge urgent">Modifiée par un rectificatif</span>}
            </dd>
          </div>
          <div>
            <dt>Budget estimé</dt>
            <dd>{formatAmount(tender.estimatedAmount)}</dd>
          </div>
          <div>
            <dt>Lots</dt>
            <dd>{tender.lots.length ? tender.lots.length : "Pas d'allotissement indiqué"}</dd>
          </div>
          <div>
            <dt>Procédure</dt>
            <dd>{tender.procedureType ? PROCEDURE_LABELS[tender.procedureType] : NOT_INDICATED}</dd>
          </div>
          <div>
            <dt>Type de marché</dt>
            <dd>
              {tender.marketTypes.length
                ? tender.marketTypes.map((type) => MARKET_TYPE_LABELS[type]).join(", ")
                : NOT_INDICATED}
            </dd>
          </div>
          <div>
            <dt>Publication</dt>
            <dd>{formatDate(tender.publishedAt)}</dd>
          </div>
        </dl>
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
              Avis sur le BOAMP
            </a>
          )}
        </div>
      </section>

      <section className="card">
        <h2>Suivi</h2>
        <FollowUpForm tender={tender} onSaved={setSaved} key={tender.followUpAt ?? "new"} />
        {tender.filterReason && <p className="reason">{tender.filterReason}</p>}
      </section>

      <section className="card">
        <h2>Dossier de consultation (DCE)</h2>
        <DocumentsSection tenderId={tender.id} />
      </section>

      <section className="card">
        <h2>Fiche de synthèse</h2>
        <AnalysisSection tenderId={tender.id} />
      </section>

      <section className="card">
        <h2>Informations de l&apos;avis</h2>
        <dl className="facts grid">
          <div>
            <dt>Acheteur</dt>
            <dd>{orNotIndicated(tender.buyer)}</dd>
          </div>
          <div>
            <dt>Référence</dt>
            <dd>{orNotIndicated(tender.reference)}</dd>
          </div>
          <div>
            <dt>Lieu d&apos;exécution</dt>
            <dd>{orNotIndicated(tender.location)}</dd>
          </div>
          <div>
            <dt>Départements</dt>
            <dd>{tender.departments.length ? tender.departments.join(", ") : NOT_INDICATED}</dd>
          </div>
          <div>
            <dt>Codes CPV</dt>
            <dd>{tender.cpvCodes.length ? tender.cpvCodes.join(", ") : NOT_INDICATED}</dd>
          </div>
          <div>
            <dt>Identifiant BOAMP</dt>
            <dd>{tender.externalId}</dd>
          </div>
        </dl>
        {tender.description && (
          <>
            <h3>Description</h3>
            <p className="description">{tender.description}</p>
          </>
        )}
      </section>

      {tender.lots.length > 0 && (
        <section className="card">
          <h2>Lots</h2>
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
        </section>
      )}

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
