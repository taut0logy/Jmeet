import { test, expect } from '@playwright/test';
import { spawn } from 'node:child_process';
import { writeFile, unlink } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { signUpVerifyAndSignIn, uniqueEmail } from './helpers.js';

// Phase C spec §7/§10 (Milestone C3 exit criterion): "a host records from
// the browser and downloads the MP4." Deliberately short — §3.8 exists
// because this machine has little disk to spare — but every step is real:
// two real browsers, the real Record button, the real always-visible
// indicator on both sides, the real composite pipeline, and the resulting
// file fetched and verified with ffprobe, not just asserted "some link
// exists."
test.setTimeout(90000);

function ffprobe(filePath) {
  return new Promise((resolve, reject) => {
    const ff = spawn('ffprobe', [
      '-v', 'error',
      '-show_entries', 'stream=codec_type,codec_name:format=duration',
      '-of', 'json',
      filePath,
    ]);
    let stdout = '';
    let stderr = '';
    ff.stdout.on('data', (c) => (stdout += c));
    ff.stderr.on('data', (c) => (stderr += c));
    ff.on('exit', (code) => (code === 0 ? resolve(JSON.parse(stdout)) : reject(new Error(stderr))));
  });
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

test.describe('recording (Milestone C3)', () => {
  test('a host records, both participants see the indicator, and the meeting detail page offers a real, playable download', async ({
    page,
    browser,
    request,
  }) => {
    const code = await createInstantMeetingAsHost(page, request, {
      name: 'C3 Host',
      emailLabel: 'c3-rec-host',
    });

    const meetingRes = await request.get(`http://localhost:4001/api/meetings/by-code/${code}`);
    const { id: meetingId } = await meetingRes.json();

    const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
    const guestPage = await guestContext.newPage();
    await signUpVerifyAndSignIn(guestPage, request, { name: 'C3 Guest', email: uniqueEmail('c3-rec-guest') });
    await guestPage.goto(`/j/${code}`);
    await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
    await guestPage.waitForURL(/\/meeting\//);
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    // Only host/cohost see the Record button at all.
    await expect(guestPage.getByTestId('record-button')).toHaveCount(0);

    await page.getByTestId('record-button').click();
    await expect(page.getByTestId('recording-indicator')).toBeVisible({ timeout: 10000 });
    // The indicator is in the always-visible header — reachable without
    // opening any panel (Phase A gotcha #23's lesson, spec §6).
    await expect(guestPage.getByTestId('recording-indicator')).toBeVisible({ timeout: 10000 });

    await page.waitForTimeout(6000); // let real media accumulate

    await page.getByTestId('record-button').click();
    await expect(page.getByTestId('recording-indicator')).toHaveCount(0, { timeout: 10000 });
    await expect(guestPage.getByTestId('recording-indicator')).toHaveCount(0, { timeout: 10000 });

    await page.goto(`/meetings/${meetingId}`);
    // Base UI's Button always advertises ARIA role="button", even rendered
    // as an <a> via the render prop (confirmed against this same page's
    // pre-existing "Join" button, which renders identically) — so this is a
    // button by role, not a link, despite being a real <a href download>.
    const downloadLink = page.getByRole('button', { name: 'Download' });
    await expect(downloadLink).toBeVisible({ timeout: 30000 });

    const href = await downloadLink.getAttribute('href');
    expect(href).toMatch(/^http:\/\/localhost:4001\/files\/recordings\//);

    const fileRes = await request.get(href);
    expect(fileRes.ok()).toBe(true);
    const buffer = await fileRes.body();
    expect(buffer.length).toBeGreaterThan(0);

    const tmpPath = path.join(os.tmpdir(), `e2e-recording-${Date.now()}.mp4`);
    await writeFile(tmpPath, buffer);
    try {
      const probe = await ffprobe(tmpPath);
      const kinds = probe.streams.map((s) => s.codec_type).sort();
      expect(kinds).toEqual(['audio', 'video']);
      expect(probe.streams.find((s) => s.codec_type === 'video').codec_name).toBe('h264');
      expect(probe.streams.find((s) => s.codec_type === 'audio').codec_name).toBe('aac');
      expect(Number(probe.format.duration)).toBeGreaterThan(3);
    } finally {
      await unlink(tmpPath).catch(() => {});
    }

    await guestContext.close();
  });

  test('a participant cannot start recording (server-side authorization, not just a hidden button)', async ({
    page,
    browser,
    request,
  }) => {
    const code = await createInstantMeetingAsHost(page, request, {
      name: 'C3 Host2',
      emailLabel: 'c3-authz-host',
    });
    const guestContext = await browser.newContext({ permissions: ['camera', 'microphone'] });
    const guestPage = await guestContext.newPage();
    await signUpVerifyAndSignIn(guestPage, request, { name: 'C3 Participant', email: uniqueEmail('c3-authz-guest') });
    await guestPage.goto(`/j/${code}`);
    await guestPage.getByRole('button', { name: /Ask to join|Join now/ }).click();
    await guestPage.waitForURL(/\/meeting\//);
    await expect(page.getByTestId('participant-tile')).toHaveCount(2, { timeout: 15000 });

    // The plain participant's control bar never renders the Record button
    // at all — spec §6's HOST/COHOST-only gate, verified through the real UI.
    await expect(guestPage.getByTestId('record-button')).toHaveCount(0);
  });
});
