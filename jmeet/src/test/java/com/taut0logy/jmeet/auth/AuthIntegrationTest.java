package com.taut0logy.jmeet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
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
class AuthIntegrationTest {

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

    /** Minimal cookie jar: CSRF + session must survive across several requests in a test. */
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
                .header("Cookie", client.cookieHeader())
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        client.absorb(response);
        return response;
    }

    private HttpResponse<String> post(Client client, String path, Object body) throws Exception {
        String payload = body == null ? "" : json.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(api(path)))
                .header("Cookie", client.cookieHeader())
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", client.cookies.getOrDefault("XSRF-TOKEN", ""))
                .POST(BodyPublishers.ofString(payload));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    /** Shared Mailpit inbox across every test in this class — must match both recipient and subject. */
    private String lastEmailLink(String recipient, String subjectContains) throws Exception {
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
                if (toMatches && ((String) m.get("Subject")).contains(subjectContains)) {
                    HttpResponse<String> detail = http.send(
                            HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/message/" + m.get("ID")))).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    Map<String, Object> detailBody = json.readValue(detail.body(), Map.class);
                    String html = (String) detailBody.get("HTML");
                    Matcher matcher = Pattern.compile("https?://[^\\s\"<]+").matcher(html);
                    if (matcher.find()) return matcher.group();
                }
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("no matching email found for " + recipient);
    }

    private String verificationToken(String email, String name) throws Exception {
        Client client = new Client();
        get(client, "/auth/sessions"); // any GET to obtain the CSRF cookie
        HttpResponse<String> register = post(client, "/auth/register", new RegisterRequest(email, "correct-horse-battery", name));
        assertThat(register.statusCode()).isEqualTo(204);

        String link = lastEmailLink(email, "Verify your email");
        String token = link.substring(link.indexOf("token=") + 6);
        return token;
    }

    @Test
    void registerVerifyLoginAndListSessions() throws Exception {
        String email = "flow-" + System.nanoTime() + "@example.com";
        Client client = new Client();
        get(client, "/auth/sessions");
        HttpResponse<String> register = post(client, "/auth/register", new RegisterRequest(email, "correct-horse-battery", "Ada"));
        assertThat(register.statusCode()).isEqualTo(204);

        String link = lastEmailLink(email, "Verify your email");
        String token = link.substring(link.indexOf("token=") + 6);

        HttpResponse<String> verify = post(client, "/auth/verify-email", new TokenRequest(token));
        assertThat(verify.statusCode()).as("link: %s, token: %s, body: %s", link, token, verify.body()).isEqualTo(204);

        HttpResponse<String> loginWrong = post(client, "/auth/login", new LoginRequest(email, "wrong-password"));
        assertThat(loginWrong.statusCode()).isEqualTo(401);

        HttpResponse<String> login = post(client, "/auth/login", new LoginRequest(email, "correct-horse-battery"));
        assertThat(login.statusCode()).as("login body: %s", login.body()).isEqualTo(204);
        assertThat(client.cookies).containsKey("SESSION");

        HttpResponse<String> sessionsResponse = get(client, "/auth/sessions");
        assertThat(sessionsResponse.statusCode()).as("sessions body: %s", sessionsResponse.body()).isEqualTo(200);
        List<Map<String, Object>> sessionsList = json.readValue(sessionsResponse.body(), List.class);
        assertThat(sessionsList).as("sessions: %s, cookies: %s", sessionsList, client.cookies).hasSize(1);
        assertThat(sessionsList.get(0).get("current")).isEqualTo(true);

        HttpResponse<String> logout = post(client, "/auth/logout", null);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> afterLogout = get(client, "/auth/sessions");
        assertThat(afterLogout.statusCode()).isEqualTo(401);
    }

    @Test
    void loginBeforeVerificationIsRejected() throws Exception {
        String email = "unverified-" + System.nanoTime() + "@example.com";
        Client client = new Client();
        get(client, "/auth/sessions");
        post(client, "/auth/register", new RegisterRequest(email, "correct-horse-battery", "Grace"));

        HttpResponse<String> login = post(client, "/auth/login", new LoginRequest(email, "correct-horse-battery"));
        assertThat(login.statusCode()).isEqualTo(403);
        assertThat(login.body()).contains("EMAIL_NOT_VERIFIED");
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = "dupe-" + System.nanoTime() + "@example.com";
        Client client = new Client();
        get(client, "/auth/sessions");
        post(client, "/auth/register", new RegisterRequest(email, "correct-horse-battery", "Alan"));

        HttpResponse<String> second = post(client, "/auth/register", new RegisterRequest(email, "another-password", "Alan"));
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void secondSessionCoexistsUntilRevoked() throws Exception {
        String email = "concurrent-" + System.nanoTime() + "@example.com";
        String token = verificationToken(email, "Marie");

        Client setup = new Client();
        get(setup, "/auth/sessions");
        post(setup, "/auth/verify-email", new TokenRequest(token));

        Client deviceA = new Client();
        get(deviceA, "/auth/sessions");
        post(deviceA, "/auth/login", new LoginRequest(email, "correct-horse-battery"));

        Client deviceB = new Client();
        get(deviceB, "/auth/sessions");
        post(deviceB, "/auth/login", new LoginRequest(email, "correct-horse-battery"));

        HttpResponse<String> fromA = get(deviceA, "/auth/sessions");
        List<Map<String, Object>> seenByA = json.readValue(fromA.body(), List.class);
        assertThat(seenByA).hasSize(2);

        HttpResponse<String> revokeOthers = delete(deviceA, "/auth/sessions");
        assertThat(revokeOthers.statusCode()).isEqualTo(204);

        HttpResponse<String> bAfterRevoke = get(deviceB, "/auth/sessions");
        assertThat(bAfterRevoke.statusCode()).isEqualTo(401);

        HttpResponse<String> aStillWorks = get(deviceA, "/auth/sessions");
        assertThat(aStillWorks.statusCode()).isEqualTo(200);
    }
}
