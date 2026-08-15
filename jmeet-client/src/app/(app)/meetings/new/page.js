'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { MeetingForm } from '@/components/meetings/meeting-form';
import { api, ApiError } from '@/lib/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export default function NewMeetingPage() {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(payload) {
    setSubmitting(true);
    try {
      const meeting = await api.post('/meetings', payload);
      toast.success('Meeting scheduled');
      router.push(`/meetings/${meeting.id}`);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Could not schedule the meeting.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Schedule a meeting</CardTitle>
      </CardHeader>
      <CardContent>
        <MeetingForm onSubmit={handleSubmit} submitting={submitting} submitLabel="Schedule" />
      </CardContent>
    </Card>
  );
}
