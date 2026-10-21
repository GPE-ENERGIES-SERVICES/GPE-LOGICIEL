# Sources d'avis de marchés publics : audit et intégration

État au 21/10/2026. Faits vérifiés le jour même (pages officielles, CGU, robots.txt, appels réels) ;
les hypothèses sont signalées comme telles.

## Classement

- **A** : API ou flux officiel documenté et exploitable.
- **B** : méthode publique vérifiée, autorisée et suffisamment stable.
- **C** : compte, partenariat, contrat ou autorisation nécessaire.
- **D** : méthode non confirmée, instable, bloquée ou plateforme indisponible.

## Sources affichées par Nukema

Relevé sur `accueil.nukema.com` (`www.nukema.com` redirige) : 14 plateformes, dont 4 absentes de
la liste de départ (Arnia, Megalis, AMPA, SafeTender). Modula, EU-Supply et APProch n'y figurent
pas. Nukema est un service de veille (agrégateur), pas une source.

## Tableau

| Source | URL vérifiée | Nature | Méthode étudiée | Classe | Intégration | Avis récupérés au test | Couverture complémentaire BOAMP/TED | Limites, autorisation nécessaire |
|---|---|---|---|---|---|---|---|---|
| BOAMP | boamp-datadila.opendatasoft.com (API DILA) | Journal officiel | API open data | A | En service (existant) | — (aucune collecte depuis le 12/10 : machine éteinte, rattrapage automatique au prochain créneau) | Référence | — |
| TED | api.ted.europa.eu | Journal officiel UE | API Search v3 | A | En service (existant) | — | 295 consultations TED seules (base réelle) ; 1 203 avis TED rattachés au BOAMP | — |
| APProch | projets-achats.marches-publics.gouv.fr ; open data data.economie.gouv.fr | Projets d'achats prévisionnels (État, collectivités, hôpitaux) | Export open data Opendatasoft, Licence Ouverte 2.0 ; noms d'acheteurs par l'API Recherche d'entreprises | A | **Connecteur fait, testé, désactivé par défaut** | 10 744 projets réels, 0 erreur, 2e collecte 0 doublon (base jetable) | Nouvelle information (avant l'avis), pas des avis ; aucun identifiant commun avec BOAMP/TED | Pas de date limite, acheteur par SIREN, 35 % « France entière », 700 à 850 projets retenus avec les critères actuels |
| PLACE | www.marches-publics.gouv.fr | Profil d'acheteur de l'État (Atexo) | Flux RSS Atexo, pages publiques | C | Non | 0 | DCE de 12,3 % des consultations retenues | CGU : « toute reprise totale ou partielle est interdite sans l'autorisation expresse et écrite du directeur » ; la page de flux RSS exige une connexion entreprise. Autorisation écrite de la DAE à demander |
| Maximilien | marches.maximilien.fr | Profil d'acheteur mutualisé IDF (Atexo) | Comme PLACE | C (D aujourd'hui) | Non | 0 | 2,2 % | En maintenance le 21/10 (page Atexo) ; flux RSS Atexo sur compte ; CGU à relire au retour en ligne |
| Megalis | marches.megalis.bretagne.bzh | Profil mutualisé Bretagne (Atexo) | Comme PLACE | C (D aujourd'hui) | Non | 0 | 3,6 % | En maintenance le 21/10 |
| Arnia | marches.ternum-bfc.fr | Profil mutualisé Bourgogne-Franche-Comté (Atexo) | Comme PLACE | C (D aujourd'hui) | Non | 0 | 0,1 % | En maintenance le 21/10 |
| AMPA | demat-ampa.fr | Profil mutualisé (Atexo) | Comme PLACE | D | Non | 0 | 0,5 % | Connexion refusée / maintenance le 21/10 |
| AWS (marches-publics.info) | www.marches-publics.info (AW Solutions) | Profil d'acheteur | Pages publiques ; service payant de transfert | C | Non | 0 | 9,3 % | CGU 2.2 : plus de 100 avis par heure ou 500 par jour = téléchargement massif interdit, blocage IP ; « service payant de transfert du contenu de la plateforme en heures creuses » : à contractualiser |
| achatpublic.com | www.achatpublic.com (Infopro Digital) | Profil d'acheteur | Pages publiques | C | Non | 0 | 10,7 % | robots.txt : `Disallow: /` ; mentions légales : toute reproduction interdite sans autorisation écrite. Partenariat à demander |
| Dematis (e-marchespublics) | www.e-marchespublics.com | Profil d'acheteur | Pages publiques | C | Non | 0 | 6,3 % | CGU Dematis : « L'utilisation de tout système ou logiciel automatisés visant à extraire et exploiter tout ou partie du Contenu […] est interdite » |
| Marchés sécurisés | www.marches-securises.fr (Atline Services) | Profil d'acheteur | Application Angular, pages publiques | C | Non | 0 | 7,0 % | CGU : usage personnel non commercial ; tout usage automatisé ou au-delà des seuils « suppose l'accord préalable de la société ATLINE SERVICES […] soumis à licence » |
| Klekoon | www.klekoon.com | Profil d'acheteur **et** service de veille (agrège BOAMP, JOUE, presse) | Pages HTML ; aucune API ni flux trouvé | C | Non | 0 | 0,5 % | Aucune CGU publiée trouvée ; base d'un concurrent de veille (droit du producteur de base de données) : accord à demander, pas de collecte |
| AJI | aji-france.com (mapa.aji-france.com) | Profil d'acheteur des EPLE (MAPA) | Pages publiques | C | Non | 0 | non mesurée (aucun lien AJI dans les consultations retenues) | CGU : interdiction d'accéder « en utilisant une méthode autre que l'interface et les instructions que nous mettons à votre disposition » ; TLS non joignable depuis ce poste le 21/10 |
| xmarches | www.xmarches.fr | Profil d'acheteur | — | D | Non | 0 | 1,8 % | Plateforme suspendue le 21/10 (avis de sécurité affiché) ; robots.txt `Allow: /` mais aucune CGU lisible |
| SafeTender | marchespublics-smica.safetender.com (Omnikles) | Profil d'acheteur | Application web | C | Non | 0 | 1,1 % | Aucune API publique ni flux trouvé ; CGU entreprises non trouvées (seules des conditions de service clients Omnikles sont publiées, non lues) : autorisation à demander |
| Modula | modula-demat.com (instances `publisher_portail`, ex. marchespublics.pro) | Profil d'acheteur | Pages publiques | C | Non | 0 | non mesurée | `modula.fr` est un domaine à vendre ; infos légales : toute reproduction « par quelque procédé que ce soit » interdite |
| EU-Supply | eu.eu-supply.com (Mercell) | Plateforme européenne | — | D | Non | 0 | 0 consultation retenue avec un lien EU-Supply | Non affichée par Nukema ; usage français marginal ; pas d'API publique trouvée (non approfondi) |

Pourcentages : part des 729 consultations retenues (base réelle, non fusionnées) ayant un lien DCE
sur la plateforme ; 267 n'ont aucun lien. Ces consultations arrivent déjà par le BOAMP ou TED : la
plateforme héberge leur DCE, pas un avis supplémentaire.

## Ce que cela veut dire

- Aucune plateforme de profil d'acheteur n'offre aujourd'hui une méthode ouverte et autorisée de
  reprise de ses avis. Les avis de plus de 90 000 € HT (et tous les avis formalisés) sont de toute
  façon publiés au BOAMP et/ou au JOUE, déjà collectés. La couverture complémentaire possible porte
  sur les MAPA de moins de 90 000 € publiés seulement sur le profil d'acheteur (environ 40 % des
  marchés de GPE selon les données essentielles, mesure du 12/10/2026).
- Les gains réalistes passent par des **accords** : priorité aux plateformes qui portent le plus de
  DCE des consultations de GPE (PLACE, achatpublic, AWS, Marchés sécurisés, Dematis). Pour chaque
  accord, un connecteur `AUTHENTICATED` (identifiants dans l'environnement) s'ajoute sans changer la
  collecte.
- APProch est la seule source ouverte supplémentaire : elle annonce des projets avant l'avis.

## Intégration réalisée : APProch

Voir `docs/technique.md`, section « APProch ». Code : `collect/approch/` (client, connecteur,
parseur), `SourceParser.republishesChangedNotices`, `ProcedureType.PURCHASE_PROJECT`, libellé
frontend. Désactivé par défaut (`collect.disabled-sources=APPROCH`).

Tests : 25 tests ajoutés (parseur sur projets réels, client HTTP avec erreurs serveur, réponse
inattendue et reprise, collecte complète sur PostgreSQL jetable : création, deux collectes sans
doublon, version modifiée appliquée, version ancienne lue après une récente, projet clos ignoré,
données de l'équipe préservées, coexistence avec le BOAMP sans rapprochement, désactivation par
défaut) ; suite complète du backend : 280 tests verts. Collecte réelle sur base jetable : voir
`docs/technique.md`.

## Préparation de la récupération des DCE

Déjà en place : `dce_link` (adresse, plateforme, source de l'avis, statut), `dossier.origin`,
contrat `document/DceRetriever`. APProch y ajoute le lien vers la consultation publiée quand
l'acheteur l'a donné (309 projets, PLACE le plus souvent). Une récupération automatique par
plateforme ne pourra être écrite qu'avec l'accord de la plateforme ; sans accord, le lien reste
`AVAILABLE` (téléchargement manuel).

## Prochaines étapes

1. Décider de l'activation d'APProch (volume : 700 à 850 projets retenus d'un coup), après
   sauvegarde ; aucun changement de schéma n'est nécessaire (pas de migration).
2. Demander les autorisations écrites, dans l'ordre : PLACE (DAE), achatpublic, AWS (service de
   transfert payant), Atline (Marchés sécurisés), Dematis.
3. Relire les CGU de Maximilien, Megalis, Arnia et AMPA à leur retour en ligne ; vérifier si un
   compte entreprise donne droit au flux RSS Atexo pour un usage automatisé.
4. Hors périmètre « avis » mais ouvert (A) : les données essentielles (DECP) pour l'écran des
   marchés attribués.
