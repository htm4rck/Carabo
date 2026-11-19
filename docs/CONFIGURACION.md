# Configuración · Carabo

Consolidación documental del 2026-11-15. Los comandos se obtuvieron de los manifiestos de esta copia; no se ejecutaron builds ni operaciones de negocio para redactar esta guía. El detalle histórico está en la wiki.

[README](../README.md) · [Mapa documental](https://dev.tordo.io/tecolyt/ui/wiki?path=Gobierno%2FDocumentacion%2FRepositorios%2FCarabo%2FIndice.md)

## Opciones por componente

Las siguientes claves son referencias observadas en código, no una afirmación de obligatoriedad o de valor predeterminado. Los valores reales no se copian.

| Clave | Consumidor observado | Valor / requisito |
|---|---|---|
| `CARABO_BUILD_DATE` | `apps/ui/scripts/generate-build-info.mjs` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `CARABO_COMMIT` | `apps/ui/scripts/generate-build-info.mjs` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `CARABO_GUIDE_E2E_READY_FILE` | `apps/api/carabo-starter-quarkus/src/test/java/io/carabo/app/service/DispatchGuideRealPeerTest.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `CARABO_GUIDE_E2E_STOP_FILE` | `apps/api/carabo-starter-quarkus/src/test/java/io/carabo/app/service/DispatchGuideRealPeerTest.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `CARABO_UI_VERSION` | `apps/ui/scripts/generate-build-info.mjs` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.build.commit` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/rest/VersionResource.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.build.date` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/rest/VersionResource.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.build.version` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/rest/VersionResource.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.certs.base-path` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/BatchService.java`, `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CertificateService.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.layout.base-url` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/client/LayoutClient.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.layout.service-token` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/rest/DispatchGuideResource.java`, `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentPdf.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.mailer.token` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentMail.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.mailer.url` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentMail.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.secrets.base-path` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/security/CredentialCipher.java`, `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/security/SecretResolver.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.security.admin-key` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/security/ApiKeyFilter.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.storage.base-path` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentMail.java`, `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentPdf.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.storage.encryption-key` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentMail.java`, `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/CustomerDocumentPdf.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.sunat.gre.client-id` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/DispatchGuideService.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.sunat.gre.client-secret` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/DispatchGuideService.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.time.fiscal-zone` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/FiscalClock.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |
| `carabo.webhooks.allow-private-addresses` | `apps/api/carabo-starter-quarkus/src/main/java/io/carabo/app/service/NotificationService.java` | Consultar la configuración del consumidor; secreto fuera de Git cuando aplique |

### Fuente: `README.md`

Consultar **Configuración fiscal y responsabilidad de numeración** en la [fuente local conservada](../README.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

### Fuente: `README.md`

Consultar **Variables de Entorno** en la [fuente local conservada](../README.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

## Dependencias externas

Consultar las instrucciones específicas consolidadas abajo y los anexos registrados en README. Si no hay definición suficiente en las fuentes, el requisito queda pendiente de documentar; no se infiere un comportamiento nuevo.

## Acceso, identidad y empresa

Las instrucciones o referencias específicas se conservan a continuación; sus fuentes y limitaciones siguen identificadas.

### Fuente: `docs/architecture/angular/ANGULAR_ADOPTION.md`

#### Sesión y compatibilidad

Se conserva el contrato bearer y la persistencia existente. No se migra a cookies desde la UI sin soporte de su backend. El interceptor delimita credenciales por origen/ruta, impone timeout y acepta 401 sólo para el token/contexto actual. Un cambio de usuario/empresa/entorno invalida observaciones previas. Las lecturas GET/HEAD anteriores se cancelan; una escritura iniciada puede terminar, pero su respuesta de otro contexto se descarta. Esto no revierte una escritura ya confirmada por el servidor; idempotencia y autorización siguen siendo responsabilidades del contrato backend.

La revisión de arquitectura no certifica los criterios de seguridad completos de Columba: almacenamiento de tokens, autorización servidor, cabeceras del gateway, CSRF y pruebas reales multiempresa requieren su aceptación específica. Un rol guardado en el navegador sólo controla experiencia visual.

### Fuente: `docs/company-selection.md`

### Selección de empresa en Carabo

La interfaz consulta `GET /access/businesses` al iniciar sesión y al abrir una sesión guardada. El selector muestra empresas habilitadas y activas en Carabo, con membresía activa del usuario. El nombre visible procede de `setup.business`.

Root no necesita un rol `*_Carabo` adicional en cada membresía. Los demás usuarios requieren `OPERATOR_Carabo`, `SUPERVISOR_Carabo` o `ADMIN_Carabo` en esa empresa. Una empresa registrada en Business pero no habilitada en Carabo no aparece. Tampoco aparecen membresías inactivas.

La consulta requiere un JWT humano válido y obtiene el usuario del token; no acepta un usuario o empresa de destino enviados por el cliente. Puede ejecutarse antes de seleccionar la empresa, sin `business-code`. Las operaciones documentales conservan su autenticación, autorización y alcance por cabecera. Las claves de integración no pueden consultar este catálogo humano.

El selector conserva la empresa activa si sigue disponible. Al cambiar, guarda la elección y recarga los datos con su `business-code`. Si la empresa guardada dejó de estar autorizada, selecciona una disponible y recarga. Una falla de consulta deja el selector deshabilitado y muestra un botón para reintentar; no amplía permisos a partir de los datos guardados en el navegador.

### Fuente: `docs/external-integration.md`

#### Identidad de máquina y licencias

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

### Fuente: `docs/runbooks/carabo-operations.md`

Consultar **Completar Configuración de una empresa** en la [fuente local conservada](runbooks/carabo-operations.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

### Fuente: `docs/runbooks/carabo-operations.md`

Consultar **Almacén seguro de credenciales: instalación y recuperación** en la [fuente local conservada](runbooks/carabo-operations.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

## Persistencia y preparación local

Consultar las instrucciones específicas consolidadas abajo y los anexos registrados en README. Si no hay definición suficiente en las fuentes, el requisito queda pendiente de documentar; no se infiere un comportamiento nuevo.

## Rutas, puertos y prefijos

Consultar las instrucciones específicas consolidadas abajo y los anexos registrados en README. Si no hay definición suficiente en las fuentes, el requisito queda pendiente de documentar; no se infiere un comportamiento nuevo.

## Ambientes y secretos

No versionar `.env`, tokens, certificados ni valores de producción. Los nombres observados no acreditan acceso ni autorización. Usar el mecanismo de secretos y los perfiles ya definidos; los ejemplos preservados son documentales.
