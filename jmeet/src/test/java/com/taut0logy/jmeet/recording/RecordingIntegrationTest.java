package com.taut0logy.jmeet.recording;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.auth.LoginRequest;
import com.taut0logy.jmeet.auth.RegisterRequest;
import com.taut0logy.jmeet.auth.TokenRequest;
import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.meeting.member.InviteMemberRequest;
import com.taut0logy.jmeet.meeting.session.MeetingSession;
import com.taut0logy.jmeet.meeting.session.MeetingSessionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RecordingIntegrationTest.FakeEgressConfig.class})
class RecordingIntegrationTest {

    /** Real LiveKit/Egress round-trips are out of JVM-test reach, same boundary M0 and M4 already
     * established — this fake exercises the real authorization/status-transition/notification
     * logic without needing a connected media pipeline. The wire-level Egress call shape is
     * covered separately by LiveKitEgressAdapterMappingTest against real protobuf types. */
    @TestConfiguration
    static class FakeEgressConfig {
        @Bean
        @Primary
        EgressPort fakeEgressPort() {
            return new FakeEgressPort();
        }
    }

    static class FakeEgressPort implements EgressPort {
        final Map<String, EgressStatusSnapshot> snapshots = new ConcurrentHashMap<>();

        @Override
        public String startRoomCompositeEgress(String roomName, String storageKey) {
            String id = "fake-egress-" + Ids.next();
            snapshots.put(id, new EgressStatusSnapshot(id, RecordingStatus.RECORDING, null, null, null, null));
            return id;
        }

        @Override
        public void stopEgress(String egressId) {
            snapshots.computeIfPresent(egressId, (id, s) -> new EgressStatusSnapshot(id, RecordingStatus.PROCESSING, null, null, null, null));
        }

        @Override
        public Optional<EgressStatusSnapshot> getEgress(String egressId) {
            return Optional.ofNullable(snapshots.get(egressId));
        }
    }

    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025);

    @BeforeAll
    static void startMailpit() {
        mailpit.start();
    }

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MeetingSessionRepository sessions;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private RecordingRepository recordings;

    @Autowired
    private RecordingReconciler reconciler;

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

    private HttpResponse<String> delete(Client client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api(path)))
                .header("Cookie", client.cookieHeader())
                .header("X-XSRF-TOKEN", client.cookies.getOrDefault("XSRF-TOKEN", ""))
                .DELETE().build();
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

    private boolean emailArrived(String recipient, String subjectContains) throws Exception {
        var deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> list = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = json.readValue(list.body(), Map.class);
            List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
            for (Map<String, Object> m : messages) {
                List<Map<String, Object>> to = (List<Map<String, Object>>) m.get("To");
                boolean toMatches = to.stream().anyMatch(t -> recipient.equalsIgnoreCase((String) t.get("Address")));
                if (toMatches && ((String) m.get("Subject")).contains(subjectContains)) return true;
            }
            Thread.sleep(200);
        }
        return false;
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

    private String createMeetingAndSession(Client owner, String title) throws Exception {
        HttpResponse<String> create = post(owner, "/meetings", Map.of("title", title, "kind", "INSTANT"));
        String meetingId = (String) json.readValue(create.body(), Map.class).get("id");
        MeetingSession session = new MeetingSession(Ids.next(), meetingId, Instant.now());
        sessions.save(session);
        return session.getId();
    }

    @Test
    void onlyHostOrCohostCanStartRecording() throws Exception {
        String ownerEmail = "rec-owner-" + System.nanoTime() + "@example.com";
        String memberEmail = "rec-member-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(ownerEmail, "Ada Lovelace");
        Client member = registerAndLogin(memberEmail, "Grace Hopper");

        HttpResponse<String> create = post(owner, "/meetings", Map.of("title", "Recorded Sync", "kind", "INSTANT"));
        Map<String, Object> created = json.readValue(create.body(), Map.class);
        String meetingId = (String) created.get("id");
        MeetingSession session = new MeetingSession(Ids.next(), meetingId, Instant.now());
        sessions.save(session);

        post(owner, "/meetings/" + meetingId + "/members", new InviteMemberRequest(memberEmail, null, null));

        HttpResponse<String> memberAttempt = post(member, "/rooms/" + session.getId() + "/recording", null);
        assertThat(memberAttempt.statusCode()).isEqualTo(403);

        HttpResponse<String> hostAttempt = post(owner, "/rooms/" + session.getId() + "/recording", null);
        assertThat(hostAttempt.statusCode()).as("host body: %s", hostAttempt.body()).isEqualTo(201);
        Map<String, Object> recordingBody = json.readValue(hostAttempt.body(), Map.class);
        assertThat(recordingBody.get("status")).isEqualTo("RECORDING");
    }

    @Test
    void duplicateRecordingIsRejected() throws Exception {
        Client owner = registerAndLogin("rec-dupe-" + System.nanoTime() + "@example.com", "Katherine Johnson");
        String sessionId = createMeetingAndSession(owner, "Dupe Sync");

        HttpResponse<String> first = post(owner, "/rooms/" + sessionId + "/recording", null);
        assertThat(first.statusCode()).isEqualTo(201);

        HttpResponse<String> second = post(owner, "/rooms/" + sessionId + "/recording", null);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("RECORDING_ALREADY_ACTIVE");
    }

    @Test
    void stopRequiresActiveRecordingAndTransitionsToProcessing() throws Exception {
        Client owner = registerAndLogin("rec-stop-" + System.nanoTime() + "@example.com", "Margaret Hamilton");
        String sessionId = createMeetingAndSession(owner, "Stop Sync");

        HttpResponse<String> stopBeforeStart = delete(owner, "/rooms/" + sessionId + "/recording");
        assertThat(stopBeforeStart.statusCode()).isEqualTo(409);
        assertThat(stopBeforeStart.body()).contains("RECORDING_NOT_ACTIVE");

        post(owner, "/rooms/" + sessionId + "/recording", null);
        HttpResponse<String> stop = delete(owner, "/rooms/" + sessionId + "/recording");
        assertThat(stop.statusCode()).as("stop body: %s", stop.body()).isEqualTo(200);
        assertThat(json.readValue(stop.body(), Map.class).get("status")).isEqualTo("PROCESSING");
    }

    @Test
    void readyEgressStatusTriggersNotificationEmailAndListedDownloadUrl() throws Exception {
        String ownerEmail = "rec-ready-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(ownerEmail, "Rosalind Franklin");
        String sessionId = createMeetingAndSession(owner, "Ready Sync");

        HttpResponse<String> start = post(owner, "/rooms/" + sessionId + "/recording", null);
        String recordingId = (String) json.readValue(start.body(), Map.class).get("id");
        Recording recording = recordings.findById(recordingId).orElseThrow();
        String meetingId = recording.getMeetingId();
        String storageKey = "recordings/" + meetingId + "/" + recordingId + ".mp4";

        recordingService.applyEgressStatus(recording.getEgressId(),
                new EgressStatusSnapshot(recording.getEgressId(), RecordingStatus.READY, storageKey, 90_000, 12_345L, null));

        assertThat(emailArrived(ownerEmail, "recording is ready")).isTrue();

        HttpResponse<String> listAfter = get(owner, "/meetings/" + meetingId + "/recordings");
        List<Map<String, Object>> after = json.readValue(listAfter.body(), List.class);
        assertThat(after.get(0).get("status")).isEqualTo("READY");
        assertThat((String) after.get(0).get("downloadUrl")).isNotBlank();
    }

    @Test
    void terminalStatusIsNotOverwrittenByALaterStaleUpdate() throws Exception {
        Client owner = registerAndLogin("rec-terminal-" + System.nanoTime() + "@example.com", "Alan Turing");
        String sessionId = createMeetingAndSession(owner, "Terminal Sync");

        HttpResponse<String> start = post(owner, "/rooms/" + sessionId + "/recording", null);
        String recordingId = (String) json.readValue(start.body(), Map.class).get("id");
        String egressId = recordings.findById(recordingId).orElseThrow().getEgressId();

        recordingService.applyEgressStatus(egressId, new EgressStatusSnapshot(egressId, RecordingStatus.FAILED, null, null, null, "network drop"));
        recordingService.applyEgressStatus(egressId, new EgressStatusSnapshot(egressId, RecordingStatus.RECORDING, null, null, null, null));

        Recording reloaded = recordings.findById(recordingId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(reloaded.getError()).isEqualTo("network drop");
    }

    @Test
    void reconcilerMarksUnknownEgressAsFailed() throws Exception {
        Client owner = registerAndLogin("rec-reconcile-" + System.nanoTime() + "@example.com", "Barbara Liskov");
        String sessionId = createMeetingAndSession(owner, "Reconcile Sync");

        HttpResponse<String> start = post(owner, "/rooms/" + sessionId + "/recording", null);
        String recordingId = (String) json.readValue(start.body(), Map.class).get("id");
        Recording recording = recordings.findById(recordingId).orElseThrow();

        // The fake port only knows about egress ids it minted itself; reconcileOne's job is to
        // treat "the SFU has no record of this" the same whether that's because it genuinely
        // vanished or, as here, was never real to begin with.
        recording.setEgressId("egress-id-unknown-to-livekit");
        recordings.save(recording);

        reconciler.reconcileOne(recordingId);

        Recording reloaded = recordings.findById(recordingId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(reloaded.getError()).isEqualTo("egress not found during reconciliation");
    }
}
