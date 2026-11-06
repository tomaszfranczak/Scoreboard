# CLAUDE.md

Working context for AI assistants on this repository.

## Project

A simple Java 25 library: a Live Football World Cup Scoreboard. In memory, no frameworks,
no runtime dependencies.

## Source of truth

- [docs/requirements-analysis.md](docs/requirements-analysis.md) — all design decisions (D1–D9).
  Refer to them by id. If something is not covered there, **ask — do not assume**.
- [docs/implementation-plan.md](docs/implementation-plan.md) — the steps. Work on one step at a time.

## Code conventions

- Immutable values are `record`s (`Match`, `MatchId`) that validate their own invariants in a compact constructor
  (no `null`s, no negative scores), so an invalid instance cannot exist.
- Only the API is public; implementation classes are package-private.
- Packages: `scoreboard` (API interface only), `scoreboard.model` (records), `scoreboard.exception` (domain exceptions),
  `scoreboard.internal` (implementation; package-private except `LiveScoreboard`) — see D9.
  `module-info.java` exports only the first three; never export `internal`, never split it into sub-packages.
- Separation of responsibilities:
  - `LiveScoreboard` — orchestration and concurrency (D4): everything that does not depend on the state
    (normalization, stateless validation, ids, new objects) runs before the lock; inside `synchronized (writeLock)`
    only state-dependent checks and replacing the state; reads only read the `volatile` state
  - `MatchFactory` — builds a valid new `Match` from raw input (input checks, normalization, id); no state
  - `ScoreboardState` — immutable state; copies its collections itself; answers questions (`findMatch`, `isPlaying`)
    and returns new versions of itself (`withStartedMatch`, `withUpdatedMatch`, `withoutMatch`); takes whole `Match` objects, never builds them
    from fields, and never decides what is an error; never pass mutable collections around
  - `MatchValidator` — business rules; `void` methods that throw; input checks (`null`, blank) run on the raw input
    before normalization, so messages can name the parameter
  - `StartedMatch` — a match together with its start sequence, so the two can never get out of sync
  - `SummaryOrder` — the ordering rule of the summary (D3), in one named place
  - `TeamNames` — normalization and comparison of team names
- Method names say what they do: a method that returns a transformed value is not called `require…` or `validate…`.
- Errors follow D6: `NullPointerException` for `null`, `IllegalArgumentException` for invalid values,
  `MatchNotFoundException` and `TeamAlreadyPlayingException` for state violations.
- No comments or Javadoc: the code explains itself through names, and the tests describe the behaviour.
- Keep it simple: no code for requirements we have not reached yet.

## Testing

- Test-first: write the tests for a step and run them to see them fail, then implement.
  The developer reviews tests and code together before committing.
- JUnit 6 + AssertJ. Test names describe the behaviour, e.g. `newMatchStartsWithZeroScore`.
- Tests follow given / when / then, marked with `// given`, `// when`, `// then` (`// when & then` when the action
  is the assertion, e.g. `assertThatThrownBy`) — the only comments allowed.
- One test class per operation (`StartMatchTest`, `UpdateScoreTest`, `FinishMatchTest`, `SummaryTest`), with input
  and state validation in a nested `Validation` class; concurrency tests live in `ScoreboardConcurrencyTest`.
- The summary order is defined (D3): assert it with `containsExactly` wherever order matters, and with
  `containsExactlyInAnyOrder` only where it does not.
- A test that passes as soon as it is written proves nothing until it is shown to fail: break the code on purpose
  (e.g. remove a lock or a check), confirm the test catches it, then restore the code.
- Every commit keeps the build green: `./mvnw verify` (Windows: `mvnw.cmd verify`). Requires JDK 25.
  The compiler runs with `-Xlint:all -Werror`; check warnings with a **clean** build (`./mvnw clean verify`),
  since incremental builds do not recompile unchanged classes.

## Changelog and versioning

- Every change visible to library users gets an entry under `[Unreleased]` in `CHANGELOG.md`, in the same commit.
  Newest entries go on top; internal changes (build, tests, refactoring) are not listed.
- Semantic Versioning; the version stays `-SNAPSHOT` between releases and is changed only in a release commit
  (see the release plan in `CHANGELOG.md`).

## Boundaries

- Do not add dependencies.
- Do not add public API beyond the accepted decisions.
- Do not commit or push — the developer reviews and commits.
