"use client";

import { useState, type FormEvent } from "react";

import { useAuth } from "@/components/AuthProvider";
import { RecordTable } from "@/components/company/RecordTable";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { companyFileUrl, getProfile, updateProfile, uploadLogo } from "@/services/companyApi";
import type { CompanyProfile } from "@/types/company";

type ProfileField = { name: keyof CompanyProfile; label: string; placeholder?: string; wide?: boolean };

/** The form in three short groups rather than one long list. */
const GROUPS: { title: string; fields: ProfileField[] }[] = [
  {
    title: "Identité légale",
    fields: [
      { name: "legalName", label: "Raison sociale", wide: true },
      { name: "tradeName", label: "Nom commercial" },
      { name: "legalForm", label: "Forme juridique", placeholder: "SAS, SARL…" },
      { name: "siren", label: "SIREN", placeholder: "9 chiffres" },
      { name: "siret", label: "SIRET du siège", placeholder: "14 chiffres" },
      { name: "vatNumber", label: "N° de TVA intracommunautaire", placeholder: "FR…" },
      { name: "apeCode", label: "Code APE", placeholder: "43.21A" },
      { name: "rcs", label: "RCS", placeholder: "RCS Marseille" },
      { name: "shareCapital", label: "Capital social (€)" },
    ],
  },
  {
    title: "Siège et coordonnées",
    fields: [
      { name: "addressLine", label: "Adresse", wide: true },
      { name: "postalCode", label: "Code postal" },
      { name: "city", label: "Ville" },
      { name: "country", label: "Pays" },
      { name: "phone", label: "Téléphone" },
      { name: "email", label: "E-mail" },
      { name: "website", label: "Site web" },
    ],
  },
  {
    title: "Zone d'intervention",
    fields: [
      { name: "interventionDepartments", label: "Départements", placeholder: "13, 83, 84…", wide: true },
      { name: "interventionNote", label: "Précisions", wide: true },
    ],
  },
];

function shown(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  return Array.isArray(value) ? value.join(", ") : String(value);
}

/** The identity of GPE, its logo and its contacts. */
export default function CompanyIdentityPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  const profile = useLoader(() => getProfile(), "company-profile");
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body: Record<string, unknown> = {};
    for (const field of GROUPS.flatMap((group) => group.fields)) {
      const value = String(form.get(field.name) ?? "").trim();
      if (field.name === "interventionDepartments") {
        body[field.name] = value ? value.split(/[\s,;]+/).map((code) => code.toUpperCase()) : [];
      } else if (field.name === "shareCapital") {
        body[field.name] = value ? Number(value.replace(/\s/g, "").replace(",", ".")) : null;
      } else if (field.name === "siren" || field.name === "siret") {
        body[field.name] = value.replace(/\s/g, "") || null;
      } else {
        body[field.name] = value || null;
      }
    }
    setBusy(true);
    setError(null);
    try {
      await updateProfile(body);
      setEditing(false);
      profile.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setBusy(false);
    }
  };

  const data = profile.data;

  return (
    <>
      <section className="card">
        <div className="company-identity-head">
          {data?.logo ? (
            // eslint-disable-next-line @next/next/no-img-element -- a file of the company, served by the backend
            <img src={companyFileUrl(data.logo.id)} alt="Logo de l'entreprise" className="company-logo" />
          ) : (
            <div className="company-logo placeholder">Logo</div>
          )}
          <div>
            <h2>{data?.legalName ?? data?.tradeName ?? "Entreprise à renseigner"}</h2>
            {data?.updatedBy && (
              <p className="muted">
                Modifié par {data.updatedBy} le {formatDateTime(data.updatedAt)}
              </p>
            )}
            {admin && (
              <div className="form-actions">
                {!editing && (
                  <button type="button" onClick={() => setEditing(true)}>
                    Modifier
                  </button>
                )}
                <label className="button secondary">
                  Changer le logo
                  <input
                    type="file"
                    accept=".png,.jpg,.jpeg"
                    hidden
                    onChange={async (event) => {
                      const file = event.target.files?.[0];
                      if (!file) {
                        return;
                      }
                      try {
                        await uploadLogo(file);
                        profile.reload();
                      } catch (err) {
                        setError(err instanceof Error ? err.message : "Logo refusé.");
                      }
                    }}
                  />
                </label>
              </div>
            )}
          </div>
        </div>
        {error && <p className="banner error">{error}</p>}
        {profile.error && <p className="banner error">{profile.error}</p>}

        {data && !editing && (
          <div className="company-groups">
            {GROUPS.map((group) => (
              <div key={group.title}>
                <h3>{group.title}</h3>
                <dl className="facts rows">
                  {group.fields.map((field) => (
                    <div key={field.name}>
                      <dt>{field.label}</dt>
                      <dd>{shown(data[field.name]) || <span className="muted">—</span>}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            ))}
          </div>
        )}

        {data && editing && (
          <form onSubmit={save}>
            {GROUPS.map((group) => (
              <fieldset key={group.title}>
                <legend>{group.title}</legend>
                <div className="form-grid">
                  {group.fields.map((field) => (
                    <label key={field.name} className={field.wide ? "wide" : undefined}>
                      {field.label}
                      <input name={field.name} defaultValue={shown(data[field.name])} placeholder={field.placeholder} />
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
            <div className="form-actions">
              <button type="submit" className="primary" disabled={busy}>
                {busy ? "Enregistrement…" : "Enregistrer"}
              </button>
              <button type="button" className="secondary" onClick={() => setEditing(false)} disabled={busy}>
                Annuler
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="card">
        <h2>Interlocuteurs</h2>
        <RecordTable
          list="contacts"
          noun="un interlocuteur"
          empty="Aucun interlocuteur."
          columns={[
            { key: "name", label: "Nom" },
            { key: "role", label: "Fonction" },
            { key: "phone", label: "Téléphone" },
            { key: "email", label: "E-mail" },
          ]}
          fields={[
            { name: "name", label: "Nom", required: true },
            { name: "role", label: "Fonction", placeholder: "Responsable études" },
            { name: "phone", label: "Téléphone" },
            { name: "email", label: "E-mail", type: "email" },
            { name: "notes", label: "Notes", type: "textarea", wide: true },
          ]}
        />
      </section>
    </>
  );
}
