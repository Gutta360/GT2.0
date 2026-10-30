#!/usr/bin/env bash
set -euo pipefail

default_target=gutta@100.65.174.126
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
jar="$script_dir/../target/gt2-backend-1.0.0-SNAPSHOT.jar"

usage() {
  printf 'Usage: %s [--apply] [JAR_PATH] [SSH_TARGET]\n' "$0" >&2
}

apply=false
if [[ ${1:-} == "--apply" ]]; then
  apply=true
  shift
fi
if [[ $# -gt 2 ]]; then
  usage
  exit 2
fi
if [[ $# -ge 1 ]]; then jar=$1; fi
target=${2:-$default_target}
if [[ ! -f "$jar" ]]; then
  printf 'JAR does not exist: %s\n' "$jar" >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  sha=$(sha256sum "$jar" | awk '{print $1}')
else
  sha=$(shasum -a 256 "$jar" | awk '{print $1}')
fi
remote_path="/home/gutta/gapps/gt2/incoming/gt2-backend-${sha:0:16}.jar"

printf 'Source: %s\n' "$jar"
printf 'SHA-256: %s\n' "$sha"
printf 'Destination: %s:%s\n' "$target" "$remote_path"
if [[ "$apply" != true ]]; then
  printf 'Dry run only; re-run with --apply to transfer the artifact.\n'
  exit 0
fi

scp -p "$jar" "$target:$remote_path"
remote_sha=$(ssh "$target" "sha256sum '$remote_path'" | awk '{print $1}')
if [[ "$remote_sha" != "$sha" ]]; then
  printf 'Remote checksum mismatch. Do not install this staged artifact.\n' >&2
  exit 1
fi
printf 'Artifact staged and verified. It has not been installed or started.\n'
printf "After approval on the server: sudo /path/to/install-release.sh --apply '%s' '%s'\n" "$remote_path" "$sha"
