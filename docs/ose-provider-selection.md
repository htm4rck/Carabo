# Puerta de decisión para integrar un OSE

Carabo no declara compatibilidad con un “OSE genérico”. Cada operador tiene autenticación, transporte, estados,
límites, errores y acuerdos operativos propios. El cliente actual implementa únicamente el contrato interno
**Carabo OSE Gateway v1** y funciona como frontera para incorporar un adaptador del proveedor elegido.

SUNAT indica que el emisor debe contratar y autorizar uno o más OSE, y mantiene un padrón oficial de operadores:

- [Sistema de Emisión OSE](https://cpe.sunat.gob.pe/informacion_general/operador_servicios_electronicos)
- [Padrón oficial de OSE](https://www.sunat.gob.pe/padronesnotificaciones/)

## Información requerida para seleccionar proveedor

1. Razón social y RUC del OSE vigente en el padrón SUNAT.
2. Contrato técnico y comercial, ambientes sandbox/productivo y SLA.
3. Esquema de autenticación y proceso de rotación/revocación.
4. Contratos de envío, consulta, CDR, baja, resumen y contingencia.
5. Catálogo de errores con clasificación terminal/reintentable.
6. Límites de tasa, tamaño, concurrencia e idempotencia.
7. Requisitos de IP, certificados, autorización SUNAT y fecha efectiva de vinculación.
8. Política de custodia, consulta, auditoría y soporte.

## Definition of Done del adaptador

- El adaptador traduce exclusivamente entre el contrato del proveedor y `Carabo OSE Gateway v1`.
- Las credenciales se resuelven por `secret://`; no aparecen en BD, logs ni respuestas.
- Suite contractual cubre aceptación, observación, rechazo, procesamiento, timeout, `429`, `5xx` y respuesta inválida.
- Sandbox devuelve CDR real, que Carabo persiste y expone por consulta/descarga.
- Reenvío de la misma clave no genera un segundo comprobante.
- Se documentan SLA, retry, circuit breaker, límites y procedimiento de escalamiento.
- El tenant piloto autoriza al OSE en SUNAT y completa la prueba de inicio a fin.

Hasta cumplir esos puntos, `SUNAT_DIRECT` permanece como canal del MVP y FUN-006 no puede marcarse lista.
