#!/bin/sh
# Imports the BOAMP notices into the database through the application, as
# the button of the Settings page does, then waits for the end of the import.
# Without date, the import resumes after the last successful one; with a date
# (initial load), it fetches every notice published since that day, at most
# one year back. Notices already imported are never duplicated.
#
#   scripts/import-boamp.sh              # new notices since the last import
#   scripts/import-boamp.sh 2026-09-01   # initial load since September 1st
#
# Logs in with BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD of .env.
# API_URL is the address of the frontend (default http://localhost:APP_PORT).
# The detailed report is written in the backend logs:
#   docker compose logs backend | grep "BOAMP import"
set -eu

cd "$(dirname "$0")/.."
set -a
. ./.env
set +a

API_URL="${API_URL:-http://localhost:${APP_PORT:-3000}}"
SINCE="${1:-}"
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

xsrf() {
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR"
}

curl -fsS -c "$JAR" -b "$JAR" "$API_URL/api/auth/csrf" -o /dev/null
printf '{"email":"%s","password":"%s"}' "$(json_escape "$BOOTSTRAP_ADMIN_EMAIL")" \
  "$(json_escape "$BOOTSTRAP_ADMIN_PASSWORD")" |
  curl -fsS -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $(xsrf)" -H "Content-Type: application/json" \
    --data @- "$API_URL/api/auth/login" -o /dev/null

curl -sS -c "$JAR" -b "$JAR" -X POST -H "X-XSRF-TOKEN: $(xsrf)" \
  "$API_URL/api/collect-runs${SINCE:+?since=$SINCE}" -w '%{http_code}' -o "$JAR.out" > "$JAR.code"
code=$(cat "$JAR.code")
if [ "$code" != "202" ]; then
  echo "Import refused ($code): $(cat "$JAR.out")" >&2
  rm -f "$JAR.out" "$JAR.code"
  exit 1
fi
rm -f "$JAR.out" "$JAR.code"
echo "BOAMP import started${SINCE:+ since $SINCE}..."

while curl -fsS -b "$JAR" "$API_URL/api/collect-runs?limit=1" | grep -q '"running":true'; do
  sleep 5
done
echo "Import finished. Last run:"
curl -fsS -b "$JAR" "$API_URL/api/collect-runs?limit=1" | sed 's/.*"runs":\[\(.*\)\]}/\1/'
echo
