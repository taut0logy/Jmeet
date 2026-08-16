create table app_user (
  id             text primary key,
  email          citext      not null unique,
  email_verified boolean     not null default false,
  name           text        not null,
  password_hash  text,
  image_url      text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create table oauth_account (
  id           text primary key,
  user_id      text not null references app_user(id) on delete cascade,
  provider     text not null,
  provider_uid text not null,
  created_at   timestamptz not null default now(),
  unique (provider, provider_uid)
);

create table auth_token (
  id         text primary key,
  user_id    text        not null references app_user(id) on delete cascade,
  purpose    text        not null,
  token_hash text        not null unique,
  expires_at timestamptz not null,
  used_at    timestamptz,
  created_at timestamptz not null default now()
);
create index auth_token_user_idx on auth_token (user_id, purpose);

create table profile (
  user_id                   text primary key references app_user(id) on delete cascade,
  display_name              text        not null,
  avatar_url                text,
  timezone                  text        not null default 'UTC',
  default_mic_muted         boolean     not null default false,
  default_camera_off        boolean     not null default false,
  preferred_audio_input_id  text,
  preferred_video_input_id  text,
  preferred_audio_output_id text,
  created_at                timestamptz not null default now(),
  updated_at                timestamptz not null default now()
);
