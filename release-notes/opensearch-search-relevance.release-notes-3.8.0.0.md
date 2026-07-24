## Version 3.8.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.8.0

### Features

* Implement Mustache template support for search queries, enabling native OpenSearch ScriptService-based templating alongside legacy `%SearchText%` placeholders ([#342](https://github.com/opensearch-project/search-relevance/pull/342))
* Implement referential integrity validation for Search Relevance entities, ensuring referenced resources exist before creation or update operations proceed ([#360](https://github.com/opensearch-project/search-relevance/pull/360))
* Add experiment execution time input signatures (SHA-256 fingerprints of query set, judgments, and search configurations) and `GET /_plugins/_search_relevance/experiments/{id}/validate` for VALID / DRIFTED / UNAVAILABLE drift checks ([#456](https://github.com/opensearch-project/search-relevance/pull/456))
* Make LLM judgment generation provider-neutral, supporting any LLM provider through ml-commons connectors while maintaining backward compatibility with OpenAI-compatible connectors ([#515](https://github.com/opensearch-project/search-relevance/pull/515))
* Report LLM judgment success/failure counts and failed queries in judgment metadata, making unrated documents visible instead of silently dropping them ([#521](https://github.com/opensearch-project/search-relevance/pull/521))

### Enhancements

* Onboard new backport-pr reusable GitHub workflow to replace obsolete backport-related workflows ([#513](https://github.com/opensearch-project/search-relevance/pull/513))
* Onboard code diff analyzer/reviewer and issue dedupe workflows ([#520](https://github.com/opensearch-project/search-relevance/pull/520))
* Optimize RBO calculation by maintaining prefix sets incrementally, reducing complexity from O(n²) to O(n) ([#500](https://github.com/opensearch-project/search-relevance/pull/500))
* Optimize Frequency Weighted similarity by replacing O(n²) list scan with HashSet-based single-pass computation ([#502](https://github.com/opensearch-project/search-relevance/pull/502))

### Bug Fixes

* Fix experiment search requests wrapping query in base64-encoded wrapper, which broke search pipeline field path resolution for processors like `ml_inference` ([#490](https://github.com/opensearch-project/search-relevance/pull/490))
* Fix BWC SearchConfigMapping tests by creating referenced index before search config creation to satisfy referential integrity validation ([#497](https://github.com/opensearch-project/search-relevance/pull/497))

### Maintenance
* Update updateVersion task and fix BWC version properties ([#475](https://github.com/opensearch-project/search-relevance/pull/475))
* Bump 1password/load-secrets-action from 4.0.0 to 4.0.1 ([#493](https://github.com/opensearch-project/search-relevance/pull/493))
* Bump actions/checkout from 6.0.3 to 7.0.0 ([#504](https://github.com/opensearch-project/search-relevance/pull/504))
* Bump actions/setup-java from 5.2.0 to 5.3.0 ([#505](https://github.com/opensearch-project/search-relevance/pull/505))
* Bump actions/setup-java from 5.3.0 to 5.4.0 ([#517](https://github.com/opensearch-project/search-relevance/pull/517))
* Bump actions/setup-java from 5.4.0 to 5.5.0 ([#526](https://github.com/opensearch-project/search-relevance/pull/526))
* Bump actions/setup-java from 5.5.0 to 5.6.0 ([#530](https://github.com/opensearch-project/search-relevance/pull/530))
* Bump aws-actions/configure-aws-credentials from 6.2.0 to 6.2.1 ([#516](https://github.com/opensearch-project/search-relevance/pull/516))
* Bump aws-actions/configure-aws-credentials from 6.2.1 to 6.2.2 ([#527](https://github.com/opensearch-project/search-relevance/pull/527))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.6.0 to 8.7.0 ([#507](https://github.com/opensearch-project/search-relevance/pull/507))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.7.0 to 8.8.0 ([#524](https://github.com/opensearch-project/search-relevance/pull/524))
* Bump com.google.errorprone:error_prone_annotations from 2.49.0 to 2.50.0 ([#491](https://github.com/opensearch-project/search-relevance/pull/491))
* Bump gradle-wrapper from 9.5.1 to 9.6.0 ([#506](https://github.com/opensearch-project/search-relevance/pull/506))
* Bump gradle-wrapper from 9.6.0 to 9.6.1 ([#518](https://github.com/opensearch-project/search-relevance/pull/518))
* Bump opensearch-project/opensearch-build/.github/workflows/get-ci-image-tag.yml ([#486](https://github.com/opensearch-project/search-relevance/pull/486))
* Bump org.javassist:javassist from 3.31.0-GA to 3.32.0-GA ([#508](https://github.com/opensearch-project/search-relevance/pull/508))
* Bump org.json:json from 20260522 to 20260719 ([#529](https://github.com/opensearch-project/search-relevance/pull/529))
