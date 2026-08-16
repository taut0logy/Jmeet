package com.taut0logy.jmeet.meeting;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.auth.LoginRequest;
import com.taut0logy.jmeet.auth.RegisterRequest;
import com.taut0logy.jmeet.auth.TokenRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
class MeetingIntegrationTest {

    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025);

    @BeforeAll
    static void startMailpit() {
        mailpit.start();
    }

    @org.springframework.test.context.DynamicPropertySource
    static void mailProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @LocalServerPort
    private int port;

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

    private HttpResponse<String> patch(Client client, String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api(path)))
                .header("Cookie", client.cookieHeader())
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", client.cookies.getOrDefault("XSRF-TOKEN", ""))
                .method("PATCH", BodyPublishers.ofString(json.writeValueAsString(body))).build();
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
    void createAndListRecurringMeetingExpandsOccurrences() throws Exception {
        String email = "owner-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(email, "Ada Lovelace");

        Map<String, Object> createBody = Map.of(
                "title", "Weekly Sync", "kind", "SCHEDULED", "startsAt", "2026-06-01T09:00:00Z",
                "rrule", "FREQ=WEEKLY;BYDAY=MO");
        HttpResponse<String> create = post(owner, "/meetings", createBody);
        assertThat(create.statusCode()).as("create body: %s", create.body()).isEqualTo(201);
        Map<String, Object> created = json.readValue(create.body(), Map.class);
        String id = (String) created.get("id");

        HttpResponse<String> detail = get(owner, "/meetings/" + id + "?from=2026-06-01T00:00:00Z&to=2026-07-06T00:00:00Z");
        assertThat(detail.statusCode()).isEqualTo(200);
        Map<String, Object> detailBody = json.readValue(detail.body(), Map.class);
        List<Map<String, Object>> occurrences = (List<Map<String, Object>>) detailBody.get("occurrences");
        assertThat(occurrences).hasSize(5);

        HttpResponse<String> list = get(owner, "/meetings?from=2026-06-01T00:00:00Z&to=2026-07-06T00:00:00Z&role=owner");
        assertThat(list.statusCode()).isEqualTo(200);
        List<Map<String, Object>> summaries = json.readValue(list.body(), List.class);
        assertThat(summaries).extracting(s -> s.get("id")).contains(id);
    }

    @Test
    void thisAndFollowingPreservesPatternAndRejectsPatternChange() throws Exception {
        String email = "series-owner-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(email, "Grace Hopper");

        Map<String, Object> createBody = Map.of(
                "title", "Standup", "kind", "SCHEDULED", "startsAt", "2026-06-01T09:00:00Z",
                "rrule", "FREQ=WEEKLY;BYDAY=MO");
        HttpResponse<String> create = post(owner, "/meetings", createBody);
        Map<String, Object> created = json.readValue(create.body(), Map.class);
        String id = (String) created.get("id");

        String rangeQuery = "?from=2026-06-01T00:00:00Z&to=2026-07-06T00:00:00Z";

        HttpResponse<String> beforeEdit = get(owner, "/meetings/" + id + rangeQuery);
        List<Map<String, Object>> beforeOccurrences = (List<Map<String, Object>>) json.readValue(beforeEdit.body(), Map.class).get("occurrences");
        assertThat(beforeOccurrences).extracting(o -> o.get("title")).containsOnly("Standup");
        String thirdOccurrenceStart = (String) beforeOccurrences.get(2).get("originalStartsAt");

        HttpResponse<String> edit = patch(owner, "/meetings/" + id + "?scope=THIS_AND_FOLLOWING&occurrenceStartsAt=" + thirdOccurrenceStart,
                Map.of("title", "Renamed Standup"));
        assertThat(edit.statusCode()).as("edit body: %s", edit.body()).isEqualTo(200);

        HttpResponse<String> afterEdit = get(owner, "/meetings/" + id + rangeQuery);
        Map<String, Object> afterBody = json.readValue(afterEdit.body(), Map.class);
        List<Map<String, Object>> afterOccurrences = (List<Map<String, Object>>) afterBody.get("occurrences");
        assertThat(afterOccurrences.get(0).get("title")).isEqualTo("Standup");
        assertThat(afterOccurrences.get(1).get("title")).isEqualTo("Standup");
        assertThat(afterOccurrences.get(2).get("title")).isEqualTo("Renamed Standup");
        assertThat(afterOccurrences.get(4).get("title")).isEqualTo("Renamed Standup");
        assertThat(afterBody.get("rrule")).isEqualTo("FREQ=WEEKLY;BYDAY=MO");

        HttpResponse<String> patternChange = patch(owner, "/meetings/" + id + "?scope=THIS_AND_FOLLOWING&occurrenceStartsAt=" + thirdOccurrenceStart,
                Map.of("rrule", "FREQ=DAILY"));
        assertThat(patternChange.statusCode()).isEqualTo(400);
        assertThat(patternChange.body()).contains("PATTERN_CHANGE_REQUIRES_SCOPE_ALL");
    }

    @Test
    void joinAccessMatrixThroughRealEndpoints() throws Exception {
        String email = "host-" + System.nanoTime() + "@example.com";
        Client host = registerAndLogin(email, "Katherine Johnson");

        Map<String, Object> createBody = Map.of("title", "Quick Sync", "kind", "INSTANT");
        HttpResponse<String> create = post(host, "/meetings", createBody);
        Map<String, Object> created = json.readValue(create.body(), Map.class);
        String id = (String) created.get("id");
        String code = (String) created.get("code");

        Client guest = new Client();
        HttpResponse<String> lobby = get(guest, "/meetings/by-code/" + code);
        assertThat(lobby.statusCode()).isEqualTo(200);
        Map<String, Object> lobbyBody = json.readValue(lobby.body(), Map.class);
        assertThat(lobbyBody.get("title")).isEqualTo("Quick Sync");
        assertThat(lobbyBody.get("hostName")).isEqualTo("Katherine Johnson");

        HttpResponse<String> noName = post(guest, "/meetings/by-code/" + code + "/join-token", Map.of());
        assertThat(noName.statusCode()).isEqualTo(400);
        assertThat(noName.body()).contains("DISPLAY_NAME_REQUIRED");

        HttpResponse<String> guestToken = post(guest, "/meetings/by-code/" + code + "/join-token",
                Map.of("displayName", "Curious Guest"));
        assertThat(guestToken.statusCode()).as("guest token body: %s", guestToken.body()).isEqualTo(200);
        assertThat((String) json.readValue(guestToken.body(), Map.class).get("token")).isNotBlank();

        HttpResponse<String> cancel = delete(host, "/meetings/" + id);
        assertThat(cancel.statusCode()).isEqualTo(204);

        HttpResponse<String> afterCancel = post(guest, "/meetings/by-code/" + code + "/join-token",
                Map.of("displayName", "Curious Guest"));
        assertThat(afterCancel.statusCode()).isEqualTo(409);
        assertThat(afterCancel.body()).contains("MEETING_NOT_JOINABLE");
    }

    @Test
    void invitedOnlyMembershipGatesJoin() throws Exception {
        String ownerEmail = "invite-owner-" + System.nanoTime() + "@example.com";
        String memberEmail = "invite-member-" + System.nanoTime() + "@example.com";
        String strangerEmail = "invite-stranger-" + System.nanoTime() + "@example.com";
        Client owner = registerAndLogin(ownerEmail, "Marie Curie");
        Client member = registerAndLogin(memberEmail, "Rosalind Franklin");
        Client stranger = registerAndLogin(strangerEmail, "Alan Turing");

        Map<String, Object> createBody = Map.of("title", "Private Sync", "kind", "INSTANT", "access", "INVITED_ONLY");
        HttpResponse<String> create = post(owner, "/meetings", createBody);
        Map<String, Object> created = json.readValue(create.body(), Map.class);
        String id = (String) created.get("id");
        String code = (String) created.get("code");

        HttpResponse<String> strangerAttempt = post(stranger, "/meetings/by-code/" + code + "/join-token", Map.of());
        assertThat(strangerAttempt.statusCode()).isEqualTo(403);
        assertThat(strangerAttempt.body()).contains("NOT_INVITED");

        HttpResponse<String> invite = post(owner, "/meetings/" + id + "/members", Map.of("email", memberEmail));
        assertThat(invite.statusCode()).as("invite body: %s", invite.body()).isEqualTo(201);
        Map<String, Object> memberRow = json.readValue(invite.body(), Map.class);
        String memberId = (String) memberRow.get("id");

        HttpResponse<String> memberAttempt = post(member, "/meetings/by-code/" + code + "/join-token", Map.of());
        assertThat(memberAttempt.statusCode()).as("member token body: %s", memberAttempt.body()).isEqualTo(200);

        HttpResponse<String> removed = delete(owner, "/meetings/" + id + "/members/" + memberId);
        assertThat(removed.statusCode()).isEqualTo(204);

        HttpResponse<String> afterRemoval = post(member, "/meetings/by-code/" + code + "/join-token", Map.of());
        assertThat(afterRemoval.statusCode()).isEqualTo(403);
        assertThat(afterRemoval.body()).contains("NOT_INVITED");
    }
}
