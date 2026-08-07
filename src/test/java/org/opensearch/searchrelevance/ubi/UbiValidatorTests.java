/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import java.util.List;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.AliasMetadata;
import org.opensearch.cluster.metadata.DataStream;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.test.OpenSearchTestCase;

public class UbiValidatorTests extends OpenSearchTestCase {

    private static final String SAMPLE_EVENTS_INDEX = "opensearch_dashboards_sample_ubi_events";

    private static final Map<String, Object> VALID_QUERIES_MAPPING = Map.of("properties", Map.of("user_query", Map.of("type", "keyword")));

    private static final Map<String, Object> VALID_EVENTS_MAPPING = Map.of(
        "properties",
        Map.of(
            "query_id",
            Map.of("type", "keyword"),
            "action_name",
            Map.of("type", "keyword"),
            "event_attributes",
            Map.of("properties", Map.of("object", Map.of("properties", Map.of("object_id", Map.of("type", "keyword")))))
        )
    );

    private static final Map<String, Object> UNRELATED_MAPPING = Map.of("properties", Map.of("something_else", Map.of("type", "keyword")));

    private IndexNameExpressionResolver resolver;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        resolver = new IndexNameExpressionResolver(new ThreadContext(Settings.EMPTY));
    }

    public void testValidateUbiQueriesIndexWithValidFields() {
        ClusterState clusterState = clusterState(index("ubi_queries", VALID_QUERIES_MAPPING));

        assertTrue(UbiValidator.validateUbiQueriesIndex(clusterState, resolver, "ubi_queries").isValid());
    }

    public void testValidateUbiQueriesIndexWithMissingFields() {
        ClusterState clusterState = clusterState(index("ubi_queries", UNRELATED_MAPPING));

        UbiValidator.ValidationResult result = UbiValidator.validateUbiQueriesIndex(clusterState, resolver, "ubi_queries");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("[ubi_queries]"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("user_query"));
    }

    public void testValidateUbiEventsIndexWithValidFields() {
        ClusterState clusterState = clusterState(index("ubi_events", VALID_EVENTS_MAPPING));

        assertTrue(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events").isValid());
    }

    public void testValidateUbiEventsIndexWithMissingFields() {
        ClusterState clusterState = clusterState(index("ubi_events", Map.of("properties", Map.of("query_id", Map.of("type", "keyword")))));

        UbiValidator.ValidationResult result = UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("action_name"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("event_attributes.object.object_id"));
    }

    public void testValidateUbiEventsIndexWithoutMapping() {
        ClusterState clusterState = clusterState(index("ubi_events", null));

        assertFalse(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events").isValid());
    }

    public void testValidateUbiQueriesIndexNotExist() {
        UbiValidator.ValidationResult result = UbiValidator.validateUbiQueriesIndex(clusterState(), resolver, "ubi_queries");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("UBI queries index [ubi_queries] does not exist"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("ubiQueriesIndex"));
    }

    public void testValidateUbiEventsIndexNotExist() {
        UbiValidator.ValidationResult result = UbiValidator.validateUbiEventsIndex(clusterState(), resolver, "ubi_events");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("UBI events index [ubi_events] does not exist"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("ubiEventsIndex"));
    }

    public void testValidateRejectsBlankIndexExpression() {
        UbiValidator.ValidationResult result = UbiValidator.validateUbiEventsIndex(clusterState(), resolver, "  ");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("must not be empty"));
    }

    public void testValidateUbiEventsIndexResolvesAlias() {
        ClusterState clusterState = clusterState(index(SAMPLE_EVENTS_INDEX, VALID_EVENTS_MAPPING, "ubi_events"));

        assertTrue(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events").isValid());
    }

    public void testValidateUbiQueriesIndexResolvesAlias() {
        ClusterState clusterState = clusterState(index("opensearch_dashboards_sample_ubi_queries", VALID_QUERIES_MAPPING, "ubi_queries"));

        assertTrue(UbiValidator.validateUbiQueriesIndex(clusterState, resolver, "ubi_queries").isValid());
    }

    public void testValidateUbiEventsIndexAcceptsAliasWhereOnlySomeBackingIndicesMatch() {
        ClusterState clusterState = clusterState(
            index("ubi_events-2026.01", VALID_EVENTS_MAPPING, "ubi_events"),
            index("unrelated-index", UNRELATED_MAPPING, "ubi_events")
        );

        assertTrue(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events").isValid());
    }

    public void testValidateUbiEventsIndexRejectsAliasWhereNoBackingIndexMatches() {
        ClusterState clusterState = clusterState(
            index("unrelated-one", UNRELATED_MAPPING, "ubi_events"),
            index("unrelated-two", UNRELATED_MAPPING, "ubi_events")
        );

        UbiValidator.ValidationResult result = UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("No index resolved from [ubi_events]"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("unrelated-one"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("unrelated-two"));
    }

    public void testValidateUbiEventsIndexResolvesWildcard() {
        ClusterState clusterState = clusterState(index(SAMPLE_EVENTS_INDEX, VALID_EVENTS_MAPPING));

        assertTrue(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "*ubi_events*").isValid());
    }

    public void testValidateUbiEventsIndexRejectsWildcardMatchingNothing() {
        UbiValidator.ValidationResult result = UbiValidator.validateUbiEventsIndex(clusterState(), resolver, "no_such_prefix*");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("UBI events index [no_such_prefix*] does not exist"));
    }

    public void testValidateUbiEventsIndexResolvesDataStream() {
        String backingIndexName = DataStream.getDefaultBackingIndexName("ubi_events", 1);
        IndexMetadata backingIndex = index(backingIndexName, VALID_EVENTS_MAPPING).build();
        DataStream dataStream = new DataStream("ubi_events", new DataStream.TimestampField("@timestamp"), List.of(backingIndex.getIndex()));
        ClusterState clusterState = ClusterState.builder(new ClusterName("test"))
            .metadata(Metadata.builder().put(backingIndex, false).put(dataStream).build())
            .build();

        assertTrue(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events").isValid());
    }

    public void testValidateUbiEventsIndexReportsClosedBackingIndex() {
        ClusterState clusterState = clusterState(
            index("ubi_events-2026.01", VALID_EVENTS_MAPPING, "ubi_events"),
            index("ubi_events-2025.01", VALID_EVENTS_MAPPING, "ubi_events").state(IndexMetadata.State.CLOSE)
        );

        UbiValidator.ValidationResult result = UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("closed index [ubi_events-2025.01]"));
        assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("ubiEventsIndex"));
    }

    public void testValidateUbiEventsIndexIgnoresClosedIndexOutsideTheExpression() {
        ClusterState clusterState = clusterState(
            index("ubi_events", VALID_EVENTS_MAPPING),
            index("some_closed_index", UNRELATED_MAPPING).state(IndexMetadata.State.CLOSE)
        );

        assertTrue(UbiValidator.validateUbiEventsIndex(clusterState, resolver, "ubi_events").isValid());
    }

    private static ClusterState clusterState(IndexMetadata.Builder... indices) {
        Metadata.Builder metadata = Metadata.builder();
        for (IndexMetadata.Builder index : indices) {
            metadata.put(index.build(), false);
        }
        return ClusterState.builder(new ClusterName("test")).metadata(metadata.build()).build();
    }

    private static IndexMetadata.Builder index(String indexName, Map<String, Object> mappingSource, String... aliases) {
        IndexMetadata.Builder builder = IndexMetadata.builder(indexName)
            .settings(
                Settings.builder()
                    .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            );
        if (mappingSource != null) {
            builder.putMapping(new MappingMetadata(MapperService.SINGLE_MAPPING_NAME, mappingSource));
        }
        for (String alias : aliases) {
            builder.putAlias(AliasMetadata.builder(alias));
        }
        return builder;
    }
}
