# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features

### Enhancements
* Added fields to experiment results to facilitate Dashboard visualization ([#174](https://github.com/opensearch-project/search-relevance/pull/174))
* Added tasks scheduling and management mechanism for hybrid optimizer experiments ([#139](https://github.com/opensearch-project/search-relevance/pull/139))
* Enabled tasks scheduling for pointwise experiments ([#167](https://github.com/opensearch-project/search-relevance/pull/167))

### Bug Fixes
* Bug fix on rest APIs error status for creations ([#176](https://github.com/opensearch-project/search-relevance/pull/176))
* Fixed pipeline parameter being ignored in pairwise metrics processing for hybrid search queries ([#187](https://github.com/opensearch-project/search-relevance/pull/187))
* Added queryText and referenceAnswer text validation from manual input ([#177](https://github.com/opensearch-project/search-relevance/pull/177))

### Infrastructure
* Added end to end integration tests for experiments ([#154](https://github.com/opensearch-project/search-relevance/pull/154))
* Enabled tasks scheduling for llm judgments ([#166](https://github.com/opensearch-project/search-relevance/pull/166))
* Upgrade gradle to 8.14 and higher JDK version to 24 ([#188](https://github.com/opensearch-project/search-relevance/pull/188))

### Documentation

### Maintenance

### Refactoring
