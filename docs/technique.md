# Documentation technique

## Architecture

Une application web, un serveur applicatif, une base PostgreSQL, un dossier de stockage des
fichiers et, plus tard, un appel à un service d'IA externe (préparé, désactivé : aucun
fournisseur n'est intégré ni configuré, aucun document n'est envoyé à une IA). Pas de
microservices ni de file de messages :
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

Le même chemin pour tout dossier, quelle que soit son origine (dépôt manuel, récupération
automatique) : seule compte la pièce, son empreinte et son texte. L'origine reste dans
`dossier.origin` sans changer l'analyse.

1. La lecture d'un dossier se termine (`DocumentProcessor`) et publie `DossierReadEvent`. Le
   texte est extrait ici et nulle part ailleurs : l'IA ne lit jamais les fichiers.
2. Si l'analyse est autorisée (`AnalysisGate`, voir « Analyse par IA ») et que l'appel d'offres
   est retenu par le filtre (RM8), ou sur demande d'un utilisateur, `AnalysisService` choisit
   les pièces : RC, avis, acte d'engagement, CCAP, CCTP puis autres pièces, dans la limite de
   `analysis.max-characters` et de `analysis.max-documents-per-tender`. Une pièce qui ne tient
   pas est laissée de côté entière (jamais coupée) et signalée « non analysée ».
3. L'empreinte des pièces (SHA-256 et type), du fournisseur, du modèle et de la version de la
   grille et des instructions est calculée. Si une analyse terminée a la même empreinte, elle
   est réutilisée (règle RM9) ; le bouton « Relancer l'analyse » force une nouvelle analyse. Une
   nouvelle version d'un document, de la grille ou des instructions donne une autre empreinte.
4. L'analyse est mise en file (`PENDING`) ; la requête HTTP rend la main aussitôt.
   `AnalysisWorker` la prend sur ses propres threads, redemande `AnalysisGate`, puis
   `ExtractionPrompt` construit les instructions (à partir de `ExtractionGrid`), le schéma JSON
   attendu et le texte des pages, chaque page portant l'identifiant du document et son numéro.
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
| `ANALYSIS_ENABLED`, `ANALYSIS_PROVIDER`, `ANALYSIS_EXTERNAL_TRANSMISSION` | Analyse par IA : `false`, `none`, `false` par défaut, rien n'est envoyé (voir « Analyse par IA ») |
| `ANALYSIS_API_KEY` | Clé du fournisseur d'IA, lue par son seul client ; vide (aucun fournisseur intégré) |
| `ANALYSIS_MONTHLY_BUDGET` | Plafond mensuel estimé en euros (vide = aucun) |
| `ANALYSIS_INPUT_PRICE`, `ANALYSIS_OUTPUT_PRICE` | Prix du modèle en euros par million de tokens, pour afficher le coût des analyses (0 = non affiché) |
| `BACKUP_DIR`, `BACKUP_TIME`, `BACKUP_KEEP_DAYS` | Sauvegarde quotidienne |

Autres réglages dans `backend/src/main/resources/application.properties` : horaires de
collecte (`collect.cron`), sources mises de côté (`collect.disabled-sources`), limites de taille des dépôts (`documents.*`), taille maximale du
texte envoyé à l'IA (`analysis.max-characters`).

Les critères de filtrage se règlent dans l'interface (Paramètres, administrateur).

## Analyse par IA : socle préparé, désactivé

**État actuel : aucun fournisseur d'IA n'est intégré à l'application ni configuré. Aucun
document n'est envoyé à une IA, aucune dépense n'est possible.** L'interface l'indique
(« préparée mais désactivée ») et les documents restent consultables normalement. Tout le reste
de la chaîne (extraction, file, contrôles, fiche, validation) est prêt et testé avec un
fournisseur simulé.

### Composants (`backend/.../analysis`)

| Classe | Rôle |
|--------|------|
| `ExtractionClient` | Contrat d'un fournisseur : `provider()`, `model()`, `complete(ExtractionRequest)`. Seul endroit où un SDK ou une API de fournisseur peut apparaître |
| `ExtractionRequest` | Ce qui est envoyé : type d'analyse, version, instructions, texte page par page, schéma JSON attendu, longueur maximale de la réponse, température 0, délai maximal |
| `ProviderException`, `FailureKind` | Erreurs typées : `NOT_CONFIGURED`, `AUTHENTICATION`, `RATE_LIMITED`, `QUOTA_EXCEEDED`, `BUDGET_REACHED`, `TIMEOUT`, `TEMPORARY`, `INVALID_RESPONSE`, `UNUSABLE_DOCUMENT`, `REFUSED`, `CONTEXT_TOO_LARGE`, `INTERRUPTED`, `UNEXPECTED` ; seules `RATE_LIMITED`, `TIMEOUT` et `TEMPORARY` sont retentées |
| `AnalysisGate` | Seul point qui décide qu'un document peut partir, à la demande et juste avant chaque appel |
| `AnalysisService` | Choix des pièces, empreinte, mise en file, annulation ; aucun appel |
| `AnalysisWorker` | File persistante : prise d'une analyse, appel, lecture, vérification, enregistrement, reprises |
| `ExtractionPrompt`, `ExtractionGrid` | Instructions et grille versionnées (`grid-3/prompt-4`), schéma JSON de la réponse (grille et aide à la décision), bloc `<entreprise>` tiré des critères de veille |
| `AnswerReader`, `AnswerChecker`, `CitationVerifier` | Lecture stricte de la réponse et vérification des citations, sans IA |
| `Decision`, `DecisionReader` | Aide à la décision lue dans la même réponse : verdict, arguments, obstacles, vérifications, limites. Chaque extrait est recherché dans le document ; un extrait non retrouvé est écarté et un obstacle sans extrait retrouvé, ou déduit, n'est jamais « confirmé ». Stockée en JSON (colonne `analysis.decision`, V26) ; nulle pour les analyses antérieures, jamais recalculée à l'affichage |
| `SpreadsheetExtractor`, `SpreadsheetText` (document) | Lecture des tableurs XLS/XLSX (Apache POI), ODS (StAX, sans DTD ni entité externe) et CSV (séparateur et encodage détectés) : cellule par cellule, avec coordonnées, type, formule et valeur enregistrée (jamais recalculée, « valeur non enregistrée » sinon). Limites : 20 Mo, 200 Mo décompressés, 50 feuilles, 20 000 lignes, 200 colonnes, 300 000 cellules ; au-delà la feuille est coupée et le signale. Pages de méthode `SHEET` : une ligne par rangée (« L12 | B12 "…" | F12 1860 [formule D12*E12] ») |
| `DocumentTriage`, `AiChoice` (document) | Décide pour chaque document de chaque DCE s'il part à l'IA et pourquoi, par des règles générales : un fichier identique (même empreinte) une seule fois ; le choix d'un utilisateur prime (`PUT /api/documents/{id}/ai-choice`, colonne `document.ai_choice`, V29) ; DPGF, BPU et DQE vers l'analyse financière ; plans et dessins jamais (type PLAN, mots du nom : détail, coupe, façade, carnet, calepinage…, ou moins de 300 caractères par page) ; pièces essentielles d'abord (RC, avis, AE, CCAP, CCTP), puis documents utiles d'après leur nom (diagnostic, rapport, étude, PGC, planning…) ; documentation générique et pages lues seulement par OCR s'il reste de la place. La sélection remplit ensuite le volume de texte permis (`analysis.max-characters`, qui fait le coût). Les mêmes règles décident quelles pages scannées valent une lecture par OCR. Un document scanné ajouté à la main est relu par OCR sans lancer d'analyse |
| `OcrEngine`, `OcrProperties` (document) | Pages sans texte lues par Tesseract (Apache 2.0, langue `fra`), installé dans l'image du backend et appelé en ligne de commande comme LibreOffice : la page est rendue en image par PDFBox (300 dpi), Tesseract rend chaque mot avec sa position et sa confiance (sortie TSV), les traits verticaux des tableaux lus comme « | » sont retirés. Pages stockées avec la méthode `OCR` et leurs mots (colonne `document_page.words`, V28) ; état du document « N page(s) lue(s) par OCR, confiance moyenne X % : texte à vérifier ». Jamais sur les plans (type PLAN, pages plus grandes qu'un A3) ; 40 pages au plus par document ; sans Tesseract, les pages restent non lues comme avant. Rien n'est envoyé à l'extérieur |
| `PdfTableExtractor`, `PdfTable`, `PdfTableText` (document) | Tableaux des PDF numériques reconstruits avec PDFBox à partir de la position des mots : lignes regroupées par hauteur, en-tête sur une ou plusieurs lignes, bords des colonnes placés dans l'espace vide entre les valeurs, chaque mot dans sa colonne ; ambiguïté signalée (valeur à cheval sur deux colonnes, deux valeurs dans une colonne de valeurs, valeur mal alignée) au lieu d'être devinée ; tableau suivi sur les pages suivantes avec ou sans en-tête répété. Les pages lues par OCR donnent leurs mots stockés : un tableau qui en contient n'est jamais « fiable » (avertissement en tête) et un nombre lu avec une confiance inférieure à 80 % rend sa cellule ambiguë. Les titres et libellés de sous-totaux qui débordent restent un seul texte ; les désignations sur plusieurs lignes sont regroupées avec la ligne qui porte l'unité et la quantité. Convertis en feuilles pour les mêmes contrôles que les tableurs, et transmis à l'IA dans `<tableaux_pdf>` avec page et position de chaque ligne. Aucun stockage : recalculé au lancement de l'analyse financière. Le texte des pages (`PdfTextExtractor`) n'est pas modifié |
| `PriceScheduleChecker`, `PriceSchedules`, `PriceCheck` | Contrôles des pièces de prix sans IA : en-tête reconnu (désignation, unité, quantité, prix unitaire, montant ; noms variables), quantité × prix unitaire contre montant, totaux et sous-totaux contre la somme des lignes, HT + TVA contre TTC, prix en toutes lettres, cadres vierges, formules sans valeur, cellules en erreur |
| `FinancialPrompt`, `FinancialReader`, `FinancialResult` | Analyse financière (`AnalysisKind.FINANCIAL`, V27), lancée par un utilisateur seulement (`POST /api/dossiers/{id}/financial-analyses`, lecture `GET /api/tenders/{id}/financial-analysis`) : DPGF, BPU et DQE seuls, avec les contrôles du code ; chaque point cité est recherché dans la rangée (tableur) ou la page (PDF) indiquée. Même file, même garde (budget, autorisation) et même client que la fiche de synthèse ; une analyse non terminée au plus par marché et par type |
| `SecretRedactor` | Masque clés et en-têtes dans les messages d'erreur avant enregistrement et journal |

Aucun SDK de fournisseur n'est une dépendance du projet ; `ProviderIsolationTests` vérifie
qu'aucun n'est importé, que seul le package `analysis` connaît le contrat, et qu'aucune
implémentation réelle n'est livrée (donc qu'aucun test ne peut appeler une IA).

### Conditions avant tout envoi (`AnalysisGate`)

Vérifiées dans l'ordre ; la première qui manque garde l'analyse éteinte, avec la raison
affichée à l'administrateur (Paramètres) et dans la fiche :

| État | Cause |
|------|-------|
| `DISABLED` | `analysis.enabled=false` (défaut) |
| `MISCONFIGURED` | fournisseur non choisi, inconnu ou en double, plafond sans prix du modèle |
| `NOT_CONFIGURED` | le fournisseur choisi n'a pas de clé ou de modèle (`model()` renvoie `null`) |
| `TRANSMISSION_NOT_ALLOWED` | `analysis.external-transmission-allowed=false` (défaut) : une clé seule n'autorise jamais l'envoi |
| `BUDGET_REACHED` | plafond mensuel atteint (estimation locale) |
| `READY` | une analyse peut être lancée |

Les valeurs hors limites (zéro, négatives, `lease` ≤ `request-timeout`) empêchent le
démarrage ; une combinaison incohérente laisse démarrer l'application, IA éteinte, avec un
avertissement au démarrage (`AI analysis: MISCONFIGURED, kept off: …`).

### File (`analysis`, V24)

Chaque ligne d'`analysis` est à la fois la demande et son historique (jamais remplacée).

| Statut | Sens |
|--------|------|
| `PENDING` | en file |
| `PROCESSING` | envoyée au fournisseur (verrou `locked_by`, `locked_at`) |
| `DONE` | réponse lue, vérifiée, enregistrée ; les pièces non lues sont listées « non analysées » (résultat partiel) |
| `RETRY_WAITING` | erreur temporaire, nouvelle tentative après `next_attempt_at` |
| `FAILED` | échec définitif, avec `error_kind` et message |
| `CANCELLED` | annulée par un utilisateur avant l'envoi |

L'état des pièces reste celui des documents : `PENDING` (à lire), `READ`, `SCANNED` (pages sans
texte : OCR nécessaire, non fait), `NOT_ANALYZED` (format non pris en charge, raison donnée),
`FAILED` (illisible, protégé, conversion impossible).

- **Idempotence** : une seule analyse non terminée par appel d'offres (index unique partiel
  `uk_analysis_unfinished_tender`) ; même empreinte terminée → réutilisée.
- **Verrouillage** : `SELECT … FOR UPDATE SKIP LOCKED`, demandes d'un utilisateur avant les
  demandes automatiques, puis les plus anciennes.
- **Concurrence** : `analysis.max-concurrent-requests` (1) threads `analysis-*`, distincts des
  collectes, de la récupération des DCE et des requêtes web.
- **Reprises** : `retry-delay` (2 min) doublé à chaque tentative, au plus `max-retry-delay`
  (1 h), ±25 % au hasard ; au plus `max-attempts` (3). Une clé refusée, une configuration
  absente ou un plafond atteint ne sont jamais retentés.
- **Délais** : `request-timeout` (5 min) est transmis au fournisseur ; une analyse restée
  `PROCESSING` au-delà de `lease` (15 min) est remise en file, ou close après `max-attempts`.
- **Démarrage** : rien n'est analysé. Les analyses interrompues par un arrêt sont remises en
  file ; si l'IA n'est pas autorisée, les analyses en attente sont closes (`NOT_CONFIGURED`)
  au lieu d'attendre indéfiniment. Aucune analyse historique n'est lancée en masse.
- **Annulation** : `POST /api/analyses/{id}/cancel`, seulement avant l'envoi.

### Résultats

Grille `grid-2` (33 informations, sections de la fiche) : identification (objet, acheteur,
référence, type de marché, procédure, lots, lieu, date limite, visite, date limite des
questions), données financières (montant estimé, minimum, maximum, durée, reconductions,
tranches et options, variantes), exigences administratives (pièces de candidature et de
l'offre, qualifications, capacité financière, références, assurances, garanties,
sous-traitance, groupement), exigences techniques (prestations, normes), points d'attention et
clauses environnementales et sociales. Ajouter une information ne demande aucune migration.

Chaque information garde la valeur proposée, l'extrait exact, le document, la page et un état :
`VERIFIED` (extrait retrouvé dans la page et valeur lisible dedans), `UNVERIFIED`,
`NOT_INDICATED` (absente : ni valeur ni extrait), `AMBIGUOUS`, `CONTRADICTORY`. Les textes
libres et les choix sont des interprétations : seul leur extrait est vérifié. Une même
information unique donnée deux fois avec deux valeurs, ou une date limite différente de celle
de l'avis, passe `CONTRADICTORY` ; la valeur de l'avis n'est jamais écrasée, les deux sont
montrées. Le modèle n'agit sur rien : il ne crée, ne modifie ni ne supprime aucune
consultation, veille, dossier ou lien. Une décision humaine (validée, corrigée, rejetée)
conserve la valeur de l'IA, son auteur et sa date (RM10). Aucun score de confiance n'est
utilisé : seules comptent la preuve textuelle et les règles de cohérence.

Une analyse porte sur toutes les pièces retenues d'un dossier en un seul appel : c'est la
synthèse de la consultation, chaque information renvoyant à la pièce qui la justifie. Deux
consultations ne partagent jamais une analyse, même pour des fichiers identiques.

### Configuration

| Propriété (variable) | Défaut | Rôle |
|----------------------|--------|------|
| `analysis.enabled` (`ANALYSIS_ENABLED`) | `false` | Interrupteur général |
| `analysis.provider` (`ANALYSIS_PROVIDER`) | `none` | Identifiant du fournisseur (`ExtractionClient.provider()`) |
| `analysis.external-transmission-allowed` (`ANALYSIS_EXTERNAL_TRANSMISSION`) | `false` | Autorise l'envoi des documents à un fournisseur externe |
| `analysis.monthly-budget` (`ANALYSIS_MONTHLY_BUDGET`) | vide | Plafond en euros des analyses créées dans le mois civil (Paris) |
| `analysis.input-price-per-million`, `analysis.output-price-per-million` (`ANALYSIS_INPUT_PRICE`, `ANALYSIS_OUTPUT_PRICE`) | `0` | Prix du modèle, pour le coût et le plafond |
| `analysis.max-characters` | 400 000 | Texte envoyé au plus par analyse |
| `analysis.max-documents-per-tender` | 10 | Pièces envoyées au plus par analyse |
| `analysis.max-output-tokens` | 8 000 | Longueur maximale de la réponse |
| `analysis.request-timeout` | 5 min | Attente maximale d'une réponse |
| `analysis.max-concurrent-requests` | 1 | Appels simultanés |
| `analysis.max-attempts`, `retry-delay`, `max-retry-delay` | 3, 2 min, 1 h | Reprises |
| `analysis.lease`, `analysis.poll-interval` | 15 min, 30 s | Libération d'une analyse bloquée, examen de la file |

**Coûts.** Le coût est une estimation de GPE : tokens renvoyés par le fournisseur × prix
configurés. Ce n'est pas la facture. Avant un appel, GPE estime la demande (1 token pour 3
caractères, plus la réponse maximale) et refuse si elle dépasse ce qui reste du plafond ; le
plafond peut être dépassé d'au plus un appel en cours. Les analyses échouées comptent (elles
sont payées). GPE ne peut pas imposer un plafond de facturation au fournisseur : en fixer un
chez lui. Le dépassement n'entraîne jamais un changement automatique de fournisseur.

**Secrets.** La clé n'est lue que par le client du fournisseur, dans l'environnement du
serveur (`ANALYSIS_API_KEY` transmise par `docker-compose.yml`, vide par défaut). Elle n'est
ni dans le code, ni dans `application.properties`, ni dans PostgreSQL, ni envoyée à
l'interface ; `GET /api/analyses/configuration` n'en contient aucune trace. Les messages
d'erreur du fournisseur sont nettoyés (`SecretRedactor`) et coupés à 500 caractères ; le texte
des documents n'est jamais journalisé.

### API

| Méthode et chemin | Droits | Rôle |
|-------------------|--------|------|
| `GET /api/analyses/configuration` | administrateur | État, raisons, limites, file, consommation du mois ; aucun secret |
| `GET /api/analyses`, `GET /api/analyses/quality` | administrateur | Journal (statut, tentatives, erreur, tokens, coût) et qualité |
| `GET /api/tenders/{id}/analysis` | connecté | Fiche : disponibilité et raison, dernière analyse, historique |
| `GET /api/analyses/{id}` | connecté | Où en est une analyse, sans réponses ni texte |
| `POST /api/dossiers/{id}/analyses?force=` | connecté | Met en file l'analyse d'un dossier de GPE (relance avec `force=true`) |
| `POST /api/analyses/{id}/cancel` | connecté | Annule une analyse pas encore envoyée |
| `PUT /api/extracted-fields/{id}/decision`, `POST /api/analyses/{id}/validation` | connecté | Validation humaine |

Aucune route n'accepte une adresse ou un texte libre à envoyer : une analyse porte toujours
sur un dossier existant de GPE. Les droits sont vérifiés côté serveur à chaque requête
(session, CSRF, rôle). GPE n'a qu'une équipe (l'entreprise) : tous les comptes voient toutes
les consultations ; il n'y a donc pas de cloisonnement par équipe à préserver aujourd'hui.

### Activer un fournisseur plus tard

1. **Choisir le fournisseur** (décision de l'entreprise) et vérifier ses conditions : lieu de
   traitement (France, UE), durée de conservation des requêtes, non-réutilisation des données
   pour l'entraînement (engagement contractuel), sous-traitants, possibilité d'un plafond de
   dépense, accord de traitement des données ; vérifier que les DCE (documents publics, mais
   parfois des annexes nominatives) peuvent lui être confiés.
2. **Écrire son client** dans `analysis` :

   ```java
   @Component
   class MonFournisseurClient implements ExtractionClient {

   	private final String apiKey;

   	MonFournisseurClient(@Value("${ANALYSIS_API_KEY:}") String apiKey) {
   		this.apiKey = apiKey;
   	}

   	@Override
   	public String provider() {
   		return "mon-fournisseur";        // valeur de ANALYSIS_PROVIDER
   	}

   	@Override
   	public String model() {
   		return apiKey.isBlank() ? null : "nom-du-modèle";   // null : pas de clé, IA éteinte
   	}

   	@Override
   	public ModelAnswer complete(ExtractionRequest request) {
   		// instructions() en consignes système, documents() en message utilisateur,
   		// outputSchema() si le fournisseur sait imposer un format, maxOutputTokens(),
   		// temperature(), timeout() comme délai du client HTTP. Renvoyer le texte et les
   		// tokens consommés. Traduire les erreurs : 401/403 → AUTHENTICATION, 429 →
   		// RATE_LIMITED, quota épuisé → QUOTA_EXCEEDED, délai → TIMEOUT, 5xx et réseau →
   		// TEMPORARY, contenu refusé → REFUSED, contexte trop long → CONTEXT_TOO_LARGE.
   		// Ne jamais mettre la clé, les en-têtes ni le texte dans le message.
   	}

   }
   ```

   Ajouter des tests avec un serveur HTTP local simulé (comme `SyntheticDceServer`), jamais
   l'API réelle, et retirer ou adapter `ProviderIsolationTests.noRealProviderIsShipped`.
3. **Renseigner `.env`** sur le serveur : `ANALYSIS_API_KEY`, `ANALYSIS_PROVIDER`,
   `ANALYSIS_INPUT_PRICE`, `ANALYSIS_OUTPUT_PRICE`, `ANALYSIS_MONTHLY_BUDGET` ; fixer aussi un
   plafond chez le fournisseur.
4. **Autoriser** : `ANALYSIS_EXTERNAL_TRANSMISSION=true` puis `ANALYSIS_ENABLED=true`,
   `docker compose up -d backend`. Le journal affiche `AI analysis: READY`, Paramètres →
   Analyses par IA indique « Disponible ».
5. **Essayer sur un dossier** (bouton « Lancer l'analyse »), vérifier la fiche, le coût et le
   journal avant de laisser l'analyse automatique (RM8) traiter les appels d'offres retenus.
   Revenir en arrière : `ANALYSIS_ENABLED=false` et redémarrer.

### Limites actuelles

- Aucun fournisseur réel : rien n'est analysé tant que l'étape 2 n'est pas faite.
- Pas d'OCR : les pages scannées sont signalées (`SCANNED`, « OCR nécessaire ») et non lues.
- Tableurs (DPGF, BPU, DQE, XLSX), plans, images, 7z et RAR conservés mais non lus ; Word
  converti par LibreOffice ; TXT et HTML lus par sections (jsoup, sans aucun chargement de
  ressource distante).
- Une analyse partagée entre deux consultations au même contenu n'est pas réutilisée :
  chacune est analysée (et payée) séparément.
- Le plafond mensuel est une estimation locale ; le mois est celui de la création de l'analyse.
- Aucune détection automatique de données personnelles dans les DCE : à vérifier avant
  activation (étape 1).
- La version de la bibliothèque d'extraction n'est pas enregistrée par page, seule la méthode
  (`NATIVE`, `OCR`, `TEXT`, `HTML`).

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
autres. `collect.disabled-sources=TED` met une source de côté (par défaut : `APPROCH`, voir plus bas).

Modes d'accès : `AUTOMATED` (API ou open data publics) et `AUTHENTICATED` (compte ou clé de
l'entreprise, fournis par l'environnement) sont collectés ; `MANUAL` (export déposé par une
personne) et `NOT_AUTOMATABLE` (CAPTCHA, protection anti-robot) ne sont jamais interrogés. Aucune
protection n'est contournée ; une source qui refuse l'accès fait échouer son run, retenté au
créneau suivant.

Identité : `source + external_id`, pour les avis bruts comme pour les consultations ; un avis
ne modifie qu'une consultation de sa source. Chaque avis de l'historique (`tender_notice`) garde
sa source (V17).

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

### Reprise, rattrapage et surveillance des collectes

**Reprise** : une collecte sans date choisie part du dernier jour demandé par la dernière
collecte réussie de la source, moins un jour (`collect_run.until_date`, V20 ; pour les collectes
plus anciennes, le jour de leur début, heure de Paris), jamais plus de 366 jours en arrière. Elle
demande au plus `collect.catch-up.max-days-per-run` jours de publication (14) ; la suivante
continue. Une collecte depuis une date choisie (import initial) n'est pas limitée. Une collecte en
échec ne déplace pas le point de départ : la suivante redemande les mêmes jours, les avis déjà
stockés étant ignorés (`raw_notice` unique par source et identifiant) et les données de l'équipe
jamais modifiées.

**Rattrapage** (`CollectCatchUp`) : une source active « a manqué le dernier créneau » quand aucune
collecte n'a démarré depuis le dernier horaire de `collect.cron` (sur 8 jours), hormis une
collecte interrompue par un redémarrage. Ce contrôle a lieu `collect.catch-up.delay` (2 min) après
le démarrage, puis chaque heure à la demie (`collect.catch-up.cron`, utile après une mise en
veille). Les sources concernées sont collectées l'une après l'autre (`CATCH_UP` dans le journal),
en tâche de fond sur le pool de l'application, jamais sur le fil du planificateur (qui garde la
clôture horaire des consultations). Tant que la dernière collecte n'atteint pas aujourd'hui, une
nouvelle est lancée après `collect.catch-up.pause` (1 min), dans la limite de
`collect.catch-up.max-runs` (5) ; au-delà, les créneaux suivants continuent. Un échec arrête le
rattrapage de la source (le créneau suivant réessaie) ; une source déjà en cours de collecte est
passée ; un seul rattrapage à la fois. Un créneau dont la collecte a échoué n'est pas rattrapé :
il est retenté au créneau suivant, comme avant. Les horaires normaux ne changent pas.
`collect.catch-up.enabled=false` désactive le tout.

Charge : 14 jours au plus par collecte (BOAMP : environ 400 avis par jour ouvré, soit une
soixantaine de pages de 100 ; TED : une requête par 250 avis), une requête à la fois, une
nouvelle tentative unique après une erreur serveur ou un délai dépassé (`HttpRetry`).

**État et alerte** (`GET /api/collect-runs/sources`, Paramètres → Journal de collecte, et bandeau
sur toutes les pages) : dernière tentative, dernière réussite et publications couvertes
(`sinceDate`–`untilDate` de la dernière réussite), prochaine collecte planifiée, dernière erreur,
heures ouvrées depuis la dernière réussite. **Heures ouvrées** (`WorkingHours`) : toutes les heures
du lundi au vendredi (heure de Paris, changements d'heure compris), aucune le samedi ni le
dimanche ; les jours fériés comptent comme ouvrés. Une source active est **en retard** au-delà de
`collect.catch-up.alert-working-hours` (24) heures ouvrées sans réussite, ou si elle n'a jamais
été collectée : exemple, une dernière réussite le vendredi à 13 h donne 18 heures le lundi à 7 h,
24 à 13 h, et l'alerte juste après si cette collecte manque aussi. Le bandeau est relu toutes les
5 minutes ; le contrôle horaire écrit aussi l'alerte dans les journaux (`no successful collection
for … working hours`).

Constat du 21/10/2026 (base réelle, lecture seule) : dernière réussite BOAMP et TED le 12/10 à
16 h 24, publications du 11/10 au 12/10 ; backend arrêté puis redémarré le 21/10 à 13 h 31, après
le créneau de 13 h ; 14 créneaux manqués, aucune alerte. Avec l'ancien code, la reprise était
automatique mais seulement au créneau suivant (22/10 à 7 h, jours du 11/10 au 22/10, démontré par
`ResumeAfterOutageTests`). Avec ce code, elle a lieu 2 minutes après le démarrage.

Retour arrière de V20 : `ALTER TABLE collect_run DROP COLUMN until_date;` (l'ancien code ignore la
colonne).

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

### APProch (projets d'achats, désactivé par défaut)

APProch (`projets-achats.marches-publics.gouv.fr`, Direction des achats de l'État) publie les
**projets d'achats** des acheteurs publics avant tout appel d'offres. Ce ne sont pas des avis :
APProch précise lui-même qu'une publication « ne constitue pas un avis de pré-information ». Les
projets sont en open data (Licence Ouverte 2.0) sur `data.economie.gouv.fr`, jeu
`projets-dachats-publics` (`approch.base-url`, API Opendatasoft comme le BOAMP), sans compte ni clé.

- **Désactivé par défaut** : `collect.disabled-sources=APPROCH` dans `application.properties`.
  L'activer (`collect.disabled-sources=` ou la liste des autres sources à exclure) fait entrer les
  projets dans les consultations au prochain créneau.
- **Lecture** (`ApprochClient`) : le jeu ne liste que les projets ouverts du jour, sans date de
  publication, et dépasse la limite de 10 000 résultats de la lecture par pages : tout est lu en une
  requête d'export (`/exports/json`, environ 8,6 Mo, 4 s), une seule fois par collecte (dernier jour
  de la période, `ApprochConnector`).
- **Acheteur** : le jeu ne donne que le SIREN. Le nom vient de l'API publique Recherche
  d'entreprises (`approch.companies-url`), demandé une fois par SIREN (77 au 21/10/2026) pendant la
  vie de l'application, à 200 ms d'intervalle (limite de l'API : 7 requêtes par seconde). Un échec
  fait échouer la collecte (retentée au créneau suivant) ; un SIREN inconnu donne « Entité
  acheteuse SIREN … ».
- **Versions** : l'identifiant d'un avis brut est le code du projet suivi d'une empreinte (SHA-256,
  12 caractères) des champs lus et du nom de l'acheteur. Un projet inchangé est ignoré ; un projet
  modifié est stocké comme nouvelle version et met à jour sa consultation comme un rectificatif
  complet, si aucune version plus récente n'a été appliquée (`SourceParser.republishesChangedNotices`,
  APProch seulement). L'historique montre « Rectificatif » pour chaque nouvelle version.
- **Consultation** (`ApprochNoticeParser`) : type de procédure `PURCHASE_PROJECT` (« Projet d'achat
  (prévisionnel) ») ; aucune date limite ni montant (le jeu donne une tranche) ; la date de
  publication est le jour de première lecture ; publication prévisionnelle, date cible de remise des
  offres, procédure envisagée, tranche de montant et durée sont écrites après la description ; lien
  vers la fiche `…/project/{code}` ; le lien vers la consultation publiée (PLACE le plus souvent),
  quand l'acheteur l'a donné, est gardé comme lien DCE. Aucun identifiant de procédure : jamais de
  rattachement ni de proposition de rapprochement avec le BOAMP ou TED. Un projet dont le statut
  n'est plus « Ouvert » est ignoré.

Vérification réelle du 21/10/2026 (base jetable Testcontainers, deux collectes successives) :
10 744 projets reçus, 10 744 consultations, 0 erreur, 0 rejet ; deuxième collecte : 0 nouvel avis,
0 doublon ; 309 liens vers une consultation publiée ; 8 projets sans SIREN ; 929 sans département ;
3 713 (35 %) listent 90 départements ou plus (projets nationaux, saisis tels quels par l'acheteur) ;
1 274 sans CPV. Première collecte : 5 min (normalisation et filtrage de 10 744 consultations) ;
suivantes : 14 s. Avec les critères actuels de l'entreprise, environ 700 à 850 projets seraient
retenus.

Limites : un projet retiré du jeu (publié ou abandonné) n'est pas clos automatiquement (rien n'est
supprimé, pas de date limite) ; la tranche de montant ne remplit pas le montant estimé ; les
départements « France entière » font passer le critère géographique.

### AWS / marches-publics.info (prêt, désactivé en attendant le contrôle des conditions)

marches-publics.info (AW Solutions, profil d'acheteur) n'a ni API, ni flux, ni export public. Ses
CGU (article 2.2) appellent « téléchargement massif » le fait de visualiser plus de 100 avis par
heure ou 500 par jour, et ses mentions légales interdisent le téléchargement massif « de
l'ensemble des avis […] notamment par un prestataire de veille ». L'entreprise indique avoir
obtenu le 21/10/2026 une confirmation écrite d'AW Solutions pour la collecte prévue ; son texte
n'est pas encore versé au projet. **La source reste désactivée** (`collect.disabled-sources`)
tant que ses conditions n'ont pas été comparées au fonctionnement décrit ici ; toute condition
plus stricte (volume, horaires, nature des avis, identification) se règle par la configuration
ci-dessous ou doit être ajoutée au code avant activation.

- **Lecture** (`collect/awsolutions/AwsClient`) : recherche publique `POST /Annonces/lister`
  (avis en cours, nature `aw-solutions.natures`, par défaut `T` = travaux, parus « aujourd'hui »
  ou « hier » : la recherche ne sait pas sélectionner un autre jour), pages suivantes
  `?pager_t=N` avec le cookie de session, puis la page de chaque avis non encore stocké. User-Agent
  `GPE-LOGICIEL (veille interne)`. Aucun DCE n'est téléchargé.
- **Limites** (`AwsProperties`) : pause avant chaque page (40 s, donc 90 pages par heure au plus à
  elle seule) ; arrêt au-delà de 90 pages lues dans l'heure ou 450 sur 24 heures (liste ou avis,
  chacune compte) ; les avis restants sont lus à la collecte suivante tant qu'ils sont du jour ou de
  la veille, et le journal l'indique (`marches-publics.info: … pages read …`). Les compteurs sont en
  mémoire : un redémarrage les remet à zéro, mais la pause garantit toujours le plafond horaire.
  Volume mesuré : 54 avis de travaux parus le 20/10/2026. Une collecte lit la veille et le jour
  (environ 12 pages de liste) plus les avis nouveaux : 30 à 45 minutes à 40 s par page, pendant
  lesquelles le planificateur (un seul fil) n'exécute pas d'autre tâche ; la clôture horaire des
  consultations échues est seulement retardée.
- **Erreurs** : une erreur serveur ou un délai dépassé est retenté une fois après
  `collect.retry-delay` ; s'il persiste, la collecte AWS échoue seule (journal `collect_run`,
  message d'erreur), les autres sources ne sont pas touchées, et la collecte suivante relit les
  mêmes jours. Un refus (4xx, blocage d'adresse) n'est pas retenté.
- **Identifiants** : avis brut = nom de la page (`MPI-pub-…`, `LDM-pub-…`, `NR-pub-…`) suivi de la
  date limite affichée dans la liste : un report de date fait relire l'avis. Consultation =
  numéro de consultation de la plateforme (`IDM-…`, présent dans le lien du DCE), commun à tous ses
  avis ; une nouvelle version met la consultation à jour comme un rectificatif complet
  (`republishesChangedNotices`) sans toucher au statut de suivi, au commentaire ni aux suivis de
  l'équipe. Conservés pour la récupération future des documents : `IDM` (identifiant de la
  consultation et `consultationId` de l'avis brut), adresse de l'avis (`notice_url`) et lien DCE
  (`…fuseaction=dematEnt.login&type=DCE&IDM=…`, dans `dce_link`).
- **Lecture des pages** (`AwsPages`, jsoup) : liste (date de parution, date limite, acheteur et
  code postal, référence, objet, lots, liens) et page de l'avis (titre, acheteur, SIRET, chaque ligne
  « libellé / valeur » des tableaux). `AwsNoticeParser` : département par la région NUTS du lieu
  d'exécution principal, sinon les codes postaux du lieu, sinon celui de l'acheteur ; procédure par
  le champ « Mode » ; CPV principal et complémentaires ; date limite des offres, sinon des
  candidatures, sinon de la liste (heure de Paris). Une page titrée « annulation » ou « sans suite »
  annule la consultation AWS (titres non observés à ce jour : règle à vérifier).
- **Rapprochement** : le site ne donne pas d'identifiant de procédure eForms. Une consultation AWS
  et une consultation d'une autre source ayant exactement la même référence d'acheteur **et** la
  même date limite sont **proposées** comme pouvant être la même (`tender_match`, règle
  `SAME_REFERENCE`), quel que soit l'ordre d'arrivée (`SourceParser.proposesMatchesByReference`).
  Aucune fusion, aucune modification de la consultation BOAMP ou TED ; la règle ne joue jamais
  entre BOAMP et TED (vérifié par test). L'historique des publications (`NoticeHistory`) affiche
  chaque proposition avec sa règle : « même identifiant de procédure eForms » ou « même référence
  d'acheteur et même date limite (pas d'identifiant de procédure commun) », sous le titre
  « proposée comme pouvant être la même, à vérifier (rien n'est fusionné automatiquement) ».
  Estimation du 21/10/2026 : 4 ou 5 des 17 avis de travaux relevés sont aussi au BOAMP.

Configuration (docker-compose, variables du fichier `.env`, prises au redémarrage du backend) :

| Variable `.env` | Propriété | Défaut | Rôle |
|---|---|---|---|
| `COLLECT_DISABLED_SOURCES` | `collect.disabled-sources` | `APPROCH,AWS` | Retirer `AWS` active la source ; le remettre la désactive |
| `AW_SOLUTIONS_NATURES` | `aw-solutions.natures` | `T` | Natures cherchées : `T`, `S`, `F` |
| `AW_SOLUTIONS_PAUSE` | `aw-solutions.pause` | `40s` | Pause avant chaque page |
| `AW_SOLUTIONS_MAX_PAGES_PER_HOUR` | `aw-solutions.max-pages-per-hour` | `90` | Arrêt dans l'heure |
| `AW_SOLUTIONS_MAX_PAGES_PER_DAY` | `aw-solutions.max-pages-per-day` | `450` | Arrêt sur 24 heures |

Dans le conteneur, les noms Spring sont `AWSOLUTIONS_MAXPAGESPERHOUR` et
`AWSOLUTIONS_MAXPAGESPERDAY` (sans tirets) : `AwsPropertiesTests` vérifie la liaison.

Première collecte contrôlée, après contrôle des conditions de la confirmation écrite :

1. Verser la confirmation au projet et ajuster la configuration à ses conditions.
2. Essai sur une base jetable (jamais la base réelle) : PostgreSQL de test, backend lancé avec
   `COLLECT_DISABLED_SOURCES=BOAMP,TED,APPROCH`, collecte d'AWS seule par
   `POST /api/collect-runs?source=AWS` (administrateur), suivi dans le journal ; vérifier le nombre
   de pages lues dans les journaux, les consultations créées, puis une deuxième collecte (0 avis
   nouveau attendu).
3. Mise en service : sauvegarde vérifiée de la base, reconstruction du backend depuis le code
   commité, `COLLECT_DISABLED_SOURCES=APPROCH` dans `.env`, redémarrage du backend, première
   collecte manuelle d'AWS en dehors des créneaux de 7 h et 13 h, contrôle du journal et des
   rapprochements proposés. Désactivation : remettre `APPROCH,AWS` et redémarrer le backend.

Tests : 32 tests AWS, plus un test BOAMP–TED (lecture des vraies pages relevées à la main le 21/10/2026 ; client contre un
serveur simulé : session et pagination, avis déjà stockés non relus, limites horaire et
journalière, pause effective, erreur passagère et persistante ; collecte complète sur PostgreSQL
jetable avec le BOAMP : idempotence, date limite modifiée, données de l'équipe intactes sur les
consultations AWS et BOAMP, propositions dans les deux sens, échec isolé puis reprise ; désactivation
par défaut ; liaison des variables d'environnement). **Aucune collecte automatisée réelle n'a encore
été faite.**

### Rapprochement entre sources

80 % des procédures françaises de TED sont aussi au BOAMP, avec le même identifiant de procédure
eForms (mesure du 12/10/2026 sur 1 460 procédures). Seul cet identifiant exact sert au
rapprochement : jamais une ressemblance de titre, d'acheteur ou de date.

- **TED après le BOAMP** (cas courant ; le BOAMP est collecté avant TED, `@Order`) : un avis TED
  sans consultation TED, dont la procédure est celle d'une seule consultation d'une autre source,
  n'en crée pas : il est ajouté à l'historique de cette consultation avec sa source
  (`SourceParser.defersToOtherSources`, TED seulement). Il ne la modifie jamais : ni champs, ni
  statut, ni identifiant de procédure ; le résultat « attribué » et le filtre des marchés
  attribués ne lisent que les avis de la source de la consultation, et un rectificatif BOAMP
  reste « le plus récent » face aux avis TED. Le bilan TED le compte en « mises à jour ».
- **Ambiguïté** : si la procédure est celle de plusieurs consultations d'autres sources, l'avis
  TED est mis de côté (« Procedure of several tenders of other sources »), sans consultation ni
  fusion.
- **TED avant le BOAMP** : la consultation TED existe déjà ; le BOAMP crée la sienne comme avant,
  et un rapprochement est proposé (`tender_match`, `SAME_PROCEDURE`, `PROPOSED`), visible sur
  les deux fiches (`matches`). Aucune fusion : la décision (et la reprise des données de GPE
  d'une fiche à l'autre) reste à faire par une personne.
- **Consultation absente du BOAMP** : TED crée sa consultation, filtrée par les critères de
  l'entreprise comme les autres.

`scripts/report-cross-source.sql` (lecture seule) liste les rapprochements proposés et les avis
TED mis de côté, avec les données de GPE de chaque côté. L'écran de validation des
rapprochements (confirmer, rejeter, fusionner de façon réversible) n'est pas encore fait.

### Doublons : décision humaine, fusion réversible

Écran : Paramètres → « Doublons à vérifier » (administrateurs), API `/api/duplicates` (V19,
`duplicate/DuplicateService`).

- **Groupes** : consultations non fusionnées qui partagent exactement un identifiant de
  procédure eForms. Chaque groupe explique pourquoi (citation d'un avis par un rectificatif, relu
  avec les règles actuelles) ; il est « certain » s'il compte deux consultations d'une même source
  dont l'une a été créée par un rectificatif citant l'autre, sinon « à vérifier ». La consultation
  proposée à la conservation : celle qui porte des données de l'équipe, sinon celle qui est citée.
- **Fusionner** : les avis et liens DCE du doublon passent sur la consultation conservée et sont
  appliqués selon les règles de la collecte (le dernier rectificatif de la source donne la date
  limite ; une consultation close seulement par échéance est rouverte si la date est à venir) ;
  les avis de la source sont réétiquetés comme relus aujourd'hui (rectificatif et non avis
  initial). Le doublon n'est pas supprimé : `tender.merged_into_id` le masque des listes et sa
  fiche renvoie vers la consultation conservée. Le statut, le commentaire et les suivis de l'équipe
  ne changent jamais. Refusée si les identifiants de procédure diffèrent ou si seul le doublon
  porte des données de l'équipe ; si les deux en portent, confirmation explicite obligatoire.
- **Ce ne sont pas des doublons** : la paire n'est plus proposée.
- **Trace et annulation** : chaque décision est gardée (`duplicate_decision` : motif, auteur,
  date). Une fusion garde l'état complet de la consultation conservée avant elle, les avis et liens
  déplacés et les étiquettes changées : l'annulation rend exactement l'état précédent. Elle est
  refusée si la consultation conservée a reçu un nouvel avis depuis (à vérifier à la main).
- **Lot** : `GET /api/duplicates/batch` donne l'aperçu des fusions des groupes certains sans
  donnée de l'équipe sur le doublon, avec un jeton ; `POST` avec ce jeton ne fait que ces
  fusions-là (refusé si les doublons ont changé depuis l'aperçu).

Essai sur une copie des données réelles (12/10/2026) : 96 groupes, 89 certains, 7 à vérifier ;
lot de 89 fusions puis 89 annulations : base identique à l'état initial (empreinte des
consultations, avis et liens). Cas 5155 / 7826 : après fusion, 5155 « Réponse envoyée »
conservée, date limite 16/10, deux rectificatifs dans l'historique, 7826 masquée ; annulation :
état identique.

Retour arrière de V19 : annuler toutes les fusions depuis l'écran, puis
`DROP TABLE duplicate_decision; ALTER TABLE tender DROP COLUMN merged_into_id;`. Sans cela, l'ancien
code fonctionne aussi sur un schéma en V19 (colonnes ajoutées seulement), mais montrerait de
nouveau les doublons fusionnés.

## DCE : liens, fichiers et récupération future

Un lien vers une plateforme n'est pas un fichier : les deux sont séparés.

- **Liens** (`dce_link`, V18, `document/DceLink`) : chaque adresse où obtenir le DCE d'une
  consultation, telle qu'un avis la donne (profil d'acheteur, page du DCE, page de dépôt), avec
  la source de cet avis, la plateforme (hôte sans `www.`), le statut de récupération, les dates
  de première et dernière vue et de dernière tentative. Une adresse n'est gardée qu'une fois par
  consultation ; seules les adresses http(s) sont retenues. Enregistrées à la normalisation par
  `DceLinkService` pour chaque avis appliqué, y compris un avis TED ajouté à l'historique d'une
  consultation BOAMP (souvent l'adresse exacte du DCE, plus précise que le profil d'acheteur du
  BOAMP), sans modifier la consultation. V18 a repris l'adresse connue des consultations déjà
  collectées (6 100 au 12/10/2026).
- **Statuts** (`DceLinkStatus`, V23) : `AVAILABLE` (lien connu, rien de décidé : DCE à télécharger
  à la main), `NOT_ELIGIBLE`, `ELIGIBLE_PENDING`, `IN_PROGRESS`, `FETCHED`, `TEMPORARY_ERROR`,
  `PERMANENT_ERROR`, `LINK_UNAVAILABLE`, `ACCESS_REQUIRED` (compte, identification ou CAPTCHA :
  action manuelle), `AUTOMATED_ACCESS_NOT_AUTHORIZED` (consultation éligible, plateforme non
  autorisée), `STORAGE_LIMIT_REACHED`. V23 a converti `TO_FETCH` en `ELIGIBLE_PENDING` et `FAILED`
  en `PERMANENT_ERROR` (aucun lien ne les portait). L'éligibilité de la consultation est tenue à
  part (`tender_eligibility`) : le statut d'un lien dit ce qui a été fait, ou non, de ce lien.
- **Fichiers** : un DCE obtenu est une version de dossier (`dossier`, `document`), stockée dans
  `STORAGE_DIR` ; la base ne garde que les métadonnées (nom, type de pièce, taille, SHA-256,
  chemin, état de lecture, nombre de pages, texte page par page). `dossier.origin` (V18) dit
  comment il a été obtenu : `UPLOAD` (déposé par un utilisateur) ou `RETRIEVED` (récupéré
  depuis `dossier.dce_link_id`). Chaque nouveau dépôt est une nouvelle version ; les
  précédentes restent.
- **API** : `GET /api/tenders/{id}/dce-links` (liens, quelle que soit la source de la
  consultation) et `GET /api/tenders/{id}/dossiers` (fichiers). Un bouton de téléchargement ne
  doit exister que pour un document de dossier, jamais pour un lien.
- **Fiche consultation** : la section « Documents (DCE) » montre d'abord « Accès au DCE »
  (`components/DceLinks` : plateforme, avis qui l'indique, état, lien « Ouvrir la plateforme »),
  puis les dossiers déposés ou récupérés. L'historique des publications
  (`components/NoticeHistory`) indique la source de chaque avis et les consultations d'autres
  sources proposées comme identiques ; le badge « Modifiée par un rectificatif » ne compare que
  les avis de la source de la consultation.
- **Récupération automatique** : voir la section suivante. Aucune plateforme n'est autorisée et
  aucun connecteur réel n'existe : rien n'est téléchargé automatiquement.

## Veilles, éligibilité et récupération contrôlée des DCE

Six étapes séparées : collecte des avis (inchangée) ; conservation des liens (`dce_link`) ;
éligibilité de la consultation ; autorisation de la plateforme ; récupération éventuelle ; stockage
et lecture du dossier.

### Veilles (`alert`, V21, `/api/alerts`)

Une veille est une recherche enregistrée **personnelle** : son propriétaire est l'utilisateur
connecté (jamais un identifiant envoyé par l'interface) ; un autre utilisateur, administrateur
compris, reçoit 404 sur ses veilles. GPE n'a pas d'équipes : l'éligibilité des DCE est commune à
l'entreprise (voir plus bas). Critères (type `Alert` du frontend) : mots-clés, région, départements,
types de marché, types de travaux, préfixes CPV, montants minimum et maximum, active. Validation
serveur : nom de 1 à 100 caractères, au moins un critère, départements `01`–`95`, `2A`, `2B`,
`971`–`976`, région connue (`tender/Regions`), types de travaux de `ACTIVITY_LABELS`, CPV de 2 à 8
chiffres, montants positifs et minimum ≤ maximum. `GET` (avec `matchCount`, `newCount`,
`indeterminateCount`, `evaluating`), `POST`, `PUT` (critères et activation), `DELETE` (204),
`POST /{id}/viewed`. La liste des consultations accepte `alert=<id>` : les consultations qui
correspondent à une veille **de l'utilisateur**, selon la dernière évaluation. Supprimer une veille
ne supprime ni consultation, ni lien, ni fichier. Chaque changement incrémente `alert_revision`.

Sémantique (celle du contrat d'API) : tous les critères renseignés doivent être vrais ; dans un
critère, une valeur suffit (un mot-clé, un département…). Mots-clés cherchés comme pour les
critères de l'entreprise (`TextMatcher`, sans accents ni casse, début de mot), **sans les synonymes
métier** prévus par le contrat (non implémentés). La région et les départements se cumulent.

### Éligibilité (`tender_eligibility`, V22, `eligibility/EligibilityService`)

Une consultation est **éligible** si elle est ouverte (statut `ACTIVE`) et correspond à au moins
une veille **active** dont le propriétaire a un **compte actif** (`app_user.enabled`), de
n'importe quel utilisateur : GPE n'a qu'une équipe, l'entreprise (le statut des consultations est
partagé, les veilles sont personnelles), et les DCE sont gardés une fois pour toute l'entreprise.
Désactiver un compte ne change pas le champ `active` de ses veilles et ne supprime rien : elles
cessent seulement de compter, et comptent de nouveau à sa réactivation. Les critères de l'entreprise
(`tender.retained`) ne sont ni une condition ni modifiés. Évaluation par veille
(`filter/AlertMatcher`, qui réutilise `TenderFilter` sans en changer le comportement historique) :
un critère faux → ne correspond pas ; sinon une information absente de la consultation
(département, type de marché, CPV, montant) ou un critère non évaluable (**types de travaux** :
les consultations ne les portent pas encore) → indéterminé ; sinon → correspond. Résultat de la
consultation : `ELIGIBLE` si une veille correspond ; sinon `INDETERMINATE` si une veille est
indéterminée ; sinon `NOT_ELIGIBLE` (aucune veille active, aucune correspondance, consultation
close). Une seule ligne par consultation : plusieurs veilles donnent une seule éligibilité et une
seule récupération par lien. Le motif enregistré ne nomme pas les veilles (privées) ; la fiche
nomme seulement celles de l'utilisateur connecté.

Réévaluation : une éligibilité est périmée si la consultation a changé depuis (`tender.updated_at`)
ou si une veille a changé (`alert_revision`), ou si un nouveau lien est apparu. Les consultations
périmées sont évaluées par lots de `dce.eligibility.batch-size` (200), dans l'ordre des
identifiants, une transaction par lot, en tâche de fond, une évaluation à la fois : après chaque
collecte, après chaque changement de veille, et toutes les `dce.eligibility.sweep-interval` (10 min),
ce qui reprend aussi une évaluation arrêtée par un redémarrage. Réévaluer ne change rien
(idempotent). Charge : environ 9 000 consultations × nombre de veilles actives, en mémoire et sans
réseau ; aucune plateforme n'est contactée. Effet sur les liens (`DceQueueService`) : non éligible →
`NOT_ELIGIBLE` ; indéterminé → `AVAILABLE` avec le motif ; éligible → `ELIGIBLE_PENDING` si la
plateforme est autorisée, sinon `AUTOMATED_ACCESS_NOT_AUTHORIZED`. Un lien `IN_PROGRESS`,
`FETCHED`, `PERMANENT_ERROR`, `LINK_UNAVAILABLE` ou `ACCESS_REQUIRED` n'est jamais changé par une
évaluation : les fichiers récupérés restent quoi qu'il arrive aux veilles. Chaque **changement de
statut** est tracé (`dce_link_event`) ; une réévaluation qui garde le statut et n'en change que le
libellé met le libellé à jour sans nouvelle entrée, et une réévaluation sans changement n'écrit
rien dans l'historique. Premier calcul en production : environ une entrée par lien (7 400 liens
`AVAILABLE` → `NOT_ELIGIBLE` sans veille), puis seulement les changements.

Changement de compte (`UserService.update` publie `UserStatusChangedEvent`, traité après la
validation) : à la désactivation, seules les consultations dont l'éligibilité cite une veille active
du compte (correspondance ou indétermination) sont marquées périmées ; à la réactivation, les
consultations ouvertes (seules à pouvoir devenir éligibles) ; rien pour un compte sans veille active.
L'évaluation suit en tâche de fond, sans réseau.

### Autorisation des plateformes (`DcePlatformPolicy`)

`dce.retrieval.allowed-platforms` : plateformes (hôte sans `www.`, exactement comme
`dce_link.platform`, un sous-domaine se déclare à part) autorisées après accord écrit. **Vide par
défaut.** Une plateforme n'est utilisée que si elle est listée **et** qu'un `DceRetriever` la prend
en charge ; aucun connecteur réel n'existe, aucun repli : une adresse qui ressemble à un fichier
n'est pas une autorisation. Vérifié côté serveur à la mise en file et à nouveau juste avant chaque
récupération. `marches-publics.info`, `aws-achat.info`, `dl.aws-achat.info` (refus d'AW Solutions)
et `projets-achats.marches-publics.gouv.fr` (APProch, sans DCE) ne peuvent pas être listées : le
démarrage échoue.

Un connecteur HTTP utilise `SafeHttpFetcher` (Apache HttpClient 5, utilisé par lui seul : les
collecteurs gardent le client HTTP du JDK, `spring.http.clients.imperative.factory=jdk`) :

- avant chaque requête et chaque redirection (jamais suivie automatiquement, au plus
  `max-redirects`) : http(s) seulement, pas d'informations d'utilisateur, ports 80 et 443
  seulement, aucune adresse numérique ambiguë (`2130706433`, `0177.0.0.1`, `0x7f.1`, `127.1`…),
  et une adresse littérale IPv4 ou IPv6 vérifiée aussitôt ;
- **une seule résolution par connexion**, faite par le résolveur validant donné à HttpClient, qui
  se connecte exactement aux adresses qu'il a renvoyées : pas de seconde résolution indépendante
  entre le contrôle et la connexion (DNS rebinding) ; une adresse interdite dans la réponse refuse
  toute la réponse ; chaque nouvelle connexion (redirection, nouvelle requête) est revalidée ;
- adresses refusées : la machine, les réseaux privés et partagés (10/8, 172.16/12, 192.168/16,
  100.64/10), link-local (169.254/16, fe80::/10), multicast, documentation, tests de performance,
  réservées (0/8, 240/4…), IPv6 uniques locales (fc00::/7) et site-local, `::`, et toute adresse
  IPv4 portée par une IPv6 (`::ffff:`, compatible, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`) selon
  l'IPv4 portée ; Teredo refusé ; en IPv6, seul l'unicast global (2000::/3) est accepté ;
- jamais de proxy, même configuré pour le système ; pas de cookies, pas de nouvelle tentative
  automatique ; délais de connexion et de lecture.

Limites restantes : la sécurité dépend de la résolution du système au moment de la connexion
(un serveur DNS qui ment de façon cohérente vers une adresse publique n'est pas détecté, ce qui
ne mène pas au réseau interne) ; une adresse publique d'un autre client du même hébergeur reste
joignable ; HTTPS vérifie le certificat du nom demandé (comportement de HttpClient) ; les
connecteurs futurs doivent utiliser ce client et aucun autre. Vérifié par des tests avec un DNS
simulé et un serveur local uniquement, aucun scénario réseau réel.

### File de récupération (`DceRetrievalWorker`)

Désactivée par défaut (`dce.retrieval.worker-enabled=false`). Regarde la file toutes les
`poll-interval` ; prend un lien `ELIGIBLE_PENDING` ou `TEMPORARY_ERROR` dont l'heure est venue
(verrou de ligne `FOR UPDATE SKIP LOCKED` puis `IN_PROGRESS` : jamais deux travailleurs sur le même
lien ; jamais une adresse déjà en cours pour une autre consultation), les consultations publiées le
plus récemment d'abord, au plus `concurrency` à la fois, sur ses propres fils (`dce-retrieval-*`),
jamais ceux des collectes. Juste avant : éligibilité et plateforme vérifiées de nouveau, espace
réservé dans le quota. Fichiers copiés dans `STORAGE_DIR/dce-tmp` en arrêtant le transfert au-delà
de `max-file-size` (500 Mo, total d'une récupération), validés par leur contenu, puis gardés comme
nouvelle version du dossier (`dossier.origin = RETRIEVED`, `dce_link_id`), lue comme un dépôt
(archives extraites avec les mêmes limites : 2 000 entrées, 2 Go décompressés, profondeur 3, chemins
hors du dossier refusés). Pas de nouvelle version si les documents sont exactement ceux de la
dernière (mêmes SHA-256, quel que soit le lien) ; un rectificatif qui change les documents en fait
une. Le dossier temporaire est toujours supprimé et la réservation libérée.

Erreurs : `AccessRequiredException` → `ACCESS_REQUIRED` ; `LinkUnavailableException` (404, 410) →
`LINK_UNAVAILABLE` ; fichier refusé, adresse refusée → `PERMANENT_ERROR` ; quota → `STORAGE_LIMIT_REACHED`
— aucun n'est retenté automatiquement. Erreur passagère → `TEMPORARY_ERROR`, nouvelle tentative
après `retry-delay` (10 min) doublé à chaque fois jusqu'à `max-retry-delay` (6 h), au plus
`max-attempts` (5) tentatives, puis `PERMANENT_ERROR`. Au démarrage : les liens laissés
`IN_PROGRESS` par un arrêt repassent en attente (la tentative compte), les réservations sans
récupération en cours sont libérées, `dce-tmp` est vidé ; **aucune récupération n'est lancée au
démarrage**. Un lien pris depuis plus de `lease` (30 min) est libéré de même. Nouvelle tentative
manuelle (`POST /api/dce-links/{id}/retry`, administrateurs) : seulement après une erreur, un lien
indisponible ou le quota, pour une consultation éligible et une plateforme autorisée.

### Quota de 3 Go (`DceStorageQuota`)

`dce.retrieval.quota=3000000000B` : 3 000 000 000 octets pour **les seuls DCE récupérés** (fichiers
reçus, archives et fichiers extraits : `dossier.stored_bytes`, V23) et les récupérations en cours
(`dce_storage_reservation`). Ce n'est ni une limite du disque ni des dossiers déposés à la main. Une
récupération réserve `max-file-size` avant de commencer ; la vérification et la réservation se font
sous un verrou consultatif PostgreSQL, donc plusieurs travailleurs, même dans plusieurs processus,
ne dépassent jamais ensemble le quota. Après extraction, l'espace réellement écrit est vérifié de
nouveau ; s'il dépasse, rien n'est gardé et le lien attend (`STORAGE_LIMIT_REACHED`) une place
libérée à la main ou un quota plus grand. Rien de déjà gardé n'est supprimé pour faire de la place.

### Type réel des fichiers (`FileTypeValidator`)

Par la signature binaire, jamais par le nom ni par le `Content-Type` : PDF (terminé par `%%EOF`),
ZIP et formats Office ouverts, OLE (Office 97), RTF, 7z, RAR, PNG, JPEG, TIFF, DWG. Refusés : page
web (connexion, formulaire de retrait), fichier vide, format inconnu, PDF tronqué, ZIP illisible,
ZIP au-delà des limites de l'extraction ou contenant un chemin interdit, extension contraire au
contenu (un nom sans extension connue prend celle du contenu). Le SHA-256 est calculé sur le contenu
validé.

### Interface

Fiche consultation (`DceLinks`) : éligibilité et motif, veilles de l'utilisateur concernées, pour
chaque lien la plateforme (autorisée ou non), le statut et son motif, la dernière tentative, le
nombre de fichiers récupérés, l'action manuelle à faire, l'historique ; toujours « Ouvrir la
plateforme », jamais de bouton de téléchargement automatique. Paramètres → Récupération des DCE
(`DcePanel`, `/api/dce/summary`, `/api/dce/events`, administrateurs) : consultations éligibles,
non éligibles, indéterminées ; DCE en attente, récupérés, action humaine, accès non autorisé,
erreurs ; espace utilisé, réservé et quota ; derniers changements d'état avec « Relancer ». Mes
veilles : branché sur le backend (`alerts` retiré de `NOT_IN_BACKEND`), avec les consultations
indéterminées et le recalcul en cours.

### Configuration

| Propriété | Défaut | Rôle |
|---|---|---|
| `dce.eligibility.batch-size` | 200 | Consultations par transaction |
| `dce.eligibility.sweep-interval` | 10m | Recherche des éligibilités périmées |
| `dce.eligibility.auto-evaluation` | true | Évaluations automatiques (désactivées dans les tests seulement) |
| `dce.retrieval.allowed-platforms` | (vide) | Plateformes autorisées après accord écrit |
| `dce.retrieval.worker-enabled` | false | Travailleur de la file |
| `dce.retrieval.concurrency` | 2 | Récupérations simultanées |
| `dce.retrieval.poll-interval` | 30s | Regard sur la file |
| `dce.retrieval.max-attempts` | 5 | Tentatives avant échec définitif |
| `dce.retrieval.retry-delay` / `max-retry-delay` | 10m / 6h | Attente entre tentatives |
| `dce.retrieval.lease` | 30m | Libération d'un lien abandonné |
| `dce.retrieval.max-file-size` | 500MB | Taille maximale d'une récupération |
| `dce.retrieval.quota` | 3000000000B | Quota des DCE récupérés, en octets |
| `dce.retrieval.connect-timeout` / `read-timeout` / `max-redirects` | 10s / 60s / 5 | Connecteurs HTTP |

Migrations : V21 (`alert`, `alert_revision`), V22 (`tender_eligibility`), V23 (statuts sur 40
caractères et conversion, colonnes de file de `dce_link`, `dce_link_event`, `dossier.stored_bytes`,
`dce_storage_reservation`), toutes additives sauf la conversion des deux anciens statuts ; retour
arrière écrit en tête de chaque fichier. Testées sur base jetable (Testcontainers) et, pour V23, sur
un schéma isolé à partir de V22 avec liens, dossiers et documents (`DceMigrationTests`).

Mise en service (avec accord) : sauvegarde vérifiée ; reconstruction du backend (V20 à V23
appliquées au démarrage) ; au premier passage, l'éligibilité de toutes les consultations est
calculée (environ 9 000, sans réseau) : sans veille, toutes sont `NOT_ELIGIBLE` et chaque lien
reçoit un événement. Aucun téléchargement tant que `allowed-platforms` est vide. Ajouter une
plateforme exige : accord écrit précisant le périmètre, un `DceRetriever` dédié et testé, son ajout
à la liste, puis `worker-enabled=true`.

## Sauvegarde et restauration

Le service `backup` (image PostgreSQL) sauvegarde une fois par jour, à `BACKUP_TIME` (heure de
Paris) ou, si l'ordinateur était éteint ou en veille à cette heure, dès qu'il fonctionne de
nouveau (vérification toutes les 10 minutes, `BACKUP_CHECK_EVERY`) :

- la base : `database_AAAA-MM-JJ_HHMM.dump` (format `pg_dump` personnalisé) ;
- les fichiers déposés : `files_AAAA-MM-JJ_HHMM.tar.gz`.

Une machine qui reste éteinte ne fait aucune sauvegarde : vérifier régulièrement la date de la
dernière (`ls backups`). Les sauvegardes quotidiennes de plus de `BACKUP_KEEP_DAYS` jours sont
supprimées ; une sauvegarde nommée autrement (par exemple `database_avant-ted_…`, faite avant un
déploiement) n'est jamais supprimée automatiquement. `BACKUP_DIR` doit être
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

- Collectes : Paramètres → Journal de collecte (état de chaque source : dernière tentative,
  dernière réussite, publications couvertes, prochaine collecte, dernière erreur, retard en heures
  ouvrées ; puis chaque collecte avec son bilan) et bandeau d'alerte de retard sur toutes les pages ;
  détail : `docker compose logs backend | grep -E "import|catch"`.
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

Sans JDK ni Node sur la machine, les mêmes commandes tournent dans des conteneurs :

```bash
docker run --rm -v "$PWD/backend:/app" -v "$HOME/.m2:/root/.m2" \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app eclipse-temurin:21-jdk ./mvnw -B test
```

Analyse par IA, sans clé ni appel réel (fournisseur simulé par Mockito) :

| Classe de test | Ce qui est vérifié |
|----------------|--------------------|
| `AnalysisWithoutProviderTests` | Démarrage sans clé ni fournisseur, état `DISABLED` affiché, demande refusée, aucune ligne créée, demande en attente close au démarrage, configuration sans secret |
| `AnalysisTransmissionTests` | Clé et IA activée mais envoi non autorisé : rien n'est envoyé, même pour un dossier retenu lu |
| `AnalysisGateTests` | Ordre des conditions, choix du fournisseur, plafond mensuel, validation des réglages, délai de reprise avec jitter |
| `AnalysisApiTests` | Analyse d'un dossier retenu, réutilisation d'une même empreinte, réponse illisible, validation humaine, journal et coût |
| `AnalysisQueueTests` | Reprise des erreurs temporaires, clé refusée jamais retentée ni affichée (réponses et journaux), un appel à la fois, reprise après arrêt sans appel au démarrage, annulation, nouvelle analyse après changement de grille, séparation des consultations, nombre de pièces, droits sur chaque route |
| `AnswerReaderTests`, `AnswerCheckerTests`, `CitationVerifierTests`, `ExtractionGridTests` | Réponse mal formée, champs inconnus, absence sans invention, contradictions, preuves et pages, schéma JSON |
| `ProviderIsolationTests`, `SecretRedactorTests` | Aucun SDK ni fournisseur réel, contrat confiné au package, masquage des clés |
| `TextFileExtractorTests`, `DocumentApiTests` | TXT, HTML sans chargement distant, PDF illisible, format inconnu, PDF scanné (OCR) |
