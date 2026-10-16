#!/usr/bin/env bash
set -Eeuo pipefail

readonly POSTGRES_CONTAINER="${CARABO_POSTGRES_CONTAINER:-tordo_dev-postgres-1}"
readonly STORAGE_VOLUME="${CARABO_STORAGE_VOLUME:-tordo_dev_carabo_storage}"
readonly CERTS_VOLUME="${CARABO_CERTS_VOLUME:-tordo_dev_carabo_certs}"
readonly OUTPUT_ROOT="${1:-build/backups}"
readonly BACKUP_ID="carabo-$(date -u +%Y%m%dT%H%M%SZ)"
readonly BACKUP_DIR="$OUTPUT_ROOT/$BACKUP_ID"

command -v docker >/dev/null || { echo 'Docker es obligatorio' >&2; exit 1; }
command -v sha256sum >/dev/null || { echo 'sha256sum es obligatorio' >&2; exit 1; }
docker inspect "$POSTGRES_CONTAINER" >/dev/null
docker volume inspect "$STORAGE_VOLUME" "$CERTS_VOLUME" >/dev/null

mkdir -p "$BACKUP_DIR"
docker exec "$POSTGRES_CONTAINER" sh -lc \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --schema=carabo --no-owner' \
  > "$BACKUP_DIR/database.dump"

absolute_backup_dir="$(cd "$BACKUP_DIR" && pwd)"
docker run --rm --volume "$STORAGE_VOLUME:/source:ro" --volume "$absolute_backup_dir:/backup" \
  alpine:3.21 tar -C /source -czf /backup/documents.tar.gz .
docker run --rm --volume "$CERTS_VOLUME:/source:ro" --volume "$absolute_backup_dir:/backup" \
  alpine:3.21 tar -C /source -czf /backup/certificates.tar.gz .

docker inspect "$POSTGRES_CONTAINER" --format '{{.Image}}' > "$BACKUP_DIR/postgres-image.txt"
(cd "$BACKUP_DIR" && sha256sum database.dump documents.tar.gz certificates.tar.gz postgres-image.txt > SHA256SUMS)
printf 'Backup verificable creado en %s\n' "$BACKUP_DIR"
printf 'El secret store NO está incluido. Debe respaldarse por su canal seguro independiente.\n'
