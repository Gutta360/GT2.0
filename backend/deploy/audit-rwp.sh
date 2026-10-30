#!/usr/bin/env bash
set -euo pipefail

printf '[host]\n'
hostname
uname -srmo
java -version 2>&1

printf '\n[gapps]\n'
find /home/gutta/gapps -maxdepth 3 -printf '%M %u:%g %p\n' 2>/dev/null | sort

printf '\n[containers]\n'
docker ps --format 'name={{.Names}} image={{.Image}} status={{.Status}} ports={{.Ports}}'

printf '\n[postgres inventory without credentials]\n'
docker exec gapps-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "${POSTGRES_DB:-postgres}" -At -F "|" -c "SELECT datname, pg_get_userbyid(datdba), pg_size_pretty(pg_database_size(datname)) FROM pg_database WHERE NOT datistemplate ORDER BY datname"' | sed 's/^/DATABASE /'
docker exec gapps-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "${POSTGRES_DB:-postgres}" -At -F "|" -c "SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin FROM pg_roles ORDER BY rolname"' | sed 's/^/ROLE /'

printf '\n[application services]\n'
systemctl show gdc-backend.service gdclab-backend.service gt2-backend.service --no-pager --property=Id,LoadState,ActiveState,SubState,FragmentPath,User,WorkingDirectory,ExecStart,EnvironmentFiles 2>/dev/null || true

printf '\n[listening TCP ports]\n'
ss -H -ltn | sort -k4

printf '\n[nginx safe routing directives]\n'
for config in $(find -L /etc/nginx/sites-enabled /etc/nginx/conf.d -maxdepth 2 -type f -print 2>/dev/null | sort); do
  printf 'CONFIG %s\n' "$config"
  awk '/^[[:space:]]*(listen|server_name|location|proxy_pass|root|index|try_files|include|return|client_max_body_size|proxy_set_header)[[:space:]]/ {print}' "$config"
done

printf '\n[tailscale]\n'
tailscale ip -4
tailscale status | sed -n '1p'
tailscale serve status 2>&1 || true

printf '\n[storage]\n'
df -hT /home/gutta/gapps

printf '\nThis audit made no changes and intentionally omitted secret values.\n'
