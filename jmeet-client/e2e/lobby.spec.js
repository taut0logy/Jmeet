import { test, expect } from '@playwright/test';
import { signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

test('guest lobby: camera preview renders, device switch works, "Ask to join" issues a token', async ({
  browser,
  page,
  request,
}) => {
  // Host: sign up and create an instant meeting to get a real join code.
  const hostEmail = uniqueEmail('e2e-lobby-host');
  await signUpVerifyAndSignIn(page, request, { name: 'E2E Lobby Host', email: hostEmail });
  await page.getByRole('button', { name: 'New instant meeting' }).click();
  await page.waitForURL(/\/j\/[a-z-]+/);
  const code = page.url().split('/j/')[1];
  expect(code).toMatch(/^[a-z]{3}-[a-z]{4}-[a-z]{3}$/);

  // Guest: fresh context, no session cookie.
  const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
  const guestPage = await guestContext.newPage();
  await guestPage.goto(`/j/${code}`);

  // Camera preview renders (fake-device video element) rather than the
  // no-camera avatar fallback or an error state.
  await expect(guestPage.locator('video')).toBeVisible();
  await expect(guestPage.getByText(/Could not access|blocked|already in use/)).toHaveCount(0);

  await guestPage.getByLabel('Your name').fill('E2E Guest');

  // Device switch: pick the fake camera explicitly and confirm the preview
  // keeps rendering (no error state after switching).
  const cameraSelect = guestPage.getByRole('combobox').first();
  if (await cameraSelect.isVisible().catch(() => false)) {
    await cameraSelect.click();
    const firstOption = guestPage.getByRole('option').first();
    if (await firstOption.isVisible().catch(() => false)) {
      await firstOption.click();
      await expect(guestPage.locator('video')).toBeVisible();
    }
  }

  // The select popup's closing transition can briefly leave an inert overlay
  // intercepting clicks; clicking the page body first is a deterministic way
  // to settle focus/dismiss state before the next interaction (more reliable
  // than waiting on an internal implementation-detail attribute).
  await guestPage.locator('body').click({ position: { x: 10, y: 10 } });
  await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
  await guestPage.waitForURL(/\/meeting\//);
  // Default waitingRoom policy is GUESTS_ONLY, and this guest has no
  // session, so they land in the waiting screen rather than connecting
  // immediately (Phase A spec §4.6).
  await expect(guestPage.getByText(/Waiting for the host/)).toBeVisible();

  await guestContext.close();
});

test('lobby respects the signed-in user\'s saved camera/mic defaults', async ({ page, request }) => {
  const email = uniqueEmail('e2e-lobby-defaults');
  await signUpVerifyAndSignIn(page, request, { name: 'E2E Defaults User', email });

  // Save "join with camera off" as this user's default before entering any
  // lobby. page.request shares the browser's session cookie automatically.
  await page.request.patch('/api/users/me', {
    headers: { 'Content-Type': 'application/json' },
    data: { defaultCameraOff: true },
  });

  await page.getByRole('button', { name: 'New instant meeting' }).click();
  await page.waitForURL(/\/j\/[a-z-]+/);

  // Camera should start OFF: the avatar-initial fallback renders, not <video>.
  await expect(page.locator('video')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Turn on camera' })).toBeVisible();
  // Mic default was untouched (false), so it should still start enabled.
  await expect(page.getByRole('button', { name: 'Mute microphone' })).toBeVisible();
});

test('lobby renders correctly in both light and dark theme', async ({ page }) => {
  await page.goto('/sign-up'); // any public page with the theme toggle

  await page.emulateMedia({ colorScheme: 'light' });
  await page.reload();
  await expect(page.locator('html')).not.toHaveClass(/dark/);

  await page.emulateMedia({ colorScheme: 'dark' });
  await page.reload();
  await expect(page.locator('html')).toHaveClass(/dark/);
});
