const API_TEST_ORIGIN = 'http://localhost:4001';

/**
 * Reads the last "sent" email (console transport, test-only route) and pulls
 * the first link out of it. Polls briefly rather than reading once: the
 * request that triggers an email (e.g. password reset) can resolve its HTTP
 * response slightly before /test/last-email reflects it, so a single
 * immediate read can observe a stale (previous) message.
 *
 * `expectSubjectContains` lets a caller wait for a *specific* email when more
 * than one might have been sent in the flow (e.g. verify-email then a
 * password reset) rather than trusting whatever is currently last.
 */
export async function getLastEmailLink(request, { expectSubjectContains, timeoutMs = 5000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastBody;
  while (Date.now() < deadline) {
    const res = await request.get(`${API_TEST_ORIGIN}/test/last-email`);
    lastBody = await res.json();
    if (!expectSubjectContains || lastBody?.subject?.includes(expectSubjectContains)) {
      const match = /https?:\/\/[^\s"<]+/.exec(lastBody?.text ?? lastBody?.html ?? '');
      if (match) return match[0];
    }
    await new Promise((r) => setTimeout(r, 150));
  }
  throw new Error(`No matching email found within ${timeoutMs}ms. Last seen: ${JSON.stringify(lastBody)}`);
}

export function uniqueEmail(label) {
  return `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
}

/** Full sign-up -> verify -> sign-in flow through the real UI, leaving the page on /dashboard. */
export async function signUpVerifyAndSignIn(page, request, { name, email, password = 'correct-horse-battery-staple' }) {
  await page.goto('/sign-up');
  await page.getByLabel('Full name').fill(name);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.waitForURL(/\/verify-email/);

  const verifyUrl = await getLastEmailLink(request);
  await page.goto(verifyUrl);

  await page.goto('/sign-in');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(/\/dashboard/);
}
