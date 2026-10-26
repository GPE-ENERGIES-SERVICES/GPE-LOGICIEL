# Récupération des DCE : audit

État au 26/10/2026. Complète `docs/audit-sources-avis.md` (CGU relues le 21/10/2026, reprises ici
sans être relues). Faits vérifiés le jour même sur la base locale (2 469 consultations ouvertes, 3
veilles actives) et par des requêtes manuelles : une à trois par plateforme, espacées de quelques
secondes, sans aucun formulaire envoyé. Les hypothèses sont signalées comme telles.

Deux règles ont borné l'audit et bornent toute suite :

- **un CAPTCHA n'est jamais contourné** : la plateforme qui en présente un au retrait du DCE ne
  peut pas être automatisée, quel que soit l'accord ;
- **un programme n'accepte pas des CGU à la place de l'entreprise** : quand le retrait passe par
  la case « j'accepte les conditions générales », l'automatiser demande l'accord écrit de la
  plateforme (ou de son gestionnaire) sur cet usage.

## En bref

| Question | Réponse |
|---|---|
| Chaque avis a-t-il un lien ? | Oui pour 2 459 consultations ouvertes sur 2 469 (99,6 %). |
| Ce lien mène-t-il au DCE ? | Pour 1 623 sur 2 477 consultations BOAMP et TED ouvertes au moment de la mesure (66 %). Les autres ne donnent que l'accueil de la plateforme : **l'avis publié ne contient rien de plus précis**. |
| Peut-on télécharger les DCE des veilles automatiquement aujourd'hui ? | **Non, sur aucune plateforme** : soit un CAPTCHA l'empêche, soit l'accord écrit manque. Aucun connecteur n'existe. |
| Où est-ce techniquement possible ? | Sur les plateformes **Atexo** (PLACE, Maximilien, Mégalis, AMPA…) : retrait anonyme sans CAPTCHA, environ un quart des liens des veilles. Il faut l'accord écrit. |
| Et AWS (marches-publics.info) ? | **Impossible** : CAPTCHA (image + hCaptcha) au retrait anonyme comme au retrait identifié. Premier volume des veilles (31 %). |

## 1. Les liens vers le DCE

### Ce qui a été corrigé le 26/10/2026

- **346 avis BOAMP sans lien** : l'adresse des documents (`urlDocConsul`, anciens formats MAPA et
  FNSimple) n'était pas lue. Lue depuis, et reprise pour les avis déjà collectés (migration V25).
  BOAMP : de 1 734 à 2 080 consultations avec lien sur 2 088.
- **108 liens Atexo à l'ancien format** (`?page=Entreprise.EntrepriseAdvancedSearch&AllCons&id=…`)
  ouvraient une recherche au lieu de la consultation. L'interface les ouvre au format actuel
  `/entreprise/consultation/{id}?orgAcronyme=…` (vérifié sur PLACE et adullact) ; l'adresse
  enregistrée ne change pas, pour ne pas créer de lien en double à la collecte suivante.
- Les liens des 40 consultations de la veille de test ont tous été ouverts : 36 mènent à la
  consultation, 4 à l'accueil de la plateforme.

### Ce qui reste : environ 850 avis sans lien direct

| Source | Consultations ouvertes | Lien direct | Accueil de plateforme seulement |
|---|---|---|---|
| BOAMP | 2 060 | 1 371 | 689 |
| TED | 417 | 252 | 165 |

Plateformes les plus concernées : marches-securises.fr (176, presque aucun lien direct : les avis
donnent `https://www.marches-securises.fr`), marches-publics.gouv.fr (154), achatpublic.com (74),
SafeTender (54, aucun lien direct).

Ce n'est plus une erreur de lecture : l'avis ne contient que cette adresse. Obtenir le lien direct
demande de **chercher la consultation sur la plateforme** (référence de l'acheteur, nom de
l'acheteur, date limite), donc un accès automatisé à sa recherche : mêmes CGU que pour les avis
(`docs/audit-sources-avis.md`), même besoin d'accord. Rien n'est fait en ce sens.

## 2. Le téléchargement automatique des DCE

### Ce qui est prêt

Toute la chaîne existe et est testée : éligibilité par veille (`eligibility/`), file d'attente,
nouvelles tentatives, quota de 3 Go, accès HTTP protégé (`SafeHttpFetcher`), validation des
fichiers, dossier, classement des pièces, texte page par page. **Il manque le connecteur de chaque
plateforme** (`DceRetriever`) : aucun n'existe hors des tests. Réglages : `dce.retrieval.worker-enabled`
(faux) et `dce.retrieval.allowed-platforms` (vide).

Deux limites du client HTTP actuel, à lever pour tout connecteur réel : il ne fait que des `GET`
et ne garde aucun cookie, alors que les retraits passent par un formulaire (`POST`) et une session.

### Plateforme par plateforme

Volumes : liens des consultations ouvertes ; « veilles » = liens des consultations éligibles (493
consultations correspondent à au moins une veille active).

| Plateforme | Liens | Directs | Dans les veilles | Retrait du DCE constaté | CGU (21/10) | Verdict |
|---|---|---|---|---|---|---|
| **AWS** (marches-publics.info, aws-achat) | 800 | 765 | **155 (31 %)** | Retrait identifié **et** anonyme derrière un CAPTCHA image + hCaptcha | Seuils de volume ; DCE limités à 10 par heure ou 20 par jour | **Impossible** sans accès officiel |
| **PLACE** (marches-publics.gouv.fr) | 369 | 212 | 70 | Page de retrait sans CAPTCHA ; choix « télécharger anonymement » ; nom, prénom, e-mail, raison sociale ; **case d'acceptation des CGU** | « Toute reprise totale ou partielle est interdite sans l'autorisation expresse et écrite du directeur » | **Possible techniquement, accord écrit requis** |
| Autres instances **Atexo** (Maximilien, AMPA, 596280, Mégalis, Alsace, Ternum…) | 270 | 191 | 55 | Même formulaire que PLACE (vérifié sur Maximilien), pas de CAPTCHA sur les pages vues | Non relues (sites hors ligne le 21/10) | **Possible techniquement**, CGU à relire et accord de chaque gestionnaire |
| **achatpublic.com** | 238 | 164 | 40 | hCaptcha présent sur la fiche de la consultation | Reproduction interdite sauf accord écrit ; `robots.txt` : `Disallow: /` | **Impossible** en l'état |
| **Dematis** (e-marchespublics) | 208 | 158 | 38 | Lien de retrait non trouvé dans la page reçue (contenu probablement chargé en JavaScript) ; pas de CAPTCHA vu | Extraction automatisée interdite, « à des fins commerciales ou non » | **Interdit** sauf accord ; flux non vérifié |
| **Marchés sécurisés** | 188 | 3 | 53 | Les avis ne donnent que l'accueil ou la page de l'acheteur : pas de consultation à ouvrir | Usage personnel seulement ; robot au-delà de 5 téléchargements par jour : accord d'Atline | **Bloqué deux fois** : pas de lien direct, accord requis |
| SafeTender | 54 | 0 | 8 | reCAPTCHA Google sur les portails | Non trouvées | **Impossible** |
| xmarches | 45 | 37 | 10 | Lien « Télécharger le règlement » avec confirmation ; retrait du DCE non vérifié | Aucune CGU lisible | À vérifier (service suspendu le 21/10, de nouveau en ligne le 26/10) |
| Klekoon | 13 | 3 | 3 | reCAPTCHA Google | Aucune CGU ; service de veille concurrent | **Impossible** |
| BravoSolution (SNCF, RATP), EDF/Enedis | ~35 | 1 | ~10 | Non vérifié (portails acheteurs, compte fournisseur probable : hypothèse) | Non lues | Hors périmètre pour l'instant |

### AWS : correction d'une affirmation

Le code (`DceRetrievalProperties.REFUSED_PLATFORMS`) indique qu'AW Solutions a « refusé » l'accès
automatisé le 21/10/2026. **Aucun document du projet ne contient ce refus** : la documentation parle
au contraire d'une confirmation écrite d'AW Solutions, pour la collecte des avis, dont le texte n'est
pas versé au projet. Le blocage reste justifié, mais pour une autre raison, constatée ici : le
CAPTCHA au retrait. Le commentaire du code est à corriger dans ce sens, et le texte de la
confirmation d'AW Solutions à verser au projet.

La seule voie pour AWS est un accès fourni par AW Solutions : les mentions légales citent un
**flux nocturne payant pour les prestataires** ; reste à savoir s'il contient les DCE.

## 3. Comment font les services de veille

Nukema affiche 14 plateformes (`docs/audit-sources-avis.md`). Leur méthode n'est pas publique.
Hypothèses, non vérifiées : accords ou licences avec les plateformes (le flux payant d'AWS en est un
exemple), comptes entreprise et flux RSS des plateformes Atexo, partenariats avec les éditeurs. Un
service de veille commercial a de toute façon besoin de ces accords : les CGU visent nommément les
« prestataires de veille ».

## 4. Recommandations

Par ordre de rendement :

1. **Atexo** (PLACE puis les instances régionales), environ un quart des liens des veilles et des
   liens directs : demander l'accord écrit de la DAE pour PLACE, puis celui des gestionnaires
   régionaux (Maximilien, Mégalis, AMPA…). En parallèle, préparer le connecteur, désactivé : formulaire
   de retrait anonyme, session, `POST`, archive ZIP, un DCE à la fois, pause entre deux retraits.
   Aucun retrait réel avant l'accord.
2. **AWS**, premier volume : demander à AW Solutions si son offre pour les prestataires (flux
   nocturne) comprend les DCE, à quel prix, et verser leur réponse au projet.
3. **achatpublic, Dematis, Marchés sécurisés** : demandes d'accord écrit (achatpublic : par
   courrier, 20 rue des Aqueducs, 94250 Gentilly). Sans accord, retrait à la main depuis le lien.
4. **SafeTender, Klekoon** : retrait à la main uniquement.
5. **Liens directs manquants** (environ 850) : à traiter avec les mêmes accords, plateforme par
   plateforme ; Marchés sécurisés en premier (176 avis sans lien direct).

En attendant, le parcours manuel fonctionne : lien depuis la fiche, retrait sur la plateforme,
dépôt du DCE dans « Documents » ; la suite (classement, texte, recherche, analyse) est automatique.

## Annexe : demande d'accord (modèle)

> Objet : demande d'autorisation de retrait automatisé des DCE
>
> Madame, Monsieur,
>
> GPE France utilise un outil interne de veille des marchés publics, réservé à son équipe, qui ne
> revend ni ne diffuse aucune donnée. Nous souhaiterions que cet outil retire automatiquement, sur
> votre plateforme, le dossier de consultation des marchés de travaux correspondant à nos critères,
> en mode anonyme ou avec notre compte entreprise, selon votre préférence.
>
> Volume prévu : quelques dizaines de dossiers par semaine, un retrait à la fois, avec une pause
> entre deux retraits, en dehors des heures que vous indiqueriez. L'outil s'identifie par un
> User-Agent nominatif et s'arrête à la première erreur ou au premier refus.
>
> Pouvez-vous nous indiquer si cet usage est autorisé, et à quelles conditions ?
