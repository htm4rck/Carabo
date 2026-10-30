# Adopción de la referencia Angular — Carabo

Fecha: 2026-10-30. Raíz: `apps/ui`. BaseHref de producción: `/carabo/ui/`.

Se divide el cliente HTTP monolítico en nueve adaptadores por capacidad: documents, emit, config, fiscal-status, integrations, batch, reports, dispatch-guides y operations. Dashboard consume la API pública de documents. DTO y ejemplos se ubican junto a su dominio. Shell/header/sidebar pasan a layout, separados del login. Se conserva el wire contract fiscal, incluidos headers, Blob e Idempotency-Key; la emisión tiene prueba específica de ese contrato. El selector de empresa usa FormsModule para mantener el valor correcto después de crear opciones y recargar; E2E acredita consultas con A y B. Build inicial de referencia: 651.42 kB.

No se modifica el backend Quarkus ni se emiten comprobantes reales en las pruebas. El guard de artefacto de producción existente sigue formando parte de verify. Continúan avisos heredados de Sass @import.

## Reglas verificables

- `core`: sesión, contexto, configuración y transporte transversal; no importa `features` ni `layout`.
- `layout`: composición visual propia del producto, navegación, cabecera y footer. Conserva su tema; no distribuye estilos globales de otra aplicación.
- `features/<capacidad>/domain`: contratos y lógica pura, sin Angular, RxJS, HTTP ni presentación.
- `features/<capacidad>/data-access`: adaptadores HTTP tipados. Las páginas no construyen transporte con HttpClient.
- `application`: coordinación cuando existe un proceso que lo justifica; consume puertos. No crear capas vacías para pantallas simples.
- `pages` y `ui`: entrada de ruta y presentación de la capacidad. Colaboración entre capacidades sólo por `public-api.ts`.
- Rutas con `loadComponent`/`loadChildren`; composición de proveedores en `app.config.ts`. Las rutas privadas separan autenticación y shell.
- Las suscripciones de componentes se vinculan a DestroyRef; el estado de aplicación nuevo se expone de sólo lectura.

`tools/check-architecture.mjs` inspecciona imports, reexports y cargas dinámicas mediante el AST de TypeScript y su resolución de aliases. Sus cinco pruebas incluyen dependencias prohibidas y una composición válida. El gate no demuestra ausencia de toda deuda ni reemplaza revisión de código.

## Verificación y desarrollo

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

## Sesión y compatibilidad

Se conserva el contrato bearer y la persistencia existente. No se migra a cookies desde la UI sin soporte de su backend. El interceptor delimita credenciales por origen/ruta, impone timeout y acepta 401 sólo para el token/contexto actual. Un cambio de usuario/empresa/entorno invalida observaciones previas. Las lecturas GET/HEAD anteriores se cancelan; una escritura iniciada puede terminar, pero su respuesta de otro contexto se descarta. Esto no revierte una escritura ya confirmada por el servidor; idempotencia y autorización siguen siendo responsabilidades del contrato backend.

La revisión de arquitectura no certifica los criterios de seguridad completos de Columba: almacenamiento de tokens, autorización servidor, cabeceras del gateway, CSRF y pruebas reales multiempresa requieren su aceptación específica. Un rol guardado en el navegador sólo controla experiencia visual.

## Reutilización entre productos

Se conserva layout/branding de cada UI. La biblioteca compartida actual tiene restricciones de peers y dependencias de tema; no se añade una dependencia privada incompatible a Angular 19/21. La extracción de un footer neutral con estilos proporcionados por cada producto continúa como entrega separada, con contrato y matriz de versiones. Esta adopción no declara cerrada esa deuda transversal.

## Resultado local

9 pruebas unitarias, 5 casos del gate de arquitectura y 3 recorridos Playwright aprobados; formato, lint, TypeScript y build de producción aprobados. Bundle inicial: 511.83 kB. Auditoría total: 0 avisos. Detalle reproducible en `dependency-audit.json` y hashes de entradas en `validation.json`.
