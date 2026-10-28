# Review Standards — the "why" behind AGENTS.md

This document is the detailed companion to [AGENTS.md](../AGENTS.md). It
distills recurring, substantive feedback from this repo's
maintainers.

AGENTS.md gives you the condensed checklist; this file gives you the
reasoning and the receipts, organized by theme. Read it when you want to
understand *why* a rule exists, or when you're arguing a case for an
exception.

---

## 1. Backward compatibility (BWC) is the single most enforced standard

Multiple maintainers raise this, repeatedly and independently. **Any change
to an index mapping, stored-document format, or REST request/response schema
needs real proof that a cluster mid-upgrade keeps working.**

**What counts as a real BWC test:** a restart-upgrade or rolling-upgrade test
(see `qa/restart-upgrade` and `qa/rolling-upgrade`) that runs an old-version
node/index against new code. A unit test that writes an "old-format" document
into an index that already has the *new* mapping proves nothing about an
actual upgrade path:

> "`assertLegacyEvaluationDocumentWithoutTookMsStillLoads`... is a weak
> stand-in for a BWC test. It writes an 'old' document into an index that
> already has the new mapping, so it shows nothing an upgrade test wouldn't."
> — heemin32, [PR #593](https://github.com/opensearch-project/search-relevance/pull/593#issuecomment-5959847467)

**Prefer backward-compatible parsing over data migration.** Support reading
both old and new formats in the same code, and only drop the old format at a
later major version — don't force a migration step:

> "There are two paths to backward compatibility: data migration or
> backward-compatible parsing. I'd lean toward the parsing approach as it's
> safer and easier to implement... at the next major version bump, we could
> fully deprecate the old format."
> — heemin32, [PR #414](https://github.com/opensearch-project/search-relevance/pull/414#issuecomment-4558640566)

**If the minimum node version in the cluster could still be old, preserve old
behavior** — don't just rewrite the BWC test to match new behavior:

> "Please don't modify the BWC test itself. The issue is in the code where we
> changed the query set format. If the minimum node version in the cluster is
> lower than the current version, we should preserve the old behavior so that
> both the old and new versions remain compatible."
> — heemin32, [PR #414](https://github.com/opensearch-project/search-relevance/pull/414#issuecomment-4577738644)

**Mapping version bumps must be complete.** If you add a field to a mapping
JSON (`src/main/resources/mappings/*.json`), bump the version field in the
same change, and make sure the mapping actually reflects the new field:

> "I'm not seeing `description` field added to
> `src/main/resources/mappings/search_configuration.json`, I think this is
> required. After #344 you probably need to increment the version field in
> the same... mapping file."
> — martin-gaievski, [PR #370](https://github.com/opensearch-project/search-relevance/pull/370#pullrequestreview-3694515356)

**A rest/index interface change is a BWC risk until proven otherwise** — call
it out yourself in the PR description, and expect reviewers to ask for
rolling-upgrade test runs:

> "The changes in rest request interface might cause an issue on
> backwards-compatibility. Can you add unit tests that assert cross-version
> (old-format / new-format) read/write works. Run rolling-upgrade
> compatibility tests before deploying."
> — fen-qin, [PR #264](https://github.com/opensearch-project/search-relevance/pull/264#discussion_r2445803290)

Related: snapshot/restore paths need the same scrutiny —
"Why there is no code for handling restore of snapshot? Can we add bwc test?"
(heemin32, [PR #318](https://github.com/opensearch-project/search-relevance/pull/318#issuecomment-3639336195)).

---

## 2. Fail fast; never swallow or silently default on bad state

A strong, repeated pattern across all three reviewers: when something is
genuinely wrong (invalid version, unknown enum value, missing data), **throw
an exception** rather than falling back to a default, logging a warning, or
quietly continuing. The user can't see a swallowed log line.

> "For -1, it should throw exception." (re: mapping version read from cluster state)
> — heemin32, [PR #344](https://github.com/opensearch-project/search-relevance/pull/344#discussion_r2615028672)

> "Rejecting is better. User cannot see the warning log."
> — heemin32, [PR #465](https://github.com/opensearch-project/search-relevance/pull/465#discussion_r3271194696)

> "The else → LLM_QUERY_SET branch silently reclassifies any unknown/empty
> sampling value as LLM. That will misclassify future sampling types, typos,
> or null values."
> — fen-qin, [PR #454](https://github.com/opensearch-project/search-relevance/pull/454#discussion_r3132788886)

> "The exception will be swallowed here. Is it expected that we don't care
> about failed judgments reading?"
> — fen-qin, [PR #167](https://github.com/opensearch-project/search-relevance/pull/167#discussion_r2196167946)

This extends to error-handling in listeners: distinguish failure *types* and
propagate the right status code — don't collapse everything into a generic
4xx or treat a partial-batch failure as success:

> "This may be silently masking real exception. If `getSearchConfiguration`
> fails with `ResourceNotFoundException` (404)... or `SearchRelevanceException`
> (500)... we report it as BAD_REQUEST/4xx. Better branch on failure type."
> — martin-gaievski, [PR #498](https://github.com/opensearch-project/search-relevance/pull/498#discussion_r3432167688)

> "What is the behavior in case last out of the 100 evaluation results has
> failed?... we need to store the experiment document with status ERROR
> instead of COMPLETED."
> — martin-gaievski, [PR #120](https://github.com/opensearch-project/search-relevance/pull/120#discussion_r2241190376)

**Validate at the API boundary, not deep in business logic**, so bad input
fails immediately with a clear error:

> "Can you move the length check to rest API level? So it will fail at the
> earliest and throw exceptions to rest response."
> — fen-qin, [PR #120](https://github.com/opensearch-project/search-relevance/pull/120#discussion_r2255329292)

---

## 3. Treat all external input as hostile

Request parameters, imported experiment results, cron expressions, doc IDs,
scores — anything supplied by a caller — needs explicit bounds/type/sanity
checks. This is framed as a security concern, not just robustness:

> "There are not enough restrictions/validations for external experiment
> results in the current code, especially at the API boundary... would you
> like to add explicit limits/checks for payload size, result count, and
> field size to improve robustness and security?"
> — fen-qin, [PR #120](https://github.com/opensearch-project/search-relevance/pull/120#issuecomment-3314002294)

> "Please add validation for both `docId` and `score`, those are user
> provided inputs, we cannot purely rely on good intentions... it's better to
> fail fast rather than silently accept wrong input and then go into a
> corrupted system state later."
> — martin-gaievski, [PR #42](https://github.com/opensearch-project/search-relevance/pull/42#discussion_r2122357395)

> "We should check for min cron interval to avoid system overload, maybe some
> rate limiting for scheduled job creation."
> — martin-gaievski, [PR #220](https://github.com/opensearch-project/search-relevance/pull/220#discussion_r2319530687)

Any field whose cardinality is driven by user input (e.g., one mapping field
per distinct query text) is a cluster-stability risk, not just a style nit —
dynamic/unbounded mappings can hit the 100k-field limit and break a user's
cluster:

> "The number of fields seems to grow in proportion to the number of query
> texts... it's likely we'll eventually hit the 100,000-field limit... I
> believe we should revisit and improve the data schema itself."
> — heemin32, [PR #74](https://github.com/opensearch-project/search-relevance/pull/74#issuecomment-2951146027)

**New public APIs and any interface change require a security review** before
merge — this is treated as a hard gate, not a suggestion:

> "This interface change affects how users interact with the search-relevance
> plugin. As with any interface modification, a new security review will be
> required."
> — fen-qin, [PR #163](https://github.com/opensearch-project/search-relevance/pull/163#issuecomment-3046177240)

> "Because this PR adds new API it has to go over the security review...
> Please share the technical doc that covers details of this change."
> — martin-gaievski, [PR #163](https://github.com/opensearch-project/search-relevance/pull/163#issuecomment-3053602950)

Don't enable template/script-style input (e.g., Mustache in queries) ahead of
the underlying engine actually closing known injection vectors — regex/string
validation alone is not a sufficient defense:

> "Simple validation won't handle several other edge cases. One example is
> `{{> partial}}`... I think we might delay support of Mustache until
> OpenSearch core disables partial unless we use the Mustache library
> directly."
> — heemin32, [PR #342](https://github.com/opensearch-project/search-relevance/pull/342#issuecomment-4836740257)

---

## 4. Concurrency: reason explicitly about races, and never block a shared thread pool

Shared mutable state (caches, lists, counters, status flags) touched from
multiple threads or async callbacks is scrutinized hard. Common failure
patterns reviewers flag:

- **Check-then-act races** on caches/maps:
  > "Since judgmentCache is actively updating, it might cause a race
  > condition. Another thread might modify cache between check and get."
  > — fen-qin, [PR #167](https://github.com/opensearch-project/search-relevance/pull/167#discussion_r2196162251)
- **Single-lock collections under high concurrency** (e.g. `Collections.synchronizedList`) becoming a bottleneck:
  > "If multiple threads try to add results to allResults at the same time,
  > SynchronizedList by nature uses a single lock for all operations... Worst
  > case, parallel operation will act like single-thread operation."
  > — fen-qin, [PR #167](https://github.com/opensearch-project/search-relevance/pull/167#discussion_r2196203447)
- **Lost-update races on read-modify-write**: prefer optimistic concurrency (`_seq_no`/`_primary_term`, retry on `VersionConflictEngineException`) over plain get-then-update.
  > martin-gaievski, [PR #498](https://github.com/opensearch-project/search-relevance/pull/498#discussion_r3432111963)
- **Stuck async state**: long-running/async jobs need a liveness heartbeat so a crashed worker doesn't leave status stuck at `PROCESSING` forever, and concurrent retries need to be handled explicitly.
  > "How do we handle concurrent retry requests? Also, how do we handle if
  > process dies and this status stays as PROCESSING forever?"
  > — heemin32, [PR #528](https://github.com/opensearch-project/search-relevance/pull/528#discussion_r3599388397)

**Never park a thread on a shared pool** (`Thread.sleep`, a blocking `get()`
with a timeout) — it starves the pool under load. Use `ActionListener`
chaining, `GroupedActionListener`, or a scheduled executor instead:

> "This is a blocking call, and it's parking a thread from the generic OS
> pool for up to 30 seconds. This can starve the generic pool in case of high
> search traffic. Better make it fully async — you can combine two searches
> with a `GroupedActionListener<SearchResponse>(2)` (or chained listeners)."
> — martin-gaievski, [PR #511](https://github.com/opensearch-project/search-relevance/pull/511#discussion_r3495306031)

Use OpenSearch's managed `ThreadPool` rather than ad hoc constructs like
`CompletableFuture.delayedExecutor`, which leaks daemon threads with no
lifecycle management:

> "The core change is correct — replacing `CompletableFuture.delayedExecutor`
> with `threadPool.schedule` on `ThreadPool.Names.GENERIC` gives proper
> lifecycle management and fixes the daemon thread leak."
> — heemin32, [PR #503](https://github.com/opensearch-project/search-relevance/pull/503#pullrequestreview-4547359421)

Async index/resource creation must genuinely complete before dependent calls
run — don't assume a `createIndexIfAbsent()`-style call is synchronous:

> "`createIndexIfAbsent()` makes an async call, so it's possible that flow
> reaches your next calls... before the index gets created. You need to run
> next actions when createIndexIfAbsent is 1) completed and 2) done with
> success."
> — martin-gaievski, [PR #54](https://github.com/opensearch-project/search-relevance/pull/54#discussion_r2127085429)

Always clean up executor/future resources in `catch`/`finally` blocks
(martin-gaievski, [PR #166](https://github.com/opensearch-project/search-relevance/pull/166#discussion_r2183256100)).

---

## 5. Test philosophy: unit tests by default, and tests must actually test something

**Default to unit tests with mocks.** Integration tests are reserved for
critical end-to-end flows because they're expensive to run and this is a
small volunteer maintainer team — every test is something that has to be
maintained forever:

> "Let's utilize unit tests for this, integ tests are expensive to run and
> should be reserved for basic end-to-end scenarios or as a last resort for
> critical flows."
> — martin-gaievski, [PR #304](https://github.com/opensearch-project/search-relevance/pull/304#discussion_r2528731943)

> "UT is preferred, integ tests are too costly to run for SRW."
> — martin-gaievski, [PR #327](https://github.com/opensearch-project/search-relevance/pull/327#issuecomment-3599075272)

If you do add an integration test, a lightweight negative test
(`expectThrows(ResponseException.class)` with a fake ID, no data ingestion)
is welcome; broad "test every variant" coverage belongs in unit tests
(martin-gaievski, [PR #360](https://github.com/opensearch-project/search-relevance/pull/360#pullrequestreview-4038523902)).

**A passing test has to be capable of failing.** Don't write assertions that
pass whether or not the new code path actually runs:

> "This pattern means tests pass regardless of whether new metrics are
> actually computed. If the new metrics code path silently breaks, these
> tests wouldn't catch it. After this PR lands, count should be exactly 7."
> — martin-gaievski, [PR #397](https://github.com/opensearch-project/search-relevance/pull/397#discussion_r2860427596)

New code paths need real unit test coverage, and anything claiming
retry/fallback behavior needs a test that actually exercises the failure path,
not just the happy path:

> "Could you kindly add a retry/fallback test as well to make sure this
> implementation can handle such a scenario now?"
> — heemin32, [PR #503](https://github.com/opensearch-project/search-relevance/pull/503#issuecomment-4773165205)

---

## 6. API and schema design

**Minimize the response/request surface.** Adding a field later is safe;
removing one is a breaking change — don't add fields speculatively:

> "Adding them now is a one-way door. Once included, we can't remove them
> without a breaking change. If we leave them out, we can always add them
> later when needed."
> — heemin32, [PR #511](https://github.com/opensearch-project/search-relevance/pull/511#issuecomment-4860653541)

**Don't offer two options that mean the same thing**, and don't store the
same data in two places/indices if you can avoid it:

> "I don't think we need both a 0-1 scale and a 1-5 scale when they're both a
> 5-point scale."
> — heemin32, [PR #264](https://github.com/opensearch-project/search-relevance/pull/264#issuecomment-3423814364)

> "If possible, I'd avoid adding another index, since additional indices come
> with non-trivial operational and resource costs."
> — heemin32, [PR #498](https://github.com/opensearch-project/search-relevance/pull/498#issuecomment-4736122331)

**Prefer explicit type hierarchies over one class with every subtype's
optional fields, or over flag-driven branching.** When variants exist (e.g.,
manual/UBI/LLM query sets, score-based vs RRF hybrid search), model them as
distinct types rather than combining all parameters and filtering invalid
combinations downstream:

> "Right now QuerySet holds every field every subtype could ever need... can
> you shape it the same way as JudgmentRating → UbiJudgment / LlmJudgment."
> — fen-qin, [PR #454](https://github.com/opensearch-project/search-relevance/pull/454#discussion_r3132741067)

> "The current approach combines all parameter values together and then
> relies on downstream logic to filter out invalid combinations. This makes
> the code harder to follow... Could we instead make the valid combinations
> explicit in the code structure itself?"
> — heemin32, [PR #465](https://github.com/opensearch-project/search-relevance/pull/465#discussion_r3282594975)

**Names must be technically precise and consistent with the rest of the
codebase** — including matching American spelling conventions already in use
(`Judgment`, not `Judgement`), and ensuring a method's name/javadoc matches
what it actually computes:

> "Method computes `1 / rank_of_first_relevant_doc` for a single query. This
> is Reciprocal Rank (RR), not Mean Reciprocal Rank. MRR by definition is the
> mean of RR values across multiple queries... Javadoc states 'Mean
> Reciprocal Rank (MRR)'... which describes RR, not MRR."
> — martin-gaievski, [PR #397](https://github.com/opensearch-project/search-relevance/pull/397#discussion_r2860403143)

Follow existing OpenSearch-ecosystem-wide conventions (e.g., per-class
imports, not wildcard imports) rather than introducing local deviations
(martin-gaievski, [PR #61](https://github.com/opensearch-project/search-relevance/pull/61#discussion_r2132451107)).

---

## 7. Don't repeat yourself — reuse constants and shared logic

Before adding a new constant, size limit, or helper method, check
`PluginConstants`, `SearchRelevanceIndices`, or settings first:

> "We do have the exact same constants in PluginConstants, this is not needed."
> — martin-gaievski, [PR #408](https://github.com/opensearch-project/search-relevance/pull/408#discussion_r2892235443)

> "Can you move it to `PluginConstants.MAX_NUMBER_OF_QUERY_TERMS`, or we
> actually have a query set max size in settings — can we reuse the same
> settings?"
> — fen-qin, [PR #454](https://github.com/opensearch-project/search-relevance/pull/454#discussion_r3132758043)

When two handlers/methods are nearly identical (e.g., parallel POST/PUT
request handling, retry vs. initial-generation scoring logic), extract the
shared piece instead of letting copies diverge:

> "There are lots in common for POST and PUT experiment APIs... besides the
> request diff, there's lots in common, like handleAsyncFailure,
> updateExperiment. Can you reuse the functions?"
> — fen-qin, [PR #120](https://github.com/opensearch-project/search-relevance/pull/120#discussion_r2255315166)

> "Duplication between retryFailedDocs and processQueryTextAsync — these two
> methods share nearly identical logic... Consider extracting the common
> pieces."
> — heemin32, [PR #528](https://github.com/opensearch-project/search-relevance/pull/528#discussion_r3599368264)

---

## 8. Dead code: delete it, don't comment it out or deprecate it

If code is unused, remove it. Commenting it out or slapping `@Deprecated` on
something nothing depends on just adds noise:

> "Remove the code instead of commenting it out."
> — fen-qin, [PR #364](https://github.com/opensearch-project/search-relevance/pull/364#discussion_r2722590707)

> "Can we just remove this method instead of marking it as deprecated? Why
> are you adding `@Deprecated`?"
> — heemin32, [PR #264](https://github.com/opensearch-project/search-relevance/pull/264#discussion_r2446215916)

The exception is **public-facing request parameters** being retired — keep
accepting and silently ignoring them rather than erroring on an unknown
field, to avoid breaking existing callers:

> "`overwriteCache` request parameter is removed. Users sending this param
> will get an unknown-field error." → "We should allow the user to pass this
> parameter but simply ignore it internally to keep backward compatibility."
> — heemin32, [PR #528](https://github.com/opensearch-project/search-relevance/pull/528#issuecomment-4996907819)

---

## 9. New/risky functionality goes behind a feature flag

Substantial new capability should be flagged so it can't ship partially, and
kept opt-in until security/stability concerns are resolved — then flipped to
opt-out once it's proven safe:

> "I think we should put this behind a feature flag so it doesn't get
> released partially."
> — heemin32 / martin-gaievski, [PR #498](https://github.com/opensearch-project/search-relevance/pull/498#issuecomment-4733517211)

> "In 3.1 all features were behind the feature flag with 'opt in' mode,
> partially because of in-process security review... It has been flipped to
> 'opt out' only recently, after all security concerns were addressed."
> — martin-gaievski, [PR #120](https://github.com/opensearch-project/search-relevance/pull/120#issuecomment-3137291798)

A genuinely new public API is expected to come with a design doc or
RFC-style GitHub issue (not a document committed into the repo — publish it
as an issue so the community can comment):

> "A new GitHub issue is the best option, which would look very much like an
> RFC. This approach makes sense for relatively big features with multiple
> components and user-facing changes."
> — martin-gaievski, [PR #120](https://github.com/opensearch-project/search-relevance/pull/120#issuecomment-3140553017)

> "We shouldn't include the design document in the code package. It's better
> to publish it as a GitHub issue so others can review it and leave comments."
> — heemin32, [PR #204](https://github.com/opensearch-project/search-relevance/pull/204#issuecomment-3413241213)

---

## 10. Performance: think about production scale, not just correctness

Before merging a hot-path change, reason about (or measure) its cost under
realistic load, and watch for accidental superlinear behavior — e.g. a
`Comparator` that re-serializes its key on every comparison instead of
computing it once:

> "This comparator is re-serializing — superlinear cost, as
> `Comparator.comparing` does not memoize the sort key... instead serialize
> each rating once and go from O(M log M) to O(M)."
> — martin-gaievski, [PR #456](https://github.com/opensearch-project/search-relevance/pull/456#discussion_r3344951420)

> "Did you measure by chance how long it may take in a real prod scenario,
> e.g. 100M documents, 5K queries, 100 rankings per query? This method
> recomputes synchronously with no guard or cache."
> — martin-gaievski, [PR #456](https://github.com/opensearch-project/search-relevance/pull/456#discussion_r3344971344)

---

## 11. Documentation and changelog discipline

**Every PR gets exactly one CHANGELOG.md entry**, in the right category
(Features / Enhancements / Bug Fixes / Infrastructure / Documentation /
Maintenance) — this is one of the most frequently repeated review comments
from both heemin32 and martin-gaievski:

> "You need to record a single item per PR."
> — heemin32, [PR #542](https://github.com/opensearch-project/search-relevance/pull/542#discussion_r3677213417)

> "Thanks for looking into this issue, the change looks good. Please fix the
> category in CHANGELOG."
> — martin-gaievski, [PR #307](https://github.com/opensearch-project/search-relevance/pull/307#pullrequestreview-3454506816)

Auto-generated release notes don't substitute for a hand-maintained
CHANGELOG.md entry (fen-qin, [PR #378](https://github.com/opensearch-project/search-relevance/pull/378#issuecomment-3843854419)).

**New or changed public APIs need user-facing documentation**, expected as
part of the PR or a prompt follow-up, not left for later:

> "We need to document this API, I expect that you're going to create a
> documentation PR with a description of this API."
> — martin-gaievski, [PR #362](https://github.com/opensearch-project/search-relevance/pull/362#pullrequestreview-3683329530)

---

## 12. Commit and PR hygiene

- **DCO sign-off is enforced** — fix it before expecting a review ("Could you fix DCO failure?" — heemin32, [PR #354](https://github.com/opensearch-project/search-relevance/pull/354)).
- **Squash noisy WIP commits** before merge — a history of "retry", "add wait", "finally ready?" is called out directly:
  > "12 commits with messages like 'retry', 'add wait', 'finally ready?' — this
  > should ideally be squashed before merge to keep main history clean."
  > — heemin32, [PR #340](https://github.com/opensearch-project/search-relevance/pull/340#pullrequestreview-4918880011)
- **Verify BWC test failures, don't wave them off** — "can you check why backwards compatibility tests all failed" (fen-qin, [PR #454](https://github.com/opensearch-project/search-relevance/pull/454#issuecomment-4306641865)) is a recurring ask; a red BWC/rolling-upgrade CI run blocks merge.

---

## Provenance

This file was generated by mining actual PR review comments (via `gh search
prs --commenter <user>` and the GitHub REST API) rather than written from
first principles. If maintainer practice shifts, prefer re-mining recent PRs
over hand-editing stale guidance.
