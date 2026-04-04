## Version 3.6.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.6.0

### Features

* Add support for Recall@K, Mean Reciprocal Rank (MRR), and Discounted Cumulative Gain (DCG@K) search evaluation metrics ([#397](https://github.com/opensearch-project/search-relevance/pull/397))
* Introduce dynamic relevance threshold for binary-dependent metrics calculation, replacing the fixed hard-coded threshold with a data-driven approach ([#394](https://github.com/opensearch-project/search-relevance/pull/394))
* Add optional name and description fields to experiments with auto-generated defaults and a PATCH endpoint for updating metadata ([#408](https://github.com/opensearch-project/search-relevance/pull/408))

### Bug Fixes

* Fix thread pool starvation in LLM judgment processing by scheduling query batches sequentially instead of submitting all queries simultaneously ([#387](https://github.com/opensearch-project/search-relevance/pull/387))
* Fix flaky DCG and MRR assertions in integration tests by using wider tolerance for position-sensitive metrics in multi-node clusters ([#427](https://github.com/opensearch-project/search-relevance/pull/427))

### Infrastructure

* Bump actions/download-artifact from 7 to 8 ([#411](https://github.com/opensearch-project/search-relevance/pull/411))
* Bump actions/setup-java from 4 to 5 ([#417](https://github.com/opensearch-project/search-relevance/pull/417))
* Bump actions/upload-artifact from 6 to 7 ([#409](https://github.com/opensearch-project/search-relevance/pull/409))
* Bump aws-actions/configure-aws-credentials from 5 to 6 ([#383](https://github.com/opensearch-project/search-relevance/pull/383))
* Bump gradle-wrapper from 9.3.1 to 9.4.0 ([#420](https://github.com/opensearch-project/search-relevance/pull/420))
* Improve demo scripts usability to support remote OpenSearch instances and execution from any directory ([#415](https://github.com/opensearch-project/search-relevance/pull/415))

### Documentation

* Add release notes for 3.5.0 ([#379](https://github.com/opensearch-project/search-relevance/pull/379))
* Add release notes for 3.6.0 ([#435](https://github.com/opensearch-project/search-relevance/pull/435))

### Maintenance

* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.2.0 to 8.2.1 ([#385](https://github.com/opensearch-project/search-relevance/pull/385))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.2.1 to 8.3.0 ([#418](https://github.com/opensearch-project/search-relevance/pull/418))
* Bump com.google.errorprone:error_prone_annotations from 2.46.0 to 2.47.0 ([#384](https://github.com/opensearch-project/search-relevance/pull/384))
* Bump com.google.errorprone:error_prone_annotations from 2.47.0 to 2.48.0 ([#410](https://github.com/opensearch-project/search-relevance/pull/410))
* Bump com.jayway.jsonpath:json-path from 2.10.0 to 3.0.0 ([#404](https://github.com/opensearch-project/search-relevance/pull/404))
* Bump de.undercouch.download from 5.6.0 to 5.7.0 ([#382](https://github.com/opensearch-project/search-relevance/pull/382))
* Bump org.reflections:reflections from 0.9.12 to 0.10.2 ([#419](https://github.com/opensearch-project/search-relevance/pull/419))

### Refactoring

* Extract reusable BatchedAsyncExecutor abstraction and migrate LlmJudgmentTaskManager and ExperimentTaskManager to use it ([#392](https://github.com/opensearch-project/search-relevance/pull/392))
