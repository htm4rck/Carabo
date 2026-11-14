# Carabo gaps del producto independiente

Corte 2026-11-14. Se conservan 15 gaps de Carabo y se añaden 7 de independencia: 22 registros. No representan funcionalidades ausentes ni un porcentaje de avance. La infraestructura compartida se considera disponible; los gaps describen tareas, datos, experiencia o intercambios pendientes.

[Alcance comercial](ALCANCE_COMERCIAL.md) · [Especificación funcional](ESPECIFICACION_FUNCIONAL.md) · [Gaps](GAPS_PRODUCTO_INDEPENDIENTE.md) · [Inventario CSV](gaps-2026-11-14.csv) · [Entradas y salidas](contratos-2026-11-14.csv)


## Mínimo standalone incorporado al alcance

Aquila es transversal; Amazilia y BirdPort incluyen Carabo. Las funciones nuevas de apoyo se habilitan sólo para el perfil standalone, sin ocultar núcleo ni aplicaciones incluidas en integrado. La ficha de aplicación y su especificación contienen los requisitos CA-AUT; los gaps existentes cierran su implementación, visibilidad/autorización y cambio de modalidad, sin nuevas filas por esta decisión.

## Habilitantes adicionales y cierre

| ID y prioridad | Qué falta | Responsable | Cierre verificable | Dependencias |
|---|---|---|---|---|
| CAB-PROD-01 P1 D/N/V | Cerrar las tareas de configuración e incorporación que necesita el usuario para operar Carabo por sí solo. Los backends y las bases compartidos se consideran disponibles; no son una brecha de provisión ni obligan a comprar sus frontales. | Producto + Auth/Setup/Business + Carabo | Empresa nueva completa el proceso básico de Carabo sin comprar otra UI; puede configurar sus tiendas/usuarios autorizados y revocar acceso sin exponer datos ajenos. | IND-01; IND-06 |
| CAB-PROD-02 P1 D/V | Completar incorporación y salida de maestros/documentos del perfil: equivalencias externas, vista previa, errores por fila y apertura/corte conciliados donde aplique. | Maestros/Operation/Inventory según dominio + Carabo | Importación representativa y repetida preserva identidades/unidades/monedas y no duplica apertura o documento; exportación completa permite conciliar y salir del producto. | IND-02; IND-03; CAB-101-01 |
| CAB-PROD-03 P1 D/N/V | CA-I01/02/03: calificar productor ERP/POS concreto, distinguir emitir/importar y devolver estados/artefactos a su original. | Anser + autoridad del dominio + Carabo | Identificar proveedor, versión, compañía y operaciones; alta, replay, respuesta perdida, rechazo, cambio de configuración y conciliación producen un efecto por autoridad con evidencia del receptor. | IND-02; IND-06; gaps del dominio aplicables |
| CAB-PROD-04 P1 D/V | CA-I04/06: validar lote sin emitir, confirmar intención y exportar documentos/estados/artefactos; libro regulatorio sigue en CAB-102. | Integraciones/Reportes + Carabo + responsable del receptor | Publicar esquema/ejemplo y procesar archivo real anonimizado del receptor: preview, duplicado, fila errónea, corrección y ACK/importación; montos y documentos concilian. Si sólo hay exportación manual, declarar esa modalidad; no cerrar reserva/pago online mediante un reporte posterior. | IND-03; CAB-PROD-02/03 |
| CAB-PROD-05 P1 D/N/V | Completar configuración/captura fiscal en Carabo para modalidad humana y conservar productor API; no exigir otros frontales, inventario o un catálogo completo. | Auth/Business/Aquila + servicios propietarios + Carabo | Registrar alternativa elegida por perfil y su motivo; usuario completa las tareas necesarias sin contratación obligatoria de otro frontal, recaptura ni pérdida de contexto. UI, ruta y API aplican sus permisos; una superficie complementaria, si se elige, conserva contexto/retorno y el coste comercial queda explícito. | IND-01; IND-05; CAB-PROD-01; servicios compartidos disponibles |
| CAB-PROD-06 PT V | Aceptar los perfiles autónomo, conectado API y conectado por lote que se ofrezcan, con instalación nueva, datos/operación representativos y responsables nominales. | Producto + QA + cliente piloto + Carabo | Cada perfil contratado registra versión/artefacto/ambiente, dependencias publicadas, permisos, fallos, reinicio/replay/concurrencia y conciliación de receptor. Una prueba de otro frontal o un lote exportado no cierra el perfil completo. | IND-06; CAB-PROD-01/02/03/04/05; gaps de capacidades aplicables |
| CAB-PROD-07 P0 D/N/V | Cerrar productor manual del frontal: formulario/DTO/validación compatibles, número fiscal bajo autoridad explícita y persistencia de contenido/identidad de la intención tras recarga. | Carabo UI/API + Business si delega numeración | Emitir factura/boleta/nota del perfil sin otro frontal; probar number obligatorio, agotamiento/concurrencia si se delega contador, respuesta perdida/reinicio y misma intención. No compartir serie con contador del ERP ni generar otra UUID para resolver un envío incierto. | CAB-098-01; CAB-100-01; CAB-PROD-01/05; IND-02 |

## Extensiones de los gaps conservados

Estos criterios se suman a los originales; no sustituyen su prioridad, responsable ni aceptación. Los consumidores conservan su estado propio.

| Gap original | Extensión para convivencia e independencia |
|---|---|
| CAB-101-01 | Importación por archivo/API distingue claramente documento emitido de solicitud por emitir, conserva origen y requiere evidencia verificable; reenviar su lote no emite. |
| CAB-103-01 | El perfil que conserve GRE ya numerada en ERP exige contrato que admita ese número; el contrato actual no queda homologado por agregar una columna a un archivo. |
| CAB-102-02 | Exportación íntegra por corte/tipo/estado/moneda y referencias se concilia con el receptor; incluye notas/bajas según contrato y detecta truncamiento por página. |
| CAB-100-02 | Cada canal/proveedor se acepta individualmente; un reporte de resultado o aceptación BETA no homologa el OSE comercial seleccionado. |

## Cómo se cierra una integración o un reporte

Un perfil registra sistema/proveedor/versión/compañía, dirección, datos, autoridad, frecuencia, errores y responsable. El cierre exige datos representativos del cliente, prueba de recepción/aplicación en el destino, respuesta de vuelta o conciliación y recuperación de duplicados o resultado incierto.

Para archivos: esquema/versiones, ejemplo, vista previa, rechazo por fila, duplicado y corrección, detalle completo y totales por moneda/estado. Si la aplicación sólo entrega un archivo al contador, se acepta esa modalidad y su recepción/conciliación; no se anuncia integración automática ni contabilización confirmada. Para API: probar altas/cambios/cancelaciones admitidos, timeout/replay y resultado autoritativo. No se cierra un efecto online con un reporte posterior.

El estado de un perfil puede ser aceptado mientras otras modalidades siguen abiertas. El gap global sólo se cierra cuando cumple todo el alcance que declara; el registro debe indicar perfiles cubiertos, evidencia, límites y pendientes. Los IND se resuelven una vez y cada producto prueba su consumo.

## Orden de entrega

1. Conservar P0 y cierres técnicos existentes; resolver los P0 aplicables del recorrido elegido.
2. Cerrar tareas mínimas de operación propia, incorporación y alternativa de experiencia.
3. Completar el primer conector o los archivos necesarios para el cliente; aceptar en el receptor.
4. Ejecutar los casos del perfil con responsables y conciliación; ampliar después sin borrar compromisos anteriores.

PT acompaña a su entrega y bloquea la habilitación cuando afecta dinero, inventario, fiscalidad o permisos. Un backend disponible, un build o una publicación aislada no completan un recorrido de usuario.

[Modelo de independencia y alternativas de experiencia](/home/marc/Documentos/Proyects/Tordo.Platform/docs/portfolio/productos-independientes-2026-11-14/MODELO_DE_INDEPENDENCIA.md)
