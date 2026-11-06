# Implementation Plan

Small steps, each ending in **one commit** that contains the tests and the implementation together
and keeps `./mvnw verify` green. Each step is developed test-first: test cases are reviewed before
the implementation is written.

Decision ids (D1–D9) refer to [requirements-analysis.md](requirements-analysis.md).

| # | Step | Branch |
|---|---|---|
| 1 | Start a match and see it in the summary | `feature/start-match` |
| 2 | Validate starting a match | `feature/start-match-validation` |
| 3 | Update the score | `feature/update-score` |
| 4 | Finish a match | `feature/finish-match` |
| 5 | Order the summary | `feature/summary-ordering` |
| 6 | Concurrency across all operations | `feature/concurrency` |
| 7 | Additional feature: score update with a sequence number | `feature/versioned-update` |
| 8 | Documentation | `docs/readme` |

---

## Step 1 — Start a match and see it in the summary

**What it does:** the thinnest vertical slice. `startMatch` is only observable through `getSummary`,
so both are built together.
- `Scoreboard` interface with `startMatch(homeTeam, awayTeam)` and `getSummary()` only.
- `Match` record (id, teams, scores) and `MatchId` record wrapping a `UUID` (D1).
- Package-private implementation `LiveScoreboard`, created through `Scoreboard.create()` (D9).
- A new match starts at 0–0 (D7).

**Tests:**
- a new scoreboard has an empty summary
- a started match appears in the summary with the given teams and a 0–0 score
- several started matches all appear in the summary
- each started match gets a different id
- the returned summary cannot be modified

**Done when:** the tests pass; no validation, ordering or concurrency code exists yet.

## Step 2 — Validate starting a match

**What it does:** applies the rules from D5 and D6 to `startMatch`, and makes starting thread-safe.
The "team plays in one match at a time" rule is a check-then-act across several entries, so the copy-on-write
state from D4 is introduced here rather than in step 6: an immutable `ScoreboardState`, writes under one lock,
reads of a `volatile` snapshot. Every later write operation follows the same pattern.

**Tests:**
- `null` team name → `NullPointerException`, the message names the side
- blank team name → `IllegalArgumentException`, the message names the side
- team names keep the spelling as provided; names differing in internal whitespace are different teams
- `Match` and `MatchId` reject `null`s and negative scores themselves
- the same team on both sides → `IllegalArgumentException`, also when it differs only in case or whitespace
- a team already playing (home or away) → `TeamAlreadyPlayingException`, also with different case or whitespace
- team names are stored trimmed
- a rematch with swapped sides while the first match is in progress → `TeamAlreadyPlayingException`
- a rejected start leaves the summary unchanged
- concurrency: many threads starting matches with different teams → all matches are present
- concurrency: many threads starting matches with the same team → exactly one succeeds

**Done when:** all rules from D5 are covered by tests and pass, and the concurrency tests pass reliably.

## Step 3 — Update the score

**What it does:** `updateScore(matchId, homeScore, awayScore)` sets the absolute score (D2).

**Tests:**
- the summary shows the updated score
- applying the same update twice gives the same result (idempotency)
- the score can decrease (correction)
- a negative score → `IllegalArgumentException`
- `null` id → `NullPointerException`
- an unknown id → `MatchNotFoundException`
- updating one match does not change the others
- a rejected update leaves the summary unchanged
- concurrency: concurrent updates of different matches → no update is lost

**Done when:** D2 is fully covered by tests and they pass.

## Step 4 — Finish a match

**What it does:** `finishMatch(matchId)` removes the match from the scoreboard (D7).

**Tests:**
- a finished match disappears from the summary; other matches stay
- `null` id → `NullPointerException`
- finishing an unknown or already finished match → `MatchNotFoundException`
- updating a finished match → `MatchNotFoundException`
- after a match is finished, its teams can start a new match
- finishing one match keeps the others with their scores
- a rejected finish leaves the summary unchanged
- concurrency: many threads finishing the same match → exactly one succeeds

**Done when:** D7 is fully covered by tests and they pass.

## Step 5 — Order the summary

**What it does:** the summary is ordered by total score (descending); ties go to the most recently started
match first. Start order comes from a monotonic counter (the start index), not the clock (D3).
`Match.totalScore()` is added here, where it is first needed. Summing two `int` scores can overflow at
`Integer.MAX_VALUE`; decide here whether to compute the total as `long` or to set an upper bound for a score.

**Tests:**
- a higher total score comes first
- with equal totals, the more recently started match comes first
- matches started one right after another are still ordered correctly (no clock involved)
- **acceptance test:** the example scenario from the spec, through the public API only, in a separate test class

**Done when:** the spec scenario produces exactly the expected order.

## Step 6 — Concurrency across all operations

**What it does:** the copy-on-write state exists since step 2 and every write operation uses it.
This step verifies the operations together under concurrent load.

**Tests** (started together through a latch, repeated to make races likely):
- concurrent starts, updates and finishes → the final state is complete and correctly ordered
- readers during concurrent writes → every summary they see is ordered and has no team twice
- summary obtained before a write does not change after it (snapshot; covered in `SummaryTest` since step 3)

Since the implementation already exists, the new tests pass at once; a mutation check (all `synchronized` blocks
removed) must show that every concurrency test fails without the locks.

**Done when:** all earlier tests still pass, the concurrency tests pass reliably, and the mutation check is done.

**Release 1.0.0** (separate commit after this step): set the version in `pom.xml` to `1.0.0`, move the `[Unreleased]`
entries in `CHANGELOG.md` to `[1.0.0]`, tag `v1.0.0`; then set the next development version `1.1.0-SNAPSHOT`.

## Step 7 — Additional feature: score update with a sequence number

**What it does:** an `updateScore` overload that applies a score only if its sequence number is higher than the last
applied one (D8). Must be a **distinct commit**, as the spec requires.

**Before coding:** decide the open questions in D8 (what happens to a stale update, how updates with and without
a sequence number interact, what an equal number means) and update the requirements analysis.

**Tests:** derived from the D8 decisions:
- the first numbered update is applied; a higher number is applied
- a lower number arriving late, and the same number again, do not change the score (`false`)
- a correction with a higher number can lower the score
- an update without a sequence number is a manual correction that keeps the last number
- sequence numbers are tracked per match
- validation as in step 3, plus a negative sequence number
- concurrency: many threads sending numbers 0–31 for one match → the highest number wins
- mutation check: disabling the number check and removing the lock must make tests fail

**Done when:** D8 has no open questions left, the tests pass, and the README has a section explaining the feature.

**Release 1.1.0** (after the documentation step): same procedure as 1.0.0 — a new backward-compatible operation is a
minor version.

## Step 8 — Documentation

**What it does:** finishes the required documents.
- `README.md`: usage example, assumptions, reasoning and trade-offs (from the requirements analysis), the extra feature.
- `AI.md`: complete the workflow, prompt history and artifacts.

**Done when:** a reviewer can understand and run the project from the README alone.
