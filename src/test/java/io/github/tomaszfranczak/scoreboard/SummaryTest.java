package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Test
    void newScoreboardHasEmptySummary() {
        // when
        var summary = scoreboard.getSummary();

        // then
        assertThat(summary).isEmpty();
    }

    @Test
    void summaryCannotBeModified() {
        // given
        scoreboard.startMatch("Mexico", "Canada");

        // when
        var summary = scoreboard.getSummary();

        // then
        assertThatThrownBy(summary::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void summaryTakenBeforeAChangeIsNotAffectedByIt() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        var summaryBefore = scoreboard.getSummary();

        // when
        scoreboard.updateScore(id, 1, 0);
        scoreboard.startMatch("Spain", "Brazil");

        // then
        assertThat(summaryBefore).containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Nested
    class Ordering {

        @Test
        void higherTotalScoreComesFirst() {
            // given
            MatchId lower = scoreboard.startMatch("Mexico", "Canada");
            MatchId higher = scoreboard.startMatch("Spain", "Brazil");
            scoreboard.updateScore(lower, 1, 0);
            scoreboard.updateScore(higher, 2, 1);

            // when
            var summary = scoreboard.getSummary();

            // then
            assertThat(summary).extracting(Match::id).containsExactly(higher, lower);
        }

        @Test
        void withEqualTotalsTheMostRecentlyStartedMatchComesFirst() {
            // given
            MatchId older = scoreboard.startMatch("Mexico", "Canada");
            MatchId newer = scoreboard.startMatch("Spain", "Brazil");
            scoreboard.updateScore(older, 2, 0);
            scoreboard.updateScore(newer, 1, 1);

            // when
            var summary = scoreboard.getSummary();

            // then
            assertThat(summary).extracting(Match::id).containsExactly(newer, older);
        }

        @Test
        void matchesStartedOneRightAfterAnotherAreOrderedByStartOrder() {
            // given
            List<MatchId> startOrder = IntStream.range(0, 10)
                    .mapToObj(i -> scoreboard.startMatch("Home " + i, "Away " + i))
                    .toList();

            // when
            var summary = scoreboard.getSummary();

            // then
            assertThat(summary).extracting(Match::id).containsExactlyElementsOf(startOrder.reversed());
        }

        @Test
        void orderFollowsScoreUpdates() {
            // given
            MatchId first = scoreboard.startMatch("Mexico", "Canada");
            MatchId second = scoreboard.startMatch("Spain", "Brazil");
            scoreboard.updateScore(second, 1, 0);

            // when
            scoreboard.updateScore(first, 2, 0);

            // then
            assertThat(scoreboard.getSummary()).extracting(Match::id).containsExactly(first, second);
        }

        @Test
        void updatingTheScoreDoesNotChangeTheStartOrder() {
            // given
            MatchId older = scoreboard.startMatch("Mexico", "Canada");
            MatchId newer = scoreboard.startMatch("Spain", "Brazil");

            // when
            scoreboard.updateScore(older, 1, 0);
            scoreboard.updateScore(older, 0, 0);

            // then
            assertThat(scoreboard.getSummary()).extracting(Match::id).containsExactly(newer, older);
        }

        @Test
        void finishingAMatchKeepsTheOrderOfTheOthers() {
            // given
            MatchId first = scoreboard.startMatch("Mexico", "Canada");
            MatchId second = scoreboard.startMatch("Spain", "Brazil");
            MatchId third = scoreboard.startMatch("Germany", "France");

            // when
            scoreboard.finishMatch(second);

            // then
            assertThat(scoreboard.getSummary()).extracting(Match::id).containsExactly(third, first);
        }
    }
}
