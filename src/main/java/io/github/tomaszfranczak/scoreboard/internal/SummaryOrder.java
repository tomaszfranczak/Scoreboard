package io.github.tomaszfranczak.scoreboard.internal;

import java.util.Comparator;

final class SummaryOrder {

    static final Comparator<StartedMatch> BY_TOTAL_SCORE_THEN_MOST_RECENT_START =
            Comparator.comparingLong((StartedMatch started) -> started.match().totalScore())
                    .thenComparingLong(StartedMatch::startSequence)
                    .reversed();

    private SummaryOrder() {
    }
}
