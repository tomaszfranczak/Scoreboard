package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.MatchNotFoundException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequencedUpdateScoreTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Test
    void firstSequencedUpdateIsApplied() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        // when
        boolean applied = scoreboard.updateScore(id, 1, 0, 7);

        // then
        assertThat(applied).isTrue();
        assertThat(scoreboard.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 1, 0));
    }

    @Test
    void higherSequenceNumberIsApplied() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 1, 0, 1);

        // when
        boolean applied = scoreboard.updateScore(id, 2, 0, 2);

        // then
        assertThat(applied).isTrue();
        assertThat(scoreboard.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 2, 0));
    }

    @Test
    void lowerSequenceNumberArrivingLateIsIgnored() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 2, 0, 2);

        // when
        boolean applied = scoreboard.updateScore(id, 1, 0, 1);

        // then
        assertThat(applied).isFalse();
        assertThat(scoreboard.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 2, 0));
    }

    @Test
    void sameSequenceNumberIsIgnoredAsADuplicate() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 1, 0, 3);

        // when
        boolean applied = scoreboard.updateScore(id, 1, 0, 3);

        // then
        assertThat(applied).isFalse();
        assertThat(scoreboard.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 1, 0));
    }

    @Test
    void correctionWithAHigherSequenceNumberCanLowerTheScore() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 2, 1, 1);

        // when
        boolean applied = scoreboard.updateScore(id, 1, 1, 2);

        // then
        assertThat(applied).isTrue();
        assertThat(scoreboard.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 1, 1));
    }

    @Test
    void updateWithoutSequenceNumberIsAManualCorrectionThatKeepsTheSequence() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(id, 2, 0, 5);

        // when
        scoreboard.updateScore(id, 1, 0);

        // then
        assertThat(scoreboard.getSummary()).containsExactly(new Match(id, "Mexico", "Canada", 1, 0));
        assertThat(scoreboard.updateScore(id, 3, 0, 4)).isFalse();
        assertThat(scoreboard.updateScore(id, 3, 0, 6)).isTrue();
    }

    @Test
    void sequenceNumbersAreTrackedPerMatch() {
        // given
        MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");
        scoreboard.updateScore(mexicoCanada, 1, 0, 10);

        // when
        boolean applied = scoreboard.updateScore(spainBrazil, 1, 0, 1);

        // then
        assertThat(applied).isTrue();
    }

    @Nested
    class Validation {

        @Test
        void rejectsNullMatchId() {
            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(null, 1, 0, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("matchId");
        }

        @Test
        void rejectsNegativeScore() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(id, -1, 0, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("homeScore");
        }

        @Test
        void rejectsNegativeSequenceNumber() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(id, 1, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sequenceNumber");
        }

        @Test
        void rejectsUnknownMatch() {
            // given
            MatchId unknown = new MatchId(UUID.randomUUID());

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(unknown, 1, 0, 1))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void rejectsFinishedMatch() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch(id);

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(id, 1, 0, 1))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }
}
