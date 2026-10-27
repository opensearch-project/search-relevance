# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

* Persist per-query OpenSearch search latency (`took`) on experiment evaluation results and pairwise snapshots. `took` is cluster query time (`SearchResponse.getTook()`), not plugin queue time, judgment scoring, or Dashboards round-trip ([#581](https://github.com/opensearch-project/search-relevance/issues/581))

### Enhancements
- Make queryText the default placeholder, deprecate SearchText but preserve fallback, and log deprecation of SearchText. ([#613](https://github.com/opensearch-project/search-relevance/pull/613))

### Bug Fixes
* Validate manual judgment rating edits on the server ([#601](https://github.com/opensearch-project/search-relevance/pull/601))

### Infrastructure
- Simplify model-group registration in demo_hybrid_optimizer.sh to use a fixed custom id with idempotent 409 handling, now that ml-commons#5140 is fixed ([#629](https://github.com/opensearch-project/search-relevance/pull/629))
- Suppress javadoc missing-comment warnings for internal classes ([#628](https://github.com/opensearch-project/search-relevance/pull/628))

### Documentation
- Fix the broken search relevance documentation link in the README ([#603](https://github.com/opensearch-project/search-relevance/pull/603))
- Fix a GitHub Pages build failure (missing `copy-curl.html` Jekyll include) and update the scheduled-experiments design doc to match the shipped implementation ([#633](https://github.com/opensearch-project/search-relevance/pull/633))

### Maintenance

### Refactoring
