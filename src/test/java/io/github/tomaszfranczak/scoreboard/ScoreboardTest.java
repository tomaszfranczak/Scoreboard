package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.MatchNotFoundException;
import io.github.tomaszfranczak.scoreboard.exception.TeamAlreadyPlayingException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

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
        void teamNamesAreStoredWithoutSurroundingWhitespace() {
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
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessageContaining(teamAlreadyPlaying.strip());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Canada", "canada", " CANADA "})
        void rejectsAwayTeamAlreadyPlaying(String teamAlreadyPlaying) {
            // given
            scoreboard.startMatch("Mexico", "Canada");

            // when & then
            assertThatThrownBy(() -> scoreboard.startMatch("Spain", teamAlreadyPlaying))
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessageContaining(teamAlreadyPlaying.strip());
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

    @Nested
    class UpdateScore {

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
    }

    @Nested
    class UpdateScoreValidation {

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
