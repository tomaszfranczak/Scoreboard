package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.MatchNotFoundException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.List;

final class LiveScoreboard implements Scoreboard {

    private final Object writeLock = new Object();
    private volatile ScoreboardState state = ScoreboardState.EMPTY;

    @Override
    public List<Match> getSummary() {
        return state.summary();
    }

    @Override
    public MatchId startMatch(String homeTeam, String awayTeam) {
        Match match = MatchFactory.create(homeTeam, awayTeam);

        synchronized (writeLock) {
            ScoreboardState current = state;
            MatchValidator.validateTeamsNotPlaying(match, current);
            state = current.withMatch(match);
        }
        return match.id();
    }

    @Override
    public void updateScore(MatchId matchId, int homeScore, int awayScore) {
        MatchValidator.validateScoreUpdate(matchId, homeScore, awayScore);

        synchronized (writeLock) {
            ScoreboardState current = state;
            Match match = matchInProgress(matchId, current);
            state = current.withMatch(match.withScore(homeScore, awayScore));
        }
    }

    @Override
    public void finishMatch(MatchId matchId) {
        MatchValidator.validateMatchId(matchId);

        synchronized (writeLock) {
            ScoreboardState current = state;
            Match match = matchInProgress(matchId, current);
            state = current.withoutMatch(match.id());
        }
    }

    private static Match matchInProgress(MatchId matchId, ScoreboardState state) {
        return state.findMatch(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
    }
}
