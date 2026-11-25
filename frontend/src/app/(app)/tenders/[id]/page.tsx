"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { Suspense, useState, type ReactNode } from "react";

import { AnalysisSection } from "@/components/analysis/AnalysisSection";
import { DecisionSection, VerdictBadge } from "@/components/analysis/DecisionSection";
import { FinancialSection } from "@/components/analysis/FinancialSection";
import { CctpSection, RcCcapSection } from "@/components/analysis/SpecialAnalyses";
import { DocumentsSection } from "@/components/DocumentsSection";
import { EstimatesSection } from "@/components/estimate/EstimatesSection";
import { MemoirsSection } from "@/components/memoir/MemoirsSection";
import { Fold } from "@/components/Fold";
import { NoticeHistory } from "@/components/NoticeHistory";
import { ProcessingPanel } from "@/components/ProcessingPanel";
import { AdministrativeSection } from "@/components/AdministrativeSection";
import { TechnicalSection } from "@/components/TechnicalSection";
import { ResponseSection } from "@/components/ResponseSection";
import { Tabs } from "@/components/Tabs";
import { FollowButton } from "@/components/FollowButton";
import { FollowUpForm } from "@/components/FollowUpForm";
import { StatusHistory } from "@/components/StatusHistory";
import {
  DomainChips,
  TenderBadges,
  TenderBanner,
  tenderKicker,
} from "@/components/TenderCard";
import {
  formatAmount,
  formatDate,
  formatDateTime,
  lotTitle,
  MARKET_TYPE_LABELS,
  PROCEDURE_LABELS,
} from "@/lib/format";
import { consultationUrl } from "@/lib/dceUrl";
import { backLink } from "@/lib/navigation";
import { useJourney } from "@/lib/useJourney";
import { useLoader } from "@/lib/useLoader";
import { getTenderAwards } from "@/services/awardApi";
import { available } from "@/services/http";
import {
  getTender,
  MANUAL_SOURCE,
  setTenderActivities,
} from "@/services/tenderApi";
import type { AwardedContract } from "@/types/award";
import type { TenderDetail } from "@/types/tender";

/** A fact of the tender, left out when it is not known. */
function Fact({ label, children }: { label: string; children: ReactNode }) {
  if (
    children === null ||
    children === undefined ||
    children === "" ||
    children === false
  ) {
    return null;
  }
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

const DOMAIN_CHOICES = [
  { value: "ELECTRICITY", label: "CFO / CFA" },
  { value: "PHOTOVOLTAIC", label: "PV" },
  { value: "IRVE", label: "IRVE" },
  { value: "ELECTRICITY,PHOTOVOLTAIC", label: "CFO / CFA et PV" },
  { value: "ELECTRICITY,IRVE", label: "CFO / CFA et IRVE" },
  { value: "PHOTOVOLTAIC,IRVE", label: "PV et IRVE" },
  { value: "ELECTRICITY,PHOTOVOLTAIC,IRVE", label: "CFO / CFA, PV et IRVE" },
  { value: "", label: "Hors domaines GPE" },
];

const DOMAIN_SOURCES: Record<string, string> = {
  RULES: "d'après l'avis",
  AI: "lu dans le DCE",
  MANUAL: "choisi à la main",
};

/** The domains of the tender, which a user may correct: the choice wins over the rules and the AI. */
function DomainPicker({
  tender,
  onSaved,
}: {
  tender: TenderDetail;
  onSaved: (tender: TenderDetail) => void;
}) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const current = (tender.activities ?? []).join(",");

  const choose = async (value: string) => {
    setSaving(true);
    setError(null);
    try {
      onSaved(
        await setTenderActivities(tender.id, value ? value.split(",") : []),
      );
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Enregistrement impossible.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <select
        aria-label="Domaine"
        value={tender.activitiesSource ? current : "UNSET"}
        disabled={saving}
        onChange={(event) => choose(event.target.value)}
      >
        {!tender.activitiesSource && (
          <option value="UNSET" disabled>
            Pas encore classé
          </option>
        )}
        {DOMAIN_CHOICES.map((choice) => (
          <option key={choice.value} value={choice.value}>
            {choice.label}
          </option>
        ))}
      </select>
      {tender.activitiesSource && (
        <span className="small muted">
          {" "}
          {DOMAIN_SOURCES[tender.activitiesSource]}
        </span>
      )}
      {error && <span className="error"> {error}</span>}
    </>
  );
}

function Chips({ items, strong }: { items: string[]; strong?: boolean }) {
  return (
    <ul className={strong ? "notice-chips strong" : "notice-chips"}>
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ul>
  );
}

function awardText(award: AwardedContract): string {
  const offers =
    award.offersReceived === null
      ? ""
      : ` · ${award.offersReceived} offres reçues`;
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
  // The tender as last saved on this page, or as loaded.
  const [saved, setSaved] = useState<TenderDetail | null>(null);
  const awards = useLoader(
    () =>
      available("awards") ? getTenderAwards(Number(id)) : Promise.resolve([]),
    // Reloaded when the status changes: « Attribué » adds an award.
    `${id}/${saved?.followUpStatus ?? ""}`,
  );
  const journey = useJourney(Number(id));
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
  const marketTypes = tender.marketTypes
    .map((type) => MARKET_TYPE_LABELS[type])
    .join(", ");
  const procedure = tender.procedureType
    ? PROCEDURE_LABELS[tender.procedureType]
    : null;
  const area =
    tender.postalCode ??
    (tender.departments.length ? tender.departments.join(", ") : null);
  const place = [tender.location, area && `(${area})`]
    .filter(Boolean)
    .join(" ");

  // Rule RM2: when a rectification moved the deadline, the change is highlighted. Only the notices of the source
  // of the consultation count: another source publishing it never changes it.
  const announcedDeadlines = new Set(
    tender.notices
      .filter((notice) => notice.source === tender.source)
      .map((notice) => notice.deadline)
      .filter(Boolean),
  );
  const deadlineChanged = announcedDeadlines.size > 1;

  const lotAmounts = tender.lots.map((lot) => lot.estimatedAmount);
  const lotTotal = lotAmounts.reduce<number>(
    (sum, amount) => sum + (amount ?? 0),
    0,
  );
  const awardsByLot = new Map(
    (awards.data ?? []).map((award) => [award.lotNumber, award]),
  );
  const singleAward =
    tender.lots.length === 0 ? awardsByLot.get(null) : undefined;

  const criteria = tender.awardCriteria ?? [];
  const allWeighted = criteria.every((criterion) => criterion.weight !== null);
  const weightTotal = criteria.reduce(
    (sum, criterion) => sum + (criterion.weight ?? 0),
    0,
  );
  const info = tender.keyInfo;
  const visit = info?.siteVisit;
  const platform =
    tender.platformName ??
    (tender.platformUrl ? hostOf(tender.platformUrl) : null);
  const cpv =
    tender.cpv ?? tender.cpvCodes.map((code) => ({ code, label: "" }));

  return (
    <article className="tender-page">
      <p>
        <Link href={back.href}>← {back.label}</Link>
      </p>

      <header className="tender-card tender-hero">
        <TenderBanner
          kicker={tenderKicker(tender)}
          buyer={tender.buyer}
          place={place}
          lead={
            <FollowButton
              key={String(tender.followed)}
              tenderId={tender.id}
              followed={tender.followed ?? false}
              onChanged={(followed) => setSaved({ ...tender, followed })}
            />
          }
          badges={
            <>
              <a href="#suivi" className="badge-link">
                <TenderBadges tender={tender} />
              </a>
              <VerdictBadge tenderId={tender.id} />
              {deadlineChanged && (
                <span className="badge urgent">
                  Date limite modifiée par un rectificatif
                </span>
              )}
            </>
          }
        />
        <div className="tender-card-body">
          <div className="hero-top">
            <div className="hero-title">
              <p className="tender-card-meta">
                {[
                  procedure,
                  marketTypes,
                  tender.reference && `Réf. ${tender.reference}`,
                ]
                  .filter(Boolean)
                  .join(" | ")}
              </p>
              <h1>{tender.title}</h1>
              <div className="chips">
                <DomainChips activities={tender.activities} />
              </div>
            </div>
            <div className="hero-actions">
              {tender.platformUrl && (
                <a
                  href={consultationUrl(tender.platformUrl)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="button primary"
                >
                  Accéder à la consultation
                </a>
              )}
              <a href="#dce" className="button">
                Déposer le DCE
              </a>
              {tender.noticeUrl && (
                <a
                  href={tender.noticeUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hero-link"
                >
                  Voir l&apos;avis sur le {tender.source}
                </a>
              )}
            </div>
          </div>
          {/* What the banner and the line above do not say yet, each leading where it is detailed. */}
          <nav className="key-links" aria-label="Repères">
            <a href="#infos" className="key-link">
              Montant estimé{" "}
              <strong>
                {tender.estimatedAmount !== null
                  ? formatAmount(tender.estimatedAmount)
                  : "Non indiqué"}
              </strong>
            </a>
            <a href="#infos" className="key-link">
              <strong>
                {tender.lots.length
                  ? `${tender.lots.length} lots`
                  : "Lot unique"}
              </strong>
              {tender.lots.length > 0 && " voir le détail"}
            </a>
            {visit?.required && (
              <a href="#infos" className="key-link warning">
                Visite obligatoire{" "}
                <strong>
                  {visit.date ? formatDateTime(visit.date) : "date dans le DCE"}
                </strong>
              </a>
            )}
            {platform &&
              (tender.platformUrl ? (
                <a
                  href={consultationUrl(tender.platformUrl)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="key-link"
                >
                  Dépôt sur <strong>{platform}</strong> ↗
                </a>
              ) : (
                <span className="key-link">
                  Dépôt sur <strong>{platform}</strong>
                </span>
              ))}
          </nav>
          <div className="hero-notice" id="infos">
            <p className="hero-object">
              {tender.description ?? "Description non disponible dans l'avis."}
            </p>
            <div className="notice-grid">
              <section className="notice-block tone-info">
                <h2>Le marché</h2>
                <dl className="notice-facts">
                  <Fact label="Lieu">
                    {tender.location && (
                      <>
                        <strong>{tender.location}</strong>
                        {tender.distanceKm != null && ` · ${tender.distanceKm} km du siège`}
                      </>
                    )}
                  </Fact>
                  <Fact label="Publié le">
                    {tender.publishedAt && <strong>{formatDate(tender.publishedAt)}</strong>}
                  </Fact>
                  {available("noticeDetails") && (
                    <Fact label="Notation">
                      {criteria.length === 0 ? (
                        <span className="muted">Non indiquée dans l&apos;avis</span>
                      ) : (
                        <>
                          {criteria.map((criterion, index) => (
                            <span key={criterion.label}>
                              {index > 0 && " · "}
                              {criterion.label}{" "}
                              <strong className="score">
                                {criterion.weight === null ? "non pondéré" : `${criterion.weight} %`}
                              </strong>
                            </span>
                          ))}
                          {allWeighted && weightTotal !== 100 && (
                            <span className="badge urgent">Total ≠ 100 %, voir le RC</span>
                          )}
                        </>
                      )}
                    </Fact>
                  )}
                  <Fact label="CPV">
                    {cpv.length > 0 &&
                      cpv.map((item) => (
                        <div key={item.code}>
                          <strong>{item.code}</strong>
                          {item.label && ` ${item.label}`}
                        </div>
                      ))}
                  </Fact>
                  <Fact label="Domaine">
                    <DomainPicker tender={tender} onSaved={setSaved} />
                  </Fact>
                  <Fact label="Pertinence">
                    {tender.relevanceScore != null && (
                      <>
                        <strong className="score">{tender.relevanceScore} %</strong>
                        {tender.filterReason && <span className="muted small"> {tender.filterReason}</span>}
                      </>
                    )}
                  </Fact>
                  <Fact label="Attribution">
                    {singleAward && awardText(singleAward)}
                  </Fact>
                </dl>
              </section>

                <section className="notice-block tone-warning">
                  <h2>À prévoir</h2>
                  {!available("noticeDetails") || !info ? (
                    <p className="muted small">
                      Non renseigné dans l&apos;avis : relevé dans le DCE.
                    </p>
                  ) : (
                    <dl className="notice-facts">
                      <Fact label="Visite">
                        {visit?.required ? (
                          <>
                            <span className="badge urgent">Obligatoire</span>{" "}
                            {visit.date && <strong>{formatDateTime(visit.date)}</strong>}
                            {visit.date && new Date(visit.date) < new Date() && <span className="muted"> (passée)</span>}
                          </>
                        ) : visit ? (
                          <span className="badge verified">Facultative</span>
                        ) : (
                          <span className="muted">Non mentionnée</span>
                        )}
                      </Fact>
                      <Fact label="À fournir">
                        {info.requiredDocuments.length > 0 && <Chips items={info.requiredDocuments} />}
                      </Fact>
                      <Fact label="Qualifications">
                        {info.qualifications.length > 0 && <Chips items={info.qualifications} strong />}
                      </Fact>
                      <Fact label="Certifications">
                        {info.certifications.length > 0 && <Chips items={info.certifications} strong />}
                      </Fact>
                      <Fact label="Conditions">{info.specialConditions}</Fact>
                    </dl>
                  )}
                </section>
            </div>

            {tender.lots.length > 0 && (
              <section className="notice-block tone-info notice-lots">
                <h2>{tender.lots.length} lots</h2>
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
              </section>
            )}

            <p className="notice-source">
              {tender.source === MANUAL_SOURCE ? "Ajouté manuellement" : `Avis ${tender.source} n° ${tender.externalId}`}
            </p>
          </div>
        </div>
      </header>

      <ProcessingPanel
        tenderId={tender.id}
        retained={tender.retained ?? null}
      />

      <Tabs
        label="Sections de l'appel d'offres"
        tabs={[
          {
            id: "dce",
            label: "DCE et documents",
            step: journey.dce,
            content: (
              <section className="card section tone-muted">
                <h2>Documents (DCE)</h2>
                <DocumentsSection
                  tenderId={tender.id}
                  manual={tender.source === MANUAL_SOURCE}
                />
              </section>
            ),
          },
          {
            id: "fiche",
            label: "Fiche IA",
            step: journey.summary,
            content: (
              <section className="card section tone-violet">
                <h2>Analyse du DCE</h2>
                <DecisionSection
                  tenderId={tender.id}
                  followUp={{ status: tender.followUpStatus, by: tender.followUpBy, at: tender.followUpAt, }}
                />
                <AnalysisSection tenderId={tender.id} />
              </section>
            ),
          },
          {
            id: "rc-ccap",
            label: "Analyse RC / CCAP",
            step: journey.summary,
            content: (
              <section className="card section tone-info">
                <h2>Analyse du RC et du CCAP</h2>
                <p className="muted">
                  Après la fiche IA, le détail de ce qui règle la réponse (RC) et
                  de ce que le contrat coûte (CCAP), chaque point avec sa source.
                </p>
                <RcCcapSection tenderId={tender.id} />
              </section>
            ),
          },
          {
            id: "cctp",
            label: "CCTP et choix techniques",
            step: journey.technical,
            content: (
              <section className="card section tone-info">
                <h2>CCTP et choix techniques</h2>
                <p className="muted">
                  Dans l&apos;ordre du chiffreur : le matériel, les choix
                  imposés, la puissance, le chantier, les points de vigilance,
                  puis nos choix techniques, d&apos;où part le chiffrage.
                </p>
                <CctpSection
                  tenderId={tender.id}
                  choices={<TechnicalSection tenderId={tender.id} />}
                />
              </section>
            ),
          },
          {
            id: "chiffrage",
            label: "Étude du chiffrage",
            step: journey.estimate,
            content: (
              <section className="card section tone-success">
                <h2>Étude du chiffrage</h2>
                <p className="muted">
                  Thomas reprend le DPGF, pré-chiffre chaque ligne (matériel et
                  main-d&apos;œuvre) et vous laisse confirmer : vert = prix sûr,
                  orange = à vérifier, rouge = sans prix. Une fois validé, le
                  DPGF rempli s&apos;exporte.
                </p>
                <EstimatesSection tender={tender} />
                <Fold title="Contrôle des bordereaux (DPGF, BPU, DQE)" count="sommes, totaux, TVA">
                  <FinancialSection tenderId={tender.id} />
                </Fold>
              </section>
            ),
          },
          {
            id: "memoire",
            label: "Mémoire",
            step: journey.memoir,
            content: (
              <section className="card section tone-violet">
                <h2>Mémoire technique</h2>
                <MemoirsSection tender={tender} />
              </section>
            ),
          },
          {
            id: "administratif",
            label: "Administratif",
            step: journey.administrative,
            content: (
              <section className="card section tone-warning">
                <h2>Administratif</h2>
                <AdministrativeSection
                  tenderId={tender.id}
                  lots={tender.lots}
                />
              </section>
            ),
          },
          {
            id: "reponse",
            label: "Dossier de réponse",
            step: journey.response,
            content: (
              <section className="card section tone-info">
                <h2>Dossier de réponse</h2>

                <ResponseSection tenderId={tender.id} />
              </section>
            ),
          },
          {
            id: "suivi",
            label: "Suivi et historique",
            content: (
              <>
                <section className="card section tone-warning">
                  <h2>Suivi</h2>
                  <FollowUpForm
                    tender={tender}
                    onSaved={setSaved}
                    key={tender.followUpAt ?? "new"}
                  />
                </section>

                <section className="card section tone-warning">
                  <h2>Historique des statuts</h2>
                  <StatusHistory
                    tenderId={tender.id}
                    version={tender.followUpAt ?? "new"}
                  />
                </section>

                <section className="card section tone-muted">
                  <h2>Historique des publications</h2>
                  <NoticeHistory tender={tender} />
                </section>
              </>
            ),
          },
        ]}
      />
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
