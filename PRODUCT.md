# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

El usuario principal es la persona responsable de facturación o contabilidad de una empresa. Opera documentos tributarios, consulta su estado ante SUNAT, descarga sus representaciones y atiende incidencias de emisión. En DEV se reutilizan los usuarios semilla de Columba para representar operador, supervisor y administrador de plataforma.

## Product Purpose

Carabo es la aplicación de facturación electrónica multitenant del ecosistema Tordo. Su propósito es generar, firmar, enviar, consultar y conservar comprobantes electrónicos con trazabilidad fiscal. El primer alcance verificable es Perú y SUNAT. El éxito significa operar sin pérdida ni duplicidad, aislar estrictamente a cada empresa y hacer visible el estado real de cada documento.

## Positioning

Carabo conecta la operación transaccional de Tordo con el ciclo tributario completo: UBL, firma digital, SUNAT u OSE, CDR, custodia, reintentos y representación impresa. No es solo una pantalla de emisión ni un conector aislado.

## Operating Context

La aplicación se usa durante la operación fiscal diaria para emitir facturas, boletas y notas; revisar aceptaciones, observaciones y rechazos; descargar XML, CDR y PDF; procesar resúmenes y bajas; consultar reportes; y administrar la configuración fiscal autorizada. DEV se publica bajo `/carabo/ui/` y la API bajo `/carabo/api/`.

## Capabilities and Constraints

- Tordo Auth es la autoridad de usuarios, empresas, roles, permisos, sesiones y licencias. Carabo no duplica credenciales ni membresías en su propia base.
- Roles confirmados: `OPERATOR_Carabo`, `SUPERVISOR_Carabo`, `ADMIN_Carabo`, `INTEGRATION_Carabo` y `ROOT_PLATFORM`.
- La configuración fiscal y la administración de empresas quedan reservadas para Admin y Root.
- Cada aplicación productora usa una identidad de máquina y credencial independiente con scopes; no sustituye una identidad humana ni consume una licencia de usuario.
- Supervisor consulta estado fiscal e integraciones sin revelar secretos; Admin/Root administra certificados y credenciales de productores.
- Cada solicitud humana debe derivar la empresa y los permisos de una sesión válida de Tordo Auth.
- La UI y el backend deben mostrar versión, fecha de construcción y revisión desplegada.
- Perú es el único país del primer alcance. Chile, Colombia y México no pueden presentarse como capacidades productivas.
- La homologación fiscal y la preparación productiva continúan abiertas; la interfaz no debe fabricar estados de conformidad.

## Brand Commitments

El nombre del producto es Carabo y su descriptor es “Facturación electrónica”. La identidad debe transmitir infraestructura fiscal robusta, precisión operativa, seguridad y alcance de clase mundial. Debe sentirse como una herramienta profesional del ecosistema Tordo, no como una plantilla administrativa genérica ni como una aplicación lúdica.

La referencia conceptual reproducible usa el seed `46069c7ea31fd284`. Su tesis es que Carabo hace inequívoca la siguiente acción fiscal segura; el mundo visual combina Lato, superficies blancas, estructura `#173317`, estados secundarios `#8CBD8C`/`#768A76` y reserva `#80EF80` para progreso, selección y siguiente acción.

## Evidence on Hand

- Alcance, riesgos y tablero de ejecución: `Tordo/Catalogos/Aplicaciones/TORDO-APP-005 Carabo Facturacion Electronica.md`.
- Implementación real Angular y Quarkus en `apps/ui` y `apps/api`.
- Convenciones de autenticación y semillas verificables en Columba y `tordo/migrations/auth/003-columba-access.sql`.
- No existen testimonios, cifras comerciales ni certificaciones de homologación aprobadas; la UI no debe inventarlos.

## Product Principles

1. La seguridad de tenant y la validez fiscal preceden a la conveniencia operativa.
2. Cada estado debe ser explícito, rastreable y accionable.
3. La densidad de información debe favorecer lectura rápida y decisiones seguras.
4. Las versiones desplegadas deben ser visibles y comprobables.
5. Una capacidad se presenta como disponible solo cuando existe evidencia técnica y operativa.

## Accessibility & Inclusion

La operación principal debe ser usable con teclado, conservar foco visible, mantener contraste legible y no depender exclusivamente del color para comunicar estados.
