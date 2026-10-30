#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. Would enable and start only gt2-backend.service, then check health.\n'
  printf 'Re-run with: sudo %s --apply\n' "$0"
  exit 0
fi
if [[ ${EUID} -ne 0 ]]; then
  printf 'Run with sudo.\n' >&2
  exit 1
fi
if [[ ! -f /etc/gt2/backend.env || ! -r /home/gutta/gapps/gt2/current/gt2-backend.jar ]]; then
  printf 'The protected environment or current GT2 release is missing.\n' >&2
  exit 1
fi
if ! systemctl is-active --quiet gt2-backend.service && ss -H -ltn '( sport = :8091 )' | grep -q .; then
  printf 'Port 8091 is already occupied by a non-running GT2 service.\n' >&2
  exit 1
fi

systemctl enable --now gt2-backend.service
for attempt in {1..30}; do
  if "$script_dir/health-check.sh" >/dev/null 2>&1; then
    "$script_dir/health-check.sh"
    printf 'GT2 started successfully; no other service was restarted.\n'
    exit 0
  fi
  sleep 1
done

printf 'GT2 did not become healthy within 30 seconds.\n' >&2
journalctl -u gt2-backend.service -n 80 --no-pager --output=short-iso >&2
exit 1
