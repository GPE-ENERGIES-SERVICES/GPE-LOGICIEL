# Contrat d'API attendu par l'interface

L'interface fonctionne aujourd'hui sur des données de démonstration servies par une API
simulée (`frontend/src/lib/mock`). Cette API simulée répond aux **mêmes URL avec le même JSON**
que le backend devra fournir : ce document liste ce qui existe déjà dans le backend Spring Boot
et ce qu'il reste à y ajouter. Les types TypeScript de `frontend/src/lib/types.ts` font foi pour
le format des réponses ; `frontend/src/lib/mock/handler.ts` est une implémentation de référence
exécutable des règles ci-dessous.

## Passer des données de démonstration au backend

| Mode | Réglage |
|------|---------|
| Démonstration | `NEXT_PUBLIC_DATA_SOURCE=mock` dans `frontend/.env.local` |
| Backend réel  | variable absente (valeur par défaut, utilisée par l'image Docker) |

La variable est lue à la construction de l'interface (`npm run dev` ou `npm run build`). Aucun
écran ne sait d'où viennent les données : seul `lib/api.ts` aiguille les appels.

## Récapitulatif

| Appel | Backend actuel | À faire |
|-------|----------------|---------|
| `GET /api/tenders` | 5 filtres, tri fixe | onglets, filtres, tri, nouveaux champs (voir plus bas) |
| `GET /api/tenders/counts` | sans paramètre | accepter les mêmes filtres que la liste, nouveaux onglets |
| `GET /api/tenders/stats` | absent | **nouveau** : répartition pour le tableau de bord |
| `GET /api/tenders/{id}` | existe | nouveaux champs (voir plus bas) |
| `PUT /api/tenders/{id}/follow-up` | existe | accepter le statut `SUBMITTED` |
| `PUT /api/tenders/{id}/follow` | absent | **nouveau** : suivre (étoile), par utilisateur |
| `DELETE /api/tenders/{id}/follow` | absent | **nouveau** : ne plus suivre |
| Autres appels (connexion, DCE, analyse, paramètres) | existent | aucun changement |

## Statuts

`FollowUpStatus` gagne la valeur `SUBMITTED` (« Réponse envoyée ») :

| Valeur | Libellé |
|--------|---------|
| `NEW` | Nouveau |
| `TO_STUDY` | À analyser |
| `RESPONDING` | En préparation |
| `SUBMITTED` | Réponse envoyée |
| `DISMISSED` | Écarté |

La colonne `follow_up_status` est un `VARCHAR(20)` sans contrainte : seule l'énumération Java
change. Le statut reste partagé par toute l'équipe.

## Suivi personnel (étoile)

Chaque utilisateur suit ses propres appels d'offres, indépendamment du statut.

- Table à créer : `tender_follow (user_id, tender_id, created_at)`, clé primaire sur les deux
  identifiants.
- `PUT /api/tenders/{id}/follow` et `DELETE /api/tenders/{id}/follow` : réponse `204`, sans
  corps ; idempotents.
- Le champ `followed` des réponses est calculé pour l'utilisateur connecté.

## Onglets (`tab`)

`TenderTab` est remplacé par :

| Valeur | Contenu |
|--------|---------|
| `NEW` | retenu par le filtre, statut `NEW`, actif |
| `TO_PROCESS` | statut `TO_STUDY` ou `RESPONDING`, actif |
| `FOLLOWED` | suivi par l'utilisateur connecté |
| `ALL` | tout, sauf les avis écartés par le filtre au statut `NEW` |
| `DISMISSED` | statut `DISMISSED` |
| `ARCHIVED` | non actif (clos, annulé, sans suite), hors `DISMISSED` et hors écartés par le filtre au statut `NEW` |
| `FILTERED_OUT` | écarté par le filtre, statut `NEW`, actif |

## `GET /api/tenders` : paramètres

Tous les paramètres se combinent. Les listes sont séparées par des virgules ; dans une liste,
il suffit qu'une valeur corresponde.

| Paramètre | Existe | Règle |
|-----------|--------|-------|
| `tab` | oui (valeurs à changer) | voir ci-dessus, `NEW` par défaut |
| `q` | oui | chaque mot doit apparaître, sans tenir compte de la casse ni des accents, dans le titre, l'acheteur, la référence, la description (existant) **et** l'identifiant de l'avis, le lieu, le libellé des types de travaux (à ajouter) |
| `department` | oui (un seul) | **liste** de codes ; un département de l'appel d'offres doit y figurer |
| `region` | non | nom de région (voir `frontend/src/lib/regions.ts`) ; un département de l'appel d'offres doit en faire partie |
| `city` | non | texte contenu dans le lieu d'exécution |
| `maxDistanceKm` | non | distance connue et inférieure ou égale |
| `activity` | non | liste de codes de types de travaux |
| `publishedWithinDays` | non | publié depuis N jours au plus |
| `publishedFrom`, `publishedTo` | non | dates `yyyy-MM-dd` incluses |
| `deadlineFrom`, `deadlineTo` | non | dates `yyyy-MM-dd` incluses, en heure de Paris |
| `minDaysLeft` | oui | date limite au moins N jours après maintenant |
| `maxDaysLeft` | non | date limite non dépassée et au plus N jours après maintenant |
| `minAmount` | non | un montant inconnu ne masque pas l'appel d'offres |
| `maxAmount` | oui | idem (règle actuelle) |
| `amountKnown` | non | `true` : montant indiqué ; `false` : non indiqué |
| `procedure` | non | liste de `ProcedureType` |
| `hasLots` | non | `true` : au moins un lot ; `false` : aucun |
| `minLots` | non | nombre de lots minimum |
| `status` | non | liste de `FollowUpStatus` |
| `followed` | non | `true` : suivis par l'utilisateur connecté |
| `sort` | non | voir ci-dessous |
| `page`, `size` | oui | `size` de 1 à 100, l'interface propose 20, 50 et 100 |

### Tri (`sort`)

Un `-` devant la clé trie dans l'ordre décroissant. Les valeurs absentes sont toujours à la fin ;
à égalité, l'identifiant le plus récent d'abord.

| Valeur | Ordre |
|--------|-------|
| absent ou `deadline` | date limite la plus proche ; les dates limites **dépassées** passent après les autres, la plus récente d'abord |
| `-deadline` | date limite la plus éloignée |
| `-published`, `published` | plus récents, plus anciens |
| `-relevance` | pertinence décroissante |
| `-amount`, `amount` | montant décroissant, croissant |
| `distance` | plus proche du siège |

## `GET /api/tenders/counts`

Accepte les mêmes paramètres que la liste (sauf `tab`, `page`, `size`, `sort`) et renvoie, pour
chaque onglet, le nombre d'appels d'offres qui correspondent : les compteurs des onglets suivent
ainsi la recherche et les filtres.

## `GET /api/tenders/stats` (nouveau)

Répartition des appels d'offres **en cours** (actifs, hors écartés par le filtre au statut `NEW`) :

```json
{
  "byStatus": { "NEW": 27, "TO_STUDY": 14, "RESPONDING": 9, "SUBMITTED": 8, "DISMISSED": 10 },
  "byActivity": { "ELECTRICITY": 23, "PHOTOVOLTAIC": 11 },
  "byDepartment": { "13": 31, "83": 12 }
}
```

Un appel d'offres compte une fois dans chacun de ses types de travaux et départements.

## Nouveaux champs

Dans la liste (`TenderSummary`) et le détail (`TenderDetail`). Tant qu'ils sont absents,
l'interface affiche « Non indiqué » ou « – ».

| Champ | Type | Origine prévue |
|-------|------|----------------|
| `reference` | texte ou `null` | déjà dans le détail, à ajouter à la liste |
| `procedureType` | `ProcedureType` ou `null` | déjà dans le détail, à ajouter à la liste |
| `source` | texte (`BOAMP`, `JOUE`…) | déjà dans le détail, à ajouter à la liste |
| `lotCount` | nombre | nombre de lots |
| `activities` | liste de codes | déduits des préfixes CPV et des mots-clés (table de correspondance à paramétrer) |
| `distanceKm` | nombre ou `null` | distance entre le siège et la commune d'exécution (voir plus bas) |
| `relevanceScore` | 0 à 100 ou `null` | d'abord calculé par règles à partir des critères satisfaits, plus tard par l'analyse |
| `followed` | booléen | suivi de l'utilisateur connecté |
| `documentCount` | nombre | documents de la dernière version du DCE déposé |
| `conditions` (détail seulement) | objet ou `null` | qualifications, visite, critères d'attribution et contraintes, repris des valeurs validées de la fiche de synthèse |

Codes de types de travaux utilisés par l'interface (`ACTIVITY_LABELS` dans
`frontend/src/lib/format.ts`) : `ELECTRICITY`, `PHOTOVOLTAIC`, `EV_CHARGING`, `HVAC`,
`ENERGY_RENOVATION`, `PUBLIC_LIGHTING`, `VRD`, `MAINTENANCE`, `STUDIES`. Un code inconnu de
l'interface s'affiche tel quel : la liste peut s'enrichir côté backend.

### Distance

Les données de démonstration prennent Marseille comme point de référence. Pour le backend :

1. ajouter l'adresse et les coordonnées du siège aux paramètres de l'entreprise ;
2. associer à chaque appel d'offres les coordonnées de sa commune d'exécution (référentiel des
   communes, par exemple le fichier officiel des communes avec leurs coordonnées) ;
3. calculer la distance à vol d'oiseau à l'enregistrement, ou à la volée.

Aucun service de géolocalisation externe n'est nécessaire.
