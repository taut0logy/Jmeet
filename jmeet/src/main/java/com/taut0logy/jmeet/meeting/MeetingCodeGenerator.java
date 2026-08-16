package com.taut0logy.jmeet.meeting;

import java.security.SecureRandom;

/** §9.1: 'xxx-xxxx-xxx', cryptographic RNG, never sequential or id-derived. */
final class MeetingCodeGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private MeetingCodeGenerator() {
    }

    static String generate() {
        return segment(3) + "-" + segment(4) + "-" + segment(3);
    }

    private static String segment(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
