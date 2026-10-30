#!/usr/bin/env bash
set -euo pipefail

app_root=/home/gutta/gapps/gt2
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. Would swap GT2 current and previous releases and restart only gt2-backend.\n'
  printf 'Database migrations are never reversed by this script.\n'
  printf 'Re-run with: sudo %s --apply\n' "$0"
  exit 0
fi
if [[ ${EUID} -ne 0 ]]; then
  printf 'Run with sudo.\n' >&2
  exit 1
fi
if [[ ! -L "$app_root/current" || ! -L "$app_root/previous" ]]; then
  printf 'Both current and previous GT2 release symlinks are required.\n' >&2
  exit 1
fi

current_target=$(readlink -f "$app_root/current")
previous_target=$(readlink -f "$app_root/previous")
for target in "$current_target" "$previous_target"; do
  if [[ "$target" != "$app_root"/releases/* || ! -r "$target/gt2-backend.jar" ]]; then
    printf 'Release target is outside the managed GT2 release directory.\n' >&2
    exit 1
  fi
done

ln -sfn "$previous_target" "$app_root/.current.rollback"
mv -Tf "$app_root/.current.rollback" "$app_root/current"
ln -sfn "$current_target" "$app_root/.previous.rollback"
mv -Tf "$app_root/.previous.rollback" "$app_root/previous"
systemctl restart gt2-backend.service

for attempt in {1..30}; do
  if "$script_dir/health-check.sh" >/dev/null 2>&1; then
    "$script_dir/health-check.sh"
    printf 'GT2 application release rollback completed.\n'
    exit 0
  fi
  sleep 1
done
printf 'Rolled-back release did not become healthy; inspect GT2 logs.\n' >&2
exit 1
