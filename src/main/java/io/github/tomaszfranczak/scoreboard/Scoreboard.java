package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.List;

public interface Scoreboard {

    static Scoreboard create() {
        return new LiveScoreboard();
    }

    MatchId startMatch(String homeTeam, String awayTeam);

    void updateScore(MatchId matchId, int homeScore, int awayScore);

    List<Match> getSummary();
}
