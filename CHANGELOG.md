# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

### Enhancements
- Register the scheduled experiment jobs and history indices as system indices. ([#638](https://github.com/opensearch-project/search-relevance/pull/638))

### Bug Fixes

### Infrastructure
- Add SPDX license header checker and missing headers ([#574](https://github.com/opensearch-project/search-relevance/pull/574))
- Attach a normalization search pipeline to the `hybrid_query_1` search configuration in `demo.sh` so evaluations using it no longer produce NDCG values above 1 from repeated unnormalized hybrid hits ([#639](https://github.com/opensearch-project/search-relevance/pull/639))
- Rescale the ESCI demo judgments (`esci_us_judgments.json`) from 0/1/10/100 to a graded 0–3 scale so DCG@K no longer overflows and demo experiments evaluate every query ([#640](https://github.com/opensearch-project/search-relevance/pull/640))

### Documentation

### Maintenance

### Refactoring
* Run search relevance index operations as the plugin subject instead of stashing the thread context ([#612](https://github.com/opensearch-project/search-relevance/pull/612))
* Stop clearing the thread context in the scheduled experiment job runner, as job-scheduler now runs every job in a clean context ([#643](https://github.com/opensearch-project/search-relevance/pull/643))
