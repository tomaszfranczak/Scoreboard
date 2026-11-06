package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.MatchNotFoundException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinishMatchTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Test
    void finishedMatchDisappearsFromSummary() {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        // when
        scoreboard.finishMatch(id);

        // then
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @Test
    void finishingOneMatchKeepsTheOthers() {
        // given
        MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");
        scoreboard.updateScore(spainBrazil, 10, 2);

        // when
        scoreboard.finishMatch(mexicoCanada);

        // then
        assertThat(scoreboard.getSummary())
                .containsExactly(new Match(spainBrazil, "Spain", "Brazil", 10, 2));
    }

    @Test
    void teamsOfFinishedMatchCanStartANewMatch() {
        // given
        MatchId first = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.finishMatch(first);

        // when
        MatchId rematch = scoreboard.startMatch("Canada", "Mexico");

        // then
        assertThat(scoreboard.getSummary())
                .containsExactly(new Match(rematch, "Canada", "Mexico", 0, 0));
    }

    @Nested
    class Validation {

        @Test
        void rejectsNullMatchId() {
            // when & then
            assertThatThrownBy(() -> scoreboard.finishMatch(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("matchId");
        }

        @Test
        void rejectsUnknownMatch() {
            // given
            MatchId unknown = new MatchId(UUID.randomUUID());

            // when & then
            assertThatThrownBy(() -> scoreboard.finishMatch(unknown))
                    .isInstanceOf(MatchNotFoundException.class)
                    .hasMessageContaining(unknown.value().toString());
        }

        @Test
        void rejectsFinishingTheSameMatchTwice() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch(id);

            // when & then
            assertThatThrownBy(() -> scoreboard.finishMatch(id))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void rejectsUpdatingAFinishedMatch() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.finishMatch(id);

            // when & then
            assertThatThrownBy(() -> scoreboard.updateScore(id, 1, 0))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void rejectedFinishDoesNotChangeSummary() {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");
            MatchId unknown = new MatchId(UUID.randomUUID());

            // when
            assertThatThrownBy(() -> scoreboard.finishMatch(unknown))
                    .isInstanceOf(MatchNotFoundException.class);

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
        }
    }
}
