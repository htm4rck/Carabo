# Consolidación de cambios locales

Se incorpora todo el trabajo funcional pendiente del clon local a `develop`,
conservando los cambios ya publicados. La copia local se sincroniza después de
verificar el push por SHA, con respaldo y detección de cambios concurrentes.

## Alcance

Selección de empresas autorizadas en Carabo, QR y autenticación para PDF,
resúmenes y bajas fiscales, validación de credenciales GRE y ampliación del código
de respuesta fiscal. Incluye servicios, UI, migración y sus pruebas/documentación.

La comprobación del selector ya documentada cubrió ocho pruebas de políticas,
los builds Quarkus/Angular y catorce verificaciones HTTP/navegador, incluidas
membresías, roles, autenticación, aislamiento y recuperación de error.

## Evidencia reutilizada

Las clases y pruebas Java pendientes tienen compilación posterior al código fuente.
Se revisaron los reportes JUnit existentes, sin volver a ejecutar las suites:

| Suite | Pruebas | Fallos/errores | Fecha del reporte existente |
| --- | --- | --- | --- |
| io.carabo.sunat.SunatBillServiceTest | 7 | 0 | 2026-10-28T02:36:02.776Z |
| io.carabo.app.client.LayoutClientTest | 1 | 0 | 2026-10-28T19:29:14.671Z |
| io.carabo.app.service.DispatchGuideReadinessTest | 2 | 0 | 2026-10-28T19:29:16.129Z |
| io.carabo.app.service.BatchServiceXmlTest | 4 | 0 | 2026-10-28T19:29:15.963Z |

La carga CSS ya estaba publicada; su contenido JSON es equivalente al local.
Se conserva el formato de develop y no se repite el build frontend ya aprobado.
Se revisan diff, sintaxis aplicable, migraciones y secretos antes del commit.
La consolidación usa `[skip ci]`; no dispara otro build/despliegue completo ni
aplica migraciones a las bases de datos. Reports y Corvus siguen excluidos.
