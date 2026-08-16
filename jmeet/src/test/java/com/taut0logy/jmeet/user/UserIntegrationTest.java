package com.taut0logy.jmeet.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class UserIntegrationTest {

    // 1x1 transparent PNG.
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

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
        HttpRequest request = HttpRequest.newBuilder(URI.create(api(path)))
                .header("Cookie", client.cookieHeader())
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", client.cookies.getOrDefault("XSRF-TOKEN", ""))
                .POST(BodyPublishers.ofString(json.writeValueAsString(body))).build();
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

    private HttpResponse<byte[]> uploadAvatar(Client client, byte[] imageBytes) throws Exception {
        String boundary = "----jmeet-test-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, "file", "avatar.png", "image/png", imageBytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create(api("/users/me/avatar")))
                .header("Cookie", client.cookieHeader())
                .header("X-XSRF-TOKEN", client.cookies.getOrDefault("XSRF-TOKEN", ""))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body)).build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        client.absorb(response);
        return response;
    }

    private static byte[] multipartBody(String boundary, String field, String filename, String contentType, byte[] data)
            throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n").getBytes());
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes());
        return out.toByteArray();
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
        post(client, "/auth/register", new com.taut0logy.jmeet.auth.RegisterRequest(email, "correct-horse-battery", name));
        String token = verificationToken(email);
        post(client, "/auth/verify-email", new com.taut0logy.jmeet.auth.TokenRequest(token));
        post(client, "/auth/login", new com.taut0logy.jmeet.auth.LoginRequest(email, "correct-horse-battery"));
        return client;
    }

    @Test
    void getReturnsUserAndProfile() throws Exception {
        String email = "profile-get-" + System.nanoTime() + "@example.com";
        Client client = registerAndLogin(email, "Ada Lovelace");

        HttpResponse<String> me = get(client, "/users/me");
        assertThat(me.statusCode()).isEqualTo(200);
        Map<String, Object> body = json.readValue(me.body(), Map.class);
        Map<String, Object> user = (Map<String, Object>) body.get("user");
        Map<String, Object> profile = (Map<String, Object>) body.get("profile");
        assertThat(user.get("email")).isEqualTo(email);
        assertThat(user.get("name")).isEqualTo("Ada Lovelace");
        assertThat(profile.get("displayName")).isEqualTo("Ada Lovelace");
        assertThat(profile.get("timezone")).isEqualTo("UTC");
    }

    @Test
    void patchAppliesOnlyProvidedFields() throws Exception {
        String email = "profile-patch-" + System.nanoTime() + "@example.com";
        Client client = registerAndLogin(email, "Grace Hopper");

        HttpResponse<String> first = patch(client, "/users/me",
                Map.of("displayName", "Grace H.", "timezone", "America/New_York"));
        Map<String, Object> firstBody = json.readValue(first.body(), Map.class);
        Map<String, Object> firstProfile = (Map<String, Object>) firstBody.get("profile");
        assertThat(firstProfile.get("displayName")).isEqualTo("Grace H.");
        assertThat(firstProfile.get("timezone")).isEqualTo("America/New_York");

        HttpResponse<String> second = patch(client, "/users/me", Map.of("defaultMicMuted", true));
        Map<String, Object> secondBody = json.readValue(second.body(), Map.class);
        Map<String, Object> secondProfile = (Map<String, Object>) secondBody.get("profile");
        assertThat(secondProfile.get("defaultMicMuted")).isEqualTo(true);
        assertThat(secondProfile.get("displayName")).isEqualTo("Grace H.");
        assertThat(secondProfile.get("timezone")).isEqualTo("America/New_York");
    }

    @Test
    void avatarUploadIsStoredAndServedBack() throws Exception {
        String email = "avatar-" + System.nanoTime() + "@example.com";
        Client client = registerAndLogin(email, "Katherine Johnson");

        HttpResponse<byte[]> upload = uploadAvatar(client, TINY_PNG);
        assertThat(upload.statusCode()).as("upload body: %s", new String(upload.body())).isEqualTo(200);
        Map<String, Object> uploadBody = json.readValue(upload.body(), Map.class);
        Map<String, Object> profile = (Map<String, Object>) uploadBody.get("profile");
        String avatarUrl = (String) profile.get("avatarUrl");
        assertThat(avatarUrl).startsWith("/api/files/avatars/");

        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + avatarUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(TINY_PNG);
    }

    @Test
    void oversizedAvatarIsRejected() throws Exception {
        String email = "avatar-big-" + System.nanoTime() + "@example.com";
        Client client = registerAndLogin(email, "Margaret Hamilton");

        byte[] big = new byte[3 * 1024 * 1024];
        HttpResponse<byte[]> upload = uploadAvatar(client, big);
        assertThat(upload.statusCode()).isEqualTo(413);
    }
}
