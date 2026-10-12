#!/bin/sh
# Backup of the database and of the uploaded files, run in the "backup"
# container. With --daily, makes one backup a day, at BACKUP_TIME (Paris time)
# or, if the computer was off or asleep then, as soon as it runs again;
# without argument, makes one backup now.
#
#   docker compose exec backup sh /scripts/backup.sh
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
AT="${BACKUP_TIME:-02:00}"
# How often the daily mode checks whether a backup is due, in seconds.
CHECK_EVERY="${BACKUP_CHECK_EVERY:-600}"

backup() {
  stamp=$(date +%Y-%m-%d_%H%M)
  # Written under a temporary name: an interrupted backup never looks complete.
  pg_dump --format=custom --file="/backups/.database_$stamp.dump"
  mv "/backups/.database_$stamp.dump" "/backups/database_$stamp.dump"
  tar -czf "/backups/.files_$stamp.tar.gz" -C /data storage
  mv "/backups/.files_$stamp.tar.gz" "/backups/files_$stamp.tar.gz"
  # Only the daily backups (named by their date) are removed when old; a
  # backup named otherwise, such as one made before a deployment, is kept.
  find /backups -maxdepth 1 \( -name 'database_2*.dump' -o -name 'files_2*.tar.gz' \) -mtime +"$KEEP_DAYS" -delete
  echo "$(date '+%Y-%m-%d %H:%M') backup $stamp done"
}

# A backup is due when none was made since the last BACKUP_TIME passed.
due() {
  now=$(date +%s)
  since=$(date -d "today $AT" +%s)
  if [ "$since" -gt "$now" ]; then
    since=$(date -d "yesterday $AT" +%s)
  fi
  [ -z "$(find /backups -maxdepth 1 -name 'database_*.dump' -newermt "@$since" | head -n 1)" ]
}

if [ "${1:-}" = "--daily" ]; then
  echo "Daily backup at $AT or at the next start after it, kept $KEEP_DAYS days"
  while true; do
    if due; then
      backup || echo "$(date '+%Y-%m-%d %H:%M') BACKUP FAILED" >&2
    fi
    sleep "$CHECK_EVERY"
  done
else
  backup
fi
