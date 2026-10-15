import { writeFileSync } from 'node:fs';

const version = process.env.CARABO_UI_VERSION || process.env.npm_package_version || '0.2.0';
const buildDate = process.env.CARABO_BUILD_DATE || new Date().toISOString().slice(0, 10);
const commit = process.env.CARABO_COMMIT || 'local';
const content = `export const UI_BUILD = ${JSON.stringify({ version, buildDate, commit }, null, 2)} as const;\n`;

writeFileSync(new URL('../src/environments/build-info.ts', import.meta.url), content, 'utf8');
