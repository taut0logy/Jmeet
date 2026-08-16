create table meeting_session (
  id                   text primary key,
  meeting_id           text not null references meeting(id) on delete cascade,
  occurrence_starts_at timestamptz,
  started_at           timestamptz not null default now(),
  ended_at             timestamptz,
  peak_participants    int not null default 0
);
create index meeting_session_idx on meeting_session (meeting_id, started_at);

create unique index meeting_session_live_uq
  on meeting_session (meeting_id) where ended_at is null;

create table participation (
  id           text primary key,
  session_id   text not null references meeting_session(id) on delete cascade,
  peer_id      text not null,
  user_id      text,
  guest_id     text,
  display_name text not null,
  role         text not null default 'PARTICIPANT',
  joined_at    timestamptz not null default now(),
  left_at      timestamptz,
  admitted_by  text,
  unique (session_id, peer_id)
);
create index participation_session_idx on participation (session_id);

create table chat_message (
  id           text primary key,
  session_id   text not null references meeting_session(id) on delete cascade,
  peer_id      text not null,
  user_id      text,
  display_name text not null,
  body         text not null,
  created_at   timestamptz not null default now()
);
create index chat_session_idx on chat_message (session_id, created_at);
