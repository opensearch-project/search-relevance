# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

* Persist per-query OpenSearch search latency (`took`) on experiment evaluation results and pairwise snapshots. `took` is cluster query time (`SearchResponse.getTook()`), not plugin queue time, judgment scoring, or Dashboards round-trip ([#581](https://github.com/opensearch-project/search-relevance/issues/581))

### Enhancements
- Make queryText the default placeholder, deprecate SearchText but preserve fallback, and log deprecation of SearchText. ([#613](https://github.com/opensearch-project/search-relevance/pull/613))

### Bug Fixes
* Validate manual judgment rating edits on the server ([#601](https://github.com/opensearch-project/search-relevance/pull/601))
* Add missing `slf4j-api` runtime dependency so LLM_JUDGMENT no longer crashes the node with `NoClassDefFoundError` on the first completed remote-model predict call

### Infrastructure

### Documentation
- Fix the broken search relevance documentation link in the README ([#603](https://github.com/opensearch-project/search-relevance/pull/603))
- Add `demo_llm_as_judge.sh`, a demo script for the LLM-as-a-judge feature supporting OpenAI or a local Ollama model ([#322](https://github.com/opensearch-project/search-relevance/pull/322))

### Maintenance

### Refactoring
