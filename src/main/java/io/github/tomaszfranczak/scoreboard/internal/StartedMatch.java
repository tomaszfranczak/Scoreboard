package io.github.tomaszfranczak.scoreboard.internal;

import io.github.tomaszfranczak.scoreboard.model.Match;

record StartedMatch(Match match, long startIndex, long lastSequenceNumber) {

    private static final long NO_SEQUENCE_NUMBER = -1;

    StartedMatch(Match match, long startIndex) {
        this(match, startIndex, NO_SEQUENCE_NUMBER);
    }

    StartedMatch withMatch(Match updated) {
        return new StartedMatch(updated, startIndex, lastSequenceNumber);
    }

    StartedMatch withMatch(Match updated, long sequenceNumber) {
        return new StartedMatch(updated, startIndex, sequenceNumber);
    }

    boolean isNewer(long sequenceNumber) {
        return sequenceNumber > lastSequenceNumber;
    }
}
