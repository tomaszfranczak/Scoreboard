package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ScoreboardState {

    static final ScoreboardState EMPTY = new ScoreboardState(Map.of());

    private final Map<MatchId, Match> matches;
    private final List<Match> summary;

    private ScoreboardState(Map<MatchId, Match> matches) {
        this.matches = Map.copyOf(matches);
        this.summary = List.copyOf(this.matches.values());
    }

    Optional<Match> findMatch(MatchId matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    boolean isPlaying(String team) {
        return matches.values().stream()
                .anyMatch(match -> TeamNames.same(match.homeTeam(), team) || TeamNames.same(match.awayTeam(), team));
    }

    List<Match> summary() {
        return summary;
    }

    ScoreboardState withMatch(Match match) {
        Map<MatchId, Match> updated = new HashMap<>(matches);
        updated.put(match.id(), match);
        return new ScoreboardState(updated);
    }

    ScoreboardState withoutMatch(MatchId matchId) {
        Map<MatchId, Match> updated = new HashMap<>(matches);
        updated.remove(matchId);
        return new ScoreboardState(updated);
    }
}
