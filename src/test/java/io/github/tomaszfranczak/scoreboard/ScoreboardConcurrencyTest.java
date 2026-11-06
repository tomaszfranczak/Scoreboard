package io.github.tomaszfranczak.scoreboard;

import io.github.tomaszfranczak.scoreboard.exception.MatchNotFoundException;
import io.github.tomaszfranczak.scoreboard.exception.TeamAlreadyPlayingException;
import io.github.tomaszfranczak.scoreboard.model.Match;
import io.github.tomaszfranczak.scoreboard.model.MatchId;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    @RepeatedTest(20)
    void concurrentUpdatesOfDifferentMatchesAreNotLost() throws Exception {
        // given
        List<MatchId> ids = IntStream.range(0, THREADS)
                .mapToObj(i -> scoreboard.startMatch("Home " + i, "Away " + i))
                .toList();

        // when
        runConcurrently(i -> () -> {
            scoreboard.updateScore(ids.get(i), i, i + 1);
            return null;
        });

        // then
        List<Match> expected = IntStream.range(0, THREADS)
                .mapToObj(i -> new Match(ids.get(i), "Home " + i, "Away " + i, i, i + 1))
                .toList();
        assertThat(scoreboard.getSummary()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(20)
    void concurrentFinishesOfTheSameMatchLetOnlyOneSucceed() throws Exception {
        // given
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        // when
        List<Boolean> finished = runConcurrently(i -> () -> {
            try {
                scoreboard.finishMatch(id);
                return true;
            } catch (MatchNotFoundException e) {
                return false;
            }
        });

        // then
        assertThat(finished).containsOnlyOnce(true);
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @RepeatedTest(20)
    void concurrentStartsUpdatesAndFinishesLeaveAConsistentState() throws Exception {
        // when
        runConcurrently(i -> () -> {
            MatchId id = scoreboard.startMatch("Home " + i, "Away " + i);
            scoreboard.updateScore(id, i, 0);
            if (i % 2 == 0) {
                scoreboard.finishMatch(id);
            }
            return null;
        });

        // then
        List<Tuple> expected = IntStream.iterate(THREADS - 1, i -> i >= 0, i -> i - 2)
                .mapToObj(i -> tuple("Home " + i, i))
                .toList();
        assertThat(scoreboard.getSummary())
                .extracting(Match::homeTeam, Match::homeScore)
                .containsExactlyElementsOf(expected);
    }

    @RepeatedTest(5)
    void readersAlwaysSeeAConsistentSummaryWhileWritesHappen() throws Exception {
        // given
        int writers = THREADS / 2;
        AtomicInteger writersLeft = new AtomicInteger(writers);
        Queue<String> violations = new ConcurrentLinkedQueue<>();

        // when
        runConcurrently(i -> i < writers
                ? () -> {
                    try {
                        playMatches(i);
                    } finally {
                        writersLeft.decrementAndGet();
                    }
                    return null;
                }
                : () -> {
                    while (writersLeft.get() > 0) {
                        collectViolations(scoreboard.getSummary(), violations);
                    }
                    return null;
                });

        // then
        assertThat(violations).isEmpty();
    }

    private void playMatches(int writer) {
        for (int round = 0; round < 20; round++) {
            MatchId id = scoreboard.startMatch("Home " + writer, "Away " + writer);
            for (int goals = 1; goals <= 5; goals++) {
                scoreboard.updateScore(id, goals, round % 3);
            }
            scoreboard.finishMatch(id);
        }
    }

    private static void collectViolations(List<Match> summary, Queue<String> violations) {
        Set<String> teams = new HashSet<>();
        for (int k = 0; k < summary.size(); k++) {
            Match match = summary.get(k);
            if (k > 0 && summary.get(k - 1).totalScore() < match.totalScore()) {
                violations.add("Not ordered by total score: " + summary);
            }
            if (!teams.add(match.homeTeam().toLowerCase(Locale.ROOT)) || !teams.add(match.awayTeam().toLowerCase(Locale.ROOT))) {
                violations.add("Team in more than one match: " + summary);
            }
        }
    }

    private List<MatchId> idsInSummary() {
        return scoreboard.getSummary().stream().map(Match::id).toList();
    }

    private List<Optional<MatchId>> startConcurrently(IntFunction<Callable<MatchId>> start) throws Exception {
        return runConcurrently(i -> () -> {
            try {
                return Optional.of(start.apply(i).call());
            } catch (TeamAlreadyPlayingException e) {
                return Optional.empty();
            }
        });
    }

    private <T> List<T> runConcurrently(IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<T> action = task.apply(i);
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    return action.call();
                }));
            }
            startSignal.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
