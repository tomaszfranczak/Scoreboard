package io.github.tomaszfranczak.scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LiveScoreboard implements Scoreboard {

    private final Map<MatchId, Match> matches = new HashMap<>();

    @Override
    public MatchId startMatch(String homeTeam, String awayTeam) {
        MatchId id = MatchId.generate();
        matches.put(id, new Match(id, homeTeam, awayTeam, 0, 0));
        return id;
    }

    @Override
    public List<Match> getSummary() {
        return List.copyOf(matches.values());
    }
}
