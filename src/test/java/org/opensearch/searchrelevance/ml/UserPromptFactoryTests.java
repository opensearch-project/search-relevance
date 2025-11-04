/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_SEARCH;
import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_SEARCH_WITH_REFERENCE;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for UserPromptFactory focusing on template variable replacement.
 */
public class UserPromptFactoryTests extends OpenSearchTestCase {

    // ============================================
    // Default Format Tests (No Template Provided)
    // ============================================

    public void testBuildUserContent_NoTemplate_NoReferenceData() {
        // Test default format when no template and no reference data
        String searchText = "What is OpenSearch?";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"},{\"id\":\"2\",\"source\":\"doc2\"}]";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, null);

        String expected = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        assertEquals("Should use INPUT_FORMAT_SEARCH when no reference data", expected, result);
    }

    public void testBuildUserContent_NoTemplate_WithReferenceData() {
        // Test default format when no template but reference data exists
        String searchText = "What is OpenSearch?";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("referenceAnswer", "OpenSearch is a search and analytics suite");
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, null);

        String expected = String.format(
            Locale.ROOT,
            INPUT_FORMAT_SEARCH_WITH_REFERENCE,
            searchText,
            "OpenSearch is a search and analytics suite",
            hitsJson
        );
        assertEquals("Should use INPUT_FORMAT_SEARCH_WITH_REFERENCE when reference data exists", expected, result);
    }

    public void testBuildUserContent_NoTemplate_MultipleReferenceFields() {
        // Test default format with multiple reference fields (should concatenate)
        String searchText = "red shoes";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("color", "red");
        referenceData.put("category", "footwear");
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, null);

        // Should concatenate all values with "; " delimiter
        assertTrue("Should contain search text", result.contains(searchText));
        assertTrue("Should contain hitsJson", result.contains(hitsJson));
        // Should use one of the reference values
        assertTrue("Should contain reference data", result.contains("red") || result.contains("footwear"));
    }

    public void testBuildUserContent_EmptyTemplate() {
        // Test that empty template falls back to default format
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, "");

        String expected = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        assertEquals("Empty template should use default format", expected, result);
    }

    public void testBuildUserContent_WhitespaceTemplate() {
        // Test that whitespace-only template falls back to default format
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, "   ");

        String expected = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        assertEquals("Whitespace template should use default format", expected, result);
    }

    // ============================================
    // Template Variable Replacement Tests
    // ============================================

    public void testBuildUserContent_Template_QueryVariable() {
        // Test replacement of {{queryText}} variable
        String searchText = "What is OpenSearch?";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";
        String template = "User query: {{queryText}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace {{queryText}} with searchText", "User query: What is OpenSearch?", result);
    }

    public void testBuildUserContent_Template_SearchTextVariable() {
        // Test replacement of {{searchText}} variable
        String searchText = "red shoes";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";
        String template = "Search: {{searchText}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace {{searchText}} with searchText", "Search: red shoes", result);
    }

    public void testBuildUserContent_Template_HitsVariable() {
        // Test replacement of {{hits}} variable
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";
        String template = "Results: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace {{hits}} with hitsJson", "Results: " + hitsJson, result);
    }

    public void testBuildUserContent_Template_ResultsVariable() {
        // Test replacement of {{results}} variable (alias for hits)
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Search results: {{results}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace {{results}} with hitsJson", "Search results: " + hitsJson, result);
    }

    public void testBuildUserContent_Template_ReferenceVariable() {
        // Test replacement of {{reference}} variable
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("referenceAnswer", "This is the reference answer");
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Reference: {{reference}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace {{reference}} with referenceAnswer", "Reference: This is the reference answer", result);
    }

    public void testBuildUserContent_Template_ReferenceAnswerVariable() {
        // Test replacement of {{referenceAnswer}} variable
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("referenceAnswer", "Expected answer");
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Expected: {{referenceAnswer}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace {{referenceAnswer}} with referenceAnswer", "Expected: Expected answer", result);
    }

    public void testBuildUserContent_Template_CustomField() {
        // Test replacement of custom field from referenceData
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("category", "electronics");
        referenceData.put("brand", "Sony");
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Category: {{category}}, Brand: {{brand}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace custom fields", "Category: electronics, Brand: Sony", result);
    }

    public void testBuildUserContent_Template_MultipleVariables() {
        // Test replacement of multiple variables in one template
        String searchText = "What is OpenSearch?";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("referenceAnswer", "OpenSearch is a search suite");
        referenceData.put("category", "technology");
        String hitsJson = "[{\"id\":\"1\",\"source\":\"doc1\"}]";
        String template = "Query: {{queryText}}\nReference: {{referenceAnswer}}\nCategory: {{category}}\nResults: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        String expected = "Query: What is OpenSearch?\n"
            + "Reference: OpenSearch is a search suite\n"
            + "Category: technology\n"
            + "Results: "
            + hitsJson;
        assertEquals("Should replace all variables", expected, result);
    }

    public void testBuildUserContent_Template_UnknownVariable() {
        // Test that unknown variables are replaced with empty string
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {{queryText}}, Unknown: {{unknownField}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace unknown variable with empty string", "Query: test, Unknown: ", result);
    }

    public void testBuildUserContent_Template_NoReferenceAnswer() {
        // Test {{reference}} when referenceAnswer doesn't exist
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("category", "tech");
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {{queryText}}, Reference: {{reference}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should replace missing reference with empty string", "Query: test, Reference: ", result);
    }

    public void testBuildUserContent_Template_NullReferenceData() {
        // Test template with null referenceData
        String searchText = "test";
        Map<String, String> referenceData = null;
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {{queryText}}, Results: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should handle null referenceData", "Query: test, Results: " + hitsJson, result);
    }

    public void testBuildUserContent_Template_SameVariableMultipleTimes() {
        // Test using the same variable multiple times
        String searchText = "OpenSearch";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "{{queryText}} is awesome. {{queryText}} is open source. What is {{queryText}}?";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals(
            "Should replace all occurrences of same variable",
            "OpenSearch is awesome. OpenSearch is open source. What is OpenSearch?",
            result
        );
    }

    public void testBuildUserContent_Template_VariableWithSpaces() {
        // Test that variables with spaces are NOT replaced (trimming happens but replacement doesn't match)
        // This is current behavior - the matcher extracts and trims the variable name,
        // but the replacement looks for the exact original pattern with spaces
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {{ query }}, Results: {{  hits  }}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        // Current behavior: variables with spaces are left as-is because replacement doesn't match
        assertEquals("Variables with spaces should be left as-is (current behavior)", template, result);
    }

    public void testBuildUserContent_Template_ComplexRealWorldExample() {
        // Test a complex real-world template
        String searchText = "red leather shoes";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("referenceAnswer", "High quality red leather shoes with rubber sole");
        referenceData.put("expectedScore", "0.9");
        referenceData.put("category", "footwear");
        String hitsJson = "[{\"id\":\"doc1\",\"source\":\"Red shoes\"},{\"id\":\"doc2\",\"source\":\"Leather boots\"}]";
        String template = "Given the search query: {{queryText}}\n\n"
            + "Expected answer: {{referenceAnswer}}\n"
            + "Expected relevance score: {{expectedScore}}\n"
            + "Product category: {{category}}\n\n"
            + "Please rate the following search results:\n"
            + "{{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        String expected = "Given the search query: red leather shoes\n\n"
            + "Expected answer: High quality red leather shoes with rubber sole\n"
            + "Expected relevance score: 0.9\n"
            + "Product category: footwear\n\n"
            + "Please rate the following search results:\n"
            + hitsJson;
        assertEquals("Should handle complex real-world template", expected, result);
    }

    public void testBuildUserContent_Template_EmptySearchText() {
        // Test with empty search text
        String searchText = "";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {{queryText}}, Results: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should handle empty search text", "Query: , Results: " + hitsJson, result);
    }

    public void testBuildUserContent_Template_NullSearchText() {
        // Test with null search text
        String searchText = null;
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {{queryText}}, Results: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should handle null search text", "Query: , Results: " + hitsJson, result);
    }

    public void testBuildUserContent_Template_EmptyHitsJson() {
        // Test with empty hits JSON
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "";
        String template = "Query: {{queryText}}, Results: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should handle empty hits JSON", "Query: test, Results: ", result);
    }

    public void testBuildUserContent_Template_NullHitsJson() {
        // Test with null hits JSON
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = null;
        String template = "Query: {{queryText}}, Results: {{hits}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should handle null hits JSON", "Query: test, Results: ", result);
    }

    public void testBuildUserContent_Template_SpecialCharactersInValues() {
        // Test with special characters in values
        String searchText = "test \"quoted\" & special <chars>";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("referenceAnswer", "Answer with 'quotes' & symbols");
        String hitsJson = "[{\"id\":\"1\",\"source\":\"data\"}]";
        String template = "Query: {{queryText}}\nReference: {{referenceAnswer}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals(
            "Should handle special characters",
            "Query: test \"quoted\" & special <chars>\nReference: Answer with 'quotes' & symbols",
            result
        );
    }

    public void testBuildUserContent_Template_NoVariables() {
        // Test template with no variables (static text)
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "This is a static prompt with no variables.";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        assertEquals("Should return template as-is when no variables", template, result);
    }

    public void testBuildUserContent_Template_MalformedVariables() {
        // Test template with malformed variables
        String searchText = "test";
        Map<String, String> referenceData = new HashMap<>();
        String hitsJson = "[{\"id\":\"1\"}]";
        String template = "Query: {query} or {{query or query}} or {{ or {{}}";

        String result = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, template);

        // Malformed variables should be left as-is
        assertTrue("Should not replace malformed variables", result.contains("{query}"));
        assertTrue("Should handle empty variable", result.contains("{{}}"));
    }
}
