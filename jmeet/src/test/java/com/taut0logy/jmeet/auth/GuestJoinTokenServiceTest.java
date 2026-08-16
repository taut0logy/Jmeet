package com.taut0logy.jmeet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taut0logy.jmeet.TestcontainersConfiguration;
import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GuestJoinTokenServiceTest {

    @DynamicPropertySource
    static void shortTtl(DynamicPropertyRegistry registry) {
        registry.add("app.join-token.ttl", () -> "1s");
    }

    @Autowired
    private GuestJoinTokenService tokens;

    @Test
    void mintedTokenVerifiesOnceThenIsRejectedAsReused() {
        String token = tokens.mint("abc-defg-hij", "Ada Lovelace");

        GuestJoinToken verified = tokens.verify(token);
        assertThat(verified.meetingCode()).isEqualTo("abc-defg-hij");
        assertThat(verified.displayName()).isEqualTo("Ada Lovelace");
        assertThat(verified.guestId()).isNotBlank();

        assertThatThrownBy(() -> tokens.verify(token))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.JOIN_TOKEN_REUSED));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = tokens.mint("abc-defg-hij", "Grace Hopper");
        Thread.sleep(1200);

        assertThatThrownBy(() -> tokens.verify(token))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.JOIN_TOKEN_EXPIRED));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokens.mint("abc-defg-hij", "Katherine Johnson");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThatThrownBy(() -> tokens.verify(tampered))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(ErrorCode.JOIN_TOKEN_INVALID));
    }
}
