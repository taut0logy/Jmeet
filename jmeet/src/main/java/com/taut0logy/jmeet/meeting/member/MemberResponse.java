package com.taut0logy.jmeet.meeting.member;

public record MemberResponse(String id, String userId, String email, MemberRole role) {

    public static MemberResponse from(MeetingMember member) {
        return new MemberResponse(member.getId(), member.getUserId(), member.getEmail(), member.getRole());
    }
}
