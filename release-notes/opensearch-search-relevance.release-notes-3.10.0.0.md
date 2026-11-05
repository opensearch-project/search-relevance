## Version 3.10.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.10.0

### Features

* Add LLM-as-a-judge demo script with local Ollama and OpenAI connector support ([#322](https://github.com/opensearch-project/search-relevance/pull/322))
* Persist per-query search latency (`tookMs`) on experiment results for performance visibility ([#593](https://github.com/opensearch-project/search-relevance/pull/593))

### Enhancements

* Make `%queryText%` the default query placeholder and deprecate `%SearchText%` with backward-compatible fallback ([#613](https://github.com/opensearch-project/search-relevance/pull/613))
* Validate manual judgment rating edits on the server, rejecting non-finite and out-of-range values that previously broke metric evaluation ([#601](https://github.com/opensearch-project/search-relevance/pull/601))
* Simplify model-group registration in `demo_hybrid_optimizer.sh` using idempotent register pattern ([#629](https://github.com/opensearch-project/search-relevance/pull/629))

### Bug Fixes

* Force jspecify 1.0.1 to fix dependency version conflict ([#602](https://github.com/opensearch-project/search-relevance/pull/602))
* Fix GitHub Pages build by removing unsupported Liquid include tags and update design doc to reflect shipped implementation ([#633](https://github.com/opensearch-project/search-relevance/pull/633))

### Infrastructure

* Suppress javadoc missing-comment warnings for internal classes while preserving real error detection ([#628](https://github.com/opensearch-project/search-relevance/pull/628))

### Documentation

* Add KNOWN_ISSUES.md documenting known issues and workarounds ([#635](https://github.com/opensearch-project/search-relevance/pull/635))
* Fix broken search relevance documentation link in the README ([#603](https://github.com/opensearch-project/search-relevance/pull/603))
* Document repository best practices for contributors and maintainers ([#621](https://github.com/opensearch-project/search-relevance/pull/621))

### Maintenance

* Bump actions/download-artifact from 8.0.1 to 8.0.2 ([#614](https://github.com/opensearch-project/search-relevance/pull/614))
* Bump actions/setup-java from 6.0.0 to 6.0.1 ([#587](https://github.com/opensearch-project/search-relevance/pull/587))
* Bump actions/setup-java from 6.0.1 to 6.1.0 ([#626](https://github.com/opensearch-project/search-relevance/pull/626))
* Bump actions/upload-artifact from 7.0.1 to 7.0.2 ([#616](https://github.com/opensearch-project/search-relevance/pull/616))
* Bump aws-actions/configure-aws-credentials from 6.2.4 to 6.3.0 ([#594](https://github.com/opensearch-project/search-relevance/pull/594))
* Bump codecov/codecov-action from 7.0.0 to 7.1.1 ([#595](https://github.com/opensearch-project/search-relevance/pull/595))
* Bump com.diffplug.spotless:spotless-plugin-gradle from 8.10.2 to 8.10.4 ([#615](https://github.com/opensearch-project/search-relevance/pull/615))
* Bump com.google.errorprone:error_prone_annotations from 2.50.0 to 2.51.0 ([#631](https://github.com/opensearch-project/search-relevance/pull/631))
* Bump com.google.guava:guava from 33.7.1-jre to 33.7.2-jre ([#611](https://github.com/opensearch-project/search-relevance/pull/611))
* Bump de.thetaphi:forbiddenapis from 3.10 to 3.11 ([#606](https://github.com/opensearch-project/search-relevance/pull/606))
* Bump gradle-wrapper from 9.7.1 to 9.8.0 ([#605](https://github.com/opensearch-project/search-relevance/pull/605))
* Bump gradle-wrapper from 9.8.0 to 9.8.1 ([#617](https://github.com/opensearch-project/search-relevance/pull/617))
* Bump gradle-wrapper from 9.8.1 to 9.8.2 ([#632](https://github.com/opensearch-project/search-relevance/pull/632))
* Bump io.freefair.gradle:lombok-plugin from 9.5.0 to 9.8.0 ([#608](https://github.com/opensearch-project/search-relevance/pull/608))
* Bump io.freefair.gradle:lombok-plugin from 9.8.0 to 9.8.2 ([#625](https://github.com/opensearch-project/search-relevance/pull/625))
