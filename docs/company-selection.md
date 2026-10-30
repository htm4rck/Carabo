# Selección de empresa en Carabo

La interfaz consulta `GET /access/businesses` al iniciar sesión y al abrir una sesión guardada. El selector muestra empresas habilitadas y activas en Carabo, con membresía activa del usuario. El nombre visible procede de `setup.business`.

Root no necesita un rol `*_Carabo` adicional en cada membresía. Los demás usuarios requieren `OPERATOR_Carabo`, `SUPERVISOR_Carabo` o `ADMIN_Carabo` en esa empresa. Una empresa registrada en Business pero no habilitada en Carabo no aparece. Tampoco aparecen membresías inactivas.

La consulta requiere un JWT humano válido y obtiene el usuario del token; no acepta un usuario o empresa de destino enviados por el cliente. Puede ejecutarse antes de seleccionar la empresa, sin `business-code`. Las operaciones documentales conservan su autenticación, autorización y alcance por cabecera. Las claves de integración no pueden consultar este catálogo humano.

El selector conserva la empresa activa si sigue disponible. Al cambiar, guarda la elección y recarga los datos con su `business-code`. Si la empresa guardada dejó de estar autorizada, selecciona una disponible y recarga. Una falla de consulta deja el selector deshabilitado y muestra un botón para reintentar; no amplía permisos a partir de los datos guardados en el navegador.

## Verificación en DEV

El 28 de octubre de 2026 se verificó `https://dev.tordo.io/carabo/ui/documents`: root puede cambiar entre Tordo Origins y Acme Corp (`B20123456789`). En Acme aparece la boleta de Birdport **B001-00000001 por S/97.00**, aceptada en SUNAT beta.

Se aprobaron ocho pruebas existentes de políticas/contexto de seguridad, el build de Quarkus y el build de producción de Angular 21. Se ejecutaron además catorce comprobaciones HTTP/navegador contra DEV: membresías activas, exclusión por rol y empresa deshabilitada, rechazo de autenticación anónima o de integración, parámetros falsificados, aislamiento documental, actualización de sesiones guardadas, inicio de sesión nuevo, persistencia del selector y recuperación de error. No se emitieron documentos durante esta comprobación.

El flujo de login del navegador usa una respuesta Auth controlada con JWT de verificación y consulta Carabo real. Las identidades temporales se eliminan al finalizar. La evidencia y el script reproducible están en Birdport: `scripts/verify-carabo-business-selector.cjs` y `docs/assets/carabo-business-selector-verification/`.

El despliegue de esta corrección se limita a DEV, mediante las imágenes locales `birdport-fiscal-carabo:dev` y `birdport-carabo-ui:dev` y el override privado de servicios. Producción conserva sus imágenes fijadas.
