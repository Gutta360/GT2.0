#!/usr/bin/env bash
set -euo pipefail

lines=${1:-100}
if [[ ! "$lines" =~ ^[1-9][0-9]{0,3}$ ]]; then
  printf 'Usage: %s [line-count from 1 to 9999]\n' "$0" >&2
  exit 2
fi
journalctl -u gt2-backend.service -n "$lines" --no-pager --output=short-iso
