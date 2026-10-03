# GPE-LOGICIEL

Outil interne de veille et d'analyse des appels d'offres publics BTP.

Il collecte les avis du BOAMP, filtre ceux qui correspondent aux critères de l'entreprise
et produit pour chaque dossier retenu une fiche de synthèse dont chaque information
renvoie à sa source (document et page).

## Stack

| Partie          | Technologie                       |
|-----------------|-----------------------------------|
| Frontend        | Next.js, React, TypeScript        |
| Backend         | Java 21, Spring Boot              |
| Base de données | PostgreSQL                        |
| Infrastructure  | Docker, Docker Compose            |

## Structure

```text
backend/    API Spring Boot
frontend/   Interface Next.js
```

Le backend est découpé par module :

| Package       | Rôle                                                                 |
|---------------|----------------------------------------------------------------------|
| `collect`     | Collecte BOAMP planifiée, journal, conversion vers le format commun  |
| `tender`      | Appels d'offres, lots, historique des avis, suivi, clôture           |
| `filter`      | Critères de l'entreprise et filtrage par règles                      |
| `user`        | Comptes, rôles, connexion                                            |

## Configuration

Copier le fichier d'exemple puis renseigner les valeurs :

```bash
cp .env.example .env
```

Le fichier `.env` n'est jamais commité. Au premier démarrage, si aucun compte n'existe,
un administrateur est créé à partir de `BOOTSTRAP_ADMIN_EMAIL` et `BOOTSTRAP_ADMIN_PASSWORD`.

## Lancer l'application complète

```bash
docker compose up -d --build
```

L'interface est disponible sur http://localhost:3000 (port `APP_PORT`). Le backend n'est pas
exposé : l'interface lui transmet les appels `/api`. La base n'écoute que sur la machine locale.

En production, servir l'interface derrière un reverse proxy HTTPS et passer
`SESSION_COOKIE_SECURE=true`.

## Développement

```bash
docker compose up -d postgres

# Backend (http://localhost:8080)
cd backend
BOOTSTRAP_ADMIN_EMAIL=admin@example.com BOOTSTRAP_ADMIN_PASSWORD=change-me-12-chars ./mvnw spring-boot:run

# Frontend (http://localhost:3000), dans un autre terminal
cd frontend
npm install
npm run dev
```

Tests :

```bash
cd backend && ./mvnw test        # Docker doit tourner (Testcontainers)
cd frontend && npm run lint && npx tsc --noEmit
```

## Fonctionnement

1. **Collecte** : deux fois par jour ouvré (7 h et 13 h), le backend interroge l'API open data
   du BOAMP et récupère les avis publiés depuis la dernière collecte réussie (avec un jour de
   recouvrement). Chaque avis est conservé tel quel ; chaque collecte est journalisée. Un
   administrateur peut lancer une collecte depuis les paramètres.
2. **Normalisation** : les avis sont convertis au format commun (objet, acheteur, lieu,
   départements, dates, type, procédure, CPV, montant, lots, liens). Les rectificatifs,
   attributions et annulations sont rattachés à l'avis initial. Une information absente reste
   « Non indiqué ».
3. **Filtrage** : un appel d'offres est retenu s'il respecte tous les critères obligatoires
   (zone, type de marché, délai, montant, mots-clés exclus) et au moins un critère métier
   (code CPV ou mot-clé). La raison est enregistrée et affichée ; les avis écartés restent
   consultables.
4. **Suivi** : chaque appel d'offres peut être classé (à étudier, en réponse, écarté) avec un
   commentaire. Un appel d'offres dont la date limite est dépassée passe au statut « clos ».

Les dates limites sont lues telles que publiées par l'acheteur et interprétées en heure de
Paris : l'heure indexée par l'API est parfois décalée.
