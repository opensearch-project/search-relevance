# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features

- Add experiment execution time input signatures (SHA-256 fingerprints of query set, judgments, and search configurations) and `GET /_plugins/_search_relevance/experiments/{id}/validate` for VALID / DRIFTED / UNAVAILABLE drift checks ([#456](https://github.com/opensearch-project/search-relevance/pull/456))

### Enhancements
- Optimize Rank-Biased Overlap (RBO) calculation from O(n²) to O(n) by maintaining prefix sets incrementally ([#499](https://github.com/opensearch-project/search-relevance/issues/499))
- Optimize Frequency Weighted similarity calculation by replacing the O(n²) `listB.contains` scan with HashSet membership and single-pass union/intersection accumulation ([#502](https://github.com/opensearch-project/search-relevance/pull/502))

### Bug Fixes
- Implement referential integrity validation for search configurations, experiments, and judgments ([#360](https://github.com/opensearch-project/search-relevance/pull/360))
- Preserve the original query structure when building experiment search requests so search pipeline request processors can resolve field paths instead of failing on a wrapped query ([#490](https://github.com/opensearch-project/search-relevance/pull/490))

### Infrastructure
- Update updateVersion task and fix BWC version properties ([#475](https://github.com/opensearch-project/search-relevance/pull/475))
- Fix BWC tests by creating the referenced index before creating the search configuration ([#497](https://github.com/opensearch-project/search-relevance/pull/497))

### Documentation

### Maintenance

### Refactoring
