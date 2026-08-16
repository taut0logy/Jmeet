create table job_record (
  message_id  uuid primary key,
  type        text        not null,
  payload     jsonb       not null,
  status      text        not null,
  attempts    int         not null default 0,
  last_error  text,
  started_at  timestamptz,
  finished_at timestamptz,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
create index job_record_status_idx on job_record (status, updated_at)
  where status in ('RUNNING','RETRYING','DEAD');

create table outbox_event (
  id              uuid primary key,
  aggregate_type  text        not null,
  aggregate_id    text        not null,
  type            text        not null,
  payload         jsonb       not null,
  attempts        int         not null default 0,
  next_attempt_at timestamptz not null default now(),
  last_error      text,
  published_at    timestamptz,
  failed_at       timestamptz,
  created_at      timestamptz not null default now()
);
create index outbox_pending_idx on outbox_event (next_attempt_at)
  where published_at is null and failed_at is null;

create table shedlock (
  name       text primary key,
  lock_until timestamptz not null,
  locked_at  timestamptz not null,
  locked_by  text        not null
);
