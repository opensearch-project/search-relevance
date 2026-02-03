## Version 3.5.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.5.0

### Features
* [SRW] LLM Judge Dynamic Template Backend ([#264](https://github.com/opensearch-project/search-relevance/pull/264))
* Introduce a _search end point on Experiment API ([#369](https://github.com/opensearch-project/search-relevance/pull/369))
* Introduce a _search end point on Query Set API ([#362](https://github.com/opensearch-project/search-relevance/pull/362))
* Add new _search endpoint for searching Search Configurations ([#372](https://github.com/opensearch-project/search-relevance/pull/372))
* Introduce _search endpoint for judgment that filters out ratings ([#371](https://github.com/opensearch-project/search-relevance/pull/371))

### Enhancements
* [Enhancement] Add description in Search Configuration ([#370](https://github.com/opensearch-project/search-relevance/pull/370))
* Support customized ubi indexes with validation ([#364](https://github.com/opensearch-project/search-relevance/pull/364))

### Bug Fixes
* [BUG] Incomplete judgment ratings groups were shown during selection add status filter to List Judgments API to return only relevant judgment states ([#304](https://github.com/opensearch-project/search-relevance/pull/304))
* Fix jackson annotations version ([#374](https://github.com/opensearch-project/search-relevance/pull/374))

### Infrastructure
* Add BWC and Integration tests for index mapping update ([#349](https://github.com/opensearch-project/search-relevance/pull/349))
* Smaller dataset that aligns with nightly playground esci dataset and attribute naming ([#354](https://github.com/opensearch-project/search-relevance/pull/354))

### Maintenance
* Bump actions/checkout from 4 to 6 ([#355](https://github.com/opensearch-project/search-relevance/pull/355))
* Bump actions/checkout from 4 to 6 ([#365](https://github.com/opensearch-project/search-relevance/pull/365))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.1.0 to 8.2.0 ([#375](https://github.com/opensearch-project/search-relevance/pull/375))
* Bump com.google.errorprone:error_prone_annotations from 2.45.0 to 2.46.0 ([#366](https://github.com/opensearch-project/search-relevance/pull/366))
* Bump gradle-wrapper from 9.2.0 to 9.3.0 ([#376](https://github.com/opensearch-project/search-relevance/pull/376))
* Bump io.freefair.gradle:lombok-plugin from 9.1.0 to 9.2.0 ([#368](https://github.com/opensearch-project/search-relevance/pull/368))
* Bump org.json:json from 20250517 to 20251224 ([#361](https://github.com/opensearch-project/search-relevance/pull/361))
* [LINT] Remove extra import that isn't used. ([#352](https://github.com/opensearch-project/search-relevance/pull/352))