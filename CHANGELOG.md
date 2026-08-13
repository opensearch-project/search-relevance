# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

### Enhancements
- Add retry endpoint for failed LLM judgments, existingJudgments parameter for rating reuse, and remove broken global cache ([#525](https://github.com/opensearch-project/search-relevance/issues/525))

### Bug Fixes
- Return correct delete REST status codes and honor querySetSize for sampling ([#542](https://github.com/opensearch-project/search-relevance/pull/542))
- Fix transport-thread blocking in experiment run and query set sampling, and notify listener on protected-index creation failure ([#559](https://github.com/opensearch-project/search-relevance/pull/559))
- Report an invalid UBI events index as a `400` naming the index and the `ubiEventsIndex` parameter instead of a `500` ([#558](https://github.com/opensearch-project/search-relevance/pull/558))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
