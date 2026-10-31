import { test, expect, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const fields = [
  'solUser',
  'solPassword',
  'greClientId',
  'greClientSecret',
  'certificatePassword',
  'oseApiToken',
];
const template =
  'SOL_USER=\nSOL_PASSWORD=\nGRE_CLIENT_ID=\nGRE_CLIENT_SECRET=\nCERTIFICATE_PASSWORD=\nOSE_API_TOKEN=\n';
const company = (id: string) => ({
  id,
  businessCode: 'B11111111111',
  businessName: `Company ${id}`,
  issuerRuc: '11111111111',
  issuerName: 'Fixture',
  sendMode: 'SUNAT_DIRECT',
  environment: 'BETA',
  active: true,
  invoiceSeries: 'F001',
  receiptSeries: 'B001',
  creditNoteSeries: 'FC01',
  debitNoteSeries: 'FD01',
  maxDocumentsPerDay: 1000,
});
async function setup(
  page: Page,
  options: {
    unavailable?: boolean;
    failStatus?: boolean;
    rejectImport?: boolean;
    configured?: string[];
  } = {},
) {
  const statuses = new Map<string, { configured: boolean; usable: boolean }>();
  for (const field of options.configured ?? [])
    statuses.set(field, { configured: true, usable: true });
  let statusReads = 0;
  const writes: Array<{ path: string; body: string; business?: string }> = [];
  const state = () => ({
    storageReady: !options.unavailable,
    fields: Object.fromEntries(
      fields.map((key) => [key, statuses.get(key) ?? { configured: false, usable: false }]),
    ),
  });
  const token = `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 1800 })).toString('base64url')}.synthetic`;
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url()),
      path = url.pathname;
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) return route.abort();
    if (path === '/auth/login')
      return route.fulfill({
        json: {
          success: true,
          data: {
            auth: { token, expiresAt: new Date(Date.now() + 1800000).toISOString() },
            user: {
              userCode: 'U11111111111',
              nif: '11111111111',
              firstName: 'Test',
              lastName: 'User',
              email: 'fixture@example.invalid',
              isActive: true,
              isVerified: true,
            },
            businesses: [
              {
                businessCode: 'B11111111111',
                defaultRoleCode: 'ADMIN_Carabo',
                isActive: true,
                business: { businessName: 'Company one' },
              },
            ],
            applications: [],
            licenses: [],
            licenseType: 'professional',
          },
        },
      });
    if (!path.startsWith('/carabo/api/')) return route.continue();
    if (path.endsWith('/access/businesses'))
      return route.fulfill({
        json: [
          {
            businessCode: 'B11111111111',
            businessName: 'Company one',
            defaultRoleCode: 'ADMIN_Carabo',
            isActive: true,
            roles: ['ADMIN_Carabo'],
          },
        ],
      });
    if (path.endsWith('/health')) return route.fulfill({ json: { version: 'fixture' } });
    if (path.endsWith('/tenants')) return route.fulfill({ json: [company('one'), company('two')] });
    if (path.endsWith('/readiness'))
      return route.fulfill({
        json: {
          businessCode: 'B11111111111',
          ready: false,
          blockers: 1,
          environment: 'BETA',
          sendMode: 'SUNAT_DIRECT',
          checks: [],
        },
      });
    if (path.endsWith('/credentials/template'))
      return route.fulfill({
        contentType: 'text/plain',
        headers: {
          'content-disposition': 'attachment; filename=carabo-credenciales-vacias.txt',
          'cache-control': 'no-store',
        },
        body: template,
      });
    if (path.includes('/credentials')) {
      if (route.request().method() === 'GET') {
        statusReads++;
        return route.fulfill(options.failStatus ? { status: 503, json: {} } : { json: state() });
      }
      const body = route.request().postData() ?? '';
      writes.push({ path, body, business: route.request().headers()['business-code'] });
      if (path.endsWith('/import') && options.rejectImport)
        return route.fulfill({
          status: 400,
          json: { error: 'El archivo contiene un campo desconocido; descarga la plantilla vacía' },
        });
      if (path.endsWith('/import')) {
        statuses.set('greClientSecret', { configured: true, usable: true });
      } else
        for (const [key, value] of Object.entries(JSON.parse(body)))
          if (typeof value === 'string' && value.trim())
            statuses.set(key, { configured: true, usable: true });
      return route.fulfill({ json: state() });
    }
    if (path.endsWith('/certificate')) return route.fulfill({ json: company('one') });
    return route.fulfill({ json: { items: [], total: 0, offset: 0, limit: 25 } });
  });
  await page.goto('/carabo/ui/login');
  await page.locator('input[autocomplete="username"]').fill('fixture@example.invalid');
  await page.locator('input[autocomplete="current-password"]').fill('synthetic-password');
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/carabo\/ui\/$/);
  await page.goto('/carabo/ui/config');
  await page.getByRole('button', { name: 'Configurar Company one', exact: true }).click();
  return { writes, statuses, reads: () => statusReads };
}
for (const viewport of [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test.describe(viewport.name, () => {
    test.use({ viewport });
    test('shows saved state on cards and opens empty replacement inputs only on request', async ({
      page,
    }) => {
      const fixture = await setup(page);
      const gre = page.getByRole('article', { name: 'SUNAT GRE', exact: true });
      await expect(page.locator('.credentials-panel input')).toHaveCount(0);
      await expect(gre.getByText('Sin registrar', { exact: true })).toHaveCount(2);
      await gre.getByRole('button', { name: 'Configurar SUNAT GRE' }).click();
      await expect(page.getByLabel('ID de cliente SUNAT GRE', { exact: true })).toBeFocused();
      const secret = page.getByLabel('Clave SUNAT GRE', { exact: true });
      await expect(secret).toHaveAttribute('type', 'password');
      await secret.fill('fixture-secret==');
      await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
      await expect(
        page.getByRole('status').filter({ hasText: 'Credenciales guardadas' }),
      ).toBeVisible();
      await expect(gre.locator('input')).toHaveCount(0);
      await expect(gre.getByText('Guardado', { exact: true })).toHaveCount(1);
      await expect(gre.getByRole('button', { name: 'Actualizar SUNAT GRE' })).toBeFocused();
      expect(JSON.parse(fixture.writes[0].body).greClientSecret).toBe('fixture-secret==');
      expect(fixture.writes[0].business).toBe('B11111111111');
      expect(await page.evaluate(() => JSON.stringify({ ...localStorage }))).not.toContain(
        'fixture-secret',
      );
      await page.getByRole('button', { name: 'Cerrar configuración', exact: true }).click();
      await page.getByRole('button', { name: 'Configurar Company one', exact: true }).click();
      await expect(gre.locator('input')).toHaveCount(0);
      expect(fixture.reads()).toBe(2);
      await gre.getByRole('button', { name: 'Actualizar SUNAT GRE' }).click();
      await expect(page.getByLabel('Nueva clave SUNAT GRE', { exact: true })).toHaveValue('');
      await expect(
        page.getByRole('button', { name: 'Guardar cambios', exact: true }),
      ).toBeDisabled();
      await gre.getByRole('button', { name: 'Cancelar', exact: true }).click();
      await expect(gre.locator('input')).toHaveCount(0);
      await expect(gre.getByRole('button', { name: 'Actualizar SUNAT GRE' })).toBeFocused();
      await page
        .locator('.credentials-panel')
        .screenshot({ path: `/tmp/carabo-credential-cards-${viewport.name}.png` });
      const panel = await page.locator('.credentials-panel').boundingBox();
      expect(panel!.width).toBeLessThanOrEqual(viewport.width);
      for (const card of await page.locator('.credential-card').all()) {
        expect(await card.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
          true,
        );
      }
    });
    test('every credential can be entered on screen and cancelling never sends or retains it', async ({
      page,
    }) => {
      const fixture = await setup(page, { configured: fields });
      for (const [title, labels] of [
        ['Acceso SOL', ['Nuevo usuario SOL', 'Nueva contraseña SOL']],
        ['SUNAT GRE', ['Nuevo ID de cliente SUNAT GRE', 'Nueva clave SUNAT GRE']],
        ['Certificado digital', ['Nueva contraseña del certificado']],
        ['Proveedor OSE', ['Nuevo token OSE']],
      ] as const) {
        const card = page.getByRole('article', { name: title, exact: true });
        await card.getByRole('button', { name: 'Actualizar ' + title, exact: true }).click();
        for (const label of labels) {
          await expect(page.getByLabel(label, { exact: true })).toHaveValue('');
          await page.getByLabel(label, { exact: true }).fill('discard-this-value');
        }
        await card.getByRole('button', { name: 'Cancelar', exact: true }).click();
        await expect(card.locator('input')).toHaveCount(0);
      }
      expect(fixture.writes).toHaveLength(0);
      const gre = page.getByRole('article', { name: 'SUNAT GRE', exact: true });
      await gre.getByRole('button', { name: 'Actualizar SUNAT GRE' }).click();
      await page.getByLabel('Nueva clave SUNAT GRE', { exact: true }).fill('must-not-go-to-sol');
      await page.getByRole('button', { name: 'Actualizar Acceso SOL', exact: true }).click();
      await page.getByLabel('Nuevo usuario SOL', { exact: true }).fill('fixture-user');
      await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
      await expect(
        page.getByRole('status').filter({ hasText: 'Credenciales guardadas' }),
      ).toBeVisible();
      expect(JSON.parse(fixture.writes[0].body).greClientSecret).toBe('');
    });
    test('TXT is an alternative for the same fields and downloads are always blank', async ({
      page,
    }) => {
      const fixture = await setup(page);
      await page.getByRole('button', { name: 'Importar TXT', exact: true }).click();
      await expect(page.getByLabel('Archivo de credenciales', { exact: true })).toBeFocused();
      for (let attempt = 0; attempt < 2; attempt++) {
        const downloadEvent = page.waitForEvent('download');
        await page.getByRole('button', { name: 'Descargar plantilla vacía' }).click();
        const download = await downloadEvent;
        const path = await download.path();
        expect(await readFile(path!, 'utf8')).toBe(template);
        if (attempt === 0) {
          await page.getByLabel('Archivo de credenciales', { exact: true }).setInputFiles({
            name: 'credentials.txt',
            mimeType: 'text/plain',
            buffer: Buffer.from('GRE_CLIENT_SECRET=fixture-import==\nSOL_PASSWORD=\n'),
          });
          await page.getByRole('button', { name: 'Importar credenciales', exact: true }).click();
          await expect(
            page.getByRole('status').filter({ hasText: 'Credenciales guardadas' }),
          ).toBeVisible();
          expect(fixture.writes[0].body).toContain('GRE_CLIENT_SECRET=fixture-import==');
          await expect(page.getByLabel('Archivo de credenciales', { exact: true })).toHaveValue('');
          await expect(
            page
              .getByRole('article', { name: 'SUNAT GRE', exact: true })
              .getByText('Guardado', { exact: true }),
          ).toHaveCount(1);
          await expect(page.locator('input[name="greClientSecret"]')).toHaveCount(0);
        }
      }
    });
    test('failed imports retain the saved card state and explain the invalid field', async ({
      page,
    }) => {
      await setup(page, { rejectImport: true, configured: ['solPassword'] });
      const sol = page.getByRole('article', { name: 'Acceso SOL', exact: true });
      await expect(sol.getByText('Guardado', { exact: true })).toHaveCount(1);
      await page.getByRole('button', { name: 'Importar TXT', exact: true }).click();
      await page.getByLabel('Archivo de credenciales', { exact: true }).setInputFiles({
        name: 'bad.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('UNKNOWN=fixture-secret'),
      });
      await page.getByRole('button', { name: 'Importar credenciales', exact: true }).click();
      await expect(page.getByRole('alert')).toHaveText(
        'El archivo contiene un campo desconocido; descarga la plantilla vacía',
      );
      await expect(sol.getByText('Guardado', { exact: true })).toHaveCount(1);
      await expect(
        page.getByRole('button', { name: 'Importar credenciales', exact: true }),
      ).toBeEnabled();
      await expect(page.locator('input[name="greClientSecret"]')).toHaveCount(0);
    });
    test('unavailable secure storage prevents edits and still allows a blank template', async ({
      page,
    }) => {
      const fixture = await setup(page, { unavailable: true });
      await expect(page.getByRole('alert')).toContainText('El almacén seguro no está disponible');
      for (const button of await page.locator('.credential-card-action').all())
        await expect(button).toBeDisabled();
      await expect(page.locator('input[name="greClientSecret"]')).toHaveCount(0);
      await page.getByRole('button', { name: 'Importar TXT', exact: true }).click();
      await expect(page.getByLabel('Archivo de credenciales', { exact: true })).toBeDisabled();
      await expect(page.getByRole('button', { name: 'Descargar plantilla vacía' })).toBeEnabled();
      expect(fixture.writes).toHaveLength(0);
    });
    test('failed status requests have a visible error and prevent blind updates', async ({
      page,
    }) => {
      await setup(page, { failStatus: true });
      await expect(page.getByRole('alert')).toContainText('No pudimos consultar las credenciales');
      await expect(page.locator('.credential-card')).toHaveCount(0);
    });
  });
}
