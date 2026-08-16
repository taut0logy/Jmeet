package com.taut0logy.jmeet.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    @DynamicPropertySource
    static void tightLimit(DynamicPropertyRegistry registry) {
        registry.add("app.auth.rate-limit.login.limit", () -> "3");
        registry.add("app.auth.rate-limit.login.period", () -> "1m");
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

    @Test
    void loginIsRateLimitedPerAccountAfterConfiguredAttempts() throws Exception {
        Client client = new Client();
        get(client, "/auth/sessions");
        String email = "ratelimited-" + System.nanoTime() + "@example.com";

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> attempt = post(client, "/auth/login", new LoginRequest(email, "wrong-password"));
            assertThat(attempt.statusCode()).as("attempt %d", i).isEqualTo(401);
        }

        HttpResponse<String> fourth = post(client, "/auth/login", new LoginRequest(email, "wrong-password"));
        assertThat(fourth.statusCode()).isEqualTo(429);
        assertThat(fourth.body()).contains("RATE_LIMIT_EXCEEDED");
    }
}
