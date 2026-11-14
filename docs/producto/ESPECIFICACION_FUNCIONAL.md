# Carabo especificación funcional integral

Carabo debe completar los resultados siguientes por modalidad contratada. Cada resultado relaciona tarea, proceso, funciones, excepciones, indicador y aceptación. Los backends y bases internos se consideran disponibles; la brecha es el recorrido de usuario, el dato o el contrato que falta completar.

[Alcance comercial](ALCANCE_COMERCIAL.md) · [Especificación funcional](ESPECIFICACION_FUNCIONAL.md) · [Gaps](GAPS_PRODUCTO_INDEPENDIENTE.md) · [Inventario CSV](gaps-2026-11-14.csv) · [Entradas y salidas](contratos-2026-11-14.csv)

Se conservan 8 capacidades del catálogo y 15 gaps anteriores. Se añaden 7 habilitantes de producto: 22 registros locales, más las dependencias compartidas IND sin contarlas otra vez como gaps propios. Los estados de desarrollo, prueba y aceptación son distintos.


## Capacidades mínimas para operar de forma autónoma

**Alcance funcional confirmado por Marc.** La aplicación debe completar el núcleo y las capacidades de apoyo de su perfil standalone. Aquila está incluido para administración transversal. Los backends y bases compartidos se consideran disponibles.

Validar, firmar, emitir, consultar, corregir, custodiar y entregar documentos conserva su alcance. Aquila proporciona administración transversal. Carabo puede contratarse standalone o incluirse con Amazilia/BirdPort.

| Requisito y capacidad | Alcance mínimo necesario | Disponibilidad | Gaps de desarrollo y aceptación |
|---|---|---|---|
| CA-AUT-01 Administración y configuración transversal; [CAP-128](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-128%20Portal%20administrativo%20y%20permisos.md) | Usar Aquila para la organización, usuarios/permisos y configuración general que corresponda; no duplicar esas pantallas en el producto. Acceso limitado por empresa, rol y recurso. | Incluida: Aquila, todos los modos | IND-01; CAB-PROD-01/05 |
| CA-AUT-02 Proceso fiscal y expediente; [CAP-098](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-098%20Impuestos%20y%20posiciones%20fiscales.md), [CAP-100](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-100%20Emision%20electronica.md), [CAP-104](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-104%20Expediente%20documental%20y%20firma.md) | Validar, firmar, enviar, consultar resultado y entregar artefactos del perfil fiscal aceptado; configuración específica del emisor conserva su interfaz de dominio. | Núcleo del proceso, todos los modos | CAB-098/100/104; CAB-PROD-06 |
| CA-AUT-03 Captura manual sin productor externo; [CAP-098](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-098%20Impuestos%20y%20posiciones%20fiscales.md), [CAP-100](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-100%20Emision%20electronica.md) | Capturar receptor, líneas, unidades/impuestos y referencia del documento desde Carabo. No necesita compras, stock, otro POS o un catálogo completo. Cuando viene incluido con Amazilia/BirdPort, éstos siguen siendo productores. | Apoyo adicional: sólo standalone | CAB-PROD-05/07; CAB-098-01 |
| CA-AUT-04 Numeración e intención manual; [CAP-100](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-100%20Emision%20electronica.md), [CAP-008](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-008%20Idempotencia%20de%20comandos.md), [CAP-009](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-009%20Recuperacion%20de%20respuesta%20perdida.md) | Resolver serie/correlativo bajo autoridad explícita y conservar la intención tras recarga. El perfil integrado respeta numeración/identidad del productor y no habilita un contador paralelo. | Apoyo adicional: sólo standalone | CAB-PROD-07; CAB-100-01 |
| CA-AUT-05 Datos reutilizables mínimos de captura; [CAP-098](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-098%20Impuestos%20y%20posiciones%20fiscales.md), [CAP-104](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-104%20Expediente%20documental%20y%20firma.md) | Permitir capturar o reutilizar datos mínimos del receptor y conceptos sin obligar a gestionar un inventario. Captura libre validada basta cuando no se necesita un maestro completo. | Apoyo adicional: sólo standalone | CAB-PROD-02/05/07 |
| CA-AUT-06 Notas, consulta y salida documental; [CAP-036](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-036%20Notas%20de%20credito.md), [CAP-101](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-101%20Importacion%20de%20comprobante%20externo.md), [CAP-102](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-102%20Libros%20y%20reportes%20regulatorios.md), [CAP-104](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-104%20Expediente%20documental%20y%20firma.md) | Corregir o reconocer documentos según contrato, consultar/exportar estados y expedientes con permisos y referencias. Emisión externa no se reenvía y exportación no acredita un libro regulatorio no aceptado. | Núcleo del proceso, todos los modos | CAB-036/101/102/104; CAB-PROD-03/04/06 |
| CA-AUT-07 Guías y proveedor específico; [CAP-103](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-103%20Guias%20de%20remision.md), [CAP-100](/opt/tordo-platform/data/tecolyt/wiki/Tordo/Catalogos/Capacidades/TORDO-CAP-100%20Emision%20electronica.md) | GRE, OSE comercial y regímenes adicionales sólo cuando formen parte del proceso contratado y estén aceptados; no son requisito universal del facturador básico. | Según proceso/mercadería contratado | CAB-103; CAB-100-02 |

Las filas de apoyo son las extensiones visibles exclusivas de standalone. En modo integrado se consumen los datos de su autoridad por el contrato de API/archivo; no se habilita otro escritor local por la sola disponibilidad de los servicios. Las inclusiones Aquila permanecen disponibles según rol en ambas modalidades.

La compra autónoma cubre esta oferta compuesta; no obliga a contratar todo otro frontal para mantener sus datos básicos. Los procesos adicionales conservan sus fases y controles. Esta incorporación al alcance no acredita que cada función esté implementada o aceptada: su estado sigue en las capacidades y gaps enlazados.

## Conectar el sistema que ya vende

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B01 / CA-AC01 |
| Pregunta del usuario | ¿Puedo emitir sin reemplazar mi ERP, POS o ecommerce? |
| Proceso | Alta de productor → validar documento → admitir identidad → emitir por ruta acordada → devolver resultado. |
| Capacidades y frontera | CAP-100/098 |
| Funciones | Credencial de máquina/scopes; documento comercial y numeración del origen; referencias estables. |
| Excepciones que deben resolverse | Productor revocado, payload inválido, tenant ajeno y repetición simultánea. |
| Indicadores con fuente/corte | Documentos admitidos/rechazados y pendientes por productor. |
| Base actual y estado | Validación/ingestión/consulta y productores existentes; conector concreto y homologación pendientes. |
| Aceptación verificable | ERP conserva su venta y un único correlativo; replay devuelve la misma emisión y ningún consumidor necesita usuario humano de servicio. |

## Emitir manualmente cuando no existe otro productor

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B02 / CA-AC02 |
| Pregunta del usuario | ¿Puedo crear un comprobante desde Carabo sin comprar otro frontal? |
| Proceso | Formulario → receptor/líneas/impuestos → numeración autorizada → confirmar intención durable → emitir/consultar. |
| Capacidades y frontera | CAP-100/098/104 |
| Funciones | Editor completo y productor manual incluido; maestro breve opcional, sin exigir inventario ni POS. |
| Excepciones que deben resolverse | Número omitido, serie incorrecta, recarga tras envío y nuevo intento sobre resultado incierto. |
| Indicadores con fuente/corte | Tiempo de captura; intenciones manuales sin conciliar. |
| Base actual y estado | Hallazgo estático: formulario no envía number, EmitApi no lo añade y API lo exige; numeración manual delegada no cerrada. |
| Aceptación verificable | Factura/boleta/nota del perfil aceptado se crea sin otro producto; correlativo válido e intención sobreviven recarga sin otra emisión. |

## Validar, firmar y entregar el documento

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B03 / CA-AC03 |
| Pregunta del usuario | ¿El documento cumple el contrato fiscal habilitado? |
| Proceso | Normalización → validación → generación UBL → firma → envío → ticket/CDR. |
| Capacidades y frontera | CAP-098/099/100/104 |
| Funciones | Tipos/afectaciones/proveedor delimitados; certificado vigente; impuestos y descuentos conservados. |
| Excepciones que deben resolverse | Datos faltantes, certificado vencido, rechazo y proveedor no homologado. |
| Indicadores con fuente/corte | Rechazos por causa; certificados próximos a vencer. |
| Base actual y estado | UBL/firma/canales y pruebas existentes; matriz externa por tenant/tipo/canal pendiente. |
| Aceptación verificable | Importes de origen y XML concilian en los casos habilitados; canal o régimen no calificado no se presenta como soportado. |

## Conocer y recuperar el resultado fiscal

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B04 / CA-AC04 |
| Pregunta del usuario | ¿Se aceptó, se rechazó o todavía debo consultar? |
| Proceso | Admisión → procesamiento → consulta/callback → resultado final → resolución autorizada. |
| Capacidades y frontera | CAP-100/009/011 |
| Funciones | Estados técnicos y fiscales distintos; referencia estable, ticket y outbox con recuperación. |
| Excepciones que deben resolverse | Caída de broker, doble worker, timeout después del envío y callback fallido. |
| Indicadores con fuente/corte | Antigüedad de pendiente; entregas de callback y DLQ. |
| Base actual y estado | Estados/reintentos/outbox existentes; resiliencia y aceptación externa mantienen gaps. |
| Aceptación verificable | 202 o ticket no se anuncia aceptado; tras pérdida de respuesta se consulta misma identidad y el productor recibe un resultado trazable. |

## Corregir y reconocer documentos externos

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B05 / CA-AC05 |
| Pregunta del usuario | ¿Puedo corregir o incorporar un comprobante sin emitirlo dos veces? |
| Proceso | Original validado → nota/corrección permitida o importación externa → vínculo y estado → comunicación al productor. |
| Capacidades y frontera | CAP-036/101/100 |
| Funciones | Distinguir documento ya emitido de solicitud por emitir; notas referencian original y numeración propia. |
| Excepciones que deben resolverse | Original ajeno, XML/CDR incompletos, nota parcial repetida y autoridad cambiada. |
| Indicadores con fuente/corte | Documentos externos incompletos; correcciones pendientes. |
| Base actual y estado | Notas y contratos existentes; importación validada y rutas externas mantienen P0. |
| Aceptación verificable | Documento externo validado no dispara envío; nota conserva original y resultado sin mover inventario ni reembolsar por inferencia. |

## Emitir guías para el traslado contratado

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B06 / CA-AC06 |
| Pregunta del usuario | ¿Puedo acompañar el movimiento con la guía correcta? |
| Proceso | Datos de traslado → numeración exclusiva → envío → ticket/CDR → expediente enlazado. |
| Capacidades y frontera | CAP-103 |
| Funciones | Origen/destino, transportista/conductor/vehículo, partidas y emisor; contador local o número externo según contrato aceptado. |
| Excepciones que deben resolverse | ERP ya numeró, envío incierto, dos workers, varias entregas y traslado modificado. |
| Indicadores con fuente/corte | Guías pendientes/rechazadas; entregas sin expediente completo. |
| Base actual y estado | GRE/ticket/preflight existentes; contrato número externo y aceptación del canal pendientes. |
| Aceptación verificable | Guía del perfil conserva un número/estado y no descuenta stock; repetir envío o recibir mercadería no origina otra guía automáticamente. |

## Conservar y entregar el expediente

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B07 / CA-AC07 |
| Pregunta del usuario | ¿Puedo recuperar los archivos y entregarlos al cliente? |
| Proceso | Resultado → XML/CDR/PDF → custodia → descarga/correo → restauración y verificación. |
| Capacidades y frontera | CAP-104/100 |
| Funciones | Artefactos protegidos, integridad, plantilla y destinatario; servicios de PDF/correo incluidos si se prometen. |
| Excepciones que deben resolverse | Archivo ausente, link revocado, correo fallido, certificado rotado y restore parcial. |
| Indicadores con fuente/corte | Expedientes incompletos; fallos de entrega y restauración. |
| Base actual y estado | Almacenamiento y mecanismos existentes; retención/restore/Layout/Mailer exigen aceptación. |
| Aceptación verificable | Restaurar BD+artefactos+referencias recupera el expediente del tenant correcto; un fallo de correo no cambia el resultado fiscal. |

## Conciliar documentos con administración y otros sistemas

| Elemento | Definición |
|---|---|
| Resultado y criterio | CA-B08 / CA-AC08 |
| Pregunta del usuario | ¿Puedo llevar mis documentos y estados al sistema que conserva mis libros? |
| Proceso | Seleccionar corte → exportar detalle/artefactos → entregar → recibir aceptación/rechazos → conciliar notas/bajas. |
| Capacidades y frontera | CAP-102/100/101/104 |
| Funciones | Reporte de intercambio completo por productor/periodo/moneda; API o archivo estructurado. |
| Excepciones que deben resolverse | Paginación, corte cambiado, nota tardía y registro repetido. |
| Indicadores con fuente/corte | Documentos/importes por estado; diferencias con el receptor. |
| Base actual y estado | Reportes/XLSX existentes; contrato externo completo y libros regulatorios son alcances distintos. |
| Aceptación verificable | ERP o contador concilia todos los documentos y notas con el reporte y sus archivos; exportar no acredita un libro regulatorio no habilitado. |

## Tareas mínimas y alternativas de experiencia

| Decisión de experiencia | Análisis |
|---|---|
| Mínimo que debe resolver | Configurar emisor y capturar receptor/líneas/identidad fiscal o recibirlos del productor; emitir, consultar, corregir y entregar/exportar artefactos. |
| Base observada | El formulario manual omite number mientras el backend lo exige. La API externa tiene contratos de productor; eso no completa automáticamente el recorrido humano. |
| Opción preferida | Cerrar el productor manual dentro de Carabo si se ofrece facturador con UI; para clientes API conservar el productor existente. No añadir inventario ni compras. |
| Cuándo sirve otro frontal | No se justifica entregar otro frontal para capturar un comprobante básico; resolver editor/numeración/recuperación en Carabo. |
| Cliente con sistemas existentes | API con ERP/POS existente o importación de archivo con vista previa e intención explícita; retorno de estados/artefactos y reporte conciliable. |
| Estado de la elección | Recomendación: completar el recorrido fiscal propio; separar modalidad manual y API sin forzar datos o funciones ajenas al resultado. |

El acceso a una función de otro dominio no traslada su autoridad. Componer una tarea en la UI principal puede consumir el mismo servicio; un acceso complementario es una alternativa por evaluar cuando el recorrido lo justifique. No se decide crear otro sistema ni regalar una aplicación completa.

## Entradas y salidas con otros sistemas

Los nombres V1 siguientes son propuestas de contrato, no endpoints o importadores entregados. Cada interfaz declara datos, autoridad, frecuencia y confirmación; su CSV permite preparar el caso de un cliente concreto.

### Documento comercial por emitir

| Campo | Contrato del perfil |
|---|---|
| Identidad y dirección | CA-I01; Productor ERP/POS/usuario → Carabo |
| Datos mínimos | Tipo, serie/número, fecha/moneda, receptor, líneas/unidades/afectaciones, referencia original si aplica e IDs de origen/intención. |
| Mecanismo | REST validación/ingestión; productor manual tras cerrar editor/numeración; sin inventario obligatorio. |
| Frecuencia y vigencia | Por documento; credencial y contrato por productor/ambiente. |
| Autoridad | Productor asigna correlativo CPE actual; Carabo emite sólo flujo delegado. |
| Confirmación y conciliación | Resultado y artefactos retornan por identidad estable; repetir no emite otro. |
| Base actual | API existente; formulario manual carece de number y requiere cierre. |

### Documento ya emitido por otro sistema

| Campo | Contrato del perfil |
|---|---|
| Identidad y dirección | CA-I02; ERP/emisor externo → Carabo/proyección |
| Datos mínimos | Identidad fiscal, emisor, fecha/moneda/importes, XML/CDR/hash/estado y procedencia confiable. |
| Mecanismo | Importación validada distinta de ingestión para emitir; contrato de CAB-101. |
| Frecuencia y vigencia | Tras emisión externa y correcciones de estado autorizadas. |
| Autoridad | Emisor externo conserva autoridad; Carabo consulta/custodia el alcance acordado. |
| Confirmación y conciliación | Ningún worker reenvía documento importado como solicitud nueva; artefactos ausentes se declaran. |
| Base actual | CAB-101 abierto; existencia de una API key no acredita esta importación. |

### Estados, artefactos y reconciliación

| Campo | Contrato del perfil |
|---|---|
| Identidad y dirección | CA-I03; Carabo → productor; productor → consulta |
| Datos mínimos | externalId/sourceDocId, documento fiscal, estado/ticket, errores y referencias a XML/CDR/PDF; revisión/fecha. |
| Mecanismo | Callback y consulta autenticada; archivo de estado como contingencia operativa conciliada. |
| Frecuencia y vigencia | Hasta terminal; consulta sigue disponible si falla callback. |
| Autoridad | Carabo conserva resultado fiscal; productor conserva estado de su documento. |
| Confirmación y conciliación | ACK técnico no se confunde con aceptación; callback repetido actualiza una referencia sin otro efecto. |
| Base actual | Consulta/outbox existentes; receptor, firma y resiliencia por aceptar. |

### Intercambio por archivos con un sistema sin API

| Campo | Contrato del perfil |
|---|---|
| Identidad y dirección | CA-I04; Cliente → Carabo; Carabo → cliente/contador |
| Datos mínimos | Lote/hash/esquema, documento/línea, identidad fiscal, importes, resultado/errores y manifiesto de artefactos. |
| Mecanismo | Archivo estructurado propuesto CARABO_DOCUMENTOS_V1; vista previa y confirmación explícita antes de emitir. |
| Frecuencia y vigencia | Lote y corte acordados; fiscalidad se confirma por resultado, no por hora de exportación. |
| Autoridad | Productor conserva origen/numero; Carabo sólo emite lo autorizado. |
| Confirmación y conciliación | Mismo archivo/intención no reemite; cambiar contenido de misma identidad genera conflicto; exportación íntegra concilia. |
| Base actual | XLSX/reportes existentes; archivo de ingreso estructurado y ciclo de receptor no acreditados. |

### Datos de guía y vínculo a traslado

| Campo | Contrato del perfil |
|---|---|
| Identidad y dirección | CA-I05; ERP/operador → Carabo → origen |
| Datos mínimos | Origen/destino, partidas/unidades, motivo, transporte/actor, serie y número según autoridad, traslado/evento y resultado. |
| Mecanismo | API GRE; ampliar contrato para número externo si ese perfil se habilita. |
| Frecuencia y vigencia | En cada traslado/solicitud; polling hasta resultado. |
| Autoridad | Un contador/emisor por serie; operador confirma movimiento físico. |
| Confirmación y conciliación | Número ERP se conserva cuando el contrato lo permita; nunca mezclar dos contadores ni crear salida de stock desde Carabo. |
| Base actual | GRE actual asigna número; recepción de GRE ya numerada requiere ampliación/homologación. |

### Salida documental y reportes

| Campo | Contrato del perfil |
|---|---|
| Identidad y dirección | CA-I06; Carabo → productor/contador/cliente |
| Datos mínimos | Periodo/corte, emisor/tipo/serie/número, identidad origen, estados, base/impuestos/total/moneda, notas/bajas y hashes/artefactos. |
| Mecanismo | API y reporte propuesto CARABO_ESTADOS_V1; exportación completa con descarga autorizada. |
| Frecuencia y vigencia | Corte acordado con ajustes posteriores identificados y acceso bajo retención pactada. |
| Autoridad | Carabo acredita resultado; autoridad contable mantiene libros. |
| Confirmación y conciliación | Todos los documentos/notas por moneda/estado cuadran con detalle y archivos; no se anuncia libro regulatorio por exportar XLSX. |
| Base actual | Reportes/custodia existentes; contrato externo y aceptación completa pendientes. |

## Reglas de incorporación y operación

- La tarea cotidiana debe poder completarse desde el recorrido contratado, con contexto, permisos y retorno claros. Configuración asistida es válida cuando el contrato la incluye.
- Cada dato tiene fuente, identidad y corte; cada efecto tiene un escritor por ámbito/vigencia. Servicio interno disponible no significa integración ya calificada con un ERP ajeno.
- Captura propia, API y archivo son alternativas por perfil. No se exige implementar todos los conectores a todos los clientes para aceptar un perfil nativo.
- Importación distingue apertura, snapshot, delta, corrección y reverso. Repetir no duplica; conflicto y error por fila son explícitos.
- Exportación contiene todo el universo autorizado, detalle y totales por moneda/estado, esquema, fecha de corte e identidad. Exportado, recibido, aplicado y conciliado son estados distintos.
- Archivo posterior no confirma última unidad compartida, cargo, cupo o emisión online. Sin esa confirmación se delimita consulta, lote o ámbito delegado.
- Datos ausentes no se convierten en ceros ni estados exitosos. Los reintentos mantienen identidad, contenido y destino.
- Cambiar modalidad o ampliar alcance reutiliza datos e históricos; resuelve pendientes y conserva la procedencia.

## Fases y aceptación

| Etapa | Resultado |
|---|---|
| E0 Delimitar | Tareas y datos del cliente; autoridad; modalidad; alternativa de experiencia y receptor. |
| E1 Operación propia | Alta/importación y ciclo nativo completo con mantenimiento mínimo en el recorrido del producto. |
| E2 Convivencia | Primer conector o archivos del receptor real con resultado de vuelta, corte y conciliación. |
| E3 Excepciones y soporte | Permisos, error, repetición, reinicio/concurrencia, cambios de autoridad y salida de datos. |
| E4 Aceptación | Versión/ambiente/perfil, casos/documentos, responsable nominal y evidencia. Los perfiles no ejercitados siguen abiertos. |

[Modelo de independencia y alternativas de experiencia](/home/marc/Documentos/Proyects/Tordo.Platform/docs/portfolio/productos-independientes-2026-11-14/MODELO_DE_INDEPENDENCIA.md)
