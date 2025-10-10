/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.integration;

import java.util.Locale;

import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

/**
 * Integration test that verifies SearchSource parsing of rescore with the `sltr` clause.
 * This ensures that when the LTR module/plugin is present, the NamedXContentRegistry
 * includes the LTR parsers and the request does not fail with an "unknown [sltr]" parsing error.
 * <p>
 * Note:
 * - This test does NOT require a fully trained LTR model. It is valid for the request to fail
 *   with model-not-found or runtime execution errors. The key validation is that parsing
 *   recognizes the "sltr" rescorer rather than failing with an unknown query/rescorer error.
 */
public class LtrSltrRescoreIT extends BaseSearchRelevanceIT {

    public void testRescoreParsingWithSltr() throws Exception {
        final String index = "ltr-sltr-it";

        final String indexConfig = "{"
            + "  \"settings\": {"
            + "    \"number_of_shards\": 1,"
            + "    \"number_of_replicas\": 0"
            + "  },"
            + "  \"mappings\": {"
            + "    \"properties\": {"
            + "      \"title\": {\"type\": \"text\"},"
            + "      \"body\": {\"type\": \"text\"}"
            + "    }"
            + "  }"
            + "}";

        createIndexWithConfiguration(index, indexConfig);

        final String bulk = "{ \"index\": {\"_index\":\""
            + index
            + "\", \"_id\":\"1\"} }\n"
            + "{ \"title\":\"alpha\", \"body\":\"foo\" }\n"
            + "{ \"index\": {\"_index\":\""
            + index
            + "\", \"_id\":\"2\"} }\n"
            + "{ \"title\":\"beta\", \"body\":\"bar\" }\n";

        bulkIngest(index, bulk);

        // Build a search request that includes rescore with sltr. We do not require the model to exist;
        // we only validate that the parser recognizes "sltr" and does not throw "unknown [sltr]" errors.
        final String searchBody = "{"
            + "  \"query\": {\"match_all\": {}},"
            + "  \"rescore\": ["
            + "    {"
            + "      \"window_size\": 10,"
            + "      \"rescore_query\": {"
            + "        \"sltr\": {"
            + "          \"params\": {\"keywords\": \"foo\"},"
            + "          \"model\": \"my_test_model\""
            + "        }"
            + "      }"
            + "    }"
            + "  ]"
            + "}";

        final Request search = new Request("GET", "/" + index + "/_search");
        search.setJsonEntity(searchBody);

        try {
            final Response ok = client().performRequest(search);
            assertEquals(200, ok.getStatusLine().getStatusCode());
        } catch (ResponseException re) {
            final Response r = re.getResponse();
            final int code = r.getStatusLine().getStatusCode();
            // Accept any response; the important part is that the error is NOT "unknown [sltr]".
            assertTrue("Expected an HTTP status (>=200). Got: " + code, code >= 200 && code < 600);
            final String msg = EntityUtils.toString(r.getEntity());
            final boolean unknownSltr = msg != null
                && msg.toLowerCase(Locale.ROOT).contains("unknown")
                && msg.toLowerCase(Locale.ROOT).contains("sltr");
            assertFalse("Cluster did not recognize 'sltr' rescore; LTR module may not be loaded. Message: " + msg, unknownSltr);
        }
    }
}
