package io.github.tomaszfranczak.scoreboard.model;

import java.util.Objects;

public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore) {

    private static final String MUST_NOT_BE_NULL = " must not be null";
    private static final String MUST_NOT_BE_NEGATIVE = " must not be negative: ";

    public Match {
        Objects.requireNonNull(id, "id" + MUST_NOT_BE_NULL);
        Objects.requireNonNull(homeTeam, "homeTeam" + MUST_NOT_BE_NULL);
        Objects.requireNonNull(awayTeam, "awayTeam" + MUST_NOT_BE_NULL);
        requireNonNegative(homeScore, "homeScore");
        requireNonNegative(awayScore, "awayScore");
    }

    public Match(MatchId id, String homeTeam, String awayTeam) {
        this(id, homeTeam, awayTeam, 0, 0);
    }

    public Match withScore(int homeScore, int awayScore) {
        return new Match(id, homeTeam, awayTeam, homeScore, awayScore);
    }

    private static void requireNonNegative(int score, String parameter) {
        if (score < 0) {
            throw new IllegalArgumentException(parameter + MUST_NOT_BE_NEGATIVE + score);
        }
    }
}
