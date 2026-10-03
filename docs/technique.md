# Documentation technique

## Architecture

Une application web, un serveur applicatif, une base PostgreSQL, un dossier de stockage des
fichiers et un appel à un service d'IA externe. Pas de microservices ni de file de messages :
les traitements longs (collecte, lecture des documents, analyse) tournent en tâche de fond
dans le backend, avec un état visible dans l'interface.

```text
Navigateur ──> frontend (Next.js) ──/api──> backend (Spring Boot) ──> PostgreSQL
                                              │
                                              ├──> API open data du BOAMP
                                              ├──> stockage des fichiers (/data/storage)
                                              └──> fournisseur d'IA (ExtractionClient)
```

| Package du backend | Rôle |
|--------------------|------|
| `collect`  | Collecte planifiée du BOAMP, avis bruts, journal, normalisation |
| `tender`   | Appels d'offres, lots, historique des avis, suivi, clôture automatique |
| `filter`   | Critères de l'entreprise, filtrage par règles et raison enregistrée |
| `document` | Dépôt du DCE, décompression sécurisée, classement, texte page par page |
| `analysis` | Grille d'extraction, appel à l'IA, vérification des citations, synthèse, validation |
| `user`     | Comptes, rôles (utilisateur, administrateur), session |

Chaîne de traçabilité : information extraite → extrait cité → page → document → dossier →
appel d'offres. Une analyse n'écrase jamais la précédente ; les décisions des utilisateurs
conservent la valeur de l'IA et leur auteur.

### Déroulement d'une analyse

1. La lecture d'un dossier se termine (`DocumentProcessor`) et publie `DossierReadEvent`.
2. Si l'appel d'offres est retenu par le filtre et qu'un fournisseur d'IA est configuré,
   `AnalysisService` choisit les pièces à envoyer : RC, avis, acte d'engagement puis autres
   pièces, dans la limite de `analysis.max-characters`. Une pièce qui ne tient pas est
   laissée de côté entière (jamais coupée) et signalée « non analysée ».
3. L'empreinte des pièces, du modèle et de la version des instructions est calculée. Si une
   analyse terminée a la même empreinte, elle est réutilisée (règle RM9) ; le bouton
   « Relancer l'analyse » force une nouvelle analyse.
4. `ExtractionPrompt` construit les instructions (à partir de `ExtractionGrid`) et le texte des
   pages, chaque page portant l'identifiant du document et son numéro.
5. `AnswerReader` lit la réponse JSON sans lui faire confiance : clés inconnues ignorées,
   sources hors des documents envoyés supprimées, champs sans réponse gardés « à vérifier ».
6. `AnswerChecker` et `CitationVerifier` retrouvent chaque extrait dans la page citée (ou
   la suivante, ou ailleurs dans le document, auquel cas la page est corrigée) et vérifient
   que les dates et montants figurent dans l'extrait. Seules ces informations passent
   « vérifiée ». Une date limite différente de celle de l'avis passe « contradictoire » (RM11).
7. Les informations, les tokens consommés et le statut sont enregistrés. Un échec est
   enregistré avec son message et visible dans la fiche et dans le journal des analyses.

## Installation

Prérequis : Docker et Docker Compose sur le serveur.

```bash
git clone <dépôt> gpe-logiciel && cd gpe-logiciel
cp .env.example .env      # puis renseigner les valeurs (voir ci-dessous)
docker compose up -d --build
```

L'interface écoute sur le port `APP_PORT`. La servir derrière un reverse proxy HTTPS
(nginx, Caddy, Traefik) limité au réseau de l'entreprise si possible, et passer
`SESSION_COOKIE_SECURE=true`. Le backend n'est pas exposé et la base n'écoute que sur la
machine locale.

Mise à jour :

```bash
git pull
docker compose up -d --build
```

Les migrations de la base (Flyway, `backend/src/main/resources/db/migration`) s'appliquent au
démarrage du backend.

## Configuration

Toutes les valeurs sont lues dans `.env`, jamais commité.

| Variable | Rôle |
|----------|------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | Base de données |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_NAME` | Premier administrateur, créé si aucun compte n'existe |
| `SESSION_COOKIE_SECURE` | `true` derrière HTTPS |
| `APP_PORT` | Port de l'interface |
| `ANALYSIS_INPUT_PRICE`, `ANALYSIS_OUTPUT_PRICE` | Prix du modèle en euros par million de tokens, pour afficher le coût des analyses (0 = non affiché) |
| `BACKUP_DIR`, `BACKUP_TIME`, `BACKUP_KEEP_DAYS` | Sauvegarde quotidienne |

Autres réglages dans `backend/src/main/resources/application.properties` : horaires de
collecte (`collect.cron`), limites de taille des dépôts (`documents.*`), taille maximale du
texte envoyé à l'IA (`analysis.max-characters`).

Les critères de filtrage se règlent dans l'interface (Paramètres, administrateur).

## Brancher un fournisseur d'IA

Le choix du fournisseur revient à l'entreprise : traitement en France ou dans l'Union
européenne, engagement contractuel de non-réutilisation des données pour l'entraînement,
plafond de dépense mensuel configuré chez le fournisseur.

Tout l'appel à l'IA passe par l'interface `analysis.ExtractionClient`, seul point à écrire :

```java
@Component
class MonFournisseurClient implements ExtractionClient {

	@Override
	public String model() {
		return "nom-du-modèle";       // enregistré avec chaque analyse
	}

	@Override
	public ModelAnswer complete(String instructions, String documents) {
		// Envoyer les instructions (consignes système) et les documents (message
		// utilisateur), demander une réponse JSON, renvoyer le texte de la réponse
		// et les tokens consommés (entrée, sortie).
	}

}
```

- La clé d'API se lit dans une variable d'environnement (à ajouter dans `.env.example` et
  `docker-compose.yml`), jamais dans le code.
- Dès qu'un tel composant existe, il remplace le client « non configuré » déclaré dans
  `AnalysisConfiguration`, et l'interface propose l'analyse.
- `model()` peut renvoyer `null` pour désactiver l'analyse (par exemple si la clé est absente).
- Les tests de `AnalysisApiTests` montrent le comportement attendu avec un faux client.

Changer de modèle ne demande aucune autre modification : les analyses gardent le nom du
modèle et la version des instructions, ce qui permet de comparer.

## Ajouter ou modifier une information de la grille

1. Ajouter ou modifier l'entrée dans `analysis.ExtractionGrid.FIELDS` : clé (50 caractères
   max), libellé, section de la fiche, forme de la valeur, question posée au modèle.
2. Changer `ExtractionGrid.VERSION` : les analyses restent comparables par version.
3. Aucune migration : chaque information est une ligne de `extracted_field`.
4. Mesurer l'effet sur les dossiers de référence (journal et qualité dans les paramètres).

Les points d'attention calculés par le code (visite obligatoire, délai court, qualifications,
date limite contradictoire) sont dans `frontend/src/lib/analysis.ts`.

## Ajouter une source d'avis

La V1 ne lit que le BOAMP. Pour une autre source (TED par exemple) :

1. Écrire un client et un parseur sur le modèle de `collect/boamp` qui produisent des
   `ParsedNotice` (format commun).
2. Conserver les avis bruts dans `raw_notice` avec un nom de source distinct.
3. Planifier la collecte comme `CollectScheduler` ; la normalisation, le filtrage et le reste
   de la chaîne sont communs. Le rapprochement entre sources reste à concevoir.

## Sauvegarde et restauration

Le service `backup` (image PostgreSQL) sauvegarde chaque jour à `BACKUP_TIME` (heure de
Paris) :

- la base : `database_AAAA-MM-JJ_HHMM.dump` (format `pg_dump` personnalisé) ;
- les fichiers déposés : `files_AAAA-MM-JJ_HHMM.tar.gz`.

Les sauvegardes de plus de `BACKUP_KEEP_DAYS` jours sont supprimées. `BACKUP_DIR` doit être
un dossier copié hors du serveur (partage réseau, dossier synchronisé, copie nocturne) : une
sauvegarde restée sur le serveur ne protège pas d'une panne de disque.

Sauvegarde immédiate :

```bash
docker compose exec backup sh /scripts/backup.sh
```

Restauration :

```bash
docker compose stop backend
docker compose exec backup sh /scripts/restore.sh 2026-10-03_0200
docker compose start backend
```

La restauration remplace la base et les fichiers par ceux de la sauvegarde. La tester au
moins une fois avant la mise en service (critère de recette).

Suivi : `docker compose logs backup` affiche chaque sauvegarde et les échecs
(`BACKUP FAILED`).

## Journaux

- Collectes : Paramètres → Journal de collecte (date, avis reçus, nouveaux, erreurs).
- Analyses : Paramètres → Analyses par IA (statut, modèle, tokens, coût, erreurs) et qualité
  par information à partir des décisions des utilisateurs.
- Journal technique : `docker compose logs backend`.

## Tests

```bash
cd backend && ./mvnw test        # Docker doit tourner (Testcontainers)
cd frontend && npm run lint && npx tsc --noEmit
```

Les règles où une erreur serait silencieuse sont couvertes par des tests automatisés :
filtrage, normalisation, rattachement des rectificatifs, lecture des documents, vérification
des citations, lecture de la réponse de l'IA.
