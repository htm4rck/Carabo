# Carabo: comparación de rendimiento con carga mínima

La implementación actual mejoró dos flujos concretos: el listado de productores y la exportación Excel grande. La separación arquitectónica por sí sola no produjo una aceleración general. El reporte de ventas mostró un aumento pequeño de latencia que queda registrado; los documentos y el Excel pequeño permanecieron próximos a la versión anterior.

Se ejecutaron **512 peticiones medidas, sin errores HTTP**, en dos rondas por versión. El pico observado de RAM del proceso Java pasó de **397,7 a 324,9 MiB**, una reducción de **72,8 MiB / 18,3%** en esta carga. Son medidas de réplicas con límites iguales y datos sintéticos; no corresponden a la RAM de las instalaciones existentes. Los cambios locales todavía no se desplegaron.

## Comparación

Los tiempos son medianas de las observaciones originales agrupadas de ambas rondas. Menos tiempo es mejor. Se usaron cuatro clientes espaciados, hasta cuatro peticiones por segundo, para las lecturas; Excel se probó con un cliente y una exportación cada dos segundos como máximo.

| Operación | Muestras por versión | Anterior | Actual | Cambio observado |
|---|---:|---:|---:|---:|
| Productores: 20 integraciones, 4 clientes | 64 | 83,7 ms | 10,1 ms | **88,0% menos latencia** |
| Ventas: 3.000 documentos, 4 clientes | 64 | 7,6 ms | 9,4 ms | 1,8 ms más / 22,8% |
| Documentos: página de 50, 4 clientes | 64 | 15,9 ms | 16,5 ms | 0,6 ms más / 4,1% |
| Excel: 200 documentos, 1 cliente | 8 | 146,2 ms | 151,5 ms | 5,3 ms más / 3,6% |
| Excel: 3.000 documentos, 1 cliente | 8 | 1.684,4 ms | 1.075,3 ms | **36,2% menos latencia** |

La mejora de productores aparece en las dos rondas: medianas anteriores de 81,6 y 110,2 ms frente a 9,7 y 10,8 ms actuales. Excel grande también mejoró en ambas: 1.478,4 y 1.719,5 ms anteriores frente a 980,6 y 1.209,8 ms actuales. La variación entre rondas impide tratar estos porcentajes como una garantía de producción.

| Operación | P95 anterior | P95 actual | Interpretación |
|---|---:|---:|---|
| Productores, 4 clientes | 139,2 ms | 13,5 ms | Reducción clara del coste del flujo. |
| Ventas, 4 clientes | 9,1 ms | 15,6 ms | Aumento observado; no hay mejora demostrada en este flujo. |
| Documentos, 4 clientes | 18,5 ms | 20,3 ms | Diferencia absoluta pequeña bajo esta carga. |
| Excel, 200 documentos | 240,3 ms | 227,8 ms | Solo ocho muestras; no permite establecer una mejora de cola. |
| Excel, 3.000 documentos | 2.298,0 ms | 1.531,6 ms | Mejor en esta muestra; P95 poco estable con ocho exportaciones. |

La regresión pequeña de ventas no se explica por más consultas: ambas versiones hacen dos en esta sesión de prueba. La extracción de reglas no cambió la consulta agregada. Se registra el resultado sin atribuirle una causa que la prueba no demuestra; conviene seguirlo al hacer la siguiente modificación. El tiempo absoluto continúa por debajo de 16 ms en el P95 de esta prueba.

## Qué explica las mejoras

**Productores.** El flujo anterior hacía una consulta de listado, veinte conteos y veinte búsquedas de última fecha, además de la consulta de membresía de esta sesión: **42 consultas por petición**. El actual hace una agregación y la misma membresía: **2 consultas**. El tiempo acumulado en PostgreSQL pasó de **59,40 a 2,70 ms por petición**, promediado en las fases con cuatro clientes. Las consultas de negocio pasan de 41 a 1. Esto explica la mayor parte de la mejora; no depende de cambiar Quarkus.

**Excel grande.** La versión anterior cargaba entidades completas y construía el libro completo con XSSF. La actual consulta proyecciones con cursor y utiliza SXSSF con ventana de cien filas. El tiempo SQL por exportación grande pasó de aproximadamente **23,6 a 6,7 ms**; el resto del tiempo se consume principalmente construyendo el archivo. En las ventanas instrumentadas de todas las fases, las pausas GC acumuladas pasaron de aproximadamente **3,19 s a 0,34 s**. Se observaron ocho recolecciones mayores antes y ninguna en la versión actual. Estos resultados corresponden al heap de 384 MiB y al colector elegido por esa JVM bajo los límites de laboratorio; no justifican imponer ese heap a los despliegues existentes.

**Lecturas de documentos.** El registro de actividad del productor pasó de buscar la entidad y sobrescribir sus campos a un incremento SQL atómico. Las llamadas SQL por petición bajaron de cinco a cuatro, pero no apareció una reducción de latencia del endpoint. El beneficio de consistencia bajo concurrencia está cubierto por las pruebas anteriores; no se presenta como una aceleración HTTP demostrada aquí.

## Cuellos de botella y mejoras propuestas

| Prioridad | Hallazgo | Evidencia | Acción propuesta |
|---|---|---|---|
| 1 | Construcción y cálculo de anchos de Excel | Aun con cursor, 3.000 filas necesitan aproximadamente 1–1,2 s. JFR muestra trabajo en POI y tipografías; las pilas con `sun.font` representan cerca del 35% del peso estimado de asignación muestreada durante esas exportaciones actuales. | Comparar anchos predefinidos o cálculo limitado a una muestra de filas. Conservar encabezados, valores y legibilidad. Es el candidato principal para reducir CPU y asignaciones temporales del Excel. |
| 2 | Listado de documentos ordena filas que luego descarta | La consulta representativa lee 3.000 filas y ordena para devolver 50. El plan actual usa `Seq Scan` + `Sort`; el índice por estado no cubre el orden por creación. | Añadir mediante migración un índice `(business_code, created_at DESC)` después de comprobar también los filtros habituales. El prototipo se probó exclusivamente en la base efímera. |
| 3 | Exportación HTTP conserva el archivo final completo | El cursor y SXSSF acotan filas y entidades, pero `ByteArrayOutputStream` y el retorno `byte[]` mantienen y copian el XLSX final en heap. | Evaluar escritura directa a la respuesta o un archivo temporal y establecer un límite explícito de exportaciones simultáneas. No se midieron exportaciones concurrentes ni el máximo de filas soportado. |
| 4 | Cada lectura de máquina genera una escritura de actividad | El flujo actual de documentos incluye autenticación, conteo, página e incremento SQL de actividad. Es una escritura y su commit por cada lectura. | Mantener el incremento atómico. Si el volumen sube, separar lo que debe auditarse de lo que puede agregarse por intervalo, definiendo antes la tolerancia a pérdidas. Con cuatro peticiones/s no se observó saturación por este mecanismo. |

El índice candidato cambió el plan de la consulta representativa de documentos a `Index Scan`, leyendo cincuenta filas directamente. Una ejecución de `EXPLAIN ANALYZE` pasó de **3,813 a 0,109 ms**, y de 512 a 9 accesos a bloques ya presentes en caché. Es evidencia del cambio de plan en 3.000 documentos; **no equivale a prometer esa reducción en el tiempo HTTP**. La consulta explicada selecciona los campos representativos del listado, mientras que la API todavía carga la entidad completa. El índice se eliminó antes de destruir la base efímera y no se modificó el esquema de ninguna instalación.

La agregación de ventas tarda unos 2,2 ms en PostgreSQL con este volumen. Aunque su plan lee las filas del periodo, esta prueba no muestra una necesidad de caché, más workers o más conexiones. Primero conviene resolver los costes comprobados de Excel y del listado.

Los porcentajes de JFR se obtienen de muestras ponderadas de asignación, no de bytes retenidos ni de porcentajes exactos de CPU. Las categorías de pilas pueden solaparse. El resumen separado conserva los tamaños de muestra: 3.096 eventos de asignación y solo 25 muestras de ejecución en las ventanas de Excel grande actuales. No se utilizan esas 25 muestras para presentar una distribución precisa de CPU.

## Condiciones y protección del servidor

- Referencia: imagen local `ghcr.io/htm4rck/carabo:244d2c6974d202c8844e5ec3c920a2baf86ed7e2`, ID `sha256:f4310f2a02e2d78ed0a483997f2db7054e012af623faa8752847d96c22f11d53`. Se comprobó que coincide con la imagen de Carabo DEV en uso.
- Actual: artefactos locales de `codex/carabo-business-architecture`, montados sobre la misma imagen para conservar sistema operativo y JVM. Las huellas SHA-256 de los JAR están en el resultado original.
- Ambas: Java Temurin 21.0.13, Quarkus 3.18.4, una API a la vez, 1 CPU, 768 MiB de contenedor, `-Xms64m -Xmx384m`, dos procesadores visibles para Java y la misma instrumentación JFR/GC.
- Dependencias exclusivas del ensayo: PostgreSQL 16 con 0,5 CPU / 256 MiB y RabbitMQ 3 con 0,25 CPU / 256 MiB. Total máximo de contenedores: 1,75 CPU y 1,25 GiB de RAM.
- Datos sintéticos: 3.000 documentos aceptados de una empresa, 300 de otra y veinte productores. Enero contiene los 3.000 documentos; dos días contienen 200. No se copiaron datos de las bases existentes.
- Autenticación humana: JWT firmado localmente y membresía verificada en SQL, con licencia root de prueba. Un usuario normal añade la consulta de roles que esta sesión omite. Las consultas de documentos usan una credencial de máquina del fixture. No se incluyó latencia de Kong, TLS, Cloudflare o del navegador.
- Dos rondas con orden anterior → actual y actual → anterior. Calentamiento equivalente de consultas y ambos tamaños de Excel antes de medir. Clientes con conexiones persistentes, solicitudes espaciadas y máximo de cuatro clientes; sin ráfagas masivas.
- Corte si la memoria disponible del host baja de 3 GiB, si el uso de swap supera 256 MiB, si la carga media supera el umbral del script o si la API supera 700 MiB de memoria de cgroup. También se detiene al detectar un error de petición. No se activó ninguno de esos cortes; se observó swap en cero.
- Red Docker interna, sin puertos publicados ni acceso de esas réplicas a las redes de las aplicaciones existentes. Solo se consultaron endpoints de lectura; las escrituras de actividad quedaron en la base efímera. No se emitieron documentos, correos ni llamadas a SUNAT/OSE/Layout.

La memoria disponible observada antes de comenzar fue de 8.741 MiB y al terminar las fases de 7.883 MiB; la carga media de un minuto pasó de 2,05 a 2,13. Es información del host compartido, no una atribución del cambio global a este ensayo. Se eliminaron los contenedores y la red al finalizar.

Esta prueba identifica costes bajo carga pequeña. No mide la capacidad máxima, la emisión fiscal, las colas con mensajes, los PDFs ni el efecto de cuatro exportaciones simultáneas. Tampoco demuestra una reducción general del heap en reposo de Carabo: el pico comparado incluye calentamiento de clases y varias exportaciones.

## Reproducción y datos

Desde la raíz de Carabo, con Docker, Python 3, openssl y el JDK 21 disponibles:

```sh
cd apps/api
./gradlew :carabo-starter-quarkus:quarkusBuild --no-daemon
cd ../..
python3 scripts/performance/minimal_load.py --output /tmp/carabo-carga-nueva
python3 scripts/performance/summarize.py /tmp/carabo-carga-nueva/results.json
```

La carpeta de salida debe ser nueva. El script crea sus propios recursos y siempre ejecuta su limpieza; no acepta una URL de servidor existente. En otra máquina hace falta disponer de la imagen de referencia o descargar esa etiqueta fijada a commit.

Se conservan [las observaciones originales y los planes SQL](results.json), [el resumen de perfiles JFR](profile-summary.json) y [la tabla agregada generada](summary.md). Los JFR y logs completos de esta ejecución quedaron en `/tmp/carabo-load-20261106-v5/`; los logs contienen únicamente datos sintéticos. Las pruebas de carga de esta entrega no modificaron código de producción ni desplegaron la rama.
