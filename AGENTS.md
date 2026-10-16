# AGENTS.md

Guidance for AI coding agents (and new contributors) working in this repo.
For process (issues, DCO, review flow) see [CONTRIBUTING.md](CONTRIBUTING.md).
For the detailed rationale and quoted maintainer feedback behind the rules
below, see [docs/REVIEW_STANDARDS.md](docs/REVIEW_STANDARDS.md).

## Project overview

This is an OpenSearch plugin (Java) that adds search-quality evaluation and
experimentation tooling: judgments, query sets, search configurations, and
experiments (pointwise, pairwise, hybrid-optimizer) measured with IR metrics.
It ships as a cluster plugin plus Dashboards UI, and maintains BWC across
rolling and full-cluster-restart upgrades.

## Setup commands

- Requires JDK 21.
- Build the plugin: `./gradlew build`
- Clean build artifacts: `./gradlew clean`

## Test commands

- Unit tests: `./gradlew test`
- Single unit test: `./gradlew test --tests "org.opensearch.searchrelevance.<pkg>.<ClassName>"`
- Integration tests (full REST cluster): `./gradlew integTest`
- Rolling-upgrade BWC tests: `./gradlew :qa:rolling-upgrade:testRollingUpgrade`
- Restart-upgrade BWC tests: `./gradlew :qa:restart-upgrade:testRestartUpgrade`
- `./gradlew build` runs `check`, which includes unit tests, integ tests, and
  Jacoco coverage — run it before opening a PR.

## Code style

- Formatting is enforced by Spotless: `./gradlew spotlessApply` to fix,
  `./gradlew spotlessCheck` to verify (also runs as part of `build`/`check`).
- Per-class imports, not wildcard imports (OpenSearch-wide convention).
- American spelling in identifiers (`Judgment`, not `Judgement`), matching
  existing code.
- Method/class names and Javadoc must match actual semantics (e.g. don't
  call something "Mean Reciprocal Rank" if it computes plain Reciprocal Rank).

## Engineering checklist (see docs/REVIEW_STANDARDS.md for full detail)

These are the standards maintainers consistently enforce in review. 
Each is backed by real PR comments — see the linked doc for quotes and context.

- **Backward compatibility is non-negotiable.** Any change to an index
  mapping, stored-document format, or REST request/response schema needs: a
  bumped mapping version, and a real restart-upgrade/rolling-upgrade BWC test
  (`qa/restart-upgrade`, `qa/rolling-upgrade`) — not just a unit test that
  writes an "old" document into an index that already has the new mapping.
  Prefer backward-compatible parsing (support old + new formats) over a
  migration step; only drop old formats at a major version bump.
- **Fail fast, never swallow or silently default.** Invalid/unexpected state
  (bad version, unknown enum value, missing data) should throw, not log-and-
  continue or fall back to a default. Validate at the API boundary. Branch
  on failure type in error handlers instead of collapsing everything to a
  generic status code.
- **Treat all external input as hostile.** Validate size/bounds/type on
  anything a caller supplies (payloads, doc IDs, scores, cron intervals).
  Avoid unbounded/dynamic mapping fields driven by user input. Any new or
  changed public API requires a security review; don't enable script/template
  style input ahead of the underlying engine closing known injection vectors.
- **Concurrency: reason explicitly about races, never block a shared pool.**
  Check-then-act races on caches/maps, single-lock collections under
  contention, and lost-update races on read-modify-write all need explicit
  handling (optimistic concurrency, `ConcurrentHashMap`, etc). Never
  `Thread.sleep`/blocking-`get()` on a shared thread pool — use
  `ActionListener` chaining, `GroupedActionListener`, or a managed
  `ThreadPool`/`ScheduledExecutorService`. Long-running async jobs need a
  liveness check so status can't get stuck forever on a crashed worker.
- **Unit tests by default; integration tests only for critical end-to-end
  flows.** This is a small volunteer maintainer team — every test is
  maintenance cost. A test must be able to fail: don't write assertions that
  pass whether or not the new code path actually runs.
- **Minimize API surface.** Fields are easy to add later, hard to remove —
  don't add them speculatively. Prefer explicit type hierarchies over one
  class holding every variant's optional fields, or flag-driven branching
  with validity filtered downstream.
- **Don't duplicate.** Reuse existing constants (`PluginConstants`,
  `SearchRelevanceIndices`) and extract shared logic instead of letting
  near-identical methods diverge.
- **Delete dead code**, don't comment it out or mark it `@Deprecated` if
  nothing depends on it. Exception: retired public request parameters should
  still be accepted and silently ignored, not rejected as unknown fields.
- **Gate risky/new functionality behind a feature flag**, opt-in until
  proven safe. A genuinely new public API expects a design doc / RFC-style
  GitHub issue (not committed into the repo) before or alongside the PR.
- **Think about production scale.** Watch for accidental superlinear
  behavior (e.g. a `Comparator` re-serializing on every comparison) in
  hot paths.
- **Update CHANGELOG.md**: exactly one entry per PR, correct category
  (Features / Enhancements / Bug Fixes / Infrastructure / Documentation /
  Maintenance). New/changed public APIs need user-facing documentation too.
- **Clean commit history and DCO sign-off.** Squash noisy "wip"/"retry"
  commits before merge.

## PR checklist

- [ ] `./gradlew build` passes (unit + integ tests + Spotless + coverage).
- [ ] Mapping/schema changes: version bumped, BWC test added.
- [ ] No swallowed exceptions or silent fallback on bad/unexpected input.
- [ ] CHANGELOG.md updated with exactly one correctly-categorized entry.
- [ ] New/changed public API: security review and design doc considered.
- [ ] Commit history squashed; DCO sign-off present.
