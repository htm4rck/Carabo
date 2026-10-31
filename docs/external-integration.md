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
| `DOCUMENT_EMIT` | Emisión síncrona de comprobantes y GRE; reservar para integraciones que lo necesiten |

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
  "number": 18452,
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

## Series y correlativos: Tordo, SAP y otros ERP

Revisión de código y documentación de referencia: **31 de octubre de 2026**. La regla de integración es que cada serie fiscal tenga un único sistema que asigne sus correlativos. Un reintento conserva el documento y su numeración.

### Comportamiento implementado

| Documento | Configuración y asignación | Papel de Carabo |
|---|---|---|
| Factura y boleta | Aquila administra `business.document_series`; Business/Operation asigna la serie y el número | Recibe `series` y `number`, valida, firma, envía y conserva el resultado |
| Nota de crédito/débito | El productor debe enviar serie y correlativo; la corrección fiscal de Tordo también usa Business/Operation | Conserva la identidad enviada y valida su relación con el comprobante original |
| GRE remitente | El productor envía la serie; actualmente Carabo asigna el correlativo mediante `CorrelativeService` | Conserva la solicitud sin número hasta preparar el envío y mantiene un contador propio por empresa y serie |

`DocumentService.buildDocument` prioriza la serie recibida y conserva `request.number`. `DocumentValidator` rechaza un correlativo ausente o fuera de 1–99999999. Carabo **no genera otro correlativo de factura, boleta o nota**. El contador histórico de tipo `DOCUMENT` en la tabla de secuencias no significa que este flujo lo utilice.

Los cuatro campos de series en Configuración son **valores predeterminados de compatibilidad**: sólo se usan cuando el productor omite `series`. No contienen rangos, sucursal, caja ni siguiente número. Con Tordo, el productor envía siempre ambos datos y prevalece la configuración de Aquila. El preflight actual todavía exige valores de respaldo de factura y boleta, aunque el productor envíe su propia serie; esa exigencia de interfaz no convierte a Carabo en el maestro de numeración.

### Justificación y comparación con otros sistemas

SAP Business One administra numeración por tipo documental y configura series digitales para documentos electrónicos. SAP S/4HANA dispone además de configuración de numeración oficial para Perú, que debe distinguirse del identificador interno del documento. Su documentación demuestra que integrar un facturador no obliga a cederle la numeración fiscal. La localización y el mapeo concreto deben verificarse en cada instalación SAP; `DocEntry` o `DocNum` no deben convertirse automáticamente en el número fiscal.

Como ejemplo de API fiscal, el contrato público de APIs Peru recibe `document_type_code`, `series`, `number` y una referencia externa tanto para comprobantes como para guías. Es un ejemplo verificable de conservación de numeración del productor, no una afirmación de que todos los proveedores operen igual.

La conclusión arquitectónica para Carabo es mantener la numeración del ERP cuando ya existe. Un contador fiscal propio se justifica cuando el ERP **delega expresamente** a Carabo la asignación de una serie exclusiva. Hace falta acordar qué sistema numera, desde qué correlativo continúa y cómo devuelve la identidad al ERP. Duplicar contadores de una misma serie en Aquila/SAP y Carabo produciría riesgo de colisiones.

Fuentes primarias:

- [SAP Business One: numeración de documentos](https://learning.sap.com/courses/implementing-sap-business-one-es/implementacion-de-la-numeracion-de-documentos-y-datos-maestros).
- [SAP Business One: configuración de series digitales, p. 5](https://help.sap.com/doc/d807de8d28ec4b6f864f77524a56abe8/10.0/en-US/How_to_Work_with_Electronic_Documents_in_SAP_Business_One.pdf).
- [SAP S/4HANA: numeración oficial en Perú](https://help.sap.com/docs/SAP_S4HANA_CLOUD/7231e70d51c24a889e97176023e37a22/3c2ca6269eea4a9c942f9cef8a6a2103.html).
- [APIs Peru: contratos de emisión](https://api.apisperu.pro/docs).

### Integración SAP propuesta y límites actuales

Para un comprobante fiscal ya numerado por SAP, el adaptador debe enviar esa serie y ese número, un `sourceDocId` que identifique inequívocamente el documento SAP y un `externalId` estable. Por ejemplo, los campos de identidad de una solicitud podrían ser:

```json
{
  "docType": "01",
  "series": "F023",
  "number": 456,
  "sourceDocId": "sap:PE01:invoice:5482",
  "externalId": "sap:PE01:invoice:5482:original"
}
```

Este fragmento ilustra el mapeo; no reemplaza el contrato completo con receptor, fecha y líneas. La factura fiscal `F023-00000456` debe mantenerse en ERP, XML, representación y estado de Carabo. Las claves internas SAP permanecen como referencias de origen. Una repetición técnica conserva estas mismas identidades; una corrección fiscal requiere su documento correspondiente.

**Pendientes de implementación, no funcionalidades disponibles:**

1. Expresar la autoridad de numeración por empresa, productor y tipo documental: ERP o Carabo delegado. El nombre `numberingAuthority` es una propuesta, no un campo admitido actualmente por la API.
2. En integración con ERP, exigir también `series` explícita y retirar el respaldo silencioso. La pantalla debería identificar la procedencia Aquila/SAP y mover los respaldos a configuración avanzada.
3. Aceptar una GRE ya numerada por SAP exige ampliar `EmitDispatchGuideRequest`: actualmente no tiene `number`. Agregar ese atributo al JSON no implementa la conservación del número externo. No conectar una serie GRE que SAP ya numere al contador interno de Carabo.
4. Verificar una clave fiscal por RUC emisor, tipo documental, serie y correlativo, con separación de ambientes. Hoy la restricción del CPE es `(business_code,series,number)` y el almacenamiento usa empresa/serie/número. Por tanto, no se debe asumir que ya se admite reutilizar la misma serie y número entre tipos distintos; hacen falta cambios compatibles en restricciones y rutas documentales antes de ofrecerlo a un ERP.
5. Un valor único de respaldo para notas de crédito/débito no representa las dos familias F/B. Para notas de boleta, el productor debe enviar la serie B adecuada; `FC01`/`FD01` no sustituyen esa decisión.

SUNAT diferencia las familias F/B y las notas asociadas a factura o boleta. La regulación de 2026 mantiene esa distinción para las notas de crédito. La selección de series debe respetarla; estos ejemplos no constituyen una autorización de serie para un emisor específico. [SUNAT: operatividad](https://orientacion.sunat.gob.pe/3529-operatividad), [anexo de notas de crédito 2026](https://www.sunat.gob.pe/legislacion/superin/2026/anexo-000048-2026.pdf).

Validaciones necesarias antes de declarar compatible un adaptador SAP: conservación exacta de serie/número; rechazo de numeración incompleta; reintentos sin nuevo documento; concurrencia sin duplicación; notas de ambas familias; migración desde el último número emitido; aislamiento de empresa y ambiente; y restitución del resultado fiscal al documento SAP correcto. Esta revisión documenta el contrato; no acredita un conector SAP homologado.

## Guías de remisión electrónica (GRE 2.0)

Las GRE usan un contrato separado que guarda una solicitud durable y procesa el envío en segundo plano. `DOCUMENT_EMIT` permite emitir; `DOCUMENT_READ` permite validar el cuerpo y consultar estado, XML, CDR y PDF. El emisor y la empresa se derivan de la API key:

```http
POST /carabo/api/dispatch-guides/emit
X-Api-Key: <api-key del productor>
Idempotency-Key: erp-09-20261016-00018452
Content-Type: application/json
```

Ejemplo completo de guía remitente con transporte privado:

```json
{
  "series": "T001",
  "issueDate": "2026-10-16",
  "startDate": "2026-10-16",
  "receiver": {
    "docType": "6",
    "doc": "20131312955",
    "name": "CLIENTE EMPRESA SAC"
  },
  "transportMode": "02",
  "grossWeight": 120.5,
  "weightUnit": "KGM",
  "packageCount": 4,
  "origin": {
    "ubigeo": "150101",
    "address": "Av. Industrial 123, Lima"
  },
  "destination": {
    "ubigeo": "040101",
    "address": "Av. Logística 456, Arequipa"
  },
  "driver": {
    "docType": "1",
    "doc": "12345678",
    "name": "ANA TORRES",
    "license": "Q12345678"
  },
  "vehiclePlate": "ABC123",
  "transferReasonCode": "01",
  "transferReasonDesc": "Venta",
  "relatedDoc": {
    "docType": "01",
    "number": "F001-18452"
  },
  "items": [
    {
      "productCode": "PROD-001",
      "productName": "Producto de prueba",
      "unitCode": "NIU",
      "quantity": 4,
      "weight": 120.5
    }
  ],
  "callbackUrl": "https://erp.example.com/webhooks/carabo",
  "externalId": "erp-09-20261016-00018452"
}
```

Para transporte público use `transportMode: "01"` y reemplace `driver`/`vehiclePlate` por
`"carrier": {"ruc": "20123456789", "name": "TRANSPORTES EJEMPLO SAC"}`.
El mismo cuerpo puede comprobarse antes de emitir mediante `POST /carabo/api/dispatch-guides/validate` con `DOCUMENT_READ`.
La respuesta usa el mismo formato `valid/issues` del comparador de comprobantes y marca rutas como
`$.origin.ubigeo`, `$.driver.license` o `$.items[0].quantity`.

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
- La GRE responde con `201` y el identificador de la solicitud persistida; se consulta con `GET /dispatch-guides/{id}`. Esa respuesta no implica que tenga número o CDR aceptada. No pasa por la cola de comprobantes.
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
