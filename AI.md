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
3. Conventions file for the AI and an implementation plan with a prompt per step. *(to be completed)*
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

## Artifacts that guided the implementation

- [docs/requirements-analysis.md](docs/requirements-analysis.md) — open questions, options, decisions and their origin.
- *(to be added: conventions file for the AI, implementation plan)*
