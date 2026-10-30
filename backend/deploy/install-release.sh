#!/usr/bin/env bash
set -euo pipefail

app_root=/home/gutta/gapps/gt2

usage() {
  printf 'Usage: sudo %s --apply /absolute/path/to/staged.jar EXPECTED_SHA256\n' "$0" >&2
}

if [[ $# -ne 3 || "$1" != "--apply" ]]; then
  usage
  printf 'Refusing to change the current release without --apply.\n' >&2
  exit 2
fi

if [[ ${EUID} -ne 0 ]]; then
  printf 'Run this release installer with sudo.\n' >&2
  exit 1
fi

artifact=$2
expected_sha=$3
if [[ ! -f "$artifact" || "$artifact" != "$app_root"/incoming/*.jar ]]; then
  printf 'The artifact must be a staged JAR under %s/incoming.\n' "$app_root" >&2
  exit 2
fi
if [[ ! "$expected_sha" =~ ^[0-9a-f]{64}$ ]]; then
  printf 'EXPECTED_SHA256 must be 64 lowercase hexadecimal characters.\n' >&2
  exit 2
fi

actual_sha=$(sha256sum "$artifact" | awk '{print $1}')
if [[ "$actual_sha" != "$expected_sha" ]]; then
  printf 'Artifact checksum mismatch; release was not installed.\n' >&2
  exit 1
fi
if ! /usr/bin/java -Djarmode=tools -jar "$artifact" list >/dev/null 2>&1; then
  printf 'Artifact is not a readable Spring Boot executable JAR.\n' >&2
  exit 1
fi

release_id=${expected_sha:0:16}
release_dir="$app_root/releases/$release_id"
install -d -o root -g gt2 -m 0750 "$release_dir"
install -o root -g gt2 -m 0640 "$artifact" "$release_dir/gt2-backend.jar.new"
mv -f "$release_dir/gt2-backend.jar.new" "$release_dir/gt2-backend.jar"
printf '%s  gt2-backend.jar\n' "$expected_sha" > "$release_dir/SHA256SUMS"
chown root:gt2 "$release_dir/SHA256SUMS"
chmod 0640 "$release_dir/SHA256SUMS"

if [[ -L "$app_root/current" ]]; then
  current_target=$(readlink -f "$app_root/current")
  if [[ "$current_target" != "$release_dir" ]]; then
    ln -sfn "$current_target" "$app_root/.previous.next"
    mv -Tf "$app_root/.previous.next" "$app_root/previous"
  fi
fi

ln -sfn "$release_dir" "$app_root/.current.next"
mv -Tf "$app_root/.current.next" "$app_root/current"

printf 'Installed release %s and updated the GT2 current symlink.\n' "$release_id"
printf 'The service was not started or restarted.\n'
