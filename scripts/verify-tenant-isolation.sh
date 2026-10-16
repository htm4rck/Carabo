#!/usr/bin/env bash
set -Eeuo pipefail

readonly BASE_URL="${CARABO_BASE_URL:-http://127.0.0.1:8998}"
: "${CARABO_TENANT_A_KEY:?Define CARABO_TENANT_A_KEY}"
: "${CARABO_TENANT_B_KEY:?Define CARABO_TENANT_B_KEY}"
: "${CARABO_DOCUMENT_A_ID:?Define CARABO_DOCUMENT_A_ID}"
: "${CARABO_DOCUMENT_B_ID:?Define CARABO_DOCUMENT_B_ID}"
: "${CARABO_EXTERNAL_A_ID:?Define CARABO_EXTERNAL_A_ID}"
: "${CARABO_EXTERNAL_B_ID:?Define CARABO_EXTERNAL_B_ID}"

request_code() {
  local key="$1" method="$2" path="$3"
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
    --request "$method" --header "X-Api-Key: $key" "$BASE_URL$path"
}

expect_code() {
  local expected="$1" actual="$2" description="$3"
  [[ "$actual" == "$expected" ]] || {
    printf 'FAIL: %s; esperado=%s recibido=%s\n' "$description" "$expected" "$actual" >&2
    exit 1
  }
  printf 'PASS: %s\n' "$description"
}

own_a="$(curl --silent --show-error --fail --header "X-Api-Key: $CARABO_TENANT_A_KEY" \
  "$BASE_URL/documents/external/$CARABO_EXTERNAL_A_ID")"
own_b="$(curl --silent --show-error --fail --header "X-Api-Key: $CARABO_TENANT_B_KEY" \
  "$BASE_URL/documents/external/$CARABO_EXTERNAL_B_ID")"
[[ "$(jq -r '.id' <<<"$own_a")" == "$CARABO_DOCUMENT_A_ID" ]]
[[ "$(jq -r '.id' <<<"$own_b")" == "$CARABO_DOCUMENT_B_ID" ]]

expect_code 404 "$(request_code "$CARABO_TENANT_A_KEY" GET "/documents/$CARABO_DOCUMENT_B_ID")" \
  'tenant A no consulta detalle de tenant B'
expect_code 404 "$(request_code "$CARABO_TENANT_B_KEY" GET "/documents/$CARABO_DOCUMENT_A_ID")" \
  'tenant B no consulta detalle de tenant A'
expect_code 404 "$(request_code "$CARABO_TENANT_A_KEY" GET "/documents/external/$CARABO_EXTERNAL_B_ID")" \
  'externalId queda aislado por tenant'
expect_code 404 "$(request_code "$CARABO_TENANT_A_KEY" GET "/documents/$CARABO_DOCUMENT_B_ID/xml")" \
  'XML de otro tenant no se descarga'
expect_code 404 "$(request_code "$CARABO_TENANT_A_KEY" GET "/documents/$CARABO_DOCUMENT_B_ID/cdr")" \
  'CDR de otro tenant no se descarga'
expect_code 404 "$(request_code "$CARABO_TENANT_A_KEY" POST "/documents/$CARABO_DOCUMENT_B_ID/retry")" \
  'documento de otro tenant no se reintenta'
expect_code 200 "$(request_code "$CARABO_TENANT_A_KEY" GET "/documents/$CARABO_DOCUMENT_A_ID/xml")" \
  'tenant A descarga su XML'
expect_code 200 "$(request_code "$CARABO_TENANT_A_KEY" GET "/documents/$CARABO_DOCUMENT_A_ID/cdr")" \
  'tenant A descarga su CDR'

list_a="$(curl --silent --show-error --fail --header "X-Api-Key: $CARABO_TENANT_A_KEY" "$BASE_URL/documents")"
list_b="$(curl --silent --show-error --fail --header "X-Api-Key: $CARABO_TENANT_B_KEY" "$BASE_URL/documents")"
jq -e --arg forbidden "$CARABO_EXTERNAL_B_ID" 'all(.items[]; .externalId != $forbidden)' <<<"$list_a" >/dev/null
jq -e --arg forbidden "$CARABO_EXTERNAL_A_ID" 'all(.items[]; .externalId != $forbidden)' <<<"$list_b" >/dev/null
printf 'PASS: listados no mezclan tenants\n'
