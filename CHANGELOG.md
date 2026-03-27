# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features
* Add optional name and description fields to experiments ([#408](https://github.com/opensearch-project/search-relevance/pull/408))
* Introduced dynamic percentile-based relevance thresholding for binary-dependent metrics (Precision, MAP) to replace hard-coded `j > 0` mapping ([#394](https://github.com/opensearch-project/search-relevance/pull/394))
* Added additional search evaluation metrics: Recall@K, Mean Reciprocal Rank (MRR), and Discounted Cumulative Gain (DCG@K) ([#397](https://github.com/opensearch-project/search-relevance/pull/397))

### Enhancements
* Allow demo scripts to be run from any directory and point to any OpenSearch server. ([#415](https://github.com/opensearch-project/search-relevance/pull/415))

### Bug Fixes
* Fixed thread pool starvation in LLM judgment processing ([#387](https://github.com/opensearch-project/search-relevance/pull/387))

### Infrastructure
* Fix flaky DCG and MRR assertions in integration tests ([#427](https://github.com/opensearch-project/search-relevance/pull/427))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
* Extract reusable BatchedAsyncExecutor; migrate LlmJudgmentTaskManager and ExperimentTaskManager to use it ([#392](https://github.com/opensearch-project/search-relevance/pull/392))
