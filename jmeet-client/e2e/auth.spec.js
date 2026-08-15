import { test, expect } from '@playwright/test';
import { getLastEmailLink, uniqueEmail } from './helpers.js';

test('sign-up -> verify -> sign-in', async ({ page, request }) => {
  const email = uniqueEmail('e2e-auth');
  const password = 'correct-horse-battery-staple';

  await page.goto('/sign-up');
  await page.getByLabel('Full name').fill('E2E Auth Test');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/verify-email/);
  await expect(page.getByText(email)).toBeVisible();

  const verifyUrl = await getLastEmailLink(request);
  expect(verifyUrl).toContain('verify-email');

  // The link redirects (via callbackURL) back to the client app once verified.
  await page.goto(verifyUrl);
  await expect(page).toHaveURL(/localhost:3000/);

  // Sign in with the now-verified account.
  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByText('New instant meeting')).toBeVisible();
});
