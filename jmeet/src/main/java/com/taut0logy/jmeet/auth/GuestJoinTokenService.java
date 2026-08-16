package com.taut0logy.jmeet.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.taut0logy.jmeet.common.AppException;
import com.taut0logy.jmeet.common.ErrorCode;
import com.taut0logy.jmeet.common.Ids;
import com.taut0logy.jmeet.config.JoinTokenProperties;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** §7.1: HS256 JWT, 120s TTL, single-use, carrying meetingCode/displayName/guestId. */
@Service
public class GuestJoinTokenService {

    private final JoinTokenProperties properties;
    private final StringRedisTemplate redis;

    public GuestJoinTokenService(JoinTokenProperties properties, StringRedisTemplate redis) {
        this.properties = properties;
        this.redis = redis;
    }

    public String mint(String meetingCode, String displayName) {
        return mint(meetingCode, displayName, null, "PARTICIPANT", false);
    }

    public String mint(String meetingCode, String displayName, String userId, String role, boolean requiresApproval) {
        Instant now = Instant.now();
        String guestId = userId == null ? Ids.next() : null;
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(userId != null ? userId : guestId)
                .claim("meetingCode", meetingCode)
                .claim("displayName", displayName)
                .claim("role", role)
                .claim("requiresApproval", requiresApproval)
                .jwtID(Ids.next())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.ttl())));
        if (userId != null) builder.claim("userId", userId);
        JWTClaimsSet claims = builder.build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(properties.secret().getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign join token", e);
        }
        return jwt.serialize();
    }

    public GuestJoinToken verify(String token) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(properties.secret().getBytes(StandardCharsets.UTF_8)))) {
                throw new AppException(ErrorCode.JOIN_TOKEN_INVALID, "This join link is invalid.");
            }
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.JOIN_TOKEN_INVALID, "This join link is invalid.");
        }

        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
            throw new AppException(ErrorCode.JOIN_TOKEN_EXPIRED, "This join link has expired.");
        }

        String usedKey = "join-token:used:" + claims.getJWTID();
        Boolean firstUse = redis.opsForValue().setIfAbsent(usedKey, "1", properties.ttl());
        if (firstUse == null || !firstUse) {
            throw new AppException(ErrorCode.JOIN_TOKEN_REUSED, "This join link has already been used.");
        }

        try {
            String userId = claims.getStringClaim("userId");
            String guestId = userId == null ? claims.getSubject() : null;
            return new GuestJoinToken(claims.getStringClaim("meetingCode"), claims.getStringClaim("displayName"),
                    guestId, userId, claims.getStringClaim("role"), Boolean.TRUE.equals(claims.getBooleanClaim("requiresApproval")));
        } catch (ParseException e) {
            throw new AppException(ErrorCode.JOIN_TOKEN_INVALID, "This join link is invalid.");
        }
    }
}
