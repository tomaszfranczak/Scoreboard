# AI Usage

> Draft — updated as the work progresses.

## Summary

**Tool:** Claude Code (model: Claude Opus 5.5), used in the Claude desktop app with access to the repository.

**How I used it:**
- **Requirements analysis first, code later.** Before writing any code I used the AI as a sparring partner
  to surface the open questions hidden in the spec and to compare design options. I took the decisions;
  the outcome is recorded in [docs/requirements-analysis.md](docs/requirements-analysis.md), together with how each decision was reached.
- **Challenging the suggestions.** I did not accept the first proposal by default. Several decisions changed
  after I pushed back or proposed alternatives (see [Where the design changed through discussion](#where-the-design-changed-through-discussion)).
- **Test-driven implementation in small, reviewed steps.** Each operation is built test-first: the AI drafts the tests
  from the decisions in the requirements analysis, I review them against the spec *before* any production code exists,
  and only then is the implementation written to make them pass. Tests are the contract I check the AI's code against,
  so a wrong assumption shows up as a disagreement about a test rather than as a bug hidden in generated code.
  TDD shapes how the work is done, not the shape of the history: each commit adds one behaviour together with its tests
  and keeps the build green, and each stage is developed on its own feature branch. *(to be completed)*

**Language note:** the conversation was held in Polish, my native language. The prompt history below is my
English translation, condensed but faithful in content. All code, documentation and commit messages are in English.

## Workflow

1. Project skeleton (Maven, JDK 25, Maven Wrapper), set up with the AI and verified with `./mvnw verify`.
2. Requirements analysis and design decisions, done as a dialogue → [docs/requirements-analysis.md](docs/requirements-analysis.md).
3. Conventions file for the AI ([CLAUDE.md](CLAUDE.md)) and an implementation plan ([docs/implementation-plan.md](docs/implementation-plan.md)).
4. TDD cycle per operation (start → update → finish → summary):
   1. **Red** — the AI writes failing tests for one operation; I review them against the spec and the decisions.
   2. **Green** — the AI writes the minimal implementation to make them pass.
   3. **Refactor** — clean-up with tests green; I review the diff before committing.

   One commit per behaviour, containing both the tests and the implementation, so every commit builds and passes.
5. Concurrency, then the additional feature (in a distinct commit), following the same cycle.
6. README and final pass on this file.

## Where the design changed through discussion

| Topic | AI's initial suggestion | What changed it | Outcome |
|---|---|---|---|
| Concurrency | One coarse-grained lock | I pointed out that the lock choice depends on the read/write ratio and that a scoreboard display is read-heavy | Copy-on-write snapshot; read-write lock considered and rejected for short read sections (D4) |
| Match identity | Opaque `MatchId` | I proposed a trading-style ticker (`POLMEX`) | Analysed as a natural key; rejected because of rematch collisions; UUID kept (D1) |
| Out-of-order feed updates | Documented as a known limitation | I suggested that the event time could solve it | Turned into the additional feature: versioned score update (D8) |
| Idempotency | Mentioned as one advantage among others | I required it to be documented explicitly | A dedicated idempotency section in D2 |
| Commit granularity | Separate commits for tests and implementation | I rejected it as excessive | One commit per behaviour with its tests; the build is green in every commit |
| Factory name | `Scoreboard.inMemory()` | I did not like the name | `Scoreboard.create()`, implementation named `LiveScoreboard` |
| Id generation | `MatchId.random()` | I wanted the name to say that an id is generated, not how | `MatchId.generate()` |
| Comments | Javadoc on the public API | I prefer self-explanatory code backed by tests | No comments; rule added to CLAUDE.md |

And the other way round, where the AI corrected my initial framing:

| My initial assumption | AI's correction | Outcome |
|---|---|---|
| The task is mainly about multithreading | "Multiple simultaneous matches" means many matches at once, not necessarily many threads; thread safety is still a reasonable assumption, but should be stated as one | Read-heavy, thread-safe usage listed as an explicit assumption |
| We want an integration test | There is no external system to integrate with; the multi-threaded test is a concurrency test | Tests to be named honestly: unit, acceptance (spec scenario), concurrency |

## Prompt history

Condensed English translation of my prompts, with a short note on the AI's response.

### 1. Approach

> We're starting a small Java project. I want to use AI deliberately rather than generate code straight away:
> first we prepare an outline, then go through it step by step, preparing good prompts for each step before implementing it.

*AI:* proposed the process — analysis → design decisions → plan → prompts per step → TDD implementation → documentation.

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

*AI:* identified the hidden questions (tie-break determinism, score semantics, identity, validation, encapsulation, concurrency, scope),
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

> Does this document actually reflect that the decisions came out of a discussion, or does it read like a finished generated output?

*AI:* the document read as a finished output; suggested recording the origin of each decision and marking
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

*AI:* renamed it to `MatchId.generate()` — the `Id` suffix would repeat the type name at the call site.

> We don't need comments; the code should explain itself together with the tests.

*AI:* removed all Javadoc and the placeholder `package-info.java`, and added the rule to CLAUDE.md.

## Artifacts that guided the implementation

- [docs/requirements-analysis.md](docs/requirements-analysis.md) — open questions, options, decisions and their origin.
- [CLAUDE.md](CLAUDE.md) — working context and boundaries for the AI.
- [docs/implementation-plan.md](docs/implementation-plan.md) — the steps, their tests and definitions of done.
