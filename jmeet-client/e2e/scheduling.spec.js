import { test, expect } from '@playwright/test';
import { signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

test('schedule a weekly recurring meeting and see it on the dashboard', async ({ page, request }) => {
  const email = uniqueEmail('e2e-schedule');
  await signUpVerifyAndSignIn(page, request, { name: 'E2E Scheduler', email });

  await page.getByRole('button', { name: 'Schedule a meeting' }).click();
  await expect(page).toHaveURL(/\/meetings\/new/);

  await page.getByLabel('Title').fill('E2E Weekly Sync');

  // Pick a start time a few minutes from now so it definitely falls inside
  // the dashboard's default 30-day window.
  const startsAt = new Date(Date.now() + 10 * 60 * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  const localValue = `${startsAt.getFullYear()}-${pad(startsAt.getMonth() + 1)}-${pad(startsAt.getDate())}T${pad(startsAt.getHours())}:${pad(startsAt.getMinutes())}`;
  await page.locator('#startsAt').fill(localValue);

  await page.getByLabel('Recurrence').click();
  await page.getByRole('option', { name: 'Weekly' }).click();
  await expect(page.getByText(/Repeats every week/)).toBeVisible();

  await page.getByRole('button', { name: 'Schedule' }).click();
  await expect(page).toHaveURL(/\/meetings\/[a-z0-9]+/);
  await expect(page.getByRole('heading', { name: 'E2E Weekly Sync' })).toBeVisible();

  await page.goto('/dashboard');
  await expect(page.getByText('E2E Weekly Sync').first()).toBeVisible();
});

test('editing "this and following" changes title/time but never the recurrence pattern', async ({ page, request }) => {
  const email = uniqueEmail('e2e-following');
  await signUpVerifyAndSignIn(page, request, { name: 'E2E Following', email });

  await page.getByRole('button', { name: 'Schedule a meeting' }).click();
  await page.getByLabel('Title').fill('E2E Following Series');
  const startsAt = new Date(Date.now() + 10 * 60 * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  await page
    .locator('#startsAt')
    .fill(
      `${startsAt.getFullYear()}-${pad(startsAt.getMonth() + 1)}-${pad(startsAt.getDate())}T${pad(startsAt.getHours())}:${pad(startsAt.getMinutes())}`,
    );
  await page.getByLabel('Recurrence').click();
  await page.getByRole('option', { name: 'Weekly' }).click();
  await page.getByRole('button', { name: 'Schedule' }).click();
  await expect(page).toHaveURL(/\/meetings\/[a-z0-9]+/);

  await page.getByRole('button', { name: 'Manage' }).first().click();
  await page.getByRole('menuitem', { name: 'Edit this and following' }).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  // Only title/time/duration are editable — no recurrence pattern controls
  // exist in this dialog at all (the API rejects a pattern change under
  // this scope; the UI simply never offers one).
  await expect(dialog.getByLabel('Recurrence')).toHaveCount(0);

  await dialog.getByLabel('Title').fill('Renamed From Here On');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Updated this and following occurrences')).toBeVisible();
});
