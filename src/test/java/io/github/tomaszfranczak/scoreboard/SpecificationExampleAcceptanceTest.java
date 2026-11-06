package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SpecificationExampleAcceptanceTest {

    private final Scoreboard scoreboard = Scoreboard.create();

    @Test
    void summaryOfTheExampleFromTheSpecification() {
        // given
        play("Mexico", 0, "Canada", 5);
        play("Spain", 10, "Brazil", 2);
        play("Germany", 2, "France", 2);
        play("Uruguay", 6, "Italy", 6);
        play("Argentina", 3, "Australia", 1);

        // when
        var summary = scoreboard.getSummary();

        // then
        assertThat(summary)
                .extracting(Match::homeTeam, Match::homeScore, Match::awayTeam, Match::awayScore)
                .containsExactly(
                        tuple("Uruguay", 6, "Italy", 6),
                        tuple("Spain", 10, "Brazil", 2),
                        tuple("Mexico", 0, "Canada", 5),
                        tuple("Argentina", 3, "Australia", 1),
                        tuple("Germany", 2, "France", 2));
    }

    private void play(String homeTeam, int homeScore, String awayTeam, int awayScore) {
        MatchId id = scoreboard.startMatch(homeTeam, awayTeam);
        scoreboard.updateScore(id, homeScore, awayScore);
    }
}
