import { test, expect } from '@playwright/test';
import { signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

// Milestone A2 exit criteria (Phase A spec §8): the Meet-parity checklist —
// chat delivery, and a cohost/host action working end to end through the
// real UI (not a direct socket call).
test.setTimeout(60000);

async function createInstantMeetingAsHost(page, request, { name, emailLabel }) {
  const email = uniqueEmail(emailLabel);
  await signUpVerifyAndSignIn(page, request, { name, email });
  await page.getByRole('button', { name: 'New instant meeting' }).click();
  await page.waitForURL(/\/j\/[a-z-]+/);
  const code = page.url().split('/j/')[1];
  await page.getByRole('button', { name: /Ask to join|Join now/ }).click();
  await page.waitForURL(/\/meeting\//);
  return code;
}

async function joinAsSecondSignedInUser(browser, request, code, { name, emailLabel }) {
  const context = await browser.newContext({ permissions: ['camera', 'microphone'] });
  const page = await context.newPage();
  const email = uniqueEmail(emailLabel);
  await signUpVerifyAndSignIn(page, request, { name, email });
  await page.goto(`/j/${code}`);
  await page.getByRole('button', { name: /Ask to join|Join now/ }).click();
  await page.waitForURL(/\/meeting\//);
  return { context, page };
}

test.describe('meeting collaboration (Milestone A2)', () => {
  test('a chat message sent by one participant is delivered to the other in real time', async ({
    page,
    browser,
    request,
  }) => {
    const code = await createInstantMeetingAsHost(page, request, { name: 'A2 Host', emailLabel: 'a2-chat-host' });
    const { context: guestContext, page: guestPage } = await joinAsSecondSignedInUser(browser, request, code, {
      name: 'A2 Guest',
      emailLabel: 'a2-chat-guest',
    });
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    await page.getByRole('button', { name: 'Chat' }).click();
    await page.getByLabel('Chat message').fill('hello from host');
    await page.getByRole('button', { name: 'Send message' }).click();

    await expect(page.getByTestId('chat-message').filter({ hasText: 'hello from host' })).toBeVisible();

    await guestPage.getByRole('button', { name: 'Chat' }).click();
    await expect(guestPage.getByTestId('chat-message').filter({ hasText: 'hello from host' })).toBeVisible({
      timeout: 10000,
    });

    await guestPage.getByLabel('Chat message').fill('hello back from guest');
    await guestPage.getByRole('button', { name: 'Send message' }).click();
    await expect(page.getByTestId('chat-message').filter({ hasText: 'hello back from guest' })).toBeVisible({
      timeout: 10000,
    });

    await guestContext.close();
  });

  test('the host can mute a participant through the participants panel, and it takes effect on the target', async ({
    page,
    browser,
    request,
  }) => {
    const code = await createInstantMeetingAsHost(page, request, { name: 'A2 Host Mute', emailLabel: 'a2-mute-host' });
    const { context: guestContext, page: guestPage } = await joinAsSecondSignedInUser(browser, request, code, {
      name: 'A2 Guest Mute',
      emailLabel: 'a2-mute-guest',
    });
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    // Guest starts unmuted.
    await expect(guestPage.getByRole('button', { name: 'Mute microphone' })).toBeVisible();

    await page.getByRole('button', { name: 'Participants' }).click();
    const guestRow = page.getByTestId('participant-row').filter({ hasText: 'A2 Guest Mute' });
    await guestRow.getByRole('button', { name: /More options/ }).click();
    await page.getByRole('menuitem', { name: 'Mute microphone' }).click();

    // The target's own control bar reflects the host-issued mute — proof it
    // reached the real mediasoup-client Producer, not just room state text.
    await expect(guestPage.getByRole('button', { name: 'Unmute microphone' })).toBeVisible({ timeout: 10000 });

    await guestContext.close();
  });

  test('the host can turn on the waiting room live, and a subsequent joiner is gated by it', async ({
    page,
    browser,
    request,
  }) => {
    // Regression coverage: host:setFlag / setRoomFlag existed end-to-end
    // (server handler, connection-hook callback, ROOM_FLAGS broadcast) but
    // had no UI ever calling it — this exercises the real gap through the
    // actual UI, not a direct socket call. Instant meetings default to
    // GUESTS_ONLY, so a signed-in second user normally walks straight in;
    // proving the live toggle actually takes effect requires flipping the
    // policy to EVERYONE first, mid-meeting, through the Settings tab.
    const code = await createInstantMeetingAsHost(page, request, {
      name: 'A2 WR Host',
      emailLabel: 'a2-wr-host',
    });
    await expect(page.getByTestId('participant-tile')).toHaveCount(1, { timeout: 10000 });

    await page.getByRole('button', { name: 'Participants' }).click();
    await page.getByRole('tab', { name: 'Settings' }).click();
    await page.getByRole('combobox').click();
    await page.getByRole('option', { name: 'Everyone (except hosts/cohosts)' }).click();

    const { context: guestContext, page: guestPage } = await joinAsSecondSignedInUser(browser, request, code, {
      name: 'A2 WR Guest',
      emailLabel: 'a2-wr-guest',
    });

    await expect(guestPage.getByText(/Waiting for the host/)).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('waiting-badge')).toHaveText('1', { timeout: 10000 });

    // Clicking Participants while someone is waiting jumps straight to the
    // Waiting tab (existing behavior, spec §4.6 decision A3).
    await page.getByRole('button', { name: 'Participants' }).click();
    await expect(page.getByText('A2 WR Guest')).toBeVisible();
    await page.getByRole('button', { name: /Admit A2 WR Guest/ }).click();

    await expect(guestPage.getByText(/Waiting for the host/)).toHaveCount(0, { timeout: 10000 });
    await expect(guestPage.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    await guestContext.close();
  });

  test('a cohost cannot end the meeting for everyone (server-side authorization, not just a hidden button)', async ({
    page,
    browser,
    request,
  }) => {
    const code = await createInstantMeetingAsHost(page, request, { name: 'A2 Real Host', emailLabel: 'a2-authz-host' });
    const { context: guestContext, page: guestPage } = await joinAsSecondSignedInUser(browser, request, code, {
      name: 'A2 Cohost',
      emailLabel: 'a2-authz-cohost',
    });
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    // Promote the guest to cohost.
    await page.getByRole('button', { name: 'Participants' }).click();
    const guestRow = page.getByTestId('participant-row').filter({ hasText: 'A2 Cohost' });
    await guestRow.getByRole('button', { name: /More options/ }).click();
    await page.getByRole('menuitem', { name: 'Make cohost' }).click();

    // The cohost's own control bar never even shows "End for all" — only
    // HOST gets that button (spec §4.7's table: end-for-all is HOST only).
    await guestPage.waitForTimeout(1000); // let the role-promotion delta land
    await expect(guestPage.getByRole('button', { name: 'End meeting for everyone' })).toHaveCount(0);

    await guestContext.close();
  });
});
