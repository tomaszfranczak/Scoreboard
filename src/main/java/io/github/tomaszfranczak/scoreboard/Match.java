package io.github.tomaszfranczak.scoreboard;

public record Match(MatchId id, String homeTeam, String awayTeam, int homeScore, int awayScore) {
}
