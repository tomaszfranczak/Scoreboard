package io.github.tomaszfranczak.scoreboard.exception;

import io.github.tomaszfranczak.scoreboard.model.MatchId;

import java.io.Serial;

public class MatchNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MatchNotFoundException(MatchId id) {
        super("No match in progress with id: " + id.value());
    }
}
