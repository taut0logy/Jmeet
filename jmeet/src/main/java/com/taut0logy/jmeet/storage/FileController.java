package com.taut0logy.jmeet.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UrlPathHelper;

@RestController
public class FileController {

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/api/files/**")
    public ResponseEntity<?> get(HttpServletRequest request) {
        String key = new UrlPathHelper().getPathWithinApplication(request).replaceFirst("^/api/files/", "");
        StorageContent content = storageService.get(key);
        return switch (content) {
            case StorageContent.Redirect redirect ->
                    ResponseEntity.status(302).header("Location", redirect.url()).build();
            case StorageContent.Stream stream -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(stream.contentType()))
                    .contentLength(stream.size())
                    .body(new InputStreamResource(stream.data()));
        };
    }
}
