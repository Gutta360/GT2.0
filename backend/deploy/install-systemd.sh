#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source_unit="$script_dir/gt2-backend.service"
target_unit=/etc/systemd/system/gt2-backend.service

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. Would install %s as %s and reload systemd.\n' "$source_unit" "$target_unit"
  printf 'The unit would not be enabled or started. Re-run with sudo %s --apply.\n' "$0"
  exit 0
fi
if [[ ${EUID} -ne 0 ]]; then
  printf 'Run with sudo.\n' >&2
  exit 1
fi
if [[ ! -f "$source_unit" ]]; then
  printf 'Unit template is missing: %s\n' "$source_unit" >&2
  exit 1
fi

systemd-analyze verify "$source_unit"
install -o root -g root -m 0644 "$source_unit" "$target_unit"
systemctl daemon-reload
printf 'Installed the GT2 unit. It was not enabled or started.\n'
