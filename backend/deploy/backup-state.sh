#!/usr/bin/env bash
set -euo pipefail

app_root=/home/gutta/gapps/gt2
container=gapps-postgres
backup_root=$app_root/shared/backups
env_file=/etc/gt2/backend.env

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. Would briefly stop only GT2, then back up its database, snapshots, and protected environment.\n'
  printf 'Re-run with: sudo %s --apply\n' "$0"
  exit 0
fi
if [[ ${EUID} -ne 0 ]]; then
  printf 'Run with sudo.\n' >&2
  exit 1
fi
umask 077
if [[ ! -f "$env_file" || ! -d "$app_root/shared/snapshots" ]]; then
  printf 'GT2 environment or snapshot directory is missing.\n' >&2
  exit 1
fi

was_active=false
if systemctl is-active --quiet gt2-backend.service; then
  was_active=true
  systemctl stop gt2-backend.service
fi
restart_if_needed() {
  if [[ "$was_active" == true ]]; then
    systemctl start gt2-backend.service || true
  fi
}
trap restart_if_needed EXIT

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="$backup_root/$timestamp"
install -d -o root -g gt2 -m 0700 "$backup_dir"

docker exec "$container" sh -lc 'exec pg_dump -U "$POSTGRES_USER" --format=custom --no-owner --no-privileges gt2' > "$backup_dir/gt2.dump.tmp"
mv "$backup_dir/gt2.dump.tmp" "$backup_dir/gt2.dump"
chmod 0600 "$backup_dir/gt2.dump"
tar --create --gzip --file "$backup_dir/snapshots.tar.gz.tmp" --directory "$app_root/shared" snapshots
mv "$backup_dir/snapshots.tar.gz.tmp" "$backup_dir/snapshots.tar.gz"
chmod 0600 "$backup_dir/snapshots.tar.gz"
install -o root -g root -m 0600 "$env_file" "$backup_dir/backend.env"
sha256sum "$backup_dir/gt2.dump" "$backup_dir/snapshots.tar.gz" > "$backup_dir/SHA256SUMS"
chmod 0600 "$backup_dir/SHA256SUMS"

restart_if_needed
was_active=false
trap - EXIT
printf 'Created protected GT2 backup set: %s\n' "$backup_dir"
printf 'Copy it to encrypted off-host storage; this server-local copy is not disaster recovery.\n'
