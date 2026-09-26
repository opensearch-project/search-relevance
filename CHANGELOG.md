# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

* Persist per-query OpenSearch search latency (`tookMs`) on experiment evaluation results and pairwise snapshots. `tookMs` is cluster query time (`SearchResponse.getTook()`), not plugin queue time, judgment scoring, or Dashboards round-trip ([#581](https://github.com/opensearch-project/search-relevance/issues/581))

### Enhancements

### Bug Fixes

### Infrastructure

### Documentation

### Maintenance

### Refactoring
