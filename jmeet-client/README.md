# jmeet-client

Next.js 16 App Router frontend for **jmeet** (the Spring Boot backend lives in
`../jmeet`). Originally ported from a mediasoup/Socket.IO client — that
media/signaling layer has been stripped out; see the `TODO(livekit)` /
`TODO(spring-security)` comments in `src/hooks`, `src/lib/auth`, and
`src/components/meeting` for what still needs wiring against LiveKit and the
Spring backend.

## Setup

```bash
npm install
cp .env.example .env.local   # points API_ORIGIN at the backend (default :8080)
npm run dev
```
