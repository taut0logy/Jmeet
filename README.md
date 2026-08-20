# jmeet

[![CI](https://github.com/taut0logy/Jmeet/actions/workflows/backend-ci-cd.yml/badge.svg)](https://github.com/taut0logy/Jmeet/actions/workflows/backend-ci-cd.yml)
![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-16-000000?logo=nextdotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4-FF6600?logo=rabbitmq&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

A self-hosted video meeting platform: schedule or start a call, share your screen, chat, raise your hand, record the session, and manage who gets in - all running on infrastructure you control instead of a third-party SaaS.

Backend is Spring Boot on Java 25. The client is Next.js. Media runs on LiveKit (WebRTC SFU), with recording handled by LiveKit Egress.

## Features

### Meetings

- Instant or scheduled meetings, with recurrence (RRULE-based - weekly standups, etc.)
- Join by link or by code, with configurable access: open link, signed-in users only, or invite-only
- Waiting room with host/cohost approval, lockable meetings

### In the room

- Live video grid with tiled, spotlight, and sidebar layouts, plus pinning
- Screen sharing, chat, emoji reactions, raise hand
- Host controls: mute participants, change roles, remove participants, lock the room, end for everyone
- Cloud recording, with a dedicated no-chrome layout page for the recorder itself
- Original synthesized sound effects for join/leave/chat/hand-raise/recording events - no licensed audio assets

### Accounts

- Email/password and Google OAuth, with email verification and password reset
- Guests can join without an account where the meeting allows it
- Profile photo upload with an in-browser crop/zoom editor

### Under the hood

- Real-time state sync over STOMP/WebSocket, with revision-gapped resync so clients never drift silently
- A durable job queue (outbox + RabbitMQ) for email and reminders, with retry and dead-lettering
- Structured JSON logs, tracing, and Prometheus metrics out of the box

## Architecture

```text
jmeet-client (Next.js) -> jmeet (Spring Boot) -> Postgres / Redis / RabbitMQ / MinIO
       |                        |
       +---- LiveKit (media) ---+ -> LiveKit Egress (recording)
```

- **`jmeet/`** - the backend. REST API plus a STOMP endpoint for real-time room events (presence, chat, reactions, recording state). Postgres for durable data, Redis for sessions and room state, RabbitMQ as the STOMP broker relay and job queue, MinIO (or any S3-compatible store) for recordings and avatars.
- **`jmeet-client/`** - the frontend. Talks to the backend over a same-origin `/api/*` proxy (keeps the session cookie first-party) and connects directly to LiveKit and the STOMP endpoint from the browser.
- **LiveKit** handles the actual audio/video routing; the backend only mints tokens and orchestrates room state. Recording works by pointing LiveKit Egress' headless browser at a stripped-down client route that renders the same video grid with no UI chrome.

The client proxies `/api/*` requests to the backend so the session cookie stays same-origin, but WebSocket (STOMP) and LiveKit media connections go straight from the browser to their own ports - there's no proxying for those.

## Getting started

**Prerequisites:** JDK 25, Node 20+, Docker.

### 1. Start infrastructure

```bash
cd jmeet
docker compose up -d
```

This brings up Postgres, Redis, RabbitMQ, Mailpit (catches outgoing email locally), MinIO, LiveKit, and LiveKit Egress.

### 2. Run the backend

```bash
cd jmeet
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The `local` profile fills in dev-friendly defaults for secrets that are required but unset in production (join-token signing key, LiveKit API secret, MinIO credentials), so there's nothing to configure by hand for local development. The backend also auto-detects and starts `compose.yaml` on boot via Spring Boot's Docker Compose support, so step 1 is optional if you'd rather let it happen automatically - running it yourself first just makes startup faster.

Runs on `http://localhost:8080`.

### 3. Run the client

```bash
cd jmeet-client
npm install
cp .env.example .env.local
npm run dev
```

Runs on `http://localhost:3000`. Visiting it will redirect you to sign in or register.

### 4. (Optional) Google sign-in

Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` as environment variables before starting the backend. Without them, email/password auth still works fine - Google just won't show up as an option.

## Configuration

Everything is env-var driven with sensible defaults; see `application.yml` for the full list. The ones you're most likely to touch:

| Variable                                              | Purpose                                        | Local default                                   |
| ------------------------------------------------------ | ----------------------------------------------- | ------------------------------------------------ |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`  | Postgres connection                            | matches `compose.yaml`                          |
| `CLIENT_BASE_URL`                                     | Where email links (verify, reset password) point | `http://localhost:3000`                        |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`            | Google OAuth                                   | unset (disables Google sign-in)                 |
| `LIVEKIT_HOST`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | LiveKit server connection                    | `http://localhost:7880` / `devkey` / dev secret |
| `STORAGE_DRIVER`                                      | `local` filesystem or `s3` (MinIO/S3) for avatars | `local`                                        |

## Testing

```bash
cd jmeet
./gradlew test          # backend, spins up real Postgres/Redis/RabbitMQ/MinIO via Testcontainers
```
