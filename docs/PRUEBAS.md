# Pruebas · Carabo

Consolidación documental del 2026-11-15. Los comandos se obtuvieron de los manifiestos de esta copia; no se ejecutaron builds ni operaciones de negocio para redactar esta guía. El detalle histórico está en la wiki.

[README](../README.md) · [Mapa documental](https://dev.tordo.io/tecolyt/ui/wiki?path=Gobierno%2FDocumentacion%2FRepositorios%2FCarabo%2FIndice.md)

## Preparación del entorno de prueba

Preparar las dependencias indicadas por cada script. Las pruebas que incluyen DEV, proveedores reales o mutaciones necesitan un destino explícito; esta normalización no las ejecuta ni concede esa autorización.

## Comandos por componente

| Directorio | Comando disponible | Condición |
|---|---|---|
| `apps/api` | `bash gradlew check` | Usar la configuración de pruebas del wrapper |
| `apps/ui` | `npm run build` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run build:prod` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run test` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run check:architecture` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run typecheck` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run lint` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run format:check` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run test:architecture` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run verify` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run test:unit` | Script declarado; revisar destino y requisitos antes de ejecutar |
| `apps/ui` | `npm run typecheck:e2e` | Script declarado; revisar destino y requisitos antes de ejecutar |

### Fuente: `apps/ui/README.md`

#### Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

### Fuente: `apps/ui/README.md`

#### Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

### Fuente: `docs/VALIDACION_HOMOLOGACION_UI.md`

### Validación de homologación para desarrollo

- Repositorio: `github.com/htm4rck/Carabo.git`.
- Rama de destino: `develop`.
- Base remota anterior: `9e2df4a08af538a5a6db4ccdd719acf659a00119`.
- Base de la consolidación: `9e2df4a08af538a5a6db4ccdd719acf659a00119`.
- Fecha de validación previa: 2026-10-30.
- Resultado: PASS: previously completed local build and compiled HTML inspection.

Se conserva la configuración propia del proyecto y se homologa la carga de CSS
mediante `optimization.styles.inlineCritical: false` cuando corresponde.
La aplicación del ajuste común se comprueba con `@tordo-io/web-security` 0.1.0.
La validación anterior se reutiliza por instrucción del usuario; no se vuelven a
lanzar suites, builds ni despliegues completos en el servidor. Este commit de
consolidación usa `[skip ci]`. Esto registra código validado en la rama de
desarrollo; no equivale a una imagen nueva construida ni a un despliegue.

Solo se incluyen los cambios de homologación y documentación enumerados en el
diff. Se preserva por separado el trabajo de otras tareas de los clones originales.
Reports y Corvus están excluidos de este proceso.

El resultado de compilación local documentado corresponde al workspace probado.
La configuración se aplica sobre la base remota conservando su historial; cuando
esa base contiene otros cambios previos, no se atribuye a este registro una nueva
validación de esos cambios. No se modifica la lógica de negocio por el ajuste CSS.

### Fuente: `docs/architecture/angular/ANGULAR22_UPGRADE.md`

#### Validación

- 9 pruebas unitarias, 5 casos del gate de arquitectura y 3 recorridos Playwright aprobados.
- Formato, lint, TypeScript, build de producción y arranque/compilación con `ng serve` aprobados.
- Bundle inicial: 511.83 kB → **524.83 kB**; se conservan los presupuestos existentes.
- APIs de navegador simuladas, sin correos ni documentos fiscales reales. El harness local no acredita seguridad del gateway desplegado.
- Auditoría total: 18 avisos; 0 altos, 0 críticos. Sin devDependencies: 0 avisos. No se declara deuda de seguridad cero.

`validation.json` contiene versiones, mediciones y hashes; `dependency-audit.json` y `production-dependency-audit.json` conservan el detalle de la consulta. La ejecución remota de CI se registra después en el PR/wiki para no confundirla con la captura local.

Fuentes: [compatibilidad Angular](https://angular.dev/reference/versions), [CLI update](https://angular.dev/cli/update), [registro core 22.2.4](https://registry.npmjs.org/@angular/core/22.2.4).

### Fuente: `docs/architecture/angular/ANGULAR_ADOPTION.md`

#### Reglas verificables

- `core`: sesión, contexto, configuración y transporte transversal; no importa `features` ni `layout`.
- `layout`: composición visual propia del producto, navegación, cabecera y footer. Conserva su tema; no distribuye estilos globales de otra aplicación.
- `features/<capacidad>/domain`: contratos y lógica pura, sin Angular, RxJS, HTTP ni presentación.
- `features/<capacidad>/data-access`: adaptadores HTTP tipados. Las páginas no construyen transporte con HttpClient.
- `application`: coordinación cuando existe un proceso que lo justifica; consume puertos. No crear capas vacías para pantallas simples.
- `pages` y `ui`: entrada de ruta y presentación de la capacidad. Colaboración entre capacidades sólo por `public-api.ts`.
- Rutas con `loadComponent`/`loadChildren`; composición de proveedores en `app.config.ts`. Las rutas privadas separan autenticación y shell.
- Las suscripciones de componentes se vinculan a DestroyRef; el estado de aplicación nuevo se expone de sólo lectura.

`tools/check-architecture.mjs` inspecciona imports, reexports y cargas dinámicas mediante el AST de TypeScript y su resolución de aliases. Sus cinco pruebas incluyen dependencias prohibidas y una composición válida. El gate no demuestra ausencia de toda deuda ni reemplaza revisión de código.

### Fuente: `docs/architecture/angular/ANGULAR_ADOPTION.md`

#### Verificación y desarrollo

Desde cada raíz Angular:

```bash
npm ci
npx playwright install chromium
npm run verify
npm run e2e
npm start
```

`verify` ejecuta formato, ESLint, límites de arquitectura, pruebas del gate, TypeScript de aplicación y E2E, pruebas unitarias y build de producción. `e2e` sirve el artefacto compilado bajo su baseHref real. Sus APIs son fixtures, los dominios externos se bloquean y no se envían correos ni documentos fiscales reales. CI conserva traces sólo en fallos con credenciales sintéticas.

El servidor en `tools/serve-ui-test.mjs` es un harness funcional local: no acredita CSP, Trusted Types, cookies, CSRF ni configuración del gateway desplegado. Ver los resultados medidos en `validation.json` junto a este documento. La ejecución remota de CI y una aceptación integrada en DEV se registran aparte de la ejecución local.

### Fuente: `docs/architecture/backend/ARCHITECTURE.md`

#### Verificación

Desde `apps/api`:

```sh
./gradlew :carabo-domain:check --no-daemon
./gradlew check :carabo-starter-quarkus:quarkusBuild --no-daemon
```

`checkArchitecture` comprueba que el JAR del núcleo solo depende de `java.base` mediante `jdeps` y que su classpath de ejecución no tiene dependencias externas. Está integrado en `check` y en el workflow de imágenes. Las pruebas unitarias usan puertos; las pruebas de persistencia levantan PostgreSQL efímero, aplican las migraciones del repositorio y eliminan su contenedor. No se conectan a bases de desarrollo ni de producción.

Las pruebas cubren separación por empresa, rollback, rotación/revocación, errores, importes exactos, límites de fechas, proyecciones sin carga de entidades, una consulta de listado, contadores concurrentes y limpieza de temporales. El smoke HTTP de aislamiento existente sigue ejecutándose en CI después de compilar la API.

Validación local de esta entrega, 2026-11-06: `check` y `quarkusBuild` completados, 133 pruebas sin fallos ni omisiones. Un smoke adicional contra la API compilada, PostgreSQL y RabbitMQ efímeros pasó 17 comprobaciones HTTP: composición CDI, contratos seguros, errores 400/409, aislamiento, actividad concurrente con `REQUIRES_NEW`, rotación/revocación, reportes, Excel y sincronización de la credencial heredada en la transacción del tenant. Se eliminaron los contenedores de prueba al finalizar. Esta validación no desplegó cambios ni midió RAM de las instalaciones existentes.

### Fuente: `docs/architecture/backend/performance-2026-11-06/README.md`

Los escenarios y resultados de este corte se mantienen en la [wiki](https://dev.tordo.io/tecolyt/ui/wiki?path=Z_Archivo%2FMigracion%20documental%202026-11-15%2FCarabo%2Fdocs%2Farchitecture%2Fbackend%2Fperformance-2026-11-06%2FREADME.md). Los comandos disponibles de esta revisión están en la tabla anterior.

### Fuente: `docs/company-selection.md`

#### Verificación en DEV

El 28 de octubre de 2026 se verificó `https://dev.tordo.io/carabo/ui/documents`: root puede cambiar entre Tordo Origins y Acme Corp (`B20123456789`). En Acme aparece la boleta de Birdport **B001-00000001 por S/97.00**, aceptada en SUNAT beta.

Se aprobaron ocho pruebas existentes de políticas/contexto de seguridad, el build de Quarkus y el build de producción de Angular 21. Se ejecutaron además catorce comprobaciones HTTP/navegador contra DEV: membresías activas, exclusión por rol y empresa deshabilitada, rechazo de autenticación anónima o de integración, parámetros falsificados, aislamiento documental, actualización de sesiones guardadas, inicio de sesión nuevo, persistencia del selector y recuperación de error. No se emitieron documentos durante esta comprobación.

El flujo de login del navegador usa una respuesta Auth controlada con JWT de verificación y consulta Carabo real. Las identidades temporales se eliminan al finalizar. La evidencia y el script reproducible están en Birdport: `scripts/verify-carabo-business-selector.cjs` y `docs/assets/carabo-business-selector-verification/`.

El despliegue de esta corrección se limita a DEV, mediante las imágenes locales `birdport-fiscal-carabo:dev` y `birdport-carabo-ui:dev` y el override privado de servicios. Producción conserva sus imágenes fijadas.

## Casos relevantes y negativos

Consultar las instrucciones específicas consolidadas abajo y los anexos registrados en README. Si no hay definición suficiente en las fuentes, el requisito queda pendiente de documentar; no se infiere un comportamiento nuevo.

## Resultado esperado y limpieza

Consultar las instrucciones específicas consolidadas abajo y los anexos registrados en README. Si no hay definición suficiente en las fuentes, el requisito queda pendiente de documentar; no se infiere un comportamiento nuevo.

## Cobertura y límites conocidos

Esta entrega comprueba documentación, enlaces y consumidores; no certifica funcionalidad ni aceptación productiva. Se conservan las limitaciones originales y los resultados fechados en la wiki.

## Aceptación y evidencia

[Fuentes y evidencias documentales](https://dev.tordo.io/tecolyt/ui/wiki?path=Gobierno%2FDocumentacion%2FRepositorios%2FCarabo%2FIndice.md). Build, prueba local, despliegue y aceptación de negocio se registran por separado.
