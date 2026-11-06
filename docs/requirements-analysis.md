# Requirements Analysis

How the intentionally open requirements were interpreted: the options considered and the decision taken for each.
The decisions came out of a discussion with an AI assistant, summarised in [AI.md](../AI.md). Decisions shaped
by that discussion have a short *Origin* note; the rest were proposed by the AI and accepted after review.

## Requirements

1. Start a new match.
2. Update the score.
3. Finish a match.
4. Get a summary of matches in progress, ordered by total score (descending); ties go to the most recently started match.
5. Exactly one additional operation, in a distinct commit.

Constraints: a simple in-memory Java library in a Maven project, supporting multiple simultaneous matches.

## Decisions

### D1. Match identity

`startMatch` returns an opaque `MatchId` (a record wrapping a `UUID`); all other operations take it.

| Option | Pros | Cons |
|---|---|---|
| Team pair (`"Mexico", "Canada"`) | Simplest API | Typos and home/away swaps; collides on rematches |
| Ticker-like key (`MEX-CAN`) | Readable, derivable by the caller | Same rematch collisions (Italy–Poland met twice in 1982); needs a name → code registry |
| **Opaque `MatchId` (UUID)** ✅ | Unambiguous, type-safe | Caller must keep the id |
| Mutable match handle | Fluent | Leaks mutable state, complicates concurrency |

The id is kept separate from start order (D3), so nobody compares ids to infer ordering.

*Origin: I proposed the ticker; analysed as a natural key, it was rejected for the same collisions as the team pair.*

### D2. Score update semantics

`updateScore(matchId, homeScore, awayScore)` sets the absolute score.

| Option | Pros | Cons |
|---|---|---|
| **Absolute score** ✅ | Idempotent: retries and duplicates are harmless; the next update repairs a missed one; corrections work naturally | Out-of-order messages can regress the score |
| Increment (`homeTeamScored`) | Models the event; order of arrival does not matter | Not idempotent: a duplicate double-counts, a lost message is lost forever |

Feeds are typically delivered at least once, so idempotency matters more than modelling events.
Scores must be non-negative and may decrease (a disallowed goal). Out-of-order delivery is handled by D8.

*Origin: I asked for the explicit comparison with increments and required idempotency to be documented.*

### D3. "Most recently started"

Start order is a monotonic counter (the start index), not a timestamp: matches started in the same millisecond would
tie, and a counter needs no injectable `Clock`. The index lives in the internal `StartedMatch`, is assigned only when a
match starts (updates keep it) and is not exposed by the API. The ordering rule is the `SummaryOrder` comparator.
`Match.totalScore()` returns a `long`, so two `int` scores cannot overflow.

### D4. Concurrency

**Assumption** (not stated in the spec): the workload is read-heavy — many displays polling the summary,
a few goals per match.

**Decision: copy-on-write.** Writes are serialised by one lock and publish a new immutable state, with the summary
already sorted, through a `volatile` field. Reads take no lock and always get a consistent snapshot in O(1).

| Approach | Good when | Why not here |
|---|---|---|
| Single lock for reads and writes | Comparable read/write rates | Readers would block each other |
| `ReentrantReadWriteLock` | Many readers and long read sections | Our read section is microseconds; readers still contend on the read count |
| `StampedLock` | Very hot, very short reads | Harder to get right, not reentrant |
| **Copy-on-write** ✅ | Reads ≫ writes, small n | Each write copies and re-sorts — fine at this scale |
| `ConcurrentHashMap` alone | Independent per-key operations | "One match per team" spans several keys; `putIfAbsent` on a fresh `MatchId` never conflicts |
| Lock per match | Many writers of different resources | Every write changes the global sorted summary, so writers still meet when publishing it; starting a match would need several locks in a fixed order |
| Lock-free (`AtomicReference.updateAndGet`) | High write contention | Work is repeated on conflict; no gain with rare writes |

Details:
- The critical section holds only state-dependent work (the "already playing" check, building the new state);
  validation, normalisation, id generation and object creation happen before the lock.
- Exceptions thrown inside `synchronized` always release the monitor, and the state is replaced last, so a failed
  operation changes nothing. `synchronized` no longer pins virtual threads since JDK 24 (JEP 491).
- A separate index of playing teams was rejected: a World Cup has a handful of simultaneous matches, and the index would
  be a second source of truth to keep in sync.

**Keeping the summary sorted.**

| Structure | Write | Read | Trade-off |
|---|---|---|---|
| **List sorted on write** ✅ | O(n log n) | O(1) | Simplest; fits rare writes and frequent reads |
| Sort on every read | O(n) | O(n log n) | Every frequent read pays for the sort |
| Sort lazily after a write | O(n) | O(1), first read O(n log n) | Helps only with write bursts; needs thread-safe lazy init |
| Ordered tree (`TreeSet`) | O(log n) | O(n) snapshot copy | Cannot be shared with lock-free readers; an update is remove + re-insert; ties must be broken or matches are dropped |
| Persistent tree | O(log n) | O(1) | No persistent collections in the JDK; over-engineering |

If writes became frequent and n large, the strategy would change to a single lock with sorting on read; the API would
not change. These are reasoned estimates, not measurements — confirming them would need a JMH benchmark.

*Origin: the AI first proposed a single lock; I pointed out that the choice depends on the read/write ratio and that
a scoreboard is read-heavy, which led to copy-on-write. I also asked about per-match locking, now listed above.*

### D5. Validation and team names

- Team names are `String`s; `null` → `NullPointerException`, blank → `IllegalArgumentException`.
- Surrounding whitespace is stripped; names are compared case-insensitively and kept as provided.
- A team cannot play itself or take part in two matches in progress. A rejected start changes nothing.
- Left out on purpose: collapsing internal whitespace (`"Costa  Rica"` ≠ `"Costa Rica"`), full Unicode case folding
  (`"ß"` ≠ `"SS"`), a maximum name length.

### D6. Error reporting

| Situation | Exception |
|---|---|
| `null` argument | `NullPointerException` |
| Invalid value (blank name, negative score or sequence number, same team twice) | `IllegalArgumentException` |
| Unknown or finished match | `MatchNotFoundException` |
| Team already playing | `TeamAlreadyPlayingException` |

Domain exceptions are unchecked: callers cannot recover at every call site. `Match` and `MatchId` validate themselves
too, so no invalid instance can exist; the non-negative score rule is therefore checked twice on purpose — once before
the lock, once in the record.

### D7. Lifecycle

A new match starts at 0–0. `finishMatch` removes it; finished matches are not kept. Finishing or updating an unknown
or finished match throws `MatchNotFoundException`: an idempotent no-op would suit retries, but without keeping finished
matches "already finished" cannot be told from "never existed".

### D8. Additional operation: score update with a sequence number

`boolean updateScore(MatchId matchId, int homeScore, int awayScore, long sequenceNumber)` applies the score only if
the sequence number is higher than the last one applied to that match.

It closes the one weakness of absolute updates (D2): a stale message arriving late can no longer overwrite a newer
score, while a correction with a higher number still applies. A feed-provided sequence number (like `MsgSeqNum` in FIX)
is preferred over a timestamp: no clock skew, no ties.

| Question | Decision |
|---|---|
| Form | An overload of `updateScore`; the existing one keeps `void`, as changing its return type after 1.0.0 would break binary compatibility |
| Stale number | Not applied, returns `false` — out-of-order messages are expected, not exceptional |
| Equal number | A duplicate, returns `false`; the same number with a different score is not detected |
| Mixing with the unnumbered update | The unnumbered update is a manual correction: always applied, keeps the last number |
| Validation | The number must be `>= 0`; the first numbered update of a match is always applied |

The check and the state swap happen under the same lock, so concurrent messages cannot apply a lower number after
a higher one.

*Origin: I suggested using the event time against out-of-order messages, chose this feature, the overload, and the
feed term "sequence number".*

### D9. API and encapsulation

- `getSummary()` returns an immutable list of immutable `Match` records.
- Clients use `Scoreboard.create()` and the `Scoreboard` interface only.
- Packages: `scoreboard` (interface), `model`, `exception`, `internal` (implementation). Inside `internal` only
  `LiveScoreboard` is public; splitting it further would force more classes to become public.
- `module-info.java` exports only the API packages.
- A client can construct a `MatchId` (record constructors are public), but it never matches a started match.
- No runtime dependencies.

## Assumptions

1. The workload is read-heavy (D4).
2. Feed messages are delivered at least once, possibly out of order (D2, D8).
3. Scores may decrease to express corrections (D2).
4. Team names are case-insensitive, without surrounding whitespace (D5).
5. Finished matches are not kept (D7).
6. Everything is in memory.
