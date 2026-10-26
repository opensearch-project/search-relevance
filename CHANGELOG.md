# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features

- Add LLM-based query set generation, alongside existing manual and UBI-based creation flows, via a new `QuerySetType` and LLM metadata fields (`modelId`, `sourceIndex`, `contextFields`, `categories`) on `QuerySet` ([#454](https://github.com/opensearch-project/search-relevance/pull/454))

### Enhancements

### Bug Fixes
- Fix race condition in index mapping migration that crashes nodes during rolling upgrades ([#443](https://github.com/opensearch-project/search-relevance/pull/443))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
