## Version 3.1.0.0 Release Notes

Compatible with OpenSearch 3.1.0

### Features

* Added new experiment type for hybrid search ([#26](https://github.com/opensearch-project/search-relevance/pull/26))
* Added feature flag for search relevance workbench ([#34](https://github.com/opensearch-project/search-relevance/pull/34))
* Added validation for hybrid query structure ([#40](https://github.com/opensearch-project/search-relevance/pull/40))
* Add support for importing judgments created externally from SRW ([#42](https://github.com/opensearch-project/search-relevance/pull/42))
* Changing URL for plugin APIs to /_plugin/_search_relevance [backend] ([#62](https://github.com/opensearch-project/search-relevance/pull/62))
* Add stats API ([#63](https://github.com/opensearch-project/search-relevance/pull/63))

### Enhancements

* Extend data model to adopt different experiment options/parameters ([#29](https://github.com/opensearch-project/search-relevance/issues/29))

### Bug Fixes

* Update demo setup to be include ubi and ecommerce data sets and run in OS 3.1 ([#10](https://github.com/opensearch-project/search-relevance/issues/10))
* Build search request with normal parsing and wrapper query ([#22](https://github.com/opensearch-project/search-relevance/pull/22))
* Change aggregation field from `action_name.keyword` to `action_name` to fix implicit judgments calculation ([#15](https://github.com/opensearch-project/search-relevance/issues/15))
* Fix COEC calculation: introduce rank in ClickthroughRate class, fix bucket size for positional aggregation, correct COEC calculation ([#23](https://github.com/opensearch-project/search-relevance/issues/23))
* LLM Judgment Processor Improvement ([#27](https://github.com/opensearch-project/search-relevance/pull/27))
* Deal with experiment processing when no experiment variants exist ([#45](https://github.com/opensearch-project/search-relevance/pull/45))
* Enable Search Relevance backend plugin as part of running demo scripts ([#60](https://github.com/opensearch-project/search-relevance/pull/60))
* Move from Judgments being "scores" to Judgments being "ratings" ([#64](https://github.com/opensearch-project/search-relevance/pull/64))
* Added lazy index creation for all APIs ([#65](https://github.com/opensearch-project/search-relevance/pull/65))
* Extend hybrid search optimizer demo script to use models ([#69](https://github.com/opensearch-project/search-relevance/pull/69))
* Set limit for number of fields programmatically during index creation ([#74](https://github.com/opensearch-project/search-relevance/pull/74))
* Change model for Judgment entity ([#77](https://github.com/opensearch-project/search-relevance/pull/77))
* Fix judgment handling for implicit judgments to be aligned with data model for Judgment again ([#93](https://github.com/opensearch-project/search-relevance/pull/93))
* Change model for Experiment and Evaluation Result entities ([#99](https://github.com/opensearch-project/search-relevance/pull/99))
* Refactor and fix LLM judgment duplication issue ([#98](https://github.com/opensearch-project/search-relevance/pull/98))
* Add text validation and query set file size check ([#116](https://github.com/opensearch-project/search-relevance/pull/116))
* Fixed missing variants in Hybrid Optimizer ([#124](https://github.com/opensearch-project/search-relevance/pull/124))

### Infrastructure

* Run integTests with security as a PR check ([#136](https://github.com/opensearch-project/search-relevance/pull/136))
* Realistic test data set based on ESCI (products, queries, judgements) ([#70](https://github.com/opensearch-project/search-relevance/pull/70))
