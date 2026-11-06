package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.TeamAlreadyPlayingException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreboardTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Nested
    class Summary {

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
    }

    @Nested
    class StartMatch {

        @Test
        void startedMatchAppearsInSummaryWithZeroScore() {
            // when
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
        }

        @Test
        void allStartedMatchesAppearInSummary() {
            // when
            MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
            MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");

            // then
            assertThat(scoreboard.getSummary()).containsExactlyInAnyOrder(
                    new Match(mexicoCanada, "Mexico", "Canada", 0, 0),
                    new Match(spainBrazil, "Spain", "Brazil", 0, 0));
        }

        @Test
        void eachStartedMatchGetsDifferentId() {
            // when
            MatchId first = scoreboard.startMatch("Mexico", "Canada");
            MatchId second = scoreboard.startMatch("Spain", "Brazil");

            // then
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void teamNamesAreStoredTrimmed() {
            // when
            MatchId id = scoreboard.startMatch("  Mexico ", "\tCanada\n");

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
        }

        @Test
        void teamNamesKeepTheSpellingAsProvided() {
            // when
            MatchId id = scoreboard.startMatch("mexico", "CANADA");

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "mexico", "CANADA", 0, 0));
        }

        @Test
        void namesDifferingInInternalWhitespaceAreDifferentTeams() {
            // given
            scoreboard.startMatch("Costa Rica", "Mexico");

            // when
            scoreboard.startMatch("Costa  Rica", "Spain");

            // then
            assertThat(scoreboard.getSummary()).hasSize(2);
        }
    }

    @Nested
    class StartMatchValidation {

        @Test
        void rejectsNullHomeTeam() {
            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch(null, "Canada"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("homeTeam");
        }

        @Test
        void rejectsNullAwayTeam() {
            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch("Mexico", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("awayTeam");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void rejectsBlankHomeTeam(String blank) {
            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch(blank, "Canada"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("homeTeam");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void rejectsBlankAwayTeam(String blank) {
            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch("Mexico", blank))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("awayTeam");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Mexico", "mexico", "MEXICO", " Mexico "})
        void rejectsSameTeamOnBothSides(String sameTeam) {
            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch("Mexico", sameTeam))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Mexico", "mexico", " MEXICO "})
        void rejectsHomeTeamAlreadyPlaying(String teamAlreadyPlaying) {
            // given
            scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch(teamAlreadyPlaying, "Spain"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Canada", "canada", " CANADA "})
        void rejectsAwayTeamAlreadyPlaying(String teamAlreadyPlaying) {
            // given
            scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch("Spain", teamAlreadyPlaying))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
        }

        @Test
        void rejectsRematchWithSwappedSidesWhileFirstMatchIsInProgress() {
            // given
            scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch("Canada", "Mexico"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
        }

        @ParameterizedTest
        @CsvSource({"Mexico, Spain", "Spain, Canada"})
        void rejectedStartDoesNotChangeSummary(String homeTeam, String awayTeam) {
            // given
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            // when
            assertThatThrownBy(() -> scoreboard.startMatch(homeTeam, awayTeam))
                    .isInstanceOf(TeamAlreadyPlayingException.class);

            // then
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
        }
    }
}
