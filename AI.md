# AI Usage

> Draft — updated as the work progresses.

## Summary

**Tool:** Claude Code (model: Claude Opus 5.5), used in the Claude desktop app with access to the repository.

**How I used it:**
- **Requirements analysis first, code later.** Before writing any code I used the AI as a sparring partner
  to surface the open questions in the spec and to compare design options. I took the decisions;
  the outcome is recorded in [docs/requirements-analysis.md](docs/requirements-analysis.md), together with how the key decisions were reached.
- **Challenging the suggestions.** I did not accept the first proposal by default. Several design decisions changed
  after I pushed back or proposed alternatives (see [Design decisions changed through discussion](#design-decisions-changed-through-discussion)).
- **Test-first implementation, reviewed by me.** For each step the AI wrote the tests first and ran them against the
  existing code to see them fail, then wrote the implementation. In steps 1 and 2 tests and implementation were written
  in one go and I reviewed them together; from step 3 on, the AI stopped after the failing tests and the API signature,
  and implemented only after I had reviewed and accepted them. I reviewed every result before committing and asked
  for changes where I disagreed — mostly about responsibilities, naming and concurrency
  (see [Review feedback on the generated code](#review-feedback-on-the-generated-code)). Because behaviour is pinned
  by tests, these refactorings could be made safely. Each commit adds one behaviour together with its tests and keeps
  the build green; each stage is developed on its own feature branch. *(to be completed)*
- **Separate review passes.** At checkpoints I asked for an independent code review (a separate AI agent) and a review
  of the documentation for accuracy. *(to be completed)*

**Language note:** the conversation was held in Polish, my native language. The prompt history below is my
English translation, condensed but faithful in content. All code, documentation and commit messages are in English.

## Workflow

1. Project skeleton (Maven, JDK 25, Maven Wrapper), set up with the AI and verified with `./mvnw verify`.
2. Requirements analysis and design decisions, done as a dialogue → [docs/requirements-analysis.md](docs/requirements-analysis.md).
3. Conventions file for the AI ([CLAUDE.md](CLAUDE.md)) and an implementation plan ([docs/implementation-plan.md](docs/implementation-plan.md)).
4. Per step:
   1. **Red** — the AI writes the tests for the step and runs them to confirm they fail.
   2. **Green** — the AI writes the implementation to make them pass.
   3. **Review and refactor** — I review the tests and the code; changes I ask for are made with the tests green.
   4. **Commit** — one commit per behaviour, containing both the tests and the implementation.
5. Concurrency, then the additional feature (in a distinct commit), following the same cycle.
6. README and final pass on this file.

## Design decisions changed through discussion

| Topic | AI's initial suggestion | What changed it | Outcome |
|---|---|---|---|
| Concurrency strategy | One coarse-grained lock | I pointed out that the lock choice depends on the read/write ratio and that a scoreboard display is read-heavy | Copy-on-write snapshot; read-write lock considered and rejected for short read sections (D4) |
| Timing of concurrency | Thread safety as a separate later step | I asked to take multithreading into account right away and questioned passing the internal map to the validator | Copy-on-write state introduced with the first business rule; the validator receives an immutable state |
| Critical section | Whole validation, id generation and object creation inside the lock | I asked to keep the critical section minimal | Only state-dependent checks and the state swap are inside the lock |
| Match identity | Opaque `MatchId` | I proposed a trading-style ticker (`POLMEX`) | Analysed as a natural key; rejected because of rematch collisions; UUID kept (D1) |
| Out-of-order feed updates | Documented as a known limitation | I suggested that the event time could solve it | Turned into the additional feature: versioned score update (D8) |
| Idempotency | Mentioned as one advantage among others | I required it to be documented explicitly | A dedicated idempotency section in D2 |
| Commit granularity | Separate commits for tests and implementation | I rejected it as excessive | One commit per behaviour with its tests; the build is green in every commit |
| Changelog and versioning | Not part of the plan | I pointed out the missing changelog and versioning | `CHANGELOG.md` (Keep a Changelog) and a SemVer release plan: 1.0.0 for the required operations, 1.1.0 for the additional one; no back-dated versions |

And the other way round, where the AI corrected my initial framing:

| My initial assumption | AI's correction | Outcome |
|---|---|---|
| The task is mainly about multithreading | "Multiple simultaneous matches" means many matches at once, not necessarily many threads; thread safety is still a reasonable assumption, but should be stated as one | Read-heavy, thread-safe usage listed as an explicit assumption |
| We want an integration test | There is no external system to integrate with; the multi-threaded test is a concurrency test | Tests named honestly: unit, acceptance (spec scenario), concurrency |

## Review feedback on the generated code

| Area | Generated first | My feedback | Result |
|---|---|---|---|
| Responsibilities | Validation, name handling and state in one class | Separate the logic early; a method that returns a new value should not be named like a validation | `TeamNames` (normalization), `MatchValidator` (rules), `MatchFactory` (creating a valid match), `ScoreboardState` (immutable state), `LiveScoreboard` (orchestration and concurrency) |
| Packages | Everything in one package | Packages per kind of class | `model` and `exception`; the implementation stays next to the interface to remain package-private (D9) |
| Test structure | Flat test methods | Use given / when / then | `// given / when / then` markers (`// when & then` for assertions on thrown exceptions) and `@Nested` groups per operation |
| Comments | Javadoc on the public API | The code and tests should explain themselves | No comments; rule added to CLAUDE.md |
| Naming | `Scoreboard.inMemory()`, `MatchId.random()`, a public `withMatch`, a private `put` | Names should say what happens, in domain terms | `Scoreboard.create()`, ids generated inside the scoreboard, `ScoreboardState.withMatch` (adds or replaces), `Match.withScore` |
| Defaults | `new Match(id, home, away, 0, 0)` | 0–0 is the default for a new match | Constructor without scores |
| Playing-teams index | — | I asked whether a separate index for a fast check would be over-engineering | Yes for this scale; rejected with the condition for revisiting it (D4) |

## Prompt history

Condensed English translation of my prompts, with a short note on the AI's response.

### 1. Approach

> We're starting a small Java project. I want to use AI deliberately rather than generate code straight away:
> first we prepare an outline, then go through it step by step, preparing good prompts for each step before implementing it.

*AI:* proposed the process — analysis → design decisions → plan → prompts per step → test-first implementation → documentation.

### 2. Project skeleton

> I already have an empty GitHub repo, JDK 25. Please prepare an initial commit with a Java skeleton. I'll commit myself, step by step.
> Should I bother with branches? When working alone it doesn't make much sense, I think.

*AI:* created a Maven skeleton (JUnit, AssertJ, enforcer, Maven Wrapper, `.gitattributes`, `.editorconfig`) and verified the build.
On branches: suggested short-lived branches per stage merged through PRs, as cheap evidence of a reviewed workflow.

### 3. Requirements analysis

> *(pasted the task description)*
> My first observation is concurrency — many threads may update scores at the same time. Go through the requirements
> and identify the other open questions and edge cases they leave unspecified. Then think about how to structure the work
> so that each step can be prompted with the right context. We'll want unit tests and some integration test.
> I think we should start with the interface and the domain model.

*AI:* identified the open questions (tie-break determinism, score semantics, identity, validation, encapsulation, concurrency, scope),
suggested writing down decisions before the interface since the interface follows from them, and refined the testing terminology.

### 4. Locking trade-off

> The lock vs read-write lock choice has to be written up as a trade-off, mentioning that the right one depends on what dominates.
> I assume that since it's a display board, there will be more readers. What do you think?

*AI:* agreed with the assumption but argued that a read-write lock does not pay off for very short read sections;
proposed copy-on-write with a pre-sorted summary and a trade-off table.

### 5. Identity and score semantics

> MatchId — what is the trade-off and what are we choosing between? Same for the absolute score.

*AI:* compared team pair / opaque id / mutable handle, and absolute score / increments / both.

### 6. Alternatives

> 1. What about symbols like in trading? `POLMEX` or `GERSWE`.
> 2. Maybe the event time would solve it?

*AI:* the ticker is a natural key with the team-pair drawbacks — good for display, weak as identity.
Event time rejects stale messages while still allowing corrections; a source version is more robust than a timestamp;
since the extra feature must be an operation, a versioned update fits as a separate method.

### 7. Decisions

> UUID is fine, absolute score obviously, and idempotency must be described. Update with a version sounds great.

### 8. Traceability of decisions

> Does this document show how the decisions were reached?

*AI:* it presented only the outcomes; suggested recording the origin of the key decisions and marking
the performance reasoning as not benchmarked.

### 9. Commit granularity

> Separate commits for tests are too much. Feature branches, yes, but not that.

*AI:* agreed — a tests-only commit leaves a red build and breaks `git bisect`. Workflow changed to one commit per
behaviour with tests and implementation together.

### 10. Conventions and plan

Original prompt (written in English):

> Write a short CLAUDE.md for this repo. It should say:
> - this is a simple Java 25 library, in memory, with no frameworks
> - docs/requirements-analysis.md is the main reference; if something is missing there, ask me
> - we use records for values, keep the implementation package-private, and use the exceptions from D6
> - we write tests first (JUnit + AssertJ) and the build must pass after every commit
>
> Then write docs/implementation-plan.md with small steps. Start with the smallest thing:
> start a match and see it in the summary. End with concurrency, the extra feature and the docs.
> For each step, write what it does, which tests check it, and when it is done.
> One step one commit.

*AI:* wrote both files; added a short "boundaries" section (no new dependencies, no API beyond the decisions,
no commits by the AI) and a rule not to assert summary order before the ordering step.

### 11. Step 1 — start a match

> Implement it step by step; I'll tell you what I don't like. For example, I don't like `inMemory`.

*AI:* renamed the factory to `Scoreboard.create()` and the implementation to `LiveScoreboard`.
Wrote the tests first and confirmed they failed (the API did not exist yet), then the minimal implementation.
Left `Match.totalScore()` out until the ordering step, where it is first used, and updated the plan accordingly.

> I don't like `random`, it should be something like `generateId`.

> We don't need comments; the code should explain itself together with the tests.

*AI:* renamed the id factory, removed all Javadoc and the placeholder `package-info.java`, and added the no-comments rule to CLAUDE.md.

### 12. Step 2 — validating a new match

> Let's go on. For tests, introduce given / when / then. Continue with the implementation and take edge cases
> and input validation into account.

*AI:* pointed out the conflict with the no-comments rule and chose `// given / when / then` markers as the only
allowed comments (structure, not explanation), recorded in CLAUDE.md. Grouped tests with `@Nested` per operation.
Wrote the validation tests first and ran them against the existing code (23 of 28 failing), then implemented.
Edge cases covered by tests: blank names including tabs and newlines, the same team differing only in case or
whitespace, a team already playing on either side, a rematch with swapped sides, and a rejected start leaving the
scoreboard unchanged. Edge cases deliberately left out (internal whitespace, Unicode case folding, name length)
were documented in D5.

### 13. Step 2 — reviewing the structure

My review comments on the generated code, in order:

> It's worth introducing packages for different kinds of classes.

> Dedicated helper classes? For example for `requireValidTeamName`.

> Maybe two dedicated methods instead of passing those strings, and then they can become constants in the helper.

> Maybe move all of this validation into a smaller method? — Do we really need the `Teams` object here?
> Wouldn't a plain `void validate` be better?

> If `requireValidAwayTeam` changes the value or creates a new one, maybe it should be named after that operation —
> some kind of normalization. At this point it's worth thinking about separating the logic: we only have the first
> features and there is already quite a lot of code here.

*AI:* for packages, pointed out the conflict with D9 (moving the implementation to its own package would make it
public) and offered two options; I chose `model` + `exception`. The validation code then went through a few
iterations driven by these comments and ended with transformation separated from checking: `TeamNames.normalize`
returns the stripped name, `MatchValidator` holds the rules as `void` checks, and `LiveScoreboard` only orchestrates.
Behaviour did not change, so the existing tests guarded every refactoring without modification.

### 14. Step 2 — concurrency

> Let's remember that we want this to work with multiple threads. And is passing the map around like this good practice?
> Let's take it into account right away.

*AI:* agreed on both. Passing `matches.values()` exposed a live, mutable view of the internal state to another class.
The "one match per team" rule is a check-then-act across several entries, so a concurrent map alone would not protect it.
Pulled the copy-on-write design (D4) forward from step 6: an immutable `ScoreboardState`, writes validated and applied
under one lock, reads of a `volatile` snapshot; the validator now receives the immutable state.
Wrote concurrency tests first and ran them against the old code: 19 of 20 repetitions failed with
`ConcurrentModificationException`, and 18 of 20 let the same team start several matches. After the change all tests
passed in repeated runs. The plan was updated: step 6 now only verifies all operations together under concurrent load.

> We want as little code as possible in the critical section. Do we really want all the validation there, or only
> the state checks? And how does the current solution compare to what we planned for the concurrency option?

*AI:* only the state checks belong there. Split the validator into `validateTeams` (stateless, before the lock) and
`validateTeamsNotPlaying` (inside the lock), and moved id generation — `UUID.randomUUID()` uses `SecureRandom` — and
creating the `Match` out of the lock as well. Compared the result with D4: it matches the plan (single writer lock,
immutable state published through `volatile`, lock-free O(1) reads, copy cost O(n) per write); the only deviations
were the timing (introduced in step 2 instead of step 6) and the oversized critical section, now fixed.
Also documented a lock-free `AtomicReference.updateAndGet` variant as a considered alternative.

> I don't like the name `withMatch`. And would adding a separate map of teams currently playing, for a fast check,
> be over-engineering?

*AI:* renamed it to `addMatch`, consistent with the upcoming update and remove operations. On the index: yes, over-engineering
for now — with a handful of simultaneous matches the scan costs nothing measurable, while the index would be a second source
of truth to keep in sync and to copy on every write. Recorded in D4 together with the condition for revisiting it.

> `Match` — we don't need `0, 0` in the constructor, that's the default anyway.

*AI:* added a constructor without scores that delegates to the canonical one with 0–0. Tests keep the full form,
since there 0–0 is the expected value being asserted.

### 15. Review checkpoint

> Review the documents and log entries and correct anything that is inaccurate. In parallel, run a separate code review —
> is there anything to improve at this stage?

*AI:* found that the documents described tests being reviewed before implementation, which did not match how the steps
were actually done (tests first and run red, then implementation, then my review); corrected AI.md and CLAUDE.md.
Also split this file's change log into design decisions and code review feedback, and made the requirements analysis
consistent with which decisions carry an origin note.

The separate code review found no defects in the concurrency core and a few real gaps: records accepting `null` and
negative values, `ScoreboardState` accepting mutable collections through its canonical constructor, a `serialVersionUID`
compiler warning (missed earlier because only incremental builds had been checked), a D9 statement that did not match
the code, and some missing edge-case tests. The documentation gaps were corrected right away.

> Change `when / then` to `when & then`. And make the fixes you found right away.

*AI:* renamed the markers and fixed all findings, test-first where behaviour changed (8 new tests failing before the fix):
- `Match` and `MatchId` validate `null`s and negative scores in compact constructors;
- `ScoreboardState` became a final class that copies its collections itself, so no caller can hand it a mutable map;
- `null` and blank checks moved to the validator, on the raw input before normalization, so the messages name the side;
- inside the lock the state is read once into a local, so the check and the update visibly use the same snapshot;
- concurrency tests now compare the returned ids with the ids in the summary instead of only counting;
- missing edge-case tests added (spelling kept as provided, internal whitespace, rejection by the away team);
- `serialVersionUID` added, `-Werror` enabled, default Maven plugin versions pinned.

> Maybe return a `Match` from all of that and take the fields from that object afterwards?

*AI:* extracted the input checks, normalization and creation into a private `newMatch` that returns a valid `Match`;
`startMatch` now reads as "create a valid match, then check the state and store it under the lock", and the state
check takes the `Match` instead of two strings.

> And then it can probably be moved out of here, right?

*AI:* yes — creating a match does not depend on the scoreboard state, so it moved to a package-private `MatchFactory`.
`LiveScoreboard` is left with orchestration and concurrency only.

### 16. Step 3 — update the score

> Let's go on.

*AI:* added the `updateScore` signature, `MatchNotFoundException` and a stub throwing `UnsupportedOperationException`,
so the new tests compiled and failed (29 red: 9 unit tests and a concurrency test repeated 20 times), then stopped
for review. Tests cover the summary showing the new score, idempotency (D2), lowering the score as a correction,
isolation between matches, `null` id, negative scores, an unknown match, a rejected update leaving the state unchanged,
and concurrent updates of different matches not being lost — moved forward from step 6, as the copy-on-write pattern
already exists.

> I accept, implement it.

*AI:* stateless checks (`null` id, negative scores) run in `MatchValidator` before the lock; inside the lock a single call,
`ScoreboardState.updateScore`, looks the match up, rejects an unknown id and returns the new state. All 107 tests passed
in repeated clean runs.

> The worst part is that we have no clear control over the events: if an old, stale event arrives — lost and delivered
> out of order — this version has no way to detect it.

*AI:* confirmed this is the known limitation of absolute updates recorded in D2: duplicates and missed updates are
handled (idempotent, self-healing), but out-of-order delivery is not. It is deliberately left out of the core operation
and addressed by the additional feature, the versioned update (D8, step 7), which must be a distinct commit anyway.

> Do a thorough review of `ScoreboardState`. I don't like `put`, not even the name.

*AI:* agreed that `put` was Map vocabulary hiding intent — adding and replacing a match went through the same call.
The review found two more issues: the state built a `Match` from its fields (so it would have to change whenever the record
gains a field, e.g. in steps 5 and 7), and it threw `MatchNotFoundException`, a rule decision that does not belong to
the state. Refactored: the state answers questions (`findMatch`, `isPlaying`) and returns new versions of itself
(`addMatch`, `replaceMatch`); the not-found rule moved to `LiveScoreboard`; `Match.withScore` builds the updated match.

> `withScore` is fine.

> For the private helper, `withMatch` fits now.

*AI:* renamed it — inside the state, "this state with the match included" is exactly what it does.

> Once more — review the whole thing, and do the tests cover everything in this version?

*AI:* measured coverage with JaCoCo run once from the command line (no pom change): 100% lines and branches.
Pointed out that coverage only shows every line ran, not that every behaviour is checked, and listed the gaps:
no test that a summary taken earlier is unaffected by later writes (the D4 snapshot guarantee), no state check after
an update of an unknown match, unchecked messages of the domain exceptions, and an undefined validation order.
Also flagged the non-negative rule existing in both the validator and the record, a possible `int` overflow in the
future `totalScore()`, and inconsistent continuation indentation.

> Yes, add everything.

*AI:* added the tests (they pass immediately — they guard existing behaviour against regressions), documented the
duplicated rule as deliberate in D6, noted the overflow decision in plan step 5, unified continuation indentation and
recorded it in `.editorconfig`.

> What about the exception thrown inside the critical section?

*AI:* it is safe — an exception leaving a `synchronized` block always releases the monitor, and with copy-on-write the state
is replaced only as the last statement, so nothing is half-changed (already covered by a test). The only cost is creating
the exception under the lock, on the error path only. Showed how to move the throw outside the lock and recommended
against it for readability. Recorded the reasoning in D4.

> Can't we wrap this more nicely? It's quite a long sequence; splitting it into smaller steps might be more readable.

*AI:* extracted "find the match in progress or throw" into a private `matchInProgress`, named after the domain rather
than the mechanics. The block under the lock now mirrors `startMatch` — read the state, one domain step, replace the
state — and the helper will be reused by `finishMatch` in step 4.

> Is reading `state` into `current` inside the lock needed? Wouldn't it be better to work on `state` directly?

*AI:* both are correct — writers are serialized by the lock. Recommended keeping the local: it makes explicit that the
check and the update use the same snapshot, and it is the shape a lock-free variant would need. Kept.

> That `" must not be null"` could be a constant.

*AI:* extracted the message suffixes (`null`, blank, negative) into private constants in `MatchValidator` and in `Match`;
the record lives in another package, so a shared public messages class would not be worth it for three strings.

> `addMatch` and `replaceMatch` both just call `withMatch` — does that make sense?

*AI:* no — they were aliases whose names promised guarantees the code did not enforce (no overwrite on add, existence on
replace). Enforcing them would add unreachable branches, since the callers already guarantee both. Replaced them with one
`withMatch(Match)` ("this state with that match"), which will pair naturally with `withoutMatch` for finishing a match.

### 17. Step 4 — finish a match

> Merged, let's go with the next step.

*AI:* added the `finishMatch` signature with a stub and wrote the tests first (28 red: 8 unit tests and a concurrency test
repeated 20 times), then stopped for review. Tests cover removing the match, keeping the others with their scores,
a rematch after finishing (the counterpart of the step 2 test that rejects it while the first match is in progress),
`null` id, an unknown match, finishing twice and updating a finished match (both `MatchNotFoundException`, D7),
a rejected finish leaving the state unchanged, and many threads finishing the same match with exactly one succeeding.

> I accept the implementation. The test class keeps growing — I suggest splitting it into dedicated classes per feature.

*AI:* implemented first (`MatchValidator.validateMatchId`, reused by the score update; `ScoreboardState.withoutMatch`;
the existing `matchInProgress` helper reused under the lock), then split the tests into `StartMatchTest`,
`UpdateScoreTest`, `FinishMatchTest` and `SummaryTest`, each with a nested `Validation` class. The split was done by
moving whole blocks with a script; a first run dropped one line from two files, which the check caught — the list of
executed test methods before and after the split was compared and is identical (139 tests).

> We have no changelog and no versioning yet — that's a gap in the process, worth adding now.

*AI:* agreed to add a changelog, but argued against back-dating versions: nothing has been released, so retroactive
versions would be fiction, and merged history should not be rewritten. Added `CHANGELOG.md` (Keep a Changelog) with
all changes so far under `[Unreleased]`, a release plan following SemVer — 1.0.0 when the four required operations are
complete, 1.1.0 for the additional operation as a backward-compatible minor addition — release steps in the plan, and a
CLAUDE.md rule that every user-visible change updates the changelog in the same commit.

> Is the order of the changelog entries right? I don't think so — the project initialization is at the top.

*AI:* agreed — entries now go newest first, consistent with the reverse-chronological order of versions; internal
changes (Maven Wrapper, compiler flags) were removed, as the changelog is for library users; only the Java 25
requirement stayed. Rule added to CLAUDE.md.

## Artifacts that guided the implementation

- [docs/requirements-analysis.md](docs/requirements-analysis.md) — open questions, options, decisions and their origin.
- [CLAUDE.md](CLAUDE.md) — working context and boundaries for the AI.
- [docs/implementation-plan.md](docs/implementation-plan.md) — the steps, their tests and definitions of done.
- [CHANGELOG.md](CHANGELOG.md) — user-visible changes and the release plan.
