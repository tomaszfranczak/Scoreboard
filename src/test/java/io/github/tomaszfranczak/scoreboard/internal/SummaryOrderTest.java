package io.github.tomaszfranczak.scoreboard.internal;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryOrderTest {

    @Test
    void higherTotalScoreComesFirst() {
        // given
        StartedMatch lower = startedMatch(1, 0, 1);
        StartedMatch higher = startedMatch(2, 1, 0);

        // when
        List<StartedMatch> sorted = sort(lower, higher);

        // then
        assertThat(sorted).containsExactly(higher, lower);
    }

    @Test
    void withEqualTotalsTheLaterStartComesFirst() {
        // given
        StartedMatch earlier = startedMatch(2, 0, 0);
        StartedMatch later = startedMatch(1, 1, 1);

        // when
        List<StartedMatch> sorted = sort(earlier, later);

        // then
        assertThat(sorted).containsExactly(later, earlier);
    }

    @Test
    void totalScoreTakesPrecedenceOverStartOrder() {
        // given
        StartedMatch earlierWithMoreGoals = startedMatch(3, 0, 0);
        StartedMatch laterWithFewerGoals = startedMatch(1, 0, 5);

        // when
        List<StartedMatch> sorted = sort(laterWithFewerGoals, earlierWithMoreGoals);

        // then
        assertThat(sorted).containsExactly(earlierWithMoreGoals, laterWithFewerGoals);
    }

    private static List<StartedMatch> sort(StartedMatch... matches) {
        return List.of(matches).stream()
                .sorted(SummaryOrder.BY_TOTAL_SCORE_THEN_MOST_RECENT_START)
                .toList();
    }

    private static StartedMatch startedMatch(int homeScore, int awayScore, long startIndex) {
        Match match = new Match(new MatchId(UUID.randomUUID()), "Home", "Away", homeScore, awayScore);
        return new StartedMatch(match, startIndex);
    }
}
