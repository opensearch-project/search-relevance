# CHANGELOG

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Breaking Changes

### Features
* Added APIs and components to implement running scheduled experiments ([#220](https://github.com/opensearch-project/search-relevance/pull/220))

### Enhancements

### Bug Fixes
* Fixed floating-point precision issues in Hybrid Optimizer weight generation by switching to step-based iteration and rounding, ensuring clean and consistent weight pairs. ([#308](https://github.com/opensearch-project/search-relevance/pull/308))
* Fixed hybrid optimizer experiments stuck in `PROCESSING` after judgment deletion by correcting failure handling. [#292](https://github.com/opensearch-project/search-relevance/pull/292)

### Infrastructure

* Use a system property to control run integ test with security plugin. [#287](https://github.com/opensearch-project/search-relevance/pull/287)

### Documentation
* Updated Developer Guide with instructions for debugging unit tests via Gradle. ([#300](https://github.com/opensearch-project/search-relevance/pull/300))


### Maintenance
* Fixed dependency of `CalculateJudgmentsIT.java` on UBI plugin and created a strategy to handle when plugin does not exist. ([#311](https://github.com/opensearch-project/search-relevance/pull/311))
* Added JDWP debug support for the `test` Gradle task to allow debugging unit tests using `-Dtest.debug=1`. ([#300](https://github.com/opensearch-project/search-relevance/pull/300))
* Fixed duplicate JDWP configuration in the `integTest` Gradle task that caused `Cannot load this JVM TI agent twice` errors when running with `-Dtest.debug=1`. ([#296](https://github.com/opensearch-project/search-relevance/pull/296))
* Removed deprecated `AccessController.doPrivileged()` usage in `JsonUtils` to prevent warnings and ensure compatibility with newer Java versions. ([#307](https://github.com/opensearch-project/search-relevance/pull/307))
* Small cleans up to test classes. ([#288](https://github.com/opensearch-project/search-relevance/pull/288))

### Refactoring
