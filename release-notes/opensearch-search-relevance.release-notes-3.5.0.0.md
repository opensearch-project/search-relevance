## Version 3.5.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.5.0

### Features
* adds version-based index mapping update support to the Search Relevance plugin [#344](https://github.com/opensearch-project/search-relevance/pull/344)
* LLM Judgement Customized Prompt Template Implementation  [#264](https://github.com/opensearch-project/search-relevance/pull/264)
* Add `_search` endpoint for searching for Search Configurations using OpenSearch DSL  [#372](https://github.com/opensearch-project/search-relevance/pull/372)
* Add `_search` endpoint for searching for Judgments using OpenSearch DSL  [#371](https://github.com/opensearch-project/search-relevance/pull/371)
* Add `_search` endpoint for searching for Query Sets using OpenSearch DSL  [#362](https://github.com/opensearch-project/search-relevance/pull/362)
* Add `_search` endpoint for searching for Experiments using OpenSearch DSL  ([#369](https://github.com/opensearch-project/search-relevance/pull/369))

### Enhancements
* Added better version of ESCI demo dataset that has images and overlaps with our ESCI judgment data.  More compelling demonstrations.  ([#354](https://github.com/opensearch-project/search-relevance/pull/354))
* Add supports to parse custom UBI indexes  [#364](https://github.com/opensearch-project/search-relevance/pull/364)
* Support for adding description in Search Configuration ([#370](https://github.com/opensearch-project/search-relevance/pull/370))

### Bug Fixes
* Added `status` filter support to judgment listing API to prevent incomplete judgment groups from appearing in create experiment workflow ([#304](https://github.com/opensearch-project/search-relevance/pull/304))
* Fix yellow cluster status on single-node clusters ([#329](https://github.com/opensearch-project/search-relevance/issues/329))

### Infrastructure
* Add BWC and Integration tests for index mapping update ([#349](https://github.com/opensearch-project/search-relevance/pull/349))

### Maintenance
* Fix jackson annotations version ([#374](https://github.com/opensearch-project/search-relevance/pull/374))
* Bump actions/checkout from 4 to 6 ([#355](https://github.com/opensearch-project/search-relevance/pull/355))
* Bump actions/checkout from 4 to 6 ([#365](https://github.com/opensearch-project/search-relevance/pull/365))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.1.0 to 8.2.0 ([#375](https://github.com/opensearch-project/search-relevance/pull/375))
* Bump com.google.errorprone:error_prone_annotations from 2.45.0 to 2.46.0 ([#366](https://github.com/opensearch-project/search-relevance/pull/366))
* Bump gradle-wrapper from 9.2.0 to 9.3.0 ([#376](https://github.com/opensearch-project/search-relevance/pull/376))
* Bump io.freefair.gradle:lombok-plugin from 9.1.0 to 9.2.0 ([#368](https://github.com/opensearch-project/search-relevance/pull/368))
* Bump org.json:json from 20250517 to 20251224 ([#361](https://github.com/opensearch-project/search-relevance/pull/361))
* [LINT] Remove extra import that isn't used. ([#352](https://github.com/opensearch-project/search-relevance/pull/352))

