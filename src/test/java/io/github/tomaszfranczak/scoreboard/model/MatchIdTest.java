package io.github.tomaszfranczak.scoreboard.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchIdTest {

    @Test
    void rejectsNullValue() {
        // when & then
        assertThatThrownBy(() -> new MatchId(null))
                .isInstanceOf(NullPointerException.class);
    }
}
