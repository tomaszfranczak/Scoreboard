package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.TeamAlreadyPlayingException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.Objects;

final class MatchValidator {

    private static final String HOME_TEAM = "homeTeam";
    private static final String AWAY_TEAM = "awayTeam";
    private static final String MATCH_ID = "matchId";
    private static final String HOME_SCORE = "homeScore";
    private static final String AWAY_SCORE = "awayScore";

    private static final String MUST_NOT_BE_NULL = " must not be null";
    private static final String MUST_NOT_BE_BLANK = " must not be blank";
    private static final String MUST_NOT_BE_NEGATIVE = " must not be negative: ";

    private MatchValidator() {
    }

    static void validateScoreUpdate(MatchId matchId, int homeScore, int awayScore) {
        Objects.requireNonNull(matchId, MATCH_ID + MUST_NOT_BE_NULL);
        requireNonNegative(homeScore, HOME_SCORE);
        requireNonNegative(awayScore, AWAY_SCORE);
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
        Objects.requireNonNull(team, parameter + MUST_NOT_BE_NULL);
        if (team.isBlank()) {
            throw new IllegalArgumentException(parameter + MUST_NOT_BE_BLANK);
        }
    }

    private static void requireNonNegative(int score, String parameter) {
        if (score < 0) {
            throw new IllegalArgumentException(parameter + MUST_NOT_BE_NEGATIVE + score);
        }
    }

    private static void requireNotPlaying(String team, ScoreboardState state) {
        if (state.isPlaying(team)) {
            throw new TeamAlreadyPlayingException(team);
        }
    }
}
