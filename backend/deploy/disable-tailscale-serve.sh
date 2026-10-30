#!/usr/bin/env bash
set -euo pipefail

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. Would remove HTTPS port 443 from the Tailscale Serve configuration.\n'
  printf 'Re-run with: %s --apply\n' "$0"
  exit 0
fi

status=$(tailscale serve status 2>&1 || true)
if [[ "$status" != *'127.0.0.1:8091'* ]]; then
  printf 'Tailscale Serve does not point to GT2 port 8091; refusing to alter it.\n' >&2
  printf '%s\n' "$status" >&2
  exit 1
fi
tailscale serve --https=443 off
printf 'Removed the GT2 private HTTPS route. No firewall or Nginx setting was changed.\n'
