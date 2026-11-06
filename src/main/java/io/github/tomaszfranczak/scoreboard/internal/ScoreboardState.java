package io.github.tomaszfranczak.scoreboard.internal;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ScoreboardState {

    static final ScoreboardState EMPTY = new ScoreboardState(Map.of(), 0);

    private final Map<MatchId, StartedMatch> matches;
    private final long nextStartSequence;
    private final List<Match> summary;

    private ScoreboardState(Map<MatchId, StartedMatch> matches, long nextStartSequence) {
        this.matches = Map.copyOf(matches);
        this.nextStartSequence = nextStartSequence;
        this.summary = this.matches.values().stream()
                .sorted(SummaryOrder.BY_TOTAL_SCORE_THEN_MOST_RECENT_START)
                .map(StartedMatch::match)
                .toList();
    }

    Optional<Match> findMatch(MatchId matchId) {
        return Optional.ofNullable(matches.get(matchId)).map(StartedMatch::match);
    }

    boolean isPlaying(String team) {
        return matches.values().stream()
                .map(StartedMatch::match)
                .anyMatch(match -> TeamNames.same(match.homeTeam(), team) || TeamNames.same(match.awayTeam(), team));
    }

    List<Match> summary() {
        return summary;
    }

    ScoreboardState withStartedMatch(Match match) {
        Map<MatchId, StartedMatch> updated = new HashMap<>(matches);
        updated.put(match.id(), new StartedMatch(match, nextStartSequence));
        return new ScoreboardState(updated, nextStartSequence + 1);
    }

    ScoreboardState withUpdatedMatch(Match match) {
        Map<MatchId, StartedMatch> updated = new HashMap<>(matches);
        updated.put(match.id(), matches.get(match.id()).withMatch(match));
        return new ScoreboardState(updated, nextStartSequence);
    }

    ScoreboardState withoutMatch(MatchId matchId) {
        Map<MatchId, StartedMatch> updated = new HashMap<>(matches);
        updated.remove(matchId);
        return new ScoreboardState(updated, nextStartSequence);
    }
}
