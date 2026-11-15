# Recuperación de tickets fiscales — 2026-11-15

La consulta RC/RA obtiene un snapshot en una transacción corta, consulta al receptor sin transacción abierta y persiste el resultado en otra transacción. Conserva operación y ticket; una respuesta tardía no puede sustituir un CDR terminal. Sólo la aceptación o rechazo fiscal cambia el estado del documento. Un error de transporte o credencial conserva la baja pendiente y no se convierte en rechazo fiscal.

La consulta SUNAT tiene un plazo de 20 segundos dentro del presupuesto HTTP del productor. Ante timeout guarda el error y permite consultar el mismo ticket. Repetir la solicitud de baja cuando ya existe ticket devuelve la operación original incluso si falló una consulta; no vuelve a enviar el lote.

Seis escenarios PostgreSQL sobre migraciones reales verifican ausencia de transacción durante la espera remota, recuperación del mismo ticket, concurrencia con resultado tardío, aislamiento de empresa, rechazo fiscal, error de credencial por HTTP y repetición sin reenvío. El cliente SOAP prueba timeout real contra un servidor local detenido antes de responder. `./gradlew check` pasó: 148 pruebas ejecutadas, 15 omisiones heredadas de suites opcionales. Estas pruebas locales sustituyen la red fiscal y no acreditan un CDR SUNAT adicional.

La prueba DEV de Amazilia obtuvo aceptación del comprobante y ticket de baja real. Su CDR se registra por separado. La guía de [SUNAT para Beta](https://orientacion.sunat.gob.pe/12-pautas-servicio-beta) describe pruebas de estructuras XML de facturas, boletas y notas; no se interpreta un ticket ni una indisponibilidad como aceptación de la baja.
