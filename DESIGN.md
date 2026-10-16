---
name: Carabo
description: Facturación electrónica con una ruta fiscal visible, segura y accionable.
colors:
  signal: "#80EF80"
  accent: "#8CBD8C"
  accent-strong: "#527852"
  sage: "#768A76"
  forest: "#173317"
  surface: "#FFFFFF"
  ground: "#F3F7F3"
  border: "#D8E3D9"
  muted: "#687A6B"
  warning: "#B9793F"
  critical: "#B55B50"
typography:
  display:
    fontFamily: "Lato, sans-serif"
    fontSize: "clamp(42px, 5vw, 72px)"
    fontWeight: 900
    lineHeight: 1.02
    letterSpacing: "-0.045em"
  headline:
    fontFamily: "Lato, sans-serif"
    fontSize: "30px"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.035em"
  title:
    fontFamily: "Lato, sans-serif"
    fontSize: "18px"
    fontWeight: 700
    lineHeight: 1.25
  body:
    fontFamily: "Lato, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.8
  label:
    fontFamily: "Lato, sans-serif"
    fontSize: "11px"
    fontWeight: 800
    lineHeight: 1.2
    letterSpacing: "0.07em"
rounded:
  xs: "5px"
  sm: "6px"
  md: "7px"
  lg: "9px"
  xl: "10px"
  full: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
components:
  button-next-action:
    backgroundColor: "{colors.signal}"
    textColor: "{colors.forest}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "13px 16px 13px 19px"
  button-structural:
    backgroundColor: "{colors.forest}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "0 20px"
    height: "52px"
  input-default:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.forest}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "0 14px"
    height: "50px"
  nav-active:
    backgroundColor: "{colors.signal}"
    textColor: "{colors.forest}"
    rounded: "{rounded.md}"
    padding: "10px 12px"
    height: "44px"
  metric-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.forest}"
    rounded: "{rounded.lg}"
    padding: "17px 18px"
  status-accepted:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.forest}"
    rounded: "{rounded.full}"
    size: "7px"
  fiscal-route-node:
    backgroundColor: "{colors.signal}"
    textColor: "{colors.forest}"
    rounded: "{rounded.full}"
    size: "34px"
  business-switcher:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.forest}"
    rounded: "{rounded.md}"
    padding: "0 10px"
    height: "42px"
---

# Design System: Carabo

## Overview

**Creative North Star: "La Ruta Fiscal Confiable"**

Carabo convierte una operación tributaria compleja en un recorrido legible y verificable. Su identidad se apoya en estructura verde bosque, superficies blancas y una jerarquía tipográfica precisa; el resultado debe sentirse profesional, sobrio y listo para operar, nunca como una plantilla administrativa genérica.

La ruta Emisión → Firma → SUNAT → CDR es el principio de wayfinding del sistema. La interfaz hace visible dónde está cada documento, qué ha ocurrido y cuál es la siguiente acción segura. La densidad favorece el escaneo rápido, mientras la profundidad ambiental ligera separa capas sin volver decorativa la herramienta.

**Key Characteristics:**

- Jerarquía fiscal explícita y accionable.
- Lato como voz única, directa y altamente legible.
- Superficies claras contenidas por estructura verde bosque.
- Verde señal escaso, reservado para progreso, selección y siguiente acción.
- Componentes compactos, precisos y con estados inequívocos.

## Colors

La paleta combina autoridad forestal y neutrales verdosos con una señal luminosa deliberadamente escasa.

### Primary

- **Verde Señal:** marca progreso confirmado, selección activa y la siguiente acción fiscal segura; no funciona como decoración general.
- **Bosque Estructural:** sostiene navegación, titulares, paneles de ruta y acciones de acceso donde se necesita máxima autoridad.

### Secondary

- **Verde Operativo:** comunica estados favorables, iconos de apoyo y focos de campo sin competir con la siguiente acción.
- **Verde Operativo Fuerte:** sirve para enlaces funcionales, iconos informativos y texto interactivo secundario.
- **Salvia de Espera:** representa pasos aún no completados, metadatos y estados secundarios.

### Tertiary

- **Ámbar de Atención:** se usa para documentos pendientes o incidencias que requieren revisión, siempre junto con texto o iconografía.
- **Rojo de Incidencia:** se reserva para rechazo, error y cierre de sesión en hover; nunca es una variante de marca.

### Neutral

- **Superficie Blanca:** base de tarjetas, formularios, cabecera y paneles de trabajo.
- **Suelo Verde Pálido:** fondo de aplicación que separa suavemente las superficies.
- **Borde Botánico:** divide tablas, campos y contenedores sin endurecer la composición.
- **Texto Musgo:** sostiene párrafos, metadatos y etiquetas de menor jerarquía.

### Named Rules

**The Signal Has a Job Rule.** El verde señal solo indica progreso, selección o siguiente acción; si no cambia una decisión operativa, no debe aparecer.

**The State Is More Than Color Rule.** Todo estado fiscal combina color con texto, número, forma o icono para conservar significado sin percepción cromática.

## Typography

**Display Font:** Lato (con fallback sans-serif)  
**Body Font:** Lato (con fallback sans-serif)  
**Label Font:** Lato (con fallback sans-serif)

**Character:** Una sola familia evita teatralidad y mantiene continuidad entre cifras, formularios y estados fiscales. El carácter nace del contraste entre titulares compactos, cuerpo respirado y etiquetas técnicas densas.

### Hierarchy

- **Display** (900, escala fluida, interlineado 1.02): reservado al mensaje principal del acceso y otras presentaciones excepcionales de marca.
- **Headline** (700, 30px, interlineado 1.15): encabeza la tarea o el estado principal de cada pantalla; baja a 25px en móvil.
- **Title** (700, 18px, interlineado 1.25): nombra paneles, acciones y agrupaciones operativas.
- **Body** (400, 15px, interlineado 1.8): explica contexto y consecuencias con una longitud de lectura contenida.
- **Label** (800, 11px, espaciado 0.07em): identifica tablas y metadatos; las mayús se reservan a encabezados breves y categorías de navegación.

### Named Rules

**The One Voice Rule.** Lato es la única voz tipográfica; la jerarquía se construye con peso, tamaño, ritmo y cifras tabulares, no con otra familia.

**The Headline Starts the Work Rule.** No se coloca un kicker decorativo sobre los titulares; el titular debe abrir directamente la tarea y su contexto operativo aparece debajo.

## Layout

El shell de escritorio reserva 260px para una barra lateral fija y desplaza el contenido principal con 25px de margen lateral; al colapsar, la navegación ocupa 60px y el contenido comienza a 85px. La cabecera flota entre ambos con 72px de altura mínima. A partir de 1199px hacia abajo, la barra lateral pasa a ser un panel fuera de lienzo y el contenido usa márgenes de 25px; por debajo de 767px esos márgenes bajan a 15px.

Las vistas operativas combinan rejillas densas y legibles: cuatro métricas en escritorio, dos en anchuras medias y una composición reducida cuando el contenido lo exige. El panel principal usa una columna flexible junto a una columna auxiliar de 290px, que se apila por debajo de 1050px. El acceso cambia de dos columnas a una a 850px y reemplaza su panel narrativo por una ruta compacta bajo 520px.

El ritmo se construye principalmente con pasos de 8, 16, 24 y 32px. Los contenedores de trabajo usan rellenos de 17–25px: suficiente aire para separar decisiones sin diluir la densidad fiscal.

**The Route Before the Grid Rule.** Cuando una vista representa avance documental, la ruta fiscal precede a métricas y tablas para que el estado se comprenda antes de inspeccionar el detalle.

## Elevation & Depth

La profundidad es ambiental, no estructural. Bordes botánicos y cambios tonales definen la mayoría de los contenedores; las sombras difusas aparecen en la cabecera flotante, el tablero fiscal oscuro, la navegación activa y elementos que se elevan al pasar el puntero. No se apilan sombras duras ni halos ornamentales.

### Shadow Vocabulary

- **Contenedor Ambiental** (`0 8px 28px rgba(23, 51, 23, .07), 0 0 0 1px rgba(23, 51, 23, .07)`): profundidad genérica de paneles destacados.
- **Tarjeta en Reposo** (`0 6px 22px rgba(23, 51, 23, .045)`): separación casi imperceptible para tarjetas Material.
- **Cabecera Flotante** (`0 8px 30px rgba(23, 51, 23, .06)`): sostiene la cabecera translúcida sobre el lienzo.
- **Tablero Fiscal** (`0 16px 38px rgba(23, 51, 23, .15)`): eleva el resumen de ruta por ser la lectura primaria de estado.
- **Respuesta Interactiva** (`0 10px 24px rgba(23, 51, 23, .08)`): aparece junto con un desplazamiento vertical de 1–2px en tarjetas y acciones accionables.

### Named Rules

**The Ambient Depth Rule.** Una sombra debe explicar capa o respuesta interactiva; nunca debe compensar una jerarquía débil ni decorar una superficie pasiva.

## Shapes

La forma es ortogonal con curvatura contenida. Campos, botones y navegación usan radios de 6–7px; tarjetas y paneles usan 8–10px; los nodos de progreso, avatares y puntos de estado son circulares. Los bordes de 1px conservan precisión y los conectores de 2px hacen visible continuidad sin dominar.

La marca de tres barras verticales y la secuencia de nodos enlazados son las siluetas propias del sistema. Los grandes círculos ambientales del acceso pueden sugerir alcance y continuidad, pero se mantienen en baja opacidad y nunca sustituyen información.

**The Compact Corners Rule.** No se usan cápsulas ni radios exuberantes en contenedores o acciones; la redondez completa pertenece a nodos, avatares y puntos de estado.

## Components

### Buttons

- **Shape:** rectángulos de curvatura compacta (6–7px) y altura de 42–52px según jerarquía.
- **Primary:** la siguiente acción usa Verde Señal con texto Bosque Estructural y peso 900; el acceso estructural usa fondo Bosque Estructural con texto blanco.
- **Hover / Focus:** una elevación vertical de 1px, sombra ambiental y avance horizontal del icono hacen visible la respuesta; el foco global usa un contorno de 3px con Verde Señal translúcido y 2px de separación.
- **Secondary:** fondo blanco, borde Verde Operativo y texto Bosque Estructural para reintentos y acciones de menor prioridad.

### Chips

- **Style:** las versiones desplegadas son etiquetas compactas con fondo verde casi blanco, borde botánico, radio de 5px y cifras tabulares.
- **State:** los estados documentales usan un punto circular junto a una etiqueta textual; aceptado, en proceso e incidencia tienen colores distintos sin depender solo de ellos.

### Cards / Containers

- **Corner Style:** curvatura contenida de 8–9px; el tablero de ruta puede llegar a 10px.
- **Background:** blanco para trabajo cotidiano y Bosque Estructural para la vista resumida de la ruta fiscal.
- **Shadow Strategy:** planas por defecto, con borde de 1px; elevación ambiental en hover o cuando el contenedor domina la lectura.
- **Border:** Borde Botánico en tarjetas, tablas y paneles claros.
- **Internal Padding:** 16–25px según densidad y jerarquía.

### Inputs / Fields

- **Style:** fondo blanco, borde de 1px, radio de 7px, altura de 50px y relleno horizontal de 14px.
- **Focus:** el borde cambia a Verde Operativo y aparece un anillo exterior translúcido de 4px.
- **Error / Disabled:** el error usa texto y borde rojos sobre fondo rojo muy pálido; los controles deshabilitados reducen opacidad pero conservan su etiqueta y forma.

### Navigation

La barra lateral usa fondo Bosque Estructural, categorías de 10px en mayús y enlaces de 44px. El estado activo invierte la relación con Verde Señal, texto oscuro y sombra contenida; el hover aclara la superficie y desplaza el enlace 2px. En tablet y móvil se convierte en un panel fuera de lienzo sin cambiar la jerarquía interna.

### Fiscal Route

La secuencia Emisión → Firma → SUNAT → CDR combina nodos circulares y conectores de 2px. Verde Señal significa completado o activo, Salvia de Espera significa pendiente y Ámbar de Atención identifica una interrupción que requiere acción. Cada nodo visible debe incluir etiqueta o cifra; la versión decorativa del acceso es la única que puede ocultarse a tecnologías de asistencia.

### Header Controls

La cabecera translúcida mantiene juntos contexto, versiones, empresa activa e identidad. El selector de empresa es un control compacto de 42px con borde visible; las versiones usan cifras tabulares y no se esconden, sino que pasan a una fila propia en anchuras estrechas.

### Security-Critical Flows

- **Revelación única:** una credencial nueva se presenta en un panel inequívoco como visible una sola vez. Copiarla no cierra el panel: antes de ocultarla o salir, la persona confirma que la guardó en el secret store del productor; cualquier intento de abandonar sin confirmar advierte que no podrá recuperarse.
- **Rotación y revocación:** ambas requieren confirmación explícita que nombre al productor y describa la consecuencia inmediata. El resultado se comunica como estado persistente, no solo mediante un toast o un cambio de color.
- **Estados por rol:** Supervisor ve estado, actividad y diagnóstico en solo lectura, sin secretos; Admin y Root ven las acciones de crear, rotar, activar y revocar. Los controles ausentes o bloqueados deben explicar el límite de rol sin insinuar que falta una licencia o que la operación falló.

**The Secret Leaves Once Rule.** Una credencial puede revelarse una vez para su custodia; después, la interfaz muestra identidad, scopes, actividad y estado, nunca el secreto recuperable.

## Do's and Don'ts

### Do:

- **Do** hacer visible la secuencia Emisión → Firma → SUNAT → CDR cuando ayude a interpretar el estado documental.
- **Do** reservar Verde Señal para progreso, selección y la siguiente acción fiscal segura.
- **Do** acompañar cada estado con texto, número, forma o icono además del color.
- **Do** conservar foco visible, contraste legible, cifras tabulares y versiones desplegadas comprobables.
- **Do** mantener componentes sobrios, precisos y accionables sobre superficies blancas con profundidad ambiental ligera.

### Don't:

- **Don't** convertir Carabo en una plantilla SaaS genérica de tarjetas intercambiables y acentos arbitrarios.
- **Don't** usar emojis como iconografía, estado o recompensa visual.
- **Don't** colocar kickers decorativos sobre titulares; la tarea debe comenzar con el titular.
- **Don't** usar Verde Señal como relleno ornamental, color de marca ubicuo o sustituto de una jerarquía clara.
- **Don't** fabricar certificaciones, estados de homologación o capacidades fiscales sin evidencia operativa.
