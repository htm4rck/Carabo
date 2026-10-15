import { readdir, readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const outputDirectory = fileURLToPath(new URL('../dist/carabo-ui/browser/', import.meta.url));
const forbidden = [/localhost(?::\d+)?/i, /127\.0\.0\.1(?::\d+)?/];

async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? files(path) : [path];
  }));
  return nested.flat();
}

const assets = await files(outputDirectory);
const inspected = assets.filter(path => ['.js', '.html', '.css'].includes(extname(path)));
const violations = [];

for (const path of inspected) {
  const contents = await readFile(path, 'utf8');
  for (const pattern of forbidden) {
    if (pattern.test(contents)) violations.push(`${path}: ${pattern}`);
  }
}

if (violations.length) {
  console.error('Production bundle contains forbidden loopback URLs:');
  console.error(violations.join('\n'));
  process.exit(1);
}

console.log(`Production bundle verified: ${inspected.length} assets contain no loopback URLs.`);
