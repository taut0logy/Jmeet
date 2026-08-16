package com.taut0logy.jmeet.common;

import com.github.f4b6a3.uuid.UuidCreator;

public final class Ids {

    private Ids() {
    }

    public static String next() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
}
