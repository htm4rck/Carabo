# Desarrollo · Carabo

Consolidación documental del 2026-11-15. Los comandos se obtuvieron de los manifiestos de esta copia; no se ejecutaron builds ni operaciones de negocio para redactar esta guía. El detalle histórico está en la wiki.

[README](../README.md) · [Mapa documental](https://dev.tordo.io/tecolyt/ui/wiki?path=Gobierno%2FDocumentacion%2FRepositorios%2FCarabo%2FIndice.md)

## Requisitos y versiones

| Manifiesto | Stack | Requisito observado |
|---|---|---|
| `apps/api/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-bom/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-core/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-domain/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-ose-client/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-scheduler/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-signer/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-starter-quarkus/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-storage/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/api/carabo-sunat-client/build.gradle` | Java/Gradle | Versión declarada en el manifiesto/wrapper; no se fija otra versión aquí |
| `apps/ui/package.json` | Node | node: `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0`; @angular/core: `^22.2.4`; typescript: `~6.0.3` |

### Fuente: `README.md`

Consultar **Requisitos** en la [fuente local conservada](../README.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

## Instalación

| Directorio | Instalación | Condición |
|---|---|---|
| `apps/ui` | `npm ci` | Lock presente; requiere acceso al registro privado |

## Ejecución local

| Directorio | Comando disponible | Condición |
|---|---|---|
| `apps/api` | `bash gradlew :carabo-starter-quarkus:quarkusDev` | Tarea conservada de la documentación del proyecto; preparar sus dependencias |
| `apps/ui` | `npm run start` | Script declarado; preparar configuración y dependencias |

### Fuente: `README.md`

Consultar **Desarrollo Local** en la [fuente local conservada](../README.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

## Estructura y puntos de extensión

Directorios existentes: `Tordo`, `apps`, `docs`, `examples`, `gradle`, `infra`, `migrations`, `ops`, `scripts`, `templates`, `tools`. La responsabilidad detallada se conserva en los fragmentos y anexos siguientes.

### Fuente: `README.md`

Consultar **Estructura del Proyecto** en la [fuente local conservada](../README.md). Se mantiene por sus consumidores; contrastar versiones y comandos con los manifiestos de arriba.

### Fuente: `apps/ui/README.md`

#### Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Convenciones específicas y flujo de cambios

Aplicar las instrucciones AGENTS.md más cercanas cuando existan y las referencias centrales de [Arquitectura/Estandares](https://dev.tordo.io/tecolyt/ui/wiki?path=Arquitectura%2FEstandares%2FIndice.md). Las reglas específicas y contratos de la revisión conservan prioridad sobre una plantilla genérica.

## Diagnóstico local

Consultar las instrucciones específicas consolidadas abajo y los anexos registrados en README. Si no hay definición suficiente en las fuentes, el requisito queda pendiente de documentar; no se infiere un comportamiento nuevo.
