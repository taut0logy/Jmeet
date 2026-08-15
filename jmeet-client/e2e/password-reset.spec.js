import { test, expect } from '@playwright/test';
import { getLastEmailLink, signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

test('password reset end to end: request -> email link -> new password -> sign in with it', async ({
  page,
  request,
}) => {
  const email = uniqueEmail('e2e-reset');
  const oldPassword = 'correct-horse-battery-staple';
  const newPassword = 'a-totally-different-password-99';

  await signUpVerifyAndSignIn(page, request, { name: 'E2E Reset User', email, password: oldPassword });
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await page.waitForURL(/\/sign-in/);

  await page.goto('/forgot-password');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByText(email)).toBeVisible();

  const resetUrl = await getLastEmailLink(request, { expectSubjectContains: 'Reset your password' });
  expect(resetUrl).toContain('reset-password');

  await page.goto(resetUrl);
  await expect(page).toHaveURL(/localhost:3000\/reset-password/);
  await page.getByLabel('New password').fill(newPassword);
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page).toHaveURL(/\/sign-in/, { timeout: 5000 });

  // The old password must no longer work — stays on /sign-in with an error
  // rather than reaching the dashboard.
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(oldPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.locator('p.text-destructive')).toBeVisible();
  await expect(page).toHaveURL(/\/sign-in/);

  // ...but the new one does.
  await page.getByLabel('Password').fill(newPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
});
