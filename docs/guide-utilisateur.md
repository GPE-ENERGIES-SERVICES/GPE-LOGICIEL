# Guide utilisateur

L'outil repère les appels d'offres publics qui correspondent à l'entreprise, lit leurs
documents et présente une fiche de synthèse dont chaque information renvoie à sa source. Il
aide à décider ; la décision reste humaine.

## Parcours

Le bandeau du haut donne accès aux rubriques : Tableau de bord, Appels d'offres, Mes veilles,
Mes suivis, Archives, Marchés attribués et, pour les administrateurs, Paramètres. Les rubriques
Marchés attribués est en cours de construction.

1. **Tableau de bord** : l'état de la dernière collecte ; le nombre de nouveaux appels
   d'offres, de ceux à traiter, de vos suivis, des échéances dans les 7 jours et des avis hors
   critères (un clic ouvre la liste correspondante) ; les échéances des 7 prochains jours ; les
   derniers appels d'offres retenus ; la répartition des appels d'offres en cours par statut, type
   de travaux et département.
2. **Appels d'offres** : les consultations en cours. Les onglets :
   - **Nouveaux** : retenues par le filtre, sans décision ;
   - **À traiter** : à analyser ou en préparation ;
   - **Tous** : toutes les consultations en cours, sauf celles hors critères sans décision ;
   - **Écartés** : écartées par un utilisateur ;
   - **Hors critères** : écartées par le filtre, à consulter pour vérifier que le filtre ne fait
     pas disparaître de bons marchés.

   Les consultations suivies et les consultations passées ont leurs propres rubriques (Mes suivis,
   Archives).

   Chaque ligne donne l'essentiel : étoile de suivi, acheteur, lieu, code postal et distance,
   travaux, type de marché, lots, procédure, montant, dates, jours restants, pertinence et statut
   (modifiable sur place).
   Les jours restants sont en rouge à moins de 3 jours, en orange à moins de 7 jours, et
   « Date limite dépassée » ensuite ; la même règle s'applique sur la fiche et le tableau de bord. Survoler « Retenu » ou « Hors critères »
   affiche la raison donnée par le filtre.

   La **recherche** porte sur l'objet, la référence, l'acheteur, la description, la ville, le code
   postal, le type de marché, le type de travaux et les codes CPV, sans tenir compte des
   majuscules ni des accents ; tous les mots saisis doivent être trouvés, en début de mot. Les
   synonymes du métier sont pris en compte : « panneaux solaires » trouve les consultations
   photovoltaïques, « bornes de recharge » celles d'IRVE, « CVC » le chauffage et la
   climatisation. Les compteurs des onglets suivent la recherche : un résultat rangé dans un autre
   onglet reste visible.

   Les **filtres**, sous la recherche, se combinent entre eux et avec la recherche : type de
   travaux, type de marché, région, départements, ville ou code postal, distance du siège,
   procédure, statut, date limite, publication, montant minimum et maximum, lots. Une liste
   s'applique dès qu'on la change ; un champ saisi s'applique avec Entrée ou « Appliquer ». Le
   nombre de filtres actifs est affiché, « Effacer les filtres » les retire tous. Un montant non
   indiqué ne masque pas une consultation.

   Le **tri** se choisit au-dessus du tableau : date limite (la plus proche par défaut ; les
   dates dépassées passent alors après les appels d'offres encore ouverts), publication,
   pertinence, montant ou distance. On peut afficher 20, 50 ou 100 lignes par page.

   Dès qu'une recherche ou un filtre est actif, « Enregistrer comme veille » ouvre une nouvelle
   veille pré-remplie avec ces critères.
3. **Mes veilles** : une veille est une recherche enregistrée (mots-clés, région, départements,
   type de marché, types de travaux, codes CPV, montant). Ses critères se combinent ; parmi les
   mots-clés, un seul suffit, synonymes compris. Chaque veille indique le nombre de consultations
   en cours qui lui correspondent et le nombre de celles publiées depuis votre dernière visite.
   « Voir les résultats » ouvre la liste des appels d'offres avec les critères de la veille, les
   plus récents d'abord, et remet le compteur des nouvelles consultations à zéro. Une veille peut
   être modifiée, désactivée ou supprimée.
4. **Mes suivis** : les consultations que vous suivez avec l'étoile ☆, en cours ou passées (une
   consultation close porte la mention « Clos »). Les onglets les rangent par statut, avec leur
   nombre ; on peut y chercher, trier et changer le statut. Retirer l'étoile retire la
   consultation de cette rubrique.
5. **Archives** : toutes les consultations passées (closes, attribuées, sans suite ou annulées),
   les plus récentes d'abord. Pour chacune : publication, montant estimé, résultat, attributaire
   et montant attribué lorsqu'ils sont connus, et la mention « Réponse envoyée » si l'équipe avait
   répondu. Recherche et filtres : année de publication, résultat, type de travaux, type de
   marché, région, départements, ville ou code postal, acheteur, montant.
6. **Fiche** : ouvrir une consultation. L'en-tête résume l'essentiel (acheteur, référence, lieu,
   type de marché, travaux, procédure, publication, date limite et jours restants, statut) ; les
   sections suivent : informations principales, objet, lots (avec leur total et, pour une
   consultation attribuée, l'attributaire de chaque lot), critères d'attribution et leur
   pondération, informations importantes (visite et sa date, documents demandés, qualifications,
   certifications, conditions particulières), documents du DCE, plateforme de dépôt, suivi,
   analyse du DCE et historique des publications dans l'ordre chronologique (un rectificatif qui
   change la date limite est signalé). Une information absente de l'avis n'est pas affichée ou est
   signalée comme non indiquée. « Retour aux résultats » ramène à la page d'où la fiche a été
   ouverte, avec sa recherche, ses filtres, son tri et sa page.
7. **Dépôt du DCE** : télécharger le dossier sur la plateforme de l'acheteur, puis le déposer
   dans la section « Documents (DCE) » (archive ZIP ou fichiers). Chaque dépôt crée une
   nouvelle version. Chaque pièce (RC, CCAP, CCTP, AE, DPGF, BPU, DQE, plans…) peut être ouverte
   ou téléchargée. Vérifier son type et le corriger si besoin : l'analyse se fonde sur le
   règlement de consultation. Dans la liste, « DCE : n documents » signale un dossier déjà déposé.
8. **Analyse** : pour un appel d'offres retenu, l'analyse démarre seule à la fin de la lecture
   et prend quelques minutes. Pour un appel d'offres écarté par le filtre, utiliser « Lancer
   l'analyse ».
9. **Vérification** : sur la fiche de synthèse, cliquer sur la source d'une information ouvre
   le document à la bonne page ; l'extrait cité est affiché sous la valeur. Valider, corriger
   ou rejeter les informations importantes, puis « Valider la fiche ».
10. **Statut et suivi** : le statut est partagé par toute l'équipe : Nouveau, À analyser, En
   préparation, Réponse envoyée ou Écarté. Il se change directement dans la liste, ou dans la
   section « Suivi » de la fiche avec un commentaire. L'étoile ☆ sert à suivre un appel d'offres :
   ce suivi est personnel et retrouvé dans l'onglet « Mes suivis ».

## La fiche de synthèse

1. **En un coup d'œil** : date limite, visite, lots, montant, durée.
2. **Points d'attention** : visite obligatoire, délai court, qualifications exigées, date
   limite différente entre l'avis et le dossier, exigences particulières relevées.
3. **Ce qui est demandé** : objet, acheteur, lieu, procédure, variantes.
4. **Pour répondre** : critères d'attribution, pièces à fournir, qualifications, assurances,
   références, plateforme de dépôt, date limite des questions.
5. **À vérifier** : tout ce qui n'est pas sûr, et les documents non analysés.
6. **Documents analysés** : les pièces lues par l'analyse et celles qui ne l'ont pas été.

## Signification des états

| État | Signification |
|------|---------------|
| Source vérifiée | L'extrait cité a été retrouvé dans le document et contient la valeur. L'interprétation reste à confirmer en lisant l'extrait (par exemple « visite facultative mais fortement recommandée »). |
| Source non vérifiée | L'extrait n'a pas été retrouvé tel quel dans la page : vérifier dans le document. |
| Non indiqué dans les documents analysés | L'information n'a pas été trouvée. Elle peut se trouver dans un document non analysé (tableur, plan, scan). |
| Ambiguë | Les documents permettent plusieurs lectures. |
| Contradictoire | Les documents, ou l'avis et le dossier, donnent des valeurs différentes. |
| Validée / Corrigée / Rejetée | Décision d'un utilisateur : elle prime sur l'analyse. La valeur proposée par l'analyse et l'auteur de la décision restent visibles. |

Une fiche est « Analysée, non vérifiée » tant qu'un utilisateur ne l'a pas validée, puis
« Validée par [nom] le [date] ». Modifier une information après coup annule la validation de
la fiche.

## États des documents

| État | Signification |
|------|---------------|
| Lu | Texte extrait de toutes les pages. |
| Lu en partie (scan) | Certaines pages n'ont pas de texte (document scanné) : elles ne sont pas analysées. |
| Non analysé | Tableur, plan ou image : conservé et téléchargeable, pas analysé dans cette version. |
| Non analysé (échec) | Fichier illisible ou protégé ; « Relancer la lecture » permet de réessayer. |

## Limites

- Une seule source : le BOAMP. Les marchés publiés uniquement ailleurs ne sont pas couverts.
- Le DCE est déposé à la main.
- L'analyse lit le règlement de consultation, l'avis et l'acte d'engagement. Le CCAP, le
  CCTP, les tableurs (DPGF, BPU, DQE), les plans et les pages scannées ne sont pas analysés.
- L'analyse est une aide à la lecture, pas une garantie : vérifier les informations
  importantes (date limite, visite, qualifications) à leur source avant de décider.
