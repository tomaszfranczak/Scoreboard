# AI Usage

## Summary

Tool: Claude Code (Claude Opus 5.5) in the Claude desktop app, with access to the repository.

- **Design before code.** I used the AI as a sparring partner to find the open questions in the spec and compare
  options. I made the decisions; they are recorded in [docs/requirements-analysis.md](docs/requirements-analysis.md).
- **Context for the AI.** [CLAUDE.md](CLAUDE.md) holds the conventions and boundaries; the
  [implementation plan](docs/implementation-plan.md) splits the work into small steps with tests and a definition of done.
- **Test-first, reviewed by me.** The AI wrote the tests first and ran them red, then implemented. In steps 1–2 I
  reviewed tests and code together; from step 3 on, the AI stopped after the failing tests and implemented only after
  I accepted them. Tests that passed immediately were checked by breaking the code on purpose (mutation check).
- **Challenging the output.** Several design decisions changed after I pushed back, and I reviewed every piece of
  generated code before committing (tables below).
- **Independent reviews.** At checkpoints I asked for a separate AI code review, a documentation accuracy review and a
  final review for structure, readability, SOLID, test coverage and document length.

The conversation was in Polish; the prompts below are my condensed English translation.

## Design decisions changed through discussion

| Topic | AI's first suggestion | What changed it | Outcome |
|---|---|---|---|
| Concurrency strategy | One coarse-grained lock | I pointed out that it depends on the read/write ratio and a scoreboard is read-heavy | Copy-on-write snapshot (D4) |
| Timing of concurrency | A separate later step | I asked to handle threads right away and questioned passing the internal map around | Immutable state from the first business rule |
| Critical section | All validation inside the lock | I asked to keep the critical section minimal | Only state-dependent work under the lock |
| Per-match locking | — | I asked whether to lock single matches instead of the whole collection | Analysed and rejected; recorded in D4 |
| Match identity | Opaque `MatchId` | I proposed a trading-style ticker (`POLMEX`) | Rejected for rematch collisions; UUID kept (D1) |
| Out-of-order messages | A documented limitation | I suggested using event time, then chose the feature that closes the gap | `updateScore` overload with a sequence number (D8) |
| Feature naming | `updateScoreIfNewer`, then `version` | I chose an overload and the feed term "sequence number" | `updateScore(..., sequenceNumber)` |
| Idempotency | One advantage among others | I asked for it to be documented explicitly | Part of D2 |
| Commits | Separate commits for tests | I rejected it as excessive | One commit per behaviour, green build |
| Changelog and versioning | Not planned | I pointed out the gap | `CHANGELOG.md` and a SemVer plan: 1.0.0 core, 1.1.0 feature |

Where the AI corrected me:

| My assumption | Correction | Outcome |
|---|---|---|
| The task is mainly about multithreading | "Simultaneous matches" does not imply threads | Thread safety stated as an explicit assumption |
| We want an integration test | There is no external system | Tests named unit, acceptance and concurrency |

## Review feedback on the generated code

| Area | Generated first | My feedback | Result |
|---|---|---|---|
| Responsibilities | Validation, names and state in one class | Separate the logic early | `MatchFactory`, `MatchValidator`, `TeamNames`, `ScoreboardState`, `LiveScoreboard` |
| Naming | A method returning a new value named `requireValid…` | Names must say what they do | `TeamNames.normalize` vs. `void` validators |
| State structure | Two parallel maps (matches, start order) | Split the responsibilities of the state | `StartedMatch` and `SummaryOrder` |
| State methods | `put`, then two identical aliases | Intent must be visible | `withStartedMatch`, `withUpdatedMatch`, `withoutMatch` |
| Packages | One package | Order the classes | `model`, `exception`, `internal` hidden by `module-info.java` |
| Tests | One growing test class, flat methods | Given/when/then, one class per feature | `// given / when / then`, `@Nested Validation` |
| Comments | Javadoc | Code and tests should explain themselves | No comments |
| Defaults | `new Match(id, home, away, 0, 0)` | 0–0 is the default | Constructor without scores |

## Prompt history

**Approach and setup**
> I want to use AI deliberately: first an outline, then step by step, preparing prompts for each step.
> Prepare a Java skeleton for an empty repo, JDK 25. I'll commit myself.

The AI created a Maven skeleton with the wrapper and verified the build.

**Requirements analysis**
> My first observation is concurrency. Identify the other open questions the requirements leave unspecified.

> The lock vs read-write lock choice must be a trade-off depending on what dominates. A display board will have more readers.

> MatchId — what are we choosing between? Same for the absolute score.

> What about trading symbols like `POLMEX`? Maybe the event time solves out-of-order updates?

> UUID is fine, absolute score obviously, and idempotency must be described.

The AI compared options for each question; the decisions and their origin are in the requirements analysis.

**Conventions and plan** (written by me in English)
> Write a short CLAUDE.md: a simple Java 25 library, in memory, no frameworks; the requirements analysis is the main
> reference — ask me if something is missing; records for values, package-private implementation, exceptions from D6;
> tests first, green build after every commit. Then an implementation plan in small steps, one step one commit.

**Steps 1–2: starting a match**
> Implement step by step; I'll tell you what I don't like.

> For tests, introduce given/when/then. Take edge cases and input validation into account.

> Introduce packages. Dedicated helper classes? If a method returns a new value, name it after that operation.
> It's worth separating the logic now.

> Remember we want this to work with multiple threads. Is passing the map around good practice?

> Keep as little code as possible in the critical section — only the state checks?

The AI wrote the concurrency tests first: on the old code 19 of 20 runs failed. After the change to an immutable
state under one lock, all passed.

**Review checkpoint**
> Review the documents and log entries for accuracy. In parallel, run a separate code review.

The documentation review found that the log claimed tests were reviewed before implementation in every step, which
was not true for steps 1–2; it was corrected. The code review found records accepting `null`, a mutable collection
in the state constructor and a compiler warning; all fixed test-first.

**Steps 3–5: update, finish, ordering**
> We have no control over stale events: an old message arriving late cannot be detected in this version.

The AI confirmed this as the known limitation of D2, to be closed by the additional feature.

> Is this too expensive on every write? — Let's record it as a trade-off, with a tree as the alternative structure.

> The test class keeps growing — split it into classes per feature.

**Step 6: concurrency across all operations**

The new tests passed at once, so the AI removed all locks on purpose: every concurrency test failed, then the code
was restored.
> That's exactly what I was going to propose.

**Step 7: the additional feature**
> Let's think together about the direction of the feature.

> I propose a feature solving the problem discovered during implementation, as an additional method — but
> `updateScoreIfNewer` doesn't fit. I'd go with an overload. Feeds number messages with a sequence id.

The AI noted that the existing `updateScore` must keep `void` after 1.0.0 (binary compatibility). Mutation check:
with the lock removed, the concurrency test failed in 35 of 200 repetitions and in every run of 20; with correct code,
0 of 200.

**Final review**
> Review the package structure, readability, edge cases and SOLID, and make the documents concise.

Result: clearer names in the state, two edge-case tests (sequence number 0, the same number with a different score),
and the documentation cut by more than half.

## Artifacts

- [docs/requirements-analysis.md](docs/requirements-analysis.md) — decisions, options and trade-offs.
- [docs/implementation-plan.md](docs/implementation-plan.md) — steps, tests and definitions of done.
- [CLAUDE.md](CLAUDE.md) — conventions and boundaries for the AI.
- [CHANGELOG.md](CHANGELOG.md) — user-visible changes and the release plan.
