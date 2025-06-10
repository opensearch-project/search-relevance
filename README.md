[![Build and Test Search Relevance](https://github.com/opensearch-project/search-relevance/actions/workflows/CI.yml/badge.svg)](https://github.com/opensearch-project/search-relevance/actions/workflows/CI.yml)
[![codecov](https://codecov.io/gh/opensearch-project/search-relevance/branch/main/graph/badge.svg?token=PYQO2GW39S)](https://codecov.io/gh/opensearch-project/search-relevance)
[![Chat](https://img.shields.io/badge/chat-on%20slack-purple)](https://opensearch.slack.com/archives/C08HKHQMBN1)
[![Chat](https://img.shields.io/badge/chat-on%20forums-blue)](https://forum.opensearch.org)
![PRs welcome!](https://img.shields.io/badge/PRs-welcome!-success)

# OpenSearch Search Relevance
In search applications, tuning relevance is a constant, iterative exercise to bring the right search results to your end users. The tooling in this repository aims to help the search relevance engineer and business user create the best search experience possible for application users without hiding internals from engineers who want to go deep into the details.

This plugin provides resource management for each tool provided. For example, most use cases involve configuring and creating search configurations, query sets, and judgments. All of these resources are created, updated, deleted, and maintained by the Search Relevance plugin. When users are satisfied with the improvements to relevancy then they take the output and manually deploy the changes into their environment.

Exposing these powerful features through a simple UI is done through the  [Dashboards Search Relevance](https://github.com/opensearch-project/dashboards-search-relevance) plugin.

> [!IMPORTANT]  
> While shipping with OpenSearch, you must OPT IN to this feature.  To enable this run:
> ```
> curl -X PUT "http://localhost:9200/_cluster/settings" -H 'Content-Type: application/json' -d'
>  {
>    "persistent" : {
>     "plugins.search_relevance.workbench_enabled" : true
>   }
> }
>  '
> ```


For tutorials on how to leverage these tools, see [here](https://docs.opensearch.org/docs/latest/search-plugins/search-relevance/).


## Project Resources

* [Project Website](https://opensearch.org/)
* [Downloads](https://opensearch.org/downloads.html).
* [Documentation](https://opensearch.org/docs/)
* Need help? Try [Forums](https://discuss.opendistrocommunity.dev/) or [Slack channel](https://opensearch.slack.com/archives/C08HKHQMBN1)
* [Project Principles](https://opensearch.org/#principles)
* [Contributing to OpenSearch](CONTRIBUTING.md)
* [Maintainer Responsibilities](MAINTAINERS.md)
* [Release Management](RELEASING.md)
* [Security](SECURITY.md)
* [Code of Conduct](#code-of-conduct)
* [License](#license)
* [Copyright](#copyright)

## Code of Conduct

This project has adopted the [Amazon Open Source Code of Conduct](CODE_OF_CONDUCT.md). For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq), or contact [opensource-codeofconduct@amazon.com](mailto:opensource-codeofconduct@amazon.com) with any additional questions or comments.

## License

This project is licensed under the [Apache v2.0 License](LICENSE).

## Copyright

Copyright OpenSearch Contributors. See [NOTICE](NOTICE) for details.
