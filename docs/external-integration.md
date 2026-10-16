# Integración de aplicaciones externas con Carabo

## Identidad de máquina y licencias

Una aplicación productora no inicia sesión como una persona y no necesita usuario, correo corporativo ni licencia humana. Cada ERP, ecommerce, POS o backend recibe una identidad de máquina independiente desde **Integraciones**.

- La credencial se entrega una sola vez y se guarda en el secret store del productor.
- Cada productor tiene sus propios scopes, actividad, rotación y revocación.
- No comparta una API key entre aplicaciones ni almacene un JWT humano en un servicio backend.
- Revocar o rotar un productor no invalida las sesiones humanas ni las demás integraciones de la empresa.

Scopes disponibles:

| Scope | Uso |
|---|---|
| `DOCUMENT_INGEST` | Publicar por `POST /documents/ingest` |
| `DOCUMENT_READ` | Consultar estado, XML, CDR y PDF del mismo tenant |
| `DOCUMENT_EMIT` | Emisión síncrona; reservar para compatibilidad controlada |

Un Supervisor puede consultar estado y actividad. Sólo Admin/Root puede crear, rotar, activar o revocar credenciales.

## Ruta recomendada

Las aplicaciones que originan un volumen sostenido de comprobantes deben usar la ingestión asíncrona:

```http
POST /carabo/api/documents/ingest
X-Api-Key: <api-key de la empresa>
Idempotency-Key: venta-2026-00018452
Content-Type: application/json
```

El cuerpo es el mismo de `POST /documents/emit`. Carabo ignora cualquier `businessCode` enviado en el JSON y deriva la empresa exclusivamente de la API key.

Ejemplo completo de factura gravada (la fecha debe corresponder al calendario fiscal de Lima):

```json
{
  "docType": "01",
  "series": "F001",
  "issueDate": "2026-10-16",
  "currency": "PEN",
  "externalId": "erp-01-20261016-00018452",
  "sourceDocId": "VENTA-00018452",
  "receiver": {
    "docType": "6",
    "doc": "20131312955",
    "name": "CLIENTE EMPRESA SAC",
    "address": "Lima, Perú"
  },
  "lines": [
    {
      "productCode": "SERV-001",
      "productName": "Servicio mensual de ejemplo",
      "unitCode": "NIU",
      "quantity": 1,
      "unitPrice": 100.00,
      "discount": 0,
      "affectationCode": "10"
    }
  ]
}
```

El header `Idempotency-Key` y `externalId` deben contener exactamente el mismo valor. El emisor, canal SUNAT/OSE, base imponible, IGV y total se derivan o calculan en Carabo; el productor no debe duplicarlos.

Antes de publicar puede comparar el JSON sin emitir ni reservar correlativo:

```http
POST /carabo/api/documents/validate
X-Api-Key: <api-key del productor>
Idempotency-Key: erp-01-20261016-00018452
Content-Type: application/json
```

La respuesta incluye `valid` y una lista `issues`; cada hallazgo contiene `path`, `severity`, valor `received`, valor `expected` y `suggestion`. La pantalla **Integraciones → Comparar documento** consume este mismo endpoint y presenta el documento del productor junto a un ejemplo correcto.

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

También puede configurar un webhook para recibir el resultado. El outbox conserva y reintenta las entregas; la consulta por `externalId` continúa siendo el mecanismo de conciliación autoritativo.

## RabbitMQ directo

La API HTTP es la única entrada soportada actualmente para productores externos. Después de autenticar, validar idempotencia y atribuir el productor, Carabo publica internamente en RabbitMQ y sólo entonces responde `202`.

La API key de Carabo **no autentica AMQP**. El broker interno usa `carabo.exchange` (direct), routing key `document.emit` y cola consumidora `carabo.document.emit`, pero no debe exponerse con el usuario compartido de plataforma. Habilitar acceso directo requiere previamente:

- identidad AMQP dedicada por productor;
- TLS y endpoint privado;
- vhost por entorno;
- permiso exclusivo de publicación sobre el exchange/routing key;
- cuotas, confirmación de publisher, trazabilidad y revocación;
- prueba de reentrega, DLQ y aislamiento entre productores.

No se necesita una librería propietaria: el futuro contrato directo será AMQP 0-9-1 y podrá usar RabbitMQ Java Client, `amqplib`, Pika u otro cliente oficial. Hasta completar el aprovisionamiento seguro, las aplicaciones externas deben usar `/documents/ingest`.

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
