## Version 3.7.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.7.0

### Features

* Onboard z_score normalization and RRF combination to HYBRID_OPTIMIZER ([#465](https://github.com/opensearch-project/search-relevance/pull/465))

### Bug Fixes

* Fix race condition in index mapping migration that crashes nodes during rolling upgrades ([#443](https://github.com/opensearch-project/search-relevance/pull/443))
* Fix typo in log message: "occured" → "occurred" ([#461](https://github.com/opensearch-project/search-relevance/pull/461))
* Fix typo in log message: "occured" → "occurred" ([#462](https://github.com/opensearch-project/search-relevance/pull/462))

### Infrastructure

* Pin actions/github-script to exact commit SHA for reproducible builds ([#467](https://github.com/opensearch-project/search-relevance/pull/467))
* Bump 1password/load-secrets-action from 3 to 4 ([#431](https://github.com/opensearch-project/search-relevance/pull/431))
* Bump actions/github-script from 8 to 9 ([#445](https://github.com/opensearch-project/search-relevance/pull/445))
* Bump codecov/codecov-action from 5 to 6 ([#432](https://github.com/opensearch-project/search-relevance/pull/432))

### Maintenance

* Add support for Jackson 3.x release line ([#469](https://github.com/opensearch-project/search-relevance/pull/469))
* Bump com.google.code.gson:gson from 2.13.1 to 2.14.0 ([#457](https://github.com/opensearch-project/search-relevance/pull/457))
* Bump com.google.errorprone:error_prone_annotations from 2.48.0 to 2.49.0 ([#446](https://github.com/opensearch-project/search-relevance/pull/446))
* Bump com.google.guava:guava from 33.4.8-jre to 33.6.0-jre ([#452](https://github.com/opensearch-project/search-relevance/pull/452))
* Bump gradle-wrapper from 9.4.0 to 9.4.1 ([#423](https://github.com/opensearch-project/search-relevance/pull/423))
* Bump gradle-wrapper from 9.4.1 to 9.5.0 ([#464](https://github.com/opensearch-project/search-relevance/pull/464))
* Bump io.freefair.gradle:lombok-plugin from 9.2.0 to 9.5.0 ([#463](https://github.com/opensearch-project/search-relevance/pull/463))
* Bump org.javassist:javassist from 3.30.2-GA to 3.31.0-GA ([#453](https://github.com/opensearch-project/search-relevance/pull/453))
