FROM opensearchproject/opensearch:3.0.0

ADD ./build/distributions/opensearch-search-relevance-3.0.0.0.zip  /tmp/opensearch-search-relevance-3.0.0.0.zip
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:/tmp/opensearch-search-relevance-3.0.0.0.zip

ADD --chmod=444 https://github.com/opensearch-project/user-behavior-insights/releases/download/3.0.0.0/opensearch-ubi-3.0.0.0.zip  /tmp/opensearch-ubi-3.0.0.0-SNAPSHOT.zip
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:/tmp/opensearch-ubi-3.0.0.0-SNAPSHOT.zip
