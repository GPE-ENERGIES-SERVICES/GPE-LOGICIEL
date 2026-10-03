#!/bin/sh
# Restores a backup made by backup.sh, in the "backup" container. Stop the
# backend first so that nothing writes during the restore:
#
#   docker compose stop backend
#   docker compose exec backup sh /scripts/restore.sh 2026-10-03_0200
#   docker compose start backend
set -eu

if [ $# -ne 1 ]; then
  echo "Usage: restore.sh <date of the backup, for example 2026-10-03_0200>" >&2
  ls /backups >&2
  exit 1
fi

database="/backups/database_$1.dump"
files="/backups/files_$1.tar.gz"
for file in "$database" "$files"; do
  if [ ! -f "$file" ]; then
    echo "Missing $file" >&2
    exit 1
  fi
done

pg_restore --clean --if-exists --no-owner --dbname="$PGDATABASE" "$database"
rm -rf /data/storage/*
tar -xzf "$files" -C /data
echo "Backup $1 restored"
