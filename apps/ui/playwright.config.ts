import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  expect: { timeout: 10000 },
  use: { baseURL: 'http://127.0.0.1:5195/carabo/ui/', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'node tools/serve-ui-test.mjs',
    url: 'http://127.0.0.1:5195/carabo/ui/',
    reuseExistingServer: false,
  },
});
