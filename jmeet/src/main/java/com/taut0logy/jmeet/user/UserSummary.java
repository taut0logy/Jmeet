package com.taut0logy.jmeet.user;

import com.taut0logy.jmeet.auth.AppUser;

public record UserSummary(String id, String email, String name, String image) {

    public static UserSummary of(AppUser user, Profile profile) {
        String image = profile.getAvatarUrl() != null ? profile.getAvatarUrl() : user.getImageUrl();
        return new UserSummary(user.getId(), user.getEmail(), user.getName(), image);
    }
}
