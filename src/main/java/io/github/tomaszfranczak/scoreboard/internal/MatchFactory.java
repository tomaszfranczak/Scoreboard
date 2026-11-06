package io.github.tomaszfranczak.scoreboard.internal;

import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.util.UUID;

final class MatchFactory {

    private MatchFactory() {
    }

    static Match create(String homeTeam, String awayTeam) {
        MatchValidator.validateTeamNames(homeTeam, awayTeam);
        String home = TeamNames.normalize(homeTeam);
        String away = TeamNames.normalize(awayTeam);
        MatchValidator.validateDifferentTeams(home, away);
        return new Match(new MatchId(UUID.randomUUID()), home, away);
    }
}
