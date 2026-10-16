#!/usr/bin/env bash
set -Eeuo pipefail

readonly BASE_URL="${CARABO_BASE_URL:-https://dev.tordo.io/carabo/api}"
readonly PAYLOAD_FILE="${CARABO_E2E_PAYLOAD:-examples/sunat-beta-invoice.json}"
readonly API_KEY="${CARABO_API_KEY:-}"
readonly TIMEOUT_SECONDS="${CARABO_E2E_TIMEOUT_SECONDS:-180}"
readonly POLL_SECONDS="${CARABO_E2E_POLL_SECONDS:-2}"
readonly EXTERNAL_ID="${CARABO_E2E_EXTERNAL_ID:-e2e-sunat-$(date -u +%Y%m%dT%H%M%SZ)}"
readonly OUTPUT_DIR="${CARABO_E2E_OUTPUT_DIR:-build/e2e/${EXTERNAL_ID}}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

for command in curl jq sha256sum; do
  command -v "$command" >/dev/null || fail "Falta la herramienta requerida: $command"
done

[[ -n "$API_KEY" ]] || fail "Define CARABO_API_KEY sin escribirla en el repositorio."
[[ -r "$PAYLOAD_FILE" ]] || fail "No se puede leer el payload: $PAYLOAD_FILE"
[[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || fail "CARABO_E2E_TIMEOUT_SECONDS debe ser entero."
[[ "$POLL_SECONDS" =~ ^[0-9]+$ ]] || fail "CARABO_E2E_POLL_SECONDS debe ser entero."

mkdir -p "$OUTPUT_DIR"
request_file="$OUTPUT_DIR/request.json"
ingest_file="$OUTPUT_DIR/ingest.json"
status_file="$OUTPUT_DIR/status.json"
headers_file="$OUTPUT_DIR/ingest.headers"

jq --arg issueDate "$(date -u +%F)" --arg externalId "$EXTERNAL_ID" \
  '.issueDate = $issueDate | .externalId = $externalId' "$PAYLOAD_FILE" > "$request_file"

printf '1/5 Verificando API %s\n' "$BASE_URL"
curl --silent --show-error --fail --max-time 15 "$BASE_URL/health" >/dev/null

printf '2/5 Encolando %s\n' "$EXTERNAL_ID"
http_code="$(curl --silent --show-error --max-time 30 \
  --output "$ingest_file" --dump-header "$headers_file" --write-out '%{http_code}' \
  --request POST "$BASE_URL/documents/ingest" \
  --header "X-Api-Key: $API_KEY" \
  --header "Idempotency-Key: $EXTERNAL_ID" \
  --header 'Content-Type: application/json' \
  --data-binary "@$request_file")"

if [[ "$http_code" != "202" && "$http_code" != "200" ]]; then
  jq . "$ingest_file" 2>/dev/null || sed -n '1,20p' "$ingest_file"
  fail "La ingestión respondió HTTP $http_code."
fi
jq -e --arg id "$EXTERNAL_ID" '.externalId == $id' "$ingest_file" >/dev/null \
  || fail "La respuesta no conserva el externalId solicitado."

printf '3/5 Esperando Worker → XML → Firma → SUNAT → CDR\n'
deadline=$((SECONDS + TIMEOUT_SECONDS))
status="QUEUED"
document_id=""
while (( SECONDS < deadline )); do
  http_code="$(curl --silent --show-error --max-time 20 \
    --output "$status_file" --write-out '%{http_code}' \
    "$BASE_URL/documents/external/$(jq -rn --arg value "$EXTERNAL_ID" '$value|@uri')" \
    --header "X-Api-Key: $API_KEY")"
  if [[ "$http_code" == "404" ]]; then
    sleep "$POLL_SECONDS"
    continue
  fi
  [[ "$http_code" == "200" ]] || fail "La consulta de estado respondió HTTP $http_code."
  status="$(jq -er '.status' "$status_file")"
  document_id="$(jq -er '.id' "$status_file")"
  printf '    estado=%s documento=%s\n' "$status" "$document_id"
  case "$status" in
    ACCEPTED|OBSERVED|REJECTED|ERROR) break ;;
  esac
  sleep "$POLL_SECONDS"
done

[[ "$status" != "QUEUED" && "$status" != "VALIDATED" && "$status" != "PENDING" ]] \
  || fail "El documento no llegó a estado terminal en ${TIMEOUT_SECONDS}s."

printf '4/5 Descargando evidencias fiscales\n'
[[ -n "$document_id" && "$document_id" != "null" ]] || fail "El estado terminal no incluye documentId."
curl --silent --show-error --fail --max-time 30 \
  "$BASE_URL/documents/$document_id/xml" --header "X-Api-Key: $API_KEY" \
  --output "$OUTPUT_DIR/signed.xml"
[[ -s "$OUTPUT_DIR/signed.xml" ]] || fail "El XML firmado está vacío."

if [[ "$status" == "ACCEPTED" || "$status" == "OBSERVED" || "$status" == "REJECTED" ]]; then
  curl --silent --show-error --fail --max-time 30 \
    "$BASE_URL/documents/$document_id/cdr" --header "X-Api-Key: $API_KEY" \
    --output "$OUTPUT_DIR/cdr.xml"
  [[ -s "$OUTPUT_DIR/cdr.xml" ]] || fail "El CDR está vacío."
fi

(cd "$OUTPUT_DIR" && sha256sum request.json ingest.json status.json signed.xml \
  $(test -f cdr.xml && printf '%s' cdr.xml) > SHA256SUMS)

printf '5/5 Resultado: %s\n' "$status"
jq '{id, externalId, fullNumber, status, hashCode, sunatResponse}' "$status_file"
printf 'Evidencia: %s\n' "$OUTPUT_DIR"

if [[ "$status" != "ACCEPTED" && "$status" != "OBSERVED" ]]; then
  fail "SUNAT no aceptó el comprobante; revisa status.json y la respuesta fiscal."
fi
