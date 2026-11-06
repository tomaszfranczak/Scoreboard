package io.github.tomaszfranczak.scoreboard.model;

import java.util.Objects;

public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore) {

    public Match {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        requireNonNegative(homeScore, "homeScore");
        requireNonNegative(awayScore, "awayScore");
    }

    public Match(MatchId id, String homeTeam, String awayTeam) {
        this(id, homeTeam, awayTeam, 0, 0);
    }

    private static void requireNonNegative(int score, String parameter) {
        if (score < 0) {
            throw new IllegalArgumentException(parameter + " must not be negative: " + score);
        }
    }
}
