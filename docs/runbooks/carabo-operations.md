# Runbook operativo de Carabo

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

1. Cargar el PKCS#12 nuevo con referencia `secret://`, alias y vigencia correctos.
2. Ejecutar preflight y una emisión beta antes de retirar el certificado anterior.

## Restore documental

1. Detener solamente productores/worker de Carabo durante la ventana.
2. Restaurar BD y volumen desde el mismo manifiesto, comprobar checksum e inventario por tenant.
3. Reanudar y conciliar documentos, outbox, RC/RA y mensajes asíncronos antes de abrir emisión.
