#!/bin/sh
# Backup of the database and of the uploaded files, run in the "backup"
# container. With --daily, runs every day at BACKUP_TIME (Paris time);
# without argument, makes one backup now.
#
#   docker compose exec backup sh /scripts/backup.sh
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
AT="${BACKUP_TIME:-02:00}"

backup() {
  stamp=$(date +%Y-%m-%d_%H%M)
  # Written under a temporary name: an interrupted backup never looks complete.
  pg_dump --format=custom --file="/backups/.database_$stamp.dump"
  mv "/backups/.database_$stamp.dump" "/backups/database_$stamp.dump"
  tar -czf "/backups/.files_$stamp.tar.gz" -C /data storage
  mv "/backups/.files_$stamp.tar.gz" "/backups/files_$stamp.tar.gz"
  find /backups -maxdepth 1 \( -name 'database_*.dump' -o -name 'files_*.tar.gz' \) -mtime +"$KEEP_DAYS" -delete
  echo "$(date '+%Y-%m-%d %H:%M') backup $stamp done"
}

if [ "${1:-}" = "--daily" ]; then
  echo "Daily backup at $AT, kept $KEEP_DAYS days"
  while true; do
    now=$(date +%s)
    next=$(date -d "today $AT" +%s)
    if [ "$next" -le "$now" ]; then
      next=$(date -d "tomorrow $AT" +%s)
    fi
    sleep $((next - now))
    backup || echo "$(date '+%Y-%m-%d %H:%M') BACKUP FAILED" >&2
  done
else
  backup
fi
