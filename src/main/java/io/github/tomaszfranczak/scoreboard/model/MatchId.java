package io.github.tomaszfranczak.scoreboard.model;

import java.util.Objects;
import java.util.UUID;

public record MatchId(UUID value) {

    public MatchId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
