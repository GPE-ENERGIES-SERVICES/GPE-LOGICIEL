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
| `collect`  | Collecte planifiée par source (connecteurs, BOAMP aujourd'hui), avis bruts, journal, normalisation |
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
collecte (`collect.cron`), sources mises de côté (`collect.disabled-sources`), limites de taille des dépôts (`documents.*`), taille maximale du
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

Sources : le BOAMP (`collect/boamp`) et TED (`collect/ted`). Une source se compose de deux beans Spring portant le
même nom de source (par exemple `TED`), sur le modèle de `collect/boamp` :

1. un `SourceConnector` (`BoampConnector`) : son nom, son mode d'accès (`SourceAccess`) et la
   lecture des avis publiés un jour donné, rendus en `SourceRecord` (identifiant dans la source,
   date, contenu en JSON ; une page HTML ou un fichier est rendu en JSON par le connecteur) ;
2. un `SourceParser` (`BoampNoticeParser`) : la lecture d'un avis stocké en `SourceNotice`
   (type d'avis, avis liés de la même source, `TenderDetails`). Une information absente reste
   `null` ou vide.

Rien d'autre à modifier : `CollectService`, `NormalizationService` et `CollectScheduler`
trouvent les connecteurs et les parseurs par injection. Chaque source a son verrou, son point
de départ, son journal (`collect_run.source`) et son bilan ; l'échec d'une source n'arrête pas les
autres. `collect.disabled-sources=TED` met une source de côté.

Modes d'accès : `AUTOMATED` (API ou open data publics) et `AUTHENTICATED` (compte ou clé de
l'entreprise, fournis par l'environnement) sont collectés ; `MANUAL` (export déposé par une
personne) et `NOT_AUTOMATABLE` (CAPTCHA, protection anti-robot) ne sont jamais interrogés. Aucune
protection n'est contournée ; une source qui refuse l'accès fait échouer son run, retenté au
créneau suivant.

Identité : `source + external_id`, pour les avis bruts comme pour les consultations ; un avis
n'est rattaché qu'à une consultation de sa source. Deux sources qui publient le même marché
donnent deux consultations : leur rapprochement (règles déterministes, validation humaine)
reste à concevoir.

Identifiant de procédure : un parseur donne dans `SourceNotice.procedureId` l'identifiant eForms
de la procédure (BOAMP `contractfolderid`, TED `procedure-identifier`), identique pour tous les
avis d'une consultation quelle que soit la source. Il est conservé en minuscules dans
`tender.procedure_id` (V16) ; le premier connu n'est jamais remplacé (un désaccord est écrit dans
les journaux). V16 l'a renseigné pour les consultations BOAMP déjà collectées dont les avis
donnent un seul identifiant (4 230 sur 7 378 au 12/10/2026, les autres avis n'étant pas au format
eForms).

`scripts/report-shared-procedures.sql` (lecture seule) liste les consultations d'une même source
qui partagent un identifiant de procédure, avec les données de GPE qui y sont attachées. Au
12/10/2026 : 70 procédures (143 consultations BOAMP), aucune donnée de GPE. Cause : des avis de
modification eForms (`ChangedNoticeIdentifier`) que le BOAMP publie avec `etat = INITIAL` et que
`BoampNoticeParser` lit donc comme des avis initiaux. Ni correction du parseur ni fusion sans
validation.

### TED

TED (Tenders Electronic Daily, Office des publications de l'UE) est lu par l'API Search v3
(`POST https://api.ted.europa.eu/v3/notices/search`, `ted.base-url`), ouverte sans compte ni clé
pour les avis publiés ; la politique d'usage de TED (700 requêtes par minute) est très
au-dessus d'une collecte (une requête par 250 avis, environ 80 à 300 avis français par jour
ouvré). `TedClient` demande les avis des acheteurs français (`buyer-country=FRA`) jour de
publication par jour, avec les seuls champs lus par `TedNoticeParser` (`TedClient.FIELDS`) ;
les liens vers l'avis ne sont gardés qu'en français et en XML multilingue (un avis stocké pèse
environ 2 Ko au lieu de 6).

Lecture (`TedNoticeParser`) :

- type : `cn-standard`, `cn-social`, `cn-desg` = appel d'offres ; avec
  `change-notice-version-identifier` = rectificatif complet (eForms répète tout l'avis), ou
  annulation si `change-reason-code = cancel` ; `can-*` = attribution ; le reste (`pin-*`,
  `veat`, `can-modif`…) est ignoré ;
- rattachement : l'avis modifié est cité tantôt par son numéro de publication, tantôt par son
  identifiant eForms (`uuid-01`) ; TED est donc la seule source dont les avis se rattachent
  aussi par identifiant de procédure (`SourceParser.linksNoticesByProcedure`), dans la même
  source et seulement s'il n'y a qu'une consultation candidate ;
- départements : codes postaux du lieu d'exécution, sinon ses régions NUTS 3
  (`NutsDepartments`, table NUTS 2024 d'Eurostat rapprochée du COG de l'INSEE : 101 régions,
  101 départements), sinon code postal de l'acheteur ;
- date limite : la plus proche des lots pour les offres, sinon (procédure restreinte ou
  négociée) pour les candidatures ;
- plateforme : `document-url-lot`, sinon `submission-url-lot`, sinon le profil d'acheteur.

Vérification réelle du 12/10/2026 (base jetable) : 5 jours, 1 198 avis, 0 erreur,
754 consultations, toutes avec titre, département et identifiant de procédure, 2 sans date
limite (absente de l'avis). Limite : une attribution dont l'appel d'offres n'a pas été collecté
reste en attente (424 sur ces 5 jours, consultations plus anciennes) et ne sera appliquée que
si cet appel d'offres est importé et cité par son numéro de publication.

TED est mis de côté par `collect.disabled-sources=TED` tant que ses avis ne sont pas
rapprochés des consultations BOAMP de la même procédure (80 % des procédures françaises de TED
sont aussi au BOAMP).

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

## Cycle de vie des données

```
Source (BOAMP : API open data, un jour de parution à la fois)   SourceConnector (BoampConnector → BoampClient)
  → avis bruts, JSON intact                             raw_notice, unique (source, external_id)
  → format commun                                       SourceParser (BoampNoticeParser) → SourceNotice
  → création ou rattachement à la consultation          collect/NormalizationService, par source
  → consultations, lots, historique des avis            tender (unique source, external_id), lot, tender_notice
  → critères de l'entreprise, clôture des échéances     filter/FilterService, tender/TenderLifecycleService
```

Orchestration : `collect/CollectService`, déclenché par `CollectScheduler` (`collect.cron`, 7 h et
13 h les jours ouvrés : chaque source active l'une après l'autre), par le bouton des paramètres
(toutes les sources actives) ou par `POST /api/collect-runs?source=BOAMP&since=AAAA-MM-JJ`
(administrateur, `scripts/import-boamp.sh`). Une seule collecte à la fois par source. Une source
jamais collectée commence `collect.initial-days` jours en arrière (7).

- **Doublons** : un avis BOAMP (`idweb`) n'est stocké qu'une fois (`INSERT … ON CONFLICT DO
  NOTHING`), une consultation qu'une fois par (source, identifiant de l'avis initial), un avis
  n'est rattaché qu'une fois. Les contraintes d'unicité sont dans PostgreSQL ; réimporter une
  période ne crée rien.
- **Mises à jour** : le BOAMP ne modifie pas un avis publié, il publie un nouvel avis lié à
  l'initial. Un rectificatif change la date limite, un rectificatif complet les informations
  qu'il donne (une information absente n'efface jamais la précédente) ; l'avis le plus récent
  prévaut, les dates annoncées restent dans l'historique des publications. Une attribution
  clôt la consultation, une annulation l'annule.
- **Données de GPE** : l'import n'écrit que les informations publiées. Le statut de l'équipe, son
  commentaire, les suivis (`tender_follow`), les DCE déposés et les analyses ne sont jamais
  modifiés par un import. Une nouvelle consultation commence au statut « Nouveau ».
- **Rien n'est supprimé** : une consultation passe à `CLOSED` (date limite dépassée ou
  attribution) ou `CANCELLED` et reste consultable dans les Archives.
- **Avis en attente** : un rectificatif ou une attribution dont l'avis initial n'a pas été
  collecté est mis de côté (« Initial notice not collected ») et appliqué dès que cet avis
  initial est importé, par exemple par un chargement initial plus ancien. Les avis liés lus par
  le parseur sont conservés dans `raw_notice.linked_ids` : la reprise ne relit pas le format de
  la source. Un avis relu (reprise, rejeu) reste rattaché à sa consultation.
- **Rectificatif avant l'avis initial** : un rectificatif complet peut être reçu avant son avis
  initial ; il crée alors la consultation. L'identifiant de l'avis initial, cité par le
  rectificatif (`linked_ids`, même source), permet ensuite de rattacher l'avis initial à cette
  consultation sans créer de doublon : il ne fait que compléter les informations absentes et
  donne la date de publication. Les doublons créés avant ce correctif ont été fusionnés par la
  migration V14 (13 paires), qui refuse et signale toute paire non conforme.
- **Limite connue** : une chaîne de rectificatifs complets reçus dans le désordre (un rectificatif
  qui en cite un autre, plus ancien, reçu après lui) donne encore deux consultations ; 3 cas
  laissés en l'état par V14, sans statut, suivi ni DCE.
- **Erreurs** : un avis sans identifiant est écarté sans arrêter la collecte ; un avis illisible
  est noté en erreur et retenté à la collecte suivante. Si une source ne répond pas (délais
  `spring.http.clients.*`) ou renvoie une erreur serveur (5xx), la requête est refaite une seule
  fois après `collect.retry-delay` (10 s, `collect/HttpRetry`) ; un refus (4xx) n'est pas répété.
  Si l'échec persiste, la collecte de cette source échoue sans avancer son point de départ : la
  suivante reprend les mêmes jours. Aucune boucle de reprise.
- **Bilan** : chaque collecte enregistre dans `collect_run` (V15) ses avis reçus, nouveaux,
  déjà connus (doublons ignorés), écartés sans identifiant, les consultations créées et mises à
  jour, les avis ignorés et en erreur, son début et sa fin (normalisation comprise). La
  normalisation reprend aussi les avis de la source laissés en attente par les collectes
  précédentes. Les collectes antérieures à V15 ont ces compteurs à 0 (leur bilan est dans les
  journaux). La même ligne est écrite dans les journaux : `BOAMP import finished in … s: …`.
  `GET /api/collect-runs/sources` donne pour chaque source son mode d'accès, si elle est
  collectée, sa dernière collecte et sa dernière collecte réussie.

## Journaux

- Collectes : Paramètres → Journal de collecte (état de chaque source, dernière collecte
  réussie ; puis chaque collecte avec son bilan) ; détail : `docker compose logs backend | grep
  "import"`.
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
