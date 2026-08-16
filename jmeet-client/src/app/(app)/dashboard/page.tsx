'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FiCalendar, FiLoader, FiArrowRight, FiVideo } from 'react-icons/fi';
import { api } from '@/lib/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

function formatOccurrence(startsAt) {
  const date = new Date(startsAt);
  return {
    day: date.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' }),
    time: date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }),
  };
}

const TILE_SHADOW = 'shadow-[0_1px_2px_rgb(0_0_0_/_0.04),0_8px_24px_-12px_rgb(0_0_0_/_0.12)] dark:shadow-[0_1px_2px_rgb(0_0_0_/_0.2),0_8px_24px_-12px_rgb(0_0_0_/_0.5)]';

export default function DashboardPage() {
  const router = useRouter();
  const [occurrences, setOccurrences] = useState(null);
  const [joinCode, setJoinCode] = useState('');
  const [creatingInstant, setCreatingInstant] = useState(false);

  useEffect(() => {
    api
      .get('/meetings')
      .then((res) => setOccurrences(res))
      .catch(() => setOccurrences([]));
  }, []);

  async function handleInstantMeeting() {
    setCreatingInstant(true);
    try {
      const meeting = await api.post('/meetings', { title: 'Instant meeting', kind: 'INSTANT' });
      router.push(`/j/${meeting.code}`);
    } finally {
      setCreatingInstant(false);
    }
  }

  function handleJoinByCode(e) {
    e.preventDefault();
    const code = joinCode.trim().toLowerCase();
    if (code) router.push(`/j/${code}`);
  }

  return (
    <div className="space-y-10">
      <div className="grid gap-4 sm:grid-cols-3">
        <button
          type="button"
          onClick={handleInstantMeeting}
          disabled={creatingInstant}
          className={`group flex flex-col items-center justify-center gap-3 rounded-xl bg-primary py-8 text-primary-foreground ring-1 ring-black/5 transition-all hover:-translate-y-0.5 hover:shadow-lg active:translate-y-0 disabled:pointer-events-none disabled:opacity-60 ${TILE_SHADOW}`}
        >
          <span className="flex size-11 items-center justify-center rounded-full bg-primary-foreground/15">
            {creatingInstant ? <FiLoader className="size-5 animate-spin" /> : <FiVideo className="size-5" />}
          </span>
          <span className="text-sm font-semibold">New instant meeting</span>
        </button>

        <Link
          href="/meetings/new"
          className={`group flex flex-col items-center justify-center gap-3 rounded-xl bg-card py-8 text-card-foreground ring-1 ring-foreground/8 transition-all hover:-translate-y-0.5 hover:shadow-lg ${TILE_SHADOW}`}
        >
          <span className="flex size-11 items-center justify-center rounded-full bg-accent text-foreground transition-colors group-hover:bg-primary/12 group-hover:text-primary">
            <FiCalendar className="size-5" />
          </span>
          <span className="text-sm font-semibold">Schedule a meeting</span>
        </Link>

        <Card className="justify-center gap-2 px-5">
          <form onSubmit={handleJoinByCode} className="flex flex-col gap-2">
            <label htmlFor="join-code" className="text-sm font-semibold">
              Join with a code
            </label>
            <div className="flex gap-2">
              <Input
                id="join-code"
                placeholder="abc-defg-hij"
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value)}
              />
              <Button type="submit" size="icon" disabled={!joinCode.trim()}>
                <FiArrowRight className="size-4" />
              </Button>
            </div>
          </form>
        </Card>
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold tracking-tight text-foreground">Upcoming meetings</h2>
        {occurrences === null ? (
          <div className="flex justify-center py-10">
            <FiLoader className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : occurrences.length === 0 ? (
          <Card>
            <CardContent className="py-10 text-center text-sm text-muted-foreground">
              Nothing scheduled in the next 30 days.
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-2">
            {occurrences.map((occ) => {
              const { day, time } = formatOccurrence(occ.startsAt);
              return (
                <Link key={occ.id} href={`/meetings/${occ.id}`}>
                  <Card className="transition-all hover:-translate-y-0.5 hover:shadow-lg">
                    <CardHeader className="flex-row items-center justify-between space-y-0 py-4">
                      <div>
                        <CardTitle className="text-base font-medium">{occ.title}</CardTitle>
                        <p className="text-sm text-muted-foreground">
                          {day} · {time}
                        </p>
                      </div>
                    </CardHeader>
                  </Card>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
