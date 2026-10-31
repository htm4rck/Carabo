import { test, expect, type Page } from '@playwright/test';
async function isolate(page: Page) {
  await page.route('**/*', (route) => {
    const url = new URL(route.request().url());
    if (url.hostname !== '127.0.0.1' && url.hostname !== 'localhost') return route.abort();
    return route.continue();
  });
}
const token = () =>
  `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 1800 })).toString('base64url')}.synthetic`;
const profile = () => ({
  success: true,
  data: {
    auth: { token: token(), expiresAt: new Date(Date.now() + 1800000).toISOString() },
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
        defaultRoleCode: 'SELLER_COLUMBA',
        isActive: true,
        business: { businessName: 'First company' },
      },
    ],
    applications: [],
    licenses: [],
    licenseType: 'professional',
  },
});

const prefix = '/carabo/ui/';
async function carabo(page: Page) {
  await isolate(page);
  const seen: { path: string; business: string | undefined }[] = [];
  let unauthorized = false;
  await page.route('**/auth/login', (route) => route.fulfill({ json: profile() }));
  await page.route('**/carabo/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname,
      business = route.request().headers()['business-code'];
    seen.push({ path, business });
    if (path.endsWith('/access/businesses'))
      return route.fulfill({
        json: [
          {
            businessCode: 'B11111111111',
            businessName: 'First company',
            defaultRoleCode: 'ADMIN_Carabo',
            isActive: true,
            roles: ['ADMIN_Carabo'],
          },
          {
            businessCode: 'B22222222222',
            businessName: 'Second company',
            defaultRoleCode: 'ADMIN_Carabo',
            isActive: true,
            roles: ['ADMIN_Carabo'],
          },
        ],
      });
    if (path.endsWith('/health'))
      return route.fulfill({
        json: { version: 'fixture', buildDate: 'synthetic', commit: 'synthetic' },
      });
    if (path.endsWith('/documents'))
      return route.fulfill(
        unauthorized
          ? { status: 401, json: {} }
          : { json: { items: [], total: 0, offset: 0, limit: 25 } },
      );
    return route.fulfill({ json: [] });
  });
  return {
    seen,
    expire: () => {
      unauthorized = true;
    },
  };
}
async function signIn(page: Page) {
  await page.goto(prefix + 'login');
  await page.locator('input[autocomplete="username"]').fill('fixture@example.invalid');
  await page.locator('input[autocomplete="current-password"]').fill('synthetic-password');
  await page.locator('form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/carabo\/ui\/$/);
}
test('protects private fiscal routes without loading the private shell', async ({ page }) => {
  await carabo(page);
  await page.goto(prefix + 'documents');
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.locator('app-sidebar').count()).toBe(0);
});
test('discovers authorized companies and scopes document requests after reload', async ({
  page,
}) => {
  const { seen } = await carabo(page);
  await signIn(page);
  await page.goto(prefix + 'documents');
  await expect
    .poll(() =>
      seen.some((call) => call.path.endsWith('/documents') && call.business === 'B11111111111'),
    )
    .toBe(true);
  await page.reload();
  await expect(page.getByRole('banner')).toBeVisible();
  await expect
    .poll(() => seen.filter((call) => call.path.endsWith('/documents')).length)
    .toBeGreaterThan(1);
  await page.getByRole('combobox', { name: 'Empresa activa' }).selectOption('B22222222222');
  await expect(page.getByRole('combobox', { name: 'Empresa activa' })).toHaveValue('B22222222222');
  await expect
    .poll(() =>
      seen.some((call) => call.path.endsWith('/documents') && call.business === 'B22222222222'),
    )
    .toBe(true);
});
test('returns to authentication when the current API session is rejected', async ({ page }) => {
  const state = await carabo(page);
  await signIn(page);
  state.expire();
  await page.goto(prefix + 'documents');
  await expect(page).toHaveURL(/\/login$/);
});

for (const width of [1440, 390]) {
  test(`requires fiscal acceptance for printable guide PDFs at width ${width}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await carabo(page);
    const states = ['BLOCKED_CONFIGURATION', 'SENT', 'REJECTED', 'ACCEPTED'];
    await page.route('**/carabo/api/dispatch-guides*', (route) =>
      route.fulfill({
        json: states.map((status, index) => ({
          id: 'guide-' + index,
          fullNumber: index === 0 ? null : 'T001-' + index,
          status,
          issueDate: '2026-10-31',
          receiverName: 'Receptor de prueba',
          receiverDoc: '87654321',
          originAddress: 'Origen',
          destinationAddress: 'Destino',
          transportAuthorized: index === 3,
          sunatResponse: index === 2 ? '2335: Documento rechazado' : 'Estado fiscal ' + status,
          hashCode: index === 0 ? null : 'digest',
          ticket: index > 0 ? 'ticket' : null,
        })),
      }),
    );
    await signIn(page);
    await page.goto(prefix + 'dispatch-guides');
    const buttons = page.getByRole('button', { name: 'Descargar PDF de traslado', exact: true });
    await expect(buttons).toHaveCount(4);
    for (let index = 0; index < 3; index++) await expect(buttons.nth(index)).toBeDisabled();
    await expect(buttons.nth(3)).toBeEnabled();
    await expect(page.getByText('No habilita el traslado', { exact: true })).toHaveCount(3);
    await expect(page.getByText('2335: Documento rechazado', { exact: true })).toBeVisible();
  });
}

for (const width of [1440, 390]) {
  test(`shows persisted GRE errors and the scheduled retry without creating another guide at ${width}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
    await carabo(page);
    let retries = 0,
      emissions = 0;
    const row = {
      id: 'gre-existing',
      fullNumber: 'T001-1',
      status: 'RETRY_PENDING',
      receiverName: 'Fixture',
      receiverDoc: '12345678',
      issueDate: '2026-10-31',
      originAddress: 'Origen',
      destinationAddress: 'Destino',
      sunatResponse: 'Resultado guardado de prueba',
      transportAuthorized: false,
      updatedAt: '2026-10-31T18:09:45Z',
      nextRetryAt: '2026-10-31T18:17:45Z',
      retryCount: 4,
      maxRetryCount: 8,
      automaticRetry: true,
      canRetry: true,
      retryAction: 'SEND',
    };
    await page.route('**/carabo/api/dispatch-guides**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('/retry')) {
        retries++;
        return route.fulfill({ status: 202, json: row });
      }
      if (path.endsWith('/emit')) {
        emissions++;
        return route.fulfill({ status: 500, json: {} });
      }
      return route.fulfill({
        json: [
          row,
          {
            ...row,
            id: 'gre-beta',
            canRetry: false,
            automaticRetry: false,
            status: 'BLOCKED_CONFIGURATION',
            retryBlocker:
              'El ambiente BETA no tiene un canal de recepción GRE 2.0 de pruebas en SUNAT',
          },
          {
            ...row,
            id: 'gre-uncertain',
            canRetry: false,
            automaticRetry: false,
            status: 'SIGNED',
            retryBlocker: 'Requiere conciliación antes de reenviar',
          },
        ],
      });
    });
    await signIn(page);
    await page.goto(prefix + 'dispatch-guides');
    await expect(page.getByText('Resultado guardado de prueba', { exact: true })).toHaveCount(3);
    await expect(
      page.getByText('Próximo intento: 31/10/2026 13:17:45 · Lima', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText(
        'El ambiente BETA no tiene un canal de recepción GRE 2.0 de pruebas en SUNAT',
        { exact: true },
      ),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Solicitar reintento', exact: true }),
    ).toHaveCount(1);
    await page.getByRole('button', { name: 'Solicitar reintento', exact: true }).click();
    await expect.poll(() => retries).toBe(1);
    expect(emissions).toBe(0);
  });
}
