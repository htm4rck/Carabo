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
