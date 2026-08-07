# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Features

### Enhancements
- Add retry endpoint for failed LLM judgments, existingJudgments parameter for rating reuse, and remove broken global cache ([#525](https://github.com/opensearch-project/search-relevance/issues/525))

### Bug Fixes
- Return correct delete REST status codes and honor querySetSize for sampling ([#542](https://github.com/opensearch-project/search-relevance/pull/542))
- Resolve UBI index expressions the way the plugin's own search does and report an invalid UBI index as a client error ([#551](https://github.com/opensearch-project/search-relevance/pull/551))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
