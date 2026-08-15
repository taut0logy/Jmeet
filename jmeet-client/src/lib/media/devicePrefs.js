// Bridges a device choice made in the pre-join lobby (a different route,
// /j/[code]) to the meeting page (/meeting/[code]) — React state doesn't
// survive that navigation, so this is sessionStorage, same mechanism
// already used for the join token itself. Global (not per-meeting-code):
// "which physical device this browser should use" isn't meeting-specific.
const KEY = 'meet:devicePrefs';

export function loadDevicePrefs() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY) ?? '{}');
  } catch {
    return {};
  }
}

export function saveDevicePrefs(patch) {
  sessionStorage.setItem(KEY, JSON.stringify({ ...loadDevicePrefs(), ...patch }));
}
