FROM opensearchstaging/opensearch:3.0.0-beta1

ADD ./build/distributions/opensearch-search-relevance-3.0.0.0.zip  /tmp/opensearch-search-relevance-3.0.0.0.zip
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:/tmp/opensearch-search-relevance-3.0.0.0.zip
