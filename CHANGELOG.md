# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features

- Add experiment execution time input signatures (SHA-256 fingerprints of query set, judgments, and search configurations) and `GET /_plugins/_search_relevance/experiments/{id}/validate` for VALID / DRIFTED / UNAVAILABLE drift checks ([#456](https://github.com/opensearch-project/search-relevance/pull/456))

### Enhancements

### Bug Fixes
- Fix race condition in index mapping migration that crashes nodes during rolling upgrades ([#443](https://github.com/opensearch-project/search-relevance/pull/443))

### Infrastructure

### Documentation

### Maintenance

### Refactoring
