package io.github.tomaszfranczak.scoreboard.internal;

import io.github.tomaszfranczak.scoreboard.model.Match;

record StartedMatch(Match match, long startSequence) {

    StartedMatch withMatch(Match updated) {
        return new StartedMatch(updated, startSequence);
    }
}
