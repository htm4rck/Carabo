# Runbook operativo de Carabo

## Completar Configuración de una empresa

Revisado el **31 de octubre de 2026**. Este procedimiento describe el comportamiento actual; no cambia ambientes, series, credenciales ni documentos existentes.

1. **Elegir empresa y ambiente.** En Carabo → Configuración → Configurar empresa, comprobar el negocio seleccionado. Mantener Acme de pruebas en **BETA**. Cambiar ese selector a Producción no crea una instalación separada ni convierte datos ficticios en datos fiscales reales.
2. **Identidad del emisor.** Razón social es el nombre del negocio en Carabo; RUC del emisor y nombre fiscal deben identificar al emisor tributario. Nombre comercial es opcional. Para uso real, dirección fiscal y ubigeo deben corresponder al emisor. La dirección de salida de la GRE proviene del almacén y puede ser distinta: la dirección ficticia de Piérola configurada en Birdport no cambia automáticamente el domicilio fiscal de Carabo.
3. **Canal.** Seleccionar **SUNAT directo** para la integración directa disponible. OSE requiere proveedor contratado y un adaptador compatible; elegir esa opción por sí sola no habilita cualquier servicio comercial. Ver [selección OSE](../ose-provider-selection.md).
4. **Ingresar credenciales desde la UI.** **Credenciales de esta empresa** muestra tarjetas de Acceso SOL, SUNAT GRE, Certificado digital y Proveedor OSE. Cada dato indica **Guardado**, **Sin registrar** o **Revisar**; los valores guardados permanecen ocultos. Pulsar **Configurar** o **Actualizar** en una tarjeta abre sus campos. Si el dato ya existe, la etiqueta indica **Nuevo/Nueva…**: el campo sirve para reemplazarlo, no representa un dato perdido. Completar lo necesario y pulsar **Guardar cambios**. Se puede ingresar cualquier credencial por pantalla; el TXT es una alternativa para esos mismos datos. Los campos vacíos conservan los valores actuales. **Cancelar** descarta los valores ingresados sin enviarlos. No se necesitan archivos del servidor por cliente ni escribir referencias `secret://`. La API devuelve exclusivamente `configured`/`usable`, nunca valores, contraseñas ni referencias internas. **Guardado** indica que Carabo puede leer el valor, no que SUNAT lo haya autenticado. **Revisar** indica un dato registrado que no se puede resolver.
5. **Obtener credenciales GRE.** En SOL del emisor, abrir **Credenciales de API SUNAT → Gestión Credenciales de API SUNAT**, registrar la aplicación y obtener `client_id` y `client_secret`. Para Carabo GRE, marcar únicamente **GRE Emisión de Comprobantes `/v1/contribuyente/gem`** y seleccionar alcance **Desktop**, como en el manual oficial. Nombre sugerido: `Carabo DEV`; URL: `https://dev.tordo.io/carabo/ui/`. Copiar el ID y la clave completos en sus campos de la UI, conservando los signos `=` finales. Son credenciales distintas de la API key Tordo/SAP → Carabo. La autenticación GRE combina aplicación y usuario SOL. [Manual oficial SUNAT, pp. 3–5](https://cpe.sunat.gob.pe/sites/default/files/inline-files/Manual_Servicios_GRE%20%281%29_0.pdf).
6. **Alternativa: TXT.** Abrir **Importar TXT** y pulsar **Descargar plantilla vacía**, completar localmente las líneas necesarias y cargar el archivo en **Importar credenciales**. Se admite UTF-8 (con o sin BOM), CRLF/LF y comentarios que comiencen por `#`; máximo 16 KB. Se divide cada línea por el primer `=` para conservar claves Base64. Campos desconocidos, duplicados, caracteres de control y archivos sin cambios se rechazan sin aplicar la actualización. El TXT temporal del servidor se elimina al terminar. La plantilla siempre contiene únicamente:

   ```ini
   SOL_USER=
   SOL_PASSWORD=
   GRE_CLIENT_ID=
   GRE_CLIENT_SECRET=
   CERTIFICATE_PASSWORD=
   OSE_API_TOKEN=
   ```

   Descargar de nuevo **nunca exporta las claves guardadas**. El archivo descargado que se completa localmente contiene secretos: custodiarlo como las credenciales originales. El token OSE es opcional para SUNAT directo. La contraseña de un certificado ya cargado se verifica antes de aceptar su cambio; para un certificado nuevo, usar la acción **Subir certificado**, que permite ingresar su contraseña directamente.

7. **Series de la pantalla.** Para el flujo Tordo, mantener los respaldos actuales y administrar series, rangos y asignación en **Aquila → Empresas → empresa → Series**, por sucursal y caja. Operation envía la identidad fiscal completa. Las cuatro entradas de Carabo no llevan un segundo correlativo y no cambian los documentos ya emitidos. Si se integra SAP, el adaptador debe enviar la serie y el número fiscal asignados allí. Consultar [responsabilidad de numeración y límites GRE](../external-integration.md#series-y-correlativos-tordo-sap-y-otros-erp).
8. **Otros campos.** Webhook es opcional y debe ser una URL HTTPS del receptor que procesa resultados; no es la URL de SUNAT. Una integración Tordo existente debe conservar su callback configurado. El máximo diario es una cuota de Carabo, no un rango fiscal ni un correlativo.
9. **Guardar y comprobar.** Guardar configuración. En la empresa, abrir el diagnóstico fiscal y comprobar identidad, certificado, referencias y canal. El certificado PKCS#12 se carga en la acción de certificado, ingresando su contraseña; se valida y se guarda cifrada. Si ya está registrado y vigente, no hace falta reemplazarlo por agregar GRE. Comprobar también `GET /carabo/api/dispatch-guides/readiness` con una identidad autorizada de la empresa: el diagnóstico general de CPE no incluye toda la configuración GRE, y el de GRE comprueba canal disponible y presencia/resolución de OAuth, no un envío ni una autenticación real ante SUNAT.
10. **Validación final.** Comparar el JSON con `/documents/validate` o `/dispatch-guides/validate` antes de emitir. Estos endpoints no generan un comprobante ni asignan correlativo. La prueba real de emisión debe conservar solicitud, XML, respuesta y CDR. Una respuesta de configuración correcta o un ticket pendiente no equivalen a aceptación. El traslado se habilita únicamente con la aceptación fiscal exigida por el flujo.

### Estado observado en Acme DEV

- Empresa `B20123456789`, emisor de prueba `20123456789`, ambiente BETA y SUNAT directo.
- Usuario SOL, referencia de contraseña SOL y certificado registrados. Esta inspección confirmó presencia en la configuración; no expuso secretos ni volvió a autenticar esas credenciales.
- ID y referencia de clave GRE todavía sin configurar.
- Respaldos actuales: factura `F001`, boleta `B001`, crédito `FC01`, débito `FD01`. Para notas vinculadas a boletas, la integración debe enviar su serie B; no usar los respaldos F como si sirvieran para ambas familias.
- Domicilio fiscal de prueba registrado: Av. Principal 123, ubigeo 150101. No se modificó al asignar Piérola a los almacenes.

### GRE de pruebas y reintentos

SUNAT declara que no ha considerado un ambiente de pruebas para la nueva GRE y ofrece SFS para validar el XML ([preguntas frecuentes, 17 y 18](https://cpe.sunat.gob.pe/node/122), consultadas el 31/10/2026). El host `gre-beta.sunat.gob.pe` del cliente anterior no resuelve DNS en este entorno. El SOAP beta que aparece en documentos antiguos no es el canal REST GRE 2.0.

Carabo conserva las solicitudes BETA/HOMOLOGATION con `BLOCKED_CONFIGURATION`, explica el motivo y no intenta autenticarlas ni enviarlas por el canal productivo. Las solicitudes antiguas no productivas que todavía tenían reintento programado se bloquean en el siguiente ciclo del procesador, sin esperar el backoff y sin envío. Las facturas beta mantienen su canal independiente. Completar claves GRE no habilita un canal BETA inexistente.

La lista de guías muestra el **último resultado guardado**, cuándo cambió el estado, cuándo se consultó la lista, fallos consecutivos y próximo intento. Consultar una guía sin ticket devuelve su estado guardado; no genera un envío.

`POST /dispatch-guides/{id}/retry` programa el mismo documento y conserva número, XML y último resultado. Supervisor/Admin/Root puede solicitarlo; una credencial productora requiere `DOCUMENT_EMIT`. Con ticket solo se consulta su resultado. Un envío incierto, rechazo o documento definitivo no se reenvía; requiere conciliación o el procedimiento fiscal correspondiente. La solicitud se bloquea también si el canal o la fecha no permiten continuar.

Procesador de envíos: cada 15 s. Consulta de tickets: cada 30 s. Tras fallos temporales, backoff de 1, 2, 4, 8, 16, 32 y 60 min; tras 8 fallos consecutivos se detiene el ciclo. Una solicitud manual de recuperación con ticket puede iniciar un nuevo ciclo de consulta. Falta de credenciales se revisa cada 5 min; no equivale a un intento de envío. Los errores reintentables previos al envío conservan su clasificación; resultados inciertos quedan bloqueados.

## Almacén seguro de credenciales: instalación y recuperación

El operador de plataforma configura **una única clave maestra por instalación**, no un archivo por empresa. `carabo.credentials.key-reference` (variable `CARABO_CREDENTIALS_KEY_REFERENCE`) usa por defecto `secret://tenant-credentials-key`, dentro de `CARABO_SECRETS_PATH` (`/run/secrets/carabo` por defecto). Debe contener exactamente 32 bytes codificados en Base64 o 64 dígitos hexadecimales, generados criptográficamente y legibles solo por el proceso/operador autorizado. Mantener el montaje de solo lectura. En DEV, el host monta `/opt/tordo-platform/.runtime/secrets/carabo` en `/run/secrets/carabo`.

Las contraseñas y tokens nuevos se cifran con AES-256-GCM, nonce aleatorio y autenticación ligada a empresa y campo. La tabla `carabo.tenant_credential` conserva versiones inmutables; la referencia activa y su versión se actualizan en la misma transacción, con bloqueo de la empresa. Las credenciales anteriores basadas en archivos siguen funcionando. Los administradores de empresa solo consultan/configuran su empresa activa; root mantiene el alcance de plataforma y las integraciones productoras no acceden a estas rutas.

Sin clave maestra válida la UI deshabilita el guardado y la API rechaza las escrituras secretas. No generar otra clave sobre una existente: invalidaría todas las credenciales cifradas. Respaldar la clave mediante el gestor de secretos **separadamente** del respaldo de BD, pero conservar ambos en un mismo punto de recuperación. Restaurar BD, volumen de certificados y clave original antes de reanudar emisión. La rotación de la clave maestra requiere un procedimiento de recifrado; cambiar el archivo no hace ese trabajo automáticamente.

Los certificados también se guardan con nombres versionados para que una transacción fallida no sobrescriba el certificado activo. Un fallo posterior a la escritura del archivo puede dejar una versión sin referencia: limpiar únicamente archivos que no estén referenciados en BD y una vez cerradas las transacciones, conservando las versiones necesarias para recuperación.

## API caída

1. Confirmar `/carabo/api/health` y revisar `docker logs tordo_dev-carabo-1` usando el correlation ID.
2. Verificar PostgreSQL, RabbitMQ, secretos y volumen documental antes de reiniciar solo `carabo`.
3. No eliminar colas, DLQ, archivos ni filas. Escalar si Flyway no queda en la versión esperada.

## Mensajes DEAD/DLQ

1. Abrir **Carabo → Entregas y webhooks** y revisar error, tenant e idempotency key.
2. Corregir configuración o payload. Un supervisor puede usar **Replay** una sola vez por intento.
3. Confirmar `REPLAYED`, luego un nuevo registro `PROCESSED`; si vuelve a `DEAD`, detener replay y escalar.

## Webhook agotado

1. Validar URL, DNS, TLS y que el receptor acepte `X-Carabo-Event-Id`, timestamp y firma HMAC v1.
2. Corregir el receptor y usar **Reactivar entrega**. Nunca deshabilitar la protección SSRF para destinos públicos.

## SUNAT u OSE no disponible

1. Separar rechazo fiscal permanente de timeout/HTTP 429/5xx reintentable.
2. Revisar certificado, credenciales y preflight del tenant sin imprimir secretos.
3. El scheduler aplicará backoff; no emitir nuevamente con otra idempotency key.
4. Para OSE, `CIRCUIT_OPEN` se recupera automáticamente después de 60 segundos.

## Certificado próximo a vencer

1. Cargar el PKCS#12 nuevo y su contraseña desde la UI; Carabo valida identidad de firma y vigencia antes de activarlo.
2. Ejecutar preflight y una emisión beta antes de retirar el certificado anterior.

## Restore documental

1. Detener solamente productores/worker de Carabo durante la ventana.
2. Restaurar BD y volumen desde el mismo manifiesto, comprobar checksum e inventario por tenant.
3. Reanudar y conciliar documentos, outbox, RC/RA y mensajes asíncronos antes de abrir emisión.
