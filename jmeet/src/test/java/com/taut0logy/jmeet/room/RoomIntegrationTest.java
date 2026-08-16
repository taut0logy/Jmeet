package com.taut0logy.jmeet.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.auth.LoginRequest;
import com.taut0logy.jmeet.auth.RegisterRequest;
import com.taut0logy.jmeet.auth.TokenRequest;
import com.taut0logy.jmeet.meeting.session.MeetingSession;
import com.taut0logy.jmeet.meeting.session.MeetingSessionRepository;
import com.taut0logy.jmeet.outbox.OutboxRelay;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RoomIntegrationTest {

    private static final String LIVEKIT_API_KEY = "devkey";
    private static final String LIVEKIT_API_SECRET = "devsecretdevsecretdevsecretdevsecret";

    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025);

    @SuppressWarnings("resource")
    static GenericContainer<?> livekit = new GenericContainer<>(DockerImageName.parse("livekit/livekit-server:latest"))
            .withExposedPorts(7880, 7881)
            .withClasspathResourceMapping("livekit-test.yaml", "/etc/livekit.yaml", BindMode.READ_ONLY)
            .withCommand("--config", "/etc/livekit.yaml");

    @BeforeAll
    static void startContainers() {
        mailpit.start();
        livekit.start();
    }

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        registry.add("app.livekit.host", () -> "http://" + livekit.getHost() + ":" + livekit.getMappedPort(7880));
        registry.add("app.livekit.api-key", () -> LIVEKIT_API_KEY);
        registry.add("app.livekit.api-secret", () -> LIVEKIT_API_SECRET);
        registry.add("app.realtime.stomp-relay-port", TestcontainersConfiguration::rabbitmqStompPort);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MeetingDurationScheduler durationScheduler;

    @Autowired
    private MeetingSessionRepository sessions;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private OutboxRelay outboxRelay;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private String api(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    static class Client {
        final Map<String, String> cookies = new HashMap<>();

        void absorb(HttpResponse<?> response) {
            for (String header : response.headers().allValues("set-cookie")) {
                String[] parts = header.split(";", 2)[0].split("=", 2);
                if (parts.length == 2) cookies.put(parts[0].trim(), parts[1].trim());
            }
        }

        String cookieHeader() {
            return cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "; " + b).orElse("");
        }
    }

    private HttpResponse<String> get(Client client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api(path)))
                .header("Cookie", client.cookieHeader()).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        client.absorb(response);
        return response;
    }

    private HttpResponse<String> post(Client client, String path, Object body) throws Exception {
        String payload = body == null ? "" : json.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(api(path)))
                .header("Cookie", client.cookieHeader())
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", client.cookies.getOrDefault("XSRF-TOKEN", ""))
                .POST(BodyPublishers.ofString(payload)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        client.absorb(response);
        return response;
    }

    private String mailpitApi(String path) {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path;
    }

    private String verificationToken(String email) throws Exception {
        var deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> list = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = json.readValue(list.body(), Map.class);
            List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
            for (Map<String, Object> m : messages) {
                List<Map<String, Object>> to = (List<Map<String, Object>>) m.get("To");
                boolean toMatches = to.stream().anyMatch(t -> email.equalsIgnoreCase((String) t.get("Address")));
                if (toMatches) {
                    HttpResponse<String> detail = http.send(
                            HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/message/" + m.get("ID")))).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    Map<String, Object> detailBody = json.readValue(detail.body(), Map.class);
                    String html = (String) detailBody.get("HTML");
                    Matcher matcher = Pattern.compile("token=([^&\\s\"<]+)").matcher(html);
                    if (matcher.find()) return matcher.group(1);
                }
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("no verification email found for " + email);
    }

    private Client registerAndLogin(String email, String name) throws Exception {
        Client client = new Client();
        get(client, "/auth/sessions");
        post(client, "/auth/register", new RegisterRequest(email, "correct-horse-battery", name));
        String token = verificationToken(email);
        post(client, "/auth/verify-email", new TokenRequest(token));
        post(client, "/auth/login", new LoginRequest(email, "correct-horse-battery"));
        return client;
    }

    private String createMeeting(Client owner, String title, String waitingRoom) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "kind", "INSTANT"));
        if (waitingRoom != null) body.put("waitingRoom", waitingRoom);
        HttpResponse<String> create = post(owner, "/meetings", body);
        return (String) json.readValue(create.body(), Map.class).get("code");
    }

    private String mintGuestJoinToken(Client guest, String code, String displayName) throws Exception {
        get(guest, "/meetings/by-code/" + code);
        HttpResponse<String> response = post(guest, "/meetings/by-code/" + code + "/join-token", Map.of("displayName", displayName));
        assertThat(response.statusCode()).as("join-token body: %s", response.body()).isEqualTo(200);
        return (String) json.readValue(response.body(), Map.class).get("token");
    }

    /** Load/concurrency hardening: the room-full check in RoomService.join() reads the active
     * count and inserts a new Participation in separate steps — a classic check-then-act shape.
     * A sequential test could never expose a race there; this fires every join at once against a
     * real Postgres to find out whether concurrent requests can squeeze past app.meeting.
     * max-participants (30 by default). */
    @Test
    void concurrentJoinsNeverExceedMaxParticipants() throws Exception {
        String email = "room-load-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(email, "Grace Hopper");
        String code = createMeeting(owner, "Load Test Sync", "OFF");

        int attempts = 40;
        List<Client> guests = new java.util.ArrayList<>();
        List<String> joinTokens = new java.util.ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Client guest = new Client();
            joinTokens.add(mintGuestJoinToken(guest, code, "Guest " + i));
            guests.add(guest);
        }

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(attempts);
        List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Client guest = guests.get(i);
            String joinToken = joinTokens.get(i);
            futures.add(pool.submit(() -> {
                HttpResponse<String> response = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
                return response.statusCode();
            }));
        }
        pool.shutdown();

        long admitted = 0;
        long roomFull = 0;
        for (java.util.concurrent.Future<Integer> future : futures) {
            int status = future.get();
            if (status == 200) admitted++;
            else if (status == 409) roomFull++;
            else throw new AssertionError("unexpected join status: " + status);
        }

        assertThat(admitted).as("admitted joins must never exceed max-participants").isLessThanOrEqualTo(30);
        assertThat(admitted + roomFull).isEqualTo(attempts);
    }

    @Test
    void joinAdmitsImmediatelyWhenNoApprovalRequired() throws Exception {
        String email = "room-owner-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(email, "Ada Lovelace");
        String code = createMeeting(owner, "Quick Sync", "OFF");
        Client guest = new Client();
        String joinToken = mintGuestJoinToken(guest, code, "Curious Guest");

        HttpResponse<String> join = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
        assertThat(join.statusCode()).as("join body: %s", join.body()).isEqualTo(200);
        Map<String, Object> joinBody = json.readValue(join.body(), Map.class);
        assertThat(joinBody.get("status")).isEqualTo("ADMITTED");
        assertThat((String) joinBody.get("token")).isNotBlank();
        String peerId = (String) joinBody.get("peerId");
        Map<String, Object> snapshot = (Map<String, Object>) joinBody.get("snapshot");
        String sessionId = (String) snapshot.get("sessionId");
        List<Map<String, Object>> participants = (List<Map<String, Object>>) snapshot.get("participants");
        assertThat(participants).hasSize(1);

        HttpResponse<String> sync = get(new Client(), "/rooms/" + sessionId + "/sync?peerId=" + peerId);
        assertThat(sync.statusCode()).isEqualTo(200);
        Map<String, Object> syncBody = json.readValue(sync.body(), Map.class);
        assertThat((List<?>) syncBody.get("participants")).hasSize(1);
    }

    @Test
    void waitingRoomAdmissionFlow() throws Exception {
        String email = "room-host-" + System.nanoTime() + "@example.com";
        Client host = registerAndLogin(email, "Grace Hopper");
        String code = createMeeting(host, "Gated Sync", "EVERYONE");
        Client guest = new Client();
        String joinToken = mintGuestJoinToken(guest, code, "Waiting Guest");

        HttpResponse<String> join = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
        assertThat(join.statusCode()).isEqualTo(200);
        Map<String, Object> joinBody = json.readValue(join.body(), Map.class);
        assertThat(joinBody.get("status")).isEqualTo("PENDING");
        assertThat(joinBody.get("token")).isNull();
        String peerId = (String) joinBody.get("peerId");
        String sessionId = (String) ((Map<String, Object>) joinBody.get("snapshot")).get("sessionId");

        HttpResponse<String> admit = post(host, "/rooms/" + sessionId + "/admissions",
                new AdmissionRequest(peerId, true, null));
        assertThat(admit.statusCode()).as("admit body: %s", admit.body()).isEqualTo(200);
        Map<String, Object> admitBody = json.readValue(admit.body(), Map.class);
        List<Map<String, Object>> participants = (List<Map<String, Object>>) admitBody.get("participants");
        assertThat(participants).extracting(p -> p.get("peerId")).contains(peerId);
    }

    @Test
    void lockedMeetingRejectsAnAlreadyMintedToken() throws Exception {
        String email = "room-lock-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(email, "Katherine Johnson");
        String code = createMeeting(owner, "Lockable Sync", "OFF");
        Client guest = new Client();
        String joinToken = mintGuestJoinToken(guest, code, "Late Guest");

        Client bootstrap = new Client();
        String bootstrapToken = mintGuestJoinToken(bootstrap, code, "Bootstrap Guest");
        HttpResponse<String> bootstrapJoin = post(bootstrap, "/rooms/" + code + "/join", new RoomJoinRequest(bootstrapToken));
        String sessionId = (String) ((Map<String, Object>) json.readValue(bootstrapJoin.body(), Map.class).get("snapshot")).get("sessionId");

        HttpResponse<String> lock = post(owner, "/rooms/" + sessionId + "/flags", new RoomFlagsRequest(true, null, null));
        assertThat(lock.statusCode()).isEqualTo(200);

        HttpResponse<String> lateJoin = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
        assertThat(lateJoin.statusCode()).isEqualTo(423);
        assertThat(lateJoin.body()).contains("MEETING_LOCKED");
    }

    @Test
    void webhookParticipantLeftMarksParticipationInactive() throws Exception {
        String email = "room-webhook-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(email, "Margaret Hamilton");
        String code = createMeeting(owner, "Webhook Sync", "OFF");
        Client guest = new Client();
        String joinToken = mintGuestJoinToken(guest, code, "Ephemeral Guest");

        HttpResponse<String> join = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
        Map<String, Object> joinBody = json.readValue(join.body(), Map.class);
        String peerId = (String) joinBody.get("peerId");
        String sessionId = (String) ((Map<String, Object>) joinBody.get("snapshot")).get("sessionId");

        String body = "{\"event\":\"participant_left\",\"room\":{\"name\":\"" + code + "\"},"
                + "\"participant\":{\"identity\":\"" + peerId + "\"}}";
        String authHeader = signWebhook(body);

        HttpResponse<String> webhook = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/internal/livekit/webhook"))
                        .header("Authorization", authHeader)
                        .POST(BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(webhook.statusCode()).as("webhook body: %s", webhook.body()).isEqualTo(204);

        // The webhook marked this exact peer's Participation as left; re-validating with
        // their own peerId now fails NOT_ADMITTED, proving the row actually changed (a
        // peerId that never existed would fail the same way for an unrelated reason).
        HttpResponse<String> sync = get(new Client(), "/rooms/" + sessionId + "/sync?peerId=" + peerId);
        assertThat(sync.statusCode()).isEqualTo(403);
        assertThat(sync.body()).contains("NOT_ADMITTED");
    }

    private String createScheduledMeeting(Client owner, String title, java.time.Instant startsAt, int durationMin) throws Exception {
        HttpResponse<String> create = post(owner, "/meetings", Map.of(
                "title", title, "kind", "SCHEDULED", "startsAt", startsAt.toString(),
                "durationMin", durationMin, "waitingRoom", "OFF"));
        return (String) json.readValue(create.body(), Map.class).get("code");
    }

    @Test
    void durationSchedulerAutoEndsASessionPastItsGracePeriod() throws Exception {
        Client owner = registerAndLogin("duration-end-" + System.nanoTime() + "@example.com", "Ada Lovelace");
        String code = createScheduledMeeting(owner, "Overrun Sync", java.time.Instant.now().minusSeconds(1200), 1);
        Client guest = new Client();
        String joinToken = mintGuestJoinToken(guest, code, "Late Guest");
        HttpResponse<String> join = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
        String sessionId = (String) ((Map<String, Object>) json.readValue(join.body(), Map.class).get("snapshot")).get("sessionId");

        durationScheduler.scan();
        outboxRelay.poll();

        var deadline = System.currentTimeMillis() + 10000;
        MeetingSession reloaded;
        do {
            reloaded = sessions.findById(sessionId).orElseThrow();
            if (reloaded.getEndedAt() != null) break;
            Thread.sleep(200);
        } while (System.currentTimeMillis() < deadline);

        assertThat(reloaded.getEndedAt()).as("session should have been auto-ended past its grace period").isNotNull();
    }

    @Test
    void durationSchedulerClaimsAWarningOnceForASessionNearingItsEnd() throws Exception {
        Client owner = registerAndLogin("duration-warn-" + System.nanoTime() + "@example.com", "Grace Hopper");
        String code = createScheduledMeeting(owner, "Almost Done Sync", java.time.Instant.now().minusSeconds(180), 5);
        Client guest = new Client();
        String joinToken = mintGuestJoinToken(guest, code, "Warned Guest");
        HttpResponse<String> join = post(guest, "/rooms/" + code + "/join", new RoomJoinRequest(joinToken));
        String sessionId = (String) ((Map<String, Object>) json.readValue(join.body(), Map.class).get("snapshot")).get("sessionId");

        durationScheduler.scan();

        assertThat(redis.hasKey("duration-warning:sent:" + sessionId)).isTrue();
    }

    private String signWebhook(String body) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hash = Base64.getEncoder().encodeToString(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(LIVEKIT_API_KEY).claim("sha256", hash).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(LIVEKIT_API_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
