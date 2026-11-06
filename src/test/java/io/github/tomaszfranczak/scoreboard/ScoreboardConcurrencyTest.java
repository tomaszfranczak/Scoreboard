package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.TeamAlreadyPlayingException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardConcurrencyTest {

    private static final int THREADS = 32;

    private final Scoreboard scoreboard = Scoreboard.create();

    @RepeatedTest(20)
    void concurrentStartsWithDifferentTeamsAreAllRecorded() throws Exception {
        // when
        List<Optional<MatchId>> started = startConcurrently(i ->
            () -> scoreboard.startMatch("Home " + i, "Away " + i));

        // then
        assertThat(started).allMatch(Optional::isPresent);
        assertThat(idsInSummary())
                .containsExactlyInAnyOrderElementsOf(started.stream().map(Optional::orElseThrow).toList());
    }

    @RepeatedTest(20)
    void concurrentStartsWithTheSameTeamLetOnlyOneMatchStart() throws Exception {
        // when
        List<Optional<MatchId>> started = startConcurrently(i ->
            () -> scoreboard.startMatch("Mexico", "Opponent " + i));

        // then
        List<MatchId> winners = started.stream().flatMap(Optional::stream).toList();
        assertThat(winners).hasSize(1);
        assertThat(idsInSummary()).containsExactlyElementsOf(winners);
    }

    private List<MatchId> idsInSummary() {
        return scoreboard.getSummary().stream().map(Match::id).toList();
    }

    private List<Optional<MatchId>> startConcurrently(IntFunction<Callable<MatchId>> task) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Optional<MatchId>>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<MatchId> action = task.apply(i);
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    try {
                        return Optional.of(action.call());
                    } catch (TeamAlreadyPlayingException e) {
                        return Optional.empty();
                    }
                }));
            }
            startSignal.countDown();

            List<Optional<MatchId>> results = new ArrayList<>();
            for (Future<Optional<MatchId>> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
