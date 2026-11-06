package io.github.tomaszfranczak.scoreboard.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    private final MatchId id = new MatchId(UUID.randomUUID());

    @Test
    void matchCreatedWithoutScoresStartsAtZero() {
        // when
        Match match = new Match(id, "Mexico", "Canada");

        // then
        assertThat(match).isEqualTo(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void withScoreKeepsIdAndTeamsAndChangesOnlyTheScore() {
        // given
        Match match = new Match(id, "Mexico", "Canada");

        // when
        Match updated = match.withScore(0, 5);

        // then
        assertThat(updated).isEqualTo(new Match(id, "Mexico", "Canada", 0, 5));
        assertThat(match).isEqualTo(new Match(id, "Mexico", "Canada", 0, 0));
    }

    @Test
    void totalScoreIsTheSumOfBothScores() {
        // given
        Match match = new Match(id, "Mexico", "Canada", 2, 3);

        // when
        long total = match.totalScore();

        // then
        assertThat(total).isEqualTo(5);
    }

    @Test
    void totalScoreDoesNotOverflowForTheHighestScores() {
        // given
        Match match = new Match(id, "Mexico", "Canada", Integer.MAX_VALUE, Integer.MAX_VALUE);

        // when
        long total = match.totalScore();

        // then
        assertThat(total).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void rejectsNullId() {
        // when & then
        assertThatThrownBy(() -> new Match(null, "Mexico", "Canada"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullHomeTeam() {
        // when & then
        assertThatThrownBy(() -> new Match(id, null, "Canada"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullAwayTeam() {
        // when & then
        assertThatThrownBy(() -> new Match(id, "Mexico", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeHomeScore() {
        // when & then
        assertThatThrownBy(() -> new Match(id, "Mexico", "Canada", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAwayScore() {
        // when & then
        assertThatThrownBy(() -> new Match(id, "Mexico", "Canada", 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
