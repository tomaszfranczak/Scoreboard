package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.TeamAlreadyPlayingException;
import io.github.tomaszfranczak.scoreboard.model.Match;

import java.util.Objects;

final class MatchValidator {

    private static final String HOME_TEAM = "homeTeam";
    private static final String AWAY_TEAM = "awayTeam";

    private MatchValidator() {
    }

    static void validateTeamNames(String homeTeam, String awayTeam) {
        requireNotBlank(homeTeam, HOME_TEAM);
        requireNotBlank(awayTeam, AWAY_TEAM);
    }

    static void validateDifferentTeams(String home, String away) {
        if (TeamNames.same(home, away)) {
            throw new IllegalArgumentException("A team cannot play against itself: " + home);
        }
    }

    static void validateTeamsNotPlaying(Match match, ScoreboardState state) {
        requireNotPlaying(match.homeTeam(), state);
        requireNotPlaying(match.awayTeam(), state);
    }

    private static void requireNotBlank(String team, String parameter) {
        Objects.requireNonNull(team, parameter + " must not be null");
        if (team.isBlank()) {
            throw new IllegalArgumentException(parameter + " must not be blank");
        }
    }

    private static void requireNotPlaying(String team, ScoreboardState state) {
        if (state.isPlaying(team)) {
            throw new TeamAlreadyPlayingException(team);
        }
    }
}
