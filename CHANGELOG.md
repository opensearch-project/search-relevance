# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Added
- Added new experiment type for hybrid search ([#10](https://github.com/opensearch-project/search-relevance/pull/26))
- Added feature flag for search relevance workbench ([#34](https://github.com/opensearch-project/search-relevance/pull/34))

### Removed

### Fixed

 - Update demo setup to be include ubi and ecommerce data sets and run in OS 3.1 ([#10](https://github.com/opensearch-project/search-relevance/issues/10))
 - Build search request with normal parsing and wrapper query ([#22](https://github.com/opensearch-project/search-relevance/pull/22))
 - Change aggregation field from `action_name.keyword` to `action_name` to fix implicit judgments calculation ([#15](https://github.com/opensearch-project/search-relevance/issues/10)).
 - Fix COEC calculation: introduce rank in ClickthroughRate class, fix bucket size for positional aggregation, correct COEC claculation ([#23](https://github.com/opensearch-project/search-relevance/issues/23)).
 - LLM Judgment Processor Improvement ([#27](https://github.com/opensearch-project/search-relevance/pull/27))

### Security
