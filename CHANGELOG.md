# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features
- adds version-based index mapping update support to the Search Relevance plugin [#344](https://github.com/opensearch-project/search-relevance/pull/344)
- LLM Judgement Customized Prompt Template Implementation  [#264](https://github.com/opensearch-project/search-relevance/pull/264)
- Add `_search` endpoint for searching for Search Configurations using OpenSearch DSL  [#372](https://github.com/opensearch-project/search-relevance/pull/372)
- Add `_search` endpoint for searching for Judgments using OpenSearch DSL  [#371](https://github.com/opensearch-project/search-relevance/pull/371)

### Enhancements
- Add BWC and Integration Tests for Index Mapping Update with Schema Version [#349](https://github.com/opensearch-project/search-relevance/pull/349)
- Added better version of ESCI demo dataset that has images and overlaps with our ESCI judgment data.  More compelling demonstrations.  ([#354](https://github.com/opensearch-project/search-relevance/pull/354))

* Add `/experiments/_search` endpoint to allow searching for experiments using OpenSearch query DSL ([#369](https://github.com/opensearch-project/search-relevance/pull/369))

### Bug Fixes
- Added `status` filter support to judgment listing API to prevent incomplete judgment groups from appearing in create experiment workflow ([#304](https://github.com/opensearch-project/search-relevance/pull/304))
- Fix yellow cluster status on single-node clusters ([#329](https://github.com/opensearch-project/search-relevance/issues/329))

### Infrastructure

### Documentation

### Maintenance
- Fix jackson annotations version ([#374](https://github.com/opensearch-project/search-relevance/pull/374))

### Refactoring
