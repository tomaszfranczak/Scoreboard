package io.github.tomaszfranczak.scoreboard.exception;

import java.io.Serial;

public class TeamAlreadyPlayingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TeamAlreadyPlayingException(String team) {
        super("Team is already playing in a match in progress: " + team);
    }
}
