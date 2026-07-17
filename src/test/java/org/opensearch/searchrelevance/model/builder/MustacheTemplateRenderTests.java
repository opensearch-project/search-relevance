/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.builder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.script.ScriptEngine;
import org.opensearch.script.ScriptModule;
import org.opensearch.script.ScriptService;
import org.opensearch.script.mustache.MustacheScriptEngine;
import org.opensearch.search.SearchModule;
import org.opensearch.test.OpenSearchTestCase;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the real Mustache rendering path in {@link SearchRequestBuilder} against a live
 * {@link ScriptService} backed by the core {@code MustacheScriptEngine}. Unlike
 * {@link SearchRequestBuilderTests} (which passes a null ScriptService and only covers the
 * legacy %SearchText% and error paths), these tests assert the actual substituted output for
 * single/multi variable templates, JSON escaping, missing variables, and partial rejection.
 */
public class MustacheTemplateRenderTests extends OpenSearchTestCase {

    private static final String TEST_INDEX = "test_index";
    private static final int TEST_SIZE = 10;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Before
    public void setup() {
        NamedXContentRegistry reg = new NamedXContentRegistry(
            new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()
        );
        ScriptEngine mustacheEngine = new MustacheScriptEngine();
        ScriptService scriptService = new ScriptService(
            Settings.EMPTY,
            Collections.singletonMap(mustacheEngine.getType(), mustacheEngine),
            ScriptModule.CORE_CONTEXTS
        );
        SearchRequestBuilder.initialize(reg, scriptService);
    }

    public void testSingleVariableSubstitution() throws Exception {
        String query = "{\"query\":{\"match\":{\"title\":\"{{queryText}}\"}}}";
        SearchRequest sr = SearchRequestBuilder.buildSearchRequest(TEST_INDEX, query, "laptop", null, null, TEST_SIZE);
        assertEquals("laptop", matchTitleValue(sr));
    }

    public void testMultipleCustomFieldSubstitution() throws Exception {
        String query = "{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"{{queryText}}\"}}],"
            + "\"filter\":[{\"term\":{\"category_filter\":\"{{category}}\"}}]}}}";
        SearchRequest sr = SearchRequestBuilder.buildSearchRequest(
            TEST_INDEX,
            query,
            "phone",
            Collections.singletonMap("category", "electronics"),
            null,
            TEST_SIZE
        );
        String rendered = sr.source().toString();
        // queryText hydrates the must clause, the custom field hydrates the filter clause
        assertEquals("phone", boolMustMatchTitleValue(sr));
        assertEquals("electronics", boolFilterTermValue(sr, "category_filter"));
        assertFalse("no unresolved template markers should remain", rendered.contains("{{"));
    }

    public void testJsonEscapingOfSpecialCharacters() throws Exception {
        // A value with quotes would corrupt the DSL under naive string replacement; the managed
        // Mustache engine JSON-escapes it, so the query stays valid and the value round-trips intact.
        String tricky = "sony \"official\" 50% #1";
        String query = "{\"query\":{\"match\":{\"title\":\"{{queryText}}\"}}}";
        SearchRequest sr = SearchRequestBuilder.buildSearchRequest(TEST_INDEX, query, tricky, null, null, TEST_SIZE);
        assertEquals(tricky, matchTitleValue(sr));
    }

    public void testMissingVariableRendersEmpty() throws Exception {
        // {{brand}} is referenced but not supplied — Mustache renders it as an empty string
        // rather than failing. This documents (and pins) the current missing-variable behavior.
        String query = "{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"{{queryText}}\"}}],"
            + "\"filter\":[{\"term\":{\"brand\":\"{{brand}}\"}}]}}}";
        SearchRequest sr = SearchRequestBuilder.buildSearchRequest(
            TEST_INDEX,
            query,
            "shoes",
            Collections.singletonMap("category", "electronics"),
            null,
            TEST_SIZE
        );
        assertEquals("shoes", boolMustMatchTitleValue(sr));
        assertEquals("", boolFilterTermValue(sr, "brand"));
        assertFalse(sr.source().toString().contains("{{"));
    }

    public void testPartialTemplateIsRejected() {
        // Mustache partials ({{>name}}) are an injection vector; OpenSearch core disables them at
        // the engine level (opensearch-project/OpenSearch#22438), so compilation must fail.
        String query = "{\"query\":{\"match\":{\"title\":\"{{>/etc/passwd}}\"}}}";
        Exception e = expectThrows(
            Exception.class,
            () -> SearchRequestBuilder.buildSearchRequest(TEST_INDEX, query, "laptop", null, null, TEST_SIZE)
        );
        assertTrue(
            "expected a partial-not-supported failure but got: " + fullMessage(e),
            fullMessage(e).contains("Partial templates are not supported")
        );
    }

    // ---- helpers: navigate the serialized SearchSourceBuilder JSON ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source(SearchRequest sr) throws Exception {
        return MAPPER.readValue(sr.source().toString(), new TypeReference<Map<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static String matchTitleValue(SearchRequest sr) throws Exception {
        Map<String, Object> query = (Map<String, Object>) source(sr).get("query");
        Map<String, Object> match = (Map<String, Object>) query.get("match");
        Map<String, Object> title = (Map<String, Object>) match.get("title");
        return (String) title.get("query");
    }

    @SuppressWarnings("unchecked")
    private static String boolMustMatchTitleValue(SearchRequest sr) throws Exception {
        Map<String, Object> query = (Map<String, Object>) source(sr).get("query");
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        List<Object> must = (List<Object>) bool.get("must");
        Map<String, Object> match = (Map<String, Object>) ((Map<String, Object>) must.get(0)).get("match");
        Map<String, Object> title = (Map<String, Object>) match.get("title");
        return (String) title.get("query");
    }

    @SuppressWarnings("unchecked")
    private static String boolFilterTermValue(SearchRequest sr, String field) throws Exception {
        Map<String, Object> query = (Map<String, Object>) source(sr).get("query");
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        List<Object> filter = (List<Object>) bool.get("filter");
        for (Object clause : filter) {
            Map<String, Object> term = (Map<String, Object>) ((Map<String, Object>) clause).get("term");
            if (term != null && term.containsKey(field)) {
                return (String) ((Map<String, Object>) term.get(field)).get("value");
            }
        }
        throw new AssertionError("term filter on field [" + field + "] not found in " + sr.source());
    }

    private static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            sb.append(cur.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
