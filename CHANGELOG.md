# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

### Enhancements
- Add retry endpoint for failed LLM judgments, existingJudgments parameter for rating reuse, and remove broken global cache ([#525](https://github.com/opensearch-project/search-relevance/issues/525))

### Bug Fixes
- Return correct delete REST status codes and honor querySetSize for sampling ([#542](https://github.com/opensearch-project/search-relevance/pull/542))
- Fix transport-thread blocking in experiment run and query set sampling, and notify listener on protected-index creation failure ([#PR](https://github.com/opensearch-project/search-relevance/pull/PR))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
