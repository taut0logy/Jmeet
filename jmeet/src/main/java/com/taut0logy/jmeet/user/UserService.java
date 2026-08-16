package com.taut0logy.jmeet.user;

import com.taut0logy.jmeet.auth.AppUser;
import com.taut0logy.jmeet.auth.AppUserRepository;
import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_AVATAR_TYPES =
            Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp");

    private final AppUserRepository users;
    private final ProfileRepository profiles;
    private final StorageService storageService;

    public UserService(AppUserRepository users, ProfileRepository profiles, StorageService storageService) {
        this.users = users;
        this.profiles = profiles;
        this.storageService = storageService;
    }

    public MeResponse me(String userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        Profile profile = profiles.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Profile not found."));
        return new MeResponse(UserSummary.of(user, profile), ProfileResponse.from(profile));
    }

    @Transactional
    public MeResponse updateProfile(String userId, ProfileUpdateRequest request) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        Profile profile = profiles.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Profile not found."));
        profile.applyPartial(request);
        return new MeResponse(UserSummary.of(user, profile), ProfileResponse.from(profile));
    }

    @Transactional
    public MeResponse updateAvatar(String userId, MultipartFile file) {
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "Images must be 2MB or smaller.");
        }
        String extension = ALLOWED_AVATAR_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG, or WebP images are allowed.");
        }

        AppUser user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found."));
        Profile profile = profiles.findById(userId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Profile not found."));

        String key = "avatars/" + userId + "." + extension;
        try (InputStream in = file.getInputStream()) {
            storageService.put(key, in, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("failed to read uploaded file", e);
        }
        profile.setAvatarUrl("/api/files/" + key);
        return new MeResponse(UserSummary.of(user, profile), ProfileResponse.from(profile));
    }
}
