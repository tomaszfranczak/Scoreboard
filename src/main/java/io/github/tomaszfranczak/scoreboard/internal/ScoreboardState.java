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
    private final long nextStartIndex;
    private final List<Match> summary;

    private ScoreboardState(Map<MatchId, StartedMatch> matches, long nextStartIndex) {
        this.matches = Map.copyOf(matches);
        this.nextStartIndex = nextStartIndex;
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
                .anyMatch(match -> plays(match, team));
    }

    boolean isNewerSequenceNumber(MatchId matchId, long sequenceNumber) {
        return matches.get(matchId).isNewer(sequenceNumber);
    }

    List<Match> summary() {
        return summary;
    }

    ScoreboardState withStartedMatch(Match match) {
        return new ScoreboardState(copyWith(new StartedMatch(match, nextStartIndex)), nextStartIndex + 1);
    }

    ScoreboardState withUpdatedMatch(Match match) {
        return new ScoreboardState(copyWith(matches.get(match.id()).withMatch(match)), nextStartIndex);
    }

    ScoreboardState withUpdatedMatch(Match match, long sequenceNumber) {
        return new ScoreboardState(copyWith(matches.get(match.id()).withMatch(match, sequenceNumber)), nextStartIndex);
    }

    ScoreboardState withoutMatch(MatchId matchId) {
        Map<MatchId, StartedMatch> updated = new HashMap<>(matches);
        updated.remove(matchId);
        return new ScoreboardState(updated, nextStartIndex);
    }

    private Map<MatchId, StartedMatch> copyWith(StartedMatch started) {
        Map<MatchId, StartedMatch> updated = new HashMap<>(matches);
        updated.put(started.match().id(), started);
        return updated;
    }

    private static boolean plays(Match match, String team) {
        return TeamNames.same(match.homeTeam(), team) || TeamNames.same(match.awayTeam(), team);
    }
}
