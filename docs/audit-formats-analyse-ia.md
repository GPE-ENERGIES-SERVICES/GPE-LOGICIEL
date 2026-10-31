# Audit — formats des pièces du DCE que l'analyse IA ne lit pas

Date : 27/10/2026. Périmètre : du dépôt (ou de la récupération) d'un DCE jusqu'au texte envoyé à
l'IA. Constat fait sur le code et sur la base locale (278 fichiers de 4 dossiers déposés).

## 1. Comment un fichier arrive jusqu'à l'IA

1. **Dépôt** : les archives `.zip` sont décompressées (y compris imbriquées, jusqu'à 3 niveaux,
   2 000 fichiers, 2 Go au plus). Chaque fichier devient un « document ».
2. **Lecture** (`DocumentProcessor`) : selon l'extension, le texte est extrait page par page et
   enregistré. Rien d'autre n'est conservé pour l'IA : **elle ne reçoit que du texte**, jamais
   d'image ni de fichier.
3. **Sélection** (`AnalysisService`) : seuls certains types de pièces sont envoyés, dans l'ordre
   RC, avis, AE, CCAP, CCTP, autres, **10 documents et 400 000 caractères au plus**. Un document
   qui ne tient pas est écarté entier, jamais coupé.

Un fichier peut donc ne pas être analysé à l'étape 2 (format non lu) ou à l'étape 3 (type de
pièce exclu, ou place insuffisante).

## 2. Ce qui est lu et ce qui ne l'est pas

| Format | Lu ? | Ce qui se passe aujourd'hui |
|---|---|---|
| PDF avec texte | Oui | Texte extrait page par page |
| Word `.doc`, `.docx`, `.odt`, `.rtf` | Oui | Converti en PDF par LibreOffice, puis lu |
| Texte `.txt`, HTML | Oui | Lu par sections |
| **PDF scanné** (pages sans texte) | **Non** | Marqué « scanné, OCR nécessaire ». Un PDF entièrement scanné n'envoie rien ; dans un PDF partiellement scanné, les pages sans texte sont ignorées |
| Tableurs `.xls`, `.xlsx`, `.xlsm`, `.ods`, `.csv` | Oui, depuis le 27/10/2026 | Lus cellule par cellule ; utilisés par l'analyse financière (DPGF, BPU, DQE), pas par la fiche de synthèse |
| **Images** `.jpg`, `.png`, `.tif`, `.gif`, `.bmp`, `.svg` | **Non** | « Plan ou image conservé, non analysé » |
| **Photos `.heic`** (iPhone) | **Non** | « Format non analysé » |
| **Plans** `.dwg`, `.dxf` | **Non** | « Plan ou image conservé, non analysé » |
| Présentations `.ppt`, `.pptx`, `.odp` | **Non** | « Format non analysé » (seul le module Writer de LibreOffice est installé) |
| E-mails `.msg`, `.eml` | **Non** | « Format non analysé » |
| Archives `.7z`, `.rar`, `.tar`, `.gz` | **Non** | « Archive non prise en charge : décompressez-la avant le dépôt » |
| PDF protégé par mot de passe | **Non** | En échec : « PDF protégé par un mot de passe » |
| Fichier texte de plus de 20 Mo | **Non** | « Fichier texte trop volumineux » |

**Exclus même quand ils sont lisibles** (étape 3) :

- les pièces classées **DPGF, BPU, DQE et PLAN**, même en PDF avec du texte ;
- tout ce qui dépasse **10 documents ou 400 000 caractères** : un gros CCTP peut être écarté
  entier, et les pièces « autres » passent en dernier.

**Limites aussi pour ce qui est lu :**

- les tableaux d'un PDF arrivent en texte à plat (colonnes mélangées) ;
- les images à l'intérieur d'un PDF (schémas, cartouches, tampons) sont ignorées.

## 3. Ce que ça donne sur les dossiers réels (base locale)

| État | Fichiers | Détail |
|---|---|---|
| Texte lu | 71 | 65 lus en entier, 6 dont quelques pages sont scannées |
| **PDF entièrement scannés** | **90** | 72 « autres pièces », 18 plans |
| **Photos `.heic`** | **92** | Dossier photos du DCE de la piscine de Beaurepaire |
| Images `.jpg` | 16 | |
| Tableurs | 9 | Dont 5 DPGF |
| **Total** | **278** | **Environ 1 fichier sur 4 a du texte exploitable** |

Lecture :

- **Les pièces écrites principales sont presque toujours lues** : RC, CCAP, CCTP et AE sont en
  PDF texte ou Word. Quelques-unes ont une page scannée (souvent la page signée).
- La grande masse non lue est faite de **photos et de PDF scannés** (plans, photos, annexes).
  Pour décider d'y aller, leur valeur est faible, mais ils gonflent le compteur « non analysés ».
- **Le vrai manque pour décider, ce sont les DPGF, BPU et DQE** : quantités, nature et volume des
  ouvrages, donc rentabilité. Ils ne sont jamais lus, même en PDF.

## 4. Solutions possibles, par priorité

### P1 — Lire les DPGF, BPU et DQE (meilleur gain pour la décision) — fait

Réalisé le 27/10/2026 : lecture des tableurs, contrôles des montants par le code et analyse
financière à la demande (voir `docs/technique.md` et le guide utilisateur). Ce qui suit est
l'analyse d'origine.


- **Tableurs** : convertir chaque feuille en texte tabulaire (bibliothèque Apache POI pour
  `.xls`, `.xlsx` et `.xlsm` ; LibreOffice Calc pour `.ods` ; `.csv` direct), une feuille = une
  « page », pour que l'IA puisse citer la source.
- **Pièces de prix en PDF** : arrêter de les exclure de la sélection.
- **Gestion de la taille** : un DPGF peut faire des milliers de lignes. Il vaut mieux une
  **seconde analyse dédiée aux prix**, déclenchée à la demande : nombre de postes, ouvrages
  principaux, quantités notables, lots concernés. On évite ainsi de prendre la place du RC et du
  CCAP dans l'analyse principale.
- **Coût** : gratuit pour la lecture ; l'analyse dédiée est un appel IA de plus, seulement quand
  on la demande.
- **Effort** : moyen (2 à 3 jours, tests compris).

### P2 — Reconnaissance de caractères (OCR) des PDF scannés — fait (v1)

Réalisé le 28/10/2026 avec Tesseract appelé directement (sans OCRmyPDF ni Ghostscript), pages
rendues par PDFBox ; tableaux scannés reconstruits par le même extracteur, jamais marqués fiables
d'office. Voir `docs/technique.md`. Ce qui suit est l'analyse d'origine.

- **Option A, locale et gratuite (recommandée)** : OCRmyPDF avec Tesseract en français, installés
  dans l'image Docker du backend. Seules les pages sans texte passent à l'OCR. Les pages sont
  enregistrées avec la méthode `OCR`, déjà prévue dans le code (`PageMethod.OCR`).
  - Durée : quelques secondes par page.
  - Qualité : bonne sur du texte tapé, médiocre sur l'écriture manuscrite et les plans.
  - Effort : moyen (2 jours) ; image Docker plus lourde (environ 150 Mo).
- **Option B, Claude lit directement le PDF** : l'API accepte les PDF et les « voit » page par
  page (texte et images).
  - Avantage : meilleure qualité, y compris sur les tableaux et les schémas.
  - Inconvénients : **plus cher** (chaque page compte aussi comme une image) ; le contrôle
    automatique des citations, qui compare l'extrait au texte, ne fonctionne plus sur ces pages.
  - À réserver aux pièces importantes scannées (RC ou CCAP scanné), sur demande.
- **Recommandation** : A par défaut ; B en option pour une pièce clé restée illisible.

### P3 — Ne plus compter les photos et images comme des « manques »

- **Le problème** : 92 photos `.heic` et 16 `.jpg` affichées comme « non analysées » font
  croire que l'analyse a raté la moitié du dossier.
- **La solution** : les classer à part, en « photo / illustration », et les compter séparément
  (« 92 photos non lues ») au lieu de « 108 pièces non analysées ».
- **Pour les lire quand même** : une conversion `.heic` vers `.jpg` (libheif) rend les photos
  consultables ; l'OCR de P2 ou la vision de Claude pourraient lire une photo de panneau ou de
  plaque. Faible intérêt pour la décision, à faire seulement sur demande.
- **Effort** : faible (½ journée pour le classement).

### P4 — Formats faciles à ajouter

- **`.ppt`, `.pptx`, `.odp`** : installer le module Impress de LibreOffice ; ils sont alors
  convertis en PDF comme les fichiers Word. Effort : très faible.
- **`.7z`, `.rar`, `.tar`, `.gz`** : décompression avec 7-Zip (`p7zip`), sous les mêmes limites
  que le `.zip` (nombre de fichiers, taille, chemins interdits). Effort : faible à moyen (1 jour),
  surtout pour les tests de sécurité.
- **`.msg`, `.eml`** : extraction du corps du message et de ses pièces jointes (Apache POI pour
  `.msg`, Jakarta Mail pour `.eml`). Rare dans un DCE. Effort : faible.

### P5 — Mieux utiliser la place disponible

- **Le problème** : la limite de 10 documents et 400 000 caractères écarte un document entier
  dès qu'il ne tient pas.
- **Ce qu'on peut faire** :
  - relever ces limites : le modèle accepte beaucoup plus de texte, mais le coût suit le volume
    envoyé ;
  - ou découper l'analyse : une passe sur les pièces administratives (RC, CCAP, AE), une passe
    sur les pièces techniques (CCTP), puis fusion. C'est plus de code et un appel de plus.
- **Dans tous les cas** : afficher clairement quelle pièce a été écartée faute de place. C'est
  déjà indiqué dans « Analyse du DCE », section 6.

### Ce qui n'a pas de solution technique raisonnable

- **PDF protégés par mot de passe** : demander une version non protégée à l'acheteur.
- **Plans `.dwg` / `.dxf`** : leur contenu est graphique. Lire le cartouche demanderait un
  convertisseur spécialisé pour un intérêt faible ; on les garde à télécharger.

## 5. Plan conseillé

| Ordre | Action | Gain | Effort |
|---|---|---|---|
| 1 | Classer photos et images à part (P3) | Compteurs justes, fin de la fausse alerte | ½ jour |
| 2 | Lire les tableurs et les pièces de prix (P1), avec une analyse prix à la demande | Rentabilité et volume estimables | 2 à 3 jours |
| 3 | OCR local des PDF scannés (P2-A) | Pièces scannées enfin lues | 2 jours |
| 4 | Présentations et archives 7z / rar (P4) | Moins de fichiers ignorés | 1 jour |
| 5 | Lecture directe des PDF par Claude pour une pièce clé (P2-B) | Cas difficiles | 1 jour, coût variable |

Aucune de ces solutions ne change les règles de veille, la collecte ni les droits d'accès. Seules
l'analyse dédiée aux prix (P1) et la lecture directe des PDF par Claude (P2-B) ajoutent un coût
d'IA, et seulement à la demande.
