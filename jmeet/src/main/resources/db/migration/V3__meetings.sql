create table meeting (
  id                  text primary key,
  code                text        not null unique,
  title               text        not null,
  description         text,
  owner_id            text        not null references app_user(id),
  kind                text        not null default 'SCHEDULED',
  status              text        not null default 'SCHEDULED',
  starts_at           timestamptz,
  duration_min        int                  default 60,
  timezone            text        not null default 'UTC',
  rrule               text,
  series_ends_at      timestamptz,
  access              text        not null default 'LINK',
  waiting_room        text        not null default 'GUESTS_ONLY',
  allow_guests        boolean     not null default true,
  mute_on_entry       boolean     not null default false,
  camera_off_on_entry boolean     not null default false,
  locked_at           timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);
create index meeting_owner_idx on meeting (owner_id, starts_at);

create table meeting_occurrence (
  id                 text primary key,
  meeting_id         text not null references meeting(id) on delete cascade,
  original_starts_at timestamptz not null,
  status             text not null default 'SCHEDULED',
  starts_at          timestamptz,
  duration_min       int,
  title              text,
  unique (meeting_id, original_starts_at)
);

create table meeting_series_override (
  id               text primary key,
  meeting_id       text not null references meeting(id) on delete cascade,
  from_starts_at   timestamptz not null,
  title            text,
  duration_min     int,
  start_time_local text
);
create index mso_meeting_idx on meeting_series_override (meeting_id, from_starts_at);

create table meeting_member (
  id         text primary key,
  meeting_id text not null references meeting(id) on delete cascade,
  user_id    text references app_user(id) on delete cascade,
  email      citext,
  role       text not null default 'INVITEE',
  created_at timestamptz not null default now(),
  unique (meeting_id, user_id)
);

create unique index meeting_member_pending_uq
  on meeting_member (meeting_id, email) where user_id is null;
