'use client';

// `score` runs 0 (unusable) to 10 (perfect); bucketed into 3 signal bars,
// matching Meet's convention.
//
// TODO(livekit): LiveKit reports connection quality as a
// ConnectionQuality enum (EXCELLENT/GOOD/POOR/LOST), not a 0-10 number —
// map it to this same 0-10 scale at the call site (e.g. EXCELLENT/GOOD -> 8,
// POOR -> 3, LOST -> 0) rather than changing this component's contract.
export function QualityBadge({ score }) {
  if (score == null) return null;
  const level = score >= 7 ? 3 : score >= 4 ? 2 : score >= 1 ? 1 : 0;
  const label = level === 3 ? 'Good connection' : level === 0 ? 'Poor connection' : 'Fair connection';

  return (
    <div className="flex items-end gap-0.5" aria-label={label} data-testid="quality-badge" data-level={level}>
      {[1, 2, 3].map((bar) => (
        <span
          key={bar}
          className={`w-1 rounded-sm ${bar <= level ? 'bg-emerald-400' : 'bg-white/25'}`}
          style={{ height: `${bar * 3 + 3}px` }}
        />
      ))}
    </div>
  );
}
