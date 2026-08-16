package com.taut0logy.jmeet.storage;

import com.taut0logy.jmeet.config.StorageProperties;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3StorageService(StorageProperties properties) {
        this.properties = properties;
        Region region = Region.of(properties.region() != null ? properties.region() : "us-east-1");

        var s3Builder = S3Client.builder().region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
        var presignerBuilder = S3Presigner.builder().region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            s3Builder.endpointOverride(URI.create(properties.endpoint()));
            presignerBuilder.endpointOverride(URI.create(properties.endpoint()));
        }
        this.s3 = s3Builder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        s3.putObject(
                PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public StorageContent get(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .build();
        String url = presigner.presignGetObject(presignRequest).url().toString();
        return new StorageContent.Redirect(url);
    }
}
