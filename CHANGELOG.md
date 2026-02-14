# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features
* Introduced dynamic percentile-based relevance thresholding for binary-dependent metrics (Precision, MAP) to replace hard-coded `j > 0` mapping ([#394](https://github.com/opensearch-project/search-relevance/pull/394))

### Enhancements

### Bug Fixes
* Fixed thread pool starvation in LLM judgment processing ([#387](https://github.com/opensearch-project/search-relevance/pull/387))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
