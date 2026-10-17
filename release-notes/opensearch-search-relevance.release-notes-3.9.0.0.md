## Version 3.9.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.9.0

### Features

* Add retry endpoint for failed judgments, `existingJudgements` parameter for reusing prior ratings, and remove global judgment cache ([#528](https://github.com/opensearch-project/search-relevance/pull/528))

### Enhancements

* Replace scheduled job sleep with polling for test results ([#340](https://github.com/opensearch-project/search-relevance/pull/340))
* Exclude vector fields from LLM judgment prompts when `contextFields` is unset to avoid wasting tokens on embeddings ([#565](https://github.com/opensearch-project/search-relevance/pull/565))
* Stop re-fetching judgment documents on every query for Hybrid and Pointwise experiments ([#560](https://github.com/opensearch-project/search-relevance/pull/560))

### Bug Fixes

* Return correct HTTP status codes for delete operations and honor `querySetSize` for PPTSS sampling ([#542](https://github.com/opensearch-project/search-relevance/pull/542))
* Report a missing UBI events index as HTTP 400 and name the `ubiEventsIndex` parameter in the error message ([#558](https://github.com/opensearch-project/search-relevance/pull/558))
* Fix transport-thread blocking and unhandled listener failure in async flows ([#559](https://github.com/opensearch-project/search-relevance/pull/559))

### Infrastructure

* Retry the search-config write in the restart-upgrade BWC test until the upgraded cluster settles ([#564](https://github.com/opensearch-project/search-relevance/pull/564))
* Stabilize flaky restart-upgrade BWC tests by waiting for a stable cluster-manager and raising fault-detection tolerances ([#562](https://github.com/opensearch-project/search-relevance/pull/562))

### Maintenance

* Bump 1password/load-secrets-action from 4.0.1 to 4.1.1 ([#537](https://github.com/opensearch-project/search-relevance/pull/537))
* Bump 1password/load-secrets-action from 4.1.1 to 5.0.0 ([#548](https://github.com/opensearch-project/search-relevance/pull/548))
* Bump actions/checkout from 7.0.0 to 7.0.1 ([#536](https://github.com/opensearch-project/search-relevance/pull/536))
* Bump actions/setup-java from 5.6.0 to 5.7.0 ([#549](https://github.com/opensearch-project/search-relevance/pull/549))
* Bump actions/setup-java from 5.7.0 to 6.0.0 ([#577](https://github.com/opensearch-project/search-relevance/pull/577))
* Bump aws-actions/configure-aws-credentials from 6.2.2 to 6.2.3 ([#538](https://github.com/opensearch-project/search-relevance/pull/538))
* Bump aws-actions/configure-aws-credentials from 6.2.3 to 6.2.4 ([#585](https://github.com/opensearch-project/search-relevance/pull/585))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.8.0 to 8.9.0 ([#550](https://github.com/opensearch-project/search-relevance/pull/550))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.10.1 to 8.10.2 ([#579](https://github.com/opensearch-project/search-relevance/pull/579))
* Bump com.google.guava:guava from 33.6.0-jre to 33.7.1-jre ([#571](https://github.com/opensearch-project/search-relevance/pull/571))
* Bump gradle-wrapper from 9.6.1 to 9.7.0 ([#555](https://github.com/opensearch-project/search-relevance/pull/555))
* Bump gradle-wrapper from 9.7.0 to 9.7.1 ([#572](https://github.com/opensearch-project/search-relevance/pull/572))
* Bump org.javassist:javassist from 3.32.0-GA to 3.33.0-GA ([#578](https://github.com/opensearch-project/search-relevance/pull/578))
* Bump org.json:json from 20260719 to 20260814 ([#570](https://github.com/opensearch-project/search-relevance/pull/570))
