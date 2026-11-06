package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Test;

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
}
