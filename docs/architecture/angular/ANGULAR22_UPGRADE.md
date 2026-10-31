# Actualización a Angular 22 — Carabo

Fecha: 2026-10-31. Raíz: `apps/ui`. Angular 21.2.25 → **22.2.4**, CLI/build **22.2.5**, TypeScript **6.0.3**, angular-eslint **22.6.0**. Material/CDK **22.2.5** donde se utilizan. Son las versiones estables consultadas en npm; no se usan versiones next/rc.

## Migración

Se ejecutaron migraciones oficiales por versión mayor (21 → 22), con pasos recuperados mediante `ng update --migrate-only` cuando la instalación ya había avanzado. Se mantienen las rutas, el baseHref, los contratos HTTP y la arquitectura de capacidades. Las bibliotecas se instalan respetando peers; no se usa `--force` ni `--legacy-peer-deps` para resolver esta actualización.

La migración oficial agrega `ChangeDetectionStrategy.Eager` a pantallas existentes y `withXhr` donde corresponde para conservar el comportamiento anterior a Angular 22. Las excepciones al nuevo lint de OnPush están sólo en esas declaraciones y explican la compatibilidad; la regla permanece habilitada para código nuevo. Migrar esas pantallas a OnPush/signals requiere una revisión propia de sus actualizaciones asíncronas. Se conserva la configuración de compatibilidad de diagnósticos de plantillas generada por Angular; `strictTemplates` y las puertas de tipos siguen habilitados.

Se retiran opciones obsoletas de TypeScript 6 (`baseUrl`/`downlevelIteration` cuando existían), con aliases relativos equivalentes. Los overrides antiguos de Vite/piscina se retiran al pasar al builder que define sus propias versiones. Se mantienen los runners de pruebas existentes soportados por Angular 22 (Vitest y Karma según aplicación).

## Node, CI y contenedores

`.nvmrc` y CI usan **Node 24.19.0**; los stages de Docker que compilan la UI también. Tipos Node **24.19.2** para representar el runtime seleccionado. `engines.node` conserva el rango admitido por Angular 22. El manifiesto de `node:24.19.0-alpine` se comprobó en el registro. Los runtimes/backend ajenos a la UI conservan su contrato; Cloud comparte package.json y su imagen pasa también a Node 24.

## Validación

- 9 pruebas unitarias, 5 casos del gate de arquitectura y 3 recorridos Playwright aprobados.
- Formato, lint, TypeScript, build de producción y arranque/compilación con `ng serve` aprobados.
- Bundle inicial: 511.83 kB → **524.83 kB**; se conservan los presupuestos existentes.
- APIs de navegador simuladas, sin correos ni documentos fiscales reales. El harness local no acredita seguridad del gateway desplegado.
- Auditoría total: 18 avisos; 0 altos, 0 críticos. Sin devDependencies: 0 avisos. No se declara deuda de seguridad cero.

`validation.json` contiene versiones, mediciones y hashes; `dependency-audit.json` y `production-dependency-audit.json` conservan el detalle de la consulta. La ejecución remota de CI se registra después en el PR/wiki para no confundirla con la captura local.

Fuentes: [compatibilidad Angular](https://angular.dev/reference/versions), [CLI update](https://angular.dev/cli/update), [registro core 22.2.4](https://registry.npmjs.org/@angular/core/22.2.4).
