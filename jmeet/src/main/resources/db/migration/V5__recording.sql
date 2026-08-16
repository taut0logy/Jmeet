create table recording (
  id           text primary key,
  meeting_id   text not null references meeting(id) on delete cascade,
  session_id   text not null references meeting_session(id) on delete cascade,
  egress_id    text unique,
  started_by   text,
  started_at   timestamptz not null default now(),
  ended_at     timestamptz,
  status       text not null default 'RECORDING',
  layout       text,
  storage_key  text,
  duration_ms  int,
  size_bytes   bigint,
  error        text
);
create index recording_meeting_idx on recording (meeting_id, started_at);
create index recording_status_idx  on recording (status) where status in ('RECORDING','PROCESSING');
