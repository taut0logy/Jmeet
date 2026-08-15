import { test, expect } from '@playwright/test';
import { signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

test('a scheduled meeting created at the 5-minute duration floor shows the ending-soon banner immediately', async ({
  page,
  request,
}) => {
  const email = uniqueEmail('e2e-duration');
  await signUpVerifyAndSignIn(page, request, { name: 'E2E Duration Host', email });

  const res = await page.request.post('/api/meetings', {
    data: {
      title: 'E2E Duration Test',
      kind: 'SCHEDULED',
      startsAt: new Date().toISOString(),
      durationMin: 5,
    },
  });
  expect(res.ok()).toBe(true);
  const { code } = await res.json();

  await page.goto(`/j/${code}`);
  await page.getByRole('button', { name: /Ask to join|Join now/ }).click();
  await page.waitForURL(/\/meeting\//);

  await expect(page.getByTestId('duration-warning-banner')).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId('duration-warning-banner')).toContainText(/This meeting ends in \d+:\d{2}/);
});
