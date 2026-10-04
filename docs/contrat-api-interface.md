# Contrat d'API attendu par l'interface

L'interface fonctionne aujourd'hui sur des données de démonstration servies par une API
simulée (`frontend/src/mock`). Cette API simulée répond aux **mêmes URL avec le même JSON**
que le backend devra fournir : ce document liste ce qui existe déjà dans le backend Spring Boot
et ce qu'il reste à y ajouter. Les types TypeScript de `frontend/src/types/` font foi pour
le format des réponses ; `frontend/src/mock/server.ts` est une implémentation de référence
exécutable des règles ci-dessous.

## Passer des données de démonstration au backend

| Mode | Réglage |
|------|---------|
| Démonstration | `NEXT_PUBLIC_DATA_SOURCE=mock` dans `frontend/.env.local` |
| Backend réel  | variable absente (valeur par défaut, utilisée par l'image Docker) |

La variable est lue à la construction de l'interface (`npm run dev` ou `npm run build`). Aucun
écran ne sait d'où viennent les données : les écrans appellent les fonctions de
`frontend/src/services/` (`getTenders`, `getTender`…), qui passent toutes par `services/http.ts`,
seul endroit qui aiguille les appels vers le backend ou vers l'API simulée.

Avec le backend réel, ce que le backend ne fournit pas encore est masqué plutôt que de produire
une erreur : la liste `NOT_IN_BACKEND` de `services/http.ts`, lue par `available()`, en décide.
Elle contient aujourd'hui les veilles (Mes veilles affiche « pas encore reliée au serveur »), les
marchés attribués (même message ; la fiche ne les demande pas), la recherche dans le DCE, le
filtre par type de travaux, la distance (filtre et tri), le tri par pertinence, les filtres année,
résultat et acheteur des Archives, et les sections Critères d'attribution et Informations
importantes de la fiche. Retirer une valeur de cette liste suffit à afficher la fonction une fois
le backend prêt. Avec les données de démonstration, tout reste affiché.

## Récapitulatif

| Appel | Backend actuel | À faire |
|-------|----------------|---------|
| `GET /api/tenders` | onglets, 13 filtres, tri (voir plus bas) | filtres et tris restants, synonymes, nouveaux champs |
| `GET /api/tenders/counts` | mêmes filtres que la liste, tous les onglets | — |
| `GET /api/tenders/status-counts` | existe | — |
| `GET /api/tenders/{id}/awards` | absent | **nouveau** : marchés attribués de la consultation, un par lot (`AwardedContract`) |
| `GET /api/awards` | absent | **nouveau** : recherche dans les marchés attribués (voir plus bas) |
| `GET /api/tenders/{id}/documents/search?q=` | absent | **nouveau** : recherche dans le DCE (voir plus bas) |
| `GET /api/tenders/{id}` | existe, avec `followed` et `result` | nouveaux champs (voir plus bas) |
| `PUT /api/tenders/{id}/follow-up` | existe, accepte `SUBMITTED` | — |
| `PUT /api/tenders/{id}/follow` | existe : suivre (étoile), par utilisateur | — |
| `DELETE /api/tenders/{id}/follow` | existe : ne plus suivre | — |
| `/api/alerts` | absent | **nouveau** : veilles de l'utilisateur (voir plus bas) |
| Autres appels (connexion, DCE, analyse, paramètres) | existent | aucun changement |

## Écrans, services et appels

| Écran | Fonctions de service | Appels |
|-------|----------------------|--------|
| Tableau de bord | `getTenderCounts`, `getStatusCounts`, `getTenders`, `getAlerts`, `markAlertViewed`, `getAwardedContracts` | `/api/tenders/counts`, `/api/tenders/status-counts`, `/api/tenders`, `/api/alerts`, `/api/awards` |
| Appels d'offres | `getTenders`, `getTenderCounts`, `updateFollowUp`, `setFollowed`, `getAlerts` | `/api/tenders` (`tab` `NEW` à `FILTERED_OUT`), `/counts`, `/follow-up`, `/follow` |
| Mes veilles | `getAlerts`, `createAlert`, `updateAlert`, `deleteAlert`, `markAlertViewed` | `/api/alerts` |
| Mes suivis | `getTenders`, `getStatusCounts` | `/api/tenders?tab=FOLLOWED`, `/api/tenders/status-counts` |
| Archives | `getTenders` | `/api/tenders?tab=ARCHIVED` |
| Marchés attribués | `getAwardedContracts` | `/api/awards` |
| Fiche | `getTender`, `getTenderAwards`, `getDossiers`, `searchDocuments`, `getTenderAnalysis`… | `/api/tenders/{id}`, `/awards`, `/dossiers`, `/documents/search`, `/analysis` |
| Paramètres, connexion | inchangées | inchangés |

## Ordre d'intégration recommandé

1. **Rétablir le mode réel** : fait (`SUBMITTED`, nouvelles valeurs de `TenderTab`).
2. **Champs simples de la liste** : fait pour `reference`, `procedureType`, `source`,
   `lotCount` ; restent `postalCode`, `documentCount`, et `result` (fourni dans le détail seulement).
3. **Recherche et filtres** : fait pour les paramètres marqués « oui » plus bas, le tri,
   `/counts` avec filtres et `/status-counts` ; restent les synonymes et les autres paramètres.
4. **Suivi personnel** : fait (table `tender_follow`, `/follow`, `followed`, onglet `FOLLOWED`).
5. **Veilles** : table `alert`, `/api/alerts`, paramètre `alert`.
6. **Attributions** : extraction des avis d'attribution, `/api/awards`, `/api/tenders/{id}/awards`,
   `winners`, `awardedAmount`.
7. **Recherche dans le DCE** : `/documents/search` sur `document_page`.
8. **Enrichissements** : `activities`, `distanceKm`, `relevanceScore`, `cpv`, `awardCriteria`,
   `keyInfo`, `platformName`.

Chaque point peut être livré seul : l'interface affiche « Non indiqué » ou « – » pour un champ
encore absent.

## Statuts

`FollowUpStatus` comprend la valeur `SUBMITTED` (« Réponse envoyée ») :

| Valeur | Libellé |
|--------|---------|
| `NEW` | Nouveau |
| `TO_STUDY` | À analyser |
| `RESPONDING` | En préparation |
| `SUBMITTED` | Réponse envoyée |
| `DISMISSED` | Écarté |

La colonne `follow_up_status` est un `VARCHAR(20)` sans contrainte : seule l'énumération Java
a changé. Le statut reste partagé par toute l'équipe. Il est distinct de l'état de la consultation
(`status` : `ACTIVE`, `CLOSED`, `CANCELLED`, `UNSUCCESSFUL`), tenu par la collecte.

## Suivi personnel (étoile)

Chaque utilisateur suit ses propres appels d'offres, indépendamment du statut.

- Table `tender_follow (user_id, tender_id, created_at)` (migration V12), clé primaire sur les
  deux identifiants. L'utilisateur est toujours celui de la session.
- `PUT /api/tenders/{id}/follow` et `DELETE /api/tenders/{id}/follow` : réponse `204`, sans
  corps ; idempotents.
- Le champ `followed` des réponses est calculé pour l'utilisateur connecté.

## Onglets (`tab`)

Valeurs de `TenderTab` :

| Valeur | Contenu |
|--------|---------|
| `NEW` | retenu par le filtre, statut `NEW`, actif |
| `TO_PROCESS` | statut `TO_STUDY` ou `RESPONDING`, actif |
| `FOLLOWED` | suivi par l'utilisateur connecté |
| `ALL` | actif, sauf les avis écartés par le filtre au statut `NEW` |
| `DISMISSED` | statut `DISMISSED`, actif |
| `ARCHIVED` | non actif : clos, annulé ou sans suite (rubrique Archives) |
| `FILTERED_OUT` | écarté par le filtre, statut `NEW`, actif |

## `GET /api/tenders` : paramètres

Tous les paramètres se combinent. Les listes sont séparées par des virgules ; dans une liste,
il suffit qu'une valeur corresponde.

| Paramètre | Existe | Règle |
|-----------|--------|-------|
| `tab` | oui | voir ci-dessus, `NEW` par défaut |
| `q` | oui, en partie | **backend actuel** : chaque terme doit être contenu, sans tenir compte de la casse ni des accents, dans le titre, l'acheteur, la référence, la description, le lieu d'exécution ou les codes CPV ; pas de synonymes. **Cible** : chaque terme doit commencer un mot, sans tenir compte de la casse ni des accents, du titre, de l'acheteur, de la référence, de la description (existant) **et** de l'identifiant de l'avis, de la ville, du code postal, du type de marché, du libellé des types de travaux, des codes et libellés CPV (à ajouter) ; les synonymes métier comptent (voir plus bas) |
| `department` | oui | **liste** de codes ; un département de l'appel d'offres doit y figurer |
| `region` | oui | nom de région (voir `frontend/src/lib/regions.ts`) ; un département de l'appel d'offres doit en faire partie |
| `city` | oui, en partie | texte contenu dans le lieu d'exécution (fait), ou début du code postal (à faire) |
| `maxDistanceKm` | non | distance connue et inférieure ou égale |
| `activity` | non | liste de codes de types de travaux |
| `marketType` | oui | liste de `MarketType` |
| `year` | non | année de publication (`2024`) |
| `result` | non | `AWARDED`, `UNSUCCESSFUL`, `CANCELLED`, ou `UNKNOWN` pour une consultation passée sans résultat connu |
| `buyer` | non | texte contenu dans le nom de l'acheteur |
| `publishedWithinDays` | oui | publié depuis N jours au plus |
| `publishedFrom`, `publishedTo` | non | dates `yyyy-MM-dd` incluses |
| `deadlineFrom`, `deadlineTo` | non | dates `yyyy-MM-dd` incluses, en heure de Paris |
| `minDaysLeft` | oui | date limite au moins N jours après maintenant |
| `maxDaysLeft` | oui | date limite non dépassée et au plus N jours après maintenant |
| `minAmount` | oui | un montant inconnu ne masque pas l'appel d'offres |
| `maxAmount` | oui | idem (règle actuelle) |
| `amountKnown` | non | `true` : montant indiqué ; `false` : non indiqué |
| `procedure` | oui | liste de `ProcedureType` |
| `hasLots` | oui | `true` : au moins un lot ; `false` : aucun |
| `minLots` | non | nombre de lots minimum |
| `status` | oui | liste de `FollowUpStatus` |
| `followed` | non | `true` : suivis par l'utilisateur connecté |
| `alert` | non | identifiant d'une veille de l'utilisateur : ses critères s'ajoutent aux autres filtres |
| `sort` | oui, en partie | voir ci-dessous |
| `page`, `size` | oui | `size` de 1 à 100, l'interface propose 20, 50 et 100 |

### Synonymes métier

Un terme ou une expression du vocabulaire métier trouve aussi ses synonymes : « photovoltaïque »
trouve « solaire », « bornes de recharge » trouve « IRVE », « CVC » trouve « chauffage » et
« climatisation »… La liste est `SEARCH_SYNONYMS` dans `frontend/src/mock/reference.ts` ; le
backend devra la reprendre (table de synonymes ou dictionnaire de la recherche plein texte).

### Tri (`sort`)

Un `-` devant la clé trie dans l'ordre décroissant. Les valeurs absentes sont toujours à la fin ;
à égalité, l'identifiant le plus récent d'abord.

| Valeur | Ordre | Backend |
|--------|-------|---------|
| absent ou `deadline` | date limite la plus proche ; les dates limites **dépassées** passent après les autres, la plus récente d'abord | oui |
| `-deadline` | date limite la plus éloignée | oui |
| `-published`, `published` | plus récents, plus anciens | oui |
| `-relevance` | pertinence décroissante | non |
| `-amount`, `amount` | montant décroissant, croissant | oui |
| `distance` | plus proche du siège | non |

## Marchés attribués (`GET /api/awards`)

Une ligne par lot attribué (type `AwardedContract` de `frontend/src/types/award.ts`), extraite des
avis d'attribution déjà collectés : attributaire, montant, date, nombre d'offres reçues. La
réponse est une page (`Page<AwardedContract>`) complétée de `winnerCount` (attributaires
différents) et `totalAmount` (somme des montants connus) calculés sur toutes les lignes trouvées.

| Paramètre | Règle |
|-----------|-------|
| `q` | comme pour la liste des appels d'offres, sur l'objet, le lot, l'acheteur, l'attributaire, le lieu et les types de travaux |
| `winner`, `buyer` | texte contenu dans le nom de l'attributaire, de l'acheteur |
| `year` | année d'attribution |
| `activity`, `marketType`, `region`, `department`, `city` | comme pour la liste des appels d'offres |
| `minAmount`, `maxAmount` | montant attribué ; un montant inconnu ne masque pas la ligne |
| `sort` | `-awardedAt` par défaut, `awardedAt`, `-amount`, `amount` |
| `page`, `size` | comme pour la liste |

À faire côté backend : extraire l'attributaire, le montant et le nombre d'offres des avis
d'attribution BOAMP (ils sont conservés bruts dans `raw_notice`).

## Recherche dans le DCE (`GET /api/tenders/{id}/documents/search?q=`)

Renvoie les pages des documents de la **dernière version** du DCE qui contiennent tous les termes
cherchés, synonymes métier compris, dans l'ordre des documents puis des pages (type
`DocumentHit` : document, nom, type de pièce, numéro de page, extrait, `readable`). Le texte est
déjà stocké page par page dans `document_page` : une recherche plein texte PostgreSQL
(`to_tsvector('french', …)`) suffit. Plus tard, les pages scannées pourront être couvertes par
l'OCR.

## Veilles (`/api/alerts`)

Une veille est une recherche enregistrée par un utilisateur (type `Alert` de
`frontend/src/types/alert.ts`). Ses critères se combinent ; ses mots-clés sont des alternatives
(un seul suffit), avec les synonymes métier.

| Appel | Réponse |
|-------|---------|
| `GET /api/alerts` | les veilles de l'utilisateur, chacune avec `matchCount` (consultations de l'onglet `ALL` qui correspondent) et `newCount` (celles publiées après le jour de `lastViewedAt`) |
| `POST /api/alerts` | crée une veille (`AlertInput`) ; `lastViewedAt` vaut `null` |
| `PUT /api/alerts/{id}` | modifie une veille, dont `active` |
| `DELETE /api/alerts/{id}` | supprime une veille ; `204` |
| `POST /api/alerts/{id}/viewed` | l'utilisateur ouvre les résultats : `lastViewedAt` passe à maintenant |

Table à créer : `alert` (utilisateur, nom, critères, active, dates). Plus tard, le backend pourra
exécuter les veilles actives après chaque collecte et prévenir l'utilisateur.

## `GET /api/tenders/counts`

Accepte les mêmes paramètres que la liste (sauf `tab`, `page`, `size`, `sort`) et renvoie, pour
chaque onglet, le nombre d'appels d'offres qui correspondent : les compteurs des onglets suivent
ainsi la recherche et les filtres.

## `GET /api/tenders/status-counts`

Mêmes paramètres que la liste, `tab` compris (`ALL` par défaut), sauf `status` qui est ignoré. Renvoie le nombre
d'appels d'offres de chaque `FollowUpStatus` : Mes suivis l'appelle avec `tab=FOLLOWED` pour les
compteurs de ses onglets.

## Nouveaux champs

Dans la liste (`TenderSummary`) et le détail (`TenderDetail`). Tant qu'ils sont absents,
l'interface affiche « Non indiqué » ou « – ».

| Champ | Type | Origine prévue |
|-------|------|----------------|
| `reference` | texte ou `null` | **fourni** |
| `procedureType` | `ProcedureType` ou `null` | **fourni** |
| `source` | texte (`BOAMP`, `JOUE`…) | **fourni** |
| `lotCount` | nombre | **fourni** : nombre de lots |
| `activities` | liste de codes | déduits des préfixes CPV et des mots-clés (table de correspondance à paramétrer) |
| `distanceKm` | nombre ou `null` | distance entre le siège et la commune d'exécution (voir plus bas) |
| `relevanceScore` | 0 à 100 ou `null` | d'abord calculé par règles à partir des critères satisfaits, plus tard par l'analyse |
| `followed` | booléen | **fourni** : suivi de l'utilisateur connecté |
| `documentCount` | nombre | documents de la dernière version du DCE déposé |
| `postalCode` | texte ou `null` | code postal du lieu d'exécution |
| `result` | objet ou `null` | issue d'une consultation passée : `AWARDED`, `UNSUCCESSFUL` ou `CANCELLED`. **Fourni dans le détail** (avis d'attribution reçu, ou état de la consultation) ; dans la liste, les Archives se rabattent sur `status` |
| `cpv` (détail seulement) | liste `{ code, label }` | libellés de la nomenclature CPV |
| `awardCriteria` (détail seulement) | liste `{ label, weight }` | critères d'attribution et pondération en %, `null` si non pondéré |
| `keyInfo` (détail seulement) | objet ou `null` | visite (`required`, `date`), documents demandés, conditions particulières, qualifications, certifications ; repris de l'avis puis des valeurs validées de la fiche de synthèse |
| `winners` | liste de textes | attributaires d'une consultation attribuée (un par lot au plus) |
| `awardedAmount` | nombre ou `null` | total attribué, `null` si un montant manque |
| `platformName` (détail seulement) | texte ou `null` | nom de la plateforme de dépôt ; à défaut l'interface affiche l'adresse de `platformUrl` |

Codes de types de travaux utilisés par l'interface (`ACTIVITY_LABELS` dans
`frontend/src/lib/format.ts`) : `ELECTRICITY`, `PHOTOVOLTAIC`, `EV_CHARGING`, `HEATING`,
`AIR_CONDITIONING`, `ENERGY_RENOVATION`, `PUBLIC_LIGHTING`, `NETWORKS`, `VRD`, `MAINTENANCE`,
`STUDIES`. Un code inconnu de l'interface s'affiche tel quel : la liste peut s'enrichir côté
backend.

### Distance

Les données de démonstration prennent Marseille comme point de référence. Pour le backend :

1. ajouter l'adresse et les coordonnées du siège aux paramètres de l'entreprise ;
2. associer à chaque appel d'offres les coordonnées de sa commune d'exécution (référentiel des
   communes, par exemple le fichier officiel des communes avec leurs coordonnées) ;
3. calculer la distance à vol d'oiseau à l'enregistrement, ou à la volée.

Aucun service de géolocalisation externe n'est nécessaire.
