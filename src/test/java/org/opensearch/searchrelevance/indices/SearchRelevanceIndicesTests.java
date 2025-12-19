/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import java.util.Map;
import java.util.Set;

import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.test.OpenSearchTestCase;

public class SearchRelevanceIndicesTests extends OpenSearchTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    public void testPropertiesOfEnumItems() {
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            assertNotNull(index.getMapping());
            assertTrue(index.getMapping().contains("\"properties\""));
            assertNotNull(index.getIndexName());
            assertFalse(index.getIndexName().isBlank());
        }
    }

    public void testProtectedFlag() {
        Set<SearchRelevanceIndices> protectedIndices = Set.of(SearchRelevanceIndices.EXPERIMENT);
        for (SearchRelevanceIndices index : protectedIndices) {
            assertTrue(index.isProtected());
        }

        Set<SearchRelevanceIndices> notProtectedIndices = Set.of(
            SearchRelevanceIndices.SEARCH_CONFIGURATION,
            SearchRelevanceIndices.JUDGMENT,
            SearchRelevanceIndices.JUDGMENT_CACHE,
            SearchRelevanceIndices.EVALUATION_RESULT,
            SearchRelevanceIndices.EXPERIMENT_VARIANT,
            SearchRelevanceIndices.QUERY_SET,
            SearchRelevanceIndices.SCHEDULED_JOBS,
            SearchRelevanceIndices.SCHEDULED_EXPERIMENT_HISTORY
        );
        for (SearchRelevanceIndices index : notProtectedIndices) {
            assertFalse(index.isProtected());
        }
    }

    /**
     * Test that all indices have schema_version defined in their mapping JSON
     * and that it is correctly parsed into the enum's schemaVersion field.
     */
    public void testSchemaVersionParsedFromMapping() {
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            // Verify schemaVersion is non-negative (all mappings should have schema_version >= 0)
            assertTrue("Index " + index.getIndexName() + " should have schema_version >= 0", index.getSchemaVersion() >= 0);

            // Verify the mapping JSON contains _meta.schema_version matching the parsed value
            Map<String, Object> mappingMap = XContentHelper.convertToMap(XContentType.JSON.xContent(), index.getMapping(), false);
            assertNotNull("Index " + index.getIndexName() + " mapping should have _meta field", mappingMap.get("_meta"));

            Map<?, ?> meta = (Map<?, ?>) mappingMap.get("_meta");
            Object versionInJson = meta.get(SearchRelevanceIndices.META_SCHEMA_VERSION_KEY);
            assertNotNull("Index " + index.getIndexName() + " _meta should have schema_version", versionInJson);

            int versionFromJson = ((Number) versionInJson).intValue();
            assertEquals(
                "Index " + index.getIndexName() + " schemaVersion should match JSON _meta.schema_version",
                versionFromJson,
                index.getSchemaVersion()
            );
        }
    }

}
