# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

* Persist per-query OpenSearch search latency (`took`) on experiment evaluation results and pairwise snapshots. `took` is cluster query time (`SearchResponse.getTook()`), not plugin queue time, judgment scoring, or Dashboards round-trip ([#581](https://github.com/opensearch-project/search-relevance/issues/581))

### Enhancements
- Make queryText the default placeholder, deprecate SearchText but preserve fallback, and log deprecation of SearchText. ([#613](https://github.com/opensearch-project/search-relevance/pull/613))

### Bug Fixes
* Validate manual judgment rating edits on the server ([#601](https://github.com/opensearch-project/search-relevance/pull/601))
* Add missing `slf4j-api` runtime dependency so LLM_JUDGMENT no longer crashes the node with `NoClassDefFoundError` on the first completed remote-model predict call ([#322](https://github.com/opensearch-project/search-relevance/pull/322))

### Infrastructure
- Simplify model-group registration in demo_hybrid_optimizer.sh to use a fixed custom id with idempotent 409 handling, now that ml-commons#5140 is fixed ([#629](https://github.com/opensearch-project/search-relevance/pull/629))
- Suppress javadoc missing-comment warnings for internal classes ([#628](https://github.com/opensearch-project/search-relevance/pull/628))

### Documentation
- Fix the broken search relevance documentation link in the README ([#603](https://github.com/opensearch-project/search-relevance/pull/603))
- Fix a GitHub Pages build failure by removing references to the missing `copy-curl.html` Jekyll include ([#633](https://github.com/opensearch-project/search-relevance/pull/633))
- Add `demo_llm_as_judge.sh`, a demo script for the LLM-as-a-judge feature supporting OpenAI or a local Ollama model ([#322](https://github.com/opensearch-project/search-relevance/pull/322))
- Add `KNOWN_ISSUES.md` with a workaround for the experiment index `isScheduled` mapping conflict after upgrading from 3.3 via 3.4 or 3.5 ([#635](https://github.com/opensearch-project/search-relevance/pull/635))

### Maintenance

### Refactoring
