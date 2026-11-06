package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.List;

final class LiveScoreboard implements Scoreboard {

    private final Object writeLock = new Object();
    private volatile ScoreboardState state = ScoreboardState.EMPTY;

    @Override
    public MatchId startMatch(String homeTeam, String awayTeam) {
        Match match = MatchFactory.create(homeTeam, awayTeam);

        synchronized (writeLock) {
            ScoreboardState current = state;
            MatchValidator.validateTeamsNotPlaying(match, current);
            state = current.addMatch(match);
        }
        return match.id();
    }

    @Override
    public List<Match> getSummary() {
        return state.summary();
    }
}
