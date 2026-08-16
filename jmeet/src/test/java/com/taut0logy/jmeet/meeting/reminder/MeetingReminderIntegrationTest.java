package com.taut0logy.jmeet.meeting.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.auth.LoginRequest;
import com.taut0logy.jmeet.auth.RegisterRequest;
import com.taut0logy.jmeet.auth.TokenRequest;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.meeting.member.InviteMemberRequest;
import com.taut0logy.jmeet.outbox.OutboxEvent;
import com.taut0logy.jmeet.outbox.OutboxEventRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Instant;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeetingReminderIntegrationTest {

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
    private MeetingReminderScheduler scheduler;

    @Autowired
    private OccurrenceExpandJobHandler occurrenceExpandJobHandler;

    @Autowired
    private MeetingReminderJobHandler meetingReminderJobHandler;

    @Autowired
    private OutboxEventRepository outboxEvents;

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

    @Test
    void schedulerDispatchesOccurrenceExpandOnceThenDedupsOnRescan() throws Exception {
        String ownerEmail = "remind-owner-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(ownerEmail, "Ada Lovelace");
        Instant startsAt = Instant.now().plusSeconds(300);
        HttpResponse<String> create = post(owner, "/meetings",
                Map.of("title", "Due Soon", "kind", "SCHEDULED", "startsAt", startsAt.toString()));
        String meetingId = (String) json.readValue(create.body(), Map.class).get("id");

        scheduler.scan();
        scheduler.scan();

        long dispatched = outboxEvents.findAll().stream()
                .filter(e -> meetingId.equals(e.getAggregateId()) && JobType.OCCURRENCE_EXPAND.key().equals(e.getType()))
                .count();
        assertThat(dispatched).isEqualTo(1);
    }

    @Test
    void occurrenceExpandFansOutToOwnerAndInvitedMember() throws Exception {
        String ownerEmail = "remind-fanout-owner-" + System.nanoTime() + "@example.com";
        String memberEmail = "remind-fanout-member-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(ownerEmail, "Grace Hopper");
        registerAndLogin(memberEmail, "Katherine Johnson");

        HttpResponse<String> create = post(owner, "/meetings",
                Map.of("title", "Fanout Sync", "kind", "SCHEDULED", "startsAt", Instant.now().plusSeconds(600).toString()));
        String meetingId = (String) json.readValue(create.body(), Map.class).get("id");
        post(owner, "/meetings/" + meetingId + "/members", new InviteMemberRequest(memberEmail, null, null));

        Instant occurrenceStartsAt = Instant.now().plusSeconds(600);
        occurrenceExpandJobHandler.handle(json.writeValueAsString(new OccurrenceExpandPayload(meetingId, occurrenceStartsAt)));

        List<OutboxEvent> reminders = outboxEvents.findAll().stream()
                .filter(e -> JobType.MEETING_REMINDER.key().equals(e.getType()) && e.getAggregateId().startsWith(meetingId + ":"))
                .toList();
        assertThat(reminders).hasSize(2);
        assertThat(reminders).extracting(OutboxEvent::getAggregateId)
                .contains(meetingId + ":" + ownerEmail, meetingId + ":" + memberEmail);
    }

    @Test
    void reminderJobHandlerSendsARealEmail() throws Exception {
        String recipientEmail = "remind-recipient-" + System.nanoTime() + "@example.com";
        MeetingReminderPayload payload = new MeetingReminderPayload("meeting-1", recipientEmail, "Standup",
                Instant.now().plusSeconds(300));
        meetingReminderJobHandler.handle(json.writeValueAsString(payload));

        assertThat(emailArrived(recipientEmail, "Reminder: Standup")).isTrue();
    }
}
