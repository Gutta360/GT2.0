#!/usr/bin/env bash
set -euo pipefail

app_root=/home/gutta/gapps/gt2
env_dir=/etc/gt2
env_file=$env_dir/backend.env

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. This script would:\n'
  printf '  create the dedicated gt2 system identity\n'
  printf '  create %s and its release/snapshot/backup directories\n' "$app_root"
  printf '  create private runtime bind-mount targets under /opt/gt2 and /var/lib/gt2\n'
  printf '  create %s with generated secrets if it does not exist\n' "$env_file"
  printf 'Re-run with: sudo %s --apply\n' "$0"
  exit 0
fi
if [[ ${EUID} -ne 0 ]]; then
  printf 'Run with sudo.\n' >&2
  exit 1
fi

if ! getent group gt2 >/dev/null; then
  groupadd --system gt2
fi
if ! id gt2 >/dev/null 2>&1; then
  useradd --system --gid gt2 --home-dir /nonexistent --shell /usr/sbin/nologin gt2
fi

install -d -o root -g gt2 -m 0755 "$app_root"
install -d -o gutta -g gt2 -m 0750 "$app_root/incoming"
install -d -o root -g gt2 -m 0750 "$app_root/releases"
install -d -o root -g gt2 -m 0750 "$app_root/shared"
install -d -o gt2 -g gt2 -m 0700 "$app_root/shared/snapshots"
install -d -o root -g gt2 -m 0700 "$app_root/shared/backups"
install -d -o root -g gt2 -m 0750 /opt/gt2 /opt/gt2/current
install -d -o gt2 -g gt2 -m 0700 /var/lib/gt2 /var/lib/gt2/snapshots
install -d -o root -g root -m 0750 "$env_dir"

if [[ -e "$env_file" ]]; then
  if [[ ! -f "$env_file" || -L "$env_file" ]]; then
    printf '%s exists but is not a regular file; refusing to continue.\n' "$env_file" >&2
    exit 1
  fi
  chown root:root "$env_file"
  chmod 0600 "$env_file"
  printf 'Preserved the existing protected environment file.\n'
else
  umask 077
  temporary=$(mktemp "$env_dir/backend.env.XXXXXX")
  trap 'rm -f "$temporary"' EXIT
  db_password=$(openssl rand -hex 32)
  admin_token=$(openssl rand -hex 32)
  snapshot_key=$(openssl rand -base64 32 | tr -d '\n')
  {
    printf 'GT2_DB_URL=jdbc:postgresql://127.0.0.1:5432/gt2\n'
    printf 'GT2_DB_USERNAME=gt2_app\n'
    printf 'GT2_DB_PASSWORD=%s\n' "$db_password"
    printf 'GT2_ADMIN_TOKEN=%s\n' "$admin_token"
    printf 'GT2_SNAPSHOT_MASTER_KEY=%s\n' "$snapshot_key"
    printf 'GT2_SNAPSHOT_STORAGE=/var/lib/gt2/snapshots\n'
    printf 'GT2_ALLOWED_ORIGINS=https://localhost\n'
    printf 'GT2_SERVER_PORT=8091\n'
    printf 'GT2_SERVER_ADDRESS=127.0.0.1\n'
    printf 'GT2_ENROLLMENT_TTL=PT30M\n'
    printf 'GT2_MAX_BATCH_SIZE=100\n'
    printf 'GT2_MAX_SNAPSHOT_BYTES=10485760\n'
    printf 'GT2_SNAPSHOT_RETENTION_COUNT=5\n'
  } > "$temporary"
  install -o root -g root -m 0600 "$temporary" "$env_file"
  rm -f "$temporary"
  trap - EXIT
  unset db_password admin_token snapshot_key
  printf 'Created %s with generated values; secret values were not displayed.\n' "$env_file"
fi

printf 'GT2 host directories are prepared. No database, unit, or service was changed.\n'
