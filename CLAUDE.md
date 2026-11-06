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

- Immutable values are `record`s (`Match`, `MatchId`).
- Only the API is public; implementation classes are package-private.
- Errors follow D6: `NullPointerException` for `null`, `IllegalArgumentException` for invalid values,
  `MatchNotFoundException` and `TeamAlreadyPlayingException` for state violations.
- Keep it simple: no code for requirements we have not reached yet.

## Testing

- Test-first: write the test cases, get them reviewed, then implement.
- JUnit 6 + AssertJ. Test names describe the behaviour, e.g. `newMatchStartsWithZeroScore`.
- Until ordering is implemented (plan step 5), do not assert the order of the summary.
- Every commit keeps the build green: `./mvnw verify` (Windows: `mvnw.cmd verify`). Requires JDK 25.

## Boundaries

- Do not add dependencies.
- Do not add public API beyond the accepted decisions.
- Do not commit or push — the developer reviews and commits.
