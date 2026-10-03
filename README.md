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
scripts/    Sauvegarde et restauration
docs/       Documentation technique et guide utilisateur
```

Le backend est découpé par module :

| Package       | Rôle                                                                      |
|---------------|---------------------------------------------------------------------------|
| `collect`     | Collecte BOAMP planifiée, journal, conversion vers le format commun       |
| `tender`      | Appels d'offres, lots, historique des avis, suivi, clôture                |
| `filter`      | Critères de l'entreprise et filtrage par règles                           |
| `document`    | Dépôt du DCE, décompression, classement des pièces, texte page par page   |
| `analysis`    | Grille d'extraction, appel à l'IA, vérification des citations, validation |
| `user`        | Comptes, rôles, connexion                                                 |

Documentation :

- [Documentation technique](docs/technique.md) : architecture, installation, configuration,
  sauvegarde et restauration, ajout d'une source, d'un champ ou d'un fournisseur d'IA.
- [Guide utilisateur](docs/guide-utilisateur.md) : parcours, signification des états, limites.

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

Le service `backup` sauvegarde chaque nuit la base et les fichiers déposés dans `BACKUP_DIR`
(voir [la documentation technique](docs/technique.md#sauvegarde-et-restauration)).

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
5. **Documents** : l'utilisateur dépose le DCE (ZIP ou fichiers). Les archives sont
   décompressées, chaque pièce est classée d'après son nom (corrigeable), le texte des PDF et
   des documents Word est extrait page par page. Un document illisible est signalé « non
   analysé ».
6. **Analyse** : pour un appel d'offres retenu, la lecture terminée lance l'analyse. Le
   règlement de consultation, l'avis et l'acte d'engagement sont envoyés au modèle d'IA, qui
   remplit une grille fixe d'une vingtaine d'informations en citant l'extrait, le document et
   la page. Le code retrouve chaque extrait dans la page citée : seule une information dont
   l'extrait est retrouvé est marquée « source vérifiée ». Un contenu déjà analysé n'est pas
   réanalysé sans demande explicite.
7. **Validation** : sur la fiche de synthèse, l'utilisateur valide, corrige ou rejette chaque
   information (la valeur de l'IA et l'auteur sont conservés), puis valide la fiche.

Tant qu'aucun fournisseur d'IA n'est branché, l'étape 6 est indisponible et l'interface le
signale ; voir la documentation technique.

Les dates limites sont lues telles que publiées par l'acheteur et interprétées en heure de
Paris : l'heure indexée par l'API est parfois décalée.
