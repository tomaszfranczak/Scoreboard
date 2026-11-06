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
| 7 | Additional feature: versioned score update | `feature/versioned-update` |
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

**Done when:** D7 is fully covered by tests and they pass.

## Step 5 — Order the summary

**What it does:** the summary is ordered by total score (descending); ties go to the most recently started
match first. Start order comes from a sequence counter, not the clock (D3).
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
- concurrent starts, updates and finishes with reads → readers always see a complete, correctly ordered summary
- summary obtained before a write does not change after it (snapshot)

**Done when:** all earlier tests still pass and the concurrency tests pass reliably.

## Step 7 — Additional feature: versioned score update

**What it does:** a separate operation that applies a score only if its version is newer than the last
applied one (D8). Must be a **distinct commit**, as the spec requires.

**Before coding:** decide the open questions in D8 (what happens to a stale update, how versioned and
unversioned updates interact, what an equal version means) and update the requirements analysis.

**Tests:** derived from the D8 decisions; at least:
- a newer version is applied
- an older version does not change the score
- a correction with a newer version can lower the score
- validation as in step 3 (null id, unknown id, negative score)

**Done when:** D8 has no open questions left, the tests pass, and the README has a section explaining the feature.

## Step 8 — Documentation

**What it does:** finishes the required documents.
- `README.md`: usage example, assumptions, reasoning and trade-offs (from the requirements analysis), the extra feature.
- `AI.md`: complete the workflow, prompt history and artifacts.

**Done when:** a reviewer can understand and run the project from the README alone.
