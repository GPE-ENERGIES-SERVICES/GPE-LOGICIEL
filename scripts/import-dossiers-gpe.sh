#!/bin/sh
# Adds the response folders GPE already sent to its libraries, as the pages
# Mémoires > Bibliothèque and Entreprise > Documents do one file at a time:
# - the technical memoir of each job ("<job>/03-Dossier RAO/01-Pieces
#   principales", Word preferred to PDF), titled with the job folder name;
# - the filled price schedules of "01-Pieces principales", into the price library;
# - the company documents of "03-Dossier RAO/02-Situation GPE",
#   "03-Assurance et Rib", "04-Références" and "05-Qualification", one per
#   name, the copy of the last job folder.
# In older folders without "03-Dossier RAO", the memoir is the « MT… » file of the
# job folder; a « MT… » file alone at the top of the folder is a job of its own.
# What is already there (same title or name) is skipped: it can be run again.
#
#   scripts/import-dossiers-gpe.sh "<folder AO PV 1>" PHOTOVOLTAIC
#
# On Windows, run it in Linux: curl.exe reads its arguments in the Windows code
# page and breaks the accents of the titles.
#   docker run --rm --add-host=host.docker.internal:host-gateway -v "<repo>:/repo" -v "<folder>:/in" \
#     -e API_URL=http://host.docker.internal:3000 alpine sh -c "apk add -q curl && sh /repo/scripts/import-dossiers-gpe.sh /in ELECTRICITY"
#
# Logs in with BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD of .env.
# API_URL is the address of the frontend (default http://localhost:APP_PORT).
set -eu

ROOT="$1"
ACTIVITY="$2"
case "$ACTIVITY" in ELECTRICITY|PHOTOVOLTAIC) ;; *) echo "Activity: ELECTRICITY or PHOTOVOLTAIC" >&2; exit 1 ;; esac

cd "$(dirname "$0")/.."
set -a
. ./.env
set +a

API_URL="${API_URL:-http://localhost:${APP_PORT:-3000}}"
JAR=$(mktemp)
trap 'rm -f "$JAR" "$JAR".*' EXIT

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

xsrf() {
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR"
}

# post <url> <curl options...>: prints the answer, fails unless 201
post() {
  url="$1"
  shift
  code=$(curl -sS -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $(xsrf)" "$@" "$API_URL$url" -w '%{http_code}' -o "$JAR.out")
  if [ "$code" != "201" ]; then
    echo "  refused ($code) $(cat "$JAR.out")" >&2
    return 1
  fi
  cat "$JAR.out"
}

curl -fsS -c "$JAR" -b "$JAR" "$API_URL/api/auth/csrf" -o /dev/null
printf '{"email":"%s","password":"%s"}' "$(json_escape "$BOOTSTRAP_ADMIN_EMAIL")" \
  "$(json_escape "$BOOTSTRAP_ADMIN_PASSWORD")" |
  curl -fsS -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $(xsrf)" -H "Content-Type: application/json" \
    --data @- "$API_URL/api/auth/login" -o /dev/null

echo "== Memoirs"
curl -fsS -b "$JAR" "$API_URL/api/memoir-library" -o "$JAR.memoirs"
memoir() {
  title="$1"
  file="$2"
  if grep -qF "\"title\":\"$(json_escape "$title")\"" "$JAR.memoirs"; then
    echo "Already there: $title"
  elif post /api/memoir-library -F "file=@$file" --form-string "title=$title" \
      --form-string "activityCode=$ACTIVITY" > /dev/null; then
    echo "$title: $(basename "$file")"
  fi
}

# Word first, then PDF
word_first() {
  grep -iE '\.(docx|doc|odt|pdf)$' | awk '{ print (tolower($0) ~ /\.pdf$/ ? 1 : 0) "\t" $0 }' | sort | head -1 | cut -f2
}

for job in "$ROOT"/*/; do
  title=$(basename "$job")
  pieces="$job/03-Dossier RAO/01-Pieces principales"
  if [ -d "$pieces" ]; then
    # « MT » as a word or « mémoire », not its annexes nor Office lock files
    found=$(ls "$pieces" | grep -iE '(^|[^a-z])mt([^a-z]|$)|moire' | grep -viE '^annexe|^~' | word_first)
  else
    # Older folders: the memoir is the « MT… » file at the top of the job folder
    pieces="$job"
    found=$(ls -p "$pieces" | grep -v '/$' | grep -iE '^mt' | word_first)
  fi
  if [ -z "$found" ]; then
    echo "No memoir found: $title" >&2
    continue
  fi
  memoir "$title" "$pieces/$found"
done
# A memoir alone at the top of the folder: one job each, titled by its name
for file in "$ROOT"/[Mm][Tt]*; do
  [ -f "$file" ] || continue
  case "$file" in *.doc|*.docx|*.odt|*.pdf|*.DOC|*.DOCX|*.PDF) ;; *) continue ;; esac
  name=$(basename "$file")
  memoir "${name%.*}" "$file"
done

echo "== Price schedules"
curl -fsS -b "$JAR" "$API_URL/api/price-library/imports" -o "$JAR.prices"
# The filled schedules sent (DPGF, BPU, DQE): their unit prices go to the price library
find "$ROOT" -path "*/03-Dossier RAO/01-Pieces principales/*" -type f \
  \( -iname "*.xlsx" -o -iname "*.xlsm" -o -iname "*.xls" -o -iname "*.ods" -o -iname "*.csv" \) |
while IFS= read -r file; do
  label=$(basename "$(dirname "$(dirname "$(dirname "$file")")")")
  case "$(basename "$file")" in "~"*) continue ;; esac
  if grep -qF "\"label\":\"$(json_escape "$label")\"" "$JAR.prices"; then
    echo "Already there: $label"
    continue
  fi
  if answer=$(post /api/price-library/imports -F "file=@$file" --form-string "label=$label" \
      --form-string "domain=$ACTIVITY"); then
    echo "$label: $(printf '%s' "$answer" | sed -n 's/.*"itemCount":\([0-9]*\).*/\1/p') prix"
  fi
done

category() {
  case "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" in
    *kbis*) echo KBIS ;;
    *assurance*) echo INSURANCE ;;
    *fiscale*) echo TAX ;;
    *urssaf*|*vigilance*) echo VIGILANCE ;;
    *cibtp*|*travailleur*) echo SOCIAL ;;
    *quali*) echo CERTIFICATE ;;
    *ca-*|*bilan*) echo FINANCIAL ;;
    *charte*|*securit*) echo SAFETY ;;
    *humains*) echo STAFF ;;
    *mat*riel*) echo EQUIPMENT ;;
    *r*alisations*|*r*f*rences*|*profile*) echo REFERENCES ;;
    *) echo OTHER ;;
  esac
}

echo "== Company documents"
curl -fsS -b "$JAR" "$API_URL/api/company/documents" -o "$JAR.documents"
: > "$JAR.seen"
# the last job folders first, so that the most recent copy of a document is kept
find "$ROOT" -type f \( -path "*/03-Dossier RAO/02-Situation GPE/*" -o -path "*/03-Dossier RAO/03-Assurance et Rib/*" \
  -o -path "*/03-Dossier RAO/04-R*f*rences/*" -o -path "*/03-Dossier RAO/05-Qualification/*" \) |
  sort -r |
while IFS= read -r file; do
  base=$(basename "$file")
  name="${base%.*}"
  case "$base" in "~"*|*" - Copie."*) continue ;; esac
  grep -qxF "$name" "$JAR.seen" && continue
  echo "$name" >> "$JAR.seen"
  if grep -qF "\"name\":\"$(json_escape "$name")\"" "$JAR.documents"; then
    echo "Already there: $name"
    continue
  fi
  created=$(printf '{"category":"%s","name":"%s"}' "$(category "$name")" "$(json_escape "$name")" |
    post /api/company/documents -H "Content-Type: application/json" --data @-) || continue
  # the keys come in alphabetical order; a new document has no file, so its last "id" is its own
  id=$(printf '%s' "$created" | sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p')
  if post "/api/company/documents/$id/files" -F "file=@$file" > /dev/null; then
    echo "$name ($(category "$name"))"
  fi
done
