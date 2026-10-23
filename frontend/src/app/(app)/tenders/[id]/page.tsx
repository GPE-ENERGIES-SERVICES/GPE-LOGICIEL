"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { Suspense, useState, type ReactNode } from "react";

import { AnalysisSection } from "@/components/analysis/AnalysisSection";
import { DeadlineBadge } from "@/components/DeadlineBadge";
import { DocumentsSection } from "@/components/DocumentsSection";
import { NoticeHistory } from "@/components/NoticeHistory";
import { FollowButton } from "@/components/FollowButton";
import { FollowUpForm } from "@/components/FollowUpForm";
import {
  ACTIVITY_LABELS,
  formatAmount,
  formatDate,
  formatDateTime,
  formatLongDateTime,
  lotTitle,
  FOLLOW_UP_LABELS,
  MARKET_TYPE_LABELS,
  PROCEDURE_LABELS,
  STATUS_LABELS,
} from "@/lib/format";
import { backLink } from "@/lib/navigation";
import { useLoader } from "@/lib/useLoader";
import { getTenderAwards } from "@/services/awardApi";
import { available } from "@/services/http";
import { getTender } from "@/services/tenderApi";
import type { AwardedContract } from "@/types/award";
import type { TenderDetail } from "@/types/tender";

/** A fact of the tender, left out when it is not known. */
function Fact({ label, children }: { label: string; children: ReactNode }) {
  if (children === null || children === undefined || children === "" || children === false) {
    return null;
  }
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function List({ items }: { items: string[] }) {
  return items.length ? (
    <ul className="plain-list">
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ul>
  ) : null;
}

function awardText(award: AwardedContract): string {
  const offers = award.offersReceived === null ? "" : ` · ${award.offersReceived} offres reçues`;
  return `${award.winner} — ${formatAmount(award.amount)} · le ${formatDate(award.awardedAt)}${offers}`;
}

function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

function TenderDetails() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const loaded = useLoader(() => getTender(id), id);
  const awards = useLoader(() => (available("awards") ? getTenderAwards(Number(id)) : Promise.resolve([])), id);
  // The tender as last saved on this page, or as loaded.
  const [saved, setSaved] = useState<TenderDetail | null>(null);
  const tender = saved ?? loaded.data;

  if (loaded.error) {
    const back = backLink(searchParams.get("from"), false);
    return (
      <>
        <p className="banner error">{loaded.error}</p>
        <p>
          <Link href={back.href}>← {back.label}</Link>
        </p>
      </>
    );
  }
  if (!tender) {
    return <p className="page-message">Chargement…</p>;
  }

  const back = backLink(searchParams.get("from"), tender.status !== "ACTIVE");
  const marketTypes = tender.marketTypes.map((type) => MARKET_TYPE_LABELS[type]).join(", ");
  const activities = (tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code).join(", ");
  const procedure = tender.procedureType ? PROCEDURE_LABELS[tender.procedureType] : null;
  const area = tender.postalCode ?? (tender.departments.length ? tender.departments.join(", ") : null);
  const place = [tender.location, area && `(${area})`].filter(Boolean).join(" ");
  const outcome =
    tender.result?.kind === "AWARDED" ? "Attribuée" : tender.status !== "ACTIVE" ? STATUS_LABELS[tender.status] : null;

  // Rule RM2: when a rectification moved the deadline, the change is highlighted. Only the notices of the source
  // of the consultation count: another source publishing it never changes it.
  const announcedDeadlines = new Set(
    tender.notices
      .filter((notice) => notice.source === tender.source)
      .map((notice) => notice.deadline)
      .filter(Boolean),
  );
  const deadlineChanged = announcedDeadlines.size > 1;
  const deadline = (
    <>
      <strong>{formatLongDateTime(tender.deadline)}</strong>{" "}
      {tender.status === "ACTIVE" && <DeadlineBadge deadline={tender.deadline} />}
      {deadlineChanged && <span className="badge urgent">Modifiée par un rectificatif</span>}
    </>
  );

  const lotAmounts = tender.lots.map((lot) => lot.estimatedAmount);
  const lotTotal = lotAmounts.reduce<number>((sum, amount) => sum + (amount ?? 0), 0);
  const awardsByLot = new Map((awards.data ?? []).map((award) => [award.lotNumber, award]));
  const singleAward = tender.lots.length === 0 ? awardsByLot.get(null) : undefined;

  const criteria = tender.awardCriteria ?? [];
  const allWeighted = criteria.every((criterion) => criterion.weight !== null);
  const weightTotal = criteria.reduce((sum, criterion) => sum + (criterion.weight ?? 0), 0);
  const info = tender.keyInfo;
  const visit = info?.siteVisit;
  const platform = tender.platformName ?? (tender.platformUrl ? hostOf(tender.platformUrl) : null);
  const cpv = tender.cpv ?? tender.cpvCodes.map((code) => ({ code, label: "" }));

  return (
    <article className="tender-page">
      <p>
        <Link href={back.href}>← {back.label}</Link>
      </p>

      <header className="tender-header">
        <h1>
          <FollowButton
            key={String(tender.followed)}
            tenderId={tender.id}
            followed={tender.followed ?? false}
            onChanged={(followed) => setSaved({ ...tender, followed })}
          />{" "}
          {tender.title}
        </h1>
        <p className="muted">
          {tender.buyer ?? "Acheteur non indiqué"}
          {tender.reference && ` · Réf. ${tender.reference}`}
        </p>
        <p className="tender-meta">{[place, marketTypes, activities, procedure].filter(Boolean).join(" · ")}</p>
        <p className="tender-meta">
          {tender.publishedAt && <>Publiée le {formatDate(tender.publishedAt)} · </>}
          Date limite de réponse : {deadline}
        </p>
        <div className="badges">
          <span className="badge">{FOLLOW_UP_LABELS[tender.followUpStatus]}</span>
          {outcome && <span className="badge">{outcome}</span>}
          {tender.retained === false && <span className="badge">Hors critères</span>}
        </div>
      </header>

      <section className="card">
        <h2>Informations principales</h2>
        <dl className="facts grid">
          <Fact label="Acheteur">{tender.buyer}</Fact>
          <Fact label="Référence">{tender.reference}</Fact>
          <Fact label="Lieu d'exécution">
            {tender.location && (
              <>
                {tender.location}
                {tender.distanceKm != null && <span className="muted"> · à {tender.distanceKm} km du siège</span>}
              </>
            )}
          </Fact>
          <Fact label="Code postal">{tender.postalCode}</Fact>
          <Fact label="Départements">{tender.postalCode ? null : tender.departments.join(", ")}</Fact>
          <Fact label="Procédure">{procedure}</Fact>
          <Fact label="Type de marché">{marketTypes}</Fact>
          <Fact label="Type de travaux">{activities}</Fact>
          <Fact label="Publication">{tender.publishedAt && formatDate(tender.publishedAt)}</Fact>
          <Fact label="Date limite de réponse">{tender.deadline && formatDateTime(tender.deadline)}</Fact>
          <Fact label="Montant estimé">{tender.estimatedAmount !== null && formatAmount(tender.estimatedAmount)}</Fact>
          <Fact label="Lots">{tender.lots.length ? tender.lots.length : "Sans allotissement"}</Fact>
          <Fact label="Pertinence">{tender.relevanceScore != null && `${tender.relevanceScore} %`}</Fact>
          <Fact label="Source">
            {tender.source} · {tender.externalId}
          </Fact>
          <Fact label="Codes CPV">
            {cpv.length > 0 && <List items={cpv.map((item) => `${item.code}${item.label ? ` – ${item.label}` : ""}`)} />}
          </Fact>
        </dl>
        {tender.filterReason && <p className="reason">{tender.filterReason}</p>}
      </section>

      <section className="card">
        <h2>Objet de la consultation</h2>
        <p className="description">{tender.description ?? "Description non disponible dans l'avis."}</p>
      </section>

      <section className="card">
        <h2>Lots</h2>
        {tender.lots.length === 0 ? (
          <>
            <p>Consultation sans allotissement : un seul marché.</p>
            {singleAward && <p>Attribution : {awardText(singleAward)}</p>}
          </>
        ) : (
          <table>
            <thead>
              <tr>
                <th>N°</th>
                <th>Intitulé</th>
                <th>Montant estimé</th>
                {awardsByLot.size > 0 && <th>Attribution</th>}
              </tr>
            </thead>
            <tbody>
              {tender.lots.map((lot) => {
                const award = awardsByLot.get(lot.number);
                return (
                  <tr key={lot.number}>
                    <td>{lot.number}</td>
                    <td>{lotTitle(lot.number, lot.title) ?? "Sans intitulé"}</td>
                    <td className="nowrap">{formatAmount(lot.estimatedAmount)}</td>
                    {awardsByLot.size > 0 && <td>{award ? awardText(award) : "Non attribué"}</td>}
                  </tr>
                );
              })}
            </tbody>
            {lotAmounts.some((amount) => amount !== null) && (
              <tfoot>
                <tr>
                  <td />
                  <td>{lotAmounts.every((amount) => amount !== null) ? "Total" : "Total des montants indiqués"}</td>
                  <td className="nowrap">{formatAmount(lotTotal)}</td>
                  {awardsByLot.size > 0 && <td />}
                </tr>
              </tfoot>
            )}
          </table>
        )}
      </section>

      {available("noticeDetails") && (
        <section className="card">
          <h2>Critères d&apos;attribution</h2>
          {criteria.length === 0 ? (
            <p className="muted">Critères non indiqués dans l&apos;avis.</p>
          ) : (
            <table className="criteria-table">
              <tbody>
                {criteria.map((criterion) => (
                  <tr key={criterion.label}>
                    <td>{criterion.label}</td>
                    <td className="number">{criterion.weight === null ? "Non pondéré" : `${criterion.weight} %`}</td>
                  </tr>
                ))}
              </tbody>
              {allWeighted && (
                <tfoot>
                  <tr>
                    <td>Total</td>
                    <td className="number">{weightTotal} %</td>
                  </tr>
                </tfoot>
              )}
            </table>
          )}
          {criteria.length > 0 && allWeighted && weightTotal !== 100 && (
            <p className="hint">Les pondérations publiées ne totalisent pas 100 % : vérifier le règlement de consultation.</p>
          )}
        </section>
      )}

      {available("noticeDetails") && (
        <section className="card">
          <h2>Informations importantes</h2>
          {!info ? (
            <p className="muted">Non renseignées dans l&apos;avis. Elles seront relevées dans le DCE lors de son analyse.</p>
          ) : (
            <dl className="facts rows">
              <Fact label="Visite du site">
                {visit?.required ? (
                  <>
                    <span className="badge urgent">Obligatoire</span> {visit.date && `le ${formatDateTime(visit.date)}`}
                    {visit.date && new Date(visit.date) < new Date() && <span className="muted"> (passée)</span>}
                  </>
                ) : visit ? (
                  "Facultative"
                ) : (
                  "Non mentionnée"
                )}
              </Fact>
              <Fact label="Documents demandés">
                {info.requiredDocuments.length > 0 && <List items={info.requiredDocuments} />}
              </Fact>
              <Fact label="Qualifications">{info.qualifications.length > 0 && <List items={info.qualifications} />}</Fact>
              <Fact label="Certifications">{info.certifications.length > 0 && <List items={info.certifications} />}</Fact>
              <Fact label="Conditions particulières">{info.specialConditions}</Fact>
            </dl>
          )}
        </section>
      )}

      <section className="card">
        <h2>Documents (DCE)</h2>
        <DocumentsSection tenderId={tender.id} />
      </section>

      <section className="card">
        <h2>Plateforme de dépôt</h2>
        <dl className="facts grid">
          <Fact label="Plateforme">{platform ?? "Non indiquée dans l'avis"}</Fact>
          <Fact label="Mode de dépôt">{platform && "Dépôt électronique sur la plateforme de l'acheteur"}</Fact>
          <Fact label="Date limite de remise">{deadline}</Fact>
        </dl>
        <div className="links">
          {tender.platformUrl && (
            <a href={tender.platformUrl} target="_blank" rel="noopener noreferrer" className="button primary">
              Accéder à la consultation
            </a>
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
        <h2>Analyse du DCE</h2>
        <p className="muted">
          Une fois le DCE déposé et lu, l&apos;analyse en tire une fiche de synthèse : informations clés, points de
          vigilance, pièces à fournir, qualifications et échéances, chacune avec sa source (document et page), à
          vérifier avant usage.
        </p>
        <AnalysisSection tenderId={tender.id} />
      </section>

      <section className="card">
        <h2>Historique des publications</h2>
        <NoticeHistory tender={tender} />
      </section>
    </article>
  );
}

export default function TenderPage() {
  return (
    <Suspense>
      <TenderDetails />
    </Suspense>
  );
}
