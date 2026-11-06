# Live Football World Cup Scoreboard

A small, thread-safe, in-memory Java library that keeps the scores of matches in progress.

## Requirements and build

JDK 25. Maven is not needed — the project ships with the Maven Wrapper.

```bash
./mvnw verify          # Windows: mvnw.cmd verify
```

## Usage

```java
Scoreboard scoreboard = Scoreboard.create();

MatchId match = scoreboard.startMatch("Mexico", "Canada");
scoreboard.updateScore(match, 0, 5);

List<Match> summary = scoreboard.getSummary();   // ordered, immutable snapshot

scoreboard.finishMatch(match);
```

| Operation | Behaviour |
|---|---|
| `startMatch(home, away)` | Starts a match at 0–0 and returns its `MatchId` |
| `updateScore(id, home, away)` | Sets the absolute score |
| `updateScore(id, home, away, sequenceNumber)` | Sets the score only if the sequence number is higher than the last one; returns whether it was applied |
| `finishMatch(id)` | Removes the match |
| `getSummary()` | Matches in progress by total score (descending); ties: most recently started first |

## Assumptions

1. The scoreboard is read far more often than it is written (displays polling it, a few goals per match).
2. Scores come from a feed that delivers messages at least once and possibly out of order.
3. A score may decrease — that is how a correction (e.g. a disallowed goal) is expressed.
4. Team names are compared case-insensitively, without surrounding whitespace; a team plays one match at a time.
5. Finished matches are not kept. Everything is in memory.

## Design and trade-offs

The full reasoning, with the options considered, is in [docs/requirements-analysis.md](docs/requirements-analysis.md).
The key points:

- **Match identity** — `startMatch` returns an opaque `MatchId` (UUID). Identifying matches by team names would be
  simpler, but collides when the same teams meet again and invites typos.
- **Absolute scores** — `updateScore` is idempotent: a retried or duplicated message is harmless and the next update
  repairs a missed one. Increments would model goals more naturally, but a duplicate would double-count.
- **Ordering** — "most recently started" uses a monotonic counter, not a clock; two matches started in the same
  millisecond would otherwise tie.
- **Concurrency: copy-on-write** — writes take one lock and publish a new immutable state with the summary already
  sorted; reads take no lock and always see a consistent snapshot. This trades O(n log n) work per write for O(1)
  reads, which fits a read-heavy scoreboard with few matches. Read-write locks, per-match locks, a lock-free variant and
  tree structures were considered and rejected for this scale; with frequent writes and thousands of matches a single
  lock with sorting on read would be the better choice. These are reasoned estimates, not benchmarks.
- **Errors** — `NullPointerException` / `IllegalArgumentException` for invalid arguments; `MatchNotFoundException`
  and `TeamAlreadyPlayingException` for invalid state.
- **Encapsulation** — the API is the `Scoreboard` interface plus immutable `Match` / `MatchId` records. The
  implementation lives in `internal`, package-private except one class, and is not exported by `module-info.java`.
- **SOLID notes** — `Scoreboard` combines reads and writes; a display needs only `getSummary()`, so a separate read
  interface would follow the interface segregation principle more strictly, but it adds a type to a small API.
  Validation and factory helpers are static, package-private functions rather than injected dependencies: they have no
  alternative implementations, so injection would only add ceremony.

## Additional feature: score update with a sequence number

```java
boolean applied = scoreboard.updateScore(match, 2, 1, sequenceNumber);
```

**Why this feature.** Choosing absolute scores leaves one weakness: a stale message that arrives late overwrites a
newer score. Sports feeds number their messages, so the scoreboard can reject anything older than what it has already
applied. The feature closes the gap left by an earlier design decision instead of adding an unrelated utility.

**Behaviour.**
- A higher sequence number is applied; a lower or equal one is ignored and returns `false` — late and duplicate
  messages are expected, not errors.
- A correction with a higher number may lower the score.
- The update without a sequence number acts as a manual correction: it always applies and keeps the last number,
  so stale feed messages are still rejected afterwards.
- It is an overload, so the original `updateScore` keeps its `void` signature from 1.0.0 (binary compatibility).

Limitation: the same sequence number with a different score is treated as a duplicate; detecting such a conflict
would need an extra exception.

## Tests

- One test class per operation, plus the example from the specification as an acceptance test.
- Concurrency tests start up to 32 threads at once and repeat each scenario; their ability to catch races was checked
  by removing the locks on purpose (mutation check).
- 100% line and branch coverage (JaCoCo, run ad hoc).

## Documentation

- [docs/requirements-analysis.md](docs/requirements-analysis.md) — decisions and trade-offs.
- [docs/implementation-plan.md](docs/implementation-plan.md) — how the work was split.
- [AI.md](AI.md) — how AI was used, with the prompt history.
- [CHANGELOG.md](CHANGELOG.md) — changes per release.
