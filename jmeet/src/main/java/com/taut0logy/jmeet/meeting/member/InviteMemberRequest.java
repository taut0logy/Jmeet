package com.taut0logy.jmeet.meeting.member;

public record InviteMemberRequest(String email, String userId, MemberRole role) {
}
