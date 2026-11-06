package io.github.tomaszfranczak.scoreboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreboardTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Test
    void newScoreboardHasEmptySummary() {
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @Test
    void startedMatchAppearsInSummaryWithZeroScore() {
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        assertThat(scoreboard.getSummary())
                .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void allStartedMatchesAppearInSummary() {
        MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");

        assertThat(scoreboard.getSummary()).containsExactlyInAnyOrder(
                new Match(mexicoCanada, "Mexico", "Canada", 0, 0),
                new Match(spainBrazil, "Spain", "Brazil", 0, 0));
    }

    @Test
    void eachStartedMatchGetsDifferentId() {
        MatchId first = scoreboard.startMatch("Mexico", "Canada");
        MatchId second = scoreboard.startMatch("Spain", "Brazil");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void summaryCannotBeModified() {
        scoreboard.startMatch("Mexico", "Canada");

        var summary = scoreboard.getSummary();

        assertThatThrownBy(summary::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
