# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features
* Added APIs and components to implement running scheduled experiments ([#220](https://github.com/opensearch-project/search-relevance/pull/220))

### Enhancements

### Bug Fixes
* Fixed hybrid optimizer experiments stuck in `PROCESSING` after judgment deletion by correcting failure handling. [#292](https://github.com/opensearch-project/search-relevance/pull/292)

### Infrastructure

* Use a system property to control run integ test with security plugin. [#287](https://github.com/opensearch-project/search-relevance/pull/287)

### Documentation
* Updated Developer Guide with instructions for debugging unit tests via Gradle. ([#300](https://github.com/opensearch-project/search-relevance/pull/300))


### Maintenance
* Added JDWP debug support for the `test` Gradle task to allow debugging unit tests using `-Dtest.debug=1`. ([#300](https://github.com/opensearch-project/search-relevance/pull/300))
* Removed deprecated `AccessController.doPrivileged()` usage in `JsonUtils` to prevent warnings and ensure compatibility with newer Java versions. ([#307](https://github.com/opensearch-project/search-relevance/pull/307))


### Refactoring
