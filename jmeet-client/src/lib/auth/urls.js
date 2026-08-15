// Better Auth's email links (verification, password reset) are built from
// BETTER_AUTH_URL — the api process's own origin, not the client's. A
// relative callbackURL/redirectTo (e.g. "/dashboard") gets embedded as-is
// and resolves against that api origin when the link is clicked, landing
// the user on the bare API server instead of the app. Verified directly:
// only an ABSOLUTE client-origin URL survives the round trip correctly.
// Every callbackURL/redirectTo passed to authClient must go through this.
export function clientUrl(path) {
  return `${window.location.origin}${path}`;
}
