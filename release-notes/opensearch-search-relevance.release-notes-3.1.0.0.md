## Version 3.1.0.0 Release Notes

Compatible with OpenSearch 3.1.0

### Added
- Added new experiment type for hybrid search ([#26](https://github.com/opensearch-project/search-relevance/pull/26))
- Added feature flag for search relevance workbench ([#34](https://github.com/opensearch-project/search-relevance/pull/34))
- [Enhancement] Extend data model to adopt different experiment options/parameters ([#29](https://github.com/opensearch-project/search-relevance/issues/29))
- Added validation for hybrid query structure ([#40](https://github.com/opensearch-project/search-relevance/pull/40))
- [Enhancement] Add support for importing judgments created externally from SRW.  ([#42](https://github.com/opensearch-project/search-relevance/pull/42)
- Changing URL for plugin APIs to /_plugin/_search_relevance [backend] ([#62](https://github.com/opensearch-project/search-relevance/pull/62)
- Added lazy index creation for all APIs ([#65](https://github.com/opensearch-project/search-relevance/pull/65)
- Realistic test data set based on ESCI (products, queries, judgements) ([#70](https://github.com/opensearch-project/search-relevance/pull/70)
- [Stats] Add stats API ([#63](https://github.com/opensearch-project/search-relevance/pull/63)))

### Fixed
- Update demo setup to be include ubi and ecommerce data sets and run in OS 3.1 ([#10](https://github.com/opensearch-project/search-relevance/issues/10))
- Build search request with normal parsing and wrapper query ([#22](https://github.com/opensearch-project/search-relevance/pull/22))
- Change aggregation field from `action_name.keyword` to `action_name` to fix implicit judgments calculation ([#15](https://github.com/opensearch-project/search-relevance/issues/10)).
- Fix COEC calculation: introduce rank in ClickthroughRate class, fix bucket size for positional aggregation, correct COEC claculation ([#23](https://github.com/opensearch-project/search-relevance/issues/23)).
- LLM Judgment Processor Improvement ([#27](https://github.com/opensearch-project/search-relevance/pull/27))
- Deal with experiment processing when no experiment variants exist. ([#45](https://github.com/opensearch-project/search-relevance/pull/45))
- Extend the `src/test/demo.sh` script to support pointwise and hybrid experiments.
- Enable Search Relevance backend plugin as part of running demo scripts. ([#60](https://github.com/opensearch-project/search-relevance/pull/60))
- Move from Judgments being "scores" to Judgments being "ratings".  ([#64](https://github.com/opensearch-project/search-relevance/pull/64))
- Extend hybrid search optimizer demo script to use models. ([#69](https://github.com/opensearch-project/search-relevance/pull/69))
- Set limit for number of fields programmatically during index creation ([#74](https://github.com/opensearch-project/search-relevance/pull/74)
- Change model for Judgment entity ([#77](https://github.com/opensearch-project/search-relevance/pull/77)
- Fix judgment handling for implicit judgments to be aligned with data model for Judgment again ([#93](https://github.com/opensearch-project/search-relevance/pull/93)
- Change model for Experiment and Evaluation Result entities: ([#99](https://github.com/opensearch-project/search-relevance/pull/99))
- Refactor and fix LLM judgment duplication issue ([#98](https://github.com/opensearch-project/search-relevance/pull/98)))
- add text validation and query set file size check ([#116](https://github.com/opensearch-project/search-relevance/pull/116)))
