package com.taut0logy.jmeet.storage;

import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "local")
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = Path.of(properties.localPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("could not create storage directory: " + root, e);
        }
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write file: " + key, e);
        }
    }

    @Override
    public StorageContent get(String key) {
        Path path = resolve(key);
        if (!Files.exists(path)) {
            throw new AppException(ErrorCode.NOT_FOUND, "File not found.");
        }
        try {
            String contentType = Files.probeContentType(path);
            return new StorageContent.Stream(
                    Files.newInputStream(path), contentType != null ? contentType : "application/octet-stream",
                    Files.size(path));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read file: " + key, e);
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid file key.");
        }
        return target;
    }
}
