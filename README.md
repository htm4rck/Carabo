# 🦉 Carabo

> Ave fiscalizadora — Sistema de facturación electrónica multitenant para Latinoamérica.

Carabo es un servicio standalone de facturación electrónica que genera, firma, envía y almacena comprobantes electrónicos ante las autoridades tributarias. Diseñado como sistema multitenant, permite que múltiples empresas operen con sus propios certificados, series y configuraciones de envío.

## Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| Runtime | Java 21 |
| Framework | Quarkus 3.18 |
| Build | Gradle 8.14 |
| Base de datos | PostgreSQL (schema `carabo`) |
| Mensajería | RabbitMQ (SmallRye Reactive Messaging) |
| Frontend | Angular 21 (standalone components) |
| Contenedores | Docker multi-stage |
| API Docs | OpenAPI 3 / Swagger UI |

## Arquitectura

```
┌─────────────────┐     RabbitMQ      ┌──────────────────┐
│  Tordo/Operation │ ──────────────▶  │  Carabo API      │
│  (aprueba venta) │  document.emit   │  (port 8998)     │
└─────────────────┘                   └────────┬─────────┘
                                               │
                          ┌────────────────────┼────────────────────┐
                          ▼                    ▼                    ▼
                   ┌─────────────┐    ┌──────────────┐    ┌──────────────┐
                   │ SUNAT Direct│    │  SUNAT REST  │    │     OSE      │
                   │ (SOAP/REST) │    │  (GRE 2.0)  │    │  Adapter     │
                   └─────────────┘    └──────────────┘    └──────────────┘
                                               │
                                               ▼
                                      ┌──────────────┐
                                      │ Layout Svc   │
                                      │ (PDF render) │
                                      └──────────────┘
```

**Flujo principal:**
1. Tordo/Operation aprueba una venta → publica a `carabo.document.emit` vía RabbitMQ
2. Carabo consume el mensaje, construye XML UBL 2.1, valida estructura
3. Firma digitalmente con certificado PKCS12 del tenant (.pfx/.p12)
4. Envía a la autoridad tributaria según `SendMode` del tenant (SUNAT_DIRECT, SUNAT_REST, OSE)
5. Recibe CDR (Constancia de Recepción), almacena resultado
6. Notifica a Tordo vía callback/webhook con el resultado
7. Layout service genera PDF representación impresa bajo demanda

## Estructura del Proyecto

```
Carabo/
├── apps/
│   ├── api/                              # Backend Quarkus
│   │   ├── carabo-bom/                   # BOM — gestión centralizada de versiones
│   │   ├── carabo-core/                  # Dominio: entidades, enums, DTOs, UBL builder, validación
│   │   │   └── country/{pe,cl,co,mx}/    # Estrategias por país (extensible)
│   │   ├── carabo-signer/                # Firma digital XML (XMLDSig, SHA-256, enveloped)
│   │   ├── carabo-sunat-client/          # Cliente SOAP BillService + REST GRE 2.0
│   │   ├── carabo-ose-client/            # Contrato HTTP Carabo OSE Gateway v1
│   │   ├── carabo-storage/               # Almacenamiento filesystem (XML firmado, CDR, PDF)
│   │   ├── carabo-scheduler/             # Jobs programados (reintentos, resumen diario)
│   │   └── carabo-starter-quarkus/       # Aplicación Quarkus
│   │       ├── rest/                     # Endpoints REST (Documents, Tenants, GRE, Reports, Health)
│   │       ├── consumer/                 # RabbitMQ consumer (document.emit)
│   │       ├── service/                  # Lógica de negocio
│   │       ├── client/                   # Cliente HTTP a Layout service
│   │       ├── scheduler/                # Scheduler de reintentos automáticos
│   │       └── security/                 # API Key filter por tenant
│   └── ui/                               # Frontend Angular 21
├── migrations/                           # SQL migrations (001-006)
├── templates/                            # Plantillas Handlebars para PDF
├── infra/
│   └── docker-compose.yml                # Despliegue containerizado
└── README.md
```

## Módulos del Backend

### carabo-core
Dominio puro sin dependencias de framework. Contiene:
- **Entidades JPA**: `Tenant`, `Document`, `DocumentLine`, `DocumentTax`, `DocumentSendLog`, `DispatchGuide`, `DispatchGuideItem`
- **Enums**: `DocumentType` (01-09, RA, RC), `DocumentStatus`, `SendMode`
- **DTOs**: `EmitDocumentRequest`, `EmitDocumentResponse`, `DocumentCallback`
- **UBL Builder**: Genera XML UBL 2.1 conforme a estándares SUNAT
- **Validador**: Reglas de negocio pre-emisión
- **Country Strategy**: Patrón Strategy para extensión a Chile, Colombia, México

### carabo-signer
Firma digital de documentos XML:
- Carga certificados PKCS12 (.pfx/.p12)
- Firma enveloped XMLDSig con RSA-SHA256
- Inserta firma en `ext:UBLExtensions/ext:ExtensionContent`
- Retorna XML firmado + digest value (hash para SUNAT)

### carabo-sunat-client
Comunicación directa con SUNAT:
- **BillService** (SOAP): `sendBill`, `getStatus` para facturas/boletas/NC/ND
- **GRE REST API**: Autenticación OAuth2, envío de guías de remisión electrónicas v2.0
- Manejo de tickets asincrónicos para documentos batch

### carabo-ose-client
Cliente HTTP para Operadores de Servicios Electrónicos:
- Implementa el contrato explícito Carabo OSE Gateway v1; no presume compatibilidad con proveedores comerciales
- Envío de XML firmado en Base64
- Consulta de estado y descarga de CDR
- Requiere un adaptador contractual y pruebas sandbox para el OSE seleccionado

### carabo-storage
Almacenamiento organizado por tenant:
```
/app/storage/documents/{businessCode}/{series}/{number}/
├── signed.xml      # XML firmado
├── cdr.xml         # Constancia de Recepción
└── document.pdf    # Representación impresa
```

### carabo-scheduler
Jobs automáticos:
- **Reintentos**: Documentos en estado `PENDING`/`ERROR` se reintentan hasta 3 veces
- **Resumen diario**: Generación automática de RC (boletas) al cierre del día

### carabo-starter-quarkus
Aplicación ejecutable que integra todos los módulos:
- REST API con API key por tenant y credencial administrativa separada
- Consumer RabbitMQ para emisión asincrónica
- Integración con Layout service para generación de PDF
- Swagger UI en `/swagger`
- Health check en `/health`

## API Endpoints

### Documentos
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/documents/emit` | Emitir documento (factura, boleta, NC, ND) |
| POST | `/api/documents/ingest` | Encolar emisión asíncrona para aplicaciones externas |
| GET | `/api/documents/external/{externalId}` | Consultar resultado por identificador externo |
| GET | `/api/documents/{id}` | Obtener documento por ID |
| GET | `/api/documents` | Listar documentos (paginado, filtros) |
| GET | `/api/documents/{id}/xml` | Descargar XML firmado |
| GET | `/api/documents/{id}/cdr` | Descargar CDR |
| GET | `/api/documents/{id}/pdf` | Generar/descargar PDF |
| POST | `/api/documents/{id}/retry` | Reintentar envío |
| POST | `/api/documents/void` | Comunicación de baja |

### Guías de Remisión (GRE 2.0)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/dispatch-guides/emit` | Emitir guía de remisión |
| GET | `/api/dispatch-guides/{id}` | Obtener guía por ID |
| GET | `/api/dispatch-guides` | Listar guías |

### Tenants
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/tenants` | Crear tenant (empresa) |
| GET | `/api/tenants` | Listar tenants |
| PUT | `/api/tenants/{id}` | Actualizar configuración |
| POST | `/api/tenants/{id}/regenerate-key` | Regenerar API Key |
| GET | `/api/tenants/{businessCode}/readiness` | Preflight fiscal seguro para SUNAT |

### Reportes
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/reports/summary` | Resumen por período |
| GET | `/api/reports/export/excel` | Exportar a Excel |

### Sistema
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/health` | Health check |
| GET | `/swagger` | Swagger UI |

## Documentos Soportados

| Código | Tipo | Envío | Estado |
|--------|------|-------|--------|
| 01 | Factura electrónica | SOAP / OSE | Implementado; homologación pendiente |
| 03 | Boleta electrónica | SOAP / OSE | Implementado; homologación pendiente |
| 07 | Nota de crédito electrónica | SOAP / OSE | Implementado; homologación pendiente |
| 08 | Nota de débito electrónica | SOAP / OSE | Implementado; homologación pendiente |
| 09 | Guía de remisión electrónica | REST GRE 2.0 | Experimental; mantener desactivado |
| RA | Comunicación de baja | SOAP | Implementado; prueba E2E pendiente |
| RC | Resumen diario de boletas | SOAP | Implementado; prueba E2E pendiente |

## Modos de Envío

| Modo | Descripción | Uso |
|------|-------------|-----|
| `SUNAT_DIRECT` | SOAP directo a SUNAT (BillService) | Facturas, NC, ND, boletas |
| `SUNAT_REST` | REST API SUNAT (GRE 2.0) | Guías de remisión |
| `OSE` | Operador de Servicios Electrónicos | Cualquier documento vía tercero |

## Multitenancy

Cada tenant (empresa) se configura independientemente:
- **Certificado digital**: Archivo .pfx/.p12 propio con contraseña
- **Credenciales SUNAT**: SOL user/password o API token OSE
- **Series**: Configuración de series por tipo de documento (F001, B001, FC01, etc.)
- **Modo de envío**: SUNAT directo u OSE por tenant
- **Webhook**: URL de notificación con firma HMAC-SHA256
- **Límites**: Máximo de documentos por día
- **Ambiente**: BETA, HOMOLOGATION, PRODUCTION

## Correlativos e idempotencia

- Los correlativos se reservan atómicamente por tenant, clase de documento y serie; no se calculan con `MAX + 1`.
- `POST /api/documents/emit` y `POST /api/dispatch-guides/emit` aceptan el header `Idempotency-Key` (máximo 100 caracteres).
- `POST /api/documents/ingest` exige `Idempotency-Key`, confirma la publicación en RabbitMQ y responde `202`; es la ruta recomendada para alto volumen.
- `POST /api/documents/validate` compara el JSON sin emitir ni reservar correlativo y devuelve hallazgos por ruta, valor recibido, esperado y corrección sugerida.
- Repetir simultáneamente una solicitud con la misma clave retorna el documento existente y no vuelve a enviarlo a SUNAT/OSE.
- `externalId` conserva compatibilidad como clave idempotente. En documentos también puede usarse `sourceDocId`.
- Si se envían `Idempotency-Key` y `externalId`, ambos deben coincidir.
- Contrato, códigos HTTP y ejemplos: [`docs/external-integration.md`](docs/external-integration.md).
- Cada aplicación productora usa una identidad de máquina propia; no requiere usuario, contraseña ni licencia humana. La pantalla **Integraciones** permite observarla y, para Admin/Root, crearla, rotarla o revocarla.
- RabbitMQ directo no está expuesto a productores externos: una API key no autentica AMQP. La entrada soportada es HTTP y Carabo realiza la publicación interna; abrir AMQP exige usuario dedicado, TLS, vhost y ACL por productor.

## Seguridad

- **API Key per tenant**: Cada empresa tiene su propia API Key (64 chars)
- **Aislamiento REST**: El tenant se deriva de la API key; un `businessCode` enviado por el cliente no cambia el ámbito
- **Administración separada**: `/tenants` exige `X-Admin-Key`; las API keys de tenant no administran empresas
- **Respuestas seguras**: Credenciales SOL/OSE, contraseñas de certificados y secretos de webhook no se serializan
- **API keys no reversibles**: PostgreSQL conserva únicamente la huella SHA-256; la credencial se muestra una vez al crearla o rotarla
- **Secret store montado**: Los campos sensibles aceptan referencias `secret://nombre`, resueltas desde un volumen de solo lectura
- **Certificados aislados**: Montados como volumen read-only
- **Webhook signing**: HMAC-SHA256 para verificar autenticidad de callbacks
- **Schema isolation**: Todos los datos en schema `carabo` separado
- **Custodia verificable**: AES-256-GCM, checksum SHA-256 y escrituras atómicas para XML/CDR/PDF
- **Backup/restore**: Procedimiento DEV con manifiesto de integridad en [`docs/storage-operations.md`](docs/storage-operations.md)

## Extensibilidad por País

El patrón `CountryStrategy` permite extender a otras autoridades tributarias:

```
carabo-core/country/
├── CountryStrategy.java          # Interface
├── CountryStrategyFactory.java   # Factory por country_code
├── pe/                           # Perú (SUNAT) — implementado
├── cl/                           # Chile (SII) — preparado
├── co/                           # Colombia (DIAN) — preparado
└── mx/                           # México (SAT/CFDI) — preparado
```

## Requisitos

- Java 21+
- Node.js 22+
- Docker & Docker Compose
- Red `tordo_network` (PostgreSQL, RabbitMQ, Kong)

## Desarrollo Local

### Backend (hot-reload)
```bash
cd apps/api
./gradlew :carabo-starter-quarkus:quarkusDev
# API disponible en http://localhost:8998
# Swagger UI en http://localhost:8998/swagger
```

### Frontend
```bash
cd apps/ui
npm install && npm start
# UI disponible en http://localhost:4200
```

### Docker (producción)
```bash
cd infra
docker compose up -d
# API: http://localhost:8998
# UI: http://localhost/carabo/ui/
```

## Variables de Entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tordo?currentSchema=carabo` | JDBC URL |
| `DB_USERNAME` | `postgres` | Usuario DB |
| `DB_PASSWORD` | `yourpassword` | Password DB |
| `RABBITMQ_HOST` | `localhost` | Host RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto RabbitMQ |
| `RABBITMQ_USERNAME` | `tordo` | Usuario RabbitMQ |
| `RABBITMQ_PASSWORD` | `tordo123` | Password RabbitMQ |
| `CERTS_BASE_PATH` | `/app/certs` | Directorio de certificados |
| `STORAGE_PATH` | `/app/storage/documents` | Directorio de almacenamiento |
| `LAYOUT_BASE_URL` | `http://localhost:47617/layout` | URL del servicio Layout (PDF) |
| `CARABO_ADMIN_KEY` | — | Credencial obligatoria enviada como `X-Admin-Key` para administrar tenants |
| `CARABO_SECRETS_PATH` | `/run/secrets/carabo` | Directorio de secretos montados referenciados mediante `secret://nombre` |
| `SUNAT_GRE_CLIENT_ID` | — | Client ID para GRE REST API |
| `SUNAT_GRE_CLIENT_SECRET` | — | Client Secret para GRE REST API |

## Migraciones

Flyway ejecuta automáticamente las migraciones V1–V5 del schema `carabo` al iniciar la API. En instalaciones existentes crea un baseline en V2. `002-seed-layout-templates.sql` se mantiene separado porque modifica el schema de Tordo/Layout y debe aplicarse desde el despliegue de plataforma.

## Integración con el Ecosistema Tordo

- **Tordo/Operation** → Publica `document.emit` cuando se aprueba una venta
- **Carabo** → Consume, procesa, notifica resultado vía callback/webhook
- **Tordo/Layout** → Renderiza PDF con plantillas Handlebars diseñadas en Aquila
- **Aquila** → Panel de administración (configuración de tenants, monitoreo)
- **BirdPort** → Ecommerce que emite facturas/boletas a través de Operation → Carabo

## Licencia

Propietario — Tordo Platform.
