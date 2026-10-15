# TORDO-APP-005 — Carabo Facturación Electrónica

## Objetivo

Poner en producción un servicio multitenant de facturación electrónica para Perú que genere, firme, envíe, consulte y conserve comprobantes electrónicos, con integración confiable con Tordo/Operation, SUNAT u OSE y Tordo/Layout.

## Diagnóstico ejecutivo

El repositorio contiene una base funcional amplia: backend Quarkus modular, frontend Angular, modelo de datos, generación UBL, firma XML, clientes SUNAT/OSE, GRE, procesamiento por RabbitMQ, almacenamiento, reportes y despliegue con Docker. Sin embargo, todavía no debe considerarse listo para producción.

Hallazgos principales:

- No existen pruebas automatizadas de backend ni frontend.
- La compilación backend fue validada con Java 21 y Gradle 8.14; el build finaliza correctamente, aunque todavía no contiene pruebas automatizadas que demuestren el comportamiento fiscal e integraciones.
- El filtro de API key autentica un tenant, pero los recursos aceptan `businessCode` e identificadores sin comprobar que pertenezcan al tenant autenticado. Esto permite acceso cruzado entre tenants.
- Los endpoints de tenants retornan la entidad completa, que contiene credenciales SOL, contraseña de certificado, token OSE, secreto de webhook y API key.
- La numeración usa `MAX(number) + 1`; bajo concurrencia puede producir correlativos duplicados.
- El consumidor RabbitMQ captura la excepción y termina normalmente, sin estrategia explícita de ACK/NACK, reintentos ni cola de mensajes muertos.
- Los callbacks son asíncronos pero no persistentes; no tienen política verificable de reintentos, timeout o auditoría de entrega.
- Las migraciones son scripts manuales y no están integradas con Flyway/Liquibase ni verificadas automáticamente al desplegar.
- El README y las rutas/configuración no están totalmente alineados: documentación `/api/...`, recursos backend `/documents`, frontend productivo `/api` y Nginx `/carabo/api/`.
- El README declara documentos en “Producción”, pero no hay evidencia automatizada de homologación SUNAT, validación XSD/schematron, pruebas con certificados ni pruebas end-to-end.
- Chile, Colombia y México están registrados como soportados, aunque sus estrategias declaran estado `STUB`.
- No se observan métricas de negocio, trazas, alertas, respaldo probado ni procedimiento de recuperación.

## Alcance recomendado para la primera salida

Incluido:

- Perú/SUNAT.
- Factura, boleta, nota de crédito y nota de débito.
- SUNAT directo como camino principal; OSE se habilita solo después de pruebas contractuales con un proveedor concreto.
- Emisión REST y asíncrona por RabbitMQ.
- XML firmado, CDR, representación PDF, consulta y reintento controlado.
- Aislamiento multitenant, observabilidad, respaldo y operación documentada.

Fuera de la primera salida:

- Chile, Colombia y México.
- Activación productiva de GRE hasta completar el ciclo de ticket/consulta y homologación.
- Soporte genérico para múltiples OSE sin adaptadores y pruebas por proveedor.

## Tabla de ejecución

Convenciones: `P0` bloquea cualquier salida; `P1` bloquea producción; `P2` completa la operación; `P3` es evolución. Las estimaciones son jornadas-persona y deben recalibrarse al asignar responsables.

| ID | Fase | Prioridad | Trabajo | Entregable / resultado | Dependencias | Responsable sugerido | Estimación | Criterio de cierre | Estado inicial |
|---|---|---:|---|---|---|---|---:|---|---|
| CAR-001 | 0. Baseline | P0 | Preparar toolchain reproducible: Java 21, wrapper ejecutable, Node 22, npm lockfile y comandos únicos de build/test | Build local y CI reproducibles | Ninguna | DevOps + Backend | 1–2 d | Backend y frontend compilan desde cero en CI | Pendiente |
| CAR-002 | 0. Baseline | P0 | Crear pipeline CI con compilación, pruebas, análisis estático, escaneo de dependencias e imágenes | Workflow con controles obligatorios | CAR-001 | DevOps | 2–3 d | Un PR no puede integrarse con controles fallidos | Pendiente |
| CAR-003 | 0. Baseline | P0 | Definir contrato OpenAPI real, prefijo de rutas y estrategia de versionado | OpenAPI versionado y rutas coherentes en API, UI, Kong, Nginx y README | CAR-001 | Arquitectura + Backend | 2 d | Prueba smoke confirma todas las rutas publicadas | Pendiente |
| CAR-004 | 0. Baseline | P1 | Integrar migraciones con Flyway o Liquibase | Migraciones automáticas, idempotentes y auditables | CAR-001 | Backend + DBA | 2–3 d | Base vacía y actualización desde versión anterior pasan en CI | Pendiente |
| CAR-005 | 1. Seguridad | P0 | Aplicar autorización por tenant en todos los recursos y servicios | Tenant derivado de la API key; consultas por ID y listados siempre filtrados | CAR-003 | Backend + Seguridad | 3–5 d | Pruebas demuestran que una API key nunca accede a otro tenant | Pendiente |
| CAR-006 | 1. Seguridad | P0 | Separar DTOs de administración y ocultar secretos | Respuestas sin SOL password, certificado password, OSE token, webhook secret ni API key | CAR-005 | Backend | 2–3 d | Tests de contrato verifican que ningún secreto se serializa | Pendiente |
| CAR-007 | 1. Seguridad | P0 | Definir rol administrativo distinto de la API key de tenant | Endpoints de alta/listado/edición de tenants restringidos | CAR-005 | Arquitectura + Seguridad | 3–5 d | Tenant común no puede administrar tenants | Pendiente |
| CAR-008 | 1. Seguridad | P1 | Externalizar y cifrar secretos; definir rotación de API keys y certificados | Integración con secret store y runbook de rotación | CAR-006 | DevOps + Seguridad | 3–5 d | No hay secretos sensibles en BD en texto claro ni en respuestas/logs | Pendiente |
| CAR-009 | 1. Seguridad | P1 | Endurecer XML y salidas HTTP: XXE, SSRF de callbacks, timeouts y límites de tamaño | Parsers y clientes HTTP seguros | CAR-005 | Backend + Seguridad | 2–4 d | Pruebas negativas de XXE/SSRF/timeouts pasan | Pendiente |
| CAR-010 | 2. Núcleo fiscal | P0 | Sustituir `MAX + 1` por correlativos transaccionales por tenant/serie/tipo | Tabla o secuencia bloqueada con asignación atómica | CAR-004 | Backend + DBA | 3–4 d | Prueba concurrente no genera huecos indebidos ni duplicados | Pendiente |
| CAR-011 | 2. Núcleo fiscal | P0 | Validar reglas fiscales, redondeos y tipos de afectación | Motor de validación con casos gravados, exonerados, inafectos, gratuitos, descuentos y anticipos definidos en alcance | CAR-010 | Backend + Especialista fiscal | 5–8 d | Batería de casos dorados concilia bases, impuestos y total | Pendiente |
| CAR-012 | 2. Núcleo fiscal | P0 | Validar UBL con XSD y reglas SUNAT antes del envío | Validador estructural y reporte de errores accionable | CAR-011 | Backend | 4–6 d | XML de cada tipo pasa XSD/reglas y casos inválidos son rechazados | Pendiente |
| CAR-013 | 2. Núcleo fiscal | P0 | Certificar firma XML y manejo de certificados | Firma compatible con SUNAT, selección segura de alias y control de vigencia | CAR-008, CAR-012 | Backend + Seguridad | 3–5 d | Firma verificada externamente; certificados vencidos o inválidos fallan de forma controlada | Pendiente |
| CAR-014 | 2. Núcleo fiscal | P0 | Completar estados y transiciones del documento | Máquina de estados explícita e idempotente | CAR-011 | Backend | 3–4 d | No existen regresiones de estado y cada transición queda auditada | Pendiente |
| CAR-015 | 2. Núcleo fiscal | P1 | Completar RA/RC y consulta de tickets | Flujo asíncrono completo hasta CDR y estado final | CAR-012, CAR-014 | Backend + Fiscal | 5–8 d | Casos aceptado, observado, rechazado y error transitorio probados | Pendiente |
| CAR-016 | 2. Núcleo fiscal | P1 | Completar GRE 2.0 o mantenerla deshabilitada | Ciclo envío-ticket-consulta-CDR y validación GRE | CAR-012, CAR-014 | Backend + Fiscal | 5–8 d | Homologación GRE superada; hasta entonces feature flag desactivado | Pendiente |
| CAR-017 | 3. Integración | P0 | Diseñar entrega RabbitMQ confiable | ACK/NACK correcto, reintentos con backoff, DLQ y deduplicación | CAR-014 | Backend + DevOps | 4–6 d | Fallos transitorios reintentan; poison messages llegan a DLQ; no hay doble emisión | Pendiente |
| CAR-018 | 3. Integración | P0 | Reforzar idempotencia REST y mensajes | Clave idempotente única por tenant/origen y respuesta repetible | CAR-010, CAR-017 | Backend + DBA | 3–4 d | Solicitudes simultáneas iguales generan un solo documento | Pendiente |
| CAR-019 | 3. Integración | P1 | Implementar outbox para eventos/callbacks | Notificaciones persistidas y desacopladas de la transacción fiscal | CAR-014, CAR-017 | Backend | 4–6 d | Caída del receptor no pierde eventos y permite replay seguro | Pendiente |
| CAR-020 | 3. Integración | P1 | Formalizar callbacks/webhooks | HMAC versionado, timestamp anti-replay, timeout, backoff y auditoría | CAR-009, CAR-019 | Backend | 3–4 d | Entrega, reintento, firma y replay están cubiertos por pruebas | Pendiente |
| CAR-021 | 3. Integración | P1 | Probar integración Tordo/Operation y Layout | Contratos, fixtures y pruebas de integración entre servicios | CAR-003, CAR-018, CAR-020 | Backend + Equipos Tordo | 4–6 d | Venta aprobada termina en documento, callback y PDF verificables | Pendiente |
| CAR-022 | 3. Integración | P2 | Crear adaptador por OSE seleccionado | Contrato aislado, mapeo de errores y pruebas sandbox | CAR-014 | Backend | 4–8 d/proveedor | Suite contractual aprobada por proveedor | Pendiente |
| CAR-023 | 4. Calidad | P0 | Pruebas unitarias de dominio, UBL, firma, validación y numeración | Suite rápida de regresión | CAR-010–CAR-014 | Backend QA | 6–10 d | Cobertura de reglas críticas y todos los casos fiscales definidos | Pendiente |
| CAR-024 | 4. Calidad | P0 | Pruebas de integración con PostgreSQL y RabbitMQ reales | Suite con contenedores para persistencia y mensajería | CAR-004, CAR-017 | Backend QA | 4–6 d | Pipeline reproduce fallos, reintentos e idempotencia | Pendiente |
| CAR-025 | 4. Calidad | P1 | Pruebas E2E de API y UI | Flujos de emisión, búsqueda, descarga, retry, reportes y configuración | CAR-021 | QA + Frontend | 5–8 d | Flujos críticos pasan en entorno efímero | Pendiente |
| CAR-026 | 4. Calidad | P1 | Pruebas de carga, concurrencia y resiliencia | Límites operativos y SLO basados en evidencia | CAR-024 | QA + DevOps | 3–5 d | Se cumplen volumen diario, latencia y recuperación acordados | Pendiente |
| CAR-027 | 4. Calidad | P1 | Homologación SUNAT con evidencias | Matriz de casos, XML, CDR y acta de resultado | CAR-012–CAR-016, CAR-023 | QA + Fiscal | 5–10 d | Todos los tipos en alcance están homologados | Pendiente |
| CAR-028 | 5. Operación | P1 | Añadir observabilidad | Logs estructurados sin secretos, correlation ID, métricas, trazas y dashboards | CAR-017, CAR-019 | DevOps + Backend | 4–6 d | Alertas detectan cola detenida, tasa de rechazo, latencia y expiración de certificados | Pendiente |
| CAR-029 | 5. Operación | P1 | Definir almacenamiento, retención, cifrado y respaldo | Política y jobs de backup de BD/XML/CDR/PDF | CAR-008 | DevOps + Seguridad | 3–5 d | Restauración completa probada con RPO/RTO acordados | Pendiente |
| CAR-030 | 5. Operación | P1 | Endurecer despliegue | Imágenes fijadas, usuario no root, health/readiness, recursos y rollback | CAR-002, CAR-028 | DevOps | 3–5 d | Despliegue y rollback ensayados sin pérdida de documentos | Pendiente |
| CAR-031 | 5. Operación | P1 | Crear runbooks y soporte | Procedimientos para caída SUNAT/OSE, DLQ, certificados, reenvíos y recuperación | CAR-027–CAR-030 | Operaciones | 2–4 d | Guardia puede resolver simulacros usando solo el runbook | Pendiente |
| CAR-032 | 6. Salida | P1 | Piloto controlado con un tenant y series de prueba | Operación paralela, monitoreo y conciliación diaria | CAR-025–CAR-031 | Producto + Operaciones | 5–10 d | Cero pérdida/duplicidad y conciliación 100 % durante el piloto | Pendiente |
| CAR-033 | 6. Salida | P1 | Go-live progresivo | Activación por tenant con feature flags y plan de reversa | CAR-032 | Producto + Operaciones | 2–5 d | Checklist aprobado y SLO estable durante ventana acordada | Pendiente |
| CAR-034 | 7. Evolución | P3 | Implementar países adicionales como proyectos independientes | Compliance, modelo, firma, transporte y homologación por país | CAR-033 | Producto + Arquitectura | Por estimar | Ningún país se declara soportado sin homologación propia | Pendiente |

## Secuencia sugerida

| Ola | Semanas orientativas | Objetivo | Ítems principales | Puerta de salida |
|---|---:|---|---|---|
| 1 | 1–2 | Baseline y contención de riesgos críticos | CAR-001–CAR-010 | Build verde, rutas coherentes, aislamiento tenant probado y correlativos seguros |
| 2 | 3–5 | Núcleo fiscal Perú confiable | CAR-011–CAR-016, CAR-023 | Casos fiscales, UBL, firma y estados cubiertos por pruebas |
| 3 | 5–7 | Integración resiliente | CAR-017–CAR-022, CAR-024 | Mensajería, idempotencia, callbacks y contratos Tordo verificados |
| 4 | 7–9 | Homologación y operación | CAR-025–CAR-031 | Homologación, carga, observabilidad, backup/restore y rollback aprobados |
| 5 | 10–11 | Piloto y salida gradual | CAR-032–CAR-033 | Conciliación 100 %, sin duplicidad ni pérdida, SLO estable |

La duración supone un equipo mínimo de 2 backend, 1 frontend compartido, 1 QA, 1 DevOps compartido y disponibilidad de un especialista fiscal. Con una sola persona, no debe interpretarse como calendario: las jornadas-persona se mantienen, pero las olas se vuelven mayormente secuenciales.

## Camino crítico

`Build/CI → aislamiento multitenant → correlativos → reglas fiscales → UBL/firma → estados → idempotencia/mensajería → integración E2E → homologación → piloto → producción`

GRE y OSE pueden ejecutarse en paralelo después de estabilizar el núcleo, pero no deben bloquear el MVP si se mantienen desactivados mediante configuración.

## Criterios de “listo para producción”

- Un tenant no puede leer, modificar, emitir, descargar ni reportar datos de otro tenant.
- Ningún secreto aparece en respuestas, logs, repositorio o imágenes.
- La numeración y la idempotencia resisten concurrencia y reentregas.
- Cada documento del alcance tiene casos fiscales dorados, XML válido, firma válida y evidencia de homologación.
- RabbitMQ, callbacks y SUNAT/OSE pueden fallar sin perder ni duplicar documentos.
- Se puede rastrear una operación completa mediante correlation ID.
- Backup y restauración están probados; despliegue y rollback tienen runbook.
- El piloto concilia el 100 % de ventas, documentos, estados, CDR y totales.

## Decisiones pendientes

| Decisión | Opciones | Recomendación | Fecha límite relativa |
|---|---|---|---|
| Canal inicial | SUNAT directo / OSE | SUNAT directo para MVP; un OSE concreto como fase posterior | Antes de CAR-011 |
| Alcance GRE | Incluir / diferir | Diferir salvo necesidad comercial inmediata | Antes de CAR-016 |
| Autoridad administrativa | API key maestra / OIDC y roles | OIDC/roles del ecosistema Tordo | Antes de CAR-007 |
| Gestión de secretos | Docker secrets / Vault / servicio cloud | Reutilizar el gestor corporativo existente | Antes de CAR-008 |
| Persistencia de archivos | Volumen local / objeto S3 compatible | Objeto durable con versionado y cifrado | Antes de CAR-029 |
| Objetivos operativos | RPO, RTO, latencia, disponibilidad, volumen | Definir con Producto y Operaciones usando el piloto | Antes de CAR-026 |

## Riesgos que requieren seguimiento semanal

| Riesgo | Probabilidad | Impacto | Mitigación | Indicador |
|---|---|---|---|---|
| Interpretación fiscal incompleta | Alta | Crítico | Especialista fiscal, casos dorados y homologación temprana | Rechazos por código SUNAT |
| Fuga entre tenants o de credenciales | Alta | Crítico | CAR-005–CAR-009 como P0 | Pruebas negativas y hallazgos de seguridad |
| Duplicidad por concurrencia o reentrega | Alta | Crítico | Correlativos transaccionales, idempotencia y DLQ | Duplicados por clave de origen/serie |
| Dependencia inestable de SUNAT/OSE | Alta | Alto | Backoff, circuit breaker, estado asíncrono y operación degradada | Error rate y edad de pendientes |
| Divergencia entre documentación y despliegue | Media | Alto | OpenAPI como contrato y smoke tests | Rutas fallidas después de deploy |
| Pérdida de XML/CDR/PDF | Media | Crítico | Storage durable, checksums, backup y restore probado | Objetos faltantes o checksum inválido |
| Expiración de certificados | Media | Crítico | Inventario y alertas 60/30/15 días | Días restantes por tenant |

## Gobierno del plan

- Actualizar semanalmente `Estado`, responsable, fecha objetivo y evidencia de cada ítem.
- No cerrar una tarea por “código terminado”; debe cumplirse el criterio de cierre y enlazarse la evidencia.
- No iniciar el piloto con un P0 abierto.
- No activar un tipo documental, GRE, OSE o país sin su propia evidencia contractual y de homologación.
