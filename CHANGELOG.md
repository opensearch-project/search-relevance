# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

### Enhancements
- Add retry endpoint for failed LLM judgments, existingJudgments parameter for rating reuse, and remove broken global cache ([#525](https://github.com/opensearch-project/search-relevance/issues/525))

### Bug Fixes
- Exclude vector fields (`knn_vector`, `rank_features`, `sparse_vector`) from LLM judgment prompts when `contextFields` is not specified ([#565](https://github.com/opensearch-project/search-relevance/pull/565))
- Return correct delete REST status codes and honor querySetSize for sampling ([#542](https://github.com/opensearch-project/search-relevance/pull/542))
- Fix transport-thread blocking in experiment run and query set sampling, and notify listener on protected-index creation failure ([#559](https://github.com/opensearch-project/search-relevance/pull/559))
- Report an invalid UBI events index as a `400` naming the index and the `ubiEventsIndex` parameter instead of a `500` ([#558](https://github.com/opensearch-project/search-relevance/pull/558))

### Infrastructure
- Stabilize flaky restart-upgrade BWC tests by waiting for a stable cluster-manager before cluster-state writes, raising the test cluster's fault-detection/publish tolerances so a transient node stall does not trigger re-election churn, and pinning the test cluster heap to a fixed `2g` per node ([#562](https://github.com/opensearch-project/search-relevance/pull/562))
- Retry the search-config write in the restart-upgrade BWC test until the upgraded cluster settles, so a transient cluster-manager re-election does not fail the mapping migration assertion ([#564](https://github.com/opensearch-project/search-relevance/pull/564))

### Documentation

### Maintenance

### Refactoring
