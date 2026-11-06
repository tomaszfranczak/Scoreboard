# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release plan:
- **1.0.0** — the four required operations are complete (start, update, finish, ordered summary).
- **1.1.0** — the additional operation (versioned score update); a backward-compatible addition, hence a minor version.

Between releases the version is a `-SNAPSHOT` of the next planned release; the final version is set in the release commit.

## [Unreleased]

### Added

- `Scoreboard.updateScore(matchId, homeScore, awayScore, sequenceNumber)` applying a score only if its sequence number
  is higher than the last one applied to that match; returns `false` for stale or duplicate messages, so scores from an
  out-of-order feed can never go back. The update without a sequence number acts as a manual correction.

## [1.0.0] - 2026-11-06

### Added

- Java module `io.github.tomaszfranczak.scoreboard` exporting the API packages (`scoreboard`, `scoreboard.model`,
  `scoreboard.exception`); the implementation is not exported.
- `Scoreboard.getSummary()` orders matches by total score (descending); matches with the same total are ordered
  with the most recently started first. `Match.totalScore()` returns a `long`, so it cannot overflow.
- `Scoreboard.finishMatch(matchId)` removing a match from the scoreboard; finishing or updating an unknown or already
  finished match throws `MatchNotFoundException`.
- `Scoreboard.updateScore(matchId, homeScore, awayScore)` setting the absolute score; idempotent, scores may decrease
  to express corrections, negative scores are rejected.
- Thread safety: all operations can be called concurrently; reads never block and always return a consistent snapshot.
- `Match` and `MatchId` reject `null` values and negative scores on construction.
- Validation when starting a match: `null` and blank team names, the same team on both sides, a team already playing
  in a match in progress; team names are compared case-insensitively, without surrounding whitespace.
- `Scoreboard.create()`, `Scoreboard.startMatch(homeTeam, awayTeam)` returning an opaque `MatchId` (a new match starts
  at 0–0), and `Scoreboard.getSummary()` returning an immutable snapshot of the matches in progress.
  Requires Java 25.
