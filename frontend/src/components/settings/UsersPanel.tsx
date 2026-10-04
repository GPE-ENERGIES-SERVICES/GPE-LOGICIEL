"use client";

import { useState, type FormEvent } from "react";

import { useLoader } from "@/lib/useLoader";
import { createUser, getUsers, updateUser } from "@/services/settingsApi";
import type { Role, User } from "@/types/user";

const ROLE_LABELS: Record<Role, string> = { USER: "Utilisateur", ADMIN: "Administrateur" };

function UserRow({ user, onSaved }: { user: User; onSaved: () => void }) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [role, setRole] = useState<Role>(user.role);
  const [enabled, setEnabled] = useState(user.enabled);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const changed =
    displayName !== user.displayName || role !== user.role || enabled !== user.enabled || password !== "";

  const save = async () => {
    setError(null);
    try {
      await updateUser(user.id, { displayName, role, enabled, password: password || null });
      setPassword("");
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    }
  };

  return (
    <tr className={user.enabled ? "" : "row-muted"}>
      <td>
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} aria-label="Nom" />
      </td>
      <td>{user.email}</td>
      <td>
        <select value={role} onChange={(e) => setRole(e.target.value as Role)} aria-label="Rôle">
          {(Object.keys(ROLE_LABELS) as Role[]).map((value) => (
            <option key={value} value={value}>
              {ROLE_LABELS[value]}
            </option>
          ))}
        </select>
      </td>
      <td>
        <label className="inline">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          Actif
        </label>
      </td>
      <td>
        <input
          type="password"
          placeholder="Nouveau mot de passe"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-label="Nouveau mot de passe"
        />
      </td>
      <td>
        <button type="button" onClick={save} disabled={!changed}>
          Enregistrer
        </button>
        {error && <p className="error">{error}</p>}
      </td>
    </tr>
  );
}

function CreateUserForm({ onCreated }: { onCreated: () => void }) {
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError(null);
    try {
      await createUser({
        email: String(form.get("email")),
        displayName: String(form.get("displayName")),
        password: String(form.get("password")),
        role: form.get("role") as Role,
      });
      formElement.reset();
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Création impossible.");
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <input name="displayName" placeholder="Nom" required maxLength={100} />
      <input name="email" type="email" placeholder="Adresse e-mail" required />
      <input
        name="password"
        type="password"
        placeholder="Mot de passe (12 caractères min.)"
        autoComplete="new-password"
        minLength={12}
        required
      />
      <select name="role" defaultValue="USER" aria-label="Rôle">
        {(Object.keys(ROLE_LABELS) as Role[]).map((value) => (
          <option key={value} value={value}>
            {ROLE_LABELS[value]}
          </option>
        ))}
      </select>
      <button type="submit" className="primary">
        Créer le compte
      </button>
      {error && <p className="error">{error}</p>}
    </form>
  );
}

export function UsersPanel() {
  const { data, error, reload } = useLoader(() => getUsers(), "users");

  return (
    <div>
      <CreateUserForm onCreated={reload} />
      {error && <p className="error">{error}</p>}
      {data && (
        <table>
          <thead>
            <tr>
              <th>Nom</th>
              <th>E-mail</th>
              <th>Rôle</th>
              <th>État</th>
              <th>Mot de passe</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.map((user) => (
              <UserRow key={`${user.id}-${user.displayName}-${user.role}-${user.enabled}`} user={user} onSaved={reload} />
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
