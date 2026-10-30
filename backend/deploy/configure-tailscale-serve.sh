#!/usr/bin/env bash
set -euo pipefail

target=http://127.0.0.1:8091
expected_host=rwp-server.tail203f6d.ts.net

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. Would publish %s to this tailnet over private HTTPS.\n' "$target"
  printf 'Expected URL: https://%s\n' "$expected_host"
  printf 'No public firewall port or Tailscale Funnel would be enabled.\n'
  printf 'Re-run after GT2 health passes: %s --apply\n' "$0"
  exit 0
fi
if ! curl --fail --silent --max-time 5 "$target/actuator/health" | grep -q '"status":"UP"'; then
  printf 'GT2 local health is not UP; refusing to configure Tailscale Serve.\n' >&2
  exit 1
fi
if tailscale serve status 2>/dev/null | grep -q 'https://'; then
  printf 'A Tailscale Serve configuration already exists; refusing to replace it.\n' >&2
  tailscale serve status >&2
  exit 1
fi

tailscale serve --bg --https=443 "$target"
tailscale serve status
printf 'Private tablet backend URL: https://%s\n' "$expected_host"
printf 'Do not run tailscale funnel for GT2.\n'
