package com.taut0logy.jmeet.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.meeting.member.MemberRole;
import org.junit.jupiter.api.Test;

class JoinAccessEvaluatorTest {

    private static JoinContext ctx(MeetingStatus status, boolean locked, MeetingAccess access, boolean allowGuests,
            WaitingRoomPolicy waitingRoom, boolean hasSession, boolean isOwner, MemberRole memberRole, String guestName) {
        return new JoinContext(status, locked, access, allowGuests, waitingRoom, hasSession, isOwner, memberRole, guestName);
    }

    @Test
    void branch1_cancelledOrEndedIsNotJoinable() {
        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.CANCELLED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, false, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.MEETING_NOT_JOINABLE));

        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.ENDED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, false, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.MEETING_NOT_JOINABLE));
    }

    @Test
    void branch2_lockedRejectsNonHostNonCohost() {
        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, true, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, false, MemberRole.INVITEE, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.MEETING_LOCKED));
    }

    @Test
    void branch2_lockedAdmitsHostAndCohost() {
        JoinDecision owner = JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, true, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, true, null, null));
        assertThat(owner.role()).isEqualTo(ParticipantRole.HOST);

        JoinDecision cohost = JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, true, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, false, MemberRole.COHOST, null));
        assertThat(cohost.role()).isEqualTo(ParticipantRole.COHOST);
    }

    @Test
    void branch3_authenticatedAccessRejectsGuest() {
        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.AUTHENTICATED, true, WaitingRoomPolicy.OFF, false, false, null, "Ada")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.AUTH_REQUIRED));
    }

    @Test
    void branch4_invitedOnlyRejectsNonMember() {
        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.INVITED_ONLY, true, WaitingRoomPolicy.OFF, true, false, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.NOT_INVITED));
    }

    @Test
    void branch4_invitedOnlyAdmitsInvitee() {
        JoinDecision decision = JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.INVITED_ONLY, true, WaitingRoomPolicy.OFF, true, false, MemberRole.INVITEE, null));
        assertThat(decision.role()).isEqualTo(ParticipantRole.PARTICIPANT);
    }

    @Test
    void branch5_guestsNotAllowedRejectsGuest() {
        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, false, WaitingRoomPolicy.OFF, false, false, null, "Ada")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.GUESTS_NOT_ALLOWED));
    }

    @Test
    void branch6_guestWithoutDisplayNameIsRejected() {
        assertThatThrownBy(() -> JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, false, false, null, "  ")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.DISPLAY_NAME_REQUIRED));
    }

    @Test
    void branch7_roleResolution() {
        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, true, null, null))
                .role()).isEqualTo(ParticipantRole.HOST);

        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, false, MemberRole.HOST, null))
                .role()).isEqualTo(ParticipantRole.HOST);

        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, true, false, MemberRole.COHOST, null))
                .role()).isEqualTo(ParticipantRole.COHOST);

        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, false, false, null, "Ada"))
                .role()).isEqualTo(ParticipantRole.PARTICIPANT);
    }

    @Test
    void branch7_approvalRequirement() {
        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.OFF, false, false, null, "Ada"))
                .requiresApproval()).isFalse();

        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.GUESTS_ONLY, false, false, null, "Ada"))
                .requiresApproval()).isTrue();
        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.GUESTS_ONLY, true, false, MemberRole.INVITEE, null))
                .requiresApproval()).isFalse();

        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.EVERYONE, true, false, MemberRole.INVITEE, null))
                .requiresApproval()).isTrue();
        assertThat(JoinAccessEvaluator.evaluate(
                ctx(MeetingStatus.SCHEDULED, false, MeetingAccess.LINK, true, WaitingRoomPolicy.EVERYONE, true, false, MemberRole.COHOST, null))
                .requiresApproval()).isFalse();
    }
}
