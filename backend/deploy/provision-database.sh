#!/usr/bin/env bash
set -euo pipefail

container=gapps-postgres
database=gt2
role=gt2_app
env_file=/etc/gt2/backend.env

if [[ ${1:-} != "--apply" ]]; then
  printf 'Dry run only. This script provisions role %s and database %s only.\n' "$role" "$database"
  printf 'It never modifies gdc or any non-GT2 database.\n'
  printf 'Re-run with: sudo %s --apply\n' "$0"
  exit 0
fi
if [[ ${EUID} -ne 0 ]]; then
  printf 'Run with sudo so the protected GT2 environment can be read.\n' >&2
  exit 1
fi
if [[ ! -f "$env_file" || -L "$env_file" ]]; then
  printf 'Protected environment file is missing or invalid: %s\n' "$env_file" >&2
  exit 1
fi
if ! docker inspect "$container" >/dev/null 2>&1; then
  printf 'Required container is not available: %s\n' "$container" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a
if [[ ${GT2_DB_USERNAME:-} != "$role" || ${GT2_DB_URL:-} != "jdbc:postgresql://127.0.0.1:5432/$database" ]]; then
  printf 'GT2 database settings do not match the audited isolated names.\n' >&2
  exit 1
fi
if [[ ! ${GT2_DB_PASSWORD:-} =~ ^[0-9a-f]{64}$ ]]; then
  printf 'The generated GT2 database password has an unexpected format.\n' >&2
  exit 1
fi

admin_psql() {
  docker exec -i "$container" sh -lc 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${POSTGRES_DB:-postgres}" "$@"' sh "$@"
}
role_exists=$(admin_psql -Atqc "SELECT count(*) FROM pg_roles WHERE rolname = '$role'")
database_owner=$(admin_psql -Atqc "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = '$database'")

if [[ "$role_exists" == "1" && -z "$database_owner" ]]; then
  printf 'Role %s exists without the expected database; refusing to assume ownership.\n' "$role" >&2
  exit 1
fi
if [[ -n "$database_owner" && "$database_owner" != "$role" ]]; then
  printf 'Database %s already exists with a different owner; refusing to modify it.\n' "$database" >&2
  exit 1
fi
if [[ "$role_exists" == "0" && -n "$database_owner" ]]; then
  printf 'Database %s exists but role %s does not; refusing to modify it.\n' "$database" "$role" >&2
  exit 1
fi

umask 077
sql_file=$(mktemp)
trap 'rm -f "$sql_file"; unset GT2_DB_PASSWORD' EXIT
printf "CREATE ROLE %s LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '%s';\n" "$role" "$GT2_DB_PASSWORD" > "$sql_file"

if [[ "$role_exists" == "0" ]]; then
  admin_psql < "$sql_file" >/dev/null
  docker exec "$container" sh -lc "createdb -U \"\$POSTGRES_USER\" --owner=$role --encoding=UTF8 --template=template0 $database"
  printf 'Created isolated GT2 database role and database.\n'
else
  printf "ALTER ROLE %s WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '%s';\n" "$role" "$GT2_DB_PASSWORD" > "$sql_file"
  admin_psql < "$sql_file" >/dev/null
  printf 'Validated the existing GT2 database and aligned its role credential.\n'
fi

admin_psql -v database="$database" -v role="$role" <<'SQL' >/dev/null
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'database') \gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'database', :'role') \gexec
SQL

rm -f "$sql_file"
unset GT2_DB_PASSWORD
trap - EXIT
printf 'Database provisioning completed without changing existing application databases.\n'
