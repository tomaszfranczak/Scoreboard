package io.github.tomaszfranczak.scoreboard;

import java.util.List;

public interface Scoreboard {

    static Scoreboard create() {
        return new LiveScoreboard();
    }

    MatchId startMatch(String homeTeam, String awayTeam);

    List<Match> getSummary();
}
