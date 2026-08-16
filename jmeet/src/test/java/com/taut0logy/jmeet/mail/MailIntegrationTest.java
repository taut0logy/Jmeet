package com.taut0logy.jmeet.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MailIntegrationTest {

    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025);

    @BeforeAll
    static void startMailpit() {
        mailpit.start();
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private Mailer mailer;

    @Autowired
    private MailService mailService;

    @Autowired
    private com.taut0logy.jmeet.outbox.OutboxRelay outboxRelay;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String mailpitApi(String path) {
        return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path;
    }

    @Test
    void rendersTemplateAndDelivers() throws Exception {
        EmailMessage email = new EmailMessage(
                "someone@example.com",
                "Welcome to jmeet",
                "notice",
                Map.of("title", "Welcome", "body", "Your account is ready.",
                        "actionUrl", "https://jmeet.local/verify", "actionLabel", "Verify email"));

        mailer.send(email);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            HttpResponse<String> list = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = objectMapper.readValue(list.body(), Map.class);
            assertThat((Integer) body.get("count")).isEqualTo(1);
        });

        HttpResponse<String> list = http.send(
                HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> listBody = objectMapper.readValue(list.body(), Map.class);
        var messages = (java.util.List<Map<String, Object>>) listBody.get("messages");
        String id = (String) messages.get(0).get("ID");
        assertThat(messages.get(0).get("Subject")).isEqualTo("Welcome to jmeet");

        HttpResponse<String> detail = http.send(
                HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/message/" + id))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> detailBody = objectMapper.readValue(detail.body(), Map.class);
        String html = (String) detailBody.get("HTML");

        assertThat(html).contains("Welcome").contains("Your account is ready.")
                .contains("https://jmeet.local/verify").contains("Verify email");
    }

    @Test
    void deliversThroughOutboxAndJobQueue() {
        EmailMessage email = new EmailMessage(
                "queued@example.com", "Queued via outbox", "notice",
                Map.of("title", "Queued", "body", "Sent through the job pipeline."));

        mailService.enqueue("agg-mail-1", email);
        outboxRelay.poll();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            HttpResponse<String> list = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitApi("/api/v1/messages"))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = objectMapper.readValue(list.body(), Map.class);
            var messages = (java.util.List<Map<String, Object>>) body.get("messages");
            assertThat(messages).anyMatch(m -> "Queued via outbox".equals(m.get("Subject")));
        });
    }
}
