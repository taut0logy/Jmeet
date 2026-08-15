// Phase A spec §4.5. A state delta applies only when its `rev` is exactly
// one past what we've applied so far — any gap (a missed event, a slow tab,
// reordering) means the caller must call `room:sync` and replace state
// wholesale instead of trying to patch around the gap.
export function isNextRev(rev, localRev) {
  return rev === localRev + 1;
}
