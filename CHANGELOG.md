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

### Documentation
- Fix the broken search relevance documentation link in the README ([#603](https://github.com/opensearch-project/search-relevance/pull/603))

### Maintenance

### Refactoring
* Run search relevance index operations as the plugin subject instead of stashing the thread context ([#612](https://github.com/opensearch-project/search-relevance/pull/612))
