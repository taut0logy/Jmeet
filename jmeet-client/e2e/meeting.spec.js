import { test, expect } from '@playwright/test';
import { signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

// Milestone A1 exit criteria (Phase A spec §8): two real browsers see and
// hear each other with getStats() proving bytes flow; a knocker is admitted
// by a host; killing the WebSocket does not interrupt media; a reload
// rejoins as the same participant.
//
// Each test spins up real mediasoup workers and multiple browser contexts
// doing actual ICE/DTLS negotiation — heavier than the rest of the suite, so
// this file gets a longer timeout than the default 30s.
test.setTimeout(60000);

// Captures every RTCPeerConnection mediasoup-client creates so the test can
// call the real getStats() API — the only actual proof media is flowing,
// as opposed to just "tiles rendered" (spec §9).
async function installPeerConnectionCapture(page) {
  await page.addInitScript(() => {
    window.__pcs = [];
    const OrigPC = window.RTCPeerConnection;
    window.RTCPeerConnection = new Proxy(OrigPC, {
      construct(target, args) {
        const pc = new target(...args);
        window.__pcs.push(pc);
        return pc;
      },
    });
  });
}

async function totalInboundBytesReceived(page) {
  return page.evaluate(async () => {
    let sum = 0;
    for (const pc of window.__pcs ?? []) {
      const stats = await pc.getStats();
      stats.forEach((report) => {
        if (report.type === 'inbound-rtp' && (report.bytesReceived ?? 0) > 0) {
          sum += report.bytesReceived;
        }
      });
    }
    return sum;
  });
}

async function waitForInboundMedia(page, { timeoutMs = 20000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  let last = 0;
  while (Date.now() < deadline) {
    last = await totalInboundBytesReceived(page);
    if (last > 0) return last;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`No inbound-rtp bytesReceived observed within ${timeoutMs}ms (last=${last})`);
}

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

// The bytesReceived check above sums every inbound-rtp report regardless of
// kind, so it would pass even if audio was negotiated but never attached to
// a playing element (the bug found while building the speaker-selector
// feature — see RemoteAudioPlayers). This proves actual audible playback:
// a real <audio> element exists with a live audio track attached, and its
// currentTime is advancing, which only happens once the browser is
// genuinely decoding and playing the stream.
async function waitForAudioPlayback(page, { timeoutMs = 20000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const playing = await page.evaluate(() => {
      const audios = Array.from(document.querySelectorAll('audio'));
      return audios.some((el) => {
        const tracks = el.srcObject?.getAudioTracks?.() ?? [];
        return tracks.length > 0 && el.currentTime > 0 && !el.paused;
      });
    });
    if (playing) return true;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`No playing <audio> element with a live track found within ${timeoutMs}ms`);
}

test.describe('meeting pipeline (Milestone A1)', () => {
  test('two participants see and hear each other; media survives a signaling socket drop', async ({
    page,
    browser,
    request,
  }) => {
    await installPeerConnectionCapture(page);
    const code = await createInstantMeetingAsHost(page, request, { name: 'E2E Host A', emailLabel: 'e2e-meeting-host-a' });
    await expect(page.getByTestId('participant-tile')).toHaveCount(1, { timeout: 10000 });

    // A second signed-in user joins — GUESTS_ONLY (the default) only gates
    // guests, so a signed-in second participant connects immediately.
    const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
    const guestPage = await guestContext.newPage();
    await installPeerConnectionCapture(guestPage);
    const guestEmail = uniqueEmail('e2e-meeting-guest');
    await signUpVerifyAndSignIn(guestPage, request, { name: 'E2E Guest A', email: guestEmail });
    await guestPage.goto(`/j/${code}`);
    await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
    await guestPage.waitForURL(/\/meeting\//);

    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });
    await expect(guestPage.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    // Real proof media is flowing, not just that tiles rendered.
    const hostBytes = await waitForInboundMedia(page);
    const guestBytes = await waitForInboundMedia(guestPage);
    expect(hostBytes).toBeGreaterThan(0);
    expect(guestBytes).toBeGreaterThan(0);

    // Sever the network (HTTP/WS) without reloading — the closest Playwright
    // gets to "kill the WebSocket" without touching the RTCPeerConnections,
    // which use their own UDP/TCP sockets independent of this.
    await guestContext.setOffline(true);
    await new Promise((r) => setTimeout(r, 2000));
    await guestContext.setOffline(false);

    // Media must not have stopped — bytes keep climbing across the drop.
    const guestBytesAfter = await waitForInboundMedia(guestPage);
    expect(guestBytesAfter).toBeGreaterThanOrEqual(guestBytes);
    await expect(guestPage.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    await guestContext.close();
  });

  test('remote peer audio is actually attached and playing, not just negotiated', async ({ page, browser, request }) => {
    // mediasoup's ActiveSpeakerObserver dominance resolution between two
    // near-identical fake-audio devices is genuinely slower/more variable
    // than with two distinct real voices — extra headroom over the file's
    // default 60s avoids that being mistaken for a regression.
    test.setTimeout(90000);
    const code = await createInstantMeetingAsHost(page, request, { name: 'E2E Host D', emailLabel: 'e2e-meeting-host-d' });
    await expect(page.getByTestId('participant-tile')).toHaveCount(1, { timeout: 10000 });

    const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
    const guestPage = await guestContext.newPage();
    const guestEmail = uniqueEmail('e2e-meeting-audio-guest');
    await signUpVerifyAndSignIn(guestPage, request, { name: 'E2E Audio Guest', email: guestEmail });
    await guestPage.goto(`/j/${code}`);
    await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
    await guestPage.waitForURL(/\/meeting\//);

    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });
    await expect(guestPage.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    await waitForAudioPlayback(page, { timeoutMs: 30000 });
    await waitForAudioPlayback(guestPage, { timeoutMs: 30000 });

    await guestContext.close();
  });

  test('a waiting guest is admitted by the host and then connects', async ({ page, browser, request }) => {
    const code = await createInstantMeetingAsHost(page, request, { name: 'E2E Host B', emailLabel: 'e2e-meeting-host-b' });
    await expect(page.getByTestId('participant-tile')).toHaveCount(1);

    // A guest with no session hits the default GUESTS_ONLY waiting room.
    const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
    const guestPage = await guestContext.newPage();
    await guestPage.goto(`/j/${code}`);
    await guestPage.getByLabel('Your name').fill('E2E Knocker');
    await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
    await guestPage.waitForURL(/\/meeting\//);
    await expect(guestPage.getByText(/Waiting for the host/)).toBeVisible();

    // The host sees a badge on the Participants control and opens the
    // waiting-room panel to admit them (Milestone A2 replaced the A1
    // always-visible inline bar with a dedicated panel — spec §4.6 decision
    // A3). Clicking Participants while someone is waiting jumps straight to
    // the Waiting tab.
    await expect(page.getByTestId('waiting-badge')).toHaveText('1', { timeout: 10000 });
    await page.getByRole('button', { name: 'Participants' }).click();
    await expect(page.getByText('E2E Knocker')).toBeVisible();
    await page.getByRole('button', { name: /Admit E2E Knocker/ }).click();

    // The guest transitions from waiting to actually being in the call.
    await expect(guestPage.getByText(/Waiting for the host/)).toHaveCount(0, { timeout: 10000 });
    await expect(guestPage.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    await guestContext.close();
  });

  test('reloading the meeting page rejoins as the same participant, not a duplicate', async ({
    page,
    browser,
    request,
  }) => {
    const code = await createInstantMeetingAsHost(page, request, { name: 'E2E Host C', emailLabel: 'e2e-meeting-host-c' });

    const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
    const guestPage = await guestContext.newPage();
    const guestEmail = uniqueEmail('e2e-meeting-reload-guest');
    await signUpVerifyAndSignIn(guestPage, request, { name: 'E2E Reload Guest', email: guestEmail });
    await guestPage.goto(`/j/${code}`);
    await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
    await guestPage.waitForURL(/\/meeting\//);

    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    await guestPage.reload();
    await expect(guestPage.getByTestId('participant-tile')).toHaveCount(2, { timeout: 20000 });

    // From the host's side, the roster settles back to exactly 2 — never 3
    // (which would mean the reload created a second, duplicate participant).
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 20000 });

    await guestContext.close();
  });

  // Regression: opening either device selector crashed the whole meeting page
  // ("This page couldn't load"). Base UI's Menu.GroupLabel reads
  // MenuGroupContext and throws if it has no Menu.Group/Menu.RadioGroup
  // parent — unlike Radix, where a standalone label is fine — and the shadcn
  // port renders DropdownMenuLabel as exactly that primitive. The label was
  // a sibling of the radio group rather than a child of it, so every menu in
  // the control bar was a landmine. Nothing caught it because the menu
  // contents only mount on open, inside a portal.
  test('the mic and camera device selectors open without crashing the page', async ({ page, request }) => {
    const pageErrors = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await createInstantMeetingAsHost(page, request, { name: 'E2E Host D', emailLabel: 'e2e-meeting-host-d' });

    // Chromium's --use-fake-device-for-media-stream gives enumerateDevices a
    // real fake mic/camera, so the menus render their device lists for real.
    for (const menuLabel of ['Microphone', 'Camera']) {
      await page.getByRole('button', { name: menuLabel, exact: true }).click();
      // A radio item proves the group actually mounted, not just the popup.
      await expect(page.getByRole('menuitemradio').first()).toBeVisible({ timeout: 10000 });
      await page.keyboard.press('Escape');
    }

    // The control bar must still be interactive — a React render throw
    // replaces the tree with the error boundary, which this would not survive.
    await expect(page.getByRole('button', { name: /Mute microphone|Unmute microphone/ })).toBeVisible();

    const contextErrors = pageErrors.filter((m) => /MenuGroupContext|Menu group parts/.test(m));
    expect(contextErrors, `Base UI menu context errors: ${contextErrors.join(' | ')}`).toEqual([]);
  });
});
