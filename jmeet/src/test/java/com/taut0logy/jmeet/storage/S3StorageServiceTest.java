package com.taut0logy.jmeet.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.taut0logy.jmeet.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/** Proves the S3 driver against a real MinIO container — not the app's default test driver (local), on purpose. */
class S3StorageServiceTest {

    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "jmeettest")
            .withEnv("MINIO_ROOT_PASSWORD", "jmeettestsecret")
            .withExposedPorts(9000);

    static S3StorageService storageService;

    @BeforeAll
    static void setUp() {
        minio.start();
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);

        // S3StorageService (production, unmodified) relies on the AWS SDK's default
        // credentials chain, which checks these system properties — this is what
        // lets a real MinIO deployment authenticate via plain env vars too.
        System.setProperty("aws.accessKeyId", "jmeettest");
        System.setProperty("aws.secretAccessKey", "jmeettestsecret");

        S3Client bootstrapClient = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("jmeettest", "jmeettestsecret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        bootstrapClient.createBucket(CreateBucketRequest.builder().bucket("jmeet-test").build());
        bootstrapClient.close();

        StorageProperties properties = new StorageProperties(
                StorageProperties.Driver.S3, "jmeet-test", "us-east-1", endpoint, true, "unused");
        storageService = new S3StorageService(properties);
    }

    @AfterAll
    static void tearDown() {
        minio.stop();
    }

    @Test
    void putThenGetReturnsAPresignedUrlServingTheSameBytes() throws Exception {
        byte[] content = "hello from minio".getBytes(StandardCharsets.UTF_8);
        storageService.put("test/hello.txt", new ByteArrayInputStream(content), content.length, "text/plain");

        StorageContent result = storageService.get("test/hello.txt");
        assertThat(result).isInstanceOf(StorageContent.Redirect.class);
        String url = ((StorageContent.Redirect) result).url();

        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
    }
}
