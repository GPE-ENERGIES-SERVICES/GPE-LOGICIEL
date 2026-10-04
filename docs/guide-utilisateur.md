# Guide utilisateur

L'outil repère les appels d'offres publics qui correspondent à l'entreprise, lit leurs
documents et présente une fiche de synthèse dont chaque information renvoie à sa source. Il
aide à décider ; la décision reste humaine.

## Parcours

1. **Tableau de bord** : l'état de la dernière collecte et le nombre d'appels d'offres de chaque
   onglet à traiter ; un clic ouvre l'onglet correspondant.
2. **Liste** (« Appels d'offres ») : les appels d'offres retenus par les critères de
   l'entreprise. Les onglets :
   - **Nouveaux** : retenus par le filtre, sans décision ;
   - **À traiter** : en cours d'analyse ou de préparation ;
   - **Mes suivis** : ceux que vous suivez (le suivi est propre à chaque utilisateur) ;
   - **Tous** : tous les appels d'offres, sauf ceux hors critères sans décision ;
   - **Écartés** : écartés par un utilisateur ;
   - **Archivés** : clos, annulés ou sans suite ;
   - **Hors critères** : écartés par le filtre, à consulter pour vérifier que le filtre ne fait
     pas disparaître de bons marchés.

   Chaque ligne donne l'essentiel : étoile de suivi, acheteur, lieu et distance, travaux, lots,
   procédure, montant, dates, jours restants, pertinence et statut (modifiable sur place). Survoler « Retenu » ou « Hors critères »
   affiche la raison donnée par le filtre.

   La **recherche** porte sur le titre, la référence, l'acheteur, la ville, la description et
   le type de travaux, sans tenir compte des majuscules ni des accents ; tous les mots saisis
   doivent être trouvés. Les compteurs des onglets suivent la recherche : un résultat rangé dans
   un autre onglet reste visible.

   Les **filtres** (panneau « Filtres », ouvert dès qu'un filtre est actif) se combinent entre
   eux et avec la recherche : localisation (région, départements, ville, distance du siège),
   type de travaux, dates (publication récente, date limite, échéance proche, délai minimum),
   montant (minimum, maximum, indiqué ou non), procédure, lots et statut. Dans un même groupe de
   cases, il suffit qu'une case corresponde. Un montant non indiqué ne masque pas un appel
   d'offres, sauf si l'on choisit « Montant : Indiqué ».

   Le **tri** se choisit au-dessus du tableau : date limite (la plus proche par défaut ; les
   dates dépassées passent alors après les appels d'offres encore ouverts), publication,
   pertinence, montant ou distance. On peut afficher 20, 50 ou 100 lignes par page.
3. **Fiche** : ouvrir un appel d'offres pour voir les informations de l'avis, l'historique des
   publications (un rectificatif qui change la date limite est signalé) et le lien vers la
   plateforme de l'acheteur.
4. **Dépôt du DCE** : télécharger le dossier sur la plateforme de l'acheteur, puis le déposer
   dans la section « Dossier de consultation » (archive ZIP ou fichiers). Chaque dépôt crée une
   nouvelle version. Vérifier le type de chaque pièce (RC, CCAP, CCTP…) et le corriger si besoin :
   l'analyse se fonde sur le règlement de consultation.
5. **Analyse** : pour un appel d'offres retenu, l'analyse démarre seule à la fin de la lecture
   et prend quelques minutes. Pour un appel d'offres écarté par le filtre, utiliser « Lancer
   l'analyse ».
6. **Vérification** : sur la fiche de synthèse, cliquer sur la source d'une information ouvre
   le document à la bonne page ; l'extrait cité est affiché sous la valeur. Valider, corriger
   ou rejeter les informations importantes, puis « Valider la fiche ».
7. **Statut et suivi** : le statut est partagé par toute l'équipe : Nouveau, À analyser, En
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
