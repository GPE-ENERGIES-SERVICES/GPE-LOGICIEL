# Sources d'avis de marchés publics : audit et intégration

État au 21/10/2026, révisé le même jour pour un **usage interne et non commercial** : GPE est
réservé à l'équipe de l'entreprise, n'est ni vendu ni proposé à des tiers, et ne redistribue
aucune donnée. Faits vérifiés le jour même (pages officielles, CGU, mentions légales, robots.txt,
requêtes manuelles) ; les hypothèses sont signalées comme telles. Le simple fait qu'une page soit
publique n'est jamais pris pour une autorisation de collecte automatisée.

## Classement

- **A** : API ou flux officiel documenté et exploitable.
- **B** : méthode publique vérifiée, autorisée et suffisamment stable.
- **C** : compte, partenariat, contrat ou autorisation nécessaire.
- **D** : méthode non confirmée, instable, bloquée ou plateforme indisponible.

## Sources affichées par Nukema

Relevé sur `accueil.nukema.com` (`www.nukema.com` redirige) : 14 plateformes, dont 4 absentes de
la liste de départ (Arnia, Megalis, AMPA, SafeTender). Modula, EU-Supply et APProch n'y figurent
pas. Nukema est un service de veille (agrégateur), pas une source.

## Ce que change l'usage interne non commercial

Trois types de restrictions sont à distinguer :

1. **Commercialisation** (revente, service à des clients, prestataire de veille) : ne concerne pas
   GPE.
2. **Redistribution** (republication, diffusion à des tiers) : ne concerne pas GPE, qui ne montre les
   avis qu'à son équipe.
3. **Collecte automatisée ou reproduction** (robots, extraction, copie dans une base) : concerne
   GPE, qui copie les avis dans sa base interne par un programme.

Résultat : la plupart des clauses ne visent pas seulement la commercialisation, mais toute
reproduction ou toute extraction automatisée, « à des fins commerciales ou non ». L'usage interne
ne lève donc l'obstacle que pour **AWS**, dont les règles sont des seuils de volume, et seulement
partiellement (voir plus bas). Une utilisation professionnelle pour répondre à des marchés n'est
pas non plus une « utilisation personnelle non commerciale » au sens de Marchés sécurisés.

| Plateforme | Commercialisation | Redistribution | Collecte automatisée ou reproduction | Accès officiel trouvé | Conclusion pour GPE |
|---|---|---|---|---|---|
| PLACE | — | — | « toute reprise totale ou partielle est interdite sans l'autorisation expresse et écrite du directeur » (pas de distinction d'usage) | Flux RSS Atexo depuis « mon compte » (guide Local Trust MPE du 26/01/2026) : compte entreprise requis | **C** : autorisation écrite, ou au minimum compte entreprise + accord sur l'usage du flux RSS. Site en 503 l'après-midi du 21/10 |
| Maximilien, Megalis, Arnia, AMPA (et toutes les instances Atexo testées) | non lu | non lu | non lu (sites hors ligne) | Même flux RSS Atexo sur compte | **D** aujourd'hui (maintenance Atexo sur toutes les instances testées le 21/10), puis **C** |
| AWS (marches-publics.info) | Le téléchargement massif est interdit « notamment par un prestataire de veille » ; flux nocturne payant pour les prestataires | — | Massif = plus de 100 avis vus par heure ou 500 par jour (CGU 2.2) ; rien n'interdit une lecture automatisée en dessous ; rien ne l'autorise expressément | Aucun (ni API, ni RSS, ni export). Recherche publique stable (formulaire, pagination, pages d'avis) | **C, connecteur prêt et désactivé** : seuils respectés par construction ; confirmation écrite d'AW Solutions à obtenir avant activation |
| achatpublic.com | — | — | « Toute utilisation ou reproduction […] par quelque procédé que ce soit, est strictement interdite […] sauf accord préalable et écrit », demande par courrier : 20 rue des Aqueducs, 94250 Gentilly ; robots.txt `Disallow: /` pour tous | Aucun trouvé | **C** : accord écrit par courrier |
| Dematis (e-marchespublics) | — | — | « tout système ou logiciel automatisés visant à extraire et exploiter tout ou partie du Contenu, à des fins commerciales ou non, est interdite » | Aucun | **C** : l'usage interne est explicitement couvert par l'interdiction |
| Marchés sécurisés (Atline) | Usage « personnel non commercial » seulement, « en aucun cas dans un but lucratif ou commercial » | Ni vente, ni distribution, ni copie | Mode automatisé au-delà des seuils (5 téléchargements par jour et par utilisateur) : accord préalable d'Atline, éventuellement sous licence | Aucun ; l'application appelle une API interne non documentée, non utilisée | **C** : usage professionnel, pas personnel ; accord d'Atline |
| AJI | « Vous ne devez utiliser aucun contenu […] sans l'autorisation du propriétaire » | Ni copie ni distribution | Accès seulement par « l'interface et les instructions que nous mettons à votre disposition » | Aucun | **C** (site injoignable le 21/10) |
| Modula | — | — | « Toute reproduction ou représentation […] par quelque procédé que ce soit, est interdite » | Aucun | **C** |
| Klekoon | Aucune CGU publiée trouvée | — | — | Aucun ; c'est aussi un service de veille concurrent (droit du producteur de base de données) | **C** : pas de collecte sans accord |
| SafeTender (Omnikles) | CGU entreprises non trouvées | — | Le portail charge un reCAPTCHA Google | Aucun | **D** : protection anti-robot, jamais interrogé |
| xmarches | Aucune CGU lisible | — | — | — | **D** : service suspendu le 21/10 (avis de sécurité) |
| EU-Supply | non approfondi | — | — | — | **D** : absent de Nukema, usage français marginal |

## Tableau de synthèse

| Source | URL vérifiée | Méthode | Classe | Intégration | Avis récupérés au test | Part des DCE des 729 consultations retenues | Couverture complémentaire BOAMP/TED |
|---|---|---|---|---|---|---|---|
| BOAMP | API DILA | API open data | A | En service (existant) | — (aucune collecte du 12/10 au 21/10 : machine éteinte, rattrapage automatique) | — | Référence |
| TED | api.ted.europa.eu | API Search v3 | A | En service (existant) | — | — | 295 consultations TED seules |
| APProch | projets-achats.marches-publics.gouv.fr ; data.economie.gouv.fr | Export open data, Licence Ouverte 2.0 | A | **Fait, testé, désactivé** | 10 744 projets réels (base jetable), 0 erreur, 0 doublon | — | Projets avant l'avis (pas des avis) |
| AWS | www.marches-publics.info | Recherche publique limitée (travaux) | C | **Prêt, testé hors ligne, désactivé** | 0 en automatique (non autorisé à ce stade) ; 17 avis relevés à la main | 9,3 % | Environ 70 % des avis de travaux relevés absents du BOAMP (4 ou 5 sur 17, estimation) |
| PLACE | www.marches-publics.gouv.fr | Flux RSS sur compte | C | Non | 0 | 12,3 % | Non mesurable sans accès |
| achatpublic | www.achatpublic.com | — | C | Non | 0 | 10,7 % | Non mesurable |
| Marchés sécurisés | www.marches-securises.fr | — | C | Non | 0 | 7,0 % | Non mesurable |
| Dematis | www.e-marchespublics.com | — | C | Non | 0 | 6,3 % | Non mesurable |
| Megalis / Maximilien / Arnia / AMPA | instances Atexo | Flux RSS sur compte | D puis C | Non | 0 | 3,6 / 2,2 / 0,1 / 0,5 % | Non mesurable |
| xmarches | www.xmarches.fr | — | D | Non | 0 | 1,8 % | — |
| SafeTender | marchespublics-smica.safetender.com | — | D | Non | 0 | 1,1 % | — |
| Klekoon | www.klekoon.com | — | C | Non | 0 | 0,5 % | — |
| AJI | aji-france.com | — | C | Non | 0 | 0 % | — |
| Modula | modula-demat.com (`modula.fr` : domaine à vendre) | — | C | Non | 0 | non mesurée | — |
| EU-Supply | eu.eu-supply.com | — | D | Non | 0 | 0 % | — |

Pourcentages : part des 729 consultations retenues (base réelle, lecture seule, non fusionnées)
ayant un lien DCE sur la plateforme ; 267 n'ont aucun lien. Ces consultations arrivent déjà par le
BOAMP ou TED : la plateforme héberge leur DCE.

## Intégrations

### APProch (fait, désactivé)

Voir `docs/technique.md`, section « APProch ». `collect/approch/`, `ProcedureType.PURCHASE_PROJECT`.
25 tests ; collecte réelle sur base jetable : 10 744 projets, 0 erreur, 0 doublon à la deuxième
collecte. Activation : décision de l'équipe (700 à 850 projets retenus d'un coup).

### AWS / marches-publics.info (prêt, désactivé)

Voir `docs/technique.md`, section « AWS ». `collect/awsolutions/` : lecture de la recherche publique
(travaux, parus aujourd'hui ou hier), pause de 40 s entre deux pages, arrêt à 90 pages par heure et
450 par jour (sous les 100 et 500 des CGU), page d'un avis lue une seule fois, identifiant de
consultation de la plateforme (IDM) et lien DCE conservés, rapprochement avec le BOAMP proposé (jamais
fusionné) quand la référence d'acheteur et la date limite sont identiques. 32 tests (vraies pages
relevées à la main, serveur simulé, PostgreSQL jetable, désactivation, variables d'environnement).

**État de l'autorisation** : l'entreprise indique avoir obtenu, le 21/10/2026, une confirmation
écrite d'AW Solutions pour la collecte prévue. Son texte n'est pas encore versé au projet : ses
conditions (volume, horaires, natures d'avis, identification, durée) doivent être comparées au
fonctionnement ci-dessus avant toute collecte réelle. Configuration d'activation, affichage de la
règle `SAME_REFERENCE` et procédure de première collecte contrôlée : `docs/technique.md`, section
« AWS ».

### Atexo (PLACE et plateformes régionales) : non préparé

Le flux RSS Atexo est un accès officiel, mais réservé aux comptes entreprise, et son format n'a pas
pu être vu (aucun exemple public ; toutes les instances étaient hors ligne le 21/10). Écrire un
parseur sans exemple réel serait une supposition. **Ce qu'il faut** : un compte entreprise GPE sur
PLACE (et les plateformes régionales utiles), l'adresse d'un flux RSS créé depuis « mon compte », et
pour PLACE l'accord écrit de la DAE sur cet usage, compte tenu de la clause de reprise. Avec ces
éléments, un connecteur `AUTHENTICATED` s'ajoute sans changer la collecte.

## Préparation de la récupération des DCE

En place : `dce_link` (adresse, plateforme, source de l'avis, statut), `dossier.origin`, contrat
`document/DceRetriever`. APProch ajoute le lien vers la consultation publiée (309 projets) ; AWS garde
le lien DCE avec l'identifiant de consultation de la plateforme (IDM). Le téléchargement automatique
des DCE n'est pas couvert par une éventuelle confirmation sur les avis : les CGU d'AWS le limitent à
10 DCE par heure ou 20 par jour, et celles d'Atline exigent un accord pour tout robot.

## Prochaines étapes

1. Verser au projet la confirmation écrite d'AW Solutions, vérifier ses conditions, tester AWS sur
   base jetable, puis décider de son activation.
2. Décider de l'activation d'APProch.
3. Ouvrir un compte entreprise sur PLACE et demander l'accord écrit de la DAE ; relire les CGU des
   plateformes Atexo régionales à leur retour en ligne.
4. Demandes d'accord écrites : achatpublic (par courrier), Atline (Marchés sécurisés), Dematis.
5. Hors périmètre « avis » mais ouvert (A) : les données essentielles (DECP) pour l'écran des
   marchés attribués.
