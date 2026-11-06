# Requirements Analysis

This document captures how the (intentionally open) requirements were interpreted:
the open questions, the options considered, and the decision taken for each.
It is the primary design artifact that guided the implementation.

Status legend: **Accepted** — decided and reviewed; **Open** — to be decided in a later phase.

The decisions were worked out in a discussion with an AI assistant (Claude Code); the dialogue is summarized
in [AI.md](../AI.md). Decisions that changed or were shaped during that discussion have an **Origin** note;
the others were proposed by the AI and accepted after review.

## 1. Requirements as stated

1. Start a new match
2. Update the score
3. Finish a match
4. Get a summary of matches in progress, ordered by total score (descending);
   ties broken by the most recently started match first
5. Exactly one additional operation of our choice, introduced in a distinct commit

Constraints: a simple Java library in a Maven project, in-memory, supporting multiple simultaneous matches.

## 2. Open questions in the requirements

| Area | Question |
|---|---|
| Ordering | How is "most recently started" determined deterministically? |
| Score update | Absolute score or increment? What about corrections and unreliable feeds? |
| Identity | How does a caller refer to a match? |
| Validation | Which inputs and states are illegal, and how are errors reported? |
| Encapsulation | Can callers mutate internal state through returned objects? |
| Concurrency | Is the library safe when used from multiple threads? |
| Scope | Does the solution stay simple, or is it over-engineered? |

## 3. Decisions

### D1. Match identity — **Accepted**

`startMatch` returns an opaque `MatchId` (a record wrapping a `UUID`). All other operations take the `MatchId`.

| Option | Pros | Cons |
|---|---|---|
| Team pair (`"Mexico", "Canada"`) | Simplest API; caller stores nothing | Stringly-typed; home/away swaps and typos; uniqueness relies on the "one live match per team" rule; collides on rematches |
| Ticker-like natural key (`MEX-CAN`) | Human-readable; deterministic, caller can derive it | Same collisions as team pair (teams can meet twice in one World Cup, e.g. Italy–Poland 1982); requires a team-name → code registry |
| **Opaque `MatchId` (UUID)** ✅ | Unambiguous regardless of business rules; type-safe; stable for future extensions (events, archive) | Caller must keep the id |
| Mutable match handle (`match.updateScore(..)`) | Fluent | Leaks mutable state; breaks the aggregate boundary; complicates concurrency |

Identity (`MatchId`) is deliberately kept separate from start order (see D3),
so callers are never tempted to compare ids to infer ordering.

> **Origin:** The AI listed team pair vs. opaque id vs. mutable handle. I proposed a trading-style ticker
> (`POLMEX`, `GERSWE`) as a readable, derivable alternative; analysing it showed it is a natural key with the
> same rematch collisions as the team pair, so it was rejected as identity. I chose UUID.

### D2. Score update semantics — **Accepted**

`updateScore(matchId, homeScore, awayScore)` sets the **absolute** score.

| Option | Pros | Cons |
|---|---|---|
| **Absolute score** ✅ | **Idempotent** — retries and duplicated feed messages are harmless; **self-healing** — the next update repairs a missed one; corrections (VAR, operator error) work naturally | No plausibility check on jumps (0–0 → 7–3); out-of-order messages can regress the score (last write wins) |
| Increment (`homeTeamScored`) | Models the domain event; out-of-order delivery is harmless (increments commute) | **Not idempotent** — a duplicate double-counts, a lost message is lost forever; corrections need extra operations |
| Both | Flexible | Larger API than the spec asks for |

**Idempotency.** Applying the same `updateScore(id, h, a)` once or many times leaves the scoreboard in the same state.
This matters for a sports data company: feeds are typically delivered *at least once*, so duplicates and retries are normal, not exceptional.

Rules:
- Scores must be non-negative; negative values are rejected.
- Scores **may decrease** — this is how corrections (e.g. a disallowed goal) are expressed.
- Out-of-order delivery is **not** detected by `updateScore`; that is addressed by the additional operation (D8).

> **Origin:** The AI recommended absolute scores; I asked for the explicit trade-off against increments,
> accepted absolute scores, and required idempotency to be documented as a first-class property.

### D3. "Most recently started" — **Accepted**

Start order is tracked with a **monotonic sequence counter**, not a wall-clock timestamp.

- `Instant.now()` has limited resolution; matches started in the same millisecond (as in any unit test) would tie, making ordering non-deterministic.
- A counter is deterministic, cheap, and needs no injectable `Clock`.
- The sequence is an internal detail and is not exposed in the public API.

### D4. Concurrency strategy — **Accepted**

**Assumption:** the workload is read-heavy — many readers (displays, API clients polling the summary),
very few writes (2–3 goals per match over 90 minutes). The spec does not state this; it is our assumption.

**Decision: copy-on-write snapshot.**
- Writes (start / update / finish) are serialized by a single lock. The writer builds a new immutable state,
  *including the already sorted summary*, and publishes it through a `volatile` reference.
- Reads (`getSummary`) take no lock: an O(1) volatile read that always returns a consistent snapshot.
- Each write costs O(n log n) for n live matches — n is small and writes are rare.

| Approach | Good when | Cost |
|---|---|---|
| `synchronized` / `ReentrantLock` | Low contention; comparable read/write rates; simplest | Readers block each other and writers |
| `ReentrantReadWriteLock` | Many readers **and** long read sections | Higher per-acquisition overhead; CAS contention on the shared read count; possible writer starvation |
| `StampedLock` (optimistic reads) | Very hot, very short reads | Harder to get right; not reentrant |
| **Copy-on-write snapshot** ✅ | **Reads ≫ writes, small data set** | Every write copies and re-sorts; poor fit for frequent writes or large n |
| `ConcurrentHashMap` alone | Independent per-key operations | Cannot enforce cross-key invariants (a team in at most one live match) without races; summary iteration is only weakly consistent |

Implementation notes:
- The writer lock is a private `synchronized` monitor. Since JDK 24 (JEP 491) `synchronized` no longer pins virtual
  threads, so `ReentrantLock` would add nothing here.
- `ConcurrentHashMap.putIfAbsent` does not help: the key is a fresh `MatchId`, so it never conflicts, while the
  invariant at risk ("a team plays in one match at a time") spans several entries. A lock-free variant would need a
  second map of team reservations with rollback, and still would not give readers a consistent snapshot.
- The validator receives the immutable state, never a live view of a mutable collection.
- Domain exceptions (`TeamAlreadyPlayingException`, `MatchNotFoundException`) are thrown inside the critical section.
  This is safe: leaving a `synchronized` block by an exception always releases the monitor (no `try/finally` to forget,
  unlike `ReentrantLock`), and the state is replaced only as the last statement, so a failed operation changes nothing.
  Creating the exception under the lock costs a few microseconds, only on the error path; moving the throw outside
  the lock would make the code harder to read for no measurable gain.
- The critical section holds only what depends on the current state: the "team already playing" check and
  building the new state. Name normalization, stateless validation, id generation (`UUID.randomUUID()` uses
  `SecureRandom`) and creating the `Match` happen before the lock is taken.
- A lock-free alternative fits the immutable state naturally: `AtomicReference.updateAndGet` with the check and
  the new state computed inside, retried on conflict. It was not chosen: under contention the work is repeated,
  the update function must stay side-effect free, and with a minimal critical section and rare writes the lock
  costs practically nothing while being easier to reason about.
- The "team already playing" check scans the matches in progress (O(n)). A separate index of playing teams would make
  it O(1), but it was rejected: a World Cup has at most a handful of simultaneous matches, the index would be a second
  source of truth to keep consistent on every start and finish, and it would be copied on every write as well.
  Worth revisiting only if the scoreboard had to hold thousands of matches.

`ReentrantReadWriteLock` was considered specifically because of the read-heavy assumption and rejected:
our read section (copy + sort of a few dozen elements) is too short for it to pay off, and readers would still contend.

If the workload changed (frequent writes, thousands of matches), the strategy should switch to a single lock with sort-on-read.
Because callers only depend on the `Scoreboard` interface, that change would not affect the API.

The performance arguments above are reasoning, **not measurements** — the scoreboard was not benchmarked.
Confirming them would require a JMH benchmark under a realistic read/write ratio.

> **Origin:** The AI initially proposed a single coarse-grained lock. I pushed back: the choice between a plain lock
> and a read-write lock is a trade-off that depends on the read/write ratio, and a scoreboard display is likely read-heavy.
> Following that assumption, the AI argued that a read-write lock does not pay off for such short read sections and
> proposed copy-on-write instead, which I accepted.

### D5. Validation and team names — **Accepted**

- Team names are `String`s in the API (no `Team` value object — not needed for this scope).
- `null` → `NullPointerException`; blank → `IllegalArgumentException`.
- Leading and trailing whitespace is stripped (`String.strip`, Unicode-aware); comparison for uniqueness is
  **case-insensitive** (`"Spain"` and `" spain "` are the same team). The stripped name as provided is kept for display.
- A match cannot be started with the same team on both sides.
- A team cannot take part in more than one match in progress.
- A rejected start leaves the scoreboard unchanged.

Edge cases considered and deliberately left out:
- Internal whitespace is not collapsed: `"Costa  Rica"` and `"Costa Rica"` are different teams.
- Case-insensitive comparison is character by character (`String.equalsIgnoreCase`), without full Unicode
  case folding — e.g. `"ß"` and `"SS"` are not equal. Team names are expected to come from a controlled source.
- There is no maximum length for a team name.

### D6. Error reporting — **Accepted**

Follows JDK conventions for programming errors, with domain-specific unchecked exceptions for state violations:

| Situation | Exception |
|---|---|
| `null` argument | `NullPointerException` (`Objects.requireNonNull`) |
| Invalid value (blank name, negative score, same team twice) | `IllegalArgumentException` |
| Unknown or already finished match | `MatchNotFoundException` |
| Team already playing in a match in progress | `TeamAlreadyPlayingException` |

Domain exceptions extend `RuntimeException`: callers cannot meaningfully recover from them at every call site,
and checked exceptions would clutter the API.

The public records (`Match`, `MatchId`) enforce the same rules in their compact constructors (`null` → NPE,
negative score → IAE), so an invalid instance cannot be built even outside the scoreboard.

The non-negative score rule therefore exists twice — in `MatchValidator` and in `Match` — on purpose:
the validator rejects an invalid update before the write lock is taken (D4), and the record guarantees that no invalid
`Match` can exist at all. Removing either would trade one of these guarantees for less code.

### D7. Lifecycle — **Accepted**

- A new match starts at 0–0.
- `finishMatch` removes the match from the scoreboard; finished matches are not retained.
- Finishing an unknown or already finished match throws `MatchNotFoundException` (fail fast, makes caller bugs visible).
  Trade-off: an idempotent no-op would be friendlier to retries, but since finished matches are not retained,
  "already finished" cannot be distinguished from "never existed" — silently accepting both would hide real bugs.

### D8. Additional operation: versioned score update — **Accepted (details Open)**

A separate operation that applies a score only if it is newer than the last applied one, e.g.
`updateScore(matchId, homeScore, awayScore, version)`.

Why:
- It addresses the one weakness of absolute updates (D2): out-of-order delivery from an unreliable feed.
- Stale messages are rejected while legitimate corrections (which carry a newer version) still apply.
- It reflects a real problem in the sports data domain, rather than a generic utility.

A source-provided **version / sequence number** is preferred over an event timestamp: it carries the same semantics
without clock skew between sources or ties on identical timestamps.

> **Origin:** While discussing the out-of-order weakness of absolute updates, I suggested that the event time could solve it.
> The AI pointed out it also resolves the "may scores decrease?" dilemma, recommended a source version over a timestamp
> (clock skew, ties), and noticed that the spec requires the extra feature to be an *operation* — so it fits as a separate method.
> I chose the versioned update as the additional feature.

Open questions (to be decided when implementing the feature):
- Is a stale update silently ignored, reported via a return value, or rejected with an exception?
- How do the unversioned and versioned updates interact on the same match?
- Is an equal version a duplicate (idempotent no-op) or a conflict?

### D9. Public API surface and encapsulation — **Accepted**

- `getSummary()` returns an immutable `List<Match>`; `Match` is an immutable record (id, teams, scores;
  `totalScore()` is added with the ordering step).
- Implementation classes are package-private; clients obtain an instance through a factory and depend only on the `Scoreboard` interface.
- Packages: `scoreboard` (the interface and its package-private implementation and helpers),
  `scoreboard.model` (`Match`, `MatchId`), `scoreboard.exception` (domain exceptions).
  The implementation stays next to the interface so that it can remain package-private. Moving it to its own package
  would make it public; hiding it again would need `module-info.java`, which only helps module-path clients —
  too much machinery for a library of this size.
- Match ids are generated inside the implementation. A client can still construct a `MatchId` (a record's canonical
  constructor is always public), but such an id never matches a started match and is rejected with `MatchNotFoundException`.
- No frameworks or runtime dependencies — plain Java 25.

## 4. Assumptions (summary for README)

1. The workload is read-heavy (D4).
2. Score updates come from a feed with at-least-once delivery — duplicates are expected (D2).
3. Scores may decrease to express corrections (D2).
4. Team names are case-insensitive, with leading and trailing whitespace stripped (D5).
5. Finished matches are not retained (D7).
6. Everything is in memory; no persistence (spec).
