#!/usr/bin/env bash
set -Eeuo pipefail

readonly BACKUP_DIR="${1:-}"
readonly CONFIRM="${2:-}"
readonly POSTGRES_CONTAINER="${CARABO_POSTGRES_CONTAINER:-tordo_dev-postgres-1}"
readonly CARABO_CONTAINER="${CARABO_CONTAINER:-tordo_dev-carabo-1}"
readonly STORAGE_VOLUME="${CARABO_STORAGE_VOLUME:-tordo_dev_carabo_storage}"
readonly CERTS_VOLUME="${CARABO_CERTS_VOLUME:-tordo_dev_carabo_certs}"

[[ -n "$BACKUP_DIR" && -d "$BACKUP_DIR" ]] || {
  echo 'Uso: restore-carabo-dev.sh <directorio-backup> --confirm' >&2; exit 1;
}
[[ "$CONFIRM" == "--confirm" ]] || {
  echo 'Restore cancelado: añade --confirm; reemplazará schema carabo y ambos volúmenes.' >&2; exit 1;
}
(cd "$BACKUP_DIR" && sha256sum --check SHA256SUMS)

absolute_backup_dir="$(cd "$BACKUP_DIR" && pwd)"
docker stop "$CARABO_CONTAINER" >/dev/null
trap 'docker start "$CARABO_CONTAINER" >/dev/null 2>&1 || true' EXIT

docker exec -i "$POSTGRES_CONTAINER" sh -lc \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' \
  < "$BACKUP_DIR/database.dump"

docker run --rm --volume "$STORAGE_VOLUME:/target" --volume "$absolute_backup_dir:/backup:ro" \
  alpine:3.21 sh -eu -c 'find /target -mindepth 1 -delete; tar -C /target -xzf /backup/documents.tar.gz'
docker run --rm --volume "$CERTS_VOLUME:/target" --volume "$absolute_backup_dir:/backup:ro" \
  alpine:3.21 sh -eu -c 'find /target -mindepth 1 -delete; tar -C /target -xzf /backup/certificates.tar.gz'

docker start "$CARABO_CONTAINER" >/dev/null
trap - EXIT
printf 'Restore completado. Verifica health, preflight fiscal y un XML/CDR conocido.\n'
