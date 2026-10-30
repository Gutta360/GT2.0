#!/usr/bin/env bash
set -euo pipefail

url=${GT2_HEALTH_URL:-http://127.0.0.1:8091/actuator/health}
response=$(curl --fail --silent --show-error --connect-timeout 3 --max-time 10 "$url")
if [[ "$response" != *'"status":"UP"'* ]]; then
  printf 'GT2 health response was not UP.\n' >&2
  exit 1
fi
printf 'GT2 health is UP at %s\n' "$url"
