# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features
* Introduced dynamic percentile-based relevance thresholding for binary-dependent metrics (Precision, MAP) to replace hard-coded `j > 0` mapping ([#394](https://github.com/opensearch-project/search-relevance/pull/394))

### Enhancements

### Bug Fixes
* Fixed thread pool starvation in LLM judgment processing ([#387](https://github.com/opensearch-project/search-relevance/pull/387))
* Ensure evaluation results are stored for zero-search-result (ZSR) queries by writing zero-valued metrics instead of skipping the record, preventing empty experiments ([#398](https://github.com/opensearch-project/search-relevance/pull/398))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
* Extract reusable BatchedAsyncExecutor; migrate LlmJudgmentTaskManager and ExperimentTaskManager to use it ([#392](https://github.com/opensearch-project/search-relevance/pull/392))
