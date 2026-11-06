package io.github.tomaszfranczak.scoreboard;

import java.util.UUID;

public record MatchId(UUID value) {

    static MatchId generate() {
        return new MatchId(UUID.randomUUID());
    }
}
