# Carabo alcance comercial y capacidades

Emite y conserva los documentos fiscales de tu negocio sin cambiar el sistema con el que vendes.

Puedes incorporar Carabo conservando los sistemas y procesos que ya te funcionan. El producto debe permitir completar las tareas contratadas, mantener los datos que necesita y entregar resultados verificables a tu operación. Los servicios y bases compartidos necesarios se consideran disponibles.

Este es el alcance objetivo por procesos. La disponibilidad se contrasta en la especificación y los gaps; las alternativas de experiencia y los perfiles comerciales se seleccionan por caso, sin convertir el ejemplo de acceso MVP a otro frontal en una decisión.

[Alcance comercial](ALCANCE_COMERCIAL.md) · [Especificación funcional](ESPECIFICACION_FUNCIONAL.md) · [Gaps](GAPS_PRODUCTO_INDEPENDIENTE.md) · [Inventario CSV](gaps-2026-11-14.csv) · [Entradas y salidas](contratos-2026-11-14.csv)

## Conectar el sistema que ya vende

**Lo que podrás hacer:** Credencial de máquina/scopes; documento comercial y numeración del origen; referencias estables.

**Recorrido:** Alta de productor → validar documento → admitir identidad → emitir por ruta acordada → devolver resultado.

**Resultado comprobable:** ERP conserva su venta y un único correlativo; replay devuelve la misma emisión y ningún consumidor necesita usuario humano de servicio.

## Emitir manualmente cuando no existe otro productor

**Lo que podrás hacer:** Editor completo y productor manual incluido; maestro breve opcional, sin exigir inventario ni POS.

**Recorrido:** Formulario → receptor/líneas/impuestos → numeración autorizada → confirmar intención durable → emitir/consultar.

**Resultado comprobable:** Factura/boleta/nota del perfil aceptado se crea sin otro producto; correlativo válido e intención sobreviven recarga sin otra emisión.

## Validar, firmar y entregar el documento

**Lo que podrás hacer:** Tipos/afectaciones/proveedor delimitados; certificado vigente; impuestos y descuentos conservados.

**Recorrido:** Normalización → validación → generación UBL → firma → envío → ticket/CDR.

**Resultado comprobable:** Importes de origen y XML concilian en los casos habilitados; canal o régimen no calificado no se presenta como soportado.

## Conocer y recuperar el resultado fiscal

**Lo que podrás hacer:** Estados técnicos y fiscales distintos; referencia estable, ticket y outbox con recuperación.

**Recorrido:** Admisión → procesamiento → consulta/callback → resultado final → resolución autorizada.

**Resultado comprobable:** 202 o ticket no se anuncia aceptado; tras pérdida de respuesta se consulta misma identidad y el productor recibe un resultado trazable.

## Corregir y reconocer documentos externos

**Lo que podrás hacer:** Distinguir documento ya emitido de solicitud por emitir; notas referencian original y numeración propia.

**Recorrido:** Original validado → nota/corrección permitida o importación externa → vínculo y estado → comunicación al productor.

**Resultado comprobable:** Documento externo validado no dispara envío; nota conserva original y resultado sin mover inventario ni reembolsar por inferencia.

## Emitir guías para el traslado contratado

**Lo que podrás hacer:** Origen/destino, transportista/conductor/vehículo, partidas y emisor; contador local o número externo según contrato aceptado.

**Recorrido:** Datos de traslado → numeración exclusiva → envío → ticket/CDR → expediente enlazado.

**Resultado comprobable:** Guía del perfil conserva un número/estado y no descuenta stock; repetir envío o recibir mercadería no origina otra guía automáticamente.

## Conservar y entregar el expediente

**Lo que podrás hacer:** Artefactos protegidos, integridad, plantilla y destinatario; servicios de PDF/correo incluidos si se prometen.

**Recorrido:** Resultado → XML/CDR/PDF → custodia → descarga/correo → restauración y verificación.

**Resultado comprobable:** Restaurar BD+artefactos+referencias recupera el expediente del tenant correcto; un fallo de correo no cambia el resultado fiscal.

## Conciliar documentos con administración y otros sistemas

**Lo que podrás hacer:** Reporte de intercambio completo por productor/periodo/moneda; API o archivo estructurado.

**Recorrido:** Seleccionar corte → exportar detalle/artefactos → entregar → recibir aceptación/rechazos → conciliar notas/bajas.

**Resultado comprobable:** ERP o contador concilia todos los documentos y notas con el reporte y sus archivos; exportar no acredita un libro regulatorio no habilitado.

## Cómo se incorpora a tu operación

| Modalidad | Qué resuelve | Condición |
|---|---|---|
| Operación propia | El usuario completa en el producto las tareas y captura mínima necesarias; backends y bases disponibles por detrás. | No exige sustituir procesos ajenos a la promesa ni comprar otra UI para sostenerla. |
| Convivencia por API o eventos | Cliente conserva sistemas/autoridades; conector de proveedor, versión y operaciones concretas. | Confirmación de efectos online y resultado de vuelta; no basta que exista un backend Tordo. |
| Convivencia por archivos | Datos o reportes estructurados con corte, validación, resultado del receptor y conciliación. | Válida para tareas/lotes con latencia aceptable; no promete reserva, cobro o emisión online por un archivo posterior. |
| Acceso complementario como alternativa | Reutilizar un recorrido ya hecho en otro frontal cuando sea más conveniente para ese usuario/perfil. | Se compara con la experiencia propia; no es solución universal ni gratuidad decidida. |

La selección delimita un piloto; no elimina capacidades ni compromisos anteriores. Cada producto conserva seguridad, recuperación, trazabilidad y exportación necesarias para la promesa. Una función básica no se convierte en otra compra porque utilice un backend compartido.

## Mínimo operativo y elección de experiencia

| Decisión de experiencia | Análisis |
|---|---|
| Mínimo que debe resolver | Configurar emisor y capturar receptor/líneas/identidad fiscal o recibirlos del productor; emitir, consultar, corregir y entregar/exportar artefactos. |
| Base observada | El formulario manual omite number mientras el backend lo exige. La API externa tiene contratos de productor; eso no completa automáticamente el recorrido humano. |
| Opción preferida | Cerrar el productor manual dentro de Carabo si se ofrece facturador con UI; para clientes API conservar el productor existente. No añadir inventario ni compras. |
| Cuándo sirve otro frontal | No se justifica entregar otro frontal para capturar un comprobante básico; resolver editor/numeración/recuperación en Carabo. |
| Cliente con sistemas existentes | API con ERP/POS existente o importación de archivo con vista previa e intención explícita; retorno de estados/artefactos y reporte conciliable. |
| Estado de la elección | Recomendación: completar el recorrido fiscal propio; separar modalidad manual y API sin forzar datos o funciones ajenas al resultado. |

[Modelo de independencia y alternativas de experiencia](/home/marc/Documentos/Proyects/Tordo.Platform/docs/portfolio/productos-independientes-2026-11-14/MODELO_DE_INDEPENDENCIA.md)
