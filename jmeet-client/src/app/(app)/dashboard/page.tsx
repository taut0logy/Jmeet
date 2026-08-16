'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FiPlus, FiCalendar, FiLoader, FiArrowRight, FiVideo } from 'react-icons/fi';
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

export default function DashboardPage() {
  const router = useRouter();
  const [occurrences, setOccurrences] = useState(null);
  const [joinCode, setJoinCode] = useState('');
  const [creatingInstant, setCreatingInstant] = useState(false);

  useEffect(() => {
    api
      .get('/meetings')
      .then((res) => setOccurrences(res.occurrences))
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
    <div className="space-y-8">
      <div className="grid gap-4 sm:grid-cols-3">
        <Button size="lg" className="h-auto flex-col gap-2 py-6" onClick={handleInstantMeeting} disabled={creatingInstant}>
          {creatingInstant ? <FiLoader className="size-5 animate-spin" /> : <FiVideo className="size-5" />}
          New instant meeting
        </Button>
        <Button
          size="lg"
          variant="outline"
          className="h-auto flex-col gap-2 py-6"
          nativeButton={false}
          render={<Link href="/meetings/new" />}
        >
          <FiCalendar className="size-5" />
          Schedule a meeting
        </Button>
        <form onSubmit={handleJoinByCode} className="flex flex-col gap-2 rounded-lg border p-4">
          <label htmlFor="join-code" className="text-sm font-medium">
            Join with a code
          </label>
          <div className="flex gap-2">
            <Input
              id="join-code"
              placeholder="abc-defg-hij"
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value)}
            />
            <Button type="submit" size="icon" variant="secondary" disabled={!joinCode.trim()}>
              <FiArrowRight className="size-4" />
            </Button>
          </div>
        </form>
      </div>

      <div>
        <h2 className="mb-3 text-sm font-medium text-muted-foreground">Upcoming meetings</h2>
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
                <Link key={`${occ.meetingId}-${occ.originalStartsAt}`} href={`/meetings/${occ.meetingId}`}>
                  <Card className="transition-colors hover:bg-accent">
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
