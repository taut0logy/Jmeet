// Builds/parses the RRULE body stored on Meeting.rrule. No timezone
// conversion happens here — the server's domain/recurrence layer treats the
// whole rrule string (including UNTIL) as "floating": local wall-clock
// digits stored as if they were UTC (see server/src/domain/recurrence/
// timezone.js). So a date/time chosen in this browser's local picker can be
// formatted directly, with no offset math, and it lands correctly.

export const WEEKDAYS = [
  { value: 'MO', label: 'M' },
  { value: 'TU', label: 'T' },
  { value: 'WE', label: 'W' },
  { value: 'TH', label: 'T' },
  { value: 'FR', label: 'F' },
  { value: 'SA', label: 'S' },
  { value: 'SU', label: 'S' },
];

export type RecurrenceState = {
  freq: '' | 'DAILY' | 'WEEKLY' | 'MONTHLY';
  interval: number;
  byday: string[];
  endType: 'never' | 'on' | 'after';
  until: string; // yyyy-mm-dd
  count: number;
};

export function defaultRecurrenceState(startsAt?: Date | string | number): RecurrenceState {
  const d = startsAt ? new Date(startsAt) : new Date();
  return {
    freq: '', // '' = does not repeat
    interval: 1,
    byday: [WEEKDAYS[(d.getDay() + 6) % 7].value], // today's weekday, Mon-first
    endType: 'never', // 'never' | 'on' | 'after'
    until: '', // yyyy-mm-dd
    count: 10,
  };
}

/** Formats a floating (local, no-offset) date-only string as an inclusive end-of-day UNTIL. */
function dateOnlyToFloatingUntil(dateStr: string) {
  return dateStr.replaceAll('-', '') + 'T235959Z';
}

export function buildRRuleString(state: RecurrenceState): string | null {
  if (!state.freq) return null;
  const parts = [`FREQ=${state.freq}`];
  if (state.interval > 1) parts.push(`INTERVAL=${state.interval}`);
  if (state.freq === 'WEEKLY' && state.byday.length) parts.push(`BYDAY=${state.byday.join(',')}`);
  if (state.endType === 'on' && state.until) parts.push(`UNTIL=${dateOnlyToFloatingUntil(state.until)}`);
  if (state.endType === 'after' && state.count) parts.push(`COUNT=${state.count}`);
  return parts.join(';');
}

export function parseRRuleString(rrule?: string | null): RecurrenceState {
  const base = defaultRecurrenceState();
  if (!rrule) return base;

  const fields = Object.fromEntries(rrule.split(';').map((p) => p.split('=')));
  const state: RecurrenceState = { ...base, freq: (fields.FREQ as RecurrenceState['freq']) ?? '' };
  if (fields.INTERVAL) state.interval = Number(fields.INTERVAL);
  if (fields.BYDAY) state.byday = fields.BYDAY.split(',');
  if (fields.UNTIL) {
    state.endType = 'on';
    const m = /^(\d{4})(\d{2})(\d{2})T/.exec(fields.UNTIL);
    if (m) state.until = `${m[1]}-${m[2]}-${m[3]}`;
  } else if (fields.COUNT) {
    state.endType = 'after';
    state.count = Number(fields.COUNT);
  }
  return state;
}

export function describeRRule(state: RecurrenceState): string {
  if (!state.freq) return 'Does not repeat';
  const every = state.interval > 1 ? `every ${state.interval} ` : 'every ';
  const unit = { DAILY: 'day', WEEKLY: 'week', MONTHLY: 'month' }[state.freq];
  const unitPlural = state.interval > 1 ? unit + 's' : unit;
  let text = `Repeats ${every}${unitPlural}`;
  if (state.freq === 'WEEKLY' && state.byday.length) {
    const names = state.byday.map((d) => WEEKDAYS.find((w) => w.value === d)?.label ?? d);
    text += ` on ${names.join(', ')}`;
  }
  if (state.endType === 'on' && state.until) text += `, until ${state.until}`;
  if (state.endType === 'after' && state.count) text += `, ${state.count} times`;
  return text;
}
