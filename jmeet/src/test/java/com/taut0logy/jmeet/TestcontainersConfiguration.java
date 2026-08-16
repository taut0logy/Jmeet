package com.taut0logy.jmeet;

import java.io.File;
import java.util.List;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String RABBITMQ_USER = "jmeet";
    static final String RABBITMQ_PASSWORD = "secret";

    /** Static so its Testcontainers-assigned STOMP port is readable from a @DynamicPropertySource
     * in another test class, which runs before the Spring context (and this @Bean) exists. */
    private static final GenericContainer<?> RABBITMQ = new GenericContainer<>(
            new ImageFromDockerfile().withFileFromFile(".", new File("docker/rabbitmq")))
            .withExposedPorts(5672, 61613)
            .withEnv("RABBITMQ_DEFAULT_USER", RABBITMQ_USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", RABBITMQ_PASSWORD)
            .withEnv("RABBITMQ_ERLANG_COOKIE", "jmeetdevcookie");

    static {
        RABBITMQ.start();
    }

    public static String rabbitmqHost() {
        return RABBITMQ.getHost();
    }

    public static int rabbitmqStompPort() {
        return RABBITMQ.getMappedPort(61613);
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
    }

    @Bean
    GenericContainer<?> rabbitmqContainer() {
        return RABBITMQ;
    }

    @Bean
    RabbitConnectionDetails rabbitConnectionDetails(GenericContainer<?> rabbitmqContainer) {
        return new RabbitConnectionDetails() {
            @Override
            public String getUsername() {
                return RABBITMQ_USER;
            }

            @Override
            public String getPassword() {
                return RABBITMQ_PASSWORD;
            }

            @Override
            public String getVirtualHost() {
                return "/";
            }

            @Override
            public List<Address> getAddresses() {
                return List.of(new Address(rabbitmqContainer.getHost(), rabbitmqContainer.getMappedPort(5672)));
            }
        };
    }

}
