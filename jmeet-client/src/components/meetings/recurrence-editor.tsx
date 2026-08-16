'use client';

import { WEEKDAYS, describeRRule } from '@/lib/recurrence';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const FREQ_OPTIONS = [
  { value: 'none', label: 'Does not repeat' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
];

export function RecurrenceEditor({ value, onChange, disabled }) {
  function set(patch) {
    onChange({ ...value, ...patch });
  }

  function toggleDay(day) {
    const has = value.byday.includes(day);
    set({ byday: has ? value.byday.filter((d) => d !== day) : [...value.byday, day] });
  }

  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <Label htmlFor="recurrence-freq">Recurrence</Label>
        <Select
          value={value.freq || 'none'}
          onValueChange={(v) => set({ freq: v === 'none' ? '' : v })}
          disabled={disabled}
        >
          <SelectTrigger id="recurrence-freq" className="w-full">
            <SelectValue>{(v) => FREQ_OPTIONS.find((o) => o.value === v)?.label ?? v}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            {FREQ_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {value.freq ? (
        <div className="space-y-3 rounded-lg border p-3">
          {value.freq === 'WEEKLY' ? (
            <div className="space-y-1.5">
              <Label>Repeat on</Label>
              <div className="flex gap-1.5">
                {WEEKDAYS.map((d) => (
                  <button
                    key={d.value}
                    type="button"
                    disabled={disabled}
                    onClick={() => toggleDay(d.value)}
                    className={`flex size-8 items-center justify-center rounded-full text-xs font-medium transition-colors ${
                      value.byday.includes(d.value)
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-secondary text-secondary-foreground hover:bg-accent'
                    }`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <div className="space-y-1.5">
            <Label>Ends</Label>
            <div className="flex flex-wrap items-center gap-2">
              <label className="flex items-center gap-1.5 text-sm">
                <input
                  type="radio"
                  checked={value.endType === 'never'}
                  onChange={() => set({ endType: 'never' })}
                  disabled={disabled}
                />
                Never
              </label>
              <label className="flex items-center gap-1.5 text-sm">
                <input
                  type="radio"
                  checked={value.endType === 'on'}
                  onChange={() => set({ endType: 'on' })}
                  disabled={disabled}
                />
                On
              </label>
              {value.endType === 'on' ? (
                <Input
                  type="date"
                  className="w-40"
                  value={value.until}
                  onChange={(e) => set({ until: e.target.value })}
                  disabled={disabled}
                />
              ) : null}
              <label className="flex items-center gap-1.5 text-sm">
                <input
                  type="radio"
                  checked={value.endType === 'after'}
                  onChange={() => set({ endType: 'after' })}
                  disabled={disabled}
                />
                After
              </label>
              {value.endType === 'after' ? (
                <Input
                  type="number"
                  min={1}
                  max={365}
                  className="w-20"
                  value={value.count}
                  onChange={(e) => set({ count: Number(e.target.value) })}
                  disabled={disabled}
                />
              ) : null}
              {value.endType === 'after' ? <span className="text-sm text-muted-foreground">times</span> : null}
            </div>
          </div>

          <p className="text-xs text-muted-foreground">{describeRRule(value)}</p>
        </div>
      ) : null}

      {disabled ? (
        <p className="text-xs text-muted-foreground">
          The recurrence pattern can&apos;t change from here — edit the whole series to change it.
        </p>
      ) : null}
    </div>
  );
}
