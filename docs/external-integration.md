# Integración de aplicaciones externas con Carabo

## Ruta recomendada

Las aplicaciones que originan un volumen sostenido de comprobantes deben usar la ingestión asíncrona:

```http
POST /carabo/api/documents/ingest
X-Api-Key: <api-key de la empresa>
Idempotency-Key: venta-2026-00018452
Content-Type: application/json
```

El cuerpo es el mismo de `POST /documents/emit`. Carabo ignora cualquier `businessCode` enviado en el JSON y deriva la empresa exclusivamente de la API key.

Respuesta cuando RabbitMQ confirma la publicación:

```http
HTTP/1.1 202 Accepted
Location: /documents/external/venta-2026-00018452
Retry-After: 2

{
  "externalId": "venta-2026-00018452",
  "status": "QUEUED",
  "statusUrl": "/documents/external/venta-2026-00018452",
  "documentId": null
}
```

La aplicación consulta el resultado conservando la misma API key:

```http
GET /carabo/api/documents/external/venta-2026-00018452
X-Api-Key: <api-key de la empresa>
```

También puede configurar un webhook para recibir el resultado. Hasta que exista outbox persistente, la consulta por `externalId` es el mecanismo de conciliación autoritativo.

## Semántica de reintentos

- `Idempotency-Key` es obligatorio en ingestión asíncrona, tiene un máximo de 100 caracteres y debe ser estable por documento de origen.
- Repetir la misma solicitud es seguro. Si el documento ya fue procesado, Carabo devuelve su estado actual sin emitir otro correlativo.
- `202` significa que el broker confirmó el mensaje; todavía no significa que SUNAT/OSE aceptó el comprobante.
- `503 BROKER_UNAVAILABLE` significa que Carabo no pudo confirmar la publicación. El cliente puede reenviar exactamente la misma solicitud y clave.
- `404` durante la consulta puede ser transitorio mientras el consumidor toma el mensaje. Respete `Retry-After` y use backoff con jitter.
- Un `REJECTED` respaldado por CDR es terminal. `ERROR` es técnico y puede ser reintentable. `OBSERVED` conserva validez fiscal pero requiere revisión.

## Capacidad y protección

- La API mantiene un buffer acotado de 1024 publicaciones y devuelve error cuando no puede aceptar más trabajo.
- El consumidor limita los mensajes sin ACK para aplicar backpressure.
- Los mensajes inválidos son rechazados y la cola está preparada para una DLQ; su operación debe monitorear `carabo.document.emit.dlq`.
- Para carga masiva, distribuya las solicitudes en el tiempo y no abra concurrencia ilimitada. El límite final se fijará con pruebas de carga y SLO del piloto.

## Estados de autoridad tributaria

| Estado | Significado | Acción del integrador |
|---|---|---|
| `QUEUED` | Confirmado por RabbitMQ | Consultar luego |
| `VALIDATED` / `PENDING` | En pipeline o esperando autoridad | Consultar con backoff |
| `ACCEPTED` | CDR aceptada | Conciliar y cerrar |
| `OBSERVED` | CDR aceptada con observaciones | Cerrar fiscalmente y revisar observación |
| `REJECTED` | Rechazo fiscal terminal | Corregir y emitir un documento nuevo |
| `ERROR` | Fallo técnico/configuración | No crear otro correlativo; consultar o reintentar el mismo documento |

## Contrato OSE

`OseClient` implementa únicamente el contrato **Carabo OSE Gateway v1**:

- `POST /api/v1/documents`
- `GET /api/v1/documents/{externalId}/status`
- `GET /api/v1/documents/{externalId}/cdr`
- autenticación `Authorization: Bearer ...`
- estados `QUEUED`, `PROCESSING`, `PENDING`, `ACCEPTED`, `OBSERVED` o `REJECTED`

No se presume compatibilidad directa con ningún OSE comercial. Para activar `SendMode.OSE` debe seleccionarse un proveedor, implementar su adaptador, ejecutar su suite contractual sandbox y conservar evidencia del XML, CDR y errores.
La puerta de selección y su Definition of Done están en [`ose-provider-selection.md`](ose-provider-selection.md).

## Pendientes antes de producción

- Prueba E2E con credenciales SUNAT beta y certificado del tenant piloto.
- Adaptador y contrato del OSE seleccionado.
- Outbox persistente para webhooks y eventos.
- Pruebas de RabbitMQ con reentrega, DLQ y caída del broker.
- Pruebas de carga para acordar tasa, latencia, concurrencia y límites por tenant.

## Cierre E2E SUNAT beta

Antes de integrar un OSE, ejecute el ciclo autoritativo completo con el tenant piloto:

```bash
export CARABO_API_KEY='<clave recuperada del secret store del productor>'
CARABO_E2E_PAYLOAD=examples/sunat-beta-invoice.json ./scripts/e2e-sunat.sh
```

El runner asigna una fecha e `externalId` únicos, publica por `/documents/ingest`, espera al worker mediante
`GET /documents/external/{externalId}` y exige un estado `ACCEPTED` u `OBSERVED`. Conserva en
`build/e2e/<externalId>/` la solicitud, respuestas, XML firmado, CDR y hashes SHA-256. Nunca escribe la API key.

Antes de ejecutarlo, el preflight `GET /tenants/{businessCode}/readiness` debe informar `ready: true`.
La pantalla Configuración presenta el mismo diagnóstico sin mostrar el usuario, contraseñas ni contenido del certificado.
