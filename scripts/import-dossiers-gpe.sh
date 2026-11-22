#!/bin/sh
# Adds the response folders GPE already sent to its libraries, as the pages
# Mémoires > Bibliothèque and Entreprise > Documents do one file at a time:
# - the technical memoir of each job ("<job>/03-Dossier RAO/01-Pieces
#   principales", Word preferred to PDF), titled with the job folder name;
# - the company documents of "03-Dossier RAO/02-Situation GPE",
#   "03-Assurance et Rib", "04-Références" and "05-Qualification", one per
#   name, the copy of the last job folder.
# What is already there (same title or name) is skipped: it can be run again.
#
#   scripts/import-dossiers-gpe.sh "<folder AO PV 1>" PHOTOVOLTAIC
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
for job in "$ROOT"/*/; do
  title=$(basename "$job")
  pieces="$job/03-Dossier RAO/01-Pieces principales"
  [ -d "$pieces" ] || continue
  if grep -qF "\"title\":\"$(json_escape "$title")\"" "$JAR.memoirs"; then
    echo "Already there: $title"
    continue
  fi
  # « MT » as a word or « mémoire », not its annexes nor Office lock files; Word first
  memoir=$(ls "$pieces" | grep -iE '(^|[^a-z])mt([^a-z]|$)|moire' | grep -viE '^annexe|^~' |
    grep -iE '\.(docx|doc|odt|pdf)$' | awk '{ print (tolower($0) ~ /\.pdf$/ ? 1 : 0) "\t" $0 }' |
    sort | head -1 | cut -f2)
  if [ -z "$memoir" ]; then
    echo "No memoir found: $title" >&2
    continue
  fi
  if post /api/memoir-library -F "file=@$pieces/$memoir" --form-string "title=$title" \
      --form-string "activityCode=$ACTIVITY" > /dev/null; then
    echo "$title: $memoir"
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
  id=$(printf '%s' "$created" | sed 's/^{"id":\([0-9]*\).*/\1/')
  if post "/api/company/documents/$id/files" -F "file=@$file" > /dev/null; then
    echo "$name ($(category "$name"))"
  fi
done
