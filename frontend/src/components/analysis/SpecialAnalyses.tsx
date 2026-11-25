"use client";

import { KeyValues, PartTitle, type Tone } from "@/components/analysis/PartTitle";
import { Icon, type IconName } from "@/components/Icon";
import { SubSteps } from "@/components/SubSteps";
import { useLoader } from "@/lib/useLoader";
import { getCctpAnalysis, getRcCcapAnalysis } from "@/services/specialAnalysisApi";
import type { ReadPart, ReadPoint } from "@/types/specialAnalysis";

const LEVELS = {
  HIGH: { label: "Fort", className: "badge critical" },
  MEDIUM: { label: "Moyen", className: "badge urgent" },
  LOW: { label: "Faible", className: "badge" },
} as const;

function ExampleBanner() {
  return (
    <p className="banner info">
      Aperçu avec des données d&apos;exemple. Cette étape sera remplie par la même analyse que la{" "}
      <a href="#fiche">fiche IA</a> : un seul lancement lit le DCE et remplit la fiche, le RC / CCAP et le CCTP.
    </p>
  );
}

function Source({ source }: { source: string | null }) {
  return source ? <span className="read-source">{source}</span> : null;
}

function Points({ points }: { points: ReadPoint[] }) {
  return (
    <dl className="read-points">
      {points.map((point) => (
        <div key={point.label}>
          <dt>
            {point.level && <span className={LEVELS[point.level].className}>{LEVELS[point.level].label}</span>}
            {point.label}
          </dt>
          <dd>
            <KeyValues text={point.text} /> <Source source={point.source} />
          </dd>
        </div>
      ))}
    </dl>
  );
}

/** A part read in the DCE, folded by a click on its title: a solid band of its colour, with what it holds on the right. */
export function Block({
  icon,
  tone,
  title,
  count,
  children,
}: {
  icon: IconName;
  tone: Tone;
  title: string;
  count?: string;
  children: React.ReactNode;
}) {
  return (
    <details className={`read-block tone-${tone}`} open>
      <summary className="read-band">
        <PartTitle icon={icon} tone={tone}>
          {title}
        </PartTitle>
        {count && <span className="read-count">{count}</span>}
        <span className="read-chevron" aria-hidden="true">
          <Icon name="chevronDown" size={14} />
        </span>
      </summary>
      <div className="read-body">{children}</div>
    </details>
  );
}

function Parts({ parts }: { parts: ReadPart[] }) {
  return parts.map((part) => (
    <div key={part.title} className="read-part">
      <h4>{part.title}</h4>
      <Points points={part.points} />
    </div>
  ));
}

/** The RC (how to answer) then the CCAP (what the contract costs), read for the costing. */
export function RcCcapSection({ tenderId }: { tenderId: number }) {
  const { data, error } = useLoader(() => getRcCcapAnalysis(tenderId), `rc-ccap-${tenderId}`);
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }
  return (
    <>
      {data.example && <ExampleBanner />}
      <SubSteps
        label="Parties de l'analyse RC / CCAP"
        steps={[
          {
            id: "rc",
            label: "Règlement de consultation (RC)",
            tone: "accent",
            content: (
              <Block icon="clipboard" tone="accent" title="Règlement de consultation (RC)">
                <Parts parts={data.rc} />
              </Block>
            ),
          },
          {
            id: "ccap",
            label: "Clauses administratives (CCAP)",
            tone: "warning",
            content: (
              <Block icon="file" tone="warning" title="Clauses administratives (CCAP)">
                <Parts parts={data.ccap} />
              </Block>
            ),
          },
        ]}
      />
    </>
  );
}

/** The CCTP read for the costing: equipment, choices imposed, power, site, what to watch, then the choices of GPE. */
export function CctpSection({ tenderId, choices }: { tenderId: number; choices?: React.ReactNode }) {
  const { data, error } = useLoader(() => getCctpAnalysis(tenderId), `cctp-${tenderId}`);
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return <p className="muted">Chargement…</p>;
  }
  return (
    <>
      {data.example && <ExampleBanner />}

      <SubSteps
        label="Parties de l'analyse du CCTP"
        steps={[
          {
            id: "materiel",
            label: "Liste du matériel",
            tone: "accent",
            content: (
              <Block icon="tag" tone="accent" title="Liste du matériel">
                <table>
                  <thead>
                    <tr>
                      <th>Désignation</th>
                      <th>Exigence du CCTP</th>
                      <th>Quantité</th>
                      <th>Source</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.equipment.map((line) => (
                      <tr key={line.designation}>
                        <td>
                          <strong>{line.designation}</strong>
                        </td>
                        <td>{line.requirement ?? <span className="muted">Libre</span>}</td>
                        <td className="nowrap">{line.quantity ?? "—"}</td>
                        <td>
                          <Source source={line.source} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Block>
            ),
          },
          {
            id: "imposes",
            label: "Choix imposés",
            tone: "accent",
            content: (
              <Block icon="settings" tone="accent" title="Choix techniques imposés">
                <Points points={data.choices} />
              </Block>
            ),
          },
          {
            id: "puissance",
            label: "Analyse de puissance",
            tone: "success",
            content: (
              <Block icon="calculator" tone="success" title="Analyse de puissance">
                <table>
                  <thead>
                    <tr>
                      <th>Poste</th>
                      <th>Puissance</th>
                      <th>Remarque</th>
                      <th>Source</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.power.map((line) => (
                      <tr key={line.item}>
                        <td>{line.item}</td>
                        <td className="nowrap">
                          <strong>{line.power}</strong>
                        </td>
                        <td>{line.note ?? ""}</td>
                        <td>
                          <Source source={line.source} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {data.totalPower && (
                  <p className="read-total">
                    Total : <KeyValues text={data.totalPower} />
                  </p>
                )}
              </Block>
            ),
          },
          {
            id: "chantier",
            label: "Analyse du chantier",
            tone: "muted",
            content: (
              <Block icon="building" tone="muted" title="Analyse du chantier">
                <Points points={data.site} />
              </Block>
            ),
          },
          {
            id: "vigilance",
            label: "Points de vigilance",
            tone: "danger",
            content: (
              <Block icon="warning" tone="danger" title="Points de vigilance">
                <Points points={data.vigilance} />
              </Block>
            ),
          },
          ...(choices
            ? [
                {
                  id: "choix",
                  label: "Nos choix techniques",
                  tone: "success" as const,
                  content: (
                    <Block icon="star" tone="success" title="Nos choix techniques (décision GPE)">
                      {choices}
                    </Block>
                  ),
                },
              ]
            : []),
        ]}
      />
    </>
  );
}
