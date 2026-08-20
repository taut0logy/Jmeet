package com.taut0logy.jmeet.meeting;

import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.meeting.member.MemberRole;

/** The seven-branch join access matrix, first match wins. o HTTP or database. */
public final class JoinAccessEvaluator {

    private JoinAccessEvaluator() {
    }

    public static JoinDecision evaluate(JoinContext ctx) {
        if (ctx.status() == MeetingStatus.CANCELLED || ctx.status() == MeetingStatus.ENDED) {
            throw new AppException(ErrorCode.MEETING_NOT_JOINABLE, "This meeting is no longer joinable.");
        }

        boolean hostOrCohost = ctx.isOwner() || ctx.memberRole() == MemberRole.HOST || ctx.memberRole() == MemberRole.COHOST;
        if (ctx.locked() && !hostOrCohost) {
            throw new AppException(ErrorCode.MEETING_LOCKED, "This meeting is locked.");
        }

        if (ctx.access() == MeetingAccess.AUTHENTICATED && !ctx.hasSession()) {
            throw new AppException(ErrorCode.AUTH_REQUIRED, "Sign in to join this meeting.");
        }

        if (ctx.access() == MeetingAccess.INVITED_ONLY && !ctx.isOwner() && ctx.memberRole() == null) {
            throw new AppException(ErrorCode.NOT_INVITED, "You are not invited to this meeting.");
        }

        if (ctx.isGuest() && !ctx.allowGuests()) {
            throw new AppException(ErrorCode.GUESTS_NOT_ALLOWED, "Guests are not allowed in this meeting.");
        }

        if (ctx.isGuest() && (ctx.guestDisplayName() == null || ctx.guestDisplayName().isBlank())) {
            throw new AppException(ErrorCode.DISPLAY_NAME_REQUIRED, "A display name is required to join as a guest.");
        }

        ParticipantRole role = ctx.isOwner() || ctx.memberRole() == MemberRole.HOST ? ParticipantRole.HOST
                : ctx.memberRole() == MemberRole.COHOST ? ParticipantRole.COHOST
                : ParticipantRole.PARTICIPANT;

        boolean requiresApproval = switch (ctx.waitingRoom()) {
            case OFF -> false;
            case GUESTS_ONLY -> ctx.isGuest();
            case EVERYONE -> role != ParticipantRole.HOST && role != ParticipantRole.COHOST;
        };

        return new JoinDecision(role, requiresApproval);
    }
}
