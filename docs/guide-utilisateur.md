# Guide utilisateur

L'outil repère les appels d'offres publics qui correspondent à l'entreprise, lit leurs
documents et présente une fiche de synthèse dont chaque information renvoie à sa source. Il
aide à décider ; la décision reste humaine.

## Parcours

Le menu de gauche donne accès au Tableau de bord, puis à deux sections : **Appels d'offres** (Tous
les appels d'offres, Mes suivis, Mes veilles, Marchés attribués, Archives) et **Chiffrage**
(Chiffrages, Tarifs fournisseurs) et **Mémoire technique** (Mémoires, Bibliothèque), puis
**Entreprise**. Pour les administrateurs s'ajoutent
**Supervision** (doublons à vérifier, en premier, avec leur nombre dans le menu ; journal de collecte ;
récupération des DCE ; journal et qualité des analyses IA) et **Paramètres** (comptes utilisateurs,
critères de filtrage, configuration de l'IA).

**Entreprise** rassemble les informations de GPE, consultables par tous et modifiables par un
administrateur : identité et interlocuteurs, activités et compétences, personnel et habilitations,
matériel, certifications, références, documents administratifs et économie (coûts horaires, TVA et
coefficients des chiffrages, autres paramètres). La vue d'ensemble liste ce qui a expiré ou expire
dans les 60 jours (documents, certifications, habilitations, contrôles du matériel). Un document se
remplace en déposant le nouveau fichier puis en mettant à jour ses dates ; l'ancien reste en
historique.

1. **Tableau de bord** : l'état de la dernière collecte, puis « Aujourd'hui » : les nouvelles
   opportunités publiées depuis hier (dont celles qui correspondent aux critères de l'entreprise),
   les consultations à analyser, les échéances des 7 prochains jours, vos suivis et les avis hors
   critères à vérifier ; un clic sur un chiffre ouvre la liste correspondante. Suivent les
   échéances proches, les nouvelles opportunités, vos veilles actives avec leurs nouvelles
   consultations (un clic ouvre leurs résultats) et les derniers marchés attribués.
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
6. **Marchés attribués** : les résultats des avis d'attribution, une ligne par lot : marché,
   acheteur, lieu, travaux, attributaire, montant attribué, date d'attribution et nombre d'offres
   reçues. Au-dessus du tableau : le nombre d'attributions, d'attributaires différents et le
   montant total attribué. Un clic sur un attributaire affiche tous ses marchés. Filtres : année,
   type de travaux, type de marché, région, départements, ville ou code postal, acheteur,
   attributaire, montant.
7. **Fiche** : ouvrir une consultation. L'en-tête résume l'essentiel (acheteur, référence, lieu,
   type de marché, travaux, procédure, publication, date limite et jours restants, statut) ; les
   sections suivent : informations principales, objet, lots (avec leur total et, pour une
   consultation attribuée, l'attributaire de chaque lot), critères d'attribution et leur
   pondération, informations importantes (visite et sa date, documents demandés, qualifications,
   certifications, conditions particulières), documents du DCE, plateforme de dépôt, suivi,
   analyse du DCE et historique des publications dans l'ordre chronologique (un rectificatif qui
   change la date limite est signalé). Une information absente de l'avis n'est pas affichée ou est
   signalée comme non indiquée. « Retour aux résultats » ramène à la page d'où la fiche a été
   ouverte, avec sa recherche, ses filtres, son tri et sa page.
8. **Dépôt du DCE** : télécharger le dossier sur la plateforme de l'acheteur, puis le déposer
   dans la section « Documents (DCE) » (archive ZIP ou fichiers). Chaque dépôt crée une
   nouvelle version. Chaque pièce (RC, CCAP, CCTP, AE, DPGF, BPU, DQE, plans…) peut être ouverte
   ou téléchargée. Vérifier son type et le corriger si besoin : l'analyse se fonde sur le
   règlement de consultation. Dans la liste, « DCE : n documents » signale un dossier déjà déposé.
   Une fois le dossier lu, « Rechercher dans les documents » trouve les pages qui contiennent les
   mots cherchés (synonymes du métier compris) : document, page et extrait, avec un lien qui ouvre
   le document à la bonne page. Seul le texte lu dans les PDF et les documents Word est couvert.
9. **Analyse** : **pas encore disponible** : l'analyse par IA est préparée mais désactivée
   tant que l'entreprise n'a pas choisi et configuré un fournisseur ; la fiche l'indique et les
   documents restent consultables. Une fois activée, pour un appel d'offres retenu, l'analyse
   démarre seule à la fin de la lecture et prend quelques minutes. Pour un appel d'offres écarté
   par le filtre, utiliser « Lancer l'analyse ». Une analyse en attente peut être annulée.
10. **Vérification** : sur la fiche de synthèse, cliquer sur le ✓ ou l'étiquette à côté d'une valeur
   affiche sa source, l'extrait cité et les boutons Valider, Corriger et Rejeter. Vérifier les
   informations importantes, puis « Valider la fiche ».
11. **Statut et suivi** : le statut est partagé par toute l'équipe : Nouveau, À analyser, En
   préparation, Réponse envoyée ou Écarté. Il se change directement dans la liste, ou dans la
   section « Suivi » de la fiche avec un commentaire. L'étoile ☆ sert à suivre un appel d'offres :
   ce suivi est personnel et retrouvé dans l'onglet « Mes suivis ».

## Ajouter un appel d'offres absent de la veille

Si vous ne trouvez pas un appel d'offres, le menu « Nouvel appel d'offres » le crée avec son nom, et
si vous les connaissez son acheteur et sa date limite. Il s'ouvre sur la même page que les autres : DCE à
déposer, fiche IA, analyse financière, chiffrage et mémoire. Il est classé « À analyser » et marqué
« Ajouté manuellement ». Il ne passe ni par les filtres ni par les veilles, et son analyse ne part jamais
toute seule. Sans avis publié, la date limite lue dans le DCE n'est comparée à aucune autre.

## Le dossier de réponse

C'est la dernière étape d'un appel d'offres, dans l'onglet « Dossier de réponse » de sa page ; le menu
« Dossiers de réponse » liste ceux en préparation. Il rappelle où en sont la fiche IA, le chiffrage et le
mémoire, sans bloquer : vous avancez dans l'ordre que vous voulez.

« Préparer le dossier » reprend les pièces de candidature et d'offre lues par la fiche IA (une valeur
rejetée est ignorée, une valeur corrigée est reprise corrigée) et propose pour chacune le document de
l'entreprise qui correspond (Kbis, assurance, attestations, certificat, bilan, charte de sécurité, moyens humains et matériels, réalisations). C'est une proposition :
vous pouvez renommer la pièce, choisir un autre document, déposer votre fichier, qui passe alors avant le
document proposé, ou la marquer « non concernée ». Relancer la préparation ajoute seulement les pièces
nouvelles et ne change jamais ce que vous avez modifié.

Une pièce est **prête** quand un fichier est joint et que vous avez coché « Vérifiée » ; changer le fichier
ou le document enlève la coche. Un document expiré est signalé. Le ZIP contient les fichiers joints, rangés
en Candidature et Offre, avec un avertissement si des pièces ne sont pas prêtes. L'export du chiffrage, qui
contient vos coûts, n'en fait jamais partie : reportez vos prix sur le bordereau de l'acheteur et déposez-le.
Le DC1, le DC2 et l'acte d'engagement du DCE se remplissent depuis leur pièce : « Remplir » lit le formulaire Word du
DCE. Avec l'IA, chaque valeur affiche sa source : Entreprise ou Chiffrage (recopiée telle quelle par le logiciel), « IA :
à vérifier », ou « Manquant » avec ce qu'il faut fournir. Vous pouvez aussi tout remplir à la main. Corrigez ici ce qu'il
faut : relancer l'IA ne change jamais vos modifications. « Générer le Word » joint le document à la pièce, à vérifier
ensuite. Un formulaire par lot : ajoutez une pièce pour chaque lot. Le montant vient du chiffrage validé du lot. Les
déclarations sur l'honneur, le lieu, la date et la signature restent à compléter sur le document. Le mémoire s'exporte en Word depuis sa page (« Exporter en Word ») : ce qui reste à rédiger y est signalé
entre crochets ; relisez-le, puis déposez sa version finale. La signature et le dépôt restent de votre côté.

## L'aide à la décision

Sur la page d'un appel d'offres, après les lots et avant les documents (DCE), le bloc « Analyse IA —
Aide à la décision » est produit par la même analyse que la fiche de synthèse, sans appel
supplémentaire. Il ne décrit pas le marché (le résumé de l'avis, au-dessus, le fait) : il donne

- un **verdict** : À étudier en priorité, À examiner, Peu adapté a priori ou Informations
  insuffisantes, avec une ou deux phrases d'explication ; aucun score ni pourcentage ;
- au plus trois **raisons d'étudier** le marché ;
- les **obstacles et risques**, marqués « Confirmé » (exigence écrite, extrait retrouvé dans le
  document), « À vérifier » ou « Information manquante » ; « (déduction) » signale ce que l'IA
  déduit sans que ce soit écrit tel quel ;
- trois à cinq **vérifications à faire avant de décider** ;
- ces parties sont repliées : leur titre donne le compte (par exemple « 1 confirmé · 1 à vérifier »),
  un clic les déplie ;
- les **limites**, en bref : au plus trois réserves et le nombre de pièces non analysées (leur liste
  reste dans « Analyse du DCE ») ; le logiciel ne connaît que les critères de veille de GPE, pas
  ses capacités (qualifications, chiffre d'affaires, moyens).

Le ✓ indique une source vérifiée : cliquer dessus affiche le document, la page et l'extrait. Une analyse faite
avant l'ajout de ce bloc n'a pas de recommandation : elle s'obtient en relançant l'analyse
(nouvel appel payant), jamais automatiquement.

## L'analyse financière (DPGF, BPU, DQE)

La section « Analyse financière », sous « Analyse du DCE », ne se lance qu'avec son bouton (appel
payant à l'IA). Elle lit les pièces classées DPGF, BPU ou DQE, en tableur (XLS, XLSX, ODS, CSV) ou en
PDF, et donne :

- les **contrôles des montants**, faits par le logiciel sans IA, feuille par feuille : colonnes
  reconnues, nombre de postes, prix renseignés ou cadre à remplir, somme des montants, totaux
  écrits comparés aux lignes, TVA et TTC, incohérences avec leur cellule (par exemple « F6 :
  quantité × prix unitaire = 4 200,00, montant écrit 4 300,00 ») ;
- une **synthèse** par l'IA : postes principaux et points à vérifier, chacun avec sa source
  (document, feuille, ligne) ; le ✓ signifie que le texte cité a été retrouvé dans
  cette ligne.

Dans un PDF numérique, les tableaux sont reconstruits à partir de la position des mots et contrôlés
comme un tableur (« Tableau 1 (p. 2 à 3) ») ; un tableau dont la mise en page est ambiguë est marqué
« structure ambiguë » et ses contrôles sont à confirmer.

Les **pages scannées** (sans texte) des pièces écrites et financières sont lues par reconnaissance
de caractères (OCR), sur le serveur : le document l'indique (« 1 page(s) lue(s) par
reconnaissance de caractères (OCR), confiance moyenne 91 % : texte à vérifier »). Un tableau lu
par OCR n'est jamais présenté comme fiable : ses valeurs sont à vérifier sur le document. Les
plans ne sont pas lus.

L'IA ne propose aucun prix et ne calcule aucune rentabilité. Une valeur de formule non enregistrée
dans le fichier est signalée comme inconnue, jamais recalculée. Si une pièce de prix est mal
classée, corriger son type dans « Documents (DCE) ».

## Le chiffrage

L'onglet « Chiffrage » de la fiche d'un marché liste ses chiffrages (par lot s'il y a des lots) et en
crée un nouveau, avec la TVA et les coefficients des paramètres de GPE.

Le chiffrage se présente comme un **tableau Excel** : N°, désignation, quantité, unité, PU HT et
montant HT. Chaque cellule se modifie directement et s'enregistre en la quittant ; Entrée ou ↓
passe à la ligne suivante. La dernière ligne, vide, ajoute une ligne. Le montant HT est calculé par
le logiciel (quantité × PU, arrondi au centime). Dans la colonne PU, un mot (PM, inclus) est une
mention à 0 €. Pour **coller depuis Excel**, copier les colonnes dans l'ordre N°, Désignation,
Quantité, Unité, PU HT et coller dans la cellule de départ : les lignes en dessous sont remplacées,
les suivantes ajoutées. Les flèches ↑ ↓ déplacent une ligne, ✕ la supprime.

Un chiffrage complet se fait ainsi sans aucun détail. Le bouton **« Détail »** d'une ligne, facultatif,
donne ses options (chapitre, option PSE, ligne à vérifier) et son **sous-détail** : les coûts
d'**une unité** du poste (fourniture, main-d'œuvre, prestation, déplacement, location,
sous-traitance, forfait, divers). Le PU reste celui saisi, sauf à choisir « Calculé » : il est alors
calculé depuis le détail (affiché en italique dans le tableau ; y saisir un prix le remplace).

Avec un détail, le logiciel calcule :

- coût d'une ligne du sous-détail = quantité par unité × coût unitaire × (1 + perte) ;
- prix unitaire HT du poste = somme des coûts × coefficient de leur type, arrondie au centime ;
- montant HT = prix unitaire × quantité, arrondi au centime ;
- TVA calculée sur le total HT ; les options (PSE) sont totalisées à part ;
- marge = total HT − déboursé sec, connue seulement quand aucun coût ne manque.

Un poste peut aussi avoir un **prix imposé** (forfait : son sous-détail ne sert qu'à montrer la marge),
ou une **mention** sans prix (PM, inclus : montant 0). Un poste sans coût, sans prix ou sans quantité
reste « sans montant » : rien n'est deviné.

Un coût se saisit à la main, ou se choisit :

- dans les **taux de GPE** (heure d'électricien, kilomètre…) : le coût est copié ; « Recopier les
  taux actuels » le met à jour dans un brouillon, si un taux a changé depuis ;
- dans les **tarifs des fournisseurs** importés (recherche par référence, EAN ou mots) : le prix net
  par unité, les références et la date du prix sont copiés.

Un nouveau tarif ou un nouveau taux ne change jamais un chiffrage existant.

« Importer un DPGF, BPU ou DQE du DCE » ajoute les lignes d'un bordereau du dossier (n°, désignation,
unité, quantité, chapitre), avec leur feuille et leur ligne d'origine. Les prix du fichier ne sont pas
repris. Les lignes d'un PDF, et celles qui n'ont pas pu être lues entièrement, sont marquées « À
vérifier » : décocher la case une fois vérifiées.

« Valider » fige le chiffrage. C'est refusé tant qu'un poste est sans montant, qu'un coût manque ou
qu'une ligne importée reste à vérifier. Pour le modifier ensuite, « Nouvelle version » en crée une
copie en brouillon ; à sa validation, l'ancienne version passe « Remplacée ». « Exporter (Excel) »
télécharge les postes, les totaux et le sous-détail. L'historique garde chaque création, import,
validation et nouvelle version.

**Avec l'IA** (seulement si l'analyse par IA est activée sur le serveur) : « Proposer des
fournitures » demande à l'IA de choisir, parmi les offres trouvées par le logiciel dans les tarifs
importés, celles qui correspondent au poste, avec une quantité par unité ; « Relecture par l'IA »
signale des points à vérifier. L'IA ne donne aucun prix et ne modifie rien : une proposition
acceptée ajoute un coût au prix du tarif ; la validation reste toujours humaine.

Les **paramètres de chiffrage** (TVA et coefficients par défaut, coûts horaires) se gèrent dans
« Entreprise › Économie » ; les **fournisseurs** (création, import d'un fichier de prix CSV ou XLSX) dans
« Chiffrage › Tarifs fournisseurs ». Les deux sont réservés aux administrateurs.

## Le mémoire technique

« Mémoire technique › Mémoires » liste les mémoires et en crée un, rattaché à un appel d'offres (et
à un lot) ou non. L'espace de travail a deux panneaux :

- **à gauche, la préparation** :
  - l'onglet **Exigences du DCE** liste les critères, sous-critères, éléments obligatoires,
    contraintes de forme, moyens, délais et méthodes attendus, avec le document et la page où ils
    sont écrits ; une exigence rattachée à aucune partie est marquée « Non couverte » ;
  - l'onglet **Sommaire** : parties, sous-parties et sections, avec leur description. On les
    ajoute, renomme, déplace (↑ ↓, → pour en faire une sous-partie, ← pour la remonter) et
    supprime ; « Partir du sommaire type » donne un premier plan ;
  - **« Analyser le DCE et proposer un sommaire »** (si l'IA est activée) relève les exigences et
    propose des parties, en pointillés : les accepter ou les refuser une à une. Une citation qui
    n'a pas été retrouvée dans le DCE est signalée. Le sommaire n'est jamais définitif tant qu'il
    n'est pas validé ; le modifier ensuite demande de le valider à nouveau ;
- **à droite, le document** : une page blanche où chaque section se tape directement et
  s'enregistre seule (« Enregistré »). La barre d'outils donne titres, gras, italique, souligné,
  listes, images (cliquer sur une image pour la remplacer ou la supprimer ; sa légende se modifie
  dans le texte) et emplacements réservés pour une photo, un schéma, un tableau ou un plan.

Sous chaque section : **Insérer depuis l'Entreprise** (présentation, moyens humains, moyens
matériels, certifications, références : les données réelles de GPE), **Demander à l'IA** (rédiger,
réécrire, développer, raccourcir, améliorer, corriger, préciser, adapter à une exigence choisie) et
**Versions précédentes**. Un texte de l'IA ou de l'Entreprise ne remplace jamais le vôtre : il
apparaît dans un encadré, avec « Remplacer mon texte », « Ajouter à la suite » ou « Ignorer ».
Rien n'est perdu : chaque texte remplacé reste dans les versions précédentes.

Une fois le sommaire validé, **« Rédiger les sections vides avec l'IA »** les rédige l'une après
l'autre : la section en cours et l'avancement s'affichent, « Arrêter » interrompt après la section
en cours. L'IA n'utilise que les données de l'Entreprise et du DCE, et liste ce qui manque au lieu de
l'inventer.

« Mémoire technique › Bibliothèque » reçoit les anciens mémoires de GPE (PDF avec texte ou Word).
Ceux marqués « Référence pour l'IA » l'aident pour le style et la présentation ; elle n'en reprend
aucun chantier ni aucune information qui ne figure pas dans les données actuelles de l'Entreprise.

## Ce qui est envoyé à l'IA

Dans « Documents (DCE) », la colonne « Envoi à l'IA » indique pour chaque fichier s'il est
« Envoyé à l'IA », « Si la place le permet », réservé à l'« Analyse financière » ou « Non envoyé »,
avec la raison (doublon, plan ou dessin, très peu de texte, documentation générale…). Les pièces
essentielles (RC, avis, AE, CCAP, CCTP) passent d'abord, puis les documents utiles (diagnostic,
rapport, étude, PGC…). Si le logiciel se trompe, « Forcer l'envoi » ou « Exclure » corrige la
décision, et « Revenir au choix automatique » l'annule. Ce choix vaut pour la prochaine analyse : il
n'en lance aucune.

## La fiche de synthèse

Chaque partie se déplie au clic ; son titre indique ce qu'elle contient (par exemple « 5 sur 11
renseignées », « 1 analysé sur 116 »). « En un coup d'œil » et « Points d'attention » sont ouverts
d'emblée. ✓ signifie que l'extrait a été retrouvé dans le document ; une étiquette orange signale ce
qui demande votre attention. Ce que les documents ne disent pas est regroupé sur une ligne à la fin de
chaque partie : cliquer sur un nom permet de le renseigner.

1. **En un coup d'œil** : date limite, visite, lots, montant, durée.
2. **Points d'attention** : visite obligatoire, délai court, qualifications exigées, date
   limite différente entre l'avis et le dossier, exigences particulières relevées.
3. **Ce qui est demandé** : objet, acheteur, lieu, procédure, variantes.
4. **Pour répondre** : critères d'attribution, pièces à fournir, qualifications, assurances,
   références, plateforme de dépôt, date limite des questions.
5. **À vérifier** : tout ce qui n'est pas sûr, et les documents non analysés.
6. **Documents** : le nombre de pièces analysées et la liste de celles qui ne l'ont pas été, avec
   la raison.

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
- L'analyse par IA n'est pas encore activée (aucun fournisseur configuré).
- Une fois activée, l'analyse lit le règlement de consultation, l'avis, l'acte d'engagement, le
  CCAP, le CCTP et les autres pièces lues (PDF, Word, texte, HTML, pages scannées lues par OCR),
  dans une limite de volume. Les pièces de prix (DPGF, BPU, DQE) vont à l'analyse financière,
  lancée à la demande. Les plans et les images ne sont pas analysés.
- L'analyse est une aide à la lecture, pas une garantie : vérifier les informations
  importantes (date limite, visite, qualifications) à leur source avant de décider.
