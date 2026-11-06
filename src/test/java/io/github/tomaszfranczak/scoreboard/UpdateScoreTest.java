package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.MatchNotFoundException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateScoreTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Test
    void summaryShowsUpdatedScore() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        // when
        scoreboard.updateScore(id, 0, 5);

        // then
        assertThat(scoreboard.getSummary())
                .containsExactly(new Match(id, "Mexico", "Canada", 0, 5));
    }

    @Test
    void applyingTheSameUpdateTwiceGivesTheSameResult() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 1, 0);

        // when
        scoreboard.updateScore(id, 1, 0);

        // then
        assertThat(scoreboard.getSummary())
                .containsExactly(new Match(id, "Mexico", "Canada", 1, 0));
    }

    @Test
    void scoreCanDecreaseToCorrectAMistake() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 2, 1);

        // when
        scoreboard.updateScore(id, 1, 1);

        // then
        assertThat(scoreboard.getSummary())
                .containsExactly(new Match(id, "Mexico", "Canada", 1, 1));
    }

    @Test
    void updatingOneMatchDoesNotChangeOthers() {
        // given
        MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");

        // when
        scoreboard.updateScore(mexicoCanada, 0, 5);

        // then
        assertThat(scoreboard.getSummary()).containsExactlyInAnyOrder(
                new Match(mexicoCanada, "Mexico", "Canada", 0, 5),
                new Match(spainBrazil, "Spain", "Brazil", 0, 0));
    }

    @Nested
    class Validation {

        @Test
        void rejectsNullMatchId() {
            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(null, 1, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("matchId");
        }

        @Test
        void rejectsNegativeHomeScore() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(id, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("homeScore");
        }

        @Test
        void rejectsNegativeAwayScore() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(id, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("awayScore");
        }

        @Test
        void rejectsUnknownMatch() {
            // given
            MatchId unknown = new MatchId(UUID.randomUUID());

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(unknown, 1, 0))
                    .isInstanceOf(MatchNotFoundException.class)
                    .hasMessageContaining(unknown.value().toString());
        }

        @Test
        void rejectedUpdateOfUnknownMatchDoesNotChangeSummary() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");
            MatchId unknown = new MatchId(UUID.randomUUID());

            // when
            assertThatThrownBy(() -> scoreboard.updateScore(unknown, 1, 0))
                    .isInstanceOf(MatchNotFoundException.class);

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
        }

        @Test
        void nullMatchIdIsReportedBeforeInvalidScores() {
            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(null, -1, 0))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectedUpdateDoesNotChangeSummary() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.updateScore(id, 1, 0);

            // when
            assertThatThrownBy(() -> scoreboard.updateScore(id, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 1, 0));
        }
    }
}
