import { defineConfig, devices } from '@playwright/test';

// E2E runs against the real api process (NODE_ENV=test, .env.test's
// meet_test database — see Phase B spec §9.3) and the real Next.js dev
// server. Postgres must already be up (`npm run db:up` at the repo root).
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'retain-on-failure',
    permissions: ['camera', 'microphone'],
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: {
          args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
        },
      },
    },
  ],
  webServer: [
    {
      command: 'node --experimental-strip-types --env-file=.env.test src/api/main.js',
      cwd: '../server',
      url: 'http://localhost:4001/health',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      // Phase A — real mediasoup workers, spawned once and reused across the
      // whole E2E run (reuseExistingServer in dev; CI always starts fresh).
      command: 'node --experimental-strip-types --env-file=.env.test src/sfu/main.js',
      cwd: '../server',
      url: 'http://localhost:4101/health',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      // Composite worker — recording tests need this running or a stopped
      // recording sits at PROCESSING forever (the sfu no longer composites
      // it itself; see server/src/worker/main.js).
      command: 'node --experimental-strip-types --env-file=.env.test src/worker/main.js',
      cwd: '../server',
      url: 'http://localhost:4201/health',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      command: 'npm run dev',
      cwd: '.',
      env: { API_ORIGIN: 'http://localhost:4001' },
      url: 'http://localhost:3000',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
  ],
});
